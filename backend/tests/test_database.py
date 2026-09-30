"""Persistence coverage for the V1 memory + handoff schema.

RideSession is a Ride Saathi intent/session, NOT a provider booking.
HandoffEvent records handing control to an external provider app.
SessionEvent is append-only analytics without transcript/location payloads.
"""

from datetime import UTC, datetime
from decimal import Decimal
from uuid import UUID, uuid4

import pytest
from sqlalchemy import delete, inspect, select, text, update
from sqlalchemy.exc import DBAPIError, IntegrityError
from sqlalchemy.orm import selectinload

from app.models import (
    DestinationCorrection,
    DestinationResolution,
    HandoffEvent,
    PlaceAlias,
    RideSession,
    SavedPlace,
    SessionEvent,
    User,
    UserPreference,
)
from app.models.enums import SessionEventType

pytestmark = [pytest.mark.asyncio, pytest.mark.postgres]


def user(**kwargs):
    kwargs.setdefault("name", "Rider")
    return User(**kwargs)


def place(owner, **kwargs):
    return SavedPlace(
        user=owner,
        label=kwargs.pop("label", "Home"),
        address_text=kwargs.pop("address_text", "Hyderabad"),
        latitude=kwargs.pop("latitude", Decimal("17.4140001")),
        longitude=kwargs.pop("longitude", Decimal("78.4120001")),
        **kwargs,
    )


def session(owner=None, **kwargs):
    return RideSession(user=owner or user(), **kwargs)


def resolution(owner=None, ride=None, **kwargs):
    ride = ride or session(owner or user())
    kwargs.setdefault("raw_transcript", "take me home")
    kwargs.setdefault("normalized_query", "home")
    kwargs.setdefault("confidence", Decimal("0.9"))
    return DestinationResolution(
        user=ride.user, ride_session=ride, **kwargs
    )


def session_event(ride, **kwargs):
    kwargs.setdefault("event_type", SessionEventType.RIDE_SESSION_STARTED)
    return SessionEvent(ride_session=ride, **kwargs)


async def test_user_defaults_and_nullable_name_before_onboarding(db):
    users = [User(), User(name="A")]
    db.add_all(users)
    await db.flush()
    for item in users:
        assert isinstance(item.id, UUID)
        assert item.phone is None and item.email is None
        assert item.status == "active" and item.preferred_language == "en"
        assert item.onboarding_completed is False
        assert item.created_at.tzinfo is not None
        assert item.updated_at.tzinfo is not None


async def test_onboarding_complete_requires_nonblank_name(db):
    db.add(User(name="Rider", onboarding_completed=True))
    await db.flush()
    for bad in (None, "", "   "):
        with pytest.raises(IntegrityError):
            async with db.begin_nested():
                db.add(User(name=bad, onboarding_completed=True))
                await db.flush()


@pytest.mark.parametrize(
    "field,value", [("phone", "+910000000001"), ("email", "rider@example.test")]
)
async def test_unique_nonnull_contacts(db, field, value):
    db.add(User(name="A", **{field: value}))
    await db.flush()
    with pytest.raises(IntegrityError):
        async with db.begin_nested():
            db.add(User(name="B", **{field: value}))
            await db.flush()


async def test_user_preference_is_one_to_one_with_defaults(db):
    owner = user()
    pref = UserPreference(user=owner)
    db.add(pref)
    await db.flush()
    assert pref.confirmation_mode == "always"
    assert pref.preferred_ride_provider == "uber"
    assert pref.auto_confirm_high_confidence is False
    with pytest.raises(IntegrityError):
        async with db.begin_nested():
            db.add(UserPreference(user=owner))
            await db.flush()


async def test_saved_place_label_unique_per_user(db):
    owner = user()
    db.add(place(owner))
    await db.flush()
    with pytest.raises(IntegrityError):
        async with db.begin_nested():
            db.add(place(owner))
            await db.flush()
    db.add(place(user(name="Other"), label="Home"))
    await db.flush()


async def test_only_one_active_home_per_user(db):
    owner = user()
    db.add(place(owner, place_type="home"))
    await db.flush()
    with pytest.raises(IntegrityError):
        async with db.begin_nested():
            db.add(place(owner, label="Home 2", place_type="home"))
            await db.flush()
    # A second home is allowed once the first is inactive; work labels unaffected.
    db.add(place(owner, label="Home 2", place_type="home", is_active=False))
    db.add(place(owner, label="Work", place_type="work"))
    await db.flush()


async def test_place_alias_unique_per_place_and_relationships(db):
    owner = user()
    home = place(owner)
    alias = PlaceAlias(
        saved_place=home, alias="Ghar", normalized_alias="ghar",
    )
    db.add(alias)
    await db.flush()
    assert alias.source == "user_created" and alias.use_count == 0
    with pytest.raises(IntegrityError):
        async with db.begin_nested():
            db.add(
                PlaceAlias(
                    saved_place=home, alias="GHAR", normalized_alias="ghar",
                )
            )
            await db.flush()
    # Same normalized alias is fine on another place.
    db.add(
        PlaceAlias(
            saved_place=place(owner, label="Work"),
            alias="Ghar",
            normalized_alias="ghar",
        )
    )
    await db.flush()
    with pytest.raises(IntegrityError):
        async with db.begin_nested():
            db.add(PlaceAlias(saved_place=home, alias="", normalized_alias=""))
            await db.flush()


async def test_ride_session_defaults_and_status(db):
    ride = session()
    db.add(ride)
    await db.flush()
    assert ride.status == "started"
    assert ride.destination_saved_place_id is None
    assert ride.destination_source is None and ride.destination_latitude is None
    assert ride.destination_longitude is None
    assert ride.started_at.tzinfo is not None


@pytest.mark.parametrize(
    "fields",
    [
        {"pickup_latitude": Decimal("91")},
        {"pickup_longitude": Decimal("181")},
        {"destination_latitude": Decimal("-91")},
        {"destination_longitude": Decimal("200")},
        {"pickup_latitude": Decimal("17.4")},  # pair must be both present/absent
        {"destination_longitude": Decimal("78.4")},
        {"resolution_confidence": Decimal("1.0001")},
        {"resolution_confidence": Decimal("-0.1")},
    ],
)
async def test_ride_session_coordinate_and_confidence_bounds(db, fields):
    ride = session(**fields)
    if "pickup_latitude" in fields and "pickup_longitude" not in fields:
        ride.pickup_longitude = None
    db.add(ride)
    with pytest.raises(IntegrityError):
        await db.flush()
    await db.rollback()


async def test_destination_resolution_bounds_and_candidate_limit(db):
    ride = session()
    good = resolution(ride=ride)
    good.candidates = [{"name": f"c{i}"} for i in range(5)]
    db.add(good)
    await db.flush()
    assert good.was_confirmed is False and good.was_corrected is False
    assert good.candidates is not None

    bad_confidence = resolution(ride=ride, confidence=Decimal("2"))
    db.add(bad_confidence)
    with pytest.raises(IntegrityError):
        await db.flush()
    await db.rollback()

    too_many = resolution(ride=ride, normalized_query="office")
    too_many.candidates = [{"name": f"c{i}"} for i in range(6)]
    db.add(too_many)
    with pytest.raises(IntegrityError):
        await db.flush()
    await db.rollback()

    non_array = resolution(ride=ride, normalized_query="park")
    non_array.candidates = {"name": "not-an-array"}  # type: ignore[assignment]
    db.add(non_array)
    with pytest.raises(IntegrityError):
        await db.flush()
    await db.rollback()


async def test_destination_correction_relationships_and_single_per_resolution(db):
    owner = user()
    ride = session(owner)
    resolved = resolution(ride=ride)
    correction = DestinationCorrection(
        user=owner,
        resolution=resolved,
        raw_query="take me hoem",
        normalized_query="home",
        accepted_candidate={"name": "Home"},
    )
    db.add(correction)
    await db.flush()
    assert correction.rejected_candidate is None
    with pytest.raises(IntegrityError):
        async with db.begin_nested():
            db.add(
                DestinationCorrection(
                    user=user(name="Other"),
                    resolution=resolved,
                    raw_query="home",
                    normalized_query="home",
                    accepted_candidate={"name": "Home"},
                )
            )
            await db.flush()


async def test_handoff_event_one_per_session(db):
    ride = session()
    first = HandoffEvent(ride_session=ride, provider="uber")
    db.add(first)
    await db.flush()
    assert first.status == "created"
    assert first.handoff_created_at.tzinfo is not None
    with pytest.raises(IntegrityError):
        async with db.begin_nested():
            db.add(HandoffEvent(ride_session=ride, provider="uber"))
            await db.flush()


@pytest.mark.parametrize(
    "enum_name",
    [
        "language_code",
        "user_status",
        "saved_place_type",
        "confirmation_mode",
        "alias_source",
        "ride_session_status",
        "destination_source",
        "handoff_status",
        "session_event_type",
    ],
)
async def test_postgres_rejects_invalid_enum_values(db, enum_name):
    # Bypass Python's enum validation and test the actual native PostgreSQL type.
    with pytest.raises(DBAPIError):
        async with db.begin_nested():
            await db.execute(text(f"SELECT CAST('not-a-real-value' AS {enum_name})"))


@pytest.mark.parametrize("loaded_relationship", [False, True])
async def test_deleting_saved_place_preserves_session_snapshot(db, loaded_relationship):
    owner = user()
    home = place(owner)
    ride = session(
        owner,
        destination_saved_place=home,
        destination_name="Home snapshot",
        destination_address_text="Original address",
        destination_latitude=home.latitude,
        destination_longitude=home.longitude,
        destination_source="saved_place",
    )
    resolved = resolution(ride=ride, selected_saved_place=home)
    audit = session_event(ride)
    db.add_all([resolved, audit])
    await db.flush()
    if loaded_relationship:
        await db.refresh(home, ["ride_sessions"])
    await db.delete(home)
    await db.flush()
    await db.refresh(ride)
    await db.refresh(resolved)
    assert ride.destination_saved_place_id is None
    assert ride.destination_name == "Home snapshot"
    assert ride.destination_address_text == "Original address"
    assert ride.destination_latitude == Decimal("17.4140001")
    assert resolved.selected_saved_place_id is None
    assert await db.get(SessionEvent, audit.id) is audit


async def test_soft_delete_preserves_session_history(db):
    owner = user()
    home = place(owner)
    ride = session(owner, destination_saved_place=home)
    db.add(ride)
    await db.flush()
    home.is_active = False
    home.deleted_at = datetime.now(UTC)
    await db.flush()
    await db.refresh(ride)
    assert ride.destination_saved_place_id == home.id
    assert await db.get(RideSession, ride.id) is ride
    # Required uniqueness applies even to soft-deleted labels; restore/rename.
    with pytest.raises(IntegrityError):
        async with db.begin_nested():
            db.add(place(owner))
            await db.flush()


async def test_saved_place_ownership_cannot_cross_users(db):
    owner = user()
    other = user(name="Other")
    home = place(owner)
    db.add(home)
    await db.flush()
    ride = session(other, destination_saved_place=home)
    db.add(ride)
    await db.flush()
    # Ownership is application-enforced; the FK alone permits the link, so
    # services must reject cross-user snapshot assignment.
    assert ride.destination_saved_place_id == home.id
    loaded = await db.scalar(
        select(RideSession)
        .where(RideSession.id == ride.id)
        .options(selectinload(RideSession.destination_saved_place))
    )
    assert loaded.destination_saved_place.user_id == owner.id != other.id


@pytest.mark.parametrize(
    "target", [User, RideSession, DestinationResolution, SavedPlace]
)
async def test_historical_parents_cannot_cascade_delete(db, target):
    owner = user()
    ride = session(owner)
    resolved = resolution(ride=ride)
    audit = session_event(ride)
    handoff = HandoffEvent(ride_session=ride, provider="uber")
    correction = DestinationCorrection(
        user=owner,
        resolution=resolved,
        raw_query="home",
        normalized_query="home",
        accepted_candidate={"name": "Home"},
    )
    db.add_all([resolved, audit, handoff, correction])
    await db.flush()
    if target is SavedPlace:
        home = place(owner)
        ride.destination_saved_place = home
        await db.flush()
        # Saved places use SET NULL snapshots, so deletion is allowed.
        await db.execute(delete(target).where(target.id == home.id))
        return
    with pytest.raises(IntegrityError):
        async with db.begin_nested():
            await db.execute(delete(target))


@pytest.mark.parametrize(
    "operation", ["orm_update", "orm_delete", "bulk_update", "bulk_delete", "truncate"]
)
async def test_session_events_are_append_only_in_postgres(db, operation):
    audit = session_event(session())
    db.add(audit)
    await db.flush()
    with pytest.raises(IntegrityError, match="append-only"):
        async with db.begin_nested():
            if operation == "orm_update":
                audit.event_type = SessionEventType.SESSION_FAILED
                await db.flush()
            elif operation == "orm_delete":
                await db.delete(audit)
                await db.flush()
            elif operation == "bulk_update":
                await db.execute(update(SessionEvent).values(event_type="session_failed"))
            elif operation == "bulk_delete":
                await db.execute(delete(SessionEvent))
            else:
                await db.execute(text("TRUNCATE session_events"))


async def test_foreign_keys_reject_unknown_parents(db):
    with pytest.raises(IntegrityError):
        async with db.begin_nested():
            db.add(RideSession(user_id=uuid4()))
            await db.flush()
    with pytest.raises(IntegrityError):
        async with db.begin_nested():
            db.add(
                DestinationResolution(
                    user_id=uuid4(),
                    ride_session_id=uuid4(),
                    raw_transcript="x",
                    normalized_query="x",
                    confidence=Decimal("0.5"),
                )
            )
            await db.flush()


async def test_updated_at_changes_on_orm_update(db):
    old = datetime(2000, 1, 1, tzinfo=UTC)
    item = User(name="Before", updated_at=old)
    db.add(item)
    await db.flush()
    item.name = "After"
    await db.flush()
    await db.refresh(item)
    assert item.updated_at > old and item.updated_at.tzinfo is not None


async def test_metadata_matches_migrated_schema(db):
    from alembic.autogenerate import compare_metadata
    from alembic.migration import MigrationContext

    from app.db.base import Base

    connection = await db.connection()

    def check(sync_connection):
        context = MigrationContext.configure(
            sync_connection,
            opts={
                "compare_type": True,
                "compare_server_default": True,
            },
        )
        assert compare_metadata(context, Base.metadata) == []
        inspector = inspect(sync_connection)
        assert len(inspector.get_enums()) == 9
        for table in Base.metadata.sorted_tables:
            uniques = {
                tuple(item["column_names"])
                for item in inspector.get_unique_constraints(table.name)
            }
            indexes = inspector.get_indexes(table.name)
            # A UNIQUE constraint already owns an index; don't add a second one.
            assert all(
                tuple(item["column_names"]) not in uniques
                for item in indexes
                if not item.get("duplicates_constraint")
            )

    await connection.run_sync(check)

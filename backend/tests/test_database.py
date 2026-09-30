from datetime import UTC, datetime
from decimal import Decimal
from uuid import UUID, uuid4

import pytest
from sqlalchemy import delete, inspect, select, text, update
from sqlalchemy.exc import DBAPIError, IntegrityError
from sqlalchemy.orm import selectinload

from app.models import (
    RideEvent,
    RideRequest,
    RideSession,
    SavedPlace,
    UberCredential,
    User,
)
from app.models.enums import RideEventSource, RideEventType, RideSessionStatus

pytestmark = [pytest.mark.asyncio, pytest.mark.postgres]


def place(user, **kwargs):
    return SavedPlace(
        user=user,
        label="Home",
        address_text="Hyderabad",
        latitude=Decimal("17.4140001"),
        longitude=Decimal("78.4120001"),
        **kwargs,
    )


def booking(user=None, **kwargs):
    return RideSession(
        user=user or User(name="Rider"),
        pickup_latitude=Decimal("17.4"),
        pickup_longitude=Decimal("78.4"),
        **kwargs,
    )


def request(ride, **kwargs):
    return RideRequest(
        ride_session=ride, idempotency_key=str(uuid4()), product_id="uber-go", **kwargs
    )


def event(ride, **kwargs):
    return RideEvent(
        ride_session=ride,
        event_type=RideEventType.SESSION_STARTED,
        source=RideEventSource.RIDE_SAATHI,
        **kwargs,
    )


async def test_user_required_fields_defaults_and_nullable_contacts(db):
    users = [User(name="A"), User(name="B")]
    db.add_all(users)
    await db.flush()
    for user in users:
        assert isinstance(user.id, UUID)
        assert user.phone is None and user.email is None
        assert user.status == "active" and user.preferred_language == "en"
        assert user.onboarding_completed is False
        assert user.created_at.tzinfo is not None
        assert user.updated_at.tzinfo is not None
    with pytest.raises(IntegrityError):
        async with db.begin_nested():
            db.add(User())
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


async def test_saved_place_relationship_and_label_uniqueness(db):
    user = User(name="Rider")
    home = place(user)
    db.add(home)
    await db.flush()
    loaded = await db.scalar(
        select(User).where(User.id == user.id).options(selectinload(User.saved_places))
    )
    assert loaded.saved_places == [home]
    assert home.user_id == user.id and home.place_type == "custom"
    assert home.latitude == Decimal("17.4140001")
    with pytest.raises(IntegrityError):
        async with db.begin_nested():
            db.add(place(user))
            await db.flush()
    db.add(place(User(name="Another rider")))  # Same label is fine for another user.
    await db.flush()


async def test_one_uber_credential_per_user_and_json_default(db):
    user = User(name="Rider")
    credential = UberCredential(
        user=user, access_token_ciphertext="test-only-ciphertext"
    )
    db.add(credential)
    await db.flush()
    assert credential.scopes == [] and credential.connected_at.tzinfo is not None
    assert credential.user_id == user.id
    loaded = await db.scalar(
        select(User)
        .where(User.id == user.id)
        .options(selectinload(User.uber_credential))
    )
    assert loaded.uber_credential is credential
    with pytest.raises(IntegrityError):
        async with db.begin_nested():
            db.add(
                UberCredential(
                    user_id=user.id, access_token_ciphertext="another-test-ciphertext"
                )
            )
            await db.flush()


async def test_session_before_destination_resolution(db):
    ride = booking()
    db.add(ride)
    await db.flush()
    assert ride.status == RideSessionStatus.STARTED
    assert ride.destination_saved_place_id is None
    assert ride.destination_source is None and ride.destination_latitude is None
    assert ride.destination_longitude is None and ride.selected_product_id is None
    assert ride.started_at.tzinfo is not None


async def test_multiple_requests_per_session_and_decimal_money(db):
    ride = booking()
    first = request(ride, estimated_fare=Decimal("123.4567"))
    second = request(ride)
    db.add_all([first, second])
    await db.commit()
    await db.refresh(first)
    loaded = await db.scalar(
        select(RideSession)
        .where(RideSession.id == ride.id)
        .options(selectinload(RideSession.ride_requests))
    )
    assert {item.id for item in loaded.ride_requests} == {first.id, second.id}
    assert first.ride_session_id == ride.id
    assert first.estimated_fare == Decimal("123.4567")
    assert first.currency == "INR" and first.provider == "uber"
    assert first.status == "pending"


@pytest.mark.parametrize(
    "field,value",
    [("idempotency_key", "booking-key"), ("provider_request_id", "provider-key")],
)
async def test_ride_request_unique_keys(db, field, value):
    ride = booking()
    first = request(ride)
    setattr(first, field, value)
    db.add(first)
    await db.flush()
    with pytest.raises(IntegrityError):
        async with db.begin_nested():
            duplicate = request(ride)
            setattr(duplicate, field, value)
            db.add(duplicate)
            await db.flush()


async def test_event_relationships_json_and_nullable_provider_ids(db):
    ride = booking()
    attempt = request(ride)
    events = [
        event(ride),
        event(ride, ride_request=attempt, event_data={"product_id": "uber-go"}),
    ]
    db.add_all(events)
    await db.flush()
    assert all(item.provider_event_id is None for item in events)
    assert events[0].event_data == {} and events[0].ride_request_id is None
    await db.refresh(events[1])
    assert events[1].event_data == {"product_id": "uber-go"}
    loaded = await db.scalar(
        select(RideRequest)
        .where(RideRequest.id == attempt.id)
        .options(selectinload(RideRequest.ride_events))
    )
    assert loaded.ride_events == [events[1]]
    assert (
        events[1].ride_session_id == ride.id and events[1].ride_request_id == attempt.id
    )
    assert events[1].occurred_at.tzinfo is not None


async def test_provider_event_id_deduplicates_webhooks(db):
    ride = booking()
    db.add(event(ride, provider_event_id="webhook-1"))
    await db.flush()
    with pytest.raises(IntegrityError):
        async with db.begin_nested():
            db.add(event(ride, provider_event_id="webhook-1"))
            await db.flush()


@pytest.mark.parametrize(
    "enum_name",
    [
        "language_code",
        "user_status",
        "saved_place_type",
        "destination_source",
        "ride_session_status",
        "ride_request_status",
        "ride_event_type",
        "ride_event_source",
    ],
)
async def test_postgres_rejects_invalid_enum_values(db, enum_name):
    # Bypass Python's enum validation and test the actual native PostgreSQL type.
    with pytest.raises(DBAPIError):
        async with db.begin_nested():
            await db.execute(text(f"SELECT CAST('not-a-real-value' AS {enum_name})"))


@pytest.mark.parametrize("loaded_relationship", [False, True])
async def test_deleting_saved_place_preserves_snapshot_and_history(
    db, loaded_relationship
):
    user = User(name="Rider")
    home = place(user)
    ride = booking(
        user,
        destination_saved_place=home,
        destination_name="Home snapshot",
        destination_address_text="Original address",
        destination_latitude=home.latitude,
        destination_longitude=home.longitude,
        destination_source="saved_place",
    )
    attempt = request(ride)
    audit = event(ride, ride_request=attempt)
    db.add(audit)
    await db.flush()
    if loaded_relationship:
        await db.refresh(home, ["ride_sessions"])
    await db.delete(home)
    await db.flush()
    await db.refresh(ride)
    assert ride.destination_saved_place_id is None
    assert ride.destination_name == "Home snapshot"
    assert ride.destination_address_text == "Original address"
    assert ride.destination_latitude == Decimal("17.4140001")
    assert await db.get(RideRequest, attempt.id) is attempt
    assert await db.get(RideEvent, audit.id) is audit


async def test_soft_delete_and_credential_revocation_preserve_history(db):
    user = User(name="Rider")
    home = place(user)
    ride = booking(user, destination_saved_place=home)
    credential = UberCredential(user=user, access_token_ciphertext="test-ciphertext")
    db.add_all([ride, credential])
    await db.flush()
    home.is_active = False
    home.deleted_at = datetime.now(UTC)
    credential.revoked_at = datetime.now(UTC)
    await db.flush()
    await db.refresh(ride)
    assert ride.destination_saved_place_id == home.id
    assert await db.get(RideSession, ride.id) is ride
    # Required uniqueness applies even to soft-deleted labels; restore/rename.
    with pytest.raises(IntegrityError):
        async with db.begin_nested():
            db.add(place(user))
            await db.flush()


@pytest.mark.parametrize("target", [User, RideSession, RideRequest])
async def test_historical_parents_cannot_cascade_delete(db, target):
    ride = booking()
    attempt = request(ride)
    db.add(event(ride, ride_request=attempt))
    await db.flush()
    with pytest.raises(IntegrityError):
        async with db.begin_nested():
            await db.execute(delete(target))


@pytest.mark.parametrize(
    "operation", ["orm_update", "orm_delete", "bulk_update", "bulk_delete", "truncate"]
)
async def test_events_are_append_only_in_postgres(db, operation):
    audit = event(booking())
    db.add(audit)
    await db.flush()
    with pytest.raises(IntegrityError, match="append-only"):
        async with db.begin_nested():
            if operation == "orm_update":
                audit.event_data = {"changed": True}
                await db.flush()
            elif operation == "orm_delete":
                await db.delete(audit)
                await db.flush()
            elif operation == "bulk_update":
                await db.execute(update(RideEvent).values(event_data={"changed": True}))
            elif operation == "bulk_delete":
                await db.execute(delete(RideEvent))
            else:
                await db.execute(text("TRUNCATE ride_events"))


async def test_foreign_keys_and_coordinate_constraints(db):
    with pytest.raises(IntegrityError):
        async with db.begin_nested():
            db.add(RideSession(user_id=uuid4(), pickup_latitude=0, pickup_longitude=0))
            await db.flush()
    with pytest.raises(IntegrityError):
        async with db.begin_nested():
            db.add(
                booking(pickup_address_text="test", destination_latitude=Decimal(91))
            )
            await db.flush()


async def test_updated_at_changes_on_orm_update(db):
    old = datetime(2000, 1, 1, tzinfo=UTC)
    user = User(name="Before", updated_at=old)
    db.add(user)
    await db.flush()
    user.name = "After"
    await db.flush()
    await db.refresh(user)
    assert user.updated_at > old and user.updated_at.tzinfo is not None


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
        assert len(inspector.get_enums()) == 8
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

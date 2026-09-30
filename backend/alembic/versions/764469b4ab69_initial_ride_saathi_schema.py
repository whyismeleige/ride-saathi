"""initial_ride_saathi_schema

Revision ID: 764469b4ab69
Revises:
Create Date: 2026-09-30 15:11:16.784495
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision: str = "764469b4ab69"
down_revision: str | None = None
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


# Frozen migration types: never import live application enums here.
language_code = postgresql.ENUM(
    "en", "hi", "te", name="language_code", create_type=False
)
user_status = postgresql.ENUM(
    "active", "disabled", "deleted", name="user_status", create_type=False
)
saved_place_type = postgresql.ENUM(
    "home", "custom", name="saved_place_type", create_type=False
)
ride_session_status = postgresql.ENUM(
    "started",
    "resolving_destination",
    "destination_confirmed",
    "selecting_product",
    "ready_to_book",
    "requesting_ride",
    "finding_driver",
    "driver_assigned",
    "driver_arriving",
    "driver_arrived",
    "ride_in_progress",
    "completed",
    "cancelled",
    "failed",
    "abandoned",
    name="ride_session_status",
    create_type=False,
)
destination_source = postgresql.ENUM(
    "saved_place", "maps_search", "manual", name="destination_source", create_type=False
)
ride_request_status = postgresql.ENUM(
    "pending",
    "finding_driver",
    "driver_assigned",
    "driver_arriving",
    "driver_arrived",
    "in_progress",
    "completed",
    "cancelled",
    "failed",
    name="ride_request_status",
    create_type=False,
)
ride_event_type = postgresql.ENUM(
    "session_started",
    "destination_resolved",
    "destination_changed",
    "product_selected",
    "product_changed",
    "ride_requested",
    "driver_searching",
    "driver_assigned",
    "driver_arriving",
    "driver_arrived",
    "ride_started",
    "ride_completed",
    "cancellation_requested",
    "ride_cancelled",
    "ride_failed",
    "session_abandoned",
    name="ride_event_type",
    create_type=False,
)
ride_event_source = postgresql.ENUM(
    "user", "ride_saathi", "uber", "system", name="ride_event_source", create_type=False
)
ENUMS = (
    language_code,
    user_status,
    saved_place_type,
    ride_session_status,
    destination_source,
    ride_request_status,
    ride_event_type,
    ride_event_source,
)


def upgrade() -> None:
    for enum in ENUMS:
        enum.create(op.get_bind(), checkfirst=False)

    op.create_table(
        "users",
        sa.Column("name", sa.Text(), nullable=False),
        sa.Column("phone", sa.Text(), nullable=True),
        sa.Column("email", sa.Text(), nullable=True),
        sa.Column(
            "preferred_language", language_code, server_default="en", nullable=False
        ),
        sa.Column("status", user_status, server_default="active", nullable=False),
        sa.Column(
            "onboarding_completed",
            sa.Boolean(),
            server_default=sa.text("false"),
            nullable=False,
        ),
        sa.Column("last_active_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_users")),
        sa.UniqueConstraint("email", name=op.f("uq_users_email")),
        sa.UniqueConstraint("phone", name=op.f("uq_users_phone")),
    )
    op.create_index(
        op.f("ix_users_onboarding_completed"),
        "users",
        ["onboarding_completed"],
        unique=False,
    )
    op.create_index(op.f("ix_users_status"), "users", ["status"], unique=False)
    op.create_table(
        "saved_places",
        sa.Column("user_id", sa.Uuid(), nullable=False),
        sa.Column("label", sa.Text(), nullable=False),
        sa.Column(
            "place_type", saved_place_type, server_default="custom", nullable=False
        ),
        sa.Column("address_text", sa.Text(), nullable=False),
        sa.Column("latitude", sa.Numeric(precision=10, scale=7), nullable=False),
        sa.Column("longitude", sa.Numeric(precision=10, scale=7), nullable=False),
        sa.Column("provider_place_id", sa.Text(), nullable=True),
        sa.Column(
            "is_active", sa.Boolean(), server_default=sa.text("true"), nullable=False
        ),
        sa.Column("deleted_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.CheckConstraint(
            "latitude BETWEEN -90 AND 90", name=op.f("ck_saved_places_latitude_range")
        ),
        sa.CheckConstraint(
            "longitude BETWEEN -180 AND 180",
            name=op.f("ck_saved_places_longitude_range"),
        ),
        sa.ForeignKeyConstraint(
            ["user_id"],
            ["users.id"],
            name=op.f("fk_saved_places_user_id_users"),
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_saved_places")),
        sa.UniqueConstraint("user_id", "label", name="uq_saved_places_user_id_label"),
    )
    op.create_index(
        op.f("ix_saved_places_is_active"), "saved_places", ["is_active"], unique=False
    )
    op.create_index(
        op.f("ix_saved_places_place_type"), "saved_places", ["place_type"], unique=False
    )
    op.create_index(
        op.f("ix_saved_places_provider_place_id"),
        "saved_places",
        ["provider_place_id"],
        unique=False,
    )
    op.create_index(
        op.f("ix_saved_places_user_id"), "saved_places", ["user_id"], unique=False
    )
    op.create_table(
        "uber_credentials",
        sa.Column("user_id", sa.Uuid(), nullable=False),
        sa.Column("uber_user_id", sa.Text(), nullable=True),
        sa.Column("access_token_ciphertext", sa.Text(), nullable=False),
        sa.Column("refresh_token_ciphertext", sa.Text(), nullable=True),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column(
            "scopes",
            postgresql.JSONB(astext_type=sa.Text()),
            server_default=sa.text("'[]'::jsonb"),
            nullable=False,
        ),
        sa.Column(
            "connected_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.Column("last_refreshed_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("revoked_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.ForeignKeyConstraint(
            ["user_id"],
            ["users.id"],
            name=op.f("fk_uber_credentials_user_id_users"),
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_uber_credentials")),
        sa.UniqueConstraint("user_id", name=op.f("uq_uber_credentials_user_id")),
    )
    op.create_index(
        op.f("ix_uber_credentials_expires_at"),
        "uber_credentials",
        ["expires_at"],
        unique=False,
    )
    op.create_index(
        op.f("ix_uber_credentials_revoked_at"),
        "uber_credentials",
        ["revoked_at"],
        unique=False,
    )
    op.create_index(
        op.f("ix_uber_credentials_uber_user_id"),
        "uber_credentials",
        ["uber_user_id"],
        unique=False,
    )
    op.create_table(
        "ride_sessions",
        sa.Column("user_id", sa.Uuid(), nullable=False),
        sa.Column(
            "status", ride_session_status, server_default="started", nullable=False
        ),
        sa.Column("pickup_address_text", sa.Text(), nullable=True),
        sa.Column("pickup_latitude", sa.Numeric(precision=10, scale=7), nullable=False),
        sa.Column(
            "pickup_longitude", sa.Numeric(precision=10, scale=7), nullable=False
        ),
        sa.Column("destination_saved_place_id", sa.Uuid(), nullable=True),
        sa.Column("destination_source", destination_source, nullable=True),
        sa.Column("destination_name", sa.Text(), nullable=True),
        sa.Column("destination_address_text", sa.Text(), nullable=True),
        sa.Column(
            "destination_latitude", sa.Numeric(precision=10, scale=7), nullable=True
        ),
        sa.Column(
            "destination_longitude", sa.Numeric(precision=10, scale=7), nullable=True
        ),
        sa.Column("selected_product_id", sa.Text(), nullable=True),
        sa.Column("selected_product_name", sa.Text(), nullable=True),
        sa.Column(
            "started_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.Column("ended_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.CheckConstraint(
            "destination_latitude BETWEEN -90 AND 90",
            name=op.f("ck_ride_sessions_destination_latitude_range"),
        ),
        sa.CheckConstraint(
            "destination_longitude BETWEEN -180 AND 180",
            name=op.f("ck_ride_sessions_destination_longitude_range"),
        ),
        sa.CheckConstraint(
            "pickup_latitude BETWEEN -90 AND 90",
            name=op.f("ck_ride_sessions_pickup_latitude_range"),
        ),
        sa.CheckConstraint(
            "pickup_longitude BETWEEN -180 AND 180",
            name=op.f("ck_ride_sessions_pickup_longitude_range"),
        ),
        sa.ForeignKeyConstraint(
            ["destination_saved_place_id"],
            ["saved_places.id"],
            name=op.f("fk_ride_sessions_destination_saved_place_id_saved_places"),
            ondelete="SET NULL",
        ),
        sa.ForeignKeyConstraint(
            ["user_id"],
            ["users.id"],
            name=op.f("fk_ride_sessions_user_id_users"),
            ondelete="RESTRICT",
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_ride_sessions")),
    )
    op.create_index(
        op.f("ix_ride_sessions_destination_saved_place_id"),
        "ride_sessions",
        ["destination_saved_place_id"],
        unique=False,
    )
    op.create_index(
        op.f("ix_ride_sessions_ended_at"), "ride_sessions", ["ended_at"], unique=False
    )
    op.create_index(
        op.f("ix_ride_sessions_started_at"),
        "ride_sessions",
        ["started_at"],
        unique=False,
    )
    op.create_index(
        op.f("ix_ride_sessions_status"), "ride_sessions", ["status"], unique=False
    )
    op.create_index(
        op.f("ix_ride_sessions_user_id"), "ride_sessions", ["user_id"], unique=False
    )
    op.create_index(
        "ix_ride_sessions_user_id_started_at",
        "ride_sessions",
        ["user_id", "started_at"],
        unique=False,
    )
    op.create_table(
        "ride_requests",
        sa.Column("ride_session_id", sa.Uuid(), nullable=False),
        sa.Column("provider", sa.Text(), server_default="uber", nullable=False),
        sa.Column("provider_request_id", sa.Text(), nullable=True),
        sa.Column("idempotency_key", sa.Text(), nullable=False),
        sa.Column("product_id", sa.Text(), nullable=False),
        sa.Column("product_name", sa.Text(), nullable=True),
        sa.Column(
            "status", ride_request_status, server_default="pending", nullable=False
        ),
        sa.Column("estimated_fare", sa.Numeric(precision=14, scale=4), nullable=True),
        sa.Column("final_fare", sa.Numeric(precision=14, scale=4), nullable=True),
        sa.Column("currency", sa.Text(), server_default="INR", nullable=False),
        sa.Column("cancellation_fee", sa.Numeric(precision=14, scale=4), nullable=True),
        sa.Column("estimated_pickup_minutes", sa.Integer(), nullable=True),
        sa.Column("driver_name", sa.Text(), nullable=True),
        sa.Column("vehicle_name", sa.Text(), nullable=True),
        sa.Column("vehicle_number", sa.Text(), nullable=True),
        sa.Column(
            "requested_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.Column("driver_assigned_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("driver_arrived_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("ride_started_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("completed_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("cancelled_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.ForeignKeyConstraint(
            ["ride_session_id"],
            ["ride_sessions.id"],
            name=op.f("fk_ride_requests_ride_session_id_ride_sessions"),
            ondelete="RESTRICT",
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_ride_requests")),
        sa.UniqueConstraint(
            "idempotency_key", name=op.f("uq_ride_requests_idempotency_key")
        ),
        sa.UniqueConstraint(
            "provider_request_id", name=op.f("uq_ride_requests_provider_request_id")
        ),
    )
    op.create_index(
        op.f("ix_ride_requests_cancelled_at"),
        "ride_requests",
        ["cancelled_at"],
        unique=False,
    )
    op.create_index(
        op.f("ix_ride_requests_completed_at"),
        "ride_requests",
        ["completed_at"],
        unique=False,
    )
    op.create_index(
        op.f("ix_ride_requests_requested_at"),
        "ride_requests",
        ["requested_at"],
        unique=False,
    )
    op.create_index(
        op.f("ix_ride_requests_ride_session_id"),
        "ride_requests",
        ["ride_session_id"],
        unique=False,
    )
    op.create_index(
        op.f("ix_ride_requests_status"), "ride_requests", ["status"], unique=False
    )
    op.create_table(
        "ride_events",
        sa.Column("ride_session_id", sa.Uuid(), nullable=False),
        sa.Column("ride_request_id", sa.Uuid(), nullable=True),
        sa.Column("event_type", ride_event_type, nullable=False),
        sa.Column("source", ride_event_source, nullable=False),
        sa.Column(
            "event_data",
            postgresql.JSONB(astext_type=sa.Text()),
            server_default=sa.text("'{}'::jsonb"),
            nullable=False,
        ),
        sa.Column("provider_event_id", sa.Text(), nullable=True),
        sa.Column(
            "occurred_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.ForeignKeyConstraint(
            ["ride_request_id"],
            ["ride_requests.id"],
            name=op.f("fk_ride_events_ride_request_id_ride_requests"),
            ondelete="RESTRICT",
        ),
        sa.ForeignKeyConstraint(
            ["ride_session_id"],
            ["ride_sessions.id"],
            name=op.f("fk_ride_events_ride_session_id_ride_sessions"),
            ondelete="RESTRICT",
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_ride_events")),
        sa.UniqueConstraint(
            "provider_event_id", name=op.f("uq_ride_events_provider_event_id")
        ),
    )
    op.create_index(
        op.f("ix_ride_events_event_type"), "ride_events", ["event_type"], unique=False
    )
    op.create_index(
        op.f("ix_ride_events_occurred_at"), "ride_events", ["occurred_at"], unique=False
    )
    op.create_index(
        op.f("ix_ride_events_ride_request_id"),
        "ride_events",
        ["ride_request_id"],
        unique=False,
    )
    op.create_index(
        op.f("ix_ride_events_ride_session_id"),
        "ride_events",
        ["ride_session_id"],
        unique=False,
    )
    op.create_index(
        "ix_ride_events_ride_session_id_occurred_at",
        "ride_events",
        ["ride_session_id", "occurred_at"],
        unique=False,
    )
    op.create_index(
        op.f("ix_ride_events_source"), "ride_events", ["source"], unique=False
    )
    # Enforce the audit boundary for ORM, bulk SQL, and accidental TRUNCATE.
    op.execute("""
        CREATE FUNCTION reject_ride_event_mutation() RETURNS trigger
        LANGUAGE plpgsql AS $$
        BEGIN
            RAISE EXCEPTION 'ride_events is append-only'
                USING ERRCODE = '23514';
        END;
        $$
    """)
    op.execute("""
        CREATE TRIGGER ride_events_append_only
        BEFORE UPDATE OR DELETE OR TRUNCATE ON ride_events
        FOR EACH STATEMENT EXECUTE FUNCTION reject_ride_event_mutation()
    """)


def downgrade() -> None:
    op.execute("DROP TRIGGER ride_events_append_only ON ride_events")
    op.execute("DROP FUNCTION reject_ride_event_mutation()")
    op.drop_table("ride_events")
    op.drop_table("ride_requests")
    op.drop_table("ride_sessions")
    op.drop_table("uber_credentials")
    op.drop_table("saved_places")
    op.drop_table("users")
    # No CASCADE: refuse to remove types if another object still depends on them.
    for enum in reversed(ENUMS):
        enum.drop(op.get_bind(), checkfirst=False)

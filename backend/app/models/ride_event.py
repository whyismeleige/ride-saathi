from __future__ import annotations

from datetime import datetime
from typing import TYPE_CHECKING, Any
from uuid import UUID

from sqlalchemy import ForeignKey, Index, Text, func, text
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base, CreatedAt, UUIDPrimaryKey
from app.models.enums import RideEventSource, RideEventType, pg_enum

if TYPE_CHECKING:
    from app.models.ride_request import RideRequest
    from app.models.ride_session import RideSession


class RideEvent(UUIDPrimaryKey, CreatedAt, Base):
    """Append-only audit record; the migration installs a database write guard."""

    __tablename__ = "ride_events"
    __table_args__ = (
        Index(
            "ix_ride_events_ride_session_id_occurred_at",
            "ride_session_id",
            "occurred_at",
        ),
    )

    ride_session_id: Mapped[UUID] = mapped_column(
        ForeignKey("ride_sessions.id", ondelete="RESTRICT"), index=True
    )
    ride_request_id: Mapped[UUID | None] = mapped_column(
        ForeignKey("ride_requests.id", ondelete="RESTRICT"), index=True
    )
    event_type: Mapped[RideEventType] = mapped_column(
        pg_enum(RideEventType, "ride_event_type"), index=True
    )
    source: Mapped[RideEventSource] = mapped_column(
        pg_enum(RideEventSource, "ride_event_source"), index=True
    )
    event_data: Mapped[dict[str, Any]] = mapped_column(
        JSONB, default=dict, server_default=text("'{}'::jsonb")
    )
    provider_event_id: Mapped[str | None] = mapped_column(Text, unique=True)
    occurred_at: Mapped[datetime] = mapped_column(server_default=func.now(), index=True)

    ride_session: Mapped[RideSession] = relationship(
        back_populates="ride_events", lazy="raise"
    )
    ride_request: Mapped[RideRequest | None] = relationship(
        back_populates="ride_events", lazy="raise"
    )

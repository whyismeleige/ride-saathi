from __future__ import annotations

from typing import TYPE_CHECKING
from uuid import UUID

from sqlalchemy import ForeignKey, Index
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base, CreatedAt, UUIDPrimaryKey
from app.models.enums import SessionEventType, pg_enum

if TYPE_CHECKING:
    from app.models.ride_session import RideSession

class SessionEvent(UUIDPrimaryKey, CreatedAt, Base):
    """Append-only analytics without transcript/location payloads."""
    __tablename__ = "session_events"
    __table_args__ = (Index("ix_session_events_session_created", "ride_session_id", "created_at"),)
    ride_session_id: Mapped[UUID] = mapped_column(ForeignKey("ride_sessions.id", ondelete="RESTRICT"))
    event_type: Mapped[SessionEventType] = mapped_column(pg_enum(SessionEventType, "session_event_type"), index=True)
    ride_session: Mapped[RideSession] = relationship(lazy="raise")

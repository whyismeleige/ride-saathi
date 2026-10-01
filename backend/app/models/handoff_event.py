from __future__ import annotations

from datetime import datetime
from typing import TYPE_CHECKING
from uuid import UUID

from sqlalchemy import ForeignKey, Text, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base, CreatedAt, UUIDPrimaryKey
from app.models.enums import HandoffStatus, pg_enum

if TYPE_CHECKING:
    from app.models.ride_session import RideSession

class HandoffEvent(UUIDPrimaryKey, CreatedAt, Base):
    __tablename__ = "handoff_events"
    ride_session_id: Mapped[UUID] = mapped_column(ForeignKey("ride_sessions.id", ondelete="RESTRICT"), unique=True)
    provider: Mapped[str] = mapped_column(Text)
    status: Mapped[HandoffStatus] = mapped_column(pg_enum(HandoffStatus, "handoff_status"), server_default="created")
    handoff_created_at: Mapped[datetime] = mapped_column(server_default=func.now())
    handoff_opened_at: Mapped[datetime | None]
    failure_reason: Mapped[str | None] = mapped_column(Text)
    ride_session: Mapped[RideSession] = relationship(lazy="raise")

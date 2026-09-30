from __future__ import annotations
from datetime import datetime
from decimal import Decimal
from uuid import UUID
from sqlalchemy import CheckConstraint, ForeignKey, Index, Numeric, Text, UniqueConstraint, func, text
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column, relationship
from app.db.base import Base, Timestamps, CreatedAt, UUIDPrimaryKey
from app.models.enums import pg_enum
from app.models.enums import HandoffStatus

class HandoffEvent(UUIDPrimaryKey, CreatedAt, Base):
    __tablename__ = "handoff_events"
    ride_session_id: Mapped[UUID] = mapped_column(ForeignKey("ride_sessions.id", ondelete="RESTRICT"), unique=True)
    provider: Mapped[str] = mapped_column(Text)
    status: Mapped[HandoffStatus] = mapped_column(pg_enum(HandoffStatus, "handoff_status"), server_default="created")
    handoff_created_at: Mapped[datetime] = mapped_column(server_default=func.now())
    handoff_opened_at: Mapped[datetime | None]
    failure_reason: Mapped[str | None] = mapped_column(Text)

from __future__ import annotations
from datetime import datetime
from decimal import Decimal
from uuid import UUID
from sqlalchemy import CheckConstraint, ForeignKey, Index, Numeric, Text, UniqueConstraint, func, text
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column, relationship
from app.db.base import Base, Timestamps, CreatedAt, UUIDPrimaryKey
from app.models.enums import pg_enum
from app.models.enums import SessionEventType

class SessionEvent(UUIDPrimaryKey, CreatedAt, Base):
    """Append-only analytics without transcript/location payloads."""
    __tablename__ = "session_events"
    __table_args__ = (Index("ix_session_events_session_created", "ride_session_id", "created_at"),)
    ride_session_id: Mapped[UUID] = mapped_column(ForeignKey("ride_sessions.id", ondelete="RESTRICT"))
    event_type: Mapped[SessionEventType] = mapped_column(pg_enum(SessionEventType, "session_event_type"), index=True)

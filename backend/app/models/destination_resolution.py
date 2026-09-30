from __future__ import annotations
from datetime import datetime
from decimal import Decimal
from uuid import UUID
from sqlalchemy import CheckConstraint, ForeignKey, Index, Numeric, Text, UniqueConstraint, func, text
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column, relationship
from app.db.base import Base, Timestamps, CreatedAt, UUIDPrimaryKey
from app.models.enums import pg_enum
from app.models.enums import DestinationSource

class DestinationResolution(UUIDPrimaryKey, CreatedAt, Base):
    __tablename__ = "destination_resolutions"
    __table_args__ = (
        CheckConstraint("confidence BETWEEN 0 AND 1", name="confidence_range"),
        CheckConstraint("selected_latitude BETWEEN -90 AND 90", name="latitude_range"),
        CheckConstraint("selected_longitude BETWEEN -180 AND 180", name="longitude_range"),
        CheckConstraint("(selected_latitude IS NULL) = (selected_longitude IS NULL)", name="coordinate_pair"),
        CheckConstraint("jsonb_typeof(candidates) = 'array' AND jsonb_array_length(candidates) <= 5", name="bounded_candidates"),
    )
    user_id: Mapped[UUID] = mapped_column(ForeignKey("users.id", ondelete="RESTRICT"), index=True)
    ride_session_id: Mapped[UUID] = mapped_column(ForeignKey("ride_sessions.id", ondelete="RESTRICT"), index=True)
    raw_transcript: Mapped[str] = mapped_column(Text)
    normalized_query: Mapped[str] = mapped_column(Text, index=True)
    selected_saved_place_id: Mapped[UUID | None] = mapped_column(ForeignKey("saved_places.id", ondelete="SET NULL"))
    selected_place_name: Mapped[str | None] = mapped_column(Text)
    selected_address_text: Mapped[str | None] = mapped_column(Text)
    selected_latitude: Mapped[Decimal | None] = mapped_column(Numeric(10, 7))
    selected_longitude: Mapped[Decimal | None] = mapped_column(Numeric(10, 7))
    confidence: Mapped[Decimal] = mapped_column(Numeric(5, 4))
    resolution_method: Mapped[DestinationSource | None] = mapped_column(pg_enum(DestinationSource, "destination_source"))
    was_confirmed: Mapped[bool] = mapped_column(server_default=text("false"))
    was_corrected: Mapped[bool] = mapped_column(server_default=text("false"))
    candidates: Mapped[list[dict]] = mapped_column(JSONB, default=list, server_default=text("'[]'::jsonb"))

class DestinationCorrection(UUIDPrimaryKey, CreatedAt, Base):
    __tablename__ = "destination_corrections"
    __table_args__ = (Index("ix_destination_corrections_user_query", "user_id", "normalized_query"),)
    user_id: Mapped[UUID] = mapped_column(ForeignKey("users.id", ondelete="RESTRICT"))
    resolution_id: Mapped[UUID] = mapped_column(ForeignKey("destination_resolutions.id", ondelete="RESTRICT"), unique=True)
    raw_query: Mapped[str] = mapped_column(Text)
    normalized_query: Mapped[str] = mapped_column(Text)
    rejected_candidate: Mapped[dict | None] = mapped_column(JSONB)
    accepted_candidate: Mapped[dict] = mapped_column(JSONB)

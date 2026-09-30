from __future__ import annotations

from datetime import datetime
from decimal import Decimal
from typing import TYPE_CHECKING
from uuid import UUID

from sqlalchemy import CheckConstraint, ForeignKey, Index, Numeric, Text, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base, Timestamps, UUIDPrimaryKey
from app.models.enums import DestinationSource, RideSessionStatus, pg_enum

if TYPE_CHECKING:
    from app.models.saved_place import SavedPlace
    from app.models.user import User

class RideSession(UUIDPrimaryKey, Timestamps, Base):
    """One intent-to-handoff attempt, never a provider booking."""
    __tablename__ = "ride_sessions"
    __table_args__ = (
        Index("ix_ride_sessions_user_id_started_at", "user_id", "started_at"),
        CheckConstraint("pickup_latitude BETWEEN -90 AND 90", name="pickup_latitude_range"),
        CheckConstraint("pickup_longitude BETWEEN -180 AND 180", name="pickup_longitude_range"),
        CheckConstraint("destination_latitude BETWEEN -90 AND 90", name="destination_latitude_range"),
        CheckConstraint("destination_longitude BETWEEN -180 AND 180", name="destination_longitude_range"),
        CheckConstraint("(pickup_latitude IS NULL) = (pickup_longitude IS NULL)", name="pickup_pair"),
        CheckConstraint("(destination_latitude IS NULL) = (destination_longitude IS NULL)", name="destination_pair"),
        CheckConstraint("resolution_confidence BETWEEN 0 AND 1", name="confidence_range"),
    )
    user_id: Mapped[UUID] = mapped_column(ForeignKey("users.id", ondelete="RESTRICT"), index=True)
    raw_utterance: Mapped[str | None] = mapped_column(Text)
    status: Mapped[RideSessionStatus] = mapped_column(pg_enum(RideSessionStatus, "ride_session_status"), server_default="started", index=True)
    pickup_address_text: Mapped[str | None] = mapped_column(Text)
    pickup_latitude: Mapped[Decimal | None] = mapped_column(Numeric(10, 7))
    pickup_longitude: Mapped[Decimal | None] = mapped_column(Numeric(10, 7))
    destination_saved_place_id: Mapped[UUID | None] = mapped_column(ForeignKey("saved_places.id", ondelete="SET NULL"), index=True)
    destination_source: Mapped[DestinationSource | None] = mapped_column(pg_enum(DestinationSource, "destination_source"))
    destination_name: Mapped[str | None] = mapped_column(Text)
    destination_address_text: Mapped[str | None] = mapped_column(Text)
    destination_latitude: Mapped[Decimal | None] = mapped_column(Numeric(10, 7))
    destination_longitude: Mapped[Decimal | None] = mapped_column(Numeric(10, 7))
    resolution_confidence: Mapped[Decimal | None] = mapped_column(Numeric(5, 4))
    preferred_provider: Mapped[str | None] = mapped_column(Text)
    preferred_product_type: Mapped[str | None] = mapped_column(Text)
    started_at: Mapped[datetime] = mapped_column(server_default=func.now())
    resolved_at: Mapped[datetime | None]
    confirmed_at: Mapped[datetime | None]
    handoff_at: Mapped[datetime | None]
    ended_at: Mapped[datetime | None]
    user: Mapped[User] = relationship(back_populates="ride_sessions", lazy="raise")
    destination_saved_place: Mapped[SavedPlace | None] = relationship(back_populates="ride_sessions", lazy="raise")

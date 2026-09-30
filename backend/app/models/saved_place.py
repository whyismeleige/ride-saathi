from __future__ import annotations

from datetime import datetime
from decimal import Decimal
from typing import TYPE_CHECKING
from uuid import UUID

from sqlalchemy import (
    CheckConstraint,
    ForeignKey,
    Numeric,
    Text,
    UniqueConstraint,
    text,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base, Timestamps, UUIDPrimaryKey
from app.models.enums import SavedPlaceType, pg_enum

if TYPE_CHECKING:
    from app.models.ride_session import RideSession
    from app.models.user import User


class SavedPlace(UUIDPrimaryKey, Timestamps, Base):
    __tablename__ = "saved_places"
    __table_args__ = (
        UniqueConstraint("user_id", "label", name="uq_saved_places_user_id_label"),
        CheckConstraint("visit_count >= 0", name="visit_count_nonnegative"),
        CheckConstraint("latitude BETWEEN -90 AND 90", name="latitude_range"),
        CheckConstraint("longitude BETWEEN -180 AND 180", name="longitude_range"),
    )

    user_id: Mapped[UUID] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), index=True
    )
    label: Mapped[str] = mapped_column(Text)
    place_type: Mapped[SavedPlaceType] = mapped_column(
        pg_enum(SavedPlaceType, "saved_place_type"), server_default="custom", index=True
    )
    address_text: Mapped[str] = mapped_column(Text)
    latitude: Mapped[Decimal] = mapped_column(Numeric(10, 7))
    longitude: Mapped[Decimal] = mapped_column(Numeric(10, 7))
    provider_place_id: Mapped[str | None] = mapped_column(Text, index=True)
    is_active: Mapped[bool] = mapped_column(server_default=text("true"), index=True)
    visit_count: Mapped[int] = mapped_column(server_default="0")
    last_used_at: Mapped[datetime | None]
    last_confirmed_at: Mapped[datetime | None]
    deleted_at: Mapped[datetime | None]

    user: Mapped[User] = relationship(back_populates="saved_places", lazy="raise")
    ride_sessions: Mapped[list[RideSession]] = relationship(
        back_populates="destination_saved_place", lazy="raise", passive_deletes="all"
    )

from __future__ import annotations

from datetime import datetime
from typing import TYPE_CHECKING

from sqlalchemy import Text, text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base, Timestamps, UUIDPrimaryKey
from app.models.enums import LanguageCode, UserStatus, pg_enum

if TYPE_CHECKING:
    from app.models.ride_session import RideSession
    from app.models.saved_place import SavedPlace
    from app.models.uber_credential import UberCredential


class User(UUIDPrimaryKey, Timestamps, Base):
    __tablename__ = "users"

    name: Mapped[str] = mapped_column(Text)
    phone: Mapped[str | None] = mapped_column(Text, unique=True)
    email: Mapped[str | None] = mapped_column(Text, unique=True)
    preferred_language: Mapped[LanguageCode] = mapped_column(
        pg_enum(LanguageCode, "language_code"), server_default="en"
    )
    status: Mapped[UserStatus] = mapped_column(
        pg_enum(UserStatus, "user_status"), server_default="active", index=True
    )
    onboarding_completed: Mapped[bool] = mapped_column(
        server_default=text("false"), index=True
    )
    last_active_at: Mapped[datetime | None]

    saved_places: Mapped[list[SavedPlace]] = relationship(
        back_populates="user", lazy="raise", passive_deletes="all"
    )
    uber_credential: Mapped[UberCredential | None] = relationship(
        back_populates="user", lazy="raise", passive_deletes="all"
    )
    ride_sessions: Mapped[list[RideSession]] = relationship(
        back_populates="user", lazy="raise", passive_deletes="all"
    )

from __future__ import annotations

from typing import TYPE_CHECKING
from uuid import UUID

from sqlalchemy import ForeignKey, Text, text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base, Timestamps, UUIDPrimaryKey
from app.models.enums import ConfirmationMode, pg_enum

if TYPE_CHECKING:
    from app.models.user import User

class UserPreference(UUIDPrimaryKey, Timestamps, Base):
    __tablename__ = "user_preferences"
    user_id: Mapped[UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), unique=True)
    confirmation_mode: Mapped[ConfirmationMode] = mapped_column(pg_enum(ConfirmationMode, "confirmation_mode"), server_default="always")
    preferred_ride_provider: Mapped[str] = mapped_column(Text, server_default="uber")
    preferred_ride_type: Mapped[str | None] = mapped_column(Text)
    auto_confirm_high_confidence: Mapped[bool] = mapped_column(server_default=text("false"))
    user: Mapped[User] = relationship(lazy="raise")

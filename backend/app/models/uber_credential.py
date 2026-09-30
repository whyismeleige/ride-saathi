from __future__ import annotations

from datetime import datetime
from typing import TYPE_CHECKING
from uuid import UUID

from sqlalchemy import ForeignKey, Text, func, text
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base, Timestamps, UUIDPrimaryKey

if TYPE_CHECKING:
    from app.models.user import User


class UberCredential(UUIDPrimaryKey, Timestamps, Base):
    """Ciphertext only. A dedicated service must encrypt before persistence.

    Never log/serialize these fields or put tokens, OAuth state or PKCE verifiers
    in JSON event payloads. No encryption or key management is provided here.
    """

    __tablename__ = "uber_credentials"

    user_id: Mapped[UUID] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), unique=True
    )
    uber_user_id: Mapped[str | None] = mapped_column(Text, index=True)
    access_token_ciphertext: Mapped[str] = mapped_column(Text, deferred=True)
    refresh_token_ciphertext: Mapped[str | None] = mapped_column(Text, deferred=True)
    expires_at: Mapped[datetime | None] = mapped_column(index=True)
    scopes: Mapped[list[str]] = mapped_column(
        JSONB, default=list, server_default=text("'[]'::jsonb")
    )
    connected_at: Mapped[datetime] = mapped_column(server_default=func.now())
    last_refreshed_at: Mapped[datetime | None]
    revoked_at: Mapped[datetime | None] = mapped_column(index=True)

    user: Mapped[User] = relationship(back_populates="uber_credential", lazy="raise")

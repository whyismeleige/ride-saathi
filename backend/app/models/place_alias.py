from __future__ import annotations

from datetime import datetime
from typing import TYPE_CHECKING
from uuid import UUID

from sqlalchemy import CheckConstraint, ForeignKey, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base, Timestamps, UUIDPrimaryKey
from app.models.enums import AliasSource, LanguageCode, pg_enum

if TYPE_CHECKING:
    from app.models.saved_place import SavedPlace

class PlaceAlias(UUIDPrimaryKey, Timestamps, Base):
    __tablename__ = "place_aliases"
    __table_args__ = (
        UniqueConstraint("saved_place_id", "normalized_alias"),
        CheckConstraint("length(normalized_alias) > 0", name="alias_not_empty"),
        CheckConstraint("use_count >= 0", name="use_count_nonnegative"),
    )
    saved_place_id: Mapped[UUID] = mapped_column(ForeignKey("saved_places.id", ondelete="CASCADE"), index=True)
    alias: Mapped[str] = mapped_column(Text)
    normalized_alias: Mapped[str] = mapped_column(Text, index=True)
    language: Mapped[LanguageCode | None] = mapped_column(pg_enum(LanguageCode, "language_code"))
    source: Mapped[AliasSource] = mapped_column(pg_enum(AliasSource, "alias_source"), server_default="user_created")
    use_count: Mapped[int] = mapped_column(server_default="0")
    last_used_at: Mapped[datetime | None]
    saved_place: Mapped[SavedPlace] = relationship(lazy="raise")

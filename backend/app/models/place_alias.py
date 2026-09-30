from __future__ import annotations
from datetime import datetime
from decimal import Decimal
from uuid import UUID
from sqlalchemy import CheckConstraint, ForeignKey, Index, Numeric, Text, UniqueConstraint, func, text
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column, relationship
from app.db.base import Base, Timestamps, CreatedAt, UUIDPrimaryKey
from app.models.enums import pg_enum
from app.models.enums import AliasSource, LanguageCode

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

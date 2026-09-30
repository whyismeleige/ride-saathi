from __future__ import annotations

from datetime import datetime
from decimal import Decimal
from typing import TYPE_CHECKING
from uuid import UUID

from sqlalchemy import ForeignKey, Numeric, Text, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base, Timestamps, UUIDPrimaryKey
from app.models.enums import RideRequestStatus, pg_enum

if TYPE_CHECKING:
    from app.models.ride_event import RideEvent
    from app.models.ride_session import RideSession


class RideRequest(UUIDPrimaryKey, Timestamps, Base):
    """An actual provider attempt; multiple attempts can belong to a session."""

    __tablename__ = "ride_requests"

    ride_session_id: Mapped[UUID] = mapped_column(
        ForeignKey("ride_sessions.id", ondelete="RESTRICT"), index=True
    )
    provider: Mapped[str] = mapped_column(Text, server_default="uber")
    provider_request_id: Mapped[str | None] = mapped_column(Text, unique=True)
    idempotency_key: Mapped[str] = mapped_column(Text, unique=True)
    product_id: Mapped[str] = mapped_column(Text)
    product_name: Mapped[str | None] = mapped_column(Text)
    status: Mapped[RideRequestStatus] = mapped_column(
        pg_enum(RideRequestStatus, "ride_request_status"),
        server_default="pending",
        index=True,
    )
    estimated_fare: Mapped[Decimal | None] = mapped_column(Numeric(14, 4))
    final_fare: Mapped[Decimal | None] = mapped_column(Numeric(14, 4))
    currency: Mapped[str] = mapped_column(Text, server_default="INR")
    cancellation_fee: Mapped[Decimal | None] = mapped_column(Numeric(14, 4))
    estimated_pickup_minutes: Mapped[int | None]
    driver_name: Mapped[str | None] = mapped_column(Text)
    vehicle_name: Mapped[str | None] = mapped_column(Text)
    vehicle_number: Mapped[str | None] = mapped_column(Text)
    requested_at: Mapped[datetime] = mapped_column(
        server_default=func.now(), index=True
    )
    driver_assigned_at: Mapped[datetime | None]
    driver_arrived_at: Mapped[datetime | None]
    ride_started_at: Mapped[datetime | None]
    completed_at: Mapped[datetime | None] = mapped_column(index=True)
    cancelled_at: Mapped[datetime | None] = mapped_column(index=True)

    ride_session: Mapped[RideSession] = relationship(
        back_populates="ride_requests", lazy="raise"
    )
    ride_events: Mapped[list[RideEvent]] = relationship(
        back_populates="ride_request", lazy="raise", passive_deletes="all"
    )

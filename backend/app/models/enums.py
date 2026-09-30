"""Python values stored as native PostgreSQL enums, never Python member names."""

from enum import StrEnum

from sqlalchemy.dialects.postgresql import ENUM


class LanguageCode(StrEnum):
    EN = "en"
    HI = "hi"
    TE = "te"


class UserStatus(StrEnum):
    ACTIVE = "active"
    DISABLED = "disabled"
    DELETED = "deleted"


class SavedPlaceType(StrEnum):
    HOME = "home"
    CUSTOM = "custom"


class DestinationSource(StrEnum):
    SAVED_PLACE = "saved_place"
    MAPS_SEARCH = "maps_search"
    MANUAL = "manual"


class RideSessionStatus(StrEnum):
    STARTED = "started"
    RESOLVING_DESTINATION = "resolving_destination"
    DESTINATION_CONFIRMED = "destination_confirmed"
    SELECTING_PRODUCT = "selecting_product"
    READY_TO_BOOK = "ready_to_book"
    REQUESTING_RIDE = "requesting_ride"
    FINDING_DRIVER = "finding_driver"
    DRIVER_ASSIGNED = "driver_assigned"
    DRIVER_ARRIVING = "driver_arriving"
    DRIVER_ARRIVED = "driver_arrived"
    RIDE_IN_PROGRESS = "ride_in_progress"
    COMPLETED = "completed"
    CANCELLED = "cancelled"
    FAILED = "failed"
    ABANDONED = "abandoned"


class RideRequestStatus(StrEnum):
    PENDING = "pending"
    FINDING_DRIVER = "finding_driver"
    DRIVER_ASSIGNED = "driver_assigned"
    DRIVER_ARRIVING = "driver_arriving"
    DRIVER_ARRIVED = "driver_arrived"
    IN_PROGRESS = "in_progress"
    COMPLETED = "completed"
    CANCELLED = "cancelled"
    FAILED = "failed"


class RideEventType(StrEnum):
    SESSION_STARTED = "session_started"
    DESTINATION_RESOLVED = "destination_resolved"
    DESTINATION_CHANGED = "destination_changed"
    PRODUCT_SELECTED = "product_selected"
    PRODUCT_CHANGED = "product_changed"
    RIDE_REQUESTED = "ride_requested"
    DRIVER_SEARCHING = "driver_searching"
    DRIVER_ASSIGNED = "driver_assigned"
    DRIVER_ARRIVING = "driver_arriving"
    DRIVER_ARRIVED = "driver_arrived"
    RIDE_STARTED = "ride_started"
    RIDE_COMPLETED = "ride_completed"
    CANCELLATION_REQUESTED = "cancellation_requested"
    RIDE_CANCELLED = "ride_cancelled"
    RIDE_FAILED = "ride_failed"
    SESSION_ABANDONED = "session_abandoned"


class RideEventSource(StrEnum):
    USER = "user"
    RIDE_SAATHI = "ride_saathi"
    UBER = "uber"
    SYSTEM = "system"


def pg_enum(enum: type[StrEnum], name: str) -> ENUM:
    return ENUM(
        enum,
        name=name,
        values_callable=lambda cls: [e.value for e in cls],
        validate_strings=True,
    )

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
    WORK = "work"
    CUSTOM = "custom"


class DestinationSource(StrEnum):
    SAVED_PLACE = "saved_place"
    ALIAS = "alias"
    MAPS_SEARCH = "maps_search"
    MEMORY = "memory"
    CONTEXT = "context"
    COMBINED = "combined"


class RideSessionStatus(StrEnum):
    STARTED = "started"
    LISTENING = "listening"
    RESOLVING_DESTINATION = "resolving_destination"
    NEEDS_CLARIFICATION = "needs_clarification"
    DESTINATION_RESOLVED = "destination_resolved"
    DESTINATION_CONFIRMED = "destination_confirmed"
    READY_FOR_HANDOFF = "ready_for_handoff"
    HANDOFF_OPENED = "handoff_opened"
    ABANDONED = "abandoned"
    FAILED = "failed"


class AliasSource(StrEnum):
    USER_CREATED = "user_created"
    LEARNED = "learned"
    CORRECTION = "correction"
    SYSTEM = "system"


class ConfirmationMode(StrEnum):
    ALWAYS = "always"
    ADAPTIVE = "adaptive"


class HandoffStatus(StrEnum):
    CREATED = "created"
    OPENED = "opened"
    FAILED = "failed"


class SessionEventType(StrEnum):
    RIDE_SESSION_STARTED = "ride_session_started"
    VOICE_RECEIVED = "voice_received"
    DESTINATION_RESOLUTION_STARTED = "destination_resolution_started"
    DESTINATION_RESOLUTION_SUCCESS = "destination_resolution_success"
    DESTINATION_RESOLUTION_FAILED = "destination_resolution_failed"
    DESTINATION_CLARIFICATION_REQUIRED = "destination_clarification_required"
    DESTINATION_CONFIRMED = "destination_confirmed"
    DESTINATION_CORRECTED = "destination_corrected"
    SAVED_PLACE_USED = "saved_place_used"
    HANDOFF_CREATED = "handoff_created"
    HANDOFF_OPENED = "handoff_opened"
    HANDOFF_FAILED = "handoff_failed"
    SESSION_ABANDONED = "session_abandoned"
    SESSION_FAILED = "session_failed"


def pg_enum(enum: type[StrEnum], name: str) -> ENUM:
    return ENUM(enum, name=name, values_callable=lambda cls: [e.value for e in cls], validate_strings=True)

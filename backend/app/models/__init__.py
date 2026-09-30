"""Import all persistent models so Alembic sees complete metadata.

The existing places module contains API DTOs, not database entities.
"""

from app.models.ride_event import RideEvent
from app.models.ride_request import RideRequest
from app.models.ride_session import RideSession
from app.models.saved_place import SavedPlace
from app.models.uber_credential import UberCredential
from app.models.user import User

__all__ = [
    "RideEvent",
    "RideRequest",
    "RideSession",
    "SavedPlace",
    "UberCredential",
    "User",
]

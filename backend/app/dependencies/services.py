"""FastAPI composition boundary for concrete services and providers."""

from typing import Annotated

from fastapi import Depends

from app.integrations.maps.base import MapsProvider
from app.integrations.maps.ola_maps import OlaMapsProvider
from app.integrations.speech.azure import AzureSpeechProvider
from app.integrations.speech.base import SpeechProvider
from app.modules.destinations.service import DestinationService


def get_maps_provider() -> MapsProvider:
    return OlaMapsProvider()


def get_speech_provider() -> SpeechProvider:
    return AzureSpeechProvider()


def get_destination_service(
    maps: Annotated[MapsProvider, Depends(get_maps_provider)],
) -> DestinationService:
    return DestinationService(maps)

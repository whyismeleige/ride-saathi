"""Small synchronous contract matching the current place-search capability."""

from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True)
class PlaceCandidate:
    address: str
    latitude: float
    longitude: float


class MapsError(Exception):
    """A provider failure reduced to a stable category, without raw HTTP data."""

    def __init__(self, category: str) -> None:
        super().__init__(category)
        self.category = category


class MapsProvider(Protocol):
    def search_places(
        self, query: str, language: str, lat: float | None = None, lng: float | None = None,
    ) -> list[PlaceCandidate]:
        """Return normalized candidates in provider order, or raise MapsError."""
        ...

"""Existing place-search policy, independent of the selected maps vendor."""

from app.integrations.maps.base import MapsProvider
from app.modules.destinations.schemas import Place

SUPPORTED_LANGUAGES = {"en", "hi", "te"}


class DestinationService:
    def __init__(self, maps: MapsProvider) -> None:
        self.maps = maps

    def search(
        self, query: str, language: str, lat: float | None = None, lng: float | None = None,
    ) -> list[Place]:
        candidates = self.maps.search_places(query, language, lat=lat, lng=lng)
        # Preserve the existing app policy: keep upstream order and exact dedupe.
        seen: set[tuple[str, float, float]] = set()
        places: list[Place] = []
        for candidate in candidates:
            key = (candidate.address, candidate.latitude, candidate.longitude)
            if key not in seen:
                seen.add(key)
                places.append(Place(
                    address=candidate.address,
                    latitude=candidate.latitude,
                    longitude=candidate.longitude,
                ))
        return places

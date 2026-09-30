import pytest

from app.integrations.maps.base import MapsError, PlaceCandidate
from app.modules.destinations.service import DestinationService


def test_search_preserves_order_deduplicates_and_forwards_bias():
    first = PlaceCandidate("Temple", 17.5, 78.5)
    second = PlaceCandidate("Market", 17.4, 78.4)
    same_name_elsewhere = PlaceCandidate("Temple", 17.6, 78.6)
    calls = []

    class FakeMaps:
        def search_places(self, query, language, lat=None, lng=None):
            calls.append((query, language, lat, lng))
            return [first, second, first, same_name_elsewhere]

    places = DestinationService(FakeMaps()).search("Temple", "te", lat=17.0, lng=78.0)

    assert calls == [("Temple", "te", 17.0, 78.0)]
    assert [place.model_dump() for place in places] == [
        {"address": "Temple", "latitude": 17.5, "longitude": 78.5},
        {"address": "Market", "latitude": 17.4, "longitude": 78.4},
        {"address": "Temple", "latitude": 17.6, "longitude": 78.6},
    ]


def test_provider_failures_retain_their_category():
    class UnavailableMaps:
        def search_places(self, *args, **kwargs):
            raise MapsError("UNAVAILABLE")

    with pytest.raises(MapsError, match="UNAVAILABLE"):
        DestinationService(UnavailableMaps()).search("Temple", "en")

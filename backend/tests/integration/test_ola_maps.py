import logging

import httpx

from app.core.config import settings
from app.core.logging import configure_logging
from app.integrations.maps import ola_maps
from app.integrations.maps.base import PlaceCandidate


def test_adapter_normalizes_provider_data_without_domain_deduplication(monkeypatch, caplog):
    item = {
        "description": "  Private test destination  ",
        "geometry": {"location": {"lat": "17.4", "lng": "78.4"}},
    }
    requests = []

    def handler(request):
        requests.append(request)
        return httpx.Response(200, json={"status": "ok", "predictions": [item, {}, item]})

    monkeypatch.setattr(settings, "ola_maps_api_key", "fake-integration-key")
    monkeypatch.setattr(settings, "ola_maps_base_url", "https://maps.example")
    monkeypatch.setattr(ola_maps, "build_client", lambda: httpx.Client(transport=httpx.MockTransport(handler)))
    with caplog.at_level(logging.INFO):
        configure_logging()
        places = ola_maps.OlaMapsProvider().search_places("Private test destination", "en")

    assert places == [PlaceCandidate("Private test destination", 17.4, 78.4)] * 2
    assert requests[0].url.path == "/places/v1/autocomplete"
    assert requests[0].url.params["api_key"] == "fake-integration-key"
    assert "fake-integration-key" not in caplog.text
    assert "Private test destination" not in caplog.text

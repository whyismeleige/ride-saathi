from app.dependencies.services import get_maps_provider
from app.integrations.maps.base import PlaceCandidate
from app.main import app


def test_existing_routes_and_lifespan(client):
    assert set(app.openapi()["paths"]) == {
        "/health",
        "/ready",
        "/v1/places/autocomplete",
    }
    assert client.get("/openapi.json").status_code == 200
    assert client.get("/docs").status_code == 404
    assert client.get("/redoc").status_code == 404
    response = client.get("/health", headers={"X-Request-Id": "architecture-check"})
    assert response.json() == {"status": "ok"}
    assert response.headers["X-Request-Id"] == "architecture-check"


def test_maps_provider_can_be_replaced_without_changing_the_endpoint(client, override_dependency):
    calls = []

    class FakeMaps:
        def search_places(self, query, language, lat=None, lng=None):
            calls.append((query, language, lat, lng))
            return [PlaceCandidate("Temple", 17.4, 78.4)]

    override_dependency[get_maps_provider] = FakeMaps
    response = client.get("/v1/places/autocomplete", params={"q": " Temple ", "language": " HI "})
    assert response.status_code == 200
    assert response.json() == {"places": [{"address": "Temple", "latitude": 17.4, "longitude": 78.4}]}
    assert calls == [("Temple", "hi", None, None)]


def test_application_shutdown_disposes_database_pool(monkeypatch):
    from unittest.mock import AsyncMock

    from fastapi.testclient import TestClient

    from app import main

    dispose = AsyncMock()
    monkeypatch.setattr(main, "dispose_engine", dispose)
    with TestClient(app) as client:
        assert client.get("/health").status_code == 200
        dispose.assert_not_awaited()
    dispose.assert_awaited_once()

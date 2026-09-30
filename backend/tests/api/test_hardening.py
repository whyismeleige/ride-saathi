"""Runtime hardening: rate limits, request ids, readiness, prod config."""

import re

import httpx
import pytest
from fastapi.testclient import TestClient

from app.core import config
from app.core.config import Settings
from app.integrations.maps import ola_maps
from app.main import app

client = TestClient(app)


@pytest.fixture(autouse=True)
def fake_provider(monkeypatch):
    monkeypatch.setattr(config.settings, "ola_maps_api_key", "test-key")
    handler = lambda request: httpx.Response(  # noqa: E731
        200,
        json={"status": "ok", "predictions": []},
        request=request,
    )
    fake = httpx.Client(transport=httpx.MockTransport(handler))
    monkeypatch.setattr(ola_maps, "get_shared_client", lambda: fake)
    monkeypatch.setattr(ola_maps, "build_client", lambda: fake)


def test_rate_limit_returns_stable_quota_error(monkeypatch):
    monkeypatch.setattr(config.settings, "maps_rate_limit_per_minute", 2)
    monkeypatch.setattr(config.settings, "maps_rate_limit_window_seconds", 60)
    assert client.get("/v1/places/autocomplete?q=hyderabad").status_code == 200
    assert client.get("/v1/places/autocomplete?q=hyderabad").status_code == 200
    response = client.get("/v1/places/autocomplete?q=hyderabad")
    assert response.status_code == 429
    assert response.json() == {
        "error": {"code": "QUOTA", "message": "Place search temporarily unavailable"}
    }


def test_untrusted_forwarded_header_ignored_by_default(monkeypatch):
    monkeypatch.setattr(config.settings, "maps_rate_limit_per_minute", 1)
    monkeypatch.setattr(config.settings, "maps_rate_limit_window_seconds", 60)
    assert client.get("/v1/places/autocomplete?q=hyderabad").status_code == 200
    # A spoofed IP must not reset the caller's bucket when untrusted.
    response = client.get(
        "/v1/places/autocomplete?q=hyderabad",
        headers={"X-Forwarded-For": "9.9.9.9"},
    )
    assert response.status_code == 429


@pytest.mark.parametrize(
    "request_id", ["bad id with spaces!", "x" * 65, "../../etc", ""]
)
def test_invalid_request_id_is_replaced(request_id):
    response = client.get("/health", headers={"X-Request-Id": request_id})
    echoed = response.headers["X-Request-Id"]
    assert re.fullmatch(r"[A-Za-z0-9_-]{1,64}", echoed)
    if request_id and re.fullmatch(r"[A-Za-z0-9_-]{1,64}", request_id):
        assert echoed == request_id
    else:
        assert echoed != request_id


def test_valid_request_id_is_echoed():
    response = client.get("/health", headers={"X-Request-Id": "abc_123-XYZ"})
    assert response.headers["X-Request-Id"] == "abc_123-XYZ"


def test_ready_without_database(monkeypatch):
    monkeypatch.setattr(config.settings, "database_url", "")
    response = client.get("/ready")
    assert response.status_code == 200
    assert response.json() == {"status": "ok", "database": "not_configured"}


def test_ready_with_unreachable_database(monkeypatch):
    monkeypatch.setattr(
        config.settings,
        "database_url",
        "postgresql://postgres:postgres@127.0.0.1:1/unreachable",
    )
    response = client.get("/ready")
    assert response.status_code == 503
    assert response.json() == {"status": "not_ready", "database": "unreachable"}


def test_production_requires_maps_key_and_database():
    with pytest.raises(Exception, match="OLA_MAPS_API_KEY"):
        Settings(
            _env_file=None,
            environment="production",
            ola_maps_api_key="",
            database_url="postgresql://u@h/db",
        )
    with pytest.raises(Exception, match="DATABASE_URL"):
        Settings(
            _env_file=None,
            environment="production",
            ola_maps_api_key="k",
            database_url="",
        )
    settings = Settings(
        _env_file=None,
        environment="production",
        ola_maps_api_key="k",
        database_url="postgresql://u@h/db",
    )
    assert settings.environment == "production"

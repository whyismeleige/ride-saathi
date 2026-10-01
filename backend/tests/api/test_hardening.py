"""Runtime hardening: rate limits, request ids, readiness, prod config."""

import re
from concurrent.futures import ThreadPoolExecutor
from unittest.mock import AsyncMock, MagicMock

import httpx
import pytest
from fastapi.testclient import TestClient
from starlette.requests import Request

from app.api.endpoints import health
from app.core import config
from app.core.config import Settings
from app.integrations.maps import ola_maps
from app.main import app
from app.modules.destinations import router as destinations_router

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
    "request_id", ["bad id with spaces!", "x" * 65, "../../etc", "", "valid-id\n"]
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


@pytest.mark.parametrize("url", ["invalid-secret-url", "sqlite:///secret.db"])
def test_ready_with_invalid_database_configuration(monkeypatch, url):
    monkeypatch.setattr(config.settings, "database_url", url)
    response = client.get("/ready")
    assert response.status_code == 503
    assert response.json() == {"status": "not_ready", "database": "unreachable"}


def test_ready_connection_timeout_disposes_engine(monkeypatch):
    monkeypatch.setattr(config.settings, "database_url", "postgresql://u@h/db")
    engine = MagicMock()
    engine.connect.return_value.__aenter__ = AsyncMock(side_effect=TimeoutError)
    engine.dispose = AsyncMock()
    monkeypatch.setattr(health, "create_engine", lambda *args, **kwargs: engine)
    assert client.get("/ready").status_code == 503
    engine.dispose.assert_awaited_once()


def test_rate_limit_expires_inactive_clients(monkeypatch):
    monkeypatch.setattr(config.settings, "maps_rate_limit_per_minute", 1)
    monkeypatch.setattr(config.settings, "maps_rate_limit_window_seconds", 60)
    monkeypatch.setattr(config.settings, "trust_proxy_headers", False)
    request = Request({"type": "http", "client": ("192.0.2.1", 1234)})
    monkeypatch.setattr(destinations_router.time, "monotonic", lambda: 100.0)
    assert destinations_router._check_rate_limit(request) is None
    assert destinations_router._check_rate_limit(request).status_code == 429
    monkeypatch.setattr(destinations_router.time, "monotonic", lambda: 160.0)
    other = Request({"type": "http", "client": ("192.0.2.2", 1234)})
    assert destinations_router._check_rate_limit(other) is None
    assert "192.0.2.1" not in destinations_router._rate_buckets
    assert destinations_router._check_rate_limit(request) is None


def test_rate_limit_admission_is_atomic(monkeypatch):
    monkeypatch.setattr(config.settings, "maps_rate_limit_per_minute", 7)
    monkeypatch.setattr(config.settings, "trust_proxy_headers", False)
    monkeypatch.setattr(destinations_router.time, "monotonic", lambda: 100.0)
    request = Request({"type": "http", "client": ("192.0.2.1", 1234)})
    with ThreadPoolExecutor(max_workers=16) as executor:
        responses = list(executor.map(
            lambda _: destinations_router._check_rate_limit(request), range(100)
        ))
    assert sum(response is None for response in responses) == 7
    assert all(response is None or response.status_code == 429 for response in responses)


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

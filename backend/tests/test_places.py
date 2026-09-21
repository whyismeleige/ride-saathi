import pytest
from fastapi.testclient import TestClient
import httpx

from app import config
from app.main import app
from app.services import ola_maps

client = TestClient(app)

SECRET_KEY = "backend-secret-key"


@pytest.fixture(autouse=True)
def configured_key(monkeypatch):
    """Every test runs with a real-looking key unless a test overrides it."""
    monkeypatch.setattr(config.settings, "ola_maps_api_key", SECRET_KEY)
    # Guard against a developer's environment leaking a real key into assertions.
    monkeypatch.delenv("OLA_MAPS_API_KEY", raising=False)


def upstream(handler):
    """Route upstream calls to a canned handler without touching real Ola."""

    def _install(monkeypatch, configured_key):
        def _factory() -> httpx.Client:
            return httpx.Client(transport=httpx.MockTransport(handler))

        monkeypatch.setattr(ola_maps, "build_client", _factory)

    return _install


PREDICTION = {
    "description": "Apollo Hospitals, Jubilee Hills, Hyderabad",
    "geometry": {"location": {"lat": 17.414, "lng": 78.412}},
    "place_id": "ola-platform:apollo",
}


def ok_upstream(items, status="ok"):
    captured = {}

    def _handler(request: httpx.Request) -> httpx.Response:
        captured["params"] = dict(request.url.params)
        return httpx.Response(200, json={"status": status, "predictions": items}, request=request)

    _handler.captured = captured
    return _handler


class TestHealth:
    def test_health(self):
        response = client.get("/health")
        assert response.status_code == 200
        assert response.json() == {"status": "ok"}


class TestAutocomplete:
    def test_success_preserves_order_and_deduplicates(self, monkeypatch, configured_key):
        distant = {**PREDICTION, "description": "Apollo Hospitals, Secunderabad",
                   "geometry": {"location": {"lat": 17.5, "lng": 78.5}}}
        duplicate = dict(PREDICTION)  # exact duplicate of the first prediction
        handler = ok_upstream([distant, PREDICTION, duplicate, PREDICTION])
        upstream(handler)(monkeypatch, configured_key)

        response = client.get("/v1/places/autocomplete", params={"q": "Apollo", "language": "en"})
        assert response.status_code == 200
        places = response.json()["places"]
        assert places == [
            {"address": distant["description"], "latitude": 17.5, "longitude": 78.5},
            {"address": PREDICTION["description"], "latitude": 17.414, "longitude": 78.412},
        ]
        assert "api_key" not in response.text
        # The credential is sent only upstream.
        assert handler.captured["params"]["api_key"] == SECRET_KEY

    def test_location_bias_forwarded(self, monkeypatch, configured_key):
        handler = ok_upstream([PREDICTION])
        upstream(handler)(monkeypatch, configured_key)
        response = client.get("/v1/places/autocomplete",
                              params={"q": "Apollo", "language": "hi", "lat": 17.44, "lng": 78.39})
        assert response.status_code == 200
        params = handler.captured["params"]
        assert params["location"] == "17.44,78.39"
        assert params["radius"] == "50000"
        assert params["strictbounds"] == "true"
        assert params["input"] == "Apollo"
        assert params["language"] == "hi"

    def test_zero_results_is_empty(self, monkeypatch, configured_key):
        upstream(ok_upstream([]))(monkeypatch, configured_key)
        response = client.get("/v1/places/autocomplete", params={"q": "Unknown"})
        assert response.status_code == 200
        assert response.json() == {"places": []}

    def test_malformed_place_entries_are_dropped(self, monkeypatch, configured_key):
        invalid = [
            {"description": "Missing geometry"},
            {"description": "Missing latitude", "geometry": {"location": {"lng": 78}}},
            {"description": "Out of range", "geometry": {"location": {"lat": 91, "lng": 78}}},
            {"description": "Not finite", "geometry": {"location": {"lat": "NaN", "lng": 78}}},
            {"description": " ", "geometry": {"location": {"lat": 17, "lng": 78}}},
            {"description": "null", "geometry": {"location": {"lat": 17, "lng": 78}}},
            "not a dict",
        ]
        upstream(ok_upstream(invalid + [PREDICTION]))(monkeypatch, configured_key)
        response = client.get("/v1/places/autocomplete", params={"q": "Apollo"})
        assert response.status_code == 200
        assert response.json()["places"] == [
            {"address": PREDICTION["description"], "latitude": 17.414, "longitude": 78.412}
        ]

    def test_all_malformed_entries_are_invalid_response(self, monkeypatch, configured_key):
        upstream(ok_upstream([{"description": "broken"}]))(monkeypatch, configured_key)
        response = client.get("/v1/places/autocomplete", params={"q": "Apollo"})
        assert response.status_code == 502
        assert response.json()["error"]["code"] == "INVALID_RESPONSE"

    def test_upstream_ocp_status_maps_to_quota(self, monkeypatch, configured_key):
        upstream(ok_upstream([], status="over_query_limit"))(monkeypatch, configured_key)
        response = client.get("/v1/places/autocomplete", params={"q": "Apollo"})
        assert response.status_code == 429
        assert response.json()["error"]["code"] == "QUOTA"

    def test_upstream_denied_status_map(self, monkeypatch, configured_key):
        upstream(ok_upstream([], status="request_denied"))(monkeypatch, configured_key)
        response = client.get("/v1/places/autocomplete", params={"q": "Apollo"})
        assert response.status_code == 403
        assert response.json()["error"]["code"] == "ACCESS_DENIED"


class TestAutocompleteValidation:
    @pytest.mark.parametrize("params", [
        {"q": ""},
        {"q": "   "},
        {"q": "xy"},
        {"q": "x" * 201},
    ])
    def test_query_length_validation(self, monkeypatch, configured_key, params):
        upstream(ok_upstream([PREDICTION]))(monkeypatch, configured_key)
        response = client.get("/v1/places/autocomplete", params=params)
        assert response.status_code == 400
        assert response.json()["error"]["code"] == "INVALID_REQUEST"

    def test_unsupported_language(self, monkeypatch, configured_key):
        upstream(ok_upstream([PREDICTION]))(monkeypatch, configured_key)
        response = client.get("/v1/places/autocomplete", params={"q": "Apollo", "language": "fr"})
        assert response.status_code == 400
        assert response.json()["error"]["code"] == "INVALID_REQUEST"

    @pytest.mark.parametrize("params", [
        {"q": "Apollo", "lat": 17.44},
        {"q": "Apollo", "lng": 78.39},
    ])
    def test_lat_lng_must_be_paired(self, params):
        response = client.get("/v1/places/autocomplete", params=params)
        assert response.status_code == 400
        assert response.json()["error"]["code"] == "INVALID_REQUEST"

    @pytest.mark.parametrize("params", [
        {"q": "Apollo", "lat": 91.0, "lng": 78.0},
        {"q": "Apollo", "lat": -91.0, "lng": 78.0},
        {"q": "Apollo", "lat": 17.0, "lng": 181.0},
        {"q": "Apollo", "lat": 17.0, "lng": -181.0},
    ])
    def test_out_of_range_coordinates(self, params):
        response = client.get("/v1/places/autocomplete", params=params)
        assert response.status_code == 400
        assert response.json()["error"]["code"] == "INVALID_REQUEST"

    def test_invalid_requests_do_not_reach_upstream(self, monkeypatch, configured_key):
        calls = []

        def _handler(request):
            calls.append(request)
            return httpx.Response(200, json={"status": "ok", "predictions": []}, request=request)

        upstream(_handler)(monkeypatch, configured_key)
        client.get("/v1/places/autocomplete", params={"q": "ap"})
        client.get("/v1/places/autocomplete", params={"q": "Apollo", "lat": 17.0})
        assert calls == []


class TestUpstreamFailures:
    @pytest.mark.parametrize("status", [401, 403])
    def test_access_denied(self, status, monkeypatch, configured_key):
        upstream(lambda r: httpx.Response(status, request=r))(monkeypatch, configured_key)
        response = client.get("/v1/places/autocomplete", params={"q": "Apollo"})
        assert response.status_code == 403
        assert response.json()["error"]["code"] == "ACCESS_DENIED"

    def test_quota(self, monkeypatch, configured_key):
        upstream(lambda r: httpx.Response(429, request=r))(monkeypatch, configured_key)
        response = client.get("/v1/places/autocomplete", params={"q": "Apollo"})
        assert response.status_code == 429
        assert response.json()["error"]["code"] == "QUOTA"

    @pytest.mark.parametrize("status", [500, 502, 503, 504])
    def test_server_errors(self, status, monkeypatch, configured_key):
        upstream(lambda r: httpx.Response(status, request=r))(monkeypatch, configured_key)
        response = client.get("/v1/places/autocomplete", params={"q": "Apollo"})
        assert response.status_code == 503
        assert response.json()["error"]["code"] == "UNAVAILABLE"

    def test_upstream_timeout(self, monkeypatch, configured_key):
        def _handler(request: httpx.Request) -> httpx.Response:
            raise httpx.ReadTimeout("upstream timed out", request=request)

        upstream(_handler)(monkeypatch, configured_key)
        response = client.get("/v1/places/autocomplete", params={"q": "Apollo"})
        assert response.status_code == 503
        assert response.json()["error"]["code"] == "UNAVAILABLE"

    def test_upstream_connection_error(self, monkeypatch, configured_key):
        def _handler(request: httpx.Request) -> httpx.Response:
            raise httpx.ConnectError("upstream unreachable", request=request)

        upstream(_handler)(monkeypatch, configured_key)
        response = client.get("/v1/places/autocomplete", params={"q": "Apollo"})
        assert response.status_code == 503
        assert response.json()["error"]["code"] == "UNAVAILABLE"

    def test_malformed_upstream_json(self, monkeypatch, configured_key):
        upstream(lambda r: httpx.Response(200, text="<html>not json</html>", request=r))(monkeypatch, configured_key)
        response = client.get("/v1/places/autocomplete", params={"q": "Apollo"})
        assert response.status_code == 502
        assert response.json()["error"]["code"] == "INVALID_RESPONSE"

    def test_unexpected_upstream_shape(self, monkeypatch, configured_key):
        # Unknown status mirrors the app's prior behavior: unavailable.
        upstream(lambda r: httpx.Response(200, json={"things": []}, request=r))(monkeypatch, configured_key)
        response = client.get("/v1/places/autocomplete", params={"q": "Apollo"})
        assert response.status_code == 503
        assert response.json()["error"]["code"] == "UNAVAILABLE"
        # "ok" status with no predictions list is unparseable.
        upstream(lambda r: httpx.Response(200, json={"status": "ok"}, request=r))(monkeypatch, configured_key)
        response = client.get("/v1/places/autocomplete", params={"q": "Apollo"})
        assert response.status_code == 502
        assert response.json()["error"]["code"] == "INVALID_RESPONSE"


class TestMissingKey:
    def test_not_configured_without_upstream_call(self, monkeypatch):
        monkeypatch.setattr(config.settings, "ola_maps_api_key", "")
        calls = []

        def _handler(request: httpx.Request) -> httpx.Response:
            calls.append(request)

        upstream(_handler)(monkeypatch, None)
        response = client.get("/v1/places/autocomplete", params={"q": "Apollo"})
        assert response.status_code == 503
        assert response.json()["error"]["code"] == "NOT_CONFIGURED"
        assert calls == []

    def test_error_bodies_never_contain_the_key(self, monkeypatch, configured_key):
        scenarios = [
            (401, None),
            (403, None),
            (429, None),
            (500, None),
            (200, {"status": "boom", "predictions": []}),
            (200, {"status": "ok", "predictions": []}),
        ]

        for status, payload in scenarios:
            def _handler(request: httpx.Request, _status=status, _payload=payload) -> httpx.Response:
                if _payload is not None:
                    return httpx.Response(_status, json=_payload, request=request)
                return httpx.Response(_status, request=request)

            upstream(_handler)(monkeypatch, configured_key)
            response = client.get("/v1/places/autocomplete", params={"q": "Apollo"})
            body = response.text
            assert SECRET_KEY not in body, body
            assert "api_key" not in body.lower(), body
            assert response.status_code in (200, 403, 429, 502, 503)

    def test_request_id_header_echoed(self):
        response = client.get("/health", headers={"X-Request-Id": "trace-123"})
        assert response.headers.get("X-Request-Id") == "trace-123"


class TestResponseShape:
    def test_error_shape_is_stable(self, monkeypatch, configured_key):
        upstream(lambda r: httpx.Response(429, request=r))(monkeypatch, configured_key)
        response = client.get("/v1/places/autocomplete", params={"q": "Apollo"})
        assert list(response.json().keys()) == ["error"]
        assert list(response.json()["error"].keys()) == ["code", "message"]

    def test_success_shape_is_stable(self, monkeypatch, configured_key):
        upstream(ok_upstream([PREDICTION]))(monkeypatch, configured_key)
        response = client.get("/v1/places/autocomplete", params={"q": "Apollo"})
        assert list(response.json().keys()) == ["places"]
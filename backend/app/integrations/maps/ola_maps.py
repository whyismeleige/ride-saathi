"""Boundary around the privileged Ola Maps API.

The Ola API key is read from server configuration and is never exposed to
clients. Errors are reduced to a small stable category so the Android app can
keep its existing user-facing behavior.
"""

import math
from threading import Lock

import httpx

from app.core.config import settings
from app.integrations.maps.base import MapsError, PlaceCandidate

AUTOCOMPLETE_PATH = "/places/v1/autocomplete"
# Matches the Android app's existing bounded search.
BIAS_RADIUS_METERS = 50_000


def build_client() -> httpx.Client:
    """Short-timeout client. No aggressive retries; no redirect following."""
    return httpx.Client(
        timeout=httpx.Timeout(settings.ola_maps_timeout_seconds),
        follow_redirects=False,
        headers={"Accept": "application/json", "User-Agent": "ride-saathi-backend"},
    )


_shared_client: httpx.Client | None = None
_client_lock = Lock()


def get_shared_client() -> httpx.Client:
    """Process-wide reused connection pool for autocomplete requests."""
    global _shared_client
    with _client_lock:
        if _shared_client is None:
            _shared_client = build_client()
        return _shared_client


def close_shared_client() -> None:
    """Release the shared pool; called on FastAPI shutdown."""
    global _shared_client
    with _client_lock:
        if _shared_client is not None:
            _shared_client.close()
            _shared_client = None


class OlaMapsProvider:
    """Adapt Ola Maps to the provider-neutral search contract."""

    def __init__(self, client: httpx.Client | None = None) -> None:
        # Tests may inject a MockTransport-backed client; runtime uses shared pool.
        self._client = client

    def search_places(
        self, query: str, language: str, lat: float | None = None, lng: float | None = None,
    ) -> list[PlaceCandidate]:
        """Call Ola autocomplete and return cleaned places in upstream order.

        ``lat``/``lng`` are an optional location bias sent together. Coordinates are
        validated by the caller but guarded here as well.
        """
        if not settings.ola_maps_api_key:
            raise MapsError("NOT_CONFIGURED")

        params: dict[str, str | float] = {
            "input": query,
            "language": language,
            "api_key": settings.ola_maps_api_key,
        }
        if lat is not None and lng is not None:
            params["location"] = f"{lat},{lng}"
            params["radius"] = str(BIAS_RADIUS_METERS)
            params["strictbounds"] = "true"

        url = f"{settings.ola_maps_base_url}{AUTOCOMPLETE_PATH}"
        client = self._client or get_shared_client()
        try:
            response = client.get(url, params=params)
        except httpx.TimeoutException:
            raise MapsError("UNAVAILABLE") from None
        except httpx.HTTPError:
            raise MapsError("UNAVAILABLE") from None
        return _map_upstream_response(response)

def _map_upstream_response(response: httpx.Response) -> list[PlaceCandidate]:
    status = response.status_code
    if status in (401, 403):
        raise MapsError("ACCESS_DENIED")
    if status == 429:
        raise MapsError("QUOTA")
    if status != 200:
        raise MapsError("UNAVAILABLE")
    try:
        payload = response.json()
    except ValueError:
        raise MapsError("INVALID_RESPONSE") from None
    if not isinstance(payload, dict):
        raise MapsError("INVALID_RESPONSE")
    return _payload_to_places(payload)


def _payload_to_places(payload: dict) -> list[PlaceCandidate]:
    status = str(payload.get("status") or "").lower()
    if status == "over_query_limit":
        raise MapsError("QUOTA")
    if status == "request_denied":
        raise MapsError("ACCESS_DENIED")
    if status not in ("ok", "zero_results"):
        raise MapsError("UNAVAILABLE")

    predictions = payload.get("predictions")
    if not isinstance(predictions, list):
        raise MapsError("INVALID_RESPONSE")

    candidates: list[PlaceCandidate] = []
    for item in predictions:
        parsed = _parse_prediction(item)
        if parsed is not None:
            candidates.append(parsed)

    if predictions and not candidates:
        # Upstream returned something, but none of it was usable.
        raise MapsError("INVALID_RESPONSE")

    return candidates


def _parse_prediction(item: object) -> PlaceCandidate | None:
    if not isinstance(item, dict):
        return None
    address = str(item.get("description") or "").strip()
    geometry = item.get("geometry")
    location = geometry.get("location") if isinstance(geometry, dict) else None
    raw_lat = location.get("lat") if isinstance(location, dict) else None
    raw_lng = location.get("lng") if isinstance(location, dict) else None
    try:
        latitude = float(raw_lat)  # type: ignore[arg-type]
        longitude = float(raw_lng)  # type: ignore[arg-type]
    except (TypeError, ValueError):
        return None
    if not (math.isfinite(latitude) and math.isfinite(longitude)):
        return None
    if not (0 < len(address) and address != "null"):
        return None
    if not (-90.0 <= latitude <= 90.0 and -180.0 <= longitude <= 180.0):
        return None
    return PlaceCandidate(address=address, latitude=latitude, longitude=longitude)

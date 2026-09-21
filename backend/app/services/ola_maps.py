"""Boundary around the privileged Ola Maps API.

The Ola API key is read from server configuration and is never exposed to
clients. Errors are reduced to a small stable category so the Android app can
keep its existing user-facing behavior.
"""

import math

import httpx

from ..config import settings
from ..models.places import Place

AUTOCOMPLETE_PATH = "/places/v1/autocomplete"
SUPPORTED_LANGUAGES = {"en", "hi", "te"}
# Matches the Android app's existing bounded search.
BIAS_RADIUS_METERS = 50_000


class OlaMapsError(Exception):
    """An upstream failure reduced to a stable category."""

    def __init__(self, category: str) -> None:
        super().__init__(category)
        self.category = category


def build_client() -> httpx.Client:
    """Short-timeout client. No aggressive retries; no redirect following."""
    return httpx.Client(
        timeout=httpx.Timeout(settings.ola_maps_timeout_seconds),
        follow_redirects=False,
        headers={"Accept": "application/json", "User-Agent": "ride-saathi-backend"},
    )


def search(query: str, language: str, lat: float | None = None, lng: float | None = None) -> list[Place]:
    """Call Ola autocomplete and return cleaned, ordered, deduplicated places.

    ``lat``/``lng`` are an optional location bias sent together. Coordinates are
    validated by the caller but guarded here as well.
    """
    if not settings.ola_maps_api_key:
        raise OlaMapsError("NOT_CONFIGURED")

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
    try:
        with build_client() as client:
            response = client.get(url, params=params)
    except httpx.TimeoutException:
        raise OlaMapsError("UNAVAILABLE") from None
    except httpx.HTTPError:
        raise OlaMapsError("UNAVAILABLE") from None
    return _map_upstream_response(response)


def _map_upstream_response(response: httpx.Response) -> list[Place]:
    status = response.status_code
    if status in (401, 403):
        raise OlaMapsError("ACCESS_DENIED")
    if status == 429:
        raise OlaMapsError("QUOTA")
    if status != 200:
        raise OlaMapsError("UNAVAILABLE")
    try:
        payload = response.json()
    except ValueError:
        raise OlaMapsError("INVALID_RESPONSE") from None
    if not isinstance(payload, dict):
        raise OlaMapsError("INVALID_RESPONSE")
    return _payload_to_places(payload)


def _payload_to_places(payload: dict) -> list[Place]:
    status = str(payload.get("status") or "").lower()
    if status == "over_query_limit":
        raise OlaMapsError("QUOTA")
    if status == "request_denied":
        raise OlaMapsError("ACCESS_DENIED")
    if status not in ("ok", "zero_results"):
        raise OlaMapsError("UNAVAILABLE")

    predictions = payload.get("predictions")
    if not isinstance(predictions, list):
        raise OlaMapsError("INVALID_RESPONSE")

    candidates: list[Place] = []
    for item in predictions:
        parsed = _parse_prediction(item)
        if parsed is not None:
            candidates.append(parsed)

    if predictions and not candidates:
        # Upstream returned something, but none of it was usable.
        raise OlaMapsError("INVALID_RESPONSE")

    # Preserve upstream ordering and drop exact duplicates, like the app did.
    seen: set[tuple] = set()
    deduplicated: list[Place] = []
    for candidate in candidates:
        key = (candidate.address, candidate.latitude, candidate.longitude)
        if key not in seen:
            seen.add(key)
            deduplicated.append(candidate)
    return deduplicated


def _parse_prediction(item: object) -> Place | None:
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
    return Place(address=address, latitude=latitude, longitude=longitude)
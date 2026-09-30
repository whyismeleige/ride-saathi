from typing import Annotated

from fastapi import APIRouter, Depends, Query, Request
from fastapi.responses import JSONResponse

from app.core.config import settings
from app.dependencies.services import get_destination_service
from app.integrations.maps.base import MapsError
from app.modules.destinations.schemas import PlacesResponse
from app.modules.destinations.service import SUPPORTED_LANGUAGES, DestinationService

router = APIRouter(prefix="/places")

MAX_QUERY_LENGTH = 200
MIN_QUERY_LENGTH = 3

# Stable error model. Codes match what the Android app understands.
ERROR_HTTP_STATUS: dict[str, int] = {
    "INVALID_REQUEST": 400,
    "ACCESS_DENIED": 403,
    "QUOTA": 429,
    "NOT_CONFIGURED": 503,
    "UNAVAILABLE": 503,
    "INVALID_RESPONSE": 502,
}

ERROR_MESSAGES: dict[str, str] = {
    "INVALID_REQUEST": "Invalid place search request",
    "ACCESS_DENIED": "Place search access was denied",
    "QUOTA": "Place search temporarily unavailable",
    "NOT_CONFIGURED": "Place search is not configured",
    "UNAVAILABLE": "Place search temporarily unavailable",
    "INVALID_RESPONSE": "Place search returned an invalid response",
}


def _error(code: str) -> JSONResponse:
    return JSONResponse(
        status_code=ERROR_HTTP_STATUS[code],
        content={"error": {"code": code, "message": ERROR_MESSAGES[code]}},
    )


def _invalid() -> JSONResponse:
    return _error("INVALID_REQUEST")


# Minimal in-memory abuse protection for the privileged maps proxy.
# Single-instance only: use reverse-proxy throttling when scaling horizontally.
# X-Forwarded-For is honored only when TRUST_PROXY_HEADERS is explicitly enabled.
_rate_buckets: dict[str, list[float]] = {}


def _client_key(request: Request) -> str:
    if settings.trust_proxy_headers:
        forwarded = request.headers.get("x-forwarded-for", "")
        first = forwarded.split(",")[0].strip()
        if first:
            return f"proxy:{first}"
    client = request.client
    return client.host if client else "unknown"


def _check_rate_limit(request: Request) -> JSONResponse | None:
    import time

    now = time.monotonic()
    window = settings.maps_rate_limit_window_seconds
    limit = settings.maps_rate_limit_per_minute
    key = _client_key(request)
    hits = [t for t in _rate_buckets.get(key, []) if now - t < window]
    if len(hits) >= limit:
        _rate_buckets[key] = hits
        return JSONResponse(
            status_code=429,
            content={"error": {"code": "QUOTA", "message": ERROR_MESSAGES["QUOTA"]}},
        )
    hits.append(now)
    _rate_buckets[key] = hits
    return None


def _reset_rate_limit() -> None:
    _rate_buckets.clear()


@router.get("/autocomplete", response_model=PlacesResponse)
def autocomplete(
    request: Request,
    service: Annotated[DestinationService, Depends(get_destination_service)],
    q: str = Query(default=""),
    language: str = Query(default="en"),
    lat: float | None = Query(default=None),
    lng: float | None = Query(default=None),
):
    """Search places, proxying the privileged upstream provider.

    Client input is treated as untrusted: query length, language, and coordinate
    pairing are validated here before anything reaches the upstream API.
    Abuse protection is a single-instance in-memory window; deployments that
    scale horizontally must add reverse-proxy throttling in front of this.
    """
    limited = _check_rate_limit(request)
    if limited is not None:
        return limited
    query = q.strip()
    if not query or len(query) < MIN_QUERY_LENGTH or len(query) > MAX_QUERY_LENGTH:
        return _invalid()

    normalized_language = language.strip().lower()
    if normalized_language not in SUPPORTED_LANGUAGES:
        return _invalid()

    if (lat is None) != (lng is None):
        return _invalid()
    if lat is not None and not (-90.0 <= lat <= 90.0):
        return _invalid()
    if lng is not None and not (-180.0 <= lng <= 180.0):
        return _invalid()

    try:
        places = service.search(query, normalized_language, lat=lat, lng=lng)
    except MapsError as error:
        return _error(error.category)
    return PlacesResponse(places=places)

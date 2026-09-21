from fastapi import APIRouter, Query
from fastapi.responses import JSONResponse

from ..models.places import PlacesResponse
from ..services.ola_maps import OlaMapsError, SUPPORTED_LANGUAGES, search

router = APIRouter(prefix="/v1")

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


@router.get("/places/autocomplete", response_model=PlacesResponse)
def autocomplete(
    q: str = Query(default=""),
    language: str = Query(default="en"),
    lat: float | None = Query(default=None),
    lng: float | None = Query(default=None),
):
    """Search places, proxying the privileged upstream provider.

    Client input is treated as untrusted: query length, language, and coordinate
    pairing are validated here before anything reaches the upstream API.
    """
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
        places = search(query, normalized_language, lat=lat, lng=lng)
    except OlaMapsError as error:
        return _error(error.category)
    return PlacesResponse(places=places)
from pydantic import BaseModel, Field


class Place(BaseModel):
    address: str
    latitude: float = Field(ge=-90.0, le=90.0)
    longitude: float = Field(ge=-180.0, le=180.0)


class PlacesResponse(BaseModel):
    """Stable Ride Saathi API contract, independent of the upstream provider."""

    places: list[Place]


class ErrorDetail(BaseModel):
    code: str
    message: str


class ErrorResponse(BaseModel):
    error: ErrorDetail
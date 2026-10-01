"""Shared OpenAPI metadata for manually validated speech requests."""

from app.modules.destinations.schemas import ErrorResponse

SPEECH_ERROR_RESPONSES = {
    status: {
        "model": ErrorResponse,
        "description": description,
        "content": {"application/json": {"examples": {
            code: {"value": {"error": {
                "code": code, "message": "Speech temporarily unavailable",
            }}}
            for code in codes
        }}},
    }
    for status, description, codes in [
        (400, "Invalid body, content type, or parameters.", ["INVALID_REQUEST"]),
        (413, "Request body exceeds the byte limit.", ["INVALID_REQUEST"]),
        (429, "Client, global, or provider rate limit exceeded.", ["QUOTA"]),
        (499, "Request cancelled after client disconnect.", ["CANCELLED"]),
        (502, "Provider returned an invalid response.", ["INVALID_RESPONSE"]),
        (503, "Provider unavailable, misconfigured, or access denied.",
         ["UNAVAILABLE", "NOT_CONFIGURED", "ACCESS_DENIED"]),
    ]
}

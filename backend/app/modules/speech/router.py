"""Bounded, rate-limited speech requests. Text/audio is never persisted."""

import asyncio
import json
import time
from collections import OrderedDict, deque
from threading import Lock
from typing import Annotated, Literal

from fastapi import APIRouter, Depends, Request, Response
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field, ValidationError, field_validator

from app.core.cancellation import ClientDisconnected, until_disconnect
from app.core.config import settings
from app.dependencies.services import get_speech_provider
from app.integrations.speech.base import SpeechError, SpeechProvider
from app.modules.speech.docs import SPEECH_ERROR_RESPONSES

router = APIRouter(prefix="/speech", tags=["Speech"])
MAX_BODY_BYTES = 16_384


class SpeechRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)
    text: str = Field(min_length=1, max_length=2000, description="Text to speak; surrounding whitespace is trimmed. Must contain valid XML characters.", examples=["Where would you like to go?"])
    language: Literal["en", "hi", "te"] = Field(
        description="Selects the speech voice and locale; does not translate text. Supply text in the desired language."
    )

    @field_validator("text")
    @classmethod
    def valid_xml_text(cls, value: str) -> str:
        if any(not (
            c in "\t\n\r" or "\u0020" <= c <= "\ud7ff"
            or "\ue000" <= c <= "\ufffd" or "\U00010000" <= c <= "\U0010ffff"
        ) for c in value):
            raise ValueError("Invalid speech text")
        return value


def _error(code: str, status: int) -> JSONResponse:
    return JSONResponse(
        status_code=status,
        content={"error": {"code": code, "message": "Speech temporarily unavailable"}},
        headers={"Cache-Control": "no-store"},
    )


_lock = Lock()
_clients: OrderedDict[str, deque[float]] = OrderedDict()
_global_hits: deque[float] = deque()


def _reset_rate_limit() -> None:
    with _lock:
        _clients.clear()
        _global_hits.clear()


def _admit(request: Request) -> bool:
    key = request.client.host if request.client else "unknown"
    if settings.trust_proxy_headers:
        key = request.headers.get("x-forwarded-for", "").split(",")[0].strip() or key
    with _lock:
        now = time.monotonic()
        cutoff = now - 60
        while _global_hits and _global_hits[0] <= cutoff:
            _global_hits.popleft()
        while _clients and next(iter(_clients.values()))[-1] <= cutoff:
            _clients.popitem(last=False)
        hits = _clients.get(key, deque())
        while hits and hits[0] <= cutoff:
            hits.popleft()
        if (len(hits) >= settings.tts_rate_limit_per_minute
                or len(_global_hits) >= settings.tts_global_rate_limit_per_minute):
            return False
        hits.append(now)
        _global_hits.append(now)
        _clients[key] = hits
        _clients.move_to_end(key)
        return True


@router.post("/synthesize", summary="Synthesize spoken audio", response_class=Response, responses={
    **SPEECH_ERROR_RESPONSES,
    200: {"description": "MP3 audio. Responses use Cache-Control: no-store.", "content": {"audio/mpeg": {"schema": {"type": "string", "format": "binary"}}}},
}, openapi_extra={"requestBody": {"required": True, "content": {
    "application/json": {"schema": SpeechRequest.model_json_schema()},
}}})
async def synthesize(
    request: Request, provider: Annotated[SpeechProvider, Depends(get_speech_provider)],
) -> Response:
    """Convert English, Hindi, or Telugu text to speech.

    Send JSON with text and language. The body is limited to 16,384 bytes and
    text to 2,000 characters. Extra fields are rejected. Text is not persisted.
    Language selects the voice, not translation: Hindi and Telugu speech require
    Hindi and Telugu input text respectively.
    """
    if not _admit(request):
        return _error("QUOTA", 429)
    if request.headers.get("content-type", "").split(";")[0].strip() != "application/json":
        return _error("INVALID_REQUEST", 400)
    # Read a bounded body ourselves: FastAPI's default validation errors echo
    # rejected input, which could include private addresses/names.
    body = bytearray()
    try:
        async with asyncio.timeout(5):
            async for chunk in request.stream():
                if len(body) + len(chunk) > MAX_BODY_BYTES:
                    return _error("INVALID_REQUEST", 413)
                body.extend(chunk)
        payload = SpeechRequest.model_validate(json.loads(body))
    except (ValueError, ValidationError, TimeoutError, RecursionError):
        return _error("INVALID_REQUEST", 400)
    try:
        audio = await until_disconnect(request, provider.synthesize(payload.text, payload.language))
    except ClientDisconnected:
        return _error("CANCELLED", 499)
    except SpeechError as error:
        status = {"NOT_CONFIGURED": 503, "ACCESS_DENIED": 503, "QUOTA": 429,
                  "UNAVAILABLE": 503, "INVALID_RESPONSE": 502, "CANCELLED": 499}[error.category]
        return _error(error.category, status)
    return Response(audio, media_type="audio/mpeg", headers={"Cache-Control": "no-store"})

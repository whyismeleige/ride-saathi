"""One bounded voice turn: STT -> structured LLM interpretation -> client decisions.

The client performs grounded saved-place/map resolution and sends the resulting
prompt to /synthesize. No model output can invoke a ride handoff.
"""

import asyncio
from typing import Annotated

from fastapi import APIRouter, Depends, Request
from fastapi.responses import JSONResponse
from pydantic import Field

from app.core.cancellation import ClientDisconnected, until_disconnect
from app.integrations.language.azure import AzureLanguageProvider, Interpretation, LanguageProvider
from app.integrations.speech.base import SpeechError
from app.integrations.speech.transcription import (
    MAX_WAV_BYTES,
    AzureTranscriptionProvider,
    TranscriptionProvider,
    validate_wav,
)
from app.modules.speech.docs import SPEECH_ERROR_RESPONSES
from app.modules.speech.router import _admit, _error

router = APIRouter(prefix="/speech", tags=["Speech"])


class VoiceTurnResponse(Interpretation):
    transcript: str = Field(description="Recognized words; empty when no speech is recognized.")
    degraded: bool = Field(description="True when interpretation failed; the transcript remains available for client-side parsing.")


def get_transcription_provider() -> TranscriptionProvider:
    return AzureTranscriptionProvider()


def get_language_provider() -> LanguageProvider:
    return AzureLanguageProvider()


async def run_turn(audio: bytes, language: str, context: str, stt: TranscriptionProvider, llm: LanguageProvider) -> dict:
    transcript = await stt.transcribe(audio, language)
    interpretation = Interpretation(intent="unclear", destination_query=None)
    degraded = False
    if transcript:
        try:
            interpretation = await llm.interpret(transcript, language, context)
        except SpeechError:
            # Preserve original words for deterministic on-device parsing.
            degraded = True
    return {"transcript": transcript, **interpretation.model_dump(), "degraded": degraded}


@router.post("/turn", summary="Transcribe and interpret a voice turn", response_model=VoiceTurnResponse,
responses=SPEECH_ERROR_RESPONSES, openapi_extra={
"parameters": [
    {"name": "language", "in": "query", "required": True,
     "description": "Recognition language.", "schema": {"type": "string", "enum": ["en", "hi", "te"]}},
    {"name": "context", "in": "query", "required": True,
     "description": "Current client screen or conversation state.",
     "schema": {"type": "string", "enum": ["home", "editing", "choices", "confirmation"]}},
], "requestBody": {"required": True, "content": {
    "audio/wav": {"schema": {"type": "string", "format": "binary"}},
}}})
async def voice_turn(
    request: Request,
    stt: Annotated[TranscriptionProvider, Depends(get_transcription_provider)],
    llm: Annotated[LanguageProvider, Depends(get_language_provider)],
) -> JSONResponse:
    """Send raw WAV bytes (not multipart): mono, 16 kHz, 16-bit uncompressed PCM,
    0.1–20 seconds, at most 644,096 bytes. Invalid parameters or audio return 400.

    Returns a transcript and destination/command/unclear interpretation. If the
    language provider fails, returns 200 with degraded=true and intent=unclear.
    The client resolves destinations and confirms rides; this endpoint cannot
    book rides. Audio is not persisted. Responses use Cache-Control: no-store.
    """
    if not _admit(request):
        return _error("QUOTA", 429)
    language = request.query_params.get("language", "")
    context = request.query_params.get("context", "")
    if (language not in {"en", "hi", "te"} or context not in {"home", "editing", "choices", "confirmation"}
            or request.headers.get("content-type", "").split(";")[0] != "audio/wav"):
        return _error("INVALID_REQUEST", 400)
    body = bytearray()
    try:
        async with asyncio.timeout(10):
            async for chunk in request.stream():
                if len(body) + len(chunk) > MAX_WAV_BYTES:
                    return _error("INVALID_REQUEST", 413)
                body.extend(chunk)
        audio = bytes(body)
        validate_wav(audio)
        async with asyncio.timeout(25):
            result = await until_disconnect(request, run_turn(audio, language, context, stt, llm))
    except TimeoutError:
        return _error("UNAVAILABLE", 503)
    except ClientDisconnected:
        return _error("CANCELLED", 499)
    except SpeechError as error:
        return _error(error.category, {"INVALID_REQUEST": 400, "QUOTA": 429, "CANCELLED": 499,
                                      "INVALID_RESPONSE": 502}.get(error.category, 503))
    return JSONResponse(result, headers={"Cache-Control": "no-store"})

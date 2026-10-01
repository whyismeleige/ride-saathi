"""Azure STT for bounded 16 kHz mono PCM WAV utterances."""

import asyncio
import io
import json
import wave
from typing import Protocol

import httpx

from app.core.config import settings
from app.integrations.speech.azure import LOCALES
from app.integrations.speech.base import SpeechError

MAX_WAV_BYTES = 20 * 16000 * 2 + 4096


def validate_wav(audio: bytes) -> None:
    try:
        with wave.open(io.BytesIO(audio), "rb") as wav:
            frames = wav.getnframes()
            if (wav.getnchannels() != 1 or wav.getsampwidth() != 2
                    or wav.getframerate() != 16000 or wav.getcomptype() != "NONE"
                    or not 1600 <= frames <= 20 * 16000
                    or len(wav.readframes(frames)) != frames * 2):
                raise SpeechError("INVALID_REQUEST")
    except (wave.Error, EOFError):
        raise SpeechError("INVALID_REQUEST") from None


class TranscriptionProvider(Protocol):
    async def transcribe(self, audio: bytes, language: str) -> str: ...


class AzureTranscriptionProvider:
    async def transcribe(self, audio: bytes, language: str) -> str:
        if not settings.azure_speech_key or not settings.azure_speech_endpoint:
            raise SpeechError("NOT_CONFIGURED")
        url = f"{settings.azure_speech_endpoint}/stt/speech/recognition/conversation/cognitiveservices/v1"
        try:
            async with asyncio.timeout(settings.stt_timeout_seconds):
                async with httpx.AsyncClient(timeout=settings.stt_timeout_seconds, follow_redirects=False) as client:
                    async with client.stream(
                        "POST", url, params={"language": LOCALES[language], "format": "simple"},
                        headers={"Ocp-Apim-Subscription-Key": settings.azure_speech_key,
                                 "Content-Type": "audio/wav; codecs=audio/pcm; samplerate=16000",
                                 "Accept": "application/json"}, content=audio,
                    ) as response:
                        if response.status_code in {401, 403}:
                            raise SpeechError("ACCESS_DENIED")
                        if response.status_code == 429:
                            raise SpeechError("QUOTA")
                        if response.status_code != 200:
                            raise SpeechError("UNAVAILABLE")
                        body = bytearray()
                        async for chunk in response.aiter_bytes():
                            if len(body) + len(chunk) > 16_384:
                                raise SpeechError("INVALID_RESPONSE")
                            body.extend(chunk)
                        payload = json.loads(body)
                        if not isinstance(payload, dict):
                            raise SpeechError("INVALID_RESPONSE")
                        status = payload.get("RecognitionStatus")
                        if status in {"NoMatch", "InitialSilenceTimeout", "BabbleTimeout"}:
                            return ""
                        text = payload.get("DisplayText")
                        if status != "Success" or not isinstance(text, str) or not 1 <= len(text.strip()) <= 2000:
                            raise SpeechError("INVALID_RESPONSE")
                        return text.strip()
        except (httpx.HTTPError, TimeoutError):
            raise SpeechError("UNAVAILABLE") from None
        except (ValueError, RecursionError):
            raise SpeechError("INVALID_RESPONSE") from None

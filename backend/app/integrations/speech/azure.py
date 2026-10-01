"""Azure Neural TTS: escaped SSML in, bounded MP3 audio out; no content logging."""

import asyncio
from xml.sax.saxutils import escape, quoteattr

import httpx

from app.core.config import settings
from app.integrations.speech.base import MAX_AUDIO_BYTES, SpeechError

LOCALES = {"en": "en-IN", "hi": "hi-IN", "te": "te-IN"}
# Only these default voices have documented cheerful-style support. Keep other
# configured voices (including Telugu Shruti) on their native intonation.
EXPRESSIVE_VOICES = {"en-IN-NeerjaNeural", "hi-IN-SwaraNeural"}


class AzureSpeechProvider:
    async def synthesize(self, text: str, language: str) -> bytes:
        if not settings.azure_speech_key or not settings.azure_speech_region:
            raise SpeechError("NOT_CONFIGURED")
        voice = getattr(settings, f"tts_voice_{language}")
        # Never interpret a spoken address or user name as markup.
        delivery = f'<prosody rate="+22%">{escape(text)}</prosody>'
        if voice in EXPRESSIVE_VOICES:
            delivery = (
                '<mstts:express-as style="cheerful" styledegree="1.25">'
                f'{delivery}</mstts:express-as>'
            )
        ssml = (
            f'<speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" '
            f'xmlns:mstts="https://www.w3.org/2001/mstts" '
            f'xml:lang="{LOCALES[language]}"><voice name={quoteattr(voice)}>'
            f'{delivery}</voice></speak>'
        )
        url = (
            f"https://{settings.azure_speech_region}.tts.speech.microsoft.com"
            "/cognitiveservices/v1"
        )
        headers = {
            "Ocp-Apim-Subscription-Key": settings.azure_speech_key,
            "Content-Type": "application/ssml+xml",
            "X-Microsoft-OutputFormat": "audio-24khz-48kbitrate-mono-mp3",
            "User-Agent": "ride-saathi-backend",
        }
        try:
            async with asyncio.timeout(settings.tts_timeout_seconds):
                async with httpx.AsyncClient(
                    timeout=settings.tts_timeout_seconds, follow_redirects=False,
                ) as client:
                    async with client.stream(
                        "POST", url, headers=headers, content=ssml.encode("utf-8"),
                    ) as response:
                        if response.status_code in (401, 403):
                            raise SpeechError("ACCESS_DENIED")
                        if response.status_code == 429:
                            raise SpeechError("QUOTA")
                        if response.status_code != 200:
                            raise SpeechError("UNAVAILABLE")
                        media_type = response.headers.get("content-type", "").split(";")[0]
                        if media_type not in {"audio/mpeg", "audio/mp3", "application/octet-stream"}:
                            raise SpeechError("INVALID_RESPONSE")
                        audio = bytearray()
                        async for chunk in response.aiter_bytes():
                            if len(audio) + len(chunk) > MAX_AUDIO_BYTES:
                                raise SpeechError("INVALID_RESPONSE")
                            audio.extend(chunk)
                        if not audio:
                            raise SpeechError("INVALID_RESPONSE")
                        return bytes(audio)
        except (httpx.HTTPError, TimeoutError):
            raise SpeechError("UNAVAILABLE") from None

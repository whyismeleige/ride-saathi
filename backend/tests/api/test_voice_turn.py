import asyncio
import io
import wave

import pytest

from app.core.cancellation import ClientDisconnected
from app.integrations.language.azure import Interpretation
from app.integrations.speech.base import SpeechError
from app.modules.speech.turn import (
    get_language_provider,
    get_transcription_provider,
    until_disconnect,
)


def wav(rate=16000, channels=1, width=2, seconds=1):
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as writer:
        writer.setparams((channels, width, rate, 0, "NONE", "not compressed"))
        writer.writeframes(b"\0" * rate * channels * width * seconds)
    return buffer.getvalue()


@pytest.fixture
def providers(override_dependency):
    class STT:
        calls = []
        transcript = "Take me to Apollo Hospital"
        async def transcribe(self, audio, language):
            self.calls.append((audio, language))
            return self.transcript

    class LLM:
        calls = []
        fail = False
        async def interpret(self, transcript, language, context):
            self.calls.append((transcript, language, context))
            if self.fail:
                raise SpeechError("UNAVAILABLE")
            return Interpretation(intent="destination", destination_query="Apollo Hospital")

    stt, llm = STT(), LLM()
    override_dependency[get_transcription_provider] = lambda: stt
    override_dependency[get_language_provider] = lambda: llm
    return stt, llm


def post(client, audio=None, language="en", context="home"):
    return client.post(f"/v1/speech/turn?language={language}&context={context}", content=audio if audio is not None else wav(), headers={"Content-Type": "audio/wav"})


@pytest.mark.parametrize("language", ["en", "hi", "te"])
def test_stt_then_llm_pipeline(client, providers, language):
    stt, llm = providers
    response = post(client, language=language)
    assert response.status_code == 200
    assert response.json() == {"transcript": stt.transcript, "intent": "destination", "destination_query": "Apollo Hospital", "degraded": False}
    assert llm.calls == [(stt.transcript, language, "home")]
    assert stt.calls[0][1] == language
    assert response.headers["Cache-Control"] == "no-store"


def test_llm_failure_preserves_original_words_for_deterministic_parsing(client, providers):
    stt, llm = providers
    stt.transcript = "No, do not confirm"
    llm.fail = True
    response = post(client, context="confirmation")
    assert response.json() == {"transcript": stt.transcript, "intent": "unclear", "destination_query": None, "degraded": True}


def test_silence_never_reaches_llm(client, providers):
    stt, llm = providers
    stt.transcript = ""
    assert post(client).status_code == 200
    assert not llm.calls


@pytest.mark.parametrize("audio", [b"not a wav", wav(rate=8000), wav(channels=2), wav(width=1), wav()[:-10], wav(seconds=21)])
def test_invalid_audio_never_reaches_azure(client, providers, audio):
    response = post(client, audio=audio)
    assert response.status_code in {400, 413}
    assert response.json()["error"]["code"] == "INVALID_REQUEST"
    assert not providers[0].calls


@pytest.mark.parametrize("language,context", [("fr", "home"), ("en", "book")])
def test_invalid_turn_context(client, providers, language, context):
    assert post(client, language=language, context=context).status_code == 400
    assert not providers[0].calls


@pytest.mark.asyncio
async def test_client_disconnect_cancels_inflight_pipeline():
    started = asyncio.Event()
    cancelled = asyncio.Event()

    async def work():
        started.set()
        try:
            await asyncio.sleep(30)
        finally:
            cancelled.set()

    class Disconnected:
        async def is_disconnected(self):
            return True

    with pytest.raises(ClientDisconnected):
        await until_disconnect(Disconnected(), work())
    assert started.is_set()
    assert cancelled.is_set()

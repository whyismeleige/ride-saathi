import asyncio
from xml.etree import ElementTree

import httpx
import pytest

from app.core.config import Settings, settings
from app.integrations.speech.azure import AzureSpeechProvider
from app.integrations.speech.base import MAX_AUDIO_BYTES, SpeechError


@pytest.fixture
def transport(monkeypatch):
    monkeypatch.setattr(settings, "azure_speech_key", "test-secret")
    monkeypatch.setattr(settings, "azure_speech_region", "centralindia")
    original = httpx.AsyncClient

    def install(handler):
        monkeypatch.setattr(httpx, "AsyncClient", lambda **kwargs: original(transport=httpx.MockTransport(handler), **kwargs))

    return install


@pytest.mark.asyncio
@pytest.mark.parametrize("language,voice", [("en", "en-IN-NeerjaNeural"), ("hi", "hi-IN-SwaraNeural"), ("te", "te-IN-ShrutiNeural")])
async def test_voice_selection_and_ssml_escaping(transport, language, voice):
    text = 'Home & clinic <audio src="https://invalid.test"/> घर ఇల్లు'

    def handle(request):
        assert str(request.url) == "https://centralindia.tts.speech.microsoft.com/cognitiveservices/v1"
        assert request.headers["Ocp-Apim-Subscription-Key"] == "test-secret"
        assert request.headers["X-Microsoft-OutputFormat"] == "audio-24khz-48kbitrate-mono-mp3"
        root = ElementTree.fromstring(request.content)
        ns = {"s": "http://www.w3.org/2001/10/synthesis", "mstts": "https://www.w3.org/2001/mstts"}
        assert root.find("s:voice", ns).attrib["name"] == voice
        prosody = root.find(".//s:prosody", ns)
        assert prosody.text == text
        assert prosody.attrib == {"rate": "+22%"}
        expression = root.find("s:voice/mstts:express-as", ns)
        if language in {"en", "hi"}:
            assert expression.attrib == {"style": "cheerful", "styledegree": "1.25"}
        else:
            assert expression is None
        assert not root.findall(".//s:audio", ns)
        return httpx.Response(200, content=b"ID3-audio", headers={"Content-Type": "audio/mpeg"})

    transport(handle)
    assert await AzureSpeechProvider().synthesize(text, language) == b"ID3-audio"


@pytest.mark.asyncio
async def test_replacement_voice_uses_native_intonation(transport, monkeypatch):
    monkeypatch.setattr(settings, "tts_voice_hi", "hi-IN-MadhurNeural")

    def handle(request):
        root = ElementTree.fromstring(request.content)
        ns = {"s": "http://www.w3.org/2001/10/synthesis", "mstts": "https://www.w3.org/2001/mstts"}
        assert root.find("s:voice", ns).attrib["name"] == "hi-IN-MadhurNeural"
        assert root.find(".//mstts:express-as", ns) is None
        assert root.find("s:voice/s:prosody", ns).text == "आप कहाँ जाना चाहते हैं?"
        return httpx.Response(200, content=b"ID3-audio", headers={"Content-Type": "audio/mpeg"})

    transport(handle)
    assert await AzureSpeechProvider().synthesize("आप कहाँ जाना चाहते हैं?", "hi") == b"ID3-audio"


@pytest.mark.asyncio
@pytest.mark.parametrize("status,category", [(401, "ACCESS_DENIED"), (403, "ACCESS_DENIED"), (429, "QUOTA"), (500, "UNAVAILABLE"), (302, "UNAVAILABLE")])
async def test_upstream_failures_do_not_leak_content(transport, status, category):
    transport(lambda request: httpx.Response(status, text="test-secret private address", headers={"Location": "https://invalid.test"}))
    with pytest.raises(SpeechError, match=f"^{category}$"):
        await AzureSpeechProvider().synthesize("Private address", "en")


@pytest.mark.asyncio
@pytest.mark.parametrize("content,media_type", [(b"", "audio/mpeg"), (b"{}", "application/json"), (b"x" * (MAX_AUDIO_BYTES + 1), "audio/mpeg")])
async def test_empty_non_audio_and_oversized_audio_rejected(transport, content, media_type):
    transport(lambda request: httpx.Response(200, content=content, headers={"Content-Type": media_type}))
    with pytest.raises(SpeechError, match="^INVALID_RESPONSE$"):
        await AzureSpeechProvider().synthesize("Home", "en")


@pytest.mark.asyncio
async def test_timeout_has_fixed_error(transport, monkeypatch):
    monkeypatch.setattr(settings, "tts_timeout_seconds", 0.01)

    async def slow(request):
        await asyncio.sleep(1)
        return httpx.Response(200)

    transport(slow)
    with pytest.raises(SpeechError, match="^UNAVAILABLE$"):
        await AzureSpeechProvider().synthesize("Home", "en")


@pytest.mark.asyncio
async def test_missing_configuration_never_calls_provider(monkeypatch):
    monkeypatch.setattr(settings, "azure_speech_key", "")
    with pytest.raises(SpeechError, match="^NOT_CONFIGURED$"):
        await AzureSpeechProvider().synthesize("Home", "en")


def test_speech_configuration_hides_key_and_rejects_host_injection():
    config = Settings(_env_file=None, azure_speech_key="private-speech-key")
    assert "private-speech-key" not in repr(config)
    with pytest.raises(ValueError):
        Settings(_env_file=None, azure_speech_region="centralindia.invalid.test/")

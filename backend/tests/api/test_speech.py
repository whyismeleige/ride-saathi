import logging

import pytest

from app.core.config import settings
from app.integrations.speech.base import SpeechError
from app.modules.speech import router as speech_router
from app.modules.speech.router import get_speech_provider


@pytest.fixture
def speech(override_dependency):
    class FakeSpeech:
        calls = []
        error = None

        async def synthesize(self, text, language):
            self.calls.append((text, language))
            if self.error:
                raise SpeechError(self.error)
            return b"ID3-test-audio"

    provider = FakeSpeech()
    override_dependency[get_speech_provider] = lambda: provider
    return provider


@pytest.mark.parametrize("language,text", [("en", "Go home"), ("hi", "घर चलें"), ("te", "ఇంటికి వెళ్ళండి")])
def test_speech_returns_audio_without_persisting_or_echoing_text(client, speech, language, text, caplog):
    caplog.set_level(logging.INFO, logger="ride-saathi")
    response = client.post("/v1/speech/synthesize", json={"text": text, "language": language})
    assert response.status_code == 200
    assert response.content == b"ID3-test-audio"
    assert response.headers["content-type"] == "audio/mpeg"
    assert response.headers["cache-control"] == "no-store"
    assert speech.calls == [(text, language)]
    assert text not in caplog.text


@pytest.mark.parametrize("body", [
    {}, {"text": " ", "language": "en"}, {"text": "x" * 2001, "language": "en"},
    {"text": "Private address", "language": "fr"},
    {"text": "Private address", "language": "en", "voice": "arbitrary"},
    {"text": "Private\u0000address", "language": "en"},
    {"text": "Private\ud800address", "language": "en"},
    {"text": 123, "language": "en"}, [], None,
])
def test_invalid_speech_never_reaches_provider_or_echoes_input(client, speech, body):
    # ASCII escapes permit testing invalid surrogate code points over valid UTF-8.
    import json

    response = client.post("/v1/speech/synthesize", content=json.dumps(body), headers={"Content-Type": "application/json"})
    assert response.status_code == 400
    assert response.json()["error"]["code"] == "INVALID_REQUEST"
    assert "Private" not in response.text
    assert not speech.calls


def test_body_size_is_bounded_before_parsing(client, speech):
    response = client.post("/v1/speech/synthesize", content=b" " * 16_385, headers={"Content-Type": "application/json"})
    assert response.status_code == 413
    assert not speech.calls


@pytest.mark.parametrize("content,media_type", [(b"not-json", "application/json"), (b"{}", "text/plain")])
def test_malformed_body(client, speech, content, media_type):
    assert client.post("/v1/speech/synthesize", content=content, headers={"Content-Type": media_type}).status_code == 400
    assert not speech.calls


@pytest.mark.parametrize("error,status", [
    ("NOT_CONFIGURED", 503), ("ACCESS_DENIED", 503), ("QUOTA", 429),
    ("UNAVAILABLE", 503), ("INVALID_RESPONSE", 502),
])
def test_provider_errors_are_sanitized(client, speech, error, status):
    speech.error = error
    response = client.post("/v1/speech/synthesize", json={"text": "Private address", "language": "en"})
    assert response.status_code == status
    assert response.json() == {"error": {"code": error, "message": "Speech temporarily unavailable"}}
    assert response.headers["cache-control"] == "no-store"


def test_per_client_limit_ignores_untrusted_forwarding(client, speech, monkeypatch):
    monkeypatch.setattr(settings, "trust_proxy_headers", False)
    monkeypatch.setattr(settings, "tts_rate_limit_per_minute", 1)
    assert client.post("/v1/speech/synthesize", json={"text": "Home", "language": "en"}).status_code == 200
    assert client.post("/v1/speech/synthesize", json={"text": "Home", "language": "en"}, headers={"X-Forwarded-For": "other"}).status_code == 429
    assert len(speech.calls) == 1


def test_global_limit_covers_distinct_clients_and_expires(client, speech, monkeypatch):
    monkeypatch.setattr(settings, "trust_proxy_headers", True)
    monkeypatch.setattr(settings, "tts_global_rate_limit_per_minute", 2)
    clock = [100.0]
    monkeypatch.setattr(speech_router.time, "monotonic", lambda: clock[0])
    for ip, status in [("192.0.2.1", 200), ("192.0.2.2", 200), ("192.0.2.3", 429)]:
        assert client.post("/v1/speech/synthesize", json={"text": "Home", "language": "en"}, headers={"X-Forwarded-For": ip}).status_code == status
    clock[0] = 161.0
    assert client.post("/v1/speech/synthesize", json={"text": "Home", "language": "en"}).status_code == 200
    assert len(speech_router._clients) == 1

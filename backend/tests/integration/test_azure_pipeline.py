import json

import httpx
import pytest

from app.core.config import Settings, settings
from app.integrations.language.azure import AzureLanguageProvider
from app.integrations.speech.base import SpeechError
from app.integrations.speech.transcription import AzureTranscriptionProvider


@pytest.fixture
def transport(monkeypatch):
    monkeypatch.setattr(settings, "azure_speech_key", "speech-secret")
    monkeypatch.setattr(settings, "azure_speech_endpoint", "https://speech-test.cognitiveservices.azure.com")
    monkeypatch.setattr(settings, "azure_openai_key", "llm-secret")
    monkeypatch.setattr(settings, "azure_openai_endpoint", "https://llm-test.openai.azure.com")
    monkeypatch.setattr(settings, "azure_openai_deployment", "ride-language")
    original = httpx.AsyncClient
    def install(handler):
        monkeypatch.setattr(httpx, "AsyncClient", lambda **kwargs: original(transport=httpx.MockTransport(handler), **kwargs))
    return install


@pytest.mark.asyncio
@pytest.mark.parametrize("language", ["en", "hi", "te"])
async def test_azure_stt_contract(transport, language):
    def handle(request):
        assert request.url.path == "/stt/speech/recognition/conversation/cognitiveservices/v1"
        assert request.url.params["language"] == f"{language}-IN"
        assert request.headers["Ocp-Apim-Subscription-Key"] == "speech-secret"
        assert "samplerate=16000" in request.headers["Content-Type"]
        assert request.content == b"test-wav"
        return httpx.Response(200, json={"RecognitionStatus": "Success", "DisplayText": " Apollo Hospital "})
    transport(handle)
    assert await AzureTranscriptionProvider().transcribe(b"test-wav", language) == "Apollo Hospital"


@pytest.mark.asyncio
@pytest.mark.parametrize("status,expected", [(401, "ACCESS_DENIED"), (429, "QUOTA"), (500, "UNAVAILABLE"), (302, "UNAVAILABLE")])
async def test_stt_errors_are_sanitized(transport, status, expected):
    transport(lambda request: httpx.Response(status, text="private recording and credentials"))
    with pytest.raises(SpeechError, match=f"^{expected}$"):
        await AzureTranscriptionProvider().transcribe(b"test-wav", "en")


@pytest.mark.asyncio
@pytest.mark.parametrize("payload", [{"RecognitionStatus": "Success"}, {"RecognitionStatus": "Error"}, [], {"RecognitionStatus": "Success", "DisplayText": "x" * 2001}])
async def test_stt_invalid_responses(transport, payload):
    transport(lambda request: httpx.Response(200, json=payload))
    with pytest.raises(SpeechError, match="INVALID_RESPONSE"):
        await AzureTranscriptionProvider().transcribe(b"test-wav", "en")


@pytest.mark.asyncio
async def test_llm_structured_contract_and_grounded_destination(transport):
    def handle(request):
        assert request.url.path == "/openai/v1/chat/completions"
        assert request.headers["api-key"] == "llm-secret"
        payload = json.loads(request.content)
        assert payload["model"] == "ride-language"
        assert payload["store"] is False
        assert payload["response_format"]["json_schema"]["strict"] is True
        assert payload["response_format"]["json_schema"]["schema"]["additionalProperties"] is False
        assert json.loads(payload["messages"][1]["content"])["transcript"] == "Take me to Apollo Hospital"
        return httpx.Response(200, json={"choices": [{"finish_reason": "stop", "message": {"content": json.dumps({"intent": "destination", "destination_query": "Apollo Hospital"})}}]})
    transport(handle)
    result = await AzureLanguageProvider().interpret("Take me to Apollo Hospital", "en", "home")
    assert result.destination_query == "Apollo Hospital"


@pytest.mark.asyncio
@pytest.mark.parametrize("output,context", [
    ({"intent": "destination", "destination_query": "Invented Clinic"}, "home"),
    ({"intent": "destination", "destination_query": "Apollo Hospital"}, "confirmation"),
    ({"intent": "destination", "destination_query": "Apollo Hospital", "latitude": 17.4}, "home"),
    ({"intent": "confirm", "destination_query": None}, "confirmation"),
    ({"intent": "command", "destination_query": "Apollo Hospital"}, "home"),
])
async def test_unsafe_or_ungrounded_model_results_are_rejected(transport, output, context):
    transport(lambda request: httpx.Response(200, json={"choices": [{"finish_reason": "stop", "message": {"content": json.dumps(output)}}]}))
    with pytest.raises(SpeechError, match="INVALID_RESPONSE"):
        await AzureLanguageProvider().interpret("Take me to Apollo Hospital", "en", context)


@pytest.mark.asyncio
@pytest.mark.parametrize("choice", [{"finish_reason": "length", "message": {"content": "{}"}}, {"finish_reason": "stop", "message": {"refusal": "No"}}])
async def test_model_refusals_and_truncation_do_not_execute(transport, choice):
    transport(lambda request: httpx.Response(200, json={"choices": [choice]}))
    with pytest.raises(SpeechError, match="INVALID_RESPONSE"):
        await AzureLanguageProvider().interpret("Home", "en", "home")


@pytest.mark.parametrize("endpoint", ["http://llm.openai.azure.com", "https://llm.openai.azure.com.evil.test", "https://user:secret@llm.openai.azure.com", "https://llm.openai.azure.com/unexpected"])
def test_only_azure_resource_roots_are_accepted(endpoint):
    with pytest.raises(ValueError):
        Settings(_env_file=None, azure_openai_endpoint=endpoint)


@pytest.mark.parametrize("path", ["", "/", "/openai/v1", "/openai/v1/"])
def test_openai_endpoint_is_normalized_to_resource_root(path):
    configured = Settings(
        _env_file=None, azure_openai_endpoint=f"https://llm.openai.azure.com{path}"
    )
    assert configured.azure_openai_endpoint == "https://llm.openai.azure.com"


def test_llm_key_is_not_in_settings_repr():
    assert "private-llm-key" not in repr(Settings(_env_file=None, azure_openai_key="private-llm-key"))

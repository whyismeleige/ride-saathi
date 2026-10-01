"""Documentation covers contracts that FastAPI cannot infer from raw requests."""


def test_speech_documentation_covers_raw_requests_and_responses(client):
    schema = client.get("/openapi.json").json()
    turn = schema["paths"]["/v1/speech/turn"]["post"]
    params = {p["name"]: p for p in turn["parameters"]}
    assert params["language"]["required"]
    assert params["language"]["schema"]["enum"] == ["en", "hi", "te"]
    assert params["context"]["required"]
    assert "audio/wav" in turn["requestBody"]["content"]
    ref = turn["responses"]["200"]["content"]["application/json"]["schema"]["$ref"]
    response = schema["components"]["schemas"][ref.rsplit("/", 1)[1]]
    assert set(response["required"]) == {"transcript", "intent", "destination_query", "degraded"}

    synthesis = schema["paths"]["/v1/speech/synthesize"]["post"]
    body = synthesis["requestBody"]["content"]["application/json"]["schema"]
    assert set(body["required"]) == {"text", "language"}
    assert body["properties"]["text"]["maxLength"] == 2000
    assert set(synthesis["responses"]["200"]["content"]) == {"audio/mpeg"}
    for operation in (turn, synthesis):
        assert operation["tags"] == ["Speech"]
        for status in (400, 413, 429, 499, 502, 503):
            content = operation["responses"][str(status)]["content"]["application/json"]
            assert content["schema"]["$ref"].endswith("/ErrorResponse")
            assert content["examples"]


def test_places_and_readiness_document_error_contracts(client):
    paths = client.get("/openapi.json").json()["paths"]
    places = paths["/v1/places/autocomplete"]["get"]
    assert {"400", "403", "422", "429", "502", "503"} <= places["responses"].keys()
    assert set(places["responses"]["503"]["content"]["application/json"]["examples"]) == {
        "NOT_CONFIGURED", "UNAVAILABLE",
    }
    readiness = paths["/ready"]["get"]["responses"]
    for status in ("200", "503"):
        assert "$ref" in readiness[status]["content"]["application/json"]["schema"]

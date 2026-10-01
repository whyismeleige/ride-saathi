# Azure voice pipeline and barge-in

## What runs where

```text
Android microphone (16 kHz mono PCM)
  -> local WebRTC speech detection + hardware acoustic echo cancellation
  -> bounded utterance in memory
  -> POST /v1/speech/turn
       -> Azure Speech STT
       -> Azure OpenAI structured destination interpretation
  -> original transcript + validated destination phrase
  -> Android saved-place matching / maps search / explicit confirmation
  -> POST /v1/speech/synthesize -> Azure neural TTS -> Android playback
```

This is an **utterance-based pipeline**, with final STT results after a pause.
It does not stream partial Azure transcripts or model tokens. Barge-in is local
and does not wait for transcription or the model. Existing touch/manual entry
continues to work; microphone input now uses Azure rather than Android's
SpeechRecognizer service. Device TTS remains the speech-output fallback.

The LLM extracts a short destination phrase copied verbatim from the recognized
text. It cannot invent coordinates, select a result, book a ride, or confirm
handoff. Original transcript commands and negative confirmation retain their
existing deterministic handling. Saved-place matches retain precedence. An
unavailable model, refusal, malformed response, or ungrounded phrase falls back
to existing transcript parsing; STT failures do not perform an action.

## Configure Azure

Set these values in backend secrets or the ignored `backend/.env` file:

```dotenv
# Speech resource: both STT and TTS use this key.
AZURE_SPEECH_KEY=your-speech-resource-key
AZURE_SPEECH_REGION=centralindia
AZURE_SPEECH_ENDPOINT=https://your-speech-resource.cognitiveservices.azure.com

# Azure OpenAI resource and an existing compatible model deployment.
AZURE_OPENAI_ENDPOINT=https://your-language-resource.openai.azure.com
AZURE_OPENAI_KEY=your-language-resource-key
AZURE_OPENAI_DEPLOYMENT=your-deployment-name
```

Use the region and resource endpoint from your Azure portal. Endpoints are HTTPS
resource roots, without `/openai/v1`, API paths, credentials, or query strings.
The deployment must support Chat Completions structured outputs (`json_schema`).
The deployment name is supplied by your account; no model is auto-provisioned.
Azure Speech and Azure OpenAI may use separate resources, regions and keys.

Both Compose files forward these settings. Rebuild/restart the backend, then
build the Android app with `API_BASE_URL` pointing to it. No Azure key belongs in
the APK. No schema migration, cloud SDK, resource creation, or paid service call
is performed by installing this code.

Optional settings:

- `STT_TIMEOUT_SECONDS=10`, `LLM_TIMEOUT_SECONDS=8`; whole server turn: 25 seconds.
- The [TTS voice and timeout settings](ONLINE_TTS.md) remain available.
- `TTS_RATE_LIMIT_PER_MINUTE=30` and `TTS_GLOBAL_RATE_LIMIT_PER_MINUTE=120`
  now bound both voice turns and synthesis together, per process. Names remain
  compatible with the initial TTS configuration.

The endpoints remain unauthenticated like the existing maps proxy. Configure
access controls, gateway limits across workers, and Azure spending limits for
your deployment. Health/readiness probes do not contact paid providers and do
not validate cloud credentials.

## Barge-in behavior

Tap the microphone to start a foreground voice session. During spoken prompts
in that session, capture stays open. Eight consecutive 20 ms speech-positive
frames trigger interruption (160 ms detector window plus device scheduling).
Playback and pending network work are cancelled immediately on the app thread.
A 300 ms pre-roll preserves the start of the utterance; recording continues through
the interruption, then submits after 800 ms of silence.

- Speech classification uses WebRTC VAD, not just a loudness threshold.
- `VOICE_COMMUNICATION` capture and playback routing use Android's acoustic
  echo cancellation where available; the previous audio mode is restored.
- If the phone cannot enable AEC, automatic interruption during playback is
  disabled and a 300 ms echo tail is suppressed. The UI says to tap the microphone
  to interrupt; the tap stops audio and starts a fresh capture session.
- The user can also stop listening via the microphone control when audio is idle.
- Utterances are limited to 20 seconds. Overlong input is discarded, not submitted
  as a truncated command; the user must start again.
- Capture ends after 30 seconds of inactivity, on pause, destruction, language
  change, or leaving an eligible flow. It never resumes in the background.
- Cancellation invalidates generations, disconnects HTTP, stops/releases audio,
  and ignores late results. The server cancels STT/LLM/TTS work on observed client
  disconnect (best effort; work already processed by Azure may still be billed).
- Changing a selected destination/search page/language invalidates a pending
  interpretation. Only a completed current prompt can invoke a listen-after callback.

Hardware AEC and VAD effectiveness vary with the phone, route, volume and room.
The code cannot certify acoustic echo rejection or interruption latency without
real-device tests. In particular, test speakerphone, wired/Bluetooth headsets,
far-field speech, and background TV separately before relying on hands-free use.

## API and data handling

`POST /v1/speech/turn?language=en&context=home` accepts `Content-Type: audio/wav`.
Audio must be uncompressed 16-bit, 16 kHz, mono WAV, 0.1–20 seconds, at most
644,096 bytes. Languages: `en`, `hi`, `te`. Contexts: `home`, `editing`, `choices`,
`confirmation`. Successful responses have `Cache-Control: no-store`:

```json
{
  "transcript": "Take me to Apollo Hospital",
  "intent": "destination",
  "destination_query": "Apollo Hospital",
  "degraded": false
}
```

`degraded=true` means STT succeeded and the LLM was unavailable/unusable. It
preserves the original transcript for deterministic parsing. Silence yields an
empty transcript and no LLM call. Errors use the same fixed, privacy-safe shape
as the TTS endpoint. Invalid audio is rejected before Azure is called.

Microphone audio now goes to the backend and Azure Speech; recognized text goes
to Azure OpenAI. Spoken prompts (which may include names/addresses) go to Azure
Speech for TTS. Audio and text are bounded in memory, not persisted in the database,
not written to app audio files, and not logged. Azure OpenAI requests set
`store=false`; provider-side data handling remains subject to your Azure resource
policies. The implementation sends one turn, without a stored conversation history.

## Validation and rollout

Local verification on 2026-10-01:

- Backend Ruff passed; 159 non-PostgreSQL tests passed.
- Android debug and QA each passed 79 JVM tests.
- Debug/QA APK builds and QA instrumentation APK compilation passed.
- Debug/QA lint passed with zero errors and 22 existing warnings.
- `git diff --check` passed. No database schema changed.

Azure credentials/endpoints/deployment are not configured in this workspace, so
no live Azure calls or physical microphone/acoustic tests were performed.

Automated checks use fake Azure transports and synthetic PCM/WAV data. They cover
STT encoding/languages/errors, strict model schema, grounded extraction, confirmation
boundaries, disconnect cancellation, model fallback, VAD debounce, pre-roll,
echo gating, overlong input rejection, and stale TTS completion.

Live activation requires the Azure settings above. Before a pilot, test all three
languages, mixed-language addresses, negative confirmation, interruption at the
start/middle/end of a prompt, rapid repeated interruptions, network loss during
each stage, permission denial, app backgrounding, and device audio-focus changes.
No live Azure or physical-microphone quality result is implied by passing JVM/API tests.

References:

- [Azure STT short-audio REST API](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/rest-speech-to-text-short)
- [Azure OpenAI Chat Completions](https://learn.microsoft.com/en-us/rest/api/aifoundry/azureopenai/chat)
- [OpenAI structured outputs](https://developers.openai.com/api/docs/guides/structured-outputs)
- [Android acoustic echo cancellation](https://developer.android.com/reference/android/media/audiofx/AcousticEchoCanceler)
- [WebRTC VAD Android wrapper](https://github.com/gkonovalov/android-vad)

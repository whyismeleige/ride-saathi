# Online speech output

For Azure STT, Azure OpenAI interpretation, and local barge-in, see the
[Azure voice pipeline](AZURE_VOICE_PIPELINE.md).

Ride Saathi requests neural speech from **Azure Speech** through its own backend.
The selected app language chooses one of these server-side defaults:

| App language | Voice |
| --- | --- |
| English (India) | `en-IN-NeerjaNeural` |
| Hindi | `hi-IN-SwaraNeural` |
| Telugu | `te-IN-ShrutiNeural` |

Speech uses a brisker rate (122%) for prompts and address confirmation, including
the Android device fallback. Azure Neerja and Swara use an expressive `cheerful` style
at intensity `1.25`; Shruti and replacement voices keep their native intonation
without an unsupported style tag. These choices follow
[Azure's supported voice styles](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/language-support?tabs=tts).
These are synthesized voices, not a recording or clone of a person. Voice quality
and pronunciation still need listening checks with the intended users.

## Enable it

1. Create an Azure Speech resource and obtain its key and region from the Azure
   portal. Keep the key in backend secrets; do not put it in Android configuration
   or commit it to Git.
2. Set these variables in `backend/.env` for local development or your backend's
   production secrets/environment:

   ```dotenv
   AZURE_SPEECH_KEY=your-speech-resource-key
   AZURE_SPEECH_REGION=centralindia
   ```

   The region must match the actual resource. `centralindia` is an example.
   Both Compose configurations forward the variables. For production Compose,
   use `.env.production` with the existing `--env-file` command.
3. Restart/recreate the backend. Rebuild its Docker image when deploying this code.
4. Build the Android app with `API_BASE_URL` pointing at that backend. There is no
   additional Android speech key, cloud SDK, or database migration.

Optional server overrides are `TTS_VOICE_EN`, `TTS_VOICE_HI`, `TTS_VOICE_TE`,
`TTS_TIMEOUT_SECONDS` (default 8), `TTS_RATE_LIMIT_PER_MINUTE` (default 30 per
client), and `TTS_GLOBAL_RATE_LIMIT_PER_MINUTE` (default 120 per process).
Select replacement voice names for the corresponding language supported by your
resource; an invalid voice falls back to device TTS through the error path.

[Azure's voice list](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/language-support?tabs=tts)
and [REST reference](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/rest-text-to-speech)
document the provider contract.

## Backend contract

`POST /v1/speech/synthesize` with `Content-Type: application/json`:

```json
{"text":"Where would you like to go?","language":"en"}
```

- `text`: 1–2,000 characters after trimming; XML-invalid characters are rejected.
- `language`: `en`, `hi`, or `te`. Selects the voice and locale; it does not
  translate `text`. Supply text in the language you want spoken.
- Success: `200`, `Content-Type: audio/mpeg`, `Cache-Control: no-store`.
- Stable errors: `INVALID_REQUEST` (400; 413 for an oversized body), `QUOTA`
  (429), `NOT_CONFIGURED` / `ACCESS_DENIED` / `UNAVAILABLE` (503), or
  `INVALID_RESPONSE` (502). Responses never echo input text or upstream errors.

Hindi and Telugu examples:

```json
{"text":"आप कहाँ जाना चाहते हैं?","language":"hi"}
```

```json
{"text":"మీరు ఎక్కడికి వెళ్లాలనుకుంటున్నారు?","language":"te"}
```

Example with a non-sensitive sample prompt:

```bash
curl --fail --request POST http://localhost:8000/v1/speech/synthesize \
  --header 'Content-Type: application/json' \
  --data '{"text":"Where would you like to go?","language":"en"}' \
  --output /tmp/ride-saathi-speech.mp3
```

The key and region are optional for backend startup. Without them, the endpoint
returns `NOT_CONFIGURED` and Android uses its existing device TTS. The health and
readiness endpoints do not contact Azure or certify that speech credentials work.

## Playback, privacy and limits

Android fetches audio in a background worker and plays it from memory. A new
prompt, barge-in, language change, pause, or destruction cancels the old download/playback.
A 12-second download/preparation deadline prevents a stalled request from blocking
speech indefinitely. Network, configuration, quota and decoder failures try device
TTS once. Loss/denial of audio focus cancels playback instead of starting fallback
speech. Listening starts only after the current prompt actually completes while
the app is active; stale or failed callbacks cannot open the microphone.

The request sends spoken prompt text—including any name or destination address
in that prompt—to the Ride Saathi backend and Azure for synthesis. The TTS endpoint does not upload microphone recordings; the
[voice input pipeline](AZURE_VOICE_PIPELINE.md) separately sends microphone audio
to Azure Speech and recognized text to Azure OpenAI. The application does not log speech text,
store it in the database, or cache audio on disk. Provider-side handling is subject
to the Azure resource's data policies. The device TTS fallback retains the phone's
configured engine behavior and may itself use network voices.

The API caps raw request bodies at 16 KiB and audio at 2 MiB, escapes SSML, fixes
voice selection on the server, and refuses redirects. Per-client and aggregate
rate limits protect the paid endpoint within one process. Like the maps proxy,
it currently has no first-party authentication: public/multi-worker deployments
need gateway authentication/throttling and provider budget controls appropriate
to their traffic. Never embed a shared Azure credential in the APK to solve this.

## Verification

Provider tests use `httpx.MockTransport`; no Azure requests or charges are made.
They cover languages, escaped input, missing settings, timeouts, upstream failures,
response bounds, privacy, and rate limits. Android JVM tests cover the POST
contract, audio bounds, online/device fallback, cancellation, language changes,
and exactly-once completion. QA instrumentation APK compilation preserves the
existing utterance-completion tests.

Local verification on 2026-10-01: backend Ruff and 121 non-PostgreSQL tests
passed; Android debug/QA each passed 69 JVM tests, APK builds, and lint (zero
errors; 22 existing warnings). The QA instrumentation APK also compiled.
Database models and migrations were not changed by this feature.

Live voice quality and audio-focus behavior on a target phone require the configured
service and a device. Check English/Hindi/Telugu prompts, mixed-language addresses,
rapid prompt replacement, app backgrounding, and network loss before the pilot.

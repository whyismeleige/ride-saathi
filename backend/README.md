# Ride Saathi Backend

A minimal FastAPI service that sits between the Ride Saathi Android app and the
Ola Maps API. Its only real job is to keep the Ola API key on the server so it
cannot be extracted from an installed APK.

```
Android app
   │  HTTPS  GET /v1/places/autocomplete
   ▼
Ride Saathi backend   (secret: OLA_MAPS_API_KEY)
   │  server-side  https://api.olamaps.io/places/v1/autocomplete
   ▼
Ola Maps API
```

## Endpoints

| Method | Path                     | Purpose                                             |
| ------ | ------------------------ | --------------------------------------------------- |
| GET    | `/health`                | Liveness check.                                     |
| GET    | `/v1/places/autocomplete`| Place search proxied to Ola Maps.                   |

### `GET /v1/places/autocomplete`

Query parameters:

| Parameter  | Required | Notes                                             |
| ---------- | -------- | ------------------------------------------------- |
| `q`        | yes      | Place query, 3–200 characters after trimming.     |
| `language` | no       | One of `en`, `hi`, `te` (default `en`).           |
| `lat`      | no       | Location bias; must be paired with `lng`.         |
| `lng`      | no       | Location bias; must be paired with `lat`.         |

Success (`200`):

```json
{
  "places": [
    {"address": "Apollo Hospitals, Jubilee Hills, Hyderabad", "latitude": 17.414, "longitude": 78.412}
  ]
}
```

Errors use a stable shape. Messages are fixed and never carry credentials or
upstream internals:

```json
{"error": {"code": "QUOTA", "message": "Place search temporarily unavailable"}}
```

| Code              | HTTP | When                                                    |
| ----------------- | ---- | ------------------------------------------------------- |
| `INVALID_REQUEST` | 400  | Bad query, language, or coordinates.                    |
| `ACCESS_DENIED`   | 403  | Upstream returned 401/403 or denied.                    |
| `QUOTA`           | 429  | Upstream returned 429 or over-query-limit.              |
| `NOT_CONFIGURED`  | 503  | `OLA_MAPS_API_KEY` is missing on the server.            |
| `UNAVAILABLE`     | 503  | Upstream timeout, network failure, or 5xx.              |
| `INVALID_RESPONSE`| 502  | Upstream body could not be parsed or was unusable.      |

## Local development

```bash
cd backend
python -m venv .venv
source .venv/bin/activate
pip install -r requirements-dev.txt
cp .env.example .env
```

Run it:

```bash
# Loads values from .env (uvicorn's built-in --env-file):
uvicorn app.main:app --reload --env-file .env
# or export the variables yourself:
export OLA_MAPS_API_KEY=your_key
uvicorn app.main:app --reload --port 8000
```

The API listens on `http://localhost:8000` (see `.env` / your shell). The
Ola API key is the only required variable; without it every autocomplete
request returns `NOT_CONFIGURED`.

## Tests

```bash
cd backend
source .venv/bin/activate
pytest -q
```

All upstream Ola calls are mocked with `httpx.MockTransport`; no real Ola
quota is ever consumed by the test suite.

## Configuration

| Variable                     | Required | Default                     | Notes                          |
| ---------------------------- | -------- | --------------------------- | ------------------------------ |
| `OLA_MAPS_API_KEY`           | yes      | (none)                      | Server-only secret.            |
| `PORT`                       | no       | `8000`                      | Honored by the Dockerfile.    |
| `ENVIRONMENT`                | no       | `development`               | Informational.                |
| `OLA_MAPS_BASE_URL`          | no       | `https://api.olamaps.io`    | Useful for sandbox testing.   |
| `OLA_MAPS_TIMEOUT_SECONDS`   | no       | `8`                         | Short upstream timeout.       |

`.env` is local-only and ignored by Git.

## Deployment

The app is provider-agnostic (works on Railway, Render, Cloud Run, Fly.io,
Koyeb, or a VPS). Just run `uvicorn app.main:app --host 0.0.0.0 --port $PORT`
(or use the included `Dockerfile`, which already respects `$PORT`) and set
`OLA_MAPS_API_KEY` as a platform secret/environment variable.

Notes for production:

- Serve behind HTTPS (the app refuses to ship cleartext in release builds).
- The `.env.example` document shows the exact variable names to configure.
- If you deploy, point the Android release build's `API_BASE_URL` at the
  HTTPS endpoint (see the root README).
- The API is intentionally unauthenticated for now. Rate limiting, Play
  Integrity / device attestation, or user/session auth can be layered on
  behind this same interface without changing the Android contract.
# Ride Saathi Backend

A modular FastAPI backend with an Ola Maps proxy and an async PostgreSQL
persistence foundation for Ride Saathi V1. The Ola API key stays on the server.
See the [backend architecture guide](../docs/architecture/backend-structure.md)
for module ownership and dependency flow, and the
[database guide](../docs/database.md) for migrations, local setup, tests, and
Supabase connection guidance.

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
| GET    | `/health`                | Cheap liveness check (no DB or provider calls).     |
| GET    | `/ready`                 | Readiness: config + DB connectivity, no provider calls. |
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

Install [uv](https://docs.astral.sh/uv/getting-started/installation/) first.
uv manages Python 3.12 and the local `.venv` automatically.

```bash
cd backend
uv sync --locked
cp .env.example .env
```

Run it:

```bash
# Settings automatically load backend/.env; shell values take priority:
uv run --locked uvicorn app.main:app --reload --no-access-log
# or export the variables yourself:
export OLA_MAPS_API_KEY=your_key
uv run --locked uvicorn app.main:app --reload --port 8000 --no-access-log
```

The API listens on `http://localhost:8000` (see `.env` / your shell). The
Ola API key is the only required variable; without it every autocomplete
request returns `NOT_CONFIGURED`.

## Docker Compose

Run these commands from `backend/` with Docker Compose v2 installed.

### Local development

Create `.env` from `.env.example` if it does not exist, then set your Ola key.

```bash
docker compose up --build -d --wait
docker compose logs -f backend
docker compose down
```

The API is available at `http://localhost:8000`. Changes under `app/` reload
automatically. Rebuild after changing `pyproject.toml` or `uv.lock`. Set
`BIND_HOST=0.0.0.0` in `.env` to connect from a physical phone on your LAN.
Set `PORT` to change the host port; the container always listens on port 8000.

### Production

Create `.env.production` from `.env.production.example` and set
`OLA_MAPS_API_KEY` and `DATABASE_URL`. This file is ignored by Git and excluded from Docker builds.

```bash
docker compose --env-file .env.production -f compose.production.yaml up --build -d --wait
docker compose --env-file .env.production -f compose.production.yaml logs -f backend
docker compose --env-file .env.production -f compose.production.yaml down
```

The production file is standalone and requires nonempty `OLA_MAPS_API_KEY` and
`DATABASE_URL` (the app also fails fast in `ENVIRONMENT=production` when either
is missing, so direct Docker runs behave the same as Compose). It runs
the image's bundled code without hot reload or source mounts, as a non-root
user with a read-only filesystem, bounded logs, and automatic restart after
process exit or Docker restart. Health checks report liveness; an unhealthy
status alone does not restart the container.

Put a host HTTPS reverse proxy in front of `127.0.0.1:8000` (or the chosen
`PORT`). TLS is handled by that proxy. Both configurations disable Uvicorn's
access log because query strings can contain searched addresses; the app
still logs request paths, status codes, latency, and request IDs.

Local and production use separate Compose project names. Choose different
host ports if running both on the same machine. Shell variables override
values in the selected environment file.

## PostgreSQL persistence

The database uses SQLAlchemy 2.x async ORM, asyncpg, and Alembic. Set
`DATABASE_URL` in `.env` before using database services. No schema changes run at
application startup. For a host-run app with local PostgreSQL:

```bash
# From backend/; local development credentials only:
docker compose up -d --wait postgres
# Set DATABASE_URL=postgresql+asyncpg://postgres:postgres@localhost:5432/ride_saathi in .env
uv run --locked alembic upgrade head
```

When using Compose for the backend too, run
`docker compose run --rm backend alembic upgrade head` after building the image.
See [database.md](../docs/database.md) for a dedicated PostgreSQL test database,
port overrides, connection pooling and migration operations.

## Tests

```bash
cd backend
uv run ruff check app tests alembic
uv run --locked pytest -q
```

Provider and API tests use fake providers or `httpx.MockTransport`; shared
fixtures block real HTTPX requests. PostgreSQL tests require `TEST_DATABASE_URL`
and otherwise explicitly skip. No real Ola quota is consumed by the test suite.
CI (`.github/workflows/backend-ci.yml`) runs lint, unit/API tests, a PostgreSQL
service, `alembic upgrade head`, the full suite, `alembic check`, and a
downgrade/upgrade round-trip.

Dependencies live in `pyproject.toml`; commit `uv.lock` alongside dependency
changes. Use `uv add <package>` for runtime dependencies and
`uv add --dev <package>` for development tools. Run `uv lock --upgrade` to
intentionally refresh all locked versions, then run the tests.

## Configuration

| Variable                     | Required | Default                     | Notes                          |
| ---------------------------- | -------- | --------------------------- | ------------------------------ |
| `DATABASE_URL`              | for persistence (`production`: yes) | (none) | PostgreSQL async connection; see database guide. |
| `OLA_MAPS_API_KEY`           | yes (`production`: yes) | (none) | Server-only secret.            |
| `PORT`                       | no       | `8000`                      | Honored by the Dockerfile.    |
| `ENVIRONMENT`                | no       | `development`               | `development`, `testing`, or `production`. |
| `LOG_LEVEL`                  | no       | `INFO`                      | `DEBUG`, `INFO`, `WARNING`, `ERROR`, `CRITICAL`. |
| `OLA_MAPS_BASE_URL`          | no       | `https://api.olamaps.io`    | Useful for sandbox testing.   |
| `OLA_MAPS_TIMEOUT_SECONDS`   | no       | `8`                         | Short upstream timeout.       |
| `MAPS_RATE_LIMIT_PER_MINUTE` | no       | `60`                        | Single-instance maps-proxy rate window. |
| `MAPS_RATE_LIMIT_WINDOW_SECONDS` | no   | `60`                        | Rate-window length in seconds. |
| `TRUST_PROXY_HEADERS`        | no       | `false`                     | Honor `X-Forwarded-For` only behind a trusted proxy. |
| `DB_ECHO` / `DB_POOL_SIZE` / `DB_MAX_OVERFLOW` / `DB_NULL_POOL` | no | `false` / `5` / `0` / `false` | Pooling; see database guide. |

`app/core/config.py` uses `pydantic-settings` and loads `backend/.env` regardless
of the current working directory. Shell variables take priority, including an
explicitly blank API key. `.env` is local-only and ignored by Git. Future provider
variables are documented in the architecture guide and added when implemented.

## Deployment

The app is provider-agnostic (works on Railway, Render, Cloud Run, Fly.io,
Koyeb, or a VPS). From `backend`, install with `uv sync --locked --no-dev`,
then run `uv run --locked --no-dev uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8000} --no-access-log`
(or use the included `Dockerfile`, which already respects `$PORT`) and set
`ENVIRONMENT=production`, with `OLA_MAPS_API_KEY` and `DATABASE_URL` supplied as
platform secrets/environment variables. Run `uv run --locked --no-dev alembic upgrade head`
as a deployment step before serving traffic.

Notes for production:

- Serve behind HTTPS (the app refuses to ship cleartext in release builds).
- The `.env.example` document shows the exact variable names to configure.
- If you deploy, point the Android release build's `API_BASE_URL` at the
  HTTPS endpoint (see the root README).
- The API is intentionally unauthenticated for now. A minimal single-instance
  in-memory rate window protects `/v1/places/autocomplete`; add reverse-proxy
  throttling when scaling horizontally. Play Integrity / device attestation or
  user/session auth can be layered on behind this same interface without
  changing the Android contract.

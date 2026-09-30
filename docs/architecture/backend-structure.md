# Backend structure

Ride Saathi is a modular monolith: one FastAPI application, with one PostgreSQL
database foundation. Feature ownership and replaceable provider boundaries let a
small team develop independently without operating separate services. Service
extraction is an option only when real scaling or ownership needs justify it.

The backend combines modular Ola Maps place search with an async PostgreSQL
persistence foundation built around destination memory and deep-link handoff.
The V1 product flow is voice/text destination input → destination resolution →
confirmation → pickup/destination snapshot → preferred ride/product selection →
deep-link handoff to the external Uber app. Ride Saathi does NOT claim a ride
was booked after the handoff. Onboarding, saved-place CRUD, destination ranking,
STT/TTS, and LLM parsing remain to be implemented (see backend-backlog.md).

## Current directory tree

Package `__init__.py` files are omitted below. `app/models/__init__.py`
explicitly registers persistent models for Alembic.

```text
backend/
├── app/
│   ├── main.py
│   ├── core/
│   │   ├── config.py
│   │   └── logging.py
│   ├── api/
│   │   ├── router.py
│   │   ├── endpoints/health.py
│   │   └── v1/router.py
│   ├── modules/
│   │   └── destinations/
│   │       ├── router.py
│   │       ├── schemas.py
│   │       └── service.py
│   ├── integrations/
│   │   └── maps/
│   │       ├── base.py
│   │       └── ola_maps.py
│   ├── dependencies/
│   │   └── services.py
│   ├── models/                    # PostgreSQL entities and model registry
│   ├── repositories/              # Foundation package
│   └── db/
│       ├── base.py
│       ├── session.py
│       └── README.md
├── tests/
│   ├── conftest.py
│   ├── test_database.py
│   ├── test_db_config.py
│   ├── unit/
│   │   ├── test_config.py
│   │   └── test_destinations.py
│   ├── integration/
│   │   └── test_ola_maps.py
│   └── api/
│       ├── test_places.py
│       └── test_composition.py
├── alembic/
├── alembic.ini
├── pyproject.toml
├── uv.lock
├── .env.example
├── .env.production.example
├── Dockerfile
├── compose.yaml
├── compose.production.yaml
└── README.md
docs/
├── database.md
└── architecture/backend-structure.md
```

Architecture documentation uses the repository's existing root `docs/` directory.
`app/db/README.md` defines the shared persistence boundary; [database.md](../database.md)
covers the schema and migration workflow. Add feature files when they acquire a
concrete responsibility.

## Responsibilities and dependency flow

| Location | Responsibility |
| --- | --- |
| `main.py` | Construct FastAPI, configure logging, attach middleware and routers, dispose the database pool on shutdown. |
| `core/` | Environment settings and cross-cutting operational logging. Add security or shared exceptions when needed. |
| `api/` | Compose versioned module routers; hold operational endpoints such as health. |
| `modules/<feature>/router.py` | HTTP validation, status/error mapping, and calls to injected services. |
| `modules/<feature>/schemas.py` | The feature's Pydantic request/response contracts. |
| `modules/<feature>/service.py` | Ride Saathi rules and use-case orchestration. |
| `integrations/` | Provider-neutral contracts and vendor HTTP clients, payload normalization, and vendor error translation. No app workflows or route imports. |
| `dependencies/` | Wire concrete providers and services for FastAPI; database sessions currently use `db.session.get_db`. |
| `db/` | Shared SQLAlchemy metadata and engine/session lifecycle; persistent models are registered in `models/__init__.py`. |
| `tests/` | Unit services/configuration, mocked adapter integration boundaries, and API contracts. |

```text
main → api/router → api/v1/router → destinations/router
                                      ↓
                              DestinationService
                                      ↓
                              MapsProvider protocol
                                      ↑ implements
                                OlaMapsProvider

dependencies/services wires the concrete provider into the service.

Future persistence: feature router → feature service → feature repository
                                                        ↓
                                           SQLAlchemy → PostgreSQL
```

The service imports only the maps contract, not the Ola implementation. The
contract carries immutable, provider-neutral candidates and categorized errors;
the destination module owns its HTTP schemas. The adapter translates upstream
data, while the service applies the existing order-preserving deduplication.
The interface stays synchronous to match the existing synchronous endpoint,
which FastAPI runs in its thread pool. Async conversion is a separate change.

Composition is the intentional exception to interface-only dependencies:
`dependencies/services.py` imports both service and concrete adapter. Neither
services nor adapters import dependency factories, API routers, or `main.py`.
Use absolute `app.*` imports. The model registry is the explicit exception to
keeping package initializers free of eager exports.

## API compatibility

`GET /health` and `GET /v1/places/autocomplete` retain their existing paths,
parameters, response bodies, error codes, status codes, and request-ID handling.
Interactive API documentation remains disabled. Domain endpoints belong in
their module's `router.py`; the API packages only compose them.

The existing Android app uses `/v1`, so this restructuring does not switch it to
`/api/v1` or add duplicate aliases. Future routers can use `/v1/users`,
`/v1/saved-places`, `/v1/destinations`, `/v1/rides`, and `/v1/auth`. A different
global prefix requires an explicit client migration.

## Adding a feature module

1. Add `app/modules/<feature>/` with a small `__init__.py` and the files needed by
   the actual use case. Begin with `router.py`, `schemas.py`, and `service.py` if
   the feature has an HTTP interface.
2. Put rules and orchestration in `service.py`, independently of FastAPI. Keep
   validation of HTTP parameters and HTTP error responses in the router.
3. When persistence is needed, add module-owned `models.py` (or a `models/`
   package for larger domains) and `repository.py`. The repository takes an
   injected SQLAlchemy session; the service owns transaction boundaries. Avoid
   raw SQL in routes and a generic repository framework without an actual need.
4. Wire dependencies in `app/dependencies/`; register the feature router in
   `app/api/v1/router.py`. No endpoint declarations belong in `main.py`.
5. Add service tests with fakes, repository integration tests against a dedicated
   PostgreSQL test database, and HTTP contract tests. Register ORM models
   explicitly in `app/models/__init__.py` for Alembic.

The planned boundaries are:

| Module | Owns |
| --- | --- |
| `users` | Profile, language, onboarding state, preferences, and User models. |
| `auth` | First-party Ride Saathi authentication when implemented (future work; handoff never depends on Uber OAuth). |
| `saved_places` | Home, named places, user aliases, and saved-place models/repositories. |
| `destinations` | Input interpretation, saved-place lookup, candidate generation, confidence/ranking, resolution; current place-search code lives here. |
| `rides` | RideSession intent/session state, DestinationResolution/Correction destination memory, SessionEvent append-only analytics, HandoffEvent deep-link handoff — handoff-only, never provider booking. |

The PostgreSQL foundation currently keeps its entities in `app/models` to
preserve the shared schema and migration registry. Create additional feature
packages when implementing their workflows; move model ownership deliberately
with the associated imports and registry updates. New schemas and services
belong beside their feature.

## Adding or replacing an external provider

For maps, implement `MapsProvider.search_places` in a new adapter such as
`integrations/maps/google_maps.py`. Return `PlaceCandidate` values and translate
failures to `MapsError` categories. Select it in `get_maps_provider`; the
destination service and endpoint remain unchanged. Test request construction,
normalization, and error categories with a mocked transport. API tests can
override `get_maps_provider` with `app.dependency_overrides`.

Add `resolve_place` to the contract only when a real resolution use case needs
it, and update each adapter and fake together. Future Google Maps, Mapbox, and
HERE adapters use the same boundary; no Google dependency is assumed today.

Follow this approach for `integrations/speech/stt`, `speech/tts`, and
`integrations/notifications`: a small capability-specific protocol and neutral
result types, implemented by vendor adapters and selected in dependencies. Wait
for actual input/output requirements before creating those protocols.

Uber is an external application we hand the user to through a deep link. No
Uber booking client, OAuth flow, or webhook lifecycle exists in this codebase,
and none should be added without an explicit product decision (see
backend-backlog.md). Provider implementations must not leak into
domain/application rules.

## Configuration, persistence, and future infrastructure

`core/config.py` uses `pydantic-settings`. Shell variables override
`backend/.env`, whose path is independent of the working directory. Unknown
dotenv keys are ignored so Compose-only configuration can share the file.
`ENVIRONMENT` accepts `development`, `testing`, and `production`; `LOG_LEVEL`
controls app logging. Credentials are omitted from settings representations.
Production fails fast when `OLA_MAPS_API_KEY` or `DATABASE_URL` is missing.
Do not log settings dumps, request queries, addresses, coordinates, transcripts,
candidate payloads, tokens, or credentials. HTTPX and HTTPCore request logs are
suppressed below WARNING because upstream URLs contain credentials and user
queries. Externally supplied `X-Request-Id` values are reused only when they
match `^[A-Za-z0-9_-]{1,64}$`; otherwise a fresh id is generated. Unexpected
exceptions are logged with the request id and path only.

`DATABASE_URL` is optional for startup and place search; the engine initializes
lazily on database use. Shared SQLAlchemy infrastructure lives in `db/base.py`
and `db/session.py`, including the `get_db` dependency and pool shutdown cleanup.
`app/models/__init__.py` registers entities for the single migration history in
`backend/alembic/`. Migrations run explicitly, never at application startup.
See [the database guide](../database.md) for pool settings and migration commands.
Supabase is a hosted PostgreSQL endpoint configured through `DATABASE_URL`, not
a required SDK or a separate persistence abstraction.

`GET /health` is a cheap liveness probe. `GET /ready` reports configuration
and database connectivity (`connected` / `not_configured`, `503` when a
configured database is unreachable) and never calls paid external providers.

The maps proxy reuses one process-wide HTTP connection pool (closed on FastAPI
shutdown; tests inject or mock the provider). Timeouts stay short and strict;
no aggressive retries. Abuse protection is a minimal single-instance in-memory
rate window on `/v1/places/autocomplete`
(`MAPS_RATE_LIMIT_PER_MINUTE`/`MAPS_RATE_LIMIT_WINDOW_SECONDS`), honoring
`X-Forwarded-For` only when `TRUST_PROXY_HEADERS=true`. Document this limit in
operations: horizontally scaled deployments must add reverse-proxy throttling.

Add `GOOGLE_MAPS_API_KEY` to typed settings and environment examples only when
that adapter is introduced. `SUPABASE_URL`/`SUPABASE_KEY` are only needed if a
separate Supabase API is deliberately adopted; ordinary PostgreSQL does not
require them. Do NOT add `REDIS_URL`, Kafka, Celery, service meshes, or
microservices without a concrete scale trigger. Unused secrets and switches are
not configured today.

Add `shared/` only for concepts actually shared across modules, `scripts/` for
real maintenance commands, and `workers/` when background tasks are required.
Keep the same service/repository boundaries for workers. No broker, worker
framework, microservices, or deployment orchestration is introduced here.

## Validation

From `backend/`, run `uv sync --locked`, then `uv run ruff check app tests alembic`,
then `uv run --locked pytest -q`. The API contract cases are retained in
`tests/api/test_places.py`. Additional tests cover settings, service policy,
replaceable dependency wiring, app startup, route registration, the adapter
boundary, and the V1 persistence invariants in `tests/test_database.py`.
Shared fixtures block real HTTPX transports; use fake providers or
`httpx.MockTransport`. No real map credentials or PostgreSQL server is required
for normal tests. PostgreSQL integration tests run when `TEST_DATABASE_URL`
selects a dedicated database ending in `_test`; otherwise they skip. CI always
sets `TEST_DATABASE_URL`, runs `alembic upgrade head`, the full suite,
`alembic check`, and a downgrade/upgrade round-trip (see
`.github/workflows/backend-ci.yml`). No formatter is configured; Ruff
(`E`, `F`, `I`, `UP`) is the linter.

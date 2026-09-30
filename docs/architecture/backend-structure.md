# Backend structure

Ride Saathi is a modular monolith: one FastAPI application, with one PostgreSQL
database planned. Feature ownership and replaceable provider boundaries let a
small team develop independently without operating separate services. Service
extraction is an option only when real scaling or ownership needs justify it.

This change starts from the committed Ola Maps backend. The separate, uncommitted
PostgreSQL foundation is intentionally not incorporated. No onboarding, OAuth,
ride booking, persistence, or destination-ranking features are implemented here.

## Current directory tree

Package `__init__.py` files are omitted below; they do not re-export internals.

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
│   └── db/
│       └── README.md
├── tests/
│   ├── conftest.py
│   ├── unit/
│   │   ├── test_config.py
│   │   └── test_destinations.py
│   ├── integration/
│   │   └── test_ola_maps.py
│   └── api/
│       ├── test_places.py
│       └── test_composition.py
├── pyproject.toml
├── uv.lock
├── .env.example
├── .env.production.example
├── Dockerfile
├── compose.yaml
├── compose.production.yaml
└── README.md
docs/
└── architecture/backend-structure.md
```

Architecture documentation uses the repository's existing root `docs/` directory.
`app/db/README.md` defines the persistence boundary pending database setup. There
are no unused routers, empty services, generic repositories, fake API endpoints,
or provider implementations. Add files when they acquire a concrete responsibility.

## Responsibilities and dependency flow

| Location | Responsibility |
| --- | --- |
| `main.py` | Construct FastAPI, configure logging, attach middleware and routers. |
| `core/` | Environment settings and cross-cutting operational logging. Add security or shared exceptions when needed. |
| `api/` | Compose versioned module routers; hold operational endpoints such as health. |
| `modules/<feature>/router.py` | HTTP validation, status/error mapping, and calls to injected services. |
| `modules/<feature>/schemas.py` | The feature's Pydantic request/response contracts. |
| `modules/<feature>/service.py` | Ride Saathi rules and use-case orchestration. |
| `integrations/` | Provider-neutral contracts and vendor HTTP clients, payload normalization, and vendor error translation. No app workflows or route imports. |
| `dependencies/` | Wire concrete providers, services, and eventually request-scoped database sessions for FastAPI. |
| `db/` | Shared SQLAlchemy metadata, engine/session lifecycle, and explicit model registration once persistence is introduced. |
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
Use absolute `app.*` imports and keep package initializers free of eager exports.

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
   explicitly for Alembic when persistence is available.

The planned boundaries are:

| Module | Owns |
| --- | --- |
| `users` | Profile, language, onboarding state, preferences, and User models. |
| `auth` | Application authentication, Uber connection workflow, credential lifecycle and credential persistence. |
| `saved_places` | Home, named places, user aliases, and saved-place models/repositories. |
| `destinations` | Input interpretation, saved-place lookup, candidate generation, confidence/ranking, resolution; current place-search code lives here. |
| `rides` | RideSession, RideRequest, RideEvent, booking/cancellation lifecycle, history, and a future `state_machine.py`. |

Create these additional packages when implementing or integrating their first
components. Do not collect their models, schemas, or services into global folders.

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

Future `integrations/uber/client.py`, `oauth.py`, `schemas.py`, and `exceptions.py`
own Uber HTTP calls, OAuth wire details, payloads, and error translation. The
auth module owns connection/credential workflows; rides owns booking decisions
and state transitions. Do not make the Uber client call domain services.

## Configuration, persistence, and future infrastructure

`core/config.py` uses `pydantic-settings`. Shell variables override
`backend/.env`, whose path is independent of the working directory. Unknown
dotenv keys are ignored so Compose-only configuration can share the file.
`ENVIRONMENT` accepts `development`, `testing`, and `production`; `LOG_LEVEL`
controls app logging. Credentials are omitted from settings representations.
Do not log settings dumps, request queries, credentials, or coordinates. HTTPX
and HTTPCore request logs are suppressed below WARNING because upstream URLs
contain credentials and user queries.

`DATABASE_URL` is optional and reserved; no engine connects during startup.
Add shared SQLAlchemy `db/base.py`, `db/session.py`, a database dependency, and
one Alembic migration history in `backend/alembic/` when integrating the database
foundation. Keep models beside their feature and use an explicit model registry
for migrations. Do not maintain a second `app/db/migrations/` history.
Supabase is a hosted PostgreSQL endpoint configured through `DATABASE_URL`, not
a required SDK or a separate persistence abstraction.

Add `UBER_CLIENT_ID`, `UBER_CLIENT_SECRET`, `UBER_REDIRECT_URI`,
`GOOGLE_MAPS_API_KEY`, and `REDIS_URL` to typed settings and environment examples
when those integrations are introduced. `SUPABASE_URL`/`SUPABASE_KEY` are only
needed if a separate Supabase API is deliberately adopted; ordinary PostgreSQL
does not require them. Unused secrets and switches are not configured today.

Add `shared/` only for concepts actually shared across modules, `scripts/` for
real maintenance commands, and `workers/` when background tasks are required.
Keep the same service/repository boundaries for workers. No broker, worker
framework, microservices, or deployment orchestration is introduced here.

## Validation

From `backend/`, run `uv sync --locked`, then `uv run --locked pytest -q`.
The 36 original API contract cases are retained in `tests/api/test_places.py`.
Additional tests cover settings, service policy, replaceable dependency wiring,
app startup, route registration, and the adapter boundary. Shared fixtures block
real HTTPX transports; use fake providers or `httpx.MockTransport`. No real map
credentials, Uber account, or PostgreSQL server is required for normal tests.
No formatter or linter is configured in the existing project.

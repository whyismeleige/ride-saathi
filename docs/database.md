# Ride Saathi V1 database

## Architecture

```text
FastAPI → services → repositories → SQLAlchemy Async / asyncpg → PostgreSQL
Development: FastAPI → SQLAlchemy → local PostgreSQL (Docker Compose)
Alpha/beta:  FastAPI → SQLAlchemy → Supabase-hosted PostgreSQL
```

Supabase is managed PostgreSQL infrastructure. The application has no Supabase
SDK or database-client dependency. The existing `app/routes` and `app/services`
structure stays in place. `app/repositories` is ready for focused query helpers;
no booking endpoints, authentication, Redis, or external provider integration
are added by this foundation.

## Local setup

Run from the repository root (requires uv and Docker Compose v2):

```bash
cd backend
uv sync --locked
# Only if .env does not already exist; preserve existing Ola configuration:
test -f .env || cp .env.example .env
docker compose up -d --wait postgres
```

Set this in `backend/.env` for a host-run app or Alembic:

```dotenv
DATABASE_URL=postgresql+asyncpg://postgres:postgres@localhost:5432/ride_saathi
```

These credentials are local development defaults only. The PostgreSQL port binds
to loopback. If 5432 is occupied, set `POSTGRES_PORT=55433` in `.env`, start the
same service, and use port 55433 in the host's `DATABASE_URL`. Data persists in the
`postgres_data` Docker volume. `docker compose stop postgres` stops the service
without deleting data; do not use `down -v` unless you intend to erase it.

The backend container uses the Compose hostname `postgres` and internal port
5432. Its local URL defaults to the included local database; optionally override
it with `COMPOSE_DATABASE_URL`. A host URL using `localhost` cannot reach the
database from inside the backend container. Production Compose passes through
`DATABASE_URL` and does not create a database service.

```bash
# From backend/, after setting DATABASE_URL:
uv run --locked alembic upgrade head
uv run --locked uvicorn app.main:app --reload

# Alternatively, run migrations with the backend image, then start the app:
docker compose build backend
docker compose run --rm backend alembic upgrade head
docker compose up -d backend
```

Settings load `backend/.env` consistently for the app and Alembic; exported
environment variables take priority. Existing health/place routes remain usable
with an empty `DATABASE_URL`. The first database operation requires a valid URL.
No connection is opened at module import or application startup.

## Configuration and connections

| Setting | Default | Purpose |
| --- | --- | --- |
| `DATABASE_URL` | empty | PostgreSQL connection URL; required for persistence/migrations |
| `DB_ECHO` | `false` | SQL statement logging; bound parameters are always hidden |
| `DB_POOL_SIZE` | `5` | Maximum persistent connections per worker, opened lazily |
| `DB_MAX_OVERFLOW` | `0` | Additional temporary connections per worker |
| `DB_NULL_POOL` | `false` | Disable client pooling when an external pooler warrants it |

Pools have a 30-second checkout timeout and connection health checking. Budget
connections across all workers and replicas. One cached engine/session factory
is used per process, and FastAPI disposes the engine during lifespan shutdown.
Never share an `AsyncSession` between concurrent tasks.

URLs beginning `postgresql://` or `postgres://` normalize to
`postgresql+asyncpg://`. The `sslmode` query option is translated to asyncpg's
`ssl` option. Percent-encode special characters in usernames/passwords. Other
libpq-specific URL options are not automatically translated; use options
supported by asyncpg. URL validation errors do not echo credentials.

## FastAPI and transaction ownership

The dependency is `app.db.session.get_db`, implemented in
`backend/app/db/session.py`:

```python
from fastapi import Depends
from sqlalchemy.ext.asyncio import AsyncSession
from app.db.session import get_db

async def endpoint(db: AsyncSession = Depends(get_db)):
    # Call a service that owns the transaction:
    async with db.begin():
        ...
```

Each dependency lifecycle gets a session. It rolls back on exceptions (including
cancellation), closes on exit, and never commits automatically. Closing also
rolls back uncommitted work. Services own transactions and call repository query
helpers with that session. Update a session/request and append its event in the
same transaction. Handle uniqueness errors with rollback or a savepoint; do not
reuse a failed transaction. For repeated booking/webhook keys, services should
load the existing record instead of repeating the external side effect. Database
uniqueness prevents duplicate rows, but provider calls still need their own
idempotency handling.

Relationships use `lazy="raise"` to avoid hidden I/O in async code. Explicitly
load what a service needs with `selectinload`, a query, or `await db.refresh`.
Do not serialize ORM instances directly into HTTP responses.

## Schema and lifecycle

| Table | Meaning |
| --- | --- |
| `users` | Required name, optional unique phone/email, onboarding and language |
| `saved_places` | User-owned named destinations, soft deletion, unique `(user_id, label)` |
| `uber_credentials` | At most one encrypted provider credential set per user |
| `ride_sessions` | Current/final configuration of the interaction and booking workflow |
| `ride_requests` | Actual provider booking attempts, with unique idempotency keys |
| `ride_events` | Append-only lifecycle history, with optional unique provider event IDs |

`RideSession != RideRequest`: many provider attempts can belong to one workflow.
Changes to destination or product update the session snapshot and append an event.
History comes from `users → ride_sessions → ride_requests`, generally displaying
completed/cancelled requests. Failed/abandoned sessions remain useful for analysis.
There is no separate ride-history table.

UUIDs are generated with Python `uuid4` on insert. All datetime columns are
`TIMESTAMPTZ`; pass timezone-aware values, normally `datetime.now(UTC)`.
PostgreSQL `now()` supplies creation/lifecycle defaults. The reusable timestamp
mixin supplies `updated_at` on SQLAlchemy ORM/Core updates. Handwritten SQL writers
must set `updated_at` explicitly. Database FK actions such as `SET NULL` do not
represent a booking change and do not advance this timestamp. `now()` is transaction
time, so writes in one transaction can have equal timestamps; order equal-time
events deterministically with their IDs where needed (UUIDs are not sequence IDs).

Coordinates are `NUMERIC(10,7)` with latitude/longitude bounds. Monetary values
are `NUMERIC(14,4)` returned as `Decimal`, supporting INR and currencies requiring
more than two fractional digits. Services own currency-specific rounding/display.
Eight native PostgreSQL enum types constrain supported languages and lifecycle
values. JSON payloads/scopes use JSONB with independent `{}`/`[]` defaults. Assign
a new dict/list when changing mutable JSON fields so SQLAlchemy detects the change.
Event payloads themselves must never be changed after insertion.

Phone/email uniqueness is exact and case-sensitive as requested; future services
must normalize contacts before persisting. Multiple NULL contacts/provider IDs
are allowed. Services must validate ownership of a chosen saved place and that
an event's optional request belongs to its session before persisting them.

### Deletion and audit behavior

- Removing a saved place normally sets `is_active=false` and `deleted_at` to an
  aware timestamp. The label remains reserved under the required uniqueness rule;
  restore or rename the existing place rather than inserting a duplicate.
- Hard deletion of a saved place uses `ON DELETE SET NULL` for the session's saved
  place reference. All copied destination details, requests, and events survive.
- User deletion is restricted if ride sessions exist. Prefer `status=deleted`.
  Saved places and credentials cascade only when a user can actually be deleted.
- Session/request/event history uses `RESTRICT`, with no ORM delete cascades.
  Revoking credentials updates `revoked_at`; it has no effect on rides.
- The migration installs a PostgreSQL trigger rejecting UPDATE, DELETE and
  TRUNCATE on `ride_events`, including bulk SQL. Append a correcting event instead.
  This is an application invariant, not protection from a database owner who can
  disable/drop triggers. Any future lawful retention/purge workflow needs an
  explicit reviewed migration or privileged maintenance process.

### Credential boundary

`access_token_ciphertext` and `refresh_token_ciphertext` accept ciphertext from a
future dedicated encryption service. The database cannot verify that arbitrary
text is encrypted. No fake encryption, key generation, or plaintext-token writer
is implemented. That service must handle authenticated encryption, managed keys,
rotation and decryption before OAuth persistence is used. Token fields are
excluded from default ORM SELECTs via deferred loading; explicitly load them only
inside the credential service. Do not log tokens, ciphertext, database URLs, model
payloads or sensitive event data. SQLAlchemy hides bound parameters even with
`DB_ECHO=true`; keep it off in production. Do not enable SQLAlchemy debug/result-row
logging. Credentials belong in environment secrets, never version control.

Temporary OAuth state/PKCE verifiers, destination candidates, booking/voice
context, STT and Maps results have no tables. Keep them in memory initially;
future Redis keys such as `ride_session:{session_id}:destination_candidates` may
use a 15–30 minute TTL. Confirmed destination details are copied into the session.

## Migrations

Alembic is the schema source of truth; neither startup nor tests call
`Base.metadata.create_all()`. The initial revision `764469b4ab69`
(`initial_ride_saathi_schema`) freezes its own enum definitions, creates six tables,
constraints/indexes, and the audit trigger. Downgrade removes dependent objects
before dropping enum types, without `CASCADE`.

From `backend/` with `DATABASE_URL` configured:

```bash
uv run --locked alembic upgrade head
uv run --locked alembic current
uv run --locked alembic history
uv run --locked alembic check
uv run --locked alembic downgrade -1
uv run --locked alembic upgrade head
```

Downgrading the initial revision deletes the schema/data; use the cycle on a
disposable local database, not as routine production recovery. Back up production
and use reviewed forward migrations. Run migrations once as a deployment step,
not from every worker. Review autogenerated migrations: native enum modifications
and triggers require explicit migration operations. Restart application workers
after schema changes to clear asyncpg's cached type/prepared-statement metadata.

## Switching to Supabase

Replace `DATABASE_URL` with the Supabase PostgreSQL direct connection URL (or the
session-mode pooler URL if required by network connectivity), then run the same
`uv run --locked alembic upgrade head`. No model, repository, or SDK change is
needed. Configure TLS, preferably certificate/hostname verification using
`sslmode=verify-full` and the driver's trusted root certificate configuration.
Keep the database accessible through the backend's trusted credentials; do not
grant client/API roles access to these tables. Use a restricted runtime role and
a separate schema-owning migration role for production.

Use a direct connection for migrations. Runtime traffic can use a direct or
session-mode pooler connection. To select a different migration connection,
override `DATABASE_URL` only for the Alembic command using a deployment secret;
there is no hardcoded runtime/migration host. [Supabase connection guidance](https://supabase.com/docs/guides/database/connecting-to-postgres).

Supabase's transaction-mode Supavisor pooler does not support prepared statements,
while SQLAlchemy's asyncpg dialect uses `prepare()` internally. Disabling a cache
alone does not remove that requirement. Use direct/session mode for this V1 stack;
do not assume transaction mode works simply by changing the URL or enabling
`DB_NULL_POOL`. Other PostgreSQL proxies must explicitly support the driver's
prepared-statement behavior and be integration-tested before adoption.
[Supabase prepared-statement guidance](https://supabase.com/docs/guides/troubleshooting/disabling-prepared-statements-qL8lEL),
[SQLAlchemy asyncpg guidance](https://docs.sqlalchemy.org/en/20/dialects/postgresql.html#asyncpg).

## Tests and verification

Use a dedicated PostgreSQL database whose name ends with `_test`. Tests apply
Alembic migrations and wrap each case in a transaction/savepoint, rolling back
inserted data even if a test calls commit. They do not fall back to SQLite.

```bash
cd backend
docker compose up -d --wait postgres
# Once, to create the dedicated test database:
docker compose exec -T postgres createdb -U postgres ride_saathi_test
TEST_DATABASE_URL=postgresql+asyncpg://postgres:postgres@localhost:5432/ride_saathi_test \
  uv run --locked pytest -q
```

Adjust the port if using `POSTGRES_PORT`. Without `TEST_DATABASE_URL`, PostgreSQL
tests explicitly skip and unit/API tests still run. Configured connection failures
fail the tests. Set the variable in CI to require full coverage. Tests exercise
native enums/JSONB, constraints, model relationships, timestamps, snapshots,
append-only enforcement and migration/metadata parity. Configuration/dependency
tests verify URL normalization, bounded pooling, hidden parameters, closure and
rollback without implicit commits. Existing Ola API tests remain mocked.

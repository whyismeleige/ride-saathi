# Backend V1 stabilization — 2026-10-01

Completed on `chore/backend-v1-stabilization` before integration into
`feat/backend-v1` and `v1-release`.

The stabilization pass reconciles ORM/migration parity, rebuilds persistence
tests for memory and deep-link handoff, removes obsolete booking/OAuth settings,
and adds runtime hardening, CI and aligned documentation. The final review also
fixes readiness errors for malformed database configuration, bounds connection
checks to five seconds, rejects newline-suffixed request IDs, serializes shared
HTTP client initialization, and makes rate-limit admission atomic with eviction
of expired client entries.

Validation on Python 3.12 and an isolated PostgreSQL 18.6 database:

- Ruff (`app tests alembic`): passed.
- Full pytest suite: **129 passed**, no skips; one upstream Starlette/HTTPX
  deprecation warning.
- Alembic upgrade from an empty database: passed.
- Alembic metadata parity check: no new upgrade operations.
- Downgrade to base, upgrade to head, and repeated parity check: passed.
- `git diff --check`: passed.

Tests use fake maps providers and consume no Ola quota. The test database is
disposable and separate from application databases. CI uses PostgreSQL 17;
this local validation used the installed PostgreSQL 18.6.

Feature work in [the post-stabilization backlog](backend-backlog.md), including
profile/saved-place HTTP endpoints and server-side handoff services, remains
future work. Completing stabilization does not implement that backlog.

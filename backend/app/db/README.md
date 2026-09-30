# Persistence boundary

This directory reserves ownership for shared PostgreSQL infrastructure. There
is no database runtime in the committed architecture baseline; the separate
PostgreSQL foundation should be integrated here when it is ready.

- `base.py`: SQLAlchemy declarative base, metadata, and common column conventions.
- `session.py`: engine/session factory and shutdown cleanup; create connections
  lazily so health checks and provider-only endpoints do not need a database.
- `model_registry.py`: explicit imports of module-owned ORM models for Alembic.
- `app/dependencies/database.py`: request-scoped session lifecycle for FastAPI.
- `app/modules/<feature>/models.py`: domain-owned tables and relationships.
- `app/modules/<feature>/repository.py`: queries using an injected session.
- `backend/alembic/` and `alembic.ini`: the single migration history and runner.

Services own transaction boundaries. Repositories must not create their own
engines, commit independently, import HTTP routes, or depend on Supabase APIs.
Use ordinary PostgreSQL via SQLAlchemy; production Supabase is supplied through
`DATABASE_URL`. Test repositories against a dedicated PostgreSQL instance,
separately from the default mocked test suite.

See [the architecture guide](../../../docs/architecture/backend-structure.md)
for module ownership and integration steps.

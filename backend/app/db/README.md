# Persistence boundary

Shared PostgreSQL infrastructure for the modular backend:

- `base.py`: SQLAlchemy declarative base, metadata, and common column conventions.
- `session.py`: lazy engine/session factory, request-scoped `get_db` dependency,
  and shutdown cleanup. Health checks and place search do not require a database.
- `app/models/__init__.py`: explicit ORM model registry used by Alembic.
- `backend/alembic/` and `alembic.ini`: the single migration history and runner.

The foundation's persistent models currently live in `app/models`. Add feature
repositories alongside their services as workflows are implemented. Services own
transaction boundaries; repositories receive sessions and must not create their
own engines or commit independently.

Use ordinary PostgreSQL via SQLAlchemy; production Supabase is supplied through
`DATABASE_URL`. Test against a dedicated database using `TEST_DATABASE_URL`.

See the [database guide](../../../docs/database.md) and
[architecture guide](../../../docs/architecture/backend-structure.md).

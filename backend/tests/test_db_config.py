import asyncio
from unittest.mock import AsyncMock

import pytest
from sqlalchemy.pool import NullPool

from app.core.config import Settings, database_url
from app.db import session as db_session


@pytest.mark.parametrize("scheme", ["postgres", "postgresql", "postgresql+asyncpg"])
def test_normalizes_postgres_urls_without_changing_credentials(scheme):
    url = database_url(
        f"{scheme}://user:p%40ss%25word@localhost:5432/db?sslmode=require"
    )
    assert url.drivername == "postgresql+asyncpg"
    assert url.password == "p@ss%word"
    assert url.query == {"ssl": "require"}
    assert "p@ss%word" not in str(url)


@pytest.mark.parametrize(
    "value",
    [
        "",
        "invalid-secret-url",
        "sqlite:///secret.db",
        "postgresql://u:secret@host:bad/db",
    ],
)
def test_bad_urls_have_safe_errors(value):
    with pytest.raises(ValueError) as error:
        database_url(value)
    assert "secret" not in str(error.value)


def test_conservative_pool_and_parameter_redaction(monkeypatch):
    monkeypatch.setenv("DB_POOL_SIZE", "3")
    monkeypatch.setenv("DB_MAX_OVERFLOW", "1")
    settings = Settings()
    monkeypatch.setattr(db_session, "settings", settings)
    engine = db_session.create_engine("postgresql://user:password@localhost/db")
    assert engine.pool.size() == 3
    assert engine.pool._max_overflow == 1
    assert engine.sync_engine.hide_parameters is True
    assert engine.echo is False


def test_external_pool_can_disable_application_pool():
    engine = db_session.create_engine(
        "postgresql://user:password@localhost/db", null_pool=True
    )
    assert isinstance(engine.pool, NullPool)


@pytest.mark.parametrize(
    "name,value",
    [("DB_POOL_SIZE", "0"), ("DB_MAX_OVERFLOW", "-1"), ("DB_ECHO", "debug")],
)
def test_invalid_pool_or_logging_settings_rejected(monkeypatch, name, value):
    monkeypatch.setenv(name, value)
    with pytest.raises(ValueError):
        Settings()


@pytest.mark.asyncio
@pytest.mark.parametrize("failure", [None, RuntimeError, asyncio.CancelledError])
async def test_dependency_closes_and_rolls_back_without_committing(
    monkeypatch, failure
):
    session = AsyncMock()
    session.__aenter__.return_value = session
    monkeypatch.setattr(db_session, "get_session_factory", lambda: lambda: session)
    dependency = db_session.get_db()
    assert await anext(dependency) is session
    if failure:
        with pytest.raises(failure):
            await dependency.athrow(failure())
        session.rollback.assert_awaited_once()
    else:
        with pytest.raises(StopAsyncIteration):
            await anext(dependency)
        session.rollback.assert_not_awaited()
    session.commit.assert_not_awaited()
    session.__aexit__.assert_awaited_once()

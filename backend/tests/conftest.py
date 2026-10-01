"""Integration tests run migrations on an explicitly selected test database."""

import os
import subprocess
import sys
from pathlib import Path

import httpx
import pytest
import pytest_asyncio
from fastapi.testclient import TestClient
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import database_url
from app.db.session import create_engine
from app.main import app


@pytest.fixture(scope="session")
def postgres_url():
    value = os.getenv("TEST_DATABASE_URL", "")
    if not value:
        pytest.skip(
            "Set TEST_DATABASE_URL to a dedicated PostgreSQL database ending in _test"
        )
    url = database_url(value)
    if not url.database or not url.database.endswith("_test"):
        pytest.fail(
            "TEST_DATABASE_URL must select a dedicated database ending in _test"
        )
    # Run real migrations, never metadata.create_all(). Do not print the URL.
    subprocess.run(
        [sys.executable, "-m", "alembic", "upgrade", "head"],
        cwd=Path(__file__).resolve().parents[1],
        env={**os.environ, "DATABASE_URL": value, "DB_ECHO": "false"},
        check=True,
    )
    return value


@pytest_asyncio.fixture
async def db(postgres_url):
    engine = create_engine(postgres_url, null_pool=True)
    try:
        async with engine.connect() as connection:
            transaction = await connection.begin()
            # Even tests that commit remain contained by the outer transaction.
            async with AsyncSession(
                bind=connection,
                expire_on_commit=False,
                join_transaction_mode="create_savepoint",
            ) as session:
                yield session
            await transaction.rollback()
    finally:
        await engine.dispose()


@pytest.fixture(autouse=True)
def reset_runtime_singletons():
    from app.integrations.maps import ola_maps
    from app.modules.destinations import router as destinations_router
    from app.modules.speech import router as speech_router

    ola_maps.close_shared_client()
    destinations_router._reset_rate_limit()
    speech_router._reset_rate_limit()
    yield
    ola_maps.close_shared_client()
    destinations_router._reset_rate_limit()
    speech_router._reset_rate_limit()


@pytest.fixture(autouse=True)
def block_provider_network(monkeypatch):
    def blocked(*args, **kwargs):
        raise AssertionError("Use a fake provider or httpx.MockTransport in tests")

    async def async_blocked(*args, **kwargs):
        blocked()

    monkeypatch.setattr(httpx.HTTPTransport, "handle_request", blocked)
    monkeypatch.setattr(httpx.AsyncHTTPTransport, "handle_async_request", async_blocked)


@pytest.fixture
def client():
    with TestClient(app) as test_client:
        yield test_client


@pytest.fixture
def override_dependency():
    original = app.dependency_overrides.copy()
    yield app.dependency_overrides
    app.dependency_overrides.clear()
    app.dependency_overrides.update(original)

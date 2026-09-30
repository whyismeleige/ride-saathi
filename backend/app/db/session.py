"""One engine per process and one session per dependency lifecycle."""

from collections.abc import AsyncGenerator
from functools import lru_cache

from sqlalchemy.ext.asyncio import (
    AsyncEngine,
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)
from sqlalchemy.pool import NullPool

from app.config import database_url, settings


def create_engine(url: str, *, null_pool: bool = False) -> AsyncEngine:
    options = (
        {"poolclass": NullPool}
        if null_pool
        else {
            "pool_size": settings.db_pool_size,
            "max_overflow": settings.db_max_overflow,
            "pool_timeout": 30,
        }
    )
    return create_async_engine(
        database_url(url),
        echo=settings.db_echo,
        hide_parameters=True,  # SQL logs/errors must not expose token ciphertext or PII.
        pool_pre_ping=True,
        **options,
    )


@lru_cache(maxsize=1)
def get_engine() -> AsyncEngine:
    # Lazy initialization lets existing non-DB endpoints run before DB is configured.
    return create_engine(settings.database_url, null_pool=settings.db_null_pool)


@lru_cache(maxsize=1)
def get_session_factory() -> async_sessionmaker[AsyncSession]:
    return async_sessionmaker(get_engine(), expire_on_commit=False)


async def get_db() -> AsyncGenerator[AsyncSession, None]:
    """Services explicitly commit; uncommitted work rolls back on close."""
    async with get_session_factory()() as session:
        try:
            yield session
        except BaseException:
            await session.rollback()
            raise


async def dispose_engine() -> None:
    """Release the pool on application shutdown without creating one."""
    if get_engine.cache_info().currsize:
        await get_engine().dispose()
    get_session_factory.cache_clear()
    get_engine.cache_clear()

import os
from pathlib import Path

from dotenv import load_dotenv
from sqlalchemy.engine import URL, make_url
from sqlalchemy.exc import ArgumentError

# Same .env source for uvicorn, CLI tools and Alembic; shell values take priority.
load_dotenv(Path(__file__).resolve().parents[1] / ".env")


def database_url(value: str) -> URL:
    """Normalize provider URLs without ever including credentials in errors."""
    if not value.strip():
        raise ValueError("DATABASE_URL must be set to use PostgreSQL")
    try:
        url = make_url(value.strip())
    except (ArgumentError, ValueError):
        raise ValueError("DATABASE_URL must be a valid PostgreSQL URL") from None
    if url.drivername not in {"postgres", "postgresql", "postgresql+asyncpg"}:
        raise ValueError("DATABASE_URL must use PostgreSQL with asyncpg")
    url = url.set(drivername="postgresql+asyncpg")
    # asyncpg accepts ssl, whereas libpq-style provider URLs often use sslmode.
    if "sslmode" in url.query:
        if "ssl" in url.query:
            raise ValueError("Use either ssl or sslmode in DATABASE_URL, not both")
        ssl_mode = url.query["sslmode"]
        url = url.difference_update_query(["sslmode"]).update_query_dict(
            {"ssl": ssl_mode}
        )
    return url


def _bool_env(name: str, default: str = "false") -> bool:
    value = os.getenv(name, default).strip().lower()
    if value not in {"true", "false", "1", "0"}:
        raise ValueError(f"{name} must be true or false")
    return value in {"true", "1"}


class Settings:
    """Environment-driven configuration.

    Values come from the environment or backend/.env at import time. A real
    secret (``OLA_MAPS_API_KEY``) must only ever live on the server.
    """

    def __init__(self) -> None:
        self.environment: str = os.getenv("ENVIRONMENT", "development").strip()
        self.port: int = int(os.getenv("PORT", "8000") or "8000")
        self.ola_maps_api_key: str = os.getenv("OLA_MAPS_API_KEY", "").strip()
        self.ola_maps_base_url: str = (
            os.getenv("OLA_MAPS_BASE_URL", "https://api.olamaps.io").strip().rstrip("/")
        )
        self.ola_maps_timeout_seconds: float = float(
            os.getenv("OLA_MAPS_TIMEOUT_SECONDS", "8") or "8"
        )

        self.database_url: str = os.getenv("DATABASE_URL", "").strip()
        self.db_echo: bool = _bool_env("DB_ECHO")
        self.db_pool_size: int = int(os.getenv("DB_POOL_SIZE", "5") or "5")
        self.db_max_overflow: int = int(os.getenv("DB_MAX_OVERFLOW", "0") or "0")
        self.db_null_pool: bool = _bool_env("DB_NULL_POOL")
        if self.db_pool_size < 1 or self.db_max_overflow < 0:
            raise ValueError(
                "DB_POOL_SIZE must be positive and DB_MAX_OVERFLOW nonnegative"
            )


settings = Settings()

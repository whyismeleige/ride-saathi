"""Process configuration; shell values override backend/.env."""

from pathlib import Path
from typing import Literal

from pydantic import Field, SecretStr, ValidationInfo, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict
from sqlalchemy.engine import URL, make_url
from sqlalchemy.exc import ArgumentError


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


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=Path(__file__).resolve().parents[2] / ".env",
        env_file_encoding="utf-8",
        extra="ignore",  # Compose-only variables may share the same .env file.
        str_strip_whitespace=True,
        hide_input_in_errors=True,
    )

    environment: Literal["development", "testing", "production"] = "development"
    log_level: Literal["DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL"] = "INFO"
    port: int = Field(default=8000, ge=1, le=65535)
    ola_maps_api_key: str = Field(default="", repr=False)
    ola_maps_base_url: str = "https://api.olamaps.io"
    ola_maps_timeout_seconds: float = Field(default=8, gt=0)

    # Persistence is optional for startup and place search.
    database_url: str = Field(default="", repr=False)

    # No fallback signing/encryption keys: auth fails closed until configured.
    app_jwt_secret: SecretStr = Field(default=SecretStr(""), repr=False)
    app_jwt_algorithm: Literal["HS256"] = "HS256"
    app_access_token_ttl_seconds: int = Field(default=3600, ge=60, le=2592000)
    app_callback_uri: str = ""
    uber_client_id: str = ""
    uber_client_secret: SecretStr = Field(default=SecretStr(""), repr=False)
    uber_redirect_uri: str = ""
    uber_scopes: str = "profile offline_access"
    uber_timeout_seconds: float = Field(default=8, gt=0, le=60)
    uber_credential_encryption_key: SecretStr = Field(default=SecretStr(""), repr=False)

    db_echo: bool = False
    db_pool_size: int = Field(default=5, ge=1)
    db_max_overflow: int = Field(default=0, ge=0)
    db_null_pool: bool = False

    @field_validator("db_echo", "db_null_pool", mode="before")
    @classmethod
    def strict_database_boolean(cls, value: object) -> object:
        if isinstance(value, str):
            value = value.strip().lower()
            if value not in {"true", "false", "1", "0"}:
                raise ValueError("Database boolean settings must be true or false")
        return value

    @field_validator(
        "port", "ola_maps_timeout_seconds", "db_pool_size", "db_max_overflow",
        mode="before",
    )
    @classmethod
    def empty_numeric_defaults(cls, value: object, info: ValidationInfo) -> object:
        # Preserve the previous settings' treatment of empty numeric variables.
        return cls.model_fields[info.field_name].default if value == "" else value

    @field_validator("ola_maps_base_url")
    @classmethod
    def strip_trailing_slash(cls, value: str) -> str:
        return value.rstrip("/")


settings = Settings()

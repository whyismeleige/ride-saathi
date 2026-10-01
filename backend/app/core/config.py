"""Process configuration; shell values override backend/.env."""

from pathlib import Path
from typing import Literal

from pydantic import Field, ValidationInfo, field_validator, model_validator
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

    # First-party Ride Saathi authentication is future work; no auth settings
    # live here until an implementation needs them. V1 deep-link handoff to
    # the external Uber app must NOT depend on Uber OAuth, so no Uber
    # OAuth/credential settings are configured.

    db_echo: bool = False
    db_pool_size: int = Field(default=5, ge=1)
    db_max_overflow: int = Field(default=0, ge=0)
    db_null_pool: bool = False

    # Minimal abuse protection for the privileged maps proxy (single-instance
    # in-memory window; see destinations router docstring).
    maps_rate_limit_per_minute: int = Field(default=60, ge=1, le=6000)
    maps_rate_limit_window_seconds: int = Field(default=60, ge=1, le=3600)
    trust_proxy_headers: bool = False

    # Optional online speech; credentials stay on the backend.
    azure_speech_key: str = Field(default="", repr=False)
    azure_speech_region: str = Field(default="", pattern=r"^[a-z0-9]*$")
    tts_voice_en: str = "en-IN-NeerjaNeural"
    tts_voice_hi: str = "hi-IN-SwaraNeural"
    tts_voice_te: str = "te-IN-ShrutiNeural"
    tts_timeout_seconds: float = Field(default=8, gt=0, le=30)
    tts_rate_limit_per_minute: int = Field(default=30, ge=1, le=600)
    tts_global_rate_limit_per_minute: int = Field(default=120, ge=1, le=6000)
    azure_speech_endpoint: str = ""
    azure_openai_endpoint: str = ""
    azure_openai_key: str = Field(default="", repr=False)
    azure_openai_deployment: str = ""
    stt_timeout_seconds: float = Field(default=10, gt=0, le=30)
    llm_timeout_seconds: float = Field(default=8, gt=0, le=30)

    @field_validator("azure_speech_endpoint", "azure_openai_endpoint")
    @classmethod
    def azure_resource_endpoint(cls, value: str, info: ValidationInfo) -> str:
        from urllib.parse import urlsplit

        if not value:
            return value
        parsed = urlsplit(value)
        resource_domains = (
            ".cognitive.microsoft.com",
            ".cognitiveservices.azure.com",
            ".openai.azure.com",
            ".services.ai.azure.com",
        )
        allowed_paths = {"", "/"}
        if info.field_name == "azure_openai_endpoint":
            allowed_paths.update({"/openai/v1", "/openai/v1/"})
        if (parsed.scheme != "https" or not parsed.hostname
                # Include the host boundary so lookalike domains are rejected.
                or not any(f"{domain}/" in f"{parsed.hostname}/" for domain in resource_domains)
                or parsed.username or parsed.password or parsed.query or parsed.fragment
                or parsed.path not in allowed_paths or parsed.port not in {None, 443}):
            raise ValueError("Use an HTTPS Azure resource root endpoint")
        # Provider clients append their API paths to this resource root.
        return parsed._replace(path="").geturl()

    @field_validator("db_echo", "db_null_pool", "trust_proxy_headers", mode="before")
    @classmethod
    def strict_database_boolean(cls, value: object) -> object:
        if isinstance(value, str):
            value = value.strip().lower()
            if value not in {"true", "false", "1", "0"}:
                raise ValueError("Database boolean settings must be true or false")
        return value

    @field_validator(
        "port", "ola_maps_timeout_seconds", "db_pool_size", "db_max_overflow",
        "maps_rate_limit_per_minute", "maps_rate_limit_window_seconds",
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

    @model_validator(mode="after")
    def require_production_secrets(self):
        if self.environment == "production":
            missing: list[str] = []
            if not self.ola_maps_api_key:
                missing.append("OLA_MAPS_API_KEY")
            if not self.database_url.strip():
                missing.append("DATABASE_URL")
            if missing:
                raise ValueError(
                    f"Missing required production settings: {', '.join(missing)}"
                )
        return self


settings = Settings()

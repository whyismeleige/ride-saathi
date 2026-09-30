"""Process configuration; shell values override backend/.env."""

from pathlib import Path
from typing import Literal

from pydantic import Field, ValidationInfo, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


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

    # Reserved for persistence; startup and place search do not require a DB.
    database_url: str = Field(default="", repr=False)

    @field_validator("port", "ola_maps_timeout_seconds", mode="before")
    @classmethod
    def empty_numeric_defaults(cls, value: object, info: ValidationInfo) -> object:
        # Preserve the previous settings' treatment of empty numeric variables.
        return cls.model_fields[info.field_name].default if value == "" else value

    @field_validator("ola_maps_base_url")
    @classmethod
    def strip_trailing_slash(cls, value: str) -> str:
        return value.rstrip("/")


settings = Settings()

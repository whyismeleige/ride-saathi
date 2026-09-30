import os

import pytest
from pydantic import ValidationError

from app.core.config import Settings


@pytest.fixture(autouse=True)
def clear_settings_environment(monkeypatch):
    for field in Settings.model_fields:
        monkeypatch.delenv(field.upper(), raising=False)
        monkeypatch.delenv(field, raising=False)


@pytest.mark.parametrize("environment", ["development", "testing", "production"])
def test_supported_environments(monkeypatch, environment):
    monkeypatch.setenv("ENVIRONMENT", environment)
    if environment == "production":
        monkeypatch.setenv("OLA_MAPS_API_KEY", "test-key")
        monkeypatch.setenv(
            "DATABASE_URL", "postgresql://user:password@localhost/db"
        )
    assert Settings(_env_file=None).environment == environment


def test_shell_overrides_dotenv_without_changing_process_environment(tmp_path, monkeypatch):
    env_file = tmp_path / ".env"
    env_file.write_text(
        "OLA_MAPS_API_KEY=file-key\nPORT=9000\nBIND_HOST=127.0.0.1\n"
        "OLA_MAPS_BASE_URL=https://maps.example/\n"
    )
    monkeypatch.setenv("OLA_MAPS_API_KEY", "shell-key")
    settings = Settings(_env_file=env_file)
    assert settings.ola_maps_api_key == "shell-key"
    assert settings.port == 9000
    assert settings.ola_maps_base_url == "https://maps.example"
    assert "shell-key" not in repr(settings)
    assert "PORT" not in os.environ


def test_blank_key_disables_provider_even_when_dotenv_has_a_key(tmp_path, monkeypatch):
    env_file = tmp_path / ".env"
    env_file.write_text("OLA_MAPS_API_KEY=file-key\n")
    monkeypatch.setenv("OLA_MAPS_API_KEY", "")
    assert Settings(_env_file=env_file).ola_maps_api_key == ""


def test_empty_numeric_variables_preserve_defaults(monkeypatch):
    monkeypatch.setenv("PORT", "")
    monkeypatch.setenv("OLA_MAPS_TIMEOUT_SECONDS", "")
    settings = Settings(_env_file=None)
    assert settings.port == 8000
    assert settings.ola_maps_timeout_seconds == 8


@pytest.mark.parametrize("values", [
    {"port": 0}, {"ola_maps_timeout_seconds": 0},
    {"environment": "invalid"}, {"log_level": "invalid"},
])
def test_invalid_configuration_is_rejected(values):
    with pytest.raises(ValidationError):
        Settings(_env_file=None, **values)

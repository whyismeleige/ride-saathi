import os


class Settings:
    """Environment-driven configuration.

    All values are read from environment variables at import time. A real
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


settings = Settings()
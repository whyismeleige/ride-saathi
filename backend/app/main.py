"""FastAPI entry point: configure infrastructure and compose HTTP routes."""

from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.router import api_router
from app.core.logging import configure_logging, request_context
from app.db.session import dispose_engine
from app.integrations.maps.ola_maps import close_shared_client


@asynccontextmanager
async def lifespan(app: FastAPI):
    try:
        yield
    finally:
        close_shared_client()
        await dispose_engine()


configure_logging()

app = FastAPI(
    title="Ride Saathi API",
    version="1",
    description=(
        "Place search and voice assistance for Ride Saathi. "
        "No client authentication is currently required; provider credentials stay on the server. "
        "Place search and speech requests are rate limited. "
        "Use Try it out to send real requests to this server."
    ),
    docs_url="/docs",
    redoc_url=None,
    openapi_tags=[
        {"name": "Health", "description": "Liveness and database readiness probes."},
        {"name": "Places", "description": "Destination search through Ola Maps."},
        {"name": "Speech", "description": "Azure speech synthesis and voice interpretation."},
    ],
    lifespan=lifespan,
)
app.middleware("http")(request_context)
app.include_router(api_router)

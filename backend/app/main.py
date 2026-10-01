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
    docs_url=None,
    redoc_url=None,
    lifespan=lifespan,
)
app.middleware("http")(request_context)
app.include_router(api_router)

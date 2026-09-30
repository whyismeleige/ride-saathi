"""FastAPI entry point: configure infrastructure and compose HTTP routes."""

from fastapi import FastAPI

from app.api.router import api_router
from app.core.logging import configure_logging, request_context

configure_logging()

app = FastAPI(title="Ride Saathi API", version="1", docs_url=None, redoc_url=None)
app.middleware("http")(request_context)
app.include_router(api_router)

"""Operational liveness/readiness endpoints, outside the versioned API prefix."""

import asyncio
from typing import Literal

from fastapi import APIRouter
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from sqlalchemy import text

from app.core.config import settings
from app.db.session import create_engine

router = APIRouter(tags=["Health"])


class HealthResponse(BaseModel):
    status: Literal["ok"]


class ReadyResponse(BaseModel):
    status: Literal["ok"]
    database: Literal["connected", "not_configured"]


class NotReadyResponse(BaseModel):
    status: Literal["not_ready"]
    database: Literal["unreachable"]


@router.get("/health", summary="Check liveness", response_model=HealthResponse)
def health() -> dict[str, str]:
    """Cheap liveness probe; never touches the database or providers."""
    return {"status": "ok"}


@router.get("/ready", summary="Check readiness", response_model=ReadyResponse,
            responses={503: {"model": NotReadyResponse, "description": "Configured database is unreachable."}})
async def ready() -> JSONResponse:
    """Readiness probe: config + optional database connectivity.

    Never calls paid external providers (no Ola Maps requests).
    """
    if not settings.database_url.strip():
        return JSONResponse(status_code=200, content={"status": "ok", "database": "not_configured"})
    engine = None
    try:
        engine = create_engine(settings.database_url, null_pool=True)
        async with asyncio.timeout(5):
            async with engine.connect() as connection:
                await connection.execute(text("SELECT 1"))
    except Exception:
        return JSONResponse(status_code=503, content={"status": "not_ready", "database": "unreachable"})
    finally:
        if engine is not None:
            await engine.dispose()
    return JSONResponse(status_code=200, content={"status": "ok", "database": "connected"})

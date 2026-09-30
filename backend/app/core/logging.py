"""Operational logging and request correlation shared by HTTP endpoints."""

import logging
import time
import uuid

from fastapi import Request

from app.core.config import settings

logger = logging.getLogger("ride-saathi")


def configure_logging() -> None:
    logging.basicConfig(
        level=settings.log_level,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
    # HTTPX's INFO log includes full upstream URLs with queries and API keys.
    logging.getLogger("httpx").setLevel(logging.WARNING)
    logging.getLogger("httpcore").setLevel(logging.WARNING)


async def request_context(request: Request, call_next):
    """Attach and echo a request id and log only operational data.

    User addresses, coordinates, and search queries are intentionally not
    logged; upstream credentials never appear in logs.
    """
    request_id = request.headers.get("X-Request-Id") or uuid.uuid4().hex[:16]
    start = time.perf_counter()
    try:
        response = await call_next(request)
    except Exception:
        logger.info(
            "request path=%s status=500 latency_ms=%d request_id=%s",
            request.url.path, round((time.perf_counter() - start) * 1000), request_id,
        )
        raise
    response.headers["X-Request-Id"] = request_id
    logger.info(
        "request path=%s status=%d latency_ms=%d request_id=%s",
        request.url.path, response.status_code, round((time.perf_counter() - start) * 1000), request_id,
    )
    return response

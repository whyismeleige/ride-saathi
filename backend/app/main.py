import logging
import time
import uuid

from fastapi import FastAPI, Request

from .routes.places import router as places_router

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
logger = logging.getLogger("ride-saathi")

app = FastAPI(title="Ride Saathi API", version="1", docs_url=None, redoc_url=None)


@app.middleware("http")
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


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


app.include_router(places_router)
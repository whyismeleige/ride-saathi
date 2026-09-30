"""Compose domain routers under the existing public v1 prefix."""

from fastapi import APIRouter

from app.modules.destinations.router import router as destinations_router

router = APIRouter(prefix="/v1")
router.include_router(destinations_router)

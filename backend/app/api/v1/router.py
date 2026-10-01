"""Compose domain routers under the existing public v1 prefix."""

from fastapi import APIRouter

from app.modules.destinations.router import router as destinations_router
from app.modules.speech.router import router as speech_router
from app.modules.speech.turn import router as turn_router

router = APIRouter(prefix="/v1")
router.include_router(destinations_router)
router.include_router(speech_router)
router.include_router(turn_router)

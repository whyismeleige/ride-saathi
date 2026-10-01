"""Cancel provider work when a caller abandons a speech request."""

import asyncio
from collections.abc import Coroutine
from typing import Any

from fastapi import Request


class ClientDisconnected(Exception):
    """The caller abandoned the response; do not continue paid provider work."""


async def until_disconnect[Result](request: Request, operation: Coroutine[Any, Any, Result]) -> Result:
    task = asyncio.create_task(operation)
    try:
        while not task.done():
            await asyncio.wait({task}, timeout=0.1)
            if not task.done() and await request.is_disconnected():
                raise ClientDisconnected()
        return await task
    finally:
        if not task.done():
            task.cancel()
        await asyncio.gather(task, return_exceptions=True)

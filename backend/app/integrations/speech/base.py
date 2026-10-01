from typing import Protocol

MAX_AUDIO_BYTES = 2 * 1024 * 1024


class SpeechError(Exception):
    def __init__(self, category: str):
        super().__init__(category)
        self.category = category


class SpeechProvider(Protocol):
    async def synthesize(self, text: str, language: str) -> bytes: ...

"""The sync provider shares exactly one connection pool across worker threads."""

import time
from concurrent.futures import ThreadPoolExecutor
from unittest.mock import MagicMock

from app.integrations.maps import ola_maps


def test_concurrent_requests_share_one_client_and_shutdown_closes_it(monkeypatch):
    client = MagicMock()

    def build():
        # Yield to other workers while the first connection pool is constructed.
        time.sleep(0.01)
        return client

    builder = MagicMock(side_effect=build)
    monkeypatch.setattr(ola_maps, "build_client", builder)
    with ThreadPoolExecutor(max_workers=16) as executor:
        clients = list(executor.map(lambda _: ola_maps.get_shared_client(), range(32)))
    assert all(value is client for value in clients)
    builder.assert_called_once()
    ola_maps.close_shared_client()
    ola_maps.close_shared_client()
    client.close.assert_called_once()

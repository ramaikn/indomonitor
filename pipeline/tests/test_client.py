from datetime import UTC, datetime, timedelta
from email.utils import format_datetime

import httpx
import pytest

from media_pipeline.client import GdeltClient


def test_retry_after_date_and_seconds():
    assert GdeltClient.retry_delay("25", 12) == 25
    assert GdeltClient.retry_delay("bad header", 12) == 12
    assert GdeltClient.retry_delay(format_datetime(datetime.now(UTC) + timedelta(hours=1)), 12) > 300


def test_rate_limit_not_disguised_as_success(monkeypatch):
    monkeypatch.setattr("media_pipeline.client.time.sleep", lambda _: None)
    calls = []

    def response(request):
        calls.append(request)
        return httpx.Response(429, headers={"Retry-After": "12"})

    with GdeltClient() as client:
        client.client.close()
        client.client = httpx.Client(transport=httpx.MockTransport(response))
        with pytest.raises(httpx.HTTPStatusError) as error:
            client.get("https://api.gdeltproject.org/api/v2/doc/doc")
    assert error.value.response.status_code == 429
    assert len(calls) == 3

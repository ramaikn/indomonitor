import os
import uuid
from datetime import UTC, datetime, timedelta

import psycopg
import pytest

from media_pipeline.normalize import Article
from media_pipeline.store import Store


@pytest.mark.skipif(not os.environ.get("TEST_DATABASE_URL"), reason="Database pengujian terpisah diperlukan")
def test_idempotency_and_rollback():
    url = os.environ["TEST_DATABASE_URL"]
    # Database khusus test, bukan database aplikasi atau data langsung.
    with psycopg.connect(url, autocommit=True) as db:
        db.execute("TRUNCATE article_observations,articles,quarantines,checkpoints,ingestion_runs "
                   "RESTART IDENTITY CASCADE")
    item = Article("https://example.test/1", "Fixture transaksi", "example.test", "ind", "mentions_indonesia",
                   datetime(2026, 1, 1, tzinfo=UTC), None, [], [])
    with Store(url) as store:
        run = store.ingest("test", "fixture-1", {}, lambda: [item], lambda value: value)
        repeated = store.ingest("test", "fixture-1", {}, lambda: [item], lambda value: value,
                                checkpoint=False)
        assert run["inserted"] == 1
        assert repeated["duplicates"] == 1
        older = Article(item.url, "Judul lama", item.domain, "ind", item.scope,
                        datetime(2025, 12, 31, tzinfo=UTC), -4, ["OLD"], [])
        store.ingest("test", "older", {}, lambda: [older], lambda value: value)
        assert store.connection.execute("SELECT title FROM articles").fetchone()[0] == item.title
        assert store.connection.execute("SELECT tone FROM articles").fetchone()[0] == -4
        assert store.ingest("test", "fixture-1", {}, lambda: [], lambda value: value)["status"] == "skipped"

        def broken():
            yield Article("https://example.test/2", None, "example.test", None, "mentions_indonesia",
                          datetime(2026, 1, 1, tzinfo=UTC), None, [], [])
            raise RuntimeError("Simulasi proses gagal")

        artifact = str(uuid.uuid4())
        with pytest.raises(RuntimeError):
            store.ingest("test", artifact, {}, broken, lambda value: value)
        db = store.connection
        assert db.execute("SELECT count(*) FROM articles").fetchone()[0] == 1
        checkpoint = db.execute("SELECT count(*) FROM checkpoints WHERE artifact=%s", (artifact,)).fetchone()
        status = db.execute("SELECT status FROM ingestion_runs WHERE artifact=%s", (artifact,)).fetchone()
        assert checkpoint[0] == 0
        assert status[0] == "failed"


@pytest.mark.skipif(not os.environ.get("TEST_DATABASE_URL"), reason="Database pengujian terpisah diperlukan")
def test_archive_backfills_gaps_and_limits_work():
    url = os.environ["TEST_DATABASE_URL"]
    with psycopg.connect(url, autocommit=True) as db:
        db.execute("TRUNCATE article_observations,articles,quarantines,checkpoints,ingestion_runs "
                   "RESTART IDENTITY CASCADE")
    latest = datetime(2026, 1, 1, 1, tzinfo=UTC)
    base = "https://data.gdeltproject.org/gdeltv2/"
    suffix = ".gkg.csv.zip"
    with Store(url) as store:
        initial, remaining = store.archive_batches("original", latest, 2)
        assert initial == [latest - timedelta(minutes=15), latest]
        assert remaining == 0
        # Satu batch sukses dan satu gagal: setelah jeda, gap lama didahulukan.
        for stamp, status in [(initial[0], "failed"), (initial[1], "success")]:
            store.connection.execute(
                "INSERT INTO ingestion_runs(id,adapter,artifact,status) VALUES(%s,'gkg',%s,%s)",
                (uuid.uuid4(), base + stamp.strftime("%Y%m%d%H%M%S") + suffix, status),
            )
        future = latest + timedelta(hours=1)
        selected, remaining = store.archive_batches("original", future, 2)
        assert selected == [initial[0], latest + timedelta(minutes=15)]
        assert remaining == 3
        # Translation memiliki checkpoint dan riwayat terpisah.
        translated, remaining = store.archive_batches("translation", future, 2)
        assert translated == [future - timedelta(minutes=15), future]
        assert remaining == 0

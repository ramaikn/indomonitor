import re
import uuid
from collections import Counter
from collections.abc import Callable, Iterable
from datetime import UTC, datetime, timedelta

import psycopg
from psycopg.types.json import Jsonb

from .normalize import Article


class Store:
    def __init__(self, url: str):
        self.connection = psycopg.connect(url, autocommit=True)

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.connection.close()

    def archive_batches(self, edition: str, latest: datetime, limit: int) -> tuple[list[datetime], int]:
        """Isi gap sejak batch pertama yang pernah dicoba, dengan batas kerja per putaran."""
        suffix = ".translation.gkg.csv.zip" if edition == "translation" else ".gkg.csv.zip"
        pattern = re.compile(r"https://data\.gdeltproject\.org/gdeltv2/(\d{14})" + re.escape(suffix) + r"$")
        rows = self.connection.execute(
            "SELECT artifact,status FROM ingestion_runs WHERE adapter='gkg' AND artifact LIKE %s",
            ("https://data.gdeltproject.org/gdeltv2/%" + suffix,),
        ).fetchall()
        attempted = {}
        for artifact, status in rows:
            match = pattern.fullmatch(artifact)
            if match:
                stamp = datetime.strptime(match.group(1), "%Y%m%d%H%M%S").replace(tzinfo=UTC)
                if stamp <= latest:
                    attempted.setdefault(stamp, set()).add(status)
        start = min(attempted) if attempted else latest - timedelta(minutes=15 * (limit - 1))
        total = int((latest - start) / timedelta(minutes=15)) + 1
        complete = sum("success" in states or "partial" in states for stamp, states in attempted.items()
                       if stamp >= start)
        pending = max(0, total - complete)
        batches = []
        for index in range(total):
            stamp = start + timedelta(minutes=15 * index)
            if not {"success", "partial"}.intersection(attempted.get(stamp, set())):
                batches.append(stamp)
                if len(batches) == limit:
                    break
        return batches, max(0, pending - len(batches))

    def ingest(self, adapter: str, artifact: str, params: dict,
               fetch: Callable[[], Iterable], parse: Callable, *, checkpoint: bool = True) -> dict:
        run_id = uuid.uuid4()
        db = self.connection
        if not db.execute("SELECT pg_try_advisory_lock(58192017)").fetchone()[0]:
            raise RuntimeError("Pipeline lain sedang berjalan. Tunggu sampai selesai.")
        counts = Counter(fetched=0, accepted=0, inserted=0, updated=0, duplicates=0,
                         rejected=0, out_of_scope=0)
        try:
            db.execute("""INSERT INTO ingestion_runs(id,adapter,artifact,status,parameters)
                          VALUES(%s,%s,%s,'running',%s)""", (run_id, adapter, artifact, Jsonb(params)))
            if checkpoint and db.execute("SELECT 1 FROM checkpoints WHERE adapter=%s AND artifact=%s",
                                         (adapter, artifact)).fetchone():
                db.execute("UPDATE ingestion_runs SET status='skipped',finished_at=now() WHERE id=%s",
                           (run_id,))
                return {"run_id": str(run_id), "status": "skipped", **counts}
            rows = fetch()
            with db.transaction():
                for number, row in enumerate(rows, 1):
                    counts["fetched"] += 1
                    try:
                        article = parse(row)
                    except (ValueError, KeyError, TypeError, OverflowError):
                        counts["rejected"] += 1
                        db.execute("INSERT INTO quarantines(run_id,row_number,reason) VALUES(%s,%s,%s)",
                                   (run_id, number, "Metadata sumber tidak valid; isi mentah tidak disimpan"))
                        continue
                    if article is None:
                        counts["out_of_scope"] += 1
                        continue
                    counts["accepted"] += 1
                    self._save(article, run_id, counts)
                status = "partial" if counts["rejected"] else "success"
                db.execute("""UPDATE ingestion_runs SET status=%s,finished_at=now(),fetched=%s,
                    accepted=%s,inserted=%s,updated=%s,duplicates=%s,rejected=%s,out_of_scope=%s
                    WHERE id=%s""",
                           (status, *(counts[key] for key in ("fetched", "accepted", "inserted", "updated",
                                                             "duplicates", "rejected", "out_of_scope")),
                            run_id))
                # Tidak melewati baris rusak secara diam-diam; run partial dapat dicoba ulang.
                if checkpoint and status == "success":
                    db.execute("INSERT INTO checkpoints(adapter,artifact,run_id) VALUES(%s,%s,%s)",
                               (adapter, artifact, run_id))
            return {"run_id": str(run_id), "status": status, **counts}
        except Exception as exc:
            # Jenis error cukup untuk UI; URL database dan detail koneksi tidak dipublikasikan.
            db.execute("UPDATE ingestion_runs SET status='failed',finished_at=now(),error=%s WHERE id=%s",
                       (type(exc).__name__ + ": pengambilan/penyimpanan gagal; periksa log lokal", run_id))
            raise
        finally:
            db.execute("SELECT pg_advisory_unlock(58192017)")

    def _save(self, item: Article, run_id: uuid.UUID, counts: Counter) -> None:
        db = self.connection
        existing = db.execute("""SELECT id,title,language,scope,last_seen_at,tone,themes,locations
                              FROM articles WHERE url=%s""", (item.url,)).fetchone()
        if existing:
            article_id = existing[0]
            latest = item.seen_at >= existing[4]
            title = item.title if item.title and (latest or not existing[1]) else existing[1]
            language = item.language if item.language and (latest or not existing[2]) else existing[2]
            scope = "indonesian_outlet" if "indonesian_outlet" in {item.scope, existing[3]} else item.scope
            tone = item.tone if item.tone is not None and (latest or existing[5] is None) else existing[5]
            themes = item.themes if item.themes and (latest or not existing[6]) else existing[6]
            locations = item.locations if item.locations and (latest or not existing[7]) else existing[7]
            merged = Article(item.url, title, item.domain, language, scope, item.seen_at,
                             tone, themes, locations)
            changed = (title, language, scope, tone, themes, locations) != (
                existing[1], existing[2], existing[3], existing[5], existing[6], existing[7]
            )
            counts["updated" if changed else "duplicates"] += 1
            db.execute("""UPDATE articles SET title=%s,language=%s,scope=%s,
                tone=%s,themes=%s,locations=%s,
                first_seen_at=LEAST(first_seen_at,%s),last_seen_at=GREATEST(last_seen_at,%s),
                content_hash=%s,last_run_id=%s,collected_at=now() WHERE id=%s""",
                       (title, language, scope, tone, Jsonb(themes), Jsonb(locations),
                        item.seen_at, item.seen_at, merged.fingerprint, run_id, article_id))
        else:
            article_id = db.execute("""INSERT INTO articles(url,title,domain,language,scope,first_seen_at,
                last_seen_at,tone,themes,locations,content_hash,last_run_id)
                VALUES(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s) RETURNING id""",
                                    (item.url, item.title, item.domain, item.language, item.scope,
                                     item.seen_at,
                                     item.seen_at, item.tone, Jsonb(item.themes), Jsonb(item.locations),
                                     item.fingerprint, run_id)).fetchone()[0]
            counts["inserted"] += 1
        db.execute("INSERT INTO article_observations(article_id,run_id) VALUES(%s,%s) ON CONFLICT DO NOTHING",
                   (article_id, run_id))

import argparse
import csv
import json
import sys
import time
from datetime import UTC, datetime, timedelta

from .client import GdeltClient
from .config import database_url
from .normalize import parse_doc, parse_gkg
from .store import Store


def collect(args) -> list[dict]:
    output = []
    if args.command == "archive" and args.edition == "both":
        errors = []
        for edition in ("original", "translation"):
            copy = argparse.Namespace(**vars(args))
            copy.edition = edition
            try:
                output.extend(collect(copy))
            except Exception as exc:
                print(f"Arsip {edition} gagal: {type(exc).__name__}: {exc}", file=sys.stderr)
                errors.append(exc)
        if errors:
            raise RuntimeError(f"{len(errors)} edisi arsip gagal; periksa riwayat run") from errors[0]
        return output
    with Store(database_url()) as store, GdeltClient() as client:
        if args.command == "archive":
            suffix = ".translation.gkg.csv.zip" if args.edition == "translation" else ".gkg.csv.zip"
            if args.at:
                latest = f"https://data.gdeltproject.org/gdeltv2/{args.at}{suffix}"
                checksum = None
            else:
                try:
                    latest, checksum = client.latest(translated=args.edition == "translation")
                except Exception as exc:
                    def unavailable(exc=exc):
                        raise exc

                    store.ingest("gkg", f"latest-manifest-{args.edition}", {}, unavailable,
                                 parse_gkg, checkpoint=False)
                    raise
            name = latest.rsplit("/", 1)[1].split(".")[0]
            stamp = datetime.strptime(name, "%Y%m%d%H%M%S").replace(tzinfo=UTC)
            if args.at:
                batches = [stamp - timedelta(minutes=15 * index) for index in range(args.batches)]
            else:
                batches, remaining = store.archive_batches(args.edition, stamp, args.batches)
                if remaining:
                    print(f"Masih ada {remaining} batch {args.edition} menunggu putaran berikutnya.",
                          file=sys.stderr, flush=True)
            failures = []
            for batch in batches:
                value = batch.strftime("%Y%m%d%H%M%S")
                url = f"https://data.gdeltproject.org/gdeltv2/{value}{suffix}"

                def fetch(url=url):
                    content = client.archive(url, checksum if url == latest else None)
                    for line in content.splitlines():
                        try:
                            # Satu record GKG per baris; tidak memperbaiki encoding secara lossy.
                            yield line.decode("utf-8-sig").split("\t")
                        except UnicodeDecodeError:
                            yield []  # Parser mengarantina baris, bukan mengganti karakter sumber.

                try:
                    result = store.ingest("gkg", url, {"selection": "editorial_domains_or_location_ID",
                                                       "edition": args.edition},
                                          fetch, parse_gkg, checkpoint=not args.reprocess)
                    output.append(result)
                    print(json.dumps(result), flush=True)
                except Exception as exc:
                    failures.append(exc)
                    print(f"Batch {value} gagal: {type(exc).__name__}: {exc}", file=sys.stderr)
            if failures:
                raise RuntimeError(
                    f"{len(failures)} batch {args.edition} gagal; tidak menghalangi batch lain"
                ) from failures[0]
        else:
            query = f"({args.query}) sourcecountry:indonesia sourcelang:indonesian"
            params = {"query": query, "mode": "artlist", "format": "json", "maxrecords": 250,
                      "timespan": f"{args.hours}h", "sort": "datedesc"}

            def fetch_doc():
                data = json.loads(client.get("https://api.gdeltproject.org/api/v2/doc/doc", params=params))
                if "articles" not in data:
                    raise ValueError("Respons DOC bukan daftar artikel")
                return data["articles"]

            result = store.ingest("doc", query, params, fetch_doc, parse_doc, checkpoint=False)
            output.append(result)
            print(json.dumps(result), flush=True)
    return output


def main():
    parser = argparse.ArgumentParser(description="Pengambilan metadata berita nyata GDELT")
    commands = parser.add_subparsers(dest="command", required=True)
    archive = commands.add_parser("archive", help="Arsip GKG 15 menit, checkpoint transaksi")
    archive.add_argument("--batches", type=int, default=4,
                         help="Maksimum batch per edisi per putaran; gap sejak run pertama diisi lebih dulu")
    archive.add_argument("--edition", choices=["original", "translation", "both"], default="both",
                         help="Original dan translingual; Indonesia memerlukan arsip translation")
    archive.add_argument("--at", help="Batch UTC YYYYMMDDHHMMSS untuk replay yang dapat diulang")
    archive.add_argument("--reprocess", action="store_true",
                         help="Ulang batch --at untuk memeriksa idempotency")
    archive.add_argument("--watch", type=int, default=0, help="Ulang setiap N menit, minimal 15")
    doc = commands.add_parser("doc", help="Pencarian judul melalui DOC API")
    doc.add_argument("--query", required=True)
    doc.add_argument("--hours", type=int, default=24)
    args = parser.parse_args()
    if args.command == "archive" and args.at:
        try:
            stamp = datetime.strptime(args.at, "%Y%m%d%H%M%S")
            if stamp.minute % 15 or stamp.second:
                parser.error("Batch harus batas 15 menit UTC")
        except ValueError:
            parser.error("at harus YYYYMMDDHHMMSS UTC")
    if args.command == "archive" and (
        not 1 <= args.batches <= 96 or args.watch != 0 and args.watch < 15
    ):
        parser.error("batches harus 1..96 dan watch minimal 15 menit")
    if args.command == "archive" and args.reprocess and not args.at:
        parser.error("reprocess hanya dapat dipakai dengan --at")
    if args.command == "doc" and (not 1 <= args.hours <= 168 or not 1 <= len(args.query) <= 200):
        parser.error("hours harus 1..168 dan query maksimal 200 karakter")
    csv.field_size_limit(10_000_000)
    while True:
        try:
            collect(args)
        except Exception as exc:
            print(f"{type(exc).__name__}: {exc}", file=sys.stderr)
            if not getattr(args, "watch", 0):
                sys.exit(1)
        if not getattr(args, "watch", 0):
            break
        time.sleep(args.watch * 60)


if __name__ == "__main__":
    main()

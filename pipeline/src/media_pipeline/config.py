import os
from pathlib import Path


def load_environment() -> None:
    path = Path(__file__).resolve().parents[3] / ".env"
    if path.exists():
        for line in path.read_text(encoding="utf-8-sig").splitlines():
            if line.strip() and not line.startswith("#"):
                key, value = line.split("=", 1)
                os.environ.setdefault(key.strip(), value.strip())


def database_url() -> str:
    load_environment()
    value = os.environ.get("DATABASE_URL")
    if not value:
        raise ValueError("DATABASE_URL belum diatur. Jalankan scripts/start-database.ps1.")
    return value


# Daftar editorial eksplisit, bukan menebak negara dari akhiran domain.
INDONESIAN_DOMAINS = frozenset({
    "antaranews.com", "kompas.com", "kompas.id", "detik.com", "tempo.co", "tempo.com",
    "cnnindonesia.com", "cnbcindonesia.com", "bisnis.com", "kontan.co.id", "republika.co.id",
    "liputan6.com", "kumparan.com", "tirto.id", "thejakartapost.com", "jakartaglobe.id",
    "beritasatu.com", "okezone.com", "sindonews.com", "medcom.id", "suara.com", "idntimes.com",
    "tribunnews.com", "rri.co.id", "tvonenews.com", "viva.co.id", "metrotvnews.com",
})

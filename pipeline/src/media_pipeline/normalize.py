import hashlib
import html
import json
import math
import re
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

from .config import INDONESIAN_DOMAINS


@dataclass(frozen=True)
class Article:
    url: str
    title: str | None
    domain: str
    language: str | None
    scope: str
    seen_at: datetime
    tone: float | None
    themes: list[str]
    locations: list[str]

    @property
    def fingerprint(self) -> str:
        values = (self.url, self.title, self.language, self.scope, self.tone, self.themes, self.locations)
        return hashlib.sha256(json.dumps(values, ensure_ascii=False).encode()).hexdigest()


def canonical_url(value: str) -> tuple[str, str]:
    parts = urlsplit(html.unescape(value.strip()))
    if parts.scheme not in {"http", "https"} or not parts.hostname or parts.username or parts.password:
        raise ValueError("URL sumber bukan HTTP/HTTPS yang valid")
    if len(value) > 4096 or parts.port not in {None, 80, 443}:
        raise ValueError("URL sumber terlalu panjang atau port tidak didukung")
    domain = parts.hostname.lower().removeprefix("www.")
    query = [(key, val) for key, val in parse_qsl(parts.query, keep_blank_values=True)
             if not key.lower().startswith("utm_") and key.lower() not in {"fbclid", "gclid"}]
    return urlunsplit((parts.scheme, domain, parts.path or "/", urlencode(query), "")), domain


def source_scope(domain: str, mentions_indonesia: bool) -> str | None:
    if any(domain == item or domain.endswith("." + item) for item in INDONESIAN_DOMAINS):
        return "indonesian_outlet"
    return "mentions_indonesia" if mentions_indonesia else None


def timestamp(value: str) -> datetime:
    result = datetime.strptime(value, "%Y%m%d%H%M%S").replace(tzinfo=UTC)
    # GKG memakai batas waktu batch 15 menit, yang dapat mendahului jam lokal
    # beberapa menit. Toleransi ini tidak mengubah timestamp sumber.
    if result.year < 2015 or result > datetime.now(UTC) + timedelta(minutes=15):
        raise ValueError("Waktu observasi tidak valid")
    return result


def parse_gkg(row: list[str]) -> Article | None:
    if len(row) < 27:
        raise ValueError("Kolom GKG kurang dari 27")
    if row[2] != "1":
        return None
    url, domain = canonical_url(row[4])
    locations = []
    indonesia = False
    for value in row[9].split(";"):
        fields = value.split("#")
        if len(fields) >= 3:
            locations.append(fields[1])
            indonesia = indonesia or fields[2] == "ID"
    scope = source_scope(domain, indonesia)
    if scope is None:
        return None
    themes = sorted(set(filter(None, row[7].split(";"))))[:150]
    tone = None
    if row[15]:
        tone = float(row[15].split(",")[0])
        if not math.isfinite(tone) or not -100 <= tone <= 100:
            raise ValueError("Skor nada GDELT tidak valid")
    title_match = re.search(r"<PAGE_TITLE>(.*?)</PAGE_TITLE>", row[26], re.DOTALL)
    title = html.unescape(title_match.group(1)).strip() if title_match else None
    language_match = re.search(r"srclc:([a-z]{3})", row[25])
    return Article(url, title[:2000] if title else None, domain,
                   language_match.group(1) if language_match else None,
                   scope, timestamp(row[1]), tone, themes, sorted(set(locations))[:100])


def parse_doc(item: dict) -> Article | None:
    url, domain = canonical_url(item["url"])
    scope = source_scope(domain, False)
    # API sudah dibatasi Indonesia; sumber di luar daftar editorial tetap diperbolehkan
    # hanya bila field sourcecountry dari respons menguatkan asalnya.
    if scope is None and item.get("sourcecountry", "").lower() == "indonesia":
        scope = "indonesian_outlet"
    if scope is None:
        return None
    title = html.unescape(item.get("title", "")).strip() or None
    seen = item["seendate"].replace("T", "").replace("Z", "")
    return Article(url, title, domain, item.get("language"), scope, timestamp(seen), None, [], [])

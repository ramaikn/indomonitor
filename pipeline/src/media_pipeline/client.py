import hashlib
import io
import time
import zipfile
from datetime import UTC, datetime
from email.utils import parsedate_to_datetime

import httpx


class GdeltClient:
    def __init__(self) -> None:
        self.client = httpx.Client(timeout=httpx.Timeout(90, connect=20), follow_redirects=False,
                                   headers={"User-Agent": "Indomonitor/0.1 (+https://github.com/ramaikn/indomonitor)"})
        self.last_request = 0.0

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.client.close()

    @staticmethod
    def retry_delay(value: str | None, minimum: int) -> float:
        if value:
            try:
                return max(minimum, float(value))
            except ValueError:
                try:
                    date = parsedate_to_datetime(value)
                    return max(minimum, (date - datetime.now(UTC)).total_seconds())
                except (TypeError, ValueError, OverflowError):
                    pass
        return minimum

    def get(self, url: str, *, params: dict | None = None, limit: int = 60_000_000) -> bytes:
        for attempt in range(3):
            time.sleep(max(0, 6 - (time.monotonic() - self.last_request)))
            self.last_request = time.monotonic()
            try:
                with self.client.stream("GET", url, params=params) as response:
                    if response.status_code in {429, 500, 502, 503, 504}:
                        if attempt == 2:
                            response.raise_for_status()
                        delay = self.retry_delay(response.headers.get("Retry-After"), 12 * (attempt + 1))
                        # Jeda yang sangat panjang menjadi kegagalan terpantau, bukan diabaikan.
                        if delay > 300:
                            response.raise_for_status()
                        time.sleep(delay)
                        continue
                    response.raise_for_status()
                    chunks, size = [], 0
                    for chunk in response.iter_bytes():
                        size += len(chunk)
                        if size > limit:
                            raise ValueError("Respons sumber melebihi batas ukuran")
                        chunks.append(chunk)
                    return b"".join(chunks)
            except (httpx.TimeoutException, httpx.NetworkError):
                if attempt == 2:
                    raise
                time.sleep(6 * (attempt + 1))
        raise RuntimeError("Pengambilan sumber gagal")

    def latest(self, translated: bool = False) -> tuple[str, str]:
        manifest = "lastupdate-translation.txt" if translated else "lastupdate.txt"
        text = self.get(f"https://data.gdeltproject.org/gdeltv2/{manifest}", limit=10000).decode()
        for line in text.splitlines():
            size, checksum, url = line.split()
            if url.endswith(".gkg.csv.zip"):
                if not url.startswith("http://data.gdeltproject.org/gdeltv2/"):
                    raise ValueError("Alamat manifest GDELT tidak dikenali")
                return url.replace("http://", "https://", 1), checksum
        raise ValueError("Manifest GKG tidak ditemukan")

    def archive(self, url: str, checksum: str | None = None) -> bytes:
        content = self.get(url)
        if checksum and hashlib.md5(content, usedforsecurity=False).hexdigest() != checksum:
            raise ValueError("Checksum arsip tidak cocok dengan manifest GDELT")
        with zipfile.ZipFile(io.BytesIO(content)) as archive:
            entries = archive.infolist()
            if len(entries) != 1 or entries[0].file_size > 250_000_000:
                raise ValueError("Isi arsip tidak sesuai atau terlalu besar")
            return archive.read(entries[0])

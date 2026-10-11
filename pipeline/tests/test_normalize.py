import pytest

from media_pipeline.normalize import canonical_url, parse_gkg, source_scope


def test_urls_preserve_identity_and_strip_tracking():
    url, domain = canonical_url("https://www.kompas.com/read/1?id=2&utm_source=test#section")
    assert url == "https://kompas.com/read/1?id=2"
    assert domain == "kompas.com"
    with pytest.raises(ValueError):
        canonical_url("javascript:alert(1)")
    assert source_scope("kompas.com.attacker.test", False) is None
    assert source_scope("news.detik.com", False) == "indonesian_outlet"
    assert source_scope("example.test", True) == "mentions_indonesia"


def test_missing_titles_are_not_fabricated():
    # Fixture hanya menguji parser, tidak pernah diimpor ke database aplikasi.
    row = [""] * 27
    row[1], row[2], row[4] = "20260101000000", "1", "https://kompas.com/read/1"
    row[7], row[15] = "ECON_TRADE;ECON_TRADE;", "-2.5,0,0"
    item = parse_gkg(row)
    assert item.title is None
    assert item.themes == ["ECON_TRADE"]
    assert item.tone == -2.5
    row[15] = "nan"
    with pytest.raises(ValueError):
        parse_gkg(row)
    with pytest.raises(ValueError):
        parse_gkg(["invalid"])

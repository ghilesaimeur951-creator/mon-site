from __future__ import annotations

import calendar
import hashlib
import json
import re
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import feedparser
import requests
from bs4 import BeautifulSoup

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "data" / "feed.json"
USER_AGENT = "CyberWatch-Mobile/0.1 (+https://github.com/ghilesaimeur951-creator/mon-site)"
TIMEOUT = 15
MAX_ITEMS = 250

RSS_SOURCES = [
    ("CERT-FR", "https://cert.ssi.gouv.fr/feed/"),
    ("CERT-FR Alertes", "https://cert.ssi.gouv.fr/alerte/feed/"),
    ("CERT-FR Menaces", "https://cert.ssi.gouv.fr/cti/feed/"),
    ("CERT-FR Avis", "https://cert.ssi.gouv.fr/avis/feed/"),
    ("CERT-FR Actualité", "https://cert.ssi.gouv.fr/actualite/feed/"),
    ("BleepingComputer", "https://www.bleepingcomputer.com/feed/"),
]

CISA_KEV = "https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json"
BLEEPING_HOME = "https://www.bleepingcomputer.com/"

session = requests.Session()
session.headers.update({"User-Agent": USER_AGENT, "Accept": "*/*"})


def now_iso() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def stable_id(url: str) -> str:
    return hashlib.sha256(url.encode("utf-8")).hexdigest()[:20]


def clean_html(value: str) -> str:
    if not value:
        return ""
    text = BeautifulSoup(value, "html.parser").get_text(" ", strip=True)
    return re.sub(r"\s+", " ", text)[:600]


def normalize_date(entry: Any) -> str:
    for key in ("published_parsed", "updated_parsed", "created_parsed"):
        parsed = getattr(entry, key, None)
        if parsed:
            ts = calendar.timegm(parsed)
            return datetime.fromtimestamp(ts, tz=timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
    return now_iso()


def classify(title: str, summary: str, source: str) -> tuple[str, str]:
    text = (title + " " + summary + " " + source).lower()

    if "cert-fr alert" in text or "alerte" in text:
        return "Alerte", "Élevée"
    if any(word in text for word in ["cve-", "vulnérab", "zero-day", "0-day", "exploit", "kev"]):
        severity = "Critique" if any(word in text for word in ["critical", "critique", "exploited", "exploitée", "rce"]) else "Élevée"
        return "Vulnérabilité", severity
    if any(word in text for word in ["ransomware", "malware", "phishing", "botnet", "apt", "threat", "menace"]):
        return "Menace", "Élevée"
    if any(word in text for word in ["breach", "incident", "compromise", "compromission", "hackers", "attaque"]):
        return "Incident", "Moyenne"
    return "Actualité", "Info"


def item(title: str, url: str, source: str, published_at: str, summary: str = "") -> dict[str, str]:
    category, severity = classify(title, summary, source)
    return {
        "id": stable_id(url),
        "title": clean_html(title)[:240],
        "summary": clean_html(summary),
        "url": url,
        "source": source,
        "published_at": published_at,
        "category": category,
        "severity": severity,
    }


def collect_rss() -> list[dict[str, str]]:
    results: list[dict[str, str]] = []
    for source, url in RSS_SOURCES:
        try:
            response = session.get(url, timeout=TIMEOUT)
            response.raise_for_status()
            feed = feedparser.parse(response.content)
            for entry in feed.entries[:40]:
                link = getattr(entry, "link", "")
                title = getattr(entry, "title", "")
                if not link or not title:
                    continue
                summary = getattr(entry, "summary", "") or getattr(entry, "description", "")
                results.append(item(title, link, source, normalize_date(entry), summary))
        except Exception as exc:
            print(f"[rss] {source}: {exc}")
    return results


def collect_cisa_kev() -> list[dict[str, str]]:
    results: list[dict[str, str]] = []
    try:
        response = session.get(CISA_KEV, timeout=TIMEOUT)
        response.raise_for_status()
        payload = response.json()
        for vuln in payload.get("vulnerabilities", [])[:120]:
            cve = vuln.get("cveID", "")
            if not cve:
                continue
            title = f"{cve} — {vuln.get('vendorProject', '')} {vuln.get('product', '')}".strip()
            summary = vuln.get("shortDescription", "")
            url = "https://www.cisa.gov/known-exploited-vulnerabilities-catalog"
            unique_url = url + "#" + cve
            date_added = vuln.get("dateAdded", "")
            published = (date_added + "T00:00:00Z") if date_added else now_iso()
            data = item(title, unique_url, "CISA KEV", published, summary)
            data["category"] = "Vulnérabilité"
            data["severity"] = "Critique"
            results.append(data)
    except Exception as exc:
        print(f"[cisa-kev] {exc}")
    return results


def scrape_bleepingcomputer() -> list[dict[str, str]]:
    """Fallback HTML scraper. RSS remains the preferred path."""
    results: list[dict[str, str]] = []
    try:
        response = session.get(BLEEPING_HOME, timeout=TIMEOUT)
        response.raise_for_status()
        soup = BeautifulSoup(response.text, "html.parser")
        seen: set[str] = set()

        for anchor in soup.select("a[href]"):
            href = anchor.get("href", "")
            title = anchor.get_text(" ", strip=True)
            if "/news/security/" not in href or len(title) < 20:
                continue
            if href in seen:
                continue
            seen.add(href)
            results.append(item(title, href, "BleepingComputer (scraping)", now_iso(), ""))
            if len(results) >= 25:
                break
    except Exception as exc:
        print(f"[scrape] BleepingComputer: {exc}")
    return results


def dedupe_and_sort(items: list[dict[str, str]]) -> list[dict[str, str]]:
    by_url: dict[str, dict[str, str]] = {}
    for current in items:
        url = current["url"]
        existing = by_url.get(url)
        if existing is None or len(current.get("summary", "")) > len(existing.get("summary", "")):
            by_url[url] = current

    return sorted(
        by_url.values(),
        key=lambda x: x.get("published_at", ""),
        reverse=True,
    )[:MAX_ITEMS]


def main() -> None:
    collected = []
    collected.extend(collect_rss())
    collected.extend(collect_cisa_kev())
    collected.extend(scrape_bleepingcomputer())

    final_items = dedupe_and_sort(collected)
    payload = {
        "generated_at": now_iso(),
        "count": len(final_items),
        "items": final_items,
    }

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Wrote {len(final_items)} items to {OUTPUT}")


if __name__ == "__main__":
    main()

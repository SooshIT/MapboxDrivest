"""Resolve coordinates from metadata batches using Nominatim."""

from __future__ import annotations

import argparse
import csv
import json
import math
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Dict, List

ROUTEGEN_DIR = Path(__file__).resolve().parents[1]
CACHE_PATH = ROUTEGEN_DIR / "work" / "geocode_cache.json"
USER_AGENT = "DrivestRouteGen/1.0"
NOMINATIM_URL = "https://nominatim.openstreetmap.org/search"


def _is_valid_number(value: object) -> bool:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return False
    return math.isfinite(number)


def load_rows(path: Path) -> List[Dict]:
    if not path.exists():
        raise FileNotFoundError(f"Input CSV not found: {path}")
    with path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        return [row for row in reader]


def load_cache() -> Dict:
    if not CACHE_PATH.exists():
        return {}
    try:
        return json.loads(CACHE_PATH.read_text(encoding="utf-8"))
    except Exception:
        return {}


def save_cache(cache: Dict) -> None:
    CACHE_PATH.parent.mkdir(parents=True, exist_ok=True)
    CACHE_PATH.write_text(json.dumps(cache, indent=2, ensure_ascii=True), encoding="utf-8")


def _fetch_first_result(query: str) -> Dict | None:
    params = {"q": query, "format": "json", "limit": "1"}
    url = f"{NOMINATIM_URL}?{urllib.parse.urlencode(params)}"
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=30) as response:
        payload = response.read().decode("utf-8")
    results = json.loads(payload)
    if not results:
        return None
    return results[0]

def _is_transient_http_error(error: urllib.error.HTTPError) -> bool:
    return error.code in (429, 500, 502, 503, 504)


def resolve_query(query: str, cache: Dict, dry_run: bool, centre_id: str, query_label: str) -> Dict | None:
    if query in cache:
        cached = cache[query]
        return cached if cached else None
    if dry_run:
        return None
    backoff_schedule = [2, 5, 10]
    for attempt in range(len(backoff_schedule) + 1):
        try:
            result = _fetch_first_result(query)
            if result and "lat" in result and "lon" in result:
                cache[query] = {"lat": result["lat"], "lng": result["lon"]}
            else:
                cache[query] = None
            return cache[query]
        except urllib.error.HTTPError as exc:
            if not _is_transient_http_error(exc):
                print(
                    f"Warning: centre_id={centre_id} query={query_label} non-retryable HTTP {exc.code}"
                )
                return None
            if attempt < len(backoff_schedule):
                wait_s = backoff_schedule[attempt]
                print(
                    f"Warning: centre_id={centre_id} query={query_label} retry {attempt + 1} after {wait_s}s (HTTP {exc.code})"
                )
                time.sleep(wait_s)
                continue
            print(
                f"Warning: centre_id={centre_id} query={query_label} failed after retries (HTTP {exc.code})"
            )
            return None
        except (urllib.error.URLError, TimeoutError, ConnectionResetError, OSError) as exc:
            if attempt < len(backoff_schedule):
                wait_s = backoff_schedule[attempt]
                print(
                    f"Warning: centre_id={centre_id} query={query_label} retry {attempt + 1} after {wait_s}s ({exc})"
                )
                time.sleep(wait_s)
                continue
            print(
                f"Warning: centre_id={centre_id} query={query_label} failed after retries ({exc})"
            )
            return None


def main() -> int:
    parser = argparse.ArgumentParser(description="Resolve coordinates using metadata queries.")
    parser.add_argument("--input", required=True, help="Input CSV to resolve.")
    parser.add_argument("--output", required=True, help="Output CSV with resolved coordinates.")
    parser.add_argument("--limit", type=int, default=0, help="Limit number of rows to process.")
    parser.add_argument("--dry-run", action="store_true", help="Validate without requesting or writing.")
    args = parser.parse_args()

    input_path = Path(args.input)
    output_path = Path(args.output)

    rows = load_rows(input_path)
    cache = load_cache()
    processed = 0

    for row in rows:
        if args.limit and processed >= args.limit:
            break

        lat_raw = row.get("lat")
        lng_raw = row.get("lng")
        if _is_valid_number(lat_raw) and _is_valid_number(lng_raw):
            row["resolution_status"] = row.get("resolution_status") or "skipped_existing"
            continue

        if row.get("resolution_status") in (
            "resolved_primary",
            "resolved_secondary",
            "resolved_tertiary",
        ):
            continue

        query_primary = (row.get("query_primary") or "").strip()
        query_secondary = (row.get("query_secondary") or "").strip()
        query_tertiary = (row.get("query_tertiary") or "").strip()

        resolved = False
        centre_id = row.get("centre_id") or ""
        for query, status, label in (
            (query_primary, "resolved_primary", "primary"),
            (query_secondary, "resolved_secondary", "secondary"),
            (query_tertiary, "resolved_tertiary", "tertiary"),
        ):
            if not query:
                continue
            result = resolve_query(query, cache, args.dry_run, centre_id, label)
            if not args.dry_run:
                time.sleep(1)
            if result and _is_valid_number(result.get("lat")) and _is_valid_number(result.get("lng")):
                row["lat"] = str(result["lat"])
                row["lng"] = str(result["lng"])
                row["resolution_status"] = status
                resolved = True
                break

        if not resolved and not args.dry_run:
            row["resolution_status"] = "unresolved"
        elif not resolved and args.dry_run:
            row["resolution_status"] = row.get("resolution_status") or "pending"

        processed += 1

    if not args.dry_run:
        save_cache(cache)

    with output_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=rows[0].keys() if rows else [])
        if rows:
            writer.writeheader()
            writer.writerows(rows)

    print(f"Rows processed: {processed}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

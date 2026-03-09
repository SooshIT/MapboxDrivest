"""Enrich missing centre coordinates using OSM Nominatim."""

from __future__ import annotations

import csv
import json
import time
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Dict, List

ROUTEGEN_DIR = Path(__file__).resolve().parents[1]
INPUT_PATH = ROUTEGEN_DIR / "config" / "centre_coordinates_missing.csv"
OUTPUT_PATH = ROUTEGEN_DIR / "config" / "centre_coordinates_enriched.csv"

NOMINATIM_URL = "https://nominatim.openstreetmap.org/search"
USER_AGENT = "DrivestRouteGen/1.0"


def load_missing_rows(path: Path) -> List[Dict]:
    if not path.exists():
        raise FileNotFoundError(f"Missing coordinates CSV not found: {path}")
    with path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        rows = []
        for row in reader:
            rows.append(
                {
                    "centre_id": (row.get("centre_id") or "").strip(),
                    "centre_name": (row.get("centre_name") or "").strip(),
                    "lat": (row.get("lat") or "").strip(),
                    "lng": (row.get("lng") or "").strip(),
                }
            )
        return rows


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


def resolve_coordinates(centre_name: str) -> Dict | None:
    query = f"DVSA driving test centre {centre_name} UK"
    result = _fetch_first_result(query)
    if result:
        return result
    fallback = f"{centre_name} driving test centre UK"
    return _fetch_first_result(fallback)


def main() -> int:
    rows = load_missing_rows(INPUT_PATH)
    total = len(rows)
    resolved = 0
    unresolved = 0

    output_rows = []
    for idx, row in enumerate(rows):
        centre_id = row["centre_id"]
        centre_name = row["centre_name"]
        lat = ""
        lng = ""

        result = None
        if centre_name:
            try:
                result = resolve_coordinates(centre_name)
            except Exception as exc:
                print(f"Warning: lookup failed for {centre_id} ({centre_name}): {exc}")

        if result and "lat" in result and "lon" in result:
            lat = str(result["lat"])
            lng = str(result["lon"])
            resolved += 1
        else:
            unresolved += 1

        output_rows.append(
            {
                "centre_id": centre_id,
                "centre_name": centre_name,
                "lat": lat,
                "lng": lng,
            }
        )

        if idx < total - 1:
            time.sleep(1)

    with OUTPUT_PATH.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=["centre_id", "centre_name", "lat", "lng"])
        writer.writeheader()
        writer.writerows(output_rows)

    print(f"Centres processed: {total}")
    print(f"Coordinates resolved: {resolved}")
    print(f"Coordinates unresolved: {unresolved}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

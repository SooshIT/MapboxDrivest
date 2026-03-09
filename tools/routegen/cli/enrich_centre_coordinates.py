#!/usr/bin/env python
"""Enrich missing centre coordinates using Nominatim."""

from __future__ import annotations

import argparse
import csv
import json
import re
import time
import urllib.parse
import urllib.request
from pathlib import Path

REQUEST_TIMEOUT = 10
SLEEP_SECONDS = 1


def _normalize_name(name: str) -> str:
    name = name.replace("_", " ")
    name = re.sub(r"\s+", " ", name).strip()
    return name


def _geocode(query: str, countrycodes: str | None = None) -> tuple[float | None, float | None]:
    params = {"q": query, "format": "json", "limit": 1}
    if countrycodes:
        params["countrycodes"] = countrycodes
    url = f"https://nominatim.openstreetmap.org/search?{urllib.parse.urlencode(params)}"
    req = urllib.request.Request(url, headers={"User-Agent": "DrivestRouteGen/1.0"})
    with urllib.request.urlopen(req, timeout=REQUEST_TIMEOUT) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    if not data:
        return None, None
    lat = data[0].get("lat")
    lon = data[0].get("lon")
    try:
        return float(lat), float(lon)
    except (TypeError, ValueError):
        return None, None


def _load_metadata() -> dict[str, dict]:
    meta_path = Path(__file__).resolve().parents[1] / "config" / "centre_metadata_extracted.csv"
    if not meta_path.exists():
        return {}
    meta = {}
    with meta_path.open("r", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        for row in reader:
            centre_id = row.get("centre_id")
            if not centre_id:
                continue
            meta[centre_id] = row
    return meta


def main() -> int:
    parser = argparse.ArgumentParser(description="Enrich missing centre coordinates using Nominatim.")
    parser.add_argument("--limit", type=int, default=None, help="Max number of missing centres to process.")
    parser.add_argument("--resume", action="store_true", help="Skip centres that already have coordinates.")
    args = parser.parse_args()

    registry_path = Path(__file__).resolve().parents[1] / "config" / "dvsa_centres.json"
    data = json.loads(registry_path.read_text(encoding="utf-8"))
    centres = data.get("centres", [])
    metadata = _load_metadata()

    total = len(centres)
    attempted = 0
    updated = 0

    for idx, centre in enumerate(centres, start=1):
        lat = centre.get("lat")
        lng = centre.get("lng")
        if args.resume and lat is not None and lng is not None:
            continue
        if lat is not None and lng is not None:
            continue
        if args.limit is not None and attempted >= args.limit:
            break

        centre_id = str(centre.get("centre_id"))
        raw_name = centre.get("centre_name") or centre_id
        name = _normalize_name(str(raw_name))
        if not name:
            continue

        attempted += 1

        meta = metadata.get(centre_id, {})
        postcode = (meta.get("cleaned_postcode_candidate") or meta.get("postcode_candidate") or "").strip()
        address = (meta.get("cleaned_address_candidate") or meta.get("address_candidate") or "").strip()
        town = (meta.get("town") or meta.get("city") or "").strip()
        county = (meta.get("county") or "").strip()

        queries: list[tuple[str, str, str | None]] = []
        if postcode:
            queries.append(("query_postcode", f"{postcode}, UK", None))
        if address and postcode:
            queries.append(("query_address", f"{address}, {postcode}, UK", None))
        if town and postcode:
            queries.append(("query_centre_town_postcode", f"{name}, {town}, {postcode}, UK", None))
        if town:
            queries.append(("query_centre_town", f"{name} DVSA driving test centre, {town}, UK", None))
        if county:
            queries.append(("query_centre_county", f"{name}, {county}, UK", None))

        # Existing fallbacks
        queries.extend(
            [
                ("query_1", f"{name} DVSA driving test centre UK", None),
                ("query_2", f"{name} driving test centre UK", None),
                ("query_3", f"{name} test centre UK", None),
                ("query_4", f"{name} DVSA UK", None),
            ]
        )

        updated_this_centre = False
        for label, query, ccodes in queries:
            try:
                new_lat, new_lng = _geocode(query, countrycodes=ccodes)
            except Exception:
                print(f"[{idx}] {centre_id} {label} error")
                time.sleep(SLEEP_SECONDS)
                continue

            if new_lat is not None and new_lng is not None:
                centre["lat"] = new_lat
                centre["lng"] = new_lng
                updated += 1
                updated_this_centre = True
                data["centres"] = centres
                registry_path.write_text(json.dumps(data, indent=2), encoding="utf-8")
                print(f"[{idx}] {centre_id} {label} updated")
                break
            else:
                print(f"[{idx}] {centre_id} {label} no_match")

            time.sleep(SLEEP_SECONDS)

        if not updated_this_centre:
            # Final fallback with countrycodes=gb
            try:
                new_lat, new_lng = _geocode(f"{name} DVSA", countrycodes="gb")
            except Exception:
                new_lat, new_lng = None, None

            if new_lat is not None and new_lng is not None:
                centre["lat"] = new_lat
                centre["lng"] = new_lng
                updated += 1
                data["centres"] = centres
                registry_path.write_text(json.dumps(data, indent=2), encoding="utf-8")
                print(f"[{idx}] {centre_id} fallback updated")
            else:
                print(f"[{idx}] {centre_id} final_no_match")

        if attempted % 10 == 0:
            data["centres"] = centres
            registry_path.write_text(json.dumps(data, indent=2), encoding="utf-8")

        time.sleep(SLEEP_SECONDS)

    data["centres"] = centres
    registry_path.write_text(json.dumps(data, indent=2), encoding="utf-8")

    still_missing = sum(1 for c in centres if c.get("lat") is None or c.get("lng") is None)

    print(f"total_centres: {total}")
    print(f"attempted_this_run: {attempted}")
    print(f"updated_this_run: {updated}")
    print(f"still_missing: {still_missing}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

"""Import a full DVSA centre list into the registry."""

from __future__ import annotations

import argparse
import csv
import json
import sys
import unicodedata
from pathlib import Path
from typing import Dict, Iterable, List, Tuple

ROUTEGEN_DIR = Path(__file__).resolve().parents[1]
REGISTRY_PATH = ROUTEGEN_DIR / "config" / "dvsa_centres.json"


def slugify(value: str) -> str:
    normalized = unicodedata.normalize("NFKD", value)
    ascii_value = normalized.encode("ascii", "ignore").decode("ascii")
    ascii_value = ascii_value.lower().strip()
    cleaned = []
    prev_underscore = False
    for ch in ascii_value:
        if ch.isalnum():
            cleaned.append(ch)
            prev_underscore = False
        else:
            if not prev_underscore:
                cleaned.append("_")
                prev_underscore = True
    slug = "".join(cleaned).strip("_")
    while "__" in slug:
        slug = slug.replace("__", "_")
    return slug


def _pick(row: Dict, keys: Iterable[str]) -> str | None:
    for key in keys:
        if key in row and row[key] not in (None, ""):
            return str(row[key]).strip()
    return None


def load_entries(path: Path) -> List[Dict]:
    if path.suffix.lower() == ".json":
        payload = json.loads(path.read_text(encoding="utf-8"))
        if isinstance(payload, dict) and "centres" in payload:
            entries = payload["centres"]
        elif isinstance(payload, list):
            entries = payload
        else:
            raise ValueError("JSON input must be a list or contain a 'centres' array.")
        if not isinstance(entries, list):
            raise ValueError("JSON centres must be a list.")
        return entries

    if path.suffix.lower() == ".csv":
        with path.open("r", encoding="utf-8-sig", newline="") as handle:
            reader = csv.DictReader(handle)
            return [row for row in reader]

    raise ValueError("Unsupported input format. Use .json or .csv.")


def distance_m(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    # Approximate distance in meters (haversine).
    from math import asin, cos, radians, sin, sqrt

    r = 6371000.0
    dlat = radians(lat2 - lat1)
    dlng = radians(lng2 - lng1)
    a = sin(dlat / 2) ** 2 + cos(radians(lat1)) * cos(radians(lat2)) * sin(dlng / 2) ** 2
    return 2 * r * asin(sqrt(a))


def normalize_entries(entries: List[Dict]) -> Tuple[List[Dict], List[str], List[str]]:
    errors: List[str] = []
    warnings: List[str] = []
    normalized: List[Dict] = []
    for idx, row in enumerate(entries):
        name = _pick(row, ("centre_name", "name", "centre", "centreName"))
        lat_raw = _pick(row, ("lat", "latitude", "centre_lat", "centreLat"))
        lng_raw = _pick(row, ("lng", "lon", "longitude", "centre_lng", "centreLng"))
        centre_id_raw = _pick(row, ("centre_id", "id", "slug", "centreId"))

        if not name:
            errors.append(f"row {idx}: missing centre_name")
            continue
        if lat_raw is None or lng_raw is None:
            errors.append(f"row {idx}: missing lat/lng for {name}")
            continue
        try:
            lat = float(lat_raw)
            lng = float(lng_raw)
        except ValueError:
            errors.append(f"row {idx}: invalid lat/lng for {name}")
            continue

        centre_id = slugify(centre_id_raw) if centre_id_raw else slugify(name)
        if not centre_id:
            errors.append(f"row {idx}: invalid centre_id for {name}")
            continue

        normalized.append(
            {
                "centre_id": centre_id,
                "centre_name": name.strip(),
                "lat": lat,
                "lng": lng,
            }
        )

    # Duplicate handling
    ids_seen: Dict[str, int] = {}
    name_seen: Dict[str, Dict] = {}
    deduped: List[Dict] = []
    for centre in normalized:
        centre_id = centre["centre_id"]
        centre_name = centre["centre_name"]
        name_key = centre_name.strip().lower()

        if centre_id in ids_seen:
            errors.append(f"duplicate centre_id: {centre_id}")
            continue

        if name_key in name_seen:
            existing = name_seen[name_key]
            dist = distance_m(existing["lat"], existing["lng"], centre["lat"], centre["lng"])
            if dist > 50:
                errors.append(
                    f"duplicate centre_name conflict: {centre_name} ({existing['lat']},{existing['lng']}) "
                    f"vs ({centre['lat']},{centre['lng']})"
                )
                continue
            warnings.append(f"duplicate centre_name ignored: {centre_name}")
            continue

        ids_seen[centre_id] = 1
        name_seen[name_key] = centre
        deduped.append(centre)

    return deduped, errors, warnings


def write_registry(centres: List[Dict], dry_run: bool) -> None:
    payload = {"centres": centres}
    if dry_run:
        print(f"Dry run: {len(centres)} centres would be imported.")
        return
    REGISTRY_PATH.write_text(json.dumps(payload, indent=2, ensure_ascii=True), encoding="utf-8")
    print(f"Wrote {len(centres)} centres to {REGISTRY_PATH}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Import DVSA centre list into the registry.")
    parser.add_argument("--input", required=True, help="Path to JSON or CSV centre list.")
    parser.add_argument("--dry-run", action="store_true", help="Validate input without writing registry.")
    args = parser.parse_args()

    input_path = Path(args.input)
    if not input_path.exists():
        raise SystemExit(f"Input file not found: {input_path}")

    try:
        entries = load_entries(input_path)
    except Exception as exc:
        raise SystemExit(str(exc)) from exc

    centres, errors, warnings = normalize_entries(entries)
    if warnings:
        for message in warnings:
            print(f"Warning: {message}")
    if errors:
        for message in errors:
            print(f"Error: {message}")
        raise SystemExit(f"Import failed with {len(errors)} error(s).")

    write_registry(centres, args.dry_run)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

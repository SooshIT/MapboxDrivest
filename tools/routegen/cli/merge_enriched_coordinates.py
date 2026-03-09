"""Merge enriched coordinates into the DVSA centre registry."""

from __future__ import annotations

import argparse
import csv
import json
import math
from pathlib import Path
from typing import Dict, List

ROUTEGEN_DIR = Path(__file__).resolve().parents[1]
REGISTRY_PATH = ROUTEGEN_DIR / "config" / "dvsa_centres.json"
ENRICHED_PATH = ROUTEGEN_DIR / "config" / "centre_coordinates_enriched.csv"


def _is_valid_number(value: object) -> bool:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return False
    return math.isfinite(number)


def load_registry() -> Dict:
    if not REGISTRY_PATH.exists():
        raise FileNotFoundError(f"Registry not found: {REGISTRY_PATH}")
    return json.loads(REGISTRY_PATH.read_text(encoding="utf-8"))


def load_enriched(path: Path) -> List[Dict]:
    if not path.exists():
        raise FileNotFoundError(f"Enriched CSV not found: {path}")
    with path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        return [row for row in reader]


def main() -> int:
    parser = argparse.ArgumentParser(description="Merge enriched coordinates into registry.")
    parser.add_argument("--overwrite", action="store_true", help="Overwrite existing coordinates.")
    args = parser.parse_args()

    registry = load_registry()
    centres = registry.get("centres", [])
    if not isinstance(centres, list):
        raise SystemExit("Registry centres must be a list.")

    enriched_rows = load_enriched(ENRICHED_PATH)
    enriched_lookup = {row.get("centre_id"): row for row in enriched_rows if row.get("centre_id")}

    updated = 0
    preserved = 0
    for centre in centres:
        centre_id = centre.get("centre_id")
        if not centre_id or centre_id not in enriched_lookup:
            continue
        row = enriched_lookup[centre_id]
        lat_raw = row.get("lat")
        lng_raw = row.get("lng")
        if not _is_valid_number(lat_raw) or not _is_valid_number(lng_raw):
            continue

        has_existing = _is_valid_number(centre.get("lat")) and _is_valid_number(centre.get("lng"))
        if has_existing and not args.overwrite:
            preserved += 1
            continue

        centre["lat"] = float(lat_raw)
        centre["lng"] = float(lng_raw)
        updated += 1

    still_missing = 0
    for centre in centres:
        has_coords = _is_valid_number(centre.get("lat")) and _is_valid_number(centre.get("lng"))
        if not has_coords:
            still_missing += 1

    print(f"Registry centres total: {len(centres)}")
    print(f"Coordinates merged: {updated}")
    print(f"Coordinates preserved: {preserved}")
    print(f"Still missing coordinates: {still_missing}")

    if updated > 0 or args.overwrite:
        REGISTRY_PATH.write_text(json.dumps(registry, indent=2, ensure_ascii=True), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

"""Export a CSV template for centre coordinates."""

from __future__ import annotations

import csv
import math
from pathlib import Path
from typing import Dict, List

import json

ROUTEGEN_DIR = Path(__file__).resolve().parents[1]
REGISTRY_PATH = ROUTEGEN_DIR / "config" / "dvsa_centres.json"
OUTPUT_PATH = ROUTEGEN_DIR / "config" / "centre_coordinates_template.csv"


def _is_valid_number(value: object) -> bool:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return False
    return math.isfinite(number)


def load_registry() -> List[Dict]:
    if not REGISTRY_PATH.exists():
        raise FileNotFoundError(f"Registry not found: {REGISTRY_PATH}")
    payload = json.loads(REGISTRY_PATH.read_text(encoding="utf-8"))
    centres = payload.get("centres", [])
    if not isinstance(centres, list):
        raise ValueError("Registry centres must be a list.")
    return centres


def main() -> int:
    centres = load_registry()

    present = 0
    missing = 0

    rows = []
    for centre in centres:
        centre_id = str(centre.get("centre_id", "")).strip()
        centre_name = str(centre.get("centre_name", "")).strip()
        lat = centre.get("lat")
        lng = centre.get("lng")
        has_coords = _is_valid_number(lat) and _is_valid_number(lng)
        if has_coords:
            present += 1
            lat_out = f"{float(lat)}"
            lng_out = f"{float(lng)}"
        else:
            missing += 1
            lat_out = ""
            lng_out = ""
        rows.append((centre_id, centre_name, lat_out, lng_out))

    with OUTPUT_PATH.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["centre_id", "centre_name", "lat", "lng"])
        writer.writerows(rows)

    print(f"Total centres: {len(centres)}")
    print(f"Coordinates already present: {present}")
    print(f"Coordinates missing: {missing}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

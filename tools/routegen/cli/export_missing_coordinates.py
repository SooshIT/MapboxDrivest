"""Export centres missing coordinates to a CSV template."""

from __future__ import annotations

import csv
import math
from pathlib import Path
from typing import Dict, List

import json

ROUTEGEN_DIR = Path(__file__).resolve().parents[1]
REGISTRY_PATH = ROUTEGEN_DIR / "config" / "dvsa_centres.json"
OUTPUT_PATH = ROUTEGEN_DIR / "config" / "centre_coordinates_missing.csv"


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
    rows = []
    present = 0
    for centre in centres:
        lat = centre.get("lat")
        lng = centre.get("lng")
        has_coords = _is_valid_number(lat) and _is_valid_number(lng)
        if has_coords:
            present += 1
            continue
        rows.append(
            (
                str(centre.get("centre_id", "")).strip(),
                str(centre.get("centre_name", "")).strip(),
                "" if not _is_valid_number(lat) else f"{float(lat)}",
                "" if not _is_valid_number(lng) else f"{float(lng)}",
            )
        )

    with OUTPUT_PATH.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["centre_id", "centre_name", "lat", "lng"])
        writer.writerows(rows)

    print(f"Total registry centres: {len(centres)}")
    print(f"Missing-coordinate centres exported: {len(rows)}")
    print(f"Coordinates already present: {present}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

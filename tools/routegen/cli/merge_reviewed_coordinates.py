"""Merge manually reviewed coordinates into the centre registry."""

from __future__ import annotations

import argparse
import csv
import json
import math
from pathlib import Path
from typing import Dict, List

ROUTEGEN_DIR = Path(__file__).resolve().parents[1]
REGISTRY_PATH = ROUTEGEN_DIR / "config" / "dvsa_centres.json"


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


def load_review(path: Path) -> List[Dict]:
    if not path.exists():
        raise FileNotFoundError(f"Review CSV not found: {path}")
    with path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        return [row for row in reader]


def main() -> int:
    parser = argparse.ArgumentParser(description="Merge approved review coordinates.")
    parser.add_argument("--input", required=True, help="Review CSV to merge.")
    parser.add_argument("--overwrite", action="store_true", help="Overwrite existing coordinates.")
    args = parser.parse_args()

    registry = load_registry()
    centres = registry.get("centres", [])
    if not isinstance(centres, list):
        raise SystemExit("Registry centres must be a list.")

    review_rows = load_review(Path(args.input))
    approved_rows = [row for row in review_rows if (row.get("review_status") or "") == "approved"]

    centre_lookup = {centre.get("centre_id"): centre for centre in centres if centre.get("centre_id")}
    merged = 0
    skipped = 0
    for row in approved_rows:
        centre_id = row.get("centre_id")
        if not centre_id or centre_id not in centre_lookup:
            skipped += 1
            continue
        lat = row.get("lat")
        lng = row.get("lng")
        if not _is_valid_number(lat) or not _is_valid_number(lng):
            skipped += 1
            continue

        centre = centre_lookup[centre_id]
        has_existing = _is_valid_number(centre.get("lat")) and _is_valid_number(centre.get("lng"))
        if has_existing and not args.overwrite:
            skipped += 1
            continue

        centre["lat"] = float(lat)
        centre["lng"] = float(lng)
        merged += 1

    still_missing = 0
    total_with_coords = 0
    for centre in centres:
        has_coords = _is_valid_number(centre.get("lat")) and _is_valid_number(centre.get("lng"))
        if has_coords:
            total_with_coords += 1
        else:
            still_missing += 1

    if merged > 0 or args.overwrite:
        REGISTRY_PATH.write_text(json.dumps(registry, indent=2, ensure_ascii=True), encoding="utf-8")

    print(f"Approved rows merged: {merged}")
    print(f"Rows skipped: {skipped}")
    print(f"Registry coordinates total: {total_with_coords}")
    print(f"Still missing coordinates: {still_missing}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

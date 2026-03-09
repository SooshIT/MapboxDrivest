#!/usr/bin/env python
"""Merge manually filled coordinates into dvsa_centres.json."""

from __future__ import annotations

import csv
import json
from pathlib import Path


def main() -> int:
    registry_path = Path(__file__).resolve().parents[1] / "config" / "dvsa_centres.json"
    input_path = Path(__file__).resolve().parents[1] / "output" / "missing_centre_coordinates.csv"

    data = json.loads(registry_path.read_text(encoding="utf-8"))
    centres = data.get("centres", [])
    centres_by_id = {c.get("centre_id"): c for c in centres if c.get("centre_id")}

    total_rows = 0
    merged = 0

    if not input_path.exists():
        print(f"Missing input CSV: {input_path}")
        return 1

    with input_path.open("r", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        for row in reader:
            total_rows += 1
            cid = row.get("centre_id")
            if not cid or cid not in centres_by_id:
                continue
            src_lat = row.get("source_lat")
            src_lng = row.get("source_lng")
            if not src_lat or not src_lng:
                continue
            try:
                lat_val = float(src_lat)
                lng_val = float(src_lng)
            except ValueError:
                continue
            centres_by_id[cid]["lat"] = lat_val
            centres_by_id[cid]["lng"] = lng_val
            merged += 1

    data["centres"] = centres
    registry_path.write_text(json.dumps(data, indent=2), encoding="utf-8")

    still_missing = sum(1 for c in centres if c.get("lat") is None or c.get("lng") is None)

    print(f"total_rows: {total_rows}")
    print(f"merged_rows: {merged}")
    print(f"still_missing: {still_missing}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

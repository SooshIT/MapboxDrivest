"""Build candidate lookup queries for missing centre coordinates."""

from __future__ import annotations

import csv
from pathlib import Path
from typing import Dict, List

ROUTEGEN_DIR = Path(__file__).resolve().parents[1]
INPUT_PATH = ROUTEGEN_DIR / "config" / "centre_coordinates_missing.csv"
OUTPUT_PATH = ROUTEGEN_DIR / "config" / "centre_coordinate_lookup_candidates.csv"


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


def main() -> int:
    rows = load_missing_rows(INPUT_PATH)
    output_rows = []
    for row in rows:
        centre_name = row["centre_name"]
        output_rows.append(
            {
                "centre_id": row["centre_id"],
                "centre_name": centre_name,
                "query_primary": f"{centre_name} driving test centre UK",
                "query_secondary": f"DVSA {centre_name} driving test centre",
                "query_tertiary": f"{centre_name} test centre",
                "lat": row["lat"],
                "lng": row["lng"],
                "status": "pending",
            }
        )

    with OUTPUT_PATH.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=[
                "centre_id",
                "centre_name",
                "query_primary",
                "query_secondary",
                "query_tertiary",
                "lat",
                "lng",
                "status",
            ],
        )
        writer.writeheader()
        writer.writerows(output_rows)

    print(f"Centres pending lookup: {len(output_rows)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

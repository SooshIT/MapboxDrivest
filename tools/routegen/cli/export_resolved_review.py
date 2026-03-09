"""Export resolved rows for manual review before merging into registry."""

from __future__ import annotations

import csv
from pathlib import Path
from typing import Dict, List

ROUTEGEN_DIR = Path(__file__).resolve().parents[1]
INPUT_PATH = ROUTEGEN_DIR / "config" / "centre_resolution_high_resolved.csv"
OUTPUT_PATH = ROUTEGEN_DIR / "config" / "centre_resolution_high_review.csv"

OUTPUT_FIELDS = [
    "centre_id",
    "centre_name",
    "lat",
    "lng",
    "resolution_status",
    "query_primary",
    "query_secondary",
    "query_tertiary",
    "lookup_confidence",
    "lookup_notes",
    "review_status",
]

RESOLVED_STATUSES = {"resolved_primary", "resolved_secondary", "resolved_tertiary"}


def load_rows(path: Path) -> List[Dict]:
    if not path.exists():
        raise FileNotFoundError(f"Input CSV not found: {path}")
    with path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        return [row for row in reader]


def main() -> int:
    rows = load_rows(INPUT_PATH)
    output_rows = []
    for row in rows:
        status = (row.get("resolution_status") or "").strip()
        if status not in RESOLVED_STATUSES:
            continue
        output_rows.append(
            {
                "centre_id": (row.get("centre_id") or "").strip(),
                "centre_name": (row.get("centre_name") or "").strip(),
                "lat": (row.get("lat") or "").strip(),
                "lng": (row.get("lng") or "").strip(),
                "resolution_status": status,
                "query_primary": (row.get("query_primary") or "").strip(),
                "query_secondary": (row.get("query_secondary") or "").strip(),
                "query_tertiary": (row.get("query_tertiary") or "").strip(),
                "lookup_confidence": (row.get("lookup_confidence") or "").strip(),
                "lookup_notes": (row.get("lookup_notes") or "").strip(),
                "review_status": "pending_review",
            }
        )

    with OUTPUT_PATH.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=OUTPUT_FIELDS)
        writer.writeheader()
        writer.writerows(output_rows)

    print(f"Review rows exported: {len(output_rows)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

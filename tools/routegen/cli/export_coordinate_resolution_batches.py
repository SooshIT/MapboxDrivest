"""Split extracted centre metadata into resolution batches by confidence."""

from __future__ import annotations

import csv
from pathlib import Path
from typing import Dict, List

ROUTEGEN_DIR = Path(__file__).resolve().parents[1]
INPUT_PATH = ROUTEGEN_DIR / "config" / "centre_metadata_extracted.csv"
OUTPUT_HIGH = ROUTEGEN_DIR / "config" / "centre_resolution_high.csv"
OUTPUT_MEDIUM = ROUTEGEN_DIR / "config" / "centre_resolution_medium.csv"
OUTPUT_LOW = ROUTEGEN_DIR / "config" / "centre_resolution_low.csv"

OUTPUT_FIELDS = [
    "centre_id",
    "centre_name",
    "cleaned_postcode_candidate",
    "cleaned_address_candidate",
    "query_primary",
    "query_secondary",
    "query_tertiary",
    "lookup_confidence",
    "lookup_notes",
    "lat",
    "lng",
    "resolution_status",
]


def load_rows(path: Path) -> List[Dict]:
    if not path.exists():
        raise FileNotFoundError(f"Input CSV not found: {path}")
    with path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        return [row for row in reader]


def build_row(row: Dict) -> Dict:
    return {
        "centre_id": (row.get("centre_id") or "").strip(),
        "centre_name": (row.get("centre_name") or "").strip(),
        "cleaned_postcode_candidate": (row.get("cleaned_postcode_candidate") or "").strip(),
        "cleaned_address_candidate": (row.get("cleaned_address_candidate") or "").strip(),
        "query_primary": (row.get("query_primary") or "").strip(),
        "query_secondary": (row.get("query_secondary") or "").strip(),
        "query_tertiary": (row.get("query_tertiary") or "").strip(),
        "lookup_confidence": (row.get("lookup_confidence") or "").strip(),
        "lookup_notes": (row.get("lookup_notes") or "").strip(),
        "lat": (row.get("lat") or "").strip(),
        "lng": (row.get("lng") or "").strip(),
        "resolution_status": "pending",
    }


def write_batch(path: Path, rows: List[Dict]) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=OUTPUT_FIELDS)
        writer.writeheader()
        writer.writerows(rows)


def main() -> int:
    rows = load_rows(INPUT_PATH)
    high: List[Dict] = []
    medium: List[Dict] = []
    low: List[Dict] = []

    for row in rows:
        confidence = (row.get("lookup_confidence") or "").strip().lower()
        if confidence not in ("high", "medium", "low"):
            confidence = "low"
        output_row = build_row(row)
        if confidence == "high":
            high.append(output_row)
        elif confidence == "medium":
            medium.append(output_row)
        else:
            low.append(output_row)

    write_batch(OUTPUT_HIGH, high)
    write_batch(OUTPUT_MEDIUM, medium)
    write_batch(OUTPUT_LOW, low)

    print(f"High confidence batch: {len(high)}")
    print(f"Medium confidence batch: {len(medium)}")
    print(f"Low confidence batch: {len(low)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

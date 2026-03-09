#!/usr/bin/env python
"""Export missing centre coordinates to a CSV for manual fill."""

from __future__ import annotations

import csv
import json
from pathlib import Path


def _load_metadata() -> dict[str, dict]:
    meta_path = Path(__file__).resolve().parents[1] / "config" / "centre_metadata_extracted.csv"
    if not meta_path.exists():
        return {}
    meta = {}
    with meta_path.open("r", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        for row in reader:
            cid = row.get("centre_id")
            if cid:
                meta[cid] = row
    return meta


def main() -> int:
    registry_path = Path(__file__).resolve().parents[1] / "config" / "dvsa_centres.json"
    output_path = Path(__file__).resolve().parents[1] / "output" / "missing_centre_coordinates.csv"

    data = json.loads(registry_path.read_text(encoding="utf-8"))
    centres = data.get("centres", [])
    metadata = _load_metadata()

    output_path.parent.mkdir(parents=True, exist_ok=True)

    with output_path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=[
                "centre_id",
                "centre_name",
                "lat",
                "lng",
                "postcode",
                "address",
                "town",
                "city",
                "county",
                "source_lat",
                "source_lng",
                "notes",
            ],
        )
        writer.writeheader()

        for centre in centres:
            if centre.get("lat") is not None and centre.get("lng") is not None:
                continue

            cid = centre.get("centre_id")
            meta = metadata.get(cid, {})

            writer.writerow(
                {
                    "centre_id": cid,
                    "centre_name": centre.get("centre_name"),
                    "lat": centre.get("lat"),
                    "lng": centre.get("lng"),
                    "postcode": meta.get("cleaned_postcode_candidate") or meta.get("postcode_candidate") or "",
                    "address": meta.get("cleaned_address_candidate") or meta.get("address_candidate") or "",
                    "town": meta.get("town") or "",
                    "city": meta.get("city") or "",
                    "county": meta.get("county") or "",
                    "source_lat": "",
                    "source_lng": "",
                    "notes": "",
                }
            )

    print(str(output_path))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

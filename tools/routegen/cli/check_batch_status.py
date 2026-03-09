
#!/usr/bin/env python
"""Report batch run status from all_centres_batch_summary.json."""

from __future__ import annotations

import json
from collections import Counter
from pathlib import Path


def main() -> int:
    summary_path = Path(__file__).resolve().parents[1] / "output" / "all_centres_batch_summary.json"
    if not summary_path.exists():
        print("Batch summary not found.")
        return 1

    data = json.loads(summary_path.read_text(encoding="utf-8"))
    if not isinstance(data, list):
        print("Batch summary not found.")
        return 1

    total = len(data)
    counts = Counter(item.get("status") for item in data)
    statuses = [
        "already_completed",
        "completed_with_routes",
        "completed_zero_routes",
        "failed",
        "skipped_missing_coordinates",
        "skipped_missing_pdf",
    ]

    print(f"Total centres: {total}")
    for status in statuses:
        print(f"{status}: {counts.get(status, 0)}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())

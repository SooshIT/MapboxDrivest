#!/usr/bin/env python
"""Analyze failed centres from batch summary and classify failure causes."""

from __future__ import annotations

import json
from collections import defaultdict
from pathlib import Path


def _last_error_line(log_text: str) -> str:
    lines = [line.strip() for line in log_text.splitlines() if line.strip()]
    if not lines:
        return ""
    for line in reversed(lines):
        lower = line.lower()
        if any(token in lower for token in ["traceback", "error", "exception", "failed"]):
            return line
    return lines[-1]


def _classify(line: str) -> str:
    text = line.lower()
    if not text:
        return "unknown"
    if "real source pdf not found" in text or "source.pdf" in text and "not found" in text:
        return "missing_pdf"
    if "osmium" in text or ("extract" in text and "osm" in text and "failed" in text):
        return "osm_extract_failure"
    if "graph" in text and ("fail" in text or "error" in text):
        return "graph_build_failure"
    if "candidate" in text and ("fail" in text or "error" in text):
        return "candidate_generation_failure"
    if "validation" in text and ("fail" in text or "error" in text):
        return "validation_failure"
    if "timeout" in text or "timed out" in text:
        return "timeout"
    return "unknown"


def main() -> int:
    base = Path(__file__).resolve().parents[1]
    summary_path = base / "output" / "all_centres_batch_summary.json"
    if not summary_path.exists():
        print("Batch summary not found.")
        return 1

    results = json.loads(summary_path.read_text(encoding="utf-8"))
    failed = [item for item in results if item.get("status") == "failed"]

    categories: dict[str, dict] = defaultdict(lambda: {"count": 0, "centres": []})
    details = {}

    for item in failed:
        centre_id = str(item.get("centre_id"))
        log_path = base / "work" / centre_id / "pipeline.log"
        last_error = ""
        if log_path.exists():
            last_error = _last_error_line(log_path.read_text(encoding="utf-8", errors="ignore"))

        category = _classify(last_error)

        # Heuristic fallback when logs are empty / only preflight line
        if category == "unknown":
            centre_dir = base / "inputs" / "centres" / centre_id
            pdf_path = centre_dir / "source.pdf"
            if not pdf_path.exists() or pdf_path.stat().st_size == 0:
                category = "missing_pdf"
                if not last_error:
                    last_error = "source.pdf missing or empty"
            elif not log_path.exists():
                category = "osm_extract_failure"
                if not last_error:
                    last_error = "pipeline.log missing; extract step likely failed"

        categories[category]["count"] += 1
        categories[category]["centres"].append(centre_id)
        details[centre_id] = {
            "category": category,
            "last_error": last_error,
        }

    analysis = {
        "total_failed": len(failed),
        "categories": categories,
        "details": details,
    }

    output_json = base / "output" / "failed_centres_analysis.json"
    output_md = base / "output" / "failed_centres_analysis.md"
    output_json.write_text(json.dumps(analysis, indent=2), encoding="utf-8")

    lines = ["Category | Count | Centres", "--- | ---: | ---"]
    for cat, info in sorted(categories.items(), key=lambda kv: kv[0]):
        centres = ", ".join(info["centres"])
        lines.append(f"{cat} | {info['count']} | {centres}")
    output_md.write_text("\n".join(lines), encoding="utf-8")

    print(f"Total failed: {len(failed)}")
    for cat, info in sorted(categories.items(), key=lambda kv: kv[0]):
        print(f"{cat}: {info['count']}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())

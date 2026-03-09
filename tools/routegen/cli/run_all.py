"""Run route generation for all active centres."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import yaml

ROUTEGEN_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROUTEGEN_DIR))

from lib.centre_registry import load_centres
from cli.run_centre import run_centre  # noqa: E402


def prepare_centre_inputs(centres: list[dict]) -> dict:
    centres_dir = ROUTEGEN_DIR / "inputs" / "centres"
    centres_dir.mkdir(parents=True, exist_ok=True)
    prepared = 0
    skipped = 0
    for centre in centres:
        slug = str(centre["centre_id"])
        centre_dir = centres_dir / slug
        if centre_dir.exists():
            skipped += 1
            continue
        centre_dir.mkdir(parents=True, exist_ok=True)
        (centre_dir / "seeds").mkdir(parents=True, exist_ok=True)
        centre_json = {
            "centre_id": slug,
            "centre_name": centre["centre_name"],
            "centre_lat": float(centre["lat"]),
            "centre_lng": float(centre["lng"]),
            "source_pdf": "source.pdf",
        }
        (centre_dir / "centre.json").write_text(
            json.dumps(centre_json, indent=2, ensure_ascii=True),
            encoding="utf-8",
        )
        prepared += 1
    return {"detected": len(centres), "prepared": prepared, "skipped": skipped}


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate routes for all active centres.")
    parser.add_argument(
        "--use-overpass",
        action="store_true",
        help="Fetch OSM data via Overpass if no local extract is available.",
    )
    parser.add_argument(
        "--input-mode",
        choices=["pdf", "seed", "hints"],
        default="pdf",
        help="Input mode: pdf (default), seed, or hints",
    )
    args = parser.parse_args()

    cfg_path = ROUTEGEN_DIR / "config" / "pipeline.yaml"
    cfg = yaml.safe_load(cfg_path.read_text(encoding="utf-8")) or {}
    if cfg.get("registry_mode"):
        centres = load_centres()
        summary = prepare_centre_inputs(centres)
        print(f"Centres detected: {summary['detected']}")
        print(f"Centres prepared: {summary['prepared']}")
        print(f"Existing centres skipped: {summary['skipped']}")
        return 0

    centres = cfg.get("active_centres") or cfg.get("seed_mode_centres") or []
    if not centres:
        raise SystemExit("No active centres configured.")

    for centre in centres:
        run_centre(centre, args.use_overpass, args.input_mode)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

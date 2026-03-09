"""Initialize input folders and centre.json for trial centres."""

from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
from typing import Dict, List

ROUTEGEN_DIR = Path(__file__).resolve().parents[1]
TRIAL_PATH = ROUTEGEN_DIR / "config" / "routegen_trial_centres.json"
REGISTRY_PATH = ROUTEGEN_DIR / "config" / "dvsa_centres.json"


def _is_valid_number(value: object) -> bool:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return False
    return math.isfinite(number)


def _load_json(path: Path) -> Dict:
    return json.loads(path.read_text(encoding="utf-8"))


def _is_valid_centre_json(data: Dict, centre_id: str) -> bool:
    if data.get("centre_id") != centre_id:
        return False
    if not data.get("centre_name"):
        return False
    if not _is_valid_number(data.get("centre_lat")):
        return False
    if not _is_valid_number(data.get("centre_lng")):
        return False
    return True


def main() -> int:
    parser = argparse.ArgumentParser(description="Initialize trial centre input folders.")
    parser.add_argument("--overwrite", action="store_true", help="Overwrite existing centre.json files.")
    args = parser.parse_args()

    if not TRIAL_PATH.exists():
        raise SystemExit(f"Trial centres file not found: {TRIAL_PATH}")
    if not REGISTRY_PATH.exists():
        raise SystemExit(f"Registry not found: {REGISTRY_PATH}")

    trial = _load_json(TRIAL_PATH).get("centres", [])
    registry = _load_json(REGISTRY_PATH).get("centres", [])
    registry_lookup = {centre.get("centre_id"): centre for centre in registry if centre.get("centre_id")}

    inputs_dir = ROUTEGEN_DIR / "inputs" / "centres"
    inputs_dir.mkdir(parents=True, exist_ok=True)

    prepared = 0
    skipped = 0
    for centre_id in trial:
        centre = registry_lookup.get(centre_id)
        if not centre:
            print(f"Warning: centre_id not found in registry: {centre_id}")
            skipped += 1
            continue
        if not _is_valid_number(centre.get("lat")) or not _is_valid_number(centre.get("lng")):
            print(f"Warning: centre_id missing coordinates: {centre_id}")
            skipped += 1
            continue

        centre_dir = inputs_dir / centre_id
        centre_dir.mkdir(parents=True, exist_ok=True)
        (centre_dir / "seeds").mkdir(parents=True, exist_ok=True)

        centre_json_path = centre_dir / "centre.json"
        if centre_json_path.exists() and not args.overwrite:
            existing = _load_json(centre_json_path)
            if _is_valid_centre_json(existing, centre_id):
                skipped += 1
                continue
            print(f"Warning: invalid centre.json for {centre_id} (use --overwrite to fix)")
            skipped += 1
            continue

        payload = {
            "centre_id": centre_id,
            "centre_name": centre.get("centre_name", centre_id),
            "centre_lat": float(centre["lat"]),
            "centre_lng": float(centre["lng"]),
            "source_pdf": "source.pdf",
        }
        centre_json_path.write_text(json.dumps(payload, indent=2, ensure_ascii=True), encoding="utf-8")
        prepared += 1

    print(f"Centres prepared: {prepared}")
    print(f"Centres skipped: {skipped}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

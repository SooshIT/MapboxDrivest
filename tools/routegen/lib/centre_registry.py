"""Centre registry loader for DVSA centres."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Dict, List

ROUTEGEN_DIR = Path(__file__).resolve().parents[1]
DEFAULT_REGISTRY_PATH = ROUTEGEN_DIR / "config" / "dvsa_centres.json"


def validate_centre_schema(centre: Dict) -> List[str]:
    errors: List[str] = []
    for key in ("centre_id", "centre_name", "lat", "lng"):
        if key not in centre:
            errors.append(f"missing {key}")
    if "centre_id" in centre and not str(centre["centre_id"]).strip():
        errors.append("centre_id is empty")
    if "centre_name" in centre and not str(centre["centre_name"]).strip():
        errors.append("centre_name is empty")
    if "lat" in centre:
        try:
            float(centre["lat"])
        except (TypeError, ValueError):
            errors.append("lat is not a number")
    if "lng" in centre:
        try:
            float(centre["lng"])
        except (TypeError, ValueError):
            errors.append("lng is not a number")
    return errors


def load_centres(path: Path | None = None) -> List[Dict]:
    registry_path = path or DEFAULT_REGISTRY_PATH
    if not registry_path.exists():
        raise FileNotFoundError(f"Centre registry not found: {registry_path}")
    payload = json.loads(registry_path.read_text(encoding="utf-8"))
    centres = payload.get("centres", [])
    errors: List[str] = []
    for idx, centre in enumerate(centres):
        centre_errors = validate_centre_schema(centre)
        if centre_errors:
            errors.append(f"centres[{idx}]: " + ", ".join(centre_errors))
    if errors:
        raise ValueError("Invalid centre registry schema: " + " | ".join(errors))
    return centres


def get_active_centres(cfg: Dict, registry_centres: List[Dict]) -> List[Dict]:
    if cfg.get("registry_mode"):
        return registry_centres
    active_ids = cfg.get("seed_mode_centres") or cfg.get("active_centres") or []
    if not active_ids:
        return []
    active_lookup = {cid: True for cid in active_ids}
    return [centre for centre in registry_centres if centre.get("centre_id") in active_lookup]

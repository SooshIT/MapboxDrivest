"""Convert routegen outputs into app asset format for Android and iOS."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Dict, List

ROUTEGEN_DIR = Path(__file__).resolve().parents[1]


def load_config(routegen_dir: Path) -> Dict:
    cfg_path = routegen_dir / "config" / "pipeline.yaml"
    raw = cfg_path.read_text(encoding="utf-8")
    cfg = {}
    current_key = None
    for line in raw.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        if stripped.startswith("- ") and current_key:
            cfg.setdefault(current_key, []).append(stripped[2:].strip())
            continue
        if ":" in stripped:
            key, value = stripped.split(":", 1)
            key = key.strip()
            value = value.strip()
            current_key = key if value == "" else None
            if value == "":
                cfg[key] = []
                continue
            cfg[key] = value
    return cfg


def convert_route(route: Dict, fallback_name: str) -> Dict:
    coords = route.get("coordinates") or route.get("geometry") or []
    geometry = [{"lat": item["lat"], "lon": item["lon"]} for item in coords if "lat" in item and "lon" in item]
    if not geometry:
        return {}
    start = geometry[0]
    distance_m = route.get("distanceM") or route.get("distanceMeters") or 0.0
    duration_s = route.get("durationS") or route.get("estimatedDurationSeconds") or 0.0
    return {
        "id": route.get("id", ""),
        "name": route.get("name") or fallback_name,
        "geometry": geometry,
        "distanceM": distance_m,
        "durationS": duration_s,
        "startLat": start["lat"],
        "startLon": start["lon"],
    }


def export_centre(centre_slug: str) -> None:
    output_path = ROUTEGEN_DIR / "output" / centre_slug / "routes.json"
    if not output_path.exists():
        raise FileNotFoundError(f"Routegen output missing: {output_path}")

    payload = json.loads(output_path.read_text(encoding="utf-8"))
    routes = payload.get("routes", [])
    converted = []
    for idx, route in enumerate(routes, start=1):
        fallback_name = f"{payload.get('metadata', {}).get('centreName', centre_slug)} Practice {idx:02d}"
        converted_route = convert_route(route, fallback_name)
        if converted_route:
            converted.append(converted_route)

    asset_payload = {"routes": converted}

    android_dir = Path("android/app/src/main/assets/routes") / centre_slug
    ios_dir = Path("ios/DrivestNavigation/Resources/Data/routes") / centre_slug
    android_dir.mkdir(parents=True, exist_ok=True)
    ios_dir.mkdir(parents=True, exist_ok=True)

    android_path = android_dir / "routes.json"
    ios_path = ios_dir / "routes.json"

    android_path.write_text(json.dumps(asset_payload, indent=2, ensure_ascii=True), encoding="utf-8")
    ios_path.write_text(json.dumps(asset_payload, indent=2, ensure_ascii=True), encoding="utf-8")

    print(f"Wrote {len(converted)} routes for {centre_slug} to {android_path}")
    print(f"Wrote {len(converted)} routes for {centre_slug} to {ios_path}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Export routegen outputs into app assets.")
    parser.add_argument("--centre", help="Centre slug to export")
    args = parser.parse_args()

    if args.centre:
        export_centre(args.centre)
        return 0

    cfg = load_config(ROUTEGEN_DIR)
    centres = cfg.get("active_centres") or []
    if not centres:
        raise SystemExit("No active centres configured.")
    for centre_slug in centres:
        export_centre(centre_slug)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

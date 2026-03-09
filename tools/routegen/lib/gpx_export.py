"""Export generated routes as GPX files."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Dict, Iterable, List
from xml.sax.saxutils import escape


def _track_points(coordinates: Iterable[Dict]) -> str:
    lines = []
    for point in coordinates:
        lat = float(point["lat"])
        lon = float(point["lon"])
        lines.append(f'      <trkpt lat="{lat:.7f}" lon="{lon:.7f}"></trkpt>')
    return "\n".join(lines)


def _description(route: Dict) -> str:
    roads = route.get("roadsUsed") or []
    roads_text = ", ".join(str(road) for road in roads[:12])
    return (
        f"{route.get('centreName', '')} practice route; "
        f"distance={float(route.get('distanceMeters', 0.0)):.1f}m; "
        f"duration={float(route.get('estimatedDurationSeconds', 0.0)):.1f}s; "
        f"roads={roads_text}"
    )


def export_routes_to_gpx(output_dir: Path, centre_slug: str, routes: List[Dict]) -> Path:
    gpx_dir = output_dir / "gpx"
    gpx_dir.mkdir(parents=True, exist_ok=True)

    manifest = []
    for idx, route in enumerate(routes, start=1):
        coordinates = route.get("coordinates") or []
        if len(coordinates) < 2:
            continue
        centre_coords = route.get("centreCoordinates") or {}
        waypoint = ""
        if centre_coords.get("lat") is not None and centre_coords.get("lon") is not None:
            waypoint = (
                f'  <wpt lat="{float(centre_coords["lat"]):.7f}" '
                f'lon="{float(centre_coords["lon"]):.7f}">\n'
                f"    <name>{escape(str(route.get('centreName', centre_slug)))} Test Centre</name>\n"
                "  </wpt>\n"
            )

        gpx = (
            '<?xml version="1.0" encoding="UTF-8"?>\n'
            '<gpx version="1.1" creator="Drivest Routegen" '
            'xmlns="http://www.topografix.com/GPX/1/1" '
            'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" '
            'xsi:schemaLocation="http://www.topografix.com/GPX/1/1 '
            'http://www.topografix.com/GPX/1/1/gpx.xsd">\n'
            "  <metadata>\n"
            f"    <name>{escape(str(route.get('name', route.get('id', 'route'))))}</name>\n"
            f"    <desc>{escape(_description(route))}</desc>\n"
            "  </metadata>\n"
            f"{waypoint}"
            "  <trk>\n"
            f"    <name>{escape(str(route.get('name', route.get('id', 'route'))))}</name>\n"
            f"    <desc>{escape(str(route.get('id', '')))}</desc>\n"
            "    <trkseg>\n"
            f"{_track_points(coordinates)}\n"
            "    </trkseg>\n"
            "  </trk>\n"
            "</gpx>\n"
        )
        filename = f"{centre_slug}-route-{idx:02d}.gpx"
        (gpx_dir / filename).write_text(gpx, encoding="utf-8")
        if idx == 1:
            (output_dir / f"{centre_slug}-route-1.gpx").write_text(gpx, encoding="utf-8")
        manifest.append(
            {
                "index": idx,
                "id": route.get("id"),
                "name": route.get("name"),
                "distanceMeters": route.get("distanceMeters"),
                "estimatedDurationSeconds": route.get("estimatedDurationSeconds"),
                "file": filename,
            }
        )

    (gpx_dir / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    return gpx_dir

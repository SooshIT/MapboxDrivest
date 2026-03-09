"""Run route generation for all DVSA centres using Geofabrik region PBFs."""

from __future__ import annotations

import argparse
import csv
import json
import os
import re
import sys
import time
import urllib.request
from pathlib import Path
from typing import Dict, Iterable, List, Optional

ROUTEGEN_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROUTEGEN_DIR))

from cli.run_centre import run_centre  # noqa: E402


INDEX_URL = "https://download.geofabrik.de/index-v1.json"
UK_PBF_REGEX = re.compile(
    r"^https://download\.geofabrik\.de/europe/united-kingdom/"
    r"(england|scotland|wales|northern-ireland)(/|-)"
)
NI_FALLBACK_PBF = "https://download.geofabrik.de/europe/ireland-and-northern-ireland-latest.osm.pbf"


def _load_registry() -> List[Dict]:
    registry_path = ROUTEGEN_DIR / "config" / "dvsa_centres.json"
    data = json.loads(registry_path.read_text(encoding="utf-8"))
    centres = data.get("centres", [])
    return centres


def _centre_input_coordinates(centre_dir: Path) -> Optional[Dict[str, float]]:
    centre_json = centre_dir / "centre.json"
    if not centre_json.exists():
        return None
    try:
        payload = json.loads(centre_json.read_text(encoding="utf-8"))
        lat = float(payload.get("centre_lat"))
        lng = float(payload.get("centre_lng"))
    except (OSError, ValueError, TypeError, json.JSONDecodeError):
        return None
    return {"lat": lat, "lng": lng}


def _load_index(cache_path: Path) -> Dict:
    if not cache_path.exists():
        cache_path.parent.mkdir(parents=True, exist_ok=True)
        with urllib.request.urlopen(INDEX_URL) as resp:
            cache_path.write_bytes(resp.read())
    return json.loads(cache_path.read_text(encoding="utf-8"))


def _entries_for_uk(index: Dict) -> List[Dict]:
    entries = []
    for entry in index.get("features", []):
        props = entry.get("properties", {})
        pbf_url = props.get("urls", {}).get("pbf", "")
        if pbf_url and (UK_PBF_REGEX.match(pbf_url) or pbf_url == NI_FALLBACK_PBF):
            entries.append({"props": props, "geometry": entry.get("geometry")})
    return entries


def _bbox_from_geometry(geometry: Optional[Dict]) -> Optional[List[float]]:
    if not geometry:
        return None
    coords = geometry.get("coordinates")
    if not coords:
        return None

    min_lon = min_lat = float("inf")
    max_lon = max_lat = float("-inf")

    def iter_coords(node):
        if isinstance(node, (list, tuple)) and len(node) == 2 and all(
            isinstance(value, (int, float)) for value in node
        ):
            yield node
        elif isinstance(node, (list, tuple)):
            for child in node:
                yield from iter_coords(child)

    for lon, lat in iter_coords(coords):
        min_lon = min(min_lon, lon)
        max_lon = max(max_lon, lon)
        min_lat = min(min_lat, lat)
        max_lat = max(max_lat, lat)

    if min_lon == float("inf") or min_lat == float("inf"):
        return None
    return [min_lon, min_lat, max_lon, max_lat]


def _pick_region(entries: List[Dict], lat: float, lng: float) -> Optional[Dict]:
    hits = []
    for entry in entries:
        props = entry.get("props", {})
        bbox = props.get("bbox") or _bbox_from_geometry(entry.get("geometry"))
        if not bbox or len(bbox) != 4:
            continue
        min_lon, min_lat, max_lon, max_lat = bbox
        if min_lat <= lat <= max_lat and min_lon <= lng <= max_lon:
            area = (max_lat - min_lat) * (max_lon - min_lon)
            hits.append((area, props))
    if not hits:
        return None
    hits.sort(key=lambda item: item[0])
    return hits[0][1]


def _region_slug(pbf_url: str) -> str:
    name = pbf_url.split("/")[-1]
    if name.endswith("-latest.osm.pbf"):
        return name[: -len("-latest.osm.pbf")]
    return name.replace(".osm.pbf", "")


def _ensure_pbf(pbf_url: str, dest_path: Path) -> None:
    if dest_path.exists():
        return
    dest_path.parent.mkdir(parents=True, exist_ok=True)
    urllib.request.urlretrieve(pbf_url, dest_path)


def _bbox_for_centre(lat: float, lng: float) -> str:
    min_lat = lat - 0.06
    max_lat = lat + 0.06
    min_lng = lng - 0.10
    max_lng = lng + 0.10
    return f"{min_lng:.7f},{min_lat:.7f},{max_lng:.7f},{max_lat:.7f}"


def _extract_osm(osm_dir: Path, pbf_path: Path, bbox: str) -> None:
    import subprocess

    cmd = [
        "docker",
        "run",
        "--rm",
        "-v",
        f"{osm_dir.as_posix()}:/data",
        "iboates/osmium",
        "extract",
        f"/data/sources/{pbf_path.name}",
        "--bbox",
        bbox,
        "-f",
        "osm",
        "-o",
        "/data/extract.osm",
        "--overwrite",
    ]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or result.stdout.strip())


def _parse_log_stats(log_path: Path) -> Dict[str, Optional[int]]:
    if not log_path.exists():
        return {"graph_nodes": None, "graph_edges": None, "candidate_count": None}
    text = log_path.read_text(encoding="utf-8", errors="ignore")
    graph_nodes = graph_edges = candidate_count = None
    import re

    m = re.search(r"Graph loaded\\. nodes=(\\d+) edges=(\\d+)", text)
    if m:
        graph_nodes = int(m.group(1))
        graph_edges = int(m.group(2))
    m = re.search(r"Generated (\\d+) candidate routes", text)
    if m:
        candidate_count = int(m.group(1))
    return {"graph_nodes": graph_nodes, "graph_edges": graph_edges, "candidate_count": candidate_count}


def _load_report(output_dir: Path) -> Dict[str, Optional[int]]:
    report_path = output_dir / "validation_report.json"
    if not report_path.exists():
        return {"validated": None, "candidates": None, "routes_count": None}
    report = json.loads(report_path.read_text(encoding="utf-8"))
    routes_path = output_dir / "routes.json"
    routes_count = None
    if routes_path.exists():
        data = json.loads(routes_path.read_text(encoding="utf-8"))
        routes_count = len(data.get("routes", [])) if isinstance(data, dict) else None
    return {
        "validated": report.get("validated"),
        "candidates": report.get("candidates"),
        "routes_count": routes_count,
    }


def _status_for(output_dir: Path, validated: Optional[int], routes_count: Optional[int]) -> str:
    if (output_dir / "routes.json").exists() and (output_dir / "validation_report.json").exists():
        if routes_count and routes_count > 0:
            return "completed_with_routes"
        return "completed_zero_routes"
    return "failed"


def _input_ready(centre_dir: Path, input_mode: str) -> bool:
    if input_mode == "hints":
        hints_path = centre_dir / "hints.json"
        return hints_path.exists() and hints_path.stat().st_size > 0
    pdf_path = centre_dir / "source.pdf"
    return pdf_path.exists() and pdf_path.stat().st_size > 0


def _missing_input_status(input_mode: str) -> str:
    if input_mode == "hints":
        return "skipped_missing_hints"
    return "skipped_missing_pdf"


def _missing_input_reason(input_mode: str) -> str:
    if input_mode == "hints":
        return "hints.json missing"
    return "source.pdf missing"


def main() -> int:
    parser = argparse.ArgumentParser(description="Run all DVSA centres with regional Geofabrik PBFs.")
    parser.add_argument("--resume", action="store_true", help="Skip centres with existing outputs.")
    parser.add_argument("--centre", help="Run a single centre by centre_id.")
    parser.add_argument(
        "--input-mode",
        choices=["pdf", "hints"],
        default="pdf",
        help="Input mode: pdf (default) or hints",
    )
    args = parser.parse_args()

    lock_path = ROUTEGEN_DIR / "output" / "run_all_dvsa.lock"
    if lock_path.exists():
        try:
            pid_text = lock_path.read_text(encoding="utf-8").strip()
        except OSError:
            pid_text = ""
        print("Another batch run is already active.")
        if pid_text:
            print(f"Active PID: {pid_text}")
        print("Delete tools/routegen/output/run_all_dvsa.lock if the process is dead.")
        return 1

    lock_path.parent.mkdir(parents=True, exist_ok=True)
    lock_path.write_text(str(os.getpid()), encoding="utf-8")

    try:
        centres = _load_registry()
        index_cache = ROUTEGEN_DIR / "inputs" / "osm" / "sources" / "geofabrik_index.json"
        index = _load_index(index_cache)
        entries = _entries_for_uk(index)

        osm_dir = ROUTEGEN_DIR / "inputs" / "osm"
        sources_dir = osm_dir / "sources"
        sources_dir.mkdir(parents=True, exist_ok=True)

        summary_json = ROUTEGEN_DIR / "output" / "all_centres_batch_summary.json"
        summary_csv = ROUTEGEN_DIR / "output" / "all_centres_batch_summary.csv"

        existing_by_id: Dict[str, Dict] = {}
        if args.resume and summary_json.exists():
            try:
                existing = json.loads(summary_json.read_text(encoding="utf-8"))
                if isinstance(existing, list):
                    existing_by_id = {
                        str(item.get("centre_id")): item for item in existing if item.get("centre_id")
                    }
            except json.JSONDecodeError:
                existing_by_id = {}

        results_by_id: Dict[str, Dict] = dict(existing_by_id)

        def write_summary() -> None:
            summary_json.parent.mkdir(parents=True, exist_ok=True)
            ordered: List[Dict] = []
            seen = set()
            for centre in centres:
                cid = str(centre.get("centre_id"))
                if cid in results_by_id:
                    ordered.append(results_by_id[cid])
                    seen.add(cid)
            for cid, entry in results_by_id.items():
                if cid not in seen:
                    ordered.append(entry)
            summary_json.write_text(json.dumps(ordered, indent=2), encoding="utf-8")
            with summary_csv.open("w", newline="", encoding="utf-8") as handle:
                writer = csv.DictWriter(
                    handle,
                    fieldnames=[
                        "centre_id",
                        "region",
                        "bbox",
                        "extract_size",
                        "graph_nodes",
                        "graph_edges",
                        "candidate_count",
                        "validated_count",
                        "routes_count",
                        "status",
                        "reason",
                        "runtime",
                    ],
                )
                writer.writeheader()
                writer.writerows(ordered)

        if args.centre:
            centres = [c for c in centres if str(c.get("centre_id")) == args.centre]

        total_centres = len(centres)
        usable_coords = sum(
            1
            for c in centres
            if (c.get("lat") is not None and c.get("lng") is not None)
            or (c.get("centre_lat") is not None and c.get("centre_lng") is not None)
        )
        missing_coords = total_centres - usable_coords
        centres_dir = ROUTEGEN_DIR / "inputs" / "centres"
        centres_with_input = 0
        centres_missing_input = 0
        for c in centres:
            cid = c.get("centre_id")
            if not cid:
                continue
            centre_dir = centres_dir / str(cid)
            if _input_ready(centre_dir, args.input_mode):
                centres_with_input += 1
            else:
                centres_missing_input += 1

        print(f"Centres with coordinates: {usable_coords}")
        print(f"Centres missing coordinates: {missing_coords}")
        if args.input_mode == "hints":
            print(f"Centres with hints.json: {centres_with_input}")
            print(f"Centres missing hints.json: {centres_missing_input}")
        else:
            print(f"Centres with source.pdf: {centres_with_input}")
            print(f"Centres missing source.pdf: {centres_missing_input}")
        terminal_statuses = {
            "already_completed",
            "completed_with_routes",
            "completed_zero_routes",
        }

        for idx, centre in enumerate(centres, start=1):
            centre_id = str(centre.get("centre_id"))
            centre_dir = ROUTEGEN_DIR / "inputs" / "centres" / centre_id
            centre_input_coords = _centre_input_coordinates(centre_dir)
            if centre_input_coords is not None:
                lat = centre_input_coords["lat"]
                lng = centre_input_coords["lng"]
            else:
                lat = centre.get("lat")
                lng = centre.get("lng")
            if lat is None or lng is None:
                lat = centre.get("centre_lat")
                lng = centre.get("centre_lng")

            if args.resume:
                existing_entry = results_by_id.get(centre_id)
                if existing_entry and existing_entry.get("status") in terminal_statuses:
                    write_summary()
                    print(f"[{idx}/{total_centres}] {centre_id} {existing_entry.get('status')}")
                    continue

            if lat is None or lng is None:
                results_by_id[centre_id] = {
                    "centre_id": centre_id,
                    "region": None,
                    "bbox": None,
                    "extract_size": None,
                    "graph_nodes": None,
                    "graph_edges": None,
                    "candidate_count": None,
                    "validated_count": None,
                    "routes_count": None,
                    "status": "skipped_missing_coordinates",
                    "reason": None,
                    "runtime": None,
                }
                write_summary()
                print(f"[{idx}/{total_centres}] {centre_id} skipped_missing_coordinates")
                continue

            output_dir = ROUTEGEN_DIR / "output" / centre_id
            if args.resume and (output_dir / "routes.json").exists() and (output_dir / "validation_report.json").exists():
                report = _load_report(output_dir)
                results_by_id[centre_id] = {
                    "centre_id": centre_id,
                    "region": None,
                    "bbox": None,
                    "extract_size": None,
                    "graph_nodes": None,
                    "graph_edges": None,
                    "candidate_count": None,
                    "validated_count": report.get("validated"),
                    "routes_count": report.get("routes_count"),
                    "status": "already_completed",
                    "reason": None,
                    "runtime": None,
                }
                write_summary()
                print(
                    f"[{idx}/{total_centres}] {centre_id} already_completed "
                    f"routes={report.get('routes_count')}"
                )
                continue

            if not _input_ready(centre_dir, args.input_mode):
                region_entry = _pick_region(entries, float(lat), float(lng))
                region = None
                if region_entry:
                    pbf_url = region_entry.get("urls", {}).get("pbf")
                    if pbf_url:
                        region = _region_slug(pbf_url)
                results_by_id[centre_id] = {
                    "centre_id": centre_id,
                    "region": region,
                    "bbox": None,
                    "extract_size": None,
                    "graph_nodes": None,
                    "graph_edges": None,
                    "candidate_count": None,
                    "validated_count": None,
                    "routes_count": None,
                    "status": _missing_input_status(args.input_mode),
                    "reason": _missing_input_reason(args.input_mode),
                    "runtime": None,
                }
                write_summary()
                print(
                    f"[{idx}/{total_centres}] {centre_id} "
                    f"{_missing_input_status(args.input_mode)}"
                )
                continue

            region_entry = _pick_region(entries, float(lat), float(lng))
            if not region_entry:
                results_by_id[centre_id] = {
                    "centre_id": centre_id,
                    "region": None,
                    "bbox": None,
                    "extract_size": None,
                    "graph_nodes": None,
                    "graph_edges": None,
                    "candidate_count": None,
                    "validated_count": None,
                    "routes_count": None,
                    "status": "failed",
                    "reason": None,
                    "runtime": None,
                }
                write_summary()
                print(f"[{idx}/{total_centres}] {centre_id} failed")
                continue

            pbf_url = region_entry.get("urls", {}).get("pbf")
            if not pbf_url:
                results_by_id[centre_id] = {
                    "centre_id": centre_id,
                    "region": None,
                    "bbox": None,
                    "extract_size": None,
                    "graph_nodes": None,
                    "graph_edges": None,
                    "candidate_count": None,
                    "validated_count": None,
                    "routes_count": None,
                    "status": "failed",
                    "reason": None,
                    "runtime": None,
                }
                write_summary()
                print(f"[{idx}/{total_centres}] {centre_id} failed")
                continue

            region = _region_slug(pbf_url)
            pbf_path = sources_dir / f"{region}-latest.osm.pbf"
            _ensure_pbf(pbf_url, pbf_path)

            bbox = _bbox_for_centre(float(lat), float(lng))
            try:
                _extract_osm(osm_dir, pbf_path, bbox)
            except RuntimeError:
                results_by_id[centre_id] = {
                    "centre_id": centre_id,
                    "region": region,
                    "bbox": bbox,
                    "extract_size": None,
                    "graph_nodes": None,
                    "graph_edges": None,
                    "candidate_count": None,
                    "validated_count": None,
                    "routes_count": None,
                    "status": "failed",
                    "reason": None,
                    "runtime": None,
                }
                write_summary()
                print(f"[{idx}/{total_centres}] {centre_id} failed")
                continue

            extract_path = osm_dir / "extract.osm"
            extract_size = extract_path.stat().st_size if extract_path.exists() else None

            start = time.monotonic()
            run_centre(centre_id, use_overpass=False, input_mode=args.input_mode)
            runtime = time.monotonic() - start

            work_dir = ROUTEGEN_DIR / "work" / centre_id
            log_stats = _parse_log_stats(work_dir / "pipeline.log")
            report = _load_report(output_dir)
            status = _status_for(output_dir, report.get("validated"), report.get("routes_count"))

            results_by_id[centre_id] = {
                "centre_id": centre_id,
                "region": region,
                "bbox": bbox,
                "extract_size": extract_size,
                "graph_nodes": log_stats.get("graph_nodes"),
                "graph_edges": log_stats.get("graph_edges"),
                "candidate_count": log_stats.get("candidate_count"),
                "validated_count": report.get("validated"),
                "routes_count": report.get("routes_count"),
                "status": status,
                "reason": None,
                "runtime": round(runtime, 2),
            }
            write_summary()
            print(
                f"[{idx}/{total_centres}] {centre_id} {status} "
                f"routes={report.get('routes_count')} time={round(runtime, 2)}s"
            )

        # Write runnable centres results
        output_result = ROUTEGEN_DIR / "output" / "runnable_centres_result.json"
        completed_with_routes = [
            cid for cid, entry in results_by_id.items() if entry.get("status") == "completed_with_routes"
        ]
        completed_zero_routes = [
            cid for cid, entry in results_by_id.items() if entry.get("status") == "completed_zero_routes"
        ]
        output_result.parent.mkdir(parents=True, exist_ok=True)
        output_result.write_text(
            json.dumps(
                {
                    "completed_with_routes": completed_with_routes,
                    "completed_zero_routes": completed_zero_routes,
                },
                indent=2,
            ),
            encoding="utf-8",
        )
        return 0
    finally:
        try:
            lock_path.unlink()
        except FileNotFoundError:
            pass


if __name__ == "__main__":
    raise SystemExit(main())

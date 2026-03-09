"""Run the route generation pipeline for one centre."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Dict, List

ROUTEGEN_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROUTEGEN_DIR))

from lib.log import PipelineLogger, ensure_dir
from lib.centre_verify import verify_centre
from lib.gpx_export import export_routes_to_gpx
from lib.normalize import clean_road_name, dedupe_names
from lib.overlap import compute_max_overlaps
from lib.pack import build_routes_pack
from lib.route_shape import describe_route_shape
from lib.score import score_route
from lib.seed_import import (
    build_seed_route,
    extract_route_key,
    list_seed_files,
    seed_quality_score,
    validate_seed_route,
)


def load_json(path: Path) -> Dict:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def load_config(routegen_dir: Path) -> Dict:
    cfg_path = routegen_dir / "config" / "pipeline.yaml"
    raw = cfg_path.read_text(encoding="utf-8")
    try:
        import yaml  # type: ignore

        cfg = yaml.safe_load(raw) or {}
    except Exception:
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
                if value.lower() in ("true", "false"):
                    cfg[key] = value.lower() == "true"
                else:
                    try:
                        if "." in value:
                            cfg[key] = float(value)
                        else:
                            cfg[key] = int(value)
                    except ValueError:
                        cfg[key] = value
    weights_path = routegen_dir / "config" / "scoring_weights.json"
    cfg["scoring_weights"] = json.loads(weights_path.read_text(encoding="utf-8"))
    return cfg


def _osm_sources_present(osm_dir: Path) -> bool:
    return any(
        (osm_dir / name).exists()
        for name in ("extract.graphml", "extract.osm", "extract.osm.pbf")
    )


def _validate_pdf(pdf_path: Path, centre_slug: str) -> List[str]:
    errors: List[str] = []
    if not pdf_path.exists():
        errors.append(
            f"Real source PDF not found for centre {centre_slug}. Route generation cannot proceed."
        )
        return errors
    if pdf_path.stat().st_size == 0:
        errors.append(
            f"Real source PDF not found for centre {centre_slug}. Route generation cannot proceed."
        )
        return errors
    try:
        try:
            from lib.pdf_extract import read_pdf_text
        except ModuleNotFoundError:
            read_pdf_text = None

        if read_pdf_text is not None:
            _ = read_pdf_text(pdf_path)
        else:
            try:
                from PyPDF2 import PdfReader  # type: ignore

                _ = PdfReader(str(pdf_path))
            except Exception as exc:
                errors.append(
                    f"Real source PDF not found for centre {centre_slug}. Route generation cannot proceed."
                )
                errors.append(f"PDF read failed for {centre_slug}: {exc}")
    except Exception:
        errors.append(
            f"Real source PDF not found for centre {centre_slug}. Route generation cannot proceed."
        )
    return errors


def _validate_hints(centre_dir: Path, centre_slug: str) -> List[str]:
    errors: List[str] = []
    try:
        from lib.hints import hints_present
    except ModuleNotFoundError:
        hints_present = None

    hints_path = centre_dir / "hints.json"
    if hints_present is None:
        if not hints_path.exists():
            errors.append(f"hints.json missing for centre {centre_slug}.")
        return errors

    if not hints_present(centre_dir):
        errors.append(
            f"Valid hints.json missing for centre {centre_slug}. Route generation cannot proceed."
        )
    return errors


def preflight_checks(
    routegen_dir: Path,
    centre_slug: str,
    use_overpass: bool,
    logger,
    input_mode: str,
) -> Dict:
    centres_dir = routegen_dir / "inputs" / "centres"
    centre_dir = centres_dir / centre_slug
    errors: List[str] = []

    if not centre_dir.exists():
        errors.append(f"Centre folder not found: {centre_dir}")
        return {"errors": errors, "centre": None, "radius": None}

    centre_json = centre_dir / "centre.json"
    if not centre_json.exists():
        errors.append(f"centre.json missing for centre {centre_slug}.")
        return {"errors": errors, "centre": None, "radius": None}

    centre = load_json(centre_json)
    centre_lat = float(centre.get("centre_lat") or 0.0)
    centre_lng = float(centre.get("centre_lng") or 0.0)
    if centre_lat == 0.0 or centre_lng == 0.0:
        errors.append(f"centre.json missing coordinates for centre {centre_slug}.")

    if input_mode == "pdf":
        pdf_path = centre_dir / centre.get("source_pdf", "source.pdf")
        errors.extend(_validate_pdf(pdf_path, centre_slug))

    if input_mode == "seed":
        seeds_dir = centre_dir / "seeds"
        if not seeds_dir.exists():
            errors.append(f"Seeds folder missing for centre {centre_slug}: {seeds_dir}")
        else:
            seed_files = list_seed_files(seeds_dir)
            if not seed_files:
                errors.append(f"No seed route files found for centre {centre_slug} in {seeds_dir}")

    if input_mode == "hints":
        errors.extend(_validate_hints(centre_dir, centre_slug))

    radius_cfg = centre_dir / "radius.json"
    radius = None
    if radius_cfg.exists():
        radius_json = load_json(radius_cfg)
        radius = float(radius_json.get("radius_meters") or 0.0)

    if input_mode in {"pdf", "hints"}:
        osm_dir = routegen_dir / "inputs" / "osm"
        if not _osm_sources_present(osm_dir) and not use_overpass:
            errors.append(
                "No local OSM extract found in tools/routegen/inputs/osm and "
                "--use-overpass not provided. Route generation cannot proceed."
            )

    if errors:
        for message in errors:
            logger.error(message)
    else:
        logger.info("Preflight checks passed.")

    return {"errors": errors, "centre": centre, "radius": radius}


def build_output_routes(centre: Dict, routes: List[Dict]) -> List[Dict]:
    output = []
    for idx, route in enumerate(routes, start=1):
        duration = float(route.get("estimated_duration_s", route["duration_s"]))
        if duration < 1200:
            difficulty = "easy"
        elif duration < 1600:
            difficulty = "medium"
        else:
            difficulty = "hard"

        coordinates = [{"lat": lat, "lon": lon} for lat, lon in route["geometry"]]
        route_key = route.get("route_key")
        route_id = f"{centre['centre_id']}-{route_key}" if route_key else f"{centre['centre_id']}-{idx:02d}"
        output.append(
            {
                "id": route_id,
                "name": route.get("display_name") or f"{centre['centre_name']} Practice {idx:02d}",
                "centreId": centre["centre_id"],
                "centreName": centre["centre_name"],
                "centreCoordinates": {
                    "lat": centre["centre_lat"],
                    "lon": centre["centre_lng"],
                },
                "distanceMeters": route["distance_m"],
                "distanceM": route["distance_m"],
                "estimatedDurationSeconds": duration,
                "durationS": duration,
                "difficultyLevel": difficulty,
                "roadsUsed": route.get("roads_used_clean", []),
                "coordinates": coordinates,
                "polyline": "",
                "validationFlags": route.get("validation", {}).get("warnings", []),
                "qualityScore": route.get("quality", {}).get("quality_score", 0.0),
                "routeFamily": route.get("shape", {}).get("family", ""),
                "routeZones": route.get("shape", {}).get("zone_keys", []),
                "sourcePdfName": centre.get("source_pdf", "source.pdf"),
                "sourceFile": route.get("source_file", ""),
                "sourceFormat": route.get("source_format", ""),
            }
        )
    return output


def _anchor_geometry_to_centre(
    coords: List[tuple[float, float]],
    centre_lat: float,
    centre_lng: float,
) -> List[tuple[float, float]]:
    anchored: List[tuple[float, float]] = list(coords or [])
    centre_coord = (centre_lat, centre_lng)
    if not anchored:
        return [centre_coord, centre_coord]
    if anchored[0] != centre_coord:
        anchored = [centre_coord] + anchored
    if anchored[-1] != centre_coord:
        anchored = anchored + [centre_coord]
    return anchored


def _write_centre_verification(output_dir: Path, logger, report: Dict) -> None:
    logger.write_json("00_centre_verification.json", report)
    (output_dir / "centre_verification.json").write_text(
        json.dumps(report, indent=2, ensure_ascii=True),
        encoding="utf-8",
    )


def _export_route_artifacts(
    output_dir: Path,
    centre_slug: str,
    output_routes: List[Dict],
    logger,
) -> None:
    gpx_dir = export_routes_to_gpx(output_dir, centre_slug, output_routes)
    logger.info(f"Exported {len(output_routes)} GPX routes to {gpx_dir}")


def run_seed_import(routegen_dir: Path, centre_slug: str, centre: Dict, cfg: Dict, logger) -> int:
    centre_dir = routegen_dir / "inputs" / "centres" / centre_slug
    work_dir = ensure_dir(routegen_dir / "work" / centre_slug)
    output_dir = ensure_dir(routegen_dir / "output" / centre_slug)

    seeds_dir = centre_dir / "seeds"
    logger.stage("seed import")
    seed_files = list_seed_files(seeds_dir)
    logger.info(f"Seed files discovered: {len(seed_files)}")
    logger.write_json("01_seed_files.json", [file.name for file in seed_files])

    logger.stage("parse seed files")
    avg_speed_kph = float(cfg.get("seed_avg_speed_kph", 30))
    parsed = []
    for file in seed_files:
        route = build_seed_route(file, avg_speed_kph)
        parsed.append(route)
    logger.write_json("02_seed_parsed.json", parsed)

    logger.stage("validate seeds")
    grouped: Dict[str, List[Dict]] = {}
    for route in parsed:
        key = extract_route_key(route["source_file"]) or route["route_key"]
        grouped.setdefault(key, []).append(route)

    selected = []
    rejection_counts: Dict[str, int] = {}
    review_entries: List[Dict] = []
    rejected_keys: Dict[str, List[str]] = {}
    for key, routes in grouped.items():
        evaluated = []
        for route in routes:
            anchor_lat = centre.get("seed_anchor_lat")
            anchor_lng = centre.get("seed_anchor_lng")
            validation = validate_seed_route(
                route,
                centre_lat=centre["centre_lat"],
                centre_lng=centre["centre_lng"],
                cfg=cfg,
                anchor_lat=anchor_lat,
                anchor_lng=anchor_lng,
            )
            route["validation"] = validation
            route["quality"] = seed_quality_score(route, validation, cfg)
            review_entry = {
                "route_id": route["route_key"],
                "source_file": route["source_file"],
                "point_count": route["point_count"],
                "distance_m": route["distance_m"],
                "distance_miles": validation["metrics"].get("distance_miles", 0.0),
                "estimated_duration_s": validation["metrics"].get("estimated_duration_s", 0.0),
                "estimated_duration_min": validation["metrics"].get("estimated_duration_min", 0.0),
                "start_distance_m": validation["metrics"].get("start_distance_m", 0.0),
                "end_distance_m": validation["metrics"].get("end_distance_m", 0.0),
                "self_overlap_ratio": validation["metrics"].get("self_overlap_ratio", 0.0),
                "rejection_reasons": list(validation["hard_failures"]),
                "warnings": list(validation["warnings"]),
                "outlier_reason": validation.get("outlier_reason"),
                "accepted": False,
            }
            route["_review"] = review_entry
            evaluated.append(route)
            review_entries.append(review_entry)

        if len(evaluated) == 1:
            route = evaluated[0]
            if not route["validation"]["passed"]:
                for reason in route["validation"]["hard_failures"]:
                    rejection_counts[reason] = rejection_counts.get(reason, 0) + 1
                rejected_keys[key] = list(route["validation"]["hard_failures"])
                logger.warn(
                    f"Seed route {key} rejected: {', '.join(route['validation']['hard_failures'])}"
                )
                logger.write_jsonl(
                    "06_rejections.jsonl",
                    {"id": key, "stage": "validation", "reasons": route["validation"]["hard_failures"]},
                )
                continue
            route["_review"]["accepted"] = True
            selected.append(route)
            logger.write_jsonl("07_validated.jsonl", route)
            continue

        gpx = [r for r in evaluated if r["source_format"] == "gpx"]
        kml = [r for r in evaluated if r["source_format"] == "kml"]
        primary = gpx[0] if gpx else evaluated[0]
        secondary = kml[0] if kml else (evaluated[1] if len(evaluated) > 1 else None)

        if primary and not primary["validation"]["passed"]:
            primary = None
        if secondary and not secondary["validation"]["passed"]:
            secondary = None

        if primary and not secondary:
            primary["_review"]["accepted"] = True
            selected.append(primary)
            logger.write_jsonl("07_validated.jsonl", primary)
            for route in evaluated:
                if route is not primary:
                    route["_review"]["rejection_reasons"].append("duplicate_rejected")
                    logger.write_jsonl(
                        "06_rejections.jsonl",
                        {"id": key, "stage": "duplicate", "reasons": ["duplicate_rejected"]},
                    )
            continue
        if secondary and not primary:
            secondary["_review"]["accepted"] = True
            selected.append(secondary)
            logger.write_jsonl("07_validated.jsonl", secondary)
            for route in evaluated:
                if route is not secondary:
                    route["_review"]["rejection_reasons"].append("duplicate_rejected")
                    logger.write_jsonl(
                        "06_rejections.jsonl",
                        {"id": key, "stage": "duplicate", "reasons": ["duplicate_rejected"]},
                    )
            continue
        if not primary and not secondary:
            for route in evaluated:
                for reason in route["validation"]["hard_failures"]:
                    rejection_counts[reason] = rejection_counts.get(reason, 0) + 1
                rejected_keys[key] = list(route["validation"]["hard_failures"])
            logger.write_jsonl(
                "06_rejections.jsonl",
                {"id": key, "stage": "validation", "reasons": route["validation"]["hard_failures"]},
            )
            continue

        primary_score = primary["quality"]["quality_score"]
        secondary_score = secondary["quality"]["quality_score"]
        gap = float(cfg.get("seed_quality_gap", 0.05))
        if abs(primary_score - secondary_score) >= gap:
            winner = primary if primary_score >= secondary_score else secondary
            loser = secondary if winner is primary else primary
            winner["_review"]["accepted"] = True
            selected.append(winner)
            logger.write_jsonl("07_validated.jsonl", winner)
            loser["_review"]["rejection_reasons"].append("duplicate_lower_quality")
            rejected_keys[key] = list(loser["_review"]["rejection_reasons"])
            logger.write_jsonl(
                "06_rejections.jsonl",
                {"id": key, "stage": "duplicate", "reasons": ["duplicate_lower_quality"]},
            )
            continue

        # Prefer GPX when quality is similar.
        if gpx:
            primary["_review"]["accepted"] = True
            selected.append(primary)
            logger.write_jsonl("07_validated.jsonl", primary)
            if secondary is not None and secondary is not primary:
                secondary["_review"]["rejection_reasons"].append("duplicate_prefer_gpx")
                rejected_keys[key] = list(secondary["_review"]["rejection_reasons"])
            logger.write_jsonl(
                "06_rejections.jsonl",
                {"id": key, "stage": "duplicate", "reasons": ["duplicate_prefer_gpx"]},
            )
        else:
            secondary["_review"]["accepted"] = True
            selected.append(secondary)
            logger.write_jsonl("07_validated.jsonl", secondary)
            if primary is not None and primary is not secondary:
                primary["_review"]["rejection_reasons"].append("duplicate_prefer_kml")
                rejected_keys[key] = list(primary["_review"]["rejection_reasons"])
            logger.write_jsonl(
                "06_rejections.jsonl",
                {"id": key, "stage": "duplicate", "reasons": ["duplicate_prefer_kml"]},
            )

    logger.info(f"Accepted {len(selected)} seed routes.")
    logger.write_json("08_scored.json", selected)
    logger.write_json("seed_validation_review.json", review_entries)

    start_values = [entry["start_distance_m"] for entry in review_entries]
    end_values = [entry["end_distance_m"] for entry in review_entries]
    if start_values and end_values:
        start_avg = sum(start_values) / len(start_values)
        end_avg = sum(end_values) / len(end_values)
        logger.write_json(
            "seed_start_end_summary.json",
            {
                "start_min_m": min(start_values),
                "start_max_m": max(start_values),
                "start_avg_m": start_avg,
                "end_min_m": min(end_values),
                "end_max_m": max(end_values),
                "end_avg_m": end_avg,
            },
        )

    logger.stage("export")
    output_routes = build_output_routes(centre, selected)
    accepted_keys = sorted({route["route_key"] for route in selected})
    rejected_key_list = sorted(set(rejected_keys.keys()) - set(accepted_keys))
    for key in rejected_key_list:
        reasons = rejected_keys.get(key, [])
        logger.warn(f"Seed route {key} rejected as unrealistic: {', '.join(reasons)}")
    pack = build_routes_pack(centre, output_routes)
    (output_dir / "routes.json").write_text(json.dumps(pack, indent=2, ensure_ascii=True), encoding="utf-8")
    (output_dir / "quality_scores.json").write_text(
        json.dumps(
            {
                "centre_id": centre["centre_id"],
                "routes": [
                    {
                        "id": route["route_key"],
                        "quality": route["quality"],
                        "source_format": route["source_format"],
                        "source_file": route["source_file"],
                    }
                    for route in selected
                ],
            },
            indent=2,
            ensure_ascii=True,
        ),
        encoding="utf-8",
    )
    filtered_rejected_reasons = {key: rejected_keys[key] for key in rejected_key_list}
    (output_dir / "validation_report.json").write_text(
        json.dumps(
            {
                "centre_id": centre["centre_id"],
                "total_seed_files": len(seed_files),
                "accepted_routes": len(selected),
                "rejection_counts": rejection_counts,
                "accepted_route_keys": accepted_keys,
                "rejected_route_keys": rejected_key_list,
                "rejected_route_reasons": filtered_rejected_reasons,
            },
            indent=2,
            ensure_ascii=True,
        ),
        encoding="utf-8",
    )
    _export_route_artifacts(output_dir, centre_slug, output_routes, logger)
    logger.info(f"Wrote {len(selected)} seed routes to {output_dir}")
    return 0


def _run_graph_generation_from_roads(
    routegen_dir: Path,
    centre_slug: str,
    centre: Dict,
    cfg: Dict,
    logger,
    use_overpass: bool,
    radius: float,
    *,
    raw_roads: List[str],
    source_format: str,
    source_file: str,
) -> int:
    from lib.candidates import generate_candidates
    from lib.graph import dead_end_nodes
    from lib.osm import load_graph
    from lib.normalize import normalize_for_match
    from lib.road_match import match_roads
    from lib.route_selection import select_route_set
    from lib.validate import early_reject, validate_route

    import osmnx as ox
    import time
    from datetime import datetime

    logger.stage("load centre")
    logger.info(f"Centre: {centre['centre_name']} ({centre['centre_id']}) radius={radius}m")
    logger.write_json("01_raw_roads.json", raw_roads)

    logger.stage("clean hint roads")
    cleaned = [clean_road_name(name) for name in raw_roads]
    cleaned = [name for name in cleaned if name]
    cleaned = dedupe_names(cleaned)
    logger.info(f"Cleaned to {len(cleaned)} unique road names.")
    logger.write_json("02_clean_roads.json", cleaned)

    logger.stage("load graph")
    osm_dir = routegen_dir / "inputs" / "osm"
    graph = load_graph(
        centre_lat=centre["centre_lat"],
        centre_lng=centre["centre_lng"],
        radius_meters=radius,
        osm_dir=osm_dir,
        use_overpass=use_overpass,
        logger=logger,
        cfg=cfg,
    )
    centre_node = ox.distance.nearest_nodes(graph, centre["centre_lng"], centre["centre_lat"])
    logger.info(f"Graph loaded. nodes={graph.number_of_nodes()} edges={graph.number_of_edges()}")
    logger.write_json(
        "04_graph.json",
        {
            "nodes": graph.number_of_nodes(),
            "edges": graph.number_of_edges(),
            "centre_node": int(centre_node),
        },
    )

    logger.stage("match roads")
    matched = match_roads(cleaned, graph, centre["centre_lat"], centre["centre_lng"], radius, logger=logger)
    matched_names = [getattr(road, "matched_name", None) or road.name for road in matched]
    logger.info(f"Matched {len(matched)} roads to OSM network.")
    logger.write_json(
        "03_matched_roads.json",
        [road.__dict__ for road in matched],
    )

    logger.stage("generate candidates")
    candidates = generate_candidates(graph, centre_node, matched, cfg, logger)
    for candidate in candidates:
        candidate["geometry"] = _anchor_geometry_to_centre(
            candidate.get("geometry", []),
            centre["centre_lat"],
            centre["centre_lng"],
        )
    logger.write_json("05_candidates.json", candidates)

    logger.stage("early rejection + validation")
    validated = []
    rejection_counts: Dict[str, int] = {}
    rejected_total = 0
    total_candidates = len(candidates)
    start_ts = datetime.now().isoformat(timespec="seconds")
    start_time = time.monotonic()
    dead_ends = dead_end_nodes(graph)
    matched_set = {normalize_for_match(name) for name in matched_names if name}
    logger.info(
        f"Validation start {start_ts}. candidates={total_candidates} accepted=0 rejected=0"
    )
    for idx, candidate in enumerate(candidates, start=1):
        early_reasons = early_reject(
            candidate,
            centre_node,
            cfg,
            graph,
            matched_names,
            centre_lat=centre["centre_lat"],
            centre_lng=centre["centre_lng"],
            dead_ends=dead_ends,
            matched_set=matched_set,
        )
        if early_reasons:
            for reason in early_reasons:
                rejection_counts[reason] = rejection_counts.get(reason, 0) + 1
            logger.write_jsonl(
                "06_rejections.jsonl",
                {"id": candidate["id"], "stage": "early", "reasons": early_reasons},
            )
            rejected_total += 1
            if idx % 10 == 0 or idx == total_candidates:
                elapsed = time.monotonic() - start_time
                logger.info(
                    f"Validation progress {idx}/{total_candidates} "
                    f"id={candidate['id']} elapsed_s={elapsed:.1f} "
                    f"accepted={len(validated)} rejected={rejected_total}"
                )
            continue

        validation = validate_route(
            candidate,
            centre_lat=centre["centre_lat"],
            centre_lng=centre["centre_lng"],
            centre_node=centre_node,
            cfg=cfg,
            graph=graph,
            matched_names=matched_names,
            dead_ends=dead_ends,
            matched_set=matched_set,
        )
        if not validation["passed"]:
            for reason in validation["hard_failures"]:
                rejection_counts[reason] = rejection_counts.get(reason, 0) + 1
            logger.write_jsonl(
                "06_rejections.jsonl",
                {"id": candidate["id"], "stage": "validation", "reasons": validation["hard_failures"]},
            )
            rejected_total += 1
            if idx % 10 == 0 or idx == total_candidates:
                elapsed = time.monotonic() - start_time
                logger.info(
                    f"Validation progress {idx}/{total_candidates} "
                    f"id={candidate['id']} elapsed_s={elapsed:.1f} "
                    f"accepted={len(validated)} rejected={rejected_total}"
                )
            continue

        candidate["validation"] = validation
        candidate["source_file"] = source_file
        candidate["source_format"] = source_format
        validated.append(candidate)
        logger.write_jsonl("07_validated.jsonl", candidate)
        if idx % 10 == 0 or idx == total_candidates:
            elapsed = time.monotonic() - start_time
            logger.info(
                f"Validation progress {idx}/{total_candidates} "
                f"id={candidate['id']} elapsed_s={elapsed:.1f} "
                f"accepted={len(validated)} rejected={rejected_total}"
            )

    logger.info(f"Validated {len(validated)} routes.")

    logger.stage("overlap + scoring")
    overlap = compute_max_overlaps(validated)
    for route in validated:
        route_overlap_ratio = overlap.get(route["id"], 0.0)
        route["overlap_ratio"] = route_overlap_ratio
        roads_used_clean = dedupe_names([clean_road_name(r) for r in route["roads_used"] if r])
        route["roads_used_clean"] = roads_used_clean
        route["hint_roads_used"] = route["validation"]["metrics"].get(
            "hint_roads_used",
            route["validation"]["metrics"].get("pdf_roads_used", 0),
        )
        inferred_shape = describe_route_shape(route, centre["centre_lat"], centre["centre_lng"], cfg)
        generated_family = route.get("family")
        if generated_family:
            inferred_shape["generation_family"] = generated_family
            inferred_shape["family"] = generated_family
        generated_zone_keys = list(route.get("zone_keys", []) or [])
        if generated_zone_keys:
            inferred_shape["zone_keys"] = list(
                dict.fromkeys(generated_zone_keys + list(inferred_shape.get("zone_keys", [])))
            )
        route["shape"] = inferred_shape
        route["quality"] = score_route(route, route["validation"], route_overlap_ratio, cfg)

    logger.write_json("08_scored.json", validated)

    validated.sort(key=lambda r: r["quality"]["quality_score"], reverse=True)
    selection = select_route_set(validated, cfg, matched_names, logger=logger)
    selected = selection["routes"]
    logger.write_json("09_selected.json", selection)

    logger.stage("export")
    output_dir = ensure_dir(routegen_dir / "output" / centre_slug)
    output_routes = build_output_routes(centre, selected)
    pack = build_routes_pack(centre, output_routes)
    (output_dir / "routes.json").write_text(json.dumps(pack, indent=2, ensure_ascii=True), encoding="utf-8")

    (output_dir / "quality_scores.json").write_text(
        json.dumps(
            {
                "centre_id": centre["centre_id"],
                "routes": [
                    {
                        "id": route["id"],
                        "quality": route["quality"],
                        "overlap_ratio": route.get("overlap_ratio", 0.0),
                        "selection": route.get("selection", {}),
                    }
                    for route in selected
                ],
            },
            indent=2,
            ensure_ascii=True,
        ),
        encoding="utf-8",
    )

    (output_dir / "validation_report.json").write_text(
        json.dumps(
            {
                "centre_id": centre["centre_id"],
                "candidates": len(candidates),
                "validated": len(validated),
                "selected": len(selected),
                "matched_hint_roads": len(matched_names),
                "hint_coverage": {
                    "total": selection["hint_roads_total"],
                    "covered": selection["hint_roads_covered"],
                    "ratio": selection["hint_coverage_ratio"],
                    "uncovered": selection["uncovered_hint_roads"],
                },
                "route_shape": {
                    "family_counts": selection.get("route_family_counts", {}),
                    "zones": selection.get("route_zones_covered", []),
                },
                "rejection_counts": rejection_counts,
            },
            indent=2,
            ensure_ascii=True,
        ),
        encoding="utf-8",
    )

    _export_route_artifacts(output_dir, centre_slug, output_routes, logger)
    logger.info(f"Wrote {len(selected)} routes to {output_dir}")
    return 0


def run_pdf_generation(
    routegen_dir: Path,
    centre_slug: str,
    centre: Dict,
    cfg: Dict,
    logger,
    use_overpass: bool,
    radius: float,
) -> int:
    from lib.pdf_extract import extract_road_candidates, read_pdf_text

    centre_dir = routegen_dir / "inputs" / "centres" / centre_slug

    logger.stage("read pdf")
    pdf_path = centre_dir / centre["source_pdf"]
    text = read_pdf_text(pdf_path)
    raw_roads = extract_road_candidates(text)
    logger.info(f"Extracted {len(raw_roads)} raw road candidates.")
    return _run_graph_generation_from_roads(
        routegen_dir,
        centre_slug,
        centre,
        cfg,
        logger,
        use_overpass,
        radius,
        raw_roads=raw_roads,
        source_format="pdf",
        source_file=centre["source_pdf"],
    )


def run_hint_generation(
    routegen_dir: Path,
    centre_slug: str,
    centre: Dict,
    cfg: Dict,
    logger,
    use_overpass: bool,
    radius: float,
) -> int:
    from lib.hints import load_hint_bundle

    centre_dir = routegen_dir / "inputs" / "centres" / centre_slug

    logger.stage("read hints")
    hint_bundle = load_hint_bundle(centre_dir, logger=logger)
    logger.info(f"Imported {len(hint_bundle['raw_roads'])} raw hint rows from structured hints.")
    logger.info(f"Expanded structured hints to {len(hint_bundle['roads'])} cleaned road names.")
    return _run_graph_generation_from_roads(
        routegen_dir,
        centre_slug,
        centre,
        cfg,
        logger,
        use_overpass,
        radius,
        raw_roads=hint_bundle["roads"],
        source_format="hints",
        source_file=Path(hint_bundle["path"]).name,
    )


def run_centre(centre_slug: str, use_overpass: bool, input_mode: str) -> int:
    routegen_dir = ROUTEGEN_DIR
    cfg = load_config(routegen_dir)
    work_dir = ensure_dir(routegen_dir / "work" / centre_slug)
    output_dir = ensure_dir(routegen_dir / "output" / centre_slug)
    logger = PipelineLogger(work_dir=work_dir, centre_slug=centre_slug, reset=True)
    for filename in ("06_rejections.jsonl", "07_validated.jsonl"):
        path = work_dir / filename
        if path.exists():
            path.unlink()

    logger.stage("preflight checks")
    preflight = preflight_checks(routegen_dir, centre_slug, use_overpass, logger, input_mode)
    if preflight["errors"]:
        return 1

    centre = preflight["centre"]
    centre["source_pdf"] = centre.get("source_pdf", "source.pdf")
    radius = preflight["radius"] or float(cfg.get("centre_default_radius_meters", 4000))
    logger.stage("centre verification")
    verification_report = verify_centre(routegen_dir, centre_slug, centre, cfg, logger)
    _write_centre_verification(output_dir, logger, verification_report)
    logger.info(
        "Centre anchor resolved via "
        f"{verification_report['resolved']['source']} "
        f"({verification_report['resolved']['lat']:.7f}, {verification_report['resolved']['lng']:.7f})"
    )

    if input_mode == "seed":
        return run_seed_import(routegen_dir, centre_slug, centre, cfg, logger)

    if input_mode == "hints":
        return run_hint_generation(routegen_dir, centre_slug, centre, cfg, logger, use_overpass, radius)

    return run_pdf_generation(routegen_dir, centre_slug, centre, cfg, logger, use_overpass, radius)


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate practice routes for a single centre.")
    parser.add_argument("--centre", required=True, help="Centre slug (e.g. colchester)")
    parser.add_argument(
        "--input-mode",
        choices=["pdf", "seed", "hints"],
        default="pdf",
        help="Input mode: pdf (default), seed, or hints",
    )
    parser.add_argument(
        "--use-overpass",
        action="store_true",
        help="Fetch OSM data via Overpass if no local extract is available.",
    )
    parser.add_argument(
        "--preflight-only",
        action="store_true",
        help="Run preflight checks only and exit.",
    )
    args = parser.parse_args()
    if args.preflight_only:
        work_dir = ensure_dir(ROUTEGEN_DIR / "work" / args.centre)
        logger = PipelineLogger(work_dir=work_dir, centre_slug=args.centre, reset=True)
        logger.stage("preflight checks")
        preflight = preflight_checks(ROUTEGEN_DIR, args.centre, args.use_overpass, logger, args.input_mode)
        return 0 if not preflight["errors"] else 1
    return run_centre(args.centre, args.use_overpass, args.input_mode)


if __name__ == "__main__":
    raise SystemExit(main())

"""Seed route import utilities."""

from __future__ import annotations

import re
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Dict, List, Optional, Tuple

from .geo import haversine_meters, bearing_degrees, turn_angle_deg


def extract_route_key(filename: str) -> Optional[str]:
    match = re.search(r"route[_ -]*(\\d{1,2})", filename, re.IGNORECASE)
    if not match:
        return None
    number = int(match.group(1))
    return f"route_{number:02d}"


def list_seed_files(seeds_dir: Path) -> List[Path]:
    files = []
    for suffix in (".gpx", ".kml"):
        files.extend(sorted(seeds_dir.glob(f"*{suffix}")))
    return files


def parse_gpx(path: Path) -> List[Tuple[float, float]]:
    tree = ET.parse(path)
    root = tree.getroot()
    coords: List[Tuple[float, float]] = []
    for elem in root.iter():
        if elem.tag.lower().endswith("trkpt"):
            lat = elem.attrib.get("lat")
            lon = elem.attrib.get("lon")
            if lat is None or lon is None:
                continue
            coords.append((float(lat), float(lon)))
    return coords


def parse_kml(path: Path) -> List[Tuple[float, float]]:
    tree = ET.parse(path)
    root = tree.getroot()
    coords: List[Tuple[float, float]] = []
    for elem in root.iter():
        if not elem.tag.lower().endswith("coordinates"):
            continue
        if not elem.text:
            continue
        raw = elem.text.strip()
        if not raw:
            continue
        for chunk in raw.split():
            parts = chunk.split(",")
            if len(parts) < 2:
                continue
            lon = float(parts[0])
            lat = float(parts[1])
            coords.append((lat, lon))
    return coords


def estimate_distance_m(coords: List[Tuple[float, float]]) -> float:
    if len(coords) < 2:
        return 0.0
    distance = 0.0
    for (lat1, lon1), (lat2, lon2) in zip(coords[:-1], coords[1:]):
        distance += haversine_meters(lat1, lon1, lat2, lon2)
    return distance


def estimate_duration_s(distance_m: float, avg_speed_kph: float) -> float:
    if distance_m <= 0 or avg_speed_kph <= 0:
        return 0.0
    speed_mps = avg_speed_kph * 1000.0 / 3600.0
    return distance_m / speed_mps


def downsample_coords(coords: List[Tuple[float, float]], min_spacing_m: float) -> List[Tuple[float, float]]:
    if not coords:
        return []
    sampled = [coords[0]]
    last = coords[0]
    for point in coords[1:]:
        if haversine_meters(last[0], last[1], point[0], point[1]) >= min_spacing_m:
            sampled.append(point)
            last = point
    if sampled[-1] != coords[-1]:
        sampled.append(coords[-1])
    return sampled


def detect_out_and_back(
    coords: List[Tuple[float, float]],
    min_spacing_m: float,
    min_segment_m: float,
    angle_threshold_deg: float,
    backtrack_ratio: float,
    backtrack_distance_m: float,
) -> Dict:
    simplified = downsample_coords(coords, min_spacing_m)
    hits = []
    for i in range(1, len(simplified) - 1):
        lat1, lon1 = simplified[i - 1]
        lat2, lon2 = simplified[i]
        lat3, lon3 = simplified[i + 1]
        seg1 = haversine_meters(lat1, lon1, lat2, lon2)
        seg2 = haversine_meters(lat2, lon2, lat3, lon3)
        if seg1 < min_segment_m or seg2 < min_segment_m:
            continue
        bearing_in = bearing_degrees(lat1, lon1, lat2, lon2)
        bearing_out = bearing_degrees(lat2, lon2, lat3, lon3)
        angle = turn_angle_deg(bearing_in, bearing_out)
        if angle < angle_threshold_deg:
            continue
        direct = haversine_meters(lat1, lon1, lat3, lon3)
        ratio = direct / max(seg1, seg2)
        if direct <= backtrack_distance_m or ratio <= backtrack_ratio:
            hits.append(
                {
                    "index": i,
                    "angle_deg": angle,
                    "direct_m": direct,
                    "ratio": ratio,
                    "seg1_m": seg1,
                    "seg2_m": seg2,
                }
            )
    return {"hits": hits, "sampled_points": len(simplified)}


def self_overlap_ratio(coords: List[Tuple[float, float]], precision: int = 5) -> float:
    if len(coords) < 3:
        return 0.0
    segments = []
    for (lat1, lon1), (lat2, lon2) in zip(coords[:-1], coords[1:]):
        a = (round(lat1, precision), round(lon1, precision))
        b = (round(lat2, precision), round(lon2, precision))
        segments.append((a, b))
    if not segments:
        return 0.0
    seen = set()
    duplicates = 0
    for seg in segments:
        rev = (seg[1], seg[0])
        if seg in seen or rev in seen:
            duplicates += 1
        seen.add(seg)
    return duplicates / max(len(segments), 1)


def build_seed_route(path: Path, avg_speed_kph: float) -> Dict:
    ext = path.suffix.lower().lstrip(".")
    if ext == "gpx":
        coords = parse_gpx(path)
    elif ext == "kml":
        coords = parse_kml(path)
    else:
        coords = []

    distance_m = estimate_distance_m(coords)
    duration_s = estimate_duration_s(distance_m, avg_speed_kph)
    return {
        "route_key": extract_route_key(path.stem) or path.stem,
        "source_file": path.name,
        "source_format": ext,
        "geometry": coords,
        "distance_m": distance_m,
        "duration_s": duration_s,
        "point_count": len(coords),
    }


def validate_seed_route(
    route: Dict,
    centre_lat: float,
    centre_lng: float,
    cfg: Dict,
    anchor_lat: Optional[float] = None,
    anchor_lng: Optional[float] = None,
) -> Dict:
    failures: List[str] = []
    warnings: List[str] = []
    metrics: Dict[str, float] = {}
    outlier_reason: Optional[str] = None

    coords = route["geometry"]
    min_points = int(cfg.get("seed_min_points", 100))
    if len(coords) < min_points:
        failures.append("insufficient_points")
        return {"passed": False, "hard_failures": failures, "warnings": warnings, "metrics": metrics}

    tolerance = float(cfg.get("seed_start_end_tolerance_meters", cfg.get("start_end_tolerance_meters", 40)))
    max_duration = float(cfg.get("max_duration_seconds", 2100))
    self_overlap_max = float(cfg.get("seed_self_overlap_max", 0.45))
    duration_multiplier = float(cfg.get("seed_duration_reject_multiplier", 1.35))

    start_lat, start_lon = coords[0]
    end_lat, end_lon = coords[-1]
    anchor_lat = centre_lat if anchor_lat is None else anchor_lat
    anchor_lng = centre_lng if anchor_lng is None else anchor_lng
    start_dist = haversine_meters(start_lat, start_lon, anchor_lat, anchor_lng)
    end_dist = haversine_meters(end_lat, end_lon, anchor_lat, anchor_lng)
    metrics["anchor_lat"] = anchor_lat
    metrics["anchor_lng"] = anchor_lng
    metrics["anchor_override_used"] = (anchor_lat, anchor_lng) != (centre_lat, centre_lng)

    metrics["start_distance_m"] = start_dist
    metrics["end_distance_m"] = end_dist
    if start_dist > tolerance or end_dist > tolerance:
        failures.append("start_end_not_at_centre")

    distance_miles = route["distance_m"] / 1609.344
    metrics["distance_miles"] = distance_miles
    metrics["estimated_duration_s"] = route["duration_s"]
    metrics["estimated_duration_min"] = route["duration_s"] / 60.0
    hard_limit = max_duration * duration_multiplier
    if route["duration_s"] > hard_limit:
        failures.append("duration_exceeds_limit")
    elif route["duration_s"] > max_duration:
        warnings.append("duration_estimate_over_limit")

    if distance_miles > 10:
        failures.append("distance_outlier")
        outlier_reason = "distance_miles_gt_10"

    reversal_info = detect_out_and_back(
        coords=coords,
        min_spacing_m=float(cfg.get("seed_downsample_spacing_m", 10)),
        min_segment_m=float(cfg.get("seed_reversal_min_segment_m", 12)),
        angle_threshold_deg=float(cfg.get("seed_reversal_angle_deg", 170)),
        backtrack_ratio=float(cfg.get("seed_reversal_backtrack_ratio", 0.2)),
        backtrack_distance_m=float(cfg.get("seed_reversal_backtrack_distance_m", 8)),
    )
    hits = reversal_info["hits"]
    metrics["reversal_hits"] = len(hits)
    metrics["reversal_sampled_points"] = reversal_info["sampled_points"]
    if hits:
        metrics["reversal_worst_ratio"] = min(hit["ratio"] for hit in hits)
    if len(hits) >= int(cfg.get("seed_reversal_min_hits", 2)):
        failures.append("immediate_segment_reversal")
    elif hits:
        warnings.append("minor_backtrack_detected")

    overlap_ratio = self_overlap_ratio(coords)
    metrics["self_overlap_ratio"] = overlap_ratio
    if overlap_ratio > self_overlap_max:
        failures.append("extreme_self_overlap")

    if overlap_ratio > 0.3:
        warnings.append("route_backtracking_detected")

    return {
        "passed": len(failures) == 0,
        "hard_failures": failures,
        "warnings": warnings,
        "metrics": metrics,
        "outlier_reason": outlier_reason,
    }


def seed_quality_score(route: Dict, validation: Dict, cfg: Dict) -> Dict:
    max_duration = float(cfg.get("max_duration_seconds", 2100))
    target_duration = max_duration * 0.85
    duration = float(route.get("duration_s", 0.0))
    duration_fit = max(0.0, 1.0 - abs(duration - target_duration) / max_duration)

    overlap_ratio = float(validation.get("metrics", {}).get("self_overlap_ratio", 0.0))
    overlap_score = max(0.0, 1.0 - overlap_ratio)

    loop_integrity = 1.0 if validation.get("passed") else 0.0
    score = 0.5 * loop_integrity + 0.3 * duration_fit + 0.2 * overlap_score

    return {
        "quality_score": round(score, 6),
        "duration_fit": duration_fit,
        "overlap_score": overlap_score,
        "loop_integrity": loop_integrity,
    }

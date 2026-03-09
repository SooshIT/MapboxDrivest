"""Zone and shape helpers for route generation."""

from __future__ import annotations

from collections import Counter
from typing import Dict, Iterable, List, Sequence

from .geo import bearing_degrees, haversine_meters

SECTOR_NAMES = ("n", "ne", "e", "se", "s", "sw", "w", "nw")


def bearing_gap_deg(a: float, b: float) -> float:
    delta = abs(a - b) % 360.0
    return delta if delta <= 180.0 else 360.0 - delta


def sector_index_from_bearing(bearing: float, sector_count: int = 8) -> int:
    normalized = bearing % 360.0
    return int((normalized + (180.0 / sector_count)) // (360.0 / sector_count)) % sector_count


def sector_name_from_bearing(bearing: float, sector_count: int = 8) -> str:
    index = sector_index_from_bearing(bearing, sector_count)
    if sector_count == 8:
        return SECTOR_NAMES[index]
    return f"s{index:02d}"


def distance_band(distance_m: float, cfg: Dict) -> str:
    local_threshold = float(cfg.get("candidate_zone_local_distance_m", 900.0))
    inner_threshold = float(cfg.get("candidate_zone_inner_distance_m", 1600.0))
    outer_threshold = float(cfg.get("candidate_zone_outer_distance_m", 2800.0))
    if distance_m < local_threshold:
        return "local"
    if distance_m < inner_threshold:
        return "inner"
    if distance_m < outer_threshold:
        return "outer"
    return "far"


def _family_from_components(
    *,
    max_distance_m: float,
    bands: Sequence[str],
    sector_indices: Sequence[int],
    corridor_gap_deg_value: float,
    cfg: Dict,
) -> str:
    inner_threshold = float(cfg.get("candidate_zone_inner_distance_m", 1600.0))
    outer_threshold = float(cfg.get("candidate_zone_outer_distance_m", 2800.0))
    unique_sectors = sorted(set(sector_indices))
    if not unique_sectors:
        return "compact_local"

    if max_distance_m < inner_threshold * 1.1 and "outer" not in bands and "far" not in bands:
        return "compact_local"

    if len(unique_sectors) == 1:
        return "single_lobe"

    sector_count = int(cfg.get("candidate_zone_sector_count", 8))
    sector_span_deg = 0.0
    for i, first in enumerate(unique_sectors):
        for second in unique_sectors[i + 1 :]:
            step_gap = min((second - first) % sector_count, (first - second) % sector_count)
            sector_span_deg = max(sector_span_deg, step_gap * (360.0 / sector_count))

    outerish = sum(1 for band in bands if band in {"outer", "far"})
    if len(unique_sectors) >= 3 and corridor_gap_deg_value >= 70.0:
        return "cross_town"
    if sector_span_deg >= 135.0 and outerish >= 1:
        return "two_lobe" if len(unique_sectors) <= 2 else "cross_town"
    if corridor_gap_deg_value >= 55.0 or max_distance_m >= outer_threshold:
        return "adjacent_arc"
    return "single_lobe"


def classify_zone_sequence(zones: Sequence[Dict], cfg: Dict) -> Dict:
    if not zones:
        return {"family": "compact_local", "zone_keys": []}

    sector_indices = [int(zone.get("sector_index", 0)) for zone in zones]
    bands = [str(zone.get("band", "local")) for zone in zones]
    max_distance_m = max(float(zone.get("distance_m", 0.0)) for zone in zones)
    ordered = sorted(zones, key=lambda zone: float(zone.get("distance_m", 0.0)))
    corridor_gap = 0.0
    if len(ordered) >= 2:
        corridor_gap = bearing_gap_deg(
            float(ordered[0].get("bearing", 0.0)),
            float(ordered[-1].get("bearing", 0.0)),
        )

    family = _family_from_components(
        max_distance_m=max_distance_m,
        bands=bands,
        sector_indices=sector_indices,
        corridor_gap_deg_value=corridor_gap,
        cfg=cfg,
    )
    zone_keys = list(dict.fromkeys(str(zone.get("zone_key", "")) for zone in zones if zone.get("zone_key")))
    return {
        "family": family,
        "zone_keys": zone_keys,
        "corridor_gap_deg": round(corridor_gap, 3),
        "max_distance_m": round(max_distance_m, 3),
    }


def build_target_zones(target_pool: Sequence[Dict], cfg: Dict) -> List[Dict]:
    sector_count = int(cfg.get("candidate_zone_sector_count", 8))
    zones: Dict[str, Dict] = {}

    for target in target_pool:
        bearing = float(target.get("bearing", 0.0))
        distance_m = float(target.get("distance_m", 0.0))
        band = distance_band(distance_m, cfg)
        sector_index = sector_index_from_bearing(bearing, sector_count)
        sector_name = sector_name_from_bearing(bearing, sector_count)
        zone_key = f"{band}:{sector_name}"

        zone = zones.setdefault(
            zone_key,
            {
                "zone_key": zone_key,
                "band": band,
                "sector_index": sector_index,
                "sector_name": sector_name,
                "targets": [],
                "target_names": [],
                "edges": [],
                "distance_sum": 0.0,
                "bearing_sum": 0.0,
                "confidence_sum": 0.0,
            },
        )
        zone["targets"].append(target)
        zone["target_names"].append(str(target.get("name", "")))
        zone["edges"].extend(list(target.get("edges", []))[:4])
        zone["distance_sum"] += distance_m
        zone["bearing_sum"] += bearing
        zone["confidence_sum"] += float(target.get("confidence", 0.0))

    output: List[Dict] = []
    for zone in zones.values():
        target_count = max(len(zone["targets"]), 1)
        edges: List[Dict] = []
        seen_edges = set()
        for edge in zone["edges"]:
            edge_key = (edge.get("u"), edge.get("v"), edge.get("key"))
            if edge_key in seen_edges:
                continue
            seen_edges.add(edge_key)
            edges.append(edge)

        zone_distance = zone["distance_sum"] / target_count
        zone_bearing = zone["bearing_sum"] / target_count
        zone_confidence = zone["confidence_sum"] / target_count
        output.append(
            {
                "zone_key": zone["zone_key"],
                "band": zone["band"],
                "sector_index": zone["sector_index"],
                "sector_name": zone["sector_name"],
                "target_count": target_count,
                "bearing": zone_bearing,
                "distance_m": zone_distance,
                "confidence": zone_confidence,
                "names": list(dict.fromkeys(name for name in zone["target_names"] if name)),
                "edges": edges[:14],
            }
        )

    output.sort(
        key=lambda zone: (
            float(zone.get("distance_m", 0.0)),
            float(zone.get("confidence", 0.0)),
            int(zone.get("target_count", 0)),
        ),
        reverse=True,
    )
    return output


def _bearing_from_first_radius(
    coords: Sequence[tuple[float, float]],
    centre_lat: float,
    centre_lng: float,
    radius_m: float,
) -> float | None:
    for lat, lng in coords:
        if haversine_meters(centre_lat, centre_lng, lat, lng) >= radius_m:
            return bearing_degrees(centre_lat, centre_lng, lat, lng)
    return None


def _bearing_from_last_radius(
    coords: Sequence[tuple[float, float]],
    centre_lat: float,
    centre_lng: float,
    radius_m: float,
) -> float | None:
    for lat, lng in reversed(coords):
        if haversine_meters(centre_lat, centre_lng, lat, lng) >= radius_m:
            return bearing_degrees(centre_lat, centre_lng, lat, lng)
    return None


def describe_route_shape(route: Dict, centre_lat: float, centre_lng: float, cfg: Dict) -> Dict:
    coords = list(route.get("geometry", []) or [])
    sector_count = int(cfg.get("candidate_zone_sector_count", 8))
    local_threshold = float(cfg.get("candidate_zone_local_distance_m", 900.0))
    corridor_radius = float(cfg.get("candidate_corridor_probe_distance_m", 180.0))

    samples = []
    for lat, lng in coords:
        distance_m = haversine_meters(centre_lat, centre_lng, lat, lng)
        bearing = bearing_degrees(centre_lat, centre_lng, lat, lng)
        samples.append((distance_m, bearing))

    distances = [distance_m for distance_m, _ in samples]
    max_radius_m = max(distances) if distances else 0.0
    mean_radius_m = sum(distances) / len(distances) if distances else 0.0

    active_samples = [(distance_m, bearing) for distance_m, bearing in samples if distance_m >= local_threshold]
    zone_counter: Counter[str] = Counter()
    sector_counter: Counter[str] = Counter()
    sector_indices: List[int] = []
    bands: List[str] = []
    for distance_m, bearing in active_samples:
        band = distance_band(distance_m, cfg)
        sector_name = sector_name_from_bearing(bearing, sector_count)
        zone_counter[f"{band}:{sector_name}"] += 1
        sector_counter[sector_name] += 1
        sector_indices.append(sector_index_from_bearing(bearing, sector_count))
        bands.append(band)

    zone_keys = [key for key, _ in zone_counter.most_common(4)]
    dominant_sectors = [key for key, _ in sector_counter.most_common(3)]

    departure_bearing = _bearing_from_first_radius(coords[1:-1], centre_lat, centre_lng, corridor_radius)
    return_bearing = _bearing_from_last_radius(coords[1:-1], centre_lat, centre_lng, corridor_radius)
    corridor_gap = 0.0
    if departure_bearing is not None and return_bearing is not None:
        corridor_gap = bearing_gap_deg(departure_bearing, return_bearing)

    family = _family_from_components(
        max_distance_m=max_radius_m,
        bands=bands,
        sector_indices=sector_indices,
        corridor_gap_deg_value=corridor_gap,
        cfg=cfg,
    )

    return {
        "family": family,
        "zone_keys": zone_keys,
        "dominant_sectors": dominant_sectors,
        "max_radius_m": round(max_radius_m, 3),
        "mean_radius_m": round(mean_radius_m, 3),
        "corridor_gap_deg": round(corridor_gap, 3),
        "active_zone_count": len(zone_keys),
    }

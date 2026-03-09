"""Route validation rules for Drivest practice routes."""

from __future__ import annotations

import bisect
import math
from typing import Dict, List, Tuple

from .geo import bearing_degrees, haversine_meters, turn_angle_deg
from .graph import dead_end_nodes
from .normalize import normalize_for_match
from .route_shape import describe_route_shape


def _is_roundabout(edge_data) -> bool:
    junction = edge_data.get("junction")
    if isinstance(junction, list):
        junction = junction[0]
    return str(junction).lower() in {"roundabout", "circular"}


def _edge_data(graph, u, v):
    data = graph.get_edge_data(u, v)
    if not data:
        return None
    key = next(iter(data.keys()))
    return data[key]


def _has_immediate_reversal(nodes: List[int]) -> bool:
    for i in range(1, len(nodes) - 1):
        if nodes[i - 1] == nodes[i + 1]:
            return True
    return False


def _contains_dead_end(nodes: List[int], dead_ends: set, centre_node: int) -> bool:
    for node in nodes[1:-1]:
        if node != centre_node and node in dead_ends:
            return True
    return False


def _centre_revisit_count(nodes: List[int], centre_node: int) -> int:
    return sum(1 for node in nodes[1:-1] if node == centre_node)


def _self_overlap_ratio(nodes: List[int]) -> float:
    edges = [
        (u, v) if u <= v else (v, u)
        for u, v in zip(nodes[:-1], nodes[1:])
        if u != v
    ]
    if not edges:
        return 0.0
    unique_edges = len(set(edges))
    return max(0.0, 1.0 - (unique_edges / len(edges)))


def _edge_length_m(graph, u: int, v: int) -> float:
    data = _edge_data(graph, u, v) or {}
    length = data.get("length")
    if isinstance(length, list):
        length = length[0] if length else 0.0
    if length:
        return float(length)
    return haversine_meters(
        graph.nodes[u]["y"],
        graph.nodes[u]["x"],
        graph.nodes[v]["y"],
        graph.nodes[v]["x"],
    )


def _cumulative_path_lengths(graph, nodes: List[int]) -> List[float]:
    cumulative = [0.0]
    for u, v in zip(nodes[:-1], nodes[1:]):
        cumulative.append(cumulative[-1] + _edge_length_m(graph, u, v))
    return cumulative


def _downsample_geometry(
    coords: List[Tuple[float, float]],
    spacing_m: float,
) -> List[Tuple[float, float]]:
    if not coords:
        return []
    if spacing_m <= 0:
        return list(coords)

    sampled = [coords[0]]
    last_lat, last_lng = coords[0]
    for lat, lng in coords[1:]:
        if haversine_meters(last_lat, last_lng, lat, lng) >= spacing_m:
            sampled.append((lat, lng))
            last_lat, last_lng = lat, lng
    if sampled[-1] != coords[-1]:
        sampled.append(coords[-1])
    return sampled


def _max_local_revisit_density(coords: List[Tuple[float, float]], cfg: Dict) -> int:
    if len(coords) < 3:
        return 0

    radius_m = float(cfg.get("braid_local_revisit_radius_meters", 60.0))
    min_index_gap = int(cfg.get("braid_local_revisit_index_gap", 10))
    sample_spacing_m = float(cfg.get("braid_local_revisit_sample_spacing_meters", 30.0))
    sampled = _downsample_geometry(coords, sample_spacing_m)
    if len(sampled) < 3:
        return 0

    lat0, lng0 = sampled[0]
    scale_x = 111320.0 * math.cos(math.radians(lat0))
    scale_y = 111320.0
    cell_size = max(radius_m, 1.0)
    radius_sq = radius_m * radius_m

    grid: Dict[Tuple[int, int], List[Tuple[int, float, float]]] = {}
    enriched: List[Tuple[int, float, float, Tuple[int, int]]] = []
    for idx, (lat, lng) in enumerate(sampled):
        x = (lng - lng0) * scale_x
        y = (lat - lat0) * scale_y
        cell = (math.floor(x / cell_size), math.floor(y / cell_size))
        grid.setdefault(cell, []).append((idx, x, y))
        enriched.append((idx, x, y, cell))

    best = 0
    for idx, x, y, (cx, cy) in enriched:
        local_count = 0
        for nx in range(cx - 1, cx + 2):
            for ny in range(cy - 1, cy + 2):
                for other_idx, ox, oy in grid.get((nx, ny), []):
                    if abs(idx - other_idx) < min_index_gap:
                        continue
                    dx = x - ox
                    dy = y - oy
                    if dx * dx + dy * dy <= radius_sq:
                        local_count += 1
        if local_count > best:
            best = local_count
    return best


def _non_centre_corridor_reentry_metrics(
    route: Dict,
    centre_lat: float,
    centre_lng: float,
    cfg: Dict,
) -> Dict[str, float | int | bool]:
    coords = list(route.get("geometry", []) or [])
    if len(coords) < 4:
        return {
            "detected": False,
            "revisit_points": 0,
            "revisit_pairs": 0,
            "max_density": 0,
        }

    proximity_m = float(cfg.get("corridor_reentry_proximity_meters", 45.0))
    centre_exemption_m = float(
        cfg.get("corridor_reentry_centre_exemption_radius_meters", 300.0)
    )
    min_index_gap = int(cfg.get("corridor_reentry_min_index_gap", 10))
    sample_spacing_m = float(cfg.get("corridor_reentry_sample_spacing_meters", 30.0))
    point_threshold = int(cfg.get("corridor_reentry_hit_threshold", 4))
    density_threshold = int(cfg.get("corridor_reentry_density_threshold", 2))
    sampled = _downsample_geometry(coords, sample_spacing_m)
    if len(sampled) < 4:
        return {
            "detected": False,
            "revisit_points": 0,
            "revisit_pairs": 0,
            "max_density": 0,
        }

    lat0, lng0 = sampled[0]
    scale_x = 111320.0 * math.cos(math.radians(lat0))
    scale_y = 111320.0
    cell_size = max(proximity_m, 1.0)
    radius_sq = proximity_m * proximity_m

    grid: Dict[Tuple[int, int], List[Tuple[int, float, float, bool]]] = {}
    enriched: List[Tuple[int, float, float, Tuple[int, int], bool]] = []
    for idx, (lat, lng) in enumerate(sampled):
        x = (lng - lng0) * scale_x
        y = (lat - lat0) * scale_y
        cell = (math.floor(x / cell_size), math.floor(y / cell_size))
        exempt = haversine_meters(lat, lng, centre_lat, centre_lng) <= centre_exemption_m
        grid.setdefault(cell, []).append((idx, x, y, exempt))
        enriched.append((idx, x, y, cell, exempt))

    revisit_points = 0
    revisit_pairs = 0
    max_density = 0
    for idx, x, y, (cx, cy), exempt in enriched:
        if exempt:
            continue
        local_count = 0
        for nx in range(cx - 1, cx + 2):
            for ny in range(cy - 1, cy + 2):
                for other_idx, ox, oy, other_exempt in grid.get((nx, ny), []):
                    if other_exempt or abs(idx - other_idx) < min_index_gap:
                        continue
                    dx = x - ox
                    dy = y - oy
                    if dx * dx + dy * dy <= radius_sq:
                        local_count += 1
        if local_count > 0:
            revisit_points += 1
            revisit_pairs += local_count
            if local_count > max_density:
                max_density = local_count

    detected = revisit_points >= point_threshold or max_density >= density_threshold
    return {
        "detected": detected,
        "revisit_points": revisit_points,
        "revisit_pairs": revisit_pairs,
        "max_density": max_density,
    }


def _candidate_shape(route: Dict, centre_lat: float, centre_lng: float, cfg: Dict) -> Dict:
    shape = dict(route.get("shape") or describe_route_shape(route, centre_lat, centre_lng, cfg))
    generated_family = route.get("family")
    if generated_family:
        shape["generation_family"] = generated_family
        shape["family"] = generated_family
    generated_zone_keys = list(route.get("zone_keys", []) or [])
    if generated_zone_keys:
        shape["zone_keys"] = list(dict.fromkeys(generated_zone_keys + list(shape.get("zone_keys", []))))
    return shape


def _braided_local_loop_metrics(route: Dict, centre_lat: float, centre_lng: float, cfg: Dict) -> Dict[str, float | int | str | bool]:
    coords = list(route.get("geometry", []) or [])
    if len(coords) < 3:
        return {
            "detected": False,
            "local_revisit_density": 0,
            "corridor_gap_deg": 0.0,
            "active_zone_count": 0,
            "family": "unclassified",
        }

    shape = _candidate_shape(route, centre_lat, centre_lng, cfg)
    family = str(shape.get("family") or "unclassified")
    allowed_families = {
        str(name)
        for name in (cfg.get("braid_route_families") or ["cross_town", "adjacent_arc", "two_lobe"])
    }
    local_density = _max_local_revisit_density(coords, cfg)
    corridor_gap = float(shape.get("corridor_gap_deg", 0.0))
    active_zone_count = int(shape.get("active_zone_count", len(shape.get("zone_keys", []) or [])))
    local_density_threshold = int(cfg.get("braid_local_revisit_threshold", 5))
    corridor_gap_threshold = float(cfg.get("braid_corridor_gap_threshold_deg", 120.0))
    active_zone_threshold = int(cfg.get("braid_active_zone_threshold", 4))
    detected = (
        family in allowed_families
        and local_density >= local_density_threshold
        and corridor_gap >= corridor_gap_threshold
        and active_zone_count >= active_zone_threshold
    )
    return {
        "detected": detected,
        "local_revisit_density": local_density,
        "corridor_gap_deg": corridor_gap,
        "active_zone_count": active_zone_count,
        "family": family,
    }


def _windowed_uturn_hits(route: Dict, graph, cfg: Dict) -> List[Dict[str, float]]:
    nodes = route.get("nodes") or []
    if len(nodes) < 5:
        return []

    window_m = float(cfg.get("u_turn_window_meters", 50.0))
    min_leg_ratio = float(cfg.get("u_turn_window_min_leg_ratio", 0.8))
    min_leg_m = max(window_m * min_leg_ratio, 10.0)
    angle_threshold = float(cfg.get("u_turn_window_angle_deg", 155.0))
    backtrack_ratio = float(cfg.get("u_turn_window_backtrack_ratio", 0.45))
    direct_distance_m = float(cfg.get("u_turn_window_direct_meters", 18.0))
    dedupe_index_gap = int(cfg.get("u_turn_window_dedupe_node_gap", 3))

    cumulative = _cumulative_path_lengths(graph, nodes)
    raw_hits: List[Dict[str, float]] = []

    for pivot_idx in range(1, len(nodes) - 1):
        target_before = max(0.0, cumulative[pivot_idx] - window_m)
        target_after = min(cumulative[-1], cumulative[pivot_idx] + window_m)
        prev_idx = bisect.bisect_right(cumulative, target_before) - 1
        next_idx = bisect.bisect_left(cumulative, target_after)
        if prev_idx < 0 or next_idx >= len(nodes):
            continue
        if prev_idx >= pivot_idx or next_idx <= pivot_idx:
            continue

        before_m = cumulative[pivot_idx] - cumulative[prev_idx]
        after_m = cumulative[next_idx] - cumulative[pivot_idx]
        if before_m < min_leg_m or after_m < min_leg_m:
            continue

        a = nodes[prev_idx]
        b = nodes[pivot_idx]
        c = nodes[next_idx]
        bearing_in = bearing_degrees(graph.nodes[a]["y"], graph.nodes[a]["x"], graph.nodes[b]["y"], graph.nodes[b]["x"])
        bearing_out = bearing_degrees(graph.nodes[b]["y"], graph.nodes[b]["x"], graph.nodes[c]["y"], graph.nodes[c]["x"])
        angle = turn_angle_deg(bearing_in, bearing_out)
        if angle < angle_threshold:
            continue

        direct_m = haversine_meters(
            graph.nodes[a]["y"],
            graph.nodes[a]["x"],
            graph.nodes[c]["y"],
            graph.nodes[c]["x"],
        )
        ratio = direct_m / max(min(before_m, after_m), 1.0)
        if direct_m > direct_distance_m and ratio > backtrack_ratio:
            continue

        local_roundabout = False
        for u, v in zip(nodes[prev_idx:next_idx], nodes[prev_idx + 1 : next_idx + 1]):
            if _is_roundabout(_edge_data(graph, u, v) or {}):
                local_roundabout = True
                break
        if local_roundabout:
            continue

        raw_hits.append(
            {
                "pivot_index": float(pivot_idx),
                "angle_deg": angle,
                "direct_m": direct_m,
                "ratio": ratio,
                "before_m": before_m,
                "after_m": after_m,
            }
        )

    hits: List[Dict[str, float]] = []
    for hit in raw_hits:
        if hits and int(hit["pivot_index"] - hits[-1]["pivot_index"]) <= dedupe_index_gap:
            if hit["angle_deg"] > hits[-1]["angle_deg"]:
                hits[-1] = hit
            continue
        hits.append(hit)
    return hits


def early_reject(
    route: Dict,
    centre_node: int,
    cfg: Dict,
    graph,
    matched_names: List[str],
    *,
    centre_lat: float | None = None,
    centre_lng: float | None = None,
    dead_ends: set | None = None,
    matched_set: set | None = None,
) -> List[str]:
    reasons = []
    max_duration = float(cfg.get("max_duration_seconds", 2100))
    min_distance_miles = float(cfg.get("min_distance_miles", 6.0))
    max_distance_miles = float(cfg.get("max_distance_miles", 9.5))
    min_hint = int(
        cfg.get(
            "min_hint_roads_per_route_early",
            max(1, int(cfg.get("min_hint_roads_per_route", cfg.get("min_pdf_roads_per_route", 4))) - 2),
        )
    )
    if route["duration_s"] > max_duration:
        reasons.append("duration_exceeds_limit")
    distance_miles = float(route.get("distance_m", 0.0)) / 1609.344
    if distance_miles < min_distance_miles or distance_miles > max_distance_miles:
        reasons.append("distance_out_of_range")
    if route["nodes"][0] != centre_node or route["nodes"][-1] != centre_node:
        reasons.append("not_closed_loop")
    if _has_immediate_reversal(route["nodes"]):
        reasons.append("immediate_segment_reversal")
    if _centre_revisit_count(route["nodes"], centre_node) > 0:
        reasons.append("centre_revisit_detected")

    if dead_ends is None:
        dead_ends = dead_end_nodes(graph)
    if _contains_dead_end(route["nodes"], dead_ends, centre_node):
        reasons.append("dead_end_stub")

    if _windowed_uturn_hits(route, graph, cfg):
        reasons.append("u_turn_detected")

    self_overlap = _self_overlap_ratio(route["nodes"])
    if self_overlap > float(cfg.get("self_overlap_max_ratio", 0.25)):
        reasons.append("high_self_overlap")

    if centre_lat is not None and centre_lng is not None:
        braid_metrics = _braided_local_loop_metrics(route, centre_lat, centre_lng, cfg)
        if bool(braid_metrics.get("detected")):
            reasons.append("braided_local_loop_detected")
        corridor_metrics = _non_centre_corridor_reentry_metrics(route, centre_lat, centre_lng, cfg)
        if bool(corridor_metrics.get("detected")):
            reasons.append("non_centre_corridor_reentry")

    if matched_set is None:
        matched_set = {normalize_for_match(name) for name in matched_names if name}
    hint_count = len({normalize_for_match(name) for name in route["roads_used"] if name} & matched_set)
    if hint_count < min_hint:
        reasons.append("insufficient_hint_roads")

    return reasons


def validate_route(
    route: Dict,
    centre_lat: float,
    centre_lng: float,
    centre_node: int,
    cfg: Dict,
    graph,
    matched_names: List[str],
    *,
    dead_ends: set | None = None,
    matched_set: set | None = None,
) -> Dict:
    failures: List[str] = []
    warnings: List[str] = []
    metrics: Dict[str, float] = {}

    tolerance = float(cfg.get("start_end_tolerance_meters", 40))
    max_duration = float(cfg.get("max_duration_seconds", 2100))
    min_distance_miles = float(cfg.get("min_distance_miles", 6.0))
    max_distance_miles = float(cfg.get("max_distance_miles", 9.5))

    coords = route["geometry"]
    if coords:
        start_lat, start_lng = coords[0]
        end_lat, end_lng = coords[-1]
        start_dist = haversine_meters(start_lat, start_lng, centre_lat, centre_lng)
        end_dist = haversine_meters(end_lat, end_lng, centre_lat, centre_lng)
    else:
        start_dist = end_dist = float("inf")

    metrics["start_distance_m"] = start_dist
    metrics["end_distance_m"] = end_dist
    if start_dist > tolerance or end_dist > tolerance:
        failures.append("start_end_not_at_centre")

    if route["duration_s"] > max_duration:
        failures.append("duration_exceeds_limit")

    if _has_immediate_reversal(route["nodes"]):
        failures.append("immediate_segment_reversal")
    centre_revisits = _centre_revisit_count(route["nodes"], centre_node)
    metrics["centre_revisits"] = centre_revisits
    if centre_revisits > 0:
        failures.append("centre_revisit_detected")

    if dead_ends is None:
        dead_ends = dead_end_nodes(graph)
    if _contains_dead_end(route["nodes"], dead_ends, centre_node):
        failures.append("dead_end_stub")

    self_overlap = _self_overlap_ratio(route["nodes"])
    metrics["self_overlap_ratio"] = self_overlap
    if self_overlap > float(cfg.get("self_overlap_max_ratio", 0.25)):
        failures.append("high_self_overlap")

    braid_metrics = _braided_local_loop_metrics(route, centre_lat, centre_lng, cfg)
    metrics["braid_local_revisit_density"] = int(braid_metrics.get("local_revisit_density", 0))
    metrics["braid_corridor_gap_deg"] = float(braid_metrics.get("corridor_gap_deg", 0.0))
    metrics["braid_active_zone_count"] = int(braid_metrics.get("active_zone_count", 0))
    if bool(braid_metrics.get("detected")):
        failures.append("braided_local_loop_detected")

    corridor_metrics = _non_centre_corridor_reentry_metrics(route, centre_lat, centre_lng, cfg)
    metrics["corridor_reentry_points"] = int(corridor_metrics.get("revisit_points", 0))
    metrics["corridor_reentry_pairs"] = int(corridor_metrics.get("revisit_pairs", 0))
    metrics["corridor_reentry_max_density"] = int(corridor_metrics.get("max_density", 0))
    if bool(corridor_metrics.get("detected")):
        failures.append("non_centre_corridor_reentry")

    # U-turn and junction realism checks
    sharp_turns = 0
    uturns = 0
    for a, b, c in zip(route["nodes"][:-2], route["nodes"][1:-1], route["nodes"][2:]):
        lat1, lon1 = graph.nodes[a]["y"], graph.nodes[a]["x"]
        lat2, lon2 = graph.nodes[b]["y"], graph.nodes[b]["x"]
        lat3, lon3 = graph.nodes[c]["y"], graph.nodes[c]["x"]
        bearing_in = bearing_degrees(lat1, lon1, lat2, lon2)
        bearing_out = bearing_degrees(lat2, lon2, lat3, lon3)
        angle = turn_angle_deg(bearing_in, bearing_out)
        if angle >= 170:
            edge_in = _edge_data(graph, a, b) or {}
            edge_out = _edge_data(graph, b, c) or {}
            if not (_is_roundabout(edge_in) or _is_roundabout(edge_out)):
                uturns += 1
        if angle >= 120:
            sharp_turns += 1

    metrics["uturns"] = uturns
    metrics["sharp_turns"] = sharp_turns
    windowed_uturn_hits = _windowed_uturn_hits(route, graph, cfg)
    metrics["node_uturns"] = uturns
    metrics["windowed_uturns"] = len(windowed_uturn_hits)
    if windowed_uturn_hits:
        metrics["windowed_uturn_min_ratio"] = min(hit["ratio"] for hit in windowed_uturn_hits)
        metrics["windowed_uturn_min_direct_m"] = min(hit["direct_m"] for hit in windowed_uturn_hits)
    metrics["uturns"] = uturns + len(windowed_uturn_hits)
    if uturns > 0 or windowed_uturn_hits:
        failures.append("u_turn_detected")
    if sharp_turns > 0:
        warnings.append("sharp_turns_present")

    # Structured hint road usage (soft)
    if matched_set is None:
        matched_set = {normalize_for_match(name) for name in matched_names if name}
    used_set = {normalize_for_match(name) for name in route["roads_used"] if name}
    hint_overlap = len(used_set & matched_set)
    metrics["hint_roads_used"] = hint_overlap
    metrics["pdf_roads_used"] = hint_overlap
    if hint_overlap < int(cfg.get("min_hint_roads_per_route", cfg.get("min_pdf_roads_per_route", 4))):
        warnings.append("low_hint_road_coverage")

    # Road diversity (soft)
    name_counts = {}
    for name in route["roads_used"]:
        key = normalize_for_match(name)
        if not key:
            continue
        name_counts[key] = name_counts.get(key, 0) + 1
    if name_counts:
        dominant = max(name_counts.values()) / max(sum(name_counts.values()), 1)
        metrics["dominant_road_ratio"] = dominant
        if dominant > 0.7:
            warnings.append("low_road_diversity")

    # Distance sanity (soft)
    distance_miles = route["distance_m"] / 1609.344
    metrics["distance_miles"] = distance_miles
    if distance_miles < min_distance_miles or distance_miles > max_distance_miles:
        failures.append("distance_out_of_range")
    if route["duration_s"] > 1200 and distance_miles < 4:
        warnings.append("distance_short_for_duration")

    return {
        "passed": len(failures) == 0,
        "hard_failures": failures,
        "warnings": warnings,
        "metrics": metrics,
    }

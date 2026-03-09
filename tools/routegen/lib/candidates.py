"""Candidate loop generation."""

from __future__ import annotations

import ast
import random
from typing import Dict, Iterable, List, Optional, Sequence, Tuple

import networkx as nx

from .geo import bearing_degrees, haversine_meters
from .graph import dead_end_nodes
from .normalize import normalize_for_match
from .route_shape import bearing_gap_deg, build_target_zones, classify_zone_sequence

DRIVABLE_HIGHWAYS = {
    "motorway",
    "trunk",
    "primary",
    "secondary",
    "tertiary",
    "unclassified",
    "residential",
    "service",
    "living_street",
    "motorway_link",
    "trunk_link",
    "primary_link",
    "secondary_link",
    "tertiary_link",
    "road",
}

_EDGE_NAME_STATS = {"excluded_non_drivable": 0, "used_name": 0, "used_ref": 0}


def _reset_edge_name_stats() -> None:
    _EDGE_NAME_STATS["excluded_non_drivable"] = 0
    _EDGE_NAME_STATS["used_name"] = 0
    _EDGE_NAME_STATS["used_ref"] = 0


def _edge_name_stats() -> Dict[str, int]:
    return dict(_EDGE_NAME_STATS)


def _is_drivable_edge(edge_data) -> bool:
    if not edge_data:
        return False
    if edge_data.get("railway"):
        return False
    highway = edge_data.get("highway")
    if isinstance(highway, list):
        highway = highway[0] if highway else None
    if not isinstance(highway, str):
        return False
    return highway.lower() in DRIVABLE_HIGHWAYS


def _edge_data(graph, u, v):
    data = graph.get_edge_data(u, v)
    if not data:
        return None
    key = next(iter(data.keys()))
    return key, data[key]


def _edge_name(edge_data) -> str:
    if not edge_data:
        return ""
    if not _is_drivable_edge(edge_data):
        _EDGE_NAME_STATS["excluded_non_drivable"] += 1
        return ""
    name = edge_data.get("name")
    if isinstance(name, list):
        name = name[0] if name else ""
    if name:
        _EDGE_NAME_STATS["used_name"] += 1
        return str(name)
    ref = edge_data.get("ref")
    if isinstance(ref, list):
        ref = ref[0] if ref else ""
    if ref:
        _EDGE_NAME_STATS["used_ref"] += 1
        return str(ref)
    return ""


def _edge_metrics(edge_data, time_attr: str = "travel_time") -> Tuple[float, float]:
    length_m = float(edge_data.get("length", 0.0) or 0.0)
    travel_time = float(
        edge_data.get(
            time_attr,
            edge_data.get("estimated_travel_time", edge_data.get("travel_time", 0.0)),
        )
        or 0.0
    )
    return length_m, travel_time


def _append_geometry(coords: List[Tuple[float, float]], edge_data) -> None:
    geom = edge_data.get("geometry")
    if geom is not None:
        for lon, lat in geom.coords:
            if not coords or coords[-1] != (lat, lon):
                coords.append((lat, lon))


def _path_metrics(
    graph,
    nodes: List[int],
    *,
    time_attr: str = "travel_time",
) -> Tuple[float, float, List[str], List[Tuple[float, float]]]:
    distance = 0.0
    duration = 0.0
    names: List[str] = []
    coords: List[Tuple[float, float]] = []
    for u, v in zip(nodes[:-1], nodes[1:]):
        data = _edge_data(graph, u, v)
        if data is None:
            continue
        _, edge = data
        length_m, travel_time = _edge_metrics(edge, time_attr=time_attr)
        distance += length_m
        duration += travel_time
        name = _edge_name(edge)
        if name:
            names.append(name)
        if not coords:
            coords.append((graph.nodes[u]["y"], graph.nodes[u]["x"]))
        _append_geometry(coords, edge)
        if coords:
            coords.append((graph.nodes[v]["y"], graph.nodes[v]["x"]))
    return distance, duration, names, coords


def _canonical_edge(u: int, v: int) -> Tuple[int, int]:
    return (u, v) if u <= v else (v, u)


def _undirected_edge_keys(nodes: Sequence[int]) -> List[Tuple[int, int]]:
    return [_canonical_edge(u, v) for u, v in zip(nodes[:-1], nodes[1:]) if u != v]


def _candidate_signature(nodes: Sequence[int]) -> Tuple[Tuple[int, int], ...]:
    return tuple(sorted(set(_undirected_edge_keys(nodes))))


def _path_repeat_ratio(nodes: Sequence[int]) -> float:
    edges = _undirected_edge_keys(nodes)
    if not edges:
        return 0.0
    return max(0.0, 1.0 - (len(set(edges)) / len(edges)))


def _edge_weight(data) -> float:
    if not data:
        return float("inf")
    if "routing_time" in data or "travel_time" in data or "length" in data:
        return float(
            data.get("routing_time", data.get("travel_time", data.get("length", 1.0))) or 1.0
        )
    weights = [
        float(
            attrs.get("routing_time", attrs.get("travel_time", attrs.get("length", 1.0))) or 1.0
        )
        for attrs in data.values()
    ]
    return min(weights) if weights else float("inf")


def _shortest_path(
    graph,
    source: int,
    target: int,
    penalized_edges: set[Tuple[int, int]] | None = None,
) -> Optional[List[int]]:
    try:
        if penalized_edges:
            def weight(u, v, data):
                base = _edge_weight(data)
                if base == float("inf"):
                    return base
                if _canonical_edge(u, v) in penalized_edges:
                    return base * 4.0 + 45.0
                return base

            return nx.shortest_path(graph, source, target, weight=weight)
        return nx.shortest_path(graph, source, target, weight="routing_time")
    except TypeError:
        try:
            return nx.shortest_path(graph, source, target, weight="routing_time")
        except (nx.NetworkXNoPath, nx.NodeNotFound):
            return None
    except (nx.NetworkXNoPath, nx.NodeNotFound):
        return None


def _merge_paths(paths: Sequence[Sequence[int]]) -> List[int]:
    merged: List[int] = []
    for path in paths:
        if not path:
            continue
        if not merged:
            merged.extend(path)
            continue
        if merged[-1] == path[0]:
            merged.extend(path[1:])
        else:
            merged.extend(path)
    return merged


def _parse_edge_identifier(edge_id) -> Optional[Tuple[int, int, object]]:
    parsed = edge_id
    if isinstance(edge_id, str):
        try:
            parsed = ast.literal_eval(edge_id)
        except (ValueError, SyntaxError):
            return None
    if isinstance(parsed, list):
        parsed = tuple(parsed)
    if not isinstance(parsed, tuple) or len(parsed) < 2:
        return None
    try:
        u = int(parsed[0])
        v = int(parsed[1])
        key = parsed[2] if len(parsed) >= 3 else 0
    except (TypeError, ValueError):
        return None
    return u, v, key


def _edge_midpoint(graph, u: int, v: int, key) -> Tuple[float, float]:
    edge_data = graph.get_edge_data(u, v, key)
    if edge_data is not None:
        geom = edge_data.get("geometry")
        if geom is not None:
            coords = list(geom.coords)
            if coords:
                lon, lat = coords[len(coords) // 2]
                return lat, lon
    lat = (graph.nodes[u]["y"] + graph.nodes[v]["y"]) / 2.0
    lon = (graph.nodes[u]["x"] + graph.nodes[v]["x"]) / 2.0
    return lat, lon


def _target_distance_weight(distance_m: float, cfg: Dict[str, float]) -> float:
    preferred = float(cfg.get("candidate_target_preferred_distance_m", 2200))
    spread = max(float(cfg.get("candidate_target_distance_spread_m", 1400)), 1.0)
    delta = abs(distance_m - preferred)
    return max(0.15, 1.15 - min(delta / spread, 1.0))


def _build_target_pool(
    graph,
    centre_node: int,
    matched_roads: Sequence[object],
    cfg: Dict[str, float],
) -> List[Dict]:
    centre_lat = float(graph.nodes[centre_node]["y"])
    centre_lng = float(graph.nodes[centre_node]["x"])
    targets: List[Dict] = []

    for order, road in enumerate(matched_roads):
        road_name = str(getattr(road, "name", "") or "")
        matched_name = str(getattr(road, "matched_name", "") or road_name)
        edge_ids = list(getattr(road, "edge_ids", []) or [])
        confidence = float(getattr(road, "confidence", 0.0) or 0.0)

        edges: List[Dict] = []
        seen_edges = set()
        for edge_id in edge_ids:
            parsed = _parse_edge_identifier(edge_id)
            if parsed is None:
                continue
            u, v, key = parsed
            if u not in graph.nodes or v not in graph.nodes:
                continue
            if not graph.has_edge(u, v):
                continue
            if not graph.has_edge(u, v, key):
                edge_keys = list((graph.get_edge_data(u, v) or {}).keys())
                if not edge_keys:
                    continue
                key = edge_keys[0]
            edge_key = (u, v, key)
            if edge_key in seen_edges:
                continue
            seen_edges.add(edge_key)

            lat, lon = _edge_midpoint(graph, u, v, key)
            distance_m = haversine_meters(centre_lat, centre_lng, lat, lon)
            bearing = bearing_degrees(centre_lat, centre_lng, lat, lon)
            edges.append(
                {
                    "u": u,
                    "v": v,
                    "key": key,
                    "distance_m": distance_m,
                    "bearing": bearing,
                }
            )

        if not edges:
            continue

        edges.sort(
            key=lambda item: (
                _target_distance_weight(float(item["distance_m"]), cfg),
                float(item["distance_m"]),
            ),
            reverse=True,
        )
        representative = edges[0]
        targets.append(
            {
                "name": road_name,
                "key": normalize_for_match(matched_name or road_name),
                "priority": order + 1,
                "confidence": confidence,
                "distance_m": float(representative["distance_m"]),
                "bearing": float(representative["bearing"]),
                "edges": edges[:12],
            }
        )

    return targets


def _pick_target_group(target_pool: Sequence[Dict], cfg: Dict[str, float], rng: random.Random) -> List[Dict]:
    if not target_pool:
        return []

    min_distance = float(cfg.get("candidate_target_min_distance_m", 700))
    min_bearing_gap = float(cfg.get("candidate_target_min_bearing_gap_deg", 55))
    desired_count = 1
    if len(target_pool) >= 2:
        desired_count = 3 if len(target_pool) >= 6 and rng.random() < 0.35 else 2

    pool = [target for target in target_pool if target["distance_m"] >= min_distance] or list(target_pool)
    first_weights = [
        _target_distance_weight(float(target["distance_m"]), cfg)
        * max(0.25, 0.5 + target["confidence"])
        for target in pool
    ]
    selected = [rng.choices(pool, weights=first_weights, k=1)[0]]

    while len(selected) < desired_count:
        remaining = [target for target in target_pool if target not in selected]
        if not remaining:
            break

        candidates: List[Dict] = []
        weights: List[float] = []
        for target in remaining:
            gap = min(bearing_gap_deg(target["bearing"], item["bearing"]) for item in selected)
            if gap < min_bearing_gap and len(remaining) > (desired_count - len(selected)):
                continue
            weight = _target_distance_weight(float(target["distance_m"]), cfg)
            weight *= max(0.25, 0.5 + target["confidence"])
            weight *= 1.0 + (gap / 180.0)
            candidates.append(target)
            weights.append(weight)

        if not candidates:
            break
        selected.append(rng.choices(candidates, weights=weights, k=1)[0])

    ordered = sorted(selected, key=lambda item: item["bearing"])
    if rng.random() < 0.5:
        ordered.reverse()
    return ordered


def _choose_target_edge(
    target: Dict,
    used_edges: set[Tuple[int, int]],
    cfg: Dict[str, float],
    rng: random.Random,
) -> Optional[Dict]:
    edges = [
        edge
        for edge in target.get("edges", [])
        if _canonical_edge(edge["u"], edge["v"]) not in used_edges
    ] or list(target.get("edges", []))
    if not edges:
        return None
    sample_pool = edges[: min(8, len(edges))]
    weights = [_target_distance_weight(float(edge["distance_m"]), cfg) for edge in sample_pool]
    return rng.choices(sample_pool, weights=weights, k=1)[0]


def _target_segment_score(
    graph,
    segment: Sequence[int],
    used_edges: set[Tuple[int, int]],
    previous: Optional[int],
) -> float:
    if len(segment) < 2:
        return float("inf")
    if previous is not None and len(segment) > 1 and segment[1] == previous:
        return float("inf")
    if len(segment) >= 3 and segment[-3] == segment[-1]:
        return float("inf")

    repeated = sum(1 for edge in _undirected_edge_keys(segment) if edge in used_edges)
    terminal = segment[-1]
    terminal_degree = int(graph.out_degree(terminal))
    score = repeated * 12.0
    if terminal_degree <= 1:
        score += 18.0
    elif terminal_degree == 2:
        score += 5.0
    score += len(segment) * 0.01
    return score


def _build_target_segment(
    graph,
    current: int,
    previous: Optional[int],
    target_edge: Dict,
    used_edges: set[Tuple[int, int]],
) -> Optional[List[int]]:
    options: List[tuple[float, List[int]]] = []
    for start_node, end_node in (
        (target_edge["u"], target_edge["v"]),
        (target_edge["v"], target_edge["u"]),
    ):
        approach = _shortest_path(graph, current, start_node, penalized_edges=used_edges)
        if not approach:
            continue
        segment = list(approach)
        if segment[-1] != start_node:
            continue
        segment.append(end_node)
        score = _target_segment_score(graph, segment, used_edges, previous)
        if score == float("inf"):
            continue
        options.append((score, segment))
    if not options:
        return None
    options.sort(key=lambda item: item[0])
    return options[0][1]


def _build_targeted_candidate(
    graph,
    centre_node: int,
    targets: Sequence[Dict],
    cfg: Dict[str, float],
    rng: random.Random,
) -> Optional[Dict]:
    used_edges: set[Tuple[int, int]] = set()
    path_parts: List[List[int]] = []
    current = centre_node
    previous = None
    target_names: List[str] = []

    for target in targets:
        target_edge = _choose_target_edge(target, used_edges, cfg, rng)
        if target_edge is None:
            return None

        segment = _build_target_segment(graph, current, previous, target_edge, used_edges)
        if not segment:
            return None
        path_parts.append(segment)
        used_edges.update(_undirected_edge_keys(segment))
        current = segment[-1]
        previous = segment[-2] if len(segment) >= 2 else previous
        target_names.extend(target.get("names") or [str(target.get("name", ""))])

    closing = _shortest_path(graph, current, centre_node, penalized_edges=used_edges)
    if not closing:
        return None
    if previous is not None and len(closing) > 1 and closing[1] == previous:
        return None
    path_parts.append(closing)

    path_nodes = _merge_paths(path_parts)
    if len(path_nodes) < 4 or path_nodes[0] != centre_node or path_nodes[-1] != centre_node:
        return None

    repeat_limit = float(
        cfg.get("candidate_path_self_overlap_max_ratio", cfg.get("self_overlap_max_ratio", 0.25))
    )
    if _path_repeat_ratio(path_nodes) > repeat_limit:
        return None

    distance, generation_duration, road_names, coords = _path_metrics(
        graph,
        path_nodes,
        time_attr="travel_time",
    )
    _, estimated_duration, _, _ = _path_metrics(
        graph,
        path_nodes,
        time_attr="estimated_travel_time",
    )
    min_duration = float(cfg.get("min_duration_seconds", 900))
    max_duration = float(cfg.get("max_duration_seconds", 2100))
    if generation_duration < min_duration or generation_duration > max_duration:
        return None

    target_key_set = {normalize_for_match(name) for name in target_names if name}
    used_key_set = {normalize_for_match(name) for name in road_names if name}
    if target_key_set and not (target_key_set & used_key_set):
        return None

    return {
        "nodes": path_nodes,
        "distance_m": distance,
        "duration_s": generation_duration,
        "estimated_duration_s": estimated_duration,
        "roads_used": road_names,
        "geometry": coords,
        "target_names": target_names,
    }


def _zone_weight(zone: Dict, cfg: Dict[str, float]) -> float:
    weight = _target_distance_weight(float(zone.get("distance_m", 0.0)), cfg)
    weight *= max(0.25, 0.5 + float(zone.get("confidence", 0.0)))
    weight *= 1.0 + min(int(zone.get("target_count", 1)), 4) * 0.12
    if str(zone.get("band")) in {"outer", "far"}:
        weight *= 1.08
    return weight


def _sample_zone(
    zones: Sequence[Dict],
    rng: random.Random,
    *,
    cfg: Dict[str, float],
    exclude: Sequence[Dict] | None = None,
) -> Optional[Dict]:
    excluded = set(id(zone) for zone in (exclude or []))
    pool = [zone for zone in zones if id(zone) not in excluded]
    if not pool:
        return None
    weights = [_zone_weight(zone, cfg) for zone in pool]
    return rng.choices(pool, weights=weights, k=1)[0]


def _zone_gap(a: Dict, b: Dict) -> float:
    return bearing_gap_deg(float(a.get("bearing", 0.0)), float(b.get("bearing", 0.0)))


def _dedupe_zone_sequence(zones: Sequence[Dict]) -> List[Dict]:
    sequence: List[Dict] = []
    seen = set()
    for zone in zones:
        key = str(zone.get("zone_key", ""))
        if not key or key in seen:
            continue
        seen.add(key)
        sequence.append(zone)
    return sequence


def _order_zone_sequence(family: str, zones: Sequence[Dict], rng: random.Random) -> List[Dict]:
    ordered = _dedupe_zone_sequence(zones)
    if not ordered:
        return []

    if family == "compact_local":
        ordered = sorted(ordered, key=lambda zone: (float(zone.get("distance_m", 0.0)), float(zone.get("bearing", 0.0))))
        if rng.random() < 0.5:
            ordered.reverse()
        return ordered

    if family == "single_lobe":
        return sorted(ordered, key=lambda zone: float(zone.get("distance_m", 0.0)))

    if family == "adjacent_arc":
        ordered = sorted(ordered, key=lambda zone: float(zone.get("bearing", 0.0)))
        if rng.random() < 0.5:
            ordered.reverse()
        return ordered

    if family == "two_lobe":
        connector = min(ordered, key=lambda zone: float(zone.get("distance_m", 0.0)))
        outer = [zone for zone in ordered if zone is not connector]
        if len(outer) >= 2 and connector is not max(ordered, key=lambda zone: float(zone.get("distance_m", 0.0))):
            outer = sorted(outer, key=lambda zone: float(zone.get("bearing", 0.0)))
            if rng.random() < 0.5:
                outer.reverse()
            return [outer[0], connector, outer[1]]
        ordered = sorted(ordered, key=lambda zone: float(zone.get("bearing", 0.0)))
        if rng.random() < 0.5:
            ordered.reverse()
        return ordered

    if family == "cross_town":
        connector = min(ordered, key=lambda zone: float(zone.get("distance_m", 0.0)))
        outer = [zone for zone in ordered if zone is not connector]
        outer = sorted(outer, key=lambda zone: float(zone.get("distance_m", 0.0)), reverse=True)
        if len(outer) >= 2:
            return [outer[0], connector, outer[1]]
        return [connector] + outer

    return ordered


def _family_candidates_for_zone(
    anchor: Dict,
    zones: Sequence[Dict],
    *,
    min_gap: float,
    max_gap: float,
    exclude: Sequence[Dict] | None = None,
) -> List[Dict]:
    excluded = set(id(zone) for zone in (exclude or []))
    output = []
    for zone in zones:
        if id(zone) in excluded or zone is anchor:
            continue
        gap = _zone_gap(anchor, zone)
        if min_gap <= gap <= max_gap:
            output.append(zone)
    return output


def _pick_family_zone_sequence(
    zone_pool: Sequence[Dict],
    cfg: Dict[str, float],
    rng: random.Random,
    preferred_family: str | None = None,
) -> tuple[str, List[Dict]] | None:
    if not zone_pool:
        return None

    compact_zones = [zone for zone in zone_pool if str(zone.get("band")) in {"local", "inner"}]
    outerish_zones = [zone for zone in zone_pool if str(zone.get("band")) in {"inner", "outer", "far"}]
    family_sequences: List[tuple[str, List[Dict], float]] = []

    if len(compact_zones) >= 2:
        first = _sample_zone(compact_zones, rng, cfg=cfg)
        if first is not None:
            neighbours = _family_candidates_for_zone(first, compact_zones, min_gap=35.0, max_gap=120.0)
            if neighbours:
                second = _sample_zone(neighbours, rng, cfg=cfg)
                if second is not None:
                    family_sequences.append(
                        ("compact_local", _order_zone_sequence("compact_local", [first, second], rng), 0.9)
                    )

    if outerish_zones:
        primary = _sample_zone(outerish_zones, rng, cfg=cfg)
        if primary is not None:
            support = _family_candidates_for_zone(primary, compact_zones or zone_pool, min_gap=0.0, max_gap=100.0)
            if support:
                helper = _sample_zone(support, rng, cfg=cfg)
                if helper is not None:
                    secondary_support = _family_candidates_for_zone(
                        primary,
                        compact_zones or zone_pool,
                        min_gap=15.0,
                        max_gap=115.0,
                        exclude=[helper],
                    )
                    if secondary_support and rng.random() < 0.65:
                        helper_two = _sample_zone(secondary_support, rng, cfg=cfg)
                        if helper_two is not None:
                            family_sequences.append(
                                (
                                    "single_lobe",
                                    _order_zone_sequence("single_lobe", [helper, primary, helper_two], rng),
                                    1.0,
                                )
                            )
                    family_sequences.append(
                        ("single_lobe", _order_zone_sequence("single_lobe", [helper, primary], rng), 0.9)
                    )
            family_sequences.append(("single_lobe", [primary], 0.55))

    if len(outerish_zones) >= 2:
        first = _sample_zone(outerish_zones, rng, cfg=cfg)
        if first is not None:
            adjacent = _family_candidates_for_zone(first, outerish_zones, min_gap=45.0, max_gap=110.0)
            if adjacent:
                second = _sample_zone(adjacent, rng, cfg=cfg)
                if second is not None:
                    connectors = [
                        zone
                        for zone in compact_zones
                        if zone is not first
                        and zone is not second
                        and 15.0 <= _zone_gap(zone, first) <= 125.0
                        and 15.0 <= _zone_gap(zone, second) <= 125.0
                    ]
                    if connectors and rng.random() < 0.7:
                        connector = _sample_zone(connectors, rng, cfg=cfg)
                        if connector is not None:
                            family_sequences.append(
                                (
                                    "adjacent_arc",
                                    _order_zone_sequence("adjacent_arc", [first, connector, second], rng),
                                    1.2,
                                )
                            )
                    family_sequences.append(
                        ("adjacent_arc", _order_zone_sequence("adjacent_arc", [first, second], rng), 1.1)
                    )

            opposing = _family_candidates_for_zone(first, outerish_zones, min_gap=110.0, max_gap=180.0)
            if opposing:
                second = _sample_zone(opposing, rng, cfg=cfg)
                if second is not None:
                    connectors = _family_candidates_for_zone(
                        first,
                        compact_zones or zone_pool,
                        min_gap=25.0,
                        max_gap=145.0,
                        exclude=[second],
                    )
                    if connectors and rng.random() < 0.55:
                        connector = _sample_zone(connectors, rng, cfg=cfg)
                        if connector is not None:
                            family_sequences.append(
                                (
                                    "two_lobe",
                                    _order_zone_sequence("two_lobe", [first, connector, second], rng),
                                    1.15,
                                )
                            )
                    family_sequences.append(
                        ("two_lobe", _order_zone_sequence("two_lobe", [first, second], rng), 1.05)
                    )

    if len(zone_pool) >= 3 and len(outerish_zones) >= 2 and compact_zones:
        first = _sample_zone(outerish_zones, rng, cfg=cfg)
        if first is not None:
            second_candidates = _family_candidates_for_zone(first, outerish_zones, min_gap=95.0, max_gap=180.0)
            if second_candidates:
                second = _sample_zone(second_candidates, rng, cfg=cfg)
                if second is not None:
                    connectors = [
                        zone
                        for zone in compact_zones
                        if zone is not first
                        and zone is not second
                        and 25.0 <= _zone_gap(zone, first) <= 155.0
                        and 25.0 <= _zone_gap(zone, second) <= 155.0
                    ]
                    if connectors:
                        connector = _sample_zone(connectors, rng, cfg=cfg)
                        if connector is not None:
                            family_sequences.append(
                                (
                                    "cross_town",
                                    _order_zone_sequence("cross_town", [first, connector, second], rng),
                                    1.2,
                                )
                            )

    if preferred_family:
        family_sequences = [item for item in family_sequences if item[0] == preferred_family]

    if not family_sequences:
        return None

    weights = [weight for _, _, weight in family_sequences]
    family, zones, _ = rng.choices(family_sequences, weights=weights, k=1)[0]
    return family, zones


def _generate_family_candidates(
    graph,
    centre_node: int,
    matched_roads: Sequence[object],
    cfg: Dict[str, float],
    rng: random.Random,
    limit: int,
) -> List[Dict]:
    target_pool = _build_target_pool(graph, centre_node, matched_roads, cfg)
    zone_pool = build_target_zones(target_pool, cfg)
    if not zone_pool or limit <= 0:
        return []

    attempts = max(limit * 12, 240)
    candidates: List[Dict] = []
    seen_signatures = set()
    family_mix = [
        ("single_lobe", float(cfg.get("candidate_family_single_lobe_ratio", 0.28))),
        ("adjacent_arc", float(cfg.get("candidate_family_adjacent_arc_ratio", 0.24))),
        ("two_lobe", float(cfg.get("candidate_family_two_lobe_ratio", 0.22))),
        ("cross_town", float(cfg.get("candidate_family_cross_town_ratio", 0.18))),
        ("compact_local", float(cfg.get("candidate_family_compact_ratio", 0.08))),
    ]
    family_targets: Dict[str, int] = {}
    allocated = 0
    for index, (family_name, ratio) in enumerate(family_mix):
        if index == len(family_mix) - 1:
            family_targets[family_name] = max(0, limit - allocated)
            break
        target = max(1, int(round(limit * ratio)))
        family_targets[family_name] = target
        allocated += target

    family_counts = {name: 0 for name, _ in family_mix}
    family_cycle: List[str] = []
    for family_name, _ in family_mix:
        family_cycle.extend([family_name] * max(family_targets.get(family_name, 0), 1))
    if not family_cycle:
        family_cycle = [name for name, _ in family_mix]

    attempt_index = 0
    for _ in range(attempts):
        if len(candidates) >= limit:
            break
        preferred_family = family_cycle[attempt_index % len(family_cycle)]
        if family_counts.get(preferred_family, 0) >= family_targets.get(preferred_family, limit):
            unmet = [name for name, target in family_targets.items() if family_counts.get(name, 0) < target]
            preferred_family = unmet[0] if unmet else None
        attempt_index += 1

        picked = _pick_family_zone_sequence(zone_pool, cfg, rng, preferred_family=preferred_family)
        if picked is None and preferred_family is not None:
            picked = _pick_family_zone_sequence(zone_pool, cfg, rng)
        if picked is None:
            break
        family, zones = picked
        candidate = _build_targeted_candidate(graph, centre_node, zones, cfg, rng)
        if candidate is None:
            continue
        signature = _candidate_signature(candidate["nodes"])
        if signature in seen_signatures:
            continue
        seen_signatures.add(signature)
        family_meta = classify_zone_sequence(zones, cfg)
        candidate["family"] = family_meta["family"] or family
        candidate["zone_keys"] = family_meta.get("zone_keys", [])
        candidate["generation_mode"] = "family"
        candidate["family_requested"] = family
        candidates.append(candidate)
        family_counts[family] = family_counts.get(family, 0) + 1

    return candidates


def _generate_targeted_candidates(
    graph,
    centre_node: int,
    matched_roads: Sequence[object],
    cfg: Dict[str, float],
    rng: random.Random,
    limit: int,
) -> List[Dict]:
    target_pool = _build_target_pool(graph, centre_node, matched_roads, cfg)
    if not target_pool or limit <= 0:
        return []

    attempts = max(limit * 10, 240)
    candidates: List[Dict] = []
    seen_signatures = set()

    for _ in range(attempts):
        if len(candidates) >= limit:
            break
        targets = _pick_target_group(target_pool, cfg, rng)
        if not targets:
            break
        candidate = _build_targeted_candidate(graph, centre_node, targets, cfg, rng)
        if candidate is None:
            continue
        signature = _candidate_signature(candidate["nodes"])
        if signature in seen_signatures:
            continue
        seen_signatures.add(signature)
        candidates.append(candidate)

    return candidates


def _choose_next_edge(
    graph,
    current: int,
    previous: Optional[int],
    matched_names: Iterable[str],
    target_names: Iterable[str],
    seen_names: Iterable[str],
    seen_edges: Iterable[Tuple[int, int]],
    recent_nodes: Iterable[int],
    node_visits: Dict[int, int],
    recent_window: int,
    max_node_visits: int,
    dead_ends: set,
    rng: random.Random,
):
    options = []
    weights = []
    for _, v, key, data in graph.out_edges(current, keys=True, data=True):
        if previous is not None and v == previous:
            continue
        if (current, v) in seen_edges:
            continue
        if v in dead_ends:
            continue
        if v in list(recent_nodes)[-recent_window:]:
            continue
        if v != current and v != previous and node_visits.get(v, 0) >= max_node_visits:
            continue
        name = _edge_name(data)
        name_key = normalize_for_match(name)
        weight = 1.0
        if name_key and name_key in target_names:
            weight *= 4.0
        if name_key and name_key in matched_names:
            weight *= 2.5
        if name_key and name_key in seen_names:
            weight *= 0.5
        visits = node_visits.get(v, 0)
        if visits == 0:
            weight *= 1.6
        else:
            weight *= 0.3
        options.append((current, v, key, data))
        weights.append(weight)
    if not options:
        return None
    return rng.choices(options, weights=weights, k=1)[0]


def _generate_weighted_walk_candidates(
    graph,
    centre_node: int,
    matched_names: Iterable[str],
    cfg: Dict[str, float],
    rng: random.Random,
    limit: int,
    existing_signatures: set[Tuple[Tuple[int, int], ...]],
) -> List[Dict]:
    if limit <= 0:
        return []

    min_duration = float(cfg.get("min_duration_seconds", 900))
    max_duration = float(cfg.get("max_duration_seconds", 2100))
    max_steps = 420
    recent_window = int(cfg.get("candidate_recent_node_window", 8))
    max_node_visits = int(cfg.get("candidate_max_node_visits", 2))
    closing_overlap_max = float(cfg.get("candidate_closing_overlap_max_ratio", 0.35))

    dead_ends = dead_end_nodes(graph)
    matched_set = {normalize_for_match(name) for name in matched_names if name}
    target_pool = sorted(matched_set)

    candidates = []
    attempts = max(limit * 6, 120)
    target_group_size = int(cfg.get("candidate_target_hint_group_size", 3))

    for _ in range(attempts):
        if len(candidates) >= limit:
            break
        if target_pool:
            group_size = min(max(target_group_size, 1), len(target_pool))
            target_names = set(rng.sample(target_pool, k=group_size))
        else:
            target_names = set()
        path_nodes = [centre_node]
        seen_names = set()
        seen_edges = set()
        recent_nodes = [centre_node]
        node_visits: Dict[int, int] = {centre_node: 1}
        total_duration = 0.0
        previous = None
        current = centre_node

        for _ in range(max_steps):
            choice = _choose_next_edge(
                graph,
                current,
                previous,
                matched_set,
                target_names,
                seen_names,
                seen_edges,
                recent_nodes,
                node_visits,
                recent_window,
                max_node_visits,
                dead_ends,
                rng,
            )
            if choice is None:
                break
            _, nxt, _, edge_data = choice
            _, travel_time = _edge_metrics(edge_data)
            total_duration += travel_time
            name = _edge_name(edge_data)
            if name:
                seen_names.add(normalize_for_match(name))
            path_nodes.append(nxt)
            seen_edges.add((current, nxt))
            node_visits[nxt] = node_visits.get(nxt, 0) + 1
            recent_nodes.append(nxt)
            if len(recent_nodes) > recent_window * 2:
                recent_nodes = recent_nodes[-recent_window * 2 :]
            previous, current = current, nxt

            if total_duration >= min_duration:
                closing = _shortest_path(
                    graph,
                    current,
                    centre_node,
                    penalized_edges={_canonical_edge(u, v) for u, v in seen_edges},
                )
                if closing and len(closing) > 1:
                    if previous is not None and closing[1] == previous:
                        continue
                    closing_edges = list(zip(closing[:-1], closing[1:]))
                    if closing_edges:
                        repeated_closing_edges = sum(
                            1
                            for edge in closing_edges
                            if edge in seen_edges or (edge[1], edge[0]) in seen_edges
                        )
                        if repeated_closing_edges / len(closing_edges) > closing_overlap_max:
                            continue
                    _, close_duration, _, _ = _path_metrics(graph, closing, time_attr="travel_time")
                    if total_duration + close_duration <= max_duration:
                        path_nodes.extend(closing[1:])
                        break

        if path_nodes[-1] != centre_node:
            continue

        if _path_repeat_ratio(path_nodes) > float(
            cfg.get("candidate_path_self_overlap_max_ratio", cfg.get("self_overlap_max_ratio", 0.25))
        ):
            continue

        distance, generation_duration, road_names, coords = _path_metrics(
            graph,
            path_nodes,
            time_attr="travel_time",
        )
        _, estimated_duration, _, _ = _path_metrics(
            graph,
            path_nodes,
            time_attr="estimated_travel_time",
        )
        if estimated_duration <= 0:
            continue

        signature = _candidate_signature(path_nodes)
        if signature in existing_signatures:
            continue
        existing_signatures.add(signature)
        candidates.append(
            {
                "nodes": path_nodes,
                "distance_m": distance,
                "duration_s": generation_duration,
                "estimated_duration_s": estimated_duration,
                "roads_used": road_names,
                "geometry": coords,
                "target_names": sorted(target_names),
            }
        )

    return candidates


def generate_candidates(
    graph,
    centre_node: int,
    matched_roads: Sequence[object],
    cfg: Dict[str, float],
    logger,
    seed: int = 42,
) -> List[Dict]:
    rng = random.Random(seed)
    max_candidates = int(cfg.get("candidate_target_max", 200))
    matched_names = [str(getattr(road, "name", "") or "") for road in matched_roads if getattr(road, "name", "")]

    _reset_edge_name_stats()

    family_limit = max(1, int(max_candidates * float(cfg.get("candidate_family_ratio", 0.45))))
    family = _generate_family_candidates(
        graph,
        centre_node,
        matched_roads,
        cfg,
        rng,
        limit=family_limit,
    )
    signatures = {_candidate_signature(candidate["nodes"]) for candidate in family}

    targeted_limit = max(1, int(max_candidates * float(cfg.get("candidate_targeted_ratio", 0.35))))
    targeted = _generate_targeted_candidates(
        graph,
        centre_node,
        matched_roads,
        cfg,
        rng,
        limit=targeted_limit,
    )
    targeted = [candidate for candidate in targeted if _candidate_signature(candidate["nodes"]) not in signatures]
    for candidate in targeted:
        signatures.add(_candidate_signature(candidate["nodes"]))

    weighted_walk = _generate_weighted_walk_candidates(
        graph,
        centre_node,
        matched_names,
        cfg,
        rng,
        limit=max(0, max_candidates - len(family) - len(targeted)),
        existing_signatures=signatures,
    )

    candidates = family + targeted + weighted_walk
    for index, candidate in enumerate(candidates, start=1):
        candidate["id"] = f"cand-{index:04d}"

    logger.info(
        f"Generated {len(candidates)} candidate routes "
        f"(family={len(family)} targeted={len(targeted)} weighted_walk={len(weighted_walk)})."
    )
    stats = _edge_name_stats()
    logger.info(
        "Candidate road-name filter: "
        f"excluded_non_drivable={stats['excluded_non_drivable']} "
        f"used_name={stats['used_name']} used_ref={stats['used_ref']}"
    )
    return candidates

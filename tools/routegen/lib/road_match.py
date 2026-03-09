"""Match cleaned road names to OSM edges."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, Iterable, List

import osmnx as ox
import pandas as pd
from rapidfuzz import process, fuzz

from .geo import haversine_meters
from .normalize import normalize_for_match

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


@dataclass
class MatchedRoad:
    name: str
    matched_name: str
    edge_ids: List[str]
    distance_m: float
    confidence: float


def _edge_name_to_string(name_value) -> List[str]:
    if isinstance(name_value, list):
        return [str(item) for item in name_value if item]
    if name_value:
        return [str(name_value)]
    return []


def _is_drivable_edge(row) -> bool:
    if row.get("railway"):
        return False
    highway = row.get("highway")
    if isinstance(highway, list):
        highway = highway[0] if highway else None
    if not isinstance(highway, str):
        return False
    return highway.lower() in DRIVABLE_HIGHWAYS


def _ref_to_string(ref_value) -> List[str]:
    if isinstance(ref_value, list):
        return [str(item) for item in ref_value if item]
    if ref_value:
        return [str(ref_value)]
    return []


def _build_name_index(edges: pd.DataFrame) -> Dict[str, List[int]]:
    index: Dict[str, List[int]] = {}
    excluded = 0
    used_name = 0
    used_ref = 0
    for idx, row in edges.iterrows():
        if not _is_drivable_edge(row):
            excluded += 1
            continue
        for name in _edge_name_to_string(row.get("name")):
            key = normalize_for_match(name)
            if not key:
                continue
            index.setdefault(key, []).append(idx)
            used_name += 1
        if not _edge_name_to_string(row.get("name")):
            for ref in _ref_to_string(row.get("ref")):
                key = normalize_for_match(ref)
                if not key:
                    continue
                index.setdefault(key, []).append(idx)
                used_ref += 1
    index["_stats"] = [excluded, used_name, used_ref]
    return index


def _edge_distance_m(row, centre_lat: float, centre_lng: float) -> float:
    geom = row.geometry
    try:
        coords = list(geom.coords)
    except Exception:
        return 0.0
    if not coords:
        return 0.0
    lat, lon = coords[len(coords) // 2][1], coords[len(coords) // 2][0]
    return haversine_meters(centre_lat, centre_lng, lat, lon)


def match_roads(
    cleaned_roads: Iterable[str],
    graph,
    centre_lat: float,
    centre_lng: float,
    radius_meters: float,
    logger=None,
) -> List[MatchedRoad]:
    edges = ox.graph_to_gdfs(graph, nodes=False, edges=True)
    index = _build_name_index(edges)
    stats = index.pop("_stats", None)
    edge_names = list(index.keys())
    if logger and stats:
        excluded, used_name, used_ref = stats
        logger.info(
            "Road match index filter: "
            f"excluded_non_drivable={excluded} used_name={used_name} used_ref={used_ref}"
        )

    matched: List[MatchedRoad] = []
    for road in cleaned_roads:
        key = normalize_for_match(road)
        if not key:
            continue
        candidates = index.get(key)
        matched_name = key

        if not candidates and edge_names:
            fuzzy = process.extractOne(key, edge_names, scorer=fuzz.WRatio)
            if fuzzy and fuzzy[1] >= 88:
                matched_name = fuzzy[0]
                candidates = index.get(matched_name)

        if not candidates:
            continue

        edge_ids = []
        distances = []
        for idx in candidates:
            row = edges.loc[idx]
            dist = _edge_distance_m(row, centre_lat, centre_lng)
            if dist <= radius_meters * 1.5:
                distances.append(dist)
                edge_ids.append(str(idx))

        if not edge_ids:
            continue

        avg_distance = sum(distances) / len(distances)
        confidence = max(0.0, 1.0 - avg_distance / max(radius_meters, 1.0))
        matched.append(
            MatchedRoad(
                name=road,
                matched_name=matched_name,
                edge_ids=edge_ids,
                distance_m=avg_distance,
                confidence=confidence,
            )
        )

    return matched

"""Route overlap detection."""

from __future__ import annotations

from typing import Dict, List, Set, Tuple


def _canonical_edge(u: int, v: int) -> Tuple[int, int]:
    return (u, v) if u <= v else (v, u)


def edge_pairs(nodes: List[int]) -> Set[Tuple[int, int]]:
    return {_canonical_edge(u, v) for u, v in zip(nodes[:-1], nodes[1:]) if u != v}


def overlap_ratio(nodes_a: List[int], nodes_b: List[int]) -> float:
    edges_a = edge_pairs(nodes_a)
    edges_b = edge_pairs(nodes_b)
    if not edges_a or not edges_b:
        return 0.0
    shared = edges_a & edges_b
    union = edges_a | edges_b
    return len(shared) / len(union)


def compute_max_overlaps(routes: List[Dict]) -> Dict[str, float]:
    overlaps: Dict[str, float] = {}
    for i, route in enumerate(routes):
        max_overlap = 0.0
        for j, other in enumerate(routes):
            if i == j:
                continue
            ratio = overlap_ratio(route["nodes"], other["nodes"])
            max_overlap = max(max_overlap, ratio)
        overlaps[route["id"]] = max_overlap
    return overlaps

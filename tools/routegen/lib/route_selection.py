"""Route-set selection for per-centre route diversity and hint coverage."""

from __future__ import annotations

from typing import Dict, Iterable, List, Set

from .normalize import normalize_for_match
from .overlap import overlap_ratio


def _hint_metric_key(cfg: Dict, preferred: str, legacy: str) -> float:
    return float(cfg.get(preferred, cfg.get(legacy, 0.0)))


def _route_family(route: Dict) -> str:
    shape = route.get("shape", {}) or {}
    family = shape.get("family") or route.get("family") or "unclassified"
    return str(family)


def _route_zone_keys(route: Dict) -> Set[str]:
    shape = route.get("shape", {}) or {}
    keys = shape.get("zone_keys") or route.get("zone_keys") or []
    return {str(key) for key in keys if key}


def _route_hint_keys(route: Dict, hint_keys: Set[str]) -> Set[str]:
    keys = {
        normalize_for_match(name)
        for name in route.get("roads_used_clean", []) or route.get("roads_used", [])
        if name
    }
    return {key for key in keys if key and key in hint_keys}


def _selection_score(
    route: Dict,
    selected: List[Dict],
    uncovered: Set[str],
    covered_route_zones: Set[str],
    selected_family_counts: Dict[str, int],
    *,
    max_hint_per_route: int,
    coverage_weight: float,
    overlap_penalty_weight: float,
    family_diversity_weight: float,
    zone_diversity_weight: float,
    family_repeat_penalty_weight: float,
    family_soft_cap: int,
    family_soft_cap_penalty_weight: float,
) -> Dict:
    route_hint_keys = set(route.get("hint_keys", []))
    new_hint_keys = route_hint_keys & uncovered
    route_zone_keys = _route_zone_keys(route)
    new_zone_keys = route_zone_keys - covered_route_zones
    route_family = _route_family(route)
    family_repeat_count = int(selected_family_counts.get(route_family, 0))
    family_soft_cap_excess = max(0, family_repeat_count + 1 - max(family_soft_cap, 1))
    family_gain = 1 if family_repeat_count == 0 else 0
    overlaps = [overlap_ratio(route["nodes"], other["nodes"]) for other in selected]
    max_existing_overlap = max(overlaps) if overlaps else 0.0
    avg_existing_overlap = sum(overlaps) / len(overlaps) if overlaps else 0.0

    coverage_gain = len(new_hint_keys)
    coverage_gain_score = min(1.0, coverage_gain / max(max_hint_per_route, 1))
    zone_gain_score = min(1.0, len(new_zone_keys) / 3.0)
    adjusted_score = (
        float(route.get("quality", {}).get("quality_score", 0.0))
        + coverage_gain_score * coverage_weight
        + family_gain * family_diversity_weight
        + zone_gain_score * zone_diversity_weight
        - avg_existing_overlap * overlap_penalty_weight
        - family_repeat_count * family_repeat_penalty_weight
        - family_soft_cap_excess * family_soft_cap_penalty_weight
    )

    return {
        "family": route_family,
        "family_gain": family_gain,
        "family_repeat_count": family_repeat_count,
        "family_soft_cap_excess": family_soft_cap_excess,
        "coverage_gain": coverage_gain,
        "coverage_gain_score": round(coverage_gain_score, 6),
        "new_hint_keys": sorted(new_hint_keys),
        "new_zone_keys": sorted(new_zone_keys),
        "zone_gain_score": round(zone_gain_score, 6),
        "max_existing_overlap": round(max_existing_overlap, 6),
        "avg_existing_overlap": round(avg_existing_overlap, 6),
        "adjusted_score": round(adjusted_score, 6),
    }


def select_route_set(routes: List[Dict], cfg: Dict, hint_names: Iterable[str], logger=None) -> Dict:
    top_n = int(cfg.get("routes_per_centre", 15))
    max_overlap = float(cfg.get("max_overlap_ratio", 0.6))
    max_hint_per_route = int(
        cfg.get("max_hint_roads_per_route", cfg.get("max_pdf_roads_per_route", 7))
    )
    coverage_weight = _hint_metric_key(
        cfg, "selection_coverage_gain_weight", "set_coverage_gain_weight"
    ) or 0.35
    overlap_penalty_weight = _hint_metric_key(
        cfg, "selection_overlap_penalty_weight", "set_overlap_penalty_weight"
    ) or 0.25
    family_diversity_weight = float(cfg.get("selection_family_diversity_weight", 0.18))
    zone_diversity_weight = float(cfg.get("selection_zone_diversity_weight", 0.12))
    family_repeat_penalty_weight = float(cfg.get("selection_family_repeat_penalty_weight", 0.06))
    family_soft_cap = int(cfg.get("selection_family_soft_cap", max(3, top_n // 3)))
    family_soft_cap_penalty_weight = float(cfg.get("selection_family_soft_cap_penalty_weight", 0.12))
    family_hard_cap = int(cfg.get("selection_family_hard_cap", 0))

    hint_keys = {normalize_for_match(name) for name in hint_names if name}
    hint_keys = {key for key in hint_keys if key}

    for route in routes:
        route["hint_keys"] = sorted(_route_hint_keys(route, hint_keys))

    selected: List[Dict] = []
    selected_ids = set()
    uncovered = set(hint_keys)
    covered_route_zones: Set[str] = set()
    selected_family_counts: Dict[str, int] = {}

    while len(selected) < top_n:
        best_route = None
        best_eval = None
        best_tuple = None

        for route in routes:
            route_id = route.get("id")
            if route_id in selected_ids:
                continue

            route_family = _route_family(route)
            if family_hard_cap > 0 and selected_family_counts.get(route_family, 0) >= family_hard_cap:
                continue

            overlaps = [overlap_ratio(route["nodes"], other["nodes"]) for other in selected]
            max_existing_overlap = max(overlaps) if overlaps else 0.0
            if max_existing_overlap > max_overlap:
                continue

            evaluation = _selection_score(
                route,
                selected,
                uncovered,
                covered_route_zones,
                selected_family_counts,
                max_hint_per_route=max_hint_per_route,
                coverage_weight=coverage_weight,
                overlap_penalty_weight=overlap_penalty_weight,
                family_diversity_weight=family_diversity_weight,
                zone_diversity_weight=zone_diversity_weight,
                family_repeat_penalty_weight=family_repeat_penalty_weight,
                family_soft_cap=family_soft_cap,
                family_soft_cap_penalty_weight=family_soft_cap_penalty_weight,
            )
            ordering = (
                evaluation["coverage_gain"],
                evaluation["family_gain"],
                evaluation["zone_gain_score"],
                evaluation["adjusted_score"],
                -evaluation["max_existing_overlap"],
                route.get("quality", {}).get("quality_score", 0.0),
            )
            if best_tuple is None or ordering > best_tuple:
                best_tuple = ordering
                best_route = route
                best_eval = evaluation

        if best_route is None or best_eval is None:
            break

        best_route["selection"] = {
            "rank": len(selected) + 1,
            **best_eval,
        }
        selected.append(best_route)
        selected_ids.add(best_route.get("id"))
        uncovered -= set(best_eval["new_hint_keys"])
        covered_route_zones |= _route_zone_keys(best_route)
        family = _route_family(best_route)
        selected_family_counts[family] = selected_family_counts.get(family, 0) + 1

    coverage_used = set()
    for route in selected:
        coverage_used |= set(route.get("hint_keys", []))

    summary = {
        "selected_count": len(selected),
        "requested_count": top_n,
        "hint_roads_total": len(hint_keys),
        "hint_roads_covered": len(coverage_used),
        "hint_coverage_ratio": round(len(coverage_used) / max(len(hint_keys), 1), 6)
        if hint_keys
        else 0.0,
        "route_zones_covered": sorted(covered_route_zones),
        "route_family_counts": selected_family_counts,
        "family_hard_cap": family_hard_cap,
        "uncovered_hint_roads": sorted(uncovered),
        "routes": selected,
    }

    if logger:
        logger.info(
            "Route-set selection: "
            f"selected={summary['selected_count']} "
            f"hint_coverage={summary['hint_roads_covered']}/{summary['hint_roads_total']}"
        )

    return summary

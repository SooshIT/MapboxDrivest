"""Route scoring for ranking."""

from __future__ import annotations

from typing import Dict


def score_route(route: Dict, validation: Dict, overlap_ratio: float, cfg: Dict) -> Dict:
    max_duration = float(cfg.get("max_duration_seconds", 2100))
    target_duration = max_duration * 0.85

    loop_integrity = 1.0 if validation.get("passed") else 0.0

    duration = float(route.get("estimated_duration_s", route.get("duration_s", 0.0)))
    duration_fit = max(0.0, 1.0 - abs(duration - target_duration) / max_duration)

    hint_roads_used = float(
        validation.get("metrics", {}).get(
            "hint_roads_used",
            validation.get("metrics", {}).get("pdf_roads_used", 0.0),
        )
    )
    max_hint = float(cfg.get("max_hint_roads_per_route", cfg.get("max_pdf_roads_per_route", 7)))
    hint_coverage = min(1.0, hint_roads_used / max(max_hint, 1.0))

    dominant_ratio = float(validation.get("metrics", {}).get("dominant_road_ratio", 0.5))
    diversity = max(0.0, 1.0 - dominant_ratio)

    sharp_turns = float(validation.get("metrics", {}).get("sharp_turns", 0.0))
    node_count = max(len(route.get("nodes", [])), 1)
    junction_realism = max(0.0, 1.0 - sharp_turns / node_count)

    uniqueness = max(0.0, 1.0 - overlap_ratio)
    shape = route.get("shape", {}) or {}
    corridor_gap = float(shape.get("corridor_gap_deg", 0.0))
    corridor_divergence = min(1.0, corridor_gap / 120.0)
    shape_variety = min(1.0, len(shape.get("zone_keys", []) or []) / 3.0)

    weights = cfg.get("scoring_weights", {})
    loop_w = float(weights.get("loop_integrity", 0.25))
    duration_w = float(weights.get("duration_fit", 0.20))
    uniq_w = float(weights.get("uniqueness", 0.20))
    hint_w = float(weights.get("hint_coverage", weights.get("pdf_coverage", 0.15)))
    div_w = float(weights.get("diversity", 0.10))
    junc_w = float(weights.get("junction_realism", 0.10))
    corridor_w = float(weights.get("corridor_divergence", 0.0))
    shape_w = float(weights.get("shape_variety", 0.0))

    score = (
        loop_integrity * loop_w
        + duration_fit * duration_w
        + uniqueness * uniq_w
        + hint_coverage * hint_w
        + diversity * div_w
        + junction_realism * junc_w
        + corridor_divergence * corridor_w
        + shape_variety * shape_w
    )

    return {
        "quality_score": round(score, 6),
        "loop_integrity": loop_integrity,
        "duration_fit": duration_fit,
        "uniqueness": uniqueness,
        "hint_coverage": hint_coverage,
        "pdf_coverage": hint_coverage,
        "diversity": diversity,
        "junction_realism": junction_realism,
        "corridor_divergence": corridor_divergence,
        "shape_variety": shape_variety,
    }

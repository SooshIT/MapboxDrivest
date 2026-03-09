"""Routing utilities and speed heuristics."""

from __future__ import annotations

from typing import Any, Dict


DEFAULT_SPEEDS_KPH: Dict[str, float] = {
    "motorway": 96.0,
    "trunk": 80.0,
    "primary": 70.0,
    "secondary": 60.0,
    "tertiary": 50.0,
    "residential": 30.0,
    "service": 20.0,
    "living_street": 20.0,
    "unclassified": 40.0,
}

LEARNER_SPEED_CAPS_KPH: Dict[str, float] = {
    "motorway": 50.0,
    "trunk": 42.0,
    "primary": 32.0,
    "secondary": 27.0,
    "tertiary": 23.0,
    "unclassified": 19.0,
    "residential": 16.5,
    "service": 12.0,
    "living_street": 10.0,
}


def _highway_key(highway_value: Any) -> str:
    if isinstance(highway_value, list) and highway_value:
        highway_value = highway_value[0]
    if isinstance(highway_value, str):
        return highway_value.lower()
    return ""


def edge_speed_kph(highway_value: Any) -> float:
    key = _highway_key(highway_value)
    return DEFAULT_SPEEDS_KPH.get(key, 30.0)


def generation_edge_speed_kph(highway_value: Any, cfg: Dict | None = None) -> float:
    cfg = cfg or {}
    factor = float(cfg.get("learner_speed_factor", 0.55))
    key = _highway_key(highway_value)
    base_speed = DEFAULT_SPEEDS_KPH.get(key, 30.0)
    return max(8.0, base_speed * factor)


def learner_edge_speed_kph(highway_value: Any, cfg: Dict | None = None) -> float:
    cfg = cfg or {}
    key = _highway_key(highway_value)
    learner_speed = generation_edge_speed_kph(highway_value, cfg)
    speed_cap = float(LEARNER_SPEED_CAPS_KPH.get(key, learner_speed))
    return max(8.0, min(learner_speed, speed_cap))


def travel_time_seconds(length_m: float, speed_kph: float) -> float:
    if length_m <= 0 or speed_kph <= 0:
        return 0.0
    return length_m / (speed_kph * 1000.0 / 3600.0)

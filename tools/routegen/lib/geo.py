"""Geospatial helpers."""

from __future__ import annotations

import math
from typing import Iterable, Tuple


def haversine_meters(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    radius = 6371000.0
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)
    a = (
        math.sin(dphi / 2) ** 2
        + math.cos(phi1) * math.cos(phi2) * math.sin(dlambda / 2) ** 2
    )
    return 2 * radius * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def bearing_degrees(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    dlambda = math.radians(lon2 - lon1)
    y = math.sin(dlambda) * math.cos(phi2)
    x = math.cos(phi1) * math.sin(phi2) - math.sin(phi1) * math.cos(phi2) * math.cos(dlambda)
    bearing = math.degrees(math.atan2(y, x))
    return (bearing + 360) % 360


def turn_angle_deg(bearing_in: float, bearing_out: float) -> float:
    delta = abs(bearing_out - bearing_in) % 360
    return delta if delta <= 180 else 360 - delta


def midpoint(points: Iterable[Tuple[float, float]]) -> Tuple[float, float]:
    pts = list(points)
    if not pts:
        return 0.0, 0.0
    lat = sum(p[0] for p in pts) / len(pts)
    lon = sum(p[1] for p in pts) / len(pts)
    return lat, lon

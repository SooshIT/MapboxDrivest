"""Resolve centre anchors from online geocoding with Excel fallback."""

from __future__ import annotations

import json
import re
import time
import urllib.parse
import urllib.request
from functools import lru_cache
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import pandas as pd
from rapidfuzz import fuzz, process

USER_AGENT = "DrivestRouteGen/1.0"
NOMINATIM_URL = "https://nominatim.openstreetmap.org/search"


def _normalize_name(value: str) -> str:
    text = str(value or "").lower().replace("&", "and")
    for old in ("(", ")", "/", "-", "_", ".", ",", "'"):
        text = text.replace(old, " ")
    for token in ("driving", "test", "centre", "center", "routes", "route", "dvsa"):
        text = text.replace(token, " ")
    return " ".join(text.split())


def _clean_text(value) -> str:
    if value is None:
        return ""
    text = str(value).strip()
    if text.lower() == "nan":
        return ""
    return text


def _clean_postcode(value: str) -> str:
    compact = re.sub(r"\s+", "", str(value or "").upper())
    if not compact:
        return ""
    if len(compact) <= 3:
        return compact
    return f"{compact[:-3]} {compact[-3:]}"


def _parse_float(value) -> Optional[float]:
    if value is None:
        return None
    text = str(value).strip()
    if not text or text.lower() == "nan":
        return None
    try:
        return float(text)
    except ValueError:
        return None


def _haversine_meters(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    from math import asin, cos, radians, sin, sqrt

    r = 6371000.0
    dlat = radians(lat2 - lat1)
    dlon = radians(lon2 - lon1)
    a = (
        sin(dlat / 2) ** 2
        + cos(radians(lat1)) * cos(radians(lat2)) * sin(dlon / 2) ** 2
    )
    return 2 * r * asin(sqrt(a))


def _load_json(path: Path) -> Dict:
    return json.loads(path.read_text(encoding="utf-8"))


def _detect_columns(frame: pd.DataFrame) -> Dict[str, str]:
    mapping: Dict[str, str] = {}
    lower = {str(col).strip().lower(): str(col) for col in frame.columns}
    name_options = [
        "test centre name",
        "test center name",
    ]
    address_options = ["address", "test centre address", "test center address"]
    postcode_options = ["postcode", "post code"]
    lat_options = ["latitude", "lat"]
    lon_options = ["longitude", "lng", "lon", "long"]

    for key, options in (
        ("name", name_options),
        ("address", address_options),
        ("postcode", postcode_options),
        ("latitude", lat_options),
        ("longitude", lon_options),
    ):
        for option in options:
            if option in lower:
                mapping[key] = lower[option]
                break
    if "name" not in mapping:
        raise RuntimeError("Workbook is missing a centre name column.")
    return mapping


@lru_cache(maxsize=4)
def _load_workbook_rows(workbook_path: str, sheet_name: str) -> Tuple[pd.DataFrame, Dict[str, str]]:
    frame = pd.read_excel(workbook_path, sheet_name=sheet_name)
    return frame, _detect_columns(frame)


def _resolve_workbook_path(routegen_dir: Path, cfg: Dict) -> Tuple[Optional[Path], Optional[str]]:
    configured = str(cfg.get("centre_verification_workbook") or "").strip()
    if configured:
        candidate = Path(configured)
        if candidate.exists():
            return candidate, str(cfg.get("centre_verification_sheet") or "")

    report_path = routegen_dir / "config" / "hints_import_report.json"
    if report_path.exists():
        report = _load_json(report_path)
        workbook = Path(str(report.get("workbook") or "").strip())
        if workbook.exists():
            sheet = str(cfg.get("centre_verification_sheet") or report.get("sheet") or "")
            return workbook, sheet
    return None, None


def _match_excel_row(centre: Dict, workbook_path: Path, sheet_name: Optional[str]) -> Optional[Dict]:
    selected_sheet = sheet_name or ""
    frame, columns = _load_workbook_rows(str(workbook_path), selected_sheet)
    name_col = columns["name"]
    target_name = _normalize_name(centre.get("centre_name") or centre.get("centre_id") or "")
    choices: List[Tuple[str, int]] = []
    exact_row = None
    for idx, row in frame.iterrows():
        row_name = _clean_text(row.get(name_col))
        if not row_name:
            continue
        normalized = _normalize_name(row_name)
        if normalized == target_name:
            exact_row = (idx, row)
            break
        choices.append((normalized, idx))
    if exact_row is not None:
        row_idx, row = exact_row
    else:
        match = process.extractOne(target_name, [choice[0] for choice in choices], scorer=fuzz.token_set_ratio)
        if not match or match[1] < 88:
            return None
        row_idx = next(idx for norm, idx in choices if norm == match[0])
        row = frame.iloc[row_idx]

    return {
        "workbook": str(workbook_path),
        "sheet": selected_sheet or frame.attrs.get("sheet_name") or "",
        "row_index": int(row_idx) + 2,
        "name": _clean_text(row.get(columns["name"])),
        "address": _clean_text(row.get(columns.get("address", ""))),
        "postcode": _clean_postcode(_clean_text(row.get(columns.get("postcode", "")))),
        "latitude": _parse_float(row.get(columns.get("latitude", ""))),
        "longitude": _parse_float(row.get(columns.get("longitude", ""))),
    }


def _build_queries(centre: Dict, excel_row: Optional[Dict]) -> List[str]:
    centre_name = _clean_text(centre.get("centre_name"))
    postcode = _clean_postcode((excel_row or {}).get("postcode", ""))
    address = _clean_text((excel_row or {}).get("address", ""))
    town = _clean_text((excel_row or {}).get("name", "")) or centre_name

    queries: List[str] = []
    if address and centre_name and postcode:
        queries.append(f"{address}, {centre_name}, {postcode}, UK")
    if address and town and postcode:
        queries.append(f"{address}, {town}, {postcode}, UK")
    if address and postcode:
        queries.append(f"{address}, {postcode}, UK")
        queries.append(f"{centre_name} driving test centre, {address}, {postcode}, UK")
    if centre_name and postcode:
        queries.append(f"{centre_name} driving test centre, {postcode}, UK")
        queries.append(f"{centre_name}, {postcode}, UK")
    if address:
        if centre_name:
            queries.append(f"{address}, {centre_name}, UK")
        queries.append(f"{address}, UK")
    if centre_name:
        queries.append(f"{centre_name} driving test centre UK")
    seen = set()
    ordered: List[str] = []
    for query in queries:
        if query and query not in seen:
            seen.add(query)
            ordered.append(query)
    return ordered


def _fetch_nominatim(query: str, *, limit: int, pause_s: float) -> List[Dict]:
    params = {
        "q": query,
        "format": "jsonv2",
        "limit": str(limit),
        "addressdetails": "1",
        "countrycodes": "gb",
    }
    url = f"{NOMINATIM_URL}?{urllib.parse.urlencode(params)}"
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=20) as response:
        payload = response.read().decode("utf-8")
    if pause_s > 0:
        time.sleep(pause_s)
    data = json.loads(payload)
    return data if isinstance(data, list) else []


def _address_similarity(excel_row: Optional[Dict], result: Dict) -> float:
    excel_parts = " ".join(
        part for part in (_clean_text((excel_row or {}).get("address")), _clean_text((excel_row or {}).get("postcode"))) if part
    ).lower()
    result_parts = " ".join(
        str(result.get(key) or "")
        for key in ("display_name",)
    ).lower()
    if not excel_parts or not result_parts:
        return 0.0
    return float(fuzz.token_set_ratio(excel_parts, result_parts)) / 100.0


def _score_result(centre: Dict, excel_row: Optional[Dict], result: Dict) -> float:
    score = 0.0
    try:
        lat = float(result.get("lat"))
        lon = float(result.get("lon"))
    except (TypeError, ValueError):
        return -1.0

    excel_postcode = _clean_postcode((excel_row or {}).get("postcode", ""))
    excel_address = _clean_text((excel_row or {}).get("address", ""))
    display_name = str(result.get("display_name") or "")
    normalized_display = _normalize_name(display_name)
    if excel_postcode and excel_postcode.replace(" ", "") in display_name.upper().replace(" ", ""):
        score += 2.0
    elif excel_postcode:
        score -= 1.5

    score += _address_similarity(excel_row, result) * 2.0
    normalized_address = _normalize_name(excel_address)
    if normalized_address:
        if normalized_address in normalized_display:
            score += 1.5
        else:
            address_tokens = [token for token in normalized_address.split() if token not in {"road", "street", "lane", "way", "drive"}]
            if address_tokens and not any(token in normalized_display for token in address_tokens):
                score -= 2.0

    centre_name = _normalize_name(centre.get("centre_name") or "")
    if centre_name:
        score += float(fuzz.partial_ratio(centre_name, normalized_display)) / 100.0

    excel_lat = (excel_row or {}).get("latitude")
    excel_lon = (excel_row or {}).get("longitude")
    if excel_lat is not None and excel_lon is not None:
        distance = _haversine_meters(float(excel_lat), float(excel_lon), lat, lon)
        if distance <= 100:
            score += 1.5
        elif distance <= 500:
            score += 1.0
        elif distance <= 1500:
            score += 0.4
        else:
            score -= min(distance / 2000.0, 2.0)

    current_lat = _parse_float(centre.get("centre_lat"))
    current_lon = _parse_float(centre.get("centre_lng"))
    if current_lat is not None and current_lon is not None:
        current_distance = _haversine_meters(current_lat, current_lon, lat, lon)
        if current_distance <= 100:
            score += 1.0
        elif current_distance <= 500:
            score += 0.6
        elif current_distance <= 1500:
            score += 0.2
        else:
            score -= min(current_distance / 2500.0, 1.5)

    return score


def verify_centre(
    routegen_dir: Path,
    centre_slug: str,
    centre: Dict,
    cfg: Dict,
    logger,
) -> Dict:
    workbook_path, workbook_sheet = _resolve_workbook_path(routegen_dir, cfg)
    excel_row = None
    if workbook_path is not None:
        try:
            excel_row = _match_excel_row(centre, workbook_path, workbook_sheet)
        except Exception as exc:
            logger.warn(f"Centre verification workbook load failed for {centre_slug}: {exc}")

    queries = _build_queries(centre, excel_row)
    candidates: List[Dict] = []
    limit = int(cfg.get("centre_verification_online_limit", 3))
    pause_s = float(cfg.get("centre_verification_online_pause_seconds", 1.0))
    online_enabled = bool(cfg.get("centre_verification_online_enabled", True))
    if online_enabled:
        for query in queries:
            try:
                results = _fetch_nominatim(query, limit=limit, pause_s=pause_s)
            except Exception as exc:
                logger.warn(f"Centre verification lookup failed for {centre_slug}: {exc}")
                continue
            for result in results:
                scored = dict(result)
                scored["query"] = query
                scored["score"] = _score_result(centre, excel_row, result)
                candidates.append(scored)

    candidates.sort(key=lambda item: item.get("score", -999.0), reverse=True)
    best_online = candidates[0] if candidates else None

    resolved_source = "centre_json"
    resolved_lat = float(centre["centre_lat"])
    resolved_lng = float(centre["centre_lng"])
    resolved_address = ""
    confidence = "low"

    min_score = float(cfg.get("centre_verification_online_min_score", 1.5))
    if best_online is not None and float(best_online.get("score", -999.0)) >= min_score:
        try:
            resolved_lat = float(best_online["lat"])
            resolved_lng = float(best_online["lon"])
            resolved_source = "online"
            resolved_address = str(best_online.get("display_name") or "")
            confidence = "high"
        except (KeyError, TypeError, ValueError):
            pass
    elif excel_row and excel_row.get("latitude") is not None and excel_row.get("longitude") is not None:
        resolved_lat = float(excel_row["latitude"])
        resolved_lng = float(excel_row["longitude"])
        resolved_source = "excel"
        resolved_address = ", ".join(
            part for part in (excel_row.get("address", ""), excel_row.get("postcode", "")) if part
        )
        confidence = "medium"

    current_lat = float(centre["centre_lat"])
    current_lng = float(centre["centre_lng"])
    centre_distance_m = _haversine_meters(current_lat, current_lng, resolved_lat, resolved_lng)

    report = {
        "centre_id": centre.get("centre_id", centre_slug),
        "centre_name": centre.get("centre_name", centre_slug),
        "source_priority": ["online", "excel", "centre_json"],
        "resolved": {
            "source": resolved_source,
            "confidence": confidence,
            "lat": resolved_lat,
            "lng": resolved_lng,
            "address": resolved_address,
        },
        "centre_json": {
            "lat": current_lat,
            "lng": current_lng,
        },
        "excel": excel_row,
        "online_queries": queries,
        "online_candidates": [
            {
                "query": candidate.get("query"),
                "score": candidate.get("score"),
                "lat": candidate.get("lat"),
                "lon": candidate.get("lon"),
                "display_name": candidate.get("display_name"),
            }
            for candidate in candidates[: max(limit, 5)]
        ],
        "distance_from_centre_json_m": centre_distance_m,
    }

    centre["centre_lat"] = resolved_lat
    centre["centre_lng"] = resolved_lng
    if excel_row:
        centre["excel_address"] = excel_row.get("address", "")
        centre["excel_postcode"] = excel_row.get("postcode", "")
    if resolved_address:
        centre["resolved_address"] = resolved_address
    centre["centre_verification"] = report
    return report

"""Structured per-centre hint ingestion."""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Dict, List

from .normalize import ROAD_SUFFIXES, clean_road_name, dedupe_names


def _coerce_hint_name(item) -> str:
    if isinstance(item, str):
        return item
    if isinstance(item, dict):
        for key in ("name", "road_name", "road", "value"):
            value = item.get(key)
            if value:
                return str(value)
    return ""


def _split_compound_hint_name(raw_name: str) -> List[str]:
    if not raw_name:
        return []

    chunks = [
        chunk.strip()
        for chunk in re.split(r"\s*(?:/|&|\band\b)\s*", raw_name, flags=re.IGNORECASE)
        if chunk.strip()
    ]
    expanded: List[str] = []
    suffixes = {suffix.lower() for suffix in ROAD_SUFFIXES}

    for chunk in chunks:
        tokens = chunk.split()
        suffix_hits = sum(1 for token in tokens if token.lower().strip(".,") in suffixes)
        if suffix_hits <= 1:
            expanded.append(chunk)
            continue

        current: List[str] = []
        for index, token in enumerate(tokens):
            current.append(token)
            token_key = token.lower().strip(".,")
            remaining_tokens = tokens[index + 1 :]
            remaining_non_suffix = any(
                next_token.lower().strip(".,") not in suffixes
                for next_token in remaining_tokens
            )
            if token_key in suffixes and remaining_non_suffix:
                expanded.append(" ".join(current))
                current = []

        if current:
            expanded.append(" ".join(current))

    return [name for name in expanded if name]


def _clean_hint_names(raw_names: List[str]) -> List[str]:
    expanded: List[str] = []
    for name in raw_names:
        expanded.extend(_split_compound_hint_name(name))
    cleaned = [clean_road_name(name) for name in expanded]
    cleaned = [name for name in cleaned if name]
    return dedupe_names(cleaned)


def load_hint_bundle(centre_dir: Path, logger=None) -> Dict:
    hints_path = centre_dir / "hints.json"
    if not hints_path.exists():
        raise FileNotFoundError(f"Hints file not found: {hints_path}")

    payload = json.loads(hints_path.read_text(encoding="utf-8"))
    raw_items = payload.get("roads", [])
    raw_names = [_coerce_hint_name(item) for item in raw_items]
    raw_names = [name for name in raw_names if name]
    cleaned = _clean_hint_names(raw_names)

    if logger:
        logger.info(f"Loaded {len(cleaned)} structured hint roads from {hints_path.name}.")

    return {
        "path": str(hints_path),
        "source": payload.get("source", {}),
        "raw_roads": raw_names,
        "roads": cleaned,
    }


def hints_present(centre_dir: Path) -> bool:
    hints_path = centre_dir / "hints.json"
    if not hints_path.exists():
        return False
    try:
        payload = json.loads(hints_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return False
    roads = payload.get("roads", [])
    return any(_coerce_hint_name(item).strip() for item in roads)


def build_hint_payload(
    *,
    centre_id: str,
    centre_name: str,
    workbook: str,
    sheet_name: str,
    roads: List[str],
) -> Dict:
    cleaned = _clean_hint_names(roads)
    return {
        "centre_id": centre_id,
        "centre_name": centre_name,
        "source": {
            "type": "excel",
            "workbook": workbook,
            "sheet": sheet_name,
        },
        "roads": [
            {"name": name, "priority": index + 1}
            for index, name in enumerate(cleaned)
        ],
    }

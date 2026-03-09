"""Road name normalization and cleaning."""

from __future__ import annotations

import re
from typing import Iterable, List

INSTRUCTION_WORDS = [
    "turn",
    "left",
    "right",
    "ahead",
    "onto",
    "into",
    "at",
    "roundabout",
    "mini roundabout",
    "rbt",
    "take",
    "exit",
    "continue",
    "bear",
    "keep",
    "follow",
    "towards",
    "toward",
    "straight",
]

SUFFIX_MAP = {
    "rd": "road",
    "st": "street",
    "ave": "avenue",
    "dr": "drive",
    "ln": "lane",
    "cres": "crescent",
    "cl": "close",
    "pl": "place",
    "sq": "square",
    "gdns": "gardens",
    "grv": "grove",
    "pkwy": "parkway",
    "byp": "bypass",
}

ROAD_SUFFIXES = {
    "road",
    "street",
    "avenue",
    "drive",
    "lane",
    "close",
    "crescent",
    "way",
    "place",
    "square",
    "gardens",
    "grove",
    "hill",
    "rise",
    "park",
    "parkway",
    "bypass",
}


def _strip_instruction_words(text: str) -> str:
    cleaned = text
    for word in INSTRUCTION_WORDS:
        cleaned = re.sub(rf"\\b{re.escape(word)}\\b", " ", cleaned, flags=re.IGNORECASE)
    return cleaned


def _expand_suffixes(tokens: List[str]) -> List[str]:
    expanded = []
    for token in tokens:
        key = token.lower().strip(".")
        expanded.append(SUFFIX_MAP.get(key, token))
    return expanded


def clean_road_name(raw: str) -> str:
    if not raw:
        return ""
    text = raw.replace("\u2019", "'").replace("\u2018", "'")
    text = _strip_instruction_words(text)
    text = re.sub(r"[^A-Za-z0-9' ]+", " ", text)
    text = re.sub(r"\\s+", " ", text).strip()
    if not text:
        return ""

    tokens = _expand_suffixes(text.split())
    text = " ".join(tokens)

    # Normalize "St" as Saint only if followed by a name and not a suffix.
    text = re.sub(r"\\bSt\\b(?=\\s+[A-Za-z])", "St", text, flags=re.IGNORECASE)

    # Normalize common suffix capitalization.
    parts = text.split()
    if parts:
        last = parts[-1].lower().strip(".")
        if last in ROAD_SUFFIXES:
            parts[-1] = last.title()
        text = " ".join(parts)

    return text.title()


def normalize_for_match(name: str) -> str:
    if not name:
        return ""
    text = name.lower()
    text = re.sub(r"[^a-z0-9 ]+", " ", text)
    text = re.sub(r"\\s+", " ", text).strip()
    tokens = text.split()
    tokens = [t for t in tokens if t not in ROAD_SUFFIXES]
    return " ".join(tokens)


def dedupe_names(names: Iterable[str]) -> List[str]:
    seen = set()
    cleaned = []
    for name in names:
        canonical = name.strip()
        key = canonical.lower()
        if not canonical or key in seen:
            continue
        seen.add(key)
        cleaned.append(canonical)
    return cleaned

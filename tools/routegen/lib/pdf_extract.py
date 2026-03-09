"""PDF extraction for route generation."""

from __future__ import annotations

import re
from pathlib import Path
from typing import List

from pypdf import PdfReader


ROAD_SUFFIX_PATTERN = r"(Road|Rd|Street|St|Avenue|Ave|Drive|Dr|Lane|Ln|Close|Crescent|Cres|Way|Place|Pl|Square|Sq|Gardens|Gdns|Grove|Grv|Hill|Rise|Park|Parkway|Bypass)"
ROAD_NAME_PATTERN = re.compile(
    rf"\b([A-Z0-9][A-Za-z0-9'\- ]{{2,40}}\s+{ROAD_SUFFIX_PATTERN})\b",
    re.IGNORECASE,
)
TRUNK_ROAD_PATTERN = re.compile(r"\\b([AB]\\d{1,4})\\b")


def read_pdf_text(pdf_path: Path) -> str:
    if not pdf_path.exists():
        raise FileNotFoundError(f"PDF not found: {pdf_path}")
    if pdf_path.stat().st_size == 0:
        return ""
    reader = PdfReader(str(pdf_path))
    pages = []
    for page in reader.pages:
        page_text = page.extract_text() or ""
        pages.append(page_text)
    return "\n".join(pages)


def extract_road_candidates(text: str) -> List[str]:
    if not text:
        return []
    matches = ROAD_NAME_PATTERN.findall(text)
    roads = [match[0] for match in matches]
    roads += TRUNK_ROAD_PATTERN.findall(text)
    return [road.strip() for road in roads if road.strip()]

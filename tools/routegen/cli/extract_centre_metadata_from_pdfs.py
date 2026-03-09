"""Extract centre metadata from DVSA PDFs for coordinate lookup."""

from __future__ import annotations

import csv
import re
from collections import Counter
from pathlib import Path
import sys
from typing import Dict, List, Tuple

ROUTEGEN_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROUTEGEN_DIR))
PDF_DIR = ROUTEGEN_DIR / "inputs" / "dvsa_pdfs" / "Route"
REGISTRY_PATH = ROUTEGEN_DIR / "config" / "dvsa_centres.json"
OUTPUT_PATH = ROUTEGEN_DIR / "config" / "centre_metadata_extracted.csv"


def _load_registry() -> Dict[str, Dict]:
    if not REGISTRY_PATH.exists():
        raise FileNotFoundError(f"Registry not found: {REGISTRY_PATH}")
    import json

    payload = json.loads(REGISTRY_PATH.read_text(encoding="utf-8"))
    centres = payload.get("centres", [])
    if not isinstance(centres, list):
        raise ValueError("Registry centres must be a list.")
    return {centre.get("centre_id"): centre for centre in centres if centre.get("centre_id")}


def _clean_postcode(postcode: str) -> str:
    compact = postcode.upper().replace(" ", "")
    if len(compact) > 3:
        return f"{compact[:-3]} {compact[-3:]}"
    return compact


POSTCODE_REGEX = re.compile(
    r"\b([A-Z]{1,2}\d{1,2}[A-Z]?\s?\d[A-Z]{2})\b",
    re.IGNORECASE,
)
STRICT_POSTCODE_REGEX = re.compile(
    r"^(GIR0AA|(?:[A-PR-UWYZ][0-9]{1,2}|"
    r"[A-PR-UWYZ][A-HK-Y][0-9]{1,2}|"
    r"[A-PR-UWYZ][0-9][A-HJKSTUW]|"
    r"[A-PR-UWYZ][A-HK-Y][0-9][ABEHMNPRVWXY])"
    r"[0-9][ABD-HJLNP-UW-Z]{2})$",
    re.IGNORECASE,
)
PHONE_REGEX = re.compile(r"\b0\d{2,4}\s?\d{3,4}\s?\d{3,4}\b")
ADDRESS_KEYWORDS = [
    "road",
    "street",
    "lane",
    "avenue",
    "drive",
    "close",
    "way",
    "industrial estate",
    "business park",
]
ADDRESS_IGNORE_PHRASES = [
    "test centre routes",
    "test centre route",
    "driving test routes",
    "driving test route",
]
INSTRUCTION_WORDS = [
    "left",
    "right",
    "roundabout",
    "crossroads",
    "junction",
    "slip road",
    "sliproad",
    "ahead",
    "exit",
    "bear",
    "turn",
    "mini roundabout",
    "give way",
    "t junction",
    "eor",
    "dual carriageway",
    "speed",
    "remain on",
    "follow road",
    "follow signs",
    "traffic lights",
    "city sign",
    "approx",
    "mile",
    "miles",
    "remain on road",
]
INSTRUCTION_PHRASES = [
    "remain on road",
    "follow road",
    "follow signs",
    "traffic lights",
    "city sign",
    "give way",
    "roundabout",
    "crossroads",
    "junction",
    "slip road",
    "sliproad",
    "dual carriageway",
    "bear left",
    "bear right",
    "exit",
]


def _read_pdf_text(pdf_path: Path) -> str:
    if not pdf_path.exists():
        raise FileNotFoundError(f"PDF not found: {pdf_path}")
    if pdf_path.stat().st_size == 0:
        return ""
    try:
        from pypdf import PdfReader  # type: ignore
    except ModuleNotFoundError:
        from PyPDF2 import PdfReader  # type: ignore

    reader = PdfReader(str(pdf_path))
    pages = []
    for page in reader.pages:
        page_text = page.extract_text() or ""
        pages.append(page_text)
    return "\n".join(pages)


def _slugify(value: str) -> str:
    lowered = value.lower().strip()
    cleaned = re.sub(r"[^a-z0-9]+", "_", lowered)
    cleaned = re.sub(r"_+", "_", cleaned).strip("_")
    return cleaned


def _insert_camel_spaces(text: str) -> str:
    return re.sub(r"([a-z])([A-Z])", r"\1 \2", text)


def _split_tokens(stem: str) -> list[str]:
    base = stem.replace("_", " ")
    base = _insert_camel_spaces(base)
    base = re.sub(r"\s+", " ", base).strip()
    return base.split(" ") if base else []


def _strip_trailing_noise(tokens: list[str]) -> list[str]:
    noise_sequences = [
        ["driving", "test", "routes"],
        ["driving", "test", "route"],
        ["driving", "routes"],
        ["driving", "route"],
        ["test", "routes"],
        ["test", "route"],
        ["routes"],
        ["route"],
    ]
    lower_tokens = [token.lower() for token in tokens]
    changed = True
    while changed and lower_tokens:
        changed = False
        for seq in noise_sequences:
            if len(lower_tokens) >= len(seq) and lower_tokens[-len(seq) :] == seq:
                tokens = tokens[: -len(seq)]
                lower_tokens = lower_tokens[: -len(seq)]
                changed = True
                break
    return tokens


def _clean_stem(stem: str) -> Tuple[str, str]:
    tokens = _split_tokens(stem)
    tokens = _strip_trailing_noise(tokens)
    centre_name = " ".join(tokens).strip()
    return centre_name, _slugify(centre_name or stem)


def _extract_candidates(text: str) -> Tuple[str, str, str]:
    postcode_matches = [m.group(1) for m in POSTCODE_REGEX.finditer(text)]
    postcode = _clean_postcode(postcode_matches[0]) if postcode_matches else ""

    phone_matches = [m.group(0) for m in PHONE_REGEX.finditer(text)]
    phone = phone_matches[0] if phone_matches else ""

    address_candidate = ""
    lines = [re.sub(r"\s+", " ", line.strip()) for line in text.splitlines()]

    def is_viable_address(line: str) -> bool:
        if len(line) < 8:
            return False
        lower_line = line.lower()
        if any(phrase in lower_line for phrase in ADDRESS_IGNORE_PHRASES):
            return False
        if lower_line in ADDRESS_KEYWORDS:
            return False
        has_keyword = any(keyword in lower_line for keyword in ADDRESS_KEYWORDS)
        has_postcode = bool(POSTCODE_REGEX.search(line))
        if not has_keyword and not has_postcode:
            return False
        if any(phrase in lower_line for phrase in INSTRUCTION_PHRASES):
            return False
        if any(word in lower_line for word in INSTRUCTION_WORDS):
            return False
        if re.search(r"\b[1-4](st|nd|rd|th)\b", lower_line):
            return False
        if re.search(r"\b\d+(st|nd|rd|th)\s+lane\b", lower_line):
            return False
        if re.search(r"\b\d+\s*(mile|miles)\b", lower_line):
            return False
        if "@" in lower_line:
            return False
        token_count = len(line.split())
        has_digit = any(ch.isdigit() for ch in line)
        has_comma = "," in line
        if not has_digit and not has_comma and token_count < 3:
            return False
        return True

    # Prefer address lines near "Driving Test Centre" blocks.
    for idx, line in enumerate(lines):
        lower_line = line.lower()
        if "driving test centre" in lower_line or "driving test center" in lower_line:
            for candidate in lines[idx : idx + 4]:
                if candidate and is_viable_address(candidate):
                    address_candidate = candidate
                    break
        if address_candidate:
            break

    if not address_candidate:
        for line in lines:
            if not line:
                continue
            lower_line = line.lower()
            if any(keyword in lower_line for keyword in ADDRESS_KEYWORDS) and is_viable_address(line):
                address_candidate = line
                break

    if not address_candidate and postcode:
        for line in lines:
            if postcode.replace(" ", "").lower() in line.replace(" ", "").lower():
                address_candidate = line
                break

    return postcode, address_candidate, phone


def _is_valid_postcode(postcode: str) -> bool:
    compact = postcode.replace(" ", "").upper()
    if not compact:
        return False
    return bool(STRICT_POSTCODE_REGEX.match(compact))


def _clean_postcode_candidate(postcode: str) -> Tuple[str, List[str]]:
    notes: List[str] = []
    if not postcode:
        notes.append("no_postcode_found")
        return "", notes
    cleaned = _clean_postcode(postcode)
    if not _is_valid_postcode(cleaned):
        notes.append("postcode_invalid")
        return "", notes
    return cleaned, notes


def _clean_address_candidate(address: str, postcode: str) -> Tuple[str, str, List[str]]:
    notes: List[str] = []
    if not address:
        notes.append("no_address_found")
        return "", "low", notes
    line = re.sub(r"\s+", " ", address).strip()
    lower_line = line.lower()
    if any(phrase in lower_line for phrase in ADDRESS_IGNORE_PHRASES):
        notes.append("route_reference_only")
        return "", "low", notes
    if any(phrase in lower_line for phrase in INSTRUCTION_PHRASES):
        notes.append("instruction_like")
        return "", "low", notes
    if any(word in lower_line for word in INSTRUCTION_WORDS):
        notes.append("instruction_like")
        return "", "low", notes
    if re.search(r"\b[AB]\d{1,4}\b", line) and "/" in line:
        notes.append("junction_style_text")
        return "", "low", notes
    if re.search(r"\b\d+(st|nd|rd|th)\s+lane\b", lower_line):
        notes.append("instruction_like")
        return "", "low", notes
    if re.search(r"\b\d+\s*(mile|miles)\b", lower_line):
        notes.append("instruction_like")
        return "", "low", notes
    if "@" in lower_line:
        notes.append("instruction_like")
        return "", "low", notes

    has_keyword = any(keyword in lower_line for keyword in ADDRESS_KEYWORDS)
    has_postcode = False
    if postcode:
        has_postcode = postcode.replace(" ", "").lower() in line.replace(" ", "").lower()
    has_building_number = bool(re.search(r"\b\d{1,4}[A-Z]?\b", line))
    has_business = "business park" in lower_line or "industrial estate" in lower_line
    has_road_number = bool(re.search(r"\b[AB]\d{1,4}\b", line))
    slash_count = line.count("/")
    paren_count = line.count("(") + line.count(")")

    if slash_count >= 2 and not (has_postcode or has_building_number or has_business):
        notes.append("route_reference_only")
        return "", "low", notes
    if paren_count >= 2 and not (has_postcode or has_business):
        notes.append("route_reference_only")
        return "", "low", notes
    if has_road_number and not (has_postcode or has_building_number or has_business):
        notes.append("road_number_only")
        return "", "low", notes

    if not has_keyword and not has_postcode and not has_business:
        notes.append("no_address_found")
        return "", "low", notes

    if has_postcode or has_business or (has_building_number and has_keyword):
        # Avoid promoting pure A/B-road references without stronger signals.
        if has_road_number and not (has_postcode or has_business or has_building_number):
            notes.append("road_number_only")
            if has_keyword and slash_count <= 1 and paren_count == 0:
                return line, "medium", notes
            return "", "low", notes
        return line, "high", notes

    if has_road_number and not (has_postcode or has_business or has_building_number):
        notes.append("road_number_only")
        if has_keyword and slash_count <= 1 and paren_count == 0:
            return line, "medium", notes
        return "", "low", notes

    if has_keyword and slash_count <= 1 and paren_count == 0:
        notes.append("address_incomplete")
        return line, "medium", notes

    notes.append("route_reference_only")
    return "", "low", notes


def _build_queries(
    centre_name: str, postcode: str, address_candidate: str
) -> Tuple[str, str, str, str]:
    if postcode:
        query_primary = f"{centre_name} driving test centre {postcode} UK"
        status = "postcode_found"
    else:
        query_primary = f"{centre_name} driving test centre UK"
        status = "no_metadata"

    query_secondary = ""
    if address_candidate:
        query_secondary = f"{address_candidate} {centre_name} UK"
        if status == "no_metadata":
            status = "address_found"

    query_tertiary = f"{centre_name} test centre UK"

    return query_primary, query_secondary, query_tertiary, status


def main() -> int:
    if not PDF_DIR.exists():
        raise SystemExit(f"PDF directory not found: {PDF_DIR}")

    registry = _load_registry()

    # Local PDF reader to avoid hard dependency on pypdf in other modules.

    pdf_files = sorted(PDF_DIR.glob("*.pdf"))
    rows = []
    status_counts: Counter[str] = Counter()
    seen_ids = set()

    for pdf_path in pdf_files:
        stem = pdf_path.stem
        centre_name, centre_id = _clean_stem(stem)
        if centre_id in seen_ids:
            continue
        registry_entry = registry.get(centre_id)
        if not registry_entry:
            print(f"Warning: no registry match for {pdf_path.name} -> {centre_id}")
            continue
        seen_ids.add(centre_id)

        try:
            text = _read_pdf_text(pdf_path)
        except Exception as exc:
            print(f"Warning: failed to read {pdf_path.name}: {exc}")
            text = ""

        postcode_raw, address_raw, phone_candidate = _extract_candidates(text)
        cleaned_postcode, postcode_notes = _clean_postcode_candidate(postcode_raw)
        cleaned_address, address_confidence, address_notes = _clean_address_candidate(
            address_raw, cleaned_postcode
        )

        query_primary, query_secondary, query_tertiary, status = _build_queries(
            registry_entry["centre_name"], cleaned_postcode, cleaned_address
        )

        lookup_notes = []
        lookup_notes.extend(postcode_notes)
        lookup_notes.extend(address_notes)
        if phone_candidate and not cleaned_postcode and not cleaned_address:
            lookup_notes.append("phone_only")

        if cleaned_postcode:
            lookup_confidence = "high"
            status = "postcode_found"
        elif cleaned_address:
            lookup_confidence = address_confidence
            status = "address_found" if address_confidence != "low" else "weak_match"
        else:
            lookup_confidence = "low"
            status = "weak_match" if phone_candidate else "no_metadata"

        status_counts[status] += 1

        rows.append(
            {
                "centre_id": centre_id,
                "centre_name": registry_entry["centre_name"],
                "postcode_candidate": postcode_raw,
                "address_candidate": address_raw,
                "phone_candidate": phone_candidate,
                "query_primary": query_primary,
                "query_secondary": query_secondary,
                "query_tertiary": query_tertiary,
                "cleaned_postcode_candidate": cleaned_postcode,
                "cleaned_address_candidate": cleaned_address,
                "lookup_confidence": lookup_confidence,
                "lookup_notes": ",".join(sorted(set(lookup_notes))),
                "extraction_status": status,
            }
        )

    with OUTPUT_PATH.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=[
                "centre_id",
                "centre_name",
                "postcode_candidate",
                "address_candidate",
                "phone_candidate",
                "query_primary",
                "query_secondary",
                "query_tertiary",
                "cleaned_postcode_candidate",
                "cleaned_address_candidate",
                "lookup_confidence",
                "lookup_notes",
                "extraction_status",
            ],
        )
        writer.writeheader()
        writer.writerows(rows)

    confidence_counts: Counter[str] = Counter(row["lookup_confidence"] for row in rows)
    print("Lookup confidence summary:")
    for level in ("high", "medium", "low"):
        if level in confidence_counts:
            print(f"- {level}: {confidence_counts[level]}")
    print("Extraction status summary:")
    for status, count in status_counts.items():
        print(f"- {status}: {count}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

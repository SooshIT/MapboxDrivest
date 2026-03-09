"""Build DVSA centre registry from PDF filenames."""

from __future__ import annotations

import argparse
import json
import math
import re
from pathlib import Path

ROUTEGEN_DIR = Path(__file__).resolve().parents[1]
PDF_DIR = ROUTEGEN_DIR / "inputs" / "dvsa_pdfs" / "Route"
REGISTRY_PATH = ROUTEGEN_DIR / "config" / "dvsa_centres.json"


def slugify(value: str) -> str:
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


def clean_stem(stem: str) -> tuple[str, str]:
    tokens = _split_tokens(stem)
    tokens = _strip_trailing_noise(tokens)
    centre_name = " ".join(tokens).strip()
    return centre_name, slugify(centre_name or stem)


def _is_suspicious(centre_name: str, centre_id: str) -> list[str]:
    reasons = []
    if re.search(r"\broute(s)?\b", centre_name, flags=re.IGNORECASE):
        reasons.append("contains route/routes")
    if re.search(r"[a-z][A-Z]", centre_name):
        reasons.append("camelcase without spacing")
    if len(centre_id) > 35:
        reasons.append("centre_id unusually long")
    return reasons


def _load_existing_registry() -> dict[str, dict]:
    if not REGISTRY_PATH.exists():
        return {}
    try:
        payload = json.loads(REGISTRY_PATH.read_text(encoding="utf-8"))
    except Exception:
        return {}
    centres = payload.get("centres", [])
    if not isinstance(centres, list):
        return {}
    existing = {}
    for centre in centres:
        centre_id = centre.get("centre_id")
        if centre_id:
            existing[str(centre_id)] = centre
    return existing


def _is_valid_number(value: object) -> bool:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return False
    return math.isfinite(number)


def main() -> int:
    parser = argparse.ArgumentParser(description="Build centre registry from PDF filenames.")
    parser.add_argument("--dry-run", action="store_true", help="Validate and report without writing registry.")
    args = parser.parse_args()

    if not PDF_DIR.exists():
        raise SystemExit(f"PDF directory not found: {PDF_DIR}")

    pdf_files = sorted(PDF_DIR.glob("*.pdf"))
    centres = []
    seen_ids = set()
    duplicates = 0
    preserved = 0
    missing = 0
    existing_registry = _load_existing_registry()
    anomalies = []

    for pdf_path in pdf_files:
        stem = pdf_path.stem
        centre_name, centre_id = clean_stem(stem)
        if not centre_id:
            print(f"Warning: skipped empty centre id for {pdf_path.name}")
            continue
        if centre_id in seen_ids:
            duplicates += 1
            print(f"Warning: duplicate centre_id skipped: {centre_id} ({pdf_path.name})")
            continue
        seen_ids.add(centre_id)
        lat = None
        lng = None
        existing = existing_registry.get(centre_id)
        if existing and _is_valid_number(existing.get("lat")) and _is_valid_number(existing.get("lng")):
            lat = float(existing.get("lat"))
            lng = float(existing.get("lng"))
            preserved += 1
        else:
            missing += 1

        centres.append(
            {
                "centre_id": centre_id,
                "centre_name": centre_name,
                "lat": lat,
                "lng": lng,
            }
        )
        reasons = _is_suspicious(centre_name, centre_id)
        if reasons:
            anomalies.append({"centre_id": centre_id, "centre_name": centre_name, "reasons": reasons})

    centres.sort(key=lambda c: c["centre_id"])
    print(f"PDF files detected: {len(pdf_files)}")
    print(f"Centres extracted: {len(centres)}")
    print(f"Duplicates skipped: {duplicates}")
    print(f"Coordinates preserved: {preserved}")
    print(f"Coordinates missing: {missing}")
    if anomalies:
        print("Anomaly warnings:")
        for entry in anomalies:
            reasons = "; ".join(entry["reasons"])
            print(f"- {entry['centre_id']} ({entry['centre_name']}): {reasons}")

    if args.dry_run:
        return 0

    REGISTRY_PATH.write_text(
        json.dumps({"centres": centres}, indent=2, ensure_ascii=True),
        encoding="utf-8",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

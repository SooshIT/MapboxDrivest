"""Export a PDF manifest for trial centres."""

from __future__ import annotations

import csv
import json
import re
from pathlib import Path
from typing import Dict

ROUTEGEN_DIR = Path(__file__).resolve().parents[1]
TRIAL_PATH = ROUTEGEN_DIR / "config" / "routegen_trial_centres.json"
REGISTRY_PATH = ROUTEGEN_DIR / "config" / "dvsa_centres.json"
PDF_DIR = ROUTEGEN_DIR / "inputs" / "dvsa_pdfs" / "Route"
OUTPUT_PATH = ROUTEGEN_DIR / "config" / "routegen_trial_pdf_manifest.csv"


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


def _clean_stem(stem: str) -> tuple[str, str]:
    tokens = _split_tokens(stem)
    tokens = _strip_trailing_noise(tokens)
    centre_name = " ".join(tokens).strip()
    return centre_name, _slugify(centre_name or stem)


def main() -> int:
    if not TRIAL_PATH.exists():
        raise SystemExit(f"Trial centres file not found: {TRIAL_PATH}")
    if not REGISTRY_PATH.exists():
        raise SystemExit(f"Registry not found: {REGISTRY_PATH}")
    if not PDF_DIR.exists():
        raise SystemExit(f"PDF directory not found: {PDF_DIR}")

    trial_ids = json.loads(TRIAL_PATH.read_text(encoding="utf-8")).get("centres", [])
    registry = json.loads(REGISTRY_PATH.read_text(encoding="utf-8")).get("centres", [])
    registry_lookup: Dict[str, Dict] = {centre.get("centre_id"): centre for centre in registry if centre.get("centre_id")}

    pdf_lookup: Dict[str, str] = {}
    for pdf_path in PDF_DIR.glob("*.pdf"):
        _, centre_id = _clean_stem(pdf_path.stem)
        if centre_id and centre_id not in pdf_lookup:
            pdf_lookup[centre_id] = pdf_path.name

    rows = []
    for centre_id in trial_ids:
        centre = registry_lookup.get(centre_id, {})
        centre_name = centre.get("centre_name", centre_id)
        pdf_filename = pdf_lookup.get(centre_id, "")
        rows.append(
            {
                "centre_id": centre_id,
                "centre_name": centre_name,
                "pdf_filename": pdf_filename,
                "pdf_found": "yes" if pdf_filename else "no",
            }
        )

    with OUTPUT_PATH.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=["centre_id", "centre_name", "pdf_filename", "pdf_found"],
        )
        writer.writeheader()
        writer.writerows(rows)

    print(f"Wrote manifest for {len(rows)} centres to {OUTPUT_PATH}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

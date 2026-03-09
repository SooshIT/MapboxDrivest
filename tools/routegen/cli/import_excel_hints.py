"""Import structured per-centre road hints from the master Excel workbook."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Tuple

import pandas as pd
from rapidfuzz import fuzz, process

ROUTEGEN_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROUTEGEN_DIR))

from lib.hints import build_hint_payload  # noqa: E402


def _normalize_name(value: str) -> str:
    text = value.lower().replace("&", "and")
    for old in ("(", ")", "/", "-", "_", ".", ",", "'"):
        text = text.replace(old, " ")
    for token in ("driving", "test", "centre", "center", "routes", "route"):
        text = text.replace(token, " ")
    return " ".join(text.split())


def _workbook_rows(path: Path, sheet_name: Optional[str]) -> Tuple[pd.DataFrame, str]:
    workbook = pd.ExcelFile(path)
    selected_sheet = sheet_name or workbook.sheet_names[0]
    frame = pd.read_excel(path, sheet_name=selected_sheet)
    return frame, selected_sheet


def _centre_name_column(frame: pd.DataFrame) -> str:
    for name in ("Test Centre Name", "Test Center Name"):
        if name in frame.columns:
            return name
    raise SystemExit("Workbook must contain 'Test Centre Name' or 'Test Center Name'.")


def _road_columns(frame: pd.DataFrame) -> List[str]:
    columns = [col for col in frame.columns if str(col).lower().startswith("road name ")]
    if not columns:
        raise SystemExit("Workbook does not contain any 'Road name N' columns.")
    return columns


def _centre_lookup() -> Dict[str, Dict]:
    centres_dir = ROUTEGEN_DIR / "inputs" / "centres"
    lookup: Dict[str, Dict] = {}
    for centre_json in centres_dir.glob("*/centre.json"):
        payload = json.loads(centre_json.read_text(encoding="utf-8"))
        centre_dir = centre_json.parent
        centre_id = str(payload.get("centre_id") or centre_dir.name)
        centre_name = str(payload.get("centre_name") or centre_id)
        for key in {
            _normalize_name(centre_id.replace("_", " ")),
            _normalize_name(centre_name),
        }:
            if key:
                lookup[key] = {
                    "centre_id": centre_id,
                    "centre_name": centre_name,
                    "centre_dir": centre_dir,
                }
    return lookup


def _match_centre(
    row_name: str,
    lookup: Dict[str, Dict],
    choices: Iterable[str],
) -> Optional[Dict]:
    normalized = _normalize_name(row_name)
    if normalized in lookup:
        return lookup[normalized]

    fuzzy = process.extractOne(normalized, list(choices), scorer=fuzz.token_set_ratio)
    if not fuzzy or fuzzy[1] < 88:
        return None
    return lookup.get(fuzzy[0])


def main() -> int:
    parser = argparse.ArgumentParser(description="Import route hints from the master Excel workbook.")
    parser.add_argument("--input", required=True, help="Path to the Excel workbook.")
    parser.add_argument("--sheet", help="Optional sheet name. Defaults to the first sheet.")
    args = parser.parse_args()

    workbook_path = Path(args.input)
    if not workbook_path.exists():
        raise SystemExit(f"Workbook not found: {workbook_path}")

    frame, selected_sheet = _workbook_rows(workbook_path, args.sheet)
    centre_col = _centre_name_column(frame)
    road_cols = _road_columns(frame)
    lookup = _centre_lookup()
    choice_keys = list(lookup.keys())

    matched_rows = 0
    unmatched_rows: List[str] = []
    centres_written = set()

    for _, row in frame.iterrows():
        row_name = str(row.get(centre_col) or "").strip()
        if not row_name:
            continue
        matched = _match_centre(row_name, lookup, choice_keys)
        if matched is None:
            unmatched_rows.append(row_name)
            continue

        roads = []
        for column in road_cols:
            value = row.get(column)
            if pd.isna(value):
                continue
            text = str(value).strip()
            if text:
                roads.append(text)
        if not roads:
            continue

        payload = build_hint_payload(
            centre_id=matched["centre_id"],
            centre_name=matched["centre_name"],
            workbook=workbook_path.name,
            sheet_name=selected_sheet,
            roads=roads,
        )
        hints_path = matched["centre_dir"] / "hints.json"
        hints_path.write_text(json.dumps(payload, indent=2, ensure_ascii=True), encoding="utf-8")
        centres_written.add(matched["centre_id"])
        matched_rows += 1

    report = {
        "workbook": str(workbook_path),
        "sheet": selected_sheet,
        "matched_rows": matched_rows,
        "centres_written": len(centres_written),
        "unmatched_rows": unmatched_rows,
    }
    report_path = ROUTEGEN_DIR / "config" / "hints_import_report.json"
    report_path.write_text(json.dumps(report, indent=2, ensure_ascii=True), encoding="utf-8")

    print(f"Matched rows: {matched_rows}")
    print(f"Centres written: {len(centres_written)}")
    print(f"Unmatched rows: {len(unmatched_rows)}")
    print(f"Report: {report_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

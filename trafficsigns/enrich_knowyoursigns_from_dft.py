#!/usr/bin/env python3
"""
Rebuild Know Your Signs data using official DfT metadata.

Inputs:
  - /tmp/traffic-signs-images-image-details.xls (DfT spreadsheet)
  - trafficsigns/Drivest_KnowYourSigns_Theory_Expanded.json

Outputs:
  - trafficsigns/Drivest_KnowYourSigns_Theory_Expanded.json
  - trafficsigns/Drivest_KnowYourSigns_Questions_Expanded.json
  - ios/DrivestNavigation/Resources/Data/knowyoursigns/Drivest_KnowYourSigns_Theory_Expanded.json
  - ios/DrivestNavigation/Resources/Data/knowyoursigns/Drivest_KnowYourSigns_Questions_Expanded.json
"""

from __future__ import annotations

import json
import os
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional, Sequence

import xlrd


ROOT = Path(__file__).resolve().parents[1]
TRAFFICSIGNS_DIR = ROOT / "trafficsigns"
IOS_KYS_DIR = ROOT / "ios" / "DrivestNavigation" / "Resources" / "Data" / "knowyoursigns"
DFT_XLS_PATH = Path("/tmp/traffic-signs-images-image-details.xls")

THEORY_PATHS = [
    TRAFFICSIGNS_DIR / "Drivest_KnowYourSigns_Theory_Expanded.json",
    IOS_KYS_DIR / "Drivest_KnowYourSigns_Theory_Expanded.json",
]
QUESTION_PATHS = [
    TRAFFICSIGNS_DIR / "Drivest_KnowYourSigns_Questions_Expanded.json",
    IOS_KYS_DIR / "Drivest_KnowYourSigns_Questions_Expanded.json",
]


@dataclass
class SignMeta:
    category: str
    description: str
    caption: str
    dgno: str
    jpg: str


def normalize_stem(value: str) -> str:
    stem = Path(value).stem.lower()
    return re.sub(r"[^a-z0-9]+", "", stem)


def load_dft_meta(path: Path) -> Dict[str, SignMeta]:
    if not path.exists():
        raise FileNotFoundError(f"DfT spreadsheet not found: {path}")
    sheet = xlrd.open_workbook(str(path)).sheet_by_index(0)
    headers = [str(sheet.cell_value(0, c)).strip() for c in range(sheet.ncols)]
    index = {name: i for i, name in enumerate(headers)}

    required = ["Category", "Description", "Caption", "DGNo", "JPG"]
    for col in required:
        if col not in index:
            raise ValueError(f"Missing expected column in DfT sheet: {col}")

    by_jpg: Dict[str, SignMeta] = {}
    for row in range(1, sheet.nrows):
        jpg = str(sheet.cell_value(row, index["JPG"])).strip()
        if not jpg:
            continue
        item = SignMeta(
            category=str(sheet.cell_value(row, index["Category"])).strip(),
            description=str(sheet.cell_value(row, index["Description"])).strip(),
            caption=str(sheet.cell_value(row, index["Caption"])).strip(),
            dgno=str(sheet.cell_value(row, index["DGNo"])).strip(),
            jpg=jpg.strip(),
        )
        by_jpg[jpg.lower()] = item
    return by_jpg


def build_stem_index(by_jpg: Dict[str, SignMeta]) -> Dict[str, SignMeta]:
    by_stem: Dict[str, SignMeta] = {}
    for jpg, meta in by_jpg.items():
        by_stem[Path(jpg).stem.lower()] = meta
    return by_stem


def pick_meta(image_filename: str, by_jpg: Dict[str, SignMeta], by_stem: Dict[str, SignMeta]) -> Optional[SignMeta]:
    lower = image_filename.lower()
    if lower in by_jpg:
        return by_jpg[lower]

    stem = Path(lower).stem
    if stem in by_stem:
        return by_stem[stem]

    # Variant fallback: 507.1LRR -> 507.1, 530A -> 530
    numeric_prefix_match = re.match(r"^([0-9]+(?:\.[0-9]+)?)", stem)
    if numeric_prefix_match:
        prefix = numeric_prefix_match.group(1).lower()
        if prefix in by_stem:
            return by_stem[prefix]

    compact = normalize_stem(stem)
    for jpg, meta in by_jpg.items():
        if normalize_stem(jpg) == compact:
            return meta
    return None


def collapse_whitespace(value: str) -> str:
    return re.sub(r"\s+", " ", value).strip()


def to_sentence(value: str) -> str:
    text = collapse_whitespace(value)
    if not text:
        return text
    return text if text.endswith(".") else f"{text}."


def build_title(meta: Optional[SignMeta], fallback_title: str) -> str:
    if not meta:
        return fallback_title
    candidate = meta.description or meta.caption
    candidate = collapse_whitespace(candidate)
    if candidate:
        return candidate
    return fallback_title


def resolve_category(meta: Optional[SignMeta], fallback_category: str) -> str:
    candidate = collapse_whitespace(meta.category) if meta else ""
    return candidate or fallback_category


def driver_action_from_meta(meta: Optional[SignMeta], category: str, description: str, fallback: str) -> str:
    if not meta:
        return fallback

    text = description.lower()
    cat = category.lower()

    if "no entry" in text:
        return "Do not enter. Find an alternative legal route."
    if "stop" in text and "bus stop" not in text:
        return "Stop fully, check all directions, then proceed only when safe."
    if "give way" in text or "yield" in text:
        return "Give way to other traffic and continue only when safe."
    if "speed limit" in text:
        return "Do not exceed the posted speed limit and adjust for conditions."
    if "minimum speed" in text:
        return "Maintain at least the minimum speed when safe to do so."
    if "end of" in text and "speed" in text:
        return "The previous speed restriction ends here; follow the new road limit."
    if "no left turn" in text or "no right turn" in text or "no u-turn" in text:
        return "Do not make the prohibited turn shown by this sign."
    if "turn left" in text or "turn right" in text:
        return "Follow the turn direction shown and position early."
    if "ahead only" in text or "straight ahead" in text:
        return "Continue in the direction indicated by the sign."
    if "keep left" in text or "keep right" in text:
        return "Pass on the side indicated and keep within your lane."
    if "one way" in text:
        return "Travel only in the permitted one-way direction."
    if "ahead" in text and any(
        token in text
        for token in ["cyclists", "children", "school", "crossing", "event", "hazard", "bend", "junction"]
    ):
        return "Reduce speed, scan ahead, and prepare early for the condition shown."
    if "bus lane" in text or "with-flow" in text or "contra-flow" in text or "route for use by" in text:
        return "Use this lane only if your vehicle class is permitted by the sign."
    if "cycle lane" in text or "cycle track" in text or "pedal cycles only" in text:
        return "Keep out of this lane unless your vehicle is specifically permitted."
    if "pedestrian" in text or "zebra" in text or "crossing" in text:
        return "Slow down and be ready to stop for pedestrians as required."
    if "tram" in text:
        return "Follow tram-specific restrictions and keep clear of tram tracks."
    if "weight limit" in text or "max gross weight" in text:
        return "Do not proceed if your vehicle exceeds the signed weight limit."
    if "width limit" in text or "max width" in text:
        return "Check vehicle width and do not enter if you exceed the limit."
    if "height limit" in text or "low bridge" in text:
        return "Check vehicle height and avoid this route if clearance is insufficient."
    if "parking" in text or "waiting" in text or "loading" in text or "bay" in text:
        return "Follow the parking, waiting, and loading restrictions exactly as signed."

    if "warning" in cat:
        return "Reduce speed, scan ahead, and prepare for the hazard shown."
    if "direction" in cat or "information" in cat:
        return "Use the sign information early to choose the correct route and lane."
    if "regulatory" in cat:
        return "Comply with the mandatory or prohibitory instruction immediately."
    if "parking" in cat:
        return "Follow the signed parking and waiting conditions before stopping."

    return fallback


def memory_hint_for_category(category: str) -> str:
    cat = category.lower()
    if "warning" in cat:
        return "Warning signs usually prepare you for hazards ahead: ease speed early."
    if "regulatory" in cat:
        return "Regulatory signs are legal instructions: comply immediately."
    if "information" in cat or "direction" in cat:
        return "Read route information early so lane changes stay calm and safe."
    if "speed" in cat:
        return "Check the number and units quickly, then stabilise your speed."
    if "parking" in cat:
        return "For parking signs, check times, symbols, and exemptions before stopping."
    return "Read shape, colour, and symbol first, then apply the instruction early."


def family_for_sign(category: str, description: str) -> str:
    cat = category.lower()
    text = description.lower()
    if "speed" in text or "speed" in cat:
        return "speed"
    if "parking" in text or "waiting" in text or "parking" in cat:
        return "parking"
    if "bus lane" in text or "cycle lane" in text or "bus and cycle" in cat or "tram" in text:
        return "lane_restriction"
    if "pedestrian" in text or "zebra" in text or "crossing" in text:
        return "pedestrian"
    if "no " in text or "prohibited" in text:
        return "prohibition"
    if "left" in text or "right" in text or "ahead" in text or "one way" in text or "keep" in text:
        return "direction"
    if "warning" in cat:
        return "warning"
    if "information" in cat or "direction" in cat:
        return "information"
    return "general"


def stable_index(seed: str, size: int) -> int:
    if size <= 0:
        return 0
    return sum(ord(ch) for ch in seed) % size


def pick_distractors(
    current_sign_id: str,
    current_description: str,
    current_category: str,
    description_pool_by_category: Dict[str, List[str]],
    all_descriptions: List[str],
) -> List[str]:
    category_pool = [d for d in description_pool_by_category.get(current_category, []) if d != current_description]
    pool = category_pool if len(category_pool) >= 3 else [d for d in all_descriptions if d != current_description]
    if not pool:
        return [
            "Follow the opposite maneuver shown on the sign.",
            "Ignore the sign if traffic is light.",
            "Use sat-nav only and disregard this sign.",
        ]

    out: List[str] = []
    idx = stable_index(current_sign_id, len(pool))
    for _ in range(min(3, len(pool))):
        candidate = pool[idx % len(pool)]
        if candidate not in out:
            out.append(candidate)
        idx += 7
    while len(out) < 3:
        filler = [
            "Ignore the sign unless traffic is heavy.",
            "Follow only road markings and ignore this sign.",
            "Continue unchanged and review later.",
        ][len(out)]
        out.append(filler)
    return out


def build_question_text(category: str) -> str:
    label = category.strip()
    if label.lower().endswith(" signs"):
        label = label[:-1]
    return f"You see this {label.lower()} on the road. What does it mean?"


def enrich_theory_and_questions(theory: dict, old_questions: List[dict], by_jpg: Dict[str, SignMeta], by_stem: Dict[str, SignMeta]) -> tuple[dict, List[dict]]:
    old_questions_by_sign = {q.get("sign_id"): q for q in old_questions}

    # Flatten signs for pool generation.
    all_signs: List[dict] = []
    for chapter in theory.get("chapters", []):
        for section in chapter.get("sections", []):
            all_signs.extend(section.get("signs", []))

    enriched_signs: List[dict] = []
    for sign in all_signs:
        image_filename = Path(sign.get("image_path", "")).name
        meta = pick_meta(image_filename, by_jpg, by_stem)
        description = build_title(meta, sign.get("title", "Road sign"))
        category = resolve_category(meta, sign.get("category", "Road signs"))
        action = driver_action_from_meta(meta, category, description, sign.get("driver_action", "Follow the sign instruction safely."))
        meaning = f"This sign means: {to_sentence(description)}"

        sign["title"] = description
        sign["meaning"] = meaning
        sign["category"] = category
        sign["driver_action"] = action
        sign["memory_hint"] = memory_hint_for_category(category)
        if meta and meta.dgno:
            sign["code"] = meta.dgno
        enriched_signs.append(sign)

    descriptions_by_category: Dict[str, List[str]] = {}
    for sign in enriched_signs:
        category = sign.get("category", "Road signs")
        descriptions_by_category.setdefault(category, [])
        meaning_text = sign.get("meaning", "").replace("This sign means: ", "").strip()
        if meaning_text.endswith("."):
            meaning_text = meaning_text[:-1]
        if meaning_text and meaning_text not in descriptions_by_category[category]:
            descriptions_by_category[category].append(meaning_text)

    all_descriptions: List[str] = []
    for values in descriptions_by_category.values():
        for v in values:
            if v not in all_descriptions:
                all_descriptions.append(v)

    new_questions: List[dict] = []
    for idx, sign in enumerate(enriched_signs, start=1):
        sign_id = sign.get("sign_id")
        category = sign.get("category", "Road signs")
        meaning_desc = sign.get("meaning", "").replace("This sign means: ", "").strip()
        if meaning_desc.endswith("."):
            meaning_desc = meaning_desc[:-1]

        distractors = pick_distractors(
            current_sign_id=str(sign_id),
            current_description=meaning_desc,
            current_category=category,
            description_pool_by_category=descriptions_by_category,
            all_descriptions=all_descriptions,
        )

        options = [meaning_desc] + distractors
        rotate = stable_index(str(sign_id) + "::opt", len(options))
        options = options[rotate:] + options[:rotate]
        correct_index = options.index(meaning_desc)

        old = old_questions_by_sign.get(sign_id, {})
        question = {
            "id": int(old.get("id", idx)),
            "topic": category,
            "difficulty": old.get("difficulty", "Medium"),
            "question": build_question_text(category),
            "image_path": sign.get("image_path"),
            "options": options,
            "correct_answer_index": correct_index,
            "explanation": f"This sign means: {to_sentence(meaning_desc)} Correct response: {to_sentence(sign.get('driver_action', 'Follow the sign instruction safely'))}",
            "sign_id": sign_id,
        }
        new_questions.append(question)

    return theory, new_questions


def load_json(path: Path):
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def save_json(path: Path, payload) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=2)
        handle.write("\n")


def main() -> None:
    by_jpg = load_dft_meta(DFT_XLS_PATH)
    by_stem = build_stem_index(by_jpg)

    source_theory = load_json(THEORY_PATHS[0])
    source_questions = load_json(QUESTION_PATHS[0])
    enriched_theory, enriched_questions = enrich_theory_and_questions(source_theory, source_questions, by_jpg, by_stem)

    for path in THEORY_PATHS:
        save_json(path, enriched_theory)
    for path in QUESTION_PATHS:
        save_json(path, enriched_questions)

    print(f"[Drivest] DfT sign rows: {len(by_jpg)}")
    print(f"[Drivest] Theory signs updated: {sum(len(s.get('signs', [])) for c in enriched_theory.get('chapters', []) for s in c.get('sections', []))}")
    print(f"[Drivest] Questions updated: {len(enriched_questions)}")
    print("[Drivest] Know Your Signs enrichment complete.")


if __name__ == "__main__":
    main()

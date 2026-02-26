import json
import os
import shutil
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

import pandas as pd


ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = ROOT / "roadsign"
SOURCE_XLS = SOURCE_ROOT / "traffic-signs-images-image-details.xls"
ASSET_ROOT = ROOT / "android" / "app" / "src" / "main" / "assets" / "traffic_signs"
ASSET_IMAGES_ROOT = ASSET_ROOT / "images"
OUTPUT_JSON = ASSET_ROOT / "traffic_signs_pack_v1.json"

SOURCE_REFERENCES = [
    "https://www.gov.uk/government/publications/know-your-traffic-signs",
    "https://assets.publishing.service.gov.uk/media/656ef4271104cf0013fa74ef/know-your-traffic-signs-dft.pdf",
]


@dataclass(frozen=True)
class CandidateImage:
    basename: str
    full_path: Path
    folder_slug: str


FOLDER_TITLE_OVERRIDES = {
    "bus-and-cycle-signs-jpg": "Bus and Cycle Signs",
    "direction-and-tourist-signs-jpg": "Direction and Tourist Signs",
    "information-signs-jpg": "Information Signs",
    "level-crossing-signs-jpg": "Level Crossing Signs",
    "low-bridge-signs-jpg": "Low Bridge Signs",
    "miscellaneous-jpg": "Miscellaneous",
    "motorway-signs-jpg": "Motorway Signs",
    "on-street-parking-jpg": "On-street Parking",
    "pedestrian-cycle-equestrian-jpg": "Pedestrian, Cycle and Equestrian",
    "pedestrian-zone-signs-jpg": "Pedestrian Zone Signs",
    "regulatory-signs-jpg": "Regulatory Signs",
    "road-works-and-temporary-jpg": "Road Works and Temporary",
    "signs-for-cyclists-and-pedestrians-jpg": "Cyclists and Pedestrians",
    "speed-limit-signs-jpg": "Speed Limit Signs",
    "tidal-flow-lane-control-jpg": "Tidal Flow Lane Control",
    "traffic-calming-jpg": "Traffic Calming",
    "tram-signs-jpg": "Tram Signs",
    "warning-signs-jpg": "Warning Signs",
}


CATEGORY_NAME_TO_FOLDER = {
    "warning signs": "warning-signs-jpg",
    "speed limit signs": "speed-limit-signs-jpg",
    "regulatory signs": "regulatory-signs-jpg",
    "bus and cycle signs": "bus-and-cycle-signs-jpg",
    "bus and cycle signs ": "bus-and-cycle-signs-jpg",
    "bus and cycle": "bus-and-cycle-signs-jpg",
    "level crossing signs": "level-crossing-signs-jpg",
    "tram signs": "tram-signs-jpg",
    "motorway signs": "motorway-signs-jpg",
    "on-street parking": "on-street-parking-jpg",
    "road works and temporary": "road-works-and-temporary-jpg",
    "information signs": "information-signs-jpg",
    "traffic calming": "traffic-calming-jpg",
    "miscellaneous": "miscellaneous-jpg",
    "low bridge signs": "low-bridge-signs-jpg",
    "pedestrian zone signs": "pedestrian-zone-signs-jpg",
    "signs for cyclists and pedestrians": "signs-for-cyclists-and-pedestrians-jpg",
    "pedestrian cycle equestrian": "pedestrian-cycle-equestrian-jpg",
    "pedestrian, cycle and equestrian": "pedestrian-cycle-equestrian-jpg",
    "direction and tourist signs": "direction-and-tourist-signs-jpg",
    "tidal flow lane control": "tidal-flow-lane-control-jpg",
}


def normalize_text(value: str) -> str:
    return " ".join(
        "".join(ch.lower() if ch.isalnum() else " " for ch in value).split()
    )


def category_to_folders(category_value: str) -> list[str]:
    if not category_value or category_value == "nan":
        return []
    folders: list[str] = []
    for raw_part in str(category_value).split(","):
        key = normalize_text(raw_part)
        mapped = CATEGORY_NAME_TO_FOLDER.get(key)
        if mapped and mapped not in folders:
            folders.append(mapped)
    return folders


def build_image_index(root: Path) -> dict[str, list[CandidateImage]]:
    index: dict[str, list[CandidateImage]] = defaultdict(list)
    for file_path in root.rglob("*.jpg"):
        rel_parts = file_path.relative_to(root).parts
        folder_slug = next((p for p in rel_parts if p.endswith("-jpg")), "uncategorized")
        index[file_path.name].append(
            CandidateImage(
                basename=file_path.name,
                full_path=file_path,
                folder_slug=folder_slug,
            )
        )
    return index


def choose_candidate(
    jpg_name: str,
    candidates: Iterable[CandidateImage],
    preferred_folders: list[str],
) -> CandidateImage | None:
    options = list(candidates)
    if not options:
        return None
    if len(options) == 1:
        return options[0]

    ranked = []
    for option in options:
        score = 0
        if preferred_folders:
            if option.folder_slug == preferred_folders[0]:
                score += 8
            if option.folder_slug in preferred_folders:
                score += 5
        score -= len(str(option.full_path))
        ranked.append((score, option))
    ranked.sort(key=lambda item: item[0], reverse=True)
    return ranked[0][1]


def safe_text(value) -> str:
    if pd.isna(value):
        return ""
    return str(value).strip()


def main() -> None:
    if not SOURCE_XLS.exists():
        raise SystemExit(f"Source spreadsheet missing: {SOURCE_XLS}")
    if not SOURCE_ROOT.exists():
        raise SystemExit(f"Source folder missing: {SOURCE_ROOT}")

    df = pd.read_excel(SOURCE_XLS)
    image_index = build_image_index(SOURCE_ROOT)

    if ASSET_ROOT.exists():
        shutil.rmtree(ASSET_ROOT)
    ASSET_IMAGES_ROOT.mkdir(parents=True, exist_ok=True)

    rows_out = []
    missing_images = []
    copied = set()
    primary_counts = Counter()

    for idx, row in df.iterrows():
        jpg_name = safe_text(row.get("JPG"))
        if not jpg_name:
            continue

        preferred_folders = category_to_folders(safe_text(row.get("Category")))
        candidate = choose_candidate(jpg_name, image_index.get(jpg_name, []), preferred_folders)
        if candidate is None:
            missing_images.append(jpg_name)
            continue

        asset_rel = Path("images") / candidate.folder_slug / candidate.basename
        target_path = ASSET_ROOT / asset_rel
        target_path.parent.mkdir(parents=True, exist_ok=True)
        if str(target_path) not in copied:
            shutil.copy2(candidate.full_path, target_path)
            copied.add(str(target_path))

        category_value = safe_text(row.get("Category"))
        official_categories = [part.strip() for part in category_value.split(",") if part.strip()]
        folder_categories = preferred_folders or [candidate.folder_slug]
        primary_counts[candidate.folder_slug] += 1

        rows_out.append(
            {
                "id": f"sign-{len(rows_out)+1}",
                "code": safe_text(row.get("DGNo")),
                "caption": safe_text(row.get("Caption")) or safe_text(row.get("Description")),
                "description": safe_text(row.get("Description")),
                "officialCategory": category_value,
                "officialCategories": official_categories,
                "primaryCategoryId": candidate.folder_slug,
                "categoryIds": folder_categories,
                "shape": safe_text(row.get("Shape")),
                "backgroundColor": safe_text(row.get("BGColour")),
                "borderColor": safe_text(row.get("BDColour")),
                "textHint": safe_text(row.get("Text")),
                "symbol1": safe_text(row.get("Symbol1")),
                "symbol2": safe_text(row.get("Symbol2")),
                "imageAssetPath": asset_rel.as_posix(),
            }
        )

    categories = []
    for folder_slug, count in sorted(primary_counts.items(), key=lambda item: (-item[1], item[0])):
        categories.append(
            {
                "id": folder_slug,
                "name": FOLDER_TITLE_OVERRIDES.get(folder_slug, folder_slug.replace("-jpg", "").replace("-", " ").title()),
                "signCount": count,
            }
        )

    pack = {
        "version": 1,
        "generatedFrom": str(SOURCE_XLS.relative_to(ROOT)).replace("\\", "/"),
        "sourceReferences": SOURCE_REFERENCES,
        "catalogueSize": len(rows_out),
        "missingImageCount": len(missing_images),
        "missingImages": sorted(set(missing_images)),
        "categories": categories,
        "signs": rows_out,
    }

    OUTPUT_JSON.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_JSON.write_text(json.dumps(pack, indent=2, ensure_ascii=False), encoding="utf-8")

    print(f"Generated {OUTPUT_JSON}")
    print(f"Signs: {len(rows_out)}")
    print(f"Categories: {len(categories)}")
    print(f"Copied images: {len(copied)}")
    print(f"Missing images: {len(set(missing_images))}")
    if missing_images:
        print("Missing image names:", ", ".join(sorted(set(missing_images))))


if __name__ == "__main__":
    main()

# Drivest Route Generation (routegen)

This folder contains the offline pipeline for generating and validating Drivest practice routes.

Scope
- Input: test centre metadata + structured centre road hints or source PDF
- Output: validated route JSON assets for backend and mobile apps
- Active centres: Colchester (`colchester`), Clacton-on-Sea (`clacton_on_sea`)
 - Status: pipeline structure is implemented; real PDFs and OSM data are required before generation can run.

Key folders
- config: pipeline configuration and overrides
- inputs: PDFs, centre metadata, and OSM extracts
- lib: generation, validation, scoring modules
- cli: entry points for centre or batch runs
- work: intermediate artifacts per centre
- output: final route assets per centre

Quick start (single centre, structured hints)
1. Import the master Excel workbook into per-centre hint files:

```bash
python tools/routegen/cli/import_excel_hints.py --input "C:\path\to\master.xlsx"
```

2. Place a local OSM extract in `tools/routegen/inputs/osm/` (preferred).
   Supported filenames:
   - `extract.osm.pbf` (preferred)
   - `extract.graphml`
   - `extract.osm`
   When a local extract is present, routegen will use it and will not require `--use-overpass`.
   `--use-overpass` is only a fallback if no local extract exists.
3. Run:

```bash
python tools/routegen/cli/run_centre.py --centre colchester --input-mode hints
```

PDF fallback mode
1. Place the PDF in `tools/routegen/inputs/centres/<centre_slug>/source.pdf`
2. Place a local OSM extract in `tools/routegen/inputs/osm/` (preferred).
   Supported filenames:
   - `extract.osm.pbf` (preferred)
   - `extract.graphml`
   - `extract.osm`
   When a local extract is present, routegen will use it and will not require `--use-overpass`.
   `--use-overpass` is only a fallback if no local extract exists.
3. Run:

```bash
python tools/routegen/cli/run_centre.py --centre colchester
```

Preflight-only check:

```bash
python tools/routegen/cli/run_centre.py --centre colchester --preflight-only
```

Seed import mode:

```bash
python tools/routegen/cli/run_centre.py --centre colchester --input-mode seed
```

Seed import reads `.gpx`/`.kml` files from `tools/routegen/inputs/centres/<centre_slug>/seeds/` and exports
the same JSON output shape as the PDF pipeline. It still enforces core validation rules.

Hint mode reads `tools/routegen/inputs/centres/<centre_slug>/hints.json` and treats those road names as
centre-specific hints, not a fixed route sequence. The generator then builds up to `routes_per_centre`
unique circular loops that collectively maximize hint-road coverage while respecting the route constraints.

Export route assets into the app:

```bash
python tools/routegen/cli/export_assets.py
```

This writes:
- `android/app/src/main/assets/routes/<centre_slug>/routes.json`
- `ios/DrivestNavigation/Resources/Data/routes/<centre_slug>/routes.json`

Outputs land in `tools/routegen/output/<centre_slug>/`.

Import full DVSA centre registry (no route generation)
Expected input formats:

JSON (list or object with "centres"):
```json
[
  { "centre_name": "Colchester", "lat": 51.872116, "lng": 0.928174 }
]
```

CSV headers (minimum):
```
centre_name,lat,lng
Colchester,51.872116,0.928174
```

Optional fields:
- `centre_id` (or `id`, `slug`); if omitted, a slug is derived from `centre_name`.

Dry-run (validate only):
```bash
python tools/routegen/cli/import_centres.py --input path/to/centres.csv --dry-run
```

Import and overwrite registry:
```bash
python tools/routegen/cli/import_centres.py --input path/to/centres.json
```

Import registry from DVSA PDF filenames (no route generation):
```bash
python tools/routegen/cli/import_centres_from_pdfs.py
```

Dry-run PDF import:
```bash
python tools/routegen/cli/import_centres_from_pdfs.py --dry-run
```

Note: The PDF filename import only builds the centre registry. Missing coordinates must be
enriched before large-scale centre initialization and route generation.

Enrich centre coordinates (no route generation):

Input JSON example:
```json
[
  { "centre_id": "aberdeen_north", "lat": 57.1497, "lng": -2.0943 }
]
```

Input CSV example:
```
centre_id,lat,lng
aberdeen_north,57.1497,-2.0943
```

Dry-run:
```bash
python tools/routegen/cli/enrich_centre_coordinates.py --input path/to/coords.csv --dry-run
```

Real run:
```bash
python tools/routegen/cli/enrich_centre_coordinates.py --input path/to/coords.csv
```

Note: Run `tools/routegen/cli/run_all.py` for large-scale centre initialization only after
coordinates are populated.

Build coordinate lookup candidates (no route generation):
```bash
python tools/routegen/cli/build_coordinate_lookup_candidates.py
```

Merge enriched coordinates into the registry:
```bash
python tools/routegen/cli/merge_enriched_coordinates.py
```

Note: Low-confidence coordinate lookups should be reviewed before running the merge step.

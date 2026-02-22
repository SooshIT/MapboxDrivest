const fs = require("fs");
const path = require("path");
const { Pool } = require("pg");

const ROOT_DIR = path.join(__dirname, "..", "..");
const DATA_DIR = path.join(ROOT_DIR, "data");
const ROUTES_DIR = path.join(DATA_DIR, "routes");
const VERSIONS_DIR = path.join(DATA_DIR, "versions");
const CENTRES_FILE = path.join(DATA_DIR, "centres.json");

function parseArgs() {
  const centreIdIndex = process.argv.findIndex((arg) => arg === "--centreId");
  if (centreIdIndex === -1 || !process.argv[centreIdIndex + 1]) {
    throw new Error("Usage: node src/workers/buildRoutesPack.js --centreId <centreId>");
  }
  return { centreId: process.argv[centreIdIndex + 1] };
}

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, "utf-8"));
}

function writeJson(filePath, payload) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  fs.writeFileSync(filePath, JSON.stringify(payload, null, 2), "utf-8");
}

function findCentre(centreId) {
  const centres = readJson(CENTRES_FILE);
  return centres.find((centre) => centre.id === centreId) || null;
}

function bboxFromCentre(centre) {
  const delta = 0.08;
  return {
    south: centre.lat - delta,
    west: centre.lon - delta,
    north: centre.lat + delta,
    east: centre.lon + delta
  };
}

function writeVersionedPack(packType, centreId, version, payload) {
  const versionFile = path.join(VERSIONS_DIR, packType, centreId, `${version}.json`);
  writeJson(versionFile, payload);
}

function pruneVersions(packType, centreId, keepLatest = 3) {
  const dir = path.join(VERSIONS_DIR, packType, centreId);
  if (!fs.existsSync(dir)) return;
  const files = fs
    .readdirSync(dir)
    .map((name) => path.join(dir, name))
    .filter((filePath) => fs.statSync(filePath).isFile())
    .sort((a, b) => fs.statSync(b).mtimeMs - fs.statSync(a).mtimeMs);

  files.slice(keepLatest).forEach((filePath) => fs.unlinkSync(filePath));
}

async function registerPackVersion(packType, centreId, version, inlineJson, generatedAtIso) {
  const databaseUrl = process.env.DATABASE_URL || "";
  if (!databaseUrl) return;

  const pool = new Pool({ connectionString: databaseUrl });
  try {
    await pool.query(
      `INSERT INTO pack_registry (centre_id, pack_type, version, url_or_inline, generated_at)
       VALUES ($1, $2, $3, $4, $5::timestamptz)`,
      [centreId, packType, version, inlineJson, generatedAtIso]
    );
  } finally {
    await pool.end();
  }
}

async function main() {
  const { centreId } = parseArgs();
  const centre = findCentre(centreId);
  if (!centre) {
    throw new Error(`Unknown centreId: ${centreId}`);
  }

  const routesPath = path.join(ROUTES_DIR, `${centreId}.json`);
  if (!fs.existsSync(routesPath)) {
    throw new Error(`Routes data missing: ${routesPath}`);
  }
  const existing = readJson(routesPath);
  const routes = Array.isArray(existing.routes) ? existing.routes : [];

  const generatedAt = new Date().toISOString();
  const version = `routes-${Date.now()}`;
  const routesPack = {
    metadata: {
      version,
      generatedAt,
      bbox: bboxFromCentre(centre)
    },
    centreId,
    routes
  };

  writeJson(routesPath, routesPack);
  writeVersionedPack("routes", centreId, version, routesPack);
  pruneVersions("routes", centreId, 3);

  await registerPackVersion("routes", centreId, version, JSON.stringify(routesPack), generatedAt);
  console.log(`Routes pack built for ${centreId}. routes=${routes.length} file=${routesPath}`);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});

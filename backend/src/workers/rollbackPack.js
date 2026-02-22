const fs = require("fs");
const path = require("path");
const { Pool } = require("pg");

const ROOT_DIR = path.join(__dirname, "..", "..");
const DATA_DIR = path.join(ROOT_DIR, "data");
const VERSIONS_DIR = path.join(DATA_DIR, "versions");
const CURRENT_ROUTES_DIR = path.join(DATA_DIR, "routes");
const CURRENT_HAZARDS_DIR = path.join(DATA_DIR, "hazards");

function parseArgs() {
  const args = process.argv;
  const centreId = readArg(args, "--centreId");
  const packType = readArg(args, "--packType");
  const toVersion = readArg(args, "--toVersion");
  if (!centreId || !packType || !toVersion) {
    throw new Error(
      "Usage: node src/workers/rollbackPack.js --centreId <centreId> --packType <hazards|routes> --toVersion <version>"
    );
  }
  if (packType !== "hazards" && packType !== "routes") {
    throw new Error("packType must be either 'hazards' or 'routes'.");
  }
  return { centreId, packType, toVersion };
}

function readArg(args, key) {
  const index = args.indexOf(key);
  if (index === -1) return null;
  return args[index + 1] || null;
}

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, "utf-8"));
}

function writeJson(filePath, payload) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  fs.writeFileSync(filePath, JSON.stringify(payload, null, 2), "utf-8");
}

async function registerRollback(packType, centreId, version, payload) {
  const databaseUrl = process.env.DATABASE_URL || "";
  if (!databaseUrl) return;

  const pool = new Pool({ connectionString: databaseUrl });
  try {
    await pool.query(
      `INSERT INTO pack_registry (centre_id, pack_type, version, url_or_inline, generated_at)
       VALUES ($1, $2, $3, $4, NOW())`,
      [centreId, packType, version, JSON.stringify(payload)]
    );
  } finally {
    await pool.end();
  }
}

async function main() {
  const { centreId, packType, toVersion } = parseArgs();
  const sourcePath = path.join(VERSIONS_DIR, packType, centreId, `${toVersion}.json`);
  if (!fs.existsSync(sourcePath)) {
    throw new Error(`Versioned pack not found: ${sourcePath}`);
  }

  const payload = readJson(sourcePath);
  const targetPath = packType === "hazards"
    ? path.join(CURRENT_HAZARDS_DIR, `${centreId}.json`)
    : path.join(CURRENT_ROUTES_DIR, `${centreId}.json`);
  writeJson(targetPath, payload);

  await registerRollback(packType, centreId, toVersion, payload);
  console.log(
    `Rollback complete. centreId=${centreId} packType=${packType} toVersion=${toVersion} target=${targetPath}`
  );
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});

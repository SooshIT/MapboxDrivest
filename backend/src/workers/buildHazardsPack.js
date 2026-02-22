const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const { Pool } = require("pg");

const ROOT_DIR = path.join(__dirname, "..", "..");
const DATA_DIR = path.join(ROOT_DIR, "data");
const CACHE_DIR = path.join(DATA_DIR, "cache", "overpass");
const HAZARDS_DIR = path.join(DATA_DIR, "hazards");
const VERSIONS_DIR = path.join(DATA_DIR, "versions");
const CENTRES_FILE = path.join(DATA_DIR, "centres.json");
const OVERPASS_URL = "https://overpass-api.de/api/interpreter";
const OVERPASS_CACHE_TTL_MS = 24 * 60 * 60 * 1000;
const REQUEST_INTERVAL_MS = 1000;

let lastRequestAt = 0;

function parseArgs() {
  const centreIdIndex = process.argv.findIndex((arg) => arg === "--centreId");
  if (centreIdIndex === -1 || !process.argv[centreIdIndex + 1]) {
    throw new Error("Usage: node src/workers/buildHazardsPack.js --centreId <centreId>");
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

function findCentre(centreId) {
  const centres = readJson(CENTRES_FILE);
  return centres.find((centre) => centre.id === centreId) || null;
}

function bboxFromCentre(centre) {
  const delta = 0.05;
  return {
    south: centre.lat - delta,
    west: centre.lon - delta,
    north: centre.lat + delta,
    east: centre.lon + delta
  };
}

function buildOverpassQueryForType(type, bbox) {
  const box = `${bbox.south},${bbox.west},${bbox.north},${bbox.east}`;
  const bodyByType = {
    TRAFFIC_SIGNAL: `
      node["highway"="traffic_signals"](${box});
      way["highway"="traffic_signals"](${box});
    `,
    ZEBRA_CROSSING: `
      node["crossing"="zebra"](${box});
      way["crossing"="zebra"](${box});
      node["crossing_ref"="zebra"](${box});
      way["crossing_ref"="zebra"](${box});
      node["highway"="crossing"]["crossing"="zebra"](${box});
      way["highway"="crossing"]["crossing"="zebra"](${box});
      node["highway"="crossing"]["crossing_ref"="zebra"](${box});
      way["highway"="crossing"]["crossing_ref"="zebra"](${box});
    `,
    ROUNDABOUT: `
      node["junction"="roundabout"](${box});
      way["junction"="roundabout"](${box});
    `,
    MINI_ROUNDABOUT: `
      node["highway"="mini_roundabout"](${box});
      way["highway"="mini_roundabout"](${box});
      node["junction"="mini_roundabout"](${box});
      way["junction"="mini_roundabout"](${box});
      node["mini_roundabout"="yes"](${box});
      way["mini_roundabout"="yes"](${box});
    `,
    SCHOOL_ZONE: `
      node["amenity"="school"](${box});
      way["amenity"="school"](${box});
      node["landuse"="school"](${box});
      way["landuse"="school"](${box});
    `,
    BUS_LANE: `
      node["bus:lanes"](${box});
      way["bus:lanes"](${box});
      node["lanes:bus"](${box});
      way["lanes:bus"](${box});
      node["busway"](${box});
      way["busway"](${box});
      node["busway:left"](${box});
      way["busway:left"](${box});
      node["busway:right"](${box});
      way["busway:right"](${box});
    `,
    BUS_STOP: `
      node["highway"="bus_stop"](${box});
      way["highway"="bus_stop"](${box});
      node["public_transport"="stop_position"]["bus"="yes"](${box});
      way["public_transport"="stop_position"]["bus"="yes"](${box});
      node["public_transport"="platform"]["bus"="yes"](${box});
      way["public_transport"="platform"]["bus"="yes"](${box});
    `,
    GIVE_WAY: `
      node["highway"="give_way"](${box});
      way["highway"="give_way"](${box});
      node["give_way"="yes"](${box});
      way["give_way"="yes"](${box});
      node["traffic_sign"~"give[_ ]?way", i](${box});
      way["traffic_sign"~"give[_ ]?way", i](${box});
    `,
    SPEED_CAMERA: `
      node["highway"="speed_camera"](${box});
      way["highway"="speed_camera"](${box});
      node["enforcement"="speed_camera"](${box});
      way["enforcement"="speed_camera"](${box});
      node["speed_camera"="yes"](${box});
      way["speed_camera"="yes"](${box});
      node["camera:speed"="yes"](${box});
      way["camera:speed"="yes"](${box});
    `
  };

  return `
[out:json][timeout:20];
(
  ${bodyByType[type] || ""}
);
out body geom;
`.trim();
}

function confidenceFor(type) {
  switch (type) {
    case "TRAFFIC_SIGNAL":
      return 0.85;
    case "ROUNDABOUT":
      return 0.8;
    case "MINI_ROUNDABOUT":
      return 0.78;
    case "ZEBRA_CROSSING":
      return 0.75;
    case "SCHOOL_ZONE":
      return 0.7;
    case "BUS_STOP":
      return 0.68;
    case "GIVE_WAY":
      return 0.72;
    case "SPEED_CAMERA":
      return 0.85;
    case "BUS_LANE":
      return 0.3;
    default:
      return 0.5;
  }
}

function confidenceLevelFor(score) {
  if (score >= 0.75) return "high";
  if (score >= 0.5) return "medium";
  return "low";
}

function scoreHazard(type, tags) {
  if (type === "BUS_LANE") {
    if (hasExplicitBuswayTag(tags)) {
      return 0.7;
    }
    return 0.3;
  }
  return Number(confidenceFor(type).toFixed(2));
}

function hasExplicitBuswayTag(tags) {
  if (!tags || typeof tags !== "object") return false;
  return Boolean(tags.busway || tags["busway:left"] || tags["busway:right"]);
}

function centroidFromGeometry(geometry) {
  if (!Array.isArray(geometry) || geometry.length === 0) return null;
  let latSum = 0;
  let lonSum = 0;
  let count = 0;
  for (const point of geometry) {
    if (typeof point?.lat !== "number" || typeof point?.lon !== "number") continue;
    latSum += point.lat;
    lonSum += point.lon;
    count += 1;
  }
  if (count === 0) return null;
  return { lat: latSum / count, lon: lonSum / count };
}

function resolveFeatureTypes(tags) {
  const types = new Set();
  if (tags.highway === "traffic_signals") types.add("TRAFFIC_SIGNAL");
  if (
    tags.crossing === "zebra" ||
    tags.crossing_ref === "zebra" ||
    (
      tags.highway === "crossing" &&
      (tags.crossing === "zebra" || tags.crossing_ref === "zebra")
    )
  ) {
    types.add("ZEBRA_CROSSING");
  }
  if (tags.junction === "roundabout") types.add("ROUNDABOUT");
  if (
    tags.highway === "mini_roundabout" ||
    tags.junction === "mini_roundabout" ||
    tags.mini_roundabout === "yes"
  ) {
    types.add("MINI_ROUNDABOUT");
  }
  if (tags.amenity === "school" || tags.landuse === "school") types.add("SCHOOL_ZONE");
  if (
    tags.highway === "bus_stop" ||
    (tags.public_transport === "stop_position" && (tags.bus === "yes" || tags.bus === "designated")) ||
    (tags.public_transport === "platform" && (tags.bus === "yes" || tags.highway === "bus_stop"))
  ) {
    types.add("BUS_STOP");
  }
  if (
    tags.highway === "give_way" ||
    tags.give_way === "yes" ||
    (typeof tags.traffic_sign === "string" && /give[_ ]?way/i.test(tags.traffic_sign))
  ) {
    types.add("GIVE_WAY");
  }
  if (
    tags.highway === "speed_camera" ||
    tags.enforcement === "speed_camera" ||
    tags.speed_camera === "yes" ||
    tags["camera:speed"] === "yes"
  ) {
    types.add("SPEED_CAMERA");
  }
  if (
    tags["bus:lanes"] ||
    tags["lanes:bus"] ||
    tags.busway ||
    tags["busway:left"] ||
    tags["busway:right"]
  ) {
    types.add("BUS_LANE");
  }
  return [...types];
}

function mapOverpassToHazards(overpassPayload) {
  const hazards = [];
  const seen = new Set();
  const elements = Array.isArray(overpassPayload?.elements) ? overpassPayload.elements : [];

  for (const element of elements) {
    const tags = element.tags || {};
    const types = resolveFeatureTypes(tags);
    if (types.length === 0) continue;

    let lat = typeof element.lat === "number" ? element.lat : null;
    let lon = typeof element.lon === "number" ? element.lon : null;
    if (lat == null || lon == null) {
      const centroid = centroidFromGeometry(element.geometry);
      if (centroid) {
        lat = centroid.lat;
        lon = centroid.lon;
      }
    }
    if (lat == null || lon == null) continue;

    for (const type of types) {
      const id = `${type.toLowerCase()}:${element.type}:${element.id}`;
      if (seen.has(id)) continue;
      seen.add(id);
      const confidenceScore = scoreHazard(type, tags);
      const confidenceLevel = confidenceLevelFor(confidenceScore);
      hazards.push({
        id,
        type,
        lat,
        lon,
        tags,
        source: "overpass",
        confidence: confidenceScore,
        confidenceHint: confidenceScore,
        confidenceScore,
        confidenceLevel,
        voiceEligible: confidenceLevel === "high" || confidenceLevel === "medium"
      });
    }
  }

  return hazards;
}

function cacheFilePath(cacheKey) {
  const hash = crypto.createHash("sha256").update(cacheKey).digest("hex");
  return path.join(CACHE_DIR, `${hash}.json`);
}

async function fetchOverpassWithCache(cacheKey, query) {
  fs.mkdirSync(CACHE_DIR, { recursive: true });
  const cachePath = cacheFilePath(cacheKey);
  let staleCachePayload = null;

  if (fs.existsSync(cachePath)) {
    staleCachePayload = readJson(cachePath);
    const stat = fs.statSync(cachePath);
    if (Date.now() - stat.mtimeMs < OVERPASS_CACHE_TTL_MS) {
      return staleCachePayload;
    }
  }

  let lastError = null;
  for (let attempt = 1; attempt <= 3; attempt += 1) {
    try {
      const elapsed = Date.now() - lastRequestAt;
      if (elapsed < REQUEST_INTERVAL_MS) {
        await new Promise((resolve) => setTimeout(resolve, REQUEST_INTERVAL_MS - elapsed));
      }
      lastRequestAt = Date.now();

      const body = new URLSearchParams({ data: query }).toString();
      const response = await fetch(OVERPASS_URL, {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded"
        },
        body
      });
      if (!response.ok) {
        const text = await response.text();
        throw new Error(`Overpass failed: ${response.status} ${text.slice(0, 300)}`);
      }
      const payload = await response.json();
      writeJson(cachePath, payload);
      return payload;
    } catch (error) {
      lastError = error;
      const backoffMs = attempt * 1200;
      await new Promise((resolve) => setTimeout(resolve, backoffMs));
    }
  }

  if (staleCachePayload) {
    console.warn(`Using stale Overpass cache for ${cacheKey}: ${lastError?.message || "unknown error"}`);
    return staleCachePayload;
  }

  console.warn(`Overpass unavailable for ${cacheKey}; continuing with empty payload.`);
  return { elements: [] };
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

  const bbox = bboxFromCentre(centre);
  const types = [
    "TRAFFIC_SIGNAL",
    "ZEBRA_CROSSING",
    "GIVE_WAY",
    "SPEED_CAMERA",
    "ROUNDABOUT",
    "MINI_ROUNDABOUT",
    "SCHOOL_ZONE",
    "BUS_LANE",
    "BUS_STOP"
  ];
  const mergedPayload = { elements: [] };

  for (const type of types) {
    const query = buildOverpassQueryForType(type, bbox);
    const cacheKey = `hazards:${centreId}:${type}:${bbox.south},${bbox.west},${bbox.north},${bbox.east}`;
    const overpassPayload = await fetchOverpassWithCache(cacheKey, query);
    const elements = Array.isArray(overpassPayload?.elements) ? overpassPayload.elements : [];
    mergedPayload.elements.push(...elements);
  }

  const hazards = mapOverpassToHazards(mergedPayload);

  const generatedAt = new Date().toISOString();
  const version = `hazards-${Date.now()}`;
  const hazardsPack = {
    metadata: {
      version,
      generatedAt,
      bbox
    },
    centreId,
    hazards
  };

  const outputPath = path.join(HAZARDS_DIR, `${centreId}.json`);
  writeJson(outputPath, hazardsPack);
  writeVersionedPack("hazards", centreId, version, hazardsPack);
  pruneVersions("hazards", centreId, 3);

  await registerPackVersion("hazards", centreId, version, JSON.stringify(hazardsPack), generatedAt);

  console.log(`Hazards pack built for ${centreId}. hazards=${hazards.length} file=${outputPath}`);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});

const crypto = require("crypto");
const fs = require("fs");
const path = require("path");

const ROOT_DIR = path.join(__dirname, "..", "..");
const DATA_DIR = path.join(ROOT_DIR, "data");
const CACHE_DIR = path.join(DATA_DIR, "cache", "overpass-route");
const OVERPASS_URL = "https://overpass-api.de/api/interpreter";
const OVERPASS_CACHE_TTL_MS = 10 * 60 * 1000;
const OVERPASS_RETRIES = 3;
const REQUEST_INTERVAL_MS = 600;
let lastRequestAt = 0;

const DEFAULT_HAZARD_TYPES = [
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

const ALLOWED_HAZARD_TYPES = new Set(DEFAULT_HAZARD_TYPES);

const OVERPASS_SNIPPETS_BY_TYPE = {
  TRAFFIC_SIGNAL: `
    node["highway"="traffic_signals"]({{BOX}});
    way["highway"="traffic_signals"]({{BOX}});
  `,
  ZEBRA_CROSSING: `
    node["crossing"="zebra"]({{BOX}});
    way["crossing"="zebra"]({{BOX}});
    node["crossing_ref"="zebra"]({{BOX}});
    way["crossing_ref"="zebra"]({{BOX}});
    node["highway"="crossing"]["crossing"="zebra"]({{BOX}});
    way["highway"="crossing"]["crossing"="zebra"]({{BOX}});
    node["highway"="crossing"]["crossing_ref"="zebra"]({{BOX}});
    way["highway"="crossing"]["crossing_ref"="zebra"]({{BOX}});
  `,
  ROUNDABOUT: `
    node["junction"="roundabout"]({{BOX}});
    way["junction"="roundabout"]({{BOX}});
  `,
  MINI_ROUNDABOUT: `
    node["highway"="mini_roundabout"]({{BOX}});
    way["highway"="mini_roundabout"]({{BOX}});
    node["junction"="mini_roundabout"]({{BOX}});
    way["junction"="mini_roundabout"]({{BOX}});
    node["mini_roundabout"="yes"]({{BOX}});
    way["mini_roundabout"="yes"]({{BOX}});
  `,
  SCHOOL_ZONE: `
    node["amenity"="school"]({{BOX}});
    way["amenity"="school"]({{BOX}});
    node["landuse"="school"]({{BOX}});
    way["landuse"="school"]({{BOX}});
  `,
  BUS_LANE: `
    node["bus:lanes"]({{BOX}});
    way["bus:lanes"]({{BOX}});
    node["lanes:bus"]({{BOX}});
    way["lanes:bus"]({{BOX}});
    node["busway"]({{BOX}});
    way["busway"]({{BOX}});
    node["busway:left"]({{BOX}});
    way["busway:left"]({{BOX}});
    node["busway:right"]({{BOX}});
    way["busway:right"]({{BOX}});
  `,
  BUS_STOP: `
    node["highway"="bus_stop"]({{BOX}});
    way["highway"="bus_stop"]({{BOX}});
    node["public_transport"="stop_position"]["bus"="yes"]({{BOX}});
    way["public_transport"="stop_position"]["bus"="yes"]({{BOX}});
    node["public_transport"="platform"]["bus"="yes"]({{BOX}});
    way["public_transport"="platform"]["bus"="yes"]({{BOX}});
  `,
  GIVE_WAY: `
    node["highway"="give_way"]({{BOX}});
    way["highway"="give_way"]({{BOX}});
    node["give_way"="yes"]({{BOX}});
    way["give_way"="yes"]({{BOX}});
    node["traffic_sign"~"give[_ ]?way", i]({{BOX}});
    way["traffic_sign"~"give[_ ]?way", i]({{BOX}});
  `,
  SPEED_CAMERA: `
    node["highway"="speed_camera"]({{BOX}});
    way["highway"="speed_camera"]({{BOX}});
    node["enforcement"="speed_camera"]({{BOX}});
    way["enforcement"="speed_camera"]({{BOX}});
    node["speed_camera"="yes"]({{BOX}});
    way["speed_camera"="yes"]({{BOX}});
    node["camera:speed"="yes"]({{BOX}});
    way["camera:speed"="yes"]({{BOX}});
  `
};

function parseHazardTypes(rawTypes) {
  if (!rawTypes) return [...DEFAULT_HAZARD_TYPES];
  const joined = Array.isArray(rawTypes) ? rawTypes.join(",") : String(rawTypes);
  const parsed = joined
    .split(",")
    .map((type) => String(type || "").trim().toUpperCase())
    .filter((type) => ALLOWED_HAZARD_TYPES.has(type));
  if (parsed.length === 0) return [...DEFAULT_HAZARD_TYPES];
  return [...new Set(parsed)];
}

function parseBbox(rawBbox) {
  const south = Number(rawBbox?.south);
  const west = Number(rawBbox?.west);
  const north = Number(rawBbox?.north);
  const east = Number(rawBbox?.east);

  if (![south, west, north, east].every((value) => Number.isFinite(value))) {
    return { ok: false, message: "south, west, north, east must be valid numbers." };
  }
  if (south >= north || west >= east) {
    return { ok: false, message: "Invalid bbox ordering. Expected south<north and west<east." };
  }
  if (south < -90 || north > 90 || west < -180 || east > 180) {
    return { ok: false, message: "Bbox is out of valid latitude/longitude range." };
  }

  const latSpan = north - south;
  const lonSpan = east - west;
  if (latSpan > 2.0 || lonSpan > 2.0) {
    return { ok: false, message: "Bbox too large. Reduce route area before requesting hazards." };
  }

  return {
    ok: true,
    bbox: { south, west, north, east }
  };
}

function buildOverpassUnionQuery(types, bbox) {
  const box = `${bbox.south},${bbox.west},${bbox.north},${bbox.east}`;
  const snippets = types
    .map((type) => OVERPASS_SNIPPETS_BY_TYPE[type] || "")
    .filter((snippet) => snippet.trim().length > 0)
    .map((snippet) => snippet.replaceAll("{{BOX}}", box))
    .join("\n");

  return `
[out:json][timeout:25];
(
  ${snippets}
);
out body geom;
`.trim();
}

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, "utf-8"));
}

function writeJson(filePath, payload) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  fs.writeFileSync(filePath, JSON.stringify(payload, null, 2), "utf-8");
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
    try {
      const stat = fs.statSync(cachePath);
      const cachedPayload = readJson(cachePath);
      staleCachePayload = cachedPayload;
      if (Date.now() - stat.mtimeMs < OVERPASS_CACHE_TTL_MS) {
        return cachedPayload;
      }
    } catch (_error) {
      // Ignore stale cache read errors and proceed to network fetch.
    }
  }

  let lastError = null;
  for (let attempt = 1; attempt <= OVERPASS_RETRIES; attempt += 1) {
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
        const message = await response.text();
        throw new Error(`Overpass failed: ${response.status} ${String(message).slice(0, 300)}`);
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
    console.warn(`Using stale route Overpass cache for ${cacheKey}: ${lastError?.message || "unknown error"}`);
    return staleCachePayload;
  }

  console.warn(`Route Overpass unavailable for ${cacheKey}; returning empty hazard set.`);
  return { elements: [] };
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

function hasExplicitBuswayTag(tags) {
  if (!tags || typeof tags !== "object") return false;
  return Boolean(tags.busway || tags["busway:left"] || tags["busway:right"]);
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

function resolveFeatureTypes(tags) {
  const types = new Set();
  if (tags.highway === "traffic_signals") types.add("TRAFFIC_SIGNAL");
  if (
    tags.crossing === "zebra" ||
    tags.crossing_ref === "zebra" ||
    (tags.highway === "crossing" && (tags.crossing === "zebra" || tags.crossing_ref === "zebra"))
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

function mapOverpassToHazards(overpassPayload, allowedTypes) {
  const hazards = [];
  const seen = new Set();
  const allowed = new Set(allowedTypes);
  const elements = Array.isArray(overpassPayload?.elements) ? overpassPayload.elements : [];

  for (const element of elements) {
    const tags = element.tags || {};
    const types = resolveFeatureTypes(tags).filter((type) => allowed.has(type));
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

async function queryRouteHazardsByBbox({ bbox, types }) {
  const query = buildOverpassUnionQuery(types, bbox);
  const sortedTypes = [...types].sort().join(",");
  const cacheKey = [
    "route",
    sortedTypes,
    bbox.south.toFixed(5),
    bbox.west.toFixed(5),
    bbox.north.toFixed(5),
    bbox.east.toFixed(5)
  ].join(":");
  const payload = await fetchOverpassWithCache(cacheKey, query);
  return mapOverpassToHazards(payload, types);
}

module.exports = {
  DEFAULT_HAZARD_TYPES,
  parseHazardTypes,
  parseBbox,
  queryRouteHazardsByBbox
};

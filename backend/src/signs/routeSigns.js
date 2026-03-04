const crypto = require("crypto");
const fs = require("fs");
const path = require("path");

const ROOT_DIR = path.join(__dirname, "..", "..");
const DATA_DIR = path.join(ROOT_DIR, "data");
const CACHE_DIR = path.join(DATA_DIR, "cache", "overpass-signs");
const OVERPASS_URL = "https://overpass-api.de/api/interpreter";
const OVERPASS_CACHE_TTL_MS = 10 * 60 * 1000;
const OVERPASS_RETRIES = 3;
const REQUEST_INTERVAL_MS = 600;
let lastRequestAt = 0;

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
    return { ok: false, message: "Bbox too large. Reduce route area before requesting signs." };
  }

  return {
    ok: true,
    bbox: { south, west, north, east }
  };
}

function buildOverpassQuery(bbox) {
  const box = `${bbox.south},${bbox.west},${bbox.north},${bbox.east}`;
  return `
[out:json][timeout:25];
(
  node["traffic_sign"](${box});
  way["traffic_sign"](${box});
  relation["traffic_sign"](${box});
  node["traffic_sign:forward"](${box});
  way["traffic_sign:forward"](${box});
  node["traffic_sign:backward"](${box});
  way["traffic_sign:backward"](${box});
  node["traffic_sign:both"](${box});
  way["traffic_sign:both"](${box});
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
    console.warn(`Using stale sign Overpass cache for ${cacheKey}: ${lastError?.message || "unknown error"}`);
    return staleCachePayload;
  }

  console.warn(`Sign Overpass unavailable for ${cacheKey}; returning empty sign set.`);
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

function extractSignValues(tags) {
  if (!tags || typeof tags !== "object") return [];
  const rawValues = [];
  for (const [key, value] of Object.entries(tags)) {
    if (!key.startsWith("traffic_sign")) continue;
    if (typeof value === "string" && value.trim().length > 0) {
      rawValues.push(value);
    }
  }
  const splitValues = rawValues
    .flatMap((value) =>
      value
        .split(/[;|,]/g)
        .map((token) => token.trim())
        .filter((token) => token.length > 0)
    )
    .filter((token) => token.toLowerCase() !== "yes");

  return [...new Set(splitValues)];
}

function mapOverpassToSigns(overpassPayload) {
  const signs = [];
  const seen = new Set();
  const elements = Array.isArray(overpassPayload?.elements) ? overpassPayload.elements : [];

  for (const element of elements) {
    const tags = element.tags || {};
    const signValues = extractSignValues(tags);
    if (signValues.length === 0) continue;

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

    const id = `sign:${element.type}:${element.id}`;
    if (seen.has(id)) continue;
    seen.add(id);
    signs.push({
      id,
      lat,
      lon,
      signValues,
      tags,
      source: "overpass"
    });
  }
  return signs;
}

async function queryRouteSignsByBbox({ bbox }) {
  const query = buildOverpassQuery(bbox);
  const cacheKey = [
    "signs",
    bbox.south.toFixed(5),
    bbox.west.toFixed(5),
    bbox.north.toFixed(5),
    bbox.east.toFixed(5)
  ].join(":");
  const payload = await fetchOverpassWithCache(cacheKey, query);
  return mapOverpassToSigns(payload);
}

module.exports = {
  parseBbox,
  queryRouteSignsByBbox
};

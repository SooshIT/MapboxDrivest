const express = require("express");
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");
const { Pool } = require("pg");
const rateLimit = require("express-rate-limit");
const {
  parseHazardTypes,
  parseBbox,
  queryRouteHazardsByBbox
} = require("./hazards/routeHazards");

const DATA_DIR = path.join(__dirname, "..", "data");
const ROUTES_DIR = path.join(DATA_DIR, "routes");
const HAZARDS_DIR = path.join(DATA_DIR, "hazards");
const SQL_DIR = path.join(__dirname, "..", "sql");
const telemetryEvents = [];
const instructorSessions = [];
const TELEMETRY_ALLOWED_EVENT_TYPES = new Set([
  "session_summary",
  "prompt_fired",
  "prompt_suppressed",
  "off_route_enter",
  "off_route_exit"
]);
const TELEMETRY_MAX_PAYLOAD_BYTES = 16 * 1024;

const databaseUrl = process.env.DATABASE_URL || "";
const pool = databaseUrl ? new Pool({ connectionString: databaseUrl }) : null;
let schemaReady = false;

const app = express();
app.use(express.json({ limit: "1mb" }));
app.use((req, res, next) => {
  const startedAtMs = Date.now();
  res.on("finish", () => {
    const logPayload = {
      path: req.path,
      centreId: res.locals.centreId ?? null,
      packType: res.locals.packType ?? null,
      version: res.locals.packVersion ?? null,
      latencyMs: Date.now() - startedAtMs
    };
    console.log(JSON.stringify(logPayload));
  });
  next();
});

const packsRateLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 60,
  standardHeaders: true,
  legacyHeaders: false
});

const telemetryRateLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 30,
  standardHeaders: true,
  legacyHeaders: false
});

const asyncHandler = (handler) => (req, res, next) =>
  Promise.resolve(handler(req, res, next)).catch(next);

function readJsonFile(filePath) {
  const text = fs.readFileSync(filePath, "utf-8");
  return JSON.parse(text);
}

async function ensureDbSchema() {
  if (!pool || schemaReady) return;
  const migrationFiles = fs
    .readdirSync(SQL_DIR)
    .filter((name) => name.endsWith(".sql"))
    .sort((a, b) => a.localeCompare(b));

  for (const fileName of migrationFiles) {
    const sqlPath = path.join(SQL_DIR, fileName);
    const sqlText = fs.readFileSync(sqlPath, "utf-8");
    await pool.query(sqlText);
  }
  schemaReady = true;
}

function getCentresFromFile() {
  const file = path.join(DATA_DIR, "centres.json");
  return readJsonFile(file);
}

async function getCentresFromDb() {
  if (!pool) return [];
  await ensureDbSchema();
  const result = await pool.query(
    `SELECT id, name, address, lat, lon
     FROM centres
     ORDER BY name ASC`
  );
  return result.rows;
}

async function loadPackFromRegistry(packType, centreId = null, req = null) {
  if (!pool) return null;
  await ensureDbSchema();
  const result = await pool.query(
    `SELECT
      centre_id,
      pack_type,
      version,
      url_or_inline,
      generated_at,
      COALESCE(canary, FALSE) AS canary,
      LEAST(GREATEST(COALESCE(rollout_percent, 100), 0), 100)::INT AS rollout_percent
     FROM pack_registry
     WHERE pack_type = $1
       AND (($2::text IS NULL AND centre_id IS NULL) OR centre_id = $2)
     ORDER BY generated_at DESC`,
    [packType, centreId]
  );
  if (result.rowCount === 0) return null;
  const row = selectPackRegistryRow(result.rows, packType, centreId, req);
  if (!row) return null;
  try {
    return {
      payload: JSON.parse(row.url_or_inline),
      version: row.version || null,
      canary: Boolean(row.canary),
      rolloutPercent: toInt(row.rollout_percent, 100)
    };
  } catch (_error) {
    return null;
  }
}

function selectPackRegistryRow(rows, packType, centreId, req) {
  if (!Array.isArray(rows) || rows.length === 0) return null;
  const nonCanary = rows.find((row) => !row.canary) || null;
  const canary = rows.find((row) => Boolean(row.canary)) || null;
  if (!canary) {
    return nonCanary || rows[0];
  }

  const rolloutPercent = Math.min(100, Math.max(0, toInt(canary.rollout_percent, 100)));
  if (rolloutPercent <= 0) return nonCanary || null;
  if (rolloutPercent >= 100) return canary;

  const rolloutKey = resolveRolloutKey(req, packType, centreId, canary.version);
  const rolloutBucket = hashToPercent(rolloutKey);
  if (rolloutBucket < rolloutPercent) {
    return canary;
  }
  return nonCanary || canary;
}

function resolveRolloutKey(req, packType, centreId, version) {
  const requestRolloutKey = String(
    req?.header?.("x-drivest-rollout-key") ||
      req?.header?.("x-session-id") ||
      req?.query?.rolloutKey ||
      req?.query?.sessionId ||
      ""
  ).trim();
  if (requestRolloutKey) {
    return requestRolloutKey;
  }
  const userAgent = String(req?.header?.("user-agent") || "unknown-agent");
  return `${userAgent}|${packType}|${centreId || "global"}|${version || "v0"}`;
}

function hashToPercent(input) {
  const hash = crypto.createHash("sha1").update(String(input || "")).digest("hex");
  const prefix = hash.slice(0, 8);
  const value = Number.parseInt(prefix, 16);
  if (!Number.isFinite(value)) return 0;
  return value % 100;
}

async function insertTelemetryEvent(event) {
  if (!pool) return;
  await ensureDbSchema();
  await pool.query(
    `INSERT INTO telemetry_events (
      event_type,
      ts,
      centre_id,
      route_id,
      prompt_type,
      stress_index,
      complexity_score,
      confidence_score,
      off_route_count,
      completion_flag,
      suppressed_flag,
      speed_before_kph,
      speed_after_kph,
      organisation_id,
      payload_json
    )
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15::jsonb)`,
    [
      event.event_type,
      event.ts,
      event.centre_id,
      event.route_id,
      event.prompt_type,
      event.stress_index,
      event.complexity_score,
      event.confidence_score,
      event.off_route_count,
      event.completion_flag,
      event.suppressed_flag,
      event.speed_before_kph,
      event.speed_after_kph,
      event.organisation_id,
      JSON.stringify(event.payload_json)
    ]
  );
}

function extractPackVersions(payloadJson) {
  if (!payloadJson || typeof payloadJson !== "object") return null;
  const raw = payloadJson.pack_versions || payloadJson.packVersions || null;
  if (!raw || typeof raw !== "object") return null;
  return {
    centres: raw.centres || raw.centresPackVersion || null,
    routes: raw.routes || raw.routesPackVersion || null,
    hazards: raw.hazards || raw.hazardsPackVersion || null
  };
}

function extractSessionId(payloadJson, event) {
  if (payloadJson && typeof payloadJson === "object") {
    const direct = payloadJson.session_id || payloadJson.sessionId || null;
    if (typeof direct === "string" && direct.trim()) {
      return direct.trim();
    }
  }
  const routeId = event.route_id || "route_unknown";
  const ts = event.ts || new Date().toISOString();
  return `${routeId}:${ts}`;
}

async function insertSessionPackUsage(event) {
  const versions = extractPackVersions(event.payload_json);
  if (!versions) return;
  if (!versions.centres && !versions.routes && !versions.hazards) return;

  if (!pool) return;
  await ensureDbSchema();
  const sessionId = extractSessionId(event.payload_json, event);
  await pool.query(
    `INSERT INTO session_pack_usage (
      organisation_id,
      centre_id,
      session_id,
      centres_pack_version,
      routes_pack_version,
      hazards_pack_version
    ) VALUES ($1, $2, $3, $4, $5, $6)`,
    [
      event.organisation_id,
      event.centre_id,
      sessionId,
      versions.centres,
      versions.routes,
      versions.hazards
    ]
  );
}

async function insertInstructorSession(session) {
  if (!pool) return;
  await ensureDbSchema();
  await pool.query(
    `INSERT INTO instructor_sessions (
      organisation_id,
      centre_id,
      route_id,
      stress_index,
      off_route_count,
      hazard_counts_json,
      payload_json
    ) VALUES ($1, $2, $3, $4, $5, $6::jsonb, $7::jsonb)`,
    [
      session.organisation_id,
      session.centre_id,
      session.route_id,
      session.stress_index,
      session.off_route_count,
      JSON.stringify(session.hazard_counts_json || {}),
      JSON.stringify(session.payload_json || {})
    ]
  );
}

async function resolveOrganisationId(rawValue) {
  const normalized = String(rawValue || "").trim();
  if (!normalized) return null;
  if (!pool) return normalized;
  await ensureDbSchema();
  const result = await pool.query(
    `SELECT id
     FROM organisations
     WHERE id = $1 OR code = $1
     LIMIT 1`,
    [normalized]
  );
  if (result.rowCount === 0) return normalized;
  return result.rows[0].id;
}

function findCentreById(centreId, centres) {
  return centres.find((centre) => centre.id === centreId);
}

function buildBboxFromCentre(centre) {
  const delta = 0.08;
  return {
    south: Number(centre.lat) - delta,
    west: Number(centre.lon) - delta,
    north: Number(centre.lat) + delta,
    east: Number(centre.lon) + delta
  };
}

function buildMetadata(bbox, version = "v1") {
  return {
    version,
    generatedAt: new Date().toISOString(),
    bbox
  };
}

function buildEtagForPayload(payload) {
  const normalized = typeof payload === "string" ? payload : JSON.stringify(payload);
  const etagSource = toStableEtagSource(normalized);
  const hash = crypto.createHash("sha1").update(etagSource).digest("hex");
  return `"${hash}"`;
}

function toStableEtagSource(payloadText) {
  const parsed = (() => {
    try {
      return JSON.parse(payloadText);
    } catch (_error) {
      return null;
    }
  })();

  if (!parsed || typeof parsed !== "object") {
    return payloadText;
  }

  const metadata = parsed.metadata;
  if (!metadata || typeof metadata !== "object") {
    return payloadText;
  }

  const stablePayload = {
    ...parsed,
    metadata: {
      ...metadata,
      generatedAt: null
    }
  };
  return JSON.stringify(stablePayload);
}

function respondWithPack(req, res, payload, context) {
  const responseBody = typeof payload === "string" ? payload : JSON.stringify(payload);
  const etag = buildEtagForPayload(responseBody);
  const ifNoneMatch = req.header("if-none-match");

  res.locals.centreId = context.centreId ?? null;
  res.locals.packType = context.packType ?? null;
  res.locals.packVersion = context.version ?? null;
  if (context.channel) {
    res.set("X-Drivest-Pack-Channel", context.channel);
  }
  res.set("ETag", etag);
  res.set("Cache-Control", "public, max-age=300");

  if (ifNoneMatch && ifNoneMatch === etag) {
    return res.status(304).end();
  }
  return res.type("application/json").send(responseBody);
}

function parseNullableInt(value) {
  if (value === null || value === undefined || value === "") return null;
  const parsed = Number.parseInt(String(value), 10);
  return Number.isFinite(parsed) ? parsed : null;
}

function parseNullableBoolean(value) {
  if (value === null || value === undefined || value === "") return null;
  if (typeof value === "boolean") return value;
  if (typeof value === "string") {
    const normalized = value.trim().toLowerCase();
    if (normalized === "true") return true;
    if (normalized === "false") return false;
  }
  return null;
}

function sanitizePayloadJson(payloadJson) {
  if (!payloadJson || typeof payloadJson !== "object") return {};
  return redactDisallowedPayloadKeys(payloadJson);
}

function isSensitiveTelemetryKey(normalizedKey) {
  const exactBlockedKeys = new Set([
    "ip",
    "ip_address",
    "device_ip",
    "lat",
    "lon",
    "latitude",
    "longitude",
    "location",
    "locations",
    "coords",
    "coordinates",
    "geometry",
    "route_geometry",
    "routegeometry",
    "polyline",
    "route_polyline",
    "routepolyline",
    "device_id",
    "deviceid",
    "advertising_id",
    "android_id",
    "idfa",
    "idfv",
    "gaid",
    "imei",
    "imsi",
    "serial"
  ]);
  if (exactBlockedKeys.has(normalizedKey)) return true;
  if (normalizedKey.includes("geometry")) return true;
  if (normalizedKey.includes("polyline")) return true;
  if (normalizedKey.includes("coordinate")) return true;
  if (normalizedKey.includes("device_id")) return true;
  if (normalizedKey.includes("advertising_id")) return true;
  return false;
}

function getPayloadSizeBytes(payload) {
  if (payload === undefined) return 0;
  try {
    return Buffer.byteLength(JSON.stringify(payload), "utf8");
  } catch (_error) {
    return TELEMETRY_MAX_PAYLOAD_BYTES + 1;
  }
}

function redactDisallowedPayloadKeys(input) {
  if (Array.isArray(input)) {
    return input.map((value) => redactDisallowedPayloadKeys(value));
  }
  if (!input || typeof input !== "object") {
    return input;
  }

  const redacted = {};
  for (const [key, value] of Object.entries(input)) {
    const normalized = key.toLowerCase();
    if (isSensitiveTelemetryKey(normalized)) {
      continue;
    }
    redacted[key] = redactDisallowedPayloadKeys(value);
  }
  return redacted;
}

function buildTelemetryEvent(payload) {
  const payloadJson = sanitizePayloadJson(payload.payload_json || payload.payload || {});
  const eventType = payload.event_type || payload.eventType;
  const completionFlagRaw =
    payload.completion_flag ??
    payload.completionFlag ??
    payloadJson.completion_flag ??
    payloadJson.completionFlag ??
    payloadJson.completed;
  const suppressedFlagRaw =
    payload.suppressed_flag ??
    payload.suppressedFlag ??
    payloadJson.suppressed_flag ??
    payloadJson.suppressedFlag ??
    payloadJson.suppressed;

  return {
    id: telemetryEvents.length + 1,
    event_type: eventType,
    ts: payload.ts || new Date().toISOString(),
    centre_id: payload.centre_id || payload.centreId || null,
    route_id: payload.route_id || payload.routeId || null,
    organisation_id:
      payload.organisation_id ||
      payload.organisationId ||
      payloadJson.organisation_id ||
      payloadJson.organisationId ||
      null,
    prompt_type:
      payload.prompt_type ||
      payload.promptType ||
      payloadJson.prompt_type ||
      payloadJson.promptType ||
      null,
    stress_index: parseNullableInt(
      payload.stress_index ?? payload.stressIndex ?? payloadJson.stress_index ?? payloadJson.stressIndex
    ),
    complexity_score: parseNullableInt(
      payload.complexity_score ??
        payload.complexityScore ??
        payloadJson.complexity_score ??
        payloadJson.complexityScore
    ),
    confidence_score: parseNullableInt(
      payload.confidence_score ??
        payload.confidenceScore ??
        payloadJson.confidence_score ??
        payloadJson.confidenceScore
    ),
    off_route_count: parseNullableInt(
      payload.off_route_count ??
        payload.offRouteCount ??
        payloadJson.off_route_count ??
        payloadJson.offRouteCount
    ),
    completion_flag: parseNullableBoolean(completionFlagRaw),
    suppressed_flag: parseNullableBoolean(suppressedFlagRaw),
    speed_before_kph: parseNullableInt(
      payload.speed_before_kph ??
        payload.speedBeforeKph ??
        payloadJson.speed_before_kph ??
        payloadJson.speedBeforeKph
    ),
    speed_after_kph: parseNullableInt(
      payload.speed_after_kph ??
        payload.speedAfterKph ??
        payloadJson.speed_after_kph ??
        payloadJson.speedAfterKph
    ),
    payload_json: payloadJson
  };
}

function toNumber(value, fallback = 0) {
  if (value === null || value === undefined || value === "") return fallback;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function toInt(value, fallback = 0) {
  return Math.trunc(toNumber(value, fallback));
}

function toRate(value) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return 0;
  return parsed;
}

function parsePagination(query) {
  const page = Math.max(1, toInt(query.page, 1));
  const pageSize = Math.min(200, Math.max(1, toInt(query.pageSize, 50)));
  const offset = (page - 1) * pageSize;
  return { page, pageSize, offset };
}

function toHazardAccuracyRow(row) {
  return {
    centreId: row.centre_id,
    promptType: row.prompt_type,
    totalFired: toInt(row.total_fired),
    totalSuppressed: toInt(row.total_suppressed),
    suppressionRate: toRate(row.suppression_rate),
    avgSpeedDeltaKph: toNumber(row.avg_speed_delta_kph, 0)
  };
}

function toRouteReliabilityRow(row) {
  return {
    centreId: row.centre_id,
    routeId: row.route_id,
    sessions: toInt(row.sessions),
    completionRate: toRate(row.completion_rate),
    avgOffRouteCount: toNumber(row.avg_off_route_count, 0),
    avgStressIndex: toNumber(row.avg_stress_index, 0),
    avgConfidenceScore: toNumber(row.avg_confidence_score, 0)
  };
}

function toConfidenceDistributionRow(row, centreId) {
  if (!row) {
    return {
      centreId,
      countLowConfidence: 0,
      countMidConfidence: 0,
      countHighConfidence: 0
    };
  }
  return {
    centreId: row.centre_id || centreId,
    countLowConfidence: toInt(row.count_low_confidence),
    countMidConfidence: toInt(row.count_mid_confidence),
    countHighConfidence: toInt(row.count_high_confidence)
  };
}

async function loadAnalyticsFromDb(centreId, pagination) {
  if (!pool) return null;
  await ensureDbSchema();

  const hazardAccuracyResult = await pool.query(
    `SELECT centre_id, prompt_type, total_fired, total_suppressed, suppression_rate, avg_speed_delta_kph
     FROM hazard_accuracy_view
     WHERE centre_id = $1
     ORDER BY prompt_type ASC`,
    [centreId]
  );

  const routeTotalResult = await pool.query(
    `SELECT COUNT(*)::INT AS total
     FROM route_reliability_view
     WHERE centre_id = $1`,
    [centreId]
  );

  const routeReliabilityResult = await pool.query(
    `SELECT centre_id, route_id, sessions, completion_rate, avg_off_route_count, avg_stress_index, avg_confidence_score
     FROM route_reliability_view
     WHERE centre_id = $1
     ORDER BY sessions DESC, route_id ASC
     LIMIT $2 OFFSET $3`,
    [centreId, pagination.pageSize, pagination.offset]
  );

  const confidenceDistributionResult = await pool.query(
    `SELECT centre_id, count_low_confidence, count_mid_confidence, count_high_confidence
     FROM confidence_distribution_view
     WHERE centre_id = $1
     LIMIT 1`,
    [centreId]
  );

  return {
    hazardAccuracy: hazardAccuracyResult.rows.map(toHazardAccuracyRow),
    routeReliability: routeReliabilityResult.rows.map(toRouteReliabilityRow),
    routeReliabilityTotal: toInt(routeTotalResult.rows[0]?.total, 0),
    confidenceDistribution: toConfidenceDistributionRow(confidenceDistributionResult.rows[0], centreId)
  };
}

function loadAnalyticsFromMemory(centreId, pagination) {
  const filtered = telemetryEvents.filter((event) => event.centre_id === centreId);

  const hazardMap = new Map();
  for (const event of filtered) {
    const promptType = event.prompt_type;
    if (!promptType) continue;
    const key = `${centreId}:${promptType}`;
    const current = hazardMap.get(key) || {
      centreId,
      promptType,
      totalFired: 0,
      totalSuppressed: 0,
      speedDeltaSum: 0,
      speedDeltaCount: 0
    };
    if (event.event_type === "prompt_fired") {
      current.totalFired += 1;
      if (event.speed_before_kph !== null && event.speed_after_kph !== null) {
        current.speedDeltaSum += (event.speed_before_kph - event.speed_after_kph);
        current.speedDeltaCount += 1;
      }
    }
    if (event.event_type === "prompt_suppressed" || event.suppressed_flag === true) {
      current.totalSuppressed += 1;
    }
    hazardMap.set(key, current);
  }

  const hazardAccuracy = [...hazardMap.values()]
    .map((row) => {
      const total = row.totalFired + row.totalSuppressed;
      return {
        centreId: row.centreId,
        promptType: row.promptType,
        totalFired: row.totalFired,
        totalSuppressed: row.totalSuppressed,
        suppressionRate: total > 0 ? row.totalSuppressed / total : 0,
        avgSpeedDeltaKph: row.speedDeltaCount > 0 ? row.speedDeltaSum / row.speedDeltaCount : 0
      };
    })
    .sort((a, b) => a.promptType.localeCompare(b.promptType));

  const sessionEvents = filtered.filter((event) => event.event_type === "session_summary" && event.route_id);
  const routeMap = new Map();
  for (const event of sessionEvents) {
    const key = `${event.centre_id}:${event.route_id}`;
    const current = routeMap.get(key) || {
      centreId: event.centre_id,
      routeId: event.route_id,
      sessions: 0,
      completedSum: 0,
      offRouteSum: 0,
      offRouteCount: 0,
      stressSum: 0,
      stressCount: 0,
      confidenceSum: 0,
      confidenceCount: 0
    };
    current.sessions += 1;
    if (event.completion_flag !== null) {
      current.completedSum += event.completion_flag ? 1 : 0;
    }
    if (event.off_route_count !== null) {
      current.offRouteSum += event.off_route_count;
      current.offRouteCount += 1;
    }
    if (event.stress_index !== null) {
      current.stressSum += event.stress_index;
      current.stressCount += 1;
    }
    if (event.confidence_score !== null) {
      current.confidenceSum += event.confidence_score;
      current.confidenceCount += 1;
    }
    routeMap.set(key, current);
  }

  const allRouteReliability = [...routeMap.values()]
    .map((row) => ({
      centreId: row.centreId,
      routeId: row.routeId,
      sessions: row.sessions,
      completionRate: row.sessions > 0 ? row.completedSum / row.sessions : 0,
      avgOffRouteCount: row.offRouteCount > 0 ? row.offRouteSum / row.offRouteCount : 0,
      avgStressIndex: row.stressCount > 0 ? row.stressSum / row.stressCount : 0,
      avgConfidenceScore: row.confidenceCount > 0 ? row.confidenceSum / row.confidenceCount : 0
    }))
    .sort((a, b) => {
      if (b.sessions !== a.sessions) return b.sessions - a.sessions;
      return a.routeId.localeCompare(b.routeId);
    });

  const routeReliability = allRouteReliability.slice(pagination.offset, pagination.offset + pagination.pageSize);

  const confidenceDistribution = sessionEvents.reduce(
    (acc, event) => {
      const score = toInt(event.confidence_score, -1);
      if (score >= 0 && score <= 39) acc.countLowConfidence += 1;
      else if (score >= 40 && score <= 69) acc.countMidConfidence += 1;
      else if (score >= 70 && score <= 100) acc.countHighConfidence += 1;
      return acc;
    },
    {
      centreId,
      countLowConfidence: 0,
      countMidConfidence: 0,
      countHighConfidence: 0
    }
  );

  return {
    hazardAccuracy,
    routeReliability,
    routeReliabilityTotal: allRouteReliability.length,
    confidenceDistribution
  };
}

async function loadOrganisationRowFromDb(organisationIdOrCode) {
  if (!pool) return null;
  await ensureDbSchema();
  const result = await pool.query(
    `SELECT id, code, name
     FROM organisations
     WHERE id = $1 OR code = $1
     LIMIT 1`,
    [organisationIdOrCode]
  );
  if (result.rowCount === 0) return null;
  return result.rows[0];
}

function buildOrganisationAliases(organisation) {
  return [...new Set([organisation?.id, organisation?.code].filter(Boolean))];
}

async function loadOrganisationStatsFromDb(organisation, aliases) {
  if (!pool) return null;
  await ensureDbSchema();

  const sessionSummaryResult = await pool.query(
    `SELECT
      COUNT(*)::INT AS session_count,
      COUNT(DISTINCT centre_id)::INT AS centre_count,
      COUNT(DISTINCT route_id)::INT AS route_count,
      ROUND(AVG(stress_index::NUMERIC), 2) AS avg_stress_index,
      ROUND(AVG(confidence_score::NUMERIC), 2) AS avg_confidence_score,
      ROUND(AVG(off_route_count::NUMERIC), 2) AS avg_off_route_count,
      MAX(ts) AS last_session_at
     FROM telemetry_events
     WHERE event_type = 'session_summary'
       AND organisation_id = ANY($1::TEXT[])`,
    [aliases]
  );

  const instructorResult = await pool.query(
    `SELECT
      COUNT(*)::INT AS instructor_session_count,
      MAX(created_at) AS last_instructor_session_at
     FROM instructor_sessions
     WHERE organisation_id = ANY($1::TEXT[])`,
    [aliases]
  );

  const packUsageResult = await pool.query(
    `SELECT COUNT(*)::INT AS sessions_with_pack_usage
     FROM session_pack_usage
     WHERE organisation_id = ANY($1::TEXT[])`,
    [aliases]
  );

  const summary = sessionSummaryResult.rows[0] || {};
  const instructor = instructorResult.rows[0] || {};
  const packUsage = packUsageResult.rows[0] || {};
  return {
    organisation: {
      id: organisation.id,
      code: organisation.code,
      name: organisation.name
    },
    sessions: {
      sessionCount: toInt(summary.session_count),
      centresCovered: toInt(summary.centre_count),
      routesCovered: toInt(summary.route_count),
      avgStressIndex: toNumber(summary.avg_stress_index, 0),
      avgConfidenceScore: toNumber(summary.avg_confidence_score, 0),
      avgOffRouteCount: toNumber(summary.avg_off_route_count, 0),
      lastSessionAt: summary.last_session_at || null,
      sessionsWithPackUsage: toInt(packUsage.sessions_with_pack_usage, 0)
    },
    instructor: {
      sessionCount: toInt(instructor.instructor_session_count, 0),
      lastSessionAt: instructor.last_instructor_session_at || null
    }
  };
}

function loadOrganisationStatsFromMemory(organisation) {
  const aliases = buildOrganisationAliases(organisation);
  const sessionEvents = telemetryEvents.filter(
    (event) => event.event_type === "session_summary" && aliases.includes(event.organisation_id)
  );
  const instructor = instructorSessions.filter((session) => aliases.includes(session.organisation_id));
  const centresCovered = new Set(sessionEvents.map((event) => event.centre_id).filter(Boolean)).size;
  const routesCovered = new Set(sessionEvents.map((event) => event.route_id).filter(Boolean)).size;
  const stressValues = sessionEvents.map((event) => event.stress_index).filter((value) => value !== null);
  const confidenceValues = sessionEvents.map((event) => event.confidence_score).filter((value) => value !== null);
  const offRouteValues = sessionEvents.map((event) => event.off_route_count).filter((value) => value !== null);

  const average = (values) => {
    if (!values.length) return 0;
    return values.reduce((sum, value) => sum + Number(value), 0) / values.length;
  };

  const lastTelemetry = sessionEvents.length ? sessionEvents[sessionEvents.length - 1].ts : null;
  const lastInstructor = instructor.length ? instructor[instructor.length - 1].created_at : null;

  return {
    organisation: {
      id: organisation.id,
      code: organisation.code,
      name: organisation.name
    },
    sessions: {
      sessionCount: sessionEvents.length,
      centresCovered,
      routesCovered,
      avgStressIndex: average(stressValues),
      avgConfidenceScore: average(confidenceValues),
      avgOffRouteCount: average(offRouteValues),
      lastSessionAt: lastTelemetry,
      sessionsWithPackUsage: 0
    },
    instructor: {
      sessionCount: instructor.length,
      lastSessionAt: lastInstructor
    }
  };
}

function loadRoutesPackFromFile(centreId, centres) {
  const centre = findCentreById(centreId, centres);
  if (!centre) return null;
  const routesPath = path.join(ROUTES_DIR, `${centreId}.json`);
  if (!fs.existsSync(routesPath)) return null;
  const routesData = readJsonFile(routesPath);
  return {
    metadata: buildMetadata(buildBboxFromCentre(centre), "routes-v1"),
    centreId,
    routes: routesData.routes || []
  };
}

function loadHazardsPackFromFile(centreId, centres) {
  const centre = findCentreById(centreId, centres);
  if (!centre) return null;
  const hazardsPath = path.join(HAZARDS_DIR, `${centreId}.json`);
  const hazardsData = fs.existsSync(hazardsPath)
    ? readJsonFile(hazardsPath)
    : { hazards: [] };
  return {
    metadata: buildMetadata(buildBboxFromCentre(centre), "hazards-v1"),
    centreId,
    hazards: hazardsData.hazards || []
  };
}

app.get("/health", (_req, res) => {
  res.json({ ok: true, service: "drivest-core-api" });
});

app.get(
  "/centres",
  packsRateLimiter,
  asyncHandler(async (req, res) => {
    const registryPack = await loadPackFromRegistry("centres", null, req);
    if (registryPack) {
      return respondWithPack(req, res, registryPack.payload, {
        centreId: null,
        packType: "centres",
        version: registryPack.version || registryPack.payload?.metadata?.version || null,
        channel: registryPack.canary ? "canary" : "stable"
      });
    }

    const dbCentres = await getCentresFromDb();
    const centres = dbCentres.length > 0 ? dbCentres : getCentresFromFile();
    const firstCentre = centres[0];
    const bbox = firstCentre ? buildBboxFromCentre(firstCentre) : { south: 0, west: 0, north: 0, east: 0 };
    const responsePayload = {
      metadata: buildMetadata(bbox, "centres-v1"),
      centres
    };
    return respondWithPack(req, res, responsePayload, {
      centreId: null,
      packType: "centres",
      version: responsePayload.metadata.version
    });
  })
);

app.get(
  "/centres/:id/routes",
  packsRateLimiter,
  asyncHandler(async (req, res) => {
    const registryPack = await loadPackFromRegistry("routes", req.params.id, req);
    if (registryPack) {
      return respondWithPack(req, res, registryPack.payload, {
        centreId: req.params.id,
        packType: "routes",
        version: registryPack.version || registryPack.payload?.metadata?.version || null,
        channel: registryPack.canary ? "canary" : "stable"
      });
    }

    const centres = getCentresFromFile();
    const filePack = loadRoutesPackFromFile(req.params.id, centres);
    if (!filePack) {
      return res.status(404).json({ error: "Routes pack not found", centreId: req.params.id });
    }
    return respondWithPack(req, res, filePack, {
      centreId: req.params.id,
      packType: "routes",
      version: filePack?.metadata?.version ?? null
    });
  })
);

app.get(
  "/centres/:id/hazards",
  packsRateLimiter,
  asyncHandler(async (req, res) => {
    const registryPack = await loadPackFromRegistry("hazards", req.params.id, req);
    if (registryPack) {
      return respondWithPack(req, res, registryPack.payload, {
        centreId: req.params.id,
        packType: "hazards",
        version: registryPack.version || registryPack.payload?.metadata?.version || null,
        channel: registryPack.canary ? "canary" : "stable"
      });
    }

    const centres = getCentresFromFile();
    const filePack = loadHazardsPackFromFile(req.params.id, centres);
    if (!filePack) {
      return res.status(404).json({ error: "Hazards pack not found", centreId: req.params.id });
    }
    return respondWithPack(req, res, filePack, {
      centreId: req.params.id,
      packType: "hazards",
      version: filePack?.metadata?.version ?? null
    });
  })
);

app.get(
  "/hazards/route",
  packsRateLimiter,
  asyncHandler(async (req, res) => {
    const parsedBbox = parseBbox(req.query || {});
    if (!parsedBbox.ok) {
      return res.status(400).json({ error: parsedBbox.message });
    }

    const types = parseHazardTypes(req.query?.types);
    const centreId = String(req.query?.centreId || "").trim() || "route-runtime";

    try {
      const hazards = await queryRouteHazardsByBbox({
        bbox: parsedBbox.bbox,
        types
      });
      const payload = {
        metadata: buildMetadata(parsedBbox.bbox, "hazards-route-v1"),
        centreId,
        hazards
      };
      return respondWithPack(req, res, payload, {
        centreId,
        packType: "hazards_route",
        version: payload.metadata.version
      });
    } catch (error) {
      return res.status(502).json({
        error: "Route hazards query failed",
        detail: String(error?.message || "Unknown error")
      });
    }
  })
);

app.get("/config", (_req, res) => {
  res.json({
    metadata: {
      version: "config-v1",
      generatedAt: new Date().toISOString(),
      bbox: { south: 0, west: 0, north: 0, east: 0 }
    },
    settings: {
      useBackendPacksDefault: false,
      telemetryEnabled: true
    }
  });
});

app.post(
  "/telemetry",
  telemetryRateLimiter,
  asyncHandler(async (req, res) => {
    const payload = req.body || {};
    if (typeof payload !== "object" || Array.isArray(payload)) {
      return res.status(400).json({ error: "Invalid payload" });
    }

    if (getPayloadSizeBytes(payload) > TELEMETRY_MAX_PAYLOAD_BYTES) {
      return res.status(413).json({
        error: `Payload too large. Max ${TELEMETRY_MAX_PAYLOAD_BYTES} bytes.`
      });
    }

    const event = buildTelemetryEvent(payload);
    if (!event.event_type) {
      return res.status(400).json({ error: "event_type is required" });
    }

    if (!TELEMETRY_ALLOWED_EVENT_TYPES.has(event.event_type)) {
      return res.status(400).json({
        error: "Unsupported event_type",
        allowed: [...TELEMETRY_ALLOWED_EVENT_TYPES]
      });
    }

    event.organisation_id = await resolveOrganisationId(event.organisation_id);
    telemetryEvents.push(event);
    await insertTelemetryEvent(event);
    if (event.event_type === "session_summary") {
      await insertSessionPackUsage(event);
    }
    res.locals.centreId = event.centre_id || null;
    res.locals.packType = "telemetry";
    return res.status(201).json({ ok: true, event });
  })
);

app.post(
  "/instructor/session",
  telemetryRateLimiter,
  asyncHandler(async (req, res) => {
    const payload = req.body || {};
    if (typeof payload !== "object" || Array.isArray(payload)) {
      return res.status(400).json({ error: "Invalid payload" });
    }

    if (getPayloadSizeBytes(payload) > TELEMETRY_MAX_PAYLOAD_BYTES) {
      return res.status(413).json({
        error: `Payload too large. Max ${TELEMETRY_MAX_PAYLOAD_BYTES} bytes.`
      });
    }

    const hazardCounts =
      payload.hazardCounts && typeof payload.hazardCounts === "object" && !Array.isArray(payload.hazardCounts)
        ? sanitizePayloadJson(payload.hazardCounts)
        : {};
    const extraPayload =
      payload.payload && typeof payload.payload === "object" && !Array.isArray(payload.payload)
        ? sanitizePayloadJson(payload.payload)
        : {};
    const organisationId = await resolveOrganisationId(payload.organisationId || payload.organisation_id || null);

    const session = {
      organisation_id: organisationId,
      centre_id: payload.centreId || payload.centre_id || null,
      route_id: payload.routeId || payload.route_id || null,
      stress_index: parseNullableInt(payload.stressIndex ?? payload.stress_index),
      off_route_count: parseNullableInt(payload.offRouteCount ?? payload.off_route_count),
      hazard_counts_json: hazardCounts,
      payload_json: extraPayload,
      created_at: new Date().toISOString()
    };

    instructorSessions.push(session);
    await insertInstructorSession(session);
    res.locals.centreId = session.centre_id || null;
    res.locals.packType = "instructor";
    return res.status(201).json({ ok: true, session });
  })
);

app.get(
  "/analytics/centre/:id",
  packsRateLimiter,
  asyncHandler(async (req, res) => {
    const centreId = String(req.params.id || "").trim();
    if (!centreId) {
      return res.status(400).json({ error: "centre id is required" });
    }

    const pagination = parsePagination(req.query);
    const analytics =
      (await loadAnalyticsFromDb(centreId, pagination)) ||
      loadAnalyticsFromMemory(centreId, pagination);

    const totalPages =
      analytics.routeReliabilityTotal > 0
        ? Math.ceil(analytics.routeReliabilityTotal / pagination.pageSize)
        : 0;

    res.locals.centreId = centreId;
    res.locals.packType = "analytics";
    return res.json({
      hazardAccuracy: analytics.hazardAccuracy,
      routeReliability: analytics.routeReliability,
      confidenceDistribution: analytics.confidenceDistribution,
      pagination: {
        page: pagination.page,
        pageSize: pagination.pageSize,
        total: analytics.routeReliabilityTotal,
        totalPages
      }
    });
  })
);

app.get(
  "/organisation/:id/stats",
  packsRateLimiter,
  asyncHandler(async (req, res) => {
    const requestedId = String(req.params.id || "").trim();
    if (!requestedId) {
      return res.status(400).json({ error: "organisation id is required" });
    }

    const defaultOrg = {
      id: "org_default",
      code: "DRVST",
      name: "Drivest Default Organisation"
    };
    const organisation =
      (await loadOrganisationRowFromDb(requestedId)) ||
      (requestedId === defaultOrg.id || requestedId.toUpperCase() === defaultOrg.code ? defaultOrg : null);
    if (!organisation) {
      return res.status(404).json({ error: "Organisation not found", organisationId: requestedId });
    }

    const aliases = buildOrganisationAliases(organisation);
    const stats =
      (await loadOrganisationStatsFromDb(organisation, aliases)) ||
      loadOrganisationStatsFromMemory(organisation);

    res.locals.packType = "organisation_stats";
    return res.json(stats);
  })
);

app.use((error, _req, res, _next) => {
  console.error(error);
  res.status(500).json({ error: "Internal server error" });
});

const port = Number(process.env.PORT || 8080);

if (require.main === module) {
  ensureDbSchema()
    .catch((error) => {
      console.error("Schema setup failed:", error);
    })
    .finally(() => {
      app.listen(port, () => {
        console.log(`Drivest Core API listening on port ${port}`);
      });
    });
}

module.exports = { app, ensureDbSchema };

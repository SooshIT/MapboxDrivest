const test = require("node:test");
const assert = require("node:assert/strict");
const request = require("supertest");
const { app } = require("../src/server");

test("GET /centres returns centres pack", async () => {
  const res = await request(app).get("/centres");
  assert.equal(res.statusCode, 200);
  assert.ok(Array.isArray(res.body.centres));
  assert.ok(res.body.metadata?.version);
});

test("GET /centres/:id/routes returns routes pack", async () => {
  const res = await request(app).get("/centres/colchester/routes");
  assert.equal(res.statusCode, 200);
  assert.equal(res.body.centreId, "colchester");
  assert.ok(Array.isArray(res.body.routes));
  assert.ok(res.headers.etag);
});

test("GET /centres/:id/routes supports If-None-Match with 304", async () => {
  const initial = await request(app).get("/centres/colchester/routes");
  assert.equal(initial.statusCode, 200);
  assert.ok(initial.headers.etag);

  const second = await request(app)
    .get("/centres/colchester/routes")
    .set("If-None-Match", initial.headers.etag);

  assert.equal(second.statusCode, 304);
});

test("GET /centres/:id/hazards returns hazards pack", async () => {
  const res = await request(app).get("/centres/colchester/hazards");
  assert.equal(res.statusCode, 200);
  assert.equal(res.body.centreId, "colchester");
  assert.ok(Array.isArray(res.body.hazards));
});

test("GET /hazards/route validates bbox query", async () => {
  const res = await request(app).get("/hazards/route").query({
    south: "bad",
    west: "0",
    north: "1",
    east: "1"
  });
  assert.equal(res.statusCode, 400);
  assert.equal(typeof res.body.error, "string");
});

test("GET /config returns config payload", async () => {
  const res = await request(app).get("/config");
  assert.equal(res.statusCode, 200);
  assert.equal(typeof res.body.settings?.useBackendPacksDefault, "boolean");
});

test("POST /telemetry and GET /analytics/centre/:id returns aggregated telemetry", async () => {
  const centreId = "colchester";
  const routeId = "colchester-hythe-town-loop";
  const organisationId = "org_default";

  const events = [
    {
      event_type: "session_summary",
      centre_id: centreId,
      route_id: routeId,
      organisation_id: organisationId,
      stress_index: 52,
      complexity_score: 60,
      confidence_score: 44,
      off_route_count: 2,
      completion_flag: true,
      payload_json: { source: "test" }
    },
    {
      event_type: "prompt_fired",
      centre_id: centreId,
      route_id: routeId,
      prompt_type: "TRAFFIC_SIGNAL",
      speed_before_kph: 40,
      speed_after_kph: 28,
      payload_json: { source: "test" }
    },
    {
      event_type: "prompt_suppressed",
      centre_id: centreId,
      route_id: routeId,
      prompt_type: "TRAFFIC_SIGNAL",
      suppressed_flag: true,
      payload_json: { source: "test" }
    }
  ];

  for (const event of events) {
    const telemetryRes = await request(app).post("/telemetry").send(event);
    assert.equal(telemetryRes.statusCode, 201);
    assert.equal(telemetryRes.body.ok, true);
  }

  const analyticsRes = await request(app)
    .get(`/analytics/centre/${centreId}`)
    .query({ page: 1, pageSize: 25 });

  assert.equal(analyticsRes.statusCode, 200);
  assert.ok(Array.isArray(analyticsRes.body.hazardAccuracy));
  assert.ok(Array.isArray(analyticsRes.body.routeReliability));
  assert.equal(typeof analyticsRes.body.confidenceDistribution, "object");
  assert.equal(typeof analyticsRes.body.pagination, "object");

  const hazardRow = analyticsRes.body.hazardAccuracy.find((row) => row.promptType === "TRAFFIC_SIGNAL");
  assert.ok(hazardRow);
  assert.equal(typeof hazardRow.totalFired, "number");
  assert.equal(typeof hazardRow.totalSuppressed, "number");
  assert.equal(typeof hazardRow.suppressionRate, "number");
  assert.equal(typeof hazardRow.avgSpeedDeltaKph, "number");

  const routeRow = analyticsRes.body.routeReliability.find((row) => row.routeId === routeId);
  assert.ok(routeRow);
  assert.equal(typeof routeRow.sessions, "number");
  assert.equal(typeof routeRow.completionRate, "number");
  assert.equal(typeof routeRow.avgOffRouteCount, "number");
  assert.equal(typeof routeRow.avgStressIndex, "number");
  assert.equal(typeof routeRow.avgConfidenceScore, "number");

  assert.equal(typeof analyticsRes.body.confidenceDistribution.countLowConfidence, "number");
  assert.equal(typeof analyticsRes.body.confidenceDistribution.countMidConfidence, "number");
  assert.equal(typeof analyticsRes.body.confidenceDistribution.countHighConfidence, "number");
});

test("POST /instructor/session and GET /organisation/:id/stats returns organisation aggregates", async () => {
  const instructorRes = await request(app).post("/instructor/session").send({
    organisationId: "org_default",
    centreId: "colchester",
    routeId: "colchester-hythe-town-loop",
    stressIndex: 58,
    offRouteCount: 3,
    hazardCounts: {
      roundabout: 5,
      trafficSignal: 6
    },
    payload: {
      complexityScore: 67,
      confidenceScore: 48
    }
  });
  assert.equal(instructorRes.statusCode, 201);
  assert.equal(instructorRes.body.ok, true);

  const statsRes = await request(app).get("/organisation/org_default/stats");
  assert.equal(statsRes.statusCode, 200);
  assert.equal(typeof statsRes.body.organisation, "object");
  assert.equal(typeof statsRes.body.sessions, "object");
  assert.equal(typeof statsRes.body.instructor, "object");
  assert.equal(typeof statsRes.body.sessions.sessionCount, "number");
  assert.equal(typeof statsRes.body.sessions.centresCovered, "number");
  assert.equal(typeof statsRes.body.sessions.routesCovered, "number");
  assert.equal(typeof statsRes.body.sessions.avgStressIndex, "number");
  assert.equal(typeof statsRes.body.sessions.avgConfidenceScore, "number");
  assert.equal(typeof statsRes.body.sessions.avgOffRouteCount, "number");
  assert.equal(typeof statsRes.body.instructor.sessionCount, "number");
});

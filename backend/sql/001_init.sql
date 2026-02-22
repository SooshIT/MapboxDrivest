CREATE TABLE IF NOT EXISTS centres (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  address TEXT NOT NULL,
  lat DOUBLE PRECISION NOT NULL,
  lon DOUBLE PRECISION NOT NULL
);

CREATE TABLE IF NOT EXISTS pack_registry (
  id BIGSERIAL PRIMARY KEY,
  centre_id TEXT NULL,
  pack_type TEXT NOT NULL,
  version TEXT NOT NULL,
  url_or_inline TEXT NOT NULL,
  generated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pack_registry_lookup
  ON pack_registry (pack_type, centre_id, generated_at DESC);

CREATE TABLE IF NOT EXISTS telemetry_events (
  id BIGSERIAL PRIMARY KEY,
  event_type TEXT NOT NULL,
  ts TIMESTAMPTZ NOT NULL,
  centre_id TEXT NULL,
  route_id TEXT NULL,
  payload_json JSONB NOT NULL DEFAULT '{}'::jsonb
);

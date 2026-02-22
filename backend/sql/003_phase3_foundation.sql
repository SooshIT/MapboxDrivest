ALTER TABLE IF EXISTS pack_registry
  ADD COLUMN IF NOT EXISTS canary BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS rollout_percent INT NOT NULL DEFAULT 100;

ALTER TABLE IF EXISTS telemetry_events
  ADD COLUMN IF NOT EXISTS organisation_id TEXT NULL;

CREATE TABLE IF NOT EXISTS instructor_sessions (
  id BIGSERIAL PRIMARY KEY,
  organisation_id TEXT NULL,
  centre_id TEXT NULL,
  route_id TEXT NULL,
  stress_index INT NULL,
  off_route_count INT NULL,
  hazard_counts_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_instructor_sessions_centre_route
  ON instructor_sessions (centre_id, route_id, created_at DESC);

CREATE TABLE IF NOT EXISTS session_pack_usage (
  id BIGSERIAL PRIMARY KEY,
  organisation_id TEXT NULL,
  centre_id TEXT NULL,
  session_id TEXT NULL,
  centres_pack_version TEXT NULL,
  routes_pack_version TEXT NULL,
  hazards_pack_version TEXT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_session_pack_usage_centre
  ON session_pack_usage (centre_id, created_at DESC);

ALTER TABLE IF EXISTS instructor_sessions
  ADD COLUMN IF NOT EXISTS organisation_id TEXT NULL;

ALTER TABLE IF EXISTS session_pack_usage
  ADD COLUMN IF NOT EXISTS organisation_id TEXT NULL;

CREATE TABLE IF NOT EXISTS organisations (
  id TEXT PRIMARY KEY,
  code TEXT UNIQUE NOT NULL,
  name TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO organisations (id, code, name)
VALUES ('org_default', 'DRVST', 'Drivest Default Organisation')
ON CONFLICT (id) DO NOTHING;

ALTER TABLE IF EXISTS telemetry_events
  ADD COLUMN IF NOT EXISTS prompt_type TEXT NULL,
  ADD COLUMN IF NOT EXISTS stress_index INT NULL,
  ADD COLUMN IF NOT EXISTS complexity_score INT NULL,
  ADD COLUMN IF NOT EXISTS confidence_score INT NULL,
  ADD COLUMN IF NOT EXISTS off_route_count INT NULL,
  ADD COLUMN IF NOT EXISTS completion_flag BOOLEAN NULL,
  ADD COLUMN IF NOT EXISTS suppressed_flag BOOLEAN NULL,
  ADD COLUMN IF NOT EXISTS speed_before_kph INT NULL,
  ADD COLUMN IF NOT EXISTS speed_after_kph INT NULL,
  ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE INDEX IF NOT EXISTS idx_telemetry_centre_route_event
  ON telemetry_events (centre_id, route_id, event_type);

CREATE INDEX IF NOT EXISTS idx_telemetry_created_at
  ON telemetry_events (created_at DESC);

CREATE OR REPLACE VIEW hazard_accuracy_view AS
SELECT
  centre_id,
  prompt_type,
  SUM(CASE WHEN event_type = 'prompt_fired' THEN 1 ELSE 0 END)::INT AS total_fired,
  SUM(CASE WHEN event_type = 'prompt_suppressed' OR COALESCE(suppressed_flag, FALSE) THEN 1 ELSE 0 END)::INT AS total_suppressed,
  CASE
    WHEN SUM(
      CASE WHEN event_type IN ('prompt_fired', 'prompt_suppressed') OR COALESCE(suppressed_flag, FALSE) THEN 1 ELSE 0 END
    ) = 0 THEN 0
    ELSE ROUND(
      (
        SUM(CASE WHEN event_type = 'prompt_suppressed' OR COALESCE(suppressed_flag, FALSE) THEN 1 ELSE 0 END)::NUMERIC
        /
        SUM(CASE WHEN event_type IN ('prompt_fired', 'prompt_suppressed') OR COALESCE(suppressed_flag, FALSE) THEN 1 ELSE 0 END)::NUMERIC
      ),
      4
    )
  END AS suppression_rate,
  ROUND(
    AVG(
      CASE
        WHEN event_type = 'prompt_fired' AND speed_after_kph IS NOT NULL AND speed_before_kph IS NOT NULL
          THEN (speed_before_kph - speed_after_kph)::NUMERIC
        ELSE NULL
      END
    ),
    2
  ) AS avg_speed_delta_kph
FROM telemetry_events
WHERE centre_id IS NOT NULL
  AND prompt_type IS NOT NULL
GROUP BY centre_id, prompt_type;

CREATE OR REPLACE VIEW route_reliability_view AS
SELECT
  centre_id,
  route_id,
  COUNT(*)::INT AS sessions,
  ROUND(
    AVG(
      CASE
        WHEN completion_flag IS TRUE THEN 1.0
        WHEN completion_flag IS FALSE THEN 0.0
        ELSE NULL
      END
    ),
    4
  ) AS completion_rate,
  ROUND(AVG(off_route_count::NUMERIC), 2) AS avg_off_route_count,
  ROUND(AVG(stress_index::NUMERIC), 2) AS avg_stress_index,
  ROUND(AVG(confidence_score::NUMERIC), 2) AS avg_confidence_score
FROM telemetry_events
WHERE event_type = 'session_summary'
  AND centre_id IS NOT NULL
  AND route_id IS NOT NULL
GROUP BY centre_id, route_id;

CREATE OR REPLACE VIEW confidence_distribution_view AS
SELECT
  centre_id,
  SUM(CASE WHEN confidence_score BETWEEN 0 AND 39 THEN 1 ELSE 0 END)::INT AS count_low_confidence,
  SUM(CASE WHEN confidence_score BETWEEN 40 AND 69 THEN 1 ELSE 0 END)::INT AS count_mid_confidence,
  SUM(CASE WHEN confidence_score BETWEEN 70 AND 100 THEN 1 ELSE 0 END)::INT AS count_high_confidence
FROM telemetry_events
WHERE event_type = 'session_summary'
  AND centre_id IS NOT NULL
GROUP BY centre_id;

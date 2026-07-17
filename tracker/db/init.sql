CREATE TABLE IF NOT EXISTS daily_logs (
  log_date   date PRIMARY KEY,
  hours      numeric(4,1) NOT NULL DEFAULT 0 CHECK (hours >= 0 AND hours <= 24),
  categories text[] NOT NULL DEFAULT '{}',
  focus      text NOT NULL DEFAULT '',
  did        text NOT NULL DEFAULT '',
  wins       text NOT NULL DEFAULT '',
  blockers   text NOT NULL DEFAULT '',
  energy     int CHECK (energy BETWEEN 1 AND 5),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS weekly_reviews (
  week_start  date PRIMARY KEY,
  summary     text NOT NULL DEFAULT '',
  wins        text NOT NULL DEFAULT '',
  blockers    text NOT NULL DEFAULT '',
  adjustments text NOT NULL DEFAULT '',
  next_focus  text NOT NULL DEFAULT '',
  on_track    boolean,
  updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_daily_logs_date ON daily_logs (log_date DESC);

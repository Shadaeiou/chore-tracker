-- Households are the top-level multi-user grouping.
CREATE TABLE households (
  id          TEXT PRIMARY KEY,
  name        TEXT NOT NULL,
  created_at  INTEGER NOT NULL
);

CREATE TABLE users (
  id            TEXT PRIMARY KEY,
  email         TEXT NOT NULL UNIQUE,
  display_name  TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  password_salt TEXT NOT NULL,
  household_id  TEXT NOT NULL REFERENCES households(id) ON DELETE CASCADE,
  created_at    INTEGER NOT NULL
);
CREATE INDEX idx_users_household ON users(household_id);

CREATE TABLE areas (
  id           TEXT PRIMARY KEY,
  household_id TEXT NOT NULL REFERENCES households(id) ON DELETE CASCADE,
  name         TEXT NOT NULL,
  icon         TEXT,
  sort_order   INTEGER NOT NULL DEFAULT 0,
  created_at   INTEGER NOT NULL
);
CREATE INDEX idx_areas_household ON areas(household_id);

CREATE TABLE tasks (
  id              TEXT PRIMARY KEY,
  area_id         TEXT NOT NULL REFERENCES areas(id) ON DELETE CASCADE,
  name            TEXT NOT NULL,
  frequency_days  INTEGER NOT NULL,
  last_done_at    INTEGER,
  created_at      INTEGER NOT NULL
);
CREATE INDEX idx_tasks_area ON tasks(area_id);

CREATE TABLE completions (
  id          TEXT PRIMARY KEY,
  task_id     TEXT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
  user_id     TEXT NOT NULL REFERENCES users(id),
  done_at     INTEGER NOT NULL
);
CREATE INDEX idx_completions_task ON completions(task_id, done_at DESC);

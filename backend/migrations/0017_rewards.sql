CREATE TABLE rewards (
  id TEXT PRIMARY KEY,
  household_id TEXT NOT NULL,
  name TEXT NOT NULL,
  emoji TEXT NOT NULL DEFAULT '🏆',
  effort_cost INTEGER NOT NULL DEFAULT 100,
  created_by TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  is_active INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE user_reward_settings (
  user_id TEXT PRIMARY KEY,
  point_ratio REAL NOT NULL DEFAULT 1.0
);

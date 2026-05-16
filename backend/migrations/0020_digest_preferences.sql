-- Per-user daily digest notification preferences.
CREATE TABLE digest_preferences (
  user_id         TEXT    PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  enabled         INTEGER NOT NULL DEFAULT 0,
  days_of_week    TEXT    NOT NULL DEFAULT '[]',  -- JSON array of local day numbers 0=Sun..6=Sat
  hour            INTEGER NOT NULL DEFAULT 8,     -- local hour 0-23
  minute          INTEGER NOT NULL DEFAULT 0,     -- local minute 0-59
  timezone        TEXT    NOT NULL DEFAULT 'UTC', -- IANA timezone string from device
  include_overdue INTEGER NOT NULL DEFAULT 1,
  last_sent_date  TEXT                            -- 'YYYY-MM-DD' in user's local timezone, deduplicates same-day sends
);

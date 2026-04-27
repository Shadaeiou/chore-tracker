-- Invite codes let an existing household member onboard another user
-- to the same household.
CREATE TABLE invites (
  code         TEXT PRIMARY KEY,           -- short, URL-safe
  household_id TEXT NOT NULL REFERENCES households(id) ON DELETE CASCADE,
  created_by   TEXT NOT NULL REFERENCES users(id),
  created_at   INTEGER NOT NULL,
  expires_at   INTEGER NOT NULL,
  used_at      INTEGER,                    -- null until consumed
  used_by      TEXT REFERENCES users(id)
);
CREATE INDEX idx_invites_household ON invites(household_id);

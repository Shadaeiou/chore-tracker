-- À la carte one-off reminders / todo items separate from recurring chores.
-- Each todo has an owner (the household member it belongs to). Public todos
-- are visible to everyone in the household; private todos only to the owner.
CREATE TABLE todos (
  id           TEXT PRIMARY KEY,
  household_id TEXT NOT NULL REFERENCES households(id) ON DELETE CASCADE,
  owner_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  text         TEXT NOT NULL,
  done_at      INTEGER,
  is_public    INTEGER NOT NULL DEFAULT 0,
  created_at   INTEGER NOT NULL
);
CREATE INDEX idx_todos_household ON todos(household_id);
CREATE INDEX idx_todos_owner ON todos(owner_id);

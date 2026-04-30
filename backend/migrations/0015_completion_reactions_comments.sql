-- Reactions and threaded comments on completed activities. One reaction per
-- user per completion (toggled / replaced via POST). Comments are unbounded
-- and ordered by created_at so a back-and-forth conversation reads naturally.
CREATE TABLE completion_reactions (
  completion_id TEXT NOT NULL REFERENCES completions(id) ON DELETE CASCADE,
  user_id       TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  emoji         TEXT NOT NULL,
  created_at    INTEGER NOT NULL,
  PRIMARY KEY (completion_id, user_id)
);
CREATE INDEX idx_completion_reactions_completion ON completion_reactions(completion_id);

CREATE TABLE completion_comments (
  id            TEXT PRIMARY KEY,
  completion_id TEXT NOT NULL REFERENCES completions(id) ON DELETE CASCADE,
  user_id       TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  text          TEXT NOT NULL,
  created_at    INTEGER NOT NULL
);
CREATE INDEX idx_completion_comments_completion ON completion_comments(completion_id, created_at);

-- FCM device tokens for push notifications (Phase 3).
CREATE TABLE device_tokens (
  token       TEXT PRIMARY KEY,
  user_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  platform    TEXT NOT NULL,
  updated_at  INTEGER NOT NULL
);
CREATE INDEX idx_device_tokens_user ON device_tokens(user_id);

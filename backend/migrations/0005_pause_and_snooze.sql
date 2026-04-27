-- Vacation/pause mode and per-task snooze (Phase 4).
ALTER TABLE households ADD COLUMN paused_until INTEGER;
ALTER TABLE tasks ADD COLUMN snoozed_until INTEGER;

-- Schedule-preserving snooze. When set, the next completion stamps
-- last_done_at to this anchor instead of the actual completion time, so
-- snoozing a chore by a day doesn't permanently shift its weekly cadence.
-- Captured at snooze time as last_done_at + frequency_days (the original
-- due before snoozing); cleared on completion or unsnooze.
ALTER TABLE tasks ADD COLUMN postpone_anchor INTEGER;

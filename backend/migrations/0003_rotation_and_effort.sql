-- Per-task rotation and effort tracking (Phase 2).
ALTER TABLE tasks ADD COLUMN assigned_to TEXT REFERENCES users(id);
ALTER TABLE tasks ADD COLUMN auto_rotate INTEGER NOT NULL DEFAULT 0;
ALTER TABLE tasks ADD COLUMN effort_points INTEGER NOT NULL DEFAULT 1;
CREATE INDEX idx_tasks_assigned ON tasks(assigned_to);

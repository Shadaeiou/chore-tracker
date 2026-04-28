-- Roles for household membership: admin can rename household, invite, and
-- remove other members; member is everyone else. Backfill the earliest user
-- in each household as admin so legacy installs aren't admin-less.
ALTER TABLE users ADD COLUMN role TEXT NOT NULL DEFAULT 'member';

UPDATE users SET role = 'admin'
  WHERE id IN (
    SELECT u.id FROM users u
    INNER JOIN (
      SELECT household_id, MIN(created_at) AS first_at
      FROM users GROUP BY household_id
    ) f ON f.household_id = u.household_id AND f.first_at = u.created_at
  );

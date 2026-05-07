-- Two-pool reward system: each reward has a scope ('household' or 'personal').
-- Personal rewards belong to a single user; household rewards belong to the household at large.
ALTER TABLE rewards ADD COLUMN scope TEXT NOT NULL DEFAULT 'household';
ALTER TABLE rewards ADD COLUMN owner_id TEXT NULL;

CREATE INDEX idx_rewards_scope ON rewards(household_id, scope);
CREATE INDEX idx_rewards_owner ON rewards(owner_id);

-- One row per household tracking the current "round": which reward is selected
-- as the household goal, the points baseline at the start of this round, and
-- (optionally) who has earned the right to pick the next reward (e.g. RPS winner).
CREATE TABLE household_reward_state (
  household_id TEXT PRIMARY KEY,
  selected_reward_id TEXT NULL,
  points_baseline INTEGER NOT NULL DEFAULT 0,
  round_number INTEGER NOT NULL DEFAULT 1,
  next_picker_id TEXT NULL,
  updated_at INTEGER NOT NULL
);

-- Personal reward redemptions: a user spending their personal points on a personal reward.
CREATE TABLE personal_redemptions (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  household_id TEXT NOT NULL,
  reward_id TEXT NOT NULL,
  reward_name TEXT NOT NULL,
  reward_emoji TEXT NOT NULL,
  cost INTEGER NOT NULL,
  redeemed_at INTEGER NOT NULL
);
CREATE INDEX idx_personal_redemptions_user ON personal_redemptions(user_id);
CREATE INDEX idx_personal_redemptions_household ON personal_redemptions(household_id);

-- Household reward wins history: when the pool reaches the selected reward's cost,
-- it's "won" and recorded here. Then a new round starts.
CREATE TABLE household_reward_wins (
  id TEXT PRIMARY KEY,
  household_id TEXT NOT NULL,
  reward_id TEXT NOT NULL,
  reward_name TEXT NOT NULL,
  reward_emoji TEXT NOT NULL,
  cost INTEGER NOT NULL,
  round_number INTEGER NOT NULL,
  won_at INTEGER NOT NULL,
  claimed_by TEXT NOT NULL
);
CREATE INDEX idx_reward_wins_household ON household_reward_wins(household_id);

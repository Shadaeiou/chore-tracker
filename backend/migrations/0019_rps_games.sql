-- Rock-Paper-Scissors games. Best-of-3 between two household members.
-- A game progresses round-by-round; both players submit a hidden choice per round,
-- and once both are submitted the round resolves and either advances or finishes.
CREATE TABLE rps_games (
  id TEXT PRIMARY KEY,
  household_id TEXT NOT NULL,
  purpose TEXT NOT NULL DEFAULT 'pick_reward',
  challenger_id TEXT NOT NULL,
  opponent_id TEXT NOT NULL,
  challenger_score INTEGER NOT NULL DEFAULT 0,
  opponent_score INTEGER NOT NULL DEFAULT 0,
  current_round INTEGER NOT NULL DEFAULT 1,
  status TEXT NOT NULL DEFAULT 'in_progress',
  winner_id TEXT NULL,
  created_at INTEGER NOT NULL,
  finished_at INTEGER NULL
);
CREATE INDEX idx_rps_games_household ON rps_games(household_id);
CREATE INDEX idx_rps_games_status ON rps_games(household_id, status);

-- One row per round per game. Stores both players' choices once submitted.
CREATE TABLE rps_rounds (
  game_id TEXT NOT NULL,
  round_number INTEGER NOT NULL,
  challenger_choice TEXT NULL,
  opponent_choice TEXT NULL,
  resolved_at INTEGER NULL,
  PRIMARY KEY (game_id, round_number)
);

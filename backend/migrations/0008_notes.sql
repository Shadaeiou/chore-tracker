-- Persistent notes on tasks ("use Method, not Clorox") and per-completion
-- notes ("this week's grocery list") so users can record context that
-- belongs with the work, not in a separate notes app.
ALTER TABLE tasks ADD COLUMN notes TEXT;
ALTER TABLE completions ADD COLUMN notes TEXT;

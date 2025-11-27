-- Enable pg_trgm extension for fuzzy string matching
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Add specialized index for trigram matching performance
-- Note: The existing idx_sanctions_name uses to_tsvector (Full Text Search),
-- while this one uses gin_trgm_ops for similarity() and LIKE queries.
CREATE INDEX IF NOT EXISTS idx_sanctions_name_trgm ON sanctions_list USING gin (normalized_name gin_trgm_ops);

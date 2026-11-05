ALTER TABLE skill_records ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
CREATE INDEX ix_skill_records_user_created ON skill_records(user_id, created_at DESC);

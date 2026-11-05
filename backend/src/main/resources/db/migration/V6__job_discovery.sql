CREATE TABLE IF NOT EXISTS saved_jobs (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    company VARCHAR(255),
    location VARCHAR(255),
    job_url TEXT,
    description TEXT,
    source VARCHAR(80),
    match_score NUMERIC(5,2),
    notes TEXT,
    status VARCHAR(40) NOT NULL DEFAULT 'saved',
    saved_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_saved_jobs_user ON saved_jobs(user_id);
CREATE INDEX IF NOT EXISTS idx_saved_jobs_status ON saved_jobs(user_id, status);

-- Phase 2 Ingestion enhancements and Application Bundles

-- 1. Enhance saved_jobs with external ID, job type, skills, deadlines, and raw data
ALTER TABLE saved_jobs ADD COLUMN IF NOT EXISTS external_job_id VARCHAR(255);
ALTER TABLE saved_jobs ADD COLUMN IF NOT EXISTS job_type VARCHAR(80);
ALTER TABLE saved_jobs ADD COLUMN IF NOT EXISTS required_skills jsonb NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE saved_jobs ADD COLUMN IF NOT EXISTS preferred_skills jsonb NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE saved_jobs ADD COLUMN IF NOT EXISTS posted_at TIMESTAMP;
ALTER TABLE saved_jobs ADD COLUMN IF NOT EXISTS deadline_at TIMESTAMP;
ALTER TABLE saved_jobs ADD COLUMN IF NOT EXISTS raw_data jsonb NOT NULL DEFAULT '{}'::jsonb;

-- Unique indexes to prevent duplicate imports per user
CREATE UNIQUE INDEX IF NOT EXISTS idx_saved_jobs_user_ext
    ON saved_jobs(user_id, source, external_job_id)
    WHERE external_job_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_saved_jobs_user_url
    ON saved_jobs(user_id, job_url)
    WHERE job_url IS NOT NULL;

-- 2. Ingestion activity log for tracking RSS / API / URL imports
CREATE TABLE IF NOT EXISTS job_ingestion_logs (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source VARCHAR(80) NOT NULL,
    source_url TEXT,
    status VARCHAR(40) NOT NULL,
    jobs_imported INTEGER NOT NULL DEFAULT 0,
    jobs_skipped INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ingestion_logs_user ON job_ingestion_logs(user_id, created_at DESC);

-- 3. Application Bundles for job-specific application preparation (Phase 2 -> Phase 3 bridge)
CREATE TABLE IF NOT EXISTS application_bundles (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_id UUID NOT NULL REFERENCES saved_jobs(id) ON DELETE CASCADE,
    resume_id UUID REFERENCES resumes(id) ON DELETE SET NULL,
    resume_version_number INTEGER,
    job_url TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT', -- DRAFT, READY, IN_REVIEW, SUBMITTED_BY_CANDIDATE
    application_answers jsonb NOT NULL DEFAULT '[]'::jsonb,
    required_documents jsonb NOT NULL DEFAULT '[]'::jsonb,
    cover_letter_text TEXT,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_bundles_user ON application_bundles(user_id);
CREATE INDEX IF NOT EXISTS idx_bundles_job ON application_bundles(job_id);
CREATE INDEX IF NOT EXISTS idx_bundles_status ON application_bundles(user_id, status);

CREATE TABLE IF NOT EXISTS users (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP
);
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP
);
CREATE TABLE IF NOT EXISTS user_profiles (
    user_id UUID PRIMARY KEY,
    full_name VARCHAR(160),
    phone VARCHAR(40),
    location VARCHAR(160),
    graduation_year INTEGER,
    linkedin_url VARCHAR(500),
    github_url VARCHAR(500),
    portfolio_url VARCHAR(500),
    preferred_roles TEXT,
    preferred_locations TEXT,
    job_type VARCHAR(40),
    salary_preference VARCHAR(120),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS resumes (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    user_id UUID NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS resume_versions (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    resume_id UUID NOT NULL REFERENCES resumes(id),
    version_number INTEGER NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    extracted_text TEXT,
    structured_data TEXT DEFAULT '{}',
    evidence TEXT DEFAULT '[]',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS saved_jobs (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    title VARCHAR(255) NOT NULL,
    company VARCHAR(255),
    location VARCHAR(255),
    job_url TEXT,
    description TEXT,
    source VARCHAR(80),
    external_job_id VARCHAR(255),
    job_type VARCHAR(80),
    required_skills TEXT DEFAULT '[]',
    preferred_skills TEXT DEFAULT '[]',
    posted_at TIMESTAMP,
    deadline_at TIMESTAMP,
    raw_data TEXT DEFAULT '{}',
    match_score NUMERIC(5,2),
    notes TEXT,
    status VARCHAR(40) DEFAULT 'saved',
    saved_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS job_ingestion_logs (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    source VARCHAR(80) NOT NULL,
    source_url TEXT,
    status VARCHAR(40) NOT NULL,
    jobs_imported INTEGER NOT NULL DEFAULT 0,
    jobs_skipped INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS application_bundles (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    job_id UUID NOT NULL REFERENCES saved_jobs(id),
    preparation_batch_id UUID,
    resume_id UUID REFERENCES resumes(id),
    resume_version_number INTEGER,
    job_url TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    application_answers TEXT DEFAULT '[]',
    required_documents TEXT DEFAULT '[]',
    cover_letter_text TEXT,
    notes TEXT,
    certifications_snapshot TEXT DEFAULT '[]',
    certifications_approved BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS application_tracking (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    job_id UUID NOT NULL REFERENCES saved_jobs(id),
    bundle_id UUID,
    status VARCHAR(32) NOT NULL DEFAULT 'SAVED',
    resume_id UUID,
    resume_version_number INTEGER,
    answer_snapshot TEXT DEFAULT '[]',
    certifications_snapshot TEXT DEFAULT '[]',
    certifications_approved BOOLEAN NOT NULL DEFAULT FALSE,
    notes TEXT,
    applied_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS certifications (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(200) NOT NULL,
    issuer VARCHAR(200),
    issue_date VARCHAR(30),
    credential_url TEXT,
    expiration_date DATE,
    credential_id VARCHAR(255),
    verification_url TEXT,
    credly_badge_url TEXT,
    score_result VARCHAR(200),
    candidate_verified BOOLEAN NOT NULL DEFAULT FALSE,
    reminder_days VARCHAR(80) NOT NULL DEFAULT '90,30,7',
    certificate_file_name VARCHAR(255),
    certificate_storage_key VARCHAR(700),
    certificate_sha256 VARCHAR(64),
    certificate_size_bytes BIGINT,
    certificate_scan_status VARCHAR(24) NOT NULL DEFAULT 'NOT_CONFIGURED',
    archived_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS user_notification_preferences (
    user_id UUID PRIMARY KEY REFERENCES users(id),
    followup_reminders BOOLEAN NOT NULL DEFAULT TRUE,
    interview_reminders BOOLEAN NOT NULL DEFAULT TRUE,
    status_changes BOOLEAN NOT NULL DEFAULT TRUE,
    preparation_reminders BOOLEAN NOT NULL DEFAULT TRUE,
    certification_reminders BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS in_app_notifications (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    kind VARCHAR(40) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    application_id UUID,
    scheduled_at TIMESTAMP,
    read_at TIMESTAMP,
    certification_id UUID,
    reminder_days_before INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

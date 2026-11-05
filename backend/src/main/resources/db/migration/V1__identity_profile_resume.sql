CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email VARCHAR(320) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE refresh_tokens (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash VARCHAR(64) NOT NULL UNIQUE, expires_at TIMESTAMPTZ NOT NULL, revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_refresh_user ON refresh_tokens(user_id);
CREATE TABLE user_profiles (
  user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  full_name VARCHAR(160), phone VARCHAR(40), location VARCHAR(160), graduation_year INTEGER,
  linkedin_url TEXT, github_url TEXT, portfolio_url TEXT, preferred_roles JSONB NOT NULL DEFAULT '[]',
  preferred_locations JSONB NOT NULL DEFAULT '[]', job_type VARCHAR(40), salary_preference VARCHAR(120),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE education_records (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  institution VARCHAR(240) NOT NULL, degree VARCHAR(160), field_of_study VARCHAR(160), graduation_year INTEGER,
  grade VARCHAR(60), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_education_user ON education_records(user_id);
CREATE TABLE skill_records (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name VARCHAR(120) NOT NULL, verified BOOLEAN NOT NULL DEFAULT false, evidence TEXT,
  UNIQUE(user_id, name)
);
CREATE TABLE resumes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  original_filename VARCHAR(255) NOT NULL, storage_key TEXT NOT NULL, sha256 CHAR(64) NOT NULL,
  content_type VARCHAR(120) NOT NULL, size_bytes BIGINT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_resumes_user_created ON resumes(user_id, created_at DESC);
CREATE TABLE resume_versions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), resume_id UUID NOT NULL REFERENCES resumes(id) ON DELETE CASCADE,
  version_number INTEGER NOT NULL, storage_key TEXT NOT NULL, sha256 CHAR(64) NOT NULL,
  extracted_text TEXT, structured_data JSONB NOT NULL DEFAULT '{}', evidence JSONB NOT NULL DEFAULT '[]',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(resume_id, version_number)
);

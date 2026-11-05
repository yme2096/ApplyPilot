CREATE TABLE projects (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name VARCHAR(200) NOT NULL, description TEXT, technologies JSONB NOT NULL DEFAULT '[]', project_url TEXT,
  start_date VARCHAR(20), end_date VARCHAR(20), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_projects_user ON projects(user_id);
CREATE TABLE work_experiences (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  company VARCHAR(200) NOT NULL, title VARCHAR(200) NOT NULL, description TEXT,
  start_date VARCHAR(20), end_date VARCHAR(20), internship BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_work_experiences_user ON work_experiences(user_id);
CREATE TABLE certifications (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name VARCHAR(200) NOT NULL, issuer VARCHAR(200), issue_date VARCHAR(30), credential_url TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_certifications_user ON certifications(user_id);
CREATE TABLE achievements (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title VARCHAR(200) NOT NULL, description TEXT, achieved_on VARCHAR(30),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_achievements_user ON achievements(user_id);

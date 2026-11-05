-- Existing primary keys make these pairs unique by implication; explicit composite
-- indexes let PostgreSQL enforce that every attached record belongs to the same user.
CREATE UNIQUE INDEX uq_saved_jobs_id_user ON saved_jobs(id, user_id);
CREATE UNIQUE INDEX uq_resumes_id_user ON resumes(id, user_id);
CREATE UNIQUE INDEX uq_bundles_id_user ON application_bundles(id, user_id);

CREATE TABLE application_tracking (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_id UUID NOT NULL,
    bundle_id UUID,
    status VARCHAR(32) NOT NULL DEFAULT 'SAVED',
    resume_id UUID,
    resume_version_number INTEGER,
    answer_snapshot JSONB NOT NULL DEFAULT '[]'::jsonb,
    notes TEXT,
    applied_at TIMESTAMPTZ,
    shortlisted_at TIMESTAMPTZ,
    interview_at TIMESTAMPTZ,
    rejected_at TIMESTAMPTZ,
    offer_at TIMESTAMPTZ,
    withdrawn_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_application_tracking_user_job UNIQUE(user_id, job_id),
    CONSTRAINT uq_application_tracking_id_user UNIQUE(id, user_id),
    CONSTRAINT fk_application_tracking_owned_job FOREIGN KEY(job_id, user_id) REFERENCES saved_jobs(id, user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_application_tracking_owned_bundle FOREIGN KEY(bundle_id, user_id) REFERENCES application_bundles(id, user_id) ON DELETE SET NULL (bundle_id),
    CONSTRAINT fk_application_tracking_owned_resume FOREIGN KEY(resume_id, user_id) REFERENCES resumes(id, user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_application_tracking_resume_version FOREIGN KEY(resume_id, resume_version_number) REFERENCES resume_versions(resume_id, version_number) ON DELETE RESTRICT,
    CONSTRAINT ck_application_tracking_status CHECK (status IN ('SAVED','PREPARING','READY_FOR_REVIEW','APPLIED','SHORTLISTED','INTERVIEW','REJECTED','OFFER','WITHDRAWN'))
);
CREATE INDEX ix_application_tracking_user_status_date ON application_tracking(user_id, status, created_at DESC);

CREATE TABLE application_followups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    application_id UUID NOT NULL,
    due_at TIMESTAMPTZ NOT NULL,
    reminder_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    note TEXT,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_followup_owned_application FOREIGN KEY(application_id, user_id) REFERENCES application_tracking(id, user_id) ON DELETE CASCADE
);
CREATE INDEX ix_followups_due ON application_followups(user_id, due_at) WHERE completed_at IS NULL;

CREATE TABLE interview_preparations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    application_id UUID NOT NULL,
    questions JSONB NOT NULL DEFAULT '[]'::jsonb,
    progress JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_interview_preparation_user_app UNIQUE(user_id, application_id),
    CONSTRAINT fk_interview_owned_application FOREIGN KEY(application_id, user_id) REFERENCES application_tracking(id, user_id) ON DELETE CASCADE
);

CREATE TABLE skill_gap_progress (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_id UUID NOT NULL,
    skill VARCHAR(160) NOT NULL,
    progress VARCHAR(24) NOT NULL DEFAULT 'NOT_STARTED',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(user_id, job_id, skill),
    CONSTRAINT fk_skill_gap_owned_job FOREIGN KEY(job_id, user_id) REFERENCES saved_jobs(id, user_id) ON DELETE CASCADE,
    CONSTRAINT ck_skill_gap_progress CHECK(progress IN ('NOT_STARTED','IN_PROGRESS','PRACTICED','ADDRESSED'))
);

CREATE TABLE in_app_notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind VARCHAR(40) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    application_id UUID,
    scheduled_at TIMESTAMPTZ,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_owned_application FOREIGN KEY(application_id, user_id) REFERENCES application_tracking(id, user_id) ON DELETE CASCADE
);
CREATE INDEX ix_notifications_user_created ON in_app_notifications(user_id, created_at DESC);

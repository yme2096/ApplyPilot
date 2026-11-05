ALTER TABLE certifications
    ADD COLUMN expiration_date DATE,
    ADD COLUMN credential_id VARCHAR(255),
    ADD COLUMN verification_url TEXT,
    ADD COLUMN credly_badge_url TEXT,
    ADD COLUMN score_result VARCHAR(200),
    ADD COLUMN candidate_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN reminder_days VARCHAR(80) NOT NULL DEFAULT '90,30,7',
    ADD COLUMN certificate_file_name VARCHAR(255),
    ADD COLUMN certificate_storage_key VARCHAR(700),
    ADD COLUMN certificate_sha256 VARCHAR(64),
    ADD COLUMN certificate_size_bytes BIGINT,
    ADD COLUMN certificate_scan_status VARCHAR(24) NOT NULL DEFAULT 'NOT_CONFIGURED',
    ADD COLUMN archived_at TIMESTAMPTZ;

CREATE UNIQUE INDEX uq_certifications_id_user ON certifications(id, user_id);
CREATE UNIQUE INDEX uq_certification_credential_id
    ON certifications(user_id, lower(credential_id))
    WHERE credential_id IS NOT NULL AND archived_at IS NULL;
CREATE UNIQUE INDEX uq_certification_without_credential
    ON certifications(user_id, lower(name), lower(coalesce(issuer, '')), coalesce(issue_date, ''))
    WHERE credential_id IS NULL AND archived_at IS NULL;

ALTER TABLE application_bundles
    ADD COLUMN certifications_snapshot JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN certifications_approved BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE application_tracking
    ADD COLUMN certifications_snapshot JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN certifications_approved BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE user_notification_preferences
    ADD COLUMN certification_reminders BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE in_app_notifications
    ADD COLUMN certification_id UUID,
    ADD COLUMN reminder_days_before INTEGER,
    ADD CONSTRAINT fk_notification_owned_certification
        FOREIGN KEY (certification_id, user_id) REFERENCES certifications(id, user_id) ON DELETE CASCADE;

CREATE UNIQUE INDEX uq_certification_expiry_notification
    ON in_app_notifications(certification_id, reminder_days_before)
    WHERE kind = 'CERTIFICATION_EXPIRY' AND certification_id IS NOT NULL;

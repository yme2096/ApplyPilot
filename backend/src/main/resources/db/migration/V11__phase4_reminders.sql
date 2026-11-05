ALTER TABLE application_tracking ADD COLUMN interview_scheduled_at TIMESTAMPTZ;
ALTER TABLE application_followups ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE application_followups ADD CONSTRAINT uq_application_followups_id_user UNIQUE(id,user_id);

ALTER TABLE in_app_notifications ADD COLUMN followup_id UUID;
ALTER TABLE in_app_notifications ADD CONSTRAINT fk_notification_owned_followup
  FOREIGN KEY(followup_id,user_id) REFERENCES application_followups(id,user_id) ON DELETE CASCADE;
CREATE UNIQUE INDEX uq_notification_followup_reminder
  ON in_app_notifications(followup_id) WHERE followup_id IS NOT NULL AND kind='FOLLOW_UP';

CREATE TABLE user_notification_preferences (
  user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  followup_reminders BOOLEAN NOT NULL DEFAULT TRUE,
  interview_reminders BOOLEAN NOT NULL DEFAULT TRUE,
  status_changes BOOLEAN NOT NULL DEFAULT TRUE,
  preparation_reminders BOOLEAN NOT NULL DEFAULT TRUE,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

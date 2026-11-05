ALTER TABLE application_bundles
  ADD COLUMN preparation_batch_id UUID;

CREATE INDEX ix_application_bundles_user_batch
  ON application_bundles(user_id, preparation_batch_id)
  WHERE preparation_batch_id IS NOT NULL;

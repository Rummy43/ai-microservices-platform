-- Persist the originating user's identity (propagated via Kafka headers) on
-- dead-letter records, so failed events retain their audit context for triage
-- and reprocessing.
ALTER TABLE dead_letter_events
    ADD COLUMN actor_username VARCHAR(255),
    ADD COLUMN actor_email    VARCHAR(255),
    ADD COLUMN actor_roles    VARCHAR(512);
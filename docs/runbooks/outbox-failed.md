# Runbook — Outbox Terminal Publish Failure

**Alert:** `OutboxPublishTerminalFailure` (`outbox_failed > 0` for 2m)
**Component:** `user-service` transactional outbox → scheduled publisher.

## What it means
One or more outbox rows exhausted every publish retry and were marked `FAILED` — a **terminal**
state. The business transaction committed, the caller got a 2xx, but the event will never reach
Kafka on its own: this is **silent event loss** unless an operator intervenes. Unlike `PENDING`
backlog alerts, this one never self-resolves; the row count only changes when someone acts.

Distinct from the DLT alerts: DLT rows failed *consumption* (they made it to Kafka). `FAILED`
outbox rows failed *publishing* (they never left the source database).

## Diagnose
```promql
outbox_failed                      # how many rows are terminally stuck
rate(outbox_failed_total[15m])     # are NEW terminal failures still occurring?
rate(outbox_retried_total[5m])     # transient failures still churning → root cause still live
up{job="user-service"}             # and check broker / Schema Registry health
```
```sql
-- MySQL (root/root, db user_db): what failed and why
SELECT id, aggregate_id, event_type, retry_count, last_error, created_at
FROM outbox_events WHERE status = 'FAILED';
```
`last_error` tells you which dependency broke:
- `Error serializing Avro message` → **Schema Registry** was unreachable (known cold-start
  window: ~4 minutes after `docker compose up -d`; gate traffic on `curl :8085/subjects`).
- Broker timeouts → Kafka outage that outlasted the retry budget.

## Fix
1. **Confirm the root cause is resolved first** (Schema Registry answering, broker up) —
   re-driving into a dead dependency just burns the retry budget again.
2. **Re-drive** — reset the rows; the scheduled publisher picks them up on its next poll:
   ```sql
   UPDATE outbox_events
   SET status = 'PENDING', retry_count = 0, last_error = NULL
   WHERE status = 'FAILED';
   ```
3. Consumers are idempotent (`processed_events` guard), so re-driving is safe even if a
   previous attempt partially succeeded.

## Verify recovery
`outbox_failed` → 0; `outbox_published_total` advanced by the re-driven count;
downstream `notifications_sent_total` (or `notifications_duplicate_total`) advanced to match.

## Escalate
Rows re-failing after a re-drive with all dependencies healthy → the payload itself may be
unserializable (schema drift) → owner + `common-schema` compatibility check.
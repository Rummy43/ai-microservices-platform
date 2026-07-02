# Runbook — Outbox Backlog (Depth / Age)

**Alerts:** `OutboxBacklogDepthHigh` (`outbox_pending > 50` for 10m) · `OutboxBacklogAgeHigh` (`outbox_oldest_pending_age_seconds > 60` for 5m)
**Component:** `user-service` transactional outbox → scheduled publisher.

## What it means
Events are committed to `outbox_events` (status `PENDING`) but not being published to Kafka fast enough. **Depth** catches "a lot stuck"; **age** catches "a little stuck for a long time" (slow-drain) — the second is the subtler failure a count alone misses.

## Diagnose
```promql
outbox_pending                        # current PENDING depth
outbox_oldest_pending_age_seconds     # age of the oldest PENDING (0 = none)
rate(outbox_published_total[5m])      # publish throughput — is the publisher running at all?
rate(outbox_failed_total[5m])         # permanent publish failures
rate(outbox_retried_total[5m])        # transient failures being retried
```
- Publisher throughput ~0 while depth grows → the `@Scheduled` publisher isn't running (thread stuck / app issue).
- Retries climbing → Kafka/Schema Registry reachability; check `up` and broker health.

## Likely causes → actions
- **Publisher not running** → restart `user-service`; confirm `outbox_published_total` climbs and depth drains.
- **Kafka/Schema Registry unreachable** → restore infra; retries then succeed.
- **Poison row repeatedly failing** → inspect the oldest PENDING row; it should move to `FAILED` after bounded retries rather than blocking the poll.

## Verify recovery
`outbox_pending` → low; `outbox_oldest_pending_age_seconds` → ~0.

## Escalate
Depth/age climbing with the publisher up and Kafka healthy → owner.
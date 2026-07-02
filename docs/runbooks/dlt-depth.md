# Runbook — Dead-Letter Depth / Influx

**Alerts:** `DltDepthHigh` (`dlt_unreprocessed > 10` for 15m) · `DltInflux` (`rate(notifications_dlt_total[5m]) > 0` for 10m)
**Component:** `notification-service` retry→DLT + self-healing reprocessor.

## What it means
- **DltInflux:** events are actively exhausting retries and landing in the DLT — a *systematic* processing failure (not a transient blip that retry absorbs).
- **DltDepthHigh:** dead-letters are accumulating faster than the scheduled self-healing reprocessor drains them (or it isn't running).

## Diagnose
```promql
dlt_unreprocessed                       # current outstanding (gauge)
rate(notifications_dlt_total[5m])       # influx rate
rate(notifications_failed_total[5m])    # upstream processing failures feeding the DLT
```
```sql
-- what's failing and for whom (actor context is persisted on the row)
SELECT event_id, last_error, actor_username, failed_at
FROM dead_letter_events WHERE reprocessed = false ORDER BY failed_at DESC LIMIT 20;
```
- Group by `last_error` to find the common failure. Use `trace_id` to pivot into Tempo/Loki.

## Likely causes → actions
- **Bad/poison payload** → fix the handler or the producer contract; reprocessor replays succeed once the cause is gone.
- **Downstream dependency was down** → once healthy, the self-healing reprocessor drains with backoff automatically; watch `dlt_unreprocessed` fall.
- **Reprocessor not running** → restart `notification-service`; confirm the gauge decreases.

## Verify recovery
`rate(notifications_dlt_total[5m])` → 0 and `dlt_unreprocessed` → below threshold.

## Note (current state)
As of 2026-07-01 there are ~32 unreprocessed dead-letters from prior fault-injection testing — `DltDepthHigh` firing is *correct* until they're drained or cleared.

## Escalate
Influx continues after the suspected cause is fixed → owner.
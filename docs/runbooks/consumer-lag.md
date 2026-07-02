# Runbook — Kafka Consumer Lag High

**Alert:** `KafkaConsumerLagHigh` (ticket)
**SLI:** `kafka_consumer_fetch_manager_records_lag_max{job="notification-service"}` · **threshold:** > 1000 for 10m

## What it means
`notification-service` is falling behind `user-created-topic` — events are produced faster than they're consumed, so notifications are getting stale. Liveness can still look green while freshness degrades.

## Diagnose
```promql
max by (job,topic,partition) (kafka_consumer_fetch_manager_records_lag)   # where the lag sits
sum by (result) (rate(spring_kafka_listener_seconds_count[5m]))           # success vs failure throughput
rate(spring_kafka_listener_seconds_sum[5m]) / rate(spring_kafka_listener_seconds_count[5m])  # per-record processing time
up{job="notification-service"}                                            # is the consumer even up?
```
- If processing time spiked: check the notification DB (PostgreSQL) latency and the idempotency lookup.
- If `up == 0`: the consumer is down — the backlog is unconsumed, not slow.

## Likely causes → actions
- **Consumer down/restarting** → bring it up; `auto-offset-reset` + committed offsets drain the backlog.
- **Slow downstream (DB) or contention** → resolve the bottleneck; consider concurrency/partitions.
- **Poison message stalling a partition** → should route to retry→DLT; confirm it's advancing, see [dlt-depth](dlt-depth.md).

## Verify recovery
`kafka_consumer_fetch_manager_records_lag_max` trends to ~0.

## Escalate
Sustained lag growth with the consumer up and DB healthy → owner.
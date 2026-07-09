# Runbook — Pipeline Consumption Stalled / Target Down

**Alerts:** `PipelineConsumptionStalled` (page) · `TargetDown` (page)
**SLI:** producer-vs-consumer counter flow · **threshold:** publishing > 0.05 ev/s for 10m while ALL consumer-side processing counters are flat or absent, sustained 5m. `TargetDown`: `up == 0` for 3m.

## What it means
Events are leaving the outbox but **nothing is arriving on the other side** — the async pipeline is severed end-to-end. This alert exists because the client-side lag metric (`kafka_consumer_fetch_manager_records_lag_max`) decays to `NaN` when the consumer stops fetching (verified by failure injection 2026-07-08: 3,600+ real broker lag, client metric NaN, `KafkaConsumerLagHigh` silent). A consumer that is most broken is least visible to its own metrics — so this rule watches *both sides* of the pipe with absent-safe semantics.

## Diagnose
```promql
up{job="notification-service"}                                   # dead process? (TargetDown should also be firing)
sum(rate(outbox_published_total[10m]))                           # producer side — confirm events are flowing in
sum(rate(notifications_sent_total[10m]))                         # consumer side — confirm the flatline
max(kafka_consumer_fetch_manager_records_lag_max)                # NaN here + real lag at broker = stalled, not slow
```
```bash
# Broker-side truth — the number the client metric can't hide:
docker exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 \
  --describe --group notification-group
```
- `up == 0` → the process is dead; every metric-based alert for it is blind. Restart first, diagnose after.
- `up == 1` but zero processing → listener containers stopped/paused, rebalance wedge, or a hung poll loop: check for `paused` listener logs, rebalance storms, and thread dumps.

## Likely causes → actions
- **Process down** → restart; committed offsets + idempotent consumer make replay safe.
- **Listener containers stopped/paused** (rebalance failure, container error handler gave up) → restart the service; inspect the error that stopped the container.
- **Deserialization poison at the head of every partition** → should route retry→DLT; if the DLT path itself is broken, events wedge — see [dlt-depth](dlt-depth.md).
- **Broker connectivity lost from consumer only** (producer fine) → check network/listener config; consumer logs will show `Connection to node -1` errors.

## Verify recovery
Consumer-side counters (`notifications_sent_total` et al.) advancing again; broker-side group lag trending to 0; `PipelineConsumptionStalled` and `TargetDown` inactive. Recovery reference: the 2026-07-08 injection drained 3,600 records in ~24s once resumed.

## Escalate
Consumer up, counters flat, broker lag growing, and no poison evidence → owner, with a JVM thread dump attached.
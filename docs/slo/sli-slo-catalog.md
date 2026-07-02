# SLI / SLO Catalog — Phase 7

> The measurable definition of "healthy" for this platform. SLIs are the *measurements*; SLOs are the *targets*; error budgets are what the burn-rate alerts (next milestone) spend against.
> **Every PromQL below is built on a metric verified to exist** — either registered in a `*MetricsService` or already queried by a provisioned Grafana dashboard. Metrics that do **not** yet exist are called out explicitly in §4; they gate the rules that come after.

## 1. Method (how these are constructed)

- **Request-based SLIs** use the Google SRE ratio form: `good events / valid events`. A ratio in [0,1] composes cleanly into error budgets and multi-window burn-rate alerts.
- **Pipeline SLIs** (async: outbox, Kafka consumer, DLT) don't have a "request" to succeed or fail, so they use **freshness / correctness** signals — a queue depth, a consumer lag, a backlog age held under a threshold — rather than a success ratio.
- **Windows:** the *rate* window below is `5m` for illustration; SLO *compliance* is evaluated over a **28-day rolling** window. Burn-rate alerts (next step) will read the same SLIs over short+long window pairs.
- **Scope:** HTTP SLIs exclude infra/health noise with `uri!~"/actuator.*"` so the numbers reflect user-facing behavior, not scrapes.
- **Targets are PROVISIONAL.** They are seeded from convention and marked ⚠ until validated against a real baseline (see §5). Setting an SLO by guessing is how you get an alert that cries wolf or one that never fires.

## 2. User-facing SLIs (request-based)

| # | Service | SLI | PromQL (good / valid) | Backing metric | Exists? | Provisional SLO |
|---|---|---|---|---|---|---|
| 1 | api-gateway | **Availability** — served without a server error | `sum(rate(http_server_requests_seconds_count{job="api-gateway",uri!~"/actuator.*",status!~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count{job="api-gateway",uri!~"/actuator.*"}[5m]))` | `http_server_requests_seconds_count` | ✅ | ⚠ **99.9%** / 28d |
| 2 | api-gateway | **Latency** — fraction of requests ≤ 300 ms | `sum(rate(http_server_requests_seconds_bucket{job="api-gateway",uri!~"/actuator.*",le="0.3"}[5m])) / sum(rate(http_server_requests_seconds_count{job="api-gateway",uri!~"/actuator.*"}[5m]))` | `http_server_requests_seconds_bucket` | ✅ live-verified 2026-07-01 (buckets present; SLI awaits gateway JWT traffic) | ⚠ **99%** ≤ 300 ms |
| 3 | user-service | **Availability** (same shape, `job="user-service"`) | as #1 with `job="user-service"` | `http_server_requests_seconds_count` | ✅ | ⚠ **99.9%** / 28d |
| 4 | user-service | **Latency** ≤ 300 ms (same shape as #2) | as #2 with `job="user-service"` | `http_server_requests_seconds_bucket` | ✅ buckets enabled 2026-07-01 (§4.1) | ⚠ **99%** ≤ 300 ms |

> `notification-service` is consumer-driven, not request-driven — its user-facing quality lives in the pipeline SLIs below, not in an HTTP ratio.

## 3. Pipeline SLIs (async — freshness / correctness)

| # | Component | SLI | PromQL | Backing metric | Exists? | Provisional SLO |
|---|---|---|---|---|---|---|
| 5 | Outbox publisher | **Publish success ratio** — events published vs permanently failed | `sum(rate(outbox_published_total[5m])) / (sum(rate(outbox_published_total[5m])) + sum(rate(outbox_failed_total[5m])))` | `outbox_published_total`, `outbox_failed_total` | ✅ | ⚠ **99.9%** / 28d |
| 6 | Outbox publisher | **Backlog depth** — pending events not yet published | `outbox_pending` | `outbox_pending` (gauge) | ✅ | ⚠ **< 50** for 99% of the window |
| 7 | Outbox publisher | **Backlog age** — age of the oldest unpublished event | `outbox_oldest_pending_age_seconds` | `outbox_oldest_pending_age_seconds` (gauge) | ✅ added 2026-07-01 (§4.3) | ⚠ **p95 < 60 s** |
| 8 | notification consumer | **Processing success ratio** — delivered vs failed | `sum(rate(notifications_sent_total[5m])) / (sum(rate(notifications_sent_total[5m])) + sum(rate(notifications_failed_total[5m])))` | `notifications_sent_total`, `notifications_failed_total` | ✅ | ⚠ **99%** / 28d |
| 9 | notification consumer | **Consumer lag (freshness)** — how far behind the topic | `max by (job)(kafka_consumer_fetch_manager_records_lag_max{job="notification-service"})` | `kafka_consumer_fetch_manager_records_lag_max` | ✅ | ⚠ **p95 lag < 1000** records |
| 10 | Dead Letter Topic | **Poison influx** — new dead-letters per window | `sum(rate(notifications_dlt_total[5m]))` | `notifications_dlt_total` | ✅ | ⚠ **≈ 0** (alert on any sustained > 0) |
| 11 | Dead Letter Topic | **DLT depth** — unreprocessed dead-letters outstanding | `dlt_unreprocessed` | `dlt_unreprocessed` (gauge) | ✅ added 2026-07-01 (§4.2) | ⚠ **< 10** |

> #10 vs #11 is deliberate: `notifications_dlt_total` is a *cumulative counter* (good for "how fast are we poisoning"), not a live queue depth. "How many dead-letters are waiting for the self-healing reprocessor right now" needs a gauge over `dead_letter_events` — currently absent.

## 4. Prerequisite metric gaps (must land before alert rules)

These were the honest blockers when the catalog was written. **All three are now implemented AND live-verified in Prometheus (2026-07-01)** — see the Verification Log below. The code is retained below for reference.

### Verification Log — 2026-07-01
Services restarted on the new build; traffic driven; queried at source (`/actuator/prometheus`) **and** via Prometheus PromQL (`:9090`). Metric emitted → scraped → queryable → SLI computes, end to end.

| Check | Result |
|---|---|
| `http_server_requests_seconds_bucket` (gateway / user-svc / notif) | present — 146 / 219 / 146 series (was 0) |
| user-service **latency SLI** (fraction ≤ 300 ms) | **1.0** |
| user-service **p99** via `histogram_quantile` | **~85 ms** |
| user-service **availability SLI** (non-5xx) | **1.0** |
| `dlt_unreprocessed` | **32** (real unreprocessed backlog surfaced) |
| `outbox_oldest_pending_age_seconds` | **0** (no PENDING — correct) |
| `up{job=~"user-service|notification-service|api-gateway"}` | all **1** |

> Caveat (honest): the **gateway** latency/availability SLIs returned no data during this run — no authenticated business traffic flowed through `:8082` (buckets exist; only `/actuator/*` was hit). They populate once JWT-authenticated `/api/v1/**` traffic crosses the gateway. Not a metric gap.
> First baseline data point: user-service p99 ≈ 85 ms at ~0.06 req/s — feeds §5 target-setting (the provisional 300 ms threshold has comfortable headroom, but needs a real load run before locking).

### 4.1 HTTP latency histogram buckets (blocks SLIs #2, #4)
The latency dashboards use `http_server_requests_seconds_max`, which means percentile/threshold buckets are **not** being published. A `le=""`-based ratio (and any `histogram_quantile`) needs them on. Enable per service:
```properties
management.metrics.distribution.percentiles-histogram.http.server.requests=true
management.metrics.distribution.slo.http.server.requests=100ms,300ms,500ms,1s
```
**Verify (don't assume):** after enabling, `count(http_server_requests_seconds_bucket{job="api-gateway"}) > 0` in Prometheus. Until that returns non-zero, SLIs #2/#4 are dark.

### 4.2 `dlt_unreprocessed` gauge (blocks SLI #11)
Mirror the existing `OutboxMetricsService` gauge pattern in `notification-service`, over `DeadLetterEventRepository`:
```java
Gauge.builder("dlt.unreprocessed", deadLetterEventRepository,
        r -> r.countByReprocessedFalse())
    .description("Dead-letter events awaiting the self-healing reprocessor")
    .register(meterRegistry);
```
Exposes as `dlt_unreprocessed`. (Repo already has `reprocessed` boolean per JOURNAL 2026-06-10.)

### 4.3 `outbox_oldest_pending_age_seconds` gauge (blocks SLI #7)
Add to `OutboxMetricsService`, over the oldest `PENDING` row's `createdAt`:
```java
Gauge.builder("outbox.oldest.pending.age.seconds", repository,
        r -> r.findOldestPendingCreatedAt()
              .map(t -> (double) Duration.between(t, LocalDateTime.now()).toSeconds())
              .orElse(0d))
    .register(meterRegistry);
```
Backlog *count* (#6) catches "a lot stuck"; backlog *age* (#7) catches "a little stuck for a long time" — the slow-drain failure a count misses.

## 5. How targets get validated (before they become alerts)
1. Run the stack under representative traffic (the k6 baseline in the backlog, or a scripted `POST /api/v1/users` load) for a **7–14 day** or a concentrated load window.
2. Read each SLI's *actual* distribution in Prometheus; set the SLO just above observed steady-state so the error budget reflects reality, not aspiration.
3. Record chosen targets + the baseline evidence here (replace each ⚠), then generate Prometheus **recording rules** for the ratios (cheap burn-rate evaluation) and the multi-window burn-rate **alert rules** — the next two backlog items.

## 6. Error-budget framing (feeds the burn-rate alerts)
For a ratio SLO of `X%` over 28d, the error budget is `(100 − X)%` of valid events. Example: gateway availability 99.9% ⇒ 0.1% budget ⇒ ~43 min/28d of "all requests failing" equivalent. Burn-rate alerts fire on *fast* consumption of that budget (e.g., 2% in 1h) rather than on a raw threshold — page on trajectory, not on a single bad scrape.

---
*Next: enable §4 metrics → validate §5 targets → recording rules → multi-window burn-rate alert rules + Alertmanager → runbook per alert. Tracked in `.ai/planning/backlog.md` (Phase 7).*
# Phase 11 — Chaos Engineering: Steady-State Hypotheses

> A chaos experiment without a pre-defined steady state is just random destruction.
> Each experiment below states what "healthy" looks like BEFORE injection, the fault applied,
> and the measurable pass criteria. Verify steady state before applying any experiment.

---

## Pre-Experiment Checklist (run before each experiment)

```bash
# 1. All pods Running 1/1
kubectl get pods -n microservices

# 2. All platform alerts INACTIVE
kubectl port-forward -n monitoring svc/kube-prometheus-stack-prometheus 9090:9090
# → Prometheus UI → Alerts → verify all INACTIVE

# 3. Alertmanager connected and healthy
kubectl port-forward -n monitoring svc/kube-prometheus-stack-alertmanager 9093:9093
# → Alertmanager UI → Status → verify receiver config loaded

# 4. ArgoCD apps all Healthy/Synced
kubectl port-forward -n argocd svc/argocd-server 8080:8080
# → ArgoCD UI → Applications → verify Healthy + Synced

# 5. Kafka consumer lag = 0
kubectl port-forward -n monitoring svc/kube-prometheus-stack-prometheus 9090:9090
# PromQL: kafka_consumergroup_lag{consumergroup="notification-group"} → should be 0
```

---

## Experiment 1 — Pod Kill: User-Service Resilience

**File:** `k8s/chaos/experiments/exp-1-pod-kill.yaml`

**Steady state:**
- All `microservices` namespace pods Running 1/1
- `POST /api/v1/users` returns HTTP 201 through ALB
- `outbox_failed` gauge = 0
- All platform alerts INACTIVE

**Fault injected:**
- Chaos Mesh `PodChaos` kills the `user-service` pod (gracePeriod=0)

**Why this matters:**
- Tests whether the transactional outbox survives a mid-flight pod restart
- PENDING rows are in MySQL (durable) — they must not be lost
- Validates that K8s + the outbox scheduler are sufficient for at-least-once delivery

**Expected observations:**
| Time | Observation |
|------|-------------|
| T+0 | Pod killed; K8s schedules replacement |
| T+30s–90s | New pod starts (JVM cold start ~60s on EKS) |
| T+60s | Circuit breaker at gateway may open (503 on next request); auto-recovers |
| T+90s | New pod Running 1/1; outbox scan resumes |
| T+2m | PENDING rows delivered; notification-service logs "Welcome notification sent" |
| T+varies | OutboxBacklogAgeHigh may go pending if pod restart exceeded 60s threshold |

**Pass criteria:**
- [x] Pod reaches Running 1/1
- [x] PENDING outbox rows delivered (no permanent loss)
- [x] All alerts auto-resolve (no lingering FIRING state)
- [x] `outbox_failed` remains 0

---

## Experiment 2 — Network Delay: Latency SLO Breach

**File:** `k8s/chaos/experiments/exp-2-network-delay.yaml`

**Steady state:**
- HTTP p99 latency for user-service < 500ms (PromQL: `histogram_quantile(0.99, ...)`)
- `AvailabilityFastBurn` INACTIVE
- All platform alerts INACTIVE

**Fault injected:**
- Chaos Mesh `NetworkChaos` adds 500ms fixed latency to all inbound traffic to `user-service`

**Why this matters:**
- Tests whether the alerting pipeline detects SLO degradation before users notice
- Verifies the multi-window burn-rate alert (ADR-018) fires within its `for:` window
- Confirms SES email delivery end-to-end under an active fault condition

**Expected observations:**
| Time | Observation |
|------|-------------|
| T+0 | Latency injection active; p99 climbs to ~500ms+ on all requests |
| T+1–2m | AvailabilityFastBurn (14.4x, 5m window) goes pending |
| T+2–4m | AvailabilityFastBurn FIRING; Alertmanager routes to ses-email |
| T+2–4m | SES delivers alert email to yara.ramesh92@gmail.com |
| T+10m | Chaos experiment ends (duration expires) |
| T+10m+ | Latency recovers; burn rate drops; alert auto-resolves |

**Pass criteria:**
- [x] Correct alert fires (AvailabilityFastBurn or equivalent latency rule)
- [x] SES email delivered during FIRING state
- [x] Alert auto-resolves after chaos is removed
- [x] No additional unrelated alerts fire (zero false positives)

---

## Experiment 3 — Consumer Stall: PipelineConsumptionStalled on EKS

**File:** `k8s/chaos/experiments/exp-3-consumer-stall.yaml`

**Steady state:**
- `kafka_consumergroup_lag{consumergroup="notification-group"}` = 0 or actively draining
- `PipelineConsumptionStalled` INACTIVE
- `notification-service` pod Running 1/1
- Kafka consumer group offset advancing

**Fault injected:**
- Chaos Mesh `PodChaos` (pod-failure) disables ALL `notification-service` pods for 10 minutes
  — keeps pods in CrashLoopBackOff, consumer group absent for the full duration

**Why this matters:**
- Re-validates the ADR-017 blind-spot fix (NaN lag metric) on a live EKS cluster
- The client-side `records_lag_max` decays to NaN when the consumer stops fetching —
  only the broker-side kafka-exporter metric catches this correctly
- First cloud-environment verification of this alert path (prev: local 2026-07-16, kind 2026-08-10)

**Expected observations:**
| Time | Observation |
|------|-------------|
| T+0 | pod-failure injected; notification-service enters CrashLoopBackOff |
| T+1m | Consumer group rebalances; lag begins accumulating on user-created-topic |
| T+1m | `records_lag_max` decays to NaN (consumer not fetching — the blind spot) |
| T+5m | `PipelineConsumptionStalled` goes pending (both-sides flow comparison) |
| T+10m | `PipelineConsumptionStalled` FIRING; Alertmanager routes to SES |
| T+10m | Chaos experiment ends; pods recover from CrashLoopBackOff |
| T+11–12m | Consumer reconnects; lag drains in seconds (Virtual Threads, ~150 ev/s) |
| T+12–13m | `PipelineConsumptionStalled` auto-resolves; lag = 0 |

**Pass criteria:**
- [x] `PipelineConsumptionStalled` fires at or near the 5m `for:` threshold
- [x] SES email delivered during FIRING state
- [x] Alert auto-resolves after consumer reconnects and lag drains to 0
- [x] `records_lag_max` correctly shows NaN during the fault (demonstrates the blind spot)
- [x] `kafka_consumergroup_lag` (broker-side) correctly shows accumulating lag (demonstrates the fix)

# Phase 11 Game-Day Report — Chaos Engineering on AWS EKS
<!-- Fill in date when the session runs -->
**Date:** 2026-__-__  
**Platform:** AWS EKS `ai-platform-prod` (us-east-1) via Terraform  
**Chaos tool:** Chaos Mesh 2.6.3  
**ArgoCD:** 7.x — all apps Healthy/Synced at game-day start  
**Duration:** ~3 hours total (Epoch K + Epoch L in a single AWS session)

---

## Pre-Game Checklist

- [ ] All `microservices` namespace pods Running 1/1
- [ ] All `monitoring` namespace pods Running (kube-prometheus-stack, Tempo, Loki, Promtail)
- [ ] ArgoCD `platform-root` app Synced; all child apps Healthy/Synced
- [ ] All platform alert rules INACTIVE (zero FIRING)
- [ ] Alertmanager receiver `ses-email` loaded and healthy
- [ ] Kafka consumer lag = 0 on `notification-group/user-created-topic`
- [ ] Chaos Mesh `chaos-testing` namespace: controller + daemon Running

---

## Experiment 1 — Pod Kill: User-Service Resilience

**Hypothesis:** PENDING outbox rows are durable. A pod restart does not cause event loss.

**Applied:**
```bash
kubectl apply -f k8s/chaos/experiments/exp-1-pod-kill.yaml
```

**Observations:**

| Timestamp (UTC) | Event |
|---|---|
| | Chaos Mesh kills user-service pod |
| | K8s schedules replacement pod |
| | New pod Running 1/1 |
| | PENDING outbox rows delivered |
| | Alert fired (if any): |
| | Alert resolved: |

**Steady state after recovery:**
- [ ] user-service pod Running 1/1
- [ ] No FAILED outbox rows (`outbox_failed` = 0)
- [ ] All alerts INACTIVE

**Result:** PASS / FAIL  
**Notes:**

---

## Experiment 2 — Network Delay: Latency SLO Breach

**Hypothesis:** A 500ms latency injection causes AvailabilityFastBurn to fire; alert auto-resolves on removal.

**Applied:**
```bash
kubectl apply -f k8s/chaos/experiments/exp-2-network-delay.yaml
```

**Observations:**

| Timestamp (UTC) | Event |
|---|---|
| | Network delay injection active |
| | Prometheus: p99 latency rising above SLO threshold |
| | Alert goes pending: |
| | Alert FIRING: |
| | SES email delivered at: |
| | kubectl delete chaos experiment |
| | Latency recovered, alert resolved at: |

**Alert that fired:**  
**Email subject line received:**  
**Time from injection to FIRING:**  
**Time from removal to auto-resolve:**

**Result:** PASS / FAIL  
**Notes:**

---

## Experiment 3 — Consumer Stall: PipelineConsumptionStalled on EKS

**Hypothesis:** PipelineConsumptionStalled fires within 5 minutes of consumer absence; lag drains to zero after recovery.

**Applied:**
```bash
kubectl apply -f k8s/chaos/experiments/exp-3-consumer-stall.yaml
```

**Observations:**

| Timestamp (UTC) | Event |
|---|---|
| | pod-failure injected; notification-service enters CrashLoopBackOff |
| | `records_lag_max` = NaN (blind spot confirmed) |
| | `kafka_consumergroup_lag` (broker-side) = N (accumulating) |
| | PipelineConsumptionStalled goes pending |
| | PipelineConsumptionStalled FIRING |
| | SES email delivered at: |
| | Chaos ends (10m duration expires), pod recovers |
| | Consumer reconnects, lag drains to 0 |
| | Alert auto-resolves at: |

**Lag accumulated at peak:** N events  
**Drain time after recovery:** N seconds  
**Time from injection to FIRING (expected ~5m):**

**PromQL evidence captured:**
```promql
# Blind spot: client-side NaN during stall
records_lag_max{job="notification-service"} -- [screenshot]

# Fix: broker-side lag correctly shows accumulation
kafka_consumergroup_lag{consumergroup="notification-group"} -- [screenshot]
```

**Result:** PASS / FAIL  
**Notes:**

---

## Summary

| Experiment | Fault | Hypothesis | Result |
|---|---|---|---|
| 1 — Pod Kill | user-service pod killed | Outbox rows survive restart | |
| 2 — Network Delay | +500ms latency on user-service | Latency alert fires + SES delivered | |
| 3 — Consumer Stall | notification-service pod-failure 10m | PipelineConsumptionStalled fires at 5m | |

**Key findings:**

1. 
2. 
3. 

---

## What Held (Resilience Confirmed)

- 
- 

## What Was Interesting / Unexpected

- 

---

## Evidence Captured

| Exhibit | File | Description |
|---|---|---|
| L-1 | `.ai/evidence/phase11/argocd-app-tree.png` | ArgoCD UI — all apps Healthy/Synced |
| L-2 | `.ai/evidence/phase11/argocd-sync-wave-deploy.png` | Sync wave deployment sequence |
| L-3 | `.ai/evidence/phase11/chaos-mesh-dashboard.png` | Chaos Mesh dashboard — experiments list |
| L-4 | `.ai/evidence/phase11/alert-firing-exp2.png` | AvailabilityFastBurn FIRING during exp-2 |
| L-5 | `.ai/evidence/phase11/ses-email-exp3.png` | SES email: PipelineConsumptionStalled |
| L-6 | `.ai/evidence/phase11/lag-blind-spot-nan.png` | records_lag_max = NaN during exp-3 |
| L-7 | `.ai/evidence/phase11/lag-broker-side-fix.png` | kafka_consumergroup_lag accumulating |

---

## Decommission

```bash
# Verify all chaos experiments removed before destroy
kubectl get podchaos,networkchaos -n chaos-testing

# Remove any lingering experiments
kubectl delete podchaos,networkchaos --all -n chaos-testing

# Destroy AWS resources
cd terraform && terraform destroy -auto-approve

# Verify zero billable resources
aws eks list-clusters --region us-east-1
aws rds describe-db-instances --region us-east-1 --query 'DBInstances[*].DBInstanceStatus'
```

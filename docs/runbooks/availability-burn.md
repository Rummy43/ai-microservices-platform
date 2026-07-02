# Runbook — Availability Error-Budget Burn

**Alerts:** `AvailabilityFastBurn` (page) · `AvailabilitySlowBurn` (ticket)
**SLO:** availability 99.9% (provisional) · **SLI:** `job:http_error_ratio:rate*` (5xx / all business requests)

## What it means
The service is returning 5xx fast enough to consume the 28-day error budget ahead of schedule. Fast burn ≈ 2% of budget in 1h; slow burn ≈ 5% in 6h. This is trajectory-based — it fires before the SLO is actually missed.

## Diagnose (backend, not the dashboard)
```promql
# which service + how bad, right now
job:http_error_ratio:rate5m
# which routes/status are erroring
sum by (job,uri,status) (rate(http_server_requests_seconds_count{status=~"5..",uri!~"/actuator.*"}[5m]))
```
- Pivot to the failing traces in Tempo, then to the exact log lines in Loki (derived fields / trace-to-logs) — filter by `trace_id`.
- Check dependencies: is a downstream circuit breaker open? `resilience4j_circuitbreaker_state{state="open"}`.

## Likely causes → actions
- **Bad deploy** → roll back the offending service; confirm error ratio decays.
- **Downstream dependency failing** → the breaker should be open (contained); fix/await the dependency, then it half-opens.
- **DB/broker unavailable** → check `up{...}` and container health; restart infra if needed.

## Verify recovery
`job:http_error_ratio:rate5m` back under `0.0144`; alert resolves after `for:` clears.

## Escalate
If not mitigated within one budget-doubling window, page the service owner.
envelope_version=1
sender_type=plan
sender_id=lock-manager-and-leases
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T11:55:24Z

# Log the build-server in_process fallback once per plan, not as a WARNING on every build

- component: plan-marshall:build-server-client
- category: improvement
- confidence: high
- source plan: lock-manager-and-leases

## Context

With the build server disabled by configuration, every one of the 76 Maven builds of this plan wrote `[WARNING] [BUILD-SERVER] resolved build (requested=auto, resolved=in_process, reason=disabled, ...)` to the work log. That line is about 70 of the 72 work-log warnings, so the few real warnings (pre-push gate degradation, baseline drift, the blocked TASK-015) are hard to find, and log-based warning counts are meaningless for the plan.

## Root cause

A configured, expected state (`reason=disabled`) is logged at WARNING level per build instead of once.

## Proposed action

Log `reason=disabled` once per plan (or at INFO), and keep WARNING for an unexpected fallback such as an unreachable enrolled server.

## Evidence

- aspect: log_analysis — warnings_work 72, all but two from BUILD-SERVER
- aspect: logging_gap_analysis — the repeated fallback warning buries the real warnings

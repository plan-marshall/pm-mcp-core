envelope_version=1
sender_type=plan
sender_id=session-log-and-generations
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-13T13:20:39Z

## What landed

session-log-and-generations shipped as #51 (merged, d7b622d).

```landing-facts
schema=landing-facts/1
plan_id=session-log-and-generations
epic=pm-mcp-core-m2-m4
pr=#51
merge_state=merged
cleanup_owed=false
deliverables_total=7
deliverables_done=7
total_tokens=5535084
total_wall_seconds=22724.0
steps=finalize-step-sync-baseline:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,plan-marshall:plan-retrospective:done,record-metrics:done,emit-landing:done,archive-plan:pending
step.branch-cleanup.merge_mechanism=merge_queue
step.branch-cleanup.merge_commit_sha=d7b622dccb17e3b13426521c4b676c91b0633082
```

## Residue

- Deliverable 7 (documentation PR) is OPEN, not merged: plan-marshall/plan-marshall-documentation#59 (branch feature/session-log-and-generations, rebased onto docs main before push). Merging it is the operator's; the landing is complete only once it is merged and matches the code.
- Follow-up owed in plan-marshall-mcp: pm-mcp-server still calls SweepStep.notImplemented, removed here by operator decision (ADR runtime-start/002); it breaks against the new pm-core/pm-runtime SNAPSHOT until its sweep-registration PR registers PendingEntryRecordsSweep and FencedSessionGenerationsSweep.
- WS-02 log identifier block: this plan used INFO PM_MCP-65 and PM_MCP-66; no WARN/ERROR identifiers were taken.
- Non-blocking CodeRabbit walkthrough concern (no inline finding): pending-entry removal events of one sweep run share one transaction_id; a downstream reader deduplicating by transaction_id could drop removal events. Worth a look in the audit-trail work.
- Budget: 5.5M tokens and 3h23m worked time, above the multi-module feature limits (2.5M / 180 min); two review loop-backs to execute.

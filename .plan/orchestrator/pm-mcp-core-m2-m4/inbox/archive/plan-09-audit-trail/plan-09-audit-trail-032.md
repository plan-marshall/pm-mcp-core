envelope_version=1
sender_type=plan
sender_id=plan-09-audit-trail
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-14T19:06:38Z

## What landed

plan-09-audit-trail shipped as #69 (merged via the merge queue as 3236ed536a37df6576acee0da0ed1188d6e485dc).

```landing-facts
schema=landing-facts/1
plan_id=plan-09-audit-trail
epic=pm-mcp-core-m2-m4
pr=#69
merge_state=merged
cleanup_owed=false
deliverables_total=6
deliverables_done=6
total_tokens=10638201
total_wall_seconds=63959.0
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,plan-marshall:plan-retrospective:done,record-metrics:done,emit-landing:done,archive-plan:pending
step.functional-review.deliverables_met=3
step.functional-review.deliverables_met_with_residuals=3
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=1
step.code-review.rules_cited=1
step.branch-cleanup.merge_mechanism=merge_queue
```

## Residue

- The documentation PR plan-marshall/plan-marshall-documentation#74 (deliverable 6) is NOT merged by this run; the operator merges it. Deliverable 6 counts as done for its authored content only.
- total_tokens is overstated by about 407786: the retrospective's self-reported 708540 tokens were accumulated before the harness usage (300754) arrived, and the accumulator is add-only.
- Follow-up wiring in plan-marshall-mcp: AuditLogSink and AuditVerification replace AuditChainVerification.NONE and the refusing sink; the assembly must create logs/ and break the FileLockManager / AuditLogSink constructor cycle.
- Known open gaps documented in ADR runtime-audit/003: a deleted head reads as absent; index entries carry no HMAC; a replayed same-state head verifies; a head rolled back with its files verifies; a cut log grows without rotating.
- Non-blocking advisories: runtime-audit.adoc understates verify() cost (it also reads the whole audit index); audit_chain_broken cannot name the file of first_broken_line without a spec change.

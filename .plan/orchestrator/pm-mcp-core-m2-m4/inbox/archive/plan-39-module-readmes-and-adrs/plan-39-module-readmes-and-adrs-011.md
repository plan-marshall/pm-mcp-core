envelope_version=1
sender_type=plan
sender_id=plan-39-module-readmes-and-adrs
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-13T18:16:16Z

## What landed

plan-39-module-readmes-and-adrs shipped as #53 (merged via the merge queue as c0ecf33).

```landing-facts
schema=landing-facts/1
plan_id=plan-39-module-readmes-and-adrs
epic=pm-mcp-core-m2-m4
pr=#53
merge_state=merged
cleanup_owed=false
deliverables_total=9
deliverables_done=9
total_tokens=7694590
total_wall_seconds=38397
steps=finalize-step-sync-baseline:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,plan-marshall:plan-retrospective:done,record-metrics:done,emit-landing:pending,archive-plan:pending
step.branch-cleanup.merge_mechanism=merge_queue
step.sync-baseline.action=rebased
```

## Residue

- Per-package ADR numbers collided twice with sibling plans that landed during finalize: #51 (core-store 007-009, runtime-start 001-002) and #54 (workflow-verify 012). This plan renumbered its own ADRs: core-store 010-023, runtime-start 003-010, workflow-verify 013-016. Other plans adding ADRs to these directories must take numbers above them. The pre-merge ADR gate (manage-adr scan) walks root doc/adr only, so per-package duplicates were checked by hand (candidate lesson 001).
- pm-workflow/doc/developer/workflow-verify.adoc (changed by #54) still says "ADRs 001 to 012"; the directory now runs to 016. Left untouched as another plan's file and as developer documentation (PLAN-40's area).
- Epic mail pm-mcp-core-m2-m4-001 (format versions stay 1): checked — no ADR of this plan records a format_version bump; the plan does not touch TransactionJournal code.
- No ADR candidate was dropped; the statement "route responses single-use" is absent from doc/adr/002 because neither #28 nor the work package states it.
- Review: the optional bot sourcery had an exhausted 7-day budget and never reviewed; the operator chose to proceed without it, and once proceeded past a coderabbit re-review timeout on 5a390c3 (coderabbit's review arrived afterwards and its findings were triaged before merge). The wait-region triage loop-back budget needed two operator grants.
- 10 candidate lessons were sent to this inbox (plan-39-module-readmes-and-adrs-001..010).

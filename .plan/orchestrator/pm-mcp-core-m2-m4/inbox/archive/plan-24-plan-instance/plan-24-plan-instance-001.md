envelope_version=1
sender_type=plan
sender_id=plan-24-plan-instance
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-15T07:47:09Z

## What landed

plan-24-plan-instance shipped as #67 (merged).

```landing-facts
schema=landing-facts/1
plan_id=plan-24-plan-instance
epic=pm-mcp-core-m2-m4
pr=#67
merge_state=merged
cleanup_owed=false
deliverables_total=8
deliverables_done=8
total_tokens=9939109
total_wall_seconds=86659
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,plan-marshall:plan-retrospective:done,record-metrics:done
step.functional-review.deliverables_met=6
step.functional-review.deliverables_met_with_residuals=2
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.branch-cleanup.merge_mechanism=merge_queue
step.sonar-roundtrip.new_code_issue_count=0
```

## Residue

Merge facts verified by the orchestrator: code PR https://github.com/plan-marshall/pm-mcp-core/pull/67 MERGED as 2ebec2a065015414d78238388ee015dca1d19dd8 (merge queue, squash). Companion docs PR https://github.com/plan-marshall/plan-marshall-documentation/pull/73 MERGED as 1eaa6c65ef24f4d2c520859af70e761ea269876c; it was rebased onto docs main first and kept the specification text rewritten upstream by docs #76 rather than deleting it as implemented.

Follow-up the epic must keep visible (operator decision 2026-10-15, lesson 2026-10-15-07-001): pm-mcp-core `composition-rules.json` and `PruneReason` still ship `recipe_override` and `auto_merge_off`, while the specification after docs #76 targets `plan_default` and `merge_by_operator` with axes, overrides and phase sub-workflows. PM-IMPL-5 and the roadmap "Remaining" name the gap. The code of this plan does not match the specification on this point.

TASK-15 was infeasible within this plan and is split into the work package `doc/plans/lane-route-pruning.adoc` (ADR `workflow-plan/011`).

Two finalize steps have no outcome in `steps` because they had not run when this landing was written: `emit-landing` (this step) and `archive-plan`. The two are omitted rather than recorded provisionally.

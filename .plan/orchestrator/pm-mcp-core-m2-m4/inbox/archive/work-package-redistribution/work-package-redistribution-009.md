envelope_version=1
sender_type=plan
sender_id=work-package-redistribution
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-17T20:10:16Z

## What landed

work-package-redistribution shipped as #85 (merged, squash c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852).

```landing-facts
schema=landing-facts/1
plan_id=work-package-redistribution
epic=pm-mcp-core-m2-m4
pr=#85
merge_state=merged
cleanup_owed=false
deliverables_total=12
deliverables_done=12
total_tokens=3825553
total_wall_seconds=23098.0
harness=claude
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,plan-marshall:plan-retrospective:done,record-metrics:done,emit-landing:done
step.functional-review.deliverables_met=11
step.functional-review.deliverables_met_with_residuals=1
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=3
step.code-review.rules_cited=2
```

## Residue

- Owed corrections remain in pm-mcp-clients and plan-marshall-mcp (the packages they name were renamed or merged here).
- The documentation PR plan-marshall-documentation #85 (roadmap.adoc line 233 re-pointed to plan-instance-alignment) was needed for the Links check and is merged.
- Sourcery did not review PR #85 (budget exhausted); the other required bots did.
- The squash message is the repository default, not a composed one.
- Token total is a floor: the 1-init phase and several finalize steps carry no token record.
- The plan used about 3.8M tokens against a 2.5M threshold and 6h24m wall clock (worked 1h24m); most of the wall clock was waiting for refine answers.
- Eight candidate-lesson messages (001-008) are in this epic's inbox; several concern plan-marshall components and may need routing to its lessons inbox.
- The main checkout carries the operator's own uncommitted `.plan/marshal.json` and `opencode.json`; the run left them untouched.

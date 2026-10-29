envelope_version=1
sender_type=plan
sender_id=static-verifier-residuals
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-14T13:18:32Z

## What landed

static-verifier-residuals (PLAN-56) shipped as #66 (merged).

```landing-facts
schema=landing-facts/1
plan_id=static-verifier-residuals
epic=pm-mcp-core-m2-m4
pr=#66
merge_state=merged
cleanup_owed=false
deliverables_total=8
deliverables_done=8
total_tokens=5809578
total_wall_seconds=20177.0
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,plan-marshall:plan-retrospective:done,record-metrics:done,emit-landing:done,archive-plan:pending
step.functional-review.deliverables_met=7
step.functional-review.deliverables_met_with_residuals=1
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.branch-cleanup.merge_mechanism=merge_queue
step.branch-cleanup.merge_commit_sha=7981d17608cce266db8e8b875baa6013ba11bb20
```

## Residue

- Documentation PR plan-marshall/plan-marshall-documentation#72 was merged before #66, as c72cbf6, by operator decision. It trims PM-WATCH-DSL-3, 4, 8, 9, 11, 15, 16, 29 and 32, rewords PM-WATCH-DSL-5, and updates the roadmap item. The functional review's D8 residual is closed by it.
- Specification issue plan-marshall/plan-marshall-documentation#71 is open. It asks how a depends_on_config value activates a guard, and Rule 3 configuration reachability stays a named seam until it is answered.
- The code review ran 3 rounds:
  - Round 1 found a blocking fail-open in the Rule 9 loop-back handling, fixed in 8c3130d.
  - Round 2 found a blocking fail-open in the STEP_LIST distinct count, fixed in 869b753.
  - Round 3 came back with 0 blocking findings at 869b753.
- The round-3 advisory fix 5523a90 changes wording only and shipped without a further review round, by operator decision. The code-review record therefore reads operator_override at 5523a90.
- CodeRabbit raised a major comment that ARCHIVE_STEPS should require commit and land on one cycle. The triage rejected it with a reply on the PR: the specified epic archives over several passes (ADR 022 decision 2).
- The pre-push gate's module-tests arm was DEGRADED: derive_gate_bundles resolves no Maven modules. All four project gates still ran green on 5523a90: pre-commit, coverage, and the pm-core and pm-runtime integration tests.
- PLAN-17 overlaps in workflow.verify. It now builds on this change.

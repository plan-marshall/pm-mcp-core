envelope_version=1
sender_type=plan
sender_id=plan-20-job-runner
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-18T10:21:11Z

## What landed

plan-20-job-runner shipped as #92 (merged).

```landing-facts
schema=landing-facts/1
plan_id=plan-20-job-runner
epic=pm-mcp-core-m2-m4
pr=#92
merge_state=merged
cleanup_owed=false
deliverables_total=11
deliverables_done=11
total_tokens=20478729
total_wall_seconds=76559.0
harness=claude
steps=finalize-step-sync-baseline:done,finalize-step-security-audit:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,plan-marshall:plan-retrospective:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done
step.functional-review.deliverables_met=7
step.functional-review.deliverables_met_with_residuals=4
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=9
step.code-review.rules_cited=5
```

## Residue

- The merge was done on the operator's word, not by the branch-cleanup barrier: PR #92 was enqueued with `ci pr merge-queue` and landed as squash commit 2ecea3f. After two merges of main and a documentation fix (60b2648) the operator ruled that no further review round was necessary. The `automatic-review` step was closed by `loop-back close` as an operator override at cb39204, with re-fire waivers for the security audit, functional review, code review, pre-push gate, ci-verify, sonar roundtrip and architecture refresh. Their `done` records therefore stand at the earlier head b494a52 (architecture-refresh at 5e53366); the final head cb39204 (CI, SonarCloud and CodeRabbit green) was not reviewed by them. The review budgets were spent: functional review 3/3, code review 5/5, plus operator grants.
- The total_tokens figure is a floor of the run's cost and the 5-execute accumulator carries a known over-count from three hand-invented metric values this session's orchestrator recorded by mistake (about 393000 tokens, 200 tool uses, 1500000 ms); a work-log WARNING names the exact over-count. The 1-init phase was never measured.
- Open items deliberately not fixed in the pull request are listed in its description (note 5): PID reuse in recovery and in the cancel and budget branch, a probe declaring exit 127 a success code, an unsettled job after a failing fallback, the unpruned store map, untested branches, the PM_RUNTIME-208 wording.
- Log identifiers and ADR numbers of this branch were renumbered when main was merged: PM_RUNTIME-30, -31, -130 to -132; core-store ADR 030/031; runtime-start ADR 013.
- The native build of pm-mcpd in plan-marshall-mcp against the deployed SNAPSHOT has not run; it is the only check of the `kill` downcall descriptor with `options.captureCallState`.

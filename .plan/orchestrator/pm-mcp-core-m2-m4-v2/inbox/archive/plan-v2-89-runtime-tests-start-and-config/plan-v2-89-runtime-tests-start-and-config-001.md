envelope_version=1
sender_type=plan
sender_id=plan-v2-89-runtime-tests-start-and-config
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-06T18:54:28Z

## What landed

plan-v2-89-runtime-tests-start-and-config shipped as #302 (merged).

```landing-facts
schema=landing-facts/1
plan_id=plan-v2-89-runtime-tests-start-and-config
epic=pm-mcp-core-m2-m4-v2
pr=#302
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=29937234
total_wall_seconds=73016.0
harness=claude
model=claude-sonnet-5-5
effort=low
plugin_version=0.1.2041
executor_version=0.1.2041
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,finalize-step-preference-emitter:done,plan-marshall:plan-retrospective:done,record-metrics:done,finalize-step-print-phase-breakdown:done,archive-plan:done
step.functional-review.deliverables_met=4
step.functional-review.deliverables_met_with_residuals=1
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=10
step.code-review.rules_cited=10
step.branch-cleanup.merge_mechanism=merge_queue
```

## Residue

- Emitted after the fact. The plan's manifest dropped `emit-landing` at compose time because the plan's source id carried the prefix `.plan/local/worktrees/_orchestrator/` and `orchestrator inbox detect` answered `orchestrated: false` (`not_orchestrator_pointer`). The same call with the epic-relative path `.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-89-...md` answers `orchestrated: true`. This is the defect `landings/PLAN-V2-89.md` already refers to; the orchestrator drained the plan by hand before this message existed, so this message may duplicate that record.
- The plan was already archived when this was written, so `refresh-runtime-stamp` could not run: `harness`, `model`, `effort`, `plugin_version` and `executor_version` are the start-time stamp from `status.json`, not a landing-time measurement. The finalize run used a newer plugin cache (0.1.2060) than the stamp (0.1.2041).
- Because the retrospective ran with `orchestrated=false`, its three lessons went to this repository's local lessons store (2026-11-06-17-001, 2026-11-06-11-002, 2026-11-06-11-003) and not to the epic inbox. Six candidate lessons about plan-marshall components were written to the `lessons-routing` inbox in the plan-marshall ledger worktree.
- The review barrier passed over a CodeRabbit re-review of the rebased head that answered "already reviewed the last commit"; the operator granted a `rereview-timeout-override` at 4f175195. Sourcery refused on diff size (optional bot).
- Merge order: plan-marshall-mcp PR #119 (cui-test-generator in the pm-mcp-server test scope) was merged first, with the operator's authorization, because the published `pm-runtime:tests` `TestBases` now needs that library. This closes the D5 residual of the functional review.
- Open advisories of the code review (not blockers): five single-class test modules over the line budget; generator values drawn in static initialisers; the identifier helper copied into about 20 test classes.

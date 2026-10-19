envelope_version=1
sender_type=plan
sender_id=plan-70-plan-instance-alignment
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-19T18:24:48Z

## What landed

plan-70-plan-instance-alignment shipped as #107 (merged by the merge queue as 21939cb): the pm-workflow plan instance composer is aligned with the specification (closed prune reasons plan_default / merge_by_operator / other_lane, shipped rule file at revision 2, plan behaviour axes with the override event, diff_summary on the composition record, lane-route pruning at the outline event, INV-INIT-2 per lane).

```landing-facts
schema=landing-facts/1
plan_id=plan-70-plan-instance-alignment
epic=pm-mcp-core-m2-m4
pr=#107
merge_state=merged
cleanup_owed=false
deliverables_total=10
deliverables_done=10
total_tokens=13471210
total_wall_seconds=38407.0
harness=claude
steps=finalize-step-sync-baseline:done,finalize-step-security-audit:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,plan-marshall:plan-retrospective:done,record-metrics:done,finalize-step-print-phase-breakdown:done
step.functional-review.deliverables_met=5
step.functional-review.deliverables_met_with_residuals=5
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=10
step.code-review.rules_cited=7
```

## Residue

- The steps list ends at print-phase-breakdown: emit-landing (this message) and archive-plan had not recorded when it was assembled.
- Both reviews closed clean at the merged head d8bfc8e with advisories only (functional 9, code 10). The open ones that matter for follow-up: the refused-parser arm of PlanInstanceStore.previousHeader (added by the last inline fix) and the no-instance-header arm have no test; the call selector and the land/adopt/unknown-lane arms of InstancePruner have no test; the lane entry ids are declared in both InstancePruner and InitInvariants; the fixture units plan.pmwf and execute.pmwf stay flat, so no fixture proves per-lane rework; Workflow DSL part 9 axes items 1 and 2 disagree when no level sets an axis (ADR workflow-plan/015).
- Breaking for plan-marshall-mcp (pre-1.0, no shims): CompositionRecord.composed takes the previous version as a second argument, CompiledInvariants.check takes a lane, the PlanFacts constructor changed and RECIPE_ID_FACT is removed. Code there stops compiling against the new SNAPSHOT until adapted.
- Owed to the milestone exit and written in plan-marshall-documentation, not here: the roadmap check-off of the plan-instance item and the documentation parts of PM-IMPL-5 this package implements. The roadmap-link fix is already merged there as PR 96.
- Not run locally, left to CI (green on the final head): verify -Pcoverage and verify -Pintegration-tests.
- The manifest's envelope_count (6) was not recomputed after the plan re-ordering; one inline triage edit after the first push (d8bfc8e) re-fired every head-dependent review and gate.
- 25 candidate-lesson messages were sent to this epic inbox (19 from lessons-capture, 6 from the retrospective).

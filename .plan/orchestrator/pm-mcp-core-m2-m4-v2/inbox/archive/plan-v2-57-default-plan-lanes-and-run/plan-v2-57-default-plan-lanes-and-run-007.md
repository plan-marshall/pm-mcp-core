envelope_version=1
sender_type=plan
sender_id=plan-v2-57-default-plan-lanes-and-run
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-11T23:04:32Z

## What landed

plan-v2-57-default-plan-lanes-and-run shipped as #378 (merged).

```landing-facts
schema=landing-facts/1
plan_id=plan-v2-57-default-plan-lanes-and-run
epic=pm-mcp-core-m2-m4-v2
pr=#378
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=18471036
total_wall_seconds=36144
harness=claude
model=claude-sonnet-5-5
effort=medium
plugin_version=0.1.2106
executor_version=0.1.2106
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,finalize-step-preference-emitter:done,plan-marshall:plan-retrospective:done,record-metrics:done,finalize-step-print-phase-breakdown:done
unresolved_review_threads=unknown
review_body_findings_total=unknown
review_body_findings_handled=unknown
sonar_open_new_issues=0
step.functional-review.deliverables_met=5
step.functional-review.deliverables_met_with_residuals=0
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=5
step.code-review.rules_cited=5
```

## Residue

- The merge landed through the platform merge queue (squash) as 6000f951ef22e5b81f543a68ce0a6e8ee8289ca6, merge-group run 43113640628 succeeded. The operator confirmed the merge at the pre-merge gate (consent recorded against head 614e8e51).
- sonar_open_new_issues=0 is NOT from the plan's sonar-roundtrip step: that step recorded "Sonar not configured, no scan performed" on both firings because this host has no Sonar credentials. The 0 comes from a direct read of the SonarCloud public API for pullRequest=378 at head 614e8e51 (0 open issues, quality gate OK). An earlier read at head c6c1767f showed one open issue, java:S1612 in DefaultPlanRunTest, which was fixed in 614e8e51.
- unresolved_review_threads, review_body_findings_total and review_body_findings_handled are unknown: no phase-6-finalize step records them yet. For reference, the unified triage resolved all 8 pr-comment findings of the run (5 accepted, 3 taken into account, 0 pending) and posted replies on the PR.
- The steps list omits emit-landing and archive-plan: this message is emitted by the first and precedes the second, so their outcomes did not exist when it was written.
- Five code-review advisories were left unfixed on purpose: the new hand-written Primitive fake in DefaultPlanPrimitives, the Optional parameter of its evaluate method, the engine guide's Decisions list omitting ADR 015, the per-lane-rework.pmwf header counting two lanes at init.ready, and the DefaultPlanRunTest Javadoc literals / the Run record without @Builder.
- The end-to-end test asserts the deepest call stack the corpus reaches (three frames), not the five the spec named; the operator chose that at the outline question.
- One file outside the declared footprint changed: pm-workflow/src/test/resources/plan/per-lane-rework.pmwf (8 PlanInstanceComposerTest cases failed until its lane routes followed the new test.lane outcomes).
- Process gaps seen (see the six candidate-lesson messages of this plan): the finalize procedure tells the triage hook to commit with no step id, which the commit gate refuses (committed under the manifest step ids automatic-review and sonar-roundtrip instead); a comment-only loop-back re-fired every head-dependent check, about 2 hours of re-verification.

envelope_version=1
sender_type=plan
sender_id=spend-removal
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-17T18:01:30Z

## What landed

spend-removal shipped as #83 (merged, squash acb5bba via the merge queue); no documentation pull request was opened.

```landing-facts
schema=landing-facts/1
plan_id=spend-removal
epic=pm-mcp-core-m2-m4
pr=#83
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=3943550
total_wall_seconds=15347.0
harness=claude
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,plan-marshall:plan-retrospective:done,record-metrics:done,emit-landing
step.branch-cleanup.merge_mechanism=merge_queue
step.create-pr.pr_number=83
step.sonar-roundtrip.new_code_issue_count=0
step.functional-review.deliverables_met=4
step.functional-review.deliverables_met_with_residuals=1
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=1
step.code-review.rules_cited=1
```

## Residue

- Owed to the milestone exit (listed in the pull request body, not done here): the roadmap check-off of the spend removal in `plan-marshall-documentation`, and the removal no longer stated as remaining in `token-usage`'s roadmap item.
- One advisory code-review finding is open and unfixed: `doc/Traceability.adoc` lists `CompositionRulesTest` and `PlanInstanceComposerTest` under PM-WF-14 "Verified by (no spend limit)", although they only had a gate name renamed in their fixtures. Two entries to remove, in a follow-up change.
- The operator asked for the five advisory findings of the first code-review round to be fixed before the merge. The fix commit re-ran both reviews, the gates, CI, the review bots and Sonar (the retrospective counts about 41% of the run's tokens in finalize). Nine candidate lessons were filed (`spend-removal-001` to `-009`).
- The first functional-review round was closed by an operator override on one criterion that could not be evidenced before the pull request existed; a later round graded it met against the pull request body.
- The run exceeded the retrospective's token budget for a multi_module tech_debt plan (about 3.9M tokens against 2.5M).
- The Sonar step needed a `url` field added to the machine-global credential file `~/.plan-marshall/credentials/workflow-integration-sonar.json`; nothing in this repository changed for it (see candidate lesson `spend-removal-001`).

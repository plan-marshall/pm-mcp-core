envelope_version=1
sender_type=plan
sender_id=plan-v2-19-engine-carries-reasons
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-21T09:44:27Z

## What landed

plan-v2-19-engine-carries-reasons shipped as #530 (merged, queue-merged); the merged pull request is #531 (squash 1ada535b), see Residue.

```landing-facts
schema=landing-facts/1
plan_id=plan-v2-19-engine-carries-reasons
epic=pm-mcp-core-m2-m4-v2
pr=#530
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=49540719
total_wall_seconds=142845.0
harness=claude
model=claude-sonnet-5-5
effort=medium
plugin_version=0.1.2173
executor_version=0.1.2173
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,finalize-step-preference-emitter:done,plan-marshall:plan-retrospective:done,record-metrics:done,finalize-step-print-phase-breakdown:done
unresolved_review_threads=unknown
review_body_findings_total=unknown
review_body_findings_handled=unknown
sonar_open_new_issues=unknown
step.functional-review.deliverables_met=2
step.functional-review.deliverables_met_with_residuals=3
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=17
step.code-review.rules_cited=13
step.branch-cleanup.merge_mechanism=merge_queue
step.sonar-roundtrip.count_status=confirmed
step.sonar-roundtrip.new_code_issue_count=0
```

## Residue

- Pull request reference: `create-pr` recorded `pr_number=530`, so `pr` above is that step's own claim. The `automatic-review` step reopened the pull request as #531 to re-ask the required reviewer CodeRabbit (loop-back grant reason), and the merge on main is commit 1ada535b `(#531)`. Read the merged PR as #531; `merge_state=merged` and `cleanup_owed=false` are the `branch-cleanup` step's claims for it.
- Four keys are `unknown`, not measured: `unresolved_review_threads`, `review_body_findings_total`, `review_body_findings_handled` (no lifecycle finalize step records them) and `sonar_open_new_issues` (`sonar-roundtrip` recorded `new_code_issue_count=0`, confirmed, issues without hotspots; that is a different key and is not inferred from it). The landing is INCOMPLETE at these four keys by design.
- `steps` lists the fifteen finalize steps recorded before this one; `emit-landing` (this step) and `archive-plan` carry no outcome at the time of writing and are omitted.
- Review gate: pre-merge consent and a head-bound `barrier-ask-override` (gap class `review-barrier-gap`) are recorded at HEAD 9daae326 over the optional reviewer Sourcery, which refused on a hard weekly quota; the required reviewer CodeRabbit participated at that head; CI and SonarCloud green. `automatic-review` closed `done` with "1 comment(s) found - 1 reviewed, 1 refused (unified triage pending)".
- Loop-back and halt history on the step records: `automatic-review` spent 4 rounds (1 operator-granted extra round after the first 3 hit CodeRabbit free-tier and rate-limit refusals) and halted once on `rate_window_await`; `functional-review` spent 3; `branch-cleanup` 2; `code-review` 1; `wait-region-unified-triage` 2; `pre-push-quality-gate` failed once (quality-gate failed for pm-core) and passed on re-run.
- Review verdicts: functional review clean (5 deliverables, 2 met and 3 met with residuals, 21 advisory, 11 unrequested changes, 5 false self-reports); code review clean (46 files, 17 advisory, 0 blocking).
- Reported by the dispatcher, NOT recorded in the step records and not verified here: an operator-authorized rebase onto main with two content conflicts (the `finalize-step-sync-baseline` record says only `rebased onto origin/main, 2 upstream commit(s)`); open follow-ups: the DSL watch count 38 versus 39 in the specification index and the missing PM-IMPL-5 backlink, the review advisories, and two flaky timing tests under daemon load.
- Tokens: 49.5M over 39h40m wall-clock; `record-metrics` reports `any_phase_missing_end_time=false`; per-step execution-log token figures are unmeasured.

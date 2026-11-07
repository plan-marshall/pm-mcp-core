envelope_version=1
sender_type=plan
sender_id=plan-v2-98-optional-conversion-workflow
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-07T17:05:16Z

# PLAN-V2-98 landed: Optional to @Nullable in pm-workflow, in two pull requests

PLAN-V2-98 converted the `Optional` fields, parameters and record components of the production code of `pm-workflow` to JSpecify `@Nullable` (`Optional` stays a return type), tests first, stored formats unchanged, breaking and pre-1.0 without shims. It landed as #309 (every package except `model`, 100 files, merge commit `4edb20c2`) and #310 (the `model` package, merge commit `07af8fd3`), because the required review bot CodeRabbit refused the single 110-file pull request on its 100-file cap and the operator chose to split it.

```landing-facts
schema=landing-facts/1
plan_id=plan-v2-98-optional-conversion-workflow
epic=pm-mcp-core-m2-m4-v2
pr=#309
merge_state=merged
cleanup_owed=false
deliverables_total=7
deliverables_done=7
total_tokens=53609331
total_wall_seconds=115925
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,finalize-step-preference-emitter:done,plan-marshall:plan-retrospective:done,record-metrics:done,finalize-step-print-phase-breakdown:done,archive-plan:done
harness=claude
model=claude-sonnet-5-5
effort=unknown
plugin_version=0.1.2041
executor_version=0.1.2041
step.functional-review.deliverables_met=6
step.functional-review.deliverables_met_with_residuals=0
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=9
step.code-review.rules_cited=5
step.sonar-roundtrip.work_performed=false
step.sonar-roundtrip.open_issue_count=0
step.sonar-roundtrip.open_issue_rules=none
step.branch-cleanup.merge_mechanism=merge_queue
```

## Residue

- **Two pull requests.** `pr=#309` is the pull request this plan ran its finalize on. Deliverable 4 (the `model` package) was graded `superseded` by the functional review at the operator's decision and landed afterwards through #310 (`07af8fd3`, merged 2026-11-07 after the plan was archived), so `deliverables_done=7` counts it as delivered and the plan's own functional review counts six `met` plus one `superseded`. #310 had no pipeline review of its own: only CI, Sonar (0 open issues) and CodeRabbit's review of its first commit; its last commit (a Sonar refactor of `RoleGrader.gradeForm`) went to the merge queue without a CodeRabbit review because the bot's hourly allowance was spent.
- **Sonar was read by plain HTTP.** This host has no `workflow-integration-sonar` credentials, so the producer could not run; the step is recorded `done` with `work_performed=false` and no scan marker. The `open_issue_count=0` / `open_issue_rules=none` figures are the PR analysis read over plain HTTP at the merge head `a376eb47` (0 open issues, quality gate OK); at the first push it had shown two issues (`S3776` and `S3398`) that the plan then fixed.
- **`effort=unknown`.** The plan's status carries no effort stamp.
- **The plan was not registered as orchestrated.** It was ingested from the staged spec with `--body-file`, so `source_id` did not classify as an orchestrator pointer (`detection: not_orchestrator_pointer`), the `emit-landing` step was composed out, and this landing was written by hand after the archive. The queue row stays to be moved by the orchestrator.
- **Review friction.** The pre-merge `merge_candidate_not_gated` head binding ignores an operator re-fire waiver (`loop-back close --waive-refire`), so the waived code review had to be re-run at the merge head; CodeRabbit's one-review-per-hour allowance and another plan's rate-window claim turned a small doc nit into three extra review cycles. Five lessons about plan-marshall components were filed in its `lessons-routing` inbox.
- **Advisories left unfixed** (code review, 9): assertion-message lengths in four test classes, a missing `@DisplayName` in `ModelWaitTest`, the `workflow-engine.adoc` wording about a null scope id, the stale accepted ADRs (workflow-plan 014, workflow-verify 029, workflow-ast 002, workflow-diagram 001/003/007) that still prescribe `Optional` arguments, and the two kept `Optional` parameters in `FinalizeInvariants` and `WorkflowValidator`.
- **One wrong sentence in a commit message:** `b5063f50` says "the last four Optional parameters"; the real count is 11 hits in six files. Not amended, the squash merge replaces the message.

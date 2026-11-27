envelope_version=1
sender_type=plan
sender_id=plan-v2-200-composed-vector-check-parser-limits
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-27T21:28:18Z

## What landed

plan-v2-200-composed-vector-check-parser-limits shipped as #641 (merged through the merge queue, 11ec6b72). PR #638 was closed and replaced by #641 to re-request the required CodeRabbit review. Standing rules revision r7 applied: R05, R06, R07, R10, R11, R19, R20 (R21 binds the orchestrator only).

```landing-facts
schema=landing-facts/1
plan_id=plan-v2-200-composed-vector-check-parser-limits
epic=pm-mcp-core-m2-m4-v2
pr=#641
merge_state=merged
cleanup_owed=unknown
deliverables_total=4
deliverables_done=4
total_tokens=19245117
total_wall_seconds=45455.0
harness=claude
model=claude-sonnet-5-5
effort=medium
plugin_version=0.1.2184
executor_version=0.1.2184
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,finalize-step-preference-emitter:done,plan-marshall:plan-retrospective:done,record-metrics:done,finalize-step-print-phase-breakdown:done
unresolved_review_threads=unknown
review_body_findings_total=unknown
review_body_findings_handled=unknown
sonar_open_new_issues=0
step.functional-review.deliverables_met=4
step.functional-review.deliverables_met_with_residuals=0
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=4
step.code-review.rules_cited=4
```

## Residue

- Facts not recorded by the lifecycle: `cleanup_owed`, `unresolved_review_threads`, `review_body_findings_total` and `review_body_findings_handled` are written `unknown` because no step recorded them. Observed directly: the worktree was removed and the local and remote branch refs were deleted after the merge.
- `pr`: `create-pr` recorded #638; the merged PR is #641 (observed by the merge-queue settle). The five review findings filed on the closed PR #638 were skipped as belonging to that PR and got no reply on GitHub.
- The steps list ends at `finalize-step-print-phase-breakdown`; `emit-landing` and `archive-plan` are the two steps that follow this write.
- Sonar: 0 new-code issues, confirmed on PR #641 (94.3% coverage on new code). A first sonar-roundtrip failed (`analysis_revision_unavailable`) because the CI verdict cached from PR #638 was applied to the new PR; one retry after PR #641 CI settled confirmed 0.
- Product leftovers that reached main (advisory, not blocking; add to a staged plan):
  - Wording: the rule (d) text (`doc/specification/model-work/04-worker-launch.adoc:49`, `pm-runtime/doc/developer/runtime-launch.adoc:236`, `doc/Traceability.adoc:1249`) lacks the element-0 (executable) exemption the code applies. ADR 014 says an empty `{{tools}}` member was previously accepted; at main it was already refused. The developer guide example uses `{{tools}}` where a path-typed slot is meant. Commit 0960237f says "fail-closed" for the residual; the residual is not fail-closed and the docs were corrected, the commit message cannot be.
  - Test gaps: no test for the adjacent-slot guard inside `carried()`; no test for the known-value branch of `{{model}}`/`{{cwd}}` in a multi-slot element; the presence tests do not call `LaunchComposer.fill`. The TASK-8 code (adjacent-slot refusal moved to `launchArguments`) was written before its test, so that test was never seen red.
  - Remaining limit, documented: an element with an unknown-value slot followed by a literal (for example `{{prompt_file}}-{{result_schema}}`) can still be split wrongly and accepted. No shipped profile has that shape; refusing it in `ProfileValidator` is open.
  - The `plan-marshall-mcp` shipped profiles cannot be checked from this repository. `verify -Pcoverage` was not run locally.
- Run cost: CodeRabbit's quota refusal used the automatic-review loop-back rounds (6 firings, ceiling 3, one operator grant); 4 candidate-lesson messages from the retrospective are in the epic inbox.

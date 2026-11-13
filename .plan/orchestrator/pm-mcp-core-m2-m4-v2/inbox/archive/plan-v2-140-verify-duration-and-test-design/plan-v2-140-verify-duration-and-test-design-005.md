envelope_version=1
sender_type=plan
sender_id=plan-v2-140-verify-duration-and-test-design
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-13T11:56:05Z

## What landed

plan-v2-140-verify-duration-and-test-design shipped as #403 (merged).

```landing-facts
schema=landing-facts/1
plan_id=plan-v2-140-verify-duration-and-test-design
epic=pm-mcp-core-m2-m4-v2
pr=#403
merge_state=merged
cleanup_owed=false
deliverables_total=7
deliverables_done=7
total_tokens=22806764
total_wall_seconds=63150.0
harness=claude
model=claude-sonnet-5-5
effort=medium
plugin_version=0.1.2114
executor_version=0.1.2114
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,finalize-step-preference-emitter:done,plan-marshall:plan-retrospective:done,record-metrics:done,finalize-step-print-phase-breakdown:done
unresolved_review_threads=0
review_body_findings_total=0
review_body_findings_handled=0
sonar_open_new_issues=0
step.functional-review.deliverables_met=3
step.functional-review.deliverables_met_with_residuals=4
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=2
step.code-review.rules_cited=2
```

## Residue

- The three review counts (unresolved threads, body findings total and handled) are read from the finalize FIND of the two review bots: CodeRabbit left one summary issue comment and no review thread, Sourcery refused on quota (optional bot); the lifecycle producer records none of the three as facts, so they were measured from that FIND, not from a gate record.
- `sonar_open_new_issues=0` comes from a plain HTTP query of SonarCloud (pullRequest 403: no open issues, quality gate OK), because the Sonar producer has no credentials configured here (`sonar-roundtrip` recorded Branch C). Configuring the credentials is an operator task.
- Deliverable 6 (the test rework the operator chose to include) was delivered narrow: three `QuestionLogRecordReader` tests on `@TempDir` plus the `LspClientTest` bounds; the rest is recorded as findings and four operator leads in ADR 025. The parent POM's `reuseForks=false` is the root cause of the 650 s and is a lead for the operator.
- Pre-merge: the merge candidate was blocked once by `merge_candidate_not_gated` because a documentation-correction commit made after the first review round advanced HEAD; both reviews were re-run on the new HEAD before the PR entered the merge queue. The `final_merge_without_asking=false` prompt was not fired; the merge followed the repository CLAUDE.md standing rule and the user's instruction to continue unattended.

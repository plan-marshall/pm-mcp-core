envelope_version=1
sender_type=plan
sender_id=plan-v2-40-envelope-packing
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-13T09:03:19Z

## What landed

plan-v2-40-envelope-packing shipped as #402 (merged by the merge queue, merge commit d503d8e30f47269af7024d13ad4a9f36478500ac).

```landing-facts
schema=landing-facts/1
plan_id=plan-v2-40-envelope-packing
epic=pm-mcp-core-m2-m4-v2
pr=#402
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=20695505
total_wall_seconds=45524.0
harness=claude
model=claude-sonnet-5-5
effort=medium
plugin_version=0.1.2114
executor_version=0.1.2114
steps=finalize-step-sync-baseline:done,finalize-step-security-audit:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,plan-marshall:plan-retrospective:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done
unresolved_review_threads=unknown
review_body_findings_total=unknown
review_body_findings_handled=unknown
sonar_open_new_issues=unknown
step.functional-review.deliverables_met=5
step.functional-review.deliverables_met_with_residuals=0
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=11
step.code-review.rules_cited=11
```

## Residue

- The four review-and-Sonar measurements are written `unknown`: no finalize step of this run records them as facts (the lifecycle producer gap the payload spec names). What the run did observe: the Sonar public issues API, queried by plain HTTP because credentials were absent, showed 0 open issues on PR #402 after the loop-back; CodeRabbit raised 2 findings plus 1 issue comment on the first push, and the loop-back fixed 2 of them (4d163b, 3e61a3) and accepted 1 (a36ad6). The merge gate reported `findings_gate: clean` and `participation_gate: complete`.
- `plugin_version` and `executor_version` are the start-time stamp (0.1.2114); the refresh at landing could not re-read the model (`reason: model_unknown`), and the run's own work-log shows scripts from the 0.1.2115 plugin cache too, so the stamp may name an older version than the one that finished the run.
- The token total (20.7M) includes cache reads and sub-agent transcripts after `enrich`; the retrospective's pre-enrich figure of 3.72M is a different population.
- The `final_merge_without_asking=false` prompt was not asked: the operator's standing instruction ("continue unattended") and the repository's CLAUDE.md rule for plan lifecycles that merge their own pull request were taken as the permission.
- Open and owed: 11 code-review advisories, ADR 001 line 23 ("kept statement for statement") is now literally false after the packer's merge loop restructure, and the squash-message setting of the merge queue is not recorded in the PR or the repository settings.
- Differences found against plan-marshall's `manage-tasks/scripts/_tasks_cost.py` (reported, not changed): it counts distinct `steps[].target` values where the specification counts distinct `target_files`, and it refuses a negative count with `ValueError` where the specification is silent.

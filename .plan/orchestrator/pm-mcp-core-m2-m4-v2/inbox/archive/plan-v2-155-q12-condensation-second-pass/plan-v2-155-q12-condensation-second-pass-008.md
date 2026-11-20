envelope_version=1
sender_type=plan
sender_id=plan-v2-155-q12-condensation-second-pass
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-20T13:19:00Z

## What landed

plan-v2-155-q12-condensation-second-pass shipped as #524 (merged, squash through the merge queue as d8dca108).

```landing-facts
schema=landing-facts/1
plan_id=plan-v2-155-q12-condensation-second-pass
epic=pm-mcp-core-m2-m4-v2
pr=#524
merge_state=merged
cleanup_owed=false
deliverables_total=6
deliverables_done=6
total_tokens=20614638
total_wall_seconds=16655.0
harness=claude
model=claude-sonnet-5-5
effort=unknown
plugin_version=0.1.2173
executor_version=0.1.2173
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,finalize-step-preference-emitter:done,plan-marshall:plan-retrospective:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done
unresolved_review_threads=unknown
review_body_findings_total=unknown
review_body_findings_handled=unknown
sonar_open_new_issues=unknown
step.functional-review.deliverables_met=4
step.functional-review.deliverables_met_with_residuals=2
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=0
step.code-review.rules_cited=0
step.branch-cleanup.merge_mechanism=merge_queue
step.branch-cleanup.merge_state=merged
step.branch-cleanup.cleanup_owed=false
```

## Residue

- Sonar never analysed this pull request (CI `skip-on-docs-only`; fetch returned `count_status=undecidable`, `analysis_revision_unavailable`), so `sonar_open_new_issues` is `unknown`, not zero. The build and the pre-push gate were not run either (build decision `not_necessary`, docs-only footprint). The operator decided both.
- CodeRabbit (required reviewer) reviewed the earlier head only; its one included review per hour was spent. Its six inline findings were fixed and answered; the final head carries a Sourcery approval and no CodeRabbit review.
- The branch was rebased once onto a moved main (three upstream commits) before the merge; all commits replayed patch-identical.
- Owed outside this PR: the workflow DSL specification item 6 states three `lost` causes where part 02 now states four (`role_undefined`, `class_mismatch`, `role_unqualified`, `role_unavailable`); the `runtime-worker.adoc` Failure Modes table (about lines 640-648) and lines 226-233 are stale; two `===` sections of part 02 carry no developer-guide link; the `trace.py` links check reports false broken links when run from a plan worktree.
- This landing is written before `archive-plan` runs, so `steps` ends at `emit-landing`.
- Seven candidate-lesson messages from the retrospective are in this epic's inbox (-001 to -007).

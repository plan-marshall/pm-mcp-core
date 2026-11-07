envelope_version=1
sender_type=plan
sender_id=plan-v2-93-http-tests-annotations
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-07T19:07:36Z

## What landed

plan-v2-93-http-tests-annotations shipped as #316 (merged).

```landing-facts
schema=landing-facts/1
plan_id=plan-v2-93-http-tests-annotations
epic=pm-mcp-core-m2-m4-v2
pr=#316
merge_state=merged
cleanup_owed=false
deliverables_total=9
deliverables_done=9
total_tokens=24713538
total_wall_seconds=34662
harness=claude
model=claude-sonnet-5-5
effort=unknown
plugin_version=0.1.2041
executor_version=0.1.2041
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,finalize-step-preference-emitter:done,plan-marshall:plan-retrospective:done,record-metrics:done,finalize-step-print-phase-breakdown:done
step.functional-review.deliverables_met=7
step.functional-review.deliverables_met_with_residuals=2
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=11
step.code-review.rules_cited=6
```

## Residue

- Merged under a recorded `barrier-ask-override` over `review-barrier-gap`: the required reviewer CodeRabbit did not review the last commit a76cb3f4 (quota refusal, stale review on an earlier commit) and the optional Sourcery refused on size. The unreviewed delta is three small edits made from CodeRabbit's own earlier comments; the operator chose "Merge anyway (record reason)" twice (once at the automatic-review re-review timeout, once at the merge gate). The merge went through the GitHub merge queue as commit 73de1648.
- Scope change after the outline, by operator decision at refine: a minimal read-only `SonarClient`/`SonarIssue` in pm-provider-sonar replaced the spec's "no production change"; a second, operator-instructed production touch was the import-order rewrite of pm-provider-http `HttpClient.java` (commit 384de3ec) produced by `verify -Ppre-commit`.
- Sonar plugin credentials were not configured, so the Sonar step recorded `work_performed=false`; the evidence (0 open issues, quality gate OK at 91f7adf5 and a76cb3f4) was read from the public SonarCloud REST API. An earlier SonarCloud failure (java:S6416 BLOCKER, java:S1192) on SonarClient was fixed by TASK-11 (91f7adf5).
- Open advisories (not blocking): EndpointHarnessTest has no LogAsserts; assertion messages under the 20-character minimum in five converted test classes; four developer guides (provider-http, provider-github, provider-gitlab, runtime-corpus) still cite superseded ADR 002; the spec's per-class literal counts before/after were not computed; the `INLINE SCHEMA BLOCKS 91` of the newer trace.py exist on main too and are not from this branch.
- Metrics note: two usage figures were mis-entered by the orchestrator and corrected with one negative-delta accumulate call (logged as a WARNING decision); one boundary row is recorded as `budget_yield` because `record-dispatch-boundary` has no `verification_handback` cause.
- Seven candidate-lessons from the retrospective are already in this epic's inbox (plan-v2-93-http-tests-annotations-001..007).

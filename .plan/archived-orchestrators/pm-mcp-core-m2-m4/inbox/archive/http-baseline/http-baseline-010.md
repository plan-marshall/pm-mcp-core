envelope_version=1
sender_type=plan
sender_id=http-baseline
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-11T14:49:47Z

## What landed

http-baseline shipped as #28 (merged).

```landing-facts
schema=landing-facts/1
plan_id=http-baseline
epic=pm-mcp-core-m2-m4
pr=#28
merge_state=merged
cleanup_owed=false
deliverables_total=4
deliverables_done=4
total_tokens=5134663
total_wall_seconds=72409.0
steps=finalize-step-sync-baseline:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,plan-marshall:plan-retrospective:done,record-metrics:done,emit-landing:done,archive-plan:pending
step.branch-cleanup.merge_mechanism=merge_queue
step.create-pr.pr_number=28
```

## Residue

- Deliverable 3 shipped CiHttpClient's cui-http rule as a guard test (`sendsThroughCuiHttp`), not a production change: the verify-first clause found CiHttpClient already routes every call through cui-http.
- Operator-approved scope deviation: the commit "style: apply pre-commit rewrites" carries whitespace-only import-block edits in two pm-provider-gitlab production classes (GitLabClient, GlabCommands) plus GlabCommandsTest, produced by `verify -Ppre-commit`.
- Two review-driven fix rounds after the PR opened (single-use mock responses; IPv6-safe mock server URLs, commit 71c8256). Merge commit 268d2c0.
- No document in plan-marshall-documentation needed changing (per the package file's trace matrix); no documentation pull request was opened.

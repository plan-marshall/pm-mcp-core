envelope_version=1
sender_type=plan
sender_id=code-analysis-base
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-11T20:15:34Z

## What landed

code-analysis-base shipped as #31 (merged, d1608b7); documentation PR plan-marshall/plan-marshall-documentation#31 is open and awaits the operator's merge.

```landing-facts
schema=landing-facts/1
plan_id=code-analysis-base
epic=pm-mcp-core-m2-m4
pr=#31
merge_state=merged
cleanup_owed=false
deliverables_total=10
deliverables_done=10
total_tokens=5155669
total_wall_seconds=18045
steps=finalize-step-sync-baseline:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,plan-marshall:plan-retrospective:done,record-metrics:done,emit-landing:done,archive-plan:pending
step.branch-cleanup.merge_mechanism=merge_queue
step.sonar-roundtrip.new_code_issue_count=0
step.create-pr.pr_number=31
```

## Residue

- Deliverables 8-10 live in plan-marshall/plan-marshall-documentation#31 (branch code-analysis-base, commits fdc7a7c, 272fa65, 241a19b), which is OPEN and must be merged by the operator after pm-mcp-core#31. Until then PM-WATCH-MOD-8 is settled in code but still present on documentation main.
- The documentation work was done in a separate worktree at /Users/oliver/git/plan-marshall-documentation-code-analysis-base (the primary documentation checkout is on feature/runtime-lifecycle of another plan); it can be removed after the documentation PR merges.
- Breaking change: FindingSource and FindingResponder lost their type parameters. Users in plan-marshall-mcp against the deployed SNAPSHOT were not checked from this repository.
- Two review-fix loop-back rounds were spent (wait-region-unified-triage 2/3): 3 CodeRabbit findings and 26 Sonar issues fixed.

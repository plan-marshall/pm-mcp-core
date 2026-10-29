envelope_version=1
sender_type=plan
sender_id=plan-37-ported-primitive-tests
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-19T16:40:28Z

## What landed

plan-37-ported-primitive-tests shipped as #106 (merged).

```landing-facts
schema=landing-facts/1
plan_id=plan-37-ported-primitive-tests
epic=pm-mcp-core-m2-m4
pr=#106
merge_state=merged
cleanup_owed=false
deliverables_total=6
deliverables_done=6
total_tokens=6079005
total_wall_seconds=31129.0
harness=claude
steps=finalize-step-sync-baseline:done,finalize-step-security-audit:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,plan-marshall:plan-retrospective:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done,archive-plan:pending
step.functional-review.deliverables_met=5
step.functional-review.deliverables_met_with_residuals=1
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=5
step.code-review.rules_cited=4
step.branch-cleanup.merge_mechanism=merge_queue
step.branch-cleanup.merge_state=merged
step.branch-cleanup.cleanup_owed=false
```

## Residue

- Merge order: documentation pull request https://github.com/plan-marshall/plan-marshall-documentation/pull/95 (pin advance to 297ae30, migration inventory) is the operator's to merge and was meant to land FIRST. This run did not check whether it has merged; it was unmerged when #106 was enqueued.
- `archive-plan:pending` is the step that runs after this message; its outcome cannot be known here.
- The first merge-queue run of #106 failed on a Maven Central error (`surefire-junit-platform:3.6.0` not resolved) and the queue dequeued the PR; the second enqueue at the same HEAD landed as ebcf128e5fcdbe39a7f3427c5f5cf28242a068f1. Infrastructure, not the change.
- The queue sets the squash message itself; no per-enqueue message override exists and `ci repo` has no merge-commit-message setting. A PR comment records this. Check the landed commit for bot prose or a second trailer.
- The `module-tests` gate arm of the pre-push quality gate was DEGRADED. The freshness check could not audit coverage (`required_coverage_unknown`).
- Sourcery (optional bot) never reviewed the last commit; the run proceeded unreviewed by it, by the operator's choice. CodeRabbit's non-ASCII file-name locale concern was accepted without a change.
- Five advisory notes were left unfixed: stale "Rule 6" comments on line 2 of `deletion.txt`, `head-change.txt` and `non-ascii-path.txt`; the nested-class sentence at `provider-git-ported.adoc:218`; missing `@throws` on `WorktreeShaScenario.run` and `unescape` and on `PortedFixtures.roster`, `load` and `read`; wording at `pm-provider-git/README.adoc:13`.
- The planted-difference trial that drops only the D tag byte stays green in the ported suite by design; the existing digest unit test pins that byte.
- The first security audit read only 5 of the 17 fixtures; it was re-run and the gap closed.
- The stale Maven-parser sentence at `03-parsers-and-gates.adoc:66` is out of this slice.

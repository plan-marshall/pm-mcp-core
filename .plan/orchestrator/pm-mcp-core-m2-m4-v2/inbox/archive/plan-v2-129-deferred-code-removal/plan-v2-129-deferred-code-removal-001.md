envelope_version=1
sender_type=plan
sender_id=plan-v2-129-deferred-code-removal
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-12T21:52:19Z

## What landed

plan-v2-129-deferred-code-removal deleted the code of the six mechanisms deferred on 2026-11-02. PR #393 was split into #396 (D1-D3, gate rewrite, fix) and #397 (D4-D6), both merged through the merge queue.

```landing-facts
schema=landing-facts/1
plan_id=plan-v2-129-deferred-code-removal
epic=pm-mcp-core-m2-m4-v2
pr=#393
merge_state=unknown
cleanup_owed=unknown
deliverables_total=6
deliverables_done=6
total_tokens=27786353
total_wall_seconds=54749
harness=claude
model=claude-sonnet-5-5
effort=low
plugin_version=0.1.2106
executor_version=0.1.2106
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,finalize-step-preference-emitter:done,plan-marshall:plan-retrospective:skipped,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done
unresolved_review_threads=unknown
review_body_findings_total=unknown
review_body_findings_handled=unknown
sonar_open_new_issues=0
step.functional-review.deliverables_met=1
step.functional-review.deliverables_met_with_residuals=5
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=6
step.code-review.rules_cited=5
```

## Residue

- The landing is not a single PR. #393 (133 files) was refused by both review bots (CodeRabbit: over the 100-file limit; Sourcery: over 150,000 diff characters), the operator chose to split, and #393 was closed unmerged with an explanatory comment. The change merged as #396 (merge commit 58b921af) and #397 (merge commit 8fb23c4e); the main tree matches the plan tree on all 133 files except upstream Traceability lines. `merge_state=unknown` and `cleanup_owed=unknown` are written because `branch-cleanup` recorded no typed facts for them; branches and the worktree were observed removed.
- Neither review bot reviewed the change (rate-limited / size refusal on #396 and #397). `unresolved_review_threads`, `review_body_findings_total` and `review_body_findings_handled` are unknown because no finalize step records them. `sonar_open_new_issues=0` is the read of the SonarCloud pull-request endpoints for #393, #396 and #397 (gate passed).
- 31 weakened-test findings (109 accepted removal rows) were closed by operator override at the functional review; the accepted removals are recorded in the plan metadata.
- `verify -Pcoverage` and the pm-core/pm-runtime integration tests ran locally on the pre-fix head only; CI ran them on #393, #396 and #397 and passed. The whole-repo `verify -Ppre-commit` (10033 tests) ran on the final head.
- Six code-review advisories (IntentSubmission Javadoc, IntentSchemaParityTest coverage, ProfileStages stale message, ADR workflow-model/002, RoleTopicsTest Javadoc and Traceability line, ADR core-store/049) and eight functional-review advisories are in the plan decision log only.
- Several dispatch-boundary metrics rows carry zero or unmeasured usage; the token total is a lower bound.
- The deletion-safety greps over the sibling repositories used local checkouts.

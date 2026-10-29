envelope_version=1
sender_type=plan
sender_id=plan-69-unit-frontmatter-and-types
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-18T08:34:25Z

## What landed

plan-69-unit-frontmatter-and-types shipped as #91 (merged by the merge queue as 21872fb).

```landing-facts
schema=landing-facts/1
plan_id=plan-69-unit-frontmatter-and-types
epic=pm-mcp-core-m2-m4
pr=#91
merge_state=merged
cleanup_owed=false
deliverables_total=10
deliverables_done=10
total_tokens=10475752
total_wall_seconds=49101.0
harness=claude
steps=finalize-step-sync-baseline:done,finalize-step-security-audit:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,plan-marshall:plan-retrospective:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done,archive-plan:pending
step.functional-review.deliverables_met=7
step.functional-review.deliverables_met_with_residuals=3
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=13
step.code-review.rules_cited=11
```

## Residue

- Breaking for the daemon assembly (`pm-mcp-server`): the deployed SNAPSHOT no longer accepts a unit without a frontmatter, `startState` is optional, and the `CompiledInvariants` constructor takes a new required argument. The default units get their frontmatter in `plan-marshall-mcp` (`default-units-and-drift-tests`).
- Deliverable D9 landed partly by design: the six specification files were left unchanged because the grammar anchors `#unit-frontmatter`, `#unit-types` and `#interface-rules` were kept as short sections.
- Two audit anti-pattern findings stay pending on purpose (Unicode format characters in a unit and a size bound on the unit file); both need a decision in the specification first.
- `trace.py` reports `BROKEN LINKS 1` in `plan-marshall-documentation` (`roadmap.adoc:233`); it predates this change.
- The token total is a floor for phase 1-init (never measured) and counts the operator-accepted post-PR fix loop; the Sourcery review budget notice was filed as a review comment twice (candidate lesson 014).

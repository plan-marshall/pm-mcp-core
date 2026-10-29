envelope_version=1
sender_type=plan
sender_id=plans-index-and-traceability-corrections
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-21T17:57:51Z

## What landed

plans-index-and-traceability-corrections shipped as PR #126 (merged via the merge queue as c45a968).

```landing-facts
schema=landing-facts/1
plan_id=plans-index-and-traceability-corrections
epic=pm-mcp-core-m2-m4
pr=#126
merge_state=merged
cleanup_owed=n/a
deliverables_total=4
deliverables_done=4
total_tokens=unknown
total_wall_seconds=43945
harness=unknown
model=unknown
effort=unknown
plugin_version=unknown
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,finalize-step-preference-emitter:done,plan-marshall:plan-retrospective:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done,archive-plan:pending
step.functional-review.deliverables_met=3
step.functional-review.deliverables_met_with_residuals=1
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=4
step.code-review.rules_cited=3
```

## Residue

- The four documentation corrections landed, plus one production hardening the review of them raised:
  the enrolled `pattern`'s `maxLength` is now capped at 256 code points (`ArgumentSchema.PATTERN_MAX_LENGTH_CEILING`,
  refused at enrolment and re-baseline, ADR `runtime-enrolment/007`). The operator chose that split when the
  documentation-only plan met the CodeRabbit finding, so the plan landed 14 files against a 4-file declaration.
- Four code-review advisories were accepted rather than fixed: the 256 ceiling is described as applying only to
  `string` schemas in the `ArgumentSchema` Javadoc, the runtime developer guide and ADR 001 decision 7, while the
  policy also refuses an untyped schema; the ceiling test lacks a negative at exactly `maxLength`.
- One pre-existing broken link in `plan-marshall-documentation` (`roadmap.adoc:242` still points at
  `pm-core/doc/developer/core-queue.adoc`, removed by pm-mcp-core #122) is outside this plan's footprint and
  was accepted by the operator.
- CodeRabbit's corpus-side ask (`SchemaSubset.checkString` evaluates a pattern before `maxLength`) is untouched:
  it validates the PM-WORK-6 verification corpus, not the enrolled-schema path this PR changes.

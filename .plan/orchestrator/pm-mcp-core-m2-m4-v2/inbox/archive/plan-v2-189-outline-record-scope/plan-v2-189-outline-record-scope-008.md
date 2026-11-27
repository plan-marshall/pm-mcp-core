envelope_version=1
sender_type=plan
sender_id=plan-v2-189-outline-record-scope
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-27T16:10:34Z

## What landed

plan-v2-189-outline-record-scope shipped as #635 (merged, queue squash 5f36ece2). Both reviews closed as operator override accepting four removed-assertion findings each.

```landing-facts
schema=landing-facts/1
plan_id=plan-v2-189-outline-record-scope
epic=pm-mcp-core-m2-m4-v2
pr=#635
merge_state=merged
cleanup_owed=false
deliverables_total=3
deliverables_done=3
total_tokens=11267325
total_wall_seconds=12572.0
harness=claude
model=claude-sonnet-5-5
effort=medium
plugin_version=0.1.2184
executor_version=0.1.2184
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,finalize-step-preference-emitter:done,plan-marshall:plan-retrospective:done,record-metrics:done,finalize-step-print-phase-breakdown:done
unresolved_review_threads=unknown
review_body_findings_total=1
review_body_findings_handled=1
sonar_open_new_issues=0
```

## Residue

- Functional review and code review were each closed by `manage-status loop-back close` (may_close=operator_override at head 99f905f2): four weakened_test findings (removed pinning tests of removed members), accepted by the operator with the reason that the plan's spec orders their deletion. The outline had not listed them under Tests to remove.
- The merge queue squash message took the PR page body, including the CodeRabbit auto-summary, instead of the explicit title-plus-Summary-plus-trailer message.
- Follow-ups outside this plan: the text of doc/plans/phase-sub-workflows.adoc and doc/specification/phase-workflows/03-q-gate-and-execute.adoc still mention the removed members; OutlineSchemaParityTest does not pin the codec read-side required-member lists (CodeRabbit notes on OutlineCodec readRecord/readDeliverable, declined as out of scope).
- unresolved_review_threads was not measured: the review findings were thread-less and no thread read was made.

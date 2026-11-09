envelope_version=1
sender_type=plan
sender_id=plan-v2-116-schema-files-tested-records
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-09T08:21:25Z

## What landed

plan-v2-116-schema-files-tested-records shipped as #323 (merged).

```landing-facts
schema=landing-facts/1
plan_id=plan-v2-116-schema-files-tested-records
epic=pm-mcp-core-m2-m4-v2
pr=#323
merge_state=merged
cleanup_owed=false
deliverables_total=8
deliverables_done=8
total_tokens=13907968
total_wall_seconds=146580.0
harness=claude
model=claude-sonnet-5-5
effort=unknown
plugin_version=0.1.2041
executor_version=0.1.2041
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,finalize-step-preference-emitter:done,plan-marshall:plan-retrospective:skipped,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done
step.branch-cleanup.merge_mechanism=merge_queue
step.branch-cleanup.work_performed=true
step.record-metrics.any_phase_missing_end_time=false
```

## Residue

- The pull request was first 199 files and was split: the 141-file import-sort commit went out as its own PR #324 (label skip-bot-review, merged), so #323 carries 58 files.
- Both reviews (functional and code) were closed by operator override at HEAD 521454af, because the same `SpecificationDocument.java` weakened_test finding recurred (the two `fail()` casts moved to `SchemaResource`, covered by `SchemaResourceTest`).
- CodeRabbit (required) participated; Sourcery (optional) refused on diff size. Sonar was not scanned (no Sonar credentials configured); the PR's SonarCloud check passed.
- `plan-marshall:plan-retrospective` is in the manifest but its lane is `off`; the operator chose to skip it.
- `effort` carries no stamp for this run, so it is written `unknown`.
- Advisory follow-ups, logged only and not fixed: enum-count claims in `pm-workflow/doc/developer/workflow-plan.adoc` (lines 29 and 43); Javadoc enum-equality claims on `PruneReason` and `CompositionCause`; ADR 018 Consequences naming the already-done `schema-files-machine-stores`; the `PlanHistorySchemaParityTest` outcome-pattern refusal probe covers capitals only; a stale comment at `pm-core/pom.xml:22`.

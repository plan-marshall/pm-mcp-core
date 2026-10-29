envelope_version=1
sender_type=plan
sender_id=workflow-engine
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-13T06:47:21Z

## What landed

workflow-engine shipped as #45 (merged, 5a5db68); companion documentation PR plan-marshall/plan-marshall-documentation#49 merged (aa6c377).

```landing-facts
schema=landing-facts/1
plan_id=workflow-engine
epic=pm-mcp-core-m2-m4
pr=#45
merge_state=merged
cleanup_owed=false
deliverables_total=9
deliverables_done=9
total_tokens=7506151
total_wall_seconds=60326
steps=finalize-step-sync-baseline:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,plan-marshall:plan-retrospective:done,record-metrics:done,emit-landing:done,archive-plan:pending
```

## Residue

- PM-WATCH-DSL-14 was trimmed to its open half, not deleted: the `{by, cause}` step-out/unwind record and the check that `step_out` is offered on resting link states only are not built yet.
- Review design points left for later packages: question/task events are not bound to a persisted wait identity (the status schema stores no question id); job/task creation can precede durable status persistence (owned by the pm-runtime port adapters per core-store ADR 002); the static verifier does not require a route for every outcome of the closed job outcome set (refused).
- `WorkflowStatusSchemaParityTest` runs only with `-Dpm.documentation.root` set, so CI does not exercise it unless a documentation checkout is provided.
- Candidate lessons workflow-engine-001..018 are in this epic's inbox.

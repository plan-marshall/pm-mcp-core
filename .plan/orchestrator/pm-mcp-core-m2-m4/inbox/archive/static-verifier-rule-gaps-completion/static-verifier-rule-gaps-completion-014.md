envelope_version=1
sender_type=plan
sender_id=static-verifier-rule-gaps-completion
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-13T08:28:50Z

## What landed

static-verifier-rule-gaps-completion (PLAN-54) shipped as #48 (merged via the merge queue as 36a0bcd).

```landing-facts
schema=landing-facts/1
plan_id=static-verifier-rule-gaps-completion
epic=pm-mcp-core-m2-m4
pr=#48
merge_state=merged
cleanup_owed=false
deliverables_total=7
deliverables_done=7
total_tokens=4121660
total_wall_seconds=80282.0
steps=finalize-step-sync-baseline:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,plan-marshall:plan-retrospective:done,record-metrics:done,emit-landing:done,archive-plan:pending
step.branch-cleanup.merge_mechanism=merge_queue
step.finalize-step-sync-baseline.action=rebased
step.record-metrics.any_phase_missing_end_time=false
```

## Residue

- The package row `static-verifier-rule-gaps` is set to `done` in doc/plans/README.adoc; the Done-When is met with five checks left as seams named in ADRs 002, 006 and 010 (Rule 3 value activation and its refusal, optional-step conservative route, guarded result-field type, remediation budget re-check target, each/role-each over the primitive SPI). PLAN-17 (project-units-and-invariants), which waited on this plan, can start.
- The specification issue "Workflow DSL Rule 3: define how a depends_on_config value activates a guard" is NOT filed yet; its text is in the PR #48 body and ADR 006 for the operator to file in plan-marshall-documentation.
- After the finalize rebase onto #45 (PLAN-13 workflow engine) the stricter verifier refused 39 engine test fixtures; this plan fixed them (TASK-12) without weakening assertions. WorkflowEngineTest.shouldFaultOnUndeclaredOutcome became shouldFaultOnOutcomeOutsideVerifiedContract; the engine's guard-miss branch is now unreachable for verified units (kept, not deleted).
- EntityKindRegistry.defaults() still maps primitive ids (screen.evaluate, triage.evaluate, ...) the specification does not name; left unchanged.

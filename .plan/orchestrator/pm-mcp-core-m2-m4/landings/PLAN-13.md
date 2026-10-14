# Landing Analysis: PLAN-13 — Workflow engine

epic: pm-mcp-core-m2-m4
workstream: WS-03
pr: #45 (https://github.com/plan-marshall/pm-mcp-core/pull/45), merge commit 5a5db68

> Landing record for one shipped plan. Lives at `landings/PLAN-13.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source: inbox message `workflow-engine-019.md` (landing-facts complete, no missing key),
corroborated on 2026-10-13 against:
- the PR (CI on head bb57bc6, `/review` history);
- the file list of 5a5db68;
- the index on `origin/main`;
- `plan-marshall-documentation` #49;
- the archived plan's decision log and build logs.

Harness: **Claude Code**. The operator's default since 2026-10-13, and consistent with the archive:
measured tokens and no other harness's traces.

## Deliverable Fidelity vs Spec

44 files, +8,520; 9 deliverables by the plan's count, the 9th the documentation PR.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| State machine interpreter and sub-workflow execution | shipped-as-specified | `workflow/engine/` main and test; runs verified units only (ADR workflow-engine 001); every wait an engine event (002); the call stack in one transaction (003) |
| Workflow status store | shipped-modified | `core.store` (`WorkflowStatus`, codec, store, `RetryCounters`), with ADRs core-store 001–006. Engine ports in `core.spi` (`JobStarter`, `QuestionRecordReader`, `QueueTaskCreator`, `WorkflowClock`; ADR core-store 002) — a package the spec did not declare |
| Index row | shipped-as-specified | `workflow-engine` `done` |
| Module documentation (WS-10 rule) | shipped | `pm-workflow/doc/developer/workflow-engine.adoc`, `pm-core/doc/developer/core-store.adoc`, 3 + 6 ADRs. The first plan built under the rule |
| Documentation PR | shipped | `plan-marshall-documentation` #49 MERGED; PM-WATCH-DSL-14 trimmed to its open half (the `{by, cause}` step-out record and the `step_out` resting-only check) |

Surface: beyond the declared `workflow.engine` and `core.store`, the plan also wrote `core.spi` (the
engine ports) and `core.service` (`InternalFaultException`), plus the module documentation it
declared. Nothing running was touched. No log records, no POM change.

## Metrics and Anomalies

- Tokens: 7,506,151; wall time 60,326 s (about 16.8 hours).
- Specification drift during the run: `plan-marshall-documentation` #43 renamed the phase
  `orchestrator` to `epic`. CodeRabbit caught it, and the plan fixed it with schema parity tests
  (`WorkflowStatusSchemaParityTest`, `WorkflowEnginePhaseParityTest`) (lesson 2026-10-13-06-001).
  No staged spec of this epic names the old phase (checked).
- `WorkflowStatusSchemaParityTest` runs only with `-Dpm.documentation.root` set. It is the same
  CI blind spot as PLAN-36's parity test.
- Gates: `verify -Ppre-commit` (OpenRewrite active) ran at 21:35:44Z, two seconds after the final
  commit bb57bc6. CI green on the head.

## Routing and Merge Behavior

- Review: `/review` re-posted four times, after every fix round; no override.
- CI/merge: green; merge queue, 5a5db68.
- Collisions: none.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#45`; `landing` `landings/PLAN-13.md`
- [x] 18 lessons: 1 promoted here (2026-10-13-06-001), 1 folded into 2026-10-10-14-001 (pre-commit
      rewrites outside the change), 16 routed to plan-marshall (`pm-mcp-core-m2-m4-153.md` to `-168.md`)
- [x] PLAN-40 note: `pm-core/doc/developer/core-store.adoc` now exists — PLAN-40 extends it

## Follow-Ups

Open design points named in review and left to later packages:
- question and task events are not bound to a persisted wait identity;
- job or task creation can precede durable status persistence (the pm-runtime port adapters own it);
- the verifier does not require a route for every outcome of the closed job outcome set.

The parity tests need a documentation checkout in CI to run there.

Unblocked: PLAN-21 `entity-addressing`, PLAN-22 `scenario-replay-harness`, one `core.store` plan
at a time (PLAN-09, PLAN-12, PLAN-14 or PLAN-15); with PLAN-12, PLAN-23 `stalled-scope-detection`.

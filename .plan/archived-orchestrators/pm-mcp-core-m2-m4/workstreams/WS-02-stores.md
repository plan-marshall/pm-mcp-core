# WS-02: Stores

epic: pm-mcp-core-m2-m4

> Charter document for one workstream — a coherent slice of the epic with its own goal
> and surface. Lives at `workstreams/WS-02-stores.md` and is tracked in the
> `workstreams[]` field of the epic header, `status.json`. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier contract.

## Charter

The versioned store substrate and the stores built on it: operator and question stores, enrolment, the per-project machine store, the session log and generations. It closes when every store package is done.

## Scope

- In scope: pm-core packages core.store, core.config and the session part of core.queue
- Out of scope: the lock manager the stores lock through (WS-01); the workflow state store, which ships with the engine (WS-03); the task queue records (WS-05)

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-06-store-substrate | staged | Versioned store substrate |
| PLAN-11-enrolment-store | staged | Enrolment store, lifecycle gate and enrolment integrity |
| PLAN-12-session-log-and-generations | staged | Scope session log and session generations |
| PLAN-14-operator-and-question-stores | staged | Operator and question stores of the scopes |
| PLAN-15-project-machine-store | staged | Per-project machine store and project resolution |

## Sequencing and Surface Notes

- The order inside the workstream is the dependency order of `doc/plans/README.adoc`; each spec names its dependencies by plan id.
- Shared surfaces with other workstreams are named per spec under Dependencies and Sequencing; `core.store` is the hot one across WS-01, WS-02, WS-03 and WS-04.

# WS-06: Test harnesses

epic: pm-mcp-core-m2-m4

> Charter document for one workstream — a coherent slice of the epic with its own goal
> and surface. Lives at `workstreams/WS-06-test-harnesses.md` and is tracked in the
> `workstreams[]` field of the epic header, `status.json`. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier contract.

## Charter

The harnesses that prove the rest: deterministic scenario replay, the supervision scenarios on it, and the differential harness against the Python reference.

## Scope

- In scope: pm-workflow test package workflow.scenario; the differential harness in test scope
- Out of scope: the production code the harnesses exercise; the real-process worker tests of plan-marshall-mcp

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-22-scenario-replay-harness | staged | Deterministic scenario replay harness |
| PLAN-35-supervision-scenarios | staged | Deterministic supervision scenarios |
| PLAN-37-differential-test-harness | parked | Differential test harness |

## Sequencing and Surface Notes

- The order inside the workstream is the dependency order of `doc/plans/README.adoc`; each spec names its dependencies by plan id.
- Shared surfaces with other workstreams are named per spec under Dependencies and Sequencing; `core.store` is the hot one across WS-01, WS-02, WS-03 and WS-04.

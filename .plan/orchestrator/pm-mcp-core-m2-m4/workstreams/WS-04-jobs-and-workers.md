# WS-04: Jobs and workers

epic: pm-mcp-core-m2-m4

> Charter document for one workstream — a coherent slice of the epic with its own goal
> and surface. Lives at `workstreams/WS-04-jobs-and-workers.md` and is tracked in the
> `workstreams[]` field of the epic header, `status.json`. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier contract.

## Charter

The Milestone 4 job side of the runtime: verify-not-resolve, the subprocess job runner, job confinement and environment, worker launch, the supervisor, and model slots with usage and budgets.

## Scope

- In scope: pm-runtime packages runtime.job, runtime.launch, runtime.security, runtime.worker; job records in core.store
- Out of scope: role artifacts and the task protocol the workers serve (WS-05); the runtime lifecycle seams they join (WS-01)

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-18-verify-not-resolve | staged | Verify-not-resolve pipeline and wrapper verification |
| PLAN-20-job-runner | staged | Subprocess job runner |
| PLAN-27-job-confinement-and-environment | staged | Job confinement and job environment in the daemon |
| PLAN-29-worker-launch | staged | Worker launch and harness enrolment |
| PLAN-30-supervisor | staged | Worker supervisor |
| PLAN-31-slots-usage-and-budgets | staged | Model slots, usage rows and learned budgets |

## Sequencing and Surface Notes

- The order inside the workstream is the dependency order of `doc/plans/README.adoc`; each spec names its dependencies by plan id.
- Shared surfaces with other workstreams are named per spec under Dependencies and Sequencing; `core.store` is the hot one across WS-01, WS-02, WS-03 and WS-04.

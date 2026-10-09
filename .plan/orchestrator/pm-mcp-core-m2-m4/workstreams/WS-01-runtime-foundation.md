# WS-01: Runtime foundation

epic: pm-mcp-core-m2-m4

> Charter document for one workstream — a coherent slice of the epic with its own goal
> and surface. Lives at `workstreams/WS-01-runtime-foundation.md` and is tracked in the
> `workstreams[]` field of the epic header, `status.json`. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier contract.

## Charter

The Milestone 2 groundwork of the daemon: the lock manager and leases, the runtime lifecycle, the audit trail, workspace confinement, the per-user service unit and stalled-scope detection. It closes when every one of these packages is done in the index.

## Scope

- In scope: pm-core packages core.service and the lock part of core.store; pm-runtime packages runtime.lock and runtime.start; the confinement checks
- Out of scope: the versioned stores themselves (WS-02); jobs and workers that later join the lifecycle seams (WS-04)

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-01-lock-manager-and-leases | staged | Runtime lock manager, lease records, and the stale lease sweep |
| PLAN-03-workspace-confinement | staged | Workspace confinement |
| PLAN-04-service-unit | staged | Per-user service unit |
| PLAN-08-runtime-lifecycle | staged | Runtime lifecycle: bounded verification, drain, recovery, sweep, version mismatch |
| PLAN-09-audit-trail | staged | Anchored audit trail |
| PLAN-23-stalled-scope-detection | staged | Stalled-scope detection |

## Sequencing and Surface Notes

- The order inside the workstream is the dependency order of `doc/plans/README.adoc`; each spec names its dependencies by plan id.
- Shared surfaces with other workstreams are named per spec under Dependencies and Sequencing; `core.store` is the hot one across WS-01, WS-02, WS-03 and WS-04.

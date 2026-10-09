# WS-07: Code analysis base

epic: pm-mcp-core-m2-m4

> Charter document for one workstream — a coherent slice of the epic with its own goal
> and surface. Lives at `workstreams/WS-07-code-analysis-base.md` and is tracked in the
> `workstreams[]` field of the epic header, `status.json`. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier contract.

## Charter

The single Milestone 6 package of the index: the provider-neutral analysis record, findings types, provider interface and contract in pm-core. Declarations, serialization and validation only.

## Scope

- In scope: pm-core packages core.analysis and core.findings
- Out of scope: the Sonar implementation and any HTTP call (roadmap Milestone 6); the store write (after the store substrate)

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-36-code-analysis-base | staged | Code analysis base: format, interface and contract |

## Sequencing and Surface Notes

- The order inside the workstream is the dependency order of `doc/plans/README.adoc`; each spec names its dependencies by plan id.
- Shared surfaces with other workstreams are named per spec under Dependencies and Sequencing; `core.store` is the hot one across WS-01, WS-02, WS-03 and WS-04.

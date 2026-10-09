# WS-08: HTTP baseline

epic: pm-mcp-core-m2-m4

> Charter document for one workstream — a coherent slice of the epic with its own goal
> and surface. Lives at `workstreams/WS-08-http-baseline.md` and is tracked in the
> `workstreams[]` field of the epic header, `status.json`. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier contract.

## Charter

The operator's HTTP rules made true for the code that exists: every HTTP call through cui-http and every HTTP test on cui-test-mockwebserver-junit5, so that later packages inherit a clean baseline.

## Scope

- In scope: the HTTP base and the HTTP tests of the provider modules pm-provider-ci, pm-provider-github, pm-provider-gitlab
- Out of scope: new provider features; pm-core and pm-workflow, which must never depend on cui-http

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-05-http-baseline | staged | HTTP baseline: cui-http for calls, the mock web server for tests |

## Sequencing and Surface Notes

- The order inside the workstream is the dependency order of `doc/plans/README.adoc`; each spec names its dependencies by plan id.
- Shared surfaces with other workstreams are named per spec under Dependencies and Sequencing; `core.store` is the hot one across WS-01, WS-02, WS-03 and WS-04.

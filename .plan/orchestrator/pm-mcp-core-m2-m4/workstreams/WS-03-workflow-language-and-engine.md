# WS-03: Workflow language and engine

epic: pm-mcp-core-m2-m4

> Charter document for one workstream — a coherent slice of the epic with its own goal
> and surface. Lives at `workstreams/WS-03-workflow-language-and-engine.md` and is tracked in the
> `workstreams[]` field of the epic header, `status.json`. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier contract.

## Charter

The dependency chain from the primitive SPI over the DSL parser, the static verifier and the interpreter to plan instances, diagrams, project units and entity addressing. It closes when a verified fixture unit runs, resumes and renders.

## Scope

- In scope: pm-core package core.spi; pm-workflow packages dsl, ast, verify, engine, diagram, unit, plan
- Out of scope: waits a model holds and everything of the model work (WS-05); the scenario harness (WS-06)

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-02-primitive-spi-and-validators | staged | Primitive SPI and in-process content validators |
| PLAN-07-workflow-dsl-parser-and-ast | staged | Workflow DSL parser and immutable AST |
| PLAN-10-static-verifier-and-fuzzer | staged | Static verifier and transition fuzzer |
| PLAN-13-workflow-engine | staged | State machine interpreter and sub-workflow execution |
| PLAN-16-diagram-generator | staged | Diagram and table generators |
| PLAN-17-project-units-and-invariants | staged | Loader of project-owned units and the compiled invariants |
| PLAN-21-entity-addressing | staged | Entity addressing of the bootstrap units |
| PLAN-24-plan-instance | staged | Plan instance composition |

## Sequencing and Surface Notes

- The order inside the workstream is the dependency order of `doc/plans/README.adoc`; each spec names its dependencies by plan id.
- Shared surfaces with other workstreams are named per spec under Dependencies and Sequencing; `core.store` is the hot one across WS-01, WS-02, WS-03 and WS-04.

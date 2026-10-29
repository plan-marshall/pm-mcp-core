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
| PLAN-41-static-verifier-remaining-rules | staged | Static verifier: the remaining rules (added to the index by #32) |
| PLAN-42-operator-wait-and-machine-scope | parked | The operator wait and the machine scope (added by #35; blocked on use cases) |
| PLAN-43-phase-sub-workflows | parked | Phases as sub-workflows, the plan's history (added by #36; blocked on UC-PLN-1) |
| PLAN-44-static-verifier-rule-gaps | staged | The rule gaps PLAN-41 left (operator decision 2026-10-12) |
| PLAN-45-project-onboarding-unit | parked | The project onboarding unit (added by #37; blocked on UC-PRJ-1 and PLAN-42) |
| PLAN-46..PLAN-53 | parked | Blocked packages added by #39-#42 (machine onboarding, decision engine, start from clients, recipe workflows, plan/epic control, epic findings review, epic decomposition); PLAN-49 forgejo-provider in WS-08 |
| PLAN-54-static-verifier-rule-gaps-completion | staged | Second remediation of the verifier gaps; Claude Code only (operator decision 2026-10-12) |
| PLAN-56-static-verifier-residuals | staged | Residual verifier gaps after PLAN-54; blocks nothing |
| PLAN-57-epic-auto | parked | An epic runs by itself (added by #58) |
| PLAN-58-operator-reports | parked | Reports to the operator (added by #58) |

## Sequencing and Surface Notes

- The order inside the workstream is the dependency order of `doc/plans/README.adoc`; each spec names its dependencies by plan id.
- Shared surfaces with other workstreams are named per spec under Dependencies and Sequencing; `core.store` is the hot one across WS-01, WS-02, WS-03 and WS-04.

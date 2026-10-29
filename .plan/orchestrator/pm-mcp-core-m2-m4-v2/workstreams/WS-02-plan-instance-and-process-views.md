# WS-02: Plan instance and process views

epic: pm-mcp-core-m2-m4-v2

> Charter document for one workstream — a coherent slice of the epic with its own goal
> and surface. Lives at `workstreams/WS-02-plan-instance-and-process-views.md` and is tracked in the
> `workstreams[]` field of the epic header, `status.json`. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier contract.

## Charter

The plan instance (versions, files and history), the process designer and overview, the recipe workflows, the default plan lanes and the scenario and layout tests of the workflow language.

## Scope

- In scope: pm-workflow plan, diagram, scenario, unit; default units fixtures
- Out of scope: the surfaces of the other workstreams (WS-01, WS-03, WS-04, WS-05, WS-06, WS-07, WS-08, WS-09, WS-10); a plan belongs to exactly one workstream.

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-V2-13-plan-instance-versions | staged | Plan instance versions in force: composition events, the engine's switch, Rules 13 and 14, resources |
| PLAN-V2-26-process-and-overview | staged | The plan's process, running work, and ended work as resources |
| PLAN-V2-33-scenario-task-protocol | staged | The scenario harness runs the task protocol: scripted workers with a lease and a generation |
| PLAN-V2-44-positioned-layout | staged | The positioned layout of a unit and of a graph of modules, computed on the server |
| PLAN-V2-49-scenario-worker-faults | staged | Worker faults in the scenario harness: the fault directives, the fault matrix and a runtime crash with a lease |
| PLAN-V2-54-recipe-workflows | staged | A recipe is a workflow |
| PLAN-V2-57-default-plan-lanes-and-run | staged | The fixture default plan models the land and adopt lanes, and the engine runs it end to end |
| PLAN-V2-58-verifier-cycle-scale | staged | Measure the static verifier's cycle enumeration on units of the size the product will have, and bound it |
| PLAN-V2-69-process-designer | staged | Designing a plan's process |

## Sequencing and Surface Notes

- The queue order (`seq`) is the n=4 schedule of the epic (see `epic.md`, Queue annotations); `next` fills the free slots in that order and the disjointness gate (`corpus surfaces`, `corpus cross-check`) decides each emit.
- Plans of one workstream share hot files with each other more than with other workstreams; the stated `Depends on:` lines of the specs are the hard order.

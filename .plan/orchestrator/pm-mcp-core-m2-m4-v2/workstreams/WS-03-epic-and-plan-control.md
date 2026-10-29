# WS-03: Epic and plan control

epic: pm-mcp-core-m2-m4-v2

> Charter document for one workstream — a coherent slice of the epic with its own goal
> and surface. Lives at `workstreams/WS-03-epic-and-plan-control.md` and is tracked in the
> `workstreams[]` field of the epic header, `status.json`. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier contract.

## Charter

The control of plans and epics by the operator: the decision engine and judgment votes, plan and epic control, epic decomposition, auto mode, review, resources and release, operator reports, cleanup, start-source intake and own calls.

## Scope

- In scope: pm-workflow epic/plan control, pm-core epic records, pm-runtime epic resources
- Out of scope: the surfaces of the other workstreams (WS-01, WS-02, WS-04, WS-05, WS-06, WS-07, WS-08, WS-09, WS-10); a plan belongs to exactly one workstream.

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-V2-21-plan-and-epic-control | staged | Controlling plans |
| PLAN-V2-22-start-source-intake | staged | The entry as a directory and the intake of the sources |
| PLAN-V2-23-decision-engine | staged | Decisions as the operator meets them |
| PLAN-V2-27-epic-resource-and-release | staged | The epic as a resource, its actions, and the release of a project |
| PLAN-V2-32-epic-decomposition | staged | The decomposition of an epic, and the intent it is cut from |
| PLAN-V2-38-epic-auto | staged | An epic runs by itself |
| PLAN-V2-41-operator-reports | staged | Reports to the operator |
| PLAN-V2-64-epic-review | staged | The review of an epic's findings and watches, by the operator |
| PLAN-V2-74-cleanup | parked | Cleaning up: one cleanup of the machine |
| PLAN-V2-76-judgment-votes | parked | A judgment role as a voter of the decision quorum |
| PLAN-V2-77-own-calls | parked | A writer states its own calls |

## Sequencing and Surface Notes

- The queue order (`seq`) is the n=4 schedule of the epic (see `epic.md`, Queue annotations); `next` fills the free slots in that order and the disjointness gate (`corpus surfaces`, `corpus cross-check`) decides each emit.
- Plans of one workstream share hot files with each other more than with other workstreams; the stated `Depends on:` lines of the specs are the hard order.

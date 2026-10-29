# WS-01: Command model and unit contract

epic: pm-mcp-core-m2-m4-v2

> Charter document for one workstream — a coherent slice of the epic with its own goal
> and surface. Lives at `workstreams/WS-01-command-model-and-unit-contract.md` and is tracked in the
> `workstreams[]` field of the epic header, `status.json`. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier contract.

## Charter

The command model of the engine and the contract of a unit: typed failure reasons on a primitive outcome, the command record and its dispatch, the unit interface in the frontmatter, the call-time inputs and slots, the production registries and the git commit primitives.

## Scope

- In scope: pm-core core.spi, pm-workflow dsl/ast/verify/engine contract, pm-provider-git primitives
- Out of scope: the surfaces of the other workstreams (WS-02, WS-03, WS-04, WS-05, WS-06, WS-07, WS-08, WS-09, WS-10); a plan belongs to exactly one workstream.

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-V2-01-primitive-failure-reasons | staged | A primitive's failure, skip and indeterminacy carry a typed, closed reason as data: `OutcomeReason`, `Primitiv |
| PLAN-V2-04-unit-input-interface | staged | A unit declares its inputs and what it reads in its frontmatter |
| PLAN-V2-10-production-registries-and-wiring | staged | The production registries of effective configuration keys and fact kinds, wired into the unit verifier |
| PLAN-V2-15-call-time-inputs-and-slots | staged | Free text and paths reach a call as typed values: slots, bind validation, provenance, `git.commit_and_push` as |
| PLAN-V2-19-engine-carries-reasons | staged | The engine uses the reason of a primitive's result: it checks it, raises the condition a reason maps to, never |
| PLAN-V2-20-git-commit-primitives | staged | The commit primitives `git.resolve-message`, `git.template-message` and `git.stage-and-commit`, and the entity |
| PLAN-V2-25-command-record-and-dispatch | staged | Every primitive the engine runs is a `Command` with an id, a correlation and a provenance, and its `CommandSet |

## Sequencing and Surface Notes

- The queue order (`seq`) is the n=4 schedule of the epic (see `epic.md`, Queue annotations); `next` fills the free slots in that order and the disjointness gate (`corpus surfaces`, `corpus cross-check`) decides each emit.
- Plans of one workstream share hot files with each other more than with other workstreams; the stated `Depends on:` lines of the specs are the hard order.

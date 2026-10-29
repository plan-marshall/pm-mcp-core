# WS-10: Spec bookkeeping and hygiene

epic: pm-mcp-core-m2-m4-v2

> Charter document for one workstream — a coherent slice of the epic with its own goal
> and surface. Lives at `workstreams/WS-10-spec-bookkeeping-and-hygiene.md` and is tracked in the
> `workstreams[]` field of the epic header, `status.json`. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier contract.

## Charter

Documentation-only and hygiene plans: package files that carry specification without an owner, the condensing of role-set and wait/launch-protocol specifications, and the last leftovers of main.

## Scope

- In scope: doc/plans, doc/specification (condensing only), single leftovers
- Out of scope: the surfaces of the other workstreams (WS-01, WS-02, WS-03, WS-04, WS-05, WS-06, WS-07, WS-08, WS-09); a plan belongs to exactly one workstream.

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-V2-34-r05-reduction-waits-launch-protocol | staged | Apply R05 to the shipped waits, worker launch, task protocol and consultation |
| PLAN-V2-45-package-files-carry-unowned-spec | staged | Package files carry the specification content that no package names |
| PLAN-V2-50-r05-reduction-role-sets | staged | Apply R05 to the shipped role sets, model caller and role qualification |
| PLAN-V2-67-main-hygiene-leftovers | staged | Settle the two SonarCloud issues of main that no plan owns |

## Sequencing and Surface Notes

- The queue order (`seq`) is the n=4 schedule of the epic (see `epic.md`, Queue annotations); `next` fills the free slots in that order and the disjointness gate (`corpus surfaces`, `corpus cross-check`) decides each emit.
- Plans of one workstream share hot files with each other more than with other workstreams; the stated `Depends on:` lines of the specs are the hard order.

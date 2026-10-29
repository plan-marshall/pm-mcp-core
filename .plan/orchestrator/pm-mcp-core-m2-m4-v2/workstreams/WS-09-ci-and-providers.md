# WS-09: Ci and providers

epic: pm-mcp-core-m2-m4-v2

> Charter document for one workstream — a coherent slice of the epic with its own goal
> and surface. Lives at `workstreams/WS-09-ci-and-providers.md` and is tracked in the
> `workstreams[]` field of the epic header, `status.json`. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier contract.

## Charter

The CI contract module and its pull-request view, and the Forgejo provider.

## Scope

- In scope: pm-providers ci, github, gitlab, forgejo
- Out of scope: the surfaces of the other workstreams (WS-01, WS-02, WS-03, WS-04, WS-05, WS-06, WS-07, WS-08, WS-10); a plan belongs to exactly one workstream.

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-V2-12-ci-contract-module | staged | The CI contract module `pm-provider-ci`: identity, issue read and issue comment, on GitHub and GitLab |
| PLAN-V2-62-ci-contract-pull-request-view | staged | The pull-request read `ci.pr-view` on the CI contract, on GitHub and GitLab |
| PLAN-V2-72-forgejo-provider | parked | Codeberg and Forgejo as a provider |

## Sequencing and Surface Notes

- The queue order (`seq`) is the n=4 schedule of the epic (see `epic.md`, Queue annotations); `next` fills the free slots in that order and the disjointness gate (`corpus surfaces`, `corpus cross-check`) decides each emit.
- Plans of one workstream share hot files with each other more than with other workstreams; the stated `Depends on:` lines of the specs are the hard order.

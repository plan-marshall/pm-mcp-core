# WS-04: Clients onboarding and profiles

epic: pm-mcp-core-m2-m4-v2

> Charter document for one workstream — a coherent slice of the epic with its own goal
> and surface. Lives at `workstreams/WS-04-clients-onboarding-and-profiles.md` and is tracked in the
> `workstreams[]` field of the epic header, `status.json`. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier contract.

## Charter

What the clients see and what the machine and the project are enrolled with: onboarding units, start from clients, chat, harness verification, machine settings, client-profile rendering and checks, the envelope and long-context thresholds.

## Scope

- In scope: pm-core profile, pm-runtime onboarding and harness, pm-workflow chat and start
- Out of scope: the surfaces of the other workstreams (WS-01, WS-02, WS-03, WS-05, WS-06, WS-07, WS-08, WS-09, WS-10); a plan belongs to exactly one workstream.

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-V2-17-project-onboarding-unit | staged | The project onboarding unit |
| PLAN-V2-24-start-from-clients | staged | Starting work from the clients: the bootstraps and the clients' side |
| PLAN-V2-29-chat | staged | Chatting with the planning role |
| PLAN-V2-39-machine-harness-verification | staged | The harness scan, the probes and the verification of an unknown harness |
| PLAN-V2-40-envelope-packing | staged | Build the envelope packing: cost sizing, the Next-Fit packer and the envelope budget with its long-context cap |
| PLAN-V2-43-machine-settings-and-axes | staged | The machine's settings, the automatic onboarding and the plan axes |
| PLAN-V2-52-client-profile-renderer | staged | ClientProfileRenderer: placeholder rendering of templates by the profile, with a per-variant digest |
| PLAN-V2-63-long-context-thresholds | staged | Admit and validate `headless.long_context_thresholds` and feed the envelope budget cap |
| PLAN-V2-68-profile-wait-margin | staged | Hold a profile's bounded wait to the specified 30-second margin below the idle timeout |
| PLAN-V2-70-host-resident-surface-inventory | staged | Host-resident surface inventory: the calculator, the derived bounds and the test that enforces them |
| PLAN-V2-71-profile-skill-load-template-check | staged | Profile validation requires the skill-load template to name the skill (`{{uri}}`) |
| PLAN-V2-75-chat-plan-revision | parked | A plan's revision from a chat |
| PLAN-V2-81-machine-stores-residue | staged | The open Sonar issues, the unresolved review findings, the coverage run and the two specification inconsistencies of PLAN-46 part 1 |

## Sequencing and Surface Notes

- The queue order (`seq`) is the n=4 schedule of the epic (see `epic.md`, Queue annotations); `next` fills the free slots in that order and the disjointness gate (`corpus surfaces`, `corpus cross-check`) decides each emit.
- Plans of one workstream share hot files with each other more than with other workstreams; the stated `Depends on:` lines of the specs are the hard order.

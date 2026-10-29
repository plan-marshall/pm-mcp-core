# WS-05: Stores roles and facts

epic: pm-mcp-core-m2-m4-v2

> Charter document for one workstream — a coherent slice of the epic with its own goal
> and surface. Lives at `workstreams/WS-05-stores-roles-and-facts.md` and is tracked in the
> `workstreams[]` field of the epic header, `status.json`. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier contract.

## Charter

The durable stores and records of the core: the epic ledger records, the lesson store, the derived facts store, the role-name definition and the role records (qualification token, decision calibration), and the corpus schema bounds.

## Scope

- In scope: pm-core core.store, core.role, core.id, core.lesson, core.facts
- Out of scope: the surfaces of the other workstreams (WS-01, WS-02, WS-03, WS-04, WS-06, WS-07, WS-08, WS-09, WS-10); a plan belongs to exactly one workstream.

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-V2-02-derived-facts-records-and-kinds | staged | Derived facts store (1 of 2): the entry records, the strict codec, the kind registry |
| PLAN-V2-03-epic-ledger-header-and-queue | staged | The epic ledger's header and queue rows: records, codecs, stores and schema parity |
| PLAN-V2-05-derived-facts-store-operations | staged | Derived facts store (2 of 2): the store, the recompute rule, stale and failed, the gate helper |
| PLAN-V2-06-role-name-single-definition | staged | One definition of the role-name pattern, and a guard that fails on a copy |
| PLAN-V2-07-epic-ledger-inbox-and-registers | staged | The epic inbox and the findings and watches registers: records, codecs, stores and schema parity |
| PLAN-V2-11-lesson-record-and-codec | staged | The lesson record and its file format |
| PLAN-V2-16-lesson-store-operations | staged | The lesson store: allocation, resolver, revision, retirement and claim |
| PLAN-V2-56-qualification-token-split | staged | Bring the qualification and judge-check codecs to the token-usage closure and fix the comments it left stale |
| PLAN-V2-60-decision-calibration-record | staged | The decision calibration record: codec, store and declaration digest in core.role.qualification |
| PLAN-V2-65-corpus-schema-length-bounds | staged | Bound the strings the shipped corpus schemas match with a pattern |

## Sequencing and Surface Notes

- The queue order (`seq`) is the n=4 schedule of the epic (see `epic.md`, Queue annotations); `next` fills the free slots in that order and the disjointness gate (`corpus surfaces`, `corpus cross-check`) decides each emit.
- Plans of one workstream share hot files with each other more than with other workstreams; the stated `Depends on:` lines of the specs are the hard order.

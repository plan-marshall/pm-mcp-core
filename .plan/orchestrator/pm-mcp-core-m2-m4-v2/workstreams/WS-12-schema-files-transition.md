# WS-12: Schema files transition

epic: pm-mcp-core-m2-m4-v2

> Charter document for one workstream — a coherent slice of the epic with its own goal
> and surface. Lives at `workstreams/WS-12-schema-files-transition.md` and is tracked in the
> `workstreams[]` field of the epic header, `status.json`. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier contract.

## Charter

Make the JSON schemas, the limits and the listed numbers of the specification files of the module that implements them, and retire every test that reads a specification at run time. Today ten parity tests, two tests that regex a number out of specification prose, two list tests and the helper `SpecificationDocument` read `.adoc` files, which lets a specification-only pull request break `main` (ADR 009, the include list of ADR 015). Operator decision 2026-11-06: the transition is the highest priority of the epic and `schema-files-foundation` is the next plan to emit, ahead of the PLAN-V2-84 family wherever the surfaces allow. The packages are the work packages of `doc/plans/` (pull request #304); the plans below are their staged specs.

## Scope

- In scope: the schema files below `src/main/resources` of the owning modules, the specification documents that include them, the parity and limit tests, the test helper `SchemaResource`, the ADR and the include list of the docs-only skip, and the documents of `plan-marshall-documentation` that state the old rule (a pull request beside).
- Out of scope: validating a stored record against its schema at run time (open question `runtime-schema-validation`, a compile-scope validator needs the operator); the six MCP tool schemas of `plan-marshall-mcp` (`tool-schemas-single-source`, carried by another session); the surfaces of the other workstreams' feature plans.

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-V2-115-schema-files-foundation | staged | The ADR, layout, include convention, `SchemaResource`, `trace.py`; first, every other plan depends on it |
| PLAN-V2-116-schema-files-tested-records | staged | 27 blocks in seven store-schema documents; eight parity tests read the file |
| PLAN-V2-117-schema-files-machine-stores | staged | 22 blocks in four documents; the audit and credential parity tests |
| PLAN-V2-118-schema-files-remaining-records | staged | 25 blocks in seven documents; a new parity test per schema whose codec exists |
| PLAN-V2-119-schema-files-other-specifications | staged | 14 blocks in eight documents (domain extensions, harness profiles, model work) |
| PLAN-V2-120-limits-in-data-files | staged | Stalled threshold and unit frontmatter limits in properties files; after PLAN-V2-89 and 98 |
| PLAN-V2-121-generated-listings | parked | BLOCKED on the open question `generated-listings`; never emitted before the operator decides |
| PLAN-V2-122-adoc-reading-retired | staged | Closing package; emit last |

## Sequencing and Surface Notes

- The foundation first; then PLAN-V2-116 to 119 in parallel (disjoint by document and by test, checked by `corpus cross-check` at each emit); PLAN-V2-120 after the two running plans that edit its tests (PLAN-V2-89, 98); PLAN-V2-122 last, after PLAN-V2-121 is decided.
- The family (WS-11) shares test classes with the move packages: `CredentialStoreMoveRecordParityTest` lies in the directory PLAN-V2-91 converts, the store parity tests in the directories PLAN-V2-85 and 86 already converted (landed). The transition goes first where the surfaces allow; the gate decides each emit.
- The queue order of the rows is the staging order (the row verb allocates the next `seq`); the emit order is decided at each `next`, not by `seq`.

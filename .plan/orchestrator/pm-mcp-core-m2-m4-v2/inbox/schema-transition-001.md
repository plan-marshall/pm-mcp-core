envelope_version=1
sender_type=orchestrator
sender_id=schema-transition
epic=pm-mcp-core-m2-m4-v2
kind=finding
created=2026-11-06T08:57:58Z
revision=1
amended=2026-11-06T08:58:46Z

# Schema transition: eight new work packages, highest priority, next task

Sender: the orchestrating session of the schema transition. Operator decision (2026-11-06): the transition is the **highest priority of this epic** and `schema-files-foundation` is the **next plan to emit**, ahead of PLAN-V2-84. Nothing in this epic was edited.

## Why

Ten parity tests, two tests that regex a number out of specification prose, two list tests and the helper `SpecificationDocument` read `.adoc` files at run time. That is what lets a specification-only pull request break `main` (ADR 009, and the include list of ADR 015). The JSON schemas become `*.schema.json` files in `src/main/resources` of the module that owns the codec; the specification includes them with `include::`. Tests read the file, never the document.

## The packages (doc/plans, pull request #304, branch `docs/schema-files-work-packages`)

Merge #304 first: the packages are not on `main` before that.

1. `schema-files-foundation`: the ADR (next free number), layout, include convention, the test helper `SchemaResource` in the test sources of pm-core (published through `pm-core:tests`, ADR 012), and the documents-here tasks (project setup, the skill `traced-implementation`, `trace.py`). **First; every package below depends on it.**
2. `schema-files-tested-records` (27 blocks, 8 parity tests switch to the file), `schema-files-machine-stores` (22 blocks, `AuditSchemaParityTest` and `CredentialStoreMoveRecordParityTest`), `schema-files-remaining-records` (25 blocks), `schema-files-other-specifications` (14 blocks in 8 documents). These four are disjoint from each other by document and can run in parallel once the foundation is on `main`.
3. `limits-in-data-files`: the stalled threshold (pm-runtime) and the unit frontmatter limits (pm-workflow) move into properties files that the code reads and the specification includes; the two regex tests become load tests.
4. `generated-listings`: **blocked** on the open question `generated-listings` (documentation repository, `doc/discussions/open-questions.adoc`, branch `docs/schema-files-open-questions`). Do not emit it.
5. `adoc-reading-retired`: closing package; deletes `SpecificationDocument` and `pm.specification.root`. Depends on all packages above; ADR 015 (the docs-only skip with `**/doc/**` and `**/src/**` as the include list) is on `main`, and this package removes the `**/doc/**` entry. Emit last.

The sibling work in `plan-marshall-mcp` (`tool-schemas-single-source`, the six MCP tool schemas; the spec and the `pm-mcp/tools/*.json` files differ today) is carried by another session and needs no plan of this epic. It depends on `schema-files-foundation` only for the rule.

## Asks

- Put `schema-files-foundation` first in the Ordered Queue and emit it next.
- Check the Expected Surface of the four move packages against PLAN-V2-84 (null-safety, all modules) and the family that depends on it: PLAN-V2-84 touches the test classes the move packages edit. Decide the order yourself; the operator wants the transition ahead of the family wherever the surfaces allow.
- Runtime validation of a stored record against its schema (a compile-scope validator, `networknt` is test-scope today) is **not** part of the transition; open question `runtime-schema-validation`. Do not plan it.
- Every package file lists tasks with spec anchors only; the done rule is the same as in every other package (the row stays `done`, no link, the file is deleted).

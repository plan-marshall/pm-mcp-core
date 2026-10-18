# PLAN-80: Corpus group and contamination on the overlay row and the imported item

epic: pm-mcp-core-m2-m4
workstream: WS-05

> Retroactive record of a package that was delivered OUTSIDE the orchestrator. It was registered by
> pm-mcp-core #81 (`f0cab7c`, `doc/plans/corpus-group-contamination.adoc`) and built by #86 (`e935da3`), whose
> commits removed the package file and its row. The queue row was staged on 2026-10-18 so the ledger and
> `main` agree; nothing here is to be emitted. See `landings/PLAN-80.md`.

## Objective

Let the overlay row and the imported item carry a `group` and a `contamination` note, written into the generated item, so that several items generated from one source row can name each other (task 4 of `question-corpus-public-datasets` in `plan-marshall-mcp`). Serves: Roadmap Milestone 4. Where: pm-core package `core.role.corpus`.

## Deliverables

Shipped as pm-mcp-core #86, all of them:

1. The members `group` and `contamination` on the overlay row and on the imported item, written into the generated item.
2. Record, codec and schema resource of the overlay and of the imported item changed together; the validator's rules follow.
3. ADR `core-role-corpus/007` (group and contamination written literally) and the developer documentation.

**Done when:** the package file and its row left the index in #86.

## Claim Labels

- OBSERVED: pm-mcp-core #86 merged as `e935da3` on 2026-10-18 (23 files: `core.role.corpus` main 6, test 7, resources 2, `runtime.corpus` tests 2, one `pm-provider-git` test, the ADR, `doc/plans`, `doc/Traceability.adoc`) — `git show --stat e935da3`
  - verdict: corroborated | checked_at: 683707ed6f25f5a2e14bed84841168513054a37f | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: git show --stat of the cited commits and ci pr view on 2026-10-18 confirm the merges, dates and file groups named

## Expected Surface

- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/corpus/` — overlay row, imported item, codecs, generator, validator
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/role/corpus/` — their tests
- OBSERVED: `pm-core/src/main/resources/de/planmarshall/core/role/corpus/` — `source-overlay.schema.json`, `imported-item.schema.json`
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/corpus/` — fixtures
- OBSERVED: `pm-core/doc/adr/core-role-corpus/007-group-and-contamination-written-literally.adoc` — ADR

## Dependencies and Sequencing

- Depends on: PLAN-79 (`corpus-overlay-extensions`), shipped
- Overlaps with: none live

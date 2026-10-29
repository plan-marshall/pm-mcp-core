# PLAN-79: Corpus overlay extensions — HTTPS source fetch, several items per row, answer schema

epic: pm-mcp-core-m2-m4
workstream: WS-05

> Retroactive record of a package that was delivered OUTSIDE the orchestrator. It was registered by
> pm-mcp-core #79 (`95a8718`, `doc/plans/corpus-overlay-extensions.adoc`), built by #80 (`238c823`) and its
> package file and row were closed by #94 (`ab308ad`). The queue row was staged on 2026-10-18 so the
> ledger and `main` agree; nothing here is to be emitted. See `landings/PLAN-79.md`.

## Objective

Make the import of `corpus-sources` work over the real rows of the three sources of the question corpus of `plan-marshall-mcp` (`question-corpus-public-datasets`): sources on Hugging Face fetched over HTTPS (the manifest member `fetch`), several items generated from one source row, and an answer schema in the overlay row. Serves: Roadmap Milestone 4. Where: pm-core package `core.role.corpus`, pm-runtime package `runtime.corpus`, and `pm-provider-git`.

## Deliverables

Shipped as pm-mcp-core #80, all of them:

1. The manifest gets the member `fetch` (`git` or `huggingface`); record, codec and schema resource change together, and every existing manifest states `git`.
2. A second implementation of `SourceRepository` in `runtime.corpus`, on cui-http, chosen per manifest by its `fetch` member.
3. Several items per overlay row (a row selected twice is no longer refused).
4. `input.answer_schema` on the overlay row.
5. Formats changed together, the validator's rules following, ADR `core-role-corpus/006` and repository ADR `006`.

**Done when:** the package is closed on `main` (`ab308ad`).

## Claim Labels

- OBSERVED: pm-mcp-core #80 merged as `238c823` on 2026-10-16 (33 files: `core.role.corpus` 21, `runtime.corpus` 4, two ADRs, `doc/LogMessages.adoc`, `doc/Traceability.adoc`) and #94 closed the package file as `ab308ad` on 2026-10-18 — `git show --stat` of both commits
  - verdict: corroborated | checked_at: 683707ed6f25f5a2e14bed84841168513054a37f | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: git show --stat of the cited commits and ci pr view on 2026-10-18 confirm the merges, dates and file groups named

## Expected Surface

- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/corpus/` — the manifest, overlay and item formats and their validators
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/role/corpus/` — their tests
- OBSERVED: `pm-core/src/main/resources/de/planmarshall/core/role/corpus/` — the schema resources
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/corpus/` — the second `SourceRepository`
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/corpus/` — its tests
- OBSERVED: `pm-core/doc/adr/core-role-corpus/006-source-fetch-kind-repeated-rows-and-answer-schema.adoc` — ADR
- OBSERVED: `doc/adr/006-corpus-fetch-split-across-pm-core-pm-provider-git-and-pm-runtime.adoc` — ADR

## Dependencies and Sequencing

- Depends on: nothing — shipped
- Overlaps with: none live

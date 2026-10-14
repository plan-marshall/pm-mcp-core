# Landing Analysis: PLAN-21 — Entity addressing of the bootstrap units

epic: pm-mcp-core-m2-m4
workstream: WS-03
pr: #54 (https://github.com/plan-marshall/pm-mcp-core/pull/54), merge commit b25d115

> Landing record for one shipped plan. Lives at `landings/PLAN-21.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source: inbox message `plan-21-entity-addressing-009.md` (landing-facts complete, no missing key),
corroborated on 2026-10-13 against the PR (CI on head 9b9f447, `/review` history), the file list of
b25d115, the index, `plan-marshall-documentation` #56, and the archived plan's decision and build
logs. Harness: Claude Code (default).

## Related pull requests (operator rule 2026-10-13)

| PR | Repository | State |
|----|------------|-------|
| #54 | pm-mcp-core | MERGED 2026-10-13T15:36:05Z (b25d115) |
| #56 | plan-marshall-documentation | MERGED 2026-10-14T06:51:30Z (42217c3) (PM-ARCH-4 / PM-WF-1 trace lines, the roadmap item, new watch item PM-WATCH-TOOL-22) |

**The landing is not complete under the operator's rule until doc #56 is merged.**

## Deliverable Fidelity vs Spec

24 files, +1,854; 6 deliverables, the 6th the documentation PR.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| Entity addressing of the workspace scope | shipped-modified | `workflow.engine`: workspace entity kinds addressed (ADR workflow-engine 004), a refused address returned as a value (007), the resolver beside the engine ports (008), entity id forms (009), no log records (010). Reworked in-PR after PLAN-12 landed `core.store.EntryId` (fix 52bc5c9): the entry-id grammar lives only in `core.store.EntryId` (005), and entry records are read through the `core.spi` port `PendingEntryReader` (006) — the port has no pm-runtime adapter yet |
| Default entity kind renamed | shipped-modified | `EntityKindRegistry` `review` → `lessons-review` (operator decision at outline; ADR workflow-verify 012), with a status note in ADR workflow-verify 005 |
| Index row | shipped-as-specified | `entity-addressing` `done` |
| Module documentation | shipped | `workflow-engine.adoc` and `workflow-verify.adoc` extended; 7 + 1 ADRs |
| Documentation PR | open | see above |

Surface: beyond the declared `workflow.engine`, the plan also touched `core.spi` (`PendingEntryReader`)
and `workflow.verify` (`EntityKindRegistry`, its test, ADR 012, and a note in ADR 005). PLAN-17, which
is running, also declares `workflow.verify`, so its PR may need a rebase over this. No log records;
the format-version advisory does not apply, since the plan touched no codec or journal.

## Metrics and Anomalies

- Tokens: 5,837,278; wall time 27,252 s.
- Gates: `verify -Ppre-commit` at 14:46:08Z, after the last commit 9b9f447 (14:45:47Z); CI green.
- Spec premise drift: the spec predates PLAN-12's `EntryId`, so the plan reworked itself in the PR.
  Lesson routed as `pm-mcp-core-m2-m4-234.md`. Orchestrator practice from now on: re-check a spec's
  premises against what landed since staging, immediately before emitting it.

## Routing and Merge Behavior

- `/review` re-posted three times; the only override was for the optional Sourcery bot. Merge queue,
  b25d115.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#54`; `landing` `landings/PLAN-21.md`
- [x] 8 lessons routed (`pm-mcp-core-m2-m4-234.md` to `-241.md`)
- [x] PLAN-12's documentation PR #59 now MERGED — PLAN-12's landing is complete

## Follow-Ups

- Merge `plan-marshall-documentation` #56 (operator).
- A pm-runtime adapter for `PendingEntryReader` over `PendingEntryStore` is owed; it belongs to the
  daemon wiring.

**Completed 2026-10-14:** doc #56 merged at 06:51:30Z (42217c3) after a roadmap conflict with main was resolved.

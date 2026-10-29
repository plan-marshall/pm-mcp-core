# Landing Analysis: PLAN-39 — Module READMEs, the ADR template, and ADRs for the decisions already shipped

epic: pm-mcp-core-m2-m4
workstream: WS-10
pr: #53 (https://github.com/plan-marshall/pm-mcp-core/pull/53), merge commit c0ecf33

> Landing record for one shipped plan. Lives at `landings/PLAN-39.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source: inbox message `plan-39-module-readmes-and-adrs-011.md` (landing-facts complete, no missing
key), corroborated on 2026-10-13 against:
- the PR (CI on head a44257b, `/review` history);
- the file list of c0ecf33;
- the archived plan's decision and build logs.

Harness: Claude Code (default).

## Related pull requests (operator rule 2026-10-13)

| PR | Repository | State |
|----|------------|-------|
| #53 | pm-mcp-core | MERGED (c0ecf33) |
| — | plan-marshall-documentation | none required (the spec: "No documentation PR") |

Complete.

## Deliverable Fidelity vs Spec

62 files, +2,373; 9 deliverables by the plan's count.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| The rule and the layout in `CLAUDE.md` and the index's "How to Work a Package" | shipped | `CLAUDE.md` changed; the index section changed (2 lines) |
| ADR template and index | shipped | `doc/adr/template.adoc` and the index |
| A README for every module | shipped | 9 `README.adoc` (pm-core, pm-workflow, pm-runtime, pm-providers and its five modules), linked from the root `README.md` |
| ADRs for the decisions already shipped | shipped | 44 ADR files, including the repository-wide 001 Java 25, 002 cui-http and the mock web server, 003 log identifiers per workstream block, 004 module documentation layout. No candidate dropped; "route responses single-use" is absent from 002 because no source states it |

Concurrent numbering (operator decision 2026-10-13) worked as designed. Two collisions with plans
that landed during finalize — #51 (core-store 007–009, runtime-start 001–002) and #54
(workflow-verify 012) — were resolved by renumbering only this plan's ADRs: core-store 010–023,
runtime-start 003–010, workflow-verify 013–016.

Format-version advisory: checked; no ADR records a bump.

## Metrics and Anomalies

- Tokens: 7,694,590; wall time 38,397 s — heavy for a documentation package. The review wait needed
  two operator loop-back grants.
- Gates: `verify -Ppre-commit` at 17:30:33Z, after the last commit a44257b (17:30:27Z); CI green.
- Gap: the pre-merge ADR gate (`manage-adr scan`) walks the root `doc/adr/` only, not the per-package
  ADR directories (lesson routed as `pm-mcp-core-m2-m4-242.md`).
- Leftover: `pm-workflow/doc/developer/workflow-verify.adoc` still cites "ADRs 001 to 012". Folded
  into PLAN-40.

## Routing and Merge Behavior

- `/review` posted 7 times, the last after the final commit. Two overrides:
  - the optional Sourcery bot, on its budget;
  - one CodeRabbit re-review timeout that advanced an intermediate head (5a390c3) unreviewed.
    CodeRabbit's review arrived afterwards and its findings were triaged before the merge.
- Merge queue, c0ecf33.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#53`; `landing` `landings/PLAN-39.md`
- [x] 10 lessons routed (`pm-mcp-core-m2-m4-242.md` to `-251.md`)
- [x] PLAN-40 spec folded (ADR references); the log-holder session plan amended to supersede ADR 003

## Follow-Ups

- ADR `doc/adr/003` (per-workstream log identifier blocks) is superseded by the log-holder split —
  noted in that session plan.

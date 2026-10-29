# Landing Analysis: PLAN-16 — Diagram and table generators

epic: pm-mcp-core-m2-m4
workstream: WS-03
pr: #49 (https://github.com/plan-marshall/pm-mcp-core/pull/49), merge commit f4b14f2

> Landing record for one shipped plan. Lives at `landings/PLAN-16.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source: inbox message `diagram-generator-011.md` (landing-facts complete, no missing key),
corroborated on 2026-10-13 against:
- the PR (CI on head 44e56a9, `/review` history);
- the file list of f4b14f2;
- the index;
- the archived plan's decision and build logs.

Harness: Claude Code (the operator's default).

## Deliverable Fidelity vs Spec

26 files, +2,828; 4 deliverables.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| Mermaid state diagrams, AsciiDoc state, link and outcome tables, and the ASCII table for terminal output, from a verified unit | shipped-as-specified | `workflow/diagram/` main and test, with golden files under `src/test/resources/diagram/`, byte-stable output (ADR 009) and one facade for three forms (ADR 001) |
| Drift tests over the default units in plan-marshall-mcp | shipped-as-specified (as context) | ADR 006 records that the drift tests live in plan-marshall-mcp |
| Index row | shipped-as-specified | `diagram-generator` `done` |
| Module documentation | shipped | `pm-workflow/doc/developer/workflow-diagram.adoc`, ADRs 001–010 |
| Documentation PR | in flight | `plan-marshall-documentation` #52 open. It overlaps that repository's open #34 (PLAN-10's documentation) on the static-verifier lines, so whichever merges second needs a small conflict resolution |

Surface: exactly the declared `workflow.diagram`, `src/test/resources/diagram/` (as narrowed on
2026-10-13), the module documentation and the index row. No log records, no POM change.

## Metrics and Anomalies

- Tokens: 4,051,611; wall time 10,876 s (about 3 hours) — fast for its size.
- Rebased onto #48 (the completed verifier); one post-rebase fixture fix in the diagram tests
  (3c28b58), and no production change outside the package. The stricter verifier touches its
  consumers again; this time the effect was small.
- Gates: `verify -Ppre-commit` at 09:27:56Z, after the last commit 44e56a9 (09:26:45Z); CI green.

## Routing and Merge Behavior

- Review: `/review` posted at 09:42Z after the last commit. CodeRabbit participated, and
  `cuioss-review-bot` participated empty. The only override was for the optional Sourcery bot
  (review budget).
- CI/merge: merge queue, f4b14f2.
- Collisions: none.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#49`; `landing` `landings/PLAN-16.md`
- [x] 10 lessons: 1 promoted here (2026-10-13-10-001, collision-free identifier sanitising), 9 routed (`pm-mcp-core-m2-m4-203.md` to `-211.md`)

## Follow-Ups

- `plan-marshall-documentation` #52 and #34 to merge in order, resolving the overlap (operator).
- Unblocked with PLAN-17: PLAN-24 `plan-instance`.

# Landing Analysis: PLAN-22 — Deterministic scenario replay harness

epic: pm-mcp-core-m2-m4
workstream: WS-06
pr: #50 (https://github.com/plan-marshall/pm-mcp-core/pull/50), merge commit 115d7ed

> Landing record for one shipped plan. Lives at `landings/PLAN-22.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source: inbox message `scenario-replay-harness-017.md` (landing-facts complete, no missing key),
corroborated on 2026-10-13 against:
- the PR (CI on head f56a7f6, `/review` history);
- the file list of 115d7ed;
- the index;
- the archived plan's decision and build logs.

Harness: Claude Code (default).

## Deliverable Fidelity vs Spec

17 files, +2,744; 7 deliverables, the 7th the documentation PR.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| Deterministic scenario replay harness | shipped-as-specified | `pm-workflow` test package `workflow.scenario`: replay twice and compare the status bytes (ADR 002); stores in a temporary directory without git (003); unsupported directives refused by name (004); scripts as text in the test (005); the package's own fixtures (006) |
| Index row | shipped-as-specified | `scenario-replay-harness` `done` |
| Module documentation | shipped | `pm-workflow/doc/developer/workflow-scenario.adoc`, 6 ADRs |
| Documentation PR | in flight | `plan-marshall-documentation` #53 open (PM-TEST-4 trace lines, the harness section, the roadmap item); follows #50 |

Surface: exactly the declared test package, module documentation and index row; no production
code, no POM change.

## Metrics and Anomalies

- Tokens: 3,819,472 (over the retrospective's 2.5 M line for a multi-module feature; one review-fix
  round). Wall time 11,692 s (about 3.2 hours).
- Gates: `verify -Ppre-commit` at 09:43:49Z, after the last commit f56a7f6 (09:42:51Z); CI green.
- A pre-commit rewrite widened a test to `throws Exception`, which Sonar flagged S1130. A recurrence,
  folded into lesson 2026-10-10-14-001.

## Routing and Merge Behavior

- `/review` after the last commit; both required bots participated. The only override was for the
  optional Sourcery bot (review budget).
- Merge queue, 115d7ed. No collisions.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#50`; `landing` `landings/PLAN-22.md`
- [x] 16 lessons: 1 promoted here (2026-10-13-11-001), 1 folded into 2026-10-10-14-001, 14 routed (`pm-mcp-core-m2-m4-212.md` to `-225.md`)

## Follow-Ups

- `plan-marshall-documentation` #53 to merge (operator).
- PLAN-35 `supervision-scenarios` builds on this harness and still waits on the supervisor chain.

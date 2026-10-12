# Landing Analysis: PLAN-10 — Static verifier and transition fuzzer

epic: pm-mcp-core-m2-m4
workstream: WS-03
pr: #32 (https://github.com/plan-marshall/pm-mcp-core/pull/32), merge commit c0d4b92

> Landing record for one shipped plan. Lives at `landings/PLAN-10.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source: inbox message `static-verifier-and-fuzzer-011.md` (landing-facts complete, no missing key),
corroborated on 2026-10-12 against the PR state (MERGED 05:19Z), the file list of c0d4b92, the
index and the new package file on `origin/main`, and the `/review` comments on #32.

## Deliverable Fidelity vs Spec

28 files, +2,382; the plan counts 7 deliverables, 1, 5, 6 and 7 partly documentation.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| Static verifier (`WorkflowValidator`, `VerificationRule`) and the transition fuzzer | shipped-modified | `workflow/verify/` main and test; fixtures `src/test/resources/verify/{passing,refused,fuzz}/`. Only part of the rules: the rest moved by operator decision into a new package |
| Index row | shipped-as-specified | `static-verifier-and-fuzzer` `done` |
| New package `static-verifier-remaining-rules` | added-unplanned | Operator override: `doc/plans/static-verifier-remaining-rules.adoc` and a second index row (`open`, depends on `static-verifier-and-fuzzer`), deliberately breaking "change only this package's own row". Staged as PLAN-41 |
| Documentation | in flight | `plan-marshall-documentation` #34 open, operator merge |

Surface: within the declared `workflow/verify/` (main, test) plus `src/test/resources/verify/`
(not declared) and the index with the new package file. No log records (WS-03 block untouched).

## Metrics and Anomalies

- Tokens: 4,856,474 recorded; wall time 48,781 s, of which about 2 h worked and the rest an
  overnight wait in the merge queue.
- Two finalize loop-backs: EXIT_PATH flagged terminal states; hard-coded guard-atom set and int
  run-counter wrap in `TransitionFuzzer` (lesson 2026-10-12-05-001).

## Routing and Merge Behavior

- Review: `/review` posted at 20:24Z and 20:57Z; no override.
- CI/merge: merge queue, c0d4b92; 0 new Sonar issues.
- Collisions: none.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#32`; `landing` `landings/PLAN-10.md`
- [x] PLAN-41 `static-verifier-remaining-rules` staged in WS-03
- [x] 10 lessons: 1 promoted here (2026-10-12-05-001), 9 routed to plan-marshall (`pm-mcp-core-m2-m4-115.md` to `-123.md`)

## Follow-Ups

- Whether `project-units-and-invariants` (PLAN-17) and `workflow-engine` (PLAN-13) depend on the new
  package: their index rows were not changed; asked of the operator.
- `plan-marshall-documentation` #34 to merge (operator).

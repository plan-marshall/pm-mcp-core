# Landing Analysis: PLAN-08 — Runtime lifecycle

epic: pm-mcp-core-m2-m4
workstream: WS-01
pr: #29 (https://github.com/plan-marshall/pm-mcp-core/pull/29), merge commit e163bf8

> Landing record for one shipped plan. Lives at `landings/PLAN-08.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source: inbox message `runtime-lifecycle-026.md` (landing-facts complete, no missing key),
corroborated on 2026-10-11 against the PR state (MERGED 20:00Z), the file list of e163bf8, the index
and `PmMcpLogMessages` on `origin/main`, and the plan's archived execution manifest.

## Deliverable Fidelity vs Spec

41 files, +6,408; the plan counts 11 deliverables, the 11th the documentation PR.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| Bounded verification, drain, recovery, sweep, version mismatch | shipped-as-specified | `runtime/start/` main and test, including `StartupColdStartIT` |
| Index row | shipped-as-specified | `runtime-lifecycle` `done` on `origin/main` |
| Documentation | in flight | `plan-marshall-documentation` #33 open, merges after #29 |

Surface: within the declared `runtime/start/`, plus `PmMcpLogMessages` (undeclared by the block
decision) and `runtime/lock/StaleLeaseSweep` with its test (outside the declaration; a sweep the
periodic step wraps). Breaking for the assembly: `StartupSequence` gains two record components and
`ProjectRecovery` refuses a partial step list; the new engines have no caller until the daemon
wiring in plan-marshall-mcp.

Log identifiers: the WS-01 WARN block 150-159 is now full; INFO 058-059 and ERROR 254-259 free.
The orchestrator allocated WARN 141-149 as WS-01 overflow and amended PLAN-09 and PLAN-23.

## Metrics and Anomalies

- Tokens: 5,331,541 recorded; wall time 52,118 s (about 14.5 hours).
- Two finalize fix rounds: review/Sonar triage (TASK-20 to 26) and a CI timing flake in
  `StartupColdStartIT` (TASK-27; lesson 2026-10-11-20-008).

## Routing and Merge Behavior

- Review: manifest `re_review_on_loopback: true`; `/review` posted at 19:34Z. No override.
- CI/merge: merge queue, e163bf8.
- Collisions: none.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#29`; `landing` `landings/PLAN-08.md`
- [x] 25 lessons: 7 promoted here (2026-10-11-20-002 to -008), 18 routed to plan-marshall
- [x] WS-01 WARN overflow 141-149 allocated; PLAN-09 and PLAN-23 amended

## Follow-Ups

- `plan-marshall-documentation` #33 to merge (operator).
- plan-marshall-mcp: adapt `pm-mcp-server` to `StartupSequence` / `ProjectRecovery`; wire the
  engines; PM-TECH-3 native cold-start figure after that.
- Unblocked by this landing: PLAN-20 `job-runner` still waits on PLAN-18.

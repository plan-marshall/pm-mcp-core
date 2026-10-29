# Landing Analysis: PLAN-12 — Scope session log and session generations

epic: pm-mcp-core-m2-m4
workstream: WS-02
pr: #51 (https://github.com/plan-marshall/pm-mcp-core/pull/51), merge commit d7b622d

> Landing record for one shipped plan. Lives at `landings/PLAN-12.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source: inbox message `session-log-and-generations-012.md` (landing-facts complete, no missing key),
corroborated on 2026-10-13 against:
- the PR (CI on head 02bb391, `/review` history);
- the file list of d7b622d;
- the index and `PmMcpLogMessages`;
- `plan-marshall-documentation` #59;
- the archived plan's decision and build logs.

Harness: Claude Code (default).

## Related pull requests (operator rule 2026-10-13)

| PR | Repository | State | Matches the code? |
|----|------------|-------|-------------------|
| #51 | pm-mcp-core | MERGED 2026-10-13T13:11:42Z (d7b622d) | — |
| #59 | plan-marshall-documentation | **OPEN** (mergeable state unknown) | Probably: its single commit 7e7c253 (13:04Z) is later than the last code fix 02bb391 (12:34Z); content not compared line by line |
| sweep registration | plan-marshall-mcp | **owed, not opened** | `pm-mcp-server` still calls `SweepStep.notImplemented`, which this plan removed (ADR runtime-start/002). The assembly breaks against the new SNAPSHOT until it registers `PendingEntryRecordsSweep` and `FencedSessionGenerationsSweep` |

**The landing is not complete under the operator's rule until #59 is merged.**

## Deliverable Fidelity vs Spec

62 files, +8,074; 7 deliverables, the 7th the documentation PR.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| Scope session log and session generations | shipped-modified | `core.store` and `core.queue` main and test, including the journal delete mode (ADR core-store/007), pending entry records (008, 009) and generation records (core-queue 001–003) |
| Sweeps | shipped-modified | `runtime.start`: `PendingEntryRecordsSweep`, `FencedSessionGenerationsSweep`, `PeriodicSweep`; the `SweepStep` factory seam removed by operator decision (ADR runtime-start/002) |
| Index row | shipped-as-specified | `session-log-and-generations` `done` |
| Module documentation | shipped | developer guides `core-store` (extended), `core-queue`, `runtime-start`; 9 ADRs |
| Documentation PR | open | see above |

Surface: beyond the declared `core.store` and `core.queue`, it also wrote `runtime.start` and
`core.service` (`AuditEvent`). No running plan declares them. Log identifiers: WS-02 INFO 065, 066.

## Metrics and Anomalies

- Tokens: 5,535,084; wall time 22,724 s. Above the retrospective's multi-module limits, after two
  review loop-backs.
- Gates: `verify -Ppre-commit` at 12:35:29Z, after the last commit 02bb391 (12:34:56Z); CI green.
- Open review concern (CodeRabbit walkthrough): the pending-entry removal events of one sweep run
  share one `transaction_id`, so a reader deduplicating by it could drop events. This touches
  PLAN-09 `audit-trail` and PLAN-11's follow-up (audit-sink deduplication).

## Routing and Merge Behavior

- `/review` after both fix rounds; no override. Merge queue, d7b622d. No collisions.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#51`; `landing` `landings/PLAN-12.md`
- [x] 11 lessons: 3 promoted here (2026-10-13-13-001 to -003), 8 routed (`pm-mcp-core-m2-m4-226.md` to `-233.md`)
- [x] Watches: doc #59 open; plan-marshall-mcp sweep registration owed; the shared `transaction_id` concern noted in PLAN-09's spec

## Follow-Ups

- Merge `plan-marshall-documentation` #59 (operator).
- plan-marshall-mcp: register the two sweeps in `pm-mcp-server`.

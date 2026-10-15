# Landing Analysis: PLAN-09 — Anchored audit trail

epic: pm-mcp-core-m2-m4
workstream: WS-01
pr: #69 (https://github.com/plan-marshall/pm-mcp-core/pull/69), merge commit 3236ed5

> Landing record for one shipped plan. Lives at `landings/PLAN-09.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source: inbox message `plan-09-audit-trail-032.md` (landing-facts complete, no missing key), drained on
2026-10-15. The orchestrator verified the following:

- #69 is `merged` (squash through the merge queue) and its merge commit 3236ed5 is on `origin/main`.
- Documentation PR `plan-marshall-documentation` #74 is `merged` (78c0d83). The landing message says the run
  left it unmerged; the operator merged it afterwards.
- `doc/LogMessages.adoc` on documentation `main` carries PM_RUNTIME-14, -25, -108, -123, -124 and -125 with
  the same templates as `RuntimeLogMessages` on `origin/main`, and PM_CORE-100 is present. The risk that
  lesson 031 names (rows handed back by five fix tasks with no owner) did not materialise.
- The index row `audit-trail` is `done`.
- The diff 3236ed5^..3236ed5 has 170 files, +8801/−202. Of these, 118 are the one-line import-block rewrite of
  the pre-commit profile (commit fa58482, lesson 012). The new package `runtime.audit` is present: `AuditHead`,
  `AuditIndex`, `AuditRotation`, `AuditVerification` and five test classes. In `core.store`: `AuditRecord`,
  `AuditHeadRecord`, `AuditIndexEntry`, `AuditCodec`, `AuditLog`, `AuditAnchor` and `AuditLogSink`. ADRs
  core-store/024–027, runtime-audit/001–004 and runtime-start/011 are present.

The orchestrator did not re-run the build or the verification fixtures. The deliverable verdicts below rest on
the plan's functional review (3 met, 3 met with residuals, 0 not met), checked against the diff.

Harness: Claude Code (operator rule since 2026-10-13).

## Deliverable Fidelity vs Spec

The spec's three deliverables were refined into six in the plan.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Append-only audit log as a hash chain with its HMAC head | shipped-as-specified | D1 (formats, chain, production sink `AuditLogSink` in `core.store`) and D4 (`AuditHead`, HMAC-SHA-256 under the enrolment integrity key, read-only key access); ADRs core-store/024, 026, runtime-audit/003 |
| 2. Rotation with the audit index, incremental verification at start | shipped-as-specified | D4 (`AuditIndex`, `AuditRotation` with caller-supplied limits, defaults 64 MiB / 30 days; `AuditVerification.verify()`), D3 (`AuditChainVerification.Outcome.Broken` carries reason and first broken line); ADRs runtime-audit/002, 004, runtime-start/011 |
| 3. The verification `pm-operator audit verify` requests | shipped-as-specified (library only) | `AuditVerification.full()`; the command itself is assembly work in plan-marshall-mcp (#74 names it as open) |
| Carried: production `AuditSink` and `AuditChainVerification` | shipped | `AuditLogSink` and `AuditVerification`; the wiring in plan-marshall-mcp is owed (watch) |
| Carried: POSIX OS lock dropped on channel close | shipped-modified | ADR core-store/024 accepts the lost OS lock for the audit log (the per-key in-process lock and `RuntimeLock` serialize appends); neither the lock manager nor `JsonlStoreFile` changed. This deviates from the specification's "append only under the log's `FileChannel.lock()`"; the deviation is recorded in the ADR |
| Added: one `transaction_id` per owed audit record | shipped-as-specified | D2 (`ScopeTransaction.recordId()`, `OrderedWrite`, `ScopeSessionLog`; PM_CORE-100 reworded). This settles the deduplication-key question carried from PLAN-11/PLAN-12 |
| Added: traced documents | shipped (operator merged #74) | 7 documentation files changed in 78c0d83 |

**Done when** (fixtures with a removed, a reordered, an edited record and a replaced head; a rotation during
verification): claimed by the functional review as met; the test classes `AuditVerificationTest` and
`AuditRotationTest` are present. The orchestrator did not run them.

**Known open gaps** (ADR runtime-audit/003, by the plan's own statement): a deleted head reads as absent; index
entries carry no HMAC; a replayed same-state head verifies; a head rolled back together with its files verifies;
a cut log grows without rotating.

**Surface delta** (landing-check, base origin/main, not stale): 9 declared, 170 realized, 91 added, 0 missing.
69 of the 91 are the import rewrite. The other 22 are the new `runtime.audit` package with its 4 ADRs, developer
guide and tests, `core.service` `AuditEvent`/`AuditSink` (with `AuditEventTest`), the log holders `CoreLogMessages`
and `RuntimeLogMessages`, `pm-runtime/README.adoc` and `doc/adr/README.adoc`.

## Metrics and Anomalies

- Tokens: 10,638,201 (landing-facts). The plan itself says this is overstated by about 407,786, because the
  retrospective's self-reported figure was added before the harness usage arrived. Read it with the epic's
  token-overstatement watch in mind.
- Duration: 63,959 s (about 17.8 hours).
- Anomalies:
  - Five code-review rounds. Six crash-window and fail-open defects in the rotation and head protocol arrived one
    per round (lessons 018–022, 024). Two loop-backs were granted by the operator (decision log cd6149, bcb8ac).
  - The pre-commit profile rewrote 118 untouched files; the operator kept the rewrite in this PR (lesson 012).
  - Finalize recorded no dispatch boundaries; two execute boundary rows record zero usage (lesson 030).
  - The `archive-plan` step was still `pending` at emit time; the plan is now archived
    (`archived-plans/2026-10-15-plan-09-audit-trail`).

## Routing and Merge Behavior

- Review: code review closed with 0 blocking and 1 advisory finding. The advisories that shipped unfixed:
  - `runtime-audit.adoc` understates the cost of `verify()`, which also reads the whole audit index.
  - `audit_chain_broken` cannot name the file of `first_broken_line` without a specification change.
  - Sourcery declined twice on its review budget (lesson 001).
- CI/merge: merged through the merge queue as 3236ed5. No collision with the running PLAN-24 was reported; PLAN-24
  (#67, 2ebec2a) merged after it and touches `core.store` only through `ScopeTransaction.create`.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-09 --status shipped`
- [x] row `pr` stamped — `#69`
- [x] row `landing` stamped — `landings/PLAN-09.md`
- [x] row `plan_marshall_plan_id` stamped — `plan-09-audit-trail`
- [x] epic.md narrative reconciled: the "no production `AuditSink`" watch is retired to `settled.md`; the
  plan-marshall-mcp wiring is added to the "still owes" watch; the POSIX OS-lock watch is narrowed to the stores
  other than the audit log; the queue annotation for PLAN-09 is updated
- [x] resume anchor updated
- [x] `queue-view.md` regenerated and committed with the row change

## Follow-Ups

- 31 candidate lessons (inbox 001–031):
  - 16 concern plan-marshall components and are routed to the plan-marshall `lessons-routing` inbox as 15
    messages, `pm-mcp-core-m2-m4-271` to `-285`. One pair is merged (005 + 010); 10 of the 15 repeat earlier
    routed messages and name them.
  - 15 concern this repository's code and go to the local lessons corpus. There are four new lessons:
    `2026-10-15-06-001` (004, locale-dependent signed content), `-002` (006, lock order of a callback), `-003`
    (008, chain predecessor after rotation) and `-004` (009, a changed `LogRecord` needs its documentation row).
    The rest are folded as recurrences: 007 into `2026-10-11-07-008`; 014–017 into `2026-10-11-20-001`; 018,
    022 and 024 into `2026-10-11-07-006`; 019–021 into `2026-10-12-13-001`.
- plan-marshall-mcp: wire `AuditLogSink` and `AuditVerification` in place of `AuditChainVerification.NONE` and
  the refusing sink; create `logs/`; break the `FileLockManager` ↔ `AuditLogSink` constructor cycle (watch).

# Landing Analysis: PLAN-11 — Enrolment store, lifecycle gate and integrity

epic: pm-mcp-core-m2-m4
workstream: WS-02
pr: #30 (https://github.com/plan-marshall/pm-mcp-core/pull/30), merge commit 5010969

> Landing record for one shipped plan. Lives at `landings/PLAN-11.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source: inbox message `plan-11-enrolment-store-018.md` (landing-facts complete, no missing key),
corroborated on 2026-10-12. Evidence:
- the PR (CI on the final head, `/review` history);
- the file list of 5010969;
- the index and `PmMcpLogMessages` on `origin/main`;
- `plan-marshall-documentation` #30;
- the archived plan's decision log and metrics.

Harness: **Claude Code**, inferred because no harness stamp exists. Dispatch tokens are measured
(11,625,971 recorded); the single `antigravity_runtime --help` call (13:02Z) follows the mid-run
executor regeneration, not plan work.

## Deliverable Fidelity vs Spec

52 files, +12,586; the plan counts 8 deliverables, the 8th the documentation PR.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| Enrolment store, lifecycle gate, integrity | shipped-modified | `core.store` (`EnrolmentStore`, `ProjectEnrolment`, `LifecycleGate`, codec), a **new package `runtime.enrolment`** (`Enrolments`, `EnrolmentCheck`, `EnrolmentHmac`, `EnrolmentKey`, `EnrolmentPolicy`, `EnrolmentSnapshot`, `EnrolmentFileCheck`) — the spec's guessed home `runtime.security` was not used. Recovery wiring in `runtime.start` (`ProjectRecovery`, `EnrolmentIntegrityStep`, `OwedEnrolmentAuditStep`, `JournalRollForwardStep`, `LeaseRecoveryStep`, `CrossScopeReconciliationStep`, `EnrolledProjects`) by operator decision (round 6: keep the pm-mcp-server assembly intact). `core.service` `LiveWork` and `AuditEvent` changes |
| Index row | shipped-as-specified | `enrolment-store` `done` on `origin/main` |
| Documentation | shipped | `plan-marshall-documentation` #30 MERGED |

Surface: expansion. Beyond the declared `core.store` and `runtime.security` (untouched), the plan wrote:
- the new `runtime.enrolment`;
- `runtime.start` (recovery steps);
- `core.service` (`LiveWork`, `AuditEvent`);
- `PmMcpLogMessages`, which is undeclared by design.

`runtime.start` is declared by staged PLAN-09 and PLAN-23, so the collision is with future work only,
never with a running plan.

Log identifiers: WS-02 INFO 060–064, WARN 161–167, ERROR 260. WARN 168–169 are left in the WS-02 block.

## Metrics and Anomalies

- Tokens: 11,625,971 recorded; wall time 174,186 s (about 48 hours). The longest and most
  expensive plan of the epic so far.
- Review: nine finalize rounds. Each wider fix round drew new findings (lesson routed as
  `pm-mcp-core-m2-m4-134.md`).
- One Sonar new-code issue accepted, not fixed (S9391, a `deleteAll` loop).
- An operator decision renamed `ProjectEnrolment.Provider` constants GITHUB/GITLAB to GH/GLAB, to
  pass PLAN-36's `ProviderNeutralityArchTest`. A cross-plan guard working as intended.
- The plan-marshall plugin cache advanced mid-run, and both executors were regenerated to 0.1.1905.
  This closes the cleanup's registry-parity finding.

## Routing and Merge Behavior

- Review: `re_review_on_loopback: true` worked. `/review` was posted 10 times, after every fix
  round; no override.
- CI: every check on the final head passed.
- Merge queue: 5010969 after about 270 s.
- Foreign-PR gate: `foreign_landing_indeterminate` because the doc branch is `feature/enrolment-store`,
  not `feature/plan-11-enrolment-store`. The known gate defect (routed earlier); the doc PR is in
  fact merged.
- Collisions: the #29/#31 `PmMcpLogMessages` conflict (advised 2026-10-12) resolved by rebase.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#30`; `landing` `landings/PLAN-11.md`
- [x] 17 lessons: 6 promoted here (2026-10-12-13-001 to -006), 1 folded as a recurrence into
      2026-10-11-20-001 (Javadoc promising behaviour the code lacks), 10 routed to plan-marshall
      (`pm-mcp-core-m2-m4-134.md` to `-143.md`)
- [x] Watch on #30's conflict retired

## Follow-Ups

Seven follow-ups listed in the PR body and the developer documentation's Known Limits need
components outside this slice:
- caller authorization;
- the live-work lock;
- the snapshot window;
- recovery wiring at startup through `ProjectRecovery.withEnrolment`;
- open points of the execution policy;
- ownership of the `state/` and `enrolments/` directories;
- audit-sink deduplication by `transaction_id`.

Their owners are not yet determined.

Unblocked: PLAN-15 `project-machine-store`, PLAN-18 `verify-not-resolve`, PLAN-39, PLAN-40, and
one of PLAN-09/12/14 at a time.

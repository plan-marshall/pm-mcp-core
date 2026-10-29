# Landing Analysis: PLAN-01 — Lock manager and leases

epic: pm-mcp-core-m2-m4
workstream: WS-01
pr: #20 (https://github.com/plan-marshall/pm-mcp-core/pull/20), merge commit c2b608b

> Landing record for one shipped plan. Lives at `landings/PLAN-01.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source: inbox message `lock-manager-and-leases-020.md` (landing-facts complete, no missing key),
corroborated on 2026-10-10 against the PR state (MERGED 11:49Z), the file list of c2b608b, the
index on `origin/main` and `PmMcpLogMessages` on `origin/main`.

## Deliverable Fidelity vs Spec

Verdicts rest on the file list of the merge commit and the PR state. Test content was not read;
"the acceptance holds" is the plan's claim plus green CI. The plan counts 8 deliverables (its
outline's split); the spec has 4.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| One lock manager, one in-process lock per key, at most one OS lock per lock file, total lock order asserted, out-of-order refused as `internal_fault` with an audit record | shipped-as-specified | `core/service/LockManager`, `LockTransaction`, `AuditEvent`, `AuditSink`, `InternalFaultException`; `core/store/LockKey`, `LockLevel`, `LockOrder`; `runtime/lock/FileLockManager`; `LockOrderTest`, `LockContractTest`, `FileLockManagerTest`, `FileLockManagerTwoProcessIT` |
| Sibling lock files for stores replaced by atomic rename; lock paths outside the protected directories | shipped-modified | `core/store/AtomicStoreFile` with `AtomicStoreFileTest`. Beyond the specification by operator decision: the parent directory is forced after the atomic move |
| Lease records under a short transaction lock; the stale lease sweep | shipped-modified | `LeaseStore`, `LeaseRecord`, `LeaseCodec`, `LeaseOwner`, `HolderInstance`, `runtime/lock/StaleLeaseSweep`, `HolderLiveness` with tests. Beyond the specification by operator decision: owner-checked `release(key, expectedOwner)` |
| Lock-free snapshot reads | shipped-as-specified | `core/store/StoreSnapshot`, `StoreIntegrity`, `StoreSnapshotTest` |
| Index row | shipped-as-specified | `lock-manager-and-leases` reads `done` on `origin/main` |
| Documentation (LogMessages entries, amendment of Format & Atomic Write Discipline) | not done | Plan's own residue: not started in `plan-marshall-documentation`; no open pull request there names it |

Surface: `inbox landing-check` returned `unmeasured` (the message supplied neither declared nor
realized paths). Read from c2b608b instead: all 37 paths sit in the declared package directories,
plus `doc/plans/README.adoc` (declared) and `PmMcpLogMessages` (undeclared by the block decision).
The ~20 out-of-package files the open PR showed earlier (`runtime.credentials`, `runtime.lsp`,
`runtime.web`, `runtime.start`, `runtime.ingest`) are not in the merge commit.

Log identifiers on `main`: INFO 050, WARN 151 (moved from 150, which #19 took), ERROR 250.

## Metrics and Anomalies

- Tokens: 6,498,792 recorded (not re-derived).
- Duration: 56,857 s wall time (about 15.8 hours).
- Anomalies: baseline drift twice during finalize (#18 and #19 merged), one content conflict in
  `PmMcpLogMessages`, resolved by rebase and force-push; `verify -Ppre-commit` rewrote 27 untracked-
  by-the-plan `pm-runtime` files and is not idempotent (lesson 2026-10-10-14-001); the pre-push gate
  resolved no bundle (recurrence on lesson 2026-10-10-07-016); `scope_creep_check` failed on all
  three runs (lesson 2026-10-10-14-011); `archive-plan` reads `pending` because it runs after the
  landing message.

## Routing and Merge Behavior

- Review: CodeRabbit and Sourcery reviewed; four review findings became fixes inside the PR
  (parent-directory force, owner-checked release, reference-counted eviction against the triage
  recommendation, the `reviseAll` key check). `cuioss-review-bot` was moved to optional for this
  plan, the third plan in a row.
- CI/merge: merged through the merge queue as squash commit c2b608b; Sonar 0 new-code issues.
- Collisions: the WARN 150 clash with #19 (recorded at the PLAN-03 landing) was resolved by
  moving to WARN 151, as the advisory asked.

## Reconciliation Actions

- [x] row `status` → `shipped`
- [x] row `pr` stamped — `#20`
- [x] row `landing` stamped — `landings/PLAN-01.md`
- [x] row `plan_marshall_plan_id` stamped — `lock-manager-and-leases`
- [x] epic.md narrative reconciled (two Watches retired, PLAN-04 hold lifted, new Watches)
- [x] inbox drained: 15 lessons promoted (2026-10-10-14-001 to -015), 4 folded
- [x] resume anchor updated, `queue-view.md` regenerated

## Follow-Ups

- Documentation owed in `plan-marshall-documentation`: `doc/LogMessages.adoc` entries INFO 050
  `STALE_LEASE_SWEEP_FINISHED`, WARN 151 `LEASE_ORPHANED`, ERROR 250 `LOCK_ORDER_VIOLATION`, and the
  amendment for the parent-directory force. No plan owns it yet.
- The `plan-marshall-mcp` package that wires `FileLockManager` must create exactly one instance per
  runtime; a second instance on the same lock file can release the first holder's OS lock on POSIX.
  Stated only in the class Javadoc.
- The `pm-mcp-parent` pre-commit recipes are not stable between runs — a parent POM follow-up.
- PLAN-04 `service-unit` is no longer held: #20 merged without touching `runtime.start`.

# Landing Analysis: PLAN-V2-124 — the leaf lock stated once, and its OS lock shown from a second process

epic: pm-mcp-core-m2-m4-v2
workstream: WS-05
pr: #380 (merge `54b81c98`) and the follow-up #385 (merge `a10f34f2`), both `merged` per the CI abstraction

> Landing record for one shipped plan, written by the `analyze` inbox drain of 2026-11-12 from the message
> `rb-v2-124-lock-manager-spec-and-os-lock-tests-001.md` of the runbook lane (harness opencode, model
> deepseek-v4.1-flash/high, executor 0.1.2106). The message is a lead. The three layers were read by one read-only leaf
> from git at `a10f34f2` (no build, no test run); the orchestrator re-read the points marked "re-read".

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 the leaf-lock rule stated once, five pointers | shipped | the statement at `store-schemas/13-leases-integrity-testing.adoc:119` is true against the code: `FileLockManager.osLockFile` (`:129-134`) returns `<file>.lock` for the leaf level only, the sidecar is created mode 0600 (`:345-346`), `AuditRotation:425` renames the log by `ATOMIC_MOVE` under the audit lock and never the sidecar, `ActivityJournal.rewrite` writes a temp file and renames (`:393-405`); pointers at `12:347` (re-read), `04:26`, `credentials-and-enrolment/03:17`, `16:48`, `filesystem-layout/02:131-138`; the search report is met (no "locked directly" outside the correct `runtime-lock.adoc:106`); the diff of 04, 12, 13, 16 adds or removes no `include::` line (R10); the `trace.py` result is the lane's claim |
| D2 wording, stale reference | shipped | `12:256`, `:271`, `13:132` name the sidecars; `model-work/08:355,357` unchanged |
| D3 OS lock from a second process, per kind | shipped | five ITs (audit append, mailbox, repair, read, journal) with `LockHolderProcess` modes; handshake is sound: the child prints `HELD` only after the store operation (`LockHolderProcess:131-135`), `startHolder` asserts it with a 30 s bound (IT:134-137), exit code asserted 0 after a bounded `waitFor`, both `Mode` switches exhaustive; the failing runs (5 of 7, 6 of 26) are the lane's claims |
| D4 unit lane says what it proves, POSIX marks | shipped | `@EnabledOnOs({LINUX, MAC})` on `LockFiles.modes`, `leafLock` and the five ITs; `PosixFilePermissions` only in `FileLockManagerTest:47,173` whose users are exactly those tests; bounded join plus `isAlive` assertion |
| D5 sidecar life cycle stated and pinned | shipped, **the pin is not new** | the one-runtime rule is in `ActivityJournal.delete` Javadoc, `core-queue.adoc`, `03-project-store.adoc:56,150`, `core-store.adoc:1793-1795`; `StoreLockGuard:44` now says "without the lock of '%s'". **`ActivityJournalTest.deleteRemovesTheSiblingLock` already existed since #328** (`7c87ef4f`, re-read by `git log -S`); the plan net-added only the `journalStillThere`/`lockStillThere` locals and the re-append assertion (`:123-130`), and the pm-core lane's lock manager takes no OS lock, so the "mutation-after" failing run proves nothing about the OS behaviour (the landing admits that limit). |
| Not in the declared surface | explained | `pm-core/doc/developer/core-store.adoc` (named by R06 and D5b); `runtime-lock.adoc` is listed for the life cycle but its diff touches only the test table (the life cycle lives in `core-store.adoc` and `core-queue.adoc`) |

## Three-Layer Analysis

### Layer 1 — Specified

All five deliverables are met. The spec's own D1a sentence ("never replaced by rename") was false, which the lane found and corrected; the specification should say so if it is re-emitted.

### Layer 2 — Implemented

About 32 new or changed assertion messages, none outside 20-60 characters (one is exactly 60, IT:237). `Protocol` has two components (no `@Builder` needed), `Mode` is an enum, `Optional` is a return type only, no new fake, no AAA comments. Minor: five ITs repeat one four-probe block (IT:226-229, 257-260, 288-291, 320-323, 353-356), arguably `@ParameterizedTest`; the literal `"qt-"` at IT:347 has no contract note; `ActivityJournal.delete` (`:286`) writes `RuntimeLock` in markdown backticks where `{@code}` is the Javadoc form; `LockHolderProcess.Mode.of` turns an unknown argument silently into PLAN mode (`:109-117`, documented); `core-store.adoc:1793-1795` has one unwrapped long line; no ADR was written for the D5b and D5c decisions (the spec said none expected).

### Layer 3 — Correct

No major finding. The new spec sentences are true against the code (sidecar names `audit.jsonl.lock` and `mailbox.jsonl.lock`, `RuntimeLock` exists and is taken at `StartupSequence:127`). Minor: **C1** the D5a claim is overstated (above). C2 (the leaf's reading that `12:347` no longer says the OS lock excludes a second process) is **not confirmed on re-read**: the sentence says the OS lock is held on the sidecar for the whole transaction, the in-process lock serialises the one runtime and the runtime lock excludes a second runtime; it is consistent with ADR 068. C3 the `03-project-store` tree (`:43`, `:56`) lists the mailbox sidecar but not `tasks/activity/<id>.jsonl.lock`, and `:150` speaks of the audit log's sidecar inside a per-project file though the audit log is machine-scoped. C4 the `READ` mode and the test name say "last-line read", while the operation is `JsonlStoreFile.read(transaction)`, a whole-store snapshot (`JsonlStoreFile:327-330`); whether that is "the" read of the spec is not shown.

`ActivityJournal.delete` with a real `FileLockManager` (read): inside one runtime a concurrent appender waits on the key's in-process lock first and opens a fresh sidecar after the delete, so there are not two holders; across processes a second runtime could lock a different inode, which is only a documented precondition excluded by `RuntimeLock` and exercised by no test. No caller of `ActivityJournal.delete` exists under `src/main` here (only tests); whether the `plan-marshall-mcp` assembly calls it is not shown. The `pm-core/src/main` diff is Javadoc plus the one `StoreLockGuard` string; `FileLockManager` is untouched; no test or doc in this repository pins the old message.

## Conformance to the Project's Own Rules

See Layer 2. Landing facts: `unresolved_review_threads=0`, `review_body_findings_total=4`, `handled=4` (two fixed, two declined with stated reasons), `self-review: clear` (after the follow-up PR #385; round 2 ran after the merge, as the landing states).

## Unverified leads

- The mutation runs (5 of 7 IT failures, 6 of 26 unit failures, 1 for the delete test), the `trace.py` result, Sonar (0 open, gate OK).
- Whether the assembly deletes a journal (`ActivityJournal.delete`) while a second process may hold its key.
- The sibling documentation checkout was not searched for the old `StoreLockGuard` message.

## Metrics and Anomalies

`complete: false` for `total_tokens` and `plugin_version` `unknown` (the Open Defect on runbook-lane landings in `epic.md`). The lane's `resolve` reports `sonar: not_configured` for a project whose pull requests SonarCloud analyses; the lane ran the spec's own read. Three whole-reactor runs hit the daemon's 1120 s bound and were split.

## Routing and Merge Behavior

PR #380 squash through the merge queue (`54b81c98`), then the follow-up #385 (`a10f34f2`). No `candidate-lesson`; nothing routed to `lessons-routing`. **Stale reference reconciled:** `16-task-queue.adoc:174` does not exist (96 lines); it survives in `settled.md:111` (the Open Defect text, with the correction at `:113`), the archived origin message `rb-v2-95-lock-manager-channel-close-001.md:79`, and this plan's spec and landing; `settled.md:113` already records the correction, so no ledger edit is needed.

## Reconciliation Actions

Row PLAN-V2-124 `running` -> `shipped`, `pr` `#380`, `landing` `landings/PLAN-V2-124.md`, `plan_marshall_plan_id` `rb-v2-124-lock-manager-spec-and-os-lock-tests`; message archived; Watch entry in `epic.md`; decision log.

## Follow-Ups

Leftovers placed by the operator's rule of 2026-11-12 (see the Watch entry and decision log): the repeated probe block and the `"qt-"` literal, the `Mode.of` fallback and the Javadoc form to the test-conformance owner; the missing tree entry, the machine-scope wording and the "last-line read" naming to the doc owner.

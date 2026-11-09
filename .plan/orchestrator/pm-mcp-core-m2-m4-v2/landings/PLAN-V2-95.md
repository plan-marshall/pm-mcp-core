# Landing Analysis: PLAN-V2-95 — The lock manager keeps a leaf key's OS lock across JSONL appends

epic: pm-mcp-core-m2-m4-v2
workstream: WS-05
pr: #328 (squash merge commit `7c87ef4f`, single parent `dffefaa3`; 17 files, +506/-78; runbook lane, harness opencode, `deepseek-v4.1-flash/high`)

> Landing record for one shipped plan. Lives at `landings/PLAN-V2-95.md`. A pasted claim is a lead, never a fact.

Source: the plan's landing message `rb-v2-95-lock-manager-channel-close-001.md` (2026-11-09T01:43Z). `landing-check` `complete: false`: `total_tokens` and `plugin_version` are `unknown` (folded into the standing Open Defect on the runbook lane); `surface_delta` `unmeasured`. Reconciled in full. The landing reports `spec_source = ledger_worktree` and `spec_differ = true` (the main-checkout copy differed throughout; expected), executor skew `match`.

Corroborated by the orchestrator: `ci pr queue-state 328` reads `merged`, merge commit `7c87ef4fa753…`, merge group run completed `success`; `ci checks status 328` reads 20 checks, overall success (`build (25)` 701 s, `build (26)` 585 s, `sonar-build` 769 s, Integration Tests and SonarCloud pass, CodeRabbit `SUCCESS`, Sourcery `SUCCESS`). Sonar over plain HTTP (`pullRequest=328`): 0 open issues, gate `OK`. The squash touches six production files, all in `pm-core` or `pm-runtime`: `FileLockManager` (the mechanism), `ActivityJournal` (its three hand-built keys), and Javadoc-only changes in `AuditLog`, `JsonlStoreFile`, `LockKey`, `LockLevel` (re-read with `git show --stat`).

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 decide and record the fix (ADR) | shipped-as-specified | ADR `core-store/068-leaf-key-os-lock-on-a-sidecar.adoc` exists (re-read at `7c87ef4f`); it chooses candidate (a), the sidecar, and names the rejected (b) and (c). It does not discuss an OFD lock or a lock on the directory as variants of (a) (leaf). |
| D2 implement it, test first | shipped-modified | `FileLockManager.osLockFile` (`:129-135`, re-read) returns `<lockFile>.lock` for a `LEAF` key and the lock file itself otherwise, and `lockFile`, `unlock` and `openAndLock` all use it (`:160`, `:176`, `:312`, re-read). The tests pin the layout and the count; only the two-process IT shows the OS lock itself (layer 2). The spec's test (1) as written cannot discriminate in one JVM (layer 1). |
| D3 apply to the audit log and document it | shipped-as-specified | `AuditLog` needed no code change (Javadoc only); ADR 024 reads "Superseded in part by ADR 068"; `core-store.adoc`, `runtime-lock.adoc`, `runtime-audit.adoc` describe the rule; the "one `FileLockManager` instance per runtime" hazard stays and says why (leaf, `FileLockManager` Javadoc `:77`, not re-read). **The shared specification is left stale on purpose** (layer 1). |

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`, surface `orchestrator.analyze`) — reason: a full-ship landing is read on three layers by its own leaf, with no size qualifier; it shared no context with the leaves of the earlier landings. The leaf wrote nothing. By reading, no build, no test run, no write. The spec and the landing message were passed as data.
- **Re-read by the orchestrator at `git show 7c87ef4f:<file>`:** the `LockLevel.LEAF` constructions across `src/main` (`ActivityJournal:198,269,291`, `LockKey:314,327`, `FileLockManager:131,314`); `WaiverLog:127`, `ChangeLedger:78`, `QuestionLog:237,426` (each `scope.lockKey()`); `FileLockManager:120-135,156-180,305-320`; `FileLockManagerTest:105-114,174-176,755-760,780-800,811-818,838-842`; the absence of `@EnabledOnOs` in the lock tests; the absence of `Thread.sleep` in the added lines; the unbounded `secondThread.join()` at `:799`; `LockKey:15-24`; `LockKeyTest:184-188`; `ActivityJournal:286-304`; the two ADR file names; `SchemaResource.java` (an import moved); `12-machine-stores.adoc:344`. **Not re-read:** `AuditEvent` line 634 (the leaf named a path that does not exist; the class is at `core/service/AuditEvent.java`), the Windows reading, the FileLockManager Javadoc at `:77`, the recorded failing run of the narrative (a job on a branch commit), `FileLockManagerTwoProcessIT:170`, and the statement that the `JournalScope` keys are non-leaf beyond the construction search above; they are the leaf's own reading and are recorded as such.
- **Verdict:** layer 1 findings — 9; layer 2 findings — 12; layer 3 findings — 10 (the leaf returned 34 in all, the count includes `info` rows).

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence |
|---------|-------|---------|----------|----------|
| L1-1 | OBSERVED: ADR 024 accepted the loss of the OS lock for the audit log | checked | confirms | ADR 024 Context line 19 and Decision line 30 (leaf, text matches the spec quote). Persisted as `corroborated`. |
| L1-2 | HYPOTHESIS: the other file-locked JSONL stores (`WaiverLog`, `ChangeLedger`, `QuestionLog`) append through the same path and are affected | checked | **refutes** | they lock under `scope.lockKey()` and the only `LockLevel.LEAF` constructions in `src/main` are `LockKey.auditLog`, `LockKey.mailbox` and the three in `ActivityJournal` (re-read). Only the audit log used a leaf key with `JsonlStoreFile` in production; `mailbox` has no production caller on `main`. The defect was live for the audit log alone. The lane found the same (its hypotheses table). Persisted as `contradicted`, `rescoped: no`. |
| L1-3 | OBSERVED: `PLAN-V2-07` builds `EpicInbox` and `EpicRegisterStore` on `JsonlStoreFile.append` under the epic lock | not_shown_by_reading | none | the spec says "under the epic lock", an `EPIC` key under `locks/`, so those stores need this fix only if they use a leaf key; the spec of PLAN-V2-07 is not in the ledger's `plans/` or `queue/` (the lane's own note). Persisted as `unverifiable`. |
| L1-4 | spec test (1): "a second `FileLockManager` instance, in a second thread with its own channel, cannot take the key meanwhile" | checked | **refutes** (the test as specified) | in one JVM the lock table keeps the first channel's registration, so the second manager is refused on the unchanged code too; the Done-when "fail on the unchanged code" is reachable only by a layout assertion (the sidecar exists) or by a second process. The lane saw the same and added both. Not a corpus claim. |
| L1-5 | Expected Surface names six paths | checked | refutes | the real code change is `FileLockManager` and `ActivityJournal` (three key sites); `JsonlStoreFile`, `AuditLog`, `LockKey`, `LockLevel` changed Javadoc only; the `LockManager` port, `JsonlStoreFileTest` and `AuditLogTest` are untouched; `ActivityJournal` was not predicted (its hand-built key would have produced `.lock.lock`). The surface over- and under-declared. |
| L1-6 | Expected Surface path of ADR 024 | checked | refutes | the spec names `024-the-audit-log-is-appended-…-under-the-in-process-lock.adoc`; the file is `024-audit-log-appended-through-the-jsonl-store-file.adoc` (re-read). |
| L1-7 | spec D1 candidates (a), (b), (c); ADR numbering | checked | confirms | ADR 068 is the next free number in `core-store/`. |
| L1-8 | Done-when "the ADR and the guides say the same as the code" | not_shown_by_reading | none | holds for the `pm-core` and `pm-runtime` guides; **the shared specification still says the append "releases the lock of the operating system with its channel after each append"** (`doc/specification/store-schemas/12-machine-stores.adoc:344`, re-read) and in six more places the lane lists. The plan's rule list excludes R05, so the specification was left stale. A correction owed. |
| L1-9 | a layer-1 reading of the spec's premise that the fix was needed before PLAN-V2-07 | not_shown_by_reading | none | see L1-3. |

**Layer 1 verdict:** findings — 9 (L1-2, L1-4 to L1-6 and L1-8 refute or leave a premise open; none blocks the shipped work, but L1-8 leaves the specification false).

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| D1 | ADR 068 | n/a | — | checked |
| D2 | `FileLockManager.osLockFile` and its three uses; `ActivityJournal` keyed on the journal itself | `FileLockManagerTest.appendKeepsTheLock` (`:755-`) fails on the unchanged code **only because the sidecar does not exist** (`isLockedIfPresent`, `:174-176`, re-read: `Files.exists(file) && isLocked(file)`); the in-JVM `isLocked` answers from the JVM lock table, so the test pins the layout and the count, not the OS lock's survival. A regression in which another channel opens the sidecar passes. `leafAppendKeepsTheOsLock` in the two-process IT is the only OS-level proof (an integration-lane test, not run in the unit lane; its fail-on-unchanged claim is inferred, not a recorded failing run) | **major** — the tests prove where the lock is taken and how often it is released, not that an append leaves it held; **minor** — `secondManagerCannotTakeTheKey` passed on the unchanged code at `2b052c31`; only the later `lockedAfterAppend` assertion makes it fail there, and that strengthening was never run against the unchanged code (the lane's N3); `repairAndAnchorUnderTheLock`: the anchor-once and two-lines assertions hold on the unchanged code, only the sidecar assertion fails there | checked, refutes |
| D2 (cont.) | "5 failed on the unchanged code" | two of the five (`leafLock`, `epicThenMailbox`) are modified pre-existing tests | not re-readable: the run is on a branch commit (job `9437558d`) | not_shown_by_reading |
| D2 (cont.) | non-leaf levels still lock their own lock file | the pre-existing `isLocked(key.lockFile())` assertions (`:220-732`) are unchanged | — | checked, confirms |
| D2 (cont.) | `releasedOnceByTheLastHolder` (`:811-`) | pins the count: first close keeps the lock, last close releases it, re-acquire works; a double release or a leak is implied, not asserted | — | checked |
| D2 (cont.) | no sleep | no `Thread.sleep` in the added lines (re-read); but `secondManagerCannotTakeTheKey` ends with an unbounded `secondThread.join()` (`:799`, re-read) where the narrative says "a bounded `join`" | the lane's wording is true of the two-process IT only | checked, refutes (the narrative, in part) |
| D3 | `AuditLog` needs no change (Javadoc only); `LockLevel` Javadoc only | `repairAndAnchorUnderTheLock` | — | checked, confirms |
| plan | files the diff touched that no deliverable names | — | `pm-core/src/test/java/de/planmarshall/core/SchemaResource.java`: an import moved by the `-Ppre-commit` gate (re-read), unrelated to the plan and shipped inside the squash (the standing practice is a cleanup commit of its own); `TestLocks`, `LockHolderProcess`, `FileLockManagerTwoProcessIT` and `runtime-audit.adoc` are explained by the ActivityJournal guards, the OS-level proof and a stale sentence | checked |

Not shown by reading: the green runs the narrative lists (CI is green, above), the failing run on the unchanged code, and the two-process IT outcome.

**Layer 2 verdict:** findings — 12 (one major: the unit tests pin the layout and the count and cannot show the OS lock survives an append; the rest minor or info).

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `ActivityJournal.java:286-298` (`delete`, re-read) | a `delete` of a journal while the manager still holds the OS lock on the sidecar's inode, and a second process waiting on that inode | the sidecar is unlinked under the lock; the waiter holds the old inode and a creator of a new sidecar can hold the journal at the same time. The same sibling file as before this plan, so pre-existing in kind; excluded in practice by `RuntimeLock` (leaf) | minor |
| L3-2 | `FileLockManager.java` class Javadoc (`:77`, not re-read) | a second `FileLockManager` instance on the same base | its failed `lock()` closes a channel on the sidecar and drops the first instance's OS lock; documented, and no test can show it in one JVM | minor |
| L3-3 | `LockKey.java:19-21` (re-read) | the class and the `lockFile` parameter Javadoc | says "the file the lock guards" for every key; for a non-leaf key `lockFile` is the lock file below `locks/` and guards another store. The sentence the diff added ("the OS lock of a leaf key is taken on the sidecar") is true; the neighbouring one stays imprecise | minor |
| L3-4 | `LockKeyTest.java:186` (re-read) | the display name | "the leaf keys are taken on the append-only file itself" contradicts ADR 068; the assertions are right (the lane's N1) | info |
| L3-5 | `AuditEvent` `details.held` (the class is `core/service/AuditEvent.java`; the leaf's `:634` was not re-read) and `StoreLockGuard` messages | a refused leaf lock | print `key.lockFile()`, which for a leaf key is the store and not the sidecar; cosmetic (the lane's N4) | info |
| L3-6 | `LockKey.java:314,327` (re-read) | `delete` or archive of an audit log or a plan directory | no cleanup of the sidecar except in `ActivityJournal.delete`; an orphan sidecar holds no lock (ADR 068 consequence), but archived plan directories carry `mcp/mailbox.jsonl.lock` | info |
| L3-7 | `FileLockManager.java:314` (re-read) | a leaf key whose parent directory is missing | `createDirectories` is skipped for a leaf key, so the acquire fails with an `UncheckedIOException` naming the sidecar (asserted by `leafLock`); the guarded store is created by the append, not at acquire | info |

Also read and noted: the sidecar name is `fileName + ".lock"` through `resolveSibling` and no hand-built leaf key remains on `main` (the three `ActivityJournal` sites now key on the journal); the enter/leave of `osLocks` is atomic, so a new acquire between the last close and the leave reopens the channel; threadLock is taken before the OS lock and released after it; no catch of a generic exception, no logging, no new dependency.

**Layer 3 not shown by reading:** the Windows path (the new tests carry no `@EnabledOnOs`, the spec scopes Windows out), the runs, and the lock under contention across two processes.

**Layer 3 verdict:** findings — 10 in the leaf's count (7 rows above, three info rows folded), all minor or info; no blocker or major defect in production code.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md | never catch or throw a generic exception in production code | conforms | `FileLockManager` adds no catch |
| CLAUDE.md | logging only through `LogRecord` holders | conforms | no logging added |
| CLAUDE.md | every public type documented, `@NullMarked` | conforms | only Javadoc touched on public types |
| CLAUDE.md | JUnit 5, EasyMock, no new hand-written fake | conforms | the anchor is `createMock(AuditAnchor.class)` |
| CLAUDE.md | assertion messages of 20 to 60 characters | conforms | the new messages read 21 to 60 (leaf) |
| CLAUDE.md | test data generated, a literal only where it is the contract and said so | deviates | `FileLockManagerTest:107-109` fixes an `Instant` and a `Clock` ("the instant and the clock of the audit records") with no contract statement; `APPENDED` and `TORN` are labelled (`:111-113`, re-read); principals and ids are generated |
| CLAUDE.md / spec Standing Constraint | OS-specific behaviour is tested with `@EnabledOnOs` | deviates | the new POSIX-only tests carry none (`git grep` of the lock test directory: 0 hits) |
| CLAUDE.md | no sleeps in tests | conforms | none in the added lines |
| CLAUDE.md | no new dependency, no module boundary change | conforms | no POM change |
| Spec Done-when | `verify`, `-Ppre-commit`, `-Pcoverage`, integration tests, Sonar | not_shown_by_reading | CI is green and Sonar reads 0 open issues (above); the full-reactor `-Pcoverage` has no green run on the host, the lane says so |

## Metrics and Anomalies

- Tokens: `unknown` (unobserved, never zero).
- Duration: the landing gives none; `build (25)` 701 s, `build (26)` 585 s, `sonar-build` 769 s (`ci checks`).
- Anomalies: CodeRabbit refused on quota three times and reviewed after the 90-minute window and an escalation (the lane's table); Sourcery refused on quota (the check on the PR reads `SUCCESS`, the lane says no review); full-reactor `verify -Pcoverage` and `verify -Ppre-commit` exceeded the host bound once each and ran per module; the first scoped test run was submitted without `--project-dir` and ran against the main checkout (the lane's disclosure); the lane used the shell for file reads (`ls`, `head`, `tail`) against the rule, and says so.

## Routing and Merge Behavior

- Review: CodeRabbit (required) reviewed `fe10ba5a`, no actionable comment; Sourcery optional.
- CI/merge: success on 20 checks; merge group run `42544879924` success; merge commit `7c87ef4f`.
- Sonar: the lane's step reports 0 open issues; the orchestrator's plain-HTTP read of `pullRequest=328` agrees, gate `OK`.

## Reconciliation Actions

- [x] row `status` → `shipped`
- [x] row `pr` stamped `#328`
- [x] row `landing` stamped `landings/PLAN-V2-95.md`
- [x] row `plan_marshall_plan_id` stamped `v2-95-lock-manager-channel-close` (from the sender `rb-v2-95-…`)
- [x] layer-1 verdicts persisted on the spec (claim 0 `corroborated`, claim 2 `unverifiable`, claim 3 `contradicted` with `rescoped: no`)
- [x] `epic.md`: recurrence folded into the runbook-lane `unknown` Open Defect; findings recorded as one Open Defect (Wa84), the specification follow-up recorded in it
- [x] resume anchor, `queue-view.md`, committed in the ledger worktree

## Follow-Ups

No fix plan is staged; the operator decides. Candidates: (1) the specification correction owed by L1-8, seven places in `doc/specification/` (store-schemas 04, 12, 13, 16; filesystem-layout 02; credentials-and-enrolment 03; model-work 08), which the plan's rule list excluded; (2) one test follow-up that proves the OS lock survives an append in the unit lane, or an `@EnabledOnOs` on the POSIX-only tests and a labelled `Instant` (L2, conformance); (3) the Javadoc and display-name residue (L3-3, L3-4, L3-5). PLAN-V2-99 shares `FileLockManager.java` and its test with this plan, which is now on `main`: its overlap with a running plan is gone; its spec must be read against the new sidecar rule. PLAN-V2-94 is free of the overlap with 95.

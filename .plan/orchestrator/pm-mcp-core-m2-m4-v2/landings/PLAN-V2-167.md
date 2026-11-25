# Landing Analysis: PLAN-V2-167 — pending-entry-closeout

epic: pm-mcp-core-m2-m4-v2
workstream: WS-03
pr: #566 (merge `95ba45dd74d61751b96a934fb28a0238a999cfad`, 29 files, +1132/-222, after a base merge of `main` at `67a893b6`)

> Landing record, runbook-lane form (`rb-v2-167-pending-entry-closeout`, harness opencode, model `deepseek-v4.1-flash/high`, effort high, executor 0.1.2184, standing rules r7, 4 of 4 deliverables by the lane's count). Landing message `-001` (2026-11-22T21:37:20Z, `merge_state=merged`). `landing-check`: incomplete on `total_tokens` and `plugin_version` (the two keys of every opencode landing, folded into the standing Open Defect). Re-read at the provider: #566 `merged` at `95ba45dd`; two inline review threads (`PRRT_kwDOVBd2NM66nUbb` and `66n8nv`), all resolved, matching the lane's 0 unresolved; CodeRabbit reviewed `78da4751` and `c7ba6996`, its quota refused `c268441d` and later heads (all its findings handled; the lane relied on the `review_complete_after_handling` exemption). Stated by the lane, not re-verified: the mutation-run log ids, the test counts (12 146 and 12 649), Sonar 0 open at the merge head, one CI infrastructure cancellation rerun green, three review-body findings handled.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 the move: epic target, source read, manifest, `finish`/`discard` | shipped-as-specified, with a Javadoc-only caller duty | `PendingEntryCreation.java:106-110` refuses an epic target with a reason (the operator chose "refuse"); the source read is `isSymbolicLink` plus `toRealPath().startsWith` and then `newInputStream(NOFOLLOW_LINKS)` (`:197-215`); `SourceManifest.java:83-90` refuses a shared path and `sources.json`; `PendingEntryStore.java:314-315,467-468` tolerate a removed directory; "the caller verifies before `move`" is stated in Javadoc (`:80-88`), not enforced (the spec allowed "state or enforce") |
| D2 the sweep | shipped-modified | a symbolic-link store path is caught per entry and reported `(held: ...)`, not at the project level (`PendingEntryRecordsSweep.java:185-193`); the dead two-argument `isResidue` is gone; the evidence rule is stated in `runtime-lifecycle/01`, `store-schemas/01`, `12`, `13`, the guide and ADR `runtime-start/016` |
| D3 the tests that can fail | met in existence; the mutation claims are not re-runnable | `PendingEntryStoreTest`, `PendingEntryCreationTest`, `SourceManifestCodecTest`, `PendingEntryRecordsSweepTest.creationCutAfterBothCommits`, `PeriodicSweepKilledRuntimeIT` (rewritten onto the real step and a real `entry.json`) |
| D4 the specification, the ADRs and the policies | met | the cut bullets of `store-schemas/01` are restored (`scope_not_found`, `integrity_hold`, the recovery sentence qualified as the assembly's); `.init-` is gone from `doc/specification`, the watch and the guides; ADRs `core-store/083` to `086` and `runtime-start/017` are new (no duplicate numbers: `core-store` ends at 086, `runtime-start` at 017); the `removed_at` detail is named in `store-schemas/12:311` |

Shared Done-when clause not met as written: **`BROKEN LINKS 0`** (the lane reports the baseline 97, all cross-repository, unchanged by the branch; the same 97 as PLAN-V2-164 and 166). The lane's cause (a stale `pm-mcp-clients` doc tree) is unverified; a lagging documentation or clients checkout is a likelier one (see the memory note on the documentation checkout). It stays the operator's item recorded in `epic.md`.

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`); no file written, no build run; the spec and the message passed as data.
- **Re-read by the orchestrator at `origin/main`:** `PendingEntryRecordsSweep.java:96-119` (the `unreadable` branch returns `Failed` before `removal.failure()` is read: a held entry is dropped from the report), `PendingEntryCreation.java:268-286` (`copyChat` filters links and special files out without a refusal), `runtime/start/package-info.java:90-92` ("its absent record"), `PendingEntryStore.java:442` (the garbled comment "the record-most years") and `SourceManifest.java:83-90` (a case-sensitive compare of the reserved name, against the message's reason "the manifest is store-written"). **Not re-read, kept as the leaf's claims:** the failure mode of a commit with create targets `a` and `a/b`, the symlinked parent and `entry.json` cases, the `removed_at` consumers, `QuestionLogRecordReader.resolve`, the full diffs of `core-store.adoc` and `runtime-start.adoc`, ADR contents beyond titles.
- **Verdict:** layer 1: no false premise held against the spec, four minor statements; layer 2: no weakened test, minor gaps; layer 3: **no blocker, no major**, minors and leads.

### Layer 1 — Specified

| Finding | Claim | Verdict | Severity |
|---------|-------|---------|----------|
| L1-1 | D2 "the sweep cannot delete an open or tampered entry" | premise held: `verify` returns `Absent` only when all four stores are absent from the sidecar and the filesystem (`PendingEntryStore.java:404-424`) | none |
| L1-2 | D3 `AuditSchemaParityTest.enums` row | replaced by `AuditEventTest.pendingEntryRemoved` and the sweep test with the literal `removed_at`; no schema file lists the `pending_entry_removed` details, so `store-schemas/12:311` has no parity guard | minor |
| L1-3 | the spec named `runtime-entry/` for the ADR | it went to `runtime-start/017` (the sweep's package): justified | none |
| L1-4 | `PendingEntryCreation.java:106-110` | a production message names "PLAN-V2-133 D5" and the test asserts that text: stale once the plan is archived | minor |
| L1-6 | `runtime/start/package-info.java:90-92` | still says the evidence is "its absent record" (re-read) | minor |
| L1-7 | `QuestionLog.java:40-43,407-420` | `moveEntryQuestions` still accepts an epic target while ADR 083 says only a plan; only `move` refuses | minor |
| L1-8 | `PendingEntryStore.java:442` vs `PendingEntryCreation.java:136-137` | the `stores()` comment is garbled and says the record is deleted first; `move` deletes it last; no Javadoc explains the difference (re-read) | minor |

### Layer 2 — Implemented

| Finding | Detail | Verdict |
|---------|--------|---------|
| L2-1 | `PeriodicSweepKilledRuntimeIT` rewritten: the real step, a real `entry.json`, the journal asserted present after the kill; the old "one at a time, blocked after the first" scenario is gone because the real step removes all in one transaction | checked, nothing meaningful lost |
| L2-2 | `hand-written-doubles.txt` lost `anonymous:SweepStep#1` because the anonymous class is gone (ADR 026); new `AuditSink` lambdas sit in argument position, which the scan does not count | checked |
| L2-3 | the refusal tests assert reason text; `refusesAnEpicTarget` checks `sources` only on an uncommitted `moveTo` | minor |
| L2-4 | no test for case variants, `a` and `a/b`, or a symlinked parent | minor |
| L2-5 | `creationCutAfterBothCommits` uses an entry with no sources and no chat | minor |
| L2-6 | stale-statement search: `.init-`, the atomic-rename publish and the two-argument `isResidue` are gone from code, specification and guides; the remaining "tampered" uses are correct | checked |

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| C-1 | `SourceManifest.java:83-90`, `PendingEntryCreation.java:215-218` (re-read) | a source file named `Sources.json` or two sources that differ only in case, on a case-insensitive filesystem | two create targets map to one file: the manifest is overwritten or the commit fails | minor (platform-dependent) |
| C-2 | `SourceManifest.java:83-90` | paths `a` and `a/b` | pass the manifest; the commit fails with an `UncheckedIOException`, not a refusal (commit failure mode unread) | lead |
| C-3 | `PendingEntryCreation.java:268-286` (re-read) | `chat/` is a link, or holds links or special files | the transcript is dropped silently and deleted with the directory by `finish`; `sources/` refuses | minor |
| C-4 | `PendingEntryCreation.java:197-215` | a link swapped in after the check, or a link in a parent that stays inside the source directory | passes; the digest compare bounds a swap to bytes equal to the manifest | minor |
| C-5 | `PendingEntryCreation.java:121`, `PendingEntryStore.java:308-329` | `pending/` or `pending/<id>` is a symbolic link | `move` and `discard` resolve through links where `verify` does not; rests on the caller's `verify` first (not run) | lead |
| C-6 | `PendingEntryCreation.java:166-197` | a public `composeRequest` with a non-normalized source directory | a valid file is refused as "leaves the source directory" | minor |
| C-7 | `PendingEntryCreation.java:197-200` | a manifest path with NUL | `InvalidPathException` instead of a `StoreRefusal` | minor |
| C-8 | `PendingEntryRecordsSweep.java:96-119` (re-read) | one unreadable record and one held entry | the `unreadable` branch returns before `removal.failure()`: the held entry is dropped from the report | minor |
| C-9 | `PendingEntryRecordsSweep.java:117,147-150` | `discard` after the commit finds the record again (the same id re-created) | `IllegalStateException` uncaught in the loop: the removed count is lost | lead |
| C-10 | `PendingEntryRecordsSweep.java:189` | any other `IllegalArgumentException` of recovery or sidecar code | reported as `held`; a held entry fails the outcome on every hourly pass | minor |
| C-12 | `AuditEvent.java:224,334-340` | `removed_at` is `Instant.toString()` | variable fraction; no consumer and no format statement in `12-machine-stores:311` | lead |
| C-13 | (not in the diff) | a creation cut between the two commits | documentation only, the assembly owns it (ADR 086): the record stays, a repeated move is refused, the plan scope is stranded | by decision |
| C-14 | `PendingEntryCreation.java:134-137` | a symlinked `sources/sources.json` or `entry.json` | followed by `entries.read` and `readSources`; only the caller's `verify` refuses | lead |

Checked, no defect: the four writes are one `ScopeTransaction`; streams from `Files.list` and `Files.walk` are closed; `@NullMarked` and the `Optional` rule hold; a sweep removing the directory before `finish` is tolerated; the sweep cannot enter the creator's window because the creator holds the workspace lock.

## API (for the assembly)

`PendingEntryCreation.move(ScopeTransaction, ScopeTransaction, EntryId)` keeps its signature: an epic target throws `IllegalArgumentException` (reason text only), a source file without a manifest entry, a stored source that is or passes through a symbolic link, or a source directory that is no directory throws `StoreRefusal` (`unreadable`, told apart by message); the caller must `verify` under the workspace lock first, the move does not. `finish` and `PendingEntryStore.discard` return `boolean` (false when the directory was already gone). The `SourceManifest` constructor throws `IllegalArgumentException` for a shared path or `sources.json` (the codec wraps it as `StoreRefusal`). `AuditEvent.pendingEntryRemoved(entryId, directory, removedAt, transactionId)` has the details `entry_id`, `store`, `removed_at`, `transaction_id`. The sweep returns `Failed` with `(held: ...)` for a symbolic-link store path instead of throwing. Whether the assembly relies on the exception types is not read.

## Residue and its placement

No staged plan owns the pending-entry stores or the start sweep (PLAN-V2-133 is parked). A new regular cleanup plan, **PLAN-V2-171** `pending-entry-residue-closeout` (WS-03, runbook, 4 deliverables), carries item 89: the manifest and the move at their edges (C-1 to C-7, L1-7, the plan id in a message, the parent links), the sweep's report and catch (C-8 to C-10, the stale `package-info`, the garbled comment), the thin tests and the `removed_at` format (L1-2, L2-3 to L2-5, C-12), the statements. **Recorded, not fixed:** the `ent-` shadowing (an identifier-grammar decision), the creation cut and the live-plan enumerator (the assembly `plan-marshall-mcp`, ADR 086), the epic's ledger copy of the sources (PLAN-V2-133 D5, parked). **Open for the operator (recorded in `epic.md`):** the links-check clause `BROKEN LINKS 0` (97 cross-repository links; 164, 166 and 167 all reported them; fetch the sibling checkouts first, a lag is a likelier cause than the lane's stale tree).

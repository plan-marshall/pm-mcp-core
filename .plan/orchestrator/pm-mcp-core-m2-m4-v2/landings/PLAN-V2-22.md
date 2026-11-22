# Landing Analysis: PLAN-V2-22 — start-source-intake (the `pm-mcp-core` slice)

epic: pm-mcp-core-m2-m4-v2
workstream: WS-03
pr: #560 (merge `969669cda3cd471e10f9b0e3d8745cce0a09ce08`, 54 files, +3183/-714)

> Landing record, runbook-lane form (`rb-v2-22-start-source-intake`, harness opencode, model `deepseek-v4.1-flash/high`, effort high, executor 0.1.2183, `deliverables_done=4` of 5, standing rules r7). One landing message (`rb-v2-22-start-source-intake-001.md`, 2026-11-22T14:28:11Z, `merge_state=merged`). `total_tokens` and `plugin_version` are `unknown` (the two keys of every opencode landing, folded into the standing Open Defect). Self-review `budget-exhausted` (round-3 fixes `3559494b` not re-reviewed). CodeRabbit 21 findings all stated handled, Sonar 0 at the merge head and gate ok: not re-verified by the orchestrator. **The operator scoped the run to the `pm-mcp-core` slice** (deliverables 3, 4, 5 at store level, 12 and 13; the answer to the lane's question "run the pm-mcp-core slice"); the parts that live in `plan-marshall-mcp` are not met and stay with that repository. No blocker; three majors.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 `rate_limited` source cause, D2 `documents_mode`, D6 to D9, D11, D14 | not met, moved to PLAN-V2-133 | the spec itself moves them (PLAN-V2-133 is parked) |
| D3 an entry never expires, the sweep removes only cut-off directories | shipped-as-specified | `PERIODIC_SWEEP.PENDING_ENTRY_MAX_AGE`, `createdBefore` and `PM_RUNTIME-23` removed; the step decides by an absent record plus an absent sidecar verdict, re-judged under the workspace lock (`PendingEntryRecordsSweep.java:129`) |
| D4 the entry as a directory | shipped-modified (partly met) | store, codec, `SourceManifest`, `verify`, `discard` real; the hold has no producer (the `integrity_hold` raiser and the `accept-edit` route are `pm-mcp-server`); "created in one transaction" has no test that can fail (L2-1) |
| D5 source directory and the move | shipped-modified (partly met) | `PendingEntryCreation.move` and `finish` exist at store level; not met here: the `start.create-scope` unit, `origin` in `mcp/status.json`, the epic-ledger part, recovery of the cross-commit window, and the `.init-<entry_id>` publish the specification still describes; the EPIC target does the wrong thing (L3-3) |
| D10 entry routes | not met, assembly | `/api/v1/entries` is a `pm-mcp-server` class |
| D12 schema parity | shipped-modified | `@Disabled` removed from `PendingEntrySchemaParityTest`; `AuditSchemaParityTest.enums` was already enabled and untouched, so that half of the row is vacuous |
| D13 reader, entry address, trust assumptions | shipped-modified (partly met) | `PendingEntryDirectoryReader` real with 9 tests, `ReadRequest.ofEntry` and the `QuestionLogRecordReader` resolution exist; the engine call site was not changed (L3-2) |

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`, `orchestrator.analyze` surface); no file written, no build run; the spec was passed as data from a file outside the repository. Reason: 54 files across the entry store, the sweep, the reader and the specification, in a drain of two landings.
- **Re-read by the orchestrator at `origin/main`:** `WorkflowEngine.java:702-704` (the question read still takes `status.scopeId()`), `PendingEntryCreation.java:94` (accepts PLAN and EPIC; the class is in `pm-core/.../store/`, not `pm-runtime`), `doc/specification/filesystem-layout/03-project-store.adoc:182-184` (sources go to the plan or the epic's ledger, not the machine store). **Not re-read, kept as the leaf's claims (unverified leads):** the sweep's no-data-loss reading (L3-7), the specification diff of L1-2, the test-gap claims, the API list, L3-1, L3-4 to L3-6, L3-9 to L3-11.
- **Verdict:** layer 1: findings (one major: the specification was rewritten to the code); layer 2: findings (test gaps); layer 3: findings (two majors, one major lead); no blocker. The sweep cannot delete an open or tampered entry by the leaf's reading.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence |
|---------|-------|---------|----------|----------|
| L1-1 | `PENDING_ENTRY_MAX_AGE`, `createdBefore` and `PM_RUNTIME-23` existed and are removed; the spec texts already stated the target | checked | confirms | the Periodic Sweep and Timeouts § Pending Entry texts |
| L1-2 | The landing rewrote the specification, not only the code: `store-schemas/01-format-and-session-log.adoc` Pending Entry went from 8 detailed bullets to short bullets; the state-catalogue/09 Creation paragraph was rewritten | unverified lead (the leaf read the diff) | refutes | dropped: the server-started plans bullet (`from_epic`, `epic_row`), `scope_not_found` for a deleted entry, "kept only in level-1-cleaned form with its screen verdict", `integrity_hold` and `accept-edit entry <entry_id>`; weakened: "a pending entry directory whose plan exists is deleted" now reads "today it does not"; left contradicting the code: the `start.create-scope` bullet still says `plans/.init-<entry_id>/` and one atomic rename. Unimplemented requirements were removed from the spec instead of staying owed |
| L1-3 | The spec says an epic's machine store keeps no source | checked | confirms | `filesystem-layout/03:184`; the code violates it (L3-3) |
| L1-4 | D4 and D12 name `decisions.jsonl` and `integrity_pause`; the landing used `decision-log.jsonl` as the spec's own correction note says | checked | confirms | |

**Layer 1 verdict:** findings — 1 major (L1-2, a lead to verify before it is acted on).

### Layer 2 — Implemented

| Finding | Detail | Verdict |
|---------|--------|---------|
| L2-1 | `PendingEntryStoreTest.created` (`:185`) asserts only the end state; it cannot fail if `create` used four transactions; there is no step-hook crash test for `create`; only `removed()` (`:292`) proves a journal of four targets | checked |
| L2-2 | Tamper tests cover the record only (`tamperedEntryAlone` `:410`) and an untracked record or manifest (`untrackedIsTampered` `:433`); none for `questions.jsonl`, `decision-log.jsonl`, the manifest or a deleted record, though the code handles them (`PendingEntryStore.java:408`, `ScopeIntegrity.java:260`) | checked |
| L2-3 | `refusesADifferingSource` tests a stored file that differs from its manifest digest; nothing checks that a file in `sources/` has a manifest entry (`move` copies only listed files); the manifest refuses neither two sources with one file path nor the path `sources.json` (`SourceManifest.java:306-310`) | checked |
| L2-4 | The PR says the manifest is "verified when a stored file is read"; the only verifier is `PendingEntryCreation.verified` at move time | checked |
| L2-5 | `doc/implementation-watch/store-schemas.adoc` itself says the routes and the client-side hold stay for the start, so "PM-WATCH-STORE-42 met" holds for the store slice only; of the crash points only `refusesARetryAfterTheTargetCommitted` is tested; the window between both commits and `finish` is tested synthetically; the abandon end has no test | checked |
| L2-6 | `PeriodicSweepKilledRuntimeIT` runs the fixture step of `RuntimeChildProcess` (a `.keep` sibling), not `PendingEntryRecordsSweep`; the landing admits it | checked |
| L2-7 | `PendingEntryRecordsSweep.isResidue(store, entryId)` (`:175`) is dead code | checked |
| L2-8 | Unexplained by a deliverable: `StoreSnapshot.readIfPresent` now throws for a dangling symlink (a primitive used by every store) and `ScopeTransaction.apply` skips the directory fsync when the parent is gone | checked |
| L2-10 | ADR `core-store/007:16` still says "records older than the pending entry record maximum age" and the flat `<entry_id>.json` | checked |

**Layer 2 verdict:** findings — 9 (test gaps, one unborne claim "met for this slice", dead code, two unexplained behaviour changes of core primitives).

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-2 | `WorkflowEngine.java:702-704` (re-read) | a `question` state of an entry whose `status.scopeId()` is null for the workspace scope | the engine builds `new ReadRequest(status.scopeId(), ...)` and nothing passes the entry address, so the entry read D13(b) added is never reached from the engine; the re-ground record named this call site as a consumer D13(b) changes; no `pm-workflow` file is in the diff. Whether an entry's bootstrap runs with `scopeId = null` is not traced | major (the premise is a lead) |
| L3-3 | `PendingEntryCreation.java:94` (re-read), `:103-121` | a move with an EPIC target | writes `sources/` and `request.md` into the epic machine scope although the spec says an epic's sources go to the ledger and defines no `request.md` for an epic; the first epic start puts content where PLAN-V2-133 D5 must relocate it | major |
| L3-4 | `PendingEntryCreation` move and the two commits | the new scope's transaction commits and the workspace's does not | the entry record stays, a retry is refused (the create targets exist), the only exit is abandoning the entry, and the plan scope that exists is stranded; the ADR, the spec and the watch say "no recovery derives it" and no plan owns the fix | major (lead on the stranding) |
| L3-1 | `PendingEntryStore.java:305` (`discard`), `PendingEntryCreation.java:132` (`finish`) | the sweep deletes a record-less directory between the workspace commit and `finish` | `deleteRecursively` lists a vanished directory and throws `UncheckedIOException`; `start.create-scope` reports failure after it succeeded | minor |
| L3-5 | `PendingEntryCreation.java:88` (`move`) | a tampered entry | `move` reads the record, manifest and stores without `verify` and copies tampered content into the new scope, which baselines it; the caller (not built here) must verify under the workspace lock first and the Javadoc does not state it | minor |
| L3-6 | `PendingEntryCreation.java:172` (`verified`) | a symlinked `sources/` or a symlinked file | `startsWith` and `Files.readAllBytes` follow symlinks (the rest of the class uses `NOFOLLOW_LINKS`); content must still match the manifest digest; `chat/` files are not covered by the digest sidecar | minor |
| L3-8 | `PendingEntryRecordsSweep.java` evidence rule | a crash before the creating transaction committed | the sweep's evidence is "absent record and absent sidecar" where the spec says "the started scope or the abandonment"; reasonable, not stated in the spec | minor |
| L3-9 | `PendingEntryRecordsSweep.java:110` | `pending/<id>/sources` is a symlink | `ScopeIntegrity.verify` throws `IllegalArgumentException`, which `run` does not catch (it catches `UncheckedIOException`, `IllegalStateException`, `InternalFaultException`, `StoreRefusal`); one bad entry aborts the step for the project | minor |
| L3-10 | `pending-entry.schema.json`, `AuditEvent` | the record format changed (`source` to `recipe`; `requested_id` and `hint` required) with `format_version` still 1; the audit detail `created_at` became `removed_at` for `pending_entry_removed`, an event in no specification | a flat `pending/<id>.json` of the old code is invisible: not listed, not swept, not migrated; pre-1.0 "no shim" excuses it but nothing says so | minor |
| L3-11 | `QuestionLogRecordReader.resolve` `:179-196` | a plan or epic id that parses as `ent-` plus hex | plan and epic existence are tested before the entry id, so such a plan id shadows the entry; whether the plan-id grammar reserves `ent-` is not checked | minor (lead) |
| L3-7 | `PendingEntryRecordsSweep.java:129`, `ScopeIntegrity.java:260` | an open or tampered entry | **not a defect, recorded as evidence:** the sweep deletes only when the record is absent at listing and `verify` is `Absent` for all four stores under the workspace lock; a record deleted outside a transaction is a mismatch and kept; non-entry names and symlinks are never listed; no data-loss path found | — |

**Layer 3 not shown by reading:** (a) whether the failing runs the message cites really fail on the code before the fix (logs not read); (b) the 54-file diff in full (`runtime-start.adoc`, `core-store.adoc`, `runtime-entry.adoc`, the codec test bodies, the generated listing byte-compare); (c) the assertion-message scanner, `ShouldHandleObjectContracts` and `LogAsserts` use, enforced by CI; (d) Sonar 0 and CodeRabbit "handled 21", stated by the message only; (e) real concurrency of sweep, creator and `finish`, and a real crash between the two commits; (f) that the reader, the sweep and `PendingEntryCreation` are wired in `pm-mcpd` (they are not wired in this repository); (g) the self-review round-3 commit `3559494b`.

**Layer 3 verdict:** findings — 11 (L3-2, L3-3 and L3-4 major; the rest minor); not shown by reading — 7. No blocker.

### Conformance to the Project's Own Rules

- R20 hygiene: no Mockito or Hamcrest in the sampled tests (EasyMock `newCapture`, generators, `@DisplayName`); no new `LogRecord`, so no `LogAsserts` obligation; `PM_RUNTIME-23` removed from the holder, `doc/log-messages/pm-runtime.adoc` and the holder test.
- Process breaks the lane states itself: a test file written through a shell heredoc with inline Python, one `git add -A .`, 13 review threads resolved against one general reply, and the links check unrunnable from a nested worktree (3253 pre-existing sibling links). Each is the lane's own report; not re-verified.
- Specification rewrite (L1-2) and the unrecorded `format_version` and flat-record policy (L3-10) are decisions the operator did not take.

## Public API the assembly must follow

`EntryId` (`fileName`, `ofFileName` and `FILE_SUFFIX` replaced by `directoryName` and `ofDirectoryName`); `PendingEntryStore` (paths now `pending/<id>/...`, new and changed `create`, `replace`, `replaceSources`, `remove`, `discard`, `verify`, `readSources`, the directory accessors, `entryIds`); `PendingEntryRecord` (`Bootstrap.source` became `Bootstrap.recipe` with `chosenRecipe()`, `requested_id` and `hint` required, `createdBefore` removed); `AuditEvent.pendingEntryRemoved(String, String, Instant, String)` with `DETAIL_REMOVED_AT` replacing `DETAIL_CREATED_AT`; `PeriodicSweep.PENDING_ENTRY_MAX_AGE` removed and `SweepStep.run(projectId, at)` taking the run instant; `RuntimeLogMessages.PENDING_ENTRY_RECORDS_SWEPT` removed; `QuestionRecordReader.ReadRequest.ofEntry`; new `PendingEntryCreation`, `SourceManifest`, `SourceManifestCodec` and `PendingEntryDirectoryReader`.

## Remaining for the assembly (`plan-marshall-mcp`) and PLAN-V2-133

The `.pmwf` units `start.open`, `start-epic.open` and `start.create-scope` (calling `move` between their own writes, committing the target then the workspace, then `finish`); `origin` of sources in `mcp/status.json`; the epic header's source list and the ledger copy; the `/api/v1/entries` routes and `accept-edit`; the client side of the integrity hold (raiser, `integrity_breach` of kind `store_tampered`, `integrity_hold`); engine wiring of the entry address; recovery of the cross-commit window and the `.init-<entry_id>` publish or a specification correction; `grants.jsonl` and `screen-verdicts.jsonl`; deliverables 1, 2, 6 to 9, 11, 14 (PLAN-V2-133, parked).

## Residue

| Residue | Owner |
|---------|-------|
| L3-2 (the engine's question read for an entry) | PLAN-V2-161 item 83 |
| L3-3, L3-4, L3-1, L3-5, L3-6, L3-9, L2-1 to L2-3, L2-6 to L2-8, L2-10, L3-10, L3-8 (store, sweep, tests, the stale ADR, the format-version and flat-record policy), the restoration of the dropped specification bullets (L1-2) | PLAN-V2-167 (new, staged, item 84) |
| the assembly's pieces and PLAN-V2-133 | Watch in `epic.md` (the assembly is a sibling repository; 133 is parked) |

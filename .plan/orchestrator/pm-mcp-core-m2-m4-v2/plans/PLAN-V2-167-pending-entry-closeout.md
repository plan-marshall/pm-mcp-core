# PLAN-V2-167: The closeout of the pending entry as a directory: the epic target of the move, the move's preconditions, the sweep's corners, the tests that can fail and the specification text the landing cut

epic: pm-mcp-core-m2-m4-v2
workstream: WS-03
origin: the three-layer analysis of the landing of PLAN-V2-22 (`landings/PLAN-V2-22.md`, 2026-11-22, #560 `969669cd`, the `pm-mcp-core` slice) and the operator's rule that leftovers go into a staged plan (no staged plan owns the pending entry store, the sweep and their specification)
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-167-pending-entry-closeout.md` and is queued as one row file, `queue/PLAN-V2-167.json`,
> in the epic ledger (lane `runbook`: the operator runs it with the runbook lane). The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r6`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.


Rules that apply to this plan: R02, R05, R06, R07, R08, R10, R11, R13, R14, R16, R17, R18, R19, R20. (R01: no work package covers this plan beyond `start-source-intake`, whose store slice PLAN-V2-22 shipped; R03, R04: no HTTP; R09: Deliverable 1 touches path confinement of the manifest's source paths; R12: no message is added; R10 because `store-schemas/01` is read by the schema parity tests: its schema blocks, kind tables and anchors stay byte-stable; R13: the format version of the pending-entry record is an operator decision, Deliverable 4; R14 because the plan touches the derived-facts-style stores' transaction journal and the digest sidecar; R16 because it touches the periodic sweep of `pm-runtime`.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-store.adoc` and `pm-runtime/doc/developer/runtime-start.adoc` and `runtime-entry.adoc` (exist; the corrected statements); ADR directories: `pm-core/doc/adr/core-store/` and `pm-runtime/doc/adr/runtime-entry/` (renumbered against `origin/main`, R11); layout: workstream WS-03.
- R12 — none (no message).

**Read the skills first (R20).** `java-core`, `java-null-safety`, `javadoc`, `junit-core` and `cui-testing` of `/home/oliver/git/plan-marshall/marketplace/bundles/{pm-dev-java,pm-dev-java-cui}/skills/`; say in the first progress note which you read. R20 applies to every test line this plan touches.

## Objective

PLAN-V2-22 shipped the `pm-mcp-core` slice of the entry as a directory: the store, the codec, the source manifest, `verify`, `discard`, the move into the new scope and the sweep that removes only a cut-off directory. The sweep cannot delete an open or tampered entry by the analysis's reading, and that is not in question. The residue is: the move accepts an EPIC target and writes sources and `request.md` into the epic's machine scope where the specification says an epic's sources go to the ledger; the move does not verify the entry it copies and follows symlinks in one read; the sweep lets one exception type escape; the tests cannot fail for the claims the landing cites ("created in one transaction", the tamper cases); and the landing cut requirement bullets from `store-schemas/01` instead of leaving them owed. None of the corrections changes a stored datum except where Deliverable 4 says the operator decides.

## Deliverables

All file and line references come from the landing analysis and are re-read at the head of `main` before editing; drop what a merge since then settled and name the commit.

1. **The move: the epic target, its preconditions and the reads (carried item 84 a to e).** `PendingEntryCreation.move` (`:88-121`) accepts PLAN and EPIC: for an EPIC refuse or defer (decide with the operator, R07; the recommendation is to refuse with a named reason until the ledger copy of PLAN-V2-133 D5 exists, because the specification says an epic's machine store keeps no source); state in the Javadoc, or enforce, that the caller verifies the entry under the workspace lock before the move (`move` reads the record, manifest and stores without `verify`); read the source files with `NOFOLLOW_LINKS` and a real confinement check in `verified` (`:172`, the rest of the class already does); refuse in `SourceManifest` two sources with one file path and the file name `sources.json` (`SourceManifest.java:306-310`) instead of an `IllegalStateException` at `move`; make `discard` (`PendingEntryStore.java:305`) and `finish` (`PendingEntryCreation.java:132`) tolerant of a directory a concurrent sweep removed.

2. **The sweep (carried item 84 f to h).** `PendingEntryRecordsSweep.java:110` also catches the `IllegalArgumentException` that `ScopeIntegrity.verify` throws for a symlinked store path (or keeps the entry and reports it), so one bad entry does not abort the step for the project; remove the dead two-argument `isResidue` (`:175`); state in the specification and the guide the evidence the code uses (an absent record and an absent sidecar, re-judged under the workspace lock), which also sweeps the residue of a crash before the creating transaction committed.

3. **The tests that can fail (carried item 84 i to l).** A step-hook crash test for `PendingEntryStore.create` that fails if the four writes were four transactions or not atomic; tamper tests for `questions.jsonl`, `decision-log.jsonl`, the manifest and a deleted record, each asserting `verify`'s reason; a test that every file of `sources/` has a manifest entry (or that `move` refuses one without); the abandon end; the window after both commits and before `finish` driven through `move`, the commits and the sweep, not by a synthetic removal transaction; `PeriodicSweepKilledRuntimeIT` with the real `PendingEntryRecordsSweep` and an `entry.json` fixture instead of the `.keep` sibling of `RuntimeChildProcess`; `AuditSchemaParityTest.enums` was already enabled, so the Done-when row of PLAN-V2-22 for it was vacuous: name the test that really covers the `pending_entry_removed` detail.

4. **The specification, the ADRs and the policies (carried item 84 m to r).** Restore, or record as owed in `doc/implementation-watch/store-schemas.adoc` with a named owner, the bullets the landing cut from `store-schemas/01-format-and-session-log.adoc`: the server-started plans bullet (`from_epic`, `epic_row`), `scope_not_found` for a deleted entry, "kept only in level-1-cleaned form with its screen verdict", `integrity_hold` and `accept-edit entry <entry_id>`, and the recovery sentence "a pending entry directory whose plan exists is deleted"; correct the `start.create-scope` bullet and `state-catalogue/09` where they still describe `plans/.init-<entry_id>/` and one atomic rename that `PendingEntryCreation` does not do (or name the owner of that publish). Update ADR `core-store/007` (it still says records older than the maximum age and the flat `<entry_id>.json`). Name the `removed_at` detail of `pending_entry_removed` in the Audit Log specification (the event appears in no specification). Record `StoreSnapshot.readIfPresent` throwing for a dangling symlink and `ScopeTransaction.apply` skipping the directory fsync when the parent is gone in an ADR or the core-store guide (both changed a primitive of every store without a record). Ask the operator (R07): the `format_version` of the pending-entry record (the record changed with `source` replaced by `recipe` and `requested_id`, `hint` required, version still 1), and a stated policy for an orphaned flat `pending/<id>.json` (pre-1.0, no shim: state it); and the owner of the recovery of a creation cut between the two commits (the entry stays, a retry is refused, the plan scope is stranded): a recovery here, or a specification correction naming the assembly. Check whether the plan-id grammar reserves the `ent-` prefix (`QuestionLogRecordReader.resolve` `:179-196` tests a plan or epic before the entry).

**Done when:** Deliverables 1 to 4; every carried item below has its recorded outcome; no behaviour change beyond the corrections the items state. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of the changed modules (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module runs); the links check (R17) reports `BROKEN LINKS 0` and `INLINE SCHEMA BLOCKS 0` run from the main checkout.

## Standing Constraints

- **Read every cited line at the head first.** The lines and counts of a carried item are those of the landing analysis it names, read on an older head; re-read each at `origin/main` before editing and drop what a merge since then settled (name the commit).
- **Work to the Done-when.** Do not stop to ask the operator which part to continue with; ask only a decision the spec marks ask-at-refine (R07) or one a carried item says needs the operator, and put it in the outline's question, not in a stop.
- **Every carried item gets an outcome** in the landing message and the pull request: adopted into a deliverable, split off to a NAMED existing plan, or dropped, each with its reason; "not in this run's surface" is no reason.
- **No behaviour change beyond the corrections the items state.** A change a carried item does not ask for is ask-at-refine (R07).
- **Where the project analyses its pull requests with Sonar** (the plan lifecycle's Sonar step reports it configured; where it cannot scan, read the pull request over plain HTTP: `pullRequest=N`, never `branch=<feature>`): an open Sonar issue is fixed in a pushed commit, or dismissed with a stated reason; the quality gate is green before the pull request is reported ready.
- **No dependency or plugin is added.**
- **No more than 100 files per pull request** (the watch of the epic ledger; CodeRabbit refuses a larger one): cut the plan into pull requests by deliverable or by module before the first one is opened.
- **Tests that can fail.** A new test fails on the code it guards (a failing run or a mutation, reported by log id); a test of a refusal asserts the reason, not only the type (CLAUDE.md, R20).

## Claim Labels

- OBSERVED: the epic-target acceptance (`PendingEntryCreation.java:94`) and the specification line that an epic's sources go to the ledger (`filesystem-layout/03-project-store.adoc:184`) were read by the orchestrator at `origin/main`; every other point was read by the analysis leaf at `969669cd`.
- LEAD (unverified): the specification diff of the cut bullets (read `git diff 969669cd^1 969669cd -- doc/specification` first), whether a stranded plan scope can follow the cross-commit window, the `ent-` shadowing of a plan id, and the concurrency of the sweep against `finish`.

## Expected Surface

Source, tests, specification and documentation (specific files, R19; the outline adds the files the re-read at the head names):

- OBSERVED (`doc`): `doc/specification/store-schemas/01-format-and-session-log.adoc`, `doc/specification/state-catalogue/09-bootstrap-and-lessons.adoc`, `doc/specification/filesystem-layout/03-project-store.adoc` (read), `doc/implementation-watch/store-schemas.adoc`.
- OBSERVED (`pm-core`): `pm-core/src/main/java/de/planmarshall/core/store/PendingEntryCreation.java`, `pm-core/src/main/java/de/planmarshall/core/store/PendingEntryStore.java`, `pm-core/src/main/java/de/planmarshall/core/store/SourceManifest.java`, `pm-core/src/main/java/de/planmarshall/core/store/SourceManifestCodec.java`, `pm-core/src/main/java/de/planmarshall/core/store/ScopeIntegrity.java` (read), `pm-core/src/main/java/de/planmarshall/core/store/StoreSnapshot.java`, `pm-core/src/main/java/de/planmarshall/core/store/ScopeTransaction.java`, `pm-core/src/main/java/de/planmarshall/core/service/AuditEvent.java` (read), `pm-core/src/main/resources/de/planmarshall/core/store/pending-entry.schema.json`, `pm-core/src/test/java/de/planmarshall/core/store/PendingEntryStoreTest.java`, `pm-core/src/test/java/de/planmarshall/core/store/PendingEntryCreationTest.java`, `pm-core/src/test/java/de/planmarshall/core/store/SourceManifestCodecTest.java`, `pm-core/doc/adr/core-store/007-delete-mode-of-the-transaction-journal.adoc`, `pm-core/doc/adr/core-store/036-entry-question-file-moved-by-the-store.adoc` (read), `pm-core/doc/developer/core-store.adoc`.
- OBSERVED (`pm-runtime`): `pm-runtime/src/main/java/de/planmarshall/runtime/start/PendingEntryRecordsSweep.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/question/QuestionLogRecordReader.java` (read), `pm-runtime/src/test/java/de/planmarshall/runtime/start/PendingEntryRecordsSweepTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/start/PeriodicSweepKilledRuntimeIT.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/start/RuntimeChildProcess.java`, `pm-runtime/doc/developer/runtime-start.adoc`, `pm-runtime/doc/developer/runtime-entry.adoc`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-22 (shipped for the `pm-mcp-core` slice, `landings/PLAN-V2-22.md`: the plan this one succeeds), PLAN-V2-83.
- Overlaps with (class level): PLAN-V2-166 and 161 by `core-store.adoc`, the store-schemas watch and Traceability; PLAN-V2-133 (parked: the sources and their screening) owns the ledger copy that Deliverable 1's epic decision waits for: the gate decides at emit; one shared file does not block.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-167-pending-entry-closeout.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the

## Carried Open Items

84. **The residue of the landing of PLAN-V2-22, the epic target of the move, its preconditions, the sweep's corners, the tests that cannot fail and the specification text the landing cut** (`landings/PLAN-V2-22.md`, carried 2026-11-22; merged as #560 `969669cd`; read by the analysis leaf at `969669cd`, (a) and the engine item re-read by the orchestrator; no blocker). Deliverables 1 to 4 above carry each point in detail: (a) the EPIC target (major); (b) `move` without `verify` (minor); (c) the symlink-following read and the manifest's reserved path and duplicates; (d) `finish` and `discard` against a vanished directory; (e) the cross-commit window with no recovery (major, owner to decide); (f) `IllegalArgumentException` escaping the sweep; (g) the dead `isResidue`; (h) the sweep's evidence rule unstated; (i) the atomicity test of `create`; (j) the tamper tests of the other stores; (k) the sources-versus-manifest test and the abandon end; (l) the real sweep in the kill test; (m) the cut specification bullets (major, a lead to verify); (n) the stale ADR 007; (o) `pending_entry_removed` in no specification; (p) the unrecorded changes of `StoreSnapshot` and `ScopeTransaction`; (q) the `format_version` and flat-record policy; (r) the `ent-` shadowing. The engine's question read for an entry (`WorkflowEngine.java:702-704`) is PLAN-V2-161 item 83.

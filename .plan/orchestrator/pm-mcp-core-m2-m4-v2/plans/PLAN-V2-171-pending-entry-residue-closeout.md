# PLAN-V2-171: The residue of the pending entry's closeout: the manifest and the move at their edges, the sweep's report and catch, and the statements and tests that stay thin

epic: pm-mcp-core-m2-m4-v2
workstream: WS-03
origin: the three-layer analysis of the landing of PLAN-V2-167 (`landings/PLAN-V2-167.md`, 2026-11-22, #566, merge `95ba45dd`) and the operator's rule that leftovers go into a staged plan (no staged plan owns `pm-core` `store` or the `pm-runtime` start sweep; PLAN-V2-133, which owns the epic's ledger copy of the sources, is parked)
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-171-pending-entry-residue-closeout.md` and is queued as one row file, `queue/PLAN-V2-171.json`,
> in the epic ledger (lane `runbook`: the operator runs it with the runbook lane). The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R05, R06, R07, R08, R10, R11, R13, R14, R17, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R09: no confinement; R12: no message is added; R10 because `store-schemas/12` and `store-schemas/13` are read by the schema parity tests: their schema blocks, kind tables and anchors stay byte-stable; R13: the pending-entry record keeps format version 1 (ADR `core-store/085`); R14 because the plan touches the pending-entry stores and their locks; R11 because an ADR may be added.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-store.adoc` and `pm-runtime/doc/developer/runtime-start.adoc` (exist; the corrected statements); ADR directories: `pm-core/doc/adr/core-store/` (next free number after 086 at `origin/main`, R11) and `pm-runtime/doc/adr/runtime-start/` (next after 017); an ADR is written only for a decision the outline cannot defer; layout: workstream WS-03.
- R12 — none (no message).

**Read the skills first (R20).** `java-core`, `java-null-safety`, `javadoc`, `junit-core` and `cui-testing` of `/home/oliver/git/plan-marshall/marketplace/bundles/{pm-dev-java,pm-dev-java-cui}/skills/`; say in the first progress note which you read. R20 applies to every test line this plan touches.

## Objective

PLAN-V2-167 closed the pending entry's move, sweep and statements with four of four deliverables and no blocker (`landings/PLAN-V2-167.md`). What it left is small and sits at the edges of the new code: the manifest checks compare path strings, so a case variant, a file and a directory of one name, and a name `sources.json` in another case pass or fail late; the transcript copy drops links and special files without saying so, where the sources are refused; the sweep's report loses a held entry when another is unreadable, and one broad catch covers more than the symbolic-link refusal; and a few statements and tests are thinner than the code they describe. This plan closes them.

## Deliverables

All file and line references come from the landing analysis and are re-read at the head of `main` before editing; drop what a merge since then settled and name the commit.

1. **The manifest and the move at their edges (carried item 89a).** (a) `SourceManifest.requireDistinctPath` (`:83-90`) compares path strings case-sensitively, and the reason the lane gave for that ("the manifest is store-written") is false for the reserved name and the paths: they come from operator file names; on a case-insensitive filesystem a source named `Sources.json` or two sources that differ only in case address one file: decide (ask-at-refine, R07, recommendation: refuse a path whose case-folded form is taken or is the reserved name, at the manifest) and test both. (b) The paths `a` and `a/b` pass the manifest; the commit then fails on the create targets with an `UncheckedIOException` and not a refusal (`PendingEntryCreation.java:197-200`, the commit's failure mode is a lead: read it): refuse at the manifest with a reason. (c) `copyChat` (`PendingEntryCreation.java:268-286`) drops a linked `chat/`, links and special files silently, and `finish` then deletes the transcript with the directory, where `sources/` refuses: make it refuse (or record why a transcript differs, with the operator's word) and test it. (d) `composeRequest` refuses a valid file when `sourceDirectory` is not normalized (`:166-197`), and a manifest path with a NUL or another invalid character raises `InvalidPathException` and not a refusal (`:197-200`): normalize and refuse with a reason. (e) `QuestionLog.moveEntryQuestions` still accepts an epic target (`QuestionLog.java:40-43,407-420`) while ADR `core-store/083` says only a plan: refuse it there too or say in its Javadoc why a direct caller may. (f) The production message of the epic refusal names `PLAN-V2-133 D5` (`PendingEntryCreation.java:106-110`) and the test asserts that text: name the capability, not the plan id. (g) Read, then decide: `move` and `discard` resolve `pending/<id>` through `scope.resolve`, which follows a link in a parent, where `verify` uses `resolveWithoutLinks` (`PendingEntryStore.java:308-329`), and `entries.read` and `readSources` follow a symlinked `sources.json` or `entry.json` (`PendingEntryCreation.java:134-137`): both rest on the caller's `verify` having refused first; either enforce it in the move or state in the Javadoc and ADR `core-store/084` that it is the caller's duty, with a test of the caller order the sweep uses.

2. **The sweep's report and its catch (carried item 89b).** (a) In `PendingEntryRecordsSweep.run` (`:96-119`) the `unreadable` branch returns `Failed` before `removal.failure()` is read, so a held or failed entry is dropped from the report when another entry is unreadable: report both. (b) The broad `IllegalArgumentException` catch around `verify` (`:185-193`) swallows any `IllegalArgumentException` of recovery or sidecar code and reports it as `held`, and a held entry makes the outcome `Failed` on every hourly pass: narrow it to the symbolic-link refusal (a named refusal type or a reason code) or record why it cannot be narrowed, and test a second cause. (c) `discard` after the commit throws `IllegalStateException` when the record is present again and the discard loop does not catch it (`:117,147-150`): the removed count is lost in the report. (d) `runtime/start/package-info.java:90-92` still says the evidence is "its absent record": say four stores absent from the sidecar and the filesystem. (e) The comment of `PendingEntryStore.stores` (`:442`) is garbled ("the record-most years") and `remove` writes the record first where `move` deletes it last (`PendingEntryCreation.java:136-137`): rewrite the comment and say in one Javadoc line why the orders differ.

3. **The tests that stay thin (carried item 89c).** `AuditEventTest.pendingEntryRemoved` and the sweep test are the only guards of the `removed_at` detail (no schema file lists it): add the parity or state in `store-schemas/12` that none exists, and state the format of `removed_at` (`Instant.toString()` has a variable fraction: say it or fix the format, `AuditEvent.java:224,334-340`); `PendingEntryCreationTest.refusesAnEpicTarget` checks `sources` on an uncommitted `moveTo` only (assert the stores of the target through a committed move as the other refusals do); `SourceManifestCodecTest` has no case variant, no `a` and `a/b`, no symlinked parent; `PendingEntryRecordsSweepTest.creationCutAfterBothCommits` uses an entry with no sources and no chat (add a populated one).

4. **The statements and the hand-offs (carried item 89d).** Keep the guide, ADRs `core-store/083` to `086` and `runtime-start/016`, `017` true to deliverables 1 to 3 in the same pull request; where a point of 1 to 3 is decided as "the caller's duty", the ADR says so. Record, do not fix: the `ent-` prefix is not reserved by the identifier grammar, so a plan id chosen as `ent-<hex>` shadows the entry in `QuestionLogRecordReader.resolve` (the guide names it; an identifier-grammar decision the spec does not ask for); the creation cut between the two commits and the live-plan enumerator belong to the assembly `plan-marshall-mcp` (ADR `core-store/086`); the epic's ledger copy of the sources is PLAN-V2-133 D5 (parked).

**Done when:** every deliverable above; every carried item below has its recorded outcome; no behaviour change beyond the corrections the items state. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed, files outside the plan's scope in their own cleanup commit), then `verify` of the changed modules (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module runs).

## Standing Constraints

- **Read every cited line at the head first.** The lines and counts of a carried item are those of the landing analysis it names, read on an older head; re-read each at `origin/main` before editing and drop what a merge since then settled (name the commit).
- **Work to the Done-when.** Do not stop to ask the operator which part to continue with; ask only a decision the spec marks ask-at-refine (R07) or one a carried item says needs the operator, and put it in the outline's question, not in a stop.
- **Every carried item gets an outcome** in the landing message and the pull request: adopted into a deliverable, split off to a NAMED existing plan, or dropped, each with its reason; "not in this run's surface" is no reason.
- **No behaviour change beyond the corrections the items state.** A change a carried item does not ask for is ask-at-refine (R07).
- **Where the project analyses its pull requests with Sonar** (the plan lifecycle's Sonar step reports it configured; where it cannot scan, read the pull request over plain HTTP: `pullRequest=N`, never `branch=<feature>`): an open Sonar issue is fixed in a pushed commit, or dismissed with a stated reason; the quality gate is green before the pull request is reported ready.
- **No dependency or plugin is added.**
- **No more than 100 files per pull request** (the watch of the epic ledger; CodeRabbit refuses a larger one): cut the plan into pull requests by deliverable or by module before the first one is opened.
- **Tests that can fail.** A new test fails on the code it guards (a failing run or a mutation, reported by log id); a test of a refusal asserts the reason, not only the type (CLAUDE.md, R20).
- **The links-check clause is the operator's.** PLAN-V2-164, 166 and 167 reported `BROKEN LINKS 97` (cross-repository links the tool cannot resolve from this checkout) against a Done-when of `0`: this plan reports the count of local broken links and the count of the rest, separately, until the operator words the clause. Fetch `origin/main` in `plan-marshall-documentation` and in `pm-mcp-clients` before counting: a lagging checkout is a likely cause (the lane of 167 named a stale `pm-mcp-clients` tree; not verified).
- **Do not repeat the mutation claims of 167** as facts: they were not re-run by the analysis. A new test of this plan is shown to fail by its own failing run or mutation, by log id.

## Claim Labels

- OBSERVED: every point was read by the analysis leaf at `origin/main` `95ba45dd`; the orchestrator re-read and confirmed `PendingEntryRecordsSweep.java:96-119` (the `unreadable` branch returns before `removal.failure()`), `PendingEntryCreation.java:268-286` (`copyChat` filters out links and special files without a refusal), `runtime/start/package-info.java:90-92` ("its absent record"), `PendingEntryStore.java:442` (the garbled comment) and `SourceManifest.java:83-90` (a case-sensitive compare of the reserved name).
- LEAD (unverified): the failure mode of a commit with the create targets `a` and `a/b` (1b); the symlinked parent and the symlinked `entry.json` (1g, read before deciding); the `removed_at` consumers (3); that `discard` can see the record again after the commit (2c, reachable only by re-creating the same id).

## Expected Surface

Source, tests, specification and documentation (specific files, R19; the outline adds the files the re-read at the head names):

- OBSERVED (`pm-core`): `pm-core/src/main/java/de/planmarshall/core/store/PendingEntryCreation.java`, `pm-core/src/main/java/de/planmarshall/core/store/PendingEntryStore.java`, `pm-core/src/main/java/de/planmarshall/core/store/SourceManifest.java`, `pm-core/src/main/java/de/planmarshall/core/store/SourceManifestCodec.java`, `pm-core/src/main/java/de/planmarshall/core/store/QuestionLog.java`, `pm-core/src/main/java/de/planmarshall/core/service/AuditEvent.java`, `pm-core/src/test/java/de/planmarshall/core/store/PendingEntryCreationTest.java`, `pm-core/src/test/java/de/planmarshall/core/store/PendingEntryStoreTest.java`, `pm-core/src/test/java/de/planmarshall/core/store/SourceManifestCodecTest.java`, `pm-core/src/test/java/de/planmarshall/core/store/QuestionLogTest.java`, `pm-core/src/test/java/de/planmarshall/core/store/AuditSchemaParityTest.java`, `pm-core/src/test/java/de/planmarshall/core/service/AuditEventTest.java`, `pm-core/doc/developer/core-store.adoc`, `pm-core/doc/adr/core-store/083-the-store-level-creation-carries-a-plans-sources-alone.adoc`, `pm-core/doc/adr/core-store/084-the-move-carries-the-manifests-files-and-no-other.adoc`, `pm-core/doc/adr/core-store/086-the-cross-commit-window-belongs-to-the-assembly.adoc`.
- OBSERVED (`pm-runtime`): `pm-runtime/src/main/java/de/planmarshall/runtime/start/PendingEntryRecordsSweep.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/start/package-info.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/start/PendingEntryRecordsSweepTest.java`, `pm-runtime/doc/developer/runtime-start.adoc`, `pm-runtime/doc/adr/runtime-start/016-the-sweep-removes-pending-entry-residue-by-evidence.adoc`, `pm-runtime/doc/adr/runtime-start/017-one-entries-symbolic-link-holds-that-entry.adoc`.
- OBSERVED (`doc`): `doc/specification/store-schemas/12-machine-stores.adoc`, `doc/Traceability.adoc`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-167 (shipped, `landings/PLAN-V2-167.md`: the plan this one succeeds), PLAN-V2-22.
- Overlaps with (class level): PLAN-V2-161 (`core-store.adoc`, `store-schemas`), PLAN-V2-133 (parked: the epic's ledger copy of the sources, the same `PendingEntryCreation`), PLAN-V2-125 and 126 (test-conformance of the `store` and `start` test trees) and every staged plan by guide, ADR and Traceability file: the gate decides at emit; one shared file does not block.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-171-pending-entry-residue-closeout.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Carried Open Items

89. **The residue of the landing of PLAN-V2-167, the manifest and the move at their edges, the sweep's report and catch, the thin tests and statements** (`landings/PLAN-V2-167.md`, carried 2026-11-22; merged as #566 `95ba45dd`; read by the analysis leaf at `95ba45dd`; no blocker, no major). Deliverables 1 to 4 carry each point: (a) the manifest and the move (the case-sensitive compare of the reserved name and of paths, `a` and `a/b`, the silent drop of links in `copyChat`, a non-normalized source directory and an invalid path character, `QuestionLog.moveEntryQuestions` and the epic target, the plan id in a production message, the parent links and the caller's `verify`); (b) the sweep's report (the `unreadable` branch drops a held entry), the broad catch, the uncaught `IllegalStateException` of the discard loop, the stale `package-info` evidence sentence and the garbled `stores` comment; (c) the thin tests (the `removed_at` guard, `refusesAnEpicTarget`, the missing manifest cases, the unpopulated creation-cut test); (d) the statements and the recorded hand-offs.

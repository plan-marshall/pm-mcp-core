# PLAN-V2-174: The second residue of the pending entry's closeout: the sweep's count and its report on a throw, the refusal tests that assert no reason, the case fold the operator was not asked, and the statements that survive

epic: pm-mcp-core-m2-m4-v2
workstream: WS-03
origin: the three-layer analysis of the landing of PLAN-V2-171 (`landings/PLAN-V2-171.md`, 2026-11-23, #572, merge `57f9d33f`) and the operator's rule that leftovers go into a staged plan (no staged plan owns `pm-core` `store` or the `pm-runtime` start sweep; PLAN-V2-133, which owns the epic's ledger copy of the sources, is parked)
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-174-pending-entry-second-residue-closeout.md` and is queued as one row file, `queue/PLAN-V2-174.json`,
> in the epic ledger (lane `runbook`: the operator runs it with the runbook lane). The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R05, R06, R07, R08, R10, R11, R13, R14, R17, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R09: no confinement; R12: no message is added; R10 because `store-schemas/12` is read by the schema parity tests: its schema blocks, kind tables and anchors stay byte-stable; R13: the pending-entry record keeps format version 1 (ADR `core-store/085`); R14 because the plan touches the pending-entry stores and their locks; R11 because an ADR may be added.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-store.adoc` and `pm-runtime/doc/developer/runtime-start.adoc` (exist; the corrected statements); ADR directories: `pm-core/doc/adr/core-store/` (next free number after 086 at `origin/main`, R11) and `pm-runtime/doc/adr/runtime-start/` (next after 017); an ADR is written only for a decision the outline cannot defer; layout: workstream WS-03.
- R12 — none (no message).

**Read the skills first (R20).** `java-core`, `java-null-safety`, `javadoc`, `junit-core` and `cui-testing` of `/home/oliver/git/plan-marshall/marketplace/bundles/{pm-dev-java,pm-dev-java-cui}/skills/`; say in the first progress note which you read. R20 applies to every test line this plan touches.

## Objective

PLAN-V2-171 closed the pending entry's manifest, move and sweep, with no blocker and no major (`landings/PLAN-V2-171.md`), but its self-review ran after the merge and left 12 minors, and the analysis found more behind them: the sweep's removed count includes an entry whose discard failed, and its report loses the unreadable list when a removal throws; two refusal tests assert no reason and one passes on the code it guards; a stored manifest that was valid now reads as a refusal; and the case fold of the manifest, an ask-at-refine decision (R07), was never put to the operator. This plan closes them.

## Deliverables

All file and line references come from the landing analysis and are re-read at the head of `main` before editing; drop what a merge since then settled and name the commit.

1. **The sweep's count and its report on a throw (carried item 92a, F1, F2, F14).** (a) `PendingEntryRecordsSweep.run` (`pm-runtime/src/main/java/de/planmarshall/runtime/start/PendingEntryRecordsSweep.java:146-159`): `removed.size()` counts an entry whose `discard` failed and the test asserts "removed 1 ..." while the directory still exists (`discardRefusedAfterCommitKeepsTheCount`): count the directories gone, and say in the report which were removed and which were kept; ADR `runtime-start/016`, the guide and `store-schemas/12` ("per directory it removes") follow. (b) The catch at `:121-124` returns a report without the already collected `unreadable` list when `remove()` throws (a rethrown non-link `IllegalArgumentException`, a `StoreRefusal`, an I/O failure from `verify`, a commit failure): report the unreadable list in that case as well, and correct the claim "no failure drops another". (c) Test the rethrow branch of the narrowed catch (`JournalScope.SymbolicLinkRefusal`, `holdsEntry`, `:194-213`) with a second cause that is no link refusal, and correct the display name of `secondCauseIsNoHeldEntry`, which claims more than it tests.

2. **The refusal tests that assert no reason, and one that passes on the old code (carried item 92b, F3, F11, F13, F4, F12).** (a) `PendingEntryCreationTest.refusesAPathBelowALinkedParent`: the pre-171 walk refused its fixture too (a link `parent` not named in the manifest gives "no manifest entry"), so the claimed test-first failure cannot come from it: make the fixture one the old code accepted (a manifest naming the path below the link) and assert the reason; its display name names the `verified` check while the refusal comes from the walk. (b) `PendingEntryStoreTest.linkedStoreRefused` asserts the type only: assert `SymbolicLinkRefusal` and the reason. (c) `SourceManifestCodecTest.invalidCharacters` asserts the message contains "path", which another path rule's message contains too. (d) `PendingEntryCreation.java:301-304` names "a symbolic link" for a plain-file `chat`: separate the two causes in the message and the test. (e) A FIFO or special file NAMED by the manifest passes `requireOnlyManifestFiles` and is opened by `verified` with a blocking open under the workspace lock (`PendingEntryCreation.java:133-135,207-222`, pre-existing): refuse a non-regular file named by the manifest with a reason, test it, and correct the guide's "carried as whole as the sources" (`core-store.adoc`) and ADR 084 where they overstate the symmetry.

3. **The case fold the operator was not asked, and the stored manifest (carried item 92c, F9, F10).** The spec of PLAN-V2-171 marked the case-fold decision of 1(a) ask-at-refine (R07); the lane implemented the recommendation (`SourceManifest.java:91-114`) and did not ask. Put it to the operator in the outline's question with the facts: the fold is `Locale.ROOT` upper then lower case, so it also refuses `ß`/`ss`, `ı`/`i` and ligatures on a case-sensitive filesystem (a collision the Javadoc admits) and does not fold NFC against NFD (accepted); a manifest stored before 171 whose paths differ only in case now reads as a `StoreRefusal` (`SourceManifestCodec.java:125`) with `format_version` still 1 (ADR `core-store/085`). Options: keep it, narrow it to ASCII and a documented Unicode set, or refuse at write and tolerate at read. Implement the answer, test the stored-manifest read, and state it in ADR 084.

4. **The tests and statements that stay thin or stale (carried item 92d, F5 to F8, F15, F16).** (a) `AuditEventTest.pendingEntryRemoved` and the sweep test check `removed_at` against `toString()` with whole seconds only: add a case with a fraction of each length the format allows (0, 3, 6 and 9 digits; the statement at `AuditEvent.java:224-229` and `store-schemas/12:311` is true) so the format is guarded where the docs name their guards. (b) ADR `core-store/083` places the epic-refusal sentence for the question move before "The operator decided this on 2026-11-22 (Deliverable 1 a)": it attributes this plan's choice to the operator's word of PLAN-V2-167: separate them. (c) The corrected "its absent record" sentence survives at `runtime-start.adoc:22` and `:68` (the diagram), `doc/Traceability.adoc:125` and `PendingEntryRecordsSweepTest.java:231`: say four stores absent from the sidecar and the filesystem. (d) ADR `core-store/036:24-25` still says "plan or epic" for the question move. (e) `doc/specification/identifiers/01-general-rules-and-registry.adoc:91` says the manifest is "moved with it into the plan or the epic". (f) `@throws` tags not aligned with their block (`PendingEntryStore.java:407`, `ScopeIntegrity.java:135`). Record, do not fix: the `ent-` prefix shadowing, the creation cut between the two commits and the live-plan enumerator (the assembly, ADR 086), the epic's ledger copy of the sources (PLAN-V2-133 D5, parked).

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
- **The lane of PLAN-V2-171 is still running its follow-up pull request #573 (branch `fix/v2-171-review-minors`, open at 2026-11-23, commits `c90396fe` and `0244da7a`), which edits these files** (`PendingEntryRecordsSweep.java`, `JournalScope.java`, `PendingEntryCreation.java`, `PendingEntryStore.java`, `ScopeIntegrity.java`, `SourceManifest.java`, `AuditEventTest.java`, ADRs 036 and 084, the guide, `doc/Traceability.adoc`) for the lane's 12 minors: read `origin/main` first; do not start before it is merged or closed, and drop every point it settled (name the commit).
- **Deliverable 3 is the operator's decision (R07):** the outline puts it as a question with the recommendation; no implementation of the fold changes before the answer.
- **The links-check clause is the operator's** (counts of 97, 3252 and 3253 by tool root against a Done-when of `0`): report local broken links and the rest separately.
- **Do not repeat the mutation claims of 171** as facts: they were not re-run by the analysis. A new test of this plan is shown to fail by its own failing run or mutation, by log id.

## Claim Labels

- OBSERVED: every point was read by the analysis leaf at `origin/main` `f1db02cd`; the orchestrator re-read `PendingEntryRecordsSweep.java:100-125,146-162` (F1: `removed.size()` is the count of the committed removals, the discard loop only appends a note; F2: the catch returns `Failed` with the message only).
- LEAD (unverified): F3 (that the old walk refused the fixture; read the pre-171 code at `7c1aeb17`), F4 (the blocking open on a FIFO), F10 (an already stored manifest with case variants), the content of the follow-up pull request #573 (not read; it also changes the case fold's key normalisation, `0244da7a`).

## Expected Surface

Source, tests, specification and documentation (specific files, R19; the outline adds the files the re-read at the head names):

- OBSERVED (`pm-core`): `pm-core/src/main/java/de/planmarshall/core/store/SourceManifest.java`, `pm-core/src/main/java/de/planmarshall/core/store/SourceManifestCodec.java`, `pm-core/src/main/java/de/planmarshall/core/store/PendingEntryCreation.java`, `pm-core/src/main/java/de/planmarshall/core/store/PendingEntryStore.java`, `pm-core/src/main/java/de/planmarshall/core/store/ScopeIntegrity.java`, `pm-core/src/main/java/de/planmarshall/core/store/JournalScope.java`, `pm-core/src/test/java/de/planmarshall/core/store/PendingEntryCreationTest.java`, `pm-core/src/test/java/de/planmarshall/core/store/PendingEntryStoreTest.java`, `pm-core/src/test/java/de/planmarshall/core/store/SourceManifestCodecTest.java`, `pm-core/src/main/java/de/planmarshall/core/service/AuditEvent.java`, `pm-core/src/test/java/de/planmarshall/core/service/AuditEventTest.java`, `pm-core/doc/developer/core-store.adoc`, `pm-core/doc/adr/core-store/036-entry-question-file-moved-by-the-store.adoc`, `pm-core/doc/adr/core-store/083-the-store-level-creation-carries-a-plans-sources-alone.adoc`, `pm-core/doc/adr/core-store/084-the-move-carries-the-manifests-files-and-no-other.adoc`.
- OBSERVED (`pm-runtime`): `pm-runtime/src/main/java/de/planmarshall/runtime/start/PendingEntryRecordsSweep.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/start/package-info.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/start/PendingEntryRecordsSweepTest.java`, `pm-runtime/doc/developer/runtime-start.adoc`, `pm-runtime/doc/adr/runtime-start/016-the-sweep-removes-pending-entry-residue-by-evidence.adoc`.
- OBSERVED (`doc`): `doc/specification/store-schemas/12-machine-stores.adoc`, `doc/specification/identifiers/01-general-rules-and-registry.adoc`, `doc/Traceability.adoc`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-171 (shipped, `landings/PLAN-V2-171.md`: the plan this one succeeds), PLAN-V2-167, and the follow-up pull request #573 of its lane (`fix/v2-171-review-minors`, merged or closed first).
- Overlaps with (class level): PLAN-V2-161 (`core-store.adoc`, `store-schemas`), PLAN-V2-133 (parked: the epic's ledger copy of the sources, the same `PendingEntryCreation`), PLAN-V2-125 and 126 (test-conformance of the `store` and `start` test trees), PLAN-V2-168 (`SourceManifest` and `PendingEntryCreation` named in its surface) and every staged plan by guide, ADR and Traceability file: the gate decides at emit; one shared file does not block.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-174-pending-entry-second-residue-closeout.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Carried Open Items

92. **The second residue of the landing of PLAN-V2-171, the sweep's count and report, the refusal tests, the case fold and the stale statements** (`landings/PLAN-V2-171.md`, carried 2026-11-23; merged as #572 `57f9d33f`; read by the analysis leaf at `f1db02cd`; no blocker, no major; the lane's own 12 minors are its follow-up pull request #573, `fix/v2-171-review-minors`). Deliverables 1 to 4 carry each point: (a) the sweep (the discard count, the report on a throw, the rethrow test); (b) the refusal tests (a fixture the old code refused, type-only assertions, the wrong cause in a message, a named FIFO); (c) the case fold that was not put to the operator and the stored manifest; (d) the `removed_at` guard and the stale statements.

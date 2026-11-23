# PLAN-V2-178: The third residue of the pending entry's closeout: the sweep's report when the removal throws after the commit, one message per manifest collision rule, and the special-file guard's proof

epic: pm-mcp-core-m2-m4-v2
workstream: WS-03
origin: the three-layer analysis of the landing of PLAN-V2-174 (`landings/PLAN-V2-174.md`, 2026-11-23, #578, merge `9f9fe51b`) and the operator's rule that leftovers go into a staged plan (no staged plan owns `PendingEntryRecordsSweep`, `SourceManifest` or `PendingEntryCreation`: PLAN-V2-125 and 126 own the test conformance of their trees, PLAN-V2-133 is parked).
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-178-pending-entry-third-residue-closeout.md` and is queued as one row file, `queue/PLAN-V2-178.json`,
> in the epic ledger (lane `runbook`: the operator runs it with the runbook lane). The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R05, R06, R08, R10, R11, R13, R14, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R07: nothing is ask-at-refine; R09: no confinement; R12: no message is added; R10 because `store-schemas/12` is read by the schema parity tests: its schema blocks, kind tables and anchors stay byte-stable; R13: the pending-entry record keeps format version 1 (ADR `core-store/085`); R14 because the plan touches the pending-entry stores and the sweep's lock; R17 is lifted, the links check is not a requirement, operator 2026-11-23.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-store.adoc` and `pm-runtime/doc/developer/runtime-start.adoc` (exist; the corrected statements); ADR directories: `pm-core/doc/adr/core-store/` (next free number after 086 at `origin/main`, R11) and `pm-runtime/doc/adr/runtime-start/` (next after 017); an ADR is written only for a decision the outline cannot defer; layout: workstream WS-03.
- R12 — none (no message).

**Read the skills first (R20).** `java-core`, `java-null-safety`, `javadoc`, `junit-core` and `cui-testing` of `/home/oliver/git/plan-marshall/marketplace/bundles/{pm-dev-java,pm-dev-java-cui}/skills/`; say in the first progress note which you read. R20 applies to every test line this plan touches.

## Objective

PLAN-V2-174 fixed the sweep's count and made the report keep the unreadable list when the removal throws, but the statement it wrote for the throw ("nothing was committed, so the next run re-judges it") is false after the commit; its new assertions on the manifest's collision message pass for every collision; and its special-file test proves the reason, not that the file is never opened. This plan closes those points and no others.

## Deliverables

All file and line references come from the landing analysis at `9f9fe51b` and are re-read at the head of `main` before editing; drop what a merge since then settled and name the commit.

1. **The sweep's report and its statement when the removal throws (carried item 97 a, d; tests first).** `PendingEntryRecordsSweep` (`pm-runtime/src/main/java/de/planmarshall/runtime/start/PendingEntryRecordsSweep.java:99-103,113-120,157,160-169`): `transaction.commit()` runs at `:157` before the discard loop, which catches `IllegalStateException` and `UncheckedIOException` only. A fault after the commit (a commit that fails after its journal is durable, or any other exception from `store.discard`) reaches the catch and drops `removed` and `kept`: the committed removals and their `pending_entry_removed` records are unreported, and the Javadoc, ADR `runtime-start/016` and the guide say "nothing was committed". Make the statement true or the code match it: either carry what was committed into the failure (the removed ids and the kept list as they stand) or state exactly which faults leave the run uncommitted and which do not; a test for a throw AFTER the commit (the failing run by log id: the `journalToRecoverFails` fixture fails in `ScopeTransaction.open`, before anything is staged) and one for a throw after staging and before the commit (the rollback of the run's removals; PLAN-V2-174 split this off as optional D1(c) for want of a seam: say which seam the test uses or why none exists).
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
2. **One message per manifest collision rule (carried item 97 b).** `SourceManifest.requireDistinctPath` (`pm-core/src/main/java/de/planmarshall/core/store/SourceManifest.java:92-105`) throws one fixed text for every collision (stored twice, case, another normalisation form, nesting): the assertions `SourceManifestCodecTest` gained (`caseVariantPaths`, `unicodeCaseVariantPaths`, `normalisationVariantPaths`, `casedNormalisationVariantPaths`) match substrings every collision carries, so they cannot tell the rules apart. Give each rule its own message (the operator's answer of 2026-11-23 keeps the fold as it is: no change of what is refused) and assert the rule in each test; the stored-manifest read (`SourceManifestCodec.read`, `format_version` stays 1) carries the same text. Show by a failing run that a test fails when its rule's message is swapped for another's.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
3. **The special-file guard's proof and the statements (carried item 97 c, e).** `PendingEntryCreation.java:223-227` checks `isRegularFile(file, NOFOLLOW_LINKS)` and then opens: the two are not atomic, so a swap for a FIFO between them blocks under the workspace lock, and plain Java has no non-blocking open: state the bound in the guide and ADR `core-store/084` (or close it, if the outline finds a way inside the platform API). `refusesASpecialFileTheManifestNames` binds a Unix-domain socket, which `open(2)` refuses at once: use a FIFO where the platform has one (`mkfifo` is not plain Java: say how the fixture is made, or why the socket is the only one possible) so the test shows the file is never opened; the `@throws` of `move` (`:104-108`) names the new refusal; `store-schemas/12-machine-stores.adoc:311` says which record a refused discard keeps (the entry's record is deleted by the commit; the audit record is what was handed over).
   - Skills: pm-dev-java:java-core, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-documents:ref-asciidoc
   - Persona: implementer

**Done when:** a test for each of deliverables 1 and 2 fails on the code before it (a failing run or a mutation, by log id); the statements of deliverables 1 and 3 say what the code does; `verify` per module (`pm-core`, `pm-runtime`) and `verify -Ppre-commit` (its rewrites reviewed and committed) are green, targeted `-Dtest` runs where a full verify exceeds the host's budget; the open Sonar issues of the pull request are fixed or dismissed with a reason.

## Standing Constraints

- **Read every cited line at the head first.** The lines and counts of a carried item are those of the landing analysis, read on an older head; re-read each at `origin/main` before editing and drop what a merge since then settled (name the commit).
- **Work to the Done-when.** Do not stop to ask the operator which part to continue with.
- **Every carried item gets an outcome** in the landing message and the pull request: adopted into a deliverable, split off to a NAMED existing plan, or dropped, each with its reason.
- **No behaviour change beyond the corrections the items state.** The fold's reach is the operator's answer of 2026-11-23 (keep it): this plan changes messages and statements, not what is refused.
- **Where the project analyses its pull requests with Sonar:** an open Sonar issue is fixed in a pushed commit, or dismissed with a stated reason; the quality gate is green before the pull request is reported ready.
- **No dependency or plugin is added.** No more than 100 files per pull request.
- **Tests that can fail.** A new test fails on the code it guards (a failing run or a mutation, by log id); a test of a refusal asserts the reason, not only the type (CLAUDE.md, R20).
- **Do not repeat the mutation claims of 174 as facts:** they were not re-run by the analysis.

## Claim Labels

All lines read at `origin/main` `9f9fe51b` (the merge of #578) by the analysis leaf; the orchestrator re-read the catch and the `remove` method of the sweep and `requireDistinctPath`. The outline re-reads each before it acts.

- OBSERVED: `transaction.commit()` precedes the discard loop (`PendingEntryRecordsSweep.java:157`); the loop catches `IllegalStateException` and `UncheckedIOException` only; the catch at `:113-120` builds the failure from the exception message and `unreadable`; `requireDistinctPath` throws one text for every collision (`SourceManifest.java:98-103`); `fold()` is unchanged (`:119-124`).
- LEAD (unverified): that `casedNormalisationVariantPaths` hits the second NFC step (the leaf read the code points, no run); that a commit can fail after its journal is durable in a way that reaches the catch (the leaf's reading of `ScopeTransaction`); the lane's mutation log ids.

## Expected Surface

Specific files (R19; the outline adds the files the re-read names):

- OBSERVED (`pm-runtime`): `pm-runtime/src/main/java/de/planmarshall/runtime/start/PendingEntryRecordsSweep.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/start/PendingEntryRecordsSweepTest.java`, `pm-runtime/doc/developer/runtime-start.adoc`, `pm-runtime/doc/adr/runtime-start/016-the-sweep-removes-pending-entry-residue-by-evidence.adoc`.
- OBSERVED (`pm-core`): `pm-core/src/main/java/de/planmarshall/core/store/SourceManifest.java`, `pm-core/src/main/java/de/planmarshall/core/store/SourceManifestCodec.java`, `pm-core/src/main/java/de/planmarshall/core/store/PendingEntryCreation.java`, `pm-core/src/test/java/de/planmarshall/core/store/SourceManifestCodecTest.java`, `pm-core/src/test/java/de/planmarshall/core/store/PendingEntryCreationTest.java`, `pm-core/doc/developer/core-store.adoc`, `pm-core/doc/adr/core-store/084-the-move-carries-the-manifests-files-and-no-other.adoc`.
- OBSERVED (`doc`): `doc/specification/store-schemas/12-machine-stores.adoc`.
- HYPOTHESIS (new): one ADR under `pm-runtime/doc/adr/runtime-start/` (after 017) if deliverable 1 takes a decision the outline cannot defer.

## Dependencies and Sequencing

- Depends on: PLAN-V2-174 (shipped, #578).
- Overlaps with (class level): PLAN-V2-125 and 126 (test conformance of the `store` and `start` test trees: the same test classes), PLAN-V2-161 (`core-store.adoc`, `store-schemas`), PLAN-V2-133 (parked, `PendingEntryCreation`), and every staged plan by guide, ADR and Traceability file: the gate decides at emit; one shared file does not block.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-178-pending-entry-third-residue-closeout.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Carried Open Items

97. **The residue of the landing of PLAN-V2-174, the report when the removal throws after the commit, one message per collision rule, the special-file guard's proof** (`landings/PLAN-V2-174.md`, carried 2026-11-23; merged as #578 `9f9fe51b`; read by the analysis leaf at `9f9fe51b`; no blocker, no major). Deliverables 1 to 3 carry each point: (a, d) the sweep's throw and its tests, (b) the message per rule, (c, e) the guard's proof and the statements; recorded, not fixed: the landing message's mutation count (M5), the `ent-` prefix shadowing, the creation cut between the two commits and the live-plan enumerator (the assembly, ADR 086), the epic's ledger copy of the sources (PLAN-V2-133 D5, parked).

# PLAN-V2-180: The residue of the cut build log's and the pending entry's closeouts: tests that overstate or miss, an unsupported why-clause, and the sweep's and the manifest's statements

epic: pm-mcp-core-m2-m4-v2
workstream: WS-03
origin: the three-layer analyses of the landings of PLAN-V2-177 (`landings/PLAN-V2-177.md`, #585, merge `baa730ec`) and PLAN-V2-178 (`landings/PLAN-V2-178.md`, #588, merge `5777d1c4`), 2026-11-24, and the operator's rule that leftovers go into a staged plan (no staged plan owns the production sources or statements named here: PLAN-V2-125 and 126 own the conformance of the test classes `BuildLogTextTest`, `ProcessTreeTrackerTest` and `SourceManifestCodecTest` and are at or over their size).
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-180-cut-log-and-pending-entry-residue-closeout.md` and is queued as one row file, `queue/PLAN-V2-180.json`,
> in the epic ledger (lane `runbook`: the operator runs it with the runbook lane). The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R05, R06, R08, R10, R11, R13, R14, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R07: nothing is ask-at-refine; R09: no confinement; R12: no message is added; R10 because `store-schemas/12` is read by the schema parity tests: its schema blocks and anchors stay byte-stable; R13: no stored format changes; R14 because the plan touches the pending-entry sweep and its lock; R17 is lifted, the links check is not a requirement, operator 2026-11-23.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-build.adoc`, `pm-core/doc/developer/core-store.adoc`, `pm-runtime/doc/developer/runtime-job.adoc` and `pm-runtime/doc/developer/runtime-start.adoc` (exist; the corrected statements); ADR directories: `pm-runtime/doc/adr/runtime-start/`, `pm-runtime/doc/adr/runtime-job/` and `pm-core/doc/adr/core-store/` (corrections edit the existing ADR; a new ADR only for a decision the outline cannot defer, the next free number at `origin/main`, R11); layout: workstream WS-03.
- R12 — none (no message).

**Read the skills first (R20).** `java-core`, `java-lombok`, `java-null-safety`, `javadoc`, `junit-core` and `cui-testing` of `/home/oliver/git/plan-marshall/marketplace/bundles/{pm-dev-java,pm-dev-java-cui}/skills/`; say in the first progress note which you read. R20 applies to every test line this plan touches.

## Objective

PLAN-V2-177 and 178 closed the cut build log's and the pending entry's second residue. Their analyses found no blocker and no major; they found tests whose names say more than the fixture does, one test of an overflow that no test covers, one why-clause the code does not support, a sweep statement that covers fewer faults than it says, and a test name and an ADR sentence that hide that a pair collides at the final normalisation. This plan closes those points and no others.

## Deliverables

All file and line references come from the landing analyses at `baa730ec` and `5777d1c4` and are re-read at the head of `main` before editing; drop what a merge since then settled and name the commit.

1. **The cut build log's tests (carried item 100 a; tests first).** (a) `BuildLogTextTest.cutMavenLogKeepsAnotherExecutionsFailureWhenTheSummarySideWins` (`pm-core/src/test/java/de/planmarshall/core/build/BuildLogTextTest.java:618`): execution A has a per-class count of 2 and a summary of 2, so the sides tie and the summary side does not win strictly: make the fixture's summary side exceed the per-class side, or rename the test to what it shows. (b) `MavenOutputParser.java:433` (`saturatedAdd(parse(Failures), parse(Errors))`) has no test of an overflowing sum: add one where Failures plus Errors pass the long range, and show by a failing run that a mutant back to `+` fails it. (c) `BuildLogTextTest.java:566-587`, the "existing 19-digit" test uses exactly `Long.MAX_VALUE` per execution and never reaches the `NumberFormatException` branch; add a 19-digit value above `9223372036854775807` and say what the old test pins. (d) `ProcessTreeTrackerTest.java:296-322`: the positive control fails when the primitive predicate is forced false but does not discriminate the two clauses; show each clause by its own mutation, or say in the test that the three fixtures cover both clauses structurally and name which fixture reaches which.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
2. **The unsupported why-clause (carried item 100 b).** `BuildOutputParser.java:109-110` and `runtime-job.adoc:291` say the cut fact reaches the Maven parser alone "because its count scan can only ever answer a whole-run total"; Jest, TAP, Gradle and pyproject also sum summaries (the spec of PLAN-V2-177 D3 noted it). State the reason the code and the decision give (the Maven parser is the one that takes the cut fact; the others derive their population from the tail, and ADR `core-build/011` rejected a tail-only figure no consumer branches on), or drop the clause. The decision itself stays: keep the carried population.
   - Skills: pm-dev-java:javadoc, pm-documents:ref-asciidoc
   - Persona: documenter
3. **The sweep's statements and tests (carried item 100 c).** (a) `PendingEntryRecordsSweepTest.refusedAuditHandOverReportsTheCommittedRemoval` reaches the OWED arm (a contract-violating sink throws inside `commit()`): its name and the sentence "refused hand-over" say more; rename it and say in the class Javadoc and ADR `runtime-start/016` which arm each test reaches; the UNDECIDED and COMMITTED arms stay untested (no seam in this repository: the lane says so) and the statements already say so, so say it in the tests' class Javadoc too. (b) `PendingEntryRecordsSweep.java:231-232,281`, ADR 016 and `runtime-start.adoc:123-135`: "a fault of the removal drops none of them" covers the five listed types; any other runtime exception from `commit()` or `discard` escapes `run()` unreported: state the scope (the types), or catch the rest at the same boundary (a behaviour change: only with a test and the outline's say). (c) `PendingEntryRecordsSweep.java:279-280`: the UNDECIDED text also covers a `commit()` refusal before the journal write and a `stage()` `IOException`, where nothing was committed: say "may have" for what it is, or split the arm; keep the emphasis marks out of Javadoc and AsciiDoc (cosmetic).
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-documents:ref-asciidoc
   - Persona: implementer
4. **The manifest's test name, the ADR sentence and the schema sentence (carried item 100 d).** (a) `SourceManifestCodecTest.casedNormalisationVariantPaths` (`:331-358`): by code point, `upper(NFC(U+0390))` is U+0399 U+0308 U+0301 and `upper(NFC(U+0399 U+0308 U+0301))` is U+03AA U+0301, equal only after the final NFC: the pair is the case rule and it is the second NFC step that makes it collide (the analysis of PLAN-V2-178 re-read this by script; the lane of 178 had refuted it by mistake). Restore the purpose in the test's name and in the ADR `core-store/084` sentence, and say in its comment which step makes the pair equal. (b) `doc/specification/store-schemas/12-machine-stores.adoc:311`: "the removal transaction deletes the entry's record and not the discard" is muddled; for a swept residue the entry's record is already absent and the sweep's transaction owes the audit record, and the discard refuses when the record is present again: say that; its schema blocks and anchors stay byte-stable (R10).
   - Skills: pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-documents:ref-asciidoc
   - Persona: implementer

**Done when:** a test for each of deliverables 1(b) and 1(c) fails on the code or the mutant before it (a failing run or a mutation, by log id); the statements of deliverables 2, 3 and 4 say what the code does; `verify` per module (`pm-core`, `pm-runtime`) and `verify -Ppre-commit` (its rewrites reviewed and committed; the rewrites of `FreshnessReconcile` and its test are not this plan's and are not shipped) are green, targeted `-Dtest` runs where a full verify exceeds the host's budget; the open Sonar issues of the pull request are fixed or dismissed with a reason.

## Standing Constraints

- **Read every cited line at the head first.** The lines and counts of a carried item are those of the landing analyses, read on older heads; re-read each at `origin/main` before editing and drop what a merge since then settled (name the commit).
- **Work to the Done-when.** Do not stop to ask the operator which part to continue with.
- **Every carried item gets an outcome** in the landing message and the pull request: adopted into a deliverable, split off to a NAMED existing plan, or dropped, each with its reason.
- **No behaviour change beyond the corrections the items state.** The fold's reach stays the operator's answer of 2026-11-23: this plan changes names, tests and statements, not what is refused. Deliverable 3(b)'s catch is the one candidate for a behaviour change and needs its own test.
- **The `JobLog` Javadoc is not here:** it is owed to PLAN-V2-162 (#567); do not edit `JobLog.java`.
- **Where the project analyses its pull requests with Sonar:** an open Sonar issue is fixed in a pushed commit, or dismissed with a stated reason; the quality gate is green before the pull request is reported ready.
- **No dependency or plugin is added.** No more than 100 files per pull request.
- **Tests that can fail.** A new test fails on the code it guards (a failing run or a mutation, by log id); a test of a refusal asserts the reason, not only the type (CLAUDE.md, R20).
- **Do not repeat the mutation claims of 177 and 178 as facts:** the analyses did not re-run them.

## Claim Labels

All lines read at `origin/main` `baa730ec` (the merge of #585) and `5777d1c4` (the merge of #588) by the analysis leaves; the orchestrator re-read `BuildOutputParser.java:105-120`, `BuildLogTextTest.java:566-575`, `MavenOutputParser.java:612-620` and the code points of the cased-normalisation pair by script. The outline re-reads each before it acts.

- OBSERVED: the four points above as the analyses read them; the cut path saturates and still does not read the `unrepresentable` flag (`result(cut)` returns `Unmeasured`).
- LEAD (unverified): that a mutant back to `+` at `MavenOutputParser.java:433` survives (the leaf read the code, no run); that `KillBindingDependentControl` resolves its interface through ArchUnit on the classpath; that any runtime exception other than the five listed escapes `run()` today.

## Expected Surface

Specific files (R19; the outline adds the files the re-read names):

- OBSERVED (`pm-core`): `pm-core/src/test/java/de/planmarshall/core/build/BuildLogTextTest.java`, `pm-core/src/main/java/de/planmarshall/core/build/MavenOutputParser.java`, `pm-core/src/test/java/de/planmarshall/core/store/SourceManifestCodecTest.java`, `pm-core/doc/adr/core-store/084-the-move-carries-the-manifests-files-and-no-other.adoc`.
- OBSERVED (`pm-runtime`): `pm-runtime/src/main/java/de/planmarshall/runtime/job/BuildOutputParser.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/job/ProcessTreeTrackerTest.java`, `pm-runtime/doc/developer/runtime-job.adoc`, `pm-runtime/src/main/java/de/planmarshall/runtime/start/PendingEntryRecordsSweep.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/start/PendingEntryRecordsSweepTest.java`, `pm-runtime/doc/developer/runtime-start.adoc`, `pm-runtime/doc/adr/runtime-start/016-the-sweep-removes-pending-entry-residue-by-evidence.adoc`.
- OBSERVED (`doc`): `doc/specification/store-schemas/12-machine-stores.adoc`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-177 (shipped, #585) and PLAN-V2-178 (shipped, #588).
- Overlaps with (class level): PLAN-V2-125 and 126 (test conformance of the same test classes `BuildLogTextTest`, `ProcessTreeTrackerTest`, `SourceManifestCodecTest`, `PendingEntryRecordsSweepTest`: they run after this plan or rebase over it), PLAN-V2-162 (`JobLog`, `runtime-job.adoc`: PR #567 open), PLAN-V2-179 (`core-store.adoc`), PLAN-V2-161 (`store-schemas/12`), PLAN-V2-149 (`store-schemas/12`), and every staged plan by guide, ADR and Traceability file: the gate decides at emit; one shared file does not block.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-180-cut-log-and-pending-entry-residue-closeout.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Carried Open Items

100. **The residue of the landings of PLAN-V2-177 and PLAN-V2-178** (`landings/PLAN-V2-177.md` and `landings/PLAN-V2-178.md`, carried 2026-11-24; merged as #585 `baa730ec` and #588 `5777d1c4`; read by the analysis leaves; no blocker, no major): deliverables 1 to 4 carry each point: (a) the cut-log tests (177 M1, M2, M3, M5), (b) the why-clause (177 M4), (c) the sweep's tests and statements (178 M1, M2, M6), (d) the manifest's test name, the ADR sentence and the schema sentence (178 M3, M5); recorded, not fixed: the lane's mutation counts (178 M4), `-T` interleaving and the count key's cost (ADR `core-build/013`), the double `pending_entry_removed` record after a recovery, the `ent-` prefix shadowing, the creation cut between the two commits and the live-plan enumerator (the assembly, ADR 086); owed to PLAN-V2-162: the `JobLog` Javadoc (177 M6).

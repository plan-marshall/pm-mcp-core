# PLAN-V2-181: The residue of the cut build log's and the sweep's third closeout: a saturation mutant the lane thought unkillable, a fault boundary that rebuilds its reach, and statements that stop short

epic: pm-mcp-core-m2-m4-v2
workstream: WS-03
origin: the three-layer analysis of the landing of PLAN-V2-180 (`landings/PLAN-V2-180.md`, #593, merge `b81f0544`), 2026-11-24, and the operator's rule that leftovers go into a staged plan (no staged plan owns the sources named here: PLAN-V2-125 and 126 own only the test conformance of the same classes and are at or over their size).
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-181-cut-log-and-sweep-third-residue-closeout.md` and is queued as one row file, `queue/PLAN-V2-181.json`,
> in the epic ledger (lane `runbook`: the operator runs it with the runbook lane). The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R05, R06, R08, R10, R11, R13, R14, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R07: nothing is ask-at-refine; R09: no confinement; R12: no message is added; R13: no stored format changes; R14 because the plan touches the pending-entry sweep and its lock; R17 is lifted, the links check is not a requirement, operator 2026-11-23.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-runtime/doc/developer/runtime-start.adoc` and `pm-runtime/doc/developer/runtime-job.adoc` (exist; corrected statements only); ADR directories: `pm-runtime/doc/adr/runtime-start/`, `pm-core/doc/adr/core-build/` and `pm-core/doc/adr/core-store/` (corrections edit the existing ADR; a new ADR only for a decision the outline cannot defer, the next free number at `origin/main`, R11); layout: workstream WS-03.
- R12 — none (no message is added; the sweep's report text is not changed by this plan).

**Read the skills first (R20).** `java-core`, `java-lombok`, `java-null-safety`, `javadoc`, `junit-core` and `cui-testing` of `/home/oliver/git/plan-marshall/marketplace/bundles/{pm-dev-java,pm-dev-java-cui}/skills/`; say in the first progress note which you read. R20 applies to every test line this plan touches.

## Objective

PLAN-V2-180 closed the cut build log's and the pending entry's third residue. Its analysis found no blocker. It found one deliverable whose Done-when the lane reported as unmeetable although a reading shows a fixture that separates the mutant, one mechanism stated wrongly in a test comment, one test whose fixture does not pin the side that wins, one fault boundary that discards the fault it had already built (CodeRabbit's second finding, carried by the lane as a staged follow-up), one test that leaves a directory unwritable on an uncaught throw, and two statements that stop short. This plan closes those points and no others.

## Deliverables

All file and line references come from the landing analysis at `b81f0544` and are re-read at the head of `main` before editing; drop what a merge since then settled and name the commit.

1. **The saturation test that separates the mutant (carried item 101 a; tests first, mutation after).** (a) `BuildLogTextTest` case `line-sum` of `cutMavenLogSaturatesAnOverflowingFailingCount` (`BuildLogTextTest.java:615-617`) passes on a mutant of `MavenOutputParser.java:433` that turns `saturatedAdd(parse(Failures), parse(Errors))` back into `+`. The lane concluded that no fixture can kill that mutant. By reading, one can: with the mutant, Failures `9223372036854775807` plus Errors `1` wraps to `Long.MIN_VALUE`; `filedFailures()` (`:546-555`) stores the first value of an execution unsaturated (`Map.merge` on an absent key never calls `saturatedAdd`), so `hasFiledFailingCount()` (`:611-613`, `anyMatch(filed > 0)`) turns false where the original answers true; the unfiled figure hides it (`Math.max(0, MAX - MIN)` wraps to `-1` and becomes `0`, `:506`). A fixture: execution A an error-level line `Tests run: 2, Failures: 9223372036854775807, Errors: 1, Skipped: 0`; execution B a warning-level line `Tests run: 1, Failures: 1, Errors: 0, Skipped: 0`; the original reports `1 further failing test`, the mutant `1 failing test` (selection at `MavenOutputParser.java:266`). Add that case (it rests on the collector filing an error-level count line, which the existing two-execution tests already assume; verify it), and show by a failing run that the mutant fails it, by log id. If a run shows the mutant survives after all, say so in the test's comment with the run's log id and drop the claim. (b) Correct the comment (`BuildLogTextTest.java:597-599`) and the statement behind it: `filedFailures:551` saturates only on a second line of the same execution; the first value is stored raw, and what hides a wrapped value in the unfiled figure is the `int` wrap in `Math.max(0, MAX - MIN)` at `:506`. (c) `BuildLogTextTest.java:658-666` (the strict summary win): the fixture shows a strict win, but a mutant that takes only the per-class side, or the minimum, still answers `1 further failing test` because the filed figure is 3 either way. Either make the fixture separate the maximum from the per-class side, or say in the test's comment that it guards the subtraction and not the choice of side.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
2. **The sweep's fault boundary and its test (carried item 101 b; tests first).** (a) `PendingEntryRecordsSweep.remove` (`:156-166`) and `RemovalState.reach` (`:238-246`): `attempt(...)` already returns a `Removal` that carries its own fault and its reach (computed at `:196`). When the `try`-with-resources close throws (a fault of the lock's own release), the catch rebuilds the fault from `state.fault(e.getMessage(), workspace)`, which re-reads `Files.exists(journalFile)` after the release was attempted. Another writer's journal can then turn UNDECIDED into OWED, the report would say `completed by the recovery of the workspace` wrongly, and the first fault's message is replaced by the release fault's. Keep the reach and the message of the first fault when `attempt` already returned one, and report the release fault additionally; add a test (a lock whose release throws, with a journal written by another writer between the attempt and the release) that fails on the code before. This is a behaviour change of a narrow path: it needs this test and no more. (b) `PendingEntryRecordsSweepTest.aCommitThatCannotWriteItsJournalLeavesTheReachUndecided` (`:633-663`): restore the directory's permission in a `finally` or an `@AfterEach` so that an uncaught throw or a skipped assumption after the `chmod` cannot leave the directory unwritable; assert the cause of the refusal, not only the report text; say in the test's Javadoc that on a root runner it skips and what then covers the undecided report text.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
3. **The two statements that stop short (carried item 101 c).** (a) `BuildOutputParser.java:109-111`: "the one parser this adapter passes the fact to and the one that answers it with an absence" is nearly circular. ADR `core-build/011:90-91` gives the reason (a parser whose figure a cut can falsify takes the flag when a plan touches it): cite it, state the reason in a sentence that the code can be checked against, or drop the clause. (b) `SourceManifestCodecTest.java:333-336` and ADR `core-store/084:35-39` describe `upper(NFC(..))` and "the case mapping"; the fold is NFC, upper, lower, NFC (`SourceManifest.java:150-151`): name the `lower()` step and say that both forms reach U+0390 after the final NFC (the equality holds; the statement is incomplete). Statements only; no behaviour change.
   - Skills: pm-dev-java:javadoc, pm-documents:ref-asciidoc, pm-dev-java:junit-core
   - Persona: documenter

**Done when:** the test of deliverable 1(a) fails on the mutant (a failing run or a mutation, by log id) or the comment records, by log id, why it cannot; the test of deliverable 2(a) fails on the code before it; the statements of deliverables 1(b), 1(c), 2(b) and 3 say what the code does; `verify` per module (`pm-core`, `pm-runtime`) and `verify -Ppre-commit` (its rewrites reviewed and committed; the rewrites of `FreshnessReconcile` and its test are not this plan's and are not shipped) are green, targeted `-Dtest` runs where a full verify exceeds the host's budget; the open Sonar issues of the pull request are fixed or dismissed with a reason.

## Standing Constraints

- **Read every cited line at the head first.** The lines of a carried item are those of the landing analysis, read at `b81f0544`; re-read each at `origin/main` before editing and drop what a merge since then settled (name the commit).
- **Work to the Done-when.** Do not stop to ask the operator which part to continue with.
- **Every carried item gets an outcome** in the landing message and the pull request: adopted into a deliverable, split off to a NAMED existing plan, or dropped, each with its reason.
- **Behaviour change only in deliverable 2(a),** with its own failing-first test. The report text of the sweep does not change.
- **The `JobLog` Javadoc is not here:** it is owed to PLAN-V2-162 (#567); do not edit `JobLog.java`.
- **Where the project analyses its pull requests with Sonar:** an open Sonar issue is fixed in a pushed commit, or dismissed with a stated reason; the quality gate is green before the pull request is reported ready.
- **No dependency or plugin is added.** No more than 100 files per pull request.
- **Tests that can fail.** A new test fails on the code it guards (a failing run or a mutation, by log id); a test of a refusal asserts the reason, not only the type (CLAUDE.md, R20).
- **Do not repeat the lane's mutation claims of PLAN-V2-180 as facts:** the analysis did not re-run them; the claim that no fixture kills the mutant at `MavenOutputParser.java:433` is the one this plan tests.

## Claim Labels

All lines read at `origin/main` `b81f0544` (the merge of #593) by the analysis leaf; the orchestrator re-read `MavenOutputParser.java:428-436,500-510,528-556,608-615` (the first value of `filedFailures` is stored by `Map.merge` without `saturatedAdd`, `hasFiledFailingCount` is `anyMatch(filed > 0)`) and `PendingEntryRecordsSweep.java:150-170,236-248` (the catch rebuilds the fault from `state.fault`, whose `reach` re-reads `journalFile`). The outline re-reads each before it acts.

- OBSERVED: the code at the lines above; the lane's own statement that the mutant at `:433` survived (its log id is in its landing message and is `stated_not_verified`).
- LEAD (unverified): that the fixture of deliverable 1(a) separates the mutant (a reading, no run); that the lock's release can fail with another writer's journal present in the way deliverable 2(a) says (the code reads so; narrow); that a root runner skips the undecided-arm test and the new-code coverage then depends on a non-root runner.

## Expected Surface

Specific files (R19; the outline adds the files the re-read names):

- OBSERVED (`pm-core`): `pm-core/src/test/java/de/planmarshall/core/build/BuildLogTextTest.java`, `pm-core/src/test/java/de/planmarshall/core/store/SourceManifestCodecTest.java`, `pm-core/doc/adr/core-store/084-the-move-carries-the-manifests-files-and-no-other.adoc`.
- OBSERVED (`pm-runtime`): `pm-runtime/src/main/java/de/planmarshall/runtime/job/BuildOutputParser.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/start/PendingEntryRecordsSweep.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/start/PendingEntryRecordsSweepTest.java`, `pm-runtime/doc/developer/runtime-start.adoc`, `pm-runtime/doc/adr/runtime-start/016-the-sweep-removes-pending-entry-residue-by-evidence.adoc`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-180 (shipped, #593).
- Overlaps with (class level): PLAN-V2-125 and 126 (test conformance of `BuildLogTextTest`, `SourceManifestCodecTest`, `PendingEntryRecordsSweepTest`: they run after this plan or rebase over it), PLAN-V2-162 (`runtime-job.adoc`, `JobLog`: PR #567 open), and every staged plan by guide, ADR and Traceability file: the gate decides at emit; one shared file does not block.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-181-cut-log-and-sweep-third-residue-closeout.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Carried Open Items

101. **The residue of the landing of PLAN-V2-180** (`landings/PLAN-V2-180.md`, carried 2026-11-24; merged as #593 `b81f0544`; read by the analysis leaf; no blocker): deliverables 1 to 3 carry each point: (a) the saturation tests (180 M1, M2, M3), (b) the sweep's fault boundary and the undecided-arm test (180 M4 = CodeRabbit's second finding, M5), (c) the two statements (180 M6, M7); recorded, not fixed: the lane's counts of runs and logs (`stated_not_verified`), the report text of the sweep (changed by 180; the assembly matching on the old wording would follow, M8), the items recorded under carried item 100 that stay recorded; owed to PLAN-V2-162: the `JobLog` Javadoc (177 M6).

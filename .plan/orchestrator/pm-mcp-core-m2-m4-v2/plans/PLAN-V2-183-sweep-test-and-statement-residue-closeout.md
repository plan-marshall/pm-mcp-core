# PLAN-V2-183: The residue of the sweep's and the cut log's fourth closeout: a test whose delegate stays open, a literal fixture, a method-wide suppression and two statements that stop short

epic: pm-mcp-core-m2-m4-v2
workstream: WS-03
origin: the three-layer analysis of the landing of PLAN-V2-181 (`landings/PLAN-V2-181.md`, #599, merge `31d97799`), 2026-11-24, and the operator's rule that leftovers go into a staged plan (none of the staged plans names `PendingEntryRecordsSweepTest`, `PendingEntryRecordsSweep` or `BuildOutputParser`: PLAN-V2-125 and 126 own the test conformance of other classes and are at or over their size).
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-183-sweep-test-and-statement-residue-closeout.md` and is queued as one row file, `queue/PLAN-V2-183.json`,
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

- R06 — developer documentation: `pm-runtime/doc/developer/runtime-start.adoc` (exists; a corrected statement only if a deliverable changes one); ADR directory: `pm-runtime/doc/adr/runtime-start/` (corrections edit the existing ADR; a new ADR only for a decision the outline cannot defer, the next free number at `origin/main`, R11); layout: workstream WS-03.
- R12 — none (no message is added).

**Read the skills first (R20).** `java-core`, `java-null-safety`, `javadoc`, `junit-core` and `cui-testing` of `/home/oliver/git/plan-marshall/marketplace/bundles/{pm-dev-java,pm-dev-java-cui}/skills/`; say in the first progress note which you read. R20 applies to every test line this plan touches.

## Objective

PLAN-V2-181 closed the cut build log's and the pending-entry sweep's third residue. Its analysis found no blocker and no major. It found one test whose real lock transaction stays open on the skip path, one free-text literal fixture, one refusal asserted by a substring, one method-wide suppression, one report path that prints a null release message, and one Javadoc clause that still partly restates its own premise. This plan closes those points and no others.

## Deliverables

All file and line references come from the landing analysis at `31d97799` and are re-read at the head of `main` before editing; drop what a merge since then settled and name the commit.

1. **The sweep test's fixtures and its refusal (carried item 103 a; tests first where a behaviour is pinned).** (a) `PendingEntryRecordsSweepTest.aFaultedLockReleaseKeepsTheFirstFaultsReach` (`:693-740`) opens the real delegate transaction (`:697`) before `assumeFalse` (`:718`); on the root runner's skip, or any failure before the mock's `close()` runs, the delegate is never closed. Open it only after the assumption, or close it in the `finally`, so no path leaves a transaction open. (b) `RELEASE_FAULT` (`:104`) is a free-text literal with no contract remark: draw it from the generators of `pm-core:tests` (a letter string), or state in one line that it is the message under test and not a contract, as the standing rule allows only for a wire format, a code or a fixture path. (c) `aCommitThatCannotWriteItsJournalLeavesTheReachUndecided` (`:665`) asserts the refusal's cause as the substring `cannot be written`: assert what names the cause (the journal path the refusal carries, as the new test already does at `:727`), so a different refusal with the same words cannot satisfy it.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: module-tester
2. **The release fault's message and the suppression (carried item 103 b; tests first for the null message).** (a) `PendingEntryRecordsSweep.RemovalState.faultBeside` (`:242-245`) appends `releaseMessage` as a string: a release exception with no message prints `null`, and the cause and the type are dropped. Verify at the outline whether `Fault` or the report accepts a null message and what the first-fault path (`state.fault(e.getMessage(), ...)`, `:174`, `:206`) does with one; name the exception type when the message is null, in the one place that composes it, with a test that fails on the code before (a release that throws an exception without a message). If the outline finds the report cannot be reached with a null message, say so in the decision and drop (a). (b) `@SuppressWarnings("java:S2583")` (`:161`) covers the whole method `remove`, so it also hides any other S2583 hit there: narrow it to the smallest scope the annotation allows (a local-variable annotation covers only the declaration, so the method may be the minimum) and state in the comment why that scope is the minimum, or keep it and say so. Read the open Sonar issues of the pull request at the head: the suppression's reason names the rule and the lines Sonar flagged.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
3. **The statement that stops short (carried item 103 c).** `BuildOutputParser.java:109-111`: the Javadoc cites ADR `core-build/011:90-91`, but its tail clause ("the one parser … that answers it with an absence here") still partly restates the premise it gives the reason for. State the reason once, checkably against the ADR's two lines (the flag is passed to the Maven count scan alone; another parser takes it when a plan touches that parser), and drop the clause that only repeats it. Statement only; no behaviour change.
   - Skills: pm-dev-java:javadoc, pm-dev-java:junit-core
   - Persona: documenter

**Done when:** the test of deliverable 2(a) fails on the code before it (a failing run, by log id), or the decision records why the null message cannot be reached; the tests of deliverable 1 pass on a non-root runner and skip cleanly on a root runner with no open transaction left; the statements of deliverables 2(b) and 3 say what the code does; `verify` per module (`pm-runtime`) and `verify -Ppre-commit` (its rewrites reviewed and committed; the rewrites of any file that is not this plan's are not shipped) are green, targeted `-Dtest` runs where a full verify exceeds the host's budget; the open Sonar issues of the pull request are fixed or dismissed with a reason.

## Standing Constraints

- **Read every cited line at the head first.** The lines of a carried item are those of the landing analysis, read at `31d97799`; re-read each at `origin/main` before editing and drop what a merge since then settled (name the commit).
- **Work to the Done-when.** Do not stop to ask the operator which part to continue with.
- **Every carried item gets an outcome** in the landing message and the pull request: adopted into a deliverable, split off to a NAMED existing plan, or dropped, each with its reason.
- **Behaviour change only in deliverable 2(a),** with its own failing-first test, and only the message composed on the release-fault path; the report's other arms and their wording do not change.
- **The `JobLog` Javadoc is not here:** it is owed to PLAN-V2-162 (#567); do not edit `JobLog.java`.
- **Where the project analyses its pull requests with Sonar:** an open Sonar issue is fixed in a pushed commit, or dismissed with a stated reason; the quality gate is green before the pull request is reported ready.
- **No dependency or plugin is added.** No more than 100 files per pull request.
- **Tests that can fail.** A new test fails on the code it guards (a failing run or a mutation, by log id); a test of a refusal asserts the reason, not only the type (CLAUDE.md, R20).

## Claim Labels

All lines read at `origin/main` `31d97799` (the merge of #599) by the analysis leaf; the orchestrator re-read `PendingEntryRecordsSweep.java:159-176,242-245` and `PendingEntryRecordsSweepTest.java:641-740` at that head. The outline re-reads each before it acts.

- OBSERVED: the real delegate transaction is opened at `PendingEntryRecordsSweepTest.java:697`, before the `assumeFalse` at `:718`, and closed only through the mock's `close()` answer — read at `PendingEntryRecordsSweepTest` § `aFaultedLockReleaseKeepsTheFirstFaultsReach`
- OBSERVED: `RELEASE_FAULT` is a free-text literal (`PendingEntryRecordsSweepTest.java:104`) and `@SuppressWarnings("java:S2583")` sits on the whole method (`PendingEntryRecordsSweep.java:161`) — read at those lines
- OBSERVED: `faultBeside` appends `releaseMessage` by string concatenation (`PendingEntryRecordsSweep.java:243`), so a null message prints `null`
- OBSERVED: the undecided-arm test asserts the cause by the substring `cannot be written` (`PendingEntryRecordsSweepTest.java:665`)
- HYPOTHESIS: a release or first-fault exception with a null message can reach the report — confirm/refute at `PendingEntryRecordsSweep` § `remove`, `RemovalState.fault`, and the `Fault` record's constructor (verify-at-outline)
- HYPOTHESIS: a narrower scope than the method is not available for the S2583 suppression — confirm/refute at `PendingEntryRecordsSweep.java:161-176` (verify-at-outline)

## Expected Surface

Specific files (R19; the outline adds the files the re-read names):

- OBSERVED (`pm-runtime`): `pm-runtime/src/main/java/de/planmarshall/runtime/start/PendingEntryRecordsSweep.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/start/PendingEntryRecordsSweepTest.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/job/BuildOutputParser.java`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-181 (shipped, #599).
- Overlaps with (class level): PLAN-V2-126 (test conformance of the runtime tests: it runs after this plan or rebases over it), PLAN-V2-162 (`runtime-job.adoc`, `JobLog`: PR #567 open; this plan touches neither) and every staged plan by guide, ADR and Traceability file: the gate decides at emit; one shared file does not block.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-183-sweep-test-and-statement-residue-closeout.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Carried Open Items

103. **The residue of the landing of PLAN-V2-181** (`landings/PLAN-V2-181.md`, carried 2026-11-24; merged as #599 `31d97799`; read by the analysis leaf; no blocker, no major): deliverables 1 to 3 carry each point: (a) the sweep test's open delegate, literal fixture and substring cause (181 L3-1, L3-2, the D2(b) point), (b) the null release message and the method-wide suppression (181 L3-4, L3-3), (c) the Javadoc tail (181 D3(a)); recorded, not fixed: the spec's `int` wrap wording (a shipped spec, no staged spec repeats it), the lane's counts of runs and logs (`stated_not_verified`), the report's added `; the lock's release failed: …` clause on the release-fault path only (an assembly string match on the old wording would not match there), and the runbook-lane `unknown` figures (an open record, owner E27).

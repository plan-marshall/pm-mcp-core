# PLAN-V2-186: The residue of the sweep's fifth closeout: a catch no test reaches with a message-less fault, a parenthetical that is not the rule set, a test that stands apart from its siblings and an ADR scoped looser than its sites

epic: pm-mcp-core-m2-m4-v2
workstream: WS-03
origin: the three-layer analysis of the landing of PLAN-V2-184 (`landings/PLAN-V2-184.md`, #606, merge `5c060a53`), 2026-11-25, and the operator's rule that leftovers go into a staged plan (none of the staged plans names `PendingEntryRecordsSweepTest` or `PendingEntryRecordsSweep`; PLAN-V2-126 names `BuildOutputParser` for its tests only and is running, PLAN-V2-125 and 126 own the test conformance of other classes).
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-186-sweep-run-catch-test-and-statement-residue.md` and is queued as one row file, `queue/PLAN-V2-186.json`,
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

- R06 — developer documentation: `pm-runtime/doc/developer/runtime-start.adoc` (exists; a corrected statement only if a deliverable changes one); ADR directory: `pm-runtime/doc/adr/runtime-start/` (corrections edit the existing ADR `016-the-sweep-removes-pending-entry-residue-by-evidence.adoc`; a new ADR only for a decision the outline cannot defer, the next free number at `origin/main`, R11); layout: workstream WS-03.
- R12 — none (no message is added).

**Read the skills first (R20).** `java-core`, `java-null-safety`, `javadoc`, `junit-core` and `cui-testing` of `/home/oliver/git/plan-marshall/marketplace/bundles/{pm-dev-java,pm-dev-java-cui}/skills/`; say in the first progress note which you read. R20 applies to every test line this plan touches.

## Objective

PLAN-V2-184 closed the sweep's fourth residue: a first fault that printed `null` is now named by its type in one helper. Its analysis found no blocker and no major. It found one catch of that helper that no test reaches, one parenthetical in the `BuildOutputParser` Javadoc that names a rule set the parsers do not share, one new test that stands apart from the release-fault tests it belongs with, one helper Javadoc that says "composes" for a method that composes nothing, and three arms (a blank message, a cause without a message, the kept and unreadable entry texts) the ADR's wording reaches wider than the code. This plan closes those points and no others.

## Deliverables

All file and line references come from the landing analysis at `5c060a53` and are re-read at the head of `main` before editing; drop what a merge since then settled and name the commit.

1. **The `run()` catch is unguarded (carried item 106 a; test first).** `PendingEntryRecordsSweep.run` appends `described(e)` at `:132` (the catch of the five types around `remove`), but no test of `PendingEntryRecordsSweepTest` reaches it with a message-less exception: the five `not swept` assertions all come through `remove`, so reverting `:132` to `e.getMessage()` passes the suite (the lane's mutation reverted `fault()` only). Add the test that reaches the `run()` catch with a message-less exception from the lock open or the candidate scan (whichever the outline finds reaches it without `remove`'s own catch), asserting the type name in the report; it fails on the mutation that reverts `:132` (a mutation run, by log id, with the failure text copied from the surefire report: the log of PLAN-V2-184's failing run, `178c128b…`, holds no expected/actual text). If the outline finds that no input reaches `:132` with a message-less exception, say so in the decision, drop the test and state in the helper's Javadoc that the catch needs no guard.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: module-tester
2. **The test that stands apart, and the helper's wording (carried item 106 b; statements and structure only).** (a) `PendingEntryRecordsSweepTest.aFirstFaultWithoutAMessageIsNamedByItsType` (`:841-843`) has no Javadoc while its release-fault siblings do (`:688-693`, `:738-742`), and it sits after the `guardedLock`, `GuardedLock` and `Release` helpers (`:790-835`): give it a Javadoc and move it beside its siblings; reword the `guardedLock` parameter text "`null` to end the mock" (`:796`) to what it means (the close does nothing). (b) `PendingEntryRecordsSweep.described` Javadoc (`:338`) says "the fault text this method composes": say it returns the message, or the type label when there is none.
   - Skills: pm-dev-java:javadoc, pm-dev-java:junit-core
   - Persona: documenter
3. **The parenthetical that is not the rule set (carried item 106 c; statement only).** `BuildOutputParser.java:114-115` lists the parsers' own Unmeasured rules as "a summary whose parts disagree, or a count too large for a `long`". Gradle's rule is an int-range overflow, a negative or a skipped count above the total (`GradleOutputParser.java:143-146`), pyproject's is "unrepresentable" (`PyprojectOutputParser.java:693-695`), Maven's is an int range: say that each parser owns its rule and name none as the shared one, or name each rule against its source line. Statement only; no behaviour change.
   - Skills: pm-dev-java:javadoc
   - Persona: documenter
4. **The arms the ADR's wording reaches wider than the code (carried item 106 d; decide, then tests first only for a behaviour change).** ADR `runtime-start/016` (`:55-56`) and the guide say a fault with no message is named by its type and never printed as `null`; the kept and unreadable entry texts (`PendingEntryRecordsSweep.java:289`, `:291`, `:399`, `:458`) still use a raw `getMessage()`, a blank message is passed through (the report ends `not swept: ` naming nothing) and a `(null, cause)` exception is named by type with the cause dropped. Decide at the outline, and say it in the statement: (i) scope the ADR and guide wording to a fault, with the reason that the messages in `PendingEntryStore` (`:319`, `:326`, `:391`, `:481`) are non-null and the codec's `UncheckedIOException` at `:458` was not read (read it, and say so); or (ii) route the four texts, a blank message and the cause through `described`, with a failing-first case for each arm that changes. Option (i) is the default: the report's other arms and wording do not change without a case.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core
   - Persona: implementer

**Done when:** the test of deliverable 1 fails on the mutation that reverts `:132` (a mutation run, by log id, with the failure text read in the surefire report), or the decision records why no input reaches the catch with a message-less exception; the statements of deliverables 2, 3 and 4 say what the code does; any behaviour change of deliverable 4 has its own failing-first case; `verify` per module (`pm-runtime`) and `verify -Ppre-commit` (its rewrites reviewed and committed; the rewrites of any file that is not this plan's are not shipped) are green, targeted `-Dtest` runs where a full verify exceeds the host's budget; the open Sonar issues of the pull request are fixed or dismissed with a reason.

## Standing Constraints

- **Read every cited line at the head first.** The lines of a carried item are those of the landing analysis, read at `5c060a53`; re-read each at `origin/main` before editing and drop what a merge since then settled (name the commit).
- **Work to the Done-when.** Do not stop to ask the operator which part to continue with.
- **Every carried item gets an outcome** in the landing message and the pull request: adopted into a deliverable, split off to a NAMED existing plan, or dropped, each with its reason.
- **Behaviour change only in deliverable 4(ii), if the outline chooses it,** with its own failing-first test per arm; the report's other arms and their wording do not change.
- **The `JobLog` Javadoc and `runtime-job.adoc` are not here:** the `JobLog` Javadoc and the false "unfinished TAP plan" example at `runtime-job.adoc:291` (with the Maven-only outcome stated three times there) are owed to PLAN-V2-162 (#567); do not edit `JobLog.java` or `runtime-job.adoc`.
- **Where the project analyses its pull requests with Sonar:** an open Sonar issue is fixed in a pushed commit, or dismissed with a stated reason; the quality gate is green before the pull request is reported ready.
- **No dependency or plugin is added.** No more than 100 files per pull request.
- **Tests that can fail.** A new test fails on the code it guards (a failing run or a mutation, by log id); a test of a refusal asserts the reason, not only the type (CLAUDE.md, R20).
- **Do not repeat the lane's claims of PLAN-V2-184 as facts:** the analysis did not re-run its failing run or its counts; the lane's quoted failure text is not in its log.

## Claim Labels

All lines read at `origin/main` `5c060a53` (the merge of #606) by the analysis leaf; the orchestrator re-read `PendingEntryRecordsSweep.java:128-134,236-254,335-350`, `PendingEntryRecordsSweepTest.java:838-862`, `BuildOutputParser.java:106-124`, `runtime-job.adoc:291` and the log `178c128b…log:10-17`. The outline re-reads each before it acts.

- OBSERVED: `run()` composes its report with `described(e)` at `PendingEntryRecordsSweep.java:132`; the five `not swept` assertions of the test class come through `remove` — read at that line and by the leaf's search of the test class
- OBSERVED: the log of PLAN-V2-184's failing run has 0 hits for "with no message" — read at `178c128b…log`
- OBSERVED: the new test has no Javadoc and follows the helpers (`PendingEntryRecordsSweepTest.java:838-843`); `described`'s Javadoc says "the fault text this method composes" (`PendingEntryRecordsSweep.java:338`) — read at those lines
- OBSERVED: `BuildOutputParser.java:114-115` names "a summary whose parts disagree, or a count too large for a long" as the parsers' own Unmeasured rule — read at those lines
- HYPOTHESIS: the Gradle and pyproject Unmeasured rules differ from that sentence — confirm/refute at `GradleOutputParser.java:143-146` and `PyprojectOutputParser.java:693-695` (verify-at-outline)
- HYPOTHESIS: an input reaches the `run()` catch with a message-less exception without passing `remove`'s own catch — confirm/refute at `PendingEntryRecordsSweep` § `run` (verify-at-outline)
- HYPOTHESIS: the messages the kept and unreadable texts print are non-null (`PendingEntryStore` `:319`, `:326`, `:391`, `:481`; the codec's `UncheckedIOException` at `:458` unread) — confirm/refute (verify-at-outline)

## Expected Surface

Specific files (R19; the outline adds the files the re-read names):

- OBSERVED (`pm-runtime`): `pm-runtime/src/main/java/de/planmarshall/runtime/start/PendingEntryRecordsSweep.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/start/PendingEntryRecordsSweepTest.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/job/BuildOutputParser.java`, `pm-runtime/doc/developer/runtime-start.adoc`, `pm-runtime/doc/adr/runtime-start/016-the-sweep-removes-pending-entry-residue-by-evidence.adoc`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-184 (shipped, #606).
- Overlaps with (class level): PLAN-V2-126 (test conformance of the runtime tests: it names `BuildOutputParser` for its tests; it runs after this plan or rebases over it), PLAN-V2-162 (`runtime-job.adoc`, `JobLog`: PR #567 open; this plan touches neither) and every staged plan by guide, ADR and Traceability file: the gate decides at emit; one shared file does not block.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-186-sweep-run-catch-test-and-statement-residue.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Carried Open Items

106. **The residue of the landing of PLAN-V2-184** (`landings/PLAN-V2-184.md`, carried 2026-11-25; merged as #606 `5c060a53`; read by the analysis leaf; no blocker, no major): deliverables 1 to 4 carry each point: (a) the unguarded `run()` catch and the failing-run evidence (184 L3-3), (b) the test's place and the helper wording (184 L3-4, L2-1), (c) the parenthetical (184 L2-2), (d) the arms the ADR reaches wider than the code (184 L2-3, L3-1, L3-2); recorded, not fixed: the anonymous-class `getSimpleName()` corner (184 L2-4, theoretical), the lane's counts of runs and logs (`stated_not_verified`), the runbook-lane `unknown` figures (an open record, owner E27), and the `runtime-job.adoc:291` example owed to PLAN-V2-162.

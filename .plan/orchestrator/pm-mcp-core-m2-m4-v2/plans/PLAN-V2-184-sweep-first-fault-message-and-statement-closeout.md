# PLAN-V2-184: The residue of the sweep's fourth closeout: a first fault that still prints null, a Javadoc that says it never does, a weak assertion, a copied mock setup and a statement said three times

epic: pm-mcp-core-m2-m4-v2
workstream: WS-03
origin: the three-layer analysis of the landing of PLAN-V2-183 (`landings/PLAN-V2-183.md`, #603, merge `e935ab2f`), 2026-11-24, and the operator's rule that leftovers go into a staged plan (none of the staged plans names `PendingEntryRecordsSweepTest`, `PendingEntryRecordsSweep` or `BuildOutputParser`: PLAN-V2-125 and 126 own the test conformance of other classes and are at or over their size).
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-184-sweep-first-fault-message-and-statement-closeout.md` and is queued as one row file, `queue/PLAN-V2-184.json`,
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

PLAN-V2-183 closed the sweep test's and the cut log's fourth residue. Its analysis found no blocker and no major. It found one report path that still prints a null message, one Javadoc that says the report never does, one assertion that can fail on a generated name, one copied mock setup and one Javadoc that states the same outcome three times. This plan closes those points and no others.

## Deliverables

All file and line references come from the landing analysis at `e935ab2f` and are re-read at the head of `main` before editing; drop what a merge since then settled and name the commit.

1. **The first fault's message (carried item 104 a; tests first).** `PendingEntryRecordsSweep.attempt` and `remove` pass `e.getMessage()` into `state.fault(...)` (`:179`, `:211`) and `run()` appends `e.getMessage()` to the report (`:132`): a message-less `IllegalStateException`, `UncheckedIOException`, `InternalFaultException` or `StoreRefusal` from `acquire`, the commit or the lock open prints `pending entry directories not swept: null`, and a null `first.message()` in `faultBeside` (`:249-254`) prints `null; the lock's release failed: …`. Name the exception type when the message is null, in the ONE place that composes it (the helper `faultBeside` already uses for the release exception, or one it shares), with a test that fails on the code before (a first fault that throws an exception without a message, by log id). Verify at the outline whether `StoreRefusal` or `InternalFaultException` can carry a null message at all (their constructors), and drop the arm that cannot, with the reason in the decision. The behaviour change is only the message composed for a null message; every other arm and wording of the report stays.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
2. **The Javadoc that overclaims, and the statements around it (carried item 104 b).** The Javadoc of `RemovalState.faultBeside` (`:243-247`) says the release fault is named "so that the report never prints `null`": say what the code does after deliverable 1 (the report names an exception by its type when it carries no message, on the first fault and on the release fault), or scope the statement to the arm it describes if deliverable 1 drops an arm. Read the statements of `runtime-start.adoc` and ADR `runtime-start/016` that the previous plan amended (they are scoped to the release fault) and correct them only where deliverable 1 changes what they say.
   - Skills: pm-dev-java:javadoc, pm-dev-java:java-core
   - Persona: documenter
3. **The test's assertion and its copied setup (carried item 104 c).** (a) `PendingEntryRecordsSweepTest.aReleaseFaultWithoutAMessageIsNamedByItsType` (`:752-790`) asserts `assertFalse(reason.contains("null"))` (`:787`): the reason embeds the generated project id (3 to 12 letters), so the letters `null` in it fail the test at about one run in 100,000. Assert what names the fault instead (the whole release part of the report, or `endsWith`), so no generated name can satisfy or fail it. (b) Lines `:766-779` repeat `:704-720` of `aFaultedLockReleaseKeepsTheFirstFaultsReach` (the guarded `LockTransaction` mock and the `LockManager` mock, about 14 lines): extract one private factory the two tests and deliverable 1's test share, with no new hand-written fake. (c) The test class Javadoc names the tests of the release fault (`:90-91`): add the test of deliverable 1.
   - Skills: pm-dev-java:java-core, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: module-tester
4. **The statement said three times (carried item 104 d).** `BuildOutputParser.java:109-126`: the Javadoc states that only a cut Maven log reports `Unmeasured` in three places (`:109-111`, `:115-117`, `:124`). State it once, with the reason and the ADR (`core-build/011` lines 77-78 and 90-91) beside it, and let the other two sentences say only what they add (the carried population of the other parsers; the three states of `TestCounts`). Statement only; no behaviour change.
   - Skills: pm-dev-java:javadoc
   - Persona: documenter

**Done when:** the test of deliverable 1 fails on the code before it (a failing run, by log id), or the decision records why no first fault can reach a null message; the assertion of deliverable 3(a) cannot fail on a generated name; the statements of deliverables 2 and 4 say what the code does; `verify` per module (`pm-runtime`) and `verify -Ppre-commit` (its rewrites reviewed and committed; the rewrites of any file that is not this plan's are not shipped) are green, targeted `-Dtest` runs where a full verify exceeds the host's budget; the open Sonar issues of the pull request are fixed or dismissed with a reason.

## Standing Constraints

- **Read every cited line at the head first.** The lines of a carried item are those of the landing analysis, read at `e935ab2f`; re-read each at `origin/main` before editing and drop what a merge since then settled (name the commit).
- **Work to the Done-when.** Do not stop to ask the operator which part to continue with.
- **Every carried item gets an outcome** in the landing message and the pull request: adopted into a deliverable, split off to a NAMED existing plan, or dropped, each with its reason.
- **Behaviour change only in deliverable 1,** with its own failing-first test, and only the message composed for an exception that carries none; the report's other arms and their wording do not change.
- **The `JobLog` Javadoc is not here:** it is owed to PLAN-V2-162 (#567); do not edit `JobLog.java`.
- **Where the project analyses its pull requests with Sonar:** an open Sonar issue is fixed in a pushed commit, or dismissed with a stated reason; the quality gate is green before the pull request is reported ready.
- **No dependency or plugin is added.** No more than 100 files per pull request.
- **Tests that can fail.** A new test fails on the code it guards (a failing run or a mutation, by log id); a test of a refusal asserts the reason, not only the type (CLAUDE.md, R20).

## Claim Labels

All lines read at `origin/main` `e935ab2f` (the merge of #603) by the analysis leaf; the orchestrator re-read `PendingEntryRecordsSweep.java:128-134,156-182,205-212,243-254,306-310`, `PendingEntryRecordsSweepTest.java:100-106,660-667,696-702,736-740,780-790` and `BuildOutputParser.java:106-126` at that head. The outline re-reads each before it acts.

- OBSERVED: the first fault's message is passed unguarded into `Fault` and the report (`PendingEntryRecordsSweep.java:132`, `:179`, `:211`, `:309`) — read at those lines
- OBSERVED: the `faultBeside` Javadoc says "so that the report never prints `null`" (`PendingEntryRecordsSweep.java:243-247`), and the code guards only the release exception's message (`:250-252`) — read at those lines
- OBSERVED: the new test asserts `assertFalse(reason.contains("null"))` (`PendingEntryRecordsSweepTest.java:787`) on a report that embeds a generated project id — read at those lines
- OBSERVED: the Javadoc of `BuildOutputParser` states the Maven-only `Unmeasured` outcome at `:109-111`, `:115-117` and `:124` — read at those lines
- HYPOTHESIS: each of `UncheckedIOException`, `IllegalArgumentException`, `IllegalStateException`, `InternalFaultException` and `StoreRefusal` can reach the first-fault arms with a null message — confirm/refute at the constructors of `InternalFaultException` and `StoreRefusal` and at the callers of `acquire` and the commit (verify-at-outline)
- HYPOTHESIS: the `run()` catch (`:132`) and the two `state.fault(...)` sites can share one helper with `faultBeside` without a second composing place — confirm/refute at `PendingEntryRecordsSweep.java` § `run`, `remove`, `attempt`, `RemovalState` (verify-at-outline)

## Expected Surface

Specific files (R19; the outline adds the files the re-read names):

- OBSERVED (`pm-runtime`): `pm-runtime/src/main/java/de/planmarshall/runtime/start/PendingEntryRecordsSweep.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/start/PendingEntryRecordsSweepTest.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/job/BuildOutputParser.java`, `pm-runtime/doc/developer/runtime-start.adoc`, `pm-runtime/doc/adr/runtime-start/016-the-sweep-removes-pending-entry-residue-by-evidence.adoc`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-183 (shipped, #603).
- Overlaps with (class level): PLAN-V2-126 (test conformance of the runtime tests: it runs after this plan or rebases over it), PLAN-V2-162 (`runtime-job.adoc`, `JobLog`: PR #567 open; this plan touches neither) and every staged plan by guide, ADR and Traceability file: the gate decides at emit; one shared file does not block.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-184-sweep-first-fault-message-and-statement-closeout.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Carried Open Items

104. **The residue of the landing of PLAN-V2-183** (`landings/PLAN-V2-183.md`, carried 2026-11-24; merged as #603 `e935ab2f`; read by the analysis leaf; no blocker, no major): deliverables 1 to 4 carry each point: (a) the first fault's null message (183 L3-3, the lane's carried item), (b) the `faultBeside` Javadoc overclaim (183 L2-1), (c) the weak `null` assertion, the copied mock setup and the test Javadoc (183 L3-1, L3-2), (d) the Javadoc that states the Maven-only outcome three times (183 L2-3); recorded, not fixed: the lane's counts of runs and logs (`stated_not_verified`), the report's added `; the lock's release failed: …` clause on the release-fault path only, the runbook-lane `unknown` figures (an open record, owner E27), the open question whether Sonar flags the line-178 condition beside the line-179 one (not shown by reading; the gate was green).

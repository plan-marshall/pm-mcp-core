# PLAN-V2-170: The closeout of the cut build log: the cut flag in the daemon path, the cut tail's failing count, the statements that still say tail-only and the guards that pass silently

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
origin: the three-layer analysis of the landing of PLAN-V2-166 (`landings/PLAN-V2-166.md`, 2026-11-22, #564, merge `cc52de5f`) and the operator's rule that leftovers go into a staged plan (no staged plan owns `pm-core` build parsing, the consult package or these statements; PLAN-V2-162, which carries `JobLog` item 81, is running and is not edited)
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-170-cut-log-and-statement-closeout.md` and is queued as one row file, `queue/PLAN-V2-170.json`,
> in the epic ledger (lane `runbook`: the operator runs it with the runbook lane). The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R05, R06, R07, R08, R10, R11, R13, R17, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R09: no confinement; R12: no message is added; R10 because `store-schemas/10` is read by the schema parity tests: its schema blocks, kind tables and anchors stay byte-stable; R13: no stored format changes.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-build.adoc` and `pm-workflow/doc/developer/workflow-consult.adoc` (exist; the corrected statements); ADR directories: `pm-core/doc/adr/core-build/` and `pm-workflow/doc/adr/workflow-consult/` (renumbered against `origin/main`, R11; an ADR is written only for the decision of deliverable 1); layout: workstream WS-11.
- R12 — none (no message).

**Read the skills first (R20).** `java-core`, `java-null-safety`, `javadoc`, `junit-core` and `cui-testing` of `/home/oliver/git/plan-marshall/marketplace/bundles/{pm-dev-java,pm-dev-java-cui}/skills/`; say in the first progress note which you read. R20 applies to every test line this plan touches.

## Objective

PLAN-V2-166 made a cut Maven build log report `Unmeasured` and bounded `freeId()`, with no blocker and one major left (`landings/PLAN-V2-166.md`): the only production caller of the Maven parser, `BuildOutputParser` in `pm-runtime`, calls the String overload, which is never cut, so the new `Unmeasured` is as unread in this repository as `tailOnly` was. The rest is small: the failing count of a cut tail, four statements that still say tail-only, and three guards that pass silently. None changes a stored datum.

## Deliverables

All file and line references come from the landing analysis and are re-read at the head of `main` before editing; drop what a merge since then settled and name the commit.

1. **The cut flag in the daemon path (carried item 88a, L3-1, major).** `MavenOutputParser.parse(Path)` is the one place `cut` exists (`MavenOutputParser.java:196-216`) and has no production caller in `pm-core` or `pm-runtime`; `BuildOutputParser.java:141` calls `parse(output)` with the text of `JobLog.readWhole`. Read `SubprocessJobRunner` (`readWhole` at `:1577` and `:1628`) and `JobLog` (`capped()`, `:219-243`) and show with a test whether a job log over the cap reaches the parser already cut. If it does, give the parser the cut fact (an overload `parse(String, boolean cut)` or the `JobLog.Complete` length against the cap) so the count is `Unmeasured` on the daemon path; if it does not, record that and state in ADR `core-build/011` where the cut can arise. A result-type or behaviour change is the operator's word (R07, ask-at-refine; recommendation: pass the fact, ~75%). The test fails on the code before the change.

2. **The failing count of a cut tail (carried item 88b, L3-2 to L3-4).** A cut tail whose `Results:` marker is in the lost head and whose summary line carries `[WARNING]` or `[INFO]` (`testFailureIgnore` with `BUILD SUCCESS`) puts the line in `ambiguousLines`, so `visibleFailures()` is 0 and no `TEST_FAILURE` is filed (`MavenOutputParser.java:368-371,207-209`); a tail whose `[ERROR] Tests run:` line is already filed gets one synthetic `TEST_FAILURE` more (`:226-232`); a cut log with no count lines is `Unmeasured` where it was null. Count from the ambiguous summary lines for a cut log, file no duplicate, and say in ADR `core-build/011` what a consumer that tells absent from unmeasured sees. Tests first, each failing on the code before the change.

3. **The statements that still say tail-only, and the sentences that may still be lost (carried item 88c).** `pm-core/src/main/java/de/planmarshall/core/build/package-info.java:49-50` and ADR `core-build/007:85` say a cut log's Maven count is the tail's alone; the specification `job-runtime/03-parsers-and-gates.adoc:15,21` states the unmeasured rule and the tail read but not the cut-log rule; ADR `workflow-consult/005:25-26` says a supplier "has to yield a free id after finitely many taken ones" with no bound or refusal (PLAN-V2-166 added `MAX_ID_DRAWS` and `consultation_ids_exhausted`); ADR `core-build/011` keeps the file name `011-a-cut-build-log-reports-tail-only.adoc` (rename it to the slug the Status records, and fix every link). Read `model-work/07` and the guide for two sentences PLAN-V2-166 may not have restored in `store-schemas/10-job-and-lesson-records.adoc` and restore or relocate each with a link, or record where it is stated: the Retirement sentence "in every other state it changes nothing and reports the state", and the Derived-from-Untrusted enumeration of the untrusted writing-task inputs with "a start screens it as a source, the lesson review reads it marked untrusted, the clients show the mark". Schema blocks, kind tables and anchors stay byte-stable (R10).

4. **The guards that pass silently and the races (carried item 88d, L3-5 to L3-8).** `ProcessTreeTrackerTest.dependsOnNoGroupSignal` matches the target by simple name and imports the outer class only: add a non-vacuity guard on the target (the class `ProcessGroupSignals` is found) and import the nested `Member` and `Survivor`. `RoleNameSingleDefinitionTest.java:48-52` says schema strings are compared "once its JSON escapes are decoded" while PLAN-V2-166's follow-up says `\uXXXX` is not decoded: read `schemaOccurrences`, then decode the escapes with a test that fails on a spelling written as `\u007b` or correct the class Javadoc. `JobLog.readWhole` (`JobLog.java:223-233`) compares only `channel.size()` after the read with `size`, so a log shrunk then regrown between the two reads reads `Complete` with a short text: compare the bytes read with `size` (the first-line change of PLAN-V2-162 item 81 touches `JobLog.java:271`; re-read at the head and rebase on it). `ConsultationService.freeId()` checks `queue.read(candidate)` outside the transaction (`:182-222`) so two concurrent opens with a seam that returns one id both pass and the loser fails at `queue.create` after the asker moved to `consulting`: move the check into the transaction or make the failed create restore the asker's status, with a test on a seam that returns one id twice.

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
- **No behaviour change beyond the corrections the items state.** The enum-body re-indentation and the `throws Exception` of #545's code are the pre-commit recipe's output (the parent POM's), not this plan's: record it in the landing message as a Watch, do not undo it.
- **The links-check clause is the operator's.** PLAN-V2-164 and PLAN-V2-166 both reported `BROKEN LINKS 97` (cross-repository links the tool cannot resolve offline) against a Done-when of `0`: this plan reports the count of local broken links and the count of the rest, separately, until the operator words the clause.

## Claim Labels

- OBSERVED: every point was read by the analysis leaf at `origin/main` `cc52de5f`; the orchestrator re-read `BuildOutputParser.java:141` (the String overload), that `parse(Path)` has no production caller in `pm-core` or `pm-runtime`, and `package-info.java:49-50` and ADR `core-build/007:85`.
- LEAD (unverified): that a job log over the cap reaches `parse(String)` already cut (deliverable 1: ADR 007 and the call count, not read line by line); the two possibly lost sentences of deliverable 3; that `\uXXXX` is not decoded by the scanner of `RoleNameSingleDefinitionTest` (deliverable 4).

## Expected Surface

Source, tests, specification and documentation (specific files, R19; the outline adds the files the re-read at the head names):

- OBSERVED (`pm-core`): `pm-core/src/main/java/de/planmarshall/core/build/MavenOutputParser.java`, `pm-core/src/main/java/de/planmarshall/core/build/BuildLogText.java`, `pm-core/src/main/java/de/planmarshall/core/build/TestCounts.java`, `pm-core/src/main/java/de/planmarshall/core/build/package-info.java`, `pm-core/src/test/java/de/planmarshall/core/build/MavenOutputParserTest.java`, `pm-core/src/test/java/de/planmarshall/core/build/BuildLogTextTest.java`, `pm-core/doc/adr/core-build/007-the-read-and-the-patterns-are-bounded.adoc`, `pm-core/doc/adr/core-build/011-a-cut-build-log-reports-tail-only.adoc`, `pm-core/doc/developer/core-build.adoc`, `pm-core/src/test/java/de/planmarshall/core/guard/RoleNameSingleDefinitionTest.java` (read).
- OBSERVED (`pm-runtime`): `pm-runtime/src/main/java/de/planmarshall/runtime/job/BuildOutputParser.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/job/SubprocessJobRunner.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/job/JobLog.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/job/ProcessTreeTrackerTest.java` (read).
- OBSERVED (`pm-workflow`): `pm-workflow/src/main/java/de/planmarshall/workflow/consult/ConsultationService.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/consult/ConsultationServiceTest.java`, `pm-workflow/doc/adr/workflow-consult/005-the-consultation-id-comes-from-a-seam.adoc`, `pm-workflow/doc/developer/workflow-consult.adoc`.
- OBSERVED (`doc`): `doc/specification/job-runtime/03-parsers-and-gates.adoc`, `doc/specification/store-schemas/10-job-and-lesson-records.adoc`, `doc/Traceability.adoc`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-166 (shipped, `landings/PLAN-V2-166.md`: the plan this one succeeds), PLAN-V2-163.
- Overlaps with (class level): PLAN-V2-162 (running: `JobLog.java`, `pm-runtime` job package), PLAN-V2-161 (`core-store.adoc`, `store-schemas/10`) and every staged plan by guide, ADR and Traceability file: the gate decides at emit; one shared file does not block.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-170-cut-log-and-statement-closeout.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Carried Open Items

88. **The residue of the landing of PLAN-V2-166, the cut flag that does not reach the daemon path, the cut tail's count, the tail-only statements and the guards that pass silently** (`landings/PLAN-V2-166.md`, carried 2026-11-22; merged as #564 `cc52de5f`; read by the analysis leaf at `cc52de5f`; no blocker, one major). OBSERVED unless marked. Deliverables 1 to 4 carry each point: (a) L3-1, the major; (b) L3-2 to L3-4; (c) L2-2 and L2-6; (d) L3-5 to L3-8. Recorded and not changed: the enum-body re-indentation of #545's code (the pre-commit recipe's output); the links-check clause (the operator's); the outcomes 14(d) (a Watch for the assembly: whether `plan-marshall-mcp` calls `ActivityJournal.delete` while a second process holds the key), 16, 20(a) and 67(c) of the landing message, which were not re-checked. The API changes of #564 the assembly must read (`TestCounts.Measured` without `tailOnly`, `ConsultationRefusal.CONSULTATION_IDS_EXHAUSTED`, `ConsultationService.MAX_ID_DRAWS`) are a Watch of the epic, not a deliverable.

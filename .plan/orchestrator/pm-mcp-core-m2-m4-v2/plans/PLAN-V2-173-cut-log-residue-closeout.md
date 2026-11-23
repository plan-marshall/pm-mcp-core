# PLAN-V2-173: The residue of the cut build log's closeout: the failing count of a cut tail that double counts and loses, the statements that say more than the code, and the guard that passes by construction

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
origin: the three-layer analysis of the landing of PLAN-V2-170 (`landings/PLAN-V2-170.md`, 2026-11-23, #570, merge `1328d390`) and the operator's rule that leftovers go into a staged plan (no staged plan owns `pm-core` build parsing or `ProcessTreeTrackerTest`; PLAN-V2-162, which edits `JobLog` for other items, is running and is not edited)
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-173-cut-log-residue-closeout.md` and is queued as one row file, `queue/PLAN-V2-173.json`,
> in the epic ledger (lane `runbook`: the operator runs it with the runbook lane). The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R05, R06, R07, R08, R10, R11, R13, R17, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R09: no confinement; R12: no message is added; R10 because `store-schemas/10` and `job-runtime/03` are read by the schema parity tests: their schema blocks, kind tables and anchors stay byte-stable; R13: no stored format changes.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-build.adoc` and `pm-runtime/doc/developer/runtime-job.adoc` (exist; the corrected statements); ADR directories: `pm-core/doc/adr/core-build/` (next free number after 011 at `origin/main`, R11) and `pm-runtime/doc/adr/runtime-job/`; an ADR is written only for the decision of deliverable 1; layout: workstream WS-11.
- R12 — none (no message).

**Read the skills first (R20).** `java-core`, `java-null-safety`, `javadoc`, `junit-core` and `cui-testing` of `/home/oliver/git/plan-marshall/marketplace/bundles/{pm-dev-java,pm-dev-java-cui}/skills/`; say in the first progress note which you read. R20 applies to every test line this plan touches.

## Objective

PLAN-V2-170 passed the cut fact to the Maven parser and made a cut log report `Unmeasured`, with no blocker (`landings/PLAN-V2-170.md`). What it left sits at the counting rule and its statements: the figure for a cut tail, "the larger of the summary side and the per-class lines, reduced by the filed `[ERROR]` lines", double counts in some mixes and loses a failure in others, while its ADR and Javadocs say it never does either and describe a different precedence; and the guard of `ProcessTreeTracker` still passes by construction. This plan closes them.

## Deliverables

All file and line references come from the landing analysis and are re-read at the head of `main` before editing; drop what a merge since then settled and name the commit.

1. **The counting rule of a cut tail (carried item 91a, F1, F2).** `MavenOutputParser` `unfiledFailures()` (`pm-core/src/main/java/de/planmarshall/core/build/MavenOutputParser.java:422-452`) subtracts the filed lines of the side it chose only: (a) a per-class `[WARNING]` line with 1 failure beside a summary `[ERROR]` line with 1 failure gives 1 - 0 = 1, a synthetic "1 further" for a failure already filed; a summary side that wins while per-class `[ERROR]` lines of another execution were filed counts those twice; (b) execution A's `[WARNING]` summary with 2 failures beside execution B's per-class `[ERROR]` lines with 2 and no summary give 2 - 2 = 0, and A's failures are never filed; (c) `filed()` counts every `[ERROR]` line while `DiagnosticCollector.collect` (`DiagnosticCollector.java:56`) drops a line whose text equals one already filed. Decide (ask-at-refine, R07; recommendation: count per execution where the tail allows it, else subtract the filed lines of BOTH sides and count after the collector's deduplication, and say in ADR `core-build/011` that the figure of a mix stays an approximation) and test each of the three constructed tails with a failing run or a mutation by log id.

2. **The statements that say more than the code (carried item 91b, F3, F4, F7, F8).** (a) ADR `core-build/011` (`:44-51`) states the precedence "summary, else ambiguous, else per-class"; the code sums summary and ambiguous and takes the larger of that and the per-class lines: state the rule the code implements and the over-count it allows; `core-build.adoc` and the PR text carry the same sentence. (b) `pm-runtime/src/main/java/de/planmarshall/runtime/job/BuildOutputParser.java:108-111` says Maven is "the one parser ... the others carry no population": Gradle, pyproject, Jest and TAP also build `TestCounts` from the cut tail, and `JobEvidence.ParseResult` (`JobEvidence.java:170`) has no `TestCounts` field, so `Unmeasured` never leaves `BuildOutputParser`: read, then decide (ask-at-refine, recommendation: say it in the Javadoc and ADR `runtime-job/015` and carry the population in `ParseResult` only if a consumer needs it) and correct the Javadoc. (c) "Filed as one error diagnostic" (`doc/specification/job-runtime/03-parsers-and-gates.adoc:25`, `core-build.adoc:195`, ADR 011:72) is inexact: per-class `[ERROR]` lines are each their own diagnostic beside the synthetic one. (d) The synthetic message reads "1 further failing tests" (`MavenOutputParser.java:255-256`): singular and plural.

3. **The guard that passes by construction (carried item 91c, F5).** `pm-runtime/src/test/java/de/planmarshall/runtime/job/ProcessTreeTrackerTest.java:281-292`: `imported.contain(ProcessGroupSignals.class)` is true because the test imports that class itself; the rule matches its target through the string constant `GROUP_SIGNALS` (`:63`), so a rename of the class passes vacuously; the subject filter `haveSimpleName("ProcessTreeTracker")` excludes `Member` and `Survivor`. Match the target by the class (a class literal's name), include the nested classes in the subject, and show the guard fails when `ProcessTreeTracker` or a nested class gains a dependency on `ProcessGroupSignals` (a failing run or a mutation by log id). The lane's mutation (dropping the import) failed the self-referential check only and proves nothing.

4. **The two Javadocs and the open note (carried item 91d, F6, F10).** (a) `pm-runtime/src/main/java/de/planmarshall/runtime/job/JobLog.java:221-224,246-249` claims a log that "shrank and regrew between two size reads is caught": it is caught only when a read saw the shorter file: say so. (b) `ConsultationService.java:224` opens in two transactions (record, then `queue.create`): a failed create leaves the asker `consulting` until `reconcile`'s orphan grace: state it in the guide next to `ConsultationService.java:154,328-339`.

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
- **PLAN-V2-162 (PR #567, running) edits `JobLog`**: deliverable 4(a) touches the Javadoc of the file the lane of 162 rebases onto; read `origin/main` first and, if #567 has not merged, leave 4(a) for the outline's note rather than conflict with it.
- **The links-check clause is the operator's** (PLAN-V2-164 to 171 reported counts of 97, 3252 and 3253 by tool root against a Done-when of `0`): report local broken links and the rest separately.
- **Do not repeat the mutation claims of 170** as facts: they were not re-run by the analysis. A new test of this plan is shown to fail by its own failing run or mutation, by log id.

## Claim Labels

- OBSERVED: every point was read by the analysis leaf at `origin/main` `f1db02cd`; the orchestrator re-read `MavenOutputParser.java:422-452` (the two subtractions and `filed()`) and `ProcessTreeTrackerTest.java:276-294` (the self-referential check).
- LEAD (unverified): the constructed tails of 1(a) and 1(b) (traced by the leaf by hand, not run); that Gradle, pyproject, Jest and TAP build counts from the cut tail (2b; read the parsers); the two-transaction open (4b).

## Expected Surface

Source, tests, specification and documentation (specific files, R19; the outline adds the files the re-read at the head names):

- OBSERVED (`pm-core`): `pm-core/src/main/java/de/planmarshall/core/build/MavenOutputParser.java`, `pm-core/src/main/java/de/planmarshall/core/build/DiagnosticCollector.java`, `pm-core/src/test/java/de/planmarshall/core/build/BuildLogTextTest.java`, `pm-core/doc/developer/core-build.adoc`, `pm-core/doc/adr/core-build/011-a-cut-build-log-reports-unmeasured.adoc`.
- OBSERVED (`pm-runtime`): `pm-runtime/src/main/java/de/planmarshall/runtime/job/BuildOutputParser.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/job/JobEvidence.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/job/JobLog.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/job/ProcessTreeTrackerTest.java`, `pm-runtime/doc/developer/runtime-job.adoc`, `pm-runtime/doc/adr/runtime-job/015-the-output-parser-receives-the-cut-fact.adoc` (file name read at the head).
- OBSERVED (`pm-workflow`): `pm-workflow/src/main/java/de/planmarshall/workflow/consult/ConsultationService.java`, `pm-workflow/doc/developer/workflow-consult.adoc`.
- OBSERVED (`doc`): `doc/specification/job-runtime/03-parsers-and-gates.adoc`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-170 (shipped, `landings/PLAN-V2-170.md`: the plan this one succeeds), PLAN-V2-166.
- Overlaps with (class level): PLAN-V2-162 (running, `JobLog`), PLAN-V2-125 and 126 (test-conformance of the `build` and `job` test trees), PLAN-V2-131 (the build-log bounds, parked or staged) and every staged plan by guide, ADR and Traceability file: the gate decides at emit; one shared file does not block.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-173-cut-log-residue-closeout.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Carried Open Items

91. **The residue of the landing of PLAN-V2-170, the counting rule of a cut tail, the statements, the vacuous guard and the Javadocs** (`landings/PLAN-V2-170.md`, carried 2026-11-23; merged as #570 `1328d390`; read by the analysis leaf at `f1db02cd`; no blocker, no major). Deliverables 1 to 4 carry each point: (a) the counting rule (double count, a lost failure, the collector's deduplication); (b) the statements (ADR 011's precedence, the Javadoc of the dispatch, "one error diagnostic", the plural); (c) the guard of `ProcessTreeTracker`; (d) the `JobLog` Javadoc and the two-transaction open.

# PLAN-V2-177: The second residue of the cut build log's closeout: the failing count's remaining shapes (a global subtraction, a truncated key, a saturation that stops at twenty digits), the statements and the guard that still misses

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
origin: the three-layer analysis of the landing of PLAN-V2-173 (`landings/PLAN-V2-173.md`, 2026-11-23, #580, merge `8537af76`) and the operator's rule that leftovers go into a staged plan (no staged plan owns `pm-core` build parsing or `ProcessTreeTrackerTest`: PLAN-V2-125 and 126 own the test conformance of their trees, 162 is running).
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-177-cut-log-second-residue-closeout.md` and is queued as one row file, `queue/PLAN-V2-177.json`,
> in the epic ledger (lane `runbook`: the operator runs it with the runbook lane). The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R05, R06, R07, R08, R10, R11, R13, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R09: no confinement; R12: no message is added; R10 because `job-runtime/03` is read by the schema parity tests: its schema blocks, kind tables and anchors stay byte-stable; R13: no stored format changes; R17 is lifted, the links check is not a requirement, operator 2026-11-23.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-build.adoc` and `pm-runtime/doc/developer/runtime-job.adoc` (exist; the corrected statements); ADR directories: `pm-core/doc/adr/core-build/` (next free number after 012 at `origin/main`, R11, re-read at the head) and `pm-runtime/doc/adr/runtime-job/`; an ADR is written only for a decision of deliverable 1; layout: workstream WS-11.
- R12 — none (no message).

**Read the skills first (R20).** `java-core`, `java-null-safety`, `javadoc`, `junit-core` and `cui-testing` of `/home/oliver/git/plan-marshall/marketplace/bundles/{pm-dev-java,pm-dev-java-cui}/skills/`; say in the first progress note which you read. R20 applies to every test line this plan touches.

## Objective

PLAN-V2-173 made the failing count of a cut Maven tail per execution after the collector's deduplication, and the closeout's findings F1, F4, F7, F8 and F10 are closed. What it left: the subtraction is still global, the deduplication key is the first 100 characters of a line, a count of twenty digits or more falls out of the saturation, the statements say more than the code, the guard of `ProcessTreeTracker` has its gaps, and the `JobLog` Javadoc of F6 is still open. This plan closes them.

## Deliverables

All file and line references come from the landing analysis at `8537af76` and are re-read at the head of `main` before editing; drop what a merge since then settled and name the commit.

1. **The counting rule's remaining shapes (carried item 96 a to d; tests first).** `MavenOutputParser` (`pm-core/src/main/java/de/planmarshall/core/build/MavenOutputParser.java`): (a) the subtraction is global while the visible figure is per execution (`:454-456,463-473,490-498`): execution A (a per-class `[ERROR]` of 1 and a summary `[ERROR]` of 1) beside execution B (a `[WARNING]` `Tests run: 3, Failures: 1`) gives visible 2, filed 2, remainder 0, and B's failure is never filed at error severity: subtract per execution or state the bound; (b) `DiagnosticCollector.messageKey` (`:81-83`) is the first 100 characters: a per-class line spends about 91 on its prefix, so two failing classes of equal counts and equal elapsed time under one package prefix are one figure, and `distinct()` (`:522-528`) runs over all executions, so identical summary lines of two modules count once: choose the key (the whole line, or the line with its execution ordinal) and its cost (ask-at-refine, R07: the collector's key is shared with every other category, so changing it widens the surface; the recommendation is a separate key for the count lines only, kept inside `MavenOutputParser`); (c) `filedFailures` adds the key of every count line, including lines the collector never files (no `[ERROR]` or `[WARNING]` marker, `filedDiagnostic` returns empty): an unmarked `Tests run: 1, Failures: 1, ...` followed by an identical `[ERROR]` line is never subtracted and the synthetic finding repeats it; (d) saturation: `CountScan.parse` (`:418-428`) returns `0L` on `NumberFormatException`, so a count of 20 digits or more gives `failed = 0` and no remainder, the cut path ignores the `unrepresentable` flag, and `:397` adds `Failures + Errors` without saturation: make the cut path saturate (or refuse to measure) for every digit count and say which, and extend `cutMavenLogSaturatesAnOverflowingFailingCount` beyond 19 digits. A test for each shape that fails on the code before it; the second half of F1 (a summary side wins while another execution's per-class errors are counted twice) gets its own test.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
2. **The statements and the missing evidence (carried item 96 e to g).** ADR `core-build/012` `:47` calls the lines "byte-identical" where the key is a truncation, `:61,83` say a hostile count cannot make the remainder zero or negative, `:87` says the filed figure "mirrors the collector" (it does not for unmarked lines), and the bounded error omits the key collisions and the global subtraction; the Javadocs of `saturatedAdd` and the count scan (`:449,503`) carry the same claims; the execution ordinal increments on every plugin header (`:380-384`), so under `-T` the lines of modules interleave and an ordinal is not a module, and the cut path never consults `PARALLEL_BUILD_BANNER`: say it; in the warning-first case no error count line is filed yet the message says "1 further failing test": count what the collector filed (nit); `%d` uses the default locale (an older line, fix it with the rest). The lane reported "saturation at `Integer.MAX_VALUE`": the code saturates at `Long.MAX_VALUE`, `Integer.MAX_VALUE` is the bound of the `unrepresentable` flag: the statements say which. `BuildLogTextTest` `:441-615`: `cutMavenLogKeepsAnErrorForAWarningFirstDuplicate` and the saturation test are not shown to fail on the old code: name the failing run of each.
   - Skills: pm-documents:ref-asciidoc, pm-dev-java:javadoc
   - Persona: documenter
3. **The population that leaves the dispatcher (carried item 96 h).** `BuildOutputParser.java:109-116` and `runtime-job.adoc:291` say the cut fact reaches the Maven parser alone because its count scan is "the one place a lost head falsifies the figure": Jest, TAP and pytest sum summary blocks, so a cut tail gives them a tail-only `Measured` that now leaves the dispatcher in `ParseResult.testCounts()` with no cut flag beside it, the hazard ADR `core-build/011` rejected for a figure nobody branches on. Decide (ask-at-refine, R07): state it exactly and add the cut fact to `ParseResult` (the assembly adapts after the `SNAPSHOT`, ADR `runtime-job/015`), or keep the carried population and say that only Maven reports `Unmeasured` for a cut. No production code reads `ParseResult.testCounts()` yet.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
4. **The guard and the `JobLog` Javadoc (carried item 96 i, j).** `ProcessTreeTrackerTest.dependsOnNoGroupSignal` (`:278-293`): the check `selected.size() == types.size()` (`:291`) is tautological and has no positive control that the rule fires: add a control that a fixture class depending on `ProcessGroupSignals` is caught; `getDeclaredClasses()` reaches direct members only, so a nested-in-nested, anonymous or local class is unchecked though the Javadoc says "every type declared in it": walk the class files of the tracker's package by name prefix; the match is the exact name, so a dependency on `ProcessGroupSignals$Signal`, on `KillBinding` or through another helper is unseen: state the bound or widen the target. `JobLog.java:221-224,246-249` still claims that a log that "shrank and regrew between two size reads is caught", true only when a read saw the shorter file (F6): read `origin/main` first; PLAN-V2-173 left it for PLAN-V2-162, whose pull request #567 edits `JobLog`: drop this item if 162 carried it (name the commit) and otherwise correct the Javadoc.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer

**Done when:** each shape of deliverable 1 has a test that fails on the code before it (a failing run or a mutation, by log id); the statements of deliverable 2 say what the code does and name the bound; deliverable 3 states its decision in the guide and the ADR where the decision is taken; the positive control of deliverable 4 fails when the dependency is introduced; `verify` per module (`pm-core`, `pm-runtime`) and `verify -Ppre-commit` (its rewrites reviewed and committed) are green, targeted `-Dtest` runs where a full verify exceeds the host's budget; the open Sonar issues of the pull request are fixed or dismissed with a reason.

## Standing Constraints

- **Read every cited line at the head first.** The lines and counts of a carried item are those of the landing analysis, read on an older head; re-read each at `origin/main` before editing and drop what a merge since then settled (name the commit).
- **Work to the Done-when.** Do not stop to ask the operator which part to continue with; ask only a decision the spec marks ask-at-refine (R07) or one a carried item says needs the operator, and put it in the outline's question, not in a stop.
- **Every carried item gets an outcome** in the landing message and the pull request: adopted into a deliverable, split off to a NAMED existing plan, or dropped, each with its reason.
- **No behaviour change beyond the corrections the items state.** A change a carried item does not ask for is ask-at-refine (R07).
- **Where the project analyses its pull requests with Sonar:** an open Sonar issue is fixed in a pushed commit, or dismissed with a stated reason; the quality gate is green before the pull request is reported ready.
- **No dependency or plugin is added.** No more than 100 files per pull request: cut by deliverable or module before the first one is opened.
- **Tests that can fail.** A new test fails on the code it guards (a failing run or a mutation, by log id); a test of a refusal asserts the reason, not only the type (CLAUDE.md, R20).
- **PLAN-V2-162 (PR #567, running) edits `JobLog` and `runtime-job.adoc`**: read `origin/main` first; if #567 has not merged, leave the `JobLog` edit of deliverable 4 and the paragraph of `runtime-job.adoc` it shares for the outline's note rather than conflict with it.
- **Do not repeat the mutation claims of 173 as facts:** they were not re-run by the analysis.

## Claim Labels

All lines read at `origin/main` `8537af76` (the merge of #580) by the analysis leaf; the orchestrator re-read the `NumberFormatException` branch, `saturatedAdd`, `messageKey`, the "one place" sentence and the tautological check. The outline re-reads each before it acts.

- OBSERVED: `MavenOutputParser.CountScan.parse` returns `0L` on `NumberFormatException` and sets `unrepresentable` (`:418-428`); `saturatedAdd` saturates at `Long.MAX_VALUE` (`:509-511`); `DiagnosticCollector.messageKey` is the first `DEDUP_MESSAGE_LENGTH` characters (`:81-83`); `BuildOutputParser.java:109-116` says the cut fact reaches Maven alone; `ProcessTreeTrackerTest.java:291` compares two sets built from the same name.
- LEAD (unverified by the analysis): the cut path ignores the `unrepresentable` flag (the leaf's reading); under `-T` the execution ordinals interleave; whether PLAN-V2-162's continuation carries the `JobLog` Javadoc.

## Expected Surface

Specific files (R19; the outline adds the files the re-read names):

- OBSERVED (`pm-core`): `pm-core/src/main/java/de/planmarshall/core/build/MavenOutputParser.java`, `pm-core/src/main/java/de/planmarshall/core/build/DiagnosticCollector.java`, `pm-core/src/test/java/de/planmarshall/core/build/BuildLogTextTest.java`, `pm-core/doc/adr/core-build/011-a-cut-build-log-reports-unmeasured.adoc`, `pm-core/doc/adr/core-build/012-the-cut-tails-failing-count-is-taken-per-execution.adoc`, `pm-core/doc/developer/core-build.adoc`.
- OBSERVED (`pm-runtime`): `pm-runtime/src/main/java/de/planmarshall/runtime/job/BuildOutputParser.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/job/JobLog.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/job/JobEvidence.java` (only if deliverable 3 adds the cut fact to `ParseResult`), `pm-runtime/src/test/java/de/planmarshall/runtime/job/BuildOutputParserTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/job/ProcessTreeTrackerTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/job/JobLogTest.java`, `pm-runtime/doc/developer/runtime-job.adoc`, `pm-runtime/doc/adr/runtime-job/015-the-parser-seam-carries-the-job-and-settlement-replacements.adoc`.
- OBSERVED (specification): `doc/specification/job-runtime/03-parsers-and-gates.adoc`.
- HYPOTHESIS (new): one ADR under `pm-core/doc/adr/core-build/` per decision of deliverable 1 (number after 012, R11) and, for deliverable 3, under `pm-runtime/doc/adr/runtime-job/`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-170 (shipped, #570) and PLAN-V2-173 (shipped, #580).
- Overlaps with (class level): PLAN-V2-162 (running, `JobLog`, `runtime-job.adoc`), PLAN-V2-125 (`BuildLogTextTest`, `JobLog`: test conformance of the `build` and `job` test trees), PLAN-V2-126 (`ProcessTreeTrackerTest`), PLAN-V2-141 and every staged plan by guide, ADR and Traceability file: the gate decides at emit; one shared file does not block.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-177-cut-log-second-residue-closeout.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Carried Open Items

96. **The residue of the landing of PLAN-V2-173, the counting rule's remaining shapes, the statements, the population that leaves the dispatcher, the guard and the `JobLog` Javadoc** (`landings/PLAN-V2-173.md`, carried 2026-11-23; merged as #580 `8537af76`; read by the analysis leaf at `8537af76`; no blocker, no major). Deliverables 1 to 4 carry each point: (a to d) the counting rule, (e to g) the statements and the missing evidence, (h) the population, (i, j) the guard and the `JobLog` Javadoc.

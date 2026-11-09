# PLAN-V2-131: Bound what a build log can cost the settlement: a size cap on the two whole-file reads, line-bounded regexes, and an excerpt that does not list every line

epic: pm-mcp-core-m2-m4-v2
workstream: WS-07
origin: the three-layer analyses of PLAN-V2-08 (`landings/PLAN-V2-08.md` C3), PLAN-V2-28 (`landings/PLAN-V2-28.md` C1, I2) and PLAN-V2-36 (`landings/PLAN-V2-36.md` C4), owner-register Wa59 and Wa69 ("size cap, quadratic regexes: only on the operator's word"), the Watches entry "PLAN-V2-08 landed" of `epic.md`, and the operator's decision of 2026-11-09 ("stage a small plan, PLAN-V2-131 build-log-bounds")
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-131-build-log-bounds.md` and is queued as one row file, `queue/PLAN-V2-131.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r6`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R05, R06, R07, R08, R10, R11, R16, R17, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R09: no confinement; R12: no message is added; R13: no format change; R14: no durable protocol; R15: no `pm-workflow` class. R10 because the plan edits `job-runtime/03-parsers-and-gates.adoc`: search the test tree for readers of the file before editing and keep every block a test reads byte-stable. R11 because Deliverable 4 writes one ADR in `core-build/` and may write one in `runtime-job/`. R16 because `JobLog` of `pm-runtime` is touched.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-build.adoc` (exists) and `pm-runtime/doc/developer/runtime-job.adoc` (exists); ADR directories: `pm-core/doc/adr/core-build/` and `pm-runtime/doc/adr/runtime-job/` (001 to 015 exist at `363ecb48`); the next free number of each at `origin/main` is taken (R11); layout: workstream WS-07.
- R12 — none (no message).

**Read the skills first (R20).** `java-core`, `java-null-safety`, `javadoc`, `junit-core` and `cui-testing` of `/home/oliver/git/plan-marshall/marketplace/bundles/{pm-dev-java,pm-dev-java-cui}/skills/`; say in the first progress note which you read. R20 applies to every test line this plan touches.

## Objective

A build log is the output of the operator's own build, but a dependency or a test can put any line in it. Today the settlement path reads the whole file and runs unbounded patterns on every line: a very large log is read into memory (`BuildLogText.java:62`, `JobLog.java:183`), two Maven and OpenRewrite patterns and three pytest patterns are quadratic or worse on one long hostile line, and `UnparsedEvidence.of` materialises every line of the log before it keeps the last fifty. The exposure is a stalled daemon and a job record left `running`, not a remote attack, and no log has done it. The plan bounds the cost without changing what any parser reports for a normal log: a size cap for the two whole-file reads, a per-line length bound in front of the patterns plus linear rewrites where one is simple, and an excerpt taken from the tail without listing the log. It is the plan that the operator's decision of 2026-11-09 staged out of PLAN-V2-127's "awaits the operator" item C4.

## Deliverables

1. **The two whole-file reads have a cap.** `BuildLogText.read` (`pm-core/.../core/build/BuildLogText.java:62`, `Files.readAllBytes`) and `JobLog.readWhole` (`pm-runtime/.../runtime/job/JobLog.java:183`, `Files.readAllBytes`) read the file whole. The cap is a limit in a properties data file of each module, read with `LimitProperties.read` (the pattern of PLAN-V2-120: `StalledScopeDetector.LIMITS`, `core.config.LimitProperties`), never a literal. Default (R07: ask at refine, the value and the rule are the operator's): a log longer than the cap is read from its END, because the count lines, the summary and the failure tail are at the end, the result says it was cut (`BuildLogText` documents the cut; `JobLog.readWhole` answers `LogState.TRUNCATED`-like with the bytes read, as it already does for a log shorter than the observed length), and the parsers see the tail. **Done when:** a log larger than the cap is read in at most the cap, a test with a log one byte over and one byte under the cap shows both sides, the cap is a data value with a test that fails for a hard-coded number, and the failing run before the change is reported.
2. **The patterns are bounded per line, and the worst ones rewritten.** The patterns the analyses name: `MavenOutputParser.EXECUTION_HEADER` (`:82-83`, `---\s+([\w.\-]+):[\w.\-]+:[\w.\-]+\s.*@.*---`, run on every line), `OpenRewriteLogParser.FINDING` (`:52`, two lazy groups under `find()`, run in the loop at `:98`), and in `PyprojectOutputParser` `SUMMARY_LINE` (`:98-99`, `^.*\b(?:passed|failed|skipped)\b.*\bin\s+[\d.]+s.*$`), `EXCEPTION_LINE` (`:93`) and `SECTION_LINE` (`:86`). A line longer than a per-line limit (a data value, default decided at refine) is cut before it meets any of them, which bounds every pattern at once; the three that are quadratic on a line below the limit are rewritten so that the cost is linear (a possessive or anchored form, or a `indexOf` test before the pattern), and a rewrite changes no result: the existing parser tests run unchanged. **Done when:** for each named pattern a hostile line of the limit's length returns inside a bound that the pre-change code misses (the failing run is reported; the test uses a bounded wait and states the bound), the per-line limit is a data value, and every existing parser test passes unchanged.
3. **The unparsed excerpt does not list the log.** `UnparsedEvidence.of` (`UnparsedEvidence.java:27-39`) does `rawLog.lines().toList()` and then keeps the last `MAX_LINES` (50) lines; it also redacts the joined tail only. It takes the tail without materialising the list (a bounded deque or a reverse scan), and the redaction input is the kept tail, as today. **Done when:** a log of a million short lines yields the same excerpt as before with memory bounded by the tail (a test compares the excerpt of the new code with the old code's result on a fixture, and a test shows no `List` of the whole log is built), and the existing `UnparsedEvidence` tests pass unchanged. The redaction gaps of the same method (ANSI, `\r`, `npm_` tokens) are PLAN-V2-127 and not here.
4. **The statements and the decision.** `doc/specification/job-runtime/03-parsers-and-gates.adoc` states what a parser reads (the whole log); it states the cap, the cut and the per-line limit instead, in the condensed form R05 allows, with every block a test reads byte-stable (R10). One ADR in `core-build/` records the choice of the tail read and of the per-line cut over a timeout (a regex `timeout` does not exist in `java.util.regex`; an interrupt-based guard was considered and is not taken); an ADR in `runtime-job/` only if `JobLog`'s answer changes shape. The developer guides `core-build.adoc` and `runtime-job.adoc` follow. **Done when:** the statements read as the code behaves, no sentence says "the whole log" where the cap applies, and every link resolves (R17).

**Done when:** Deliverables 1 to 4; no parser result changes for a log below the cap whose lines are below the per-line limit. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed, in their own commit where they touch files outside the plan's scope), then targeted `-Dtest` runs of the changed classes and the per-module run of `pm-core` and `pm-runtime` (a full `verify` exceeds the host's bound; each run is reported in the pull request body), `verify -Pcoverage`. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main` first; the sibling checkout lags).

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`):

- **Default and its alternatives.** The defaults are the tail read, the per-line cut and the linear rewrites above. A cap or per-line value, a change of `JobLog.readWhole`'s answer type, a new public type, and a dependency are ask-at-refine (R07). A `Thread.interrupt`-based regex guard is not taken without the operator's word.
- **No result changes below the bounds.** A parser test that passes today passes unchanged; a rewrite of a pattern is accepted only with a differential test over the existing fixtures and over generated lines (cui-test-generator) showing equal results.
- **Not here:** the success marker, the npm prefix, the redaction shapes and the ANSI/`\r` handling of the excerpt (PLAN-V2-127); the test conformance of `BuildOutputParserTest`, `JobSettlementTest`, `SubprocessJobRunnerTest` (PLAN-V2-90); the `RuntimeException` catch of `JobSettlement.run` (PLAN-V2-94); the Optional conversions (PLAN-V2-97 to 99). Anything else the outline finds wrong is reported in the landing and the inbox message, not fixed here.
- **Timing tests are bounded and stated.** A test that proves a pattern is no longer quadratic names its input length and its bound and uses a preemptive timeout; it never depends on wall-clock comparison of two runs.
- **Where the project analyses its pull requests with Sonar** (read it over plain HTTP: `pullRequest=N`, never `branch=<feature>`): an open Sonar issue is fixed in a pushed commit, or dismissed with a stated reason recorded as `{issue-key}: {reason}` and transmitted through `plan-marshall:workflow-integration-sonar:sonar_rest transition --issue-key {key} --transition {falsepositive|wontfix}`. A dismissal the run cannot transmit is a blocker. The merge gate reads the open-issue count at the head (`0` confirmed, or every remaining issue dismissed); an `undecidable` read blocks.
- **No dependency or plugin is added.**

## Claim Labels

- OBSERVED: `BuildLogText.read` is `stripAnsi(new String(Files.readAllBytes(logFile), UTF_8))` with no cap — `BuildLogText.java:62`, read at `363ecb48`
- OBSERVED: `JobLog.readWhole` calls `Files.readAllBytes(path)` and builds `LaunchReport.read(new String(bytes, UTF_8))` — `JobLog.java:183, 189`
- OBSERVED: `MavenOutputParser.EXECUTION_HEADER` at `:82-83`; `OpenRewriteLogParser.FINDING` at `:52` matched in a loop at `:98`; `PyprojectOutputParser.SECTION_LINE` `:86`, `EXCEPTION_LINE` `:93` (MULTILINE), `SUMMARY_LINE` `:98-99`
- OBSERVED: `UnparsedEvidence.of` does `rawLog.lines().toList()` (`:37`) and keeps the last `MAX_LINES = 50` (`:40`); `BuildLogText.read` has nine parser callers in `core.build`, and `JobLog` two runtime consumers (`SubprocessJobRunner`, `JobLogTest`)
- OBSERVED: the limits pattern is `LimitProperties.read(resource, key)` with the resource named by the class, e.g. `StalledScopeDetector.LIMITS` = `/de/planmarshall/runtime/start/stalled-scope.properties` (`StalledScopeDetector.java:47`)
- HYPOTHESIS: `EXECUTION_HEADER` is quadratic to cubic on repeated `--- a:b:c ` text and `OpenRewriteLogParser.FINDING` quadratic per start (landing analysis of PLAN-V2-08 C3, "timing not shown by reading"); the three pytest patterns are quadratic on a long `E` or banner line (PLAN-V2-28 C1, "structure checked, timing not shown"). The failing run is the evidence and Deliverable 2 reports it; a pattern that is linear is dropped from the deliverable
- HYPOTHESIS: a log tail is where the count and summary lines live for Maven, Gradle, pytest, Jest and TAP, so a tail read keeps every parser's marker; a parser that reads the head (a launch report, a leading banner) needs the head as well: the outline lists each parser's reads before it fixes the rule
- Verify-first clause: before editing, list the open pull requests for one that touches `core.build`, `JobLog`, `SubprocessJobRunner` or `job-runtime/03` (PLAN-V2-18, 99 and 127 may), re-read the files at the head, and re-read the three landing analyses; a refutation loops back to re-scope

## Expected Surface

Code and tests:

- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/build/BuildLogText.java` — the cap and the cut (D1)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/build/MavenOutputParser.java` — `EXECUTION_HEADER` (D2)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/build/OpenRewriteLogParser.java` — `FINDING` (D2)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/build/PyprojectOutputParser.java` — three patterns (D2)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/build/UnparsedEvidence.java` — the tail without a list (D3)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/build/BuildLogTextTest.java` — the cap cases (D1)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/job/JobLog.java` — the cap on `readWhole` (D1)
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/job/JobLogTest.java` — the cap cases (D1)
- HYPOTHESIS: `pm-core/src/main/resources/de/planmarshall/core/build/build-limits.properties` and `pm-runtime/src/main/resources/de/planmarshall/runtime/job/job-log-limits.properties` — the data files of the cap and the per-line limit (D1, D2; the names are the outline's)
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/build/BuildLogBoundsTest.java` — the hostile-line and large-log cases (D2, D3; the name is the outline's)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/build/UnparsedEvidenceTest.java` — the excerpt equality case (D3)

Specification, documentation:

- OBSERVED: `doc/specification/job-runtime/03-parsers-and-gates.adoc` — what a parser reads (D4); the blocks a test reads stay byte-stable (R10)
- OBSERVED: `pm-core/doc/developer/core-build.adoc` and `pm-runtime/doc/developer/runtime-job.adoc` — the developer documentation (D4)
- HYPOTHESIS: `pm-core/doc/adr/core-build/` — one new ADR, the next free number at `origin/main` (D4)

Not surface (R19): `doc/plans/README.adoc`, `doc/adr/README.adoc`, `doc/LogMessages.adoc`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-83 (conformance foundation, shipped) and, as sequencing, PLAN-V2-127 (it edits `UnparsedEvidence`, `core-build.adoc`, `job-runtime/03` and the `core-build/` ADR numbers) and PLAN-V2-18 (`core.build` parsers, running). Emit after 127 has landed.
- Overlaps with (class level): PLAN-V2-99 (`optional-conversion-runtime-and-providers`, running: `JobLog`, `SubprocessJobRunner`), PLAN-V2-90 and PLAN-V2-94 (`core.build` and `runtime.job` classes), PLAN-V2-125 and 126 (test directories of `core.build` and `runtime.job`). The gate (`corpus cross-check`) decides at emit; sequence this plan after 99 when it reports a multi-file overlap.
- Adjacent to: PLAN-V2-36 (shipped: the adapter that puts the parsers on the settlement path), PLAN-V2-28 and 08 (the parsers).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-131-build-log-bounds.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Carried Open Items

- Absorbed (Wa59, Wa69, `epic.md` Watches "PLAN-V2-08 landed"): C3 of the PLAN-V2-08 analysis (two polynomial regexes, no size cap on `BuildLogText.read`), C1 and I2 of the PLAN-V2-28 analysis (quadratic pytest patterns, no size cap), C4 of the PLAN-V2-36 analysis (the adapter puts the unbounded parsers on the settlement path; `JobLog` reads the whole file; `of()` lists every line).
- Owned elsewhere: the success marker and redaction shapes (PLAN-V2-127), the `JobSettlement` catch (PLAN-V2-94), test conformance (PLAN-V2-90, 125, 126).
- Stays open: whether a time bound (an interrupt-based guard) is wanted on top of the line cut (the operator's word, R07); the redaction of the whole log before the tail is taken (PLAN-V2-127 C2 remainder, needs the cap of Deliverable 1).

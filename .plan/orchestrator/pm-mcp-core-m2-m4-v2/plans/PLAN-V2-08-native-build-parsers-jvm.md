# PLAN-V2-08: Native build output parsers for the JVM tools: Maven, Gradle and the OpenRewrite log

epic: pm-mcp-core-m2-m4-v2
workstream: WS-07
origin: PLAN-143 of the epic pm-mcp-core-m2-m4

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-08-native-build-parsers-jvm.md` and is queued as one row file, `queue/PLAN-V2-08.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r2`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R17, R18, R19. Package bookkeeping (R01, R05): this plan is 1 of 7 plans of the package `build-parsers-and-gate-honesty` (`doc/plans/build-parsers-and-gate-honesty.adoc`). It deletes from the package file the bullets of _What_ and the _Done When_ paragraphs it delivers and drops its deliverables from the covered sections of the specification and watch items; the plan that delivers the last bullet removes the package's row from `doc/plans/README.adoc` and deletes the package file. The plans of the package edit different sections of the same documents; whichever merges second merges the base branch (the lane's step 7) before the merge gate.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-build.adoc` (new); ADR directory: `pm-core/doc/adr/core-build/` (new directory, `001-…`); layout: workstream WS-10.
- R12 — log message holder: none expected (`CoreLogMessages`, `de.planmarshall.core.log`, prefix `PM_CORE`, would take it; next free: INFO 2, WARN 103, ERROR 201).

## Objective

Implement the first part of the work package `build-parsers-and-gate-honesty` (`doc/plans/build-parsers-and-gate-honesty.adoc`, Milestone 6): the shared result model of a parsed build in package `core.build` and the three parsers for the JVM tools — Maven, Gradle and the OpenRewrite log — each compiled from the `plan-marshall` module it ports (`PM-IMPL-6`) and verified by Java tests whose data and expected values derive from that module and its pytest suite (`PM-TEST-2`). The JavaScript and Python parsers are the plan PLAN-V2-28 (`native-build-parsers-npm-and-python`), which builds on the model delivered here. Package `core.build` holds only its `package-info.java` today, so every class named below is new.

## Deliverables

1. **The result model and the counts that may not be guessed.** Records in `core.build`: the diagnostic (file, line, column, message, category, severity), the test counts with a `tests_population` of `measured` or `unmeasured`, and the parsed build (diagnostics, counts, build status, the positive success marker, the `no_tests_ran` verdict). Test counts are aggregated over every summary block of a multi-module run; a successful build whose summaries cannot be aggregated reports `tests_population: unmeasured`, never `tests_run: 0`; `no_tests_ran` exists only with a positive marker, never from a zero count. The shared JVM category patterns (compiler, test, dependency, OpenRewrite info) of `script-shared/scripts/build/_build_jvm_patterns.py` become one compiled table. Tests: the invariants of the records, including that `unmeasured` carries no number.
2. **The Maven parser** (port of `build-maven/_maven_cmd_parse.py`): the line scanner over `[ERROR]` and `[WARNING]` lines with the shared patterns, stack-trace collection, deduplication across the reactor summary and the per-module output, the Surefire and Failsafe summary aggregated over every block, a passing `Tests run:` line skipped. Two *deliberate changes*, each with its own fixture and never an expected value taken from the original: the failure-trailer advice lines (`Re-run Maven using the -X switch…`, `[Help 1] http…`, `After correcting the problems, you can resume the build…`, `mvn <args> -rf :<module>`) are dropped whole against a compiled pattern table matched with a full match, so a reworded line is filed and a genuine diagnostic is never dropped; an unrecognised `[ERROR]` line is categorised `unrecognized`, never given a real category. Fixtures ported from `test/plan-marshall/build-maven/test_maven_cmd_parse.py` of the `plan-marshall` checkout (PM-WATCH-JOB-10, -11, -18).
3. **The Gradle parser** (port of `build-gradle/_gradle_cmd_parse.py`): compiler error locations of the form `<path>.<java|kt|groovy|scala>:<line>[:<col>]`, the failing tasks, the `N tests completed, M failed` summaries with the shared category patterns, the build status; the same counts rules as Maven. Fixtures ported from `test/plan-marshall/build-gradle/test_gradle_cmd_parse.py`.
4. **The OpenRewrite log parser** (port of `pm-dev-java-cui:parse-rewrite-log`, `parse_rewrite_log.py`, and the call site `build-maven/_maven_cmd_rewrite_log.py`): the structured OpenRewrite `WARN` finding lines of a Maven log, one recipe finding per line (newly detected or pre-existing), run only when the build reached the `rewrite:run` goal and the java-cui domain is active; the domain state is an input of the parse, not a lookup. The fail-closed verdicts `observed`, `not_observed`, `domain_inactive`, `parse_error`, each with its own fixture. Fixtures ported from `test/pm-dev-java-cui/parse-rewrite-log/test_parse_rewrite_log.py` and `test/plan-marshall/build-maven/test_maven_rewrite_log.py`.
5. **Documentation and decisions.** `pm-core/doc/developer/core-build.adoc` (new: the package, the model, how a parser is added, the deliberate changes against `plan-marshall`, the fixture provenance) and the ADRs of the package (new directory `pm-core/doc/adr/core-build/`, numbers from `001`): the shape of the result model, and one ADR for the deliberate changes (`unrecognized`, positive `no_tests_ran` marker, trailer table). Add one line to `pm-core/README.adoc` for the new package documentation. Delete from `doc/specification/job-runtime/03-parsers-and-gates.adoc` § Native Build Output Parsers only the rows and paragraphs for Maven, Gradle and OpenRewrite that are implemented (the table rows for the JavaScript and Python parsers stay for the sibling plan), and the watch items PM-WATCH-JOB-10, -11, -18 where fully covered.

**Done when:** Each diagnostic form of the three ported modules is a JUnit test whose expected values equal the original's, with the commit of the `plan-marshall` checkout recorded in the fixture directory; each deliberate change has its own fixture asserting the corrected behaviour (a reworded trailer line is filed, a trailer line is dropped, an unknown `[ERROR]` line is `unrecognized`, a successful build with unusable summaries is `unmeasured`, a zero count without the marker is no `no_tests_ran`); a multi-module Maven log is aggregated over every summary block; the four OpenRewrite verdicts are each shown; no test runs Python or reads the sibling checkout; the developer guide and the ADRs are written. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (`pm-core`; where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and the integration tests of every changed module with tests (`verify -Pintegration-tests -pl <module> -am`, only on `pm-core` and `pm-runtime`; here: `pm-core`) are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-IMPL-6, PM-TEST-2
- Specification: `doc/specification/job-runtime/03-parsers-and-gates.adoc` § Native Build Output Parsers; `doc/specification/store-schemas/13-leases-integrity-testing.adoc` § Ported-Primitive Tests
- Watch: `doc/implementation-watch/job-runtime.adoc` PM-WATCH-JOB-10, PM-WATCH-JOB-11, PM-WATCH-JOB-18

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **Fixtures, not the sibling checkout.** Expected values are derived from the original and committed as plain files; the commit of the `plan-marshall` checkout they were derived from is recorded in the fixture directory (the checkout is the sibling `../plan-marshall`, never read by a test; no Python runs in the build).
- **Log messages: none expected.** A parser is a pure function of text; if the trace matrix shows a message it goes into `CoreLogMessages` at the next free identifier of its level (ADR `doc/adr/005-one-log-message-holder-per-module.adoc`) and the plan says so in its inbox message.
- No new dependency (`CLAUDE.md`): the parsers are line scanners over the JDK; a library need is a question to the operator, not an addition.

## Claim Labels

- OBSERVED: the package file and its index row (`open`, depends on nothing) — `doc/plans/build-parsers-and-gate-honesty.adoc` (What, Trace, Done When) and `doc/plans/README.adoc` at `origin/main` `a8da4104`; the package is cut into seven plans because its _What_ has six bullets with about twenty testable parts
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/build/package-info.java` is the only file of the package `core.build` (15 lines, Javadoc naming `doc/specification/job-runtime/03-parsers-and-gates.adoc`) — read at `a8da4104`
- OBSERVED: the ported sources exist in the sibling checkout `../plan-marshall` (at `b1609ec3e`): `marketplace/bundles/plan-marshall/skills/build-maven/scripts/_maven_cmd_parse.py`, `_maven_cmd_rewrite_log.py`, `build-gradle/scripts/_gradle_cmd_parse.py`, `script-shared/scripts/build/_build_jvm_patterns.py`, `marketplace/bundles/pm-dev-java-cui/skills/parse-rewrite-log/scripts/parse_rewrite_log.py`, and the pytest suites `test/plan-marshall/build-maven/test_maven_cmd_parse.py`, `test_maven_rewrite_log.py`, `build-gradle/test_gradle_cmd_parse.py`, `test/pm-dev-java-cui/parse-rewrite-log/test_parse_rewrite_log.py` — listed with `ls`
- OBSERVED: the shipped precedent of the ported-test convention: `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/ported/` (`WorktreeShaPortedTest`, `WorktreeShaScenario`, `PortedFixtures`) with committed plain-file fixtures under `src/test/resources/ported`
- OBSERVED: the seam the parsers will feed: `pm-runtime/src/main/java/de/planmarshall/runtime/job/SubprocessJobRunner.java` line 439 `public interface OutputParser` with `ParseResult parse(JobId.Kind kind, String output)` at line 446, and `JobEvidence.ParseResult(successMarker, diagnostics, contradictingEvidence, selfReportContradiction)` at `JobEvidence.java` line 168; wiring them is the plan PLAN-V2-36 (`failed-unparsed-and-parser-wiring`), not this one
- HYPOTHESIS: no test of the repository reads `doc/specification/job-runtime/03-parsers-and-gates.adoc` as input (R10): `grep` for `job-runtime/` and `SpecificationDocument` over `pm-*/src/test` finds only the store-schema parity tests — confirm at the outline before deleting sections (verify-at-outline)
- HYPOTHESIS: the parsers need no library beyond the JDK and `cui-java-tools`; if a diagnostic form needs one, stop and ask (`CLAUDE.md`: never add a dependency without asking)
- Verify-first clause: before editing, list the open pull requests for one that names `build-parsers-and-gate-honesty` or touches `pm-core/.../core/build/`, re-read the `plan-marshall` sources at the head of the sibling checkout and record the commit the fixtures derive from; a refutation of any claim above loops back to re-scope.

## Expected Surface

- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/build/package-info.java` — existing; Javadoc extended
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/build/` new classes the text implies: the diagnostic, test-counts and parsed-build records, the compiled JVM category table, `MavenOutputParser`, `GradleOutputParser`, `OpenRewriteLogParser` and its verdict type (exact names at the outline)
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/build/` new test classes, one per class above, and `pm-core/src/test/resources/ported/build/` new fixture directory (committed plain files)
- OBSERVED: `doc/plans/build-parsers-and-gate-honesty.adoc` — the package file, edited (shared with the six sibling plans, section-disjoint)
- OBSERVED: `doc/specification/job-runtime/03-parsers-and-gates.adoc` — specification part; Maven, Gradle and OpenRewrite rows and the counts paragraph deleted
- OBSERVED: `doc/implementation-watch/job-runtime.adoc` — PM-WATCH-JOB-10, -11, -18
- HYPOTHESIS: `pm-core/doc/developer/core-build.adoc` and `pm-core/doc/adr/core-build/` — new (a developer guide or an ADR directory that does not exist yet is listed, R19)

## Dependencies and Sequencing

- Depends on: nothing (the package depends on nothing in the index).
- Overlaps with: no live plan names `pm-core/.../core/build/` (`grep` over `plans/*.md` finds none). Documents: the package file, `job-runtime/03-parsers-and-gates.adoc` and `implementation-watch/job-runtime.adoc` are shared with the other six plans of the package (PLAN-V2-28 (`native-build-parsers-npm-and-python`), PLAN-V2-18 (`coverage-report-parsers`), PLAN-V2-36 (`failed-unparsed-and-parser-wiring`), PLAN-V2-51 (`adaptive-timeout-learning`), PLAN-V2-37 (`gate-honesty-facts`), PLAN-V2-55 (`gate-baseline-separation`)), each in different sections.
- Adjacent to: `pm-runtime` `runtime.job` (the `OutputParser` seam), which only PLAN-V2-36 (`failed-unparsed-and-parser-wiring`) touches; the original scripts of `plan-marshall`, which stay untouched.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-08-native-build-parsers-jvm.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

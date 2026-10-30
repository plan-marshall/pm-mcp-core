# PLAN-V2-18: Native coverage report parsers: JaCoCo, Cobertura, LCOV and Istanbul

epic: pm-mcp-core-m2-m4-v2
workstream: WS-07
origin: PLAN-134 of the epic pm-mcp-core-m2-m4

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-18-coverage-report-parsers.md` and is queued as one row file, `queue/PLAN-V2-18.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r4`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R17, R18, R19, R20. Package bookkeeping (R01, R05): this plan is 3 of 7 plans of the package `build-parsers-and-gate-honesty` (`doc/plans/build-parsers-and-gate-honesty.adoc`). It deletes from the package file the bullets of _What_ and the _Done When_ paragraphs it delivers and drops its deliverables from the covered sections of the specification and watch items; the plan that delivers the last bullet removes the package's row from `doc/plans/README.adoc` and deletes the package file. The plans of the package edit different sections of the same documents; whichever merges second merges the base branch (the lane's step 7) before the merge gate.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-build.adoc` (created by PLAN-V2-08 (`native-build-parsers-jvm`), or created here if this plan lands first); ADR directory: `pm-core/doc/adr/core-build/` (the next free number of the directory); layout: workstream WS-10.
- R12 — log message holder: none expected (`CoreLogMessages`, prefix `PM_CORE`).

## Objective

Implement the coverage part of the work package `build-parsers-and-gate-honesty`: the four coverage report parsers of package `core.build`, ported from `plan-marshall`'s format-agnostic `script-shared/scripts/build/_coverage_parse.py`. The threshold verdict (`passed`) compares the overall *line* percentage with the threshold and the low-coverage list compares each item's line percentage; every other metric is parsed and rendered and never feeds a threshold. The plan shares no class with the build output parsers and can run beside them.

## Deliverables

1. **The coverage model and the threshold verdict:** overall and per-item percentages (`instruction`, `branch`, `line`, `method`/`function`, `statement` as the format gives them), the `missed_methods` detail, the verdict on the overall line percentage only, and the low-coverage list per item on the line percentage; a metric other than the line never changes either. Tests: a fixture whose branch coverage is below the threshold and line coverage above it passes.
2. **JaCoCo (`jacoco.xml`):** a StAX reader of the `INSTRUCTION`, `BRANCH`, `LINE` and `METHOD` counters (`covered / (missed + covered)`), per class the `LINE` counter and the methods with missed `METHOD` counters (`<init>` and `<clinit>` excluded) as `missed_methods`. The reader is configured against untrusted input: no DTD, no external entities (a fixture with a `DOCTYPE` and an external entity is refused, never expanded).
3. **Cobertura (`coverage.xml`):** the root `line-rate` and `branch-rate` and per class `line-rate` (methods with `line-rate` 0 as `missed_methods`); the same XML hardening as JaCoCo.
4. **LCOV (`lcov.info`):** the per-file records `SF:`, `LF:`, `LH:`, `BRF:`, `BRH:`, `FNF:`, `FNH:` up to `end_of_record`, summed over all files into overall `line`, `branch` and `function` (`statement` equals `line`); `DA:` records may be kept as detail and never enter a percentage.
5. **Istanbul (`coverage-summary.json`):** a Jackson streaming reader (`jackson-core`, already a dependency of `pm-core`) extracting the `pct` of `lines`, `statements`, `functions` and `branches` from `total` and per file; overall `line` is `lines.pct`. A malformed document is a refusal with the reason, never a zero. Documentation: extend or create `pm-core/doc/developer/core-build.adoc`, the ADR for the model and the hardening, and delete the implemented coverage section of the specification.

**Done when:** Each of the four formats parses its fixture (ported from `test/plan-marshall/extension-api/test_coverage_parse.py` of the `plan-marshall` checkout, commit recorded) into the overall and per-item figures with the expected values; the threshold verdict follows the overall line percentage only and a metric outside the line never changes it; `<init>` and `<clinit>` are not in `missed_methods`; an XML report with a DTD or an external entity is refused; an unreadable or truncated report is a refusal naming the format, never `0 %`; no test runs Python. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (`pm-core`; where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and the integration tests of every changed module with tests (`verify -Pintegration-tests -pl <module> -am`, only on `pm-core` and `pm-runtime`; here: `pm-core`) are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-EXEC-6, PM-TEST-2
- Specification: `doc/specification/job-runtime/03-parsers-and-gates.adoc` § Native Coverage Report Parsers
- Watch: `doc/implementation-watch/job-runtime.adoc` (no item names the coverage parsers; PM-WATCH-GEN-2 of the documentation repository for the gate that reads them)

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **Fixtures, not the sibling checkout.** Expected values are derived from the original and committed as plain files; the commit of the `plan-marshall` checkout they were derived from is recorded in the fixture directory (the checkout is the sibling `../plan-marshall`, never read by a test; no Python runs in the build).
- **Log messages: none expected.**
- No new dependency: StAX is the JDK's (`javax.xml.stream`), the JSON reader is `jackson-core`, already declared in `pm-core/pom.xml` (OBSERVED, `artifactId` `jackson-core`).

## Claim Labels

- OBSERVED: the package file and its index row (`open`, depends on nothing) — `doc/plans/build-parsers-and-gate-honesty.adoc` (What, Trace, Done When) and `doc/plans/README.adoc` at `origin/main` `a8da4104`; the package is cut into seven plans because its _What_ has six bullets with about twenty testable parts
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/build/package-info.java` is the only file of the package `core.build` (15 lines, Javadoc naming `doc/specification/job-runtime/03-parsers-and-gates.adoc`) — read at `a8da4104`
- OBSERVED: the original and its suite exist in the sibling checkout `../plan-marshall`: `marketplace/bundles/plan-marshall/skills/script-shared/scripts/build/_coverage_parse.py`, `_build_coverage_report.py`, and `test/plan-marshall/extension-api/test_coverage_parse.py` — listed with `ls`/`find`
- OBSERVED: `pm-core` uses Jackson streaming throughout (`import com.fasterxml.jackson.core.JsonParser` in 38 files of `pm-core/src/main`) and declares `jackson-core` in `pm-core/pom.xml`; it has no `module-info.java`, so `java.xml` is available
- HYPOTHESIS: a StAX factory with `IS_SUPPORTING_EXTERNAL_ENTITIES` and `SUPPORT_DTD` off reads real `jacoco.xml` and `coverage.xml` files (JaCoCo writes a `DOCTYPE` with a system id) — confirm with the ported fixtures; if a real report carries a DTD that must be read, the reader tolerates the declaration without resolving it (verify-at-outline)
- Verify-first clause: before editing, list the open pull requests touching `core/build/`, re-read `_coverage_parse.py` at the head of the sibling checkout, and read how PLAN-V2-08 (`native-build-parsers-jvm`) shaped its model if it has landed (reuse it where it fits, otherwise keep the coverage model separate); a refutation loops back to re-scope.

## Expected Surface

- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/build/` new classes: the coverage report and threshold-verdict records, `JacocoReportParser`, `CoberturaReportParser`, `LcovReportParser`, `IstanbulSummaryParser` (exact names at the outline)
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/build/` new test classes and fixtures under `pm-core/src/test/resources/ported/coverage/` (new)
- OBSERVED: `doc/plans/build-parsers-and-gate-honesty.adoc` — the package file, edited
- OBSERVED: `doc/specification/job-runtime/03-parsers-and-gates.adoc` — specification part; § Native Coverage Report Parsers deleted where implemented
- HYPOTHESIS: `pm-core/doc/developer/core-build.adoc` and `pm-core/doc/adr/core-build/` — created by the first of the package's plans to land, extended additively by the others

## Dependencies and Sequencing

- Depends on: PLAN-V2-83 (`skills-conformance-foundation`: the dependencies and rule R20); and then: nothing.
- Overlaps with: disjoint in code from every other plan of the package and from every live plan (no live plan names `core/build/`). Documents shared with the six sibling plans: the package file and `job-runtime/03-parsers-and-gates.adoc` (different section). If it lands before PLAN-V2-08 (`native-build-parsers-jvm`), it creates the developer guide and the ADR directory and the sibling extends them.
- Adjacent to: PLAN-V2-37 (`gate-honesty-facts`) (reads the coverage figures for `gate_coverage` and `baseline_debt`).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-18-coverage-report-parsers.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

# PLAN-V2-28: Native build output parsers for npm, TypeScript, ESLint, Jest, TAP and pyproject

epic: pm-mcp-core-m2-m4-v2
workstream: WS-07
origin: PLAN-144 of the epic pm-mcp-core-m2-m4

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-28-native-build-parsers-npm-and-python.md` and is queued as one row file, `queue/PLAN-V2-28.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r4`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R17, R18, R19, R20. Package bookkeeping (R01, R05): this plan is 2 of 7 plans of the package `build-parsers-and-gate-honesty` (`doc/plans/build-parsers-and-gate-honesty.adoc`). It deletes from the package file the bullets of _What_ and the _Done When_ paragraphs it delivers and drops its deliverables from the covered sections of the specification and watch items; the plan that delivers the last bullet removes the package's row from `doc/plans/README.adoc` and deletes the package file. The plans of the package edit different sections of the same documents; whichever merges second merges the base branch (the lane's step 7) before the merge gate.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-build.adoc` (created by PLAN-V2-08 (`native-build-parsers-jvm`), extended); ADR directory: `pm-core/doc/adr/core-build/` (created by PLAN-V2-08 (`native-build-parsers-jvm`); the next free number); layout: workstream WS-10.
- R12 — log message holder: none expected (`CoreLogMessages`, prefix `PM_CORE`; next free: INFO 2, WARN 103, ERROR 201).

## Objective

Implement the second part of the work package `build-parsers-and-gate-honesty`: the six parsers of the JavaScript and Python tools — npm errors, TypeScript, ESLint, Jest, TAP and pyproject (pytest, ruff, mypy) — each compiled from the `plan-marshall` module it ports and producing the result model that PLAN-V2-08 (`native-build-parsers-jvm`) delivered (diagnostic, test counts with `tests_population`, parsed build), with the same counts rules.

## Deliverables

1. **npm errors and TypeScript** (ports of `build-npm/_npm_parse_errors.py` and `_npm_parse_typescript.py`): the scanner over `npm ERR!` lines classifying error codes (`ERESOLVE`, `E404`, others) with the conflicting package or peer dependency and the message; the `tsc` diagnostics in both forms `path(line,col): error TSnnnn: message` and `path:line:col - error TSnnnn: message`. Fixtures ported from `test/plan-marshall/build-npm/test_npm_parsers.py`.
2. **ESLint** (`_npm_parse_eslint.py`): the stylish formatter output — file header lines followed by `line:col severity message rule` rows — and the problem summary; error and warning counts.
3. **Jest and TAP** (`_npm_parse_jest.py`, `_npm_parse_tap.py`): `FAIL <suite>` blocks with the failing test and message and the `Tests:` summary line; `not ok N - <name>` lines and the `# tests` / `# pass` / `# fail` / `# skipped` summary. Test counts follow the rules of the model (`unmeasured`, never `0`; `no_tests_ran` only from a positive marker — a test filter that matches nothing reports zero tests, PM-WATCH-JOB-11).
4. **pyproject** (`build-pyproject/_pyproject_cmd_parse.py`): the tool-detecting scanner for pytest (`FAILED` and collection or setup `ERROR` short-summary lines, the deepest traceback frame for the location), ruff (`path:line:col: CODE message`) and mypy (`path:line: error: message`); the pytest-xdist group suffixes are normalised (PM-WATCH-JOB-12). Fixtures ported from `test_pyproject_cmd_parse.py` and `test_pyproject_cmd_parse_collection_errors.py` under `test/plan-marshall/build-pyproject/`.
5. **Documentation.** Extend `pm-core/doc/developer/core-build.adoc` (the six parsers, their fixtures) and the ADRs where a decision was taken; delete from `doc/specification/job-runtime/03-parsers-and-gates.adoc` § Native Build Output Parsers the rows of these parsers and from `doc/implementation-watch/job-runtime.adoc` PM-WATCH-JOB-12 where covered.

**Done when:** Each diagnostic form of the six ported modules is a JUnit test with the ported module's own expected values (commit recorded); both `tsc` forms, the stylish ESLint rows and summary, the Jest `FAIL` block and `Tests:` line, the TAP summary, and the pytest, ruff and mypy forms each have a fixture; the xdist suffix normalisation and the deepest-frame location are shown; a multi-block run is aggregated; `unmeasured` is reported where counts cannot be aggregated; no test runs Python. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (`pm-core`; where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and the integration tests of every changed module with tests (`verify -Pintegration-tests -pl <module> -am`, only on `pm-core` and `pm-runtime`; here: `pm-core`) are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-IMPL-6, PM-TEST-2
- Specification: `doc/specification/job-runtime/03-parsers-and-gates.adoc` § Native Build Output Parsers; `doc/specification/store-schemas/13-leases-integrity-testing.adoc` § Ported-Primitive Tests
- Watch: `doc/implementation-watch/job-runtime.adoc` PM-WATCH-JOB-11, PM-WATCH-JOB-12

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **Fixtures, not the sibling checkout.** Expected values are derived from the original and committed as plain files; the commit of the `plan-marshall` checkout they were derived from is recorded in the fixture directory (the checkout is the sibling `../plan-marshall`, never read by a test; no Python runs in the build).
- **Log messages: none expected** (a parser is a pure function of text).
- No new dependency (`CLAUDE.md`).

## Claim Labels

- OBSERVED: the package file and its index row (`open`, depends on nothing) — `doc/plans/build-parsers-and-gate-honesty.adoc` (What, Trace, Done When) and `doc/plans/README.adoc` at `origin/main` `a8da4104`; the package is cut into seven plans because its _What_ has six bullets with about twenty testable parts
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/build/package-info.java` is the only file of the package `core.build` (15 lines, Javadoc naming `doc/specification/job-runtime/03-parsers-and-gates.adoc`) — read at `a8da4104`
- OBSERVED: the ported sources exist in the sibling checkout `../plan-marshall` (at `b1609ec3e`): `build-npm/scripts/_npm_parse_errors.py`, `_npm_parse_typescript.py`, `_npm_parse_eslint.py`, `_npm_parse_jest.py`, `_npm_parse_tap.py`, `_npm_cmd_parse.py`, `build-pyproject/scripts/_pyproject_cmd_parse.py`, and the suites `test/plan-marshall/build-npm/test_npm_parsers.py`, `test/plan-marshall/build-pyproject/test_pyproject_cmd_parse.py`, `test_pyproject_cmd_parse_collection_errors.py` — listed with `ls`/`find`
- HYPOTHESIS: the result model (diagnostic, test counts, parsed build) exists when this plan starts, delivered by PLAN-V2-08 (`native-build-parsers-jvm`); if its shape does not fit a form here, extend it in this plan and say so in the ADR
- Verify-first clause: before editing, confirm that PLAN-V2-08 (`native-build-parsers-jvm`) is merged (its model classes exist in `core.build`), re-read the six original modules at the head of the sibling checkout, and list the open pull requests touching `core/build/`; a refutation loops back to re-scope.

## Expected Surface

- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/build/` new classes the text implies: `NpmErrorParser`, `TypeScriptOutputParser`, `EslintOutputParser`, `JestOutputParser`, `TapOutputParser`, `PyprojectOutputParser` (exact names at the outline); the result model of PLAN-V2-08 (`native-build-parsers-jvm`) is read and, where a form needs it, extended
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/build/` new test classes, one per parser, and fixture directories under `pm-core/src/test/resources/ported/build/` (new)
- OBSERVED: `doc/plans/build-parsers-and-gate-honesty.adoc` — the package file, edited
- OBSERVED: `doc/specification/job-runtime/03-parsers-and-gates.adoc` — specification part; the rows of the JavaScript and Python parsers deleted
- OBSERVED: `doc/implementation-watch/job-runtime.adoc` — PM-WATCH-JOB-12 (and -11 for the npm and pytest parts if the sibling plan left it)
- HYPOTHESIS: `pm-core/doc/developer/core-build.adoc` and `pm-core/doc/adr/core-build/` — created by the sibling plan, extended additively (shared bookkeeping once they exist, R19)

## Dependencies and Sequencing

- Depends on: PLAN-V2-83 (`skills-conformance-foundation`: the dependencies and rule R20); and then: PLAN-V2-08 (`native-build-parsers-jvm`) (the result model it delivers in `core.build`).
- Overlaps with: disjoint from PLAN-V2-18 (`coverage-report-parsers`), PLAN-V2-51 (`adaptive-timeout-learning`) (new classes only); shares the package file, `job-runtime/03-parsers-and-gates.adoc` and `implementation-watch/job-runtime.adoc` with every plan of the package, in different sections. No live plan (PLAN-46, PLAN-105, PLAN-V2-39, PLAN-V2-43 included) names `core/build/`.
- Adjacent to: PLAN-V2-08 (`native-build-parsers-jvm`) (the model); PLAN-V2-36 (`failed-unparsed-and-parser-wiring`) (dispatches all nine parsers).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-28-native-build-parsers-npm-and-python.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

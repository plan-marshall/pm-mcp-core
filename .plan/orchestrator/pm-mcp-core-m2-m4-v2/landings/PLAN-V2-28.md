# Landing Analysis: PLAN-V2-28 — Native build parsers: npm, TypeScript, ESLint, Jest, TAP, pyproject

epic: pm-mcp-core-m2-m4-v2
workstream: WS-07
pr: #285 (merge commit `5de1f9bc`, `5de1f9bc059946cfe2867fc5be37d59460bfefc0`; 81 files, +3864/-87)

> Landing record for one shipped plan. Lives at `landings/PLAN-V2-28.md`. A pasted claim is a lead, never a fact.

Source: inbox message `rb-v2-28-native-build-parsers-npm-and-python-001.md` (runbook lane, harness opencode, model
`deepseek-v4.1-flash/high`, effort high, executor 0.1.2041). Corroborated: `ci pr view 285` `merged`, merge sha as above;
`ci checks status`: `overall_status: success`. Lane-stated SonarCloud read of the PR: gate green, coverage 87.7 %. Sonar `main`
(analysis of `11ef1250`, which contains this merge): gate OK, 0 open BUGs.

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|---|---|---|
| Six parsers (npm error, TypeScript, ESLint, Jest, TAP, pyproject) + `EslintResult` | shipped | all present in `pm-core/.../core/build/`; roster closure over the originals' `def test_` cases holds (31, 34+26; 57 pyproject cases) |
| Fixtures with `diagnostic` / `diagnostic-count` lines | shipped | every new `.txt` fixture carries one, so PLAN-V2-08's I3 does not recur in the fixtures |
| `unmeasured`, never `0`; `no_tests_ran` from a positive marker | **partly** | see L3-2: the verdict is unreachable for pyproject and constant-false for four parsers |
| ADR 003, developer guide, Traceability, PROVENANCE, watch edit | shipped, with internal contradictions | S4/I4 |

Surface delta: `pm-providers/` reformatting and `DerivedFactRefreshTest.java` touched by the pre-commit profile (disclosed).

## Metrics and Anomalies

- Tokens / plugin_version `unknown` (open defect). Self-review four rounds clear (round 1: 18 findings). CodeRabbit (required)
  reviewed `533f0f7c`, 12 inline threads handled, later heads refused on quota; Sourcery approved. No operator question.
- Pin drift again: the spec pins the originals at `b1609ec3e`, the lane ported from `7b7e08f0` (`BuildLogFixtures.java:63`), the
  same drift as PLAN-V2-08 S1.

## Three-Layer Analysis (standing rule R21, r6)

Done at `11ef1250` by a read-only sub-agent (it also read the Python originals); points marked **checked** re-read by the
orchestrator. Nothing was built or run.

### Layer 1 — Specified
- **S1 (medium).** "Positive marker" carries two meanings again (PLAN-V2-08 S2): the success banner and the tool's own no-tests
  statement. The spec never says whether the four npm parsers have one, nor what Jest ("No tests found, exiting with code 1") or
  pytest (exit code 5, "no tests ran") must report; PM-WATCH-JOB-11 says "a test filter matching nothing reports zero tests",
  deliverable 3 says "never 0".
- **S2 (low).** "`unmeasured` where counts cannot be aggregated" has no definition of "cannot" (pytest `error`, `xfailed`,
  `deselected` unaddressed). "the ported module's own expected values" and "ADRs where a decision was taken" are unmeasurable.
- **S3 (low).** The spec lists `_npm_cmd_parse.py` as a source but no deliverable ports it; its "only file of the package" claim
  was already false (the lane refuted it).

### Layer 2 — Implemented
- **I1 (medium, checked).** `PyprojectOutputParser` status comes from filed ERROR diagnostics only (`:163-164`):
  `3 failed, 2 passed in 1s` without `FAILED` lines is `SUCCESS` with `successMarker` true. The original used `FAILED`/`error` markers.
  Not declared in ADR 003; Jest and TAP got the correction.
- **I2 (low).** PLAN-V2-08 follow-ups not taken: `BuildLogText.read` still `Files.readAllBytes` with no size cap (`:62`, checked);
  `JvmCategoryPatterns.java:30-31` stale Javadoc (C5); Jest count line is a substring `find()` (`:164`, checked) while TAP anchors;
  overflow fixtures only for TypeScript and ESLint.
- **I3 (low).** `PortedExpectations.assertDiagnostics` still returns silently when no `diagnostic` line exists (~`:228`).
- **I4 (low).** ADR 003 contradicts itself: lines 82-85 say a spaced `FAILED` id is filed, the "held" bullet at 123-124 says it is
  reported truncated; line 142 counts ten held surprises, the list has eleven. `core-build.adoc:200` ("`noTestsRan` is `false`
  throughout", "no tool of the six prints a positive 'no tests' marker") contradicts `PyprojectOutputParser`'s `NO_TESTS_MARKER`
  (`:165-166`, checked). PM-WATCH-JOB-11 now claims the guard for "all nine parsers" although it is constant-false for five.

### Layer 3 — Correct
- **C1 (medium, structure checked, timing not shown by reading).** Quadratic regexes: `SUMMARY_LINE` (`:99-100`,
  `^.*\b(?:passed|failed|skipped)\b.*\bin\s+[\d.]+s.*$`, run on every line, checked), `EXCEPTION_LINE` (`:94`, long space run in an
  `E` line), the `=+` header pattern (`:87`), and `substring(end)` copies in the `.py` scan loops. Same class as PLAN-V2-08 C3, now
  repeated; no size cap on the input (I2).
- **C2 (medium).** Status misses a printed failure: ESLint (`EslintOutputParser.java:79`, checked: from filed diagnostics only, so
  unplaced error rows give `SUCCESS` while `problems().errors()` > 0; `core-build.adoc:178` states it, no fixture covers it); pytest
  (I1); npm (`NpmErrorParser.java:86`, checked: `diagnostics.isEmpty() ? SUCCESS` — `npm ERR!` lines without a code line yield
  `SUCCESS`, the claim that such lines are the only input is not shown by reading).
- **C3 (medium).** `noTestsRan` is unreachable for pyproject: `ParsedBuild.noTestsRanFrom` (`:80`, checked) needs
  `successMarker && testCounts == null && marker`, and `successMarker = hasPassedSummary(lines)` (`:160`, `:170-172`, checked)
  requires a summary line; the sub-agent reads that as making `testCounts` non-empty, so `:165-166` is dead (the last step is the
  sub-agent's reading, not re-derived). `reportsNoCountForAMatchedNothingFilter` pins `no tests ran` to `SUCCESS` with null counts,
  so the exit-code-5 scenario of PM-WATCH-JOB-11 reads green. Jest "No tests found" likewise.
- **C4 (low).** pytest ignores `error`, `xfailed`, `xpassed`, `deselected` and reports `Measured` over a partial line (`:574-583`);
  mypy and ruff drop the diagnostic on line-number overflow (`:180-181`, `:195-196`) while TypeScript and ESLint keep the file.
- **C5 (low, checked).** `return new String[]{tail, null};` under `@NullMarked` (`:425`).
- CLAUDE.md conformance: **breach.** 32 `@Nullable` occurrences over the parsers; 13 private helpers return `@Nullable`
  (e.g. `NpmErrorParser.java:110`, `TypeScriptOutputParser.java:99`, `EslintOutputParser.java:144`, `JestOutputParser.java:140`,
  pyproject `:266 :284 :365 :383 :494 :527 :538 :609`, `YamlBlock.stackTrace()` in TAP) against "never as a `@Nullable` return";
  the six test classes carry `@EnableGeneratorController` but use no `Generators.*` and no one-line contract comment;
  `TypeScriptOutputParserTest.java:67` assertion message `"The file is kept"` is 16 characters. Passes: suppressions carry reasons,
  `@NullMarked`, no Mockito/Hamcrest/Random/UUID, `ParsedBuild` keeps `@Builder`.

Owner: none is certain. PLAN-V2-36 wires the parsers into the runner and decides each point "at refine" (its Standing Constraint
now names this analysis: S1, I1, C2, C3 change what `status` and `noTestsRan` mean to the runner), but its surface does not own the
parser internals (C1 regexes, size cap, the `@Nullable` returns, I2 overflow fixtures). A fix plan for the parser defects is staged
only on the operator's word (R21, r6). The `@Nullable` returns of the parsers would fall to PLAN-V2-97 (Optional conversion, core).

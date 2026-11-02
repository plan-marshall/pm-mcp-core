# Landing Analysis: PLAN-V2-08 — Native build output parsers for the JVM tools (Maven, Gradle, OpenRewrite log)

epic: pm-mcp-core-m2-m4-v2
workstream: WS-07
pr: #277 (merge commit `685fdb38`, `685fdb385ab9ef7847b5e6e9ada596017764ffb6`; 114 files, +5930/-50; supersedes #273, closed unmerged)

> Landing record for one shipped plan. Lives at `landings/PLAN-V2-08.md`. Written by the `analyze` verb after verifying
> claims against ground truth (actual code, artifacts, PR state); a pasted claim is a lead, never a fact.

Source: inbox message `rb-v2-08-native-build-parsers-jvm-001.md` (runbook lane, harness opencode, model
`deepseek-v4.1-flash/high`, effort high, executor 0.1.2041, standing rules **r4**, R01–R13, R17–R20). Corroborated against
`origin/main` (the merge diff of `685fdb38`) and the PR state read through `ci pr view` and `ci checks status`
(`merged`; `overall_status: success`).

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 — result model, counts that may not be guessed | shipped-as-specified | `Diagnostic`, `TestCounts` (sealed; `Unmeasured` has no count accessor, `TestCounts.java:32,103`), `ParsedBuild` (five components), compiled `JvmCategoryPatterns`, shared helpers; `DiagnosticTest`, `TestCountsTest`, `ParsedBuildTest` |
| D2 — Maven parser | shipped-as-specified | `MavenOutputParser` (305); trailer table, `unrecognized`, reactor aggregation, parallel-build rule; `MavenOutputParserTest` over 23 fixtures |
| D3 — Gradle parser | shipped, one undeclared drift | `GradleOutputParser` (172); the count line is whole-line anchored (see L2-3) |
| D4 — OpenRewrite log parser | shipped-as-specified | `OpenRewriteLogParser` (134); the four verdicts, each with fixtures |
| D5 — documentation and decisions | shipped, one disclosed deviation | `core-build.adoc` (206), ADRs `core-build/001` and `002`, one README line, spec rows reduced; `PM-WATCH-JOB-10` and `-18` deleted, `-11` kept because the npm and pyproject parsers still owe its guard (consistent with the spec's "where fully covered") |

Surface delta: the claimed surface was a hypothesis; the PR touches only the package, its tests and fixtures, the guide, the
ADRs, and the shared bookkeeping files (`Traceability`, `ImplementationWatch`, `Specification`, the package file, the
`job-runtime` specification and watch). No `pm-runtime` file. No dependency added. No log message added.

## Metrics and Anomalies

- Tokens `unknown`, `plugin_version` `unknown`, duration not measured: the open defect in `epic.md`, ninth recurrence.
- The spec was read at **r4**; `standing-rules.md` carries r5 (R21 binds the orchestrator only).
- Review shape: the longest of the epic. CodeRabbit (required) reviewed two heads then refused on quota; #273 was closed and
  #277 opened to get a review; six 90-minute waits followed. Merged under the lane's standing review authorization (all
  findings of both reviews fixed or answered with a reason). Operator was asked once ("wait 90 minutes").
- Sonar: 25 issues on the first head, all fixed in code; 0 on the merge head (lane-stated, public API). Sonar `main` today:
  no open issue in `core/build`; the gate is still ERROR on `new_reliability_rating` from the null-rule issues (PLAN-V2-110).
- Verification: the four gate builds are lane-reported green on the merge head; not re-run here.

## Routing and Merge Behavior

- Collision check: ran beside 110, 111, 16 and 12/11. No file collision with a running plan (`corpus cross-check`).
- Package bookkeeping: PLAN-V2-08 is plan 1 of 7 of `build-parsers-and-gate-honesty`; the package file stays, and the
  remaining plans (28, 18, 36, 37, 51, 55) edit different sections.

## Three-Layer Analysis (standing rule R21, r5)

Done at the merge commit `685fdb38` by a read-only review (a delegated reader over the package, its tests, the spec, and the
Python originals), then the points marked **checked** re-read by the orchestrator. Nothing was built or run. What only a run
could show is marked "not shown by reading".

### Layer 1 — Specified: sound in aim, internally loose on "deliberate changes" and "marker"

- **S1 (medium, checked).** The spec says "two deliberate changes" (D2: trailer table, `unrecognized`) but D1 and Done-when add
  four more behaviours, and the ADR requirement names a third set. The trailer table is not a change at all: the original at
  the pinned head already carries it (`_maven_cmd_parse.py:50-72`), so "never an expected value taken from the original"
  cannot hold for it. The spec pinned `b1609ec3e`; the lane derived from `7b7e08f0` and recorded it. The PR rewrote the
  specification text to "five deliberate changes".
- **S2 (medium, checked).** "Positive marker" means two things: the success banner (D1) and the tool's own no-tests statement
  (`no_tests_ran` exists only with a positive marker). The spec never names the Maven or Gradle no-tests string; the lane
  chose `No tests to run.` and `No tests found for given includes`.
- **S3 (medium, checked).** The spec does not say what a log with no count line at all reports. D1 says the population is
  `measured` or `unmeasured`; the implementation returns an empty `Optional<TestCounts>`, which is neither. PLAN-V2-36 will
  consume it.
- **S4 (low).** `severity` is a field with no stated derivation (Maven: the printed level; Gradle: the category).
- **S5 (low).** Several Done-when clauses are process gates or not mechanically checkable ("expected values equal the
  original's" when no Python runs; "where fully covered" for the watch deletion).
- **S6 (low).** Nothing is said of hostile input, integer overflow or log size; the lane added overflow handling on its own.

### Layer 2 — Implemented as specified: met; one undeclared drift, and weak parser-level tests

- **D1–D5 met** against the spec text; every Done-when clause is backed by a fixture or file (provenance pinned in
  `ported/build/PROVENANCE.adoc`; no `ProcessBuilder` and no sibling path in the build tests).
- **I1 (low-medium, checked).** `GradleOutputParser.java:45` anchors the `N tests completed` line with `^\s*…\s*$` and
  `matches()`. The original reads the count with a substring search over every match. A prefixed line (`> 5 tests completed,
  2 failed`) no longer counts. ADR 002 lists the sum-over-blocks change but not this anchoring. Whether real Gradle ever
  prefixes the line: not shown by reading.
- **I2 (low-medium).** The overflow paths (`MavenOutputParser.java:261-271`, `GradleOutputParser.java:139-141`,
  `OpenRewriteLogParser.java:102`) have no parser-level fixture; only the helper-level tests (`TestCountsTest`,
  `JvmFileLocationTest`) cover them. A mutation that dropped the overflow guard in a parser would not fail a parser fixture.
- **I3 (low).** `PortedExpectations.assertDiagnostics` (`:206-208`) returns without asserting when a fixture lists no
  `diagnostic` lines; only an explicit `diagnostic-count` pins the count then. Whether any fixture omits both: not shown by
  reading.
- **I4 (low).** The ported tests are data-driven with hard-coded roster counts (35, 16, 57) and hand-derived expectations; the
  honesty of those expectations rests on the manual mutation checks the lane reports (six, not re-run here).

### Layer 3 — Implementation correctness: no defect that corrupts a count; three honest limits and one stale Javadoc

- **C1 (medium, checked).** `MavenOutputParser.java:278-282`: any ambiguous count line makes the whole population
  `Unmeasured`. A green build with `Failures: 1` (`testFailureIgnore`) therefore loses its failed signal
  (`deliberate-unmeasured-on-success.txt`). ADR 002 admits it and defers it to PLAN-V2-36; it is a real loss for gate honesty.
- **C2 (medium, checked).** `detectStatus` (`:189-194`) returns `FAILURE` when `[ERROR]` appears anywhere, even beside
  `BUILD SUCCESS`; `status` and `successMarker` can disagree, and `noTestsRan` cannot be true for such a log. This is ported
  and documented in the `ParsedBuild` Javadoc.
- **C3 (medium for hostile input, checked for structure; timing not shown by reading).** Polynomial regexes on a single hostile
  line: `EXECUTION_HEADER` (`MavenOutputParser.java:84`, `\s.*@.*---`, run on every line) is quadratic to cubic on repeated
  `--- a:b:c ` text; `OpenRewriteLogParser.FINDING` (`:53`, two lazy `.+?` groups under `find()`) is quadratic per start.
  `BuildLogText.read` has no size cap. The inputs are build logs of the operator's own builds, so the exposure is a stalled
  daemon on a crafted log, not a remote attack.
- **C4 (low, checked).** The Gradle no-tests marker is `startsWith("No tests found for given includes")`; real Gradle usually
  fails the task in that case, so the green-and-marker combination may be rare. Not shown by reading.
- **C5 (low, checked).** `JvmCategoryPatterns.java:30-31` says `maven()` "treats a message that carries none of the tag's
  characters as unclassified". No such code exists (a grep finds only the sentence); the patterns are simply escaped. The
  Javadoc is stale.
- **C6 (low, ported).** `FAILED` is a case-insensitive regex, so any Gradle line containing "failed" is `TEST_FAILURE`. The
  lane declined this CodeRabbit finding as the original's behaviour; ADR 002 does not carry it.
- **Checked and fine:** aggregation sums every summary block (per-class lines only as a fallback); the parallel banner
  withholds `byRunner`; counts are read as `long` and `from()` rejects a negative `passed`; the `noTestsRan` rule is stated
  once and enforced in the `ParsedBuild` constructor; a drifted OpenRewrite line is `PARSE_ERROR` (fail-closed); the dedup key
  equals the original's.

### CLAUDE.md conformance

No miss found in production code: `Optional` is used for returns and locals only; no `@Nullable` returns; `@Builder` on
`ParsedBuild`, `Diagnostic`, `RewriteFinding` and `TestCounts.Measured`; static-only classes are `@UtilityClass`; no generic
catch or throw; `package-info` carries `@NullMarked`; the eight `@SuppressWarnings("java:S8786")` each carry a reason; no
`NOSONAR`. One trivial redundancy: `public static` on the interface method `TestCounts.from` (`TestCounts.java:134`).
Tests: 148 assertion calls, none outside the 20–60 range on the literal (the five short ones are suffixes of a prefixed
label); generators in the invariant tests, fixtures as golden files; no Mockito, Hamcrest, `Random` or `UUID.randomUUID`.
This is a cleaner result than PLAN-V2-12 and PLAN-V2-11, which both had `@Nullable` returns and bare suppressions.

### Verdict

Shipped as specified; the lane's honesty claims check out against the code. **No credential or count-corrupting defect.**
The findings are owned as follows: C1 and the no-count-line gap (S3) by PLAN-V2-36 (the wiring plan that reads the model);
S1/S2/S4 by nobody (specification wording); C3, C5, I1–I3 by nobody. They go to the operator, and become a staged fix-plan
spec only on the operator's word.

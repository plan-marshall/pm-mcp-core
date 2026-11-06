# Landing Analysis: PLAN-V2-36 — Failed-unparsed evidence and parser wiring

epic: pm-mcp-core-m2-m4-v2
workstream: WS-07
pr: #297 (merge commit `99199666`; 20 files, +1267/-48)

> Landing record for one shipped plan. Lives at `landings/PLAN-V2-36.md`. A pasted claim is a lead, never a fact.

Source: inbox message `rb-v2-36-failed-unparsed-and-parser-wiring-001.md` (runbook lane, harness opencode, model
`deepseek-v4.1-flash/high`, effort high, executor 0.1.2041, standing rules r6; all 5 deliverables reported met, three
self-review rounds, CodeRabbit reviewed three heads under its quota). Tokens and plugin version `unknown` (open defect).
Corroborated: `ci pr view 297` `merged`; `ci checks status` `overall_status: success` (20 checks); Sonar `main` (analysis of
`99199666`, 2026-11-05 21:19 UTC) gate OK, 0 open BUGs; `Maven Build` and `Integration Tests` push runs of `99199666` green.

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|---|---|---|
| D1 parser seam receives the job | shipped | `OutputParser.parse(JobRecord, String)` called with `current` in `settle`; `parserReceivesTheRecord` is `@EnumSource(BuildSystem.class)` |
| D2 `BuildOutputParser` adapter | shipped in form | ANSI strip before npm marker choice (`BuildOutputParser:80`); key parsed by prefix before `:`; see C1 for the effect on npm |
| D3 `failed_unparsed` with evidence | **partly** | `UnparsedEvidence` bounds enforced in the canonical constructor; runner keeps `<job_id>.log`; **no production caller** (checked: the type occurs only in `core/build` main sources) |
| D4 settlement seam | shipped | only `FATE` and `SLOT_RELEASE` refused; two-arg `of` and `JobRecoveryStep` unchanged |
| D5 documentation | partly | guides, ADR `runtime-job/015`, `core-build/004`, watch and traceability present; see S2 |

## Three-Layer Analysis (standing rule R21, r6)

Done at `99199666` by a read-only sub-agent (no build, no test); the points marked **checked** re-read by the orchestrator.

### Layer 1 — Specified
- **S1 (medium).** Done-when and Objective promise an excerpt with the resource URI on a `failed_unparsed` build, but the failure
  envelope belongs to `remediation-units`; the criterion is satisfied by form (a factory plus a test that calls it). The lane says so.
- **S2 (medium).** D5 says to delete the implemented § Unparseable Output Fallback, but the envelope bullet is not implemented; the lane
  rewrote the section "Implemented by ... Remaining: ...". The deleted sentence "the gate's caller routes the failure to its build
  remediation policy" now lives only in the plan text. Not checked whether the `remediation-units` spec carries it.
- **S3 (low).** Budget key written `build:<system>:<verb>:<module>` in D1, `<system>:<verb>[@module]` in the code and hypothesis.
- **S4 (low).** D4 names two seams; five steps are `notImplemented` in `JobSettlement`, all five replaceable.
- **S5 (medium).** "Decide each of PLAN-V2-28 S1, I1, C2, C3 at refine" has no measurable check; the positive-marker ambiguity (S1) is
  not decided and `noTestsRan` (C3) is absent from the guide's "four limits".
- `spec_differ: true` explained: the ledger worktree copy had one more bullet (the PLAN-V2-28 landing); the lane read the fuller copy.

### Layer 2 — Implemented
- **I1 (medium, checked).** `JobSettlement.run` catches `RuntimeException` in production code under `@SuppressWarnings("java:S1181")`.
  CLAUDE.md allows it only for a last-resort handler a specification names; the Javadoc says the spec names it, but the cited
  "Write order at job end" only says the first failing step stops the rest. The key `S1181` is the Throwable rule. Only
  `e.getMessage()` is kept (may be null, no stack).
- **I2 (low).** The replacement map is not passed on the recovery path: PLAN-V2-51 and 55 steps will not run for recovered jobs; the guide is silent.
- **I3 (medium, checked in part).** Assertion messages outside 20-60 or missing: `BuildOutputParserTest` (none at 65, 79, 93, 101, 114,
  126 ... 306; line 154 `"no row was placed"` = 17 chars), `UnparsedEvidenceTest:117-121, 133-134` (`assertThrows` without message),
  `JobSettlementTest:440-453`, `SubprocessJobRunnerTest:1898`; the rest of `SubprocessJobRunnerTest` not scanned.
- **I4 (medium).** `BuildOutputParserTest` has no generator (plan id, job id, `"ab".repeat(32)`, instants literal); `UnparsedEvidenceTest`
  one `Generators.letterStrings`, ids literal; `SubprocessJobRunnerTest` `"plan-one"`. The log fixtures are defensible as the contract; the identifiers are not.
- **I5 (low).** Three new anonymous `SettlementStep` implementations are new hand-written doubles.
- Clean: no `Random`, `UUID.randomUUID` or Mockito; no new `@Nullable` return (parameters only); no new log record.

### Layer 3 — Correct
- **C1 (high for operations, medium as a defect; checked).** `Jest`, `Tap`, `TypeScript`, `Eslint` and `NpmError` build `ParsedBuild` with
  `successMarker=false`; pyproject sets it only in the pytest branch (`PyprojectOutputParser:160`). A zero-exit `npm test`, `lint`, `tsc`,
  and a mypy-only or ruff-only log therefore ends `indeterminate` (row 8), never `succeeded`; `zeroCountIsNoMarker` pins it; no test
  shows an npm job succeeding; the guide names it in one sentence, not as a limit.
- **C2 (medium).** `UnparsedEvidence.of` does not strip ANSI (the adapter does); by the regex (not run) `SecretRedactor` misses a label split by
  an escape; escapes use the 4 KB budget and the cut may leave a partial escape; `\r` frames count as lines (`String.lines()`).
- **C3 (medium, secret leakage).** Redaction runs on the 50-line tail only; no pattern for PEM blocks, URL userinfo or `--password x`;
  the ADR does not name the gaps (the redactor's narrowness was mandated by the spec).
- **C4 (medium).** The adapter puts the unbounded parsers on the settlement path: `BuildLogText.read` has no size cap, `JobLog` reads the whole
  file, `of()` lists every line, and the PLAN-V2-28 quadratic regexes now run on every settled pyproject job without a timeout. A parser throw is
  contained by the last resort; a CPU hang is not (unverified, would leave the record `running`).
- **C5 (medium).** The PLAN-V2-28 status defects are handled well: a failing count without a `FAILED` line is contradicting, an ESLint summary alone is
  contradicting. A failing build with counts but no located diagnostic still ends `failed_unparsed` (row 10).
- **C6 (medium).** Maven `Unmeasured` counts are not contradicting, so BUILD SUCCESS with an ambiguous count line is `succeeded` (a recorded limit);
  `noTestsRan` is ignored by the adapter.
- **C7 (low).** npm tool selection takes the first match (`error TS\d+` beats Jest, e.g. ts-jest); `NPM_MARKER` matches only `npm ERR! ` (newer npm prints `npm error`; unverified).
- **C8 (low).** For a log under 50 lines the launch report enters the excerpt; the plan id is not checked for URI safety. Replacement steps are shared across jobs; thread-safety is not documented.

## Verdict

D1 and D4 are met; D2 in form; D3 and D5 partly (no consumer of the evidence until `remediation-units`). No wrong-fate bug is shown for Maven, Gradle or
pytest. The operational defect is C1 (npm-family jobs never succeed); the secret-handling gaps are C2 and C3. Test-conformance debt is real (I3 to I5).

## Ownership

- PLAN-V2-90 (runtime-tests-jobs, staged): I3, I4, I5 for `BuildOutputParserTest`, `JobSettlementTest`, `SubprocessJobRunnerTest` (bullet added to its spec).
- PLAN-V2-87 (running): `UnparsedEvidenceTest` literals and messages, only if its surface covers `pm-core/.../build` (not checked; otherwise none).
- PLAN-V2-99 collides with `ParseResult`/`Settled` (risk, not an owner); PLAN-V2-97 owns the `ParsedBuild.testCounts` `@Nullable` return; PLAN-V2-51 and 55 read I2.
- No owner: S1, S2, S5, I1, C1 to C8 (parser internals, the size cap, success markers, the redactor, ANSI in the excerpt). A fix plan only on the operator's word (Wa69).

envelope_version=1
sender_type=plan
sender_id=rb-v2-28-native-build-parsers-npm-and-python
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-05T01:49:39Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-28-native-build-parsers-npm-and-python
epic=pm-mcp-core-m2-m4-v2
pr=#285
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2041
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
step.sonar-roundtrip.open_issue_count=n/a
step.sonar-roundtrip.open_issue_rules=n/a
```

guard: none on this harness (unenforced)

# Landing — PLAN-V2-28 native build parsers (npm, TypeScript, ESLint, Jest, TAP, pyproject)

Standing rules: r4 — R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R17, R18, R19, R20.
Spec read from the ledger worktree (`spec_source: ledger_worktree`, `spec_differ: true` — the main checkout's copy differed).
Executor skew: `match` (executor 0.1.2041 = lane 0.1.2041).

## 1. Deliverables

| Deliverable | Done-when | Order | Evidence |
|---|---|---|---|
| D1 npm errors and TypeScript | met | none | `NpmErrorParserTest`, `TypeScriptOutputParserTest`; fixtures `ported/build/npm-errors/`, `ported/build/npm-typescript/` with 31-case rosters; commit `2456e645` |
| D2 ESLint | met | none | `EslintOutputParserTest` (rows + `problems 6 2`), `EslintResult`; fixtures `ported/build/npm-eslint/`; commit `e3ff8516` |
| D3 Jest and TAP | met | none | `JestOutputParserTest`, `TapOutputParserTest`; aggregated and unmeasured fixtures; commit `21a9872f` |
| D4 pyproject (pytest, ruff, mypy) | met | none | `PyprojectOutputParserTest`; 57-case roster over both original suites; xdist and deepest-frame fixtures; commit `0796f5ef` |
| D5 Documentation | met | none | developer guide, ADR `core-build/003`, specification table replaced, watch item `PM-WATCH-JOB-12` deleted, package file, `Traceability.adoc`, `PROVENANCE.adoc`; commit `5d5b418f` |

The spec's Done-when renders as these five deliverables plus the shared `Done when:` clause, all met on the merged head:

* every diagnostic form of the six ported modules is a JUnit test with the ported module's own expected values, the pinned
  commit `7b7e08f0…` recorded in `BuildLogFixtures.PIN`, every `original:` header and `PROVENANCE.adoc` — order `none`
  (the clause claims existence and fidelity, not fail-first);
* both `tsc` forms, the ESLint rows and summary, the Jest `FAIL` block and `Tests:` line, the TAP summary, and the pytest,
  ruff and mypy forms each have a fixture — order `none`;
* the xdist normalisation (`deliberate-pyproject-xdist-group.txt`) and the deepest-frame location
  (`deliberate-pyproject-deepest-frame.txt`, `pyproject-captured-output.txt`) are shown; a multi-block run is aggregated for
  Jest, TAP and pytest; `unmeasured` is reported for Jest and TAP; no test runs Python — order `none`;
* Build: `verify -Ppre-commit` green on the final head (`0bef2a6a`, 550 s, 9016 tests), `verify` green on the merged
  `main` (`5de1f9bc`, 1008 s, 9016 tests), `verify -Pcoverage` green at `c03f0777` (638 s), and
  `verify -Pintegration-tests -pl pm-core -am` green at `c03f0777` (92 s, 21 tests) and again in CI
  (`integration-tests / test`, green on the merged head) — order `none`;
* Links: `trace.py` against the sibling documentation checkout at `d014afb` — `BROKEN LINKS 0`, job-runtime watch counts
  40/40/40 — order `none`;
* Sonar: the lane's `resolve` reports `sonar: not_configured`, so the lane's Sonar facts are `n/a` at both fields. The CI
  **SonarCloud** analysis of the pull request is configured independently of the lane and was read through the provider's
  public API: **0 open issues** and **quality gate passed** on the merge head (reliability 1, security 1, maintainability
  1, coverage 87.7 %, duplication 0.0 %). This is the read the spec's Sonar clause asks for, recorded here because the
  lane's own credential path is unconfigured — order `none`.

Per-head test runs: the corpus was run at every pushed head through the gate canonical (`verify -Ppre-commit`):
`2456e645` (9 failures, corrected), `e3ff8516`, `21a9872f`, `0796f5ef` (6 then 1 failure, corrected), `c03f0777`,
`028e517a`, `0fa2c5c9`, `620b6921`, `d0d81160`, `e40bd5cc`, `9a45ad66`, `3cd5709a`, `533f0f7c`, `18aaebea`, `0bef2a6a`.
The project publishes no `module-tests` canonical, so the gate canonical is the per-head run.

## 2. Personas and skills

* production code — `persona-implementer`; unit and module tests — `persona-module-tester`; every self-review round —
  `persona-code-reviewer`.
* R20 skills read as standards: `cui-logging`, `cui-testing`, `cui-http-testing`, `java-core`, `java-lombok`,
  `java-null-safety`, `javadoc`, `junit-core`, `junit-integration`.
* The spec declares no `Skills:`/`Persona:` lines: `skills: none named`. No skill was added for surface drift.

## 3. Hypotheses

* “The result model (diagnostic, test counts, parsed build) exists when this plan starts, delivered by PLAN-V2-08” —
  **confirmed**: `ParsedBuild`, `Diagnostic`, `TestCounts`, `TestsPopulation` are in `core.build` at the head
  (`pm-core/src/main/java/de/planmarshall/core/build/ParsedBuild.java:44`). No new model type was needed; `EslintResult`
  is added beside the model for the ESLint summary only.
* “`pm-core/src/main/java/de/planmarshall/core/build/package-info.java` is the only file of the package” — **refuted** at
  the head: the package now holds the model, the three JVM parsers and their helpers (the plan's pre-condition, verified
  before editing).

## 4. Findings

Self-review (four rounds, `persona-code-reviewer` lenses): round 1 — 18 findings (spec's stale “Remaining:” sentence,
ADR counts, ESLint summary classification, an accidental frame search on a fallback string, blank-input throws, a test that
could not fail, roster reasons, assertion lengths, `@UtilityClass`); round 2 — 7 open/new; round 3 — 1 new (a nameless TAP
block with its own error was dropped); round 4 — **no findings open**. All fixed in `028e517a`, `0fa2c5c9`, `028e517a`.

CI and review findings (CodeRabbit, 12 inline threads + 1 outside-diff): mypy `.pyi`/column forms, spaced pytest `FAILED`
node ids, the diagnostics ordering contract, the TAP stack indent, TypeScript and ESLint position overflow, the TAP and Jest
status rules, an unread Jest summary, and the ESLint total width — **fixed** in `5d926358`, `8975214a`, `bf9d6184`,
`533f0f7c`, `18aaebea`. Two were **answered, not changed**: the ESLint file-scope length and the TAP dedup key, both held
as in the original and recorded in ADR `core-build/003`'s held list. Every thread is replied to and resolved.

## 5. Reviewers

| Reviewer | Required | State | Head | History |
|---|---|---|---|---|
| coderabbit | required, quota-limited | reviewed → then refused on quota | formal review on `533f0f7c` (“no actionable comments”); earlier reviews on `e40bd5cc`, `5d926358`, `6d0e5f75` | 12 inline threads handled; re-triggered after each push; refusals at 00:34 and 01:18 recorded (“Review rate limited”, no ETA) |
| sourcery | optional, quota-limited | approved | `8975214a` | approved at every earlier head; quota exhausted, later triggers refused (“250,000 diff characters”, next window in ~6 days) |

Standing review authorization applied for coderabbit: qualifying review on `533f0f7c`, every finding of that review and of
every later one handled, re-triggered once after the last push with the refusal recorded, every other gate condition green
on `0bef2a6a`.

## 6. Operator questions

1. “The self-review hit its 3-round limit … how should I proceed?” — options: dispatch a confirming round 4 (recommended),
   accept as-is. Selected: **dispatch a confirming round 4**.
2. “My push was rejected: the branch was force-updated by another writer … how do you want me to proceed?” — options:
   adopt the remote head and finish here (recommended), stop and let the other run land it, re-apply my resolution on top.
   Selected: **re-apply my resolution on top**. Applied as a fast-forward from the remote head plus a commit carrying my
   resolution (`3cd5709a`), which the lane's no-forced-push rule required.
Free text: none.

## 7. Rule check

* Guard: `guard: none on this harness (unenforced)` — no guard log line exists for this run and none is claimed.
* Shell rules broken by me, recorded: one `grep` (a surefire report path) and one `head` inside a pipe (a `git status`
  listing) before I re-read the rule; both were read-only and neither changed a file. No `&&`, `;`, loops, heredocs or
  `sleep` were used; all builds went through the resolved `maven` verb; all CI through `ci`.
* Spec read: `spec_source: ledger_worktree`, `spec_differ: true` — recorded here so the read of the ledger copy is not
  silent.
* Executor skew: `match`.
* Sourcery's trigger was posted twice with no review produced (quota); recorded above.

## 8. Build server

Setup preflight `ready`; the daemon became unreachable during the run (`build_server_reason: unreachable` at landing), so
the later builds ran in process. Per-mechanism counts: `daemon_longpoll` 14, `in_process_fallback` 15.
Fallbacks: `quality-gate: degraded, reason=unreachable` (from the first unreachable build onward); `quality-gate:
bound-expiry at 471 s (learned bound)` — re-submitted once with `--timeout 1200` and green; `quality-gate: bound-expiry at
1200 s and at 2400 s` on the last heads — re-run with `--timeout 3600` (one run failed on the flaky
`LspClientTest.stubborn` LSP-start timeout in `pm-runtime`, a module this plan does not touch, and passed on the retry);
`verify` and `verify -Pcoverage`/`-Pintegration-tests`: `in_process_fallback`. No `killed` job. No launch-fault retry.
`runtime_stamp_reason`: plugin version could not be measured (`plugin_version: unknown`).

## 9. Residue

* The `verify -Ppre-commit` profile rewrote files it does not own: ten under `pm-providers/` and
  `pm-core/.../DerivedFactRefreshTest.java` (formatting only, commits `da5a87ea`, `e40bd5cc`); reported as an
  undeclared-surface observation.
* `PM-WATCH-STORE-56` is referenced but undefined in the documentation checkout on `main`; it is not this plan's.
* Owed to the milestone exit (R05): the concept and user documentation of the slice, and the roadmap check-off for the
  package `build-parsers-and-gate-honesty`.

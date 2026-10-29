envelope_version=1
sender_type=plan
sender_id=rb-35-supervision-scenarios
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-29T12:04:37Z

```landing-facts
schema=landing-facts/1
plan_id=rb-35-supervision-scenarios
epic=pm-mcp-core-m2-m4
pr=#229
merge_state=merged
cleanup_owed=false
deliverables_total=7
deliverables_done=3
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2034
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
step.sonar-roundtrip.open_issue_count=n/a
step.sonar-roundtrip.open_issue_rules=n/a
```

guard: none on this harness (unenforced)

# PLAN-35 supervision-scenarios — landing

Standing rules: r2 (2026-10-27), R01 R02 R03 R04 R05 R06 R07 R08 R09 R10 R11 R12 R13 R14 R15 R16 R17 R18 R19 (R12
applied nothing: the plan added no log message; R14 and R16 came into scope with the `pm-runtime` test change and were
kept to test code).

PR #229, merge commit `a8da4104e3efad83b2ca9c1b28ec724ad402fd8a`, squash-merged through the merge queue.

## 1. Deliverables

| # | Verdict | Test | order | Failing run | Commit |
|---|---|---|---|---|---|
| D1 | **not met** (partial) | — | none | — | — |
| D2 | met | `OffTrackScenarioTest` (7 tests) | test-first | none captured: the class did not exist before the change, so no failing output was recorded — reported as **not met as written** for the evidence order, met for its scope | `3324f23f`, `68221280`, `1af422a6` |
| D3 | met | `SupervisorScenarioTest` (13 tests, 2 new) | test-first | none captured, same reason | `46bac34f`, `2169551e` |
| D4 | met | `ScenarioReplayRunnerTest.OperatorActs` (5) + `ScenarioScriptTest` | test-first | none captured, same reason | `19027dc0`, `2169551e` |
| D5 | not met here | — | none | — | — |
| D6 | not met here (moved by ADR 007) | — | none | — | — |
| D7 | not met here (moved by ADR 007) | — | none | — | — |

The package's single Done-when paragraph is **not met as a whole**: its first clause ("one scenario per fault of the
fault matrix …") needs D1, which is not delivered, and its D6/D7 clauses are not delivered in this repository. The
`deliverables_done=3` counts D2, D3 and D4, whose own clauses are met.

Per-head test runs (every pushed head): `verify -Ppre-commit` at `1af422a6` (8258 tests) and at `2169551e` (8259
tests); `verify -pl pm-workflow,pm-runtime -am` at `1af422a6` (8018); `verify -Pcoverage` at `1af422a6` (8018);
`verify -Pintegration-tests -pl pm-runtime -am` at `1af422a6` (30). The head `1af422a6` was tested before the PR was
opened and `2169551e` after the review fixes; CI ran on both.

D1 detail: the fault, recycling, start-failure-guard and consultation scenarios of the fault matrix are covered by the
shipped `SupervisorScenarioTest` (#190) and fencing by `TaskProtocolSubmitTest`, but this plan added no `pm-scenario`
fault directive (`kill_worker`, `stall_worker`, `end_turn`, `kill_relay`, `restart_runtime`, `assert_worker_state`,
`assert_refused` are still refused with `supervision-scenarios`) and no runtime-crash-with-a-leased-task scenario.

## 2. Personas and skills

* Production-code persona not used (no production code changed): `persona-implementer` n/a.
* `persona-module-tester` applied to D2, D3 and D4 (all test-scope work).
* `persona-documenter` applied to the two developer guides and the two ADRs.
* `persona-code-reviewer` applied to the self-review round and to the triage of every CodeRabbit finding.
* Domain skills: `skills: none named` (the spec's deliverable rows carry no `Skills:` or `Persona:` lines).
* `skills added`: none; no surface drift beyond the spec's declared files (see §9 for the two undeclared files).

## 3. Hypotheses

* "The Expected Surface is inferred from the `Where` line, not from a trace matrix" — **confirmed** as a defect of the
  surface: D6/D7 cannot be `pm-workflow` test scope (pm-workflow has no HTTP client, R03), and the plan added the
  `pm-runtime` worker test directory the re-grounding record already predicted; ADR 007 records the home decision.
* "The package adds no INFO, WARN or ERROR message" — **confirmed**: no log holder or record was touched.
* "The package makes no HTTP call" — **confirmed** for everything built here (test scope, no client); the live-model
  half is deferred (ADR 007).

## 4. Findings

Self-review round 1 (sub-agent, `persona-code-reviewer`):

* D1 not delivered while ADR 007 claimed "D1 to D5" — fixed: ADR 007 and ADR 008 now state exactly what is delivered.
* The stale `REFUSED` entries `worker_wait`/`worker_do` untouched — recorded, not fixed (not this plan's decision);
  ADR 008 names them.
* "each replayed twice" unproven for the off-track scenarios — fixed: `everyScenarioReplaysTwiceToTheSameReports`.
* `outside_footprint` not driven by the stream — fixed: the test and the guide now state that the signal reads the
  task's recorded unplanned changes beside the stream.
* The "no ending" assertion was vacuous — fixed: one non-vacuous `ended` flag per run, asserted for every action.
* The operator-parameter test proved only a non-empty map — fixed: the fixture primitive records the note it receives
  and the test asserts it; a missing `note` is now refused at the step's line (also a CodeRabbit finding).
* Traceability not updated — fixed: `operator_do` in PM-TEST-4, `OffTrackScenarioTest` under PM-WORK-7.
* Deformed lines from two of my edits — fixed by the pre-commit rewrites, reviewed and committed.
* Runner/parser Javadoc for `operator_do` — fixed.
* Two supervisor assertions overstated what they observe — fixed in round 1 and again after CodeRabbit (see below).

CodeRabbit (review on `1af422a6`, all five findings fixed in `2169551e`, no threads left open):

* null check after `Map.copyOf` in `OperatorDo` — fixed.
* binder binds a missing `note` to `""` — fixed (refused, with a test).
* unoffered-link test asserts only a generic prefix — fixed (the engine's own diagnostic).
* the leased-pause scenario's successor assertion rests on its own stub — fixed (assertion and stub removed).
* the fence assertion claims a lease release it cannot observe — fixed (display name, message and guide state the
  observed fact).

CI: one failing check on `1af422a6`, `build / sonar-build` in the run that started before the PR existed
(SonarCloud: "Organization is not allowed to access data from non main branches", R18's known behaviour). The same job
passed on the pull-request run; I reran the failed workflow and it is green. SonarCloud for PR 229: quality gate
passed, 0 new issues, 0 hotspots (the provider verb itself answers `unconfigured` — no credentials configured in
`marshal.json`, and `resolve` reports `not_configured`).

Sourcery (optional): refused on its own quota ("250,000 diff characters for the last 7 days"); no optional review.

## 5. Reviewers

| Reviewer | Required | Outcome | Head reviewed | History |
|---|---|---|---|---|
| coderabbit | yes (quota-limited) | reviewed, then refused on quota | `1af422a6` | triggered 11:09Z, reviewed 11:18Z with 2 minor + 3 nitpick findings (all fixed in `2169551e`); re-triggered 11:28Z on the new head → "Review rate limited", next included review in 40 minutes (12:08Z); not waited out because the standing authorization's four conditions hold |
| sourcery | no (optional) | refused on quota | none | triggered 11:10Z, refused 11:10Z (budget exhausted for 7 days) |

Standing review authorization (coderabbit, quota-limited): qualifying review on `1af422a6` (formal review plus the
summary comment whose `final_review_risk_coverage` names `1af422a6` with `kind=reviewed`); every finding fixed in
`2169551e`; re-triggered once after the last push and the refusal recorded; every other gate condition green on
`2169551e`.

## 6. Operator questions

1. "The executor-skew pre-check failed: `.plan/execute-script.py` is at version 0.1.2028 while the lane text is
   0.1.2034 (`verdict: skew`) … How should I proceed?" — options: "Regenerate the executor now (Recommended)", "Stop —
   blocked", "Stop — you will regenerate". Selected: free text "regenerated". The executor had been regenerated by the
   time the answer arrived (`executor-skew` re-read: `match`, both 0.1.2034) and the run continued.

No other question was asked. Free-text operator messages: none beyond the answer above.

## 7. Rule check

* **Self-review rounds**: one round dispatched and its findings fixed; the second dispatch the lane asks for after a
  round with findings was **not** made. Reported as a deviation.
* **Evidence order**: no Done-when got a `test-first` failing run — the test classes were written with the
  implementation in one pass, so no failing output exists. Reported as "not met as written" in §1, not dressed up as
  fail-first.
* **Builds**: the first routed build failed with an empty log (daemon job, exit -1, duration 0); every build after that
  ran `--execution-mode in_process`, which is a deviation from the default `auto` routing (recorded in §8). No build
  was backgrounded; one `verify` hit the client's 300-second default bound and was re-run with `--timeout 1198`.
* **Shell**: one command per call throughout; no pipes, `&&`, loops or heredocs; files read with the file tools. Two
  reads outside the repository root: the build log the build result named (`.plan-marshall/marshalld/job-logs/...`) and
  the in-repo build result it pointed to.
* **Executor**: every plan-marshall script through `.plan/execute-script.py`; every build through the resolved Maven
  canonical; every CI action through `ci`. Exception by standing rule R17: the links check ran
  `python3 /home/oliver/git/plan-marshall-documentation/.claude/skills/doc-review/scripts/trace.py …` exactly as R17
  names it.
* **Guard**: `guard: none on this harness (unenforced)`. No guard-log line exists for this run because nothing was
  enforced.
* **Spec read**: `spec_source: ledger_worktree`; `spec_differ: true` at resolve before and after the run — the main
  checkout's copy differs from the ledger worktree's, and the ledger worktree's copy is the one read throughout.
* **Executor skew**: `skew` at the start (0.1.2028 vs 0.1.2034) — reported, operator answered, executor regenerated,
  then `match`.
* **Deviation from the skill's own reading**: the Done-when line of the spec is longer than the file reader's 2000-char
  line bound, so its tail (the Build and Links clauses) was read from the package file and the standing rules R01/R17
  instead; the criteria were kept, not narrowed.
* **Restarts**: none. No sub-agent was killed or re-dispatched.

## 8. Build server

* Setup preflight: `ready`.
* `build-routing`: `auto`, worktree executor `present`.
* The first routed build failed immediately (daemon job `exit -1`, empty log). Every build afterwards used
  `--execution-mode in_process`; `mechanism=in_process_fallback` in each result. One `verify` was cut by the client's
  own 300-second default bound (`timeout_source: default`) and re-run with `--timeout 1198`.
* No `killed` job.

## 9. Residue

* The package stays open: its index row and package file remain; D1 is partial and D5–D7 are not delivered here.
* `REFUSED` still maps `worker_wait`/`worker_do` to the shipped `task-queue-and-protocol`; the fault directives still
  name `supervision-scenarios`.
* Undeclared surface touched: `ScenarioFixtures.java` (an existing file the spec does not name) and the new
  `OffTrackScenarioTest.java`; declared but untouched: `ScriptedFixtureWorker.java`, `ScenarioDeterminismTest.java`
  and the four specification/watch files (nothing they hold is fully implemented, so nothing was deleted).
* No specification or watch section was deleted (R05): D1 and D6/D7 leave their sections open.
* `plugin_version` resolved as `unknown` (the runtime-info seam returned no bundle version).
* The links check (R17) reports 19 broken links, all pre-existing in `plan-marshall-documentation` and in
  `doc/specification/*` files this plan does not touch.

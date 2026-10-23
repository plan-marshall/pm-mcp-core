envelope_version=1
sender_type=plan
sender_id=rb-25-waits
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-23T07:28:44Z

```landing-facts
schema=landing-facts/1
plan_id=rb-25-waits
epic=pm-mcp-core-m2-m4
pr=#145
merge_state=merged
cleanup_owed=false
deliverables_total=1
deliverables_done=1
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=0.1.1986
executor_version=0.1.1986
steps=setup:done,implement:done,build-gate:done,self-review:budget-exhausted,pr:done,reviews:done,merge:done
```

# Landing report — PLAN-25 `waits`

Standing rules: r1, R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R15, R18 (read in full
before the run).

## 1. Deliverables

### D1 — the bounded long-poll and the progress-reset wait

Done-when clauses of `implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-25-waits.md`:

| Done-when | Verdict | Test | order | Failing run | Commit |
|---|---|---|---|---|---|
| With a virtual clock: a bounded wait answers at its window | met | `ModelWaitTest.shouldAnswerABoundedWaitAtItsWindow` | `mutation-after` | `9459c58` with the frame generator reduced to one frame, `maven-2026-10-22-203750` | `9459c58` |
| With a virtual clock: a progress wait emits no gap longer than the interval | met | `ModelWaitTest.shouldEmitProgressFramesWithoutAGapLongerThanTheInterval`, `shouldKeepTheBoundedWindowOfAPmWaitThatQualifiesForProgress`, `shouldHalveTheIntervalOfAWaitWhenItsWindowCannotCarryIt` | `mutation-after` | `9459c58` with one frame, `maven-2026-10-22-203750` (`a gap of PT29M55S before 2026-10-22T10:30:00Z exceeds PT5S`) | `9459c58` |
| With a virtual clock: the selection follows profile and token in all four combinations | met | `ModelWaitTest.shouldSelectTheWaitFromTheProfileAndTheToken` (4 cases), `shouldSelectTheSameWaitsForAPmWaitInAllFourCombinations` | `mutation-after` | `9459c58` with the token dropped, `maven-2026-10-22-204005` (`Expected Bounded but was Progress`) | `9459c58` |
| Sonar: no open issue other than one dismissed with its reason, quality gate green | met | SonarCloud PR search and quality gate | `none` | — | PR #145: search `pullRequest=145&resolved=false` returned `total: 0`; the gate returned `status: OK` |

Per-head test runs: `ModelWaitTest` at `9459c58` (10 tests, `maven-2026-10-22-203627`) and at `4616718` (13 tests,
`maven-2026-10-22-214324`). The full gate is `verify -Ppre-commit` (the project publishes no separate
`module-tests`/`test-compile` canonical): green at `9459c58` (6923 tests, `maven-2026-10-22-204820`), `94890ea` (6926,
`maven-2026-10-22-211217`), `c5d89d3` (6926, `maven-2026-10-22-212939`), `4616718` (6926,
`maven-2026-10-22-214339`) and `37274e5` (6931, `maven-2026-10-23-055003`). CI is green on `37274e5`.

Commits: `9459c58` (the wait types, selection, tests, guide, ADR, traceability, index and spec note), `94890ea`
(self-review round 1), `c5d89d3` (round 2), `4616718` (round 3), `f7a3ec2` (a blank line), `0f71605` (Sonar S5778),
`5f2ce54` (CodeRabbit wording), `37274e5` (merge of `origin/main`).

## 2. Personas and skills

* Production code (`ModelCall`, `ConnectionSettings`, `ModelWait`, `WaitSelection`, `WaitSettings`): `persona-implementer`.
* Unit tests (`ModelWaitTest`): `persona-module-tester`.
* Documentation (`workflow-engine.adoc`, ADR 012, Traceability, spec note): `persona-documenter`.
* Every self-review round and every PR comment: `persona-code-reviewer`.
* The spec declares no `Skills:` or `Persona:` line: `skills: none named`. No skill was added for surface drift; the
  only `pm-runtime` changes in the diff come from the merged `origin/main` and are not this plan's edit.

## 3. Hypotheses

* `HYPOTHESIS: part of this package lands in workflow.hypermedia` — **refuted**. The What bullet names only the
  bounded long-poll and the progress-reset wait; the composite wait is `finalize.concurrent-wait` (PM-WF-9) and has no
  deliverable here (`doc/plans/waits.adoc:11-13` before deletion; `PLAN-25-waits.md:34`).
* `HYPOTHESIS: the Expected Surface is inferred from the Where line` — **confirmed in part**: engine code/tests and
  the module docs/ADRs; the hypermedia paths are unused for the reason above.
* `HYPOTHESIS: the package adds log messages` — **refuted**: `WorkflowLogMessages` is unchanged.
* `HYPOTHESIS: the package makes no HTTP call` — **confirmed**: no HTTP type is used.
* `OBSERVED: the waits extend RestingWait and the engine step loop` — **refuted by design**: the waits a model holds
  are call-hold windows, not engine-state waits; `RestingWait`, `EngineEvent` and the step loop are unchanged
  (`pm-workflow/src/main/java/de/planmarshall/workflow/engine/WaitSelection.java:1-80`).

## 4. Findings

Self-review:
* Round 1: blocker (README self-dependency), major (interval floor/OOM), minor (unreachable bound, select-time
  exception, zero-frame progress, stale package descriptions), nits (ADR 002 relation, brittle count, missing boundary
  tests, partial spec list, refutations not recorded). All fixed in `94890ea`; the refutations are in the PR body and
  here.
* Round 2: minor (zero-frame progress at interval >= window), nit (blank line). The zero-frame rule was fixed in
  `c5d89d3` (halve the interval when the window cannot carry it); the blank line did not land.
* Round 3: nits (`Progress` allowed interval == window, stale wording, blank line). Fixed in `4616718` and `f7a3ec2`.
  The round limit was reached; the operator selected "Proceed to the pull request", so the state is
  `budget-exhausted` and the round-3 fixes are gate-verified, not re-reviewed.

PR comments:
* CodeRabbit inline (guide, idle-margin wording): fixed in `5f2ce54`; CodeRabbit confirmed and the thread is resolved.
* CodeRabbit inline (`WaitSelection`, clamp the tokenless window to the idle margin): declined with the reason that the
  specification fixes the window and the margin is a profile-validation rule; CodeRabbit withdrew the finding and the
  thread is resolved.
* CodeRabbit review-body nit (share the bounded-wait limits with `ProfileValidator`): answered — different modules and
  contracts; the loader's `idle − 30` gap is pre-existing and recorded as residue.
* Sonar: 9 `java:S5778` issues in the test lambdas; fixed in `0f71605`. Final PR search: 0 open issues.
* Sourcery (optional): refused on its 7-day budget; not a gate.

## 5. Reviewers

| Reviewer | Required? | State | Head reviewed | Trigger / wait history |
|---|---|---|---|---|
| coderabbit | yes, `quota_limited` | reviewed, then refused on rate limit | `f7a3ec2` (formal review) | triggered 2026-10-23T05:00; reviewed 05:06; re-triggered 05:25 after `5f2ce54` — refused; re-triggered 06:14 after `37274e5` — refused ("Review rate limited", next included review 56 minutes later) |
| sourcery | no, `quota_limited` | refused | — | triggered 04:59 — refused on its 7-day budget of 250,000 diff characters |

Standing review authorization (quota-limited coderabbit): a qualifying formal review exists on `f7a3ec2`; every
finding of it is fixed (`5f2ce54`) or answered with a reason (the tokenless-clamp and the limits nit), and both
threads are resolved; it was re-triggered after the last push and the refusal is recorded; every other condition holds
on the current head `37274e5` (CI green, `verify` green, Sonar 0 issues and gate OK, every comment handled). The
reviewed head is therefore not the current head, under the section-7 quota exception.

## 6. Operator questions

1. "The self-review reached its 3-round limit. Round 3 reported three nits; all three are now fixed at head f7a3ec2
   and the full gate is green, but no fourth review round has confirmed them. How do you want to proceed?" — options
   "Proceed to the pull request" / "Fourth self-review round"; selected **"Proceed to the pull request"**.
2. "The merge queue dequeued PR #145. The merge-group run 39911304700 failed in `build / build (25)` because Maven
   Central answered 403 while resolving `org.apache.maven.surefire:surefire-junit-platform:pom:3.6.0` for pm-core — an
   infrastructure failure, not a code failure. All PR checks on 37274e5 are green, Sonar has 0 open issues, and the PR
   is no longer in the queue. How do you want to proceed?" — options "Re-enqueue the PR" / "Stop before the merge";
   selected **"Re-enqueue the PR"**.

No free-text operator messages.

## 7. Rule check

guard: none on this harness (unenforced). No guard log exists for this run.

* R01: the package was worked as the index says (branch, commits, PR; the row and package file were removed in the
  commits; the `Depends on` cells were updated). Not fully met: the `traced-implementation` trace matrix was not built
  from the sibling repository (the lane forbids reads outside the repository root), and `verify -Pcoverage` and
  `verify -Pintegration-tests` were not run — the lane's build gate is the resolve-supplied `quality-gate`
  (`verify -Ppre-commit`) and `verify`. CI runs the integration profile.
* R02–R04: Java 25 code; no HTTP call, so `cui-http`/mockwebserver do not apply.
* R05: the code, tests, Javadoc, developer guide, ADR, traceability lines and the spec note are in this PR; the
  implemented rows of Timeouts § Waits were kept rather than deleted because other specification parts anchor them,
  and the section points to the developer guide instead. That is a partial deviation from R05's "deletion of the
  implemented specification sections".
* R06: `workflow-engine.adoc` extended and ADR `workflow-engine/012` added, with the template sections.
* R07: no design choice that changes the file set was left open; the interval and window rules are in ADR 012.
* R08: only the package's row (and its name in the `Depends on` cells) was removed from `doc/plans/README.adoc`; only
  this package's requirements were added to `doc/Traceability.adoc`.
* R09–R10: `core.store` confinement and the specification-reader blocks were not touched.
* R11: ADR 012 is the next free number in `pm-workflow/doc/adr/workflow-engine/` against `origin/main` (011 was the
  highest).
* R12: no log message was added.
* R13: no versioned artifact changed.
* R15: none of its six checklist items applies to this diff (no parser, verifier, line-oriented reader, printer/reader
  pair, enum expected-set elsewhere, caller-specific exception or generalised concept).
* R18: PR #145's Sonar search returned 0 open issues and its quality gate is green.

One build-side effect was not a rule break but is recorded: the gate's OpenRewrite step rewrote five unrelated
`pm-runtime` sources in the worktree. The changes were identified as tool output (they conflicted with the corrected
`origin/main`), reverted with the reverse of their own diff before the base merge, and never staged or committed.

## 8. Build server

* Setup preflight at the worktree: `ready` (registered, daemon reachable). The setup-time `resolve` reported the
  project root as `down` with reason `unreachable`; the worktree preflight is the authority for the worktree and said
  `ready`.
* The first routed build re-ran the worktree's own executor and failed with `executor_absent_from_worktree` (the
  worktree has no `.plan/execute-script.py`). Every later build ran with `--execution-mode in_process`,
  `mechanism=in_process_fallback`; no `degraded` result and no `killed` job.
* One bound expiry: the first post-merge gate hit the explicit 617 s bound (5266 tests passed, no failure,
  `maven-2026-10-23-053849`); the gate re-ran with `--timeout 1800` and was green (6931 tests,
  `maven-2026-10-23-055003`).
* No `runtime_stamp_reason` was carried.

## 9. Residue

* PM-WATCH-JOB-30 (every wait maps to exactly one `wait_reason`) stays open: wait attribution spans the whole engine,
  not this package.
* The composite wait (`finalize.concurrent-wait`) and the TOON encoder sections of `hypermedia-format/03-…` remain; the
  `workflow.hypermedia` package is untouched.
* The MCP tool binding of `pm_wait`/`pm_build` and the relay's `notifications/progress` sender are not in this
  repository.
* `pm-core`'s `ProfileValidator` accepts a bounded wait above `idle_timeout_seconds − 30` when progress resets no timer
  (it rejects only `bounded >= idle`); the specification requires the 30-second margin. This is pre-existing and out
  of this package; `ConnectionSettings` enforces the specified rule, so a loader-accepted profile in that narrow band
  is refused when a wait is built.
* Owed to the milestone exit, in `plan-marshall-documentation`: the concept and user documentation of the waits and
  the roadmap check-off.

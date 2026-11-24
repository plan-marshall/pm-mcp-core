envelope_version=1
sender_type=plan
sender_id=rb-v2-183-sweep-test-and-statement-residue-closeout
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-24T18:01:39Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-183-sweep-test-and-statement-residue-closeout
epic=pm-mcp-core-m2-m4-v2
pr=#603
merge_state=merged
cleanup_owed=false
deliverables_total=3
deliverables_done=3
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2184
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=0
review_body_findings_handled=0
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced)

# PLAN-V2-183 — the sweep test's and the cut log's fourth closeout residue

Standing rules: r7 (2026-11-10), applying R02, R05, R06, R08, R10, R11, R13, R14, R18, R19, R20. Read completely before the first edit; R21 binds the orchestrator's analysis, not this plan. Skills read (R20): `pm-dev-java:java-core`, `pm-dev-java:java-null-safety`, `pm-dev-java:javadoc`, `pm-dev-java:junit-core`, `pm-dev-java-cui:cui-testing`.

PR #603, merged as `e935ab2f` through the merge queue. Branch `feature/v2-183-sweep-test-and-statement-residue-closeout` and its worktree removed; `main` switched and pulled to `e935ab2f`. `cleanup_owed=false`: no base worktree was created (no Done-when named a base sha).

## 1. Deliverables

### Deliverable 1 — the sweep test's fixtures and its refusal — met

- (a) met — order `none` (a fixture's resource handling; no behaviour pinned by a test could be failed first). `PendingEntryRecordsSweepTest.aFaultedLockReleaseKeepsTheFirstFaultsReach` now opens the real delegate transaction only after `assumeFalse` and closes it in a `finally`; `LockTransaction.close()` is a documented no-op on a closed transaction, so the normal-path double close is safe. Commit `d5ce5164`.
- (b) met — order `none`. `RELEASE_FAULT` (free-text literal) replaced by `releaseFault = Generators.letterStrings(10, 40).next()`. Commit `d5ce5164`.
- (c) met — order `none`. The undecided-arm test asserts `Store cannot be written: <journal path>` (the production text of `AtomicStoreFile`) instead of the shared substring `cannot be written`. Commit `d5ce5164`. The class Javadoc names the release-fault tests.
- Done-when clause "the tests of deliverable 1 pass on a non-root runner and skip cleanly on a root runner with no open transaction left": **non-root half run-evidenced** — `PendingEntryRecordsSweepTest` 21 tests green at `d5ce5164` (log `3cea0c749ec44a7fa81f9ed2e53c9437`), 22 at `44e586d6` and at the head `a1c969f1` (log `64f5bfb287184016b0e75338e07d39c9`), `Tests run: 22, Failures: 0, Errors: 0, Skipped: 0`, so the POSIX-permission tests executed; **root half argued from the code, not run** — no root runner exists on this host (uid 1000), and the delegate is now opened after the assumption and closed in a `finally`, so the skip path opens none. Self-review accepted this as met on that basis; the analysis may weigh it.

### Deliverable 2 — the release fault's message and the suppression — met

- (a) met — order `test-first`. `RemovalState.faultBeside` now takes the release exception and names it by type when `getMessage()` is null (`IllegalStateException with no message`), keeping the message-bearing wording unchanged. Test `PendingEntryRecordsSweepTest.aReleaseFaultWithoutAMessageIsNamedByItsType`: **failing run** on the tree of `d5ce5164` with the new test added and the fix absent (the test was uncommitted then; the ledger row records no worktree sha) — build log `2a4744a5de9d48108ef1f03c0d4d3c50`, Maven log `.plan/local/plans/NO_PLAN/build-results/pm-runtime/maven-2026-11-24-165634.log`, 2 assertion failures (`named by its type ==> false`, `no null … ==> true`); **passing at `44e586d6`**, log `151b38f34c94422f8290da29a961be73` (22 tests). Commit `44e586d6`.
- (b) met — order `none`. The method-wide `@SuppressWarnings("java:S2583")` is kept and the comment now states why the method is the smallest scope **that covers the flagged branch** (`@SuppressWarnings` can annotate neither a catch clause, a block nor a statement; a local variable or a parameter does not cover the conditional). The statement was tightened in `a1c969f1`. Sonar at the head reports 0 open issues, so the suppression holds. Developer guide `runtime-start.adoc` and ADR `runtime-start/016` amended; both name the new test.

### Deliverable 3 — the statement that stops short — met

Order `none` (statement only). `BuildOutputParser` Javadoc states the reason once: ADR `core-build/011` passes the flag to the Maven count scan alone, and another parser takes it when a plan touches that parser. Re-read against `BuildOutputParser.parse` (only `MavenOutputParser.parse(output, outputCut)` takes the cut fact) and ADR `core-build/011` Consequences 78 and 90-91. Commit `0efd678d`.

### Done-when clauses beyond the deliverables

- `verify` per module and `verify -Ppre-commit` green: `verify -Ppre-commit` at `a1c969f1` green with no rewrite (log `26cf16a22a4946fea2e4aa0f7f7146ef`); `verify -pl pm-runtime -am` 8496 tests (log `b1735e3565a046d18a6e5dd56537e6a0`); full `verify` 12840 tests (log `718b9b9b7b884ce1ac0075cab28e2a31`); `package` 12840 tests (log `64f5bfb287184016b0e75338e07d39c9`); `package -DskipTests` (log `0e76ef536b5445199df79c6468ea7816`). The earlier head `0efd678d` carries the same five green (logs `5e20cadc…`, `a350e7d5…`, `c4990e08…`, `d2bb36a8…`, `1576ef78…`).
- Open Sonar issues fixed or dismissed: the head `a1c969f1` has 0 open new-code issues and 0 security hotspots (`count_status: confirmed`); nothing needed a fix or a dismissal; the quality gate is `OK`.

## 2. Personas and skills

Production code (`PendingEntryRecordsSweep`): `persona-implementer`. Test work: `persona-module-tester`. Statements (`BuildOutputParser`, ADR, guide): `persona-documenter`. Self-review: `persona-code-reviewer` (dispatched, read-only). Per deliverable the spec's declared skills were loaded: D1 `java-core`, `java-null-safety`, `junit-core`, `cui-testing`; D2 those plus `javadoc`; D3 `javadoc`, `junit-core`. No skill was added for surface drift — the changed files are the spec's surface plus the R06 documentation pair.

## 3. Hypotheses

- "a release or first-fault exception with a null message can reach the report" — **confirmed**: `Fault` has no null guard, `report` appends the message (`PendingEntryRecordsSweep.java` report arm), and `faultBeside` composed the string before the change; the new test reaches it through the release path. The **first-fault** path (`state.fault(e.getMessage(), …)`) is reachable the same way and is carried, not fixed (see Residue).
- "a narrower scope than the method is not available for the S2583 suppression" — **confirmed**: the flagged code is the `state.faultBeside(first, e)` arm of the conditional inside the catch (`PendingEntryRecordsSweep.java` `remove`), and `@SuppressWarnings` targets exclude catch clauses, blocks and statements, so the narrower declarations do not cover it.

## 4. Findings

- Self-review round 1 (no blocker, no major): F1 failing-run sha bookkeeping — carried into this report (the failing run cites the tree of `d5ce5164` with the test uncommitted, not a commit sha); F2 root-runner half argued from code — disclosed above; F3 Sonar clause — closed by the measured read at the head; F4 first-fault null message — out of scope by the Standing Constraint, carried; F5 ADR wrap and F6 suppression-comment wording — fixed in `a1c969f1`; F7-F9 refuted preferences.
- Self-review round 2: F5 and F6 closed; asked that the verify evidence be re-run at the new head — done (all five builds at `a1c969f1`).
- CI: 20 checks green at `a1c969f1`, 0 failing (CodeRabbit SUCCESS, SonarCloud SUCCESS, integration tests SUCCESS).
- PR comments: no review finding. CodeRabbit generated no actionable comment; its summary carries the `final_review_risk_coverage` marker (`coveredCommitId` `a1c969f1`, `kind=reviewed`). The Sourcery notices are review-budget refusals, not findings. The SonarQube comment reports the passed gate with 0 new issues.

## 5. Reviewers

- `coderabbit` (required) — **reviewed** head `a1c969f1`; auto-reviewed on push (summary comment at 17:41Z) and answered the explicit `@coderabbitai review` with "already reviewed the last commit" at 17:45Z. The resolve flag `review_complete_after_handling` plus the marker-bearing review on record make it the review at any head of this PR; no re-trigger was needed after the final head. Trigger history: one trigger; no refusal; no quota wait.
- `sourcery` (optional) — **refused** on its own review budget ("request another review in 1 day and 9 hours"), twice, at 17:41Z; optional, so it does not gate the merge.

## 6. Operator questions

None asked. No free-text operator messages.

## 7. Rule check

- `guard: none on this harness (unenforced)` — no pre-tool-use guard exists for the opencode harness here, so nothing was enforced and nothing is claimed as kept. The `.plan/temp/guard.log` path names no line for this run.
- Spec read: `spec_source: ledger_worktree` (the shared ledger worktree's copy), no `spec_differ` marker at setup or at landing.
- `executor-skew`: `match` at the start (executor `0.1.2183`, lane text `0.1.2183`). The landing-time resolve reports `executor_version: 0.1.2184`: the checkout's executor was regenerated during the run by another session, which is a newer executor, not a skew, and the lane text this run read is the installed one.
- Disclosed deviation: the resolve result's `quality-gate` canonical is plain `verify`; because the spec's Done-when requires `verify -Ppre-commit`, the run issued both — the canonical `verify` for the gate and the spec's `-Ppre-commit` profile as an additional executor call. All builds went through `plan-marshall:build-maven:maven`; no CI CLI, no direct wrapper, no inline Python, no `sleep`.
- Shell: one command per call, no pipes/loops/command substitution; files read and searched with the file tools; temp files only under `.plan/temp/` and the verb-returned ci body slots; repository edits only inside the worktree; commits staged by path; no stash/rebase/forced push/clone; base branch never merged because `origin/main` did not move (`a00db8de` at both ends).
- Every pushed head tested: `0efd678d` and `a1c969f1` each carry `package` and `package -DskipTests` runs, plus the gate canonical.

## 8. Build server

Setup preflight `ready` (registered). 13 builds, all `daemon_longpoll` (`mechanism` read from each build's `[BUILD-SERVER] resolved build` line): 3 targeted test runs, and per head `0efd678d` and `a1c969f1` the quality-gate, pre-commit, per-module verify, module-tests and test-compile canonicals. No fallback, no degraded result, no launch-fault retry, no killed job. `runtime_stamp_reason` absent; `plugin_version` is `unknown` (the runtime-info seam reported no plugin version).

## 9. Residue

- **Carried (new):** the **first-fault** path still feeds a nullable `e.getMessage()` into `Fault(String message, …)` in `PendingEntryRecordsSweep.remove`/`attempt`, so a first fault without a message would still print `null` in its report. The spec's Standing Constraint confines behaviour change to the release-fault path, so this is recorded for the next residue plan, not fixed here.
- **Carried (from the spec, unchanged):** the `int` wrap wording of a shipped spec; the lane's counts of runs and logs; the report's added release-fault clause; the runbook-lane `unknown` figures (owner E27).
- Nothing else is open: no dangling worktree, no branch, no base-evidence tree, no PR thread.

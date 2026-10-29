envelope_version=1
sender_type=plan
sender_id=rb-84-job-runner-residuals
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-22T06:55:49Z

```landing-facts
schema=landing-facts/1
plan_id=rb-84-job-runner-residuals
epic=pm-mcp-core-m2-m4
pr=#135
merge_state=merged
cleanup_owed=false
deliverables_total=7
deliverables_done=7
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash
steps=setup:done,implement:done,build-gate:done,self-review:budget-exhausted,pr:done,reviews:done,merge:done
```

## Deliverables

The spec's seven items, with the order that applies: `test-first` = the test was seen failing against the unfixed code; `mutation-after` = the failing output came from mutating the committed fix; `none` = no failing output captured.

1. **Recovery guards against a reused PID; the report's PID and the post-cancel termination** — met.
   - `JobRecoveryStepTest.reusedPidIsNotSignalled` (test-first, failing at `99971f3`, `maven-2026-10-21-153025.log`: `expected <UNIDENTIFIABLE> but was <PROBED>`, three kill calls), `SubprocessJobRunnerTest.cancelSignalsTheStartedProcess` (test-first, same log, the reported PID's group was killed), `SubprocessJobRunnerTest.pidReuse` (test-first, `bd708c6`, `maven-2026-10-21-165004.log`, the reused PID was not recognised), plus `groupThatOutlivedItsLeaderIsTerminated`, `TerminationProtocolTest.deadLeaderSkipsTheBarePidStep`, `failingSweepDoesNotAbortTheSettlement`. Pass at `e240ca1` / `7694431` / `b7b35aa`. Commit `e240ca1`, refined by `7694431`, `7647227`, `a13e144`, `b7b35aa`.
2. **A probe whose declared success codes include 127** — met. `ProbeJobTest.launcherFailureIsNotADeclaredSuccess` (test-first, failing at `e240ca1`, `maven-2026-10-21-154301.log`: `expected <FAILED> but was <SUCCEEDED>`), control `declared127FromTheProbe`, and `missingLauncherIsNotADeclaredSuccess` (none, new test of a changed line). Commit `45b4c60`.
3. **A failing fallback or an `Error` from the termination does not leave a job unsettled** — met. `SubprocessJobRunnerTest.fallbackFails` (test-first, failing at `45b4c60`, `maven-2026-10-21-154544.log`: the job did not settle) and `terminationFailsWithError` (test-first, `maven-2026-10-21-154532.log`: `StackOverflowError` escaped, record `RUNNING`). Commit `5ef1ca0`, refined by the last-resort retry and `PM_RUNTIME-209` in `bd708c6`.
4. **The store map is pruned; the four branches are tested** — met. `storeMapIsPruned` (mutation-after, `e41bd90` with `stores.remove` removed, `maven-2026-10-21-155509.log`: `expected <0> but was <1>`), `settledRecordWindowIsBounded` (none, new), `writeAheadFailureRegistersNothing` (none, new: register-after-close), `threadCannotBeStarted` (none, new: thread-start failure), `waitForReportsInterrupt`, `awaitEndOrCauseReportsInterrupt`, `awaitReportReportsInterrupt` (none, new: interrupt branches). The clamp item is refuted: `SubprocessJobRunnerTest.childWall` already covers both clamps and the null case. Commit `e41bd90`, window in `1acf0e8`.
5. **`PM_RUNTIME-208` says what happened; the ADR corrections; the cancel evidence** — met. The record and `doc/LogMessages.adoc` no longer claim the settlement ran. `cancelAnsweredBeforeTheExitDecidesTheFate` (mutation-after: cause branch emptied, `maven-2026-10-21-160157.log`, `CANCELLED` became `SUCCEEDED`), `cancelInTheMeantime` (mutation-after: the `terminateAndSettle` cancel read removed, `maven-2026-10-21-160243.log`, `CANCELLED` became `KILLED`), `cancelDuringSettlementIsAnsweredFalse` (mutation-after: both door guards removed, `maven-2026-10-21-160330.log`, the late cancel was answered true). ADR runtime-job/008 and 009 were re-read against the code and narrate nothing superseded; `runtime-job/012` never existed — the note's "012" is ADR `runtime-start/013` after PLAN-20's renumber, and its recovery rule was corrected. Commit `95dc01e`, ADR 004/013 refinements in `a13e144` and `21b997d`.
6. **Termination step 3a** — met as specified-conformant for a probe, with the worker's owner named. The operator chose "implement here": `ProcessTreeTracker` records a probe's descendants with their start instants (refreshed during the report wait and at least once per second afterwards, only while the root is the job's), sweeps them at the job's end and writes `job_descendant_survived` (security, PID and executable) for a survivor, with `PM_RUNTIME-137` and `PM_RUNTIME-138`. `ProbeJobTest.descendantOutsideTheGroupIsEnded` (mutation-after: tracking removed, `f239bfd`, `maven-2026-10-21-161037.log`: `the descendant 125061 is still alive`), `ProcessTreeTrackerTest` (none, new: detached member, escalation, reused member, dead member, reused root, survivor), `survivingDescendantIsAudited`, `failingSweepDoesNotAbortTheSettlement` (none, new). **Owner of the rest:** the worker wiring belongs to the supervisor (PLAN-30), and the Linux child-subreaper mechanism `pm-exec` must implement is owed to the repository that builds the launcher. ADR `runtime-job/013`.
7. **The native build result is reported, not assumed** — met per the operator's choice "report not run, name owner": the native build of `pm-mcpd` in `plan-marshall-mcp` against the deployed SNAPSHOT has **not run**; it remains the only check of the `kill` descriptor's `options.captureCallState`. No plan in `plan-marshall-mcp` is named in this ledger; PLAN-78 § Deliverable 3 assigns the owner repository `plan-marshall-mcp`, and the epic watch carries it. Recorded in the PR body.

Test-compile on pushed heads: the targeted `test`/`verify` runs compiled the heads they ran at — `e240ca1`, `45b4c60`, `5ef1ca0`, `e41bd90`, `95dc01e`, `f239bfd`, `7647227`, `cd0fd77`, `a13e144`, `21b997d`, `0250d62`, `b7b35aa`, `c52f985`; CI ran the full build on `0250d62` and `c52f985`. Not every pushed head had its own compile before the push (the intermediates `bd708c6`, `7694431`, `1acf0e8` were compiled by the `7647227` verify).

## Personas and skills

- Production code (D1–D4, D6): `persona-implementer`.
- Unit tests (all deliverables): `persona-module-tester`.
- Documentation (D5, the guides and ADRs): `persona-documenter`.
- Self-review rounds and PR-comment triage: `persona-code-reviewer`.
- The spec declares no `Skills:` or `Persona:` lines: **skills: none named**. No domain skill was added for surface drift; the one surface addition (`pm-core` `JobRunner` Javadoc) is reported below.

## Hypotheses

- "The open items of note 5 are real defects of the merged code" — **confirmed** for the PID reuse (`SubprocessJobRunner` read the report's PID; `JobRecoveryStep` probed a recorded PID unconditionally), the probe 127 (`settleEnded`/`settleUnlaunched` passed the declared codes on a launcher failure), the failing fallback (`settleEnded`'s `finally` had no guard; `terminateByHandle` caught only `IllegalArgumentException | IllegalStateException`), the unpruned store map (`stores` was documented "never pruned"), and `PM_RUNTIME-208`'s text. **Refuted** for the clamp branch (`SubprocessJobRunnerTest.childWall` already covered both clamps), and for "ADR 008/009 narrate superseded versions" (re-read against the code, no statement contradicted).
- "`TerminationProtocol` handles only descendants that remain in the group" — confirmed at `TerminationProtocol.java` (steps 1 to 4 signal the group/PID; step 3a was absent).
- "ADR runtime-job/012" — refuted: no such file exists (`origin/main` holds 001–009).

## Findings

- Self-review round 1 (11 findings): a regression the first PID guard introduced (a leader-less group was no longer terminated), the missing `job_descendant_survived` audit, the tracker wiring before the post-spawn failure, the sweep inflating `endedAt`, the cache order, the last-resort semantics, the `recordOf` contract, doc claims and two formatting corruptions — all fixed.
- Round 2 (10): SIGKILL wait before a survivor, the bare-PID step skipped for a known-dead leader, a single audit on the cause path, an unreadable start treated as reused, a refused audit not aborting settlement, the reused-PID refusal pinned, the cache window pinned — fixed.
- Round 3 (4): the recovery half of the unreadable start, the recovery's bare-PID step, a host-dependent survivor assertion, three ADR statements — fixed; the operator then chose to proceed without a fourth round.
- Sonar (CI): 17 new-code smells at `7647227` — fixed in `cd0fd77` (S1181 suppressions with reasons, S3077, S1128, S2925, S5778, S6213); the gate passed on every later head.
- CodeRabbit: 3 inline + 1 outside-diff at `7647227`, 1 outside-diff at `cd0fd77`, 2 inline at `a13e144`, 1 inline at `0250d62` — all fixed (`a13e144`, `21b997d`, `b7b35aa`), every thread replied to and resolved; the outside-diff findings answered in a PR comment.
- Sourcery: refused on a hard quota (250k diff characters per 7 days) on every trigger; optional, not gating.

## Reviewers

| Reviewer | Result | Head(s) reviewed | History |
|---|---|---|---|
| coderabbit (required) | reviewed, last at `0250d62` | `7647227`, `cd0fd77`, `a13e144`, `0250d62` | triggered at creation, after each push; the `21b997d` and later reviews were refused on quota ("Next included review available in 29 minutes", then "0 remain after this review"); every finding handled. **Standing authorization for `b7b35aa`/`c52f985`:** a qualifying review exists (`0250d62`), every finding is fixed and every thread resolved, it was re-triggered once after the last push, and every other gate condition holds on the current head (`c52f985`). The operator selected "merge now on the recorded evidence" for the gap that `c52f985` is newer than the last review. |
| sourcery (optional) | refused | — | hard quota on every trigger (`3 days`, then `2 days 16 hours`); optional, not gating. |

CI on the merged head `c52f985`: Maven Build (25/26, sonar-build), SonarCloud, Integration Tests and Dependency Review green; the merge-group run `39736070360` succeeded. Local `verify` green at `c52f985` (`maven-2026-10-22-062228.log`, 6807 tests).

## Operator questions

1. Step 3a's owner: options {PLAN-27 job-confinement, PLAN-30 supervisor, implement here}; selected **"Implement here"**.
2. The native build: options {report not run and name the owner, run it now}; selected **"Report not run, name owner"**.
3. The self-review limit: options {proceed to the PR, a fourth round}; selected **"Proceed to the PR"**.
4. The merge gate (first call): aborted by the server restart — no answer, so it was asked again.
5. The merge gate again: options {wait ~90 minutes and re-trigger, merge now on the recorded evidence, leave the PR open}; selected **"Merge now on the recorded evidence"**.
Free-text operator messages: none.

## Rule check

`guard: none on this harness (unenforced)` — no guard log exists, so no guard refusal is reported and "all kept" is not claimed.

Rules I did not keep:
- Shell file access: `wc -l` on four test files, `grep -n "settledRecords.put"` on a source file, `ls` of a findings directory.
- `find /home/oliver/.config/opencode/skills …` to locate the CI script (a read outside the repository root), and `pgrep -fa surefire` (process listing).
- `kill 125061`, to end a `sleep 120` left by the mutation evidence of `descendantOutsideTheGroupIsEnded`.
- Test-compile at every pushed head: not every push had its own compile before it (see Deliverables).
- The standing constraint's profile gates (`verify -Ppre-commit`, `-Pcoverage`, `-Pintegration-tests`) and the sibling-checkout links check were not run; the lane forbids invoking an external script with `python3`, and its build gate is the single `verify`.
- The merge gate was bent on the operator's explicit selection: CodeRabbit last reviewed `0250d62`, not the final head `c52f985` (quota), and one earlier local `verify` was blocked by the host's inotify-watch limit.

## Residue

- Step 3a's worker wiring belongs to PLAN-30 (supervisor); the Linux child-subreaper mechanism is owed to the launcher repository (`pm-exec`).
- The specification's step-3a sentence "and the runtime status shows it" has no runtime-status surface in this repository; it is not built and has no owner in this ledger.
- The native build of `pm-mcpd` has not run; the owner repository is `plan-marshall-mcp` per PLAN-78, with no plan named for it.
- `recordOf` answers a bounded window of settled records (1024 by default); a caller that needs an older record reads its store. The `pm-core` `JobRunner` Javadoc states the window — a Javadoc-only change outside the spec's Expected Surface.
- The standing constraint's profile gates and the links check remain unrun.
- `origin/main` moved twice during the run (`7756dfd`, `a51c7d5`); both were merged, the last one only the orchestrator ledger.
- Owed to the milestone exit, in `plan-marshall-documentation`: the concept and user documentation of the job runtime and the roadmap check-off.

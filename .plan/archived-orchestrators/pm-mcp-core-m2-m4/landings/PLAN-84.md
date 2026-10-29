# Landing Analysis: PLAN-84 — Close the job-runner residuals of PLAN-20 and termination step 3a

epic: pm-mcp-core-m2-m4
workstream: WS-04
pr: #135 (https://github.com/plan-marshall/pm-mcp-core/pull/135), merged as squash `0bf64f7` through the merge queue on 2026-10-22 06:46Z

> Landing record for one shipped plan. Lives at `landings/PLAN-84.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> reconciliation contract.

Source of the landing: inbox message `rb-84-job-runner-residuals-001.md` (`kind: landing`, sender `rb-84-job-runner-residuals`, `/plan-marshall-runbook-lane` on opencode, plan-reported). `inbox landing-check`: `complete: false`, `missing_keys: [total_tokens]`; `landing-facts` block present (`deliverables_total=7`, `deliverables_done=7`, `pr=#135`, `merge_state=merged`, `cleanup_owed=false`, `harness=opencode`, `model=deepseek-v4.1-flash`, `steps=…,self-review:budget-exhausted,…`). No candidate-lesson messages came with it.

## Deliverable Fidelity vs Spec

Read at `0bf64f7` (the tree equals the PR head `c52f985`: `git diff c52f985 0bf64f7` is empty). All 24 test names the landing cites exist in the merged test tree (`git grep "void <name>"`); the orchestrator ran the six touched classes (`ProcessTreeTrackerTest`, `ProbeJobTest`, `SubprocessJobRunnerTest`, `JobRecoveryStepTest`, `TerminationProtocolTest`, `RuntimeLogMessagesTest`) on `main` at `0bf64f7`: 185 tests, green. The 16 commit shas the landing names all exist in the PR history.

| # | Deliverable | Verdict | Evidence |
|---|-------------|---------|----------|
| 1 | PID reuse in recovery, in the launch report's PID and after a cancel | met | `SubprocessJobRunner.pidReused` (start instant of the handle against the started process; an unreadable instant counts as reused), `JobRecoveryStep` `UNIDENTIFIABLE` / `ORPHAN_UNIDENTIFIABLE`, `TerminationProtocol.terminate(long pid, boolean leaderMayLive)`; `reusedPidIsNotSignalled`, `cancelSignalsTheStartedProcess`, `pidReuse`, `groupThatOutlivedItsLeaderIsTerminated`, `deadLeaderSkipsTheBarePidStep` exist; ADR runtime-job/010 |
| 2 | A probe declaring 127 | met | `launcherFailureIsNotADeclaredSuccess`, `declared127FromTheProbe`, `missingLauncherIsNotADeclaredSuccess` exist; ADR runtime-job/011 |
| 3 | A failing fallback or an `Error` from the termination | met | `fallbackFails`, `terminationFailsWithError` exist; `PM_RUNTIME-132` (termination failure, process destroyed through its handle) and the new `PM_RUNTIME-209` (last-resort settlement) are in `doc/LogMessages.adoc` and `RuntimeLogMessages` |
| 4 | The store map pruned; the untested branches | met | `SETTLED_RECORD_CACHE = 1024`, builder `settledRecordsKept`; `storeMapIsPruned`, `settledRecordWindowIsBounded`, `writeAheadFailureRegistersNothing`, `threadCannotBeStarted`, the three interrupt tests exist; the clamp item is refuted as the landing says (`childWall`); ADR runtime-job/012. The `pm-core` `JobRunner` Javadoc states the window |
| 5 | `PM_RUNTIME-208`, the ADR corrections, the cancel evidence | met | `PM_RUNTIME-208` now reads "Supervision of job '%s' failed", `cancelAnsweredBeforeTheExitDecidesTheFate`, `cancelInTheMeantime`, `cancelDuringSettlementIsAnsweredFalse` exist. The spec's "ADR `runtime-job/012`" never existed (the directory held 001 to 009 before); the landing's reading, that the note meant `runtime-start/013`, is borne out by the diff, which edits `runtime-start/013`, `runtime-job/004` and `008` and adds 010 to 013 |
| 6 | Termination step 3a | partly met, owner not corroborated | see below |
| 7 | The native build reported | met (reported, not run) | the build did not run: the checks of #135 list no native build, and `plan-marshall-mcp` has no plan for it; recorded in the PR body |

**Deliverable 6.** Delivered: `ProcessTreeTracker` (new, 236 lines) records a probe's descendants with their start instants, refreshes them during the report wait and at least once per second afterwards, sweeps them at the job's end (`SIGTERM`, grace period, `SIGKILL`) and writes the security audit record `job_descendant_survived` for a survivor; `PM_RUNTIME-137` and `-138`; ADR runtime-job/013; the operator chose "implement here" at refine. **Not delivered**, as the landing says: the worker (`llm`) half of step 3a, the Linux child-subreaper mechanism of the launcher (`PR_SET_CHILD_SUBREAPER`, the specification's `job-runtime/01` step 3a), and the specification's sentence "and the runtime status shows it". The spec's Done-when accepts "specified-conformant **or its owner is named**". The landing names the supervisor (PLAN-30) for the worker wiring and the launcher repository for the subreaper. **Neither owner is corroborated**: `doc/plans/supervisor.adoc`, `worker-launch.adoc`, `job-confinement-and-environment.adoc` and the specs PLAN-27, 29, 30 say nothing of descendants or the subreaper, and no package of `pm-mcp-clients` (`origin/main` `07f2c87`) names them (`git grep -i "subreaper\|descendant"` over `doc/plans/`). So the owner is named on paper only; recorded as an Open Defect for the operator's decision.

## Surface delta (measured by the orchestrator)

Declared 12 entries (all `HYPOTHESIS` but three), realized 23 files, added 6, declared and untouched 4, `state: expansion_detected`. Added: `pm-core/.../core/service/JobRunner.java` (Javadoc), `pm-runtime/.../runtime/job/ProcessTreeTracker.java` (the new class of step 3a), `pm-runtime/.../runtime/log/RuntimeLogMessages.java` and its test (the log holder, which the specs leave out by design), `pm-runtime/doc/developer/runtime-start.adoc` and ADR `runtime-start/013`. Declared and untouched: `ProcessGroupSignals.java` and `KillBinding.java` (class guesses; the design put the guard elsewhere and the native build did not run), `doc/plans/README.adoc` (no row of its own), `job-runtime/01-pipeline-and-subprocesses.adoc` ("only if step 3a needs a clarification"). Calibration as for PLAN-20: a class-level `HYPOTHESIS` surface is wrong in both directions.

## Metrics and Anomalies

- `total_tokens`: not in the message (opencode; the tenth landing with it). Wall clock: the first CI run was 2026-10-21 ~18:30Z, the merge 2026-10-22 06:46Z (~12 h); `mvn verify` locally 6781 tests at `f239bfd`, 6807 at `c52f985` (plan-reported).
- CI on the final head (30 checks, `ci checks status`): all green or skipped by the path rules (`build / build (25)`, `(26)`, `sonar-build`, `quarkus-alignment`, integration tests, dependency review, `CodeRabbit` status SUCCESS, Sourcery skipped). SonarCloud gate OK. **Two issues were still open on the pull request at the merge and the landing does not mention them**: `java:S3776` `SubprocessJobRunner.java` 1176 (cognitive complexity 17 of 15) and `java:S5778` `JobRecoveryStepTest.java` 391 (API `pullRequest=135`, 2026-10-22). Both are on `main` now and are in PLAN-94 (Deliverables 6 and 2). The 17 smells the landing says it fixed at `7647227` are gone.
- Reviews: all 6 review threads are resolved (18 comments); CodeRabbit last reviewed `0250d62`, the final head `c52f985` (08:22 local, 38 minutes later) was refused on quota; the operator selected "merge now on the recorded evidence", which bends the literal gate (plan-reported, confirmed by the review history: 16 reviews, the last bot review 05:58Z). Sourcery refused on a hard quota (optional).
- Third-party text read for this record (CodeRabbit's summary comment) was taken as leads only; see the Watches below.
- Self-reported rule breaks (plan-reported, disclosed in its Rule check): shell file access (`wc`, `grep`, `ls`), `find` outside the repository root, `pgrep`, a `kill` of a leftover `sleep 120`; the self-review ended after three rounds at the budget (`self-review:budget-exhausted`, the operator chose to proceed); not every pushed head had its own compile; `verify -Ppre-commit`, `-Pcoverage`, `-Pintegration-tests` and the sibling links check were not run locally (CI ran the integration tests and the coverage-covering check). The main checkout is clean here (`git status` empty on `main`).
- Environment, plan-reported and consistent with the executor-skew watch: the executor maps the lane to `0.1.1961` (registry `0.1.1981`); the executor skew is reported again.

## Routing and Merge Behavior

Merged through the merge queue as a squash; no ejection. `origin/main` moved twice during the run (`7756dfd` the index fix #136, `a51c7d5` the ledger #138);. No collision with PLAN-19 (#137) or PLAN-85 (#129): their changed files share no path with the footprint beyond the shared documents. `corpus declaration-currency` over the 23 footprint paths: the staged and parked specs whose declared surface the footprint touches are PLAN-27 (15 paths: the runtime job), PLAN-40, 46, 47, 57, 58, 60, 62, 76 (2 to 5, the shared documents and log files), PLAN-59 and 63 (3), and PLAN-94 (3, already re-stamped at `0bf64f7`); 11 of them hold claims now stale (PLAN-27, 40, 46, 47, 57, 58, 59, 60, 62, 63, 76), re-grounded by the next `cleanup`. PLAN-27 is the one whose content moves: it composes the confinement of a job on the runner this plan changed.

## Reconciliation Actions

- Row `PLAN-84`: `shipped`, `pr=#135`, `landing=landings/PLAN-84.md`, `plan_marshall_plan_id=rb-84-job-runner-residuals`; message `-001` archived.
- Open Defect added: the worker half and the launcher half of step 3a and the runtime-status sentence have no corroborated owner (the landing's PLAN-30 is not backed by its spec or package).
- Open Defect folded: the landing carried no `total_tokens` (tenth).
- Watch updated: the native build of `pm-mcpd` has not run and no plan in `plan-marshall-mcp` is named.
- Watch added (unverified leads from the review bot, partly corroborated in the code): the sweep of remembered descendants signals by numeric PID after a liveness check (`ProcessTreeTracker.sweep`, `signals.signalProcess(pid, SIGTERM)`), so a descendant that exits and whose PID is reused between the check and the signal could be signalled (the bot rates it moderate and inferred; the window exists in the code, exploitability is unproven); and, where a platform cannot report a start instant, `pidReused` answers true and the runner must end the job through the handle of the process it started (the bot fears a job left running; the code comment and ADR 010 say the handle is used, no test of an unreadable instant on the cancel path was read).
- PLAN-94 absorbed the two Sonar issues before this landing was analysed (staged 2026-10-22).

## Follow-Ups

- Decide the owner of the worker half of step 3a, the launcher subreaper (`pm-exec`, `pm-mcp-clients`) and the runtime-status sentence (Open Defects). The nearest packages are `worker-launch` (PLAN-29: it starts `llm` jobs on the runner) and `job-confinement-and-environment` (PLAN-27: it composes the job); `supervisor` (PLAN-30) is the landing's guess.
- Run `cleanup` to re-ground the 11 stale specs (PLAN-27 first); PLAN-27 should read `ProcessTreeTracker`, `TerminationProtocol.terminate(pid, leaderMayLive)` and ADR runtime-job/010 to 013 at its refine.
- Owed to the milestone exit, in `plan-marshall-documentation`: the concept and user documentation of the job runtime and the roadmap check-off.

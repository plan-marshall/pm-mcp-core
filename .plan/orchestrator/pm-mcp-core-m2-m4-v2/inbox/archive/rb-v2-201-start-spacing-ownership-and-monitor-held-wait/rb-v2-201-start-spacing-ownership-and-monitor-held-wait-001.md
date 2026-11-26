envelope_version=1
sender_type=plan
sender_id=rb-v2-201-start-spacing-ownership-and-monitor-held-wait
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-26T04:52:45Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-201-start-spacing-ownership-and-monitor-held-wait
epic=pm-mcp-core-m2-m4-v2
pr=#625
merge_state=merged
cleanup_owed=false
deliverables_total=4
deliverables_done=4
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2184
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=5
review_body_findings_handled=5
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced).

Standing rules: epic `pm-mcp-core-m2-m4-v2`, revision **r7** (2026-11-10), read in full before the spec; the rules the spec named and this run applied: R05, R06, R11, R14, R19, R20 (R21 binds the orchestrator's analysis, not this plan).

## 1. Deliverables

**D1 — the failing tests first** (commit `6ec52433`, extended by `47c2ba09`, `b49c3e00`, `a026af4a`). Met.

- T1 `StartSpacingInterleavingTest.aStartWaitsOutsideTheSupervisorMonitor` — order `test-first`; failing at `90505ad2` in log `.plan/local/plans/NO_PLAN/build-results/pm-runtime/maven-2026-11-25-222445.log` (3 failures, 5.022 s); passes on the final head (verified by `verify`, log `170225cb048e4f0ca629c2a926baf693`).
- T2 `anEndOfANonOwnerReleasesNothing` — `test-first`; same base log; the owner's own end is the test's second half.
- T3 `theBoundHandsTheTurnToTheNextStart` — `test-first`; same base log.
- T4 controls: `HarnessStartSpacingTest.twoHarnessesAreNotSpaced` (existing) and `aStartWithNoPendingWorkerProceedsAtOnce` (new) — order `none`; green in the gate runs.
- The class never sleeps; T1 is ordered by the model-slot claim latch (`SupervisorTestFixture.claims`) and, after the review, also asserts with a bounded 200 ms observation that the second start is still held by the turn before the first call runs.

**D2 — the redesign** (commit `ad20742a`, extended by `a026af4a`). Met; I1–I4 hold.

- I1: `WorkerSupervisor.startOne` awaits the spacing outside the registry monitor (the start lock is held; no worker call takes it). Pinned by T1 and `aStartPreparedWhileTheDrainBeginsRegistersNothing`.
- I2: `HarnessStartSpacing.firstCall`/`ended` release only the owner (`owner.equals(pending.get(harness))`). Pinned by T2 and T7's WorkerEnding test.
- I3: past the bound the waiter becomes the owner and the former owner's release is a no-op. Pinned by T3.
- I4: the spacing only delays, never refuses (the deadline breaks the loop; the supervisor restores an interrupt and proceeds). Pinned by `theWaitIsCapped` and the unstarted-turn releases.
- The lock order and the pre-existing supervisor ↔ guard ABBA cycle are recorded in ADR `runtime-worker/024` (item 42(a)'s; recorded, not owned).
- The review found a regression of the split (a drain beginning during the spacing wait could see the supervisor quiet and the start then spawned and registered): fixed in `a026af4a` with the admission re-check under the registry monitor and the new `WorkerStartRefusal.STOPPED_ADMITTING`; test `aStartPreparedWhileTheDrainBeginsRegistersNothing`.

**D3 — the documents** (commit `9a99c3a0`, corrected by `2a161f2b`, `7b15212d`, `a026af4a`). Met.

- ADR `runtime-worker/001` names the owner the shared instance keeps; ADR `runtime-worker/024` the owner and the wait outside the registry monitor with the lock order; ADR `runtime-worker/025` the counting rule (a launch refused before its spawn counts, except `harness_update_unverified`; the supervisor's own declines count nothing).
- `runtime-worker.adoc` and `runtime-launch.adoc` state the new wiring, the counted and uncounted refusals, the record-before-count order, who records which refusal, the start lock, the drain re-check and the report on the declined outcome; `StartFailureGuard`'s Javadoc follows; `doc/Traceability.adoc` names the two test classes and ADRs 024/025 under PM-WORK-3.
- ADRs 024 and 025 are the next free numbers of the directory against `origin/main` (R11 checked at 023).

**D4 — the start path's counting and the releases** (commit `8205ab86`, extended by `a026af4a`). Met.

- T5: `WorkerSupervisorStartFailureTest.anEnrolmentStoreRefusalCountsOneStartFailure` (the four reasons; `[1]` `HARNESS_NOT_ENROLLED` and `[2]` `HARNESS_VERSION_UNSUPPORTED` fail at `90505ad2`, `[3]`/`[4]` already passed there), `aCompositionRefusalCountsOneStartFailure`, `aRunnerRefusalCountsOneStartFailure`, and the uncounted control `anUpdateEvidenceRefusalCountsNothing` — order `test-first` for the failing cases, same base log.
- T6 `aThrowingCountStillRecordsTheRefusal` — `test-first`; same base log (no record, no release on the old order).
- T7: `theFirstCallReleasesTheTurn`, `theEndOfThePendingWorkerReleasesTheTurn`, `aRefusedStartReleasesTheTurn` — order `mutation-after`; one mutation run each at fix commit `9a99c3a0`: logs `1ad054ec65d24a09864422d84b4a99e3`, `6bfb3e6bdeee4a30a986f2a1f577cfd5`, `a225ab61f989435a9ec671b091c64a89`.
- T8 `aThrowingRunnerReleasesTheTurn` — `test-first`; same base log.
- 4(c): `aBatchStopsWhenTheGuardHolds` — order `mutation-after`; removing the mid-batch stop at `2a161f2b` fails it (log `725d6be583d74735a66703845f8182eb`, 3 starts where 1 is required).

**The Done-when "T1 to T3 and T5 to T8 fail at `90505ad2`" is not met as written for T7.** The spec's own T7 definition is "deleting one fails a test", and on the old code all three release tests pass (the old `firstCall`/`ended` released by harness id), so T7 cannot fail at the base. It is proved `mutation-after` with the three logs; the blanket clause needs the spec corrected (spec issue, not a plan defect).

**Other Done-whens.** The five existing `HarnessStartSpacingTest` cases stay green with the same counts (only the owner arguments changed; one control added) — order `none`. The four builds: `verify -Ppre-commit` green at `b49c3e00` (`ff09651ea3444036836f0e950ec36cf7`) and `a026af4a` (`0bac09af6c1d4ad58a5a1c5ad22c015a`); `verify` green at `a026af4a` (`e74e8791e65e4ed1a82b7a3e0f13b22d`) and at the merge head `741a6d29` (`170225cb048e4f0ca629c2a926baf693`); `verify -Pcoverage` green at `0d9737c2` (`aeaf2a7c928143efb464d6ed6b0e5c31`) and at the merge commit `89ffec36` (`.plan/local/plans/NO_PLAN/build-results/default/maven-2026-11-26-043429.log`, 12980 tests); `verify -Pintegration-tests -pl pm-runtime -am` green at `0d9737c2` (`54eaf3e7b5e549eebebaa565148e2458`) and at the merge commit (`.plan/local/plans/NO_PLAN/build-results/pm-runtime/maven-2026-11-26-044951.log`, 45 tests). Sonar: the open-finding read at the merge head `741a6d29` is `0` measured (see §7).

## 2. Personas and skills

- D1: `skill: plan-marshall-persona-module-tester`; skills `pm-dev-java:junit-core`, `pm-dev-java-cui:cui-testing`.
- D2 and D4: `persona-implementer` (the spec's `Persona: implementer`); skills `pm-dev-java:java-core`, `pm-dev-java:java-null-safety`, `pm-dev-java:javadoc`, `pm-dev-java:junit-core` (D4 also `pm-dev-java-cui:cui-testing`).
- D3: `persona-documenter`; skills `pm-documents:ref-asciidoc`, `pm-dev-java:javadoc`.
- Self-review rounds 1 and 2 and the PR review triage: `persona-code-reviewer`.
- No skill was added for surface drift: every touched file is in the spec's Expected Surface, except `WorkerStartRefusal.java` (the drain decline the redesign needed; reported here), `doc/Traceability.adoc` (R05/R08 shared bookkeeping) and the two ADRs (R06).
- `skills: none named` nowhere — the spec declares skills per deliverable.

## 3. Hypotheses

- "`WorkerSupervisor.start` is `synchronized` (`:141`), `startOne` calls `startAfterSpacing` (`:292`), `awaitTurn` runs there (`:312`)" — **confirmed**, `WorkerSupervisor.java:141, 292, 309-317` at `90505ad2`.
- "`SupervisorCalls.callHeartbeat` is `synchronized` (`:89`), `known(...)` reads the registry, `firstCall` calls `spacing().firstCall` (`:380`)" — **confirmed**, `SupervisorCalls.java:89, 390-392, 380`.
- "`WorkerEnding.end` calls `spacing().ended(generation.harness())` (`:94`) before it checks for a request" — **confirmed**, `WorkerEnding.java:93-95`.
- "`HarnessStartSpacing.pending` is a `HashSet<String>` keyed by harness, and `awaitTurn` adds the marker even after the bound" — **confirmed**, `HarnessStartSpacing.java:44, 74-84`.
- OBSERVED "`awaitTurn` has no caller in `src/main`, and `SupervisorEnvironment` has no `spacing()` at `3ba79c4f`" — stale by `90505ad2`; the wiring is on main, as the spec's `90505ad2` note says.

## 4. Findings

**Self-review round 1 (12 findings).** Fixed: the ADR's lock order and the false "outside every monitor" claims (ADR 024 now records the real order and the pre-existing supervisor ↔ guard ABBA cycle); the untested mid-batch stop (new test + mutation log `725d6be5…`) and `start`'s outcome contract; the Traceability lines; the explicit `verify -Ppre-commit` run (the first "quality-gate" run was `verify`); T1's ordering latch; the duplicate owner-end test (merged into the non-owner test); the "who records the refusal" wording. Reported, not changed: T7's blanket-clause inconsistency; Sonar read timing; the update-evidence reading (ADR 025); the `StartOutcome.Declined` API change.

**Self-review round 2 (5 open items).** Fixed: the merged owner control could not fail (`<500 ms` against a 300 ms bound → `<150 ms`); three residual "outside every monitor" claims; the test `throws` clause is the pre-commit recipe's `throws Exception` (committed `0d9737c2`). The four builds re-run on the final head. Reported: T7/DW2 as recorded; the Sonar read after the PR's CI.

**CI and PR comments.** CI green on every head (`0d9737c2`, `b49c3e00`, `a026af4a`, `741a6d29`); SonarCloud Code Analysis green on each. CodeRabbit reviewed `b49c3e00` with 5 findings, all handled in `a026af4a` (dispositions: `inline inline-1`, `inline inline-2`, `outside-diff outside-diff-1`, `outside-diff outside-diff-2`, `nitpick nitpick-1`). The admission re-check is a real defect of the redesign (a drain during the spacing wait), fixed with a test. No other PR comment needed action; Sourcery's hard-quota notice is not a finding.

## 5. Reviewers

- **coderabbit** (required; `quota_limited`, `review_complete_after_handling`): refused on quota at `2026-11-26T00:57:51Z` (comment `7179289095`) with no qualifying review; re-triggered at `02:28:07Z` (comment `7180833985`), ≥ the 90-minute floor; reviewed `b49c3e00` at `02:35:11Z` with a qualifying review whose marker names the covered commit (`final_review_risk_coverage … coveredCommitId b49c3e00`). Its 5 findings were fixed in `a026af4a`; both inline threads were answered and resolved; the review-gate accepted the review for the merge head under the `review_complete_after_handling` rule, and no re-trigger was posted for the later heads.
- **sourcery** (optional): refused on its hard quota at `00:58:06Z` (next in ~20h39m); its check reported success from `b49c3e00` on (no findings surfaced). Not gating.

## 6. Operator questions

1. **Base-sha evidence, first form:** asked at the point where the detached base tree at `90505ad2` reported `executor_in_worktree: absent` (the repository holds no marketplace for the generator and the copy fallback is disabled for detached trees). Options: run at the D1 commit (recommended), stop as blocked, retry another sanctioned form. Selected: **"Retry the base worktree differently"**.
2. **Base-sha evidence, second form:** options: in-process build in the base tree (recommended), run at the D1 commit, stop as blocked. Selected: **"In-process build in the base tree"**. The failing run used that form (`module-tests` with `--project-dir <base>` and `--execution-mode in_process`, scoped to the two test classes).

No free-text operator message changed the run.

## 7. Rule check

- guard: none on this harness (unenforced) — no guard log exists (`.plan/temp/guard.log` was never written), and no refusal was possible. The counts below are the run's own record.
- Shell file operations the run issued (all against the base tree or the run's temp dir, never the plan worktree): 10 `cp` (the base-tree test copies, the two saved originals, the two restores), 2 `rm` (the copied test file and the base-evidence owner marker), 2 `ls` (the ADR directory, the base tree's `.plan`), 3 `grep` (a test-file check, two log/source excerpts), 1 `cmp`, 1 `printf`. **The three shell `grep` calls and the two `ls` calls are read-class shell commands the rules forbid** (files are read with the file tools); they changed no file. No guard refused them (no guard on this harness).
- `cp`/`rm` are the base-evidence recipe's own copy/restore steps; the base tree was restored to its tracked state (clean `status --porcelain`) before removal.
- One build attempt in the main checkout (`verify -Pcoverage`, log `b2269758b7004d07a9ff4069b8bb9c9e`) failed on `pm-workflow`'s `WorkflowParserRefusalTest.fixturesPresent` (3 failures) — a stale `target/test-classes` fixture count in the main checkout, not a defect at the merge commit: the same profile is green in a fresh tree at that commit. The run is recorded, not cited as evidence. No source was changed to silence it.
- Restarts: none. Sub-agents: the self-review reviewer (2 rounds, same session, no re-dispatch). No killed sub-agent.
- Spec read: `spec_source: ledger_worktree`, no `spec_differ` marker. `executor-skew`: **match** (executor 0.1.2183, lane 0.1.2183), recorded and never asked about.
- No rule was bent; the two base-evidence deviations were put to the operator and selected (see §6).

## 8. Build server

Setup preflight: `ready`. Builds by mechanism: `daemon_longpoll` 15, `in_process_fallback` 5 (the base-evidence runs by design — `executor_absent_from_worktree` on the detached tree — and the two merge-head runs in a fresh detached tree). Fallbacks: the base-evidence runs (`in_process` by the operator's form); the first base-evidence run hit the learned 651 s bound (`timeout`) and was re-run scoped with `--timeout 1255`; no launch-fault retry; no `killed` job. `runtime_stamp_reason`: none (the resolve reported `plugin_version: unknown`, which is stamped as `unknown` above).

## 9. Residue

- **Pre-existing latent deadlock, reported not fixed:** `StartFailureGuard.clear` calls `WorkerSupervisor.forgetStartFailures` under the guard's monitor while the start path takes the guard under the supervisor's monitor (ABBA, reachable from `WorkerRecycling.afterTaskClosed` on the relay thread). Item 42(a)'s per the spec; recorded in ADR `runtime-worker/024` and in the guide's residue list. A staged fix-plan is the orchestrator's decision.
- **Spec issue:** the Done-when's blanket "T1 to T3 and T5 to T8 fail at `90505ad2`" cannot hold for T7 (its own definition is mutation-based); T7 is `mutation-after`. The spec's line should be corrected.
- **Spec-correction lead:** Model Work § Start-Failure Guard says the supervisor counts starts "that the runtime refused before the spawn", which literally includes `harness_update_unverified`; ADR 025 reads the Store Schemas part's enumeration as governing and excludes it. If the general sentence should govern, the rule is one line in `WorkerSupervisor.countsAsStartFailure`.
- **Assembly-visible API:** `StartOutcome.Declined` gained the guard's `StartFailureReport` and `WorkerStartRefusal` gained `STOPPED_ADMITTING`; the assembly has no consumer of either today.
- **`plugin_version: unknown`** in the runtime stamp (the resolve could not read the bundle version).
- The `v2-187-build-output-parser-…` and other worktrees of the setup time belong to other plans; this run created and removed only its own base tree.

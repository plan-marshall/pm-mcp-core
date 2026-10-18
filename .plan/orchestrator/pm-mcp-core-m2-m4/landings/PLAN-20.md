# Landing Analysis: PLAN-20 — The subprocess job runner

epic: pm-mcp-core-m2-m4
workstream: WS-04
pr: #92 (https://github.com/plan-marshall/pm-mcp-core/pull/92), merged through the merge queue as squash `2ecea3f`, 2026-10-18

> Landing record for one shipped plan. Lives at `landings/PLAN-20.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source of the landing: inbox message `plan-20-job-runner-015.md` (`kind: landing`), corroborated by the orchestrator against `ci pr view 92`, `git show --stat 2ecea3f`, the spec's Expected Surface, the PR description (open items, note 5) and a `git grep` of `main` for the obligations of pm-mcp-core #84. The plan id is `plan-20-job-runner`.

## Deliverable Fidelity vs Spec

The spec carries four bullets of `doc/plans/job-runner.adoc` § What; the plan counts eleven (`deliverables_total=11`, 11 done). `functional-review`: 7 met, 4 met with residuals, 0 not met. The orchestrator checked what the merged tree and the PR text can show; the per-deliverable verdicts are the plan's.

| Spec bullet | Verdict | Evidence |
|-------------|---------|----------|
| 1. The runner through `pm-exec`, write-ahead job record and log, orphan rules of recovery, fixed write order | shipped-modified | `SubprocessJobRunner` implements `JobRunner` and `JobStarter`; `PmExecLauncher`, `JobLog`, `TerminationProtocol`, `JobRecoveryStep`, `JobDrainParticipant`; in `pm-core` `JobId`, `JobRecord`, `JobRecordCodec`, `JobRecordStore`. The declared classes `JobRuntime`, `JobRecovery`, `ProcessTermination`, `RecoveryStep` and `StartupSequence` were NOT created or touched: the design took other names (below) |
| 2. Termination for every cause of the outcome table; the job log readable while the job runs | shipped-modified | process-group signals through FFM `kill(2)` (`KillBinding`, `ProcessGroupSignals`, ADR `runtime-job/008`); `TerminationProtocol` handles descendants that stay in the group. NOT delivered: step 3a of the termination protocol of pm-mcp-core #84 (descendants of a worker or probe that left the process group, launcher staying as the job's process) and the refusal reason `harness_update_unverified` — `git grep` of `main` finds neither in `pm-runtime` or `pm-core`; the landing message and the lessons do not mention #84, and whether the heads-up reached the plan is unknown (the orchestrator gave it to the operator as a paste text and sent nothing) |
| 3. Probe jobs (`probe`, prefix `v`) | shipped-as-specified | probe handling in the runner; one open item: a probe declaring exit 127 a success code (below) |
| 4. `pm-exec` makes the job the leader of its process group and reports PID and start instant | shipped-as-specified (pre-existing in `pm-mcp-clients`) | consumed through `PmExecLauncher`; the native build in `plan-marshall-mcp` has not run |

Done-when was not re-run by the orchestrator; the merge queue admitted the PR, which needs the required checks green (6046 tests per the PR description).

## Metrics and Anomalies

- Tokens: 20,478,729 total (plan-reported, a floor); wall clock 76,559 s (about 21 h 16 min). The largest run of the epic, ahead of PLAN-74 (15.2M) and PLAN-69 (10.5M); 65% of the tokens fell in finalize (security audit 16 times, functional review 11, code review 9; 37 finalize dispatches).
- The 5-execute accumulator carries about 393,000 tokens, 200 tool uses and 1,500,000 ms of metric values that the session's orchestrator recorded by mistake (the plan's own statement; a work-log WARNING names the over-count); the figure is a floor for the init phase and over-counted for execute, so it is not a reliable total in either direction.
- Review budgets were spent (functional review 3/3, code review 5/5 plus operator grants); the final head `cb39204` was not reviewed by the earlier-done steps (their `done` records stand at `b494a52`; architecture-refresh at `5e53366`). The merge was done on the operator's word with `ci pr merge-queue`, not by the branch-cleanup barrier, and `automatic-review` was closed by `loop-back close` as an operator override with re-fire waivers.

## Routing and Merge Behavior

- Review: functional review as above; code review `blocking_count=0`, `advisory_count=9`. CI on the merge candidate green (Java 25 and 26, integration tests, quarkus alignment, dependency review, SonarCloud); CodeRabbit green.
- CI/merge: merge queue, `cleanup_owed=false`; main was merged into the branch twice, which renumbered log identifiers (`PM_RUNTIME-30`, `-31`, `-130` to `-132`), the `core-store` ADRs (030, 031) and `runtime-start` ADR 013.
- Surface calibration (declared against realised): the spec declared 54 entries; the merged diff touched 86 files, 43 of them not declared (13 `pm-runtime` main classes, 11 tests, 3 `pm-core` classes, 2 resources, `doc/LogMessages.adoc`, `doc/Traceability.adoc`, `doc/Specification.adoc`, `doc/ImplementationWatch.adoc`, six specification parts, `pm-core/README.adoc`, `pm-runtime/README.adoc`, `doc/plans/slots-usage-and-budgets.adoc`, one ADR). Twelve or more declared class entries were not touched (the class names were guessed: `JobRuntime`, `ProcessTermination`, `JobRecovery`, `VerifyNotResolvePipeline`, `RecoveryStep`, `StartupSequence` and their tests). The declared overlap with PLAN-74 in `RecoveryStep`/`StartupSequence` never materialised, as the branch footprints already showed on 2026-10-17; the declared overlap with PLAN-14 and PLAN-69 in `core.store` was real only for the ADR numbers. The spec is shipped, so this is calibration only: a class-level surface drafted from names, not from the code, is wrong in both directions.
- No collision with PLAN-14 was reported.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-20 --status shipped`
- [x] row `pr`, `landing`, `plan_marshall_plan_id` stamped — `orchestrator queue --set-row PLAN-20`
- [x] epic.md narrative reconciled (queue annotation; Watches for the native build and the undelivered obligations of #84)
- [x] landing message `plan-20-job-runner-015.md` archived after the reconciliation was persisted
- [x] candidate lessons `-001` to `-014`: dispositioned in the decision log (`-001` to `-012` forwarded to plan-marshall's `lessons-routing` inbox, `-013` and `-014` folded as pm-runtime facts into the staged specs)
- [ ] owed outside this verb: the native build of `pm-mcpd` against the SNAPSHOT (only check of the `kill` downcall descriptor); step 3a of #84 and `harness_update_unverified` (owner assigned through PLAN-78); the open items of PR note 5 (staged as a follow-up plan)
- [x] resume anchor updated in `resume_anchor.md`

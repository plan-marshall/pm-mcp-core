# Landing Analysis: PLAN-30 — Worker supervisor

epic: pm-mcp-core-m2-m4
workstream: WS-04
pr: #190 (merged as f4a1bd07f480bbf04c18545192e0690fbca03563)

> Landing record for one shipped plan. Source: inbox message `plan-30-supervisor-002.md` (plan lane, opencode,
> 2026-10-26T15:47:51Z), corroborated against git at `f4a1bd07`, the PR and its checks through the `ci` abstraction, the
> SonarCloud public API (`pullRequest=190` and `branch=main`), and the code the deliverables name. The message is a lead;
> every fact below marked "checked" was read from git, the PR or the API by me, not taken from it. No test was re-run: the
> test counts are the lane's report, the CI result is read.

Standing rules: r1 and R01 to R18 named by the landing. **R05 is applied this time**: the implemented specification prose
and the covered watch items were deleted (76 watch lines, 53 specification lines), contrary to the four earlier landings.

## Deliverable Fidelity vs Spec

The spec's six bullets plus the carried PLAN-19 gate were re-outlined by the lane into **14 deliverables** (the spec text
named 6; the operator's limit for this epic is 12, see Anomalies). Mapping and what I checked:

| Lane deliverable (spec bullet) | Verdict | Evidence |
|--------------------------------|---------|----------|
| D1 `WorkerState`, `WorkerStateCodec`, `JobRecordStore.writeWorker` (bullet 1) | shipped-as-specified | Checked: `WorkerState.java` (532 lines) and `WorkerStateCodec.java` (372) are `A`; `JobRecordStore` +68; `JobRecordSchemaParityTest` +59 and `WorkerStateCodecTest` 335 lines. |
| D2 `WorkerParameters` | shipped, **undeclared** | Checked: `pm-core/.../profile/WorkerParameters.java` `A` with `WorkerParametersTest`; not in the spec's Expected Surface. |
| D3 `VerificationRecord.tool`, `AuditEvent.workerStartFailed` | shipped | Checked: `VerificationRecord` carries `Tool tool` (line 43); `AuditEvent.workerStartFailed(...)` at line 473 with the constant `worker_start_failed`. |
| D4 supervisor core, generations, `ModelSlots`, `WorkerProtocol`, `WorkerCalls`, `WorkerTimers`, six log records (bullet 1) | shipped-as-specified | Checked: `WorkerSupervisor` (428 lines), `WorkerGeneration`, `WorkerLifecycle`; `ModelSlots.over(ModelSlotScheduler, …)` calls `scheduler.request(...)` and matches `Granted` (the PLAN-31 seam, as the operator's paste told it); six `RuntimeLogMessages` records `WORKER_GENERATION_STARTED/ENDED/RECYCLED`, `WORKER_START_FAILED`, `WORKER_START_GUARD_HELD`, `AGENT_STARTED_DETECTED`. |
| D5 `LossDetection` (exit, ack, silence) | shipped | Checked: `LossDetection` (282 lines), `LossReason`, `LossSignal`; `WorkerLossDetectionTest` 317 lines. |
| D6 ending, fencing, pause at once, drain (bullet 3) | shipped | Checked: `WorkerEnding` (207), `EndCause`, `WorkerDrainParticipant`; `EndCause` documents "The operator paused the plan at once; its task is queued again without counting a re-offer" and maps it to `JobRunner.CancelCause.PLAN_PAUSED_NOW`. |
| D7 recycling: idle with lease exclusion, token budget, changed configuration (bullets 2 and 4) | shipped | Checked: `WorkerRecycling` (232), `RecycleCause`; `WorkerRecyclingTest` 311 lines. |
| D8 start-failure guard (bullet 1) | shipped | Checked: `StartFailureGuard` (251), `StartFailureState`, `StartFailureReport`, `HarnessStartSpacing`; `StartFailureGuardTest` 323 lines. |
| D9 `agent_started` (bullet 6) | **met_with_residuals**, as the lane says | Checked: `AgentStartedDetector.detect(generation, rootPid, held)` compares path and digest, threshold 1 and action `restart` in its Javadoc, `AGENT_STARTED_DETECTED` logged; `SystemProcessExecutables` registers the `libproc` `proc_pidpath` downcall in `reachability-metadata.json` (new descriptor `jint(jint, void*, jint)`). **No caller of the detector exists in `src/main`**: `grep AgentStartedDetector pm-runtime/src/main` finds only its own file, and `WorkerSupervisor` is referenced only inside the package. The scenario test builds the detector itself. The "pass is the supervision loop's call" (developer doc line 268); the loop is unbuilt (see Follow-Ups). |
| D10 `ProfileWorkerEligibility` (carried gate, `PM-WATCH-CLIENT-13`) | shipped | Checked: `evaluate(LoadedProfile, RuntimePlatform, HarnessProfile)` honours `worker.eligible: true` only with `faults` and `egress` `passed`, a non-null `zero_tool` only with `classes` and `isolation` `passed`; `not_run` is a constant; two control fixtures `verification-record-faults-failed.json` and `verification-record-isolation-not-run.json`. The gate reads the record through its own types, **`LoadedProfile.java` is untouched** (declared, not changed). |
| D11 `ProbeWorkspace`, `DecoyServers`, `VerifyingBinaryCheck` + control records | shipped | Checked: the three classes and their tests exist (`ProbeWorkspaceTest`, `DecoyServersTest`, `VerifyingBinaryCheckTest`); `PM-WATCH-CLIENT-13`'s three named controls are built here, not handed to new watch items. |
| D12 `FixtureHarness`, `VirtualClock`, Done-When scenarios | shipped | Checked: `FixtureHarness` (235, a real child process), `VirtualClock`, `SupervisorScenarioTest` (386) with the `agent_started` scenario at line 296; `SupervisorTestFixture` 456. |
| D13 `runtime-worker.adoc` and ADRs | shipped | Checked: `pm-runtime/doc/developer/runtime-worker.adoc` (547 lines) and **nine** ADRs `runtime-worker/001` to `009`; `runtime-enrolment/006` amended. |
| D14 specification/watch reduction, traceability, index row and package file removed | **met_with_residuals** | Checked: `doc/plans/supervisor.adoc` deleted (30 lines), its index row gone and `supervisor` dropped from the _Depends on_ of seven rows (`PLAN-34, 51, 59, 60, 66, 73`, `35`); `model-work.adoc` −58 and `client-profiles.adoc` −18 watch lines; `03-supervision-and-recycling.adoc` +6/−39; `doc/Traceability.adoc` +87 with `PM-WORK-3`, `PM-ARCH-8`, `PM-CLIENT-5`. Residual: five links in `plan-marshall-documentation` still name the deleted watch anchors (the correction pull request there is owed). |

**Done-when**, clause by clause, against the tests that exist (named, not re-run): losses at their deadline and never
before (`WorkerLossDetectionTest`, `VirtualClock`); a start failure repeated to the guard's count ends the role's starts and
a changed configuration clears it (`StartFailureGuardTest`); a pause at once ends by force and re-queues without a re-offer
(`WorkerEndingTest`, `SupervisorScenarioTest`); a changed role assignment recycles after the task and a consulting worker's
`wait_again` answers do not recycle it as idle (`WorkerRecyclingTest`); an enrolled executable or `pm-operator` in the
worker's tree is detected, restarted, re-offered and named in a `security` record **in the scenario**, never in production
(no caller). **Sonar clause:** met (see below).

### Whole diff, checked

`git diff f4a1bd07^1 f4a1bd07` (first parent `2dba06fd` = #192): **95 paths — 70 `A`, 24 `M`, 1 `D`; +12,263/−194.**
By kind: 34 production Java files in `runtime.worker` (with `package-info`), 6 in `pm-core` and `RuntimeLogMessages`, 26 test
files, 14 `.adoc` under `doc/`, 11 module `.adoc` (the developer guide, nine ADRs and the amended `runtime-enrolment/006`), and
3 JSON files (two control fixtures and the reachability metadata). One squash commit; the lane's branch and worktree are gone.

### Surface delta (`inbox landing-check`)

`expansion_detected`: declared 12 entries (two of them directories), realized 95, **25 undeclared, 1 declared and untouched**.

- Undeclared: `doc/LogMessages.adoc`, `doc/Traceability.adoc`, `doc/plans/token-usage.adoc`, `doc/plans/worker-activity.adoc`,
  `model-work/04-worker-launch.adoc`, `05-consultation.adoc`, `09-completion-usage-and-spend.adoc`, `store-schemas/11-enrolment-store.adoc`,
  the `pm-core` store files (`JobRecordStore`, `WorkerState`, `WorkerStateCodec`) and their tests, `AuditEvent` and its test,
  `WorkerParameters` and its test, `VerificationRecordToolTest`, `runtime-enrolment/006`, `RuntimeLogMessages` and its test,
  the native-image `reachability-metadata.json`, and the two JSON control fixtures.
- Declared, untouched: `pm-core/.../profile/LoadedProfile.java`.
- Two of the undeclared are the ones the spec warned of (`RuntimeLogMessages` "not a declared surface" by design; the
  index row neighbours). The `pm-core` store files are the real expansion: the spec's Expected Surface was inferred from the
  package file's `Where` line (a HYPOTHESIS), and the worker state lives in the job record.
- The footprint base the tool reports is `f4a1bd07` itself (`origin/main` now is the merge); both path lists were supplied,
  so the counts are the merge's own diff.

## Metrics and Anomalies

- **Checked CI:** `ci checks status --pr-number 190`: `success` over 30 checks; `build (25)`, `build (26)`, `sonar-build`,
  `quarkus-alignment`, integration tests, SonarCloud, CodeRabbit all pass. One earlier workflow run on the PR (`40345362878`)
  shows `build / build` SKIPPED beside a green `covering-run-check`; the run on the final head (`40345375423`) ran the build.
- **Checked Sonar:** `pullRequest=190`: quality gate `OK` (new coverage 82.9, duplication within the threshold), **0 open
  issues**. `branch=main` still reads the **2** open issues of PLAN-104's landing: `java:S9428` `StoreDigest.java:97`, `java:S112`
  `MacSecurity.java:305`; PLAN-30 added none. Lane's test counts (3459 `pm-core`, 5374 `pm-runtime`) not re-run.
- **Live proof of the docs-only skip: delivered.** #192 (`2dba06fd`, specification only: no non-`.adoc` path) shows
  `build / build (25)`, `build (26)` and `sonar-build` `SUCCESS` and `check-changes` `SKIPPED`, so the build ran; #186 ran it
  the same way. ADR `doc/adr/009` holds in practice; the memory note `docs-only-pr-skips-the-parity-tests` is retired.
- **Anomaly: 14 deliverables against the raised limit of 12** (operator decision of 2026-10-22). The spec carried 11 at the
  earlier outline and 6 bullets plus the carried gate; the lane's re-outline split the gate and the controls. No recorded
  rationale was read in the message; the plan shipped as one pull request of 95 paths, which is the cost.
- **Anomaly: undeclared `pm-core` store files.** The job record carries the worker state now (`WorkerState`, ADR
  `runtime-worker/003`); 21 staged and parked specs declare the `core/store` directory (see Collision analysis).
- **`total_tokens=unknown`** (the 30th message); `plugin_version=0.1.1996` and `executor_version=0.1.1996` read this time (the
  `unknown` of PLAN-104 was a one-off). `guard: none` on this harness.
- **Build gate killed once:** `verify -Ppre-commit` on `019824ea` was killed (`exit_code -9`) after 904 s with no verdict; not
  retried blind, the module-scoped verify ran, the full gate was later green twice. The third external kill of the gate on this
  host (PLAN-94 had two).
- **The lane asked six operator questions**, including "Merge PR #190?" answered "Merge now": the merge had the operator's word.
- **CodeRabbit:** 24 findings, 22 fixed, 1 partial (tracker retirement needs the unwired supervision loop), 1 rejected (no
  production loop in this slice); last re-trigger refused on quota; `sourcery` refused the diff size.

## Routing and Merge Behavior

Persona routing as the message states (implementer, module-tester, documenter, code-reviewer); the spec names no
per-deliverable skills. Plan retrospective `skipped`. Merge: one squash commit through the merge queue; the lane's branch
`feature/plan-30-supervisor` and its worktree are removed (`git branch -a`, `git worktree list`: neither exists). Review
rounds: functional review round 1 found six blockers (D11 to D14 not met, three false self-reports), fixed in four tasks;
the code review found seven blockers over three rounds; Sonar needed 2 blockers and 52 smells cleared. These are the
lane's figures.

## Collision Analysis

`corpus declaration-currency` over the 95-path footprint, PLAN-30's own spec excluded: all 102 compared specs read
`disagree` (declarations are package-level), one is unevaluated (PLAN-102). **30 staged and parked specs share files beyond
the index with the footprint:** 28 on code (`JobRecordStore`, `WorkerState`, `WorkerStateCodec` by `core/store` directory;
`VerificationRecord`, `WorkerParameters` by `core/profile`; `runtime/worker/` by package) and 2 on documents only (PLAN-35,
PLAN-60). **Two specs say `runtime.worker` holds only `package-info.java`** (PLAN-66 in its Expected Surface, PLAN-73 in
Deliverable 1): their premise "the package is greenfield" is now false. (The first count of this report, seven, was a prefix
match on the `runtime/` root that PLAN-46, 47, 57, 58, 62, 76 and 77 declare as a hypothesis; the cleanup of 2026-10-26
read the specs and corrected it.) Running plans: none. The two shared files with the
last landing (`model-work/04-worker-launch.adoc`, `store-schemas/11`) merged clean.

ADR numbers: `runtime-worker` 001 to 009 are taken (a new directory); `core-store` stays at 046 next and `runtime-launch` at 006
(PLAN-30 took none there); `runtime-enrolment/006` was amended, not added.

## Reconciliation Actions

- Row PLAN-30: `running` → `shipped`; `pr` `#190`; `landing` `landings/PLAN-30.md`; `plan_marshall_plan_id` `plan-30-supervisor`.
- `epic.md`: the queue annotation. **Stay open:** the verify-not-resolve validation of a composed vector (nothing in
  `runtime.worker` references `VerifyNotResolve`), and the `HarnessEnrolmentStore` top-level `harnesses` decode lead (the
  lane's `runtime-enrolment/006` amendment concerns the sidecar, not that member). **Closed:** the "record's stage and
  platform gate had no owner" defect (delivered: D10 and D11), and the PLAN-30 half of the model-slot seam (delivered:
  `ModelSlots.over`). **Added:** the supervision-loop Open Defect, the 14-against-12 note, and the third external kill of the
  pre-commit gate. The Sonar defect is unchanged at 2 open; `total_tokens` recurs for the 30th time.
- No spec is retired or corrected; PLAN-30's spec is terminal.

## Follow-Ups

1. **The production supervision loop has no owner.** `WorkerSupervisor`, `AgentStartedDetector`, `LossDetection` and the
   recycling are a library with a fixture harness; nothing in `pm-runtime` runs a periodic pass, and `pm-mcp-server`
   (`plan-marshall-mcp`) is told to wire `WorkerProtocol` and `WorkerCalls` (ADR `runtime-worker/009`). A search of the staged
   specs for "supervision loop" found none. Candidates: `worker-activity` (PLAN-66, which raises the other four off-track
   signals and so needs a pass too), or `pm-mcp-server` wiring in `plan-marshall-mcp`. The operator decides.
2. **Re-ground the specs made stale by #190** (the seven that declare `runtime/worker/`, the 28 on code), together with the 14
   of #181; do it once, after the documentation session stops merging.
3. The correction pull request in `plan-marshall-documentation` for five links to deleted watch anchors (the operator merges).
4. The code review's twelve advisories sit in the lane's `work/code-review-findings.json`; none blocks.
5. The lessons-routing note about the flat OpenCode install stays with plan-marshall's inbox, not filed here.

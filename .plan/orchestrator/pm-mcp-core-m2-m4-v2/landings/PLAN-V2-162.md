# Landing Analysis: PLAN-V2-162 — The runtime worker and job residue (cut at the delivered boundary)

epic: pm-mcp-core-m2-m4-v2
workstream: WS-06
pr: #567 (https://github.com/plan-marshall/pm-mcp-core/pull/567), merge `90505ad2c690193439fa2258e702d09e9638c616` (merge-group run `45689467397`, success), final head `738bde1b`, 71 files (+1405/-257)

> Landing record for one shipped plan (runbook lane `rb-v2-162-runtime-worker-and-job-residue`, opencode, `deepseek-v4.1-flash/high`). Written by the `analyze` verb of the thirty-eighth run (2026-11-25) from the inbox message `rb-v2-162-runtime-worker-and-job-residue-001.md` (revision 27; the earlier partial landing, revision 25, is superseded by it). The PR is corroborated through `ci pr view` (state merged, merge commit `90505ad2`) and git (`git diff c6986b48 90505ad2`); the merged code was read by one read-only level-3 leaf (57 tool calls, no write) and every point below marked "re-read" was read again by the orchestrator at the line. The operator cut the pull request at the delivered boundary on 2026-11-25: deliverable 1 is met, 2 to 5 are delivered in part, and the rest is split off to plans PLAN-V2-197 to 202 (staged before this landing).

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 start-failure wiring and the answer schema (56, 49, 75(a), 61) | shipped-as-specified for the two refusals the spec names; the other two refusals the specification counts stay uncounted (L1-1, L2-1) | `WorkerSupervisor.java:278` calls `countStoreRefusal` (`:386-391`: only `HARNESS_STORE_UNREADABLE` and `HARNESS_TAMPERED`), re-read; `AnswerSchema.java:84-132`; `WorkerSupervisorStartFailureTest.java:77` (the hold test fails on the old code) |
| D2 hold and worker (55, 19, 30) | shipped in part, as the cut says: 19 (override, not clock seam, reason in the Javadoc), 55(a)(c), 55(b) in part, 30(a), 30(c) in part | `SupervisorCalls.java:221, 239-253`; `ProbeBudget.java:80`; `WorkerVectorCheck.java:117-120, 167`; tests `SupervisorCallsTest:435-463`, `ProbeJobTest:363` |
| D3 supervisor residue and git-metadata guard (42, 41) | shipped in part; 41(a), 42(f) gaps, 42(h) `finishAttempt` split off | `SupervisionPass.java:196, 343` (`TaskNotOpenException`), `GitMetadataDigest.java:100-110`, `SubprocessJobRunner.java:1082-1129` |
| D4 grading adapter (48) | shipped in part: 48(a) and 48(c) zero-tool only; the rest split off to PLAN-V2-197 | `WorkerGradingHarness.java:110-112`; `QualificationService.java:52-61` |
| D5 job-log read-out and egress bound (44, 50, 81, 85) | shipped in part by the lane's own count; 44, 81, 85, 50(a)(c) delivered; 85 includes the wiring of `HarnessStartSpacing` (L3-1) | `JobLog.java:349, 360`, `SecretRedactor.java:94, 347, 626-636`, `DiagnosticCollector.java:133-139`, `HarnessStartSpacing.java:72-85`, `WorkerSupervisor.java:309-317` |

The lane's own count is `deliverables_done=1` of 5, and this analysis agrees: the spec's Done-when ("Deliverables 1 to 5") is not met and was not the operator's cut.

## Three-Layer Analysis

- **Dispatch:** one read-only leaf per landing (`execution-context-level-3`, `orchestrator.analyze` surface), 57 tool calls, no write. Reason: 71 files of runtime, concurrency and redaction code whose correctness is decided by reading lock order, regexes and caller sets.
- **Orchestrator re-read** at `90505ad2`: `WorkerSupervisor.java:141-165, 270-320, 380-395` (the synchronized `start`, `startOne`, `startAfterSpacing`, `countStoreRefusal`), `SupervisorCalls.java:370-392` (`firstCall`, `known`), `WorkerEnding.java:88-100`, `HarnessStartSpacing.java:60-95`, `HarnessVerifier.java:98, 129, 144`, `11-enrolment-store.adoc:58` (the refusal rule), `WorkerSupervisorStartFailureTest.java:90-115`, `DiagnosticCollector.java:133-139`, the `QualificationService`, `SupervisorEnvironment` and `SupervisorCalls` diffs, and the progress file's 15 `finding:` lines. Plan-marshall-mcp `origin/main` `fd7e252` read for consumers of the changed runtime API. Points not re-read are marked unverified leads.
- **Verdict:** layer 1 findings (two, one a minor lead); layer 2 findings (three major points of the leaf, one of them refuted by the spec, see below); layer 3 findings — **one major defect (L3-1), no blocker**.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L1-1 | Spec premise "`StartFailureGuard.startFailed` has one definition and no caller" holds at the base, but its scope names two refusals; the specification counts four: `11-enrolment-store.adoc:58` says `harness_version_unsupported` and `harness_not_enrolled` also "count as a start failure of the role on that harness" and hold the role | checked, re-read | refutes (the premise is narrower than the rule) | `11-enrolment-store.adoc:58`; `HarnessVerifier.java:98, 129, 144` refuse with those reasons |
| L1-2 | The Done-when "Deliverables 1 to 5" cannot hold after the operator's cut of 2026-11-25 | checked | refutes | the lane's `deliverables_done=1` and the cut text of the landing |
| L1-3 | The spec's claim that `ConsultationService.validateSubmit` has no caller | checked, orchestrator grep at `90505ad2` | confirms (and now moot: the removal plan PLAN-V2-202 replaced the wiring plan) | `ScenarioReplayRunner.java:442` is the only caller |
| L1-4 | Lead: the spec edit `04-worker-launch.adoc:112` asserts "launch skill renders empty fails it for a fresh worker; prefix skills not checked" against `PromptComposer`, whose Javadoc line 31 says the composition fails for any skill that renders empty | not_shown_by_reading | none | not re-read |

The claim section of this spec is a numbered list the parser does not address, so no corpus verdict is persisted from this layer.

**Layer 1 verdict:** findings — 2 checked, 1 lead.

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|----------------------------------|---------|
| D1, 56 | `WorkerSupervisor.java:278, 386-391` (re-read) | `threeStoreRefusalsHoldTheRole` fails on the old code (it asserts the hold); `storeRefusalCountsOneStartFailure` (`:60`) asserts only the recorded reason and one non-hold, so it passes on the old code too; only `harness_tampered` is exercised, `harness_store_unreadable` has no wiring test (L2-2) | the other two refusals of L1-1 stay uncounted (L2-1) | checked |
| D1, 49 | tests only: `ProfileWorkerEligibilityTest`; the main diff of `ProfileWorkerEligibility` is a Javadoc reflow | closes gaps over existing behaviour, so it passes on the code it guards; no mutation evidence in the diff (the lane claims `mutation-after`, not re-run) | none | not_shown_by_reading for "fails on the code it guards" |
| D2, 55(b) | `SupervisorCalls.java:221, 239-253` | `anAnswerDeliveredLeavesTheHold`, `aHoldOpenedAtTheBudgetRecycles` fail on the old code | none | checked |
| D2, 30(c) | `WorkerVectorCheck.java:117-120, 167` | `anExecutableWithAMetacharacterConforms` pins the exemption; the negative (element 1 and later still refused) is covered by an older test the leaf assumed | none | checked, negative not shown |
| D5, 44(a) | the production change in `DiagnosticCollector` is only the surrogate-safe truncate (`:133-139`, re-read); the bound the lane names lives in `SecretRedactor` (`:94, 347, 626-636`) | flood test and key-block tests changed shape; whether the flood test fails on the old code is not shown by reading (wall-clock bounds of 120 s and 5 s: flake risk, L3-5) | the lane's narrative "flood and key-block bound in `DiagnosticCollector`" names the wrong class | not_shown_by_reading |
| D5, 85 | `HarnessStartSpacing` wired in `WorkerSupervisor.java:292-317`, `WorkerEnding.java:93-94`, `SupervisorCalls.java:380` | `aStartWaitsForThePendingStartOfItsHarness` pins the full-bound wait with no first call (it pins the defect of L3-1); no test pins either release site (`WorkerEnding:94`, `SupervisorCalls:380`, `WorkerSupervisor:294`): deleting one breaks no test | the leaf called the wiring "unexplained"; **refuted: item 85 is in the spec's Carried Open Items and the Continuation's R07 answer says "`HarnessStartSpacing` is wired in `WorkerSupervisor`"**; the narrative lists 85 as delivered under D5 | checked |
| (assembly API) | `QualificationService.harness` takes a required `WorkerEnding`; `SupervisorEnvironment` gains a required non-null `spacing`; `SupervisorCalls.detection/loss/forget` go from public to package-private; `StartGuard.startFailed` gains a default method | n/a | the narrative says no assembly-visible change was made; these are assembly-visible by signature. **No consumer exists:** `git grep` over `plan-marshall-mcp` `origin/main` `fd7e252` finds none of `SupervisorEnvironment`, `QualificationService`, `HarnessStartSpacing`, `WorkerGradingHarness`, `SupervisorCalls`, `StartFailureGuard` | checked (nothing breaks today) |
| (unlisted files) | `EndpointHarness` (`atModelsPointer` extraction), `EndpointVerification` (import swap), `EndpointOutcome` and `HarnessEnrolmentStore` (dead `IllegalArgumentException` catches removed), `LaunchRefusal` (`ARGUMENT_REJECTED` act text), `AuditChainVerification` (two refusals, message reworded), `SubprocessJobRunner:1082-1129` (see L2-3), `JobConfinementComposer` (WARN 153) | n/a | no deliverable names them; each is small and behaviour-neutral except the two named | not_shown_by_reading for the user-visible texts |

- **L2-1 (major):** the other two refusals the specification counts, `harness_not_enrolled` and `harness_version_unsupported`, never reach `startFailed`: `countStoreRefusal` names only two reasons, and the Javadoc this PR added says "a refusal of any other reason counts nothing" (`StartFailureGuard.java:36-37`), which contradicts the specification on `main` (L1-1). The `CompositionException` path (`WorkerSupervisor.java:287-291`) and a runner `Refused` after spawn (`:292-297`) are not counted either, although `StartFailureGuard.java:32` says "the runtime refuses the launch before its spawn" counts.
- **L2-2 (minor):** the weak test named above; `harness_store_unreadable` has no wiring test.
- **L2-3 (minor, unlisted behaviour change):** `SubprocessJobRunner.java:1082-1129` records the worker's `git_metadata` as `UNENFORCED` unless the composition sent a read-only path; `runtime-job.adoc` still says "no code path raises it to enforced except the copy of a report's value". The narrative has no item for it.
- **L2-4 (minor):** `RuntimeLogMessages` `PM_RUNTIME-153` is a second deviation from the spec's R12 ("none"); the lane recorded only 152. Its Javadoc says the record "names the counts, never the paths" while the template logs the project root path as its first argument.

**Layer 2 verdict:** findings — 4 (one major).

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| **L3-1** start spacing waits for a first call that cannot happen | `WorkerSupervisor.java:141` (`public synchronized start`), `:309-317` (`awaitTurn`), `HarnessStartSpacing.java:72-85`, `SupervisorCalls.java:89, 390-391`, `WorkerSupervisor.java:173` — all re-read | a second start of a harness (or two requests in one `start`) while the first worker of that role has not made its first call | `start` holds the supervisor monitor while `awaitTurn` waits up to the 30 s bound; the first worker's first call enters `callHeartbeat` (synchronized on the calls object), `known` calls `supervisor.generation(...)` (synchronized on the supervisor) and blocks until `start` returns, so the early release by `SupervisorCalls.firstCall` (`:380`) never fires for one supervisor: every second start of a role's harness waits the full 30 s, and every call of every worker of that supervisor stalls with it (heartbeat, wait, `turnCompleted`, `taskClosed`) and the timer thread's `lost()`. The test `aStartWaitsForThePendingStartOfItsHarness` (`:96-113`) asserts exactly this full-bound wait, so it pins the stall. The lane's own review found it (CodeRabbit inline-7, -8, -9) and split it to PLAN-V2-201 by the operator's cut; the merge therefore ships a wired defect. **Latent:** nothing in the assembly constructs the supervisor yet (`fd7e252`) | major |
| L3-2 spacing ownership | `WorkerEnding.java:93-94` | an `end()` of a worker long past its first call, or a worker of zero-tool grading that never calls | `ended(harness)` runs before the idempotence check and clears the harness's one pending flag, which may belong to a newer start, releasing its waiter early; a zero-tool worker makes no first call (`StartFailureGuard.java:32-33`) so each following start of its harness waits for its end or 30 s; `startWorker` (`:325-349`) releases the slot but not the spacing when the runner throws (CodeRabbit inline-9) | minor |
| L3-3 `countStoreRefusal` ordering | `WorkerSupervisor.java:278-280, 386-391` | `startFailed` throws (audit sink or `protocol.closeUnavailable` at the hold) | the claimed model slot (`:271`) leaks and the refusal is never recorded; at the third failure the role's tasks close before this launch's own refusal is written; the returned `StartFailureReport` is discarded; after the guard holds mid-loop (`:160-162`) the remaining requests still run `startOne` and count again | minor |
| L3-4 fail-closed digest on a stale worktree record | `GitMetadataDigest.java:100-110`, `GitMetadataPaths.java:169` | a linked worktree whose directory was removed before `git worktree prune` | the digest carries `unreadable`, no baseline is taken, every verify answers `Changed`, every git job is refused `git_metadata_changed` until the operator rebaselines; the product creates and removes item worktrees, so an availability regression is plausible. How often it occurs: not shown by reading. The fail-closed intent of 41(b) holds | minor |
| L3-5 test bounds | `SecretRedactorTest` (`PEM_BOUND` 5 s), `DiagnosticCollectorRedactionTest` (`FLOOD_BOUND` 120 s) | a loaded host | wall-clock assertions can flake | minor |
| L3-6 `JobLog.capped` guard | `JobLog.java:360` | a multi-byte or invalid-UTF-8 tail under the test-only `cap` parameter | `tailText.length() == cap` compares chars with a byte cap, so the first line may be repeated; unreachable at the shipped 16 MiB cap against the 64 KiB first-line bound; the tests use ASCII | minor |
| L3-7 process-global mutator | `ProbeBudget.java:80` | any production caller | `override` and `clearOverrides` mutate a static map; the guard forbids only raising a budget; the Javadoc says production never calls it; parallel-test interference not shown by reading | minor |
| L3-8 lock order (pre-existing) | `StartFailureGuard.java:175`, `WorkerRecycling.java:201` | `clear()` holds the guard monitor and calls into the synchronized supervisor while `start()` holds the supervisor monitor then the guard's | supervisor→guard against guard→supervisor; the PR adds a second supervisor→guard acquisition that also does audit and queue I/O; whether it deadlocks at run time: not shown by reading | minor |

**Confirmed clean (re-read or checked, polarity confirms):** `WorkerVectorCheck.java:167` the metacharacter exemption starts at position 1 and element 0 must equal the enrolled executable path exactly (`starts()` `:117-120`), the job runs by `execve` with an argv list, so the exemption cannot smuggle a second command; the removed empty-split branch was dead (`split` with limit -1 never returns an empty array). `SecretRedactor` doubly escaped JSON and the line-structure-preserving mask are linear. `JobLog` head/tail read is overlap-free at the shipped cap. `AnswerSchema` refuses a bound that is not an integer in int range (`minItems 2.0` is refused: fail-closed, the carried item 56(b)'s decision). `WorkerGradingHarness` zero-tool worktree and `ProbeBudget` seam are pinned by tests that fail on the old code.

**Layer 3 not shown by reading:** the deadlock of L3-8; whether the flood test fails on the pre-fix pattern (the lane gives a failing run id, not re-run); how often L3-4 occurs; whether the long fixtures default spacing slows other multi-start tests.

**Layer 3 verdict:** findings — 8 (one major).

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md Code Standards | Optional only as a return type | conforms | `SecretRedactor.urlUserInformationMatch`, `AnswerSchema.malformed*` |
| CLAUDE.md Code Standards | no generic catch | conforms | new catches `TaskNotOpenException`, `InterruptedException`; dead `IllegalArgumentException` catches removed |
| CLAUDE.md Code Standards | every `LogRecord` a class logs is asserted by its constant with `LogAsserts` | **deviates** | `PM_RUNTIME-152` at `WorkerRecyclingTest.java:298` and `PM_RUNTIME-153` at `JobConfinementComposerTest.java:281` are asserted by free-text substring (`assertLogMessagePresentContaining`), not by the constants |
| CLAUDE.md Code Standards | LogRecord holder, ranges, `doc/LogMessages` | conforms | next free WARN identifiers 152 and 153, rows in `doc/log-messages/pm-runtime.adoc` |
| CLAUDE.md Code Standards | assertion messages of 20 to 60 characters | conforms for single-line assertions; wrapped messages not scanned | the leaf's scan |
| CLAUDE.md Code Standards | no new hand-written fake | conforms | `WorkerGradingHarnessTest` uses `createMock` |
| CLAUDE.md Code Standards | test data from generators, literals only where the contract | conforms with two minor literals | `ProbeBudgetTest` uses `30L` although a `MODELS_SECONDS` constant exists; `WorkerVectorCheckTest` `/opt/pm&tools/claude` |
| CLAUDE.md Dependency Rules | no new dependency or plugin | conforms | no POM in the diff |

## Metrics and Anomalies

- Tokens: `unknown`. The lane states `total_tokens=unknown` (the plan-less metrics file records `enrichment_skipped`, `no_session_id_identity_unresolved`); recorded as unobserved, not zero. `landing-check`: `complete: false`, missing key `total_tokens` (an Open Defect entry, below).
- Duration: not recorded; builds: `pm-core` 5 669 and `pm-runtime` 8 436 tests green at intermediate heads, 8 594 (both modules) at `738bde1b`, log `111abdbcfc7a44f3af5639e058453187` (lane's figures, not re-run).
- Anomalies: the self-review step ended `budget-exhausted` (first run); `harness=opencode`, `model=deepseek-v4.1-flash/high`; CodeRabbit refused on quota on 2026-11-22 and 23 and reviewed on 2026-11-24 and 25; Sourcery refused (review budget); the heads `61942366` and `77d08aea` carry no build check (no CI run was created) and the lane did not claim CI green on them; two shell-rule breaks and the R17 links check not run are recorded by the lane itself (§7 of its message).

## Routing and Merge Behavior

- Review: re-read by `runbook_lane review-gate --pr-number 567` after the merge: `unresolved_review_threads: 0` (the landing says 0: agrees), `review_body_findings_total: 15` (agrees), `review_body_findings_handled: 0` with `reason: body_findings_undisposed` — **the provider cannot reproduce this number**; the dispositions live in the run's progress file, which is still readable: `.plan/temp/rb-v2-162-runtime-worker-and-job-residue/progress.md` carries 15 `finding:` lines (lines 105-119), so `review_body_findings_handled=15` is **re-verified** (the gate's 0 is the missing disposition input, not a difference). The 15 dispositions: 7 fixed (`d5c8da5d` ×6, `738bde1b`), 5 answered with reasons (inline-1, -3, -4, -13 and one duplicate), 3 split off (minor-1, inline-7, inline-8, inline-9: the start-spacing ownership, the monitor-held wait and the release when the runner throws — the same defect as L3-1, L3-2).
- CI/merge: merged through the queue as `90505ad2` (merge-group run `45689467397`, success); the branch merged `origin/main` three times after the cut (`3aea14c2`, `3ba79c4f`, `c6986b48`), the last conflict in `05-consultation.adoc` resolved by taking `main`'s text (the file is identical to `main`'s). `merge_state` read back through `ci`: `merged`. CI on the final head `738bde1b`: green per the lane (both build shards, integration tests, dependency review, SonarCloud); not re-read after the merge.
- Sonar: the landing's `sonar_open_new_issues=0` on `738bde1b`. The pull request is merged, so the open-finding read is **undecidable** after the merge (the provider answers for the head that no longer carries the analysis); recorded as the lane's measured figure, not re-measured, and not as an anomaly.
- Collisions: none observed with 187, 188 or 195 (the gate's earlier zero-overlap read stands). The PR touched the files PLAN-V2-146 to 150 declare (3 to 15 shared files each), so those plans rebase on `main` before they start; the gate reads the live surface at emit.

## Reconciliation Actions

- [x] row `status` → `shipped`
- [x] row `pr` stamped `567`
- [x] row `landing` stamped `landings/PLAN-V2-162.md`
- [x] row `plan_marshall_plan_id` stamped `rb-v2-162-runtime-worker-and-job-residue` (taken from the matched sender, not typed)
- [x] epic.md narrative: the 162 hold-back notes retired; the Decisions entry of the thirty-eighth run records the landing and where each layer-2 and layer-3 finding went
- [x] Open Defect: the landing message is incomplete (`total_tokens` missing; `landing-check` `complete: false`)
- [x] resume anchor and `queue-view.md`

## Follow-Ups

- **L3-1, L3-2, L3-3 and L2-1, L2-2 go into the staged PLAN-V2-201** (`start-spacing-ownership-and-monitor-held-wait`, WS-06): the start path of `WorkerSupervisor.startOne` is one surface, and the plan already owns the two CodeRabbit concurrency findings. The fold adds the refusals the specification counts (`harness_not_enrolled`, `harness_version_unsupported`, the composition and post-spawn refusals), the test for `harness_store_unreadable`, a try/finally around the count, the spacing release on a throwing runner, the ownership of `ended`, and a test for each of the three release sites. **L3-1 is reported to the operator at once (below); the plan is staged, and the start is the operator's word.**
- L3-4, L2-3 and the `runtime-job.adoc` sentence go into PLAN-V2-198 (`git-baseline-decision`, the git-metadata guard).
- The `LogAsserts` deviation (152, 153), L2-4, L3-5, L3-6 and L3-7 go into PLAN-V2-199 (`supervisor-t3-test-gaps-and-finish-attempt`) as one test-and-statement deliverable.
- The assembly-visible signature changes (L2 table) are named in PLAN-V2-197's text: no consumer exists at `fd7e252`, so no companion pull request is owed yet.
- The consultation residue of the lane's message is answered by PLAN-V2-202 (rewritten as the removal of `consultation_ids`).

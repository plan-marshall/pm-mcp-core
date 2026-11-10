# Landing Analysis: PLAN-V2-106 — EasyMock for the hand-written fakes

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #355 (merge `6937efe4364b6abd96dbfdf52c324f0096071874`, merged 2026-11-10T14:02:54Z through the merge queue)

> Landing record for one shipped plan, written by the `analyze` inbox drain of 2026-11-10 from the runbook-lane
> message `rb-v2-106-easymock-for-fakes-001.md` (harness opencode, model deepseek-v4.1-flash/high). The message is a
> lead; the three layers were read by one read-only leaf from git at `6937efe4` (no build), and the provider figures
> were re-read from GitHub and the SonarCloud public API.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 inventory and the rule | shipped-modified | The scan, re-run by reading at `6937efe4`, finds 38 declarations; the PR body table accounts for all of them (its "36 distinct" is consistent). The keep vocabulary is too wide: any "real shared in-memory implementation" or "fixture holding such a class" may stay, so the Done-when is satisfiable without converting what D2 and D3 name. |
| D2 `pm-core`, `pm-workflow`, providers | shipped-modified (not fully met) | Converted: `FailingAuditSink`, `FailingCreateLocks`, `TaskFixtures.RecordingAudit`, `ModelFixtures.RecordingQueue`. Kept: `RecordingAuditSink` (`core/store`), `RecordingLockManager` (sound), `EngineFixtures.RecordingJobStarter`/`RecordingQueueTaskCreator`, `ScenarioFixtures.RecordingQueueTaskCreator` (weak). No provider class matched. |
| D3 `pm-runtime` | shipped-modified (not fully met) | Converted: the endpoint `RecordingAuditSink` and `RecordingRegistry`. Kept: `FakePmExec` (sound), `lock.RecordingAuditSink`, `RecoveryStepFixtures`, `start/RecordingProcessCall`, `SupervisorTestFixture`, `StubProcess` (weak to moderate). |
| D4 shared test JAR | shipped | `FakeBus`, `FakeSecretService`, `TestBases` and the whole `pm-runtime` test JAR are untouched (the diff holds only endpoint tests); "owed outside: none" is correct. |

Facts block: `deliverables_total=4`, `deliverables_done=2` — consistent with the narrative and with this table.

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`general-purpose`, `orchestrator.analyze`), reason: the diff spans three modules and the question is per class; no build.
- **Verdict:** specified — partly; implemented — partly; correct — met for what was converted.

### Layer 1 — Specified

| Finding | Claim | Verdict | Evidence |
|---------|-------|---------|----------|
| L1-1 | D2 and D3 name `RecordingAuditSink`, `RecordingLockManager`, `FakePmExec`, `RecordingProcessCall`, `SupervisorTestFixture` and `RecoveryStepFixtures` for conversion, yet D1 lets any real shared in-memory implementation stay; the Done-when passes with any stated reason. | checked, refutes | spec D1, D2, D3, Done-when |
| L1-2 | The Done-when scan also matches `FailingExitCodeGenerator` (a `TypedGenerator<Integer>`, `FailingExitCodeGenerator.java:20`) and `FailingExitCodeGeneratorTest` (`:21`); neither is a double. | checked, confirms the lane | — |
| L1-3 | The scan misses fixture-resident hand-written doubles: `SequenceJobStarter`, `ScriptedQuestionRecordReader`, `FixedQuestionRecordReader`, `FixedPendingEntryReader`, `ModelFixtures.Qualifications`/`Guard`, `ConsultTestLocks`, `TestLocks`, `Paused`. | checked, refutes | — |

**Layer 1 verdict:** findings — the spec needs a closed keep vocabulary and a scan that excludes `testing/generators/`.

### Layer 2 — Implemented

- The claims of the narrative about the sound keeps are right: `FakeBus.java:35` (Unix-domain-socket bus), `FakeSecretService.java:30`, `FakePmExec.java:40` (`/bin/sh` launcher), `RecordingLockManager.java:29` (stateful real lock manager, about 33 test files).
- Two details of the PR body are wrong: it lists `job.RecordingProcessCall`, which does not exist (only `start/RecordingProcessCall.java:27`, queue-scripted, two users: `ServiceManagerTest`, `ServiceUnitInstallerTest`); it calls `SubprocessJobRunnerTest`'s `RecordingLocks` in-memory, but it wraps a real delegate (`:2136`).
- No `@Test`, `@ParameterizedTest` or `@Nested` line appears in the java diff: the claim holds. Pass counts (4545 / 6833 / 6748) were not re-run.
- **Weak keeps** (each a pure or scripted recorder of the kind the PR itself converted): `lock/RecordingAuditSink.java:24`, `RecoveryStepFixtures.RecordingAuditSink` (`:77`, 7 test files), `core/store` `RecordingAuditSink` (borderline; 21 test files, 31 construction sites), `EngineFixtures.RecordingJobStarter` (`:783`; "cannot toggle after replay" is not sound, `andAnswer` plus a flag does it), `EngineFixtures.RecordingQueueTaskCreator` (`:799`), `ScenarioFixtures.RecordingQueueTaskCreator` (`:512`), `start/RecordingProcessCall`, `StubProcess` (`SubprocessJobRunnerTest.java:2071`), `SupervisorTestFixture` inner classes (`:258-394`), and the per-test `RecordingProtocol`, `RecordingTimers`, `RecordingGuard`, `RecordingStep` (unexamined).

**Layer 2 verdict:** findings — about half of the keeps are weak; the lane's own report is honest about the deviation.

### Layer 3 — Correct

- The converted doubles are behaviour-equivalent: `AuditSinkDoubles.java:85-110` scripts "first append throws, later ones captured or discarded" like the old `FailingAuditSink`; `ConsultationServiceTest` (about `:475-482`) serves the first call, throws on the second, serves later ones like the old `++opened == 2`. The interfaces have one or two methods, so `createMock` strictness is irrelevant. No assertion was lost; no Mockito, PowerMock or Hamcrest; no POM change; no AAA phase comments; the touched messages are 32 to 38 characters.
- Nits (info): three consecutive blank lines at `JournalRecoveryTest.java:113-115` and `OrderedWriteTest.java:101-103` (the lane says it fixed these); `ModelFixtures` trailing blank line; literal failure texts at `AuditSinkDoubles.java:78,88` not generated; `EnrolmentStoreTest` names the capture of accepted appends `refused`; the `TaskFixtures` recorder lost its `CopyOnWriteArrayList` (matters only for concurrent appends).

**Layer 3 verdict:** no defect found by reading; build, coverage and the pass counts not shown by reading.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md Code Standards | test doubles are EasyMock, no new hand-written fake | conforms for the diff; the kept weak fakes are pre-existing | L2 |
| CLAUDE.md Code Standards | assertion messages of 20 to 60 characters | conforms on the touched assertions | L3 |
| CLAUDE.md Code Standards | Mockito, PowerMock, Hamcrest forbidden | conforms | no occurrence |
| operator decision "replace the fakes" | replace | deviates in part | D2, D3 |

## Metrics and Anomalies

- Tokens: `unknown`; `plugin_version` `unknown`; executor `0.1.2088` against the plugin pin, no skew reported by the lane.
- One daemon bound expiry (combined `test -pl pm-core,pm-workflow,pm-runtime` hit 300 s, then split into three runs). No fallback to in-process, no restart.
- The lane stated two lapses itself: the R17 `trace.py` run on the operator's selected option, and no question at the self-review round limit because the PR was already enqueued.

## Routing and Merge Behavior

- Review: GitHub `reviewThreads` of #355: none; Sonar public API: 0 open issues and quality gate `OK` on `pullRequest=355`; equal to the facts block (`unresolved_review_threads=0`, `sonar_open_new_issues=0`).
- CI/merge: #355 `MERGED` at 2026-11-10T14:02:54Z, merge commit `6937efe4`, through the queue.
- Surface collisions with the epic's plans: PLAN-V2-129 (staged) deletes `JudgmentEndpointTest`, `JudgmentEndpointRemoteTest` and `SecretRegistry`, which 106 converted; 129 must start from a branch after `6937efe4` or meets a modify/delete conflict. PLAN-V2-101, 103, 104, 105 (staged, after 106) own the directories core/store, task, model, consult, endpoint, lock/start now touched. PLAN-V2-124 (staged) edits `lock/LockHolderProcess`, `FileLockManagerTest`, `FileLockManagerTwoProcessIT`, users of `lock.RecordingAuditSink`. PLAN-V2-114 edits `core-store.adoc` lines 728, 729, 824, 826; 106 touched about line 2072 only. PLAN-V2-127, 100, 108 (running) clear.

## Reconciliation Actions

- [x] row `status` → `shipped`
- [x] row `pr` stamped `#355`
- [x] row `landing` stamped `landings/PLAN-V2-106.md`
- [x] row `plan_marshall_plan_id` stamped `v2-106-easymock-for-fakes`
- [x] follow-up staged: PLAN-V2-136 `easymock-for-fakes-residue`, depends on 106, to run before 101 to 105 and after 124
- [x] resume anchor and `queue-view.md` regenerated

## Follow-Ups

- The weak keeps, the fixture-resident doubles and the keep vocabulary: PLAN-V2-136, drafted from this analysis; the Done-when of the follow-up closes the vocabulary and excludes `testing/generators/` from the scan.
- The two wrong PR-body statements are corrected in the follow-up's grounding claims, not in the merged PR.

## Corrections (2026-11-10, from the drafting of PLAN-V2-136, read at `6937efe4`)

- `core/store` `RecordingAuditSink` has 21 using test files, not about 34 (34 is `RecordingLockManager`'s count).
- `FakeKeyrings` (`CredentialStoreMoveTest.java:1168`) is a two-method stub and converts; `TerminationProtocolTest.RecordingSignals` and `FakeParticipant` are scripted without changing state and are close keeps; `ConsultTestLocks` and the four `TestLocks` are in-memory lock managers (sound keeps, like `RecordingLockManager`), not doubles to convert.
- PLAN-V2-114 collides with the follow-up on nine test classes, not on `core-store.adoc` alone.
- Wrong statements of the PR body #355 (recorded in PLAN-V2-136's Grounding Corrections): "job.RecordingProcessCall" (only `start/RecordingProcessCall`), `RecordingLocks` "in-memory" (it wraps a real manager), "cannot toggle after replay" (`andAnswer` over a flag can), "five engine tests", "~25" and "~28" test counts, "the provider fakes" (no provider class matches), and "sonar: not_configured" (the landing message and the public API read 0 issues, gate OK).

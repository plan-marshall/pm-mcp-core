# Landing Analysis: PLAN-V2-136 — the residue hand-written doubles are replaced by EasyMock

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #411 (merge `6aa55a56`), `merged` through the merge queue, no label

> Landing record for one shipped plan, written by the `analyze` inbox drain of 2026-11-14 from the message
> `rb-v2-136-easymock-for-fakes-residue-001.md` (revision 1, amended) of the runbook lane (harness opencode, model
> deepseek-v4.1-flash/high, executor 0.1.2118, standing rules r7). The message is a lead. The three layers were read by
> one read-only leaf from git at `origin/main` `32a711f9` (the scans were run, no build, no test run); the orchestrator
> re-read the points marked "re-read"; the provider figures were re-read from the SonarCloud public API.

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|-------------|---------|----------|
| D1 `pm-core` audit sink and anchors | met | scan `RecordingAuditSink\|RecordingAnchor\|FailingPredecessorAnchor -- pm-core/src` empty; `AuditSinkDoubles.capturing`, `withoutTransactionIds`, `tailRepairWithoutId` added; `core-store.adoc` updated. |
| D2 `pm-workflow` engine and scenario ports | met | the named doubles are gone from `pm-workflow/src/test`; `EngineFixtures` and `ScenarioFixtures` carry `Capture` fields (`jobRequests`, `taskRequests`, `entryRequests`) beside `refusing`, `answerQuestion`, `answerEntry`. |
| D3 model and task fixtures | met | `class (Qualifications\|Guard\|Paused)\b` empty; `ModelCallerTest.Fixtures`, `TaskFixtures.pause(Set)`. |
| D4 `pm-runtime` audit sinks | met | scan empty; new public `runtime/test/AuditSinkDoubles.java`; `LockHolderProcess` uses `capturing()`; the 43 integration tests are the lane's claim only. |
| D5 worker doubles | met | the eight names are gone. |
| D6 steps, process calls, stub, keyrings | met | the four names are gone, `start/RecordingProcessCall.java` deleted, `process()` of `SubprocessJobRunnerTest` is `createMock(Process.class)` (H1 holds on Java 25: `StubProcess` has no K10). |
| D7 the closed list, the scan, the record | met, with deviations | ADR 026 exists with its README row; the closed-list scan returns **13** declarations at the head, not the 14 the spec named (`ProcessTreeTrackerTest.RecordingSignals` was replaced by #390 `1283710f`, in git log): the lane handled it in ADR 026 and the PR body; the fixture scan is empty. |

Plan-level Done-when: no `pom.xml` in the diff (re-read: none); no `@Test`, `@ParameterizedTest`, `@RepeatedTest` or `@Nested` line added or removed (211 assertion lines removed, 212 added); the guides that named a removed double are updated; the per-class table is in the PR body. **Carried items open:** Carried 13 (the one-sentence pointer in `CLAUDE.md`) was not added (re-read: `CLAUDE.md` is not in the diff, the message does not mention it); Carried 17 (`DefaultPlanPrimitives.Scripted`, `DefaultPlanPrimitives.java:118`, re-read, and `EngineFixtures.Scripted` at `EngineFixtures.java:750`) was neither converted nor put in the table nor named in the ADR or the message; Carried 16 (a search of the assembly for users of `runtime.test.AuditSinkDoubles`) is open and honestly stated; Carried 14 and 15 are moot.

## Three-Layer Analysis

Read at `32a711f9` by one read-only leaf (`execution-context-level-3`, 43 tool uses, the scans run at HEAD); the orchestrator re-read the `ActivityRecorderTest` and `AuditLogTest` lines, the two `Scripted` classes, the `verify(` count and the `CLAUDE.md` absence.

### Layer 1 — Specified

- K1 to K9 and F1 to F3 are coherent. The Done-when "exactly 14" was stale: **the spec's "State at the emit" section of 2026-11-13, which I wrote, re-read the paths and did not re-run the scan**, so it carried a count #390 had already changed; the lane's verify-first clause caught it. The reason for K7 (S3) never held for the `TerminationProtocolTest` signals (the spec admits it in Carried 6); it stays a weak keep.
- The spec foresaw the capture threading risk (H3 and the Standing Constraint) and did not foresee the opposite one: a `createMock` with only `.anyTimes()` expectations and no `verify` cannot fail on a missing call.
- ADR 026 does not contradict ADR 025 or `CLAUDE.md`. Its definition covers anonymous classes and lambdas, but its closed list is scan-derived, so hand-written doubles outside the scan remain: five anonymous `ProcessGroupSignals` in `SubprocessJobRunnerTest` (lines 962, 1073, 1193, 1496, 1616), anonymous `RecoveryStep` in `PeriodicSweepTest:351` and `ProjectRecoveryTest:585`, `HoldingEverything` six times; ADR 026 declares them out of scope ("unscanned population"). **No scan test or script exists: the closed list is enforced by reading.**

### Layer 2 — Implemented

As the table. New helpers: `AuditSinkDoubles` (core, package-private; runtime, public) and `ProcessCallDoubles` build only EasyMock expectations, no hand-written implementation of a production interface. The `pm-runtime` helper is new public API of the published `pm-runtime:tests` jar; ADR 026 says the published surface is unchanged and does not mention it. The `AuditSinkDoubles` twin persists (ADR 025 S5 "one home" unmet by design, the artifact boundary). ADR 012 line 69 still says PLAN-V2-106 replaces the fakes with EasyMock doubles; ADR 026 covers it by keeping `FakeBus`, `FakeSecretService` and `FakePmExec` (K1 to K3) without editing the sentence: partly addressed.

### Layer 3 — Correct

About 16 converted sites read; the intent survived in all of them; no weakened assertion found. Observations:

- **Zero `verify(` and zero `reset(` added (re-read: no added line carries `verify(`).** Every mock is `createMock` or `createNiceMock` with `.anyTimes()` expectations, so "expected but never called" is undetectable; "nothing called" is asserted through the recorded lists (`anchorState.calls`, `fixture.cancels.isEmpty()`) or by a `createMock` with no expectations (an unexpected call raises `AssertionError`). The spec said `verify` or an unexpected call, so it is satisfied.
- **Cosmetic conversions:** `ProcessCallDoubles.create` is a 1:1 port of the old `RecordingProcessCall` (the call record, the answer queue, the snapshot of `Files.exists`) into `andAnswer`; the `SupervisorTestFixture` mocks keep the same `CopyOnWriteArrayList`s and the `order` appends inside `andAnswer`. This meets the letter; nothing is gained over the old class and nothing is lost.
- `anyObject`: 51 uses in the diff, always paired with recording in `andAnswer` or with `capture(...)`; no value comparison was weakened.
- **A misleading message (re-read):** `ActivityRecorderTest.java:129` asserts `assertEquals(0, signals.size(), "the third entry raises the first signal")` after two appends: it asserts that two entries raise no signal, and the message says the opposite; the lane added this message while converting.
- **A fragile stub (re-read):** `AuditLogTest.failingPredecessor` (`:154`) is a `createNiceMock` with one `andThrow`; a second `emptyLogPredecessor` call would return `null`, where the old anchor threw every time. Only one call occurs today.
- **A behaviour shift:** the old stubs threw `UnsupportedOperationException` with a specific text ("an activity signal never reports an agent start"); unexpected calls now raise `AssertionError`; this matters only if production code catches `RuntimeException` around protocol calls (unverified).
- **Threading (H3):** sound where read (`DerivedFactStoreLockTest` reads after both futures `get()` and the executor closes; `FileLockManagerTest` reads in single-threaded tests and after `other.join()` in `refusedKeyStaysFree`; the `LessonStore*` writers each create their own mock; `LockHolderProcess` uses a nice mock whose captures are never read; the supervisor fixture is single-threaded on a virtual clock except `HarnessStartSpacingTest`). The spec required each such site to be proved and named in the landing; the lane named only some.
- Child JVM (H5): nothing refutes it; the added EasyMock class-loading time in the child's start-up in the two-process timing tests is unverified.
- **Static state under `reuseForks=true` (#403): none added** (no static mock, capture or list; the helpers are stateless).

## Conformance to the Project's Own Rules

A script over the 211 added assertion statements: 159 carry the message on the same or a continuation line, all 20 to 60 characters apart from non-message strings; 52 have the message on an unchanged continuation line the scan could not check. The CodeRabbit 18-character message is fixed; no more found in added lines. No AAA comments, no `Random`, `randomUUID` or Mockito. The lane added messages to untouched assertions in touched lines (`WorkerEndingTest`, `EntityAddressingTest`), which overlaps PLAN-V2-101 to 105 and is harmless. Style slips: double blank lines inside import blocks (`EngineFixtures`, `ScenarioFixtures`), a line over 120 characters at `LessonStoreCrashTest:509`. The lane message says the five R20 skills were read while its section 2 says "skills: none named" (the second refers to the deliverables' `Skills:` lines only). Sonar `pullRequest=411`: 0 open, gate `OK` (re-read); `main` open 83. CodeRabbit: five findings, all fixed and resolved; Sourcery refused on quota.

## Unverified leads

The 43 integration tests; the added EasyMock start-up cost in `LockHolderProcess`; any use of `runtime.test.AuditSinkDoubles` in the assembly; whether any production code catches `RuntimeException` around a protocol call; the 52 assertion messages on unchanged continuation lines.

## Metrics and Anomalies

`total_tokens` unknown (opencode lane), harness opencode, model deepseek-v4.1-flash/high, executor 0.1.2118; an executor skew (0.1.2114 against the lane's 0.1.2118) was resolved by the operator choosing "regenerated, continue"; the final gate ran at 10 363 tests; `sonar_open_new_issues=n/a` in the facts (the lane's Sonar provider is unconfigured; it read the public API: three issues at `f2de6d3e` fixed in `f6d80822`, 0 open at the later heads).

## Routing and Merge Behavior

One pull request through the merge queue under the `CLAUDE.md` "Who merges" rule. No `candidate-lesson` message from the runbook lane; nothing routed.

## Reconciliation Actions

Row PLAN-V2-136 `running` -> `shipped`, `pr` `#411`, `landing` `landings/PLAN-V2-136.md`, `plan_marshall_plan_id` `rb-v2-136-easymock-for-fakes-residue`; message archived; Watch entry in `epic.md`; decision log; resume anchor.

## Follow-Ups

Placed 2026-11-14 under the rule "every leftover goes into a staged plan":

1. **PLAN-V2-125** (test conformance, `pm-core` and `pm-workflow`): convert or list `DefaultPlanPrimitives.Scripted` (`:118`) and `EngineFixtures.Scripted` (`:750`) (Carried 17); the double blank lines in the imports of `EngineFixtures` and `ScenarioFixtures`, the over-long line at `LessonStoreCrashTest:509`; the fragile `failingPredecessor` stub (`anyTimes()` or a call count).
2. **PLAN-V2-126** (runtime and providers): the misleading message at `ActivityRecorderTest.java:129`; widen the scan by role (`implements <production interface>`, anonymous classes and lambdas included) and **add a scan test so that the closed list is enforced** (ask-at-refine, recommendation yes, about 65%; the unscanned population named above: the five `ProcessGroupSignals`, `RecoveryStep`, `HoldingEverything`, `ManualTimers`, `ScriptedRandom`, `GraderFixtures.*`); check that no production code catches `RuntimeException` around a protocol call.
3. **PLAN-V2-114** (analysis findings): the three present-tense statements that are now false, in ADRs that otherwise record history (`workflow-engine/006:28` "use the double `FixedPendingEntryReader`", the `runtime-start` ADR that names `RecordingProcessCall` in a present-tense sentence, ADR 012 line 69), and the one-sentence pointer to ADR 026 in `CLAUDE.md` (Carried 13, ask-at-refine: the operator's decision whether `CLAUDE.md` names the closed list, recommendation yes, one sentence, about 70%).
4. **PLAN-V2-141** (test-design rework): the assembly check of `runtime.test.AuditSinkDoubles` (Carried 16) before the next change of `pm-runtime:tests`; ADR 026 states the published surface unchanged while a new public class entered it.

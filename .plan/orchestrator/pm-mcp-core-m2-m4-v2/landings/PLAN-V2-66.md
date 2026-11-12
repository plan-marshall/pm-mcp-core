# Landing Analysis: PLAN-V2-66 — the process-tree sweep ends descendants through their ProcessHandle, and the PID-reuse decision is pinned

epic: pm-mcp-core-m2-m4-v2
workstream: WS-06
pr: #390 (merge `1283710f68feab933f7caaf4aa9da09c10ab2e4d`, state `merged` per the CI abstraction)

> Landing record for one shipped plan, written by the `analyze` inbox drain of 2026-11-12 from the message
> `rb-v2-66-process-tree-sweep-pid-reuse-001.md` of the runbook lane (harness opencode, model deepseek-v4.1-flash/high,
> executor 0.1.2114). The message is a lead. The three layers were read by one read-only leaf from git at `1283710f`
> and from the JDK 25 `java.base` sources of the local `src.zip` (no build, no test run, the JDK's C files were not
> available offline); the orchestrator re-read the points marked "re-read" (the JDK source included); the provider
> figures were re-read from the CI abstraction and the SonarCloud public API.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 the table of four answers of `pidReused`, mutation-after | met | `SubprocessJobRunnerPidReuseTest.java:113-143` calls the production overload `SubprocessJobRunner.pidReused(Launched, LongFunction)` (`SubprocessJobRunner.java:1178-1186`), not a copy; test 5 (`:145-164`) runs `terminateByHandle` with an unreadable instant on a strict-count `Process` mock and the production one-argument delegate `pidReused(Launched)` (`:1163-1165`) with `ProcessHandle::of`. Dropping either `isEmpty()` guard fails tests 1, 2 and 5 (the lane's "3 of 5 failed" matches); tests 3 and 4 pin the readable rows. |
| D2 the handle form of the sweep | met | `ProcessTreeTracker.java:197-199` (`destroy`), `:207-209` (`destroyForcibly`); the sweep no longer reads `signals` (`:158` is a null check only). `ProcessTreeTrackerTest` asserts behaviour: which handle was destroyed, `destroy` before `destroyForcibly` (strict mock, `:119-128`), the survivor's command, the elapsed grace, and `verify(signals)`. |
| D3 the ADR, the Javadoc, the guide | met, with one wrong sentence | ADR `runtime-job/017` is complete (the JDK reading with `jdk-25-ga` permalinks, the handle choice, the answer on lead (2), the residual window accepted); Javadoc at `ProcessTreeTracker.java:28-39,183-191`, guide at `runtime-job.adoc:260,262,407`, ADR 008:53 and 013:33. **One explanation is wrong** (L1-c below). |
| Done-when "`ProcessTreeTrackerTest` passes beside every existing test", Build, Sonar, Links | not shown by reading | run facts; Sonar re-read below. |
| Not in the declared surface | explained | `SubprocessJobRunner.java` gains a production seam (`pidReused(Launched, LongFunction)`), which the spec's Expected Surface allowed as a test helper only; the lane asked the operator and was answered "Add a package-private seam" (the answer is the lane's report, not verifiable here); `ProcessGroupSignals.java:14-19` is Javadoc only (the spec said "read, not changed"); ADR 008, 013 and `doc/Traceability.adoc` are consistent bookkeeping. |

The squash message says the window is "closed" and that a reuse between the check and the call "is refused by the JDK"; ADR 017 and the Javadoc say "narrowed", which is the accurate word.

## Three-Layer Analysis

Read at `1283710f` by one read-only leaf (`execution-context-level-3`); the orchestrator re-read the `pidReused` overload, the sweep loop and the JDK's `Info.info` at their `file:line`.

### Layer 1 — Specified

L1-a (minor): the spec's "if the JDK compares / if it does not" dichotomy and the Objective's "closes the window" miss the middle state: the JDK compares the start time and then calls `kill(2)`, so the window narrows to two adjacent system calls (ADR 017:40-43,102-108 says so and accepts it; the bound `kill(2)` permissions plus `CAP_KILL` is accurate; the practical bound is sequential PID allocation, which the ADR does not name, and "microsecond-scale" is unmeasured). L1-b (minor): the spec says a narrower fix does not exist; the race-free Linux form (`pidfd_open` after a start-time re-check, then `pidfd_send_signal`, through the FFM binding the repository already carries) is not weighed in ADR 017's alternatives 1 to 4. **L1-c (minor, re-read): a factual error in the ADR and the Javadoc.** `SubprocessJobRunner.java:1170-1171` and ADR 017 say that on Linux and macOS an unreadable start instant arises only through the exit race. JDK 25 `ProcessHandleImpl.Info.info(pid, startTime)` (`ProcessHandleImpl.java:552-566`) returns an empty `Info` whenever the handle's start time differs from the PID's current one, so for a genuinely reused PID the stale own handle (`launched.process().toHandle().info()`, `SubprocessJobRunner.java:1184`) reads an empty instant: `own.isEmpty()` is the normal reuse signature, and the differing-readable-instants row is reachable on the own side through a read race only. The decision (unreadable means reused) is right; the explanation, and the spec's "a platform without them" framing, understate the normal path. The same code shows that `startInstant()` is empty for `STARTTIME_ANY`, so ADR 017's claim that an unreadable-instant member has nothing to signal holds.

### Layer 2 — Implemented

Conformance: EasyMock only, `RecordingSignals` removed, `FakeTime` pre-existed, `createStrictMock` where the order is the contract; assertion messages in 20 to 60 characters; generated data (`Generators.longs`, `Generators.letterStrings`, generated instants), the literal signal and handle-call order stated as the contract, `ProcessHandle.current().pid()` (`PidReuseTest:156`) a real PID by necessity; no AAA comments, no new log record, no generic catch, `package-info` unchanged. Deviations: the four-row table is four `@Test` methods (`PidReuseTest:113-143`), not a `@ParameterizedTest`; the test helpers (`:86,98,108`) take `Optional<Instant>` parameters. Test gaps: no multi-member sweep (a cooperative member getting no `destroyForcibly` while a stubborn one does); the PID-keyed final filter (`ProcessTreeTracker.java:217-218`, the lane's F11 fix) is unpinned, because every test reuses the same `Member` instances and a mutation back to `Member` equality fails nothing; no `destroy()` returning false and no survivor with an empty command; `SubprocessJobRunnerTest.java:1614-1625` keeps an anonymous throwing `ProcessGroupSignals` the tracker never reads (the test still proves a throwing sweep is logged and settled, because the handle throws at `:1627-1629`).

### Layer 3 — Correct

No blocker and no major defect. **(a) Escalation** (re-read, `:193-215`): every selected member is destroyed, then `survivors()` is polled every 100 ms for up to `SWEEP_GRACE` (1 s), then `destroyForcibly`, then another 100 ms; bounded, and `escalated` asserts the elapsed grace plus the poll. A member whose handle is dead is never selected (`:226`, `deadMemberIsNotSignalled`); a member that dies between selection and `destroy()` makes the JDK return false with no signal; the return value is ignored, as `Refused` was before, and a refusal ends as a reported survivor. Minor: a `destroy()` that throws on one member ends the loop (`:197-199`): the others get no signal, no escalation and no survivor audit, and only the WARN of `RunningJob.sweepTree` is logged; identical to the old behaviour for a throwing primitive, in practice reachable only through an `IllegalStateException` for the JVM's own PID, untested. **(b) Members without a readable instant: no regression.** The old `Descendants.system()` dropped them too and the old survivor filter also needed a readable instant (ADR 017:44-47 and the Consequences say so). **(c) The PID-keyed filter** compares `starts.of(pid)` with the recorded `startedAt` (`:226`), so a reused PID with a different start is never a survivor; an improvement: the survivor command is read from the member's own handle (`:219`), whose `info()` blanks on a PID that moved, where the old code read a fresh `ProcessHandle.of(pid)` and could report a foreign process's command. **(d) `ProcessGroupSignals`**: the sweep no longer calls it; Javadoc, ADR 008, 013 and the guide are consistent; the `signals` parameter of `sweep` is dead (`:134-136,158`; call sites `SubprocessJobRunner.java:1014`, `AgentStartedDetector.java:119`; ADR alternative 4 justifies keeping it). **(e)** `failingSweepDoesNotAbortTheSettlement` still proves a failing sweep is logged and settled. **(f)** `members` is a `ConcurrentHashMap`; `refresh()` and `sweep()` run on the job's own thread, sequentially, and the detector never sweeps (the only `.sweep()` call is `SubprocessJobRunner.java:1786`); no race beyond the accepted JDK window. **(g)** the production delegate is `pidReused(launched, ProcessHandle::of)`; the method body is otherwise unchanged, so there is no behaviour difference.

## Conformance to the Project's Own Rules

See Layer 2. The review gate in the message: 0 unresolved threads, 2 body findings, 2 handled (CodeRabbit required, two threads about documentation accuracy, fixed and resolved; Sourcery optional, refused on quota once). The lane's own rule check names a reviewer-quota deviation (an escalated trigger 69 min 40 s after a refusal, before the 90-minute floor; corrected by waiting the full floor next time), the `trace.py` executor-form exception that R17 prescribes, and persona skills applied from the repository's conventions without the `skill` tool; none bears on the merged code.

## Unverified leads

- The native `destroy0` condition (`start == startTime || start == 0 || startTime == 0`) and the two-system-call window: the JDK's C source was not readable offline; the Java Javadoc (`ProcessHandleImpl.java:332-348`) is consistent with it.
- `verify(signalling)` catches an unexpected call that `terminateByHandle` swallows through `catch (RuntimeException | Error)` (`SubprocessJobRunner.java:1145`) only with EasyMock 3.5 or later; a mutation run would show it.
- Native image (`pm-mcpd`): `ProcessHandle.destroy` on a descendant handle is exercised by nothing in this repository; verified by the assembly build (`CLAUDE.md`).
- Windows: `destroy` and `destroyForcibly` are both `TerminateProcess`; the "SIGTERM then SIGKILL" Javadoc and ADR 017 describe Linux and macOS only.
- The `pm-mcp-server` assembly may construct `Member(pid, startedAt)`, whose constructor is removed (pre-1.0, a break is allowed).
- The lane's counts (10141 tests, coverage, links check) are run facts not shown by reading.

## Metrics and Anomalies

`complete: false` for `total_tokens` only in effect (`unknown`, no metrics phase in this lane); `plugin_version` `0.1.2114`; `surface_delta` unmeasured. Sonar over plain HTTP: `pullRequest=390` 0 open issues, 0 hotspots to review, gate `OK`; the last analysis of `main` read at `a4c2fa86` (86 open, no issue created today), not yet at the merge. **Build-time evidence for PLAN-V2-140:** every successful build was routed (`daemon_longpoll`), none in process; `verify -pl pm-runtime -am` hit the daemon's learned supervisory bound at 443 s and was continued in the split form (`test-compile`, then `test` green with 10141 tests); `verify -Pcoverage` hit the 1800 s default bound once and was green with `--timeout 2050`; the lane's whole-suite counts were 10141, 10205 and 10150 tests at the three heads it ran. Self-review ran two rounds (the second `clear`); CodeRabbit's quota refused three times (13:39, 15:19, 16:29) and reviewed on the fourth trigger at 18:08 (the lane waited about four and a half hours for a review).

## Routing and Merge Behavior

One PR (#390), squash through the merge queue. The message carries no `candidate-lesson`, so nothing is routed to `lessons-routing`.

## Reconciliation Actions

Row PLAN-V2-66 `running` -> `shipped`, `pr` `#390`, `landing` `landings/PLAN-V2-66.md`, `plan_marshall_plan_id` `rb-v2-66-process-tree-sweep-pid-reuse`; message `rb-v2-66-process-tree-sweep-pid-reuse-001` archived; Watch entry in `epic.md`; decision log.

## Follow-Ups

Placed 2026-11-12 under the operator's rule "every leftover goes into a staged plan":

1. L1-c (the wrong explanation in the Javadoc, ADR 017 and the guide), the practical bound and the Windows sentence, L1-b (the `pidfd` alternative, ask-at-refine, recommendation record it and keep the handle form ~75%), the sweep loop that one throw ends (ask-at-refine, recommendation catch per member ~70%), the dead `signals` parameter, and the assembly-side questions -> PLAN-V2-114 item 17.
2. The Layer-2 deviations (the four-row table as a `@ParameterizedTest`, the `Optional` parameters, the dead anonymous `ProcessGroupSignals`) and the test gaps (several members, the PID-keyed filter, `destroy()` false, an empty survivor command, the mutation run for `verify(signalling)`) -> PLAN-V2-126 (its older note on `ProcessTreeTracker.rootStartedAt` carries a re-read warning, because #390 rewrote the tracker).
3. The squash message's "closed": a statement of the record, no plan carries it.

# PLAN-V2-66: The PID-reuse window of the process-tree sweep, and the unreadable start instant

epic: pm-mcp-core-m2-m4-v2
workstream: WS-06
origin: PLAN-117 of the epic pm-mcp-core-m2-m4

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-V2-66-process-tree-sweep-pid-reuse.md` and is queued as one row file, `queue/PLAN-V2-66.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r4`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R16, R17, R18, R19, R20. (R12 applies only if the trace matrix shows a message to add, see Standing Constraints; R14 and R16 apply because the plan changes `pm-runtime`, R16 (3) and (4) in particular — signal numbers differ per operating system, and a suppression is made inline with its reason; R15 does not apply, no `pm-workflow` code changes; R17 because the plan runs the links check.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-runtime/doc/developer/runtime-job.adoc` (extended); ADR directory: `pm-runtime/doc/adr/runtime-job/`; layout: workstream WS-04.
- R12 — log message holder: `RuntimeLogMessages` (`de.planmarshall.runtime.log`, prefix `PM_RUNTIME`).

## Objective

Settle the two leads that CodeRabbit raised on #135 (the process-tree tracking of PLAN-84) and that the epic carries as unverified: (1) `ProcessTreeTracker.sweep` checks that a remembered member still runs with the start instant it had and then signals it by its numeric PID, so a member that exits and whose PID is reused between the check and the signal could be signalled; (2) `SubprocessJobRunner.pidReused` answers `true` when a platform reports no start instant, and the bot fears that a cancelled or over-budget job is then left running. Read at the head of `main`, lead (2) is refuted as behaviour (the started process is ended through its handle on both paths) and lacks only a test; lead (1) is confirmed as a window and is unproven as an exploit, with operating-system permissions bounding the damage. The plan pins (2) with a test, and for (1) either closes the window by signalling through the `ProcessHandle` that was recorded with the member (the JDK checks the start time again inside the destroy) or, if the read of the JDK shows it does not, records the window as an accepted, bounded risk in an ADR. It changes no behaviour of a job that ends normally.

## Deliverables

1. **The tests that pin lead (2).** In a new test class `SubprocessJobRunnerPidReuseTest` (`runtime.job`): `pidReused` is `true` when the start instant of the launched process is unreadable, `true` when the instant of the PID's current process is unreadable, `false` when both are readable and equal, and `true` when they differ (the table of four, with a `Process` whose `toHandle().info().startInstant()` is empty built for the test); and `terminateByHandle` for a launched process whose instant is unreadable signals nothing by PID and destroys the process through its handle exactly once (the existing `SubprocessJobRunnerTest` case at lines 1385-1425 covers a reused PID, an unchanged PID and an absent PID, not this one). The class builds the runner it needs itself; it does not edit `SubprocessJobRunnerTest` (declared by `runtime-metrics`), and if the trace matrix shows that is not possible it edits only a helper there and names the overlap in the landing.
2. **Decide lead (1) by reading the JDK, then act on the answer.** First, read the OpenJDK implementation of `ProcessHandle.destroy()` and `destroyForcibly()` (`ProcessHandleImpl.destroyProcess`, `destroy0(pid, startTime, force)` and its native part for Linux and macOS) for the release in use (Java 25) and record in the ADR whether the native code compares the start time recorded in the handle before it sends the signal. If it does: (a) `ProcessTreeTracker.Member` keeps the `ProcessHandle` it was read from (`Descendants.system()` already holds it at `descendants().map(member -> …)`), and `sweep` sends `SIGTERM` and `SIGKILL` through `handle.destroy()` and `handle.destroyForcibly()` for the members whose recorded start instant still matches; the numeric `ProcessGroupSignals.signalProcess` is no longer used by the sweep, the injected seam of the tests becomes the handle; (b) the tests of `ProcessTreeTrackerTest` follow (a reused PID is not signalled, a member that exited is not signalled, a survivor is answered with its command), and a test shows the member is signalled through its handle and not by PID. If it does not, nothing in `sweep` changes and the ADR records the window as accepted: its length (the check and the signal, microseconds), the bound (the operating system refuses a signal to a process of another user, and the runtime runs as the operator), and the reason a narrower fix does not exist.
3. **Document the decision.** One ADR in `pm-runtime/doc/adr/runtime-job/` (next free number; Status, Date, Context, Decision, Alternatives considered, Consequences, Source) records the reading of the JDK, the choice taken for lead (1), and the answer to lead (2) (the started process is ended through its handle whatever `pidReused` answers: `terminateByHandle` `finally` block and the cancel path; the cost is that the group of a job whose start instant is unreadable is not signalled, so its descendants are left to the tracker, which needs start instants as well — on Linux and macOS the platform reports them, so the case is a platform without them). Update the Javadoc of `ProcessTreeTracker` and the termination section of `pm-runtime/doc/developer/runtime-job.adoc` to the chosen behaviour.

**Done when:** the table of Deliverable 1 passes and fails when `pidReused` is changed to answer `false` for an unreadable instant; the ADR states the JDK reading and the choice for lead (1); if the sweep signals through handles, `ProcessTreeTrackerTest` passes beside every existing test of `runtime.job`; the guide and the Javadoc state the chosen behaviour. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of `pm-runtime` (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and `verify -Pintegration-tests -pl pm-runtime -am` are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set:** PM-EXEC-3 (termination), PM-SEC-5 (confinement and descendants); `doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc` § Step 3a (A Worker's Descendants Outside the Group); ADRs `pm-runtime/doc/adr/runtime-job/010`, `013` and `pm-runtime/doc/adr/runtime-launch/003`; the landing of PLAN-84 (#135) and the CodeRabbit review it links.

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **Log messages: none expected.** If the trace matrix shows one, it goes into `RuntimeLogMessages` at the next free identifier of its level, and the plan says so in its inbox message.
- **No change of the termination protocol, the settlement or the audit record.** `TerminationProtocol`, `ProcessGroupSignals`, `KillBinding` and the `job_descendant_survived` record are read, not changed. The `kill(2)` descriptor registered in `reachability-metadata.json` (R16 (1)) stays as it is: if the sweep stops using `signalProcess`, the descriptor is still the termination protocol's.
- **No claim of exploitability.** The lead is a window the code has; the pull request and the ADR state it as a window with the bound above and do not call it a vulnerability unless the reading proves a path the bound does not cover.

## Claim Labels

- OBSERVED: the sweep checks, then signals by PID — `ProcessTreeTracker.sweep()` (`pm-runtime/src/main/java/de/planmarshall/runtime/job/ProcessTreeTracker.java:180-210`): `survivors()` filters members whose current start instant equals the remembered one (lines 211-217), then `signals.signalProcess(pid, Signal.SIGTERM)` (line 186) and `signalProcess(pid, Signal.SIGKILL)` (line 196); between the filter and the call the PID can be reused
  - verdict: corroborated | checked_at: a8da4104e3efad83b2ca9c1b28ec724ad402fd8a | by: pm-mcp-core-m2-m4/spec-draft(runtime-worker) | rescoped: n/a | evidence: file read at a8da4104
- OBSERVED: `Descendants.system()` holds a `ProcessHandle` for each member when it reads the tree and keeps only `Member(pid, startedAt)` — `ProcessTreeTracker.java:99-105`
  - verdict: corroborated | checked_at: a8da4104e3efad83b2ca9c1b28ec724ad402fd8a | by: pm-mcp-core-m2-m4/spec-draft(runtime-worker) | rescoped: n/a | evidence: file read at a8da4104
- OBSERVED: lead (2) is refuted as behaviour — `SubprocessJobRunner.pidReused` answers `true` when `current.isEmpty() || own.isEmpty()` (`SubprocessJobRunner.java:1136-1146`), and both callers end the process through its handle in that case: `terminateByHandle` destroys it in the `finally` block when the termination did not reach it (lines 1107-1128), and the cancel path calls `process.destroyForcibly()` when `pidReused(launched)` (lines 1262-1266); ADR `runtime-job/010` says the same
  - verdict: corroborated | checked_at: a8da4104e3efad83b2ca9c1b28ec724ad402fd8a | by: pm-mcp-core-m2-m4/spec-draft(runtime-worker) | rescoped: n/a | evidence: file read at a8da4104
- OBSERVED: no test covers an unreadable start instant — `SubprocessJobRunnerTest.java:1385-1425` asserts a reused PID (differing instants), an unchanged PID and an absent PID; `grep -rn "pidReused" pm-runtime/src/test` finds only that case
  - verdict: corroborated | checked_at: a8da4104e3efad83b2ca9c1b28ec724ad402fd8a | by: pm-mcp-core-m2-m4/spec-draft(runtime-worker) | rescoped: n/a | evidence: grep at a8da4104
- HYPOTHESIS: the JDK's `ProcessHandleImpl.destroyProcess` passes the start time recorded in the handle to the native `destroy0` and the native code refuses to signal a process whose start time differs, which would close the window for a signal sent through the handle — recalled, not read in this session; confirm/refute against the OpenJDK source of the release in use before Deliverable 2 chooses (verify-at-outline). If refuted, Deliverable 2 reduces to the ADR.
- HYPOTHESIS: on a platform that reports no start instant, the tracker records no member at all (`Descendants.system()` drops a member without an instant, line 101), so the descendants of such a job are not swept either; whether any supported platform lacks the instant was not checked — Linux reads `/proc`, macOS `proc_pidinfo` (verify-at-outline, one line in the ADR)
- Verify-first clause: before editing, list the open pull requests for one that touches `ProcessTreeTracker` or `SubprocessJobRunner` (`runtime-metrics` declares `SubprocessJobRunner` and its test), re-read both at the head of `main` and the review of #135; a refutation of any claim above loops back to re-scope.

## Expected Surface

- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/job/ProcessTreeTracker.java` — `Member` and `sweep`, only if the JDK reading allows the handle form
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/job/ProcessTreeTrackerTest.java` — the sweep through handles, only with the same condition
- HYPOTHESIS: `pm-runtime/src/test/java/de/planmarshall/runtime/job/SubprocessJobRunnerPidReuseTest.java` — new test class, the table of four and the handle destroy
- OBSERVED: `pm-runtime/doc/developer/runtime-job.adoc` — the termination section, extended (shared bookkeeping, R19)
- HYPOTHESIS: `pm-runtime/doc/adr/runtime-job/` — one ADR, the next free number (shared bookkeeping)

## Dependencies and Sequencing

- Depends on: PLAN-V2-83 (`skills-conformance-foundation`: the dependencies and rule R20); and then: nothing. PLAN-84 (`job-runner-residuals`, #135) and PLAN-29 (#172, the worker half) are shipped.
- Overlaps with, by file: none of the live plans declares `ProcessTreeTracker` or its test. `SubprocessJobRunner.java` and `SubprocessJobRunnerTest.java` are declared by PLAN-V2-42 (`runtime-metrics`, staged) and are not edited here, unless Deliverable 1 cannot be built without a helper (then 1 shared entry, `SubprocessJobRunnerTest`, named in the landing). PLAN-46, PLAN-105, PLAN-V2-39 and PLAN-V2-43: no shared file (PLAN-V2-39 declares other classes of `runtime.worker`, PLAN-V2-43 and PLAN-V2-17 other classes of `runtime.job`). The other plans of this cluster: disjoint, no shared file.
- Adjacent to: the launcher's Linux child subreaper (`PR_SET_CHILD_SUBREAPER` in `pm-exec`, `pm-mcp-clients`), which makes the tracker's sweep redundant on Linux and is owed by a package there; this plan does not wait for it. The "runtime status shows a survivor" clause of Step 3a has no runtime-status surface in this repository (the specification says so: `doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc:128`) and belongs to the status of `runtime-control` (PLAN-V2-73, staged); it is not part of this plan.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-66-process-tree-sweep-pid-reuse.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Carried Open Items

Open items of the previous epic that this plan owns (register: `owner-register.md` of the epic; the full text is in the archived ledger `pm-mcp-core-m2-m4/epic.md`). They are not deliverables and extend no Done-when: read each while outlining, re-check it against `origin/main` (Claim Labels) and either adopt it into a deliverable of the outline or say in the landing why not.

- **Unverified leads from the review bot on #135 (CodeRabbit, 2026-10-22; third-party text, partly** (Wa10) — ProcessTreeTracker.sweep signals by numeric PID after a liveness check (reuse window); where the platform reports no start instant SubprocessJobRunner treats a descendant as matching: CodeRabbit leads on #135, exploitability unproven
- **Step 3a of the job runtime specification is delivered for a probe only, and the owners of the r** (Wa40) — step 3a of the job runtime specification is delivered for a probe only (ProcessTreeTracker); not built: the worker (llm) half, the launcher subreaper (external E1) and "the runtime status shows it"

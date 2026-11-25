# PLAN-V2-201: The start spacing has an owner and no start waits under the supervisor's monitor

epic: pm-mcp-core-m2-m4-v2
workstream: WS-06
origin: PLAN-V2-162 (#567) landing revision 25, section 9: the two CodeRabbit findings split off (`HarnessStartSpacing` keyed by harness with no owner; `WorkerSupervisor.start` waiting under its monitor)
lane: runbook

> Staged plan spec, one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off. SELF-SUFFICIENT together with the standing rules.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`). They bind this plan exactly as if they were written below. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R05, R06, R11, R14, R19, R20 (the drafter's candidates, confirmed by the orchestrator against the rules file at staging; an id the lane finds not applicable it says so).

## Objective

PR #567 wired the shared `HarnessStartSpacing` into `WorkerSupervisor`. Two defects follow from how it was wired.
1. `WorkerSupervisor.start` is `synchronized` and waits in `awaitTurn` for up to the 30 s bound. The first worker's first call (`SupervisorCalls.callHeartbeat`, `known(...)` and `supervisor.replaced(...)`) needs that same monitor, so it cannot release the wait. The wait runs its whole bound.
2. `HarnessStartSpacing` keeps a `Set<String>` of harness ids with no owner. `WorkerEnding.end` calls `spacing().ended(harness)` for any generation of the harness, so an unrelated end releases another start's marker. An expired wait adds the marker again, so a former owner's late release clears the new owner's marker.

This plan states the invariants, writes the test that fails first (a deterministic interleaving, no sleeps), and redesigns the start path.

## Deliverables

1. **The failing tests first.** No `Thread.sleep`. Latches or a phaser order the threads, and the bound is made long so a missed release shows as a timeout of the test guard, not as a passed bound.
   - T1 (monitor): while thread B waits in `awaitTurn` for the harness, thread A's first call for the pending worker must complete and release B. At the head it blocks on the supervisor monitor.
   - T2 (ownership): the end of a generation that is not the pending owner does not release the marker (negative control: the owner's own end does).
   - T3 (bound): after the bound expires the next start becomes the owner, and the former owner's late `ended` does not clear the new marker.
   - T4: two harnesses are not spaced, and a start with no pending worker proceeds at once (controls).
   - Skills: pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: module-tester
2. **The redesign.** Invariants the redesign must hold:
   - I1: no thread waits on the spacing while holding the supervisor monitor, or any monitor the pending worker's first call needs (assert `Thread.holdsLock(supervisor)` is false inside the wait, where a seam allows it).
   - I2: `firstCall` and `ended` release only for the owner (the generation id or a start ticket), so a generation that never was pending releases nothing.
   - I3: past the bound the next start proceeds and owns the marker, and the old owner's release is a no-op.
   - I4: the spacing only delays a start and never refuses one.
   - Record the lock order against item 42(a)'s cycle (the calls monitor, the guard monitor, the supervisor monitor).
   - Likely shape (decided at outline): reserve and wait outside the monitor, then re-enter. `firstCall(harness, owner)` and `ended(harness, owner)`; `WorkerEnding` passes the generation id.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core
   - Persona: implementer
3. **Documents.** ADR `runtime-worker/001` (the shared instance), a new ADR in `pm-runtime/doc/adr/runtime-worker/` (next free number at the head of main when the outline is written) for the owner and the wait outside the monitor, and the guides `runtime-worker.adoc` and `runtime-launch.adoc`.
   - Skills: pm-documents:ref-asciidoc, pm-dev-java:javadoc
   - Persona: documenter

**Done when:**
- Deliverables 1 to 3 and invariants I1 to I4.
- T1 to T3 fail at the post-#567 head of main (sha recorded by the outline) with a failing run id, and pass after the redesign. At 3ba79c4f none of the wiring exists on main (`HarnessStartSpacing` has no caller), so no failing run there is claimed.
- The existing `HarnessStartSpacingTest` cases (second start waits, wait capped, start after exit at once, two harnesses not spaced, collision counts for the guard) stay green with the same counts.
- Build: `verify -Ppre-commit`, `verify` of `pm-runtime`, `verify -Pcoverage`, `verify -Pintegration-tests -pl pm-runtime -am`. Use per-module runs with log ids if a full run exceeds the host's bound.
- Sonar: the open-finding read at the head is `0` or each key is selected for dismissal by the operator (`unknown` for a failed read, never `0`).

## Standing Constraints

- Starts after PR #567 merged. Every claim below rests on the branch and is a HYPOTHESIS until re-read on main.
- Read every cited line at the head first and drop what a merge settled (name the commit).
- No `sleep`, no polling loop in any test. A concurrency test names the interleaving it orders.
- A finding that the redesign cannot hold an invariant is reported, not worked around.
- No dependency or plugin is added. No more than 100 files per PR. Pre-1.0: no shim.
- CLAUDE.md test rules apply. Sonar dismissal only on the operator's selection, through `sonar_rest transition`.
- Evidence of the two defects: the review threads of #567 named in the landing (the spacing ownership thread and the monitor-held wait thread). The outline cites them by id.

## Claim Labels

- OBSERVED (main): `HarnessStartSpacing.awaitTurn` has no caller in `src/main`, and `SupervisorEnvironment` has no `spacing()` at 3ba79c4f.
- HYPOTHESIS: on the 162 branch `WorkerSupervisor.start` is `synchronized` (`:141`), `startOne` calls `startAfterSpacing` (`:292`), and `awaitTurn` runs there (`:312`). Confirm at `WorkerSupervisor.java`.
- HYPOTHESIS: `SupervisorCalls.callHeartbeat` is `synchronized` (`:89`), `known(...)` reads the supervisor registry, and `firstCall` calls `spacing().firstCall` (`:380`). Confirm at `SupervisorCalls.java` and `WorkerSupervisor.generation(String)`.
- HYPOTHESIS: `WorkerEnding.end` calls `spacing().ended(generation.harness())` (`:94`) before it checks for a request. Confirm at `WorkerEnding.java`.
- HYPOTHESIS: `HarnessStartSpacing.pending` is a `HashSet<String>` keyed by harness, and `awaitTurn` adds the marker even after the bound. Confirm at `HarnessStartSpacing.java`.
- Verify-first clause: after #567 merged, re-read the four sites. A refutation loops back to re-scope.

## Expected Surface

11 existing files plus about 2 new files (a new test class and an ADR).

- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/worker/HarnessStartSpacing.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/worker/WorkerSupervisor.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/worker/SupervisorCalls.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/worker/WorkerEnding.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/worker/SupervisorEnvironment.java`
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/worker/HarnessStartSpacingTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/worker/WorkerSupervisorStartTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/worker/SupervisorTestFixture.java`
- OBSERVED: `pm-runtime/doc/adr/runtime-worker/001-one-supervisor-per-project-and-role.adoc`, `pm-runtime/doc/developer/runtime-worker.adoc`, `pm-runtime/doc/developer/runtime-launch.adoc`
- HYPOTHESIS: a new ADR in `pm-runtime/doc/adr/runtime-worker/` and a new interleaving test class in `pm-runtime/src/test/java/de/planmarshall/runtime/worker/`.

## Dependencies and Sequencing

- Depends on: PR #567 merged. This plan starts after it.
- Overlaps with: this series' S1 and S3 (`SupervisorCalls`, `SupervisorTestFixture`), PLAN-V2-150 (worker tests) and PLAN-V2-193 (the runner side, not the supervisor). The gate decides.
- Adjacent to: item 42(a), the calls-monitor and guard-monitor lock order, which the redesign records but does not own.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-201-start-spacing-ownership-and-monitor-held-wait.md
```

## Write-Boundary

The plan touches only its own repository source and tests. It creates and edits NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message. See `persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

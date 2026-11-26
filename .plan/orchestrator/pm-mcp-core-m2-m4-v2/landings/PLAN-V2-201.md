# Landing Analysis: PLAN-V2-201 — the start spacing has an owner and no start waits under the supervisor's monitor

epic: pm-mcp-core-m2-m4-v2
workstream: WS-06
pr: #625 (https://github.com/plan-marshall/pm-mcp-core/pull/625), merge `89ffec36` (merge queue), analysed on the thirty-ninth run, 2026-11-26

> Landing record. Source: the lane's inbox message `rb-v2-201-start-spacing-ownership-and-monitor-held-wait-001` (landing-facts complete, `merge_state=merged`), corroborated against git (`89ffec36`), the ci abstraction (`pr view`, `review-gate`), the lane's progress file and the merged code, read by one read-only leaf and re-read by the orchestrator at the cited lines. Standing rules r7 (R21 binds this analysis).

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 the failing tests first (T1 to T4) | shipped-modified | `StartSpacingInterleavingTest` (new) carries T1 `aStartWaitsOutsideTheSupervisorMonitor`, T2 `anEndOfANonOwnerReleasesNothing`, T3 `theBoundHandsTheTurnToTheNextStart`; T4 controls in `HarnessStartSpacingTest`. No `Thread.sleep` (grep of the three test classes empty). **Modified:** T1 to T3 and the later releases assert wall-clock thresholds against a real 300 ms bound (L3-9), and the spec's "the bound is made long" is not what the tests do |
| D2 the redesign, I1 to I4 | shipped-as-specified, with one regression (L3-1) | I1: `awaitTurn` runs at `WorkerSupervisor.java:311` with `startLock` held and `this` not held; I2/I3: `HarnessStartSpacing.release` removes only for `owner.equals(pending.get(harness))`, the expired waiter `put`s itself as owner (`:91-96`); I4: the deadline `break`, an interrupt restores the flag and proceeds. The lock order is recorded in ADR `runtime-worker/024`. The redesign broke the probe settlement on a gate decline (L3-1) |
| D3 the documents | shipped-as-specified (one more ADR than declared) | ADR 001 amended, ADR 024 (owner, wait outside the monitor, lock order), ADR 025 (counting rule), `runtime-worker.adoc`, `runtime-launch.adoc`, `doc/Traceability.adoc` |
| D4 counting and releases | shipped-as-specified, with two notes | (a) `WorkerSupervisor.java:446-448` counts every launch refusal except `HARNESS_UPDATE_UNVERIFIED` (six of seven reasons), (b) `declineRefused :354-362` records the refusal and releases the slot before the count, (c) the loop stops when the guard holds (`:175-179`), (d) `startWorker` releases the spacing turn in its `finally` (`:333-336`). Notes: 4(c) is pinned through the probe path and not through a counted refusal reaching the threshold (L2-2); `harness_update_unverified` is excluded by ADR 025 against the sentence of Model Work (L1-1) |
| Done-when "T1-T3 and T5-T8 fail at `90505ad2`" | not met as written | the lane says so for T7; the leaf adds that the uncounted control and two of the four T5 cases (`harness_tampered`, `harness_store_unreadable`) were already green on the old code, which the lane's own `[3]/[4] already passed there` also states (L1-2) |
| Files beyond the declared surface | explained | `WorkerStartRefusal.java` (`STOPPED_ADMITTING`, the review's regression fix), `doc/Traceability.adoc`, ADR 025; `StartOutcome.Declined` gained a `@Nullable StartFailureReport` component (no deliverable asked for it; the lane reports it; the assembly has no consumer). Declared but untouched: `SupervisorEnvironment`, `HarnessVerifier`, `LaunchRefusal`, `SupervisorCallsTest`, `WorkerSupervisorStartTest`, `11-enrolment-store.adoc` |

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`, `orchestrator.analyze`) — reason: a concurrency redesign across `WorkerSupervisor`, `HarnessStartSpacing`, `SupervisorCalls`, `WorkerEnding` and `StartFailureGuard` with four monitors is the depth the Dispatch Decision Rule reserves for a leaf, and it must not share context with the 187 read. The orchestrator re-read the cited points of L3-1 to L3-5 (`WorkerSupervisor.java:138-200, 279-283, 285-345, 354-362, 541-543`; `StartFailureGuard.java:120-185`; `HarnessStartSpacing.java:84-100`; the pre-merge `WorkerSupervisor.java:135-175`), L1-1 (`model-work/03:131`, `11-enrolment-store.adoc:56-61`, the Javadoc at `WorkerSupervisor.java:440-448`) and the gate test (`WorkerSupervisorStartTest.java:162-170`). Points marked "leaf only" below were not re-read and are unverified leads.
- **Verdict:** layer 1 findings — 3 (1 spec gap, 1 unmeetable Done-when, 1 silence); layer 2 findings — 3 (minor); **layer 3 findings — 7, one major (L3-1), five minor, one info; and two points not shown by reading.**

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| none | the spec's premises about `90505ad2` (the `synchronized start`, the ownerless `Set<String>`, `WorkerEnding.end` releasing by harness, `countStoreRefusal` counting two reasons) held and were the defects the landing fixed | checked | confirms | the pre-merge `WorkerSupervisor.java` (`git show 89ffec36^`), the diff |
| L1-1 (major, spec gap) | the specification set does not agree on whether `harness_update_unverified` counts: Model Work `03-supervision-and-recycling.adoc:131` counts every start "the runtime refused before the spawn"; `11-enrolment-store.adoc:58` names `harness_version_unsupported` and `harness_not_enrolled`, `:59` `harness_tampered`, `:60` `harness_store_unreadable`, and says nothing on update evidence; the code excludes it (`WorkerSupervisor.java:446-448`) by ADR 025 alone | checked (re-read) | refutes (the spec leaves it open) | `model-work/03:131`, `11-enrolment-store.adoc:56-61`, `WorkerSupervisor.java:440-448` |
| L1-2 | Done-when "T1 to T3 and T5 to T8 fail at `90505ad2`" cannot hold as written (T7's own definition is mutation-based; the uncounted control and two T5 cases already passed on the old code) | checked | refutes | the lane's §1 and §9; the leaf by reading the old code |
| L1-3 (minor, spec gap) | the spec is silent on three interactions the redesign met: a drain beginning during the wait (the review found it), the probe batch (the hold stop makes a probe batch start one worker where it started N), and the probe settlement when the gate declines (broken, L3-1) | checked (leaf; L3-1 re-read) | refutes | `WorkerSupervisor.java:157-183, 315` |
| L1-4 | the spec's I1 clause "assert `Thread.holdsLock(supervisor)` false inside the wait where a seam allows" is not delivered; `HarnessStartSpacing` is a concrete injected class with no seam, so the spec's own condition covers it | checked (leaf only) | confirms | `HarnessStartSpacing.java:84` |

Persisted verdicts: none. No finding names a corpus claim of a staged spec of this epic (the 201 spec is shipped; its claims are historical).

**Layer 1 verdict:** findings — 4 (L1-1 major, L1-2, L1-3 minor, L1-4 info).

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| D1/D2 | `WorkerSupervisor.java:157-183, 285-340`, `HarnessStartSpacing.java:84-134`, `SupervisorCalls.java:380`, `WorkerEnding.java:95` | `StartSpacingInterleavingTest` (T1-T3), `HarnessStartSpacingTest`; each fails by mutation by reading (deleting the release at `SupervisorCalls:380`, `WorkerEnding:95` or `WorkerSupervisor:335` leaves the next start waiting its bound against a `<500 ms` assertion) | none | checked (leaf; mutations not re-read) |
| D4 a, b, d | `:446-448`, `:354-362`, `:333-336` | `WorkerSupervisorStartFailureTest` (T5, T6), `StartSpacingInterleavingTest` (T8) | none | checked (leaf) |
| D4 c | `:175-179` | `aBatchStopsWhenTheGuardHolds` | **L2-2 (minor):** the test pre-fills three failures and advances the backoff, so the batch stops because the probe is in flight; the case the spec names (the count reaching `HOLD_THRESHOLD` mid-batch through counted refusals) has no test. Removing the `break` still fails it, so the break is pinned, but not for the stated reason | checked (leaf) |
| the probe settlement on a gate decline | `WorkerSupervisor.java:164-183` | **none** | **L2-1 (minor):** no test combines a held guard with a due probe and an ineligible gate; `gateRefusedStartsNothing` (`WorkerSupervisorStartTest.java:162-170`) uses `OPEN_GUARD` | checked (re-read: the only `HARNESS_INELIGIBLE` in `src/test`) |
| the lane's Done-when claim "T1-T3, T5-T8 fail at `90505ad2`" | — | — | **L2-3 (minor):** unborne for T7 as the lane says, and for the two T5 cases and the control as the leaf adds; whether T1-T3, T6, T8 and the other two T5 cases fail at `90505ad2` with a failing run id is not shown by reading (the lane cites the base log) | not_shown_by_reading |

**Layer 2 verdict:** findings — 3 (minor).

### Layer 3 — Correct

**L3-1 (major, re-read): the redesign moved the eligibility-gate decline out of the `try` whose `finally` settles the guard's probe.**

- Before (`git show 89ffec36^`, `start`, `:150-167`): `if (!gate.admits(...)) return declineAll(..., HARNESS_INELIGIBLE);` sat INSIDE `try { … } finally { settleProbeUnlessStarted(role, outcomes); }`.
- Now (`WorkerSupervisor.java:160-183`): `startGuard().holds(...)` (`:164`) and `eligibilityGate().admits(...)` (`:167-168`) run in the `synchronized (this)` block BEFORE the `try` that starts at `:172`; `declineAll` (`:541-543`) settles nothing.
- Trigger: the guard holds the role on the harness (`StartFailureState.held`), the probe backoff has passed, so `StartFailureGuard.holds` (`:133-144`) sets `probeInFlight=true` and answers `false`; the gate then declines (an ineligible harness: no current verification record). `start` returns `HARNESS_INELIGIBLE` and `probeDeclined` is never called. An exception thrown by `gate.admits` leaks the probe the same way.
- Effect: every later `holds()` answers `true` through the `probeInFlight` branch (`StartFailureGuard.java:139`); the role stays held with no future probe until a `firstCall` or a `clear`. That is exactly the state the Javadoc at `WorkerSupervisor.java:145-146` says cannot happen ("a throwing start never leaves the guard held with no future probe"). The landing's own test of the settle (`settleProbeUnlessStarted`) does not reach it (L2-1).
- Reach: narrow — it needs a held guard, a due probe and an ineligible gate at the same start; and `WorkerGradingHarness:229`, `WorkerJudgeScorer:80` are the callers in this repository. Checked by the orchestrator (`git grep` at `plan-marshall-mcp` `origin/main` `fd7e252`): the assembly has no reference to `WorkerSupervisor`, `WorkerGradingHarness` or `WorkerJudgeScorer`, so no live role is held by it today; the callers are the two harness classes of this repository.

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `WorkerSupervisor.java:164-168` (try at `:172`) | held guard + due probe + gate decline, or a throwing `gate.admits` | the probe is never settled; the role is held with no future probe | major |
| L3-2 | `WorkerSupervisor.java:145-146` | the Javadoc "a throwing start never leaves the guard held with no future probe" | contradicted by L3-1; the `awaitTurn` Javadoc (`HarnessStartSpacing.java:73-82`) also omits that an interrupt records no turn (L3-4) | minor |
| L3-3 | `WorkerSupervisor.java:285-310, 354-362` | `refuse()` (a job-record write) throws inside `declineRefused`; or `harnessVerifier.verify` throws; or `composer.compose` throws a non-`CompositionException` | the model-slot lease claimed at `:293` is not in a try/finally: the slot leaks (and, for `refuse()` throwing, the count is lost). D4(b) fixed only the throwing COUNT | minor |
| L3-4 | `HarnessStartSpacing.java:94`, `WorkerSupervisor.java:371-377` | an interrupt during `awaitTurn` | `timedWait` throws before `pending.put` (`:96`): the start spawns with no owner marker, so a later start is not spaced behind that worker, and the previous owner's marker stays; the flag stays set through the rest of the batch. I4 holds (nothing refused), but the Javadoc does not state it | minor |
| L3-5 | `WorkerSupervisor.java:292-310, 315` | a drain begins mid-batch | `startOne`'s first block does not test `admitting`: the remaining requests each claim a slot, verify and compose, can write a refusal record and COUNT a start failure, and are declined `STOPPED_ADMITTING` only after the wait. Wasted work and a count during a drain | minor |
| L3-6 | `WorkerSupervisor.java:327-336` | `register()` throws after `startWorker` returned `Started` | the worker runs unregistered, its slot is not released, and the `finally` releases the spacing turn of a live worker so the next start is unspaced (the slot leak is pre-existing, the spacing release is new) | info |
| L3-9 | `StartSpacingInterleavingTest.java:94, 137-139, 158-160, 204-257` | wall-clock assertions (`>= 200 ms`, `< 150 ms`, `>= 150 ms`, `< 200 ms`, `< 500 ms`) against a real 300 ms bound; `HarnessStartSpacing` has no injectable clock | not a sleep, but timing-dependent: a loaded host can flake the upper bounds; the spec's "the bound is made long" is not what the tests do. T1's `secondStarted.await(200 ms)` is a bounded negative observation; the claim latch orders the slot claim and not the entry into `awaitTurn` (leaf only) | minor |

**Layer 3 holds (confirms), leaf only unless marked:** I1 holds on the start path — `awaitTurn` at `:311` runs with `startLock` held and `this` not held, `startLock` is private, no worker-call path takes it, the `start` callers hold no monitor (re-read at `:157-200, 285-345`); no new lock-order inversion (`startLock`, then `this`, then the guard monitor, then the runner/lock transaction); the pre-existing supervisor/guard ABBA is confirmed in code (`StartFailureGuard.java:177-181` `clear` holds the guard monitor and calls `retireFailures`, which is `synchronized` on the supervisor) and ADR 024 records it accurately; I2 and I3 owner semantics hold; every path after `awaitTurn` releases the turn exactly for its owner and releases the slot once; one failure is counted per refused launch (`HARNESS_UPDATE_UNVERIFIED` the only uncounted of seven `LaunchRefusal` reasons); the mid-loop hold stop is sound (`holds()` admits one probe only when held, no probe in flight and the backoff passed, so a probe batch starts one worker); owner identity is the same `generationId` at `awaitTurn`, `LaunchComposer`, `register`, `SupervisorCalls:380` and `WorkerEnding:95`.

**Layer 3 not shown by reading:** L3-7 — the verify, coverage, integration-test and Sonar results, and the lane's recorded `WorkflowParserRefusalTest.fixturesPresent` failure in its main checkout (a stale `target/test-classes`, "green in a fresh tree at the merge commit"); L3-8 — whether the upper-bound assertions flake on a loaded host (no flake rate recorded); the `HarnessStartSpacing.java:51` field Javadoc is garbled and about 125 columns (cosmetic; whether the pre-commit gate rewrites it is not shown).

**Layer 3 verdict:** findings — 7 (L3-1 **major**, L3-2 to L3-5 and L3-9 minor, L3-6 info); not shown by reading — 3. **L3-1 is reported to the operator at once and is not fixed inline.**

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md Code Standards | JUnit 5 only, no Mockito, EasyMock for doubles, no new hand-written fake | conforms | the new tests; one EasyMock `StartGuard` mock in `WorkerSupervisorStartFailureTest` |
| CLAUDE.md Code Standards | every assertion carries a message of 20 to 60 characters | conforms | 26 to 49 characters in the changed tests (counted by reading; the scanner run is not shown) |
| task constraint | no `Thread.sleep`, no polling loop in a test | conforms (letter) | grep empty; bounded awaits and wall-clock thresholds exist (L3-9) |
| CLAUDE.md Code Standards | test data from generators, literals only where the contract | conforms | `Generators.letterStrings`, `RuntimeGenerators.jobIds`; literals commented |
| CLAUDE.md Code Standards | every `LogRecord` a class logs is asserted with `LogAsserts` | not_shown_by_reading | the new tests count `WORKER_START_FAILED` through audit events; `WorkerSupervisorStartTest` was not read |
| `java-null-safety` | `@Nullable` for a component that may be absent, no `@Nullable` return | conforms | `Declined(reason, @Nullable StartFailureReport report)` |
| CLAUDE.md Code Standards | never catch a generic exception | conforms | the one added catch is `InterruptedException` with the flag restored |
| CLAUDE.md Dependencies | no dependency, pre-1.0 no shim | conforms | no POM; signatures changed in place |
| `javadoc` accuracy rules | a Javadoc claim the code contradicts must not stand | **deviates** | `WorkerSupervisor.java:145-146` (L3-1/L3-2); `awaitTurn` Javadoc omits the unmarked interrupt (L3-4) |
| `java-core` review rules | resources released on every path | **deviates** | the model-slot lease on the first-block paths (L3-3) and after a throwing `register` (L3-6) |
| CLAUDE.md Module Documentation | developer docs and ADRs in the same PR | conforms | ADR 001, 024, 025, the two guides, Traceability |
| CLAUDE.md Build | verify, coverage, Sonar | not_shown_by_reading | a run |

## Metrics and Anomalies

- Tokens: `unknown` (unobserved, not zero). Model `deepseek-v4.1-flash/high`, effort high, harness opencode, `plugin_version unknown`, executor 0.1.2184.
- Builds: 15 `daemon_longpoll`, 5 `in_process_fallback` (the base-evidence runs, by the operator's answer). One failing coverage run in the main checkout, recorded by the lane as a stale `target/test-classes` and not cited as evidence.
- Anomalies: the base-sha evidence needed two operator questions (the detached tree at `90505ad2` has no executor); the epic's recurring gap (PLAN-V2-82, -152, -153, -185, -188). The lane violated its own rule with three `grep` and two `ls` shell calls, no guard on this harness; recorded by the lane itself.

## Routing and Merge Behavior

- Review: CodeRabbit (required, quota-limited) refused at 00:57Z, re-triggered at 02:28Z, reviewed `b49c3e00` at 02:35Z with 5 findings (2 inline, 2 outside-diff, 1 nitpick), all fixed in `a026af4a` (the admission re-check is a real defect of the redesign, fixed with a test); Sourcery refused on its hard quota, not gating. Landing facts: 0 unresolved threads, 5 body findings, 5 handled. Re-read at 2026-11-26T06:33Z: `review-gate --pr-number 625` gives 0 unresolved threads and 5 body findings (**no difference** on those two); it gives `review_body_findings_handled: 0` with `undisposed[5]` and `merge_allowed: false` — the provider alone cannot reproduce `handled`, so the number is re-read from the run's progress file: 5 `finding:` lines (`inline-1`, `inline-2`, `outside-diff-1`, `outside-diff-2`, `nitpick-1`, each `a026af4a`), **5 of 5 verified**. The gate's 0 is not a difference.
- CI/merge: green on every head (`0d9737c2`, `b49c3e00`, `a026af4a`, `741a6d29`), merged through the merge queue as `89ffec36`. `cleanup_owed=false`.
- Sonar: the landing's `sonar_open_new_issues=0` (read at the merge head `741a6d29`, gate ok, `open_issue_rules=none`); not re-measurable after the merge, recorded as the lane's measured claim.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#625`; `landing` `landings/PLAN-V2-201.md`; `plan_marshall_plan_id` `rb-v2-201-start-spacing-ownership-and-monitor-held-wait`
- [x] epic.md: Decisions entry; Open Defect for L3-1 and its minors (awaiting the operator's word, no plan staged)
- [x] resume anchor updated; `queue-view.md` regenerated and committed

## Follow-Ups

- **L3-1 (major) and L3-2 to L3-6, L2-1/L2-2, L3-9** — reported to the operator at once; they become one staged fix plan only on the operator's word. The orchestrator's recommendation: stage one small plan (the gate decline back inside the settling scope or settled by its own `try`; a test of held guard + due probe + gate decline and of a throwing gate; the slot lease in a try/finally; the interrupt Javadoc; the `admitting` test at the head of `startOne`'s first block; the 4(c) test through a counted refusal), not a hot fix: the trigger needs three conditions at once, the assembly does not call the supervisor yet (checked above), and the fix is small enough to ship as its own plan.
- **L1-1** — the counting of `harness_update_unverified` is settled by ADR 025 and not by the specification: an open question for the operator (recommended: state the exclusion in `model-work/03:131` and `11-enrolment-store.adoc:58`, because the verifier maps a failing platform check to that reason, so a persistent refusal would otherwise hold the guard).
- **L1-2** — the spec's Done-when is historical; no action.
- The pre-existing supervisor/guard ABBA stays item 42(a)'s, recorded in ADR 024.

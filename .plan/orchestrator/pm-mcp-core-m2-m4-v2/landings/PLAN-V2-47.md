# Landing Analysis: PLAN-V2-47 — continuation-hold-and-budget

epic: pm-mcp-core-m2-m4-v2
workstream: WS-06
pr: #506 (merge `76fb9503`, through the merge queue; read at head `76fb9503`)

> Landing record, runbook lane (`rb-v2-47-continuation-hold-and-budget`, harness opencode, model `deepseek-v4.1-flash/high`).
> Drained from `inbox/rb-v2-47-continuation-hold-and-budget-001.md` on 2026-11-19. The message is a lead: the PR state,
> merge SHA, the review numbers and the lane's `finding:` lines were read back. `inbox landing-check`: `complete: false`,
> missing `total_tokens` and `plugin_version`, both `unknown` (a failed read, not a zero).

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 the entries and the lifecycle of a hold | shipped-modified | `SupervisorCalls.java:198-241` (`holdOpened`, `holdClosed`, `answerDelivered`), `WorkerLifecycle.java:68`, `:70` (the two new edges the spec did not name), `SupervisorCallsTest` cases. The clause "the hold's time is not charged to the worker budget" is settled for the token budget only (L2-1) |
| D2 the notification | shipped-as-specified | `WorkerRecycling.java:168-179`, `:244-259`, `WorkerRecyclingTest` (order `hold_ended`, `fence`, `release`, `write`) |
| D3 the tests | shipped-as-specified | the three test groups; D3(c) needed no code, the six doubles are gone (the cleanup re-ground of 2026-11-17 said so) |
| D4 the decisions documented | shipped-modified | ADR `runtime-worker/016`, guide § The Hold, ADR 012/014 notes, `question-continuation.adoc`, Traceability PM-WORK-2/3; the "Not built" sentence the spec names no longer exists (#484), the lane rewrote the hold item instead |
| D5 the owed rest | shipped-as-specified | stated in the PR body; the package stays `open` |

The 30-file rewrite of `pm-core` and `pm-workflow` is the pre-commit profile's formatting (the leaf read `git diff -w`: imports, FQNs, wrapping, no behaviour); the lane's note that the spec's "R15 does not apply" is no longer literally true holds.

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`, `orchestrator.analyze`) — reason: a full-ship landing is read by its own leaf with no size qualifier; the leaf wrote nothing. The leaf's header says "1 major"; none of its findings carries severity major, so the highest recorded here is minor.
- **Re-read by the orchestrator at the file and line** (all held): `WorkerRecycling.java:140`, `:147-150`, `:151-157`, `:168-179`, `:244-259`; `SupervisorCalls.java:167-178`, `:198-214`, `:216-241`; `WorkerLifecycle.java:65-73` (the edges are at `:68` and `:70`, the leaf cited `:69-72`); ADR `016` lines 29-31 and 82 and the guide line 271 ("a hold produces no turn"); the `llm` worker-budget row at `timeouts/01-job-budgets-and-waits.adoc:127`; no `llm:`/`budget_floor`/`hold_seconds` accounting in `src/main`.
- **Not re-read, recorded as unverified leads:** `SupervisorTestFixture.java:413` (only the `holdEnded` mock expectation was seen), the guard of `LossDetection.java:136-141`, `WorkerEnding.java:73`/`139-158`, `question-continuation.adoc:16`, the "counts no idle wake-up" assertion gap.
- **Verdict:** layer 1 findings (4, minor); layer 2 findings (2, minor); layer 3 findings (5, minor). No blocker, no major.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L1-1 | The spec frames the hold's time as accounted by the generation's budget; two budgets exist, the TIME budget `llm:<role>` (Timeouts 127, Model Work 06:244, pauses from ask to acknowledgement) and the TOKEN budget `recycling.token_budget`; a generation holds no time accounting | checked | refutes | `timeouts/01-job-budgets-and-waits.adoc:127` |
| L1-2 | The spec never says the transition table lacks `EXECUTING → HOLDING` and `OFFERED → HOLDING`; only `CONSULTING → HOLDING` existed; `WorkerLifecycle` is not in the Expected Surface | checked | refutes | `WorkerLifecycle.java:68`, `:70` |
| L1-3 | D4 names a "Not built" sentence of `question-continuation.adoc` that #484 removed | unverified lead (not re-read) | confirms | `doc/plans/question-continuation.adoc:16` |
| L1-4 | The Objective says "a worker that reaches its token budget while holding"; `afterTurnCompleted` returns early for a non-WARM form, so a fresh worker's hold is not covered; ADR 016 Consequences says so, the spec does not scope it | checked | refutes | `WorkerRecycling.java:140` |

Persisted by `corpus set-verdict`: none; the cleanup of 2026-11-17 already settled the spec's claims 2 and 5 as `contradicted` with their evidence, and no finding names a further labelled claim.

**Layer 1 verdict:** findings — 4

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| D1 | `SupervisorCalls.java:198-241` | `SupervisorCallsTest` (refused transition, other task, unknown generation, `ACK_MISSED` at the deadline and not before, silence suppression and re-arm) | **L2-1** the "time budget" half of "the hold's time is not charged" is neither built nor named as owed: ADR 016 and the guide settle it by "a hold produces no turn" (the token budget only), the spec's own fallback ("names it as owed") is not applied; **L2-2** the same sentence is inconsistent with Model Work 06:234 and with the `HOLDING` branch of `turnCompleted` (`SupervisorCalls.java:173-175`) that exists for turns reported while holding | checked |
| D2 | `WorkerRecycling.java:244-259` | `WorkerRecyclingTest` (once, order, under the budget, executing/waiting, facts absent) | the `finally` of `recycleAtTheBudget` has no test (no `andThrow`); "counts no idle wake-up" has no direct assertion | checked |
| D3 | `SupervisorTestFixture.java:413` (partly re-read) | n/a | none | unverified lead |
| report | n/a | n/a | the touched files no deliverable names are ADR 012/014 notes, the `WorkerRecycling` constructor gaining `WorkerTaskActivity` (a public signature change the assembly owes; ADR 016 names it, D5 does not), two constructor follow-throughs and the builder test | checked |

The lane's claim "the hold has no production caller until the assembly implements `holdEnded`" is borne out (no caller of the three entries outside `SupervisorCalls` and `WorkerRecycling`). The PR body, the test counts and the review-round counts cannot be read by a leaf.

**Layer 2 verdict:** findings — 2

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `SupervisorCalls.java:175`, `:230-241` | the transport reports the continued worker's next turn before `holdClosed`, or never calls `holdClosed` after an answer | a turn on a HOLDING generation keeps HOLDING and `answerDelivered` does not leave it, so a later turn at the budget sends `holdEnded` for a worker that is executing, and the task with a delivered answer is suspended or falls back; the order lives in prose only. Whether the assembly's order prevents it is not shown | minor |
| L3-2 | `SupervisorCalls.java:198-214` | `turnCompleted(>= budget)` while EXECUTING with a lease, then `holdOpened` | `holdOpened` never re-decides the budget; the hold produces no further turn, so the generation is never recycled and silence stays suppressed; it depends on the assembly reporting `holdOpened` before the turn | minor |
| L3-3 | `SupervisorCalls.java:230-241` | a stray or late `answerDelivered` after `holdClosed`/`taskClosed`, or to a WAITING generation | no lifecycle check, no task named: it arms the acknowledgement deadline and closes an open wait, a spurious `ACK_MISSED` loss for a worker that owes nothing (by spec: only "unknown generation ignored") | minor |
| L3-4 | `WorkerRecycling.java:244-259` | `tasks.facts(queueTaskId)` empty on a transient adapter read | the notification is skipped silently, the `holdEnded` set is not marked, the recycle runs and the task stays `awaiting_answer` under a recycled worker; a later duplicate turn report can send the notification after the end was requested | minor |
| L3-5 | `WorkerRecycling.java:168-179` | `holdEnded` or the facts read throws (including `HoldEndedReport`'s `IllegalArgumentException` for an attempt below 1) | the `finally` recycles, then the exception reaches the transport through `SupervisorCalls.turnCompleted` (`:177`); a second throwing recycle would mask the first; the "inbound API never throws" wording covers the hold entries only; untested | minor |
| L3-6 | `WorkerRecycling.java:244-259` (unverified lead) | `holdEnded` runs under the `SupervisorCalls` and `WorkerRecycling` monitors | the assembly's suspend or fallback (store I/O, possible re-entry into `WorkerCalls`) would run under the supervisor locks; deadlock cannot be settled by reading | info |

**Layer 3 not shown by reading:** the 11280 tests, the quality gate and the lock behaviour of L3-6.

**Layer 3 verdict:** findings — 6 (no major; L3-6 not shown)

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md § Code Standards | no generic `Exception` caught or thrown | conforms | only `IllegalArgumentException` and `NullPointerException` checks added |
| CLAUDE.md § Code Standards | `Optional` only as a return type | conforms | `Optional<RecycleCause> recycleAtTheBudget` |
| CLAUDE.md § Code Standards | a record of four or more components carries `@Builder` | conforms | `HoldEndedReport` (5), builder tested |
| CLAUDE.md § Code Standards | log records in `RuntimeLogMessages` | conforms | no new record; the skipped notification is silent (L3-4) |
| CLAUDE.md § Code Standards | no new hand-written fake | conforms | the fixture protocol is an EasyMock mock; the stateless lambda stubs are outside ADR 026 |
| CLAUDE.md § Build | coverage, verify, scan | not_shown_by_reading | needs a run |

## Metrics and Anomalies

- Tokens: `unknown` (unobserved). Duration: not measured in the message.
- Anomalies: five self-review rounds (the third round limit and the fifth were extended by the operator's answers); `self-review:budget-exhausted`; coderabbit and sourcery refused on quota for hours; `LspClientTest` failed under load in three full runs and passed in isolation; the formatter rewrote 30 files of `origin/main` into the branch.

## Routing and Merge Behavior

- Review: coderabbit required, sourcery optional and refused. `runbook_lane review-gate --pr-number 506 --required-reviewer coderabbit` on the merged head reads `unresolved_review_threads: 0` and `review_body_findings_total: 6`, **the same as the landing's claim**. `review_body_findings_handled=6` is **re-verified from the lane's progress file**: its `finding:` lines name `inline-1` to `inline-6` and `outside-diff-1`, each with the commit that fixed it (the gate's own `handled: 0` and `body_findings_undisposed` read a disposition record the file does not hold in the gate's form; recorded as a difference in the form, not a false claim).
- CI/merge: merge queue (merge-group run 44449299261, success), `76fb9503`.
- Sonar: `sonar_open_new_issues=0` with `quality_gate_status=ok` claimed (0 new issues, 0 hotspots, 97.6 % new-code coverage); no Sonar read in this analysis, so it stays the lane's claim.

## Reconciliation Actions

- [x] row `status` → `shipped`
- [x] row `pr` stamped `#506`
- [x] row `landing` stamped
- [x] row `plan_marshall_plan_id` stamped `rb-v2-47-continuation-hold-and-budget`
- [x] epic.md: an Open Defect for the incomplete landing
- [x] the residue carried to PLAN-V2-114 item 55 (L1-1, L2-1, L2-2, L3-1 to L3-5)

## Follow-Ups

- The hold has no production caller until the assembly implements `holdEnded` and calls the three entries (in `holdOpened`-before-`turnCompleted` order), the `WorkerTaskActivity` constructor argument, a fresh worker's `context_budget_tokens` bound, the resume from the stored session, the restart sweep, and the engine's `<asked>` state and `facts.answers` producer: owed to plan-marshall-mcp and the staged owners, as the PR body lists.
- The time budget `llm:<role>` pause during a hold is owed (L2-1): carried by PLAN-V2-114 item 55.

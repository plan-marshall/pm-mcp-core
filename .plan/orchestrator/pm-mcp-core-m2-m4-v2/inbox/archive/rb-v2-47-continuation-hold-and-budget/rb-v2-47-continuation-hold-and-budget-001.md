envelope_version=1
sender_type=plan
sender_id=rb-v2-47-continuation-hold-and-budget
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-19T03:15:44Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-47-continuation-hold-and-budget
epic=pm-mcp-core-m2-m4-v2
pr=#506
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2155
steps=setup:done,implement:done,build-gate:done,self-review:budget-exhausted,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=6
review_body_findings_handled=6
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

Guard: none on this harness (unenforced).

# PLAN-V2-47: the hold ends at the token budget, and the supervisor's half of the continuation

Merged as `76fb9503` through the merge queue (merge-group run 44449299261, success). The pull request is #506.

## Deliverables

Every Done-when is met as written. Order is `test-first` where a failing run is named, `none` where the clause is
documentary or compile-level and names no failing sha. All runs are on the pushed head `111175f9`, and the four gate
runs on `5965cc92` cover the final head.

**D1 — the entries and the lifecycle of a hold.** Met.
- "the three entries exist and are implemented": `SupervisorCallsTest.aHoldMovesTheGenerationToHolding`,
  `.aClosedHoldResumesTheExecution`, `.aRefusedHoldTransitionIsRefused`, `.aCloseOfANonHoldingGenerationIsRefused`,
  `.aHoldOfAnotherTaskIsRefused`, `.aCloseOfAnotherTaskIsRefused`, `.aHeldWaitIsNeverOfferedATask`,
  `.aHeldWaitOfAnEndingGenerationIsAnsweredEnd`, `.aHeldBoundedWaitKeepsTheSuppression`,
  `.aClosedHeldTaskReArmsTheSilenceLimit`, `.aFirstTurnHoldIsAccepted`, `.aFirstTurnHoldAcknowledgesTheOffer`,
  `.theHoldEntriesIgnoreAnUnknownGeneration` — order `test-first`; failing run `4a204c8c^` build
  `4fc1dbd78f7b403f9db189c59fdbc7d9` (the entries did not exist) and `4a204c8c^` build
  `c3e5dd49742f4f699a579830855062af` (assertion failures); green at `111175f9`, build
  `50e44bef972a40b88cbc4dbe666fdc40` (52 targeted tests).
- "the answer's acknowledgement deadline is armed by `answerDelivered`": `.anUnacknowledgedAnswerIsLostAtItsDeadline`
  (`ACK_MISSED` at the deadline and not one second before) with `.anAcknowledgedAnswerClearsTheDeadline` as the
  acknowledging control — order `test-first`, same failing runs.
- "the hold's time is not charged to the worker budget": met by reading; the budget is the latest turn's context
  (`WorkerRecycling` reads `generation.contextTokens()`), never elapsed time; recorded in ADR `runtime-worker/016`.
  Order `none` (the spec asks for the reading, not for a failing sha).
- "the hold keeps its lease and model slot, counts no idle wake-up, suppresses the silence deadline": covered by the
  tests above; order `test-first`.

**D2 — the notification.** Met. `WorkerRecyclingTest.holdingWorkerSendsHoldEndedBeforeItsRecycle` (one `holdEnded`, the
recording protocol showing `hold_ended`, `fence`, `release`, `write`), `.theHoldIsEndedOnce`,
`.holdingWorkerUnderTheBudgetSendsNothing`, `.aGenerationThatDoesNotHoldSendsNothing` (an executing and a waiting
generation at the budget), `.aHoldWhoseTaskCannotBeReadIsRecycled`; wiring end to end by
`SupervisorCallsTest.aHeldTurnAtTheBudgetEndsTheHold` (one `holdEnded`, `HOLDING` until the end path, `RECYCLE`).
Order `test-first`; failing runs `4fc1dbd7…` and `c3e5dd49…` (the report was never sent).

**D3 — the tests.** Met. The three groups above plus `WorkerProtocolRecordsTest.holdEndedReportBuilderEqualsConstructor`
and `.holdEndedReportRefusesAttemptBelowOne` (order `test-first`: the record did not exist in `4fc1dbd7…`).
**D3(c) needed no code and got none:** no hand-written `WorkerProtocol` implementer remains (PLAN-V2-136 landed); the
six named test classes mock the interface with EasyMock, which carries a new method without an edit, so "every
`WorkerProtocol` double compiles against the new method" is met by the whole reactor's compile (order `none`, a
compile-level clause that names no failing sha). The spec's own re-ground record of 2026-11-17 said so.

**D4 — the decisions documented.** Met (order `none`): ADR
`pm-runtime/doc/adr/runtime-worker/016-the-hold-ends-before-the-recycle.adoc` (the notification before the recycle, the
one-value cause, the runtime not deciding suspend or fall back with its alternatives, the two table edges, the attempt's
source, the budget reading, the wait bookkeeping, the acknowledgement rule), `runtime-worker.adoc` § The Hold and the
corrected lifecycle and recycling text, ADR `012`/`014` notes, `doc/plans/question-continuation.adoc`,
`doc/Traceability.adoc` PM-WORK-2 and PM-WORK-3.

**D5 — the owed rest.** Met (order `none`): stated in the pull request body, with the owner of each item — the
assembly's `holdEnded` and its calls of the three entries (in `holdOpened`-before-`turnCompleted` order), the
`WorkerTaskActivity` constructor argument, a fresh worker's `context_budget_tokens` bound, the resume from the stored
session, the restart sweep, and the engine's `<asked>` state and `facts.answers` producer. The package stays `open`; no
specification section is deleted.

Sonar: `sonar_open_new_issues=0` confirmed on `5965cc92`, quality gate `OK` (0 new issues, 0 hotspots, 97.6 % new-code
coverage). Build: `verify -Ppre-commit`, `verify`, `verify -Pcoverage`, `verify -Pintegration-tests -pl pm-runtime -am`
all green on `5965cc92` (11280 / 11280 / 11280 tests and 43 integration tests). Links (R17): `BROKEN LINKS 26`, all into
`pm-mcp-clients` items deleted there or `doc/plans/harness-names-in-code.adoc`, none in this plan's files; `INLINE
SCHEMA BLOCKS 0`.

Per pushed head: `4a204c8c`/`84cb5213` 11119 tests; `ad62c94f` 11123; `686dfd68` 11123; `47eca63c` 11126; `5f7b7081`
11127; `111175f9` 11128; `99594f73` 11128; `deaa80df` 11131; `28527e71` (the merge of `origin/main`) 11279;
`5965cc92` 11280.

## Personas and skills

`persona-implementer` governed D1, D2 and D5's code; `persona-module-tester` governed D3; `persona-documenter` governed
D4 and each documentation edit; `persona-code-reviewer` governed every self-review round and every triage of the PR
comments. The standing rules' R20 coding standards were read and applied (the repository's `CLAUDE.md` rules, the
`junit-core` assertion-message bound, EasyMock doubles, `@Builder` on records of four or more components).

Skills per deliverable: every deliverable declares `skills: none named`, and none was added for surface drift.

## Hypotheses

- "The hold's time is charged to the worker budget only if the budget accounting counts the hold's wall time" —
  **refuted**: the budget is the latest turn's context (`WorkerRecycling.afterTurnCompleted` reading
  `generation.contextTokens()`, `WorkerRecycling.java:147-150`), set by `SupervisorCalls.turnCompleted`, never an
  elapsed time; a hold produces no turn, so nothing is charged. Recorded in ADR `runtime-worker/016`.
- "The plan that precedes this one (`PLAN-V2-31 supervisor-calls-and-pass`) has landed and `SupervisorCalls` exists" —
  **confirmed**: `pm-runtime/src/main/java/de/planmarshall/runtime/worker/SupervisorCalls.java:52`.

Spec claims the run had to correct at the outline, from its binding re-ground records: D3(c)'s six hand-written
implementers exist no more (EasyMock since PLAN-V2-136), and the literal "Not built" sentence of
`doc/plans/question-continuation.adoc` no longer exists (the docs review #484 rewrote the package files) — the task
sentence that says what exists was updated instead.

## Findings

**Self-review (five rounds, read-only sub-agent with `persona-code-reviewer`).**
- Round 1: F1 (high) a turn reported on a holding generation advanced it to `EXECUTING` before the recycling decided its
  budget, so `holdEnded` could never fire in the wired path and the suppressed silence deadline leaked — fixed in
  `ad62c94f` with the end-to-end test `aHeldTurnAtTheBudgetEndsTheHold`. F2 the entries threw where the port never
  throws — fixed. F3 the transport's order was unstated — stated in `WorkerCalls`, ADR 016 and the guide. F4 the fresh
  worker's `context_budget_tokens` bound — recorded as owed. F5 stale ADR 012/014 sentences — notes added. F6 surface
  delta — recorded below. F7 commit noise — the formatter re-inserts it, committed as its output. F8
  `HoldEndCause.wireName()` — declined: it is the canonical spelling of the cause's wire value for the assembly's
  adapter. F9 the empty-facts branch — test added.
- Round 2: F1–F5, F7–F9 closed; N1 a closed held task left the wait open, N2 the interface sentence was overbroad, N3
  coverage edges and the ADR 014 note split a list — fixed in `47eca63c`.
- Round 3: N1–N3 closed; N4 a held bounded wait that named a task went through the offer path, re-pointed the lease and
  left the hold unclosable — fixed in `5f7b7081`.
- Round 4: N4 closed; N5 a held wait of an end-requested generation was answered `wait again` instead of `end`, N6 the
  new test did not discriminate the defect it advertised — fixed in `111175f9`.
- Round 5: N5 and N6 closed, no new finding; the only item left is the surface delta below.
- `self-review:budget-exhausted` is reported because the loop ended by the operator's authorisation of two rounds beyond
  the lane's limit, not by a round that reported nothing open — and the one item the last round leaves is the R19
  observation recorded here, not a code finding.

**PR comments (coderabbit, both reviews).** Six findings, all handled and all resolved as threads where they had one:
1. `inline-1` the guide copied the transition table and had drifted — `TRANSITIONS` is named as the source now
   (`deaa80df`).
2. `inline-2` a first asking turn's hold was refused because the supervisor still records the offer — `OFFERED → HOLDING`
   added, `aFirstTurnHoldIsAccepted` (`deaa80df`).
3. `inline-3` an answered hold stayed silence-suppressed with an informational acknowledgement — `answerDelivered` closes
   the wait, `anAnsweredHoldIsSilentAgain` (`deaa80df`).
4. `inline-4` `holdClosed` accepted any move to `EXECUTING` — it requires `HOLDING`,
   `aCloseOfAnExecutingGenerationIsRefused` (`deaa80df`).
5. `outside-diff-1` the hold-ended marker was set before the protocol call — set after it (`deaa80df`).
6. `inline-6` a hold opened from an offer kept the offer's acknowledgement deadline armed — the hold is the offer's
   acknowledgement now, `aFirstTurnHoldAcknowledgesTheOffer` (`5965cc92`); and
   `inline-5` a refused notification skipped the recycle — the recycle runs in a `finally` (`5965cc92`).
Sonar opened one finding of its own (`java:S1192`, the duplicated `"queueTaskId"` literal) — fixed in `99594f73`; the
re-read on `5965cc92` is `0` confirmed.

## Reviewers

| Reviewer | Kind | Outcome | Head | History |
|---|---|---|---|---|
| coderabbit | required | reviewed | `99594f73` (marker `coveredCommitId`), and again `28527e71` | trigger 20:14Z and 21:55Z refused on quota; escalated 23:26Z refused; trigger 00:58Z accepted, review 01:20Z (4 threads + 1 outside-diff); automatic review of the merged head 02:18Z (2 threads). All six findings handled, all threads resolved. |
| sourcery | optional | refused | — | triggered 20:14Z, refused on quota (250,000 diff characters per 7 days), next review in ~17 hours. Not retried; it never gated the merge. |

**Standing review authorization** was not needed: coderabbit's marker names the head it reviewed, every finding is
handled, and `review-gate` reports `merge_allowed: true` with `--required-reviewer coderabbit`.

## Operator questions

1. "Self-review reached its 3-round limit … How should I continue?" — options: "Run a 4th review round (Recommended)";
   "Proceed to the PR now". Selected: "Run a 4th review round (Recommended)".
2. "Round 4 found two more items in my fix … How should I continue?" — options: "Run a 5th review round (Recommended)";
   "Proceed to the PR now". Selected: "Run a 5th review round (Recommended)".

No free-text operator message was sent during the run.

## Rule check

- `guard: none on this harness (unenforced)` — the resolve result reports `guard: none`, so no guard-log line exists for
  this run and the lane's guarantees are unenforced here.
- Spec read: `spec_source: ledger_worktree`, and no `spec_differ` marker was carried.
- `executor-skew`: `match` (executor 0.1.2155, lane 0.1.2155).
- One shell file-operation the rules forbid: a `grep -c` of a source file (used once to count the duplicated literal).
  Every other file read and search went through the file tools.
- Reads outside the repository root, both mandated: the documentation sibling under
  `/home/oliver/git/plan-marshall-documentation/` (R17's links check and the project skill `traced-implementation`) and
  the build daemon's `log_file` paths under `~/.plan-marshall/marshalld/job-logs/` (the lane requires reading the log a
  build names).
- Temporary files: `.plan/temp/rb-v2-47-continuation-hold-and-budget/progress.md`, the landing file, and the CI body
  slots the `prepare-*` verbs returned (the private slots `v2-47`, `v2-47-coderabbit*`, `v2-47-t1..t6`, and the default
  `pr-create-default.md` slot, which another run consumed twice before the private slot was used).
- No session budget was applied; the run stopped only for the reviewer quota windows.
- No restart, no killed sub-agent, no re-dispatch of a run step. The five self-review rounds are the only repeated work.
- Deviations, all stated: the `throws Exception` and blank-line rewrites are the pre-commit profile's; the 30-file
  rewrite of the merged `origin/main` is the gate's; the spec's "R15 does not apply, no `pm-workflow` code changes" is
  no longer literally true because of them.

## Build server

Setup preflight: `ready`. Every build was routed (`daemon_longpoll`); no `in_process` fallback and no launch-fault
retry. One client wait bound expired on a routed `test -pl pm-runtime` (`timeout_used_seconds 300`, `timeout_source
floor`, log `e6f3a278a72b4fd290e4b7b826e37cc4`) and the run re-attached; later long builds were given `--timeout 900`.
`LspClientTest` failed under load in three full runs (`d9ed211f…`, `e1f07e79…`, `ba2156c8…`, always a 2 s LSP
initialize timeout) and passed in isolation and in the neighbouring full runs at the same heads; this plan touches no LSP
code. No `killed` job. No `runtime_stamp_reason` was carried.

## Residue

- **Surface delta (R19), 12 files against the spec's Expected Surface** — `WorkerLifecycle.java` (the two table edges),
  and `SupervisionPassTest.java`, `SupervisorScenarioTest.java` and `WorkerProtocolRecordsTest.java` (the constructor's
  new argument; the report's contract) are this plan's own and implied by its text; `ConfigValueDomain.java`,
  `EffectiveConfigKeys.java`, `EffectiveConfigKeysSchemaParityTest.java`, `EffectiveConfigKeysTest.java`,
  `ProductRegistries.java`, `ConfigKeyRegistry.java`, `ProductRegistriesTest.java` and
  `ProductRegistriesWiringTest.java` are the pre-commit profile's rewrites. The merge of `origin/main` added 30 more
  files the same profile rewrote (`28527e71`), all formatting. None of it is behaviour.
- The owed rest above is the plan's real residue: the hold has no production caller until the assembly implements
  `holdEnded` and calls the three entries, and a fresh worker's hold budget is not built.
- The spec's line "R15 does not apply, no `pm-workflow` code changes" is now false: the gate rewrote two `pm-workflow`
  classes and two of its tests. No behaviour of `pm-workflow` changed, and the spec is not edited by this lane.

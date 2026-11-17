# Landing Analysis: PLAN-V2-49 — The scenario harness runs the worker faults and the runtime restart

epic: pm-mcp-core-m2-m4-v2
workstream: WS-02
pr: #464 (merge `4840f8ea`, 2026-11-16)

> Landing record for one shipped plan, **delivered, with overstated proof**. Written by `analyze` (inbox scan) from the lane's landing
> message `rb-v2-49-scenario-worker-faults-001.md` (revision 1, amended 20:06:02Z; 5 of 5 deliverables claimed). The reading confirms the
> five deliverables and finds the lane's headline counts wrong in the harmless direction, one proof claim unsupported and several
> documentation statements that contradict the code. Nothing is blocking. A claim of the message is a lead; the counts, the ADR sentence
> and the review and Sonar numbers were re-read.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 fault directives `kill_worker`, `end_turn`, `kill_relay`, `stall_worker <seconds>` | met | read at `ScenarioScript.java:230-249`, `REFUSED` no longer names them (`:73-83`), an unmapped word is refused (`:253`), a fault without an earlier `worker_wait` of its role is refused at read time (`:116-123`); run by `ScenarioReplayRunner` (deadline logic `:726-741`, stall compare `:626`: equality counts as lost) |
| D2 `restart_runtime` | met, weak proof | engine, queue store, fence store, consultation service, task protocol and status store rebuilt over the directory (`ScenarioReplayRunner.java:639-665`, `ScenarioFixtures.java:247-262`); no test shows the rebuild (no `assertNotSame`, nothing in memory asserted gone): `RESTART_WITH_A_LEASE` would pass if `restart()` did nothing, because `WAITED_AGAIN` is the protocol's own answer to a held lease |
| D3 fault matrix (scenario 12) | met, one row relabelled | the spec's row "stalled longer than its silence limit" became "stalled up to the limit" (`STALL_LONG` stalls exactly `SILENCE_LIMIT`); a strict overrun is covered only through `STALL_CUMULATIVE` (20 s plus 15 s against 30 s); late-call refusal `generation_fenced` is in every loss scenario; the budget case closes the task `lost` with `ATTEMPTS_SPENT` |
| D4 runtime crash with a leased task | met in two pieces | the spec's one scenario (same generation waits again, a second generation is answered `no_task`, one submit closes the task) is `RESTART_WITH_A_LEASE` plus `RESTART_HELD_BY_ONE_GENERATION`; the `no_task` half is a direct protocol call in Java (`shouldAnswerASecondGenerationNoTask`), not a replayed script |
| D5 tables, documents, package file | met | `REFUSED`, `package-info.java`, spec part 07 sentence, ADR 010 new, guide, `Traceability.adoc` PM-TEST-4; `doc/plans/supervision-scenarios.adoc` reduced to what `plan-marshall-mcp` still owes and kept with its index row (`doc/plans/README.adoc` not in the diff) |

## Three-Layer Analysis

One read-only leaf (`execution-context-level-3`, `orchestrator.analyze`) read the merged diff (`4840f8ea^1..4840f8ea`, 19 files, +1611/-67,
nothing under `src/main`) against the spec and the PLAN-V2-33 landing; the orchestrator re-read the script count, the nested test count,
the `attemptEnd()` callers, the ADR 010 sentence, the diff stat, the review threads and Sonar. By reading, no build, no test run, no write.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity |
|---------|-------|---------|----------|
| L1-1 | "12 scripts through `WorkerFaultScenarios.ALL`" | false: `all()` holds **13** (8 fault, 5 restart), 13 `all.put(` (re-read) | minor |
| L1-2 | "`ScenarioReplayRunnerTest$WorkerFaults` (16 tests)" | false: **20** `@Test` in the nested class (re-read); the guide's test table carries the stale numbers and says "three runtime-crash scenarios" for five | minor |
| L1-3 | D3's "longer than" row and D4's single scenario were relabelled or split without saying so | confirmed | minor |
| L1-4 | Expected Surface: `ScriptedFixtureWorker.java` declared, `ScriptedProtocolWorker.java` changed (the lane's R19 note is right); ADRs 004, 007, 009 and `doc/Traceability.adoc` touched though not surface | confirmed | minor |
| L1-5 | the diff touches `LandingVariantValidationTest.java` by one blank line: a gate rewrite outside the plan's scope, not disclosed in the message (memory rule: own cleanup commit) | confirmed (diff stat, re-read) | minor |

### Layer 2 — Implemented

| Point | Reading | Verdict |
|-------|---------|---------|
| Deadline logic | ack deadline while offered and not informational, silence limit from the last call otherwise; read-only on `WorkerParameters`; stalls add up; loss instants asserted by equality, so an early or late loss fails (`ScenarioReplayRunnerTest.java:655-656,675-676,708-709,740-743,856-857`) | sound; "detected at the deadline" is a self-consistency check of the harness, not a detector (info) |
| Order claim of ADR 010 | `report` ends the attempt and spends where `counts()` is true (`TaskProtocol.java:443-461,970-976`), `fence` finds the lease released and spends none (`:839-862`; `QueueTaskRecord.java:387-391`) | confirmed |
| ADR 010 Alternative 4 | says the wait is held "for a third of the limit" and asserts the loss "comes later than the unsuppressed deadline": the code holds `max(1, SILENCE_LIMIT/6)` (`WorkerFaultScenarios.java:37`), a sixth, and the test asserts equality to `ack + 5 + 1 + 30` only | false statement (re-read), minor |
| Guide failure-mode row "kill_worker or stall_worker on a task the protocol no longer leases" (`workflow-scenario.adoc:291`, code `ScenarioReplayRunner.java:683-686`) | no test fires it, contrary to "each new row fired by a test"; also untested: `end_turn` on an unacknowledged offer, `stall_worker` on an unacknowledged offer (the shared `deadline()` ack branch is covered through `kill_worker`) | minor |
| `package-info.java` | the ADR path in markdown backticks renders literally in Javadoc | minor |
| Stale records | ADR 001 Consequences still says the faults "follow with `supervision-scenarios`"; ADR 008 (`:7,50`) still lists `worker_wait`/`worker_do` as refused (stale since PLAN-V2-33) | minor |
| Assertion messages | about 30 sampled new messages are 20–60 characters; `assertTrue(failure.getMessage().contains(...), failure.getMessage())` (`ScenarioReplayRunnerTest.java:972-973`) uses the dynamic text as its message | info |

### Layer 3 — Correct

| Point | Reading | Verdict |
|-------|---------|---------|
| `restart_runtime` | "nothing in memory survives" is overbroad: `locks`, `questionReader` (answers held in memory), the job-request capture, the `ScriptedFixtureWorker` map and the `replaced` flag survive; `lastCallAt` is not durable (a consultation call is not stored, so after a restart it falls back to the ack instant), untested | minor |
| Restart cost | `WAITED_AGAIN.counts()` is true: the first wait after a restart ends the first attempt and spends a re-offer; nothing asserts `spentReOffers` there and neither ADR nor guide says it | minor |
| `QueueTasks` id counter | `sequence = queue.records().size()` (`ScenarioFixtures.java:737`) counts records, not the highest id: with qt-1 and qt-3 present the next id is `qt-3`, a collision; the harness cannot produce a gap, so the lane's "harmless" holds, but "seeds from the records" is a count | minor |
| Consultation fault refusal (`inline-7`, `inline-8`) | present (`ScenarioReplayRunner.java:676-679`) and tested (`shouldRefuseAFaultOnAConsultationTask`, `shouldCountAConsultationAsACall`) | clean |
| Dead code | `Deadline.seconds()` (`ScenarioReplayRunner.java:749`) is never read; a double blank line before the easymock import in `ScenarioFixtures` | minor |
| `LossReason.attemptEnd()` | no caller in `src/main` of `pm-core`, `pm-workflow` or `pm-runtime` (grep, re-read: declaration `LossReason.java:58` and Javadoc only); the supervisor only fences, so the report that writes the attempt end is outside this repository; the method was kept on purpose (PLAN-V2-129 A5, settled 2026-11-10) | the lane's lead is right; recorded as a Watch |
| PLAN-V2-33 carried points | item 1 adopted (`queued` is reached only through the `workerStateOf` seam); items 2, 3 and 4 answered in the landing text only: the guide gives no rationale for the failure form (`:280`), does not state the narrowed `assert_worker_state` check, and does not say that the Java-test half stays | minor |

## Review and Quality Numbers

| Number | Message | Re-read |
|--------|---------|---------|
| unresolved review threads | 0 | 0 (8 threads on #464) |
| review body findings total / handled | 8 / 8 | not re-verified (the finding list is in the message only); the 8 threads resolved match |
| Sonar open new issues / hotspots | 0 | 0 / 0 (SonarCloud, `pullRequest=464`) |

`landing-check`: `complete=false`, missing `total_tokens` and `plugin_version` only (register E27). Surface delta unmeasured. The lane's own
rule check names about 16 read-class shell calls (`ls`, `sed`, `grep`, `cat`, `tail`, `wc`) and one `;` chain: forms, not writes; guard
unenforced on this harness.

## Residue

1. **Carried into PLAN-V2-114, item 38** (everything in layers 1 to 3 above that is a statement or a test of the package): the stale
   counts and the guide's test table, ADR 010 "third" and its unproved "later than", ADR 001 and 008, the `package-info` markup, the missing
   tests (loss refused at the protocol, `end_turn` unacknowledged, `stall_worker` on an offer, restart wait cost, a restart that rebuilds),
   the overbroad "nothing in memory survives", the `QueueTasks` count, `Deadline.seconds()`, the PLAN-V2-33 carried items 2 to 4 as guide
   text, and the `LandingVariantValidationTest` blank line in its own cleanup commit.
2. **Watch in `epic.md`:** `LossReason.attemptEnd()` has no caller in this repository.
3. **Not routed:** the stale `target/test-classes` after a renamed resource (a build-hygiene note for the host, not a defect of the repository);
   the test class `ScenarioReplayRunnerTest` at about 1 060 lines (`R20` reports, not enforces; PLAN-V2-114 item 38 may split it at the outline);
   the spec's own inconsistencies (standing-rules `r4` against `r7`, the garbled R17 parenthetical) are settled by this record.

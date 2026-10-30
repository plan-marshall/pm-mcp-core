# PLAN-V2-25: Every primitive the engine runs is a `Command` with an id, a correlation and a provenance, and its `CommandSettlement` comes back on the `EngineStep`: the dispatch through a value, no behaviour change, no log yet

epic: pm-mcp-core-m2-m4-v2
workstream: WS-01

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-25-command-record-and-dispatch.md` and is queued as one row file, `queue/PLAN-V2-25.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.
>
> This is the THIRD of three specs of the command-model groundwork (decided by the operator on 2026-10-29: option B1 first, building toward C). It `Depends on:` PLAN-V2-19 (`engine-carries-reasons`, the second: the engine's reason handling, which this plan's dispatch carries on), PLAN-V2-15 (`call-time-inputs-and-slots`, AMENDED to carry `command_id` and `correlation_id` in the call frame: it brings the id types `CommandId` and `CorrelationId` in `core.id`, the id source in `Ports`, and the frame members this plan reuses) and PLAN-105 (`plan-instance-files-and-history`, landed #230). The first is PLAN-V2-01 (`primitive-failure-reasons`).

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r4`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R15, R17, R18, R19, R20. (R15 because the plan changes `pm-workflow`: its item 4 — a completeness test over an enum derives its expectation at run time — governs the `Trigger` test; R10 because `05-primitives.adoc` may be read by a test, searched at the outline; R09 holds trivially; R13 and R14 do not apply: nothing is persisted, no `core.store` change, no versioned artifact; R16 does not apply: no `pm-runtime`.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: the existing `pm-core/doc/developer/core-spi.adoc` (package `core.spi`) and `pm-workflow/doc/developer/workflow-engine.adoc` (package `workflow.engine`); ADR directories: `pm-core/doc/adr/core-spi/` and `pm-workflow/doc/adr/workflow-engine/` (the next free numbers of each, renumbered against `origin/main`, R11); layout: workstream WS-03. No new package: no `README.adoc` line.
- R12 — log message holder: `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`), `CoreLogMessages` for `pm-core`; none expected.

## Objective

The engine already dispatches "primitive id plus bound input to handler to typed result": `PrimitiveRegistry.find(id)` (`WorkflowEngine.java` line 529), `binder.bind(...)`, `execute(...)`, the result (lines 813 to 830). But the call is never a value: it lives in the locals of `runPrimitive` and leaves no trace, so nothing can later say which command ran, with which input, for which cause, with which result. The operator decided on 2026-10-29 that the command model is groundwork: first the reason (`PLAN-V2-01`, `PLAN-V2-19`), then the command as a value (this plan), then the persisted log (not staged). This plan makes the command a value and dispatches through it, **without changing what any scope does** and **without writing a log**:

- `Command`: `commandId`, `correlationId`, the primitive id, the typed input record the binder returned, the `Provenance` (what caused the advance), the instant;
- `CommandSettlement`: what the command returned, flattened to plain values (outcome name and class, reason, facts, spawned job, a condition it raised), so a later log can store it without generics;
- the engine builds the `Command` around the record its `ParameterBinder` returns (the binder keeps its shape: the assembly implements it), calls the handler with `command.input()`, and returns the settlements of an advance on the `EngineStep` it already returns. The engine stays a pure function over the status: nothing is pushed to a sink, the caller that writes the status in a transaction can write the settlements in the same one (the later plan).

The handler keeps the name `Primitive` (decided): a rename touches 15 test files, 8 specification parts, the requirement and the catalogue's ids for no gain; `Command` is the data type beside it.

**Decisions this spec takes (each becomes an ADR, R06):**

1. *`Command` is a record of values, `Primitive` stays the handler.* `Command(CommandId commandId, CorrelationId correlationId, String primitiveId, Record input, Provenance provenance, Instant issuedAt)` in `core.spi`. No `target` kind (primitive or unit call) is declared: the unit call is already a value, the call frame, which `PLAN-V2-15` (amended) gives the same two ids; a second kind is added by the plan that has a consumer for it.
2. *The ids are `PLAN-V2-15`'s.* `CommandId` (`^cmd-[0-9a-f]{16}$`) and `CorrelationId` (`^cor-[0-9a-f]{16}$`) are drawn from the `RandomGenerator` that `Ports` carries; one `CorrelationId` per advance (per `Run`), shared by every command the advance runs and by the frames it pushes; one `CommandId` per primitive run. A seeded generator makes a scenario replay deterministic.
3. *`Provenance(Trigger trigger, Optional<String> triggerRef)`: what caused the advance, nothing about who.* `Trigger` has one value per entry point of the engine that creates a `Run`: `START`, `RESUME` (the advance without an event), `JOB_SETTLED` (ref: the job id), `TASKS_SETTLED`, `QUESTION_RESOLVED` (ref: the question id), `OPERATOR_ACT` (ref: `<state id>/<link id>`), `TIME_ELAPSED`, `STEP_OUT`, `FALLBACK`. The operator's or device's identity is not known to the engine (the audit context has it); the persisted log adds the actor from there. A completeness test derives the expectation from the permitted subclasses of `EngineEvent` (R15 item 4).
4. *The settlements travel on `EngineStep`, not through a sink port.* `Resting`, `Ended` and `ConditionRaised` gain the component `settlements` (a list, in run order). Reason: the engine keeps nothing of its own and returns the status for the caller to write in the transaction of the advance (`EngineStep` Javadoc); a log written in that transaction needs the settlements back, not pushed. What a sink cannot give back — a write-AHEAD line before a side-effecting primitive runs — is the later plan's `issued` hook; this plan does not pre-empt it.
5. *`CommandSettlement(Command command, String outcome, OutcomeClass outcomeClass, Optional<String> reason, Map<String,Object> facts, Optional<String> spawnedJobId, Optional<String> raisedCondition, Instant settledAt)`.* Flat, so it serializes without generics; `reason` is the wire name; `raisedCondition` is set when `PLAN-V2-19`'s mapping raised one (then no transition was taken).
6. *A value that carries an input does not print it.* `Command.toString()` prints the ids, the primitive id, the provenance and the NAME of the input's class, never the input; `CommandSettlement.toString()` prints the fact names, never their values. Commit text, paths and tokens reach a primitive as its input (`PLAN-V2-15`), and a record's generated `toString` would put them into a log line or an exception message. The later log redacts and bounds what it stores (the operator's answer, recorded under Dependencies); this plan only refuses to leak by accident.
7. *The context a primitive receives does not change.* `WorkflowExecutionContext` is a functional interface the engine implements as a lambda (`WorkflowEngine.java` line 818) and tests implement too; adding the command id to it would break every implementer. A primitive that needs its command id as an idempotency key for an external effect gets it through the later plan, with the `issued` hook.
8. *Nothing is persisted and no native-image metadata is needed.* The records are in-memory values; the schema blocks the parity tests read are unchanged (the frame blocks are `PLAN-V2-15`'s).

## Deliverables

1. **The value records (test first).** In `pm-core` package `de.planmarshall.core.spi`: `Command` (the six components of Decision 1; non-null; `input` is a `Record`; `toString` of Decision 6), `Provenance` with the nested enum `Trigger` (wire names in lower case with an underscore, a `wireName()` method; the `triggerRef` is non-blank when present), `CommandSettlement` (Decision 5; the facts an unmodifiable ordered copy, as `PrimitiveResult` keeps them; the outcome name in the grammar of a wire name; the reason, if present, in the same grammar; `toString` of Decision 6), each with `@since` and a documented component list. `package-info.java` names them.
   **Done when:** `CommandTest`, `ProvenanceTest` and `CommandSettlementTest` (new, `pm-core/src/test/.../spi/`) cover each component's null and blank refusal, the facts copy, the order of the facts, the wire names of all `Trigger` values (a pinned list), and that `toString` of a command whose input record holds a secret-looking text and of a settlement whose fact holds one contains neither value (a generator-driven check with cui-test-generator).
2. **The dispatch through the value (test first).** `WorkflowEngine.Run` gains a `Trigger`, a trigger reference and a correlation id (the one `PLAN-V2-15` mints per `Run`); each of the five places that creates a `Run` (the lines of `start`, `advance(status)`, `advance(status, event)`, `stepOut`, `unwindToFallback`) passes its trigger; `runPrimitive` builds the `Command` around the record `binder.bind(...)` returned (a fresh `CommandId` from the `RandomGenerator` of `Ports`, the clock's instant), calls the handler with `command.input()` through the existing private `execute(...)`, and, after the result and `PLAN-V2-19`'s checks, appends a `CommandSettlement` to the `Run`. The `ParameterBinder` and `Ports` signatures, the registry, `Primitive`, `PrimitiveResult`, the transition selection and the facts merge are unchanged.
   **Done when:** `CommandDispatchTest` (new, `pm-workflow/src/test/.../engine/`; a new fixture file, `EngineFixtures` read and not edited) shows, over a unit that chains three primitives: three settlements in run order, three distinct command ids, one correlation id, the instants from the clock, the outcome and the facts of each result, the input record identical (same instance) to what the binder returned; a parameterized case per `Trigger` (one event of each permitted subclass of `EngineEvent`, plus `start`, the plain advance, `stepOut` and `unwindToFallback`) shows the provenance and its reference; a reason-bearing result of `PLAN-V2-19`'s fixtures shows `reason` and, for a mapped reason, `raisedCondition` and no transition; two engines built with identically seeded generators produce equal settlements and equal statuses; every existing test of `pm-workflow` passes UNCHANGED (this is the proof of "no behaviour change"; list the command and the count in the pull request body).
3. **The settlements on the step (test first).** `EngineStep.Resting`, `Ended` and `ConditionRaised` gain `settlements` (an unmodifiable list copy, never null); the factory `ConditionRaised.raise` used by the public `WorkflowEngine.raise` and `ConditionRaised.of` produce an empty list; `WorkflowEngine.ended(...)` and `resting(...)` and the in-chain raise pass the run's list. The three construction sites in main (`EngineStep.java` line 125, `WorkflowEngine.java` lines 490 and 494) and the four in tests (`WorkflowEngineTest` lines 630 to 634 and `EngineResumeTest` line 271) are updated.
   **Done when:** `EngineStepTest` cases (in `WorkflowEngineTest`, extended) show the list is copied and immutable and the three variants carry it; the contradictions the constructors already refuse are still refused; `ScenarioReplayRunner` (test code that reads steps) compiles and its scenarios pass unchanged.
4. **The specification, the guides and the decisions (R05, R06).** `05-primitives.adoc`: one short subsection `The Command and its Settlement` (`[#command-and-settlement]`, new anchor; every existing anchor stays) that names the two values, the ids, the provenance and the rule that the engine returns settlements and writes none; "Remaining: the persisted command log". `core-spi.adoc` (the contract table rows, `Pitfalls`: "do not print or log an input record; print its class"), `workflow-engine.adoc` (a section `Commands and Settlements`: the dispatch, the triggers, the correlation, determinism with a seeded generator). One ADR per decision (eight; next free numbers per directory, R11). The Traceability lines for PM-IMPL-5 and PM-WF-16 are shared bookkeeping.
   **Done when:** each guide claim names the test that proves it; the search of the test tree for readers of `05-primitives.adoc` is recorded in the first progress note (none at HEAD); the links check (R17) reports `BROKEN LINKS 0` or only the sibling's own; the pull request body lists **Owed to the milestone exit** and the **external hand-offs** below.

**Done when:** every primitive an advance runs leaves a `CommandSettlement` on the returned step, carrying a command id, the advance's correlation id, a provenance, the input record's identity, the outcome, the reason and the facts; the engine's behaviour (statuses, transitions, conditions, faults) is the same as before for every existing test; the four deliverables' tests exist and pass beside every existing test of `pm-core` and `pm-workflow`; `verify`, `verify -Ppre-commit` (its rewrites reviewed and committed), `verify -Pcoverage` and `verify -Pintegration-tests -pl pm-core -am` are green. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit`, then `verify` of `pm-core` and `pm-workflow` (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed and new classes and the per-module run, each reported in the pull request body). **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main`; a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own.

**Trace starting set:** PM-IMPL-5; PM-WF-16; PM-ARCH-4 (the engine keeps nothing of its own); Workflow DSL § Primitive Interface (Outcomes and Reasons, from `PLAN-V2-01`); Identifiers § Registry (`command_id`, `correlation_id`, from `PLAN-V2-15`). Watch: none edited.

## Standing Constraints

- **Log messages: none expected.** Nothing is logged, nothing is written.
- **No behaviour change.** A scope advances to the same status with the same conditions and faults as before; the only new thing is data on the returned step. Every existing test passes unchanged; if one must change its expectation, stop and say why (R07).
- **The `ParameterBinder`, `Ports`, `Primitive`, `PrimitiveResult` and `WorkflowExecutionContext` shapes are not changed by this plan** (`Ports` and the ids are `PLAN-V2-15`'s; if `PLAN-V2-15` merged without them, see the verify-first clause).
- **No input is printed.** Decision 6 holds for `toString`, for exception messages and for any log line this plan or its tests might add.
- **No generic `Exception`** is caught or thrown in production code.
- **Add no dependency and no plugin** (CLAUDE.md).
- **`EngineFixtures`, `ScenarioFixtures` and the other shared fixture files are read, not edited** (`PLAN-V2-15`, `PLAN-V2-33`, `PLAN-V2-49`); new fixtures go into new files.

## Claim Labels

All lines read at `origin/main` `26008c5c` (the local ref; `fetch` was not allowed).

- OBSERVED: nothing of this exists — `git ls-tree` finds no class `Command`, `Provenance`, `Trigger`, `CommandSettlement` or `IdSource`; `git grep -i 'command_id\|correlation'` over `doc`, `pm-core/src` and `pm-workflow/src` is empty; `pm-core/src/main/java/de/planmarshall/core/id/` holds `package-info.java` only; `doc/specification/identifiers/01-general-rules-and-registry.adoc` registers no command or correlation id
- OBSERVED: the dispatch — `pm-workflow/src/main/java/de/planmarshall/workflow/engine/WorkflowEngine.java` lines 802 to 830 (`runPrimitive`: `primitiveOf`, `binder.bind` at 816, the context lambda at 818, `execute` at 819, the outcome check at 820, the facts merge at 824 to 826, `wireName()` at 828), 660 to 668 (`execute` helper, signature at 661: casts the record to `parameterType()` and calls `primitive.execute`), 529 to 531 (`primitiveOf` over `PrimitiveRegistry.find`), 170 to 197 (`ParameterBinder`), 153 (`Ports(jobs, tasks, questions, clock)`)
- OBSERVED: the places that create a `Run` — `WorkflowEngine.java` lines 263 (`start`), 284 to 287 (`advance(status)`), 318 (`advance(status, event)`), 440 (`stepOut`), 480 (`unwindToFallback`); the events — `EngineEvent.java` lines 39 (`JobSettled`), 72 (`TasksSettled`), 109 (`QuestionResolved`), 135 (`OperatorAct`), 163 (`TimeElapsed`)
- OBSERVED: the step — `pm-workflow/src/main/java/de/planmarshall/workflow/engine/EngineStep.java` lines 32 (sealed, three variants), 49 (`Resting`), 78 (`Ended`), 102 (`ConditionRaised`, `of` at 122, `raise` at 139); the construction sites in main: `EngineStep.java` 125, `WorkflowEngine.java` 490 and 494; tests `WorkflowEngineTest.java` 630 to 634 and `EngineResumeTest.java` 271; the engine returns the status for the caller to write in the transaction of the advance (the Javadoc of `EngineStep`)
- OBSERVED: the `Ports` construction sites — `pm-workflow/src/test/.../engine/EngineFixtures.java` line 278 and `scenario/ScenarioFixtures.java` line 256, the only two outside the record
- OBSERVED: the precedent of random ids drawn in `pm-core` — `pm-core/src/main/java/de/planmarshall/core/queue/QueueTaskId.java` (`^qt-[0-9a-f]{16}$`, `random()`, `random(RandomGenerator)`, `parse`), `store/EntryId.java`, `store/JobId.java`
- OBSERVED: the frame — `pm-core/src/main/java/de/planmarshall/core/store/WorkflowStatus.java` line 195 (`CallFrame(callerWorkflowId, callerStateId, subworkflowId, entry, boundArgs, pushedAt)`, which `PLAN-V2-15` extends by `argSources` and, amended, by `commandId` and `correlationId`)
- OBSERVED: the replay harness reads steps — `pm-workflow/src/test/.../scenario/ScenarioReplayRunner.java` lines 67, 234, 281, 293 to 295
- OBSERVED: the specification — `doc/specification/workflow-dsl/05-primitives.adoc` lines 136 to 148 (the step records "derived from the outcome class": specification text with no code behind it), 150 to 186 (the example, `PLAN-V2-20`'s)
- HYPOTHESIS: `PLAN-V2-15` (amended) has merged `CommandId`, `CorrelationId`, the `RandomGenerator` component of `Ports` and the two frame members; re-read them before editing
- HYPOTHESIS: `PLAN-V2-19` has merged `runPrimitive`'s reason handling and its in-chain raise, so the settlement can name `raisedCondition`; the shape of its private result type is the outline's
- HYPOTHESIS: the three main and four test construction sites of the `EngineStep` variants are all there is (`git grep -n 'new EngineStep\.\|ConditionRaised(' ` at the head)
- HYPOTHESIS: nothing in `plan-marshall-mcp` constructs an `EngineStep` (it has no consumer of the engine yet: `pm-mcp-server/pom.xml` depends on `pm-core` only) — recheck at the head
- Verify-first clause: before editing, confirm `PLAN-V2-19`, `PLAN-V2-15` and `PLAN-105` are merged; if `PLAN-V2-15` merged WITHOUT the id types and the `Ports` generator, Deliverable 1 also creates `core/id/CommandId.java` and `CorrelationId.java` (the grammar and `random(RandomGenerator)` of `QueueTaskId`), their tests, and the `Ports` component, and the registry rows in `identifiers/01-general-rules-and-registry.adoc` — say so in the first progress note and list those files in the landing; list the open pull requests for one that touches `WorkflowEngine`, `EngineStep` or `05-primitives.adoc`; re-read the lines above at the head. A refutation loops back to re-scope.

## Expected Surface

`pm-core` main (`de.planmarshall.core.spi`):

- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/spi/Command.java`, `Provenance.java`, `CommandSettlement.java` — new
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/spi/package-info.java` — the contract paragraph names the values (shared with `PLAN-V2-01`, sequenced after it)
- HYPOTHESIS, `PLAN-V2-15`'s (amended), read not edited: `pm-core/src/main/java/de/planmarshall/core/id/CommandId.java`, `CorrelationId.java`

`pm-core` tests:

- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/spi/CommandTest.java`, `ProvenanceTest.java`, `CommandSettlementTest.java` — new

`pm-workflow` main (`de.planmarshall.workflow.engine`):

- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/engine/WorkflowEngine.java` — `Run`, the five creation sites, `runPrimitive`, `ended`, `resting`
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/engine/EngineStep.java` — the component `settlements` of the three variants

`pm-workflow` tests:

- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/engine/WorkflowEngineTest.java`, `EngineResumeTest.java` — the four construction sites of the step variants
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/engine/CommandDispatchTest.java` and a new fixture file for it — new
- OBSERVED, read not edited: `engine/EngineFixtures.java`, `scenario/ScenarioFixtures.java`, `scenario/ScenarioReplayRunner.java`

Specification (specific files, R19):

- OBSERVED: `doc/specification/workflow-dsl/05-primitives.adoc` — one new subsection; the lines 150 to 186 stay byte-stable

Not surface (R19, shared bookkeeping): `doc/plans/README.adoc`, `doc/Traceability.adoc`, `doc/LogMessages.adoc`, the existing guides `core-spi.adoc`, `workflow-engine.adoc`, and the ADR directories `core-spi/`, `workflow-engine/`.

## Dependencies and Sequencing


- **Landing note (2026-10-29):** PLAN-105 landed (#230): the places that create a `Run` are now seven, not five, and `Run` already holds the history, the transaction and `instanceVersion`.
- Depends on: PLAN-V2-83 (`skills-conformance-foundation`: the dependencies and rule R20); and then: PLAN-V2-19 (`engine-carries-reasons`); PLAN-V2-15 (`call-time-inputs-and-slots`, amended: `CommandId`, `CorrelationId`, the generator in `Ports`, the frame members); PLAN-105 (`plan-instance-files-and-history`, landed #230). Transitively PLAN-V2-01 (`primitive-failure-reasons`) and PLAN-V2-04.
- **Overlaps with live plans** (exact entries of the Expected Surfaces, counted over `plans/*.md` on 2026-10-29, live = staged, running, parked): `WorkflowEngine.java`: `PLAN-V2-21`, `PLAN-V2-42`, `PLAN-V2-13`, `PLAN-105`, `PLAN-V2-15` (5 plans); `WorkflowEngineTest.java`: `PLAN-V2-21`, `PLAN-V2-42`, `PLAN-V2-13` (3); `05-primitives.adoc`: `PLAN-V2-69`, `PLAN-V2-20` (2; also `PLAN-V2-01` and `PLAN-V2-19`, which precede this plan); `core/spi/package-info.java`: no live plan declares it besides `PLAN-V2-01`; `EngineStep.java`, `EngineResumeTest.java`: 0; `EngineFixtures` and `ScenarioFixtures` are read, not edited (`PLAN-V2-13`, `PLAN-V2-15`, `PLAN-V2-33`, `PLAN-V2-49` own them): 0. `PLAN-46`, `PLAN-V2-39`, `PLAN-V2-43`, `PLAN-V2-57`, `PLAN-V2-58`, `PLAN-V2-10`: 0.
- **Verdict for the disjointness gate:** NOT disjoint from `PLAN-V2-13`, `PLAN-V2-21` and `PLAN-V2-42` (the engine file, merge order only for `PLAN-V2-21` and `PLAN-V2-42`, a real rebase for `PLAN-V2-13`), and from `PLAN-V2-69` and `PLAN-V2-20` on `05-primitives.adoc` (byte-disjoint hunks); sequenced after `PLAN-V2-19`, `PLAN-V2-15` and `PLAN-105`. Do not emit together with `PLAN-V2-13`; recommended order `PLAN-V2-01`, `PLAN-V2-15`, `PLAN-V2-19`, `PLAN-V2-25`, then `PLAN-V2-13`.
- Blocks: the follow-up below.
- **Follow-up, not staged (the operator's answers of 2026-10-29; the next epic revision stages them):** `command-log-store` (WS-02) and `engine-writes-the-command-log` (WS-03) build the persisted command log on this plan's values. Decided: three linked records — the command log (the effects view: `issued` and `settled` lines), the plan's history (`PLAN-105`, the process view) and the audit log (the hash-chained security mirror) — tied by `command_id`; the input stored redacted and bounded with the SHA-256 of the full value instead of the full input (`SecretRedactor` of `core.queue` exists); replay means an audit read or a result replay in the scenario harness, never a re-execution of a side effect; a write-ahead `issued` line, with a resume rule that asks whether the effect happened, for primitives with external side effects (git and CI). The first needs the record, codec, schema block and parity test (R10), the store as a `ScopeTransaction` target with the digest sidecar, retention and `delete-plan`; the second the `issued` hook, the resume rule, `command_id` on the `left` line of the history and the derived step record.
- **External hand-offs (not specced here; list them in the pull request body):** (1) `plan-marshall-mcp`, package `engine-wiring`: construct the engine with a `RandomGenerator` in `Ports` (a `SecureRandom`; `PLAN-V2-15`'s hand-off) and read `EngineStep.settlements()` (ignoring them is valid until the log exists). (2) None for `pm-mcp-clients`: a command log reaches the clients only if `PLAN-V2-26`'s process resource surfaces it (a decision of the follow-up).
- Adjacent to: `pm-mcp-clients` (`pm-api`), which stays untouched.

## Package Bookkeeping

No work package file of `doc/plans/` covers this plan; like PLAN-95 it has no package file and no index row (R01's removal of a row and a file does not apply). R05's condense-and-point applies to `05-primitives.adoc`. No pinned schema block changes (nothing is persisted).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-25-command-record-and-dispatch.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

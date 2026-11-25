# PLAN-V2-202: The submit validates the consultation references: TaskProtocol.submit never checks consultation_ids

epic: pm-mcp-core-m2-m4-v2
workstream: WS-06
origin: PLAN-V2-162 (#567) review (CodeRabbit finding on `05-consultation`), landing revision 25, section 9; PM-WATCH-DISP-78
lane: runbook

> Staged plan spec, one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off. SELF-SUFFICIENT together with the standing rules. This is a production defect: it needs no #567 code and may start at once on main.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`). They bind this plan exactly as if they were written below. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R05, R06, R11, R14, R15, R19, R20 (the drafter's candidates, confirmed by the orchestrator against the rules file at staging; an id the lane finds not applicable it says so).

## Objective

`ConsultationService.validateSubmit` (`pm-workflow`, `consult` package) compares a submit's `consultation_ids` with the consultations delivered to the task. `TaskProtocol.submit` (`task` package) never calls it, takes no `consultation_ids`, and has no refusal that can carry its reason. A submit after an answer therefore closes the task `submitted` with an empty or wrong list. Model Work § The Submit Refers to the Consultation and PM-WATCH-DISP-78 require the opposite: every delivered id and no other, else `invalid_link_args` naming the id, the task stays executing, and the worker may submit again. The scenario suite hides the defect, because its replay runner calls the validator itself before it calls the protocol.

## Deliverables

1. **Failing tests first, through the submit path of the task protocol.** All tests are in `TaskProtocolSubmitTest` or a new neighbour, not in `ConsultationServiceTest`.
   - A task with a delivered consultation and an empty list is refused `invalid_link_args`, the detail names the missing id, and the task stays `EXECUTING`. This fails at 3ba79c4f.
   - An unknown id is refused, naming it. This fails at 3ba79c4f.
   - Re-submitting the corrected list closes the task `submitted` (the worker may submit again).
   - Matched controls: the exact delivered ids close the task `submitted` (holds at 3ba79c4f); a task with no consultation and no ids closes `submitted` (holds at 3ba79c4f); a consultation answered between the worker's last read and its submit is refused naming the new id; a consultation entity still gets `link_not_available`.
   - Skills: pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: module-tester
2. **Wire the validation into the closing transaction.** `submit` takes the list and checks it inside `onLeasedTask`, after the link check and before the skills check (Model Work order: job and generation, lease, link, schema, skills), so there is no gap between the check and the close.
   - One implementation of the rule. `ConsultationService.validateSubmit` either becomes the pure rule over the leased record that both use, or is removed. Do not copy it. Today the `task` and `consult` packages import each other not at all, so the outline decides where the rule lives.
   - `CallResult.Refused` carries only `missingSkills`, so give it a reason member. The detail text is the one of `validateSubmit` ("omits the delivered consultation_id '%s'", "names the consultation_id '%s', which was not delivered").
   - Edge set of the unchanged consumers: `CallResult.Accepted(task)` is unchanged, `Refused.missingSkills` stays empty for the new refusal, and the refusals `link_not_available`, `stale_entity`, `skills_not_loaded` keep their order and meaning.
   - Remove the validator call from `ScenarioReplayRunner` (`:442`), so the scenario goes through the protocol.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety, pm-dev-java:java-lombok, pm-dev-java:javadoc, pm-dev-java:junit-core
   - Persona: implementer
3. **Documents.** Correct the sentences that say no production submit path calls it: `pm-workflow/doc/developer/workflow-consult.adoc` (`:112-114`, `:122`), the task guide, the scenario guide (`:144`, `:280`), ADR `workflow-consult/005` (`:20`), ADR `workflow-scenario/009` (`:50`). State the submit in `doc/specification/model-work/05-consultation.adoc` § The Submit Refers to the Consultation and `02-task-protocol.adoc` § Execute & Submit. Update PM-WATCH-DISP-78 and Traceability. Add one ADR in `pm-workflow/doc/adr/workflow-task/` (next free number at the head of main when the outline is written) for the rule's place.
   - Skills: pm-documents:ref-asciidoc, pm-dev-java:javadoc
   - Persona: documenter

**Done when:**
- Deliverables 1 to 3.
- The first two tests of deliverable 1 fail at 3ba79c4f with a failing run id and pass after the change.
- The matched controls hold at 3ba79c4f and still hold after the change.
- The scenario suite (`ScenarioReplayRunnerTest`, the consult scenario at `:1082`) passes without the runner's own validator call.
- A scratch mutation that makes the protocol accept every list fails the new tests (reported by log id).
- Build: `verify -Ppre-commit`, `verify` of `pm-workflow`, `verify -Pcoverage`, and `verify -Pintegration-tests -pl pm-core -am` is unchanged by this plan. Use per-module runs with log ids if a full run exceeds the host's bound.
- Sonar: the open-finding read at the head is `0` or each key is selected for dismissal by the operator (`unknown` for a failed read, never `0`).

## Standing Constraints

- Read every cited line at the head first and drop what a merge settled (name the commit).
- Work to the Done-when. Ask only the questions this spec marks ask-at-refine, once.
- Every carried item gets an outcome: adopted, split off to a NAMED plan, or dropped with its reason.
- No behaviour change beyond the check. No model-facing content. Pre-1.0: no shim, no overload that skips the check.
- The refusal is asserted by reason, not by type. A literal stays only where it is the contract (the detail texts, the wire name).
- No dependency or plugin is added. No more than 100 files per PR.
- Sonar dismissal only on the operator's selection, through `sonar_rest transition`.

## Claim Labels

- OBSERVED: `ConsultationService.validateSubmit(String askingTaskId, List<String> consultationIds)` is at `consult/ConsultationService.java:409`. It reads the queue with `queue.read`, takes the delivered ids as those with `deliveredAt() != null`, and refuses a missing id first and an unknown id second.
- OBSERVED: `TaskProtocol.submit(TaskCall, List<RoleSkill>, Instant)` is at `task/TaskProtocol.java:386` and `submit(TaskCall, Instant)` at `:413`. Neither reads `consultation_ids`. `TaskCall` is `(queueTaskId, generationId)` only.
- OBSERVED: `CallResult.Refused(TaskRefusal, QueueTaskRecord, List<String> missingSkills)` has no reason member. `TaskRefusal.INVALID_LINK_ARGS` exists (`TaskRefusal.java:30`).
- OBSERVED: `ScenarioReplayRunner.java:442` calls `fixtures.consultations.validateSubmit(...)` before `worker.submit(at)`.
- OBSERVED: `workflow-consult.adoc:114` and `:122` say no production submit path calls the validator. Neither `TaskProtocol` nor `ConsultationService` appears in plan-marshall-mcp origin/main 02639c14, so the wiring belongs in this repository.
- OBSERVED (drift): the PLAN-32 landing called the wiring "server-assembly work". At 3ba79c4f the protocol, not the assembly, owns the submit, so this plan fixes it here.
- HYPOTHESIS: PM-WATCH-DISP-78's first half (arguments outside the link's closed schema refused `invalid_link_args`) is also unbuilt. `TaskCall` carries no arguments. Confirm at `TaskProtocol.java` § `submit` and the watch entry; adopt it or split it off to a named plan.
- HYPOTHESIS: workstream WS-06 (the task protocol and the consultation of the worker); the orchestrator confirms against the epic's workstream list.
- Verify-first clause: re-read `TaskProtocol.submit` at the head. A caller added since (the assembly) changes the signature cost.

## Expected Surface

24 existing files plus 1 new ADR.

- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/task/TaskProtocol.java`, `pm-workflow/src/main/java/de/planmarshall/workflow/task/CallResult.java`, `pm-workflow/src/main/java/de/planmarshall/workflow/task/TaskRefusal.java`, `pm-workflow/src/main/java/de/planmarshall/workflow/task/package-info.java`, `pm-workflow/src/main/java/de/planmarshall/workflow/consult/ConsultationService.java`, `pm-workflow/src/main/java/de/planmarshall/workflow/consult/SubmitValidation.java`, `pm-workflow/src/main/java/de/planmarshall/workflow/consult/package-info.java`
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/task/TaskProtocolSubmitTest.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/task/TaskProtocolRefusalTest.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/task/TaskFixtures.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/consult/ConsultationServiceTest.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/consult/ConsultationFixtures.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ScenarioReplayRunner.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ScenarioReplayRunnerTest.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ScriptedProtocolWorker.java`
- OBSERVED: `pm-workflow/doc/developer/workflow-consult.adoc`, `pm-workflow/doc/developer/workflow-task.adoc`, `pm-workflow/doc/developer/workflow-scenario.adoc`, `pm-workflow/doc/adr/workflow-consult/005-the-consultation-id-comes-from-a-seam.adoc`, `pm-workflow/doc/adr/workflow-scenario/009-task-protocol-under-the-replay.adoc`, `doc/specification/model-work/02-task-protocol.adoc`, `doc/specification/model-work/05-consultation.adoc`, `doc/implementation-watch/model-work.adoc`, `doc/Traceability.adoc`
- HYPOTHESIS: a new ADR in `pm-workflow/doc/adr/workflow-task/`.

## Dependencies and Sequencing

- Depends on: none. It needs no #567 code.
- Overlaps with: the staged plans that touch `TaskProtocol` or the scenario harness; the gate decides at emit.
- Adjacent to: `pm-runtime` (untouched). The assembly has no consumer of `TaskProtocol` at 02639c14, so no companion PR is expected; re-check at the outline.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-202-submit-validates-the-consultation-ids.md
```

## Write-Boundary

The plan touches only its own repository source and tests. It creates and edits NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message. See `persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

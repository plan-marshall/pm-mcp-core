# PLAN-V2-202: The consultation references the worker declares are removed: the server already knows which consultations it delivered

epic: pm-mcp-core-m2-m4-v2
workstream: WS-06
origin: PLAN-V2-162 (#567) review (CodeRabbit finding on `05-consultation`), landing revision 25 and 27, section 9; PM-WATCH-DISP-78; **superseded in direction by `doc-review-2026-11-25-002.md` section 1 (merged as core #617, `515c1fa3`)**: this plan was staged as "the submit validates the `consultation_ids`" and is now "the `consultation_ids` are removed".
lane: runbook

> Staged plan spec, one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off. SELF-SUFFICIENT together with the standing rules. The file keeps its name (the queue row and the hand-off command name it). It needs no #567 code and may start at once on main.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`). They bind this plan exactly as if they were written below. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R05, R06, R11, R13, R14, R15, R19, R20 (the orchestrator's candidates; an id the lane finds not applicable it says so). R13 binds because a refusal and a scenario grammar shrink.

## Objective

The merged specification (Model Work 05, "The Result Refers to the Consultation"; watch DISP-78) no longer lets the worker name the consultations of its submit: the server records the consultations it delivered with the accepted final submission, so a list the worker declares can only be wrong. The engine still has the old mechanism: `ConsultationService.validateSubmit` compares a submit's `consultation_ids` with the delivered ones, `SubmitValidation` carries its answer, and the scenario harness calls it. At the head no production caller exists (`TaskProtocol.submit` takes no `consultation_ids`; the PLAN-V2-162 landing found the same). The earlier direction of this plan, to wire the check into `TaskProtocol.submit`, is withdrawn by the operator's merged decision. This plan removes the mechanism, the tests that pin it and the sentences that describe it, and proves the accepted result carries the delivered consultations without the worker's help.

## Deliverables

1. **Failing tests first: the record of the delivered consultations does not depend on the worker.** In `TaskProtocolSubmitTest` or a new neighbour (not in `ConsultationServiceTest`): a task with one delivered consultation closes `submitted` with a submit that names no consultation, and the closed `QueueTaskRecord` keeps the consultation with its id and role (`QueueTaskRecord.consultations`, schema `queue-task.schema.json:98-117`); a task with no consultation closes `submitted` with an empty record list; a consultation answered between the worker's last read and its submit is part of the record (the server's list is the delivered set at the close, not the worker's last read). **Ask-at-refine (put it in the outline's question once, with the recommendation):** whether `consultations[]` on the asking task already is the record "with the result", or the accepted result needs its own member; recommended: it already is, no new member (the lane read it in the PLAN-V2-162 review and the schema carries id and role across the close; confirm at `TaskProtocol` and the codec).
   - Skills: pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: module-tester
2. **Remove the mechanism.** `ConsultationService.java:70-72` (the sentence on the submit's list) and `:401-433` (`validateSubmit`); `SubmitValidation.java` (the whole type); `consult/package-info.java:14` (the validation clause). `ConsultationRefusal.INVALID_LINK_ARGS` STAYS (`:163`, `:242`): another refusal uses it, check with a grep at the head before leaving it. Tests: `ConsultationServiceTest:62` (class Javadoc), `:320-321`, `:326-356` are deleted with a negative control only where the type is gone (nothing to test of a removed type: the compile is the control); the scenario harness: `ScenarioReplayRunner:37`, `:430-447` (the validator call before `worker.submit`), `:497-504` (the `consultation_ids` argument read), `ScenarioScript:38` (the grammar's `consultation_ids` member), `ScenarioReplayRunnerTest:1067-1118` (scenario 14, the `consultation_ids` case is rewritten to submit with no list and to assert the recorded consultation), `ScenarioScriptTest:250, 261`. Pre-1.0: no shim, no ignored legacy argument: a scenario script naming `consultation_ids` is refused by the grammar and the refusal names the member.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core
   - Persona: implementer
   - Test order: deliverable 1 first, red at the head where the record assertion needs the server's list; then the removal; one negative control: a script with `consultation_ids` is refused as an unknown member.
3. **Documents.** `pm-workflow/doc/developer/workflow-consult.adoc:6, 23, 60, 107-112` and `workflow-scenario.adoc:101, 143` drop the validator; the task guide states that the accepted submit records the delivered consultations. ADRs `workflow-consult/005:19` and `workflow-scenario/009:51` are HISTORY: leave their text, add a stamp naming this plan only if the ADR directory's convention asks for one. Update PM-WATCH-DISP-78 in `doc/implementation-watch/model-work.adoc` (the half "arguments outside the link's closed schema refused `invalid_link_args`" is a separate point: read `TaskCall` and the watch entry; adopt it or name the plan it belongs to) and `doc/Traceability.adoc`. Whether an ADR is needed for the record's place is decided at the outline (a design decision only if the answer to the ask above is "a new member").
   - Skills: pm-documents:ref-asciidoc, pm-dev-java:javadoc
   - Persona: documenter

**Done when:**
- Deliverables 1 to 3.
- The record test of deliverable 1 passes at the head and still passes after the removal (it must not depend on `validateSubmit`); the scenario suite (`ScenarioReplayRunnerTest`, the consult scenario) passes with no validator call and no `consultation_ids` in any script.
- `ConsultationService` has no method named `validateSubmit`, `SubmitValidation` is gone, and a script naming `consultation_ids` is refused naming the member; fails at 90505ad2.
- A scratch mutation that makes the server's record drop the delivered consultation fails the new tests (reported by log id).
- Build: `verify -Ppre-commit` (rewrites reviewed and committed), `verify` of `pm-workflow` (and `pm-core` if a schema member is added), `verify -Pcoverage`. Use per-module runs with log ids if a full run exceeds the host's bound.
- Sonar: the open-finding read at the head is `0` or each key is selected for dismissal by the operator (`unknown` for a failed read, never `0`).
- **Companion (outside this repository, state it in the landing message):** the stub of the assembly, `plan-marshall-mcp` `pm-e2e/src/test/java/de/planmarshall/e2e/question/stub/QuestionStub.java:1881, 2165-2167` (`consultation_ids` in the submit grammar and its refusal), pinned by `QuestionStubTest.java:576-590` and the fixtures `QuestionDriver.java:2415`, `QuestionStubTest.java:1263`. That repository has no epic; this plan changes the stub in the same step as a companion pull request there, or names in the landing message why not.

## Standing Constraints

- Read every cited line at the head first and drop what a merge settled (name the commit). The lines are those of the doc review on `3ba79c4f`; the head is `90505ad2`.
- Work to the Done-when. Ask only the one question this spec marks ask-at-refine, once.
- Every carried item gets an outcome: adopted, split off to a NAMED plan, or dropped with its reason.
- No behaviour change beyond the removal. No model-facing content. Pre-1.0: no shim, no overload that ignores a legacy argument.
- The refusal of a script member is asserted by reason, not by type. A literal stays only where it is the contract (the wire name `consultation_ids` in the negative control).
- No dependency or plugin is added. No more than 100 files per PR.
- Sonar dismissal only on the operator's selection, through `sonar_rest transition`.

## Claim Labels

- OBSERVED: `ConsultationService.validateSubmit(String askingTaskId, List<String> consultationIds)` at `consult/ConsultationService.java:409` (Javadoc `:402`; the class sentence `:70`), with `SubmitValidation.Refused/Accepted` returns (`:414-432`); `consult/package-info.java:14` names the validation.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: ConsultationService.java:409 validateSubmit(String, List<String>), Javadoc 402, class sentence 70, returns 414-432 (Refused/Accepted); consult/package-info.java:14 names the validation.
- OBSERVED: the only callers at the head are `ScenarioReplayRunner.java:442` and `ConsultationServiceTest:321-352`; `TaskProtocol.submit` takes no `consultation_ids` (re-read by the PLAN-V2-162 lane and by the orchestrator's grep at 90505ad2).
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: git grep validateSubmit: callers only ScenarioReplayRunner.java:442 and ConsultationServiceTest:321-352; TaskProtocol.java:386,413 submit(...) take no consultation_ids (no hit in task/). Orchestrator grep at 90505ad2 itself not re-run.
- OBSERVED: `queue-task.schema.json:98` carries `consultations` on the asking task (`required` list at `:6`); the PLAN-V2-162 landing states `QueueTaskRecord.consultations` keeps each delivered consultation with its id and role across the close: a lead to confirm at `QueueTaskRecord` and its codec.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: queue-task.schema.json:98 consultations (items with consultation_id, role), required list at :6; QueueTaskRecord.java:73,829 holds consultations, QueueTaskRecordCodec.java:202,276-330 writes/reads them. PLAN-V2-162 landing text not opened (.plan).
- OBSERVED (doc review, `3ba79c4f`): `ScenarioScript:38`, `ScenarioReplayRunner:497-504`, `ScenarioReplayRunnerTest:1067-1118`, `ScenarioScriptTest:250, 261`; docs `workflow-consult.adoc:6, 23, 60, 107-112`, `workflow-scenario.adoc:101, 143`.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: ScenarioScript.java:38, ScenarioReplayRunner.java:497-504 (consultationIds), ScenarioReplayRunnerTest:1067-1118 (ACCEPTED scenario), ScenarioScriptTest:250,261; workflow-consult.adoc:6,23,60,109(107-112); workflow-scenario.adoc:101,143 all carry consultation_ids/validateSubmit.
- HYPOTHESIS: `ConsultationRefusal.INVALID_LINK_ARGS` has another producer than `validateSubmit` (the review says it stays at `:163`, `:242`); confirm by grep at the outline before leaving it.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: ConsultationRefusal.INVALID_LINK_ARGS is produced at ConsultationService.java:163 (question) and :242 (answer), not by validateSubmit (returns SubmitValidation.Refused); TaskProtocol.java:772 uses the separate TaskRefusal enum. Producer other than validateSubmit exists.
- HYPOTHESIS: PM-WATCH-DISP-78's first half (arguments outside the link's closed schema refused `invalid_link_args`) is unbuilt: `TaskCall` carries no arguments. Adopt it or split it off to a named plan.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: TaskCall.java:25 is record TaskCall(String queueTaskId, String generationId): no arguments; TaskProtocol.submit(TaskCall,..) :386/:413 no argument check; model-work.adoc:126-139 PM-WATCH-DISP-78 Guard still lists the argument-outside-schema refusal. Half is unbuilt.
- HYPOTHESIS: workstream WS-06 (the task protocol and the consultation of the worker); the orchestrator confirms against the epic's workstream list.
  - verdict: unverifiable | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: WS-06 is a workstream id in the epic's workstream list under .plan/, which this leaf may not open; nothing in the checked-in code or specs names it.
- Verify-first clause: re-read `ConsultationService` and `TaskProtocol` at the head. A caller added since (the assembly) turns the removal into a signature change for it: stop and name it.
  - verdict: unverifiable | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: In this repo a re-read finds no new caller (only ScenarioReplayRunner:442 and ConsultationServiceTest) and TaskProtocol.submit has no consultation_ids; an assembly (plan-marshall-mcp) caller lies outside the checkout and cannot be read.

## Expected Surface

About 22 existing files, no new file.

- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/consult/ConsultationService.java`, `pm-workflow/src/main/java/de/planmarshall/workflow/consult/SubmitValidation.java`, `pm-workflow/src/main/java/de/planmarshall/workflow/consult/package-info.java`
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/task/TaskProtocol.java`, `pm-workflow/src/main/java/de/planmarshall/workflow/task/CallResult.java`, `pm-workflow/src/main/java/de/planmarshall/workflow/task/TaskRefusal.java` (only if the answer to the ask is "a new member")
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/task/TaskProtocolSubmitTest.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/task/TaskFixtures.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/consult/ConsultationServiceTest.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/consult/ConsultationFixtures.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ScenarioReplayRunner.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ScenarioReplayRunnerTest.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ScenarioScript.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ScenarioScriptTest.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ScriptedProtocolWorker.java`
- OBSERVED: `pm-workflow/doc/developer/workflow-consult.adoc`, `pm-workflow/doc/developer/workflow-task.adoc`, `pm-workflow/doc/developer/workflow-scenario.adoc`, `doc/implementation-watch/model-work.adoc`, `doc/Traceability.adoc`
- HYPOTHESIS: `pm-core/src/main/resources/de/planmarshall/core/queue/queue-task.schema.json` (only if the answer to the ask is "a new member")

Not surface (R19): `pm-workflow/doc/adr/workflow-consult/005-the-consultation-id-comes-from-a-seam.adoc` and `pm-workflow/doc/adr/workflow-scenario/009-task-protocol-under-the-replay.adoc` (history), `pm-workflow/src/test/java/de/planmarshall/workflow/task/TaskProtocolRefusalTest.java`

## Dependencies and Sequencing

- Depends on: none. It needs no #567 code.
- Overlaps with: PLAN-V2-161 (items 34 and 38 touch `ScenarioReplayRunner`, `AdditionalRulesTest`, `WorkflowParserTest`: this plan and PLAN-V2-191 go first), PLAN-V2-191 (deliverable 4 edits `WorkflowParserTest` and `AdditionalRulesTest`, not the scenario harness); other staged plans that touch `TaskProtocol` or the scenario harness: the gate decides at emit.
- Adjacent to: `pm-runtime` (untouched) and the assembly `plan-marshall-mcp` (companion pull request for the stub, see Done when).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-202-submit-validates-the-consultation-ids.md
```

## Write-Boundary

The plan touches only its own repository source and tests. It creates and edits NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message. See `persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

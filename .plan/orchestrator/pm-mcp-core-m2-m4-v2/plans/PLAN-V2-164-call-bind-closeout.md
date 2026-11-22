# PLAN-V2-164: The closeout of the call-time bind: the free-parameter validation, the tests the guides cite, Rule 6 and the small fixes of #522

epic: pm-mcp-core-m2-m4-v2
workstream: WS-01
origin: the three-layer analysis of the landing of PLAN-V2-157 (`landings/PLAN-V2-157.md`, 2026-11-20, PR #522 merged as `d65dbc66`) and the operator's rule that leftovers go into a staged plan
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-164-call-bind-closeout.md` and is queued as one row file, `queue/PLAN-V2-164.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r7`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R15, R17, R18, R19, R20. (R14 because the plan changes `core.store` — the call frame and the slots of `WorkflowStatus` — and its checklist items 4 and 7 are the point of Deliverable 1; R09 because the path check uses `PathConfinement`, a refused path is a value; R10 because `WorkflowStatusSchemaParityTest` and `UnitFrontmatterReaderTest` read specification blocks; R12 only if the trace matrix shows a message; R16 does not apply: no `pm-runtime`.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-workflow/doc/developer/workflow-input.adoc` (new, package `workflow.input`), and the existing `pm-workflow/doc/developer/workflow-dsl.adoc`, `workflow-ast.adoc`, `workflow-engine.adoc`, `workflow-verify.adoc`, `pm-core/doc/developer/core-store.adoc`, `pm-providers/pm-provider-git/doc/developer/provider-git.adoc`; ADR directories: `pm-workflow/doc/adr/workflow-input/` (new), `pm-workflow/doc/adr/workflow-dsl/`, `…/workflow-engine/`, `…/workflow-verify/`, `pm-core/doc/adr/core-store/`, `pm-providers/pm-provider-git/doc/adr/provider-git/`; layout: workstream WS-01. The module `README.adoc` of `pm-workflow` gets one line for the new package `workflow.input` (R06).
- R12 — log message holder: `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`); `CoreLogMessages` for `pm-core`; none expected.

## Objective

PLAN-V2-157 (#522) built the call-time bind and met five of its six deliverables. Its landing analysis found what is left: the free-parameter validation of a call that no staged plan owns, documentation and watch items that cite tests that do not exist, tests the bind still lacks, a Rule 6 that does not see the consumption of a slot, a measurement the lane argued instead of took, a reserved-word set that drifts, a fail-open `default` arm in the lesson store that the merge of PLAN-V2-156 brought in, and three behaviours decided without asking. None changes a stored datum; the bind itself is sound and every existing call still verifies. This plan closes the family.

## Deliverables

All file and line references are those of `d65dbc66` (the merge of #522) and are re-read at the head of `main` before editing.

1. **The free-parameter runtime validation of a call (carried item 1 of PLAN-V2-15, split off by #522 to no plan).** A call argument the callee does not declare is refused `undeclared_param` and a required one that is missing `missing_param`, at the bind, with the reasons in the same `precondition_unmet` condition as `input_refused` and `input_unbound`; the `ParameterBinder`'s default refusal for any free parameter is decided with the operator (R07): the shape of the binder is the assembly's contract (PLAN-V2-25 keeps it), so say in the ADR which side owns what. Tests per refusal and a matched control.

2. **The tests the documentation cites exist, and the gaps of #522's tests are closed.** Either the codec refusal tests (a malformed `command_id` and `correlation_id`, an unknown provenance, a slot without `value` or `filled_at`, an invalid slot) or the citations in `core-store.adoc` (`:182`, `:206`), the watch item DSL-41 and Traceability are removed: never a cited test that does not exist. Add the missing cases: the `HeaderJson` `SlotRef` read back (and a plain enum value that starts with `slot.` must not read as one), a `SubWorkflowInvariants` case carrying `fills`, `PathConfinement` rejection (a symlink and a path outside the worktree), a null `bindingContext`, a fallback absent with a default, a required input omitted, a refusal on the second input of a call that leaves the slot filled, a slot bound to two inputs of one call, and the per-weakened-type cases the watch item DSL-43 names as owed.

3. **Rule 6, the dominance measurement and the reserved words.** Measure the cost of the dominance check over `UnitGraph` (the clause D2 of PLAN-V2-157 left argued, not measured) and record the figure in ADR `workflow-verify/039`; decide whether Rule 6 models the consumption of a slot (a slot filled once and read by two calls, or bound to two inputs of one call, verifies today and is refused `input_unbound` at run time); the `RESERVED_KEYWORDS` of `WorkflowValidator` (`:143`, used at `:1025`, the spec called it unused) differs from `WorkflowParser`'s by `decide`, `phase` and `inherit`: move one set where both read it, or record why two.

4. **The small fixes of #522.** Remove the fail-open `default -> false` in `LessonStore.isUntrusted` (`:815`; the PR body called it a reindent), the duplicate `import org.jspecify.annotations.Nullable;` in `JGitOperations`, the dead `InputRefusal` built and discarded in `invalidLinkArgs`, the misleading `input_unbound` a required input gets when `bindingContext` is null (the reason says the port is missing), the call-site matrix column of `06-reusable-sub-workflows.adoc` the spec asked for (a paragraph was written), and the counts of the landing and the PR body that disagree (the analysis leaf found 14 `InputBindingTest` cases against 15 claimed).

5. **Three behaviours #522 decided that the specification does not state (R07: ask at refine).** The refusal of the glob characters `* ? [` in a path (the hypothesis above), a tab refused in a commit message body, and a space at the edge of a path segment admitted (`.git ` passes the `.git` rule on NTFS): put each to the operator with its recommendation, state the answer in the specification and one ADR (R06), and test it.

**Done when:** Deliverables 1 to 5; a call argument the callee does not declare and a required one that is missing are refused at the bind with the stated reasons, no document cites a test that does not exist, and every existing `call` site still verifies. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of `pm-core`, `pm-workflow` and `pm-provider-git` (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module runs); the links check (R17) reports `BROKEN LINKS 0` and `INLINE SCHEMA BLOCKS 0` run from the main checkout.

## Standing Constraints

- **Read every cited line at the head first.** The lines and counts of a carried item are those of the landing analysis it names, read on an older head; re-read each at `origin/main` before editing and drop what a merge since then settled (name the commit).
- **Work to the Done-when.** Do not stop to ask the operator which part to continue with; ask only a decision the spec marks ask-at-refine (R07) or one a carried item says needs the operator, and put it in the outline's question, not in a stop.
- **Every carried item gets an outcome** in the landing message and the pull request: adopted into a deliverable, split off to a NAMED existing plan, or dropped, each with its reason; "not in this run's surface" is no reason.
- **No behaviour change beyond the corrections the items state.** A change a carried item does not ask for is ask-at-refine (R07).
- **Where the project analyses its pull requests with Sonar** (the plan lifecycle's Sonar step reports it configured; where it cannot scan, read the pull request over plain HTTP: `pullRequest=N`, never `branch=<feature>`): an open Sonar issue is fixed in a pushed commit, or dismissed with a stated reason; the quality gate is green before the pull request is reported ready.
- **No dependency or plugin is added.**
- **No more than 100 files per pull request** (the watch of the epic ledger; CodeRabbit refuses a larger one): cut the plan into pull requests by deliverable or by module before the first one is opened.
- **Tests that can fail.** A new test fails on the code it guards (a failing run or a mutation, reported by log id); a test of a refusal asserts the reason, not only the type (CLAUDE.md, R20).

## Claim Labels

- OBSERVED: at `d65dbc66` `LessonStore.java:815` has a `default -> false;` arm in the `isUntrusted` switch although the switch covers all seven `LessonResolution` variants, so a new variant would be trusted silently.
- OBSERVED: `WorkflowStatusCodecTest` at `d65dbc66` has `slotRoundTrip` (`:169`) and the golden documents but no test that refuses a malformed `command_id` or `correlation_id`, an unknown provenance or a slot without `value` or `filled_at`, while `core-store.adoc` (`:182`, `:206`) and the watch item DSL-41 cite such a test.
- OBSERVED: `InputBindingContext` has an interface in `pm-workflow` and no implementation and no caller outside tests in any `src/main`; with a null context every `path` or `list<path>` input is refused and `input_fallback` is skipped.
- OBSERVED: the free-parameter runtime validation (`undeclared_param`, `missing_param`, the default refusal of `ParameterBinder`) is named by no staged plan: PLAN-V2-25's spec says "the binder keeps its shape" (`PLAN-V2-25-command-record-and-dispatch.md:34`, `:68`) and the landing of PLAN-V2-157 names PLAN-V2-25 in one place and PLAN-V2-20 in another.
- HYPOTHESIS: JGit's `addFilepattern` and `setOnly` do no glob expansion, so the refusal of `* ? [` in `PathRules` and in the provider (CodeRabbit, not in the spec's Decision 3) makes a file name such as `pages/[id].tsx` unbindable for nothing — from memory, not run; Deliverable 5 settles it with a test.
- HYPOTHESIS: Rule 6 does not model the consumption of a slot, so a slot filled once and read by two calls, or bound to two inputs of one call, verifies and is refused at run time with `input_unbound` — read by the analysis leaf, not run.

## Expected Surface

Source, tests, specification and documentation (specific files, R19):

- OBSERVED (the free-parameter validation and the bind, Deliverable 1): `pm-workflow/src/main/java/de/planmarshall/workflow/engine/WorkflowEngine.java`, `pm-workflow/src/main/java/de/planmarshall/workflow/engine/InputBindingContext.java`, `pm-workflow/src/main/java/de/planmarshall/workflow/engine/CallStack.java`, `pm-workflow/src/main/java/de/planmarshall/workflow/ast/InputDecl.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/engine/InputBindingTest.java`.
- OBSERVED (tests and citations, Deliverable 2): `pm-core/src/test/java/de/planmarshall/core/store/WorkflowStatusCodecTest.java`, `pm-core/src/test/java/de/planmarshall/core/store/WorkflowStatusSchemaParityTest.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/plan/InstancePrunerTest.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/verify/DefaultPlanUnitTest.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/verify/CompiledInvariantsTest.java`, `pm-workflow/src/main/java/de/planmarshall/workflow/plan/HeaderJson.java`, `pm-workflow/src/main/java/de/planmarshall/workflow/verify/SubWorkflowInvariants.java`, `pm-core/doc/developer/core-store.adoc`, `doc/implementation-watch/workflow-dsl.adoc`, `doc/Traceability.adoc`.
- OBSERVED (Rule 6, the reserved words, Deliverable 3): `pm-workflow/src/main/java/de/planmarshall/workflow/verify/WorkflowValidator.java`, `pm-workflow/src/main/java/de/planmarshall/workflow/dsl/WorkflowParser.java`, `pm-workflow/src/main/java/de/planmarshall/workflow/verify/CompiledInvariants.java`; the ADR directory `pm-workflow/doc/adr/workflow-verify/` (039 and the next free number, R11).
- OBSERVED (small fixes, Deliverable 4): `pm-core/src/main/java/de/planmarshall/core/store/LessonStore.java`, `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/JGitOperations.java`, `pm-workflow/src/main/java/de/planmarshall/workflow/input/InputValidator.java`, `pm-workflow/src/main/java/de/planmarshall/workflow/input/PathRules.java`, `doc/specification/workflow-dsl/06-reusable-sub-workflows.adoc`.

- OBSERVED (carried item 74, added 2026-11-21; read by the analysis leaf at `aab5ccdb`): `doc/specification/state-catalogue/01-conventions-generic-conditions.adoc`, `pm-core/doc/developer/core-store.adoc`, `doc/implementation-watch/workflow-dsl.adoc`.

- OBSERVED (carried item 77, added 2026-11-21; read by the analysis leaf at `aaa7c002`): `pm-core/src/main/java/de/planmarshall/core/spi/Command.java`, `pm-core/src/main/java/de/planmarshall/core/spi/CommandSettlement.java`, `pm-workflow/src/main/java/de/planmarshall/workflow/engine/EngineStep.java`, `pm-workflow/src/main/java/de/planmarshall/workflow/engine/WorkflowEngine.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/engine/CommandDispatchTest.java`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-157 (shipped, #522, `landings/PLAN-V2-157.md`: the plan this one closes), PLAN-V2-15 (shipped, #513), PLAN-V2-156 (shipped, #516), PLAN-V2-83.
- Overlaps with (class level): PLAN-V2-19, 20 and 25 (the engine track: `WorkflowEngine`, `CallStack`, the call frame), PLAN-V2-161 (the engine's composition entry points) and the running PLAN-V2-158 and 159 on `LessonStore` (one line): the gate decides at emit.
- Not here: the assembly's `InputBindingContext` implementation, the `TasksSettled` producer, the production `git-commit-and-push.pmwf` and `StateCatalogueDriftTest` (`plan-marshall-mcp`), the primitives of PLAN-V2-20.

## Carried Open Items

74. **What the merge of PLAN-V2-19 (#531 `1ada535b`) made true or stale in this spec** (`landings/PLAN-V2-19.md`, carried 2026-11-21; merged as #531 `1ada535b` (the plan's own #530 was closed); read by the analysis leaf at `aab5ccdb`; no blocker). Binding over any contradicting older text: (a) PLAN-V2-19 is merged: the engine track is now 164, then 25 (19 leaves the overlap list and the sequencing note). (b) The engine hunks of PLAN-V2-19 do not touch `call`, `CallStack`, `invalidLinkArgs` or `storeSlot` (hunks at `advance`, chain AutoStep, `runPrimitive`, `Pending`, `leave`, `returnFromFrame`): the textual conflict with Deliverable 1 is low; `core-store.adoc` (inserted at `:233`), watch DSL-44 (`:609`) and Traceability were edited by 19, so expect hunk conflicts in Deliverable 2. (c) Deliverable 1: the `precondition_unmet` row of `state-catalogue/01-conventions-generic-conditions.adoc` (one long line, `:160-161`) now carries a "Second origin" sentence mid-row and `facts.reason` has two vocabularies (cause names such as `input_unbound`, and a primitive's reason wire name): adding `undeclared_param` and `missing_param` must edit the same line and keep that sentence. (d) Deliverable 4: `LessonStore.java:815` (`default -> false`) is still there at `aab5ccdb`; PLAN-V2-159's landing found ADR 079 `:79-81` false about it (PLAN-V2-163 item 64 carries the ADR side): the plan that removes the arm and the plan that corrects the ADR are the same change, decide it once. (e) Next free ADR numbers: workflow-verify 041, workflow-engine 023, core-store 081, core-spi 014 (R11). **Stale since 2026-11-21 (PLAN-V2-25 merged as #542 `aaa7c002`, before this plan):** workflow-engine is at 028 and core-spi at 019 (read both at the head); `workflow-verify` 041 is also taken by the open pull request #545 of PLAN-V2-20 (its ADR 041 is the commit-message kind), so take the next free number at the head. The "engine track is 164, then 25" of (a) is reversed: 25 merged first and this plan edits the engine as #542 left it (the `Run` constructors, the `advance` overloads, the chain's call-refusal raise at `WorkflowEngine.java:1279`, adjacent to Deliverable 1).

77. **What the merge of PLAN-V2-25 (#542 `aaa7c002`) leaves in the engine, three untested paths and a leak guard** (`landings/PLAN-V2-25.md`, carried 2026-11-21; read by the analysis leaf at `aaa7c002`; no blocker). The tests and the engine paths are here because this plan edits `WorkflowEngine` next: (a) **`spawnedJobId` mapping is asserted by no test** (`CommandDispatchTest`): on the raised exit `settle` carries `result.spawnedJobId()` (`WorkflowEngine.java:1575`) although the engine binds no job (only the routed exit sets it, `:1577`); decide the raised-exit semantics, then pin both exits. (b) **The transactional overloads of `advance` have no provenance test** (`WorkflowEngine.java:612`, `:629`, `:647`, `:666`; `drive()` uses the non-transactional entry points only): add the case per overload. (c) **The null-provenance refusal (`:1548`) has no test**; `composeOperatorEvent` (`:416`) and `restingWait` (`:680`) create a `Run` with a null provenance by the operator's decision (Q1 A, ADR core-spi/015): say so in the test. (d) **Lombok `@Builder` on `Command:38`, `CommandSettlement:48` and the three `EngineStep` variants** generates a builder `toString` that would print an input or a fact value if a builder were ever printed; `lombok.config` sets no `toString` option; the premise is unchecked (not compiled): check it and pin it with a leak test or switch the option off. (e) An unmappable `conditionKind` now throws `IllegalArgumentException` from the `CommandSettlement` constructor (`:1571`) before `ConditionRaised.raise`: the message and the site differ from what item 71(b) of PLAN-V2-161 describes; settle both in one place. The statements the merge made false in the specifications and guides are item 78 of PLAN-V2-161.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-164-call-bind-closeout.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

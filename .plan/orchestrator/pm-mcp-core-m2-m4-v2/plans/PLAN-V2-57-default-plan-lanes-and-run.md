# PLAN-V2-57: The fixture default plan models the land and adopt lanes, and the engine runs it end to end

epic: pm-mcp-core-m2-m4-v2
workstream: WS-02
origin: PLAN-120 of the epic pm-mcp-core-m2-m4

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-57-default-plan-lanes-and-run.md` and is queued as one row file, `queue/PLAN-V2-57.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.
> Origin: the open items PLAN-43 (#220) recorded and did not fix (message `-004`, landing `landings/PLAN-43.md`): the fixture corpus does not model the adopt lane or the land lane's "calls no refine, outline or plan phase", and no engine test runs the default plan end to end.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r2`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R15, R17, R18, R19. (R12 applies only if the trace matrix shows a message to add; R14 and R16 do not apply: test scope of `pm-workflow`; R15 because the plan edits fixture units of `pm-workflow` and its verifier tests: item (1), a refusal fixture is a tree the parser accepts; R17 because the plan runs the links check.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-workflow/doc/developer/workflow-unit.adoc`; ADR directory: `pm-workflow/doc/adr/workflow-unit/`; layout: workstream WS-10.
- R12 — log message holder: `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`); none expected.

## Objective

The fixture corpus `unit-fixtures/defaults/` is the shape the product's plan unit and six phase sub-workflows must have, written for the invariant and unit-loader tests of `pm-workflow` (the real units are `plan-marshall-mcp`'s). Two things the specification says of the plan unit are not in it, and no test runs it: (1) the land lane "calls no refine, outline, or plan phase" (the plan unit calls `init`, then `execute` at its entry `land`, then `finalize`), but the fixture reaches the land entry from the exit `land` of the plan phase `phase-4-plan`, so the lane passes refine, outline and plan; (2) the adopt lane "calls `init` and then `finalize` at its entry `adopt`", but the fixture's `init` has no such exit and the plan unit never calls `finalize` at `adopt` although `finalize.pmwf` declares the entry. Model both in the fixtures, prove each lane's path over the call-resolved graph, and add the first engine test that runs the fixture plan unit end to end through its phase sub-workflows for each lane. Serves: PM-WF-16, PM-IMPL-5; the work package `phase-sub-workflows` (the part that shipped as PLAN-43 and was not proven). Where: pm-workflow, test scope and the fixture corpus. The real default units and their drift tests stay `plan-marshall-mcp`'s.

## Deliverables

1. **The land and adopt routes in the fixture units.** `init.pmwf` gains the exits `land` and `adopt` (a terminal state each, reached by `init.ready` through the lane outcomes; `LaneOutcome` of `InvariantFixtures` and the `test.lane` primitive gain `land` and `adopt`), and `plan.pmwf` routes `phases.init`'s `land` to `phases.execute.land` and its `adopt` to a new call state `phases.finalize.adopt` (`call phase-6-finalize entry adopt`, the spec's state `phases.finalize-adopt`), whose exits are routed like those of `phases.finalize`. The exit `land` of `phase-4-plan.pmwf` (and `plan.land-exit` and the `land` outcome of `test.route`, `RouteOutcome.LAND` at `InvariantFixtures.java` line 618) goes, since the plan phase is not on the land lane's path; if the trace matrix shows a fixture test that needs it, the plan keeps it and says so. The compiled invariants and the verifier pass over the changed corpus unchanged in meaning (`CompiledInvariantsTest`, `DefaultUnitLoaderTest`, `WorkflowValidatorTest`), and every fixture unit still parses and verifies on its own.
2. **The proof of each lane's path.** `DefaultPlanUnitTest` gains, for the lanes deep, light, land and adopt, a test that walks `CallGraphExpansion.of(plan, units)` from the start state along the lane's selecting outcomes and asserts the phases the lane enters: deep all six; light all but refine; land `init`, `execute` (entry `land`) and `finalize`, and none of `refine`, `outline`, `plan`; adopt `init` and `finalize` (entry `adopt`) only. The existing assertions that pin the list of calls and the land call (`entersEveryPhaseAtItsEntry`, `landLaneCallsLandWithinExecute`, `lanesAndPhaseIdentities`) change to the new shape. A matched control: a mutation of the fixture that adds a call to `phases.plan` on the land route makes the land test fail.
3. **The end-to-end run.** A new test class `DefaultPlanRunTest` in `workflow.engine` runs the fixture plan unit through `WorkflowEngine` with the other eleven units as the catalog, for each lane (deep, light, land, adopt), from `start` to the terminal `completed`, and for the loop-backs the fixture models (the rework gate back to the authoring entry of outline, the finalize re-entry into execute, the hand-back fallback after an exhausted gate). It asserts, per run, the sequence of entered phase units from the call stack (the phase of a record is the phase unit on the call stack), the entry each call used, the final state and the outcome, and that a call stack of depth five is reached and popped. The primitives the fixture units name (`test.classify`, `test.lane`, `test.gate`, `test.qgate`, `test.route`, `test.merge`, `test.capped`, `test.deliver`, `test.build`, `test.proceed`, …) are executable scripted primitives of a new fixture class (the existing registry of `InvariantFixtures` declares them for verification only). The test asserts **nothing about the phase value of a status that ends `handed_back` or `returned`**: that is `plan-instance-files-and-history` (PLAN-105), which owns the phase of those ends.
4. **The documents.** `workflow-unit.adoc` (the fixture-corpus section: the twelve units, their lane routes, the end-to-end run) and one ADR of `workflow-unit` (the lanes as routes of the plan unit in the fixtures; why the fixture land route no longer passes the plan phase; what the fixtures deliberately leave out of the real plan unit: the asking and routing states `phases.ask` and `phases.route`).

**Done when:** the fixture corpus models the land and adopt routes of the specification; `DefaultPlanUnitTest` proves the phases each of the four lanes enters and that the land lane enters none of refine, outline and plan, with a failing matched control; `DefaultPlanRunTest` runs each lane end to end through the engine and the loop-backs listed, beside every existing test of `workflow.engine`, `workflow.verify` and `workflow.unit` unchanged in meaning; the guide and the ADR are written. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of `pm-workflow` (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and the integration tests of every changed module with tests (none) are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set:**

- Requirements: PM-WF-16; PM-IMPL-5
- Specification: State Catalogue § Plan Unit `phases` (`state-catalogue/18-plan-unit-axes-short-landings.adoc`, the `init` exits `land` and `adopt`); § Land Lane (`state-catalogue/03-plan-phases-4-5-land.adoc`); Phase Workflows § Adopt Lane (`phase-workflows/02-planning-lanes-phases-3-4.adoc`); Store Schemas § Phases as Sub-Workflows and the Phase of a Record (`store-schemas/03-findings-and-review-records.adoc`)
- Watch: none (`doc/implementation-watch/phase-workflows.adoc` PM-WATCH-PHASE-84 is the adopt lane's baseline sync, a runtime matter, not taken)

## Standing Constraints

- **Log messages: none expected.** An INFO, WARN or ERROR message the trace matrix shows goes into the holder of the module (ADR `doc/adr/005-one-log-message-holder-per-module.adoc`) and the inbox message says so.
- **Fixtures are written for the test, never copied from the product** (the build rule of `CLAUDE.md`, module `unit-fixtures/defaults/` header comments): the changed units stay written by hand for these tests.
- **Not taken, by decision of the epic:** the phase value of a `handed_back` or `returned` end (`WorkflowEngine.ended`; PLAN-105), the lane pruning of the composed instance (PLAN-105), the asking and routing states of the real plan unit, the real units and their drift tests (`plan-marshall-mcp` / `default-units-and-drift-tests`, blocked).
- **No production code** unless the end-to-end run shows a defect of the engine: it is then a lead in the inbox message, and a fix is made only if it is one line and needs no file that PLAN-105 declares.

## Claim Labels

- OBSERVED: the fixture land route passes the plan phase: `phase-4-plan.pmwf` declares `exit: land` (line 8), routes `land -> plan.land-exit` (line 44) with the terminal `plan.land-exit` (line 48), and `plan.pmwf` routes `phases.plan` `land -> phases.execute.land` (line 53); `init.pmwf` declares only the exits `deep` and `light` (lines 7 and 8) and its lane primitive `test.lane` has the outcomes `DEEP`, `LIGHT` (`InvariantFixtures.java` line 537) — read at `a8da4104`
- OBSERVED: the adopt entry exists in `finalize.pmwf` (`entry: adopt`, line 7; `entry adopt -> finalize.create-pr`, line 26) and no state of `plan.pmwf` calls it: `grep -n adopt plan.pmwf init.pmwf` finds nothing
- OBSERVED: the specification routes the lanes at `init`: "`init`: `deep` -> refine, `land` -> execute-land, `adopt` -> finalize-adopt" and "the land lane calls `init` and `execute` at its entry `land`, then `finalize`; the adopt lane calls `init` and then `finalize` at its entry `adopt`" — `doc/specification/state-catalogue/18-plan-unit-axes-short-landings.adoc` (the Plan Unit table and the paragraph above it); "The land lane calls no refine, outline, or plan phase" — `state-catalogue/03-plan-phases-4-5-land.adoc` line 186
- OBSERVED: no engine test runs the plan unit through its phases: `DefaultPlanUnitTest` (`pm-workflow/src/test/java/de/planmarshall/workflow/verify/DefaultPlanUnitTest.java`) asserts the shape of the units (lines 55 to 116) and fuzzes them (line 118); `SubWorkflowExecutionTest` runs calls over small units written in the test; the registry of `InvariantFixtures` (line 293) holds `Declared` primitives, which verify and do not execute
- OBSERVED: the engine takes a catalog of callees and a primitive registry: `WorkflowEngine(WorkflowDefinition unit, WorkflowCatalog catalog, WorkflowValidator validator, …, Ports)` (line 213) and `EngineFixtures.engine(unit, callees…)` (line 398)
- OBSERVED: the twelve units of the corpus and their state counts (`grep -c '^state'`): `plan` 17, `finalize` 13, `execute` 13, `remediation-qgate-findings` 14, `outline` 10, `git-commit-and-push` 9, `init` 8, `phase-4-plan` 8, `refine` 6, `land` 5, `adjudicate-decisions` 4, `replan` 4
- OBSERVED: the epic ledger records these as open items of PLAN-43 and assigns the real units, the adopt route and the end-to-end test of the real default plan to `plan-marshall-mcp`; this plan takes the fixture-corpus half, which the ledger left without an owner — `landings/PLAN-43.md` (D3 and D4 rows)
- HYPOTHESIS: the compiled invariants pass over the changed corpus without a change of the invariant classes (the lane fact `adopt` and `land` are already exercised by `CompiledInvariantsTest` lines 281 to 340, `INV-INIT-2 per lane`) — confirm/refute by running `CompiledInvariantsTest`, `DefaultUnitLoaderTest` and `WorkflowValidatorTest` after D1 (verify-at-outline)
- HYPOTHESIS: the engine runs a catalog call of five frames (plan, phase, remediation, …) with executable scripted primitives and the manual clock of `EngineFixtures` without a store of the real queue — confirm/refute at `WorkflowEngine.advance` and `EngineFixtures.ports()` (verify-at-outline)
- HYPOTHESIS: a test pinned to the removed `land` exit of `phase-4-plan` exists in `DefaultUnitLoaderTest`, `ProjectUnitLoaderTest` or `PlanInstanceComposerTest` — confirm/refute with `git grep -n "plan.land-exit\|land-exit"` over `pm-workflow/src/test` (verify-at-outline)
- Verify-first clause: before scoping, list the open pull requests for one that touches `pm-workflow/src/test/resources/unit-fixtures/defaults/` (PLAN-V2-54 names these fixtures in an open question and may edit `init.pmwf` or `outline.pmwf`), re-read the four fixtures and `DefaultPlanUnitTest` at the head, and confirm PLAN-105 is merged or note the order; a refutation of any claim above loops back to re-scope.

## Expected Surface

- OBSERVED: `pm-workflow/src/test/resources/unit-fixtures/defaults/init.pmwf` — the exits `land` and `adopt`
- OBSERVED: `pm-workflow/src/test/resources/unit-fixtures/defaults/plan.pmwf` — the routes of the two lanes; the new call state `phases.finalize.adopt`
- OBSERVED: `pm-workflow/src/test/resources/unit-fixtures/defaults/phase-4-plan.pmwf` — the `land` exit goes (unless a test needs it)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/verify/DefaultPlanUnitTest.java` — the lane proofs and the matched control; the assertions that pin the old shape
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/verify/InvariantFixtures.java` — `LaneOutcome` and the lane primitive's outcomes
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/verify/CompiledInvariantsTest.java` — only if a pinned assertion names the changed shape (PLAN-V2-69 declares this file for `INV-AXIS-1`)
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/unit/DefaultUnitLoaderTest.java` — only if it pins the state set of `phase-4-plan` or `init`
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/engine/DefaultPlanRunTest.java` — new test class (the end-to-end runs)
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/engine/DefaultPlanPrimitives.java` — new fixture class (executable scripted primitives of the fixture corpus)
- OBSERVED: `pm-workflow/doc/developer/workflow-unit.adoc` — developer guide of an existing package (additive: the fixture-corpus section)
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-unit/` — one ADR, the next free number of the directory (007 is the last at `a8da4104`; renumber against `origin/main`, R11)
- Not surface (R19): `doc/plans/README.adoc`, `doc/Traceability.adoc`, `doc/LogMessages.adoc`. The package file `doc/plans/phase-sub-workflows.adoc` is not edited: it stays open on PLAN-105 and PLAN-106, whose pull requests close it; its Done-when item "a test per lane shows the phases it calls and prunes" is met by this plan for the phases a lane calls, by PLAN-105 for the pruning.

## Dependencies and Sequencing


- **Landing note (2026-10-29):** PLAN-105 landed (#230): lane pruning over the composed set is done; the `aborted` phase is not: `handed_back` and `returned` still end with phase `aborted` (`WorkflowEngine.ended` untouched).
- Depends on: nothing. Best merged after PLAN-105 (landed #230), which edits `WorkflowEngine.java` and `workflow/plan/`: the new end-to-end test reads the engine's behaviour and asserts neither history records nor the phase of a `handed_back` or `returned` end, so it does not depend on PLAN-105's changes; whichever merges second merges the base branch before the merge gate.
- Overlaps with: PLAN-46 and PLAN-105 (landed #233 and #230): no shared file by the declared surfaces (PLAN-105 declares `workflow/plan/*`, `WorkflowEngine.java`, `InstanceMermaidRenderer.java`, `store-schemas/02` and `14`, `workflow-dsl/09`; this plan declares none of them). PLAN-V2-69 (staged) declares `CompiledInvariantsTest.java`: this plan lists it as a HYPOTHESIS entry only (1 possible shared file). PLAN-V2-54 (staged) names `unit-fixtures/defaults` in an open question but declares no file of it (possible shared fixtures, not declared). PLAN-V2-13 declares `EngineFixtures.java`: this plan reads it and adds new classes beside it, 0 shared.
- Disjoint from the other plans of this cluster (PLAN-V2-33 (`scenario-task-protocol`), PLAN-V2-49 (`scenario-worker-faults`), PLAN-V2-58 (`verifier-cycle-scale`), PLAN-V2-44 (`positioned-layout`)) by file.
- Package bookkeeping (R01/R05): the package `phase-sub-workflows` stays open (see Expected Surface); no package file of its own.
- Adjacent to: `plan-marshall-mcp` / `default-units-and-drift-tests` (blocked: the real units, the drift tests and the end-to-end test over the real default plan), which this plan's fixtures and test class are the model for.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-57-default-plan-lanes-and-run.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

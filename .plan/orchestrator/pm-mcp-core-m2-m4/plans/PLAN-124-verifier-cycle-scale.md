# PLAN-124: Measure the static verifier's cycle enumeration on units of the size the product will have, and bound it

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-124-verifier-cycle-scale.md` and is queued as one row file, `queue/PLAN-124.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r2`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R15, R17, R18, R19. (R12 applies only if the trace matrix shows a message to add; R14 and R16 do not apply: `pm-workflow` only; R15 because the plan changes `pm-workflow` code: item (1), one owner per refusal, is why no caller of `findSimpleCycles` changes.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-workflow/doc/developer/workflow-verify.adoc`; ADR directory: `pm-workflow/doc/adr/workflow-verify/`; layout: workstream WS-10.
- R12 — log message holder: `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`); none expected.

## Objective

`UnitGraph.findSimpleCycles` enumerates every elementary cycle of a unit's graph by a depth-first search from each state, over the whole graph and not per strongly connected component; the number of simple cycles is exponential in the worst case, and two rules call it for every unit they verify (Rule 11, `WorkflowValidator.checkCycleAwaitedEvent`, and the compiled invariant on capped model loops, `SubWorkflowInvariants.cappedModelLoops`). Nothing measures it: no test builds a unit of the size or shape the default plan units will have (the phase units `phase-5-execute` and `phase-6-finalize` with their loop-backs, remediation and re-planning calls), and the units themselves live in `plan-marshall-mcp`, which will run this code over them in its drift tests. Measure first on generated units of realistic and of worst-case shape; where the measure shows a blow-up, restrict the enumeration to the strongly connected components and keep the result identical. Serves: PM-IMPL-5 (the static verifier); Roadmap Milestone 3 (the verifier). Where: pm-workflow, package `workflow.verify`, one method. The plan changes no verification result and no caller.

## Deliverables

1. **A scale test with a retained oracle.** A new test class `UnitGraphCycleScaleTest` in `workflow.verify` generates units in the DSL text of the fixtures (parsed by the real parser, not hand-built ASTs) in three families: (a) a chain of `n` diamonds closed by one loop-back edge (`2^n` simple cycles, the worst case of the DFS), (b) the shape of a phase with `k` remediation call sites each looping back to one gate (the shape of `phase-5-execute`), (c) a unit of a few hundred states whose cycles are few. The test keeps the present implementation as `legacyFindSimpleCycles` (a private copy in the test class) and asserts for every unit small enough to enumerate that the result of `UnitGraph.findSimpleCycles` equals the legacy result element by element and in the same order (the order decides which cycle a violation names first). For the large units it asserts the time bound `assertTimeoutPreemptively` with a generous limit stated in the test (not a benchmark) and the number of cycles.
2. **The measurement, recorded.** The numbers (units, states, edges, cycles, time on the host) go into one ADR of `workflow-verify` and into the pull request body, whether or not D3 is needed.
3. **Restrict the enumeration to the strongly connected components (only if D1 shows the present implementation unbounded or over the limit of D1 on family (b) or (c); otherwise this deliverable is not built and the ADR says the measure showed none).** `findSimpleCycles` computes the strongly connected components of the unit graph once, drops every state outside a component with a cycle, and runs the existing depth-first search inside each component with the same state order and the same successor order, so the returned list is identical to the legacy list. The signature, the return type (`List<List<String>>`, each cycle ending in its start state) and every caller stay as they are. A family-(a) unit with `2^n` cycles stays exponential by its nature; for it the test records the count and the ADR records that the unit is refused by no rule and that the enumeration is the cost of the rule (the decision to cap it is the operator's and is not taken here).
4. **The documents.** `workflow-verify.adoc` § Rule 11: Cycles (and the `UnitGraph` entry of the type list) states the cost of the enumeration, the family shapes measured and the limit; one ADR (the next free number of `workflow-verify`) records the measure and the choice of D3 or its absence.

**Done when:** `UnitGraphCycleScaleTest` exists and passes beside every existing test of `workflow.verify` unchanged (`AdditionalRulesTest`, `CompiledInvariantsTest`, `WorkflowValidatorTest`, `TransitionFuzzerTest`); for every unit small enough to enumerate the new result equals the retained legacy result in content and order; the measurement is in the ADR and in the pull request body; the guide states the cost. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of `pm-workflow` (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and the integration tests of every changed module with tests (none) are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set:**

- Requirements: PM-IMPL-5
- Specification: Workflow DSL § Static AST Verification Rules (Rule 11); § Compiled Invariants (capped model loops)
- Watch: none anchors on the cost of the enumeration (`doc/implementation-watch/workflow-dsl.adoc` has none; the epic ledger carries the lead)

## Standing Constraints

- **Log messages: none expected.** An INFO, WARN or ERROR message the trace matrix shows goes into the holder of the module (ADR `doc/adr/005-one-log-message-holder-per-module.adoc`) and the inbox message says so.
- **No change of a verification result.** The return of `findSimpleCycles` is identical for every unit; a deviation found in D1 is a defect to report in the inbox message, not an improvement to take.
- **No cap, no new refusal, no caller change.** A limit on the number of cycles, a refusal of a unit for its cycle count and a change in `WorkflowValidator` or `SubWorkflowInvariants` are decisions of the operator; they would also touch files that PLAN-48 and PLAN-71 name.
- **Not taken, and why:** the "2^N configuration combinations" of Rule 3 are enumerated nowhere (Rule 3 configuration reachability is a named seam, ADRs `workflow-verify/004` and `006`, until the specification issue `plan-marshall-documentation` #71 is answered); the call-resolved graph's bare-id keys (`CallGraphExpansion`) are a decision of the operator, listed in the cluster report, not part of this plan.

## Claim Labels

- OBSERVED: `UnitGraph.findSimpleCycles` starts a depth-first search at every state and descends only into states of a larger index than the start state (a canonical form with the lowest-index state first); it does not restrict the search to a strongly connected component — `pm-workflow/src/main/java/de/planmarshall/workflow/verify/UnitGraph.java` lines 339 to 375 (read at `a8da4104`)
- OBSERVED: two callers: `WorkflowValidator.checkCycleAwaitedEvent` (`WorkflowValidator.java` line 2134, called at line 262) and `SubWorkflowInvariants.cappedModelLoops` (`SubWorkflowInvariants.java` line 265); both iterate the returned list and de-duplicate by the joined state ids
- OBSERVED: no test calls `findSimpleCycles` or builds a unit for its scale: `git grep -n findSimpleCycles pm-workflow/src/test` finds nothing; `UnitGraph` is named by `TransitionFuzzerTest` only
- OBSERVED: the default units of the product are not in this repository — `doc/specification/workflow-dsl/07-loop-back-diagrams-verification.adoc` § Build-Time Drift Gate ("`pm-workflow` carries no unit of the product and tests the generator on fixture units only"); the fixture corpus `pm-workflow/src/test/resources/unit-fixtures/defaults/` holds twelve units of 4 to 17 states each (`plan.pmwf` 17, `remediation-qgate-findings.pmwf` 14, `execute.pmwf` 13, `finalize.pmwf` 13)
- OBSERVED: the "merged plan unit" of the original lead no longer exists: since PLAN-43 (#220) the plan unit calls six phase sub-workflows and the rules run per unit; the invariants run over the call-resolved graph (`CallGraphExpansion`, ADR `workflow-verify/033`) but `findSimpleCycles` is called on the unit itself (`UnitGraph.of(definition)`), not on the expansion
- OBSERVED: no enumeration of configuration combinations exists: `depends_on_config` is parsed and printed (`WorkflowParser.java` line 630, `WorkflowPrinter.java` line 222) and nothing in `workflow.verify` reads `GuardSpec.dependsOnConfig()`; ADRs `workflow-verify/004` and `006` name the seam
- HYPOTHESIS: the present implementation is practical on the real phase units and impractical only on a unit with many independent branch-and-rejoin sections inside one loop — confirm/refute by D1 (verify-at-outline)
- HYPOTHESIS: restricting the search to the strongly connected components returns the same list in the same order, because the present search already visits states in the declaration order and keeps only cycles through the start — confirm/refute by the retained oracle of D1 on the twelve fixture units and on the generated units (verify-at-outline)
- Verify-first clause: before scoping, list the open pull requests for one that touches `pm-workflow/src/main/java/de/planmarshall/workflow/verify/UnitGraph.java`, re-read `findSimpleCycles` at the head, and check that no plan of `plans/` declares `UnitGraph.java`; a refutation of any claim above loops back to re-scope.

## Expected Surface

- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/UnitGraph.java` — `findSimpleCycles` and its helper `dfsCycles` (only if D3 is built)
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/verify/UnitGraphCycleScaleTest.java` — new test class (the generated families, the retained oracle, the time bound)
- OBSERVED: `pm-workflow/doc/developer/workflow-verify.adoc` — developer guide of an existing package (additive: § Rule 11: Cycles, the `UnitGraph` entry)
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-verify/` — one ADR, the next free number of the directory (033 is the last at `a8da4104`; renumber against `origin/main`, R11)
- Not surface (R19): `doc/plans/README.adoc`, `doc/Traceability.adoc`, `doc/LogMessages.adoc`. This plan has no package file and no specification part to edit.

## Dependencies and Sequencing

- Depends on: nothing.
- Overlaps with: PLAN-46 and PLAN-105 (running): no shared file. Staged plans that declare other classes of `workflow.verify` (`WorkflowValidator.java`: PLAN-48 and PLAN-71; `CompiledInvariants.java`, `InvariantId.java`, `CompiledInvariantsTest.java`, `DeclarationBounds.java`: PLAN-62): 0 shared files, because this plan changes no caller and adds its test as a new class. `UnitGraph.java` is declared by no live plan.
- Disjoint from the other plans of this cluster (PLAN-122 (`scenario-task-protocol`), PLAN-123 (`scenario-worker-faults`), PLAN-120 (`default-plan-lanes-and-run`), PLAN-121 (`positioned-layout`)) by file.
- Package bookkeeping (R01/R05): no package file covers this plan (like PLAN-95).
- Adjacent to: `plan-marshall-mcp` / `default-units-and-drift-tests` (blocked), which runs this code over the product's units; its numbers can supersede the generated ones.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-124-verifier-cycle-scale.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

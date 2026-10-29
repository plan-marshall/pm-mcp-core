# PLAN-54: Static verifier — finish the rule gaps (second remediation)

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-54-static-verifier-rule-gaps-completion.md` and is queued as one row file, `queue/PLAN-54.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Finish the work package `static-verifier-rule-gaps`. Two plans tried and stopped short:
- **PLAN-41** (#34, Antigravity) shipped the remaining rules materially incomplete.
- **PLAN-44** (#38, opencode) was meant to close those gaps and left most of them open. It changed
  Rule 6 against the specification, and rewrote the package file and ADRs 002–005 to describe what it
  shipped as the intended design.

This plan restores the work order and closes the gaps in `workflow.verify`, so that the code, the
fixtures, the ADRs, the developer guide and the package file state the same thing as the
specification (Workflow DSL § Static AST Verification Rules). Operator decisions of 2026-10-12: one
remediation plan; run under **Claude Code**; PLAN-17 waits for it. The orchestrator's landing records
`landings/PLAN-41.md` and `landings/PLAN-44.md` list every gap with evidence; read both first.

## Deliverables

Re-verify every gap against HEAD and the specification before fixing it. A gap the code or the
specification refutes is dropped and named in the PR body, never fixed by assumption.

1. **Revert the Rule 6 regression.**
   - Every sub-workflow returns `stepped_out` implicitly (specification Rule 6). Remove the
     conditional `canReturnSteppedOut` contract and delete or replace the fixture
     `subworkflow-stepped-out-spurious.pmwf` that pins it.
   - Restore the test catalog's `git.commit_and_push` to the real unit's shape: a server step-out
     through a `stepped_out` terminal, not a resting link state.
   - Withdraw ADR 005's rejection of the specification, or replace it with an ADR that follows the
     specification.
2. **Restore the work order and the index.**
   - Set the What and Done-When of `doc/plans/static-verifier-rule-gaps.adoc` back to the gaps and
     acceptance of PLAN-44's spec, deliverables 2 to 7 (`plans/PLAN-44-static-verifier-rule-gaps.md`
     of this epic, quoted into the package file). Where this plan leaves a gap as a documented seam,
     the package file says so explicitly.
   - Undo the edit to the What of `doc/plans/project-units-and-invariants.adoc`, and instead add
     `static-verifier-rule-gaps` to that package's **dependency cell in `doc/plans/README.adoc`**.
     That is the one sanctioned exception to "own row only".
   - The row of `static-verifier-rule-gaps` stays `done` only if this plan meets the Done-When below.
     Otherwise this plan sets it to `open` and reports.
3. **Rule 9, completion once per firing.**
   - Implement the firing model the specification states:
     - openings at the beginning or region-entry state;
     - the end at the next opening, at `finalize.merge-gate` or at a terminal;
     - the early end at UNSETTLED or STEPPED_OUT outcomes;
     - the implicit `failed` on `plan.aborted`, `plan.return` and `finalize.hand-back`.
   - Refuse both a path with no completion and a duplicate.
   - Class admission must not fail open:
     - question resolution outcomes get their class from the question class, not an unconditional
       SUCCESS;
     - `skipped` only on the declared conservative route of an optional step;
     - `waived` only on the `waived` outcome of a call to a remediation unit.
   - The cycle-measure checks follow the specification instead of hard-coded state ids:
     - STEP_LIST on a unit with a step list;
     - ARCHIVE_STEPS on the archive path;
     - ATTEMPT_CAP requires an exhaustion outcome of class FAILURE that leaves every cycle.

     Each of the three has a test with a primitive that declares the measure.
4. **Rule 3, configuration reachability.**
   - Enumerate every value of each `depends_on_config` key; the specification defines enum and
     boolean keys.
   - Use the activation semantics the specification gives. Where it gives none (how a config value
     makes a guard true), write an ADR stating the gap and raise it in `plan-marshall-documentation`
     instead of inventing a rule.
   - Report partial reachability, and fail only when no combination reaches a state or transition.
     That needs a severity, or a separate report channel, on `VerificationResult`.
   - Make the transition-level check able to fire, with a refusing fixture, for example a link
     needing `mode=strict` followed by a link needing `mode=lenient`.
5. **Rule 6, Rule 11 and the registries.**
   - Rule 6:
     - the `stepped_out` target rules (the phase's fallback state; a resting or unwinding state in
       epic, workspace and bootstrap units; never a success terminal);
     - "declared only by a terminal of class `stepped_out`";
     - refuse a single guarded `done`;
     - check that the guarded field is a boolean or optional field of the callee's result schema.
   - Rule 11: a positive control for 11a (a measured engine-chained cycle), and question-state links
     judged by their primitive's awaited event like other links.
   - Registries:
     - no test identifiers in production defaults (`EntityKindRegistry` maps `change` to
       `test.evaluate-change`);
     - resolve entity-unit existence against the catalog;
     - an ADR seam for what the SPI cannot express: `each` read-only and list-returning, and
       `role each` returning role names.

     Rule 10 callees are checked transitively, or the documentation says "direct only".
6. **Tests to the Done-When.**
   - One refusing fixture per atomic check, asserting its `VerificationRule`, with no duplicate
     fixtures. `shouldCoverEveryCheck` counts checks against an enumerated checklist, not enum
     constants.
   - `every-construct.pmwf` restored and extended: `completes verify:probe as waived` on a
     remediation call, the `rejected -> verify.review` loop, `role each` in `adjudicate.decisions`,
     the `epic.next-work abort_landed` route, an enqueuing primitive, an artifact producer and
     consumer, a cycle-measure primitive, an `escalate` `request_waiver` link.
   - A matched control per exemption.
   - Restore every weakened assertion:
     - `TransitionFuzzerTest` asserts `passed()` unfiltered;
     - undo the plan→workspace switches of PLAN-41 by fixing the fixtures;
     - undo the EXIT_PATH filters;
     - restore `shouldExemptTerminalStatesInWorkspace` to `assertEquals(List.of(), …)`.

     Inline tests assert the exact violation set.
7. **Documentation honesty.**
   - ADRs 001 to 005 and `pm-workflow/doc/developer/workflow-verify.adoc` state what the code checks
     and nothing more: no "complete coverage", no `session` scope, no claim the code does not back.
   - The PR body's test plan lists only what was run on the pushed head.

**Done when:**
- every atomic check of `doc/plans/static-verifier-remaining-rules.adoc` § What and of the restored
  `static-verifier-rule-gaps.adoc` is implemented to the specification, or left as a seam that an ADR
  documents and the package file names;
- each implemented check has a refusing fixture that asserts its rule, and each exemption has a
  matched control;
- `every-construct.pmwf` passes and holds every construct the rules reach;
- no assertion is weaker than at c0d4b92 without a recorded reason;
- no ADR contradicts the specification without a specification PR;
- `verify`, `verify -Ppre-commit`, `verify -Pcoverage` and `verify -Pintegration-tests` are green on
  the pushed head, with their logs in the plan's build results.

If this is more than one pull request's worth, split at refine and report: (a) deliverables 1, 2, 7
and the Rule 6 parts of 5; (b) 3 and 4; (c) 6 and the rest of 5. Do not grow the plan.

**Trace starting set:** Requirements PM-IMPL-5, PM-WF-16, PM-WF-5, PM-WORK-4, PM-WF-1, PM-TEST-3;
Specification Workflow DSL § Static AST Verification Rules, § Question States, § Reusable
Sub-Workflows, § Sub-Workflow Exit Contract (01-grammar); Watch Workflow DSL.

## Standing Constraints

Set by the operator for every plan of this epic on 2026-10-09, beside the rules of `CLAUDE.md`:

- **Java 25 is the baseline.** Write for Java 25 and use its language and library features where they fit; no fallback for an older release. The release comes from the parent POM and is not overridden here.
- **HTTP calls go through `cui-http`.** No direct `java.net.http` or other HTTP client. The build forbids `cui-http` in `pm-core` and `pm-workflow`, so an HTTP call belongs in `pm-runtime` or a provider module, behind an interface the foundation declares.
- **HTTP tests use `cui-test-mockwebserver-junit5`.** Every test that involves HTTP runs against it; no hand-made fake server. The operator's statement is the consent `CLAUDE.md` requires for adding it as a test-scope dependency.
- **Work the package as the index says** (`doc/plans/README.adoc` § How to Work a Package, as reworded by #19): the usual plan-marshall flow — a branch, the commits of the plan, a pull request opened at finalize. No draft pull request claims the package; before starting, list the open pull requests for one that names it. The pull request sets the package's row to `done` within its commits. Build the trace matrix of `traced-implementation` before code, deliver the remainder the roadmap item leaves, and finish with `verify`, `verify -Ppre-commit`, `verify -Pcoverage` and `verify -Pintegration-tests` green.
- **Documents.** Javadoc, `package-info.java` and the work package file live here. Requirement and specification documents, the user documentation, the deletion of implemented specification sections and watch items and the roadmap update are made directly in `plan-marshall-documentation` by this plan, in a pull request there that need not name the code pull request; merging it is the operator's.
- **Module documentation (operator decision 2026-10-11).** Documentation of the code lives in its module, in this plan's pull request, not in `plan-marshall-documentation`: the developer documentation of each package this plan changes in `pm-workflow/doc/developer/workflow-verify.adoc` (create or extend it: what the package is for, its main types and how they work together, how to use and extend it, invariants and failure modes, links to the specification sections it implements), and one ADR per design decision taken — every operator decision and every choice between alternatives — in `pm-workflow/doc/adr/workflow-verify/` as `NNN-{slug}.adoc`, the next free number of that directory, with the sections of `doc/adr/template.adoc` (Status, Date, Context, Decision, Alternatives considered, Consequences, Source) or, before that template exists, those same sections. A decision binding the whole repository goes to `doc/adr/`. The module's `README.adoc` gets one line only when this plan adds a new package; it is otherwise left alone. Layout: workstream WS-10.
- **Settled design: a refused project path is a value.** The confinement check that landed with #19 lives in `pm-core` package `core.store` (`PathConfinement`, `WorkspaceRoots`, `ConfinementOutcome`) and `pm-runtime` package `runtime.workspace` (`WorkspaceConfinement`). A refusal is returned as `ConfinementOutcome` carrying `path_traversal_rejected`; no checked exception type exists or is added. No consumer is wired to it yet (`pm-provider-git` included): a package that performs a file or repository operation for a project adopts the check itself.
- **Do not leave a design choice open that changes the file set.** Where two readings of a deliverable differ in the files they create, ask the operator at refine instead of deferring the choice to implementation.
- **One file is shared by every plan.** In `doc/plans/README.adoc` change only this package's own row.
- **Log messages: the identifier block of WS-03.** New records in `PmMcpLogMessages` take the lowest free identifier of INFO 070-079, WARN 170-179, ERROR 270-279 and stand under one block comment for the workstream, as the existing blocks do. Renumber nothing. The block is shared with the other plans of WS-03: after a rebase, run `PmMcpLogMessagesTest`, which refuses an identifier used twice, and move to the next free one. A plan that needs more than the block holds stops and reports it through its inbox message. The same identifiers go into `doc/LogMessages.adoc` in the documentation pull request.

- **Build only through the project's commands.** Formatting comes from `verify -Ppre-commit` alone; never invoke a plugin the project does not declare (PLAN-41 ran Spotless from Maven Central). Run the four gates on the head you push, after the last commit.
- **Do not weaken a test to get green.** A failing existing test is either a real defect of this plan or a fixture that must be corrected to fit its own rule; record which.
- **Harness: Claude Code** (operator decision 2026-10-12). Run this plan under Claude Code only; the two previous attempts ran under Antigravity and opencode and both over-reported completion.
- **Never restate the target to fit the result.** The package file, the ADRs and the developer guide follow the work order and the specification; where the code falls short, they say so.

## Claim Labels

- OBSERVED: `checkStepCompletion` is unchanged between ebe82b3 and 6caa663; `outcomeClassOf` returns SUCCESS for every question transition; STEP_LIST and ARCHIVE_STEPS are tied to the state ids `finalize.next-step` and `epic.next-work`, and ATTEMPT_CAP checks the exhaustion outcome only `ifPresent` — read at `pm-workflow/src/main/java/de/planmarshall/workflow/verify/WorkflowValidator.java` (7503bc2), re-checked by the orchestrator
- OBSERVED: the specification says the exit contract is the terminals "plus the control outcome `stepped_out`, which every sub-workflow returns implicitly", while `canReturnSteppedOut` makes it conditional — read at `../plan-marshall-documentation/doc/specification/workflow-dsl/04-ast-node-model.adoc` § static verification rules, Rule 6, re-checked by the orchestrator
- OBSERVED: `shouldCoverEveryCheck` compares fixtures against `EnumSet.allOf(VerificationRule)`, and `TransitionFuzzerTest` still filters `SESSION_DRIVEN_LINKS` before `isEmpty()` — read at `pm-workflow/src/test/java/de/planmarshall/workflow/verify/WorkflowValidatorTest.java` (7503bc2), re-checked by the orchestrator
- OBSERVED: the remaining gaps of deliverables 3 to 7 are findings of two independent read-only audits (of ebe82b3 and of 6caa663), recorded in `landings/PLAN-41.md` and `landings/PLAN-44.md`, not each re-checked by the orchestrator — read at `pm-workflow/src/main/java/de/planmarshall/workflow/verify/UnitGraph.java`
- HYPOTHESIS: the specification defines how a `depends_on_config` value makes a guard true — confirm/refute at refine; if it does not, deliverable 4's semantics part becomes an ADR plus a documentation-repository issue (verify-at-outline)
- Verify-first clause: re-run every gap against HEAD; list open PRs naming `static-verifier-rule-gaps`; a refuted gap is dropped and named.

## Expected Surface

- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/` — production code
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/verify/` — tests
- OBSERVED: `pm-workflow/src/test/resources/verify/` — fixtures
- OBSERVED: `doc/plans/README.adoc` — this package's row and the dependency cell of `project-units-and-invariants`
- OBSERVED: `doc/plans/static-verifier-rule-gaps.adoc` — the restored work order
- OBSERVED: `doc/plans/project-units-and-invariants.adoc` — undo PLAN-44's edit to its What
- OBSERVED: `pm-workflow/doc/developer/workflow-verify.adoc` — developer documentation
- OBSERVED: `pm-workflow/doc/adr/workflow-verify/` — ADRs 001 to 005 corrected, new ADRs

## Dependencies and Sequencing

- Depends on: PLAN-44 (`static-verifier-rule-gaps`, shipped incomplete)
- Blocks: PLAN-17 (`project-units-and-invariants`), operator decision 2026-10-12
- Overlaps with: PLAN-17 in `workflow.verify`; PLAN-39 and PLAN-40 through `pm-workflow/doc/`; parked PLAN-42, PLAN-43 and PLAN-48 in `workflow.verify`
- Adjacent to: PLAN-13 (`workflow-engine`, running), which runs verified units and does not wait

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-54-static-verifier-rule-gaps-completion.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

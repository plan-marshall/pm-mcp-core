# PLAN-44: Static verifier — the rule gaps PLAN-41 left

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-44-static-verifier-rule-gaps.md` and is queued as one row file, `queue/PLAN-44.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Close the gaps between `doc/plans/static-verifier-remaining-rules.adoc` and what PLAN-41 (#34, ebe82b3) actually shipped in `workflow.verify`, and correct the over-claims it left in ADR 001 and the developer guide. PLAN-41 reported the package complete; an independent audit of the merged code against the package file and the specification (Workflow DSL § Static AST Verification Rules) found it materially incomplete. The operator decided on 2026-10-12 that this is a new work package with its own file and index row, and that PLAN-17 waits for it.

## Deliverables

Each gap below is a finding of the orchestrator's audit at ebe82b3 (claim labels below). Re-verify each against the code and the specification before fixing it; a gap the code refutes is dropped and named in the PR body.

1. **Package file and index row.** Add `doc/plans/static-verifier-rule-gaps.adoc` (Serves: Milestone 3; Where: pm-workflow `workflow.verify`; What: the gaps below; Done When as below) and its row in `doc/plans/README.adoc` (depends on `static-verifier-remaining-rules`), set to `done` within this plan's commits. Add it to the dependency cell of `project-units-and-invariants` (operator decision 2026-10-12) — the one sanctioned exception to "own row only".
2. **Rule 9, completion once per firing.** Model the firing as the specification states (opening at the beginning or region-entry state; end at the next opening, at `finalize.merge-gate` or a terminal; early end at UNSETTLED or STEPPED_OUT outcomes; implicit `failed` on `plan.aborted`, `plan.return`, `finalize.hand-back`) and refuse a path with no completion as well as a duplicate. Class admission must not fail open: no SUCCESS fallback in `outcomeClassOf` for question outcomes or unresolved outcomes; `skipped` only on the declared conservative route of an optional step; `waived` only on a remediation call. The cycle-measure checks (STEP_LIST, ARCHIVE_STEPS, ATTEMPT_CAP with a FAILURE-class exhaustion outcome) follow the specification instead of hard-coded state ids, and each has a test.
3. **Rule 11b.** Implement the per-link rule (a `noop` or link step counts through its primitive's `awaitedEvent()`), so a resting cycle without an awaited event is refused; `stepAwaitsEvent` no longer answers `true` for every resting state. Add a refusing fixture and a matched control; give Rule 11a a positive control (a measured engine-chained cycle).
4. **Rule 3, configuration reachability.** Enumerate every value of each `depends_on_config` key (enums, not booleans only), use the specification's activation semantics, fail only when no combination reaches a state or transition and report the reaching combinations otherwise, and make the transition-level check able to fire. Fix the stale class Javadoc. Address the 2^N rebuild per state if the merged plan unit makes it slow.
5. **Rule 6 and the registries.** Add the `stepped_out` target rules and the "declared only by a terminal of class `stepped_out`" rule; refuse a single guarded `done` and check that the guarded field is a boolean or optional field of the callee's result schema. Add the question class `ask` (outcomes `answered`, `assumed`) to `QuestionClassRegistry.defaults()` and replace the fixture `question-class-unregistered.pmwf`, which uses `ask` as its unknown class. Production defaults carry no test identifiers (`EntityKindRegistry` maps `change` to `test.evaluate-change`; entity units are the placeholder `"entity"`): resolve entity-unit existence against the catalog. Where the SPI cannot express read-only / list-returning / role-name-returning primitives (Rule 1 `each`, `role each`), say so in an ADR and leave the seam, instead of claiming the check.
6. **Tests to the Done-When.** One refusing fixture per atomic check (not per enum constant: `shouldCoverEveryCheck` must count checks), `every-construct.pmwf` extended to every construct the rules reach (restore the deleted `completes verify:probe as waived` and `rejected -> verify.review`), and a matched control per exemption (`role each` in `adjudicate.decisions`, `epic.aborted` via `abort_landed`, depth exactly 4, producer present, `request_waiver`, a valid job-starting target). Restore the assertions PLAN-41 weakened: `TransitionFuzzerTest` asserts `passed()` on an unfiltered result; tests changed from `plan` to `workspace` to dodge Rule 10 get fixtures that fit their rule instead. Inline tests assert the exact violation set, not `anyMatch` plus a substring.
7. **Documentation honesty.** ADR 001 and `pm-workflow/doc/developer/workflow-verify.adoc` state what the code checks: no "complete coverage", no "every check has a fixture" unless true after this plan; no `session` scope; Rule 10 callees transitive or stated as direct only. Add an ADR per design decision this plan takes.

**Done when:** every atomic check of `static-verifier-remaining-rules.adoc` § What is implemented to the specification or explicitly left as a documented seam with an ADR; each implemented check has a refusing fixture that asserts its `VerificationRule`, and each exemption a matched control; `every-construct.pmwf` passes and contains every construct the rules reach; no existing assertion is weaker than at c0d4b92 without a recorded reason; `verify`, `verify -Ppre-commit`, `verify -Pcoverage` and `verify -Pintegration-tests` green on the pushed head.

**Trace starting set:** as PLAN-41 — Requirements PM-IMPL-5, PM-WF-16, PM-WF-5, PM-WORK-4, PM-WF-1, PM-TEST-3; Specification Workflow DSL § Static AST Verification Rules, § Question States (classes), § Reusable Sub-Workflows; Watch Workflow DSL.

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

## Claim Labels

- OBSERVED: `QuestionClassRegistry.defaults()` registers init, clarification, review, task, setup — not `ask`, which Workflow DSL 02-units-jobs-decisions lists with outcomes `answered`, `assumed`; the fixture `question-class-unregistered.pmwf` uses `question ask` — read at `pm-workflow/src/main/java/de/planmarshall/workflow/verify/QuestionClassRegistry.java` (ebe82b3), re-checked by the orchestrator
- OBSERVED: `stepAwaitsEvent` returns `true` for JobStep, ModelJob, QuestionWait and InteractiveLinks, so the non-chained branch of `checkCycleAwaitedEvent` practically never refuses — read at `pm-workflow/src/main/java/de/planmarshall/workflow/verify/WorkflowValidator.java` (ebe82b3), re-checked by the orchestrator
- OBSERVED: the remaining gaps of deliverables 2 to 6 are findings of an independent read-only audit of ebe82b3 against the package file and the specification, not each re-checked by the orchestrator — read at `pm-workflow/src/main/java/de/planmarshall/workflow/verify/WorkflowValidator.java` and `UnitGraph.java`
- HYPOTHESIS: deliverables 2 to 6 fit one pull request — confirm/refute at refine; if not, split by rule group (Rule 9; Rules 3 and 11; Rule 6 and registries; tests and docs) and report it (verify-at-outline)
- Verify-first clause: re-run the audit's claims against HEAD before scoping; list the open pull requests for one that names `static-verifier-rule-gaps`; a refuted gap is dropped and named, never fixed by assumption.

## Expected Surface

- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/` — production code
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/verify/` — tests
- OBSERVED: `pm-workflow/src/test/resources/verify/` — fixtures
- OBSERVED: `doc/plans/README.adoc` — this package's own row, and the dependency cell of `project-units-and-invariants`
- HYPOTHESIS: `doc/plans/static-verifier-rule-gaps.adoc` — the new package file
- OBSERVED: `pm-workflow/doc/developer/workflow-verify.adoc` — developer documentation of the package
- OBSERVED: `pm-workflow/doc/adr/workflow-verify/` — ADR 001 corrected, new ADRs

## Dependencies and Sequencing

- Depends on: PLAN-41 (`static-verifier-remaining-rules`), shipped
- Blocks: PLAN-17 (`project-units-and-invariants`), operator decision 2026-10-12
- Overlaps with: PLAN-17 in `workflow.verify`; PLAN-40 through `pm-workflow/doc/developer/`; PLAN-42 and PLAN-43 (parked) in `workflow.verify`
- Adjacent to: PLAN-13 (`workflow-engine`), which does not wait for this plan (operator decision 2026-10-12)

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-44-static-verifier-rule-gaps.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

# PLAN-41: Static verifier — the remaining rules

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-41-static-verifier-remaining-rules.md` and is queued as one row file, `queue/PLAN-41.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `static-verifier-remaining-rules` of `doc/plans/static-verifier-remaining-rules.adoc` — the checks of the static verification rules that `static-verifier-and-fuzzer` (PLAN-10, #32) left out, each as a further `VerificationRule` constant of `WorkflowValidator` — as one pull request in this repository. The package was added to the index by #32 on the operator's decision. It serves Roadmap Milestone 3 and lands in pm-workflow package `workflow.verify`. The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

## Deliverables

The bullets of `doc/plans/static-verifier-remaining-rules.adoc` § What, in its words; its "Not in this package" list is context, not a deliverable.

1. Rule 1, the rest (entity kinds of `job llm`, `each` primitives, `role each` only in `adjudicate.decisions`, `ask` refusals, role-name grammar, registered schemas and question classes, `enqueuesJob()` outcomes).
2. Rule 2, the rest (the `job llm` transition bijection plus `lost`; `asked` engine-made; question-clause bijection).
3. Rule 3, the rest (state identifiers unique across the definition unit incl. `<state>.asked`; `startState` and targets exist; reachability under every `depends_on_config` combination; abort-terminal targeting; implicit link identifiers; condition kinds and reserved ids refused as targets).
4. Rules 4, 6, 7, 8, 9, 10, 11 (11a, 11b) and 12.

5. **Index dependencies (operator decision 2026-10-12).** In `doc/plans/README.adoc`, add `static-verifier-remaining-rules` to the dependencies of `project-units-and-invariants` and of `workflow-engine`, beside setting this package's own row to `done`. This is the one sanctioned exception to "change only this package's own row".

**Done when** (the package's own acceptance): one refusing fixture per added rule check; the passing unit of `static-verifier-and-fuzzer` extended to every construct the added rules reach; a matched control per exemption.

**Trace starting set** (documents live in `../plan-marshall-documentation`):

- Requirements: PM-IMPL-5, PM-WF-16 (Rules 6, 7), PM-WF-5 and PM-WORK-4 (Rule 4), PM-WF-1 (Rule 10), PM-TEST-3
- Specification: Workflow DSL § Static AST Verification Rules
- Watch: Workflow DSL (PM-WATCH-DSL-3, 4, 5, 8, 9, 11, 15, 16, 29, 32)

Scope split: four rule groups with many checks each. If the trace matrix shows more than one pull request's worth, split at refine by rule group and report it, instead of growing the plan.

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

## Claim Labels

- OBSERVED: the deliverables, the trace starting set and the acceptance above are the package file's own — read at `doc/plans/static-verifier-remaining-rules.adoc` (main at c0d4b92)
- OBSERVED: the index gives this package the status `open` and the single dependency `static-verifier-and-fuzzer`, which is `done` — read at `doc/plans/README.adoc` § Packages (main at c0d4b92)
- OBSERVED: `workflow.verify` holds `WorkflowValidator` and its `VerificationRule` constants from #32, with fixtures below `pm-workflow/src/test/resources/verify/` — file list of c0d4b92
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line and #32's layout, not from a trace matrix — confirm/refute at the trace matrix (verify-at-outline)
- HYPOTHESIS: the package adds no log message (#32 added none) — confirm/refute at the trace matrix (verify-at-outline)
- Verify-first clause: before scoping, list the open pull requests for one that names `static-verifier-remaining-rules`, and check in `doc/plans/README.adoc` that every dependency is `done`; a refutation of any claim above loops back to re-scope.

## Expected Surface

- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/` — production code
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/verify/` — tests
- OBSERVED: `pm-workflow/src/test/resources/verify/` — refusing and passing fixtures
- OBSERVED: `doc/plans/README.adoc` — this package's own status row, and the dependency cells of `project-units-and-invariants` and `workflow-engine`
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-verify.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-verify/` — ADRs of the package's design decisions; new directory if absent

## Dependencies and Sequencing

- Depends on: PLAN-10 (`static-verifier-and-fuzzer`), shipped
- Blocks: PLAN-13 (`workflow-engine`) and PLAN-17 (`project-units-and-invariants`), by operator decision 2026-10-12
- Overlaps with: PLAN-17 (`project-units-and-invariants`) in `workflow.verify` — sequenced by the gate; PLAN-40 (`module-developer-docs`) through `pm-workflow/doc/developer/`; every plan through `doc/plans/README.adoc`; the plans of WS-03 through their identifier block in `PmMcpLogMessages`, which is not a declared surface
- Adjacent to: `plan-marshall-mcp` (Rule 5's rest, the released-states baseline) and the role sets of Milestone 4 (Rule 13), which stay untouched

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-41-static-verifier-remaining-rules.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

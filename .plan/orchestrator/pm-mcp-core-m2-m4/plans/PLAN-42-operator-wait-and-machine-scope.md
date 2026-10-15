# PLAN-42: Waiting for the operator, and the machine scope

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-42-operator-wait-and-machine-scope.md` and is queued as one row file, `queue/PLAN-42.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `operator-wait-and-machine-scope` of `doc/plans/operator-wait-and-machine-scope.adoc`: waiting for the operator, and the machine scope. Serves: roadmap Milestone 7 (the operator wait as a kind of state, the machine scope); the operator waits in both clients of `pm-mcp-clients`. Where: pm-workflow (the operator wait in the language, the static verifier, the engine); pm-core (the machine scope's store). The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

**Parked:** waits on packages that are not done: `operator-and-question-stores`, `waits`. Re-ground this spec before emitting it.

## Deliverables

The bullets of `doc/plans/operator-wait-and-machine-scope.adoc` § What, in its words (regenerated at 710da61, after the specification moved into this repository).

1. Implements Workflow DSL § Waiting for the Operator and § Machine Scope (`PM-WF-20`, `PM-WF-26`).
2. The operator wait: a state of any unit and any scope may wait for the operator, in its three forms (a link state, the operator states of the decision unit, the waiver wait); it offers the operator's links to both clients, the first act winning, holds no worker and no model slot, has no time limit, and ends by an operator act through a link or by an engine-owned transition that ends the scope; its awaited events and the static verifier's rules on resting states and operator links (Rules 10, 11b) follow. Holding nothing is structural, not a release of its own: an operator wait is no model-job state, so the engine opens no task in it and offers none to a worker, and the scope reaches it only after the tasks of the state before have settled, each closed task having released its lease and its model slot where they are held (`task-queue-and-protocol`, `slots-usage-and-budgets`); the one exception is a worker holding its task for the answer to its own question within its role's hold limit, which suspends or falls back after it (Model Work § Continuing after a Question). Consent states (`<sub-workflow>.consent`, links `yes`, `yes_remember`, `no`) are operator waits of this form; their store is `machine-onboarding-unit`'s. The question modes `auto` and `none`, the question wait, the deadline, the class fallbacks, and the expiry of a waiver request go; a waiver request ends by `waive` or `decline`.
3. What halts: an operator wait halts its own scope only, shown as waiting for the operator, never as stalled; the machine scope's onboarding halts the system: while its first run has not reached `onboard.completed`, the setup gate refuses to start a plan, an epic, or a project onboarding. This package builds that guard once, reading the machine scope's state: the setup gate's reason `machine_not_onboarded` and, for an act over the local API that needs a step of the onboarding not done yet, `onboarding_incomplete` (`409`), each naming the missing step and both clients; the onboarding itself and the operator's verbs stay open. Its callers are the starts of `start-from-clients` (`start.open`, `start-epic.open`, `start.create-scope`) and the start of a project's onboarding of `project-onboarding-unit`, which build those entry points.
4. The machine scope: the fourth top-level scope, one per machine (`scope_type: machine`, `scope_id` null, `NO_PLAN` for its workers), its workflow state in `<PM_MCP_BASE>/machine/mcp/status.json`, its states of the phase `machine`, and its resident unit `onboarding.pmwf` compiled into the binary; a condition of the machine scope retried at the compiled backoff and shown as a hold.
5. Where `waits` or `operator-and-question-stores` have already implemented question deadlines and fallbacks, this package removes them, and so the mention of a deadline in the Javadoc of `EngineEvent.QuestionResolved` of `workflow-engine`.
6. Schema parity: the scope `machine` and the phase `machine` of the status store, and `WorkflowStatusSchemaParityTest.phasesEqualSchema` and `scopeTypesEqualSchema` re-enabled.

**Done when** (the package's own acceptance): A test shows a plan in an operator wait with no open task and no task offered for its scope, holding no worker and no slot, another plan running on, and no transition by time; a test shows a waiver request ended by `decline`; a test shows the machine-onboarding guard refusing with `machine_not_onboarded` and `onboarding_incomplete` while the machine's first onboarding has not reached `onboard.completed`, the onboarding itself and the operator's verbs still answered, and passing once it has, and the machine scope's unit passing the graph check of a resident unit; the static verifier accepts an operator wait in a unit of any scope and refuses a `link` clause rendered to a model; `WorkflowStatusSchemaParityTest.phasesEqualSchema` and `scopeTypesEqualSchema` run without `@Disabled` and pass.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Use cases: UC-PLN-2 (a decision as an operator wait); UC-ONB-1 (the machine's onboarding)
- Requirements: PM-WF-20; PM-WF-26; PM-WF-12; PM-SEC-8
- Specification: Workflow DSL § Waiting for the Operator; § Machine Scope; Workflow DSL § Waivers & Integrity; § Question States; § Static Verification; Model Work § Question Delivery

## Standing Constraints

Set by the operator for every plan of this epic on 2026-10-09, beside the rules of `CLAUDE.md`:

- **Java 25 is the baseline.** Write for Java 25 and use its language and library features where they fit; no fallback for an older release. The release comes from the parent POM and is not overridden here.
- **HTTP calls go through `cui-http`.** No direct `java.net.http` or other HTTP client. The build forbids `cui-http` in `pm-core` and `pm-workflow`, so an HTTP call belongs in `pm-runtime` or a provider module, behind an interface the foundation declares.
- **HTTP tests use `cui-test-mockwebserver-junit5`.** Every test that involves HTTP runs against it; no hand-made fake server. The operator's statement is the consent `CLAUDE.md` requires for adding it as a test-scope dependency.
- **Work the package as the index says** (`doc/plans/README.adoc` § How to Work a Package, as reworded by #19): the usual plan-marshall flow — a branch, the commits of the plan, a pull request opened at finalize. No draft pull request claims the package; before starting, list the open pull requests for one that names it. The pull request removes the package's row from the index and deletes its package file within its commits, and drops the package from the _Depends on_ column of every row that names it. Build the trace matrix of `traced-implementation` before code, deliver the remainder the roadmap item leaves, and finish with `verify`, `verify -Ppre-commit`, `verify -Pcoverage` and `verify -Pintegration-tests` green.
- **Documents (operator decision 2026-10-15, pm-mcp-core ADR 007).** The specifications, the implementation watch and the traceability of the engine live in this repository: `doc/specification/`, `doc/implementation-watch/`, `doc/Traceability.adoc`, and the log message reference `doc/LogMessages.adoc`. This plan's one pull request carries the code, the tests, Javadoc and `package-info.java`, the developer documentation and ADRs, the deletion of the implemented specification sections and watch items, a correction of a specification statement it proves wrong, and the `Implementation:`, `Verified by:` and `Documentation:` lines of the requirements it implements in `doc/Traceability.adoc`. It opens a pull request in `plan-marshall-documentation` only to correct a requirement or to change `cross-cutting.adoc` or a specification that lives there; merging that one is the operator's. The concept and user documentation of the slice and the roadmap check-off are written there at the milestone exit: list them in the pull request body as **Owed to the milestone exit**.
- **Module documentation (operator decision 2026-10-11).** Documentation of the code lives in its module, in this plan's pull request, not in `plan-marshall-documentation`: the developer documentation of each package this plan changes in the developer file of each package it changes (`{module}/doc/developer/{package}.adoc`) (create or extend it: what the package is for, its main types and how they work together, how to use and extend it, invariants and failure modes, links to the specification sections it implements), and one ADR per design decision taken — every operator decision and every choice between alternatives — in `{module}/doc/adr/{package}/` as `NNN-{slug}.adoc`, the next free number of that directory, with the sections of `doc/adr/template.adoc` (Status, Date, Context, Decision, Alternatives considered, Consequences, Source) or, before that template exists, those same sections. A decision binding the whole repository goes to `doc/adr/`. The module's `README.adoc` gets one line only when this plan adds a new package; it is otherwise left alone. Layout: workstream WS-10.
- **Settled design: a refused project path is a value.** The confinement check that landed with #19 lives in `pm-core` package `core.store` (`PathConfinement`, `WorkspaceRoots`, `ConfinementOutcome`) and `pm-runtime` package `runtime.workspace` (`WorkspaceConfinement`). A refusal is returned as `ConfinementOutcome` carrying `path_traversal_rejected`; no checked exception type exists or is added. No consumer is wired to it yet (`pm-provider-git` included): a package that performs a file or repository operation for a project adopts the check itself.
- **Do not leave a design choice open that changes the file set.** Where two readings of a deliverable differ in the files they create, ask the operator at refine instead of deferring the choice to implementation.
- **Files shared by every plan.** In `doc/plans/README.adoc` remove only this package's own row (and its name from the _Depends on_ column); in `doc/Traceability.adoc` add only the sections of the requirements this package implements. Neither is a declared surface.
- **Log messages: the holder of your module, next free identifier** (operator decision 2026-10-13; pm-mcp-core #64, ADR `doc/adr/005-one-log-message-holder-per-module.adoc`). A new INFO, WARN or ERROR record goes into the holder of the module whose production code logs it, at the next free identifier of its level (INFO 1-99, WARN 100-199, ERROR 200-299, the same ranges in every holder); for this plan: `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`); `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`). A record two modules log goes to the lower one (`pm-core`); a module logs only through its own holder and `CoreLogMessages`. There are no workstream blocks and nothing is renumbered. A provider's first record creates its holder `de.planmarshall.provider.<x>.log.<X>LogMessages` (prefix `PM_PROVIDER_<X>`) with a `package-info.java`, and adds the prefix to `KNOWN_PREFIXES` in `CoreLogMessagesTest`. After a rebase, run the holder's test, which refuses an identifier used twice or outside its level's range, and move to the next free one. The same identifiers go into `doc/LogMessages.adoc` of this repository, under the holder's section.

- **Format versions stay 1 (operator decision 2026-10-13).** Nothing is released yet, so every versioned artifact (`format_version` of a store document, record, sidecar, journal, codec) stays at version 1. A format change replaces version 1 in place: no version bump, no reader for an older version, no migration. Do not raise a `FORMAT_VERSION` constant, a `format_version` in a fixture, schema or specification example, or an ADR that records a bump.

## Claim Labels

- OBSERVED: the Objective, Deliverables, Done when, Trace and Depends on above are the package file's and its index row's own — regenerated from `doc/plans/operator-wait-and-machine-scope.adoc` and `doc/plans/README.adoc` at 710da61 (branch `docs/specification-into-core`, pm-mcp-core #77), after the specification moved into this repository and the package was aligned with the changed specification; the claims below were checked before that
  - verdict: corroborated | checked_at: 710da6150c6da19d4335643ca6070ef5517f0896 | by: pm-mcp-core-m2-m4/restructure | rescoped: n/a | evidence: regenerated by the orchestrator from the package file and its index row at 710da6150c6da19d4335643ca6070ef5517f0896 after the specification moved into pm-mcp-core (#77); § What, Trace, Done When and Depends on are the package's own
- OBSERVED: the package file and its index row (`open`; depends on PLAN-13 (`workflow-engine`); PLAN-14 (`operator-and-question-stores`); PLAN-25 (`waits`)) — read at `doc/plans/operator-wait-and-machine-scope.adoc` and `doc/plans/README.adoc` (main at a8a4214, regenerated by cleanup)
  - verdict: corroborated | checked_at: a8a421408bfa31a4a64630c5139fd9e50bfac6f3 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: regenerated by cleanup from doc/plans package file and index row at a8a421408bfa31a4a64630c5139fd9e50bfac6f3; Objective, Deliverables, Done when, Trace and Depends on are the package's own at that sha
- OBSERVED: the static verifier's Rule 10 (`SESSION_DRIVEN_LINKS`, resting link states only in session-driven units) shipped with PLAN-41 (#34) and is one of the things this package changes — read at `pm-workflow/src/main/java/de/planmarshall/workflow/verify/VerificationRule.java`
  - verdict: corroborated | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: pm-workflow/src/main/java/de/planmarshall/workflow/verify/VerificationRule.java exists at fa58b0d (presence checked, content not re-read)
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line; the machine scope's store lands in `core.store` — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- Verify-first clause: before scoping, check that the index no longer says `blocked`, that the use cases are agreed, and which of the packages this one restructures have shipped since; a refutation loops back to re-scope.
  - verdict: unverifiable | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: verify-first clause is executed by the plan at refine

## Expected Surface

- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/` — the resting-state rule
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/engine/` — the operator wait in the engine
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/dsl/` — the operator wait in the language
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/` — the machine scope's store
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-verify.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-verify/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-engine.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-engine/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-dsl.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-dsl/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- HYPOTHESIS: `pm-core/doc/developer/core-store.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-core/doc/adr/core-store/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- OBSERVED: `doc/plans/operator-wait-and-machine-scope.adoc` — the package file, deleted when the package is done
- OBSERVED: `doc/specification/model-work/06-envelopes-questions-deadlines.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/workflow-dsl/01-grammar.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/workflow-dsl/02-units-jobs-decisions.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/workflow-dsl/04-ast-node-model.adoc` — specification part; the implemented sections deleted

## Dependencies and Sequencing

- Depends on: PLAN-14 (`operator-and-question-stores`); PLAN-25 (`waits`)
- Blocks: PLAN-43 (`phase-sub-workflows`)
- Restructures: what PLAN-14 and PLAN-25 implement for question modes, deadlines and fallbacks; PLAN-41's Rule 10

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-42-operator-wait-and-machine-scope.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

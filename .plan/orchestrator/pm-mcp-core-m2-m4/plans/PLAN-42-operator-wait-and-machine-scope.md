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

Implement the work package `operator-wait-and-machine-scope` of `doc/plans/operator-wait-and-machine-scope.adoc` (added to the index by #35 on 2026-10-12): the operator wait as one mechanism of the workflow language, and the machine scope as the fourth top-level scope. It serves no roadmap milestone yet and lands in pm-workflow (language, static verifier, engine) and pm-core (the machine scope's store). It writes its specification changes into `plan-marshall-documentation` itself, first.

**Parked:** the index gives the package the status `blocked` — it waits on use cases to be agreed in `plan-marshall-documentation`, not on code. Re-ground this spec against the agreed use cases and the specification before emitting it.

## Deliverables

The bullets of `doc/plans/operator-wait-and-machine-scope.adoc` § What, in its words.

1. Specification first: the operator wait defined once in the workflow language; every own wait, deadline, mode or fallback reduced to a reference to it; the principle that no state waits for an operator decision replaced; the machine scope.
2. The operator wait: any state of any unit and scope may wait for the operator, offers the operator's links, holds no worker and no model slot, has no time limit, ends by an operator act through a link or an override; the static verifier's rule on resting states follows. Question modes `auto` and `none`, the question wait, the deadline, the class fallbacks and the expiry of a waiver request go; a waiver request ends by `waive` or `decline`.
3. What halts: an operator wait halts its own scope only; the machine scope's onboarding halts the system — while incomplete, no plan and no orchestrator starts.
4. The machine scope: the fourth top-level scope, one per machine, its store, and the onboarding unit compiled into the binary.
5. Where `waits` or `operator-and-question-stores` already implemented question deadlines and fallbacks, remove them.

**Done when** (the package's own acceptance): the specification of both changes merged in `plan-marshall-documentation`; a test shows a plan in an operator wait holding no worker and no slot, another plan running on, and no transition by time; a test shows a waiver request ended by `decline`; a test shows no plan starting while the machine's onboarding is incomplete; the static verifier accepts an operator wait in a unit that is not session-driven and refuses one without an operator link.

**Trace starting set** (documents live in `../plan-marshall-documentation`):

- Use cases: Waiting for the Operator (`use-cases/03-plans.adoc#change-operator-wait`); Machine Scope (`use-cases/01-onboarding.adoc#change-machine-scope`)
- Proposal: Specification Changes for the Clients § Foundational Findings
- Specification: Workflow DSL § Waivers & Integrity, § Question States, § Static Verification; Model Work § Question Delivery

## Standing Constraints

Set by the operator for every plan of this epic on 2026-10-09, beside the rules of `CLAUDE.md`:

- **Java 25 is the baseline.** Write for Java 25 and use its language and library features where they fit; no fallback for an older release. The release comes from the parent POM and is not overridden here.
- **HTTP calls go through `cui-http`.** No direct `java.net.http` or other HTTP client. The build forbids `cui-http` in `pm-core` and `pm-workflow`, so an HTTP call belongs in `pm-runtime` or a provider module, behind an interface the foundation declares.
- **HTTP tests use `cui-test-mockwebserver-junit5`.** Every test that involves HTTP runs against it; no hand-made fake server. The operator's statement is the consent `CLAUDE.md` requires for adding it as a test-scope dependency.
- **Work the package as the index says** (`doc/plans/README.adoc` § How to Work a Package, as reworded by #19): the usual plan-marshall flow — a branch, the commits of the plan, a pull request opened at finalize. No draft pull request claims the package; before starting, list the open pull requests for one that names it. The pull request sets the package's row to `done` within its commits. Build the trace matrix of `traced-implementation` before code, deliver the remainder the roadmap item leaves, and finish with `verify`, `verify -Ppre-commit`, `verify -Pcoverage` and `verify -Pintegration-tests` green.
- **Documents.** Javadoc, `package-info.java` and the work package file live here. Requirement and specification documents, the user documentation, the deletion of implemented specification sections and watch items and the roadmap update are made directly in `plan-marshall-documentation` by this plan, in a pull request there that need not name the code pull request; merging it is the operator's.
- **Module documentation (operator decision 2026-10-11).** Documentation of the code lives in its module, in this plan's pull request, not in `plan-marshall-documentation`: the developer documentation of each package this plan changes in the developer file of each package it changes (`{module}/doc/developer/{package}.adoc`) (create or extend it: what the package is for, its main types and how they work together, how to use and extend it, invariants and failure modes, links to the specification sections it implements), and one ADR per design decision taken — every operator decision and every choice between alternatives — in `{module}/doc/adr/{package}/` as `NNN-{slug}.adoc`, the next free number of that directory, with the sections of `doc/adr/template.adoc` (Status, Date, Context, Decision, Alternatives considered, Consequences, Source) or, before that template exists, those same sections. A decision binding the whole repository goes to `doc/adr/`. The module's `README.adoc` gets one line only when this plan adds a new package; it is otherwise left alone. Layout: workstream WS-10.
- **Settled design: a refused project path is a value.** The confinement check that landed with #19 lives in `pm-core` package `core.store` (`PathConfinement`, `WorkspaceRoots`, `ConfinementOutcome`) and `pm-runtime` package `runtime.workspace` (`WorkspaceConfinement`). A refusal is returned as `ConfinementOutcome` carrying `path_traversal_rejected`; no checked exception type exists or is added. No consumer is wired to it yet (`pm-provider-git` included): a package that performs a file or repository operation for a project adopts the check itself.
- **Do not leave a design choice open that changes the file set.** Where two readings of a deliverable differ in the files they create, ask the operator at refine instead of deferring the choice to implementation.
- **One file is shared by every plan.** In `doc/plans/README.adoc` change only this package's own row.
- **Log messages: the identifier block of WS-03.** New records in `PmMcpLogMessages` take the lowest free identifier of INFO 070-079, WARN 170-179, ERROR 270-279 and stand under one block comment for the workstream, as the existing blocks do. Renumber nothing. The block is shared with the other plans of WS-03: after a rebase, run `PmMcpLogMessagesTest`, which refuses an identifier used twice, and move to the next free one. A plan that needs more than the block holds stops and reports it through its inbox message. The same identifiers go into `doc/LogMessages.adoc` in the documentation pull request.

## Claim Labels

- OBSERVED: the package file and its index row (`blocked`; depends on `workflow-engine`, `operator-and-question-stores`, `waits`, and the agreed use cases) — read at `doc/plans/operator-wait-and-machine-scope.adoc` and `doc/plans/README.adoc` (main at ebe82b3)
- OBSERVED: the static verifier's Rule 10 (`SESSION_DRIVEN_LINKS`, resting link states only in session-driven units) shipped with PLAN-41 (#34) and is one of the things this package changes — read at `pm-workflow/src/main/java/de/planmarshall/workflow/verify/VerificationRule.java`
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line; the machine scope's store lands in `core.store` — confirm/refute at the trace matrix (verify-at-outline)
- Verify-first clause: before scoping, check that the index no longer says `blocked`, that the use cases are agreed, and which of the packages this one restructures have shipped since; a refutation loops back to re-scope.

## Expected Surface

- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/` — the resting-state rule
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/engine/` — the operator wait in the engine
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/dsl/` — the operator wait in the language
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/` — the machine scope's store
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only

## Dependencies and Sequencing

- Depends on: PLAN-13 (`workflow-engine`), PLAN-14 (`operator-and-question-stores`), PLAN-25 (`waits`), and the agreed use cases (no work package)
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

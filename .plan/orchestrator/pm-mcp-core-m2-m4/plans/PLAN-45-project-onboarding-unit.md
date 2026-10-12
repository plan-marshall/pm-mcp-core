# PLAN-45: The project onboarding unit

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-45-project-onboarding-unit.md` and is queued as one row file, `queue/PLAN-45.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `project-onboarding-unit` of `doc/plans/project-onboarding-unit.adoc` (added to the index by #37 on 2026-10-12): onboarding a project as one process, the project's values over the machine's, and the projects of the machine. It serves UC-PRJ-1 and UC-PRJ-2 and the project onboarding of both clients, no roadmap milestone yet, and lands in pm-workflow (the unit of the machine scope, the setup's choices as operator waits), pm-core (detection, enrolment writes, project values over machine values) and pm-runtime (favourites, recent, most used). It writes its specification changes into `plan-marshall-documentation` itself, first.

**Parked:** the index gives the package the status `blocked` — it waits on use cases to be agreed in `plan-marshall-documentation`, not on code. Re-ground this spec against the agreed use cases and the specification before emitting it.

## Deliverables

The bullets of `doc/plans/project-onboarding-unit.adoc` § What, in its words.

1. Specification first: the changes Onboarding a Project as One Process and Project Values over Machine Values, once UC-PRJ-1 is agreed.
2. The unit: `project.checkout` to `project.enrol` in the machine scope, then the project setup; the consent kind `inspect_checkout`; detection of remote, base branch, build wrappers, host tools, language servers and scripts with a check line each; the id proposed from the remote; the enrolment written as `pm-operator enrol` writes it.
3. The setup's choices as operator waits served to both clients, the entry session still driving it where the operator starts it from a coding agent.
4. The project's values over the machine's: every aspect the machine's onboarding records, proposed from the machine and recorded for the project only when changed; `project_defaults` as the machine's value of the project's operator keys.
5. The projects of the machine: favourites and their order in a machine store; recent and most used from the operator's acts in a project.

**Done when** (the package's own acceptance): the specification of the changes merged in `plan-marshall-documentation`; a test onboards a fixture checkout from `project.checkout` to a ready project without a session; a test shows a refused consent leaving the checkout unread; a test shows an accepted machine proposal recording nothing for the project and a changed one recorded; `pm-operator enrol` still enrols as a direct verb with its arguments as the answers.

**Trace starting set** (documents live in `../plan-marshall-documentation`):

- Use cases: UC-PRJ-1, UC-PRJ-2 and their changes (`use-cases/04-projects.adoc`)
- Specification: CLI & Security § Enrolment, Lifecycle Gate & Release; Domain Extensions § Setup State Machine; Store Schemas § Enrolment Store

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

- OBSERVED: the package file and its index row (`blocked`; depends on `operator-wait-and-machine-scope`, `enrolment-store`, and UC-PRJ-1 agreed) — read at `doc/plans/project-onboarding-unit.adoc` and `doc/plans/README.adoc` (main at 5010969)
- OBSERVED: the enrolment store, lifecycle gate and integrity shipped with PLAN-11 (#30): `core.store` (`EnrolmentStore`, `ProjectEnrolment`, `LifecycleGate`) and the new package `runtime.enrolment` — file list of 5010969
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line — confirm/refute at the trace matrix (verify-at-outline)
- Verify-first clause: before scoping, check that the index no longer says `blocked`, that the use cases are agreed, and which of the packages this one restructures have shipped since; a refutation loops back to re-scope.

## Expected Surface

- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/unit/` — the onboarding unit of the machine scope
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/` — enrolment writes, project values over machine values
- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/enrolment/` — detection and enrolment
- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/workspace/` — the projects of the machine
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only

## Dependencies and Sequencing

- Depends on: PLAN-42 (`operator-wait-and-machine-scope`), PLAN-11 (`enrolment-store`, shipped #30), and UC-PRJ-1 to UC-PRJ-3 agreed (no work package; widened by #41/#42)
- Overlaps with: the `core.store` plans; PLAN-11's `runtime.enrolment`

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-45-project-onboarding-unit.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

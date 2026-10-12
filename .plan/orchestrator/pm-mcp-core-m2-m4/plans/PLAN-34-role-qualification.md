# PLAN-34: Role qualification

epic: pm-mcp-core-m2-m4
workstream: WS-05

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-34-role-qualification.md` and is queued as one row file, `queue/PLAN-34.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `role-qualification` of `doc/plans/role-qualification.adoc` — role qualification — as one pull request in this repository with its documentation pull request in `plan-marshall-documentation`. It serves Roadmap Milestone 4 and lands in: pm-core package `core.role`; pm-workflow. The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

## Deliverables

The bullets of `doc/plans/role-qualification.adoc` § What, in its words. A bullet that states what already exists or what follows in a later milestone is context, not a deliverable.

1. Grading a role's configuration against the model verification corpus, its record bound to the qualification digest, its invalidation, and the conservative fallback of an unqualified or unavailable role.
2. The corpus itself and its validation by the build are `plan-marshall-mcp` / `qualification-corpus-in-the-build`.

**Done when** (the package's own acceptance): A changed member of the digest invalidates the record; an unqualified role takes its conservative fallback; a grading run over a fixture corpus with a scripted harness writes the specified record.

**Trace starting set** (documents live in `../plan-marshall-documentation`):

- Requirements: PM-WORK-6, PM-TEST-6
- Specification: Model Work § Role Qualification; Store Schemas § Role Qualification Record
- Watch: Model Work; Store Schemas

## Standing Constraints

Set by the operator for every plan of this epic on 2026-10-09, beside the rules of `CLAUDE.md`:

- **Java 25 is the baseline.** Write for Java 25 and use its language and library features where they fit; no fallback for an older release. The release comes from the parent POM and is not overridden here.
- **HTTP calls go through `cui-http`.** No direct `java.net.http` or other HTTP client. The build forbids `cui-http` in `pm-core` and `pm-workflow`, so an HTTP call belongs in `pm-runtime` or a provider module, behind an interface the foundation declares.
- **HTTP tests use `cui-test-mockwebserver-junit5`.** Every test that involves HTTP runs against it; no hand-made fake server. The operator's statement is the consent `CLAUDE.md` requires for adding it as a test-scope dependency.
- **Work the package as the index says** (`doc/plans/README.adoc` § How to Work a Package, as reworded by #19): the usual plan-marshall flow — a branch, the commits of the plan, a pull request opened at finalize. No draft pull request claims the package; before starting, list the open pull requests for one that names it. The pull request sets the package's row to `done` within its commits. Build the trace matrix of `traced-implementation` before code, deliver the remainder the roadmap item leaves, and finish with `verify`, `verify -Ppre-commit`, `verify -Pcoverage` and `verify -Pintegration-tests` green.
- **Documents.** Javadoc, `package-info.java` and the work package file live here. Requirement and specification documents, the user documentation, the deletion of implemented specification sections and watch items and the roadmap update are made directly in `plan-marshall-documentation` by this plan, in a pull request there that need not name the code pull request; merging it is the operator's.
- **Module documentation (operator decision 2026-10-11).** Documentation of the code lives in its module, in this plan's pull request, not in `plan-marshall-documentation`: the developer documentation of each package this plan changes in `pm-core/doc/developer/core-role.adoc`, `pm-workflow/doc/developer/workflow-model.adoc` (create or extend it: what the package is for, its main types and how they work together, how to use and extend it, invariants and failure modes, links to the specification sections it implements), and one ADR per design decision taken — every operator decision and every choice between alternatives — in `pm-core/doc/adr/core-role/`, `pm-workflow/doc/adr/workflow-model/` as `NNN-{slug}.adoc`, the next free number of that directory, with the sections of `doc/adr/template.adoc` (Status, Date, Context, Decision, Alternatives considered, Consequences, Source) or, before that template exists, those same sections. A decision binding the whole repository goes to `doc/adr/`. The module's `README.adoc` gets one line only when this plan adds a new package; it is otherwise left alone. Layout: workstream WS-10.
- **Settled design: a refused project path is a value.** The confinement check that landed with #19 lives in `pm-core` package `core.store` (`PathConfinement`, `WorkspaceRoots`, `ConfinementOutcome`) and `pm-runtime` package `runtime.workspace` (`WorkspaceConfinement`). A refusal is returned as `ConfinementOutcome` carrying `path_traversal_rejected`; no checked exception type exists or is added. No consumer is wired to it yet (`pm-provider-git` included): a package that performs a file or repository operation for a project adopts the check itself.
- **Do not leave a design choice open that changes the file set.** Where two readings of a deliverable differ in the files they create, ask the operator at refine instead of deferring the choice to implementation.
- **One file is shared by every plan.** In `doc/plans/README.adoc` change only this package's own row.
- **Log messages: the identifier block of WS-05.** New records in `PmMcpLogMessages` take the lowest free identifier of INFO 090-099, WARN 190-199, ERROR 290-299 and stand under one block comment for the workstream, as the existing blocks do. Renumber nothing. The block is shared with the other plans of WS-05: after a rebase, run `PmMcpLogMessagesTest`, which refuses an identifier used twice, and move to the next free one. A plan that needs more than the block holds stops and reports it through its inbox message. The same identifiers go into `doc/LogMessages.adoc` in the documentation pull request.

## Claim Labels

- OBSERVED: the deliverables, the trace starting set and the acceptance above are the package file's own — read at `doc/plans/role-qualification.adoc` § What, Trace, Done When (origin/main at 7d99648)
  - verdict: corroborated | checked_at: c0d4b92f568923098a36992b6c22e78100b84028 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: doc/plans/role-qualification.adoc exists at c0d4b92 (presence checked, content not re-read)
- OBSERVED: the index gives this package the status `open` and the dependencies `role-sets-and-model-caller`, `supervisor` — read at `doc/plans/README.adoc` § Packages
  - verdict: corroborated | checked_at: c0d4b92f568923098a36992b6c22e78100b84028 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: index row of role-qualification still reads open as claimed at c0d4b92; dependencies cell: link:role-sets-and-model-caller.adoc[`role-sets-and-model-caller`]; link:supervisor.adoc[`supervisor`]
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/` exists and holds `package-info.java` today, so the package is greenfield apart from its package description — directory listing on 2026-10-09
  - verdict: corroborated | checked_at: c0d4b92f568923098a36992b6c22e78100b84028 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: pm-core/src/main/java/de/planmarshall/core/role/ still holds only package-info.java at c0d4b92
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/model/` exists and holds `package-info.java` today, so the package is greenfield apart from its package description — directory listing on 2026-10-09
  - verdict: corroborated | checked_at: c0d4b92f568923098a36992b6c22e78100b84028 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: pm-workflow/src/main/java/de/planmarshall/workflow/model/ still holds only package-info.java at c0d4b92
- HYPOTHESIS: part of this package lands in `pm-workflow` package `workflow.model`. This is a guess: the package file names only the module, and no package description of the package table (read 2026-10-10) covers the subject. The same kind of guess was refuted twice for PLAN-03, which landed in `core.store` and a new package `runtime.workspace` after an operator answer. Expect to ask the operator for the package home at refine — confirm/refute at `../plan-marshall-documentation/doc/specification/module-structure.adoc` § the package table of `pm-workflow` (verify-at-outline)
  - verdict: unverifiable | checked_at: c0d4b92f568923098a36992b6c22e78100b84028 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line of the package file, not from a trace matrix — confirm/refute at the trace matrix of `traced-implementation` (verify-at-outline)
  - verdict: unverifiable | checked_at: c0d4b92f568923098a36992b6c22e78100b84028 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- OBSERVED: `PmMcpLogMessagesTest` refuses an identifier used twice and one outside the range of its level, and the scheme is INFO 001-099, WARN 100-199, ERROR 200-299 with 10-29, 30 and 40 of each level in use — read at `pm-core/src/test/java/de/planmarshall/core/log/PmMcpLogMessagesTest.java` § `unique`, `ranges` and `pm-core/src/main/java/de/planmarshall/core/log/PmMcpLogMessages.java`
  - verdict: corroborated | checked_at: c0d4b92f568923098a36992b6c22e78100b84028 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: the guard test exists; identifiers in use at c0d4b92: [1, 10, 11, 12, 13, 14, 15, 17, 30, 40, 50, 51, 52, 53, 54, 55, 56, 57, 110, 111, 112, 113, 130, 140, 150, 151, 152, 153, 154, 155, 156, 157, 158, 159, 160, 210, 230, 250, 251, 252, 253] (more blocks than the claim lists; ranges per workstream unchanged)
- HYPOTHESIS: the package adds INFO, WARN or ERROR messages; `PmMcpLogMessages` is deliberately NOT in the Expected Surface, because the operator chose per-workstream identifier blocks on 2026-10-09 so that concurrent plans add in different places — confirm/refute the need at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: c0d4b92f568923098a36992b6c22e78100b84028 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- HYPOTHESIS: the package makes no HTTP call, so the two HTTP constraints bind only if the trace matrix shows one — confirm/refute at the specification sections of the trace starting set (verify-at-outline)
  - verdict: unverifiable | checked_at: c0d4b92f568923098a36992b6c22e78100b84028 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- Verify-first clause: before scoping, read the roadmap item this package serves for what the code already covers, list the open pull requests for one that names `role-qualification`, and check in `doc/plans/README.adoc` that every dependency is `done`; a refutation of any claim above loops back to re-scope.
  - verdict: unverifiable | checked_at: c0d4b92f568923098a36992b6c22e78100b84028 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: verify-first clause is executed by the plan at refine

## Expected Surface

- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/` — production code
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/model/` — production code; package inferred, verify-at-outline
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/role/` — tests; new directory
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/model/` — tests; new directory; package inferred, verify-at-outline
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only
- HYPOTHESIS: `pm-core/doc/developer/core-role.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-core/doc/adr/core-role/` — ADRs of the package's design decisions; new directory if absent
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-model.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-model/` — ADRs of the package's design decisions; new directory if absent

## Dependencies and Sequencing

- Depends on: PLAN-26 (`role-sets-and-model-caller`); PLAN-30 (`supervisor`)
- Overlaps with: PLAN-26 (`role-sets-and-model-caller`) in `core.role`, `workflow.model`; every plan through `doc/plans/README.adoc`; the plans of WS-05 through their shared identifier block in `PmMcpLogMessages`, which is not a declared surface
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` and the client contract `pm-api` in `pm-mcp-clients`, which stay untouched — a change there is a pull request in that repository; and `doc/LogMessages.adoc` of `plan-marshall-documentation`, which goes into the documentation pull request

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-34-role-qualification.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

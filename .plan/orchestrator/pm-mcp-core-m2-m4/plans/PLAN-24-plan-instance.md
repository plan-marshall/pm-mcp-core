# PLAN-24: Plan instance composition

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-24-plan-instance.md` and is queued as one row file, `queue/PLAN-24.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `plan-instance` of `doc/plans/plan-instance.adoc` — plan instance composition — as one pull request in this repository with its documentation pull request in `plan-marshall-documentation`. It serves Roadmap Milestone 3 and lands in: pm-workflow package `workflow.plan`. The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

## Deliverables

The bullets of `doc/plans/plan-instance.adoc` § What, in its words. A bullet that states what already exists or what follows in a later milestone is context, not a deliverable.

1. Composition of a plan instance from templates and facts with provenance; immutable versions with the composition log and the closed set of composition events; the position rule.
2. The instance in force rendered by the diagram generator as a read-only resource.

**Done when** (the package's own acceptance): A composed instance verifies; each composition event produces a new version with its cause; a version that lacks the current state or a state of the call stack is refused and the old one kept.

**Trace starting set** (documents live in `../plan-marshall-documentation`):

- Requirements: PM-IMPL-5, PM-WF-1
- Specification: Workflow DSL; Store Schemas § Plan Instance, Waivers & Questions; Domain Extensions § Configuration
- Watch: Workflow DSL; Store Schemas

## Standing Constraints

Set by the operator for every plan of this epic on 2026-10-09, beside the rules of `CLAUDE.md`:

- **Java 25 is the baseline.** Write for Java 25 and use its language and library features where they fit; no fallback for an older release. The release comes from the parent POM and is not overridden here.
- **HTTP calls go through `cui-http`.** No direct `java.net.http` or other HTTP client. The build forbids `cui-http` in `pm-core` and `pm-workflow`, so an HTTP call belongs in `pm-runtime` or a provider module, behind an interface the foundation declares.
- **HTTP tests use `cui-test-mockwebserver-junit5`.** Every test that involves HTTP runs against it; no hand-made fake server. The operator's statement is the consent `CLAUDE.md` requires for adding it as a test-scope dependency.
- **Work the package as the index says** (`doc/plans/README.adoc` § How to Work a Package): claim it with a draft pull request that sets its row to `in progress`, build the trace matrix of `traced-implementation` before code, deliver the remainder the roadmap item leaves, and finish with `verify`, `verify -Ppre-commit`, `verify -Pcoverage` and `verify -Pintegration-tests` green. The documentation pull request in `plan-marshall-documentation` names the code pull request and merges after it.
- **One file is shared by every plan.** In `doc/plans/README.adoc` change only this package's own row.
- **Log messages: the identifier block of WS-03.** New records in `PmMcpLogMessages` take the lowest free identifier of INFO 070-079, WARN 170-179, ERROR 270-279 and stand under one block comment for the workstream, as the existing blocks do. Renumber nothing. The block is shared with the other plans of WS-03: after a rebase, run `PmMcpLogMessagesTest`, which refuses an identifier used twice, and move to the next free one. A plan that needs more than the block holds stops and reports it through its inbox message. The same identifiers go into `doc/LogMessages.adoc` in the documentation pull request.

## Claim Labels

- OBSERVED: the deliverables, the trace starting set and the acceptance above are the package file's own — read at `doc/plans/plan-instance.adoc` § What, Trace, Done When (origin/main at c5f5397)
- OBSERVED: the index gives this package the status `open` and the dependencies `workflow-engine`, `project-units-and-invariants`, `diagram-generator` — read at `doc/plans/README.adoc` § Packages
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/` exists and holds `package-info.java` today, so the package is greenfield apart from its package description — directory listing on 2026-10-09
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line of the package file, not from a trace matrix — confirm/refute at the trace matrix of `traced-implementation` (verify-at-outline)
- OBSERVED: `PmMcpLogMessagesTest` refuses an identifier used twice and one outside the range of its level, and the scheme is INFO 001-099, WARN 100-199, ERROR 200-299 with 10-29, 30 and 40 of each level in use — read at `pm-core/src/test/java/de/planmarshall/core/log/PmMcpLogMessagesTest.java` § `unique`, `ranges` and `pm-core/src/main/java/de/planmarshall/core/log/PmMcpLogMessages.java`
- HYPOTHESIS: the package adds INFO, WARN or ERROR messages; `PmMcpLogMessages` is deliberately NOT in the Expected Surface, because the operator chose per-workstream identifier blocks on 2026-10-09 so that concurrent plans add in different places — confirm/refute the need at the trace matrix (verify-at-outline)
- HYPOTHESIS: the package makes no HTTP call, so the two HTTP constraints bind only if the trace matrix shows one — confirm/refute at the specification sections of the trace starting set (verify-at-outline)
- Verify-first clause: before scoping, read the roadmap item this package serves for what the code already covers, list the open pull requests for one that names `plan-instance`, and check in `doc/plans/README.adoc` that every dependency is `done`; a refutation of any claim above loops back to re-scope.

## Expected Surface

- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/` — production code
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/plan/` — tests; new directory
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only

## Dependencies and Sequencing

- Depends on: PLAN-13 (`workflow-engine`); PLAN-17 (`project-units-and-invariants`); PLAN-16 (`diagram-generator`)
- Overlaps with: no other plan by package; every plan through `doc/plans/README.adoc`; the plans of WS-03 through their shared identifier block in `PmMcpLogMessages`, which is not a declared surface
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` and the client contract `pm-api` in `pm-mcp-clients`, which stay untouched — a change there is a pull request in that repository; and `doc/LogMessages.adoc` of `plan-marshall-documentation`, which goes into the documentation pull request

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-24-plan-instance.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

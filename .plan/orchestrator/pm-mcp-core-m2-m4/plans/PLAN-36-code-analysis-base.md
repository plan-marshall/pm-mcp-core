# PLAN-36: Code analysis base: format, interface and contract

epic: pm-mcp-core-m2-m4
workstream: WS-07

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-36-code-analysis-base.md` and is queued as one row file, `queue/PLAN-36.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `code-analysis-base` of `doc/plans/code-analysis-base.adoc` — code analysis base: format, interface and contract — as one pull request in this repository with its documentation pull request in `plan-marshall-documentation`. It serves Roadmap Milestone 6 and lands in: pm-core package `core.analysis`; the findings types in `core.findings`. The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

## Deliverables

The bullets of `doc/plans/code-analysis-base.adoc` § What, in its words. A bullet that states what already exists or what follows in a later milestone is context, not a deliverable.

1. *Format and serialization*: the analysis result record as a Java record with its nested types (target, quality gate with its conditions, the counts), serialized as one canonical JSON line with every member present, and its consistency rules checked on construction. The enumeration `AnalysisStatus` exists.
2. *Provider-neutral findings*: the finding types carry `source: analysis` with the provider's id and `provider_ref.issue_key`; nothing in `pm-core` names a single provider.
3. *Interface*: `CodeAnalysisProvider` with `id()`, `status(target)` and `issues(target)`, and the registry of providers by id, whose members are passed to its constructor.
4. *Contract*: the operations `analysis.status` and `analysis.issues` with their typed input, their closed outcomes including the shared transport outcomes, and their output.
5. Declarations, serialization and validation only: no HTTP call and no store write. The store write comes with the store substrate, the Sonar implementation with roadmap Milestone 6.

**Done when** (the package's own acceptance): The record round-trips byte for byte through its JSON line and validates against the schema of the specification, which the test reads from the documentation checkout; one refusing test per consistency rule; a fixture provider registered under its id is found by the registry and a duplicate id is refused; each operation returns each of its outcomes in a test; a build test fails when a class of `pm-core` names a provider (control fixture); an architecture test fails when a class of `core.analysis` or `core.findings` references a network API or writes a file (control fixture), so that the package stays declarations, serialization and validation.

**Trace starting set** (documents live in `../plan-marshall-documentation`):

- Requirements: PM-IMPL-1, PM-IMPL-2
- Specification: Module Structure § Findings & Code-Analysis Contracts; Store Schemas § Analysis Result Record; § Findings Ledger Record; Hypermedia Format § `analysis_status`; Job Runtime § Outcome Derivation
- Watch: PM-WATCH-MOD-8; Store Schemas; Cross-Cutting

## Standing Constraints

Set by the operator for every plan of this epic on 2026-10-09, beside the rules of `CLAUDE.md`:

- **Java 25 is the baseline.** Write for Java 25 and use its language and library features where they fit; no fallback for an older release. The release comes from the parent POM and is not overridden here.
- **HTTP calls go through `cui-http`.** No direct `java.net.http` or other HTTP client. The build forbids `cui-http` in `pm-core` and `pm-workflow`, so an HTTP call belongs in `pm-runtime` or a provider module, behind an interface the foundation declares.
- **HTTP tests use `cui-test-mockwebserver-junit5`.** Every test that involves HTTP runs against it; no hand-made fake server. The operator's statement is the consent `CLAUDE.md` requires for adding it as a test-scope dependency.
- **Work the package as the index says** (`doc/plans/README.adoc` § How to Work a Package): claim it with a draft pull request that sets its row to `in progress`, build the trace matrix of `traced-implementation` before code, deliver the remainder the roadmap item leaves, and finish with `verify`, `verify -Ppre-commit`, `verify -Pcoverage` and `verify -Pintegration-tests` green. The documentation pull request in `plan-marshall-documentation` names the code pull request and merges after it.
- **One file is shared by every plan.** In `doc/plans/README.adoc` change only this package's own row.
- **Log messages: the identifier block of WS-07.** New records in `PmMcpLogMessages` take the lowest free identifier of INFO 002-009, WARN 102-109, ERROR 202-209 and stand under one block comment for the workstream, as the existing blocks do. Renumber nothing. The block is shared with the other plans of WS-07: after a rebase, run `PmMcpLogMessagesTest`, which refuses an identifier used twice, and move to the next free one. A plan that needs more than the block holds stops and reports it through its inbox message. The same identifiers go into `doc/LogMessages.adoc` in the documentation pull request.

## Claim Labels

- OBSERVED: the deliverables, the trace starting set and the acceptance above are the package file's own — read at `doc/plans/code-analysis-base.adoc` § What, Trace, Done When (origin/main at c5f5397)
- OBSERVED: the index gives this package the status `open` and the dependencies nothing inside this repository — read at `doc/plans/README.adoc` § Packages
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/analysis/` exists and holds `AnalysisStatus.java`, `package-info.java` today, so the package is an extension of those declarations — directory listing on 2026-10-09
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/findings/` exists and holds `Disposition.java`, `FindingResponder.java`, `FindingSource.java`, `package-info.java` today, so the package is an extension of those declarations — directory listing on 2026-10-09
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line of the package file, not from a trace matrix — confirm/refute at the trace matrix of `traced-implementation` (verify-at-outline)
- OBSERVED: `PmMcpLogMessagesTest` refuses an identifier used twice and one outside the range of its level, and the scheme is INFO 001-099, WARN 100-199, ERROR 200-299 with 10-29, 30 and 40 of each level in use — read at `pm-core/src/test/java/de/planmarshall/core/log/PmMcpLogMessagesTest.java` § `unique`, `ranges` and `pm-core/src/main/java/de/planmarshall/core/log/PmMcpLogMessages.java`
- HYPOTHESIS: the package adds INFO, WARN or ERROR messages; `PmMcpLogMessages` is deliberately NOT in the Expected Surface, because the operator chose per-workstream identifier blocks on 2026-10-09 so that concurrent plans add in different places — confirm/refute the need at the trace matrix (verify-at-outline)
- HYPOTHESIS: the package makes no HTTP call, so the two HTTP constraints bind only if the trace matrix shows one — confirm/refute at the specification sections of the trace starting set (verify-at-outline)
- Verify-first clause: before scoping, read the roadmap item this package serves for what the code already covers, list the open pull requests for one that names `code-analysis-base`, and check in `doc/plans/README.adoc` that every dependency is `done`; a refutation of any claim above loops back to re-scope.

## Expected Surface

- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/analysis/` — production code
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/findings/` — production code
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/analysis/` — tests
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/findings/` — tests
- HYPOTHESIS: `src/guard-controls/` — control fixtures of the two build and architecture tests
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only

## Dependencies and Sequencing

- Depends on: none
- Overlaps with: no other plan by package; every plan through `doc/plans/README.adoc`; the plans of WS-07 through their shared identifier block in `PmMcpLogMessages`, which is not a declared surface
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` and the client contract `pm-api` in `pm-mcp-clients`, which stay untouched — a change there is a pull request in that repository; and `doc/LogMessages.adoc` of `plan-marshall-documentation`, which goes into the documentation pull request

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-36-code-analysis-base.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

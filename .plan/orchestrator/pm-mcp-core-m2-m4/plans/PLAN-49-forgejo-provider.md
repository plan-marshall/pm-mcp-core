# PLAN-49: Codeberg and Forgejo as a provider

epic: pm-mcp-core-m2-m4
workstream: WS-08

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-49-forgejo-provider.md` and is queued as one row file, `queue/PLAN-49.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `forgejo-provider` of `doc/plans/forgejo-provider.adoc`: codeberg and Forgejo as a provider. Serves: roadmap Milestone 6 (the native `pm-provider-forgejo` for Codeberg and every Forgejo or Gitea forge); Use Cases, UC-STA-4; issues of Codeberg in both clients of `pm-mcp-clients`. Where: pm-providers (the module `pm-provider-forgejo`, listed in the Module Structure Specification); pm-core (the value `forgejo` in every closed list of providers). The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

**Parked:** the index gives the package the status `blocked`; the CI contract of `pm-provider-ci` (no work package). Re-ground this spec before emitting it.

## Deliverables

The bullets of `doc/plans/forgejo-provider.adoc` § What, in its words (regenerated at 710da61, after the specification moved into this repository).

1. Implements Module Structure § Codeberg and Forgejo (`pm-provider-forgejo`) (`PM-EXEC-5`, `PM-TECH-2`).
2. The CI contract of `pm-provider-ci` over the Forgejo REST API (`/api/v1`) on the shared origin-pinned HTTP base, the native variant only, `ci.issue-view` and `ci.issue-comment` first, then the rest of the contract for a project whose remote is on a Forgejo forge; the findings contract of `pm-core` for its pull request reviews.
3. The host detection (`codeberg.org`, else a probe of `/api/v1/version`) for the derived fact `ci_provider`; the value `forgejo` in every closed list of providers and integrations (the team's `ci_provider` override, the machine's provider defaults, a project's integrations, the audit's integration records); the credential key `forgejo`, whose entry's `url` names the forge's host.

**Done when** (the package's own acceptance): The provider reads an issue of a public and of a private repository against a recorded Forgejo API, and detects Codeberg and a self-hosted Forgejo.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Use case: UC-STA-4
- Requirements: PM-EXEC-5; PM-TECH-2
- Specification: Module Structure § Codeberg and Forgejo; Store Schemas § Machine Provider Defaults; § Operator Local Configuration; Store Schemas § Project Configuration; § Derived Facts; CLI & Security § Agent Credentials

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
- **Log messages: the holder of your module, next free identifier** (operator decision 2026-10-13; pm-mcp-core #64, ADR `doc/adr/005-one-log-message-holder-per-module.adoc`). A new INFO, WARN or ERROR record goes into the holder of the module whose production code logs it, at the next free identifier of its level (INFO 1-99, WARN 100-199, ERROR 200-299, the same ranges in every holder); for this plan: `ForgejoLogMessages` (`de.planmarshall.provider.forgejo.log`, prefix `PM_PROVIDER_FORGEJO`, created with its first record); `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`). A record two modules log goes to the lower one (`pm-core`); a module logs only through its own holder and `CoreLogMessages`. There are no workstream blocks and nothing is renumbered. A provider's first record creates its holder `de.planmarshall.provider.<x>.log.<X>LogMessages` (prefix `PM_PROVIDER_<X>`) with a `package-info.java`, and adds the prefix to `KNOWN_PREFIXES` in `CoreLogMessagesTest`. After a rebase, run the holder's test, which refuses an identifier used twice or outside its level's range, and move to the next free one. The same identifiers go into `doc/LogMessages.adoc` of this repository, under the holder's section.

- **Format versions stay 1 (operator decision 2026-10-13).** Nothing is released yet, so every versioned artifact (`format_version` of a store document, record, sidecar, journal, codec) stays at version 1. A format change replaces version 1 in place: no version bump, no reader for an older version, no migration. Do not raise a `FORMAT_VERSION` constant, a `format_version` in a fixture, schema or specification example, or an ADR that records a bump.

## Claim Labels

- OBSERVED: the Objective, Deliverables, Done when, Trace and Depends on above are the package file's and its index row's own — regenerated from `doc/plans/forgejo-provider.adoc` and `doc/plans/README.adoc` at 710da61 (branch `docs/specification-into-core`, pm-mcp-core #77), after the specification moved into this repository and the package was aligned with the changed specification; the claims below were checked before that
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: doc/plans/forgejo-provider.adoc is unchanged since d1d58d2 (#77, no later commit touches it); its What, Trace and Done When equal the Deliverables, trace set and acceptance above
- OBSERVED: the package file and its index row (`blocked`; depends on the CI contract of `pm-provider-ci` (no work package)) — read at `doc/plans/forgejo-provider.adoc` and `doc/plans/README.adoc` (main at a8a4214, regenerated by cleanup)
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: README.adoc row forgejo-provider re-read at 238c823: blocked; depends on the CI contract of `pm-provider-ci` (no work package); matches the claim
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line only — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: the Expected Surface mirrors the Where line of doc/plans/forgejo-provider.adoc; listed paths exist or are declared new; packages the Where line names but the surface omitted were added by cleanup
- Verify-first clause: before scoping, check that the index no longer says `blocked`, that the use cases are agreed, and which of the packages this one restructures have shipped since; a refutation loops back to re-scope.
  - verdict: corroborated | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: row forgejo-provider is blocked in the index at c281f6c; open PRs 71, 86 and 87 (ci pr view): none names the package; the dependencies not done are listed in the spec's Depends on line

## Expected Surface

- HYPOTHESIS: `pm-providers/pm-provider-forgejo/` — inferred from the `Where` line
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/findings/` — inferred from the `Where` line
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/ProjectEnrolment.java` — the closed list of integrations and providers (wire name `gitlab`) lives here, not only in `core.findings`; added by cleanup on 2026-10-16
- HYPOTHESIS: `pm-providers/pom.xml` — a new module needs a module entry and a `KNOWN_PREFIXES` entry; added by cleanup on 2026-10-16
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only
- HYPOTHESIS: `pm-core/doc/developer/core-findings.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-core/doc/adr/core-findings/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- OBSERVED: `doc/plans/forgejo-provider.adoc` — the package file, deleted when the package is done
- OBSERVED: `doc/specification/credentials-and-enrolment/01-provider-credentials.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/07-project-configuration.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/08-project-configuration-local.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/09-derived-facts-and-build-timings.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/12-machine-stores.adoc` — specification part; the implemented sections deleted

## Dependencies and Sequencing

- Depends on: the CI contract of `pm-provider-ci` (no work package)

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-49-forgejo-provider.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

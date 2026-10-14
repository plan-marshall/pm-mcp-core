# PLAN-46: The onboarding of the machine

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-46-machine-onboarding-unit.md` and is queued as one row file, `queue/PLAN-46.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `machine-onboarding-unit` of `doc/plans/machine-onboarding-unit.adoc` (added to the index on 2026-10-12): the onboarding of the machine. Serves: Use Cases, UC-ONB-1 to UC-ONB-10; the onboarding and the machine's settings in both clients of `pm-mcp-clients`; no roadmap milestone yet. Where: pm-workflow (the onboarding's sub-workflows in the machine scope, the machine's settings, the plan axes and overrides in the composition); pm-core (the consent, catalogue, steward, role, and credential stores); pm-runtime (the harness scan and probes, provider verification). It writes its specification changes into `plan-marshall-documentation` itself, first.

**Parked:** the index gives the package the status `blocked` — it waits on use cases to be agreed in `plan-marshall-documentation` and on the packages below, not on code alone. Re-ground this spec against the agreed use cases and the specification before emitting it; this spec was generated from the package file and has had no orchestrator review beyond that.

## Deliverables

The bullets of `doc/plans/machine-onboarding-unit.adoc` § What, in its words.

1. Specification first*: the package writes the engine side of the onboarding's changes into `plan-marshall-documentation` as part of its own work, once UC-ONB is agreed (Specification Changes for the Clients, Packages); `onboarding-contract` of `pm-mcp-clients` writes the client side beside it.
2. The onboarding's sub-workflows in the machine scope of `operator-wait-and-machine-scope`: languages, harnesses (one scan, a default), models, roles, credentials, providers, plan defaults; a later run of one aspect through `onboard.entry` (`reconfigure`).
3. The consent store `consents.json` and its closed list of kinds, among them `check_credential_store` (asked in `store.consent`, only for an OS store; the file store is checked without one) and `verify_providers` (asked in `providers.verify-consent`); a project's kind with its project, and `run_checkout_scripts` with the digest of each script, in this store only, never in an enrolment; the consent state before every action; an act of the operator's own, a credential the operator types, stored without a consent.
4. The harness scan, the login and model probes, and the verification of an unknown harness, as confined jobs; the profile fields they read. The step needs one usable harness: the last chosen one cannot be left out, and when none is found or every chosen one fails its probe the step goes to `harness.none` (an operator wait: recheck, add one, or later, the onboarding resting there).
5. The model catalogue, the default and the operator's role assignment; the steward role outside both: not among the roles `roles.load` lists, always on the default harness with that harness's default model, outside the catalogue (`model_not_allowed` never applies to it), exempt from qualification, and its submits.
6. The credential store choice and its move; the machine's provider defaults and their verification.
7. The automatic onboarding with its consent and its result (reasons, items); a state it cannot decide alone (a login, a token, no harness found, the model ids of a harness that cannot list its models) shown as in the guided run.
8. The machine's settings* (UC-ONB-10), states of the onboarding unit reached from `onboard.completed`: `settings.choose` (the recorded aspects, the remembered consents, the documents mode, the credentials with their status); reconfigure an aspect as a later run of its sub-workflow; withdraw a consent (`settings.withdraw`, `settings.remove-consent`, audited, asked again at its next action); the documents mode (`settings.documents-mode`, `settings.record`: the machine key `sources.documents_mode`, `copy`, `move`, or `ask`, the default, which `start-from-clients` reads); a credential rotated or replaced (`settings.credential`, `settings.store-credential`, every token derived from the old secret dropped), verified again through `providers.verify-consent`, `providers.verify`, and `providers.failed`, after which a plan held by the expired credential goes on.
9. Concurrent changes of a setting* (Concurrent Changes of a Setting): every change of a recorded value, of the machine's settings here, of a project's operator values in `project-onboarding-unit`, and of a plan's own values in `plan-and-epic-control`, carries the revision of the settings it was made against; the first change wins, a later one against an older revision is refused with `conflict` (`409`) and the current value, which the operator may change again on top of it; nothing is merged and nothing is overwritten unseen.
10. The plan behaviour axes and plan overrides in the composition: each axis a call site, each variant a sub-workflow named with its axis as prefix; the landing axis a call site inside the phase `finalize`, which keeps its checks and reviews, with `landing.pull-request` (push, pull request, its reviews, merge), `landing.main` (commit and push on the base branch), and `landing.files` (no commit, the plan handed back), apart from the land lane; `method.*` and `ask.*`; the prune reason `plan_default`, the composition event `override`, their binding where the plan first needs them; `overrides.json` with a role's harness, model, and effort, the variant of each axis and of an integration, and the optional providers.
11. Validation of the landing*: `defaults.validate` checks only what the machine provides (a provider for `landing.pull-request`), never a repository; a landing variant is validated where a project is known (its setup, a plan's start, a plan's settings), refusing `landing.pull-request` without a provider and `landing.main` on a protected base branch with the reason, also a machine default that does not fit the project.

**Done when** (the package's own acceptance): The specification is merged in `plan-marshall-documentation`; the onboarding runs from its first state to `onboard.completed` and as a later run of each aspect, against the scenarios of `onboarding-contract` replayed on the runtime; no action runs without its consent, the store check and the provider verification included, and a typed credential is stored without one; a run with every chosen harness failing rests in `harness.none`; the steward runs outside the catalogue and the assignment; the automatic run reports each value it chose; each branch of the machine's settings is tested, a withdrawn consent asked again, a rotated credential verified and a plan held by the old one going on; of two changes of a setting made against the same revision the first is recorded and the second refused with `conflict` and the current value; a landing variant is refused with its reason where it does not fit a project, and the machine default is never checked against a repository.

**Trace starting set** (documents live in `../plan-marshall-documentation`):

* Use cases: UC-ONB-1 to UC-ONB-10 and their changes, among them Consent Before Acting, Steward Role, Plan Behaviour Axes, Concurrent Changes of a Setting, and Plan Overrides
* Specification: Workflow DSL § Plan Instance; Model Work § Roles & Configuration; Store Schemas § Machine Stores; Client Profiles § Profile Schema

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
- **Log messages: the holder of your module, next free identifier** (operator decision 2026-10-13; pm-mcp-core #64, ADR `doc/adr/005-one-log-message-holder-per-module.adoc`). A new INFO, WARN or ERROR record goes into the holder of the module whose production code logs it, at the next free identifier of its level (INFO 1-99, WARN 100-199, ERROR 200-299, the same ranges in every holder); for this plan: `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`); `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`). A record two modules log goes to the lower one (`pm-core`); a module logs only through its own holder and `CoreLogMessages`. There are no workstream blocks and nothing is renumbered. A provider's first record creates its holder `de.planmarshall.provider.<x>.log.<X>LogMessages` (prefix `PM_PROVIDER_<X>`) with a `package-info.java`, and adds the prefix to `KNOWN_PREFIXES` in `CoreLogMessagesTest`. After a rebase, run the holder's test, which refuses an identifier used twice or outside its level's range, and move to the next free one. The same identifiers go into `doc/LogMessages.adoc` of `plan-marshall-documentation`, under the holder's section, in the documentation pull request.

- **Format versions stay 1 (operator decision 2026-10-13).** Nothing is released yet, so every versioned artifact (`format_version` of a store document, record, sidecar, journal, codec) stays at version 1. A format change replaces version 1 in place: no version bump, no reader for an older version, no migration. Do not raise a `FORMAT_VERSION` constant, a `format_version` in a fixture, schema or specification example, or an ADR that records a bump.

## Claim Labels

- OBSERVED: the package file and its index row (`blocked`; depends on `operator-wait-and-machine-scope`; UC-ONB agreed (no work package)) — read at `doc/plans/machine-onboarding-unit.adoc` and `doc/plans/README.adoc` (main at 557ee81)
  - verdict: corroborated | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: doc/plans/machine-onboarding-unit.adoc exists at fa58b0d (presence checked, content not re-read)
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line only — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- Verify-first clause: before scoping, check that the index no longer says `blocked`, that the use cases are agreed, and which of the packages this one restructures have shipped since; a refutation loops back to re-scope.
  - verdict: unverifiable | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: verify-first clause is executed by the plan at refine

## Expected Surface

- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/unit/` — inferred from the `Where` line
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/` — inferred from the `Where` line
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-unit.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-unit/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- HYPOTHESIS: `pm-core/doc/developer/core-store.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-core/doc/adr/core-store/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)

## Dependencies and Sequencing

- Depends on: PLAN-42 (`operator-wait-and-machine-scope`); UC-ONB agreed (no work package)

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-46-machine-onboarding-unit.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

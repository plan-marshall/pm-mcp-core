# PLAN-50: A recipe is a workflow

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-50-recipe-workflows.md` and is queued as one row file, `queue/PLAN-50.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `recipe-workflows` of `doc/plans/recipe-workflows.adoc`: a recipe is a workflow. Serves: roadmap Milestone 7 (the start of a recipe as its own workflow); Use Cases, UC-STA-5; running a recipe in both clients of `pm-mcp-clients`. Where: pm-workflow (the recipe's workflow, composition); pm-core (the recipe descriptor). The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

**Parked:** waits on packages that are not done: `phase-sub-workflows`, `start-from-clients`, `unit-frontmatter-and-types`, `plan-instance-alignment`. Re-ground this spec before emitting it.

## Deliverables

The bullets of `doc/plans/recipe-workflows.adoc` § What, in its words (regenerated at 710da61, after the specification moved into this repository).

1. Implements Domain Extensions § A Recipe Is a Workflow with its descriptor schema, and State Catalogue § Recipe Choice `recipe` (`PM-EXT-4`).
2. A recipe is a unit of type `recipe` without entries and exits (the unit types and the frontmatter are `unit-frontmatter-and-types`'s) beside its declarative descriptor: a workflow of phase sub-workflows, its own where it differs and the plan's shared ones (finalize, the landing) elsewhere, its parameters, and whether it takes sources; starting it creates a plan whose process is that workflow; the recipe states of Phases 1 and 3 go. Recipes come from the domains' bundles (built in at build time, a duplicate key failing the build), from the machine (`<PM_MCP_BASE>/recipes/<recipe_id>.json` with its `.pmwf`, offered in every project), and from the project (`.marshall/recipes/`), a project's recipe taking the place of one of the same key; an empty harvest ends the recipe's plan with the composition event and prune reason `empty_harvest`. The simplify recipe is its own workflow, so no composition rule keys on `recipe_id`: the removal of the shipped rule `simplify-recipe` and its reason `recipe_override` is `plan-instance-alignment`'s.
3. Through `start.ready`: a recipe's start passes `start.ready` of `start-from-clients` like a plan's (the recipe and its parameters, the title and id proposed from the recipe and the time, both changeable); `recipe.create` re-checks what creation needs and returns to `start.ready` with the cause where the recipe, the id, or a gate changed meanwhile, creating nothing. A parameter without an option is shown with its reason and the recipe cannot run; a request-scoped recipe gathers its sources as a plan does, without a chat; leaving keeps the entry with the recipe and its parameters.

**Done when** (the package's own acceptance): A recipe runs as its own workflow from its parameters to its landing, an empty harvest ends it without a pull request, and no recipe state is left in the plan's phases; the simplify recipe runs as its own workflow, its steps not pruned by a rule on `recipe_id`; a recipe's start passes `start.ready`, and a change found at `recipe.create` returns there with its cause.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Use case: UC-STA-5
- Requirements: PM-EXT-4
- Specification: Domain Extensions § A Recipe Is a Workflow; State Catalogue § Recipe Choice; Workflow DSL § Unit Types; Phase Workflows (the recipe source and states); Workflow DSL § Plan Instance

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
- **Specifications can be test input (found by PLAN-69, 2026-10-18).** A schema-parity test reads blocks of the `.adoc` specifications under `doc/specification/` (for example `WorkflowStatusSchemaParityTest` reads the JSON schema block after `== Workflow State Store` of `store-schemas/02-workflow-state-store.adoc`). Before the outline deletes or edits the implemented sections of a specification part, search the test tree for readers of that file, list each as a read file of the deliverable and state which blocks must stay byte-stable; a block a test reads is not deleted with the covered prose.
- **Checklist for `pm-workflow` code (found by PLAN-69, 2026-10-18).** (1) A refusal added for a workflow-DSL construct has exactly one owner, the parser or the verifier; every verifier refusal fixture is a tree the parser accepts, and fixture-count success criteria are written after that split. (2) A new pre-parsing layer in front of the lexer accepts at least what the lexer accepts (line endings, whitespace); add a CRLF twin fixture for every new line-oriented reader. (3) Where a printer and a reader must agree on a lexical rule, the printer calls the reader's predicate. (4) A completeness test over an enum derives the expected set from the enum at run time. (5) An exception to an invariant for one caller is scoped to that caller, with a refusing fixture from every other caller that reaches the check. (6) When a concept is generalised (start state to starting states), sweep every diagnostic message that names the old one.
- **Renumber the ADRs against `origin/main` before finalize and after every rebase (found by PLAN-14, 2026-10-18).** An ADR added to a module ADR directory (for example `pm-core/doc/adr/core-store/`) can take a number another plan took in the same directory; different slugs mean different file names, so git reports no conflict and the duplicate-number gate of the branch-cleanup step scans only `doc/adr`. List the touched ADR directories with `git ls-tree origin/main` and renumber the plan's ADRs to the next free numbers; PLAN-14 renumbered twice.
- **Log messages: the holder of your module, next free identifier** (operator decision 2026-10-13; pm-mcp-core #64, ADR `doc/adr/005-one-log-message-holder-per-module.adoc`). A new INFO, WARN or ERROR record goes into the holder of the module whose production code logs it, at the next free identifier of its level (INFO 1-99, WARN 100-199, ERROR 200-299, the same ranges in every holder); for this plan: `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`); `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`). A record two modules log goes to the lower one (`pm-core`); a module logs only through its own holder and `CoreLogMessages`. There are no workstream blocks and nothing is renumbered. A provider's first record creates its holder `de.planmarshall.provider.<x>.log.<X>LogMessages` (prefix `PM_PROVIDER_<X>`) with a `package-info.java`, and adds the prefix to `KNOWN_PREFIXES` in `CoreLogMessagesTest`. After a rebase, run the holder's test, which refuses an identifier used twice or outside its level's range, and move to the next free one. The same identifiers go into `doc/LogMessages.adoc` of this repository, under the holder's section.

- **Format versions stay 1 (operator decision 2026-10-13).** Nothing is released yet, so every versioned artifact (`format_version` of a store document, record, sidecar, journal, codec) stays at version 1. A format change replaces version 1 in place: no version bump, no reader for an older version, no migration. Do not raise a `FORMAT_VERSION` constant, a `format_version` in a fixture, schema or specification example, or an ADR that records a bump.

## Claim Labels

- OBSERVED: the Objective, Deliverables, Done when, Trace and Depends on above are the package file's and its index row's own — regenerated from `doc/plans/recipe-workflows.adoc` and `doc/plans/README.adoc` at 710da61 (branch `docs/specification-into-core`, pm-mcp-core #77), after the specification moved into this repository and the package was aligned with the changed specification; the claims below were checked before that
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: doc/plans/recipe-workflows.adoc is unchanged since d1d58d2 (#77, no later commit touches it); its What, Trace and Done When equal the Deliverables, trace set and acceptance above
- OBSERVED: the package file and its index row (`open`; depends on PLAN-43 (`phase-sub-workflows`); PLAN-48 (`start-from-clients`); PLAN-69 (`unit-frontmatter-and-types`); PLAN-70 (`plan-instance-alignment`)) — read at `doc/plans/recipe-workflows.adoc` and `doc/plans/README.adoc` (main at a8a4214, regenerated by cleanup)
  - verdict: contradicted | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: yes | evidence: README.adoc row recipe-workflows re-read at 238c823: open; depends on phase-sub-workflows, start-from-clients, unit-frontmatter-and-types, plan-instance-alignment; the claim named other dependencies (done and dropped, or added since); rewritten from the row
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line only — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: contradicted | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: yes | evidence: core/profile is the client profile loader; the recipe descriptor belongs to core/recipe; surface paths core/profile, core-profile.adoc and the core-profile ADR directory were wrong and are corrected to core/recipe
- Verify-first clause: before scoping, check that the index no longer says `blocked`, that the use cases are agreed, and which of the packages this one restructures have shipped since; a refutation loops back to re-scope.
  - verdict: corroborated | checked_at: 98ce971c84fefa9a4650a1e115274c257e61f3fb | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: row recipe-workflows is open in the index at 98ce971; open PRs (ci pr list): #71 (a dependabot bump) only, none names the package; doc/plans/recipe-workflows.adoc is unchanged since 3323ce7; code claims of the spec were not re-read

## Expected Surface

- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/` — inferred from the `Where` line
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/recipe/` — the recipe descriptor (`core.recipe`, recipes and extension points; `package-info.java` only today; `core.profile` is the client profile loader and is not this package's home) — corrected by cleanup on 2026-10-16
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-plan.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-plan/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- HYPOTHESIS: `pm-core/doc/developer/core-recipe.adoc` — developer documentation of the package; created (corrected from `core-profile.adoc` by cleanup on 2026-10-16)
- HYPOTHESIS: `pm-core/doc/adr/core-recipe/` — ADRs of the package's design decisions; new directory (corrected from `core-profile/` by cleanup on 2026-10-16)
- OBSERVED: `doc/plans/recipe-workflows.adoc` — the package file, deleted when the package is done
- OBSERVED: `doc/specification/domain-extensions/05-recipes-and-extension-points.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/phase-workflows/01-planning-phases-1-2.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/state-catalogue/09-bootstrap-and-lessons.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/workflow-dsl/01-grammar.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/workflow-dsl/09-plan-instance.adoc` — specification part; the implemented sections deleted

## Dependencies and Sequencing

- Depends on: PLAN-43 (`phase-sub-workflows`); PLAN-48 (`start-from-clients`); PLAN-69 (`unit-frontmatter-and-types`); PLAN-70 (`plan-instance-alignment`)

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-50-recipe-workflows.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

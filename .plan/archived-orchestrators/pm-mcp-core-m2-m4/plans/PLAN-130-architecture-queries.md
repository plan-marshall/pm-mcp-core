# PLAN-130: The architecture queries: the server-side computation behind pm://architecture/*

epic: pm-mcp-core-m2-m4
workstream: WS-05

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-130-architecture-queries.md` and is queued as one row file, `queue/PLAN-130.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r2`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R17, R18, R19. Package bookkeeping (R01, R05): this plan is 6 of 7 plans of the package `domain-extensions-and-architecture` (`doc/plans/domain-extensions-and-architecture.adoc`). It deletes from the package file the bullets of _What_ and the _Done When_ paragraphs it delivers and drops its deliverables from the covered sections of the specification and watch items; the plan that delivers the last bullet removes the package's row from `doc/plans/README.adoc` and deletes the package file. The plans of the package edit different sections of the same documents; whichever merges second merges the base branch (the lane's step 7) before the merge gate. In-repo share only: the MCP resources themselves (URI routing, TOON encoding) are `pm-mcp-server` in `plan-marshall-mcp`. This is the last plan of the package unless PLAN-136 (`documentation-domain-verbs`) lands later: whichever merges last removes the package row and file.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-discovery.adoc` (extended); ADR directory: `pm-core/doc/adr/core-discovery/` (the next free number); layout: workstream WS-10.
- R12 — log message holder: none expected (`CoreLogMessages`, prefix `PM_CORE`; next free: INFO 2, WARN 103, ERROR 201).

## Objective

Sixth part of the work package `domain-extensions-and-architecture`: the read-only computations behind the `pm://architecture/*` resources, as an `ArchitectureQueries` service over the discovered modules, the merged edges, the path attribution and the build map, each answer with its facts' provenance and the specified zero-result and error semantics: an empty result only for a population actually examined, `indeterminate` otherwise.

## Deliverables

1. **overview, modules/{module_id}, files and files?path:** the project-wide inventory with active domains, resolver status reports and enrichment status; the facts of one module with profiles and resolved `skill://` URIs; the tier-0 file inventory (categories with counts and a coverage block `{files_scanned, excluded{reason: n}, complete}`; a zero count is honest only when `files_scanned > 0` and `complete: true`, otherwise the category is `indeterminate`); `which-module` through the path attribution plus the matching build-map route.
2. **graph and neighbours/{module_id}:** the merged Axis C graph (`MergedDependencyEdge[]` with `resolverIds`) and topological layers; direct upstream and downstream with provenance; with at least two modules and zero edges the answer is `indeterminate` with the resolver status reports, never an empty edge set (PM-WATCH-EXT-5), and a gate reading the graph treats it as `indeterminate`.
3. **files/{+path}/neighbours:** the files the file imports (the same scanners as the resolvers; unresolvable imports in `unresolved[]`), its paired test or production file by the build system's source-set convention (`none` when there is none) and its owning module; the light gate counts `|neighbours|` from this.
4. **impact and drift:** the transitive downstream blast radius and the `derive-verification` set for given modules, the closure being the modules, their transitive reverse dependents and every repository-wide test module (PM-WATCH-EXT-10); the drift report against the structural snapshot with `changed[]` for modules whose comparison is certified different and `indeterminate[]{module, reason}` for modules the hash surface cannot certify, an uncertifiable comparison being `indeterminate`, never `changed` (PM-WATCH-EXT-25). A read of a project that is not enrolled is refused with the closed outcome.
5. **Documentation:** extend the guide, ADRs, delete the implemented § Architecture Query Resources bullets of `03-module-discovery.adoc` and PM-WATCH-EXT-5, -8, -10, -25 where covered; name the resource routing as owed to `plan-marshall-mcp`. The snapshot store (`derived-facts.json`) is an input as a value; its owner is an open operator question (see PLAN-142 (`module-discovery`)).

**Done when:** Each query answers a fixture project and names its facts' provenance; an empty graph over two or more modules is `indeterminate`; a zero count in the file inventory is reported only with `files_scanned > 0` and `complete: true`; the impact closure includes reverse dependents and repository-wide test modules; an uncertifiable drift comparison is `indeterminate`; the LSP resolver's `NOT_RUN` reason appears in the overview; a read of an unenrolled project is refused with the closed outcome. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (`pm-core`; where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and the integration tests of every changed module with tests (`verify -Pintegration-tests -pl <module> -am`, only on `pm-core` and `pm-runtime`; here: `pm-core`) are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-TOOL-3, PM-EXT-3
- Specification: `doc/specification/domain-extensions/03-module-discovery.adoc` § Architecture Query Resources (`pm://architecture/...`)
- Watch: `doc/implementation-watch/domain-extensions.adoc` PM-WATCH-EXT-5, -8, -10, -25

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- The two capability vocabularies (`derivable`/`not_derivable` and `available`/`unavailable`) are never mixed in one answer.
- **Operator question:** the owner of `derived-facts.json` (the structural snapshot `module_discovery`) — see PLAN-142 (`module-discovery`).

## Claim Labels

- OBSERVED: the package file and its index row (`open`, depends on nothing) — `doc/plans/domain-extensions-and-architecture.adoc` (What: seven bullets, Done When: six paragraphs) and `doc/plans/README.adoc` at `origin/main` `a8da4104`; the package is cut into seven plans because the seven bullets hold about thirty testable parts
- OBSERVED: the packages `core.domain`, `core.architecture` and `core.discovery` of `pm-core` hold only their `package-info.java` (15 lines each, Javadoc naming the specification part) at `a8da4104` — every class named in the plans of this package is new
- OBSERVED: `doc/specification/domain-extensions/03-module-discovery.adoc` lines 81-122 specify the nine resources, the resource contracts, "Empty Graph Is Not Clean" and the two capability vocabularies
- OBSERVED: no `pm://architecture` string exists in `pm-*/src/main` (`grep` finds none): the resource layer is not in this repository
- HYPOTHESIS: the enrichment status (`responsibility`, `purpose`, `key_packages`, "persisted semantic enrichment") is model judgment stored under `.marshall/project-architecture/` and is read as an opaque input; its writer is outside this package — confirm at `04-setup-state-machine.adoc` (verify-at-outline)
- Verify-first clause: before editing, confirm that the plans PLAN-129 (`architecture-merges`), PLAN-131 (`architecture-resolvers`), PLAN-142 (`module-discovery`) and PLAN-132 (`build-map-and-derive-verification`) are merged, list the open pull requests touching `core/discovery/`, and re-read the resource table at the head; a refutation loops back to re-scope.

## Expected Surface

- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/discovery/` new classes: `ArchitectureQueries` and the answer types (exact names at the outline); `pm-core/src/test/java/de/planmarshall/core/discovery/` new test classes and fixtures
- OBSERVED: `doc/plans/domain-extensions-and-architecture.adoc`, `doc/specification/domain-extensions/03-module-discovery.adoc` (the query section), `doc/implementation-watch/domain-extensions.adoc` (PM-WATCH-EXT-5, -8, -10, -25)
- HYPOTHESIS: `pm-core/doc/developer/core-discovery.adoc` and `pm-core/doc/adr/core-discovery/` — created by PLAN-142 (`module-discovery`), extended additively

## Dependencies and Sequencing

- Depends on: PLAN-129 (`architecture-merges`), PLAN-131 (`architecture-resolvers`), PLAN-142 (`module-discovery`), PLAN-132 (`build-map-and-derive-verification`).
- Overlaps with: the parked PLAN-101 `change-map` declares `core/discovery/` (it follows this package); none with PLAN-46, 105, 108, 109.
- Adjacent to: `plan-marshall-mcp` `pm-mcp-server` (the `pm://architecture/*` routing); `change-map` and `finalize-pipeline-and-merge` (blocked, read the module crawl and the dependency parser).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-130-architecture-queries.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

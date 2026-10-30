# PLAN-V2-79: The Axis C resolvers: build-system, import, cross-reference, LSP and project-script

epic: pm-mcp-core-m2-m4-v2
workstream: WS-08
origin: PLAN-131 of the epic pm-mcp-core-m2-m4

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-79-architecture-resolvers.md` and is queued as one row file, `queue/PLAN-V2-79.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r4`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R17, R18, R19, R20. Package bookkeeping (R01, R05): this plan is 3 of 7 plans of the package `domain-extensions-and-architecture` (`doc/plans/domain-extensions-and-architecture.adoc`). It deletes from the package file the bullets of _What_ and the _Done When_ paragraphs it delivers and drops its deliverables from the covered sections of the specification and watch items; the plan that delivers the last bullet removes the package's row from `doc/plans/README.adoc` and deletes the package file. The plans of the package edit different sections of the same documents; whichever merges second merges the base branch (the lane's step 7) before the merge gate. Scope note: the package file's _What_ does not list the concrete resolvers as a bullet, but the specification's sealed Axis C interface permits nine, the Done-When says every edge carries the ID of the resolver that found it, and PM-WATCH-EXT-3 and -4 are guards of the `markdown` resolver; this plan covers them and says so: if the operator reads the package otherwise, the plan is cut or dropped.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-architecture.adoc` (created by PLAN-V2-30 (`architecture-merges`), extended); ADR directory: `pm-core/doc/adr/core-architecture/` (the next free number); layout: workstream WS-10.
- R12 — log message holder: none expected (`CoreLogMessages`, prefix `PM_CORE`; next free: INFO 2, WARN 103, ERROR 201).

## Objective

Third part of the work package `domain-extensions-and-architecture`: the nine permitted implementations of Axis C that derive directed module dependency edges — Maven, Gradle, npm, pyproject, Python import, Markdown cross-reference, documentation cross-reference, the LSP resolver that reports `NOT_RUN` with its reason until Milestone 13, and the project-script resolver that runs the enrolled `edge_resolver` scripts of the project as `project_script` jobs.

## Deliverables

1. **The build-system resolvers** `maven`, `gradle`, `npm`, `pyproject` (the declared-dependency join): edges between discovered modules from the dependency coordinates of `DiscoveredModuleFacts`, resolver IDs as in `plan-marshall` (`derivation_resolver_id()` of `build-maven`, `build-npm`, `build-pyproject`); `pyproject` and `python` stay distinct IDs. Ported tests, expected values committed as plain files.
2. **The `python` import resolver:** the import join from the line-oriented import scan of PLAN-V2-14 (`module-discovery`) (module-level edges only; file-level import facts are never merged into the module graph, PM-WATCH-EXT guard). Fixture: a tree with an import and a string literal that looks like one (the accepted error of the deliberate simplification).
3. **The `markdown` and `documentation` cross-reference resolvers:** edges from the cross-references of documents and plugin bundles; the `markdown` resolver resolves both the exact GitHub slug (strip `[^\w\s-]`, space to `-`, hyphens never collapsed, so a spaced em dash becomes `--`) and the collapsed form (PM-WATCH-EXT-3), and a dropped cross-reference note carries the source file, the locator and the target (PM-WATCH-EXT-4).
4. **The `lsp` and `project-script` resolvers:** `LspSymbolEdgeResolver` answers `NOT_RUN` with an explicit reason until the language-server milestone, never an empty list; `ProjectScriptEdgeResolver` runs every enrolled project script of `worker_kind: edge_resolver` as a confined `project_script` job through `JobStarter` (`core.spi`) and unions their edges, `NOT_RUN`/`ABSENT` where the project has none. Tests with a fake `JobStarter`.
5. **Documentation:** extend `core-architecture.adoc`, the ADRs, delete the covered parts of `02-merge-algorithms.adoc` and PM-WATCH-EXT-3, -4; name the resolver registration in the assembly as owed to `plan-marshall-mcp`.

**Done when:** Each resolver derives the expected edges for its fixture and stamps its ID; an edge found by two resolvers carries both IDs through `DerivationMerge`; the `markdown` resolver resolves both slug forms and its drop notes carry file, locator and target; the `lsp` resolver reports `NOT_RUN` with its reason; the `project-script` resolver runs the enrolled scripts through the job seam and reports `NOT_RUN`/`ABSENT` for a project without one; no resolver reads file-level import facts into the module graph. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (`pm-core`; where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and the integration tests of every changed module with tests (`verify -Pintegration-tests -pl <module> -am`, only on `pm-core` and `pm-runtime`; here: `pm-core`) are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-EXT-2
- Specification: `doc/specification/domain-extensions/01-four-axis-architecture.adoc` § Sealed Java Interfaces (Axis C), Project-script resolver; `doc/specification/domain-extensions/02-merge-algorithms.adoc`; `doc/specification/domain-extensions/05-recipes-and-extension-points.adoc` § Out-of-Process Project Script Jobs
- Watch: `doc/implementation-watch/domain-extensions.adoc` PM-WATCH-EXT-3, PM-WATCH-EXT-4

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **Fixtures, not the sibling checkout.** Where a component is ported from `plan-marshall`, the expected values are derived from the original at a pinned commit, committed as plain files and the commit recorded; no test runs Python or reads the sibling checkout.

## Claim Labels

- OBSERVED: the package file and its index row (`open`, depends on nothing) — `doc/plans/domain-extensions-and-architecture.adoc` (What: seven bullets, Done When: six paragraphs) and `doc/plans/README.adoc` at `origin/main` `a8da4104`; the package is cut into seven plans because the seven bullets hold about thirty testable parts
- OBSERVED: the packages `core.domain`, `core.architecture` and `core.discovery` of `pm-core` hold only their `package-info.java` (15 lines each, Javadoc naming the specification part) at `a8da4104` — every class named in the plans of this package is new
- OBSERVED: `doc/implementation-watch/domain-extensions.adoc` lines 46-64: PM-WATCH-EXT-3 (both slug forms) and PM-WATCH-EXT-4 (dropped references carry identity) name the `markdown` resolver of Axis C; `01-four-axis-architecture.adoc` lines 127-131 permit the nine classes
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/spi/JobStarter.java` exists as the seam that starts a job from a primitive
- HYPOTHESIS: the resolvers read `DiscoveredModuleFacts` (declared dependencies) and the file tree, and need no process, except the project-script resolver; the edge rules of each (coordinates matched to reactor modules) are those of the originals in `plan-marshall` (`derivation_resolver_id` modules of `build-maven`, `build-npm`, `build-pyproject`, `pm-dev-python`, `pm-plugin-development`, `pm-documents`) — read at the pinned commit before the outline (verify-at-outline)
- HYPOTHESIS: this plan's inclusion in the package is a reading of the specification, not of the package file — see the scope note of the First Instruction; the operator can drop it without affecting the other plans
- Verify-first clause: before editing, confirm that PLAN-V2-30 (`architecture-merges`) and PLAN-V2-14 (`module-discovery`) are merged, list the open pull requests touching `core/architecture/`, and read the originals at the head of the sibling checkout; a refutation loops back to re-scope.

## Expected Surface

- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/architecture/` (or `core/domain/`, as the permits clause fixes) new classes: `MavenEdgeResolver`, `GradleEdgeResolver`, `NpmEdgeResolver`, `PyprojectEdgeResolver`, `PythonImportEdgeResolver`, `MarkdownCrossRefResolver`, `DocumentationCrossRefResolver`, `LspSymbolEdgeResolver`, `ProjectScriptEdgeResolver`
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/architecture/` new test classes and fixtures under `pm-core/src/test/resources/ported/architecture/` (new)
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/domain/EdgeDerivationAxis.java` (created by PLAN-V2-30 (`architecture-merges`)) — the `permits` clause is completed here
- OBSERVED: `doc/plans/domain-extensions-and-architecture.adoc`, `doc/specification/domain-extensions/02-merge-algorithms.adoc`, `doc/implementation-watch/domain-extensions.adoc` (PM-WATCH-EXT-3, -4)

## Dependencies and Sequencing

- Depends on: PLAN-V2-83 (`skills-conformance-foundation`: the dependencies and rule R20); and then: PLAN-V2-30 (`architecture-merges`) (the Axis C contract) and PLAN-V2-14 (`module-discovery`) (the import scan and the dependency coordinates).
- Overlaps with: code-disjoint from every plan except the two it follows; none with PLAN-46, PLAN-105, PLAN-V2-39, PLAN-V2-43 or the parked PLAN-V2-78.
- Adjacent to: PLAN-V2-80 (`architecture-queries`) (the graph the resolvers feed); `plan-marshall-mcp` (registration of the resolvers).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-79-architecture-resolvers.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

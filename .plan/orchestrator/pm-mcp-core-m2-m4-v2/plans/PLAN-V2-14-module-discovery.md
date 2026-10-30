# PLAN-V2-14: Server-owned module discovery: the live crawl, the descriptor parsers and the build-file dependency parsers

epic: pm-mcp-core-m2-m4-v2
workstream: WS-08
origin: PLAN-142 of the epic pm-mcp-core-m2-m4

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-14-module-discovery.md` and is queued as one row file, `queue/PLAN-V2-14.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r4`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R17, R18, R19, R20. Package bookkeeping (R01, R05): this plan is 4 of 7 plans of the package `domain-extensions-and-architecture` (`doc/plans/domain-extensions-and-architecture.adoc`). It deletes from the package file the bullets of _What_ and the _Done When_ paragraphs it delivers and drops its deliverables from the covered sections of the specification and watch items; the plan that delivers the last bullet removes the package's row from `doc/plans/README.adoc` and deletes the package file. The plans of the package edit different sections of the same documents; whichever merges second merges the base branch (the lane's step 7) before the merge gate. R09 applies: a crawl of a project reads the project's files and adopts the confinement check itself.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-discovery.adoc` (new); ADR directory: `pm-core/doc/adr/core-discovery/` (new directory, from `001`); layout: workstream WS-10.
- R12 — log message holder: none expected (`CoreLogMessages`, prefix `PM_CORE`; next free: INFO 2, WARN 103, ERROR 201).

## Objective

Fourth part of the work package `domain-extensions-and-architecture`: the discovery of the modules of a project per build system (Maven, Gradle, npm, pyproject, documentation, marshall-plugin) in package `core.discovery` as a live crawl of the worktree, with per-descriptor failure isolation, the descriptor tree hash that keys the cached snapshot, and each build domain's build-file dependency parser that the change map and the pre-merge Dependency Delta Check read. The enrolled discovery jobs (`help:all-profiles dependency:tree`, `gradle properties dependencies tasks`) run lazily as jobs of the runtime and are not part of the crawl.

## Deliverables

1. **The crawl and its invariants:** `ModuleDiscovery` over a project root (confined through `PathConfinement`, R09) producing `DiscoveredModuleFacts`; the live worktree decides, uncommitted descriptors included, and a module directory on disk but absent from a snapshot is still discovered; a malformed descriptor (root or workspace member) is reported for its module with the parse diagnostic and does not abort the remaining modules; the descriptor tree hash covers the live descriptor content, not `HEAD`; the in-process crawl starts no child process (the single exception, `git rev-parse --git-common-dir`, is the caller's seam, not a process of the crawl).
2. **The Maven, Gradle and npm descriptor parsers:** `pom.xml` (StAX, DTD and external entities off: coordinates, packaging, parent/reactor hierarchy, profiles, test directory presence, Java package roots), `settings.gradle(.kts)`/`build.gradle(.kts)` (subproject paths, applied plugins, source sets, dependencies, a line-oriented reader of the subset), `package.json` with `workspaces` globs (Jackson streaming: workspace packages, scripts, dependencies, `tsconfig.json` presence).
3. **pyproject and the Python import scan:** `pyproject.toml` (project metadata, `[project.dependencies]`, `[tool.pytest]`, `[tool.mypy]`, `[tool.ruff]`, top-level package directories and test roots) and the line-oriented import scan (`import x[.y]` and `from x[.y] import …`, leading whitespace, continuation lines and parenthesised lists joined; no AST; the deliberate simplification against `plan-marshall` recorded in an ADR). **Operator question (CLAUDE.md, no dependency without asking):** the specification says "TOML parser in `pm-core`"; `pm-core` has no TOML library — add one, or write a compiled subset reader of the fields above. Ask at refine.
4. **The documentation and marshall-plugin scans:** the `documentation` module (a `doc/` or `docs/` directory holding `.adoc` or `.md` at any depth, or a root `README.adoc`; one module with its documentation root and its outbound `component_refs`) and the `marshall-plugin` modules (one per bundle of `marketplace/.claude-plugin/marketplace.json`, active only when the marketplace is named `plan-marshall`: skills, agents and commands as packages with their frontmatter descriptions, and `component_refs` to other bundles).
5. **The build-file dependency parsers:** per build domain, a parser that claims its build files (`pom.xml`, `build.gradle(.kts)` and `settings.gradle(.kts)`, `package.json`, `pyproject.toml`) and returns, for a file at a given revision (its bytes are the input), the ecosystem-qualified coordinates (`maven:<groupId>:<artifactId>`, `npm:<name>`, `pypi:<name>`) with their scope, or a parse diagnostic; the coordinate set is the same over two reads of one revision; a file no parser claims, or one that fails to parse, is reported so that the Dependency Delta Check can be `indeterminate`.
6. **Documentation:** `core-discovery.adoc` (new), the ADRs, deletion of the implemented parts of `doc/specification/domain-extensions/03-module-discovery.adoc` § Server-Owned Module Discovery and PM-WATCH-EXT-6, -7 where covered. The persisted `module_discovery` derived fact (`derived-facts.json`) has no store in the repository (`grep` finds only `LockKey`): this plan returns the snapshot as a value and names the store as an open gap (operator question).

**Done when:** The discovery of a fixture repository of each build system yields its modules, kinds and packaging; a malformed descriptor is reported for its module and the rest are discovered; an uncommitted descriptor is discovered; the descriptor tree hash changes with the live content; each dependency parser returns the ecosystem-qualified coordinates with their scope for its fixture, a file no parser claims or one that fails to parse yields its diagnostic, and the coordinate set is the same over two reads of one revision; the crawl starts no process; a path outside the project root is refused as the confinement check says. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (`pm-core`; where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and the integration tests of every changed module with tests (`verify -Pintegration-tests -pl <module> -am`, only on `pm-core` and `pm-runtime`; here: `pm-core`) are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-EXT-3
- Specification: `doc/specification/domain-extensions/03-module-discovery.adoc` § Server-Owned Module Discovery & Enrolled Discovery Jobs; `doc/specification/store-schemas/09-derived-facts-and-build-timings.adoc` (derived fact `module_discovery`)
- Watch: `doc/implementation-watch/domain-extensions.adoc` PM-WATCH-EXT-6, -7

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **Operator questions** (R07): the owner of `derived-facts.json`. **TOML decided 2026-10-29 (operator walk, decision 11B of 18): a strict subset reader in `pm-core`, no library, no new dependency.** It reads tables, strings, numbers, booleans, arrays and inline tables; for any construct it does not support it reports "unsupported TOML construct" for that module and reads nothing from the file (it never guesses); the supported subset and the refusals are written into the developer guide and an ADR. Deliverable 3 and the `pyproject.toml` dependency parser of deliverable 5 use it.
- **Fixtures, not product content.** Where a component is ported from `plan-marshall`, the expected values are derived from the original at a pinned commit, committed as plain files and the commit recorded; no test runs Python or reads the sibling checkout.
- R09: a refused path is a `ConfinementOutcome` value; no checked exception is added.

## Claim Labels

- OBSERVED: the package file and its index row (`open`, depends on nothing) — `doc/plans/domain-extensions-and-architecture.adoc` (What: seven bullets, Done When: six paragraphs) and `doc/plans/README.adoc` at `origin/main` `a8da4104`; the package is cut into seven plans because the seven bullets hold about thirty testable parts
- OBSERVED: the packages `core.domain`, `core.architecture` and `core.discovery` of `pm-core` hold only their `package-info.java` (15 lines each, Javadoc naming the specification part) at `a8da4104` — every class named in the plans of this package is new
- OBSERVED: `doc/specification/domain-extensions/03-module-discovery.adoc` lines 16-62: the table of six build systems with their compiled-in descriptor parsers and enrolled discovery job templates, and the bullets (Python Import Scan, Live Crawl, In-Process Crawl vs. Lazy Discovery Jobs, Per-Descriptor Failure Isolation, Build-File Dependency Parser)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/ProjectEnrolment.java` line 219 has the host tool kind `ASCIIDOCTOR("asciidoctor")` and line 489 `templates(discoveryTemplates, "discovery_templates")`: the enrolment already carries `build.discovery_templates`
- OBSERVED: `grep -rn 'derived-facts\|DerivedFacts' pm-*/src/main` finds only `LockKey.java` (the lock name); no class reads or writes `derived-facts.json`, and no work package of the index names it
- OBSERVED: `PathConfinement`, `WorkspaceRoots`, `ConfinementOutcome` in `pm-core/src/main/java/de/planmarshall/core/store/` are the confinement check of R09
- OBSERVED: `doc/plans/README.adoc`: `change-map` (blocked, no spec) depends on this package for "the module discovery and the build-file dependency parser", and `finalize-pipeline-and-merge` for the same through `domain-extensions-and-architecture`
- HYPOTHESIS: no TOML library is on the classpath of `pm-core` (`pm-core/pom.xml` declares `lombok`, `pm-api`, `cui-java-tools`, `jackson-core` and test dependencies) — confirm with the dependency tree; adding one needs the operator
- Verify-first clause: before editing, confirm that PLAN-V2-09 (`domain-axes-and-descriptors`) is merged (`DiscoveredModuleFacts`), list the open pull requests touching `core/discovery/` (the parked PLAN-V2-78 `change-map` declares the directory), and re-read the originals (`discover_modules` modules of `pm-documents`, `plugin_discover.py` of `pm-plugin-development`) at the sibling checkout; a refutation loops back to re-scope.

## Expected Surface

- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/discovery/package-info.java` — existing, extended
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/discovery/TomlSubsetReader.java` — new: the strict TOML subset reader of the decision of 2026-10-29 (verify-at-outline the final name)
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/discovery/TomlSubsetReaderTest.java` — new: supported constructs and the refusal of every unsupported one
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/discovery/` new classes: `ModuleDiscovery`, the parsers (`PomDescriptorParser`, `GradleDescriptorParser`, `PackageJsonParser`, `PyprojectParser`, `PythonImportScan`, `DocumentationScan`, `MarshallPluginScan`) and the build-file dependency parsers with their coordinate and diagnostic types (exact names at the outline)
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/discovery/` new test classes and fixture trees under `pm-core/src/test/resources/discovery/` (new)
- OBSERVED: `doc/plans/domain-extensions-and-architecture.adoc`, `doc/specification/domain-extensions/03-module-discovery.adoc` (the discovery section), `doc/implementation-watch/domain-extensions.adoc` (PM-WATCH-EXT-6, -7)
- HYPOTHESIS: `pm-core/doc/developer/core-discovery.adoc` and `pm-core/doc/adr/core-discovery/` — new

## Dependencies and Sequencing

- Depends on: PLAN-V2-83 (`skills-conformance-foundation`: the dependencies and rule R20); and then: PLAN-V2-09 (`domain-axes-and-descriptors`) (`DiscoveredModuleFacts`).; PLAN-V2-05 (`derived-facts-store-operations`) (the Derived Facts Store: records, kind registry, derivation and staleness rules; operator decision 12 of 18)
- Overlaps with: PLAN-V2-78 `change-map` (parked) declares the directory `pm-core/src/main/java/de/planmarshall/core/discovery/`: 1 shared entry; it depends on this package by the index, so it follows. None with PLAN-46, PLAN-105, PLAN-V2-39, PLAN-V2-43 (PLAN-V2-17/PLAN-46/PLAN-V2-74 declare `core/config/`, not `core/discovery/`). Documents: `03-module-discovery.adoc` is shared with PLAN-V2-35 (`build-map-and-derive-verification`) and PLAN-V2-80 (`architecture-queries`), in different sections.
- Adjacent to: PLAN-V2-79 (`architecture-resolvers`) (uses the import scan and the coordinates); `project-onboarding-unit` PLAN-V2-17 (`setup.discover-modules`).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-14-module-discovery.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

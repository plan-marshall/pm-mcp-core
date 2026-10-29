# PLAN-129: Provenance-preserving merges of dependency edges (Axis C) and path ownership (Axis D)

epic: pm-mcp-core-m2-m4
workstream: WS-05

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-129-architecture-merges.md` and is queued as one row file, `queue/PLAN-129.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r2`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R17, R18, R19. Package bookkeeping (R01, R05): this plan is 2 of 7 plans of the package `domain-extensions-and-architecture` (`doc/plans/domain-extensions-and-architecture.adoc`). It deletes from the package file the bullets of _What_ and the _Done When_ paragraphs it delivers and drops its deliverables from the covered sections of the specification and watch items; the plan that delivers the last bullet removes the package's row from `doc/plans/README.adoc` and deletes the package file. The plans of the package edit different sections of the same documents; whichever merges second merges the base branch (the lane's step 7) before the merge gate.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-architecture.adoc` (new); ADR directory: `pm-core/doc/adr/core-architecture/` (new directory, from `001`); layout: workstream WS-10.
- R12 — log message holder: none expected (`CoreLogMessages`, prefix `PM_CORE`; next free: INFO 2, WARN 103, ERROR 201).

## Objective

Second part of the work package `domain-extensions-and-architecture`: the Axis C and D contracts and the two merges over them in package `core.architecture` — `DerivationMerge` over edge-derivation results and the path-attribution merge — with failing-resolver and failing-attributor isolation, registration in ID order, the surviving report of a dropped duplicate, ordered output and provenance on every contribution. The concrete resolvers are the plan PLAN-131 (`architecture-resolvers`).

## Deliverables

1. **Contracts and result types:** `EdgeDerivationAxis` (`resolverId`, `deriveEdges(WorkspaceDiscoveryContext)` throwing `ResolverException`), `ResolverExecutionResult` with the status (`OK`, `ERROR`, `NOT_RUN`, `NOT_DISPATCHED`) and the `NonRunReason` (`ABSENT`, `FAILED_TO_START`, `TIMEOUT`, `UNSUPPORTED`), `MergedDependencyEdge` with `resolverIds`, `PathOwnershipAxis` (`attributorId`, `claimPaths()` throwing `AttributorException`), `PathClaimSet`, `PathOwnershipClaim`, `AttributorReport`, `PathClaimCollision`. The spec lists the resolver classes in the `permits` clause of the sealed Axis C interface; PLAN-131 (`architecture-resolvers`) adds them, so this plan declares the interface with the permitted classes that exist and corrects the specification's code block where it differs (R05), recording that in an ADR.
2. **`DerivationMerge`:** activation by explicit resolver ID only; the idempotent union of the same directed edge into one `MergedDependencyEdge` with the sorted union of resolver IDs and each resolver's count of distinct accepted pairs; drops with a `merge: ` note on the producing report for malformed candidates, self-edges and edges naming a module outside the discovered set; `NOT_DISPATCHED`/`DISABLED` reported with zero edges and excluded from the count that ran; `NOT_RUN` with its reason, never an empty list masquerading as a clean scan; a resolver that throws is `ERROR` with zero edges and a sanitized note (simple class name and message, line breaks collapsed, 200 characters, no stack trace), counted as dispatched, never aborting the merge; duplicate IDs keep the first registration and note the drop on its report; every map is a `TreeMap`, merged edges sorted by `(from, to)`, reports by ID. Tests with fixture resolvers (test-scope classes implementing the interface through a test-visible permitted type, or the final resolvers when merged).
3. **`PathAttributionMerge`:** the single `normalizeSpelling` for registration and lookup (trim, backslash to `/`, strip leading `./` runs, strip trailing slashes; no symlink, no filesystem, no `..` collapse, no stripping of a leading `/`); the claim validity filters (dropped and reported, never thrown: blank, root, `.`, `..`, absolute, drive-letter and repository-escaping prefixes, unknown target modules); failing-attributor isolation; registration in `attributorId` order; the collision-without-winner rule (two attributors, one prefix, different modules: no claim, a `PathClaimCollision`, a note naming every contender; identical claims merge to one with the sorted union of producers; `.marshall` and `.marshall/local` are not a collision); one report per attributor; zero attributors is a first-class outcome; the segment-boundary longest-prefix lookup (`.marshalls/x` never resolves through `.marshall`).
4. **Documentation:** `core-architecture.adoc` (new), the ADRs (the contract/permits decision, the collision rule), deletion of the implemented parts of `doc/specification/domain-extensions/02-merge-algorithms.adoc` (PM-WATCH-EXT-5 is deleted by PLAN-130 (`architecture-queries`), which covers its guard about the empty graph). The two merges' Java listings in the specification are the contract: a difference between a listing and the final signature is corrected in the specification in the same pull request.

**Done when:** The two merges are shown by fixtures with two resolvers and two attributors: a resolver and an attributor that throw are `ERROR` with their sanitized note and contribute nothing while their siblings do; reports are ordered by ID and merged edges by `(from, to)` whatever order the caller passes; a duplicate ID keeps the first and notes the drop; every edge carries the ID of the resolver that found it and an edge found by two resolvers carries both; a collision yields no claim and a diagnostic; a prefix outside the repository is dropped with a note; two runs over the same input serialise byte-identically; a graph of at least two modules with zero edges is distinguishable from "no resolver ran" by the reports. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (`pm-core`; where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and the integration tests of every changed module with tests (`verify -Pintegration-tests -pl <module> -am`, only on `pm-core` and `pm-runtime`; here: `pm-core`) are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-EXT-1, PM-EXT-2
- Specification: `doc/specification/domain-extensions/02-merge-algorithms.adoc` (every section); `doc/specification/domain-extensions/01-four-axis-architecture.adoc` § Sealed Java Interfaces (Axes C and D)
- Watch: `doc/implementation-watch/domain-extensions.adoc` PM-WATCH-EXT-5 (an empty merged graph is not a clean graph; its guard is covered by PLAN-130 (`architecture-queries`))

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- Never throw generic `Exception`/`RuntimeException` in production code (`CLAUDE.md`): `ResolverException` and `AttributorException` are the specification's checked types.
- **Log messages: none expected** (a failure is a report, not a log record).

## Claim Labels

- OBSERVED: the package file and its index row (`open`, depends on nothing) — `doc/plans/domain-extensions-and-architecture.adoc` (What: seven bullets, Done When: six paragraphs) and `doc/plans/README.adoc` at `origin/main` `a8da4104`; the package is cut into seven plans because the seven bullets hold about thirty testable parts
- OBSERVED: the packages `core.domain`, `core.architecture` and `core.discovery` of `pm-core` hold only their `package-info.java` (15 lines each, Javadoc naming the specification part) at `a8da4104` — every class named in the plans of this package is new
- OBSERVED: `doc/specification/domain-extensions/02-merge-algorithms.adoc` (404 lines): § Edge Derivation Merge from line 17 (seven numbered rules, listing in package `de.planmarshall.core.architecture`) and § Path Attribution Merge from line 206 (six rules, listing); both import `de.planmarshall.core.domain.*` types
- OBSERVED: the sealed Axis C interface permits nine classes in the specification: `MavenEdgeResolver`, `GradleEdgeResolver`, `NpmEdgeResolver`, `PyprojectEdgeResolver`, `PythonImportEdgeResolver`, `MarkdownCrossRefResolver`, `DocumentationCrossRefResolver`, `LspSymbolEdgeResolver`, `ProjectScriptEdgeResolver` (`01-four-axis-architecture.adoc` lines 127-131); none exists in code
- HYPOTHESIS: the `WorkspaceDiscoveryContext` the resolvers receive (discovered modules, the root) is defined by this plan as a record over `DiscoveredModuleFacts` of PLAN-137 (`domain-axes-and-descriptors`); the specification names the type without a body — confirm at the specification (verify-at-outline)
- Verify-first clause: before editing, confirm that PLAN-137 (`domain-axes-and-descriptors`) is merged (`DiscoveredModuleFacts`), list the open pull requests touching `core/architecture/`, and re-read both merge sections at the head; a refutation loops back to re-scope.

## Expected Surface

- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/architecture/package-info.java` — existing, extended
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/architecture/` new classes: `DerivationMerge`, `PathAttributionMerge`, `MergedDependencyEdge`, `AttributorReport`, `PathClaimCollision`, the result types; `pm-core/src/main/java/de/planmarshall/core/domain/` new classes: `EdgeDerivationAxis`, `PathOwnershipAxis`, `WorkspaceDiscoveryContext`, `ResolverException`, `AttributorException`, `PathClaimSet`, `PathOwnershipClaim`, `ResolverExecutionResult`
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/architecture/` new test classes
- OBSERVED: `doc/plans/domain-extensions-and-architecture.adoc`, `doc/specification/domain-extensions/02-merge-algorithms.adoc`, `doc/implementation-watch/domain-extensions.adoc` (read; no item deleted)
- HYPOTHESIS: `pm-core/doc/developer/core-architecture.adoc` and `pm-core/doc/adr/core-architecture/` — new

## Dependencies and Sequencing

- Depends on: PLAN-137 (`domain-axes-and-descriptors`) (`DiscoveredModuleFacts`, the package `core.domain`).
- Overlaps with: code-disjoint from PLAN-142 (`module-discovery`) and PLAN-131 (`architecture-resolvers`) (the latter depends on this plan); adds new classes to `core.domain` beside PLAN-137 (`domain-axes-and-descriptors`), which it follows. None with PLAN-46, 105, 108, 109.
- Adjacent to: PLAN-131 (`architecture-resolvers`) (the concrete resolvers); PLAN-130 (`architecture-queries`) (the graph and the attribution table).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-129-architecture-merges.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

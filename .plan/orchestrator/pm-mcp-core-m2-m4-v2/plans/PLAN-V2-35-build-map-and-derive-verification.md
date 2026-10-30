# PLAN-V2-35: The build map and the deterministic derive-verification, and the resolver enablement binding

epic: pm-mcp-core-m2-m4-v2
workstream: WS-08
origin: PLAN-132 of the epic pm-mcp-core-m2-m4

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-35-build-map-and-derive-verification.md` and is queued as one row file, `queue/PLAN-V2-35.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r4`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R17, R18, R19, R20. Package bookkeeping (R01, R05): this plan is 5 of 7 plans of the package `domain-extensions-and-architecture` (`doc/plans/domain-extensions-and-architecture.adoc`). It deletes from the package file the bullets of _What_ and the _Done When_ paragraphs it delivers and drops its deliverables from the covered sections of the specification and watch items; the plan that delivers the last bullet removes the package's row from `doc/plans/README.adoc` and deletes the package file. The plans of the package edit different sections of the same documents; whichever merges second merges the base branch (the lane's step 7) before the merge gate.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-discovery.adoc` (created by PLAN-V2-14 (`module-discovery`), extended); ADR directory: `pm-core/doc/adr/core-discovery/` (the next free number); layout: workstream WS-10.
- R12 — log message holder: none expected (`CoreLogMessages`, prefix `PM_CORE`; next free: INFO 2, WARN 103, ERROR 201).

## Objective

Fifth part of the work package `domain-extensions-and-architecture`: the file-to-build contract `.marshall/config/build-map.json` read fail-closed, the route precedence of the longest matching glob, the classification of changed paths into `(role, build_class)`, the canonical command resolution rules of `derive-verification`, and the machine-local binding `derivation_resolvers.<resolver_id>.enabled` that decides which resolver is dispatched. The seeding and reseeding of the map belong to the setup state machine (`project-onboarding-unit`), not here.

## Deliverables

1. **The build map reader, fail closed:** `BuildMap` over `build-map.json` (`routes`: a domain-keyed inventory of `{glob, role, build_class}`, roles `production`, `test`, `config`, build classes `compile`, `module-tests`, `verify`, `none`); an absent file is `build_map_unseeded`, a malformed one, another `format_version`, or an entry with an unknown role or class is `build_map_invalid`, each an explicit refusal and never an empty verification set (the *deliberate hardening* against `plan-marshall`, which degrades an absent map to empty). The reader lives in `core.discovery`, not `core.config`.
2. **Route precedence and classification:** the `fnmatch` dialect, separate from the Axis A detection dialect (a path-bearing route matches the whole repository-relative path, a single `*` spans `/`, no `**`; a bare basename matches at any depth); a path matched by several routes across all domains takes the route with the longest glob string, equal lengths break by the glob's alphabetical order, so the result never depends on the order of domains or entries (PM-WATCH-EXT-35); a path matched by no route is unclaimed (the outline gate classifies it `unknown`). Tests: shuffled orders give one result.
3. **`derive-verification`:** from the changed-artifact list of a task, the canonical command set: production paths resolve `compile` on the owning module; test paths `test-compile` and `module-tests` (a Maven Failsafe `*IT`, `IT*`, `*ITCase` test path routes to `verify`); config paths `verify` for the affected module; `none` requires zero build jobs and routes only to the applicable documentation or plugin validators. The owning module comes from the path attribution of PLAN-V2-30 (`architecture-merges`) and the discovered modules. Whether and where "the `derive-verification` entry of the machine store that records what was derived and what was verified" is stored is unclear (no store exists for it): the result is returned as a value, and the entry is an open question for the outline (Verify-first).
4. **The resolver enablement binding:** a reader of the `derivation_resolvers` member of a project's `local-config.json` (store-schemas/08): an absent entry means enabled, only an explicit `false` disables, a malformed entry fails the closed schema and is reported and never read as a setting; PLAN-V2-30 (`architecture-merges`) folds a disabled resolver in as `NOT_DISPATCHED`/`DISABLED`. The reader takes the member's JSON, not the file: the owner of the whole `local-config.json` document is the onboarding work (`project-onboarding-unit`).
5. **Documentation:** extend `core-discovery.adoc`, the ADRs (fail-closed read, route precedence, the binding), deletion of the implemented § Build Map & Deterministic `derive-verification` bullets of `03-module-discovery.adoc` and PM-WATCH-EXT-9, -35 where covered.

**Done when:** The build map classifies a changed production, test and configuration path into the expected `(role, build_class)` and honours the longest glob whatever the order of domains and entries; an absent, malformed, other-version or unknown-role map is refused with `build_map_unseeded` or `build_map_invalid`; `derive-verification` returns the canonical command set per rule (including the Failsafe case and the zero-job `none` case); a resolver disabled in `local-config.json` is reported `NOT_DISPATCHED`/`DISABLED` and the others run; a malformed `derivation_resolvers` entry is reported and not read as a setting. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (`pm-core`; where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and the integration tests of every changed module with tests (`verify -Pintegration-tests -pl <module> -am`, only on `pm-core` and `pm-runtime`; here: `pm-core`) are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-EXT-3, PM-EXT-2
- Specification: `doc/specification/domain-extensions/03-module-discovery.adoc` § Build Map & Deterministic `derive-verification`; `doc/specification/domain-extensions/01-four-axis-architecture.adoc` Machine-local resolver enablement; `doc/specification/store-schemas/08-project-configuration-local.adoc` (`build-map.json`, `derivation_resolvers`)
- Watch: `doc/implementation-watch/domain-extensions.adoc` PM-WATCH-EXT-9, PM-WATCH-EXT-35

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- The classification of a Maven `*IT` path to `verify` is the one rule that depends on the build system; keep it in the Axis B implementation of PLAN-V2-09 (`domain-axes-and-descriptors`), not in the map.
- Re-verify the originals (`manage-config/scripts/_cmd_build_map.py`, `manage-architecture/scripts/_architecture_core.py` `classify_changed_path`) at the head of `plan-marshall`, as the specification asks (it pins `1d4a1da37`).

## Claim Labels

- OBSERVED: the package file and its index row (`open`, depends on nothing) — `doc/plans/domain-extensions-and-architecture.adoc` (What: seven bullets, Done When: six paragraphs) and `doc/plans/README.adoc` at `origin/main` `a8da4104`; the package is cut into seven plans because the seven bullets hold about thirty testable parts
- OBSERVED: the packages `core.domain`, `core.architecture` and `core.discovery` of `pm-core` hold only their `package-info.java` (15 lines each, Javadoc naming the specification part) at `a8da4104` — every class named in the plans of this package is new
- OBSERVED: `doc/specification/domain-extensions/03-module-discovery.adoc` lines 64-79 (Build Map & Deterministic `derive-verification`) state the seeding, write-once, explicit reseed, separate glob dialect, route precedence (anchor `route-precedence`), fail-closed read and the canonical command resolution rules; line 267 of `store-schemas/08-project-configuration-local.adoc` declares `derivation_resolvers`
- OBSERVED: no class reads `build-map.json` or `derivation_resolvers` (`grep -rn 'build-map\|derivation_resolvers' pm-*/src/main` finds nothing); `pm-core/src/main/java/de/planmarshall/core/config/package-info.java` is the only file of `core.config`, a directory declared by PLAN-V2-17, PLAN-46 and PLAN-V2-74
- HYPOTHESIS: the owning module of a path comes from the longest-prefix attribution of PLAN-V2-30 (`architecture-merges`) for non-module paths and from the module roots for module paths — confirm at the specification's `which-module` row (verify-at-outline)
- Verify-first clause: before editing, confirm that PLAN-V2-09 (`domain-axes-and-descriptors`), PLAN-V2-30 (`architecture-merges`) and PLAN-V2-14 (`module-discovery`) are merged, list the open pull requests touching `core/discovery/` and `core/config/`, and re-read the originals in the sibling checkout; a refutation loops back to re-scope.

## Expected Surface

- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/discovery/` new classes: `BuildMap` and its reader, the route-precedence matcher, `DeriveVerification`, the refusal types; `pm-core/src/main/java/de/planmarshall/core/architecture/` new class: the resolver binding reader (exact names at the outline)
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/discovery/` and `.../architecture/` new test classes and fixtures
- OBSERVED: `doc/plans/domain-extensions-and-architecture.adoc`, `doc/specification/domain-extensions/03-module-discovery.adoc` (the build-map section), `doc/implementation-watch/domain-extensions.adoc` (PM-WATCH-EXT-9, -35)
- HYPOTHESIS: `pm-core/doc/developer/core-discovery.adoc` and `pm-core/doc/adr/core-discovery/` — created by PLAN-V2-14 (`module-discovery`), extended additively

## Dependencies and Sequencing

- Depends on: PLAN-V2-83 (`skills-conformance-foundation`: the dependencies and rule R20); and then: PLAN-V2-09 (`domain-axes-and-descriptors`) (Axis B), PLAN-V2-30 (`architecture-merges`) (path attribution) and PLAN-V2-14 (`module-discovery`) (module roots).
- Overlaps with: the parked PLAN-V2-78 declares the `core/discovery/` directory (1 shared entry, it follows this package); none with PLAN-46, PLAN-105, PLAN-V2-39, PLAN-V2-43; `core.config` is not touched, so PLAN-V2-17/PLAN-46/PLAN-V2-74 are not overlapped.
- Adjacent to: `project-onboarding-unit` PLAN-V2-17 (seeds and reseeds the map; owns `local-config.json`); `change-map` (reads the classification).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-35-build-map-and-derive-verification.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

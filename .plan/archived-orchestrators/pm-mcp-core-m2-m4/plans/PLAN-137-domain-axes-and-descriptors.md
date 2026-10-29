# PLAN-137: The domain descriptor and Axes A and B: applicability, profiles and build-map routes

epic: pm-mcp-core-m2-m4
workstream: WS-05

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-137-domain-axes-and-descriptors.md` and is queued as one row file, `queue/PLAN-137.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r2`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R17, R18, R19. Package bookkeeping (R01, R05): this plan is 1 of 7 plans of the package `domain-extensions-and-architecture` (`doc/plans/domain-extensions-and-architecture.adoc`). It deletes from the package file the bullets of _What_ and the _Done When_ paragraphs it delivers and drops its deliverables from the covered sections of the specification and watch items; the plan that delivers the last bullet removes the package's row from `doc/plans/README.adoc` and deletes the package file. The plans of the package edit different sections of the same documents; whichever merges second merges the base branch (the lane's step 7) before the merge gate.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-domain.adoc` (new); ADR directory: `pm-core/doc/adr/core-domain/` (new directory, from `001`); layout: workstream WS-10.
- R12 — log message holder: none expected (`CoreLogMessages`, prefix `PM_CORE`; next free: INFO 2, WARN 103, ERROR 201).

## Objective

First part of the work package `domain-extensions-and-architecture` (Milestone 6): the declarative domain descriptor `<domain_id>.json` with its closed schema and fail-closed validation, the module facts it reads, Axis A (the applicability of a domain to a module, the profile skill lists and the promotion of optionals) and Axis B (build-system routes and the classification of a path into a build class), in package `core.domain`. Axis C and D (edge derivation and path ownership) are PLAN-129 (`architecture-merges`) and PLAN-131 (`architecture-resolvers`).

## Deliverables

1. **The module facts and the `build_system` decision.** `DiscoveredModuleFacts` (module id, path, packaging, declared dependency coordinates, the build systems, test-directory presence, file existence by module-relative path). Watch item PM-WATCH-EXT-36 is an open question: `when.build_system` holds when its value is a member of the module's build systems and `none` when discovery attributes none, but the module record of `store-schemas/09` has one build system and no `none`. The recommendation of the item is a `build_systems` array (empty means none), as in `plan-marshall`; this changes the stored module record, so it is the operator's decision (R07): ask at refine, record the decision in an ADR and in the specification, and delete the watch item.
2. **The descriptor and its closed validation.** A descriptor record and a validator over the closed schema of Domain Extensions § Declarative Domain Descriptor Schema (`format_version`, `domain_id`, `display_name`, `additive_to`, `always_on`, `provides_file_globs`, `skills_by_profile` including `core`, `minimal_profiles`, `triage_skill`, `arch_gate`, `applicability_rules` with `rule_id`, `when`, `activate_profiles` and `promote_optional_skills_to_default`, the technique preconditions, the domain verbs): an unknown key, an unknown axis, an unparsable document, a `domain_id` that does not match its file, a profile listed in both `minimal_profiles` and `skills_by_profile`, an unknown `additive_to` and an `additive_to` cycle each refuse the descriptor with the key and the file, and nothing is applied partially. The validator is hand-written over Jackson streaming like `ProfileValidator`; the JSON schema block of the specification is checked by a parity test.
3. **Axis A: `SkillLoadingAxis` and its declarative implementation.** `evaluateApplicability(DiscoveredModuleFacts)`: all predicates of one rule must hold (AND), a module matches when any rule matches (OR), an empty `when` matches every module; `packaging_equals`/`packaging_not_equals` compare the discovered packaging exactly (a module without packaging satisfies only the negative form), `dependency_contains` is a substring test over the coordinates, `file_exists` tests a module-relative path, `path_matches` uses the domain-detection glob dialect; a matching rule activates exactly the listed profiles and `promote_optional_skills_to_default` moves the named skills from `optionals` to `defaults` for that module; `core` is prepended into every profile (duplicates removed, first kept) but is never activatable; a `verification` task resolves only the core skills; an additive domain applies only where its parent applies and its skills follow the parent's. Tests: a fixture tree per predicate, the promotion, the additive pair `java-cui` over `java`.
4. **Axis B: `BuildRoutingAxis` and its declarative implementation:** the `classifyGlobs` routes `(glob, role)` with role `production`, `test` or `config` (single-`*` fnmatch globs or bare basenames, never `**`), `classifyBuildClass` (production to `compile`, test to `module-tests`, config to `verify`, any other role to `none`, unless the build system overrides it for a path), `classifyPaths` returning the route with the length of the matching glob as its specificity, and the canonical command and discovery argument templates per canonical verb. The longest-glob precedence across domains is the build map (PLAN-132 (`build-map-and-derive-verification`)), not here.
5. **Documentation and the shipped descriptors.** `core-domain.adoc` (new), ADRs (the `build_systems` decision, the fail-closed descriptor loading, the Axis A/B shape), and deletion of the implemented parts of `01-four-axis-architecture.adoc` (Core Architectural Invariants 1 and 2, the Axis A and B interfaces, the schema and the applicability semantics; the documentation-verbs section stays for PLAN-136 (`documentation-domain-verbs`)) and of PM-WATCH-EXT-2, -36 (and -23 where covered). The descriptors of the shipped domains are content of the product and live outside this repository: the tests here use fixture descriptors written for the test, and the test that every shipped descriptor validates is owed to `plan-marshall-mcp` after the SNAPSHOT deploy.

**Done when:** A fixture descriptor of each shape validates against the closed schema and an unknown key, unknown axis, unresolvable `domain_id`, `additive_to` cycle and unparsable file are each refused with the key and the file, never partially applied; Axis A selects the same modules for a fixture tree as the declared `applies_to_module` rules state, an optional skill promoted by `promote_optional_skills_to_default` appears in the resolved set, and a `when` predicate naming an unknown build system refuses the descriptor; Axis B classifies a production, a test and a configuration path into the expected `(role, build_class)`; the operator's decision on `build_system` is recorded; the guide and ADRs are written; the follow-up in `plan-marshall-mcp` for the shipped descriptors is named. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (`pm-core`; where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and the integration tests of every changed module with tests (`verify -Pintegration-tests -pl <module> -am`, only on `pm-core` and `pm-runtime`; here: `pm-core`) are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-EXT-1, PM-EXT-2
- Specification: `doc/specification/domain-extensions/01-four-axis-architecture.adoc` § Four-Axis Domain Architecture, Core Architectural Invariants, Sealed Java Interfaces (Axes A and B), Declarative Domain Descriptor Schema; `doc/specification/store-schemas/09-derived-facts-and-build-timings.adoc` (the module record, lines 197-201 per the watch item)
- Watch: `doc/implementation-watch/domain-extensions.adoc` PM-WATCH-EXT-2, -23, -36

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **Decided 2026-10-29 (operator walk, decision 11A of 18; the orchestrator was above 95% sure): option 1, a module record `build_systems` array (empty means none).** The specification sentence already reads "a member of the module's build systems", the watch item recommends it, and `plan-marshall` carries a list (`javascript-cui` needs npm and maven); one build system per module would split multi-system modules. R07 is satisfied; the plan records the result in the specification and the requirement and deletes PM-WATCH-EXT-36.
- **Fixtures, not product content:** descriptors and skill URIs in tests are written for the test. Where a component is ported from `plan-marshall`, the expected values are derived from the original at a pinned commit, committed as plain files and the commit recorded; no test runs Python or reads the sibling checkout.
- No new dependency (`CLAUDE.md`): validation is hand-written over `jackson-core`, like `ProfileValidator`.

## Claim Labels

- OBSERVED: the package file and its index row (`open`, depends on nothing) — `doc/plans/domain-extensions-and-architecture.adoc` (What: seven bullets, Done When: six paragraphs) and `doc/plans/README.adoc` at `origin/main` `a8da4104`; the package is cut into seven plans because the seven bullets hold about thirty testable parts
- OBSERVED: the packages `core.domain`, `core.architecture` and `core.discovery` of `pm-core` hold only their `package-info.java` (15 lines each, Javadoc naming the specification part) at `a8da4104` — every class named in the plans of this package is new
- OBSERVED: the sealed interfaces, the descriptor schema and the applicability semantics are specified in `doc/specification/domain-extensions/01-four-axis-architecture.adoc`: Axis A `SkillLoadingAxis` permits `DeclarativeDomainAxisA`, Axis B `BuildRoutingAxis` permits `DeclarativeBuildSystemAxisB` (file lines 29-169), the descriptor schema from line 170, the `when` semantics at line 344 and after
- OBSERVED: `doc/implementation-watch/domain-extensions.adoc` line 80 PM-WATCH-EXT-36 "Open: the build-system predicate against a module record with one build system" — Guard: "The plan implementing this anchor decides the point (with the operator where it changes scope)"
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/profile/ProfileValidator.java` is the precedent for a hand-written closed-schema validator over Jackson streaming with JSON-pointer refusals; `pm-core/pom.xml` declares `jackson-core` and no JSON-schema library at compile scope (`json-schema-validator` is test scope)
- HYPOTHESIS: the technique preconditions (descriptor member `technique_preconditions`, Core Architectural Invariant 4) share the `when` grammar of Axis A and are evaluated by the renderer (PLAN-133 (`client-profile-renderer`)) from the results this plan's evaluator produces — confirm at the specification (verify-at-outline)
- HYPOTHESIS: the shipped descriptors (`classpath:/domains/<domain_id>.json`) are content of `plan-marshall-mcp` (they name `skill://` URIs); confirm with a look at that repository's `src` and `pm-modules` before deciding the test location
- Verify-first clause: before editing, list the open pull requests touching `core/domain/`, re-read the schema block of `01-four-axis-architecture.adoc` and PM-WATCH-EXT-36 at the head, and build the `build_systems` array as decided on 2026-10-29 (no question to the operator is left); a refutation loops back to re-scope.

## Expected Surface

- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/domain/package-info.java` — existing, Javadoc extended
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/domain/` new classes: `DiscoveredModuleFacts`, the descriptor record and validator, `SkillLoadingAxis`, `DeclarativeDomainAxisA`, `ApplicabilityMatch`, `ProfileSkills`, `BuildRoutingAxis`, `DeclarativeBuildSystemAxisB`, `BuildMapRoute`, `BuildClass`, `BuildMapRole`, `ClassifiedPathRoute`, `CanonicalBuildVerb`, `ArgumentTemplate` (names as the specification's code block; confirmed at the outline)
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/domain/` new test classes (including a descriptor-schema parity test reading `01-four-axis-architecture.adoc`: R10, the block stays byte-stable) and fixtures under `pm-core/src/test/resources/domains/` (new)
- OBSERVED: `doc/plans/domain-extensions-and-architecture.adoc`, `doc/specification/domain-extensions/01-four-axis-architecture.adoc`, `doc/specification/store-schemas/09-derived-facts-and-build-timings.adoc` (the module record, if the decision changes it), `doc/implementation-watch/domain-extensions.adoc` (PM-WATCH-EXT-2, -23, -36)
- HYPOTHESIS: `pm-core/doc/developer/core-domain.adoc` and `pm-core/doc/adr/core-domain/` — new

## Dependencies and Sequencing

- Depends on: nothing.; PLAN-158 (`role-name-single-definition`) (`RoleName` and `ClientId` in `core.id`: the one definition of the role-name pattern, enforced by a guard that fails on a copy)
- Overlaps with: none with PLAN-46, 105, 108, 109 (no `core/domain/` entry; PLAN-45/46/63 declare `core/config/`, which this plan does not touch). `01-four-axis-architecture.adoc` is shared with PLAN-136 (`documentation-domain-verbs`) (different section); `store-schemas/09` and the watch file with the other plans of the package (different sections).
- Adjacent to: PLAN-133 (`client-profile-renderer`) (reads the precondition results); `project-onboarding-unit` PLAN-45 (the setup that reads the descriptors); the shipped descriptors in `plan-marshall-mcp`.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-137-domain-axes-and-descriptors.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

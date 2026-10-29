# PLAN-V2-10: The production registries of effective configuration keys and fact kinds, wired into the unit verifier

epic: pm-mcp-core-m2-m4-v2
workstream: WS-01
origin: PLAN-161 of the epic pm-mcp-core-m2-m4

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-10-production-registries-and-wiring.md` and is queued as one row file, `queue/PLAN-V2-10.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.
>
> This is the FIRST of two specs of the operator's decision 3.2 (the repository owns what `PLAN-V2-04` (`unit-input-interface`) and `PLAN-V2-15` (`call-time-inputs-and-slots`) leave without a package): A, this one, the production registries and their wiring; B, `PLAN-V2-20-git-commit-primitives.md`, the commit primitives and the entity kind `commit-message`, which `Depends on:` this one. It builds what `PLAN-V2-04` Decision 4 left as arguments of the verifier.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r3`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R10, R11, R12, R15, R17, R18, R19. (R12 applies only if the trace matrix shows a message to add; R10 because `EffectiveConfigKeysSchemaParityTest` reads the schema blocks of `store-schemas/07`, `08` and `12`; R15 is the checklist for the new `pm-workflow` class; R09, R13, R14 and R16 do not apply: no path, no versioned artifact, no `core.store`, no `pm-runtime`.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-config.adoc` (new, package `core.config`; `PLAN-V2-17` and `PLAN-46` also create it, whichever merges first creates it and the others add a section) and `pm-workflow/doc/developer/workflow-unit.adoc` (exists; a section "Product registries"); ADR directories: `pm-core/doc/adr/core-config/` (new) and `pm-workflow/doc/adr/workflow-unit/`; layout: workstream WS-03.
- R12 — log message holder: `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`) and `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`); none expected.

## Objective

`PLAN-V2-04` makes a unit declare `reads_config` and `reads_fact` and checks them against a `ConfigKeyRegistry` and a `FactKindRegistry` that are ARGUMENTS of the verifier, defaulting to EMPTY registries (a unit that declares a read fails closed). No production registry exists: `pm-core` `core.config` holds `package-info.java` only, and no class names the derived fact kinds. Until this plan the production verifier therefore refuses every unit that declares a `reads_*`. This plan builds the two production registries and the one place that assembles a verifier context with them:

- the closed table of the effective configuration keys of Store Schemas 07 and 08 in the effective-key form `<file_stem>.<dotted path>`, each with its value domain and whether a compiled default exists, as code of `pm-core` (`core.config`), pinned to the schema blocks by a parity test (R10), because the specification is not on the classpath of a JAR;
- the `FactKindRegistry`, built from the closed enumeration `DerivedFactKind` that `PLAN-V2-02` builds (no second list of the sixteen kinds), and the `ConfigKeyRegistry`, built from the table;
- `ProductRegistries`, one class in `pm-workflow` `workflow.unit`, that returns the `VerificationContext` the three verifier call sites read (`ProjectUnitLoader`, `DefaultUnitLoader` and `PlanInstanceComposer` all take the registries from the context): the default entity kinds, question classes and schemas plus the two registries of this plan. The assembly passes the primitives and the compiled units and gets a context; the empty registries stay the default of the test constructors.

**Decisions this spec takes (each becomes an ADR, R06):**

1. *The key table is compiled code, pinned by a parity test, not read from the specification.* `doc/` is not on the classpath of the JARs (CLAUDE.md: the modules carry no content from the specification tree) and the specification says the pinned compiled defaults live in a table that a test pins (`07-project-configuration.adoc` line 71). Alternative rejected: deriving the key set at run time from a copy of the schema blocks (a second copy of the schema in a resource, which drifts).
2. *The stems are `project`, `workflow` and `local-config`.* These are the three stems `PLAN-V2-04` lets `reads_config` name (`derived.` belongs to `reads_fact`). The team files `domains.json` and `build-map.json` (Store Schemas 08, blocks 0 and 1) are lists and maps of records without a key a guard or a unit reads, and the file metadata members `format_version` and `revision` are not configuration values: all four are left out and the parity test says so.
3. *A key is any addressable node, not only a leaf.* `PLAN-V2-15` Deliverable 4 declares `reads_config: local-config.commit_trailer` and `local-config.commit_identity`, which are objects (`08-project-configuration-local.adoc` lines 221 and 252). A node with `properties` is a key of domain object; a map node (`additionalProperties` as a schema, `propertyNames`) contributes a wildcard segment `*` that matches exactly one non-empty identifier segment (`workflow.plan.phase-6-finalize.steps.*.lane`).
4. *`hasCompiledDefault` is the schema keyword `default` OR a listed prose default, with no edit of the specification blocks.* Some nodes state a compiled default only in their description (`commit_trailer`: "compiled default plan-marshall <noreply@cuioss.de>", line 252), others have the keyword (`review.currency`). The code table carries the flag; the parity test derives the expected flag as "keyword present, or the node is in the test's list `PROSE_DEFAULTS`", and a second assertion fails for a node whose description names a default, has no keyword and is not listed (a new prose default must be classified by whoever adds it). A key whose presence is itself the answer (`review.required_bots`, item 1 of the specification) has no default. Alternative: add the missing `default` keywords to the blocks — rejected here because it edits the blocks other plans read; the operator may veto (see the operator questions in the report).
5. *The value domain is the closed set the schema subset can express:* `Enumeration(values)`, `IntegerRange(min?, max?)`, `Bool`, `Text`, `ListOf(domain)`, `Object`. `PLAN-V2-04`'s check "the domain of an `input_fallback` key is assignable to the input type" reads exactly this (enum to enum with a subset of values, integer range inside range, list of enum to `list<enum>`).
6. *`FactKindRegistry` has no list of its own:* it is `DerivedFactKind.values()` by wire name (`PLAN-V2-02` Deliverable 2). The registry of value schemas of `PLAN-V2-02` Deliverable 5 (`DerivedFactKinds`) and the store of `PLAN-V2-05` are NOT used: a unit's `reads_fact` names a kind, never a value.
7. *One factory, no hidden default.* The production context is built by `ProductRegistries.context(...)`; no production class builds a `VerificationContext` with empty registries. The empty registries of `PLAN-V2-04` remain the default of the existing test constructors, and a test shows that the same unit that passes under the product context is refused under the empty one (the control).

## Deliverables

1. **The effective key table (test first).** In `pm-core` package `core.config`: `ConfigValueDomain` (sealed: the six members of Decision 5), `EffectiveConfigKey` (record: key, domain, `hasCompiledDefault`; the key grammar `stem "." segment {"." segment}` with `segment` an identifier or `*`; the constructor refuses a stem other than the three, an empty segment, a `derived.` stem) and `EffectiveConfigKeys` (the compiled table, immutable: `all()`, `find(String key)` that matches a wildcard segment, `stems()`). About 100 keys: `project` 15, `workflow` 20 (7 wildcard), `local-config` 63 (8 wildcard), counted over the three blocks with `format_version` and `revision` left out. `find` answers the exact key and the key whose wildcard segment matches, never a prefix of a key.
   **Done when:** `EffectiveConfigKeysTest` covers the grammar refusals (unknown stem, `derived.push_remote`, empty segment, `a..b`), `find` for an exact key, a wildcard match and a non-match, the object key `local-config.commit_trailer` and its member `local-config.commit_trailer.email`, and the immutability; every `EffectiveConfigKey` constructor refusal has a case.
2. **The parity test with the schema blocks (R10; test first).** `EffectiveConfigKeysSchemaParityTest` in `pm-core/src/test/java/de/planmarshall/core/config/` reads the schema blocks with `SpecificationDocument.jsonBlockAfter` (`ProjectConfigFile` after `=== Team File project.json` and `WorkflowConfigFile` after `=== Team File workflow.json` of `07-project-configuration.adoc`; `OperatorLocalConfig` after `== Operator Local Configuration` of `08-project-configuration-local.adoc`; the `$defs/cleanup_duration` of `12-machine-stores.adoc` for the three external `$ref`s of lines 157 to 159) and computes the expected key set by the walk of Decision 3 (internal `$ref`, `allOf` of `#/$defs/settings`, wildcard from map nodes). It asserts that the table equals that set (no key missing, none extra), that each domain equals the schema's `type`, `enum`, `minimum` and `maximum` and `items`, and the default flag by Decision 4 (`PROSE_DEFAULTS` is a constant of the test with one line of reason per key); the stems and the four left-out names are asserted, not implied.
   **Done when:** the test passes at the head and fails when a key, an enumeration value, a bound or the keyword `default` of the schema block is changed without the table (shown once on a scratch copy, stated in the pull request body); the second assertion of Decision 4 fails for a planted undeclared prose default.
3. **The registries and the wiring (test first).** In `pm-workflow` package `workflow.unit`, the new class `ProductRegistries` (final, static): `configKeys()` (a `ConfigKeyRegistry` of `PLAN-V2-04` built from `EffectiveConfigKeys.all()`), `factKinds()` (a `FactKindRegistry` of `PLAN-V2-04` built from the wire names of `DerivedFactKind.values()`) and `context(PrimitiveRegistry, List<WorkflowDefinition> compiledUnits)` returning a `VerificationContext` with `EntityKindRegistry.defaults()`, `QuestionClassRegistry.defaults()`, `SchemaRegistry.defaults()`, `configKeys()` and `factKinds()`. The exact constructors and factories of `ConfigKeyRegistry` and `FactKindRegistry` are those `PLAN-V2-04` merged (read them at the outline); if they take their own declaration records, `ProductRegistries` converts, and nothing in `pm-core` knows `pm-workflow`.
   **Done when:** `ProductRegistriesTest` shows, with the completeness derived from the sources at run time (R15 item 4): the fact kinds of the registry are exactly the wire names of `DerivedFactKind.values()`; the config keys are exactly `EffectiveConfigKeys.all()`; the registries are not empty (one key per stem); a `derived.` key is not a config key; `context(...)` holds the default entity kinds, question classes and schemas and the two registries; `ProductRegistriesWiringTest` (Deliverable 4) passes.
4. **The three call sites read them (test first).** `ProductRegistriesWiringTest` (new, `pm-workflow/src/test/java/de/planmarshall/workflow/unit/`) builds a `DefaultUnitLoader` (over a fixture classpath root), a `ProjectUnitLoader` and a `PlanInstanceComposer` with the product context and shows, per call site: a unit with `reads_config: project.review.currency`, `reads_fact: push_remote` and a required input `currency` of type `enum[exact_sha, after_last_push]` with `input_fallback: project.review.currency` passes (`review.currency` has the compiled default `after_last_push`, `07` line 159); a unit with the misspelled key `project.review.curency` is refused as `CONFIG_KEY_BINDING`, with the unknown kind `push_remot` as `FACT_KIND_BINDING`, with `reads_config: derived.push_remote` as refused, and a required input whose `input_fallback` is `project.merge_method` (no compiled default) as `INPUT_DECLARATION`; the same passing unit is refused under a `VerificationContext` built with the empty registries (the control of Decision 7). The fixtures are new `.pmwf` files below `pm-workflow/src/test/resources/unit-fixtures/product-registries/`, each a tree the parser accepts (R15 item 1), with a CRLF twin of the passing one (R15 item 2).
   **Done when:** the test class passes for the three call sites and the control, and no existing test file is edited.
5. **Documentation and the specification (R05, R06, R10).** One ADR per decision above (7; next free numbers of `pm-core/doc/adr/core-config/` (new directory, starts at 001) and `pm-workflow/doc/adr/workflow-unit/`, renumbered against `origin/main`, R11); `core-config.adoc` (the table, the grammar, the wildcard, how to add a key: a schema change, a table row, a parity-test pass) and the section "Product registries" of `workflow-unit.adoc`; in `store-schemas/07-project-configuration.adoc` one sentence appended to the paragraph at line 71 ("Implemented by `EffectiveConfigKeys` for the key set, the domains and the existence of a compiled default; Remaining: the values of the compiled defaults"), no schema block touched (R10); `doc/Traceability.adoc` lines for PM-EXT-6 and PM-IMPL-5 (shared bookkeeping). The compiled default VALUES table that line 71 describes is not built here (Dependencies).
   **Done when:** the links check (R17) reports `BROKEN LINKS 0` or only links of the sibling's own deleted items; the pull request body lists **Owed to the milestone exit** and the **external hand-offs**; the schema blocks of `07`, `08` and `12` are byte-identical to `origin/main`.

**Done when:** `EffectiveConfigKeys` is pinned to the closed schemas; `ProductRegistries.context(...)` gives the three verifier call sites a registry that accepts the real keys and fact kinds and refuses an unknown one; nothing in `pm-core` knows `pm-workflow`; the five deliverables' tests exist and pass beside every existing test of `pm-core` and `pm-workflow`; `verify`, `verify -Ppre-commit` (its rewrites reviewed and committed), `verify -Pcoverage` and `verify -Pintegration-tests -pl pm-core -am` are green. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit`, then `verify` of `pm-core` and `pm-workflow` (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed and new classes and the per-module run, each reported in the pull request body). **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main`; a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own.

**Trace starting set:** PM-EXT-6 (configuration split by owner class), PM-IMPL-5, PM-WF-16; Store Schemas 07 § Effective Configuration and Sources (item 4, the key forms) and 08 § Operator Local Configuration; Workflow DSL § Formal EBNF Grammar (`config_key`, `depends_on_config`) and § Static AST Verification Rules (Rule 1). Watch: Workflow DSL.

## Standing Constraints

- **Log messages: none expected.** The registries are values; a refusal is a `Violation` of `PLAN-V2-04`.
- **No edit of a schema block** of `07`, `08` or `12` (R10); the parity test reads them. A key the schema gains later is a table row in the same pull request.
- **No dependency of `pm-core` on `pm-workflow`, none of `pm-workflow` on a provider** (CLAUDE.md, enforcer); the factory lives in `pm-workflow`, the table and the enum in `pm-core`.
- **`DerivedFactKind` is read, not edited** (`PLAN-V2-02`); `DerivedFactKinds` and the store of `PLAN-V2-05` are not used.
- **The existing verifier constructors keep their empty defaults** (the contract of `PLAN-V2-04`); this plan adds a factory, it changes no constructor.
- **Add no dependency and no plugin** (CLAUDE.md).

## Claim Labels

All lines read at `origin/main` `26008c5c` (the local ref; `fetch` was not allowed, and the sibling checkout of the documentation was read at its local `origin/main` `24eabd4`, which may lag).

- OBSERVED: no registry exists — `git grep -i -E 'ConfigKeyRegistry|FactKindRegistry|EffectiveConfig|DerivedFact'` over `pm-*/src` of `origin/main` finds only the lock key `LockKey.derivedFacts`; `pm-core/src/main/java/de/planmarshall/core/config/package-info.java` lines 17 to 19 list `store-schemas/07`, `08`, `09` and the package declaration is line 23 (no other file in `core/config/`); `plan-marshall-mcp` `origin/main` `41094bc` has no hit for `ConfigKey`, `FactKind`, `PrimitiveRegistry`; no landing and no Open Defect of `epic.md` names a registry (the third-plan note is `epic.md` line 413)
- OBSERVED: the three call sites read the context — `pm-workflow/src/main/java/de/planmarshall/workflow/unit/VerificationContext.java` line 37 (record of `registry`, `entityKinds`, `questionClasses`, `schemas`, `compiledUnits`) and line 63 (`validator(...)` builds the `WorkflowValidator`); `…/unit/ProjectUnitLoader.java` line 170 (`context.validator(subUnits, List.of()).verify(unit)`); `…/unit/DefaultUnitLoader.java` lines 58 and 73 (the context is a constructor argument); `…/plan/PlanInstanceComposer.java` line 249 (`new WorkflowValidator(context.registry(), List.of(), context.entityKinds(), …)`); `git grep 'new VerificationContext('` over `pm-*/src/main` finds no production caller (the assembly builds it)
- OBSERVED: the defaults that exist — `EntityKindRegistry.defaults()` line 89, `QuestionClassRegistry.defaults()` line 75, `SchemaRegistry.defaults()` line 64
- OBSERVED: the effective key form and the compiled-default table — `doc/specification/store-schemas/07-project-configuration.adoc` line 67 (item 4: "The effective key of a value is `<file_stem>.<dotted path>`", the examples `project.review.currency`, `workflow.plan.phase-2-refine.compatibility`, `local-config.plan_defaults.asking`, `derived.push_remote`) and line 71 ("a test pins that table"); `doc/specification/workflow-dsl/01-grammar.adoc` lines 209 (`config_key = identifier , { "." , identifier }`) and 243 (`depends_on_config`, the same form)
- OBSERVED: the schema blocks — `07` has two JSON blocks (`ProjectConfigFile`, `WorkflowConfigFile`), `08` three (`DomainsConfigFile`, `BuildMapConfigFile`, `OperatorLocalConfig`); walked read-only on 2026-10-29: `project` 15 nodes, `workflow` 20 (7 below a map), `local-config` 63 (8 below a map), `format_version` and `revision` excluded; `OperatorLocalConfig` uses `allOf` of `#/$defs/settings` (`08` line 187) with `unevaluatedProperties: false` (line 188) and three external `$ref`s to `machine-config.json#/$defs/cleanup_duration` (lines 157 to 159), whose definition is in `12-machine-stores.adoc` line 205
- OBSERVED: the object-valued keys — `08` lines 221 (`commit_identity`) and 252 (`commit_trailer`, description "compiled default plan-marshall <noreply@cuioss.de>", no `default` keyword)
- OBSERVED: no test reads these blocks today — `git grep -E '07-project-configuration|08-project-configuration' origin/main -- 'pm-*/src/test'` finds none; `SpecificationDocument.jsonBlockAfter` is a `pm-core` test helper (`pm-core/src/test/java/de/planmarshall/core/SpecificationDocument.java` line 72), so the parity test lives in `pm-core`
- OBSERVED: the pattern to follow — `pm-core/src/test/java/de/planmarshall/core/store/JobRecordSchemaParityTest.java` lines 60 to 62 (reads a block and compares members)
- OBSERVED: `pm-core/doc/developer/core-config.adoc` and `pm-core/doc/adr/core-config/` do not exist at `origin/main`; `PLAN-V2-17` and `PLAN-46` list the guide as new
- HYPOTHESIS: `PLAN-V2-04` merges `ConfigKeyRegistry` and `FactKindRegistry` as value objects of `workflow.verify` with a constructor over declaration values; the adapter code of Deliverable 3 follows what it merged
- HYPOTHESIS: `PLAN-V2-02` merges `DerivedFactKind` with `fromWireName` and a wire-name accessor, so the registry reads the wire names without a mapping table
- HYPOTHESIS: the walk of Decision 3 needs `propertyNames` only to confirm the wildcard segment's pattern; confirm at the outline against `workflow.plan.phase-6-finalize.steps.*`, `project_steps.*` and `local-config.upstream_mcp.*`
- Verify-first clause: before editing, confirm `PLAN-V2-04` and `PLAN-V2-02` (Deliverable 2) are merged, list the open pull requests for one that touches `pm-core/src/main/java/de/planmarshall/core/config/`, `store-schemas/07`, `08` or `12` (`PLAN-V2-17`, `PLAN-46`, `PLAN-V2-24`, `PLAN-V2-72`, `PLAN-V2-38`, `PLAN-V2-74` declare them), and re-walk the three blocks at the head; a refutation loops back to re-scope.

## Expected Surface

Code and tests (`pm-core`, package `de.planmarshall.core.config`; every entry is a new file, R19):

- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/config/ConfigValueDomain.java`, `EffectiveConfigKey.java`, `EffectiveConfigKeys.java` — new (D1)
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/config/EffectiveConfigKeysTest.java`, `EffectiveConfigKeysSchemaParityTest.java` — new (D1, D2)
- OBSERVED, read not edited: `pm-core/src/main/java/de/planmarshall/core/config/package-info.java` (already lists the three specification parts), `pm-core/src/test/java/de/planmarshall/core/SpecificationDocument.java`

Code and tests (`pm-workflow`, package `de.planmarshall.workflow.unit`; new files):

- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/unit/ProductRegistries.java` — new (D3)
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/unit/ProductRegistriesTest.java`, `ProductRegistriesWiringTest.java` — new (D3, D4)
- HYPOTHESIS: `pm-workflow/src/test/resources/unit-fixtures/product-registries/*.pmwf` — new directory (D4)
- Read, not edited: `ConfigKeyRegistry`, `FactKindRegistry`, `VerificationContext`, `WorkflowValidator` (`PLAN-V2-04`), `DerivedFactKind` (`PLAN-V2-02`), `ProjectUnitLoader`, `DefaultUnitLoader`, `PlanInstanceComposer`, `EntityKindRegistry`, `QuestionClassRegistry`, `SchemaRegistry`

Specification (a specific file, R19):

- OBSERVED: `doc/specification/store-schemas/07-project-configuration.adoc` — one sentence at line 71; the schema blocks byte-stable (R10); `08` and `12` are read, not edited

New documents to list (R19): `pm-core/doc/developer/core-config.adoc`, `pm-core/doc/adr/core-config/` (both new).

Not surface (R19, shared bookkeeping): `doc/plans/README.adoc`, `doc/Traceability.adoc`, `doc/LogMessages.adoc`, the existing developer guide `pm-workflow/doc/developer/workflow-unit.adoc` and the ADR directory `pm-workflow/doc/adr/workflow-unit/`.

## Dependencies and Sequencing

- Depends on: `PLAN-V2-04` (`unit-input-interface`: the two registry classes, the context that carries them, Rule 1 bindings; it is itself after `PLAN-105`); `PLAN-V2-02` (`derived-facts-records-and-kinds`, Deliverable 2 only: the enumeration `DerivedFactKind`; `DerivedFactKinds`, the codec and `PLAN-V2-05` are not needed). Not a dependency: `PLAN-V2-15` (this plan's fixtures are its own).
- **Overlaps with live plans** (exact entries of the Expected Surfaces read on 2026-10-29): `core/config/` directory, no shared file: `PLAN-V2-17`, `PLAN-46` (running), `PLAN-V2-74` 3; the new guide `core-config.adoc`: `PLAN-V2-17`, `PLAN-46` 2 (additive sections; whichever merges later rebases); `store-schemas/07`: `PLAN-V2-72`, `PLAN-V2-38` 2 (this plan edits line 71 only; they edit other parts); `store-schemas/08` (read only): `PLAN-V2-17`, `PLAN-V2-24`, `PLAN-V2-72`, `PLAN-V2-38`, `PLAN-V2-74` 5; `workflow/unit`: `PLAN-V2-54`, `PLAN-V2-69`, `PLAN-V2-57` edit the loaders, `PLAN-V2-04` the context (this plan adds new files only, 0 shared files). With `PLAN-V2-02`, `PLAN-V2-05`: 0 (read only). With `PLAN-105`, `PLAN-V2-39`, `PLAN-V2-43`, `PLAN-V2-13`: 0. With `PLAN-V2-15` and the second plan of this pair: 0 files (disjoint).
- **Verdict for the disjointness gate:** disjoint at the file level from every live plan except the new developer guide `core-config.adoc` (`PLAN-V2-17`, `PLAN-46`, an additive document) and the one sentence of `07`; not startable before `PLAN-V2-04` and `PLAN-V2-02` merge.
- **External hand-offs (not specced here; list them in the pull request body):** (1) `plan-marshall-mcp`, package `engine-wiring` (CDI producers): build the `VerificationContext` with `ProductRegistries.context(primitiveRegistry, compiledUnits)` and pass it to the loaders and the composer; (2) the VALUES of the compiled defaults (`07` line 71: "the compiled default table ... a test pins that table") are built by no package of the index (the neighbours are `project-onboarding-unit`, `machine-onboarding-unit`, `machine-settings-and-axes`); this plan pins only their existence; an operator decision whether to stage it; (3) Rule 3 `depends_on_config` still checks no key (ADR `workflow-verify/004` and `006`): the registry could serve it, outside this plan.
- Blocks: the second plan of this pair (it verifies the `commit_and_push` unit of `PLAN-V2-15` against the product context).
- Adjacent to: `plan-marshall-mcp` and `pm-mcp-clients`, which stay untouched.

## Package Bookkeeping

No work package file of `doc/plans/` covers this plan (the neighbours are `project-onboarding-unit`, `machine-onboarding-unit` and `process-designer`, which are other plans); like `PLAN-95` it has no package file and no index row. R01's removal of a row and a file does not apply; R05's reduction applies to the one sentence it touches.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-10-production-registries-and-wiring.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

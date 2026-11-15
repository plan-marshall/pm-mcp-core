# PLAN-V2-71: Profile validation requires the skill-load template to name the skill (`{{uri}}`)

epic: pm-mcp-core-m2-m4-v2
workstream: WS-04
origin: PLAN-145 of the epic pm-mcp-core-m2-m4

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-71-profile-skill-load-template-check.md` and is queued as one row file, `queue/PLAN-V2-71.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R13, R17, R18, R19, R20. (R12 is dropped: a refusal is a `ProfileRefusal`, no log record is added.) Package bookkeeping (R01, R05): this plan delivers the on-load half of the one sentence of _Rendering Invariants_ item 5 ("which profile validation checks"); the package `client-profile-rendering` (`doc/plans/client-profile-rendering.adoc`, tasks 1 to 4, done-when list) keeps every task open until the renderer (PLAN-V2-52) has landed, so this plan **does not edit the package file or its index row**. The specification states the requirement once (condense and point, no "Implemented by" or "Remaining:" paragraph).

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-profile.adoc` (existing, extended); ADR directory: `pm-core/doc/adr/core-profile/` (the next free number is 016 at the emit; renumber at the head — only if the plan takes a decision beyond the rule); layout: workstream WS-05.

## Objective

The package `client-profile-rendering` says that profile completeness — every canonical placeholder has a resolution in every profile the operator installs — is checked by profile validation on load. The profile schema already requires all seven tool names and the `load_directive_template`; what the validator does not check is that the template contains the `{{uri}}` slot, so a profile whose template is `pm_skill()` is valid and `{{skill_load:<uri>}}` would render a directive that names no skill. Add that one check and its tests.

## Deliverables

1. **The rule in `ProfileValidator`:** `skill_loading.load_directive_template` must contain the slot `{{uri}}` exactly once; a template without it, or with it twice, is refused with the pointer `/skill_loading/load_directive_template`. Tests in `ProfileValidatorTest` (a parameterized case: none, one, two, a `{{uri}}` split by a character; the literal `{{uri}}` is the contract and the test says so in one comment) written first, and `ShippedProfilesTest.everyShippedProfileValidates` is the proof that every shipped profile still validates (report its result in the pull request body).
   - Skills: pm-dev-java:java-core, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
2. **Specification and documentation:** the `description` of `load_directive_template` in `pm-core/src/main/resources/de/planmarshall/core/profile/harness-profile-v1.schema.json` (the schema block of `01-profile-schema.adoc` is included from that file) states the requirement — the description string only, no `pattern`, no new member, format version 1 (R13) — and `HarnessProfileSchemaParityTest` stays green unedited; `pm-core/doc/developer/core-profile.adoc` names the rule among the validator's checks. If a shipped profile of `pm-api` lacked the slot, that is a change in `pm-mcp-clients`, reported as a finding and not worked around (at the emit all five carry it once).
   - Skills: pm-documents:ref-asciidoc
   - Persona: documenter
3. **The residue of PLAN-V2-68 in the same class and test** (the landing analysis, `landings/PLAN-V2-68.md`): (a) the refusal message of the margin rule in `ProfileValidator.connection` says "is not 30 seconds below the idle timeout", which reads as "not exactly 30 below"; reword it to say the bounded wait must be at least the margin below the idle timeout, still without the words "host" and "client", and assert the wording once in `ProfileValidatorTest` (one `detail()` assertion for the refused 271/300 case); (b) the three acceptance assertions of `ProfileValidatorTest.marginBoundaryAccepted` (`assertEquals("acme-cli", clientId(...))`) prove only that nothing was thrown: make each assert a value specific to its case (the connection value that was loaded, read through the loaded document or the accessor the test class already uses; if none exists, assert the absence of a refusal explicitly and say so in the display name), and wrap the over-long line `ProfileValidatorTest.java:184`; (c) the Javadoc of `IDLE_MARGIN_SECONDS` (`ProfileValidator.java:44`) says "host": say "harness". Ground each at the head before editing.
   - Skills: pm-dev-java:java-core, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer

**Done when:** A profile whose `load_directive_template` lacks `{{uri}}` or carries it twice is refused on load with the pointer named; every shipped profile of the `pm-api` SNAPSHOT validates; the schema description and the guide state the rule; the margin refusal message says "at least", is asserted, and the acceptance assertions of `marginBoundaryAccepted` each fail for a wrong loaded value, not only for a thrown refusal. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (`pm-core`; where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and the integration tests of every changed module with tests (`verify -Pintegration-tests -pl <module> -am`, only on `pm-core` and `pm-runtime`; here: `pm-core`) are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-CLIENT-2
- Specification: `doc/specification/harness-profiles/02-placeholders.adoc` § Rendering Invariants, item 5 (Profile Completeness); `doc/specification/harness-profiles/01-profile-schema.adoc` `skill_loading.load_directive_template` (the schema block is the file `harness-profile-v1.schema.json`)
- Watch: `doc/implementation-watch/harness-profiles.adoc` (the watch items the specification part names at `02-placeholders.adoc:101`)

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- `ProfileValidator` and `ProfileValidatorTest` are shared ground: PLAN-46 (#233), PLAN-V2-39 (#412, did not touch either file) and PLAN-V2-68 (#426) have landed, so there is nothing to wait for; start from the head of `main` and re-read `skillLoading` there. PLAN-V2-43, 63, 135 and 114 are staged and also name these files; the later one rebases.
- **The specification is the authority on the rule's shape:** the schema description states it, the validator enforces it; a finding that the specification says otherwise (for example a harness that loads skills without a URI) is raised in the inbox message, not decided by the lane.

## Claim Labels

- OBSERVED: the package file and its index row (`open`, depends on nothing) — `doc/plans/client-profile-rendering.adoc` (What, Trace, Done When) and `doc/plans/README.adoc` at `origin/main` `a8da4104`
- OBSERVED: `ClientProfileRenderer` does not exist: `grep -rn 'ClientProfileRenderer' pm-*/src` finds no class (the name appears in specification and watch text only); package `core.profile` holds `CanonicalJson`, `EndpointProfile`, `EnrolledHarness`, `JsonDocument`, `JsonNode`, `JsonPointer`, `LoadedProfile`, `ProfileLoader`, `ProfileRefusal`, `ProfileRules`, `ProfileSet`, `ProfileSetKind`, `ProfileSets`, `ProfileStages`, `ProfileValidator`, `ShippedProfiles`, `SupportedVersions`, `VerificationRecord`, `WorkerParameters`, `package-info` — listed with `ls` at `a8da4104`
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/profile/ProfileValidator.java` lines 597-603: `skillLoading` checks `mode`, `schema_residency`, `skills_extension` and the template as a model-readable string (`text(..., 256)`, `stringRule(template, ..., true)`), and nothing requires `{{uri}}`; lines 1332-1336 only accept `uri` as one of the closed placeholders
- OBSERVED: the schema block of `01-profile-schema.adoc` lines 124-138 requires `mode`, `load_directive_template`, `schema_residency`, `skills_extension` and gives the template an example with `{{uri}}`, with no `pattern` or `contains`
- HYPOTHESIS: the specification intends `{{uri}}` to be mandatory (the rendering rule `{{skill_load:<uri>}}` resolves to the directive naming the `<uri>`); the table of Client Profiles § Placeholders shows it in every resolution — confirm with the operator if any harness is meant to load skills without a URI (verify-at-outline)
- Verify-first clause: before editing, confirm that PLAN-46, PLAN-V2-39 and PLAN-V2-43 are merged (they declare `ProfileValidator.java`) and re-read lines 597-603 at the head; a refutation loops back to re-scope.

## Expected Surface

- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/profile/ProfileValidator.java` — `skillLoading`
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/profile/ProfileValidatorTest.java` — the new cases
- OBSERVED: `pm-core/src/main/resources/de/planmarshall/core/profile/harness-profile-v1.schema.json` — the `description` of `load_directive_template` only (D2)
- OBSERVED: `pm-core/doc/developer/core-profile.adoc` — developer documentation, extended (D2)

## Dependencies and Sequencing


- **Landing note (2026-10-29):** PLAN-46 landed (#233): the dependency on PLAN-46 for `ProfileValidator` is spent.
- Depends on: PLAN-V2-83 (`skills-conformance-foundation`: the dependencies and rule R20); and then: PLAN-46 (`machine-onboarding-unit`, part 1 landed #233), PLAN-V2-39 (`machine-harness-verification`, staged) and PLAN-V2-43 (`machine-settings-and-axes`, staged): all three declare `ProfileValidator.java` in their Expected Surface.
- Sequenced 2026-10-29 by the cross-check: `ProfileValidator` is shared ground; run after PLAN-V2-39 and PLAN-V2-68 (the group PLAN-V2-68, PLAN-V2-71, PLAN-V2-52, PLAN-V2-70 in that order).
- Overlaps with: `ProfileValidator.java` and `ProfileValidatorTest.java` with PLAN-V2-43, 63, 114 and 135 (staged; the later one rebases); no running plan touches them.
- Adjacent to: PLAN-V2-52 (`client-profile-renderer`) (the renderer's refusal for a profile that lacks a resolution is the fail-closed twin of this check).

## State at the emit

**State at the emit (2026-11-14, `origin/main` `6bd6daac`; re-grounded by the orchestrator), binding over any contradicting older text of this spec:** PLAN-V2-68 shipped (#426): `ProfileValidator.connection` now ends at `:1046-1050` with the margin rule and the constant `IDLE_MARGIN_SECONDS` at `:45`. `skillLoading` is at `ProfileValidator.java:512-523` (the template is read at `:517-518` through `text(..., 256)` and `stringRule(template, ..., true)` at `:519`; nothing requires `{{uri}}`); `stringRule` (`:1235`) walks `PLACEHOLDER` (`:251`, `\{\{([^{}]*)\}\}`) and accepts `uri` among the closed placeholders (`:1252-1254`). The schema block is the file `harness-profile-v1.schema.json` (`skill_loading.load_directive_template` at `:109-112`, description "Template rendered when a skill references another skill (e.g. 'pm_skill(uri="{{uri}}")')", no `pattern`); `01-profile-schema.adoc` no longer carries the text, so Deliverable 2 edits the schema file's description string and the developer guide. `ProfileFixtures.java:78` builds the template `pm_skill(uri="{{uri}}")`. The five shipped profiles are resources of `pm-api` and each carries `{{uri}}` once in `load_directive_template` (read at `pm-mcp-clients` `origin/main` `6fa8c512`: claude, opencode, antigravity, codex, neutral); `ShippedProfilesTest.everyShippedProfileValidates` runs the validator over the jar's copies. The package file `doc/plans/client-profile-rendering.adoc` now lists Tasks 1 to 4 and a Done-When list, with no "What" bullets: this plan leaves it and the index row untouched. Rendering Invariants item 5 stands at `02-placeholders.adoc:107`; the watch file is `doc/implementation-watch/harness-profiles.adoc`. The next free ADR number of `core-profile` is 016 (015 is PLAN-V2-68's). Overlap with the plans running at the emit (PLAN-V2-144 `EndpointVerification`, PLAN-V2-143 `SecretRedactor` and `DiagnosticCollector`, PLAN-V2-123 schema parity tests and `endpoint-profile.schema.json`): none in code; PLAN-V2-123 edits `HarnessProfileSchemaParityTest` only if its outline says so (it does not name the template).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-71-profile-skill-load-template-check.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

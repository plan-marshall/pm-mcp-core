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

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r2`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R17, R18, R19. Package bookkeeping (R01, R05): this plan is 2 of 3 plans of the package `client-profile-rendering` (`doc/plans/client-profile-rendering.adoc`). It deletes from the package file the bullets of _What_ and the _Done When_ paragraphs it delivers and drops its deliverables from the covered sections of the specification and watch items; the plan that delivers the last bullet removes the package's row from `doc/plans/README.adoc` and deletes the package file. The plans of the package edit different sections of the same documents; whichever merges second merges the base branch (the lane's step 7) before the merge gate. This is a small plan; it carries no deliverable of the package file's bullets except the part of _Rendering invariants_ that says profile validation checks completeness on load.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-profile.adoc` (existing, extended); ADR directory: `pm-core/doc/adr/core-profile/` (the next free number — only if the plan takes a decision beyond the rule); layout: workstream WS-10.
- R12 — log message holder: none expected (a refusal is a `ProfileRefusal`, not a log record).

## Objective

The package `client-profile-rendering` says that profile completeness — every canonical placeholder has a resolution in every profile the operator installs — is checked by profile validation on load. The profile schema already requires all seven tool names and the `load_directive_template`; what the validator does not check is that the template contains the `{{uri}}` slot, so a profile whose template is `pm_skill()` is valid and `{{skill_load:<uri>}}` would render a directive that names no skill. Add that one check and its tests.

## Deliverables

1. **The rule in `ProfileValidator`:** `skill_loading.load_directive_template` must contain the slot `{{uri}}` exactly once; a template without it, or with it twice, is refused with the pointer `/skill_loading/load_directive_template`. Tests in `ProfileValidatorTest` (a parameterized case: none, one, two, a `{{uri}}` split by a character) and a case that every shipped profile still validates.
2. **Specification and documentation:** the description of `load_directive_template` in `doc/specification/client-profiles/01-profile-schema.adoc` states the requirement; `pm-core/doc/developer/core-profile.adoc` names the rule among the validator's checks. If the lane finds that the shipped profiles of `pm-api` do not all carry the slot, that is a change in `pm-mcp-clients`, reported as a finding and not worked around.

**Done when:** A profile whose `load_directive_template` lacks `{{uri}}` or carries it twice is refused on load with the pointer named; every shipped profile of the `pm-api` SNAPSHOT validates; the specification and the guide state the rule. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (`pm-core`; where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and the integration tests of every changed module with tests (`verify -Pintegration-tests -pl <module> -am`, only on `pm-core` and `pm-runtime`; here: `pm-core`) are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-CLIENT-2
- Specification: `doc/specification/client-profiles/02-placeholders.adoc` § Rendering Invariants, item 5 (Profile Completeness); `doc/specification/client-profiles/01-profile-schema.adoc` `skill_loading.load_directive_template`
- Watch: `doc/implementation-watch/client-profiles.adoc` PM-WATCH-CLIENT-6

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- The rule changes a validator that three other plans edit (PLAN-46, PLAN-V2-39, PLAN-V2-43): start only after they have landed, and merge the base branch first.

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
- OBSERVED: `doc/specification/client-profiles/01-profile-schema.adoc` — the description of `load_directive_template`
- OBSERVED: `doc/plans/client-profile-rendering.adoc` — the package file (shared with the two other plans of the package)

## Dependencies and Sequencing

- Depends on: PLAN-46 (`machine-onboarding-unit`, running), PLAN-V2-39 (`machine-harness-verification`, staged) and PLAN-V2-43 (`machine-settings-and-axes`, staged): all three declare `ProfileValidator.java` in their Expected Surface.
- Sequenced 2026-10-29 by the cross-check: `ProfileValidator` is shared ground; run after PLAN-V2-39 and PLAN-V2-68 (the group PLAN-V2-68, PLAN-V2-71, PLAN-V2-52, PLAN-V2-70 in that order).
- Overlaps with: `ProfileValidator.java` with PLAN-46, PLAN-V2-39 and PLAN-V2-43 (3 plans, 1 shared entry each; PLAN-V2-39 and PLAN-V2-43 also `ProfileValidatorTest.java`): the reason for the dependency. No other shared entry.
- Adjacent to: PLAN-V2-52 (`client-profile-renderer`) (the renderer's refusal for a profile that lacks a resolution is the fail-closed twin of this check).

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

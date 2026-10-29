# PLAN-133: ClientProfileRenderer: placeholder rendering of templates by the profile, with a per-variant digest

epic: pm-mcp-core-m2-m4
workstream: WS-05

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-133-client-profile-renderer.md` and is queued as one row file, `queue/PLAN-133.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r2`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R17, R18, R19. Package bookkeeping (R01, R05): this plan is 1 of 3 plans of the package `client-profile-rendering` (`doc/plans/client-profile-rendering.adoc`). It deletes from the package file the bullets of _What_ and the _Done When_ paragraphs it delivers and drops its deliverables from the covered sections of the specification and watch items; the plan that delivers the last bullet removes the package's row from `doc/plans/README.adoc` and deletes the package file. The plans of the package edit different sections of the same documents; whichever merges second merges the base branch (the lane's step 7) before the merge gate.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-profile.adoc` (existing, extended); ADR directory: `pm-core/doc/adr/core-profile/` (the next free number of the directory); layout: workstream WS-10.
- R12 — log message holder: `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`): the template-defect record is an ERROR at the next free identifier (200 and the following are taken at `a8da4104`: the next is 201).

## Objective

Implement the renderer of the work package `client-profile-rendering` (Milestone 14): `ClientProfileRenderer` in package `core.profile`, which renders the canonical placeholder notation of vocabulary skill templates, worker launch prompts and model-facing guidance strings with the profile of a harness — the seven tool placeholders and `{{skill_load:<uri>}}` — under the rendering invariants of Client Profiles § Rendering Invariants: zero unresolved placeholders (fail closed, the template defect logged), vocabulary-only translation, a SHA-256 digest of the rendered bytes per variant, the technique precondition marker, and profile completeness proven by a test over every shipped profile. The renderer is a pure function of (template, profile, project facts); it takes no model and no client id beyond the profile, which is the calibration decision (keyed by the model of the role artifact, never by `client_id`).

## Deliverables

1. **The canonical placeholder table and the renderer.** A closed table of the seven tool placeholders (`{{tool:read_file}}`, `write_file`, `edit_file`, `search_code`, `find_files`, `run_shell`, `ask_user`) resolved from the profile's `tools` members and `{{skill_load:<uri>}}` resolved from `skill_loading.load_directive_template` with the profile's `{{uri}}` slot replaced by `<uri>`; `ClientProfileRenderer` renders a template for a `LoadedProfile` (its `document()` is the validated profile) and returns a value, not an exception: rendered bytes with their digest, or a refusal naming the template and the token. Tests: the whole table for each of the four profiles of the specification (`claude`, `opencode`, `antigravity`, `codex`) against the normative resolution table of Client Profiles § Normative Placeholder Syntax, read from the shipped profile documents.
2. **Zero unresolved placeholders.** A `{{…}}` token that is not in the table, or whose profile member is absent, makes the render a refusal: the template is named in a template-defect log record (ERROR, `CoreLogMessages`, documented in `doc/LogMessages.adoc`) and no raw `{{…}}` reaches the output. Tests: an unknown token, a profile document missing `tools.read_file` (what a profile the operator installs after the build could not have passed on load), an unclosed `{{`, a nested token, and the log assertion with `cui-test-juli-logger`.
3. **Vocabulary-only translation and `ask_user`.** Two renders of one template under two profiles differ only inside the resolved placeholders (a test strips the resolved spans and compares the rest byte for byte, over templates containing every placeholder, adjacent placeholders and a placeholder at the start and the end); the renderer varies no emphasis, scaffolding, structure or format. `{{tool:ask_user}}` renders the harness's own question tool name where a skill tells a worker not to use it; that the tool is in no worker's tool set is `tool_not_permitted` of role validation (`RoleValidationReason`), already implemented and only asserted here.
4. **The per-variant digest and the technique precondition.** The digest is the SHA-256 of the rendered bytes: stable over two renders of unchanged bytes, changed when a resolved value changes, unchanged by anything that leaves the rendered bytes unchanged (the renderer has no configuration or version input that is not rendered). A section whose technique precondition is unmet in the project renders the marker "available technique, not adopted in this project" in place of binding wording, and the precondition result is an input of the variant, hence of the digest. The notation that marks a guarded section in a template is not in the specification: ask the operator at refine (R07) and write the decided notation into Client Profiles § Placeholder Rendering with an ADR.
5. **Profile completeness over every shipped profile, and documentation.** A test renders a set of fixture templates (written for the test, never copied from the product: every placeholder, with and without the guarded section) for every profile `ShippedProfiles.read()` returns and fails on any unresolved or missing form. Extend `pm-core/doc/developer/core-profile.adoc`; ADRs: the renderer as a pure function without a model input (the calibration decision), the refusal value instead of an exception, the guarded-section notation; delete from `doc/specification/client-profiles/02-placeholders.adoc` the implemented rules and from `doc/implementation-watch/client-profiles.adoc` PM-WATCH-CLIENT-1, -2, -6 where covered. The test over the product's own templates, the serving of rendered bytes with `_meta["de.planmarshall.mcp/sha256"]`, and the wiring of the renderer into the launch prompt are `plan-marshall-mcp` (`worker-surface-tools`, `job-runtime-wiring`): name them in the pull request body as owed.

**Done when:** Every canonical placeholder and `{{skill_load:<uri>}}` resolves in each shipped profile (a test over `ShippedProfiles.read()`); a template with a token no profile resolves is a refusal with the template named in a template-defect log line and no raw `{{…}}` in any output; two renders of one template under two profiles differ only inside the resolved placeholders; the digest is stable over two renders of unchanged bytes and changes when a resolved value changes; an optional technique with an unmet precondition renders the marker and the precondition enters the digest; the developer guide, the ADRs and the log record are written; the owed follow-ups in `plan-marshall-mcp` are listed in the pull request body. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (`pm-core`; where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and the integration tests of every changed module with tests (`verify -Pintegration-tests -pl <module> -am`, only on `pm-core` and `pm-runtime`; here: `pm-core`) are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-CLIENT-2, PM-SKILL-2
- Specification: `doc/specification/client-profiles/02-placeholders.adoc` § On-the-Fly Placeholder Rendering Rules, § Rendering Invariants, § Calibration Decision; `doc/specification/client-profiles/01-profile-schema.adoc` `tools` and `skill_loading`, § Worker Block; `doc/specification/model-work/01-roles-and-configuration.adoc` § Role Validation
- Watch: `doc/implementation-watch/client-profiles.adoc` PM-WATCH-CLIENT-1, -2, -6; `doc/implementation-watch/domain-extensions.adoc` PM-WATCH-EXT-23

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **Log messages:** one ERROR record for the template defect in `CoreLogMessages` at the next free identifier (see R12); its test is `CoreLogMessagesTest`.
- **Fixtures, not product content:** the fixture templates are written for the test (`CLAUDE.md`: nothing from `skills/` or `workflows/` of the product is copied).
- Never throw generic `Exception`/`RuntimeException` in production code (`CLAUDE.md`): the refusal is a value.

## Claim Labels

- OBSERVED: the package file and its index row (`open`, depends on nothing) — `doc/plans/client-profile-rendering.adoc` (What, Trace, Done When) and `doc/plans/README.adoc` at `origin/main` `a8da4104`
- OBSERVED: `ClientProfileRenderer` does not exist: `grep -rn 'ClientProfileRenderer' pm-*/src` finds no class (the name appears in specification and watch text only); package `core.profile` holds `CanonicalJson`, `EndpointProfile`, `EnrolledHarness`, `JsonDocument`, `JsonNode`, `JsonPointer`, `LoadedProfile`, `ProfileLoader`, `ProfileRefusal`, `ProfileRules`, `ProfileSet`, `ProfileSetKind`, `ProfileSets`, `ProfileStages`, `ProfileValidator`, `ShippedProfiles`, `SupportedVersions`, `VerificationRecord`, `WorkerParameters`, `package-info` — listed with `ls` at `a8da4104`
- OBSERVED: the profile schema already carries the resolution sources: `doc/specification/client-profiles/01-profile-schema.adoc` line 89 `"tools"` with the seven required members `read_file` … `ask_user` and `idioms`, line 124 `"skill_loading"` with `load_directive_template` (example `pm_skill(uri="{{uri}}")`); the shipped `claude.json` of `pm-mcp-clients/pm-api/src/main/resources/profiles/` holds `tools.read_file = "Read"` … `ask_user = "AskUserQuestion"` and the template `pm_skill(uri="{{uri}}")` (the five shipped profiles are `antigravity`, `claude`, `codex`, `neutral`, `opencode`)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/profile/ProfileValidator.java` lines 597-603 (`skillLoading`) validates the template as a model-readable string and lines 1332-1336 accept only closed placeholders (the slots, `session_id`, `uri`) in profile strings; `ShippedProfiles.java` line 36 `ShippedProfiles`, lines 51 and 71 `read(ClassLoader)` / `read()` return the shipped `ProfileSets`; `ShippedProfilesTest` exists
- OBSERVED: `ask_user` is withheld from workers by role validation: `doc/Traceability.adoc` § PM-CLIENT-2 (line 1349) lists "the harness's ask-user tool that no role may name" among the implemented facts and `RoleValidationReason` exists in `pm-core/src/main/java/de/planmarshall/core/role/`
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/launch/PromptComposer.java` appends skill texts from `request.skills().resolve(uri)` without any placeholder rendering (lines 20-110): the wiring point is the provider of the skill text, which the assembly implements
- OBSERVED: `CoreLogMessages` holds INFO 1, WARN 100-102 and ERROR 200 at `a8da4104` (`pm-core/src/main/java/de/planmarshall/core/log/CoreLogMessages.java`, identifiers read at lines 40, 58, 69, 83, 98)
- HYPOTHESIS: the `tools.idioms` map (`rewrite`/`preserve`/`omit` dispositions) has no placeholder form in the normative table; the renderer does not apply it and Rendering Invariant 4 names "tool idioms" as a placeholder dimension — confirm at the specification whether a form is missing (a specification question for the operator, not a choice for the lane; verify-at-outline)
- HYPOTHESIS: the guarded-section notation and the origin of the technique precondition facts (the descriptor's `technique_preconditions`, `domain-extensions-and-architecture`) are not specified; the renderer takes the evaluated precondition results as a map input and the notation is decided at refine (R07)
- Verify-first clause: before editing, list the open pull requests touching `core/profile/` (PLAN-46, 108 and 109 are staged or running there), re-read `02-placeholders.adoc` at the head, and read the shipped profile documents of the `pm-api` SNAPSHOT the build resolves; a refutation loops back to re-scope.

## Expected Surface

- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/profile/` new classes the text implies: `ClientProfileRenderer`, the placeholder table, the render outcome (rendered with digest, or refusal) (exact names at the outline)
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/profile/` new test classes: `ClientProfileRendererTest`, `RenderingCompletenessTest`, and fixture templates under `pm-core/src/test/resources/` (new directory)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/log/CoreLogMessages.java` and `pm-core/src/test/java/de/planmarshall/core/log/CoreLogMessagesTest.java` — the ERROR record (holder: R12, not declared surface)
- OBSERVED: `doc/plans/client-profile-rendering.adoc` — the package file, edited (shared with PLAN-145 (`profile-skill-load-template-check`) and PLAN-141 (`host-resident-surface-inventory`); the last to merge deletes it)
- OBSERVED: `doc/specification/client-profiles/02-placeholders.adoc` — specification part; the rules and invariants deleted, the guarded-section notation added
- OBSERVED: `doc/implementation-watch/client-profiles.adoc` — PM-WATCH-CLIENT-1, -2, -6

## Dependencies and Sequencing

- Depends on: nothing (the package depends on nothing in the index).
- Sequenced 2026-10-29 by the cross-check: it shares `ProfileValidator` and `ProfileStages` with PLAN-108, 112 and 145; run after PLAN-108 (the group PLAN-112, 145, 133, 141 in that order).
- Overlaps with: PLAN-46, 105, 108 and 109: none — the renderer adds classes beside, and does not edit, `ProfileValidator.java`, `VerificationRecord.java` (PLAN-108) or `ProfileSets.java`. `CoreLogMessages.java` is the shared log holder (R12, not a declared surface). Documents: the package file is shared with the two other plans of the package (different bullets); `02-placeholders.adoc` is shared with PLAN-141 (`host-resident-surface-inventory`) (different section: the inventory).
- Adjacent to: PLAN-145 (`profile-skill-load-template-check`) (validator side of profile completeness); `domain-extensions-and-architecture` plans (the technique preconditions); `plan-marshall-mcp` `worker-surface-tools` (serves the rendered bytes and the digest `_meta`).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-133-client-profile-renderer.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

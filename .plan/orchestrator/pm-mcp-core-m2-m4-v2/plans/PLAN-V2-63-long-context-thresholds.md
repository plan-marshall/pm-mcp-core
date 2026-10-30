# PLAN-V2-63: Admit and validate `headless.long_context_thresholds` and feed the envelope budget cap

epic: pm-mcp-core-m2-m4-v2
workstream: WS-04
origin: PLAN-152 of the epic pm-mcp-core-m2-m4
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-63-long-context-thresholds.md` and is queued as one row file, `queue/PLAN-V2-63.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r4`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R13, R15, R17, R18, R19, R20. (R10 because several tests read specification parts; this plan edits `client-profiles/01-profile-schema.adoc` and must check which blocks a test reads before it condenses; R12 is dropped, no message is added; R14 and R16 are dropped, no `core.store` and no `pm-runtime` class changes; R15 applies because the plan adds one test to `pm-workflow`; R17 because the plan runs the links check.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-profile.adoc` (extended) and `pm-workflow/doc/developer/workflow-envelope.adoc` (created by PLAN-V2-40 (`envelope-packing`), extended with one section); ADR directory: `pm-core/doc/adr/core-profile/` (the next free number; the last is 012 at `a8da4104`); layout: workstream WS-05.
- R12 — log message holder: none.

## Objective

The profile schema declares `headless.long_context_thresholds` — per model id of the harness, the input tokens above which its provider bills a request at the long-context price — and the specification caps the fallbacks of the envelope budget at it (Client Profiles § Model Tiers & Models of a Harness, the bullet _Long-context price tier_; Model Work § Cost Sizing, Envelope Budget, item 4). `ProfileValidator` does not know the member: `headless` is a closed object whose optional members are `model_efforts` and `context_budget_tokens` only, so a profile that carries the member is refused with "the member 'long_context_thresholds' is not declared by 'headless'". The epic's open defect (specification content of #174 "has no staged owner") and `PLAN-61`'s open question name it as without an owner. This plan admits and validates the member, reads it into a small value `LongContextThresholds` and proves with one test that the cap of `EnvelopeBudget` (delivered by `envelope-packing`) works on a threshold read from a validated profile document. It then closes the work package PLAN-V2-40 (`envelope-packing`): it removes its index row and deletes its package file.

The plan changes `ProfileValidator`, which `machine-harness-verification`, `profile-wait-margin` and `profile-skill-load-template-check` also change; it runs after them. The envelope code itself is PLAN-V2-40 (`envelope-packing`), which has no such dependency and runs free.

## Deliverables

1. **The member in `ProfileValidator` (`headless`).** Add `long_context_thresholds` to the optional members of `headless` (`HEADLESS_OPTIONAL`) and validate it where the optional `context_budget_tokens` is validated: an object (an empty object is valid) whose every member name is at most 128 characters (the schema's `propertyNames.maxLength`) and whose every value is an integer of at least 10 000 and at most `Integer.MAX_VALUE` (the schema's `minimum: 10000`; the maximum as for `context_budget_tokens`). The refusal pointer is the member's: `/headless/long_context_thresholds/<model id>` for a bad value or name, `/headless/long_context_thresholds` for a non-object. The endpoint profile kind stays closed against the member (its own member sets do not declare it). Tests in `ProfileValidatorTest` (JUnit 5, `@DisplayName`, AAA): the member absent, empty, with one entry and with several entries is accepted (add it to the group "the optional blocks and every alternative form load"); a value of 9 999, a non-integer, a negative value, a name of 129 characters, and a non-object each refuse with the pointer above; a name of exactly 128 characters and a value of exactly 10 000 are accepted; an endpoint profile that carries the member is refused as an undeclared member.
2. **`LongContextThresholds`, the reading of the member.** A new value class in `pm-core` package `core.role`, beside `HarnessProfile`: `LongContextThresholds.of(JsonNode document)` reads `headless.long_context_thresholds` of a validated profile document (absent, `null` headless block and the endpoint kind give the empty value), `of(LoadedProfile)` delegates to it, and `forModel(String modelId)` returns an `OptionalInt`. It does not add a component to the record `HarnessProfile`: five test classes and the production wiring construct that record (`EffectiveRoleFactsTest`, `RoleFixtures`, `RoleValidationTableTest`, `QualificationFixtures`, `ModelFixtures`), and the member is read by the envelope computation alone. Tests (`LongContextThresholdsTest`): a model with an entry, a model without one, the absent member, the endpoint profile, the exact model id match (no prefix or case folding, as identifiers are compared byte-exactly, `identifiers/01-general-rules-and-registry.adoc` general rule 2).
3. **The cap on a validated document (cross-module proof).** One new test class in `pm-workflow`, `EnvelopeBudgetProfileThresholdTest` (package `de.planmarshall.workflow.envelope`): parse a minimal profile document that carries `headless.context_budget_tokens` 400 000 and `headless.long_context_thresholds` for one model (`JsonDocument.parse`), read `LongContextThresholds.of(document).forModel(model)`, and resolve `EnvelopeBudget` with it: the profile fallback is capped at the threshold; a role-declared budget above it is kept and answers `aboveThreshold`; a model with no entry is not capped. The class edits no production class of `pm-workflow`. If `EnvelopeBudget` in the head of `main` differs from the signature PLAN-V2-40 (`envelope-packing`) specifies (plain `OptionalInt` inputs), adapt the test to it and say so in the pull request body.
4. **Documentation, the specification and the close of the package (R05, R06).** Extend `pm-core/doc/developer/core-profile.adoc` (the member, its rule, `LongContextThresholds`) and `pm-workflow/doc/developer/workflow-envelope.adoc` (one section: where the threshold comes from); one ADR in `pm-core/doc/adr/core-profile/` (next free number) for the decision that the member is read by a value class beside `HarnessProfile` and not as a component of the record, with the alternative and the consequence. Reduce `doc/specification/client-profiles/01-profile-schema.adoc` as R05 (r3) says, **condense and point**: the description of the member at lines 418 to 424 of the schema block stays byte-stable (R10: look for a test that reads the block before touching it; `ShippedProfilesTest` and `ProfileValidatorTest` build their documents themselves, but check), the bullet _Long-context price tier_ (line 791) becomes "Implemented by `ProfileValidator` (the member) and `LongContextThresholds`; the cap by `EnvelopeBudget` … Remaining: the shipped entries of the profiles (`plan-marshall-mcp`) and the role view", with the anchor `#long-context-thresholds` kept (the Model Work item 4 links it). `doc/Traceability.adoc`: add the classes to the sections of `PM-CLIENT-5` and `PM-WF-14`. **Close the package PLAN-V2-40 (`envelope-packing`):** delete `doc/plans/envelope-packing.adoc`, remove its row from `doc/plans/README.adoc` and drop it from the _Depends on_ column of every row that names it (R01).

**Done when:** a profile that carries `headless.long_context_thresholds` with valid entries loads and one with an invalid entry is refused at the pointer of the entry; `LongContextThresholds.forModel` returns the entry of a model and nothing for any other; the cap test of Deliverable 3 passes against the real `EnvelopeBudget`; the existing `ProfileValidatorTest`, `HarnessProfileTest` and `ShippedProfilesTest` cases stay green unchanged; the package file and its row are gone; `verify`, `verify -Ppre-commit` (its rewrites reviewed and committed), `verify -Pcoverage` and `verify -Pintegration-tests -pl pm-core -am` are green. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (`pm-core`, `pm-workflow`; where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and `verify -Pintegration-tests -pl pm-core -am` are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set:** PM-CLIENT-5 (Versioned Client Profile Schema & Override Hierarchy) and PM-WORK-1; Client Profiles § Model Tiers & Models of a Harness (the bullet _Long-context price tier_, `doc/specification/client-profiles/01-profile-schema.adoc` line 791) and the schema block (lines 418 to 424); Model Work § Cost Sizing, Envelope Budget & Minimum Task Work item 4 (`model-work/06-envelopes-questions-deadlines.adoc` line 40). The lead is the epic's open defect on the specification content of #174 and the open question of `PLAN-61`.

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **The plan starts from the head of `main` after `machine-harness-verification`, `profile-wait-margin` and `profile-skill-load-template-check` have merged**, and re-reads `ProfileValidator.headless` and `ProfileValidatorTest` there (those plans add `readable_paths`, the credential rule, the wait margin and the template check to the same two files). `client-profile-renderer` and `host-resident-surface-inventory` (PLAN-V2-52, PLAN-V2-70) share `ProfileValidator`/`ProfileStages` with them: the gate decides the order, one at a time.
- **The shipped profiles are not edited here.** A shipped profile holds an entry "only where the provider documents the price, with the documented value, checked for every release" (the specification); the profiles are content of `plan-marshall-mcp`. List in the pull request body, as **Owed to the content repository**, that no shipped profile carries an entry yet.
- **Do not add a component to `HarnessProfile`** (Deliverable 2): a change of that record ripples into five test classes and is a shared-ground change.

## Claim Labels

- OBSERVED: the member is refused today — `pm-core/src/main/java/de/planmarshall/core/profile/ProfileValidator.java` line 204 (`HEADLESS_OPTIONAL = Set.of(NAME_MODEL_EFFORTS, NAME_CONTEXT_BUDGET_TOKENS)`), lines 609 to 615 (`headless`: the closed object check with `HEADLESS` plus `HEADLESS_OPTIONAL`) and lines 670 to 673 (the optional `context_budget_tokens`, an integer of at least 10 000); `long_context_thresholds` occurs nowhere in `pm-*/src` (grep)
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/empty-packages-draft | evidence: read at a8da4104; `grep -rn long_context_thresholds pm-*/src` finds nothing
- OBSERVED: the schema and the rule — `doc/specification/client-profiles/01-profile-schema.adoc` lines 418 to 424 (`long_context_thresholds`: object, `propertyNames.maxLength` 128, `additionalProperties` integer `minimum` 10000; described as the cap of the fallback), line 416 (the `context_budget_tokens` description: "this fallback and the compiled one are capped at the model's long_context_thresholds entry"), line 791 (the bullet; "A `context_budget_tokens` the role artifact sets, and the operator's `envelope_budget_tokens`, are taken as set")
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/empty-packages-draft | evidence: read at a8da4104
- OBSERVED: the harness projection reads the budget without a cap — `pm-core/src/main/java/de/planmarshall/core/role/HarnessProfile.java` line 156 (`integer(headless, "context_budget_tokens")`); the record has eleven components and is constructed with `new HarnessProfile(` in `HarnessProfile.java` lines 153 and 174 and in five test classes (`EffectiveRoleFactsTest` line 160, `RoleFixtures` line 89, `RoleValidationTableTest` line 204, `QualificationFixtures` line 280, `pm-workflow/.../model/ModelFixtures.java` line 75)
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/empty-packages-draft | evidence: `grep -rn "new HarnessProfile(" pm-*/src`
- OBSERVED: no owner — `plans/PLAN-61-token-usage.md` (shipped) line 169 "headless.long_context_thresholds (#174, a closed headless member the validator would refuse, and a cap of the envelope-budget fallback in model-work/06) has no owner among the staged packages"; `epic.md` line 537 ("`headless.long_context_thresholds` (#174) which no package file names"); no `doc/plans/*.adoc` names it
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/empty-packages-draft | evidence: grep over the ledger and `doc/plans/`
- OBSERVED: `JsonDocument.parse(byte[], String)` is public (`pm-core/src/main/java/de/planmarshall/core/profile/JsonDocument.java` line 53), so a `pm-workflow` test can build a document without a pm-core test fixture
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/empty-packages-draft | evidence: read at a8da4104
- OBSERVED: the live and staged plans that name the same files — `ProfileValidatorTest.java` in the Expected Surface of PLAN-V2-39, PLAN-V2-68 and PLAN-V2-71; `client-profiles/01-profile-schema.adoc` in that of PLAN-V2-39, PLAN-V2-71, PLAN-V2-41, PLAN-V2-42 and (parked) PLAN-V2-50; `HarnessProfile.java` in none
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/empty-packages-draft | evidence: `awk` over every `## Expected Surface` of `plans/*.md` with the queue status of each
- HYPOTHESIS: no test reads the schema block of `01-profile-schema.adoc` (lines 418 to 424) — `grep -rln "01-profile-schema" pm-*/src/test` found no file at `a8da4104`; re-run the grep at the outline (R10)
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/empty-packages-draft | evidence: the grep found nothing
- Verify-first clause: before editing, confirm that `machine-harness-verification`, `profile-wait-margin`, `profile-skill-load-template-check` and PLAN-V2-40 (`envelope-packing`) have merged (`doc/plans/README.adoc` has the row of PLAN-V2-40 (`envelope-packing`) and no other of them), list the open pull requests for one that touches `ProfileValidator.java`, and re-read lines 604 to 675 at the head; a refutation loops back to re-scope.
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/empty-packages-draft | evidence: PLAN-V2-39, PLAN-V2-68, PLAN-V2-71 are staged and declare the validator

## Expected Surface

- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/profile/ProfileValidator.java` — `HEADLESS_OPTIONAL` and `headless` (D1); shared with PLAN-V2-39, PLAN-V2-68, PLAN-V2-71 (and PLAN-V2-52, PLAN-V2-70 by their text)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/profile/ProfileValidatorTest.java` — the member's cases (D1); shared with PLAN-V2-39, PLAN-V2-68, PLAN-V2-71
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/role/LongContextThresholds.java` — new (D2)
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/role/LongContextThresholdsTest.java` — new (D2)
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/envelope/EnvelopeBudgetProfileThresholdTest.java` — new (D3)
- OBSERVED: `doc/specification/client-profiles/01-profile-schema.adoc` — specification part; the bullet at line 791 condensed, the schema block kept (D4); shared with PLAN-V2-39, PLAN-V2-71, PLAN-V2-41, PLAN-V2-42, PLAN-V2-50 (other sections)
- OBSERVED: `pm-core/doc/developer/core-profile.adoc` — developer documentation, extended
- OBSERVED: `pm-core/doc/adr/core-profile/` — one ADR, the next free number
- (created by PLAN-V2-40 (`envelope-packing`), extended here) `pm-workflow/doc/developer/workflow-envelope.adoc`
- OBSERVED (at the start of this plan; created by `envelope-packing`): `doc/plans/envelope-packing.adoc` — deleted (D4)

Not surface (R19): `doc/plans/README.adoc`, `doc/Traceability.adoc`, the ADR directory and developer guide above as bookkeeping where they exist.

## Dependencies and Sequencing


- **Landing note (2026-10-29):** PLAN-46 landed (#233): the dependency on PLAN-46 is spent.
- Depends on: PLAN-V2-83 (`skills-conformance-foundation`: the dependencies and rule R20); and then: PLAN-V2-40 (`envelope-packing`) (it creates `EnvelopeBudget`, the package file and its row, and the developer guide this plan extends); PLAN-V2-39 (`machine-harness-verification`, staged): it declares `ProfileValidator.java`, `ProfileValidatorTest.java` and `client-profiles/01-profile-schema.adoc` (3 shared entries), and PLAN-V2-39 itself waits for PLAN-46 (landed #233).
- Sequenced: `ProfileValidator` is shared ground; the group PLAN-V2-68, PLAN-V2-71, PLAN-V2-52, PLAN-V2-70 runs after PLAN-V2-39 one at a time, and this plan joins that group after PLAN-V2-68 and PLAN-V2-71 (the two that edit `ProfileValidatorTest` as well); the gate decides against PLAN-V2-52 and PLAN-V2-70, with which it shares no class by its own surface.
- Overlaps with the live plans (class level): PLAN-46 and PLAN-105 none (PLAN-46 names `ProfileValidator` in its text, not in its surface); PLAN-V2-39: the 3 entries above; PLAN-V2-43: none.
- Overlaps with the other plan of this cut: PLAN-V2-40 (`envelope-packing`) — the package file, its row and the guide (bookkeeping; hence the dependency). Disjoint in classes.
- Adjacent to: the shipped profiles and the role view in `plan-marshall-mcp`/the assembly (untouched), `EffectiveRole.contextBudget` (unchanged: the worker's budget is not capped, only the envelope fallbacks).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-63-long-context-thresholds.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

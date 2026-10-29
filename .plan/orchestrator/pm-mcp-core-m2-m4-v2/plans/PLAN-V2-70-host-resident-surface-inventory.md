# PLAN-V2-70: Host-resident surface inventory: the calculator, the derived bounds and the test that enforces them

epic: pm-mcp-core-m2-m4-v2
workstream: WS-04
origin: PLAN-141 of the epic pm-mcp-core-m2-m4

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-70-host-resident-surface-inventory.md` and is queued as one row file, `queue/PLAN-V2-70.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r2`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R17, R18, R19. Package bookkeeping (R01, R05): this plan is 3 of 3 plans of the package `client-profile-rendering` (`doc/plans/client-profile-rendering.adoc`). It deletes from the package file the bullets of _What_ and the _Done When_ paragraphs it delivers and drops its deliverables from the covered sections of the specification and watch items; the plan that delivers the last bullet removes the package's row from `doc/plans/README.adoc` and deletes the package file. The plans of the package edit different sections of the same documents; whichever merges second merges the base branch (the lane's step 7) before the merge gate. In-repo share only: see the objective.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-profile.adoc` (existing, extended); ADR directory: `pm-core/doc/adr/core-profile/` (the next free number); layout: workstream WS-10.
- R12 — log message holder: none expected.

## Objective

Deliver the in-repository share of the host-resident surface inventory of the work package `client-profile-rendering` (Client Profiles § Host-Resident Surface Inventory): for each built-in profile, the bytes the harness injects into every turn of a worker — the tool schemas and descriptions of the role's tool set when `schema_residency` is `resident`, the shim skill's description where the bundle installed it, and the `pm_skills` catalogue entries once loaded — per component and in total with the population measured, the length and shape bounds derived from the measured distribution with their derivation recorded, and a build-time test that enforces them. The inputs that name the served set (the tool schemas of the MCP server and the skill descriptions after domain filtering) are produced in `plan-marshall-mcp` and `plan-marshall`; this plan delivers the calculation and the enforcement over a measured set handed to it, and a fixture-based run, not the published figures.

## Deliverables

1. **The calculator** (new, `core.profile`): from a profile and a measured component set (name, kind, UTF-8 byte length of the description or schema) it returns the resident surface per component and in total, the population it measured (count, and the criteria: the served set for the profile after domain filtering, not the files in a binary), and applies residency by the actual injection mechanism — `schema_residency: resident` counts the tool schemas, `on_demand` does not; the shim skill's description counts only where the profile's `skills_extension` is `false` (the bundle installs it); the launch prompt and delivered skills are not resident. Tests per shipped profile with a fixture component set written for the test.
2. **Derived bounds and their derivation:** a length bound and a shape bound on a description are computed from the measured distribution (count, mean, median, maximum, the share of the bytes held by the largest components, in the form the watch item records) and the result records how the bound came from the distribution; a build-time test fails when a description exceeds its bound, and a bound without a recorded derivation is refused.
3. **Named runtimes and models:** a description shape rule carries the runtimes and models it was reasoned about (a value in the record, not prose), and the rule states that every runtime reads the descriptions byte-identically; a rule without them is refused. Documentation: extend `core-profile.adoc`, an ADR for the calculator's inputs and the bound derivation, and delete the implemented subsection from `doc/specification/client-profiles/02-placeholders.adoc` and PM-WATCH-CLIENT-3 where covered. Name as owed to `plan-marshall-mcp`/`plan-marshall` the measured population and the published inventory (the PM-MIG-5 baseline).

**Done when:** The inventory is derived per shipped profile from a fixture set with its per-component and total bytes matching the fixture's rendered surface and with the population it measured; each length or shape bound records how it came from the distribution and the test fails on a description over it; a shape rule names the runtimes and models it was reasoned about; the published figures and their population are listed as owed in the pull request body. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (`pm-core`; where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and the integration tests of every changed module with tests (`verify -Pintegration-tests -pl <module> -am`, only on `pm-core` and `pm-runtime`; here: `pm-core`) are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-SKILL-2, PM-MIG-5
- Specification: `doc/specification/client-profiles/02-placeholders.adoc` § Host-Resident Surface Inventory; `doc/specification/client-profiles/01-profile-schema.adoc` `skill_loading.schema_residency`, `skills_extension`
- Watch: `doc/implementation-watch/client-profiles.adoc` PM-WATCH-CLIENT-3

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **In-repo share only.** The tool schemas are registered by `pm-mcp-server` and the skill descriptions are model-facing content; neither is in this repository (`CLAUDE.md`: no module carries model-facing content). The plan's fixtures are written for the test.

## Claim Labels

- OBSERVED: the package file and its index row (`open`, depends on nothing) — `doc/plans/client-profile-rendering.adoc` (What, Trace, Done When) and `doc/plans/README.adoc` at `origin/main` `a8da4104`
- OBSERVED: `ClientProfileRenderer` does not exist: `grep -rn 'ClientProfileRenderer' pm-*/src` finds no class (the name appears in specification and watch text only); package `core.profile` holds `CanonicalJson`, `EndpointProfile`, `EnrolledHarness`, `JsonDocument`, `JsonNode`, `JsonPointer`, `LoadedProfile`, `ProfileLoader`, `ProfileRefusal`, `ProfileRules`, `ProfileSet`, `ProfileSetKind`, `ProfileSets`, `ProfileStages`, `ProfileValidator`, `ShippedProfiles`, `SupportedVersions`, `VerificationRecord`, `WorkerParameters`, `package-info` — listed with `ls` at `a8da4104`
- OBSERVED: `doc/specification/client-profiles/02-placeholders.adoc` § Host-Resident Surface Inventory (from line 116 of the file) requires "the build derives and publishes" the inventory; `doc/implementation-watch/client-profiles.adoc` line 141 PM-WATCH-CLIENT-3 records the baseline fixture (157 components, 27,354 bytes, mean 174, median 119)
- OBSERVED: the profile schema has `skill_loading.schema_residency` (`resident`/`on_demand`) and `skills_extension` (true once the host's MCP Skills Extension support is verified; the bundle of such a host installs no shim skill) — `01-profile-schema.adoc` lines 124-138
- HYPOTHESIS: the phrase "from the profile's own fixture" (Done When of the package) means a per-profile fixture of the served set; no such fixture exists in the repository (`grep -rn 'resident' pm-core/src/test` finds none) — the form of the fixture is decided at the outline, and if it needs the product's skill descriptions the production half is `plan-marshall-mcp`'s (verify-at-outline)
- HYPOTHESIS: the plan is mostly the other repositories' (the measured population and the published inventory): the in-repo share is the calculator and the enforcement; if the lane finds nothing it can verify without the product's content, it reports that and ends with the calculator and its tests only
- Verify-first clause: before editing, read the roadmap item of Milestone 14 for what is already covered, list the open pull requests touching `core/profile/`, and check whether `plan-marshall-mcp` `worker-surface-tools` already defines the served-set fixture; a refutation loops back to re-scope.

## Expected Surface

- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/profile/` new classes: the inventory calculator, the derived-bound record (exact names at the outline), and their tests under `pm-core/src/test/java/de/planmarshall/core/profile/`
- OBSERVED: `doc/plans/client-profile-rendering.adoc` — the package file (the last plan of the package to merge deletes it and removes the row)
- OBSERVED: `doc/specification/client-profiles/02-placeholders.adoc` — § Host-Resident Surface Inventory
- OBSERVED: `doc/implementation-watch/client-profiles.adoc` — PM-WATCH-CLIENT-3

## Dependencies and Sequencing

- Depends on: nothing.
- Sequenced 2026-10-29 by the cross-check: it shares `ProfileValidator` and `ProfileStages` with PLAN-V2-39, PLAN-V2-68 and PLAN-V2-71; run after PLAN-V2-39 (the group PLAN-V2-68, PLAN-V2-71, PLAN-V2-52, PLAN-V2-70 in that order).
- Overlaps with: disjoint in code from PLAN-V2-52 (`client-profile-renderer`) and PLAN-V2-71 (`profile-skill-load-template-check`) and from PLAN-46, PLAN-105, PLAN-V2-39, PLAN-V2-43 (new classes only). `02-placeholders.adoc` and the package file are shared with PLAN-V2-52 (`client-profile-renderer`), in different sections.
- Adjacent to: `plan-marshall-mcp` `worker-surface-tools` (the served set); `plan-marshall` (the PM-MIG-5 baseline measurement).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-70-host-resident-surface-inventory.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

# Landing Analysis: PLAN-V2-153 — The machine onboarding's remainder

epic: pm-mcp-core-m2-m4-v2
workstream: WS-04
pr: #470 (merge `1e9466ac`, 2026-11-17)

> Landing record for one shipped plan, **delivered, with one schema drift the lane did not see**. Written by `analyze` (inbox scan) from
> the lane's landing message `rb-v2-153-machine-onboarding-remainder-001.md` (`deliverables_done` 3 of 5). The reading finds D2 and D3
> delivered in behaviour (the lane's "not met" is a missing-evidence clause, not a missing behaviour), D4 met as an algorithm and a
> validator rather than as written, and a status-schema enum that now disagrees with the code. A claim of the message is a lead; the
> schema line, the dangling references, the unused enum constants, the dead check and the aspect documentation were re-read.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 where each remaining deliverable lives; the two spec corrections | met | `workflow-dsl/01-grammar.adoc:319` reads `enum[first_run\|auto\|reconfigure]`; `workflow-dsl/09` item 1 lost the contradictory sentence; the table is in the PR body (unreadable after the squash) |
| D2 the aspects' engine half | **delivered; the lane's "not met" is an evidence gap** | `core.config.OnboardingAspect`, `workflow.onboarding.*` (router, `AutoOnboardingResult`, `OnboardRun`) are new files; their tests cannot compile at `27378186`, so "a test that fails at the base" holds by construction. The lane could not build a base tree (`worktree-create --detach` answered `executor_in_worktree: absent`) |
| D3 the settings states | **delivered; same evidence gap** | `MachineSettingsStatesTest.verifyProvidersFirstChangeWins` (second call throws `SettingsConflict`, value and revision asserted, nothing written), `withdrawalConflict`, `MachineSettingsWritersTest.planDefaultsFirstChangeWins`; the lane proved the two-changes case by mutation (`mutation-after`) |
| D4 the axes in the composition and the landing check's contract | met as "algorithm plus validator", not as written | `LandingStepComposition.merge`, `PlanAxisLevels`, `MachineConfigurationStore.writePlanDefaults` and the `Project` unknown answers are delivered; a composed file per variant and anything touching the finalize step list are narrowed to the assembly by ADR 023; no code turns `PlanDefaults` or the project documents into the maps `PlanAxisLevels` validates |
| D5 the package closes | met, with stale forward references | package file and index row deleted, `Depends on` cells cleaned, five package files re-pointed; 13 stale hits of the closed package's name remain (below) |

Count: the message says 3 of 5 by strict evidence; by behaviour D1, D2, D3 and D5 are met and D4 is arguable. The queue row is stamped
shipped; the strict count is the lane's own and is recorded here.

## Three-Layer Analysis

One read-only leaf (`execution-context-level-3`, `orchestrator.analyze`) read the merged diff (`1e9466ac^1..1e9466ac`, 43 files,
+3561/-120) against the spec; the leaf could not read the PR body or the two correction commits (the merge is squashed). The orchestrator
re-read the status schema, the dangling name, the aspect documentation, the unused constants and the dead check. By reading, no build, no
test run, no write.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity |
|---------|-------|---------|----------|
| L1-1 | D2 and D3 not met as written because the at-base failure is unevidenced | an evidence gap only; the behaviour and its tests exist | the lane is over-strict |
| L1-2 | D4 met | met as "algorithm plus validator"; the composed file per variant and the step list are the assembly's by ADR 023 | the lane is generous |
| L1-3 | Expected Surface: `PlanFacts` needs a reader | refuted: the reader is the new class `PlanAxisLevels`; `PlanFacts.java` is not touched; `state-catalogue/14` and `19` untouched | the lane's "partly confirmed" is fair |
| L1-4 | files the diff touches that the Expected Surface does not name | `OnboardingAspect`, `PlanDefaults`, `MachineSettingsStates`, the runtime-config ADRs 005 and 006 and guide, the whole `workflow.onboarding` package with its guide and ADR 001, workflow-plan ADRs 022 and 023, five package files | minor, surface drift (R19) |

### Layer 2 — Implemented

| Point | Reading | Verdict |
|-------|---------|---------|
| Duplicate states refused | `LandingStepComposition.java:151-163`; `LandingStepCompositionTest.refusesDuplicateStates` covers a duplicate state, a variant supplied twice and an unbound state | confirmed |
| Gate rewrite | the PR touches `LandingVariantValidationTest` (+24 lines, `unknownAnswersAreRefused`) and nothing outside the plan's files; the one incidental edit, `MachineConfigurationStoreTest.landing` from `freeText()` to `"landing.main"`, is forced by the stricter codec | clean |
| `compose(request)` preview form "stated in Javadoc, Traceability, ADR 023" | stated in `PlanInstanceComposer.java:140-147` and `workflow-plan.adoc:245`; not in Traceability and not in ADR 023 (which covers the merge only); "no production caller" is vacuous: no `src/main` class calls either overload | the lane is partly wrong, minor |
| Owed work named | the units are in `workflow-onboarding.adoc`; the settings audit, `DerivedTokenCache` and hold/retry are in `runtime-config/006`; ADR 023 and `workflow-plan.adoc` say "named owed in the plan's table", the PR body, which is not durable | minor, doc statement |
| Aspect documentation | `OnboardingAspect` is documented in the two `workflow-onboarding` files only; **`pm-core/doc/developer/core-config.adoc` does not mention it** (grep over `*.adoc`, re-read) although the repository requires the developer doc of each changed package | doc defect |
| `OnboardingContractReplayTest` | replays no fixture: `scenarios()` is five hand-coded cases; the word "replays" and the guide's "onboarding-contract scenarios" overstate | minor |
| Traceability PM-WF-25 | calls `PlanAxisLevels` "the reader of the three axis levels"; the class itself says no store-to-map reader exists (`PlanAxisLevels.java:22-23`) | minor, overclaim |

### Layer 3 — Correct

| Point | Reading | Verdict |
|-------|---------|---------|
| **`onboard_run` enum drift** | `status.schema.json:87` (pm-core) is `["first_run","reconfigure",null]`; `OnboardRun.AUTO("auto")` (`OnboardRun.java:27`) and the corrected `workflow-dsl/01:319` admit `auto`; a machine `status.json` recording `onboard_run: auto` does not validate against the schema. The lane fixed the same drift in the grammar and not here (re-read) | **major, introduced by this PR, latent until the assembly writes `auto`** |
| Stale package name | 13 hits in 11 files outside `.plan/` name `machine-onboarding-unit`: `core-config.adoc:40`, `core-profile.adoc:27`, `core-role.adoc:32`, `ProfileLoader.java:53`, `RoleEnvironment.java:22`, `LeaseOwnerSchemaParityTest.java:66`, `PlanInstanceHeaderTest.java:579,597,601`, `workflow-plan/015:36` (forward claims, now stale) and ADR Source lines in `core-profile/013`, `core-profile/014`, `runtime-launch/008` (history) | minor, doc statement; the lane claimed only the `Depends on` cells |
| Dead code | `State.WITHDRAW` and `State.REMOVE_CONSENT` are declared (`MachineSettingsStates.java:96,98`) and referenced nowhere (grep, re-read); in `PlanAxisLevels.validate` the check `!key.equals(axis.wireName())` cannot be true because `axisOf` selects by wire name, and the `values == null` branch is unreachable when `level()` calls it only for non-null | minor |
| `plan_defaults` against the schema | `machine-config.schema.json:157-166` matches `PlanDefaults.*_VARIANTS`, `POSTURES`, `KEYS`; `MachineConfigurationSchemaParityTest.planDefaults` pins them. Nothing ties them to `PlanFacts.Axis.variants()` in `pm-workflow` (the modules cannot see each other): drift surfaces only as a runtime refusal | minor, residue |
| `writePlanDefaults` | `transact` under the lock, revision checked, `SettingsConflict` carries the current defaults, a null posture clears it (`MachineConfigurationStore.java:196-221`); `level(level, null)` clears (`PlanAxisLevelsTest.clearingALevel`) | clean |
| `reverify` order | the provider probes run before the revision check, so a stale revision is found after the probes ran, with nothing written | minor |
| `merge` output unverified | `LandingStepComposition.merge` drops the bound variant's non-`finalize.` states without running the verifier; the test fixtures' step states transition to a dropped terminal (`landing-main.done`), so the asserted tree is dangling; the spec says real step states target the phase's hub | minor |
| `Project` type change | only `PlanInstanceComposer.landingViolation` and tests use it; a partial unknown such as `new Project(true, null)` is allowed and tested | clean |
| Libraries with no production caller | `LandingStepComposition.merge`, `PlanAxisLevels`, `holdsLandingSteps`: awaiting the assembly, stated in ADR 023 | info |

## Review and Quality Numbers

| Number | Message | Re-read |
|--------|---------|---------|
| unresolved review threads | 0 | not re-queried here |
| review body findings total / handled | 5 / 5 | not re-verified (progress prose) |
| Sonar open new issues | 0 | not re-queried |

`landing-check`: `complete=false`, missing `total_tokens` and `plugin_version` only (register E27). Surface delta unmeasured. Self-review
stopped at the budget after round 3 with nothing known open (`self-review: budget-exhausted`); this reading is the fourth look and finds
the items above. CodeRabbit did not review the final head (hourly limit); its qualifying review at `f6d0f306` with every finding handled
is the lane's recorded authorization. Sourcery refused by diff size (optional). The lane's own shell-rule breaks: three, each abandoned.

## Residue

1. **Carried into PLAN-V2-114, item 40:** the `onboard_run` enum in `status.schema.json` and its test; the 11 forward references to the
   closed package (the three ADR Source lines may stay); `OnboardingAspect` in `core-config.adoc`; the unused constants and the two dead
   branches; `reverify`'s revision pre-check; `merge`'s dangling-state fixtures or the verifier note; the "named owed in the plan's table"
   sentences as the owed items themselves; the Traceability PM-WF-25 wording and the missing `compose(request)` statement; the "replays"
   wording; a parity test between `PlanDefaults` and `PlanFacts.Axis.variants()` (belongs to the assembly or a module that sees both).
2. **Owed to `plan-marshall-mcp` (named by the lane, unchanged):** `onboarding.pmwf`, the seven aspect sub-workflows, the `auto.*`,
   `settings.*`, `landing.*`, `method.*`, `ask.*` units, the `phase-6-finalize` unit and the wiring of `LandingStepComposition`, the
   `DerivedTokenCache` registrations, the hold/retry after a rotation. No plan of this epic stages them.
3. **Not routed:** the `LspClientTest` timing flake (pre-existing, recorded in the PLAN-V2-43 landing); the PR body's "4 of 5" (the merged
   body is not edited).

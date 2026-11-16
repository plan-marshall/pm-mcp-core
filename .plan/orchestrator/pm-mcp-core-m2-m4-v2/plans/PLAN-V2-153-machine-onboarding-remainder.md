# PLAN-V2-153: The remainder of the machine onboarding unit: aspects, the automatic run, the settings states, the axes in the composition

epic: pm-mcp-core-m2-m4-v2
workstream: WS-04
origin: PLAN-V2-43 (shipped #462, 0 of 5 deliverables fully met; `landings/PLAN-V2-43.md`)

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-153-machine-onboarding-remainder.md` and is queued as one row file, `queue/PLAN-V2-153.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.
> **Staged 2026-11-16 by `analyze`** from the landing of PLAN-V2-43: that plan delivered the engine and store halves this repository could carry (the two settings writers, `overrides.json`, the project-aware landing check) and named the rest owed. The package `machine-onboarding-unit` stays `open` until this plan removes its file and index row (R01). The spec text below is the **remainder of PLAN-V2-43's deliverables**, restated against what the merge `27378186` delivers; the deliverable numbers D2, D7, D8, D10 and D11 are PLAN-V2-43's labels (Tasks 1, 8, 9, 11, 12 of the package file).

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision, and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R15, R17, R18, R19, R20 (the set of PLAN-V2-43, which this plan completes).

Plan parameters (the values the rules ask for):

- R06 — developer documentation: the developer file of each package it changes (`{module}/doc/developer/{package}.adoc`); ADR directory: `{module}/doc/adr/{package}/`; layout: workstream WS-04.
- R12 — log message holder: `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`); `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`); `RuntimeLogMessages` (`de.planmarshall.runtime.log`, prefix `PM_RUNTIME`).

## Objective

Finish the work package `machine-onboarding-unit` in this repository: what PLAN-V2-43 could not carry. The onboarding's aspects and the automatic run (D2, D7) as far as the engine, the stores and the runtime of this repository can build them over fixtures, the states of the machine's settings that are still missing (D8), the composition half of the plan axes (D10), the contract of the landing check's call sites (D11), and the package's own bookkeeping (R01). The default units (`onboarding.pmwf`, the aspect sub-workflows, `landing.*`, `method.*`, `ask.*`) are `plan-marshall-mcp` resources, which this repository does not carry: the plan builds the engine half and names the units owed.

## Deliverables

1. **Where each remaining deliverable lives (the outline's first act).** The landing of PLAN-V2-43 found that D2 and D7 have no home in `pm-workflow` as written: no proposal vocabulary exists there, and the units are `plan-marshall-mcp` resources. Before any edit, read `doc/plans/machine-onboarding-unit.adoc` § Tasks 1, 8, 9 and the Trace against the code at the head, and record in the pull request body a table over D2, D7, the D8 states, the D10 composition half and the D11 call sites: what this repository can build and test over fixtures, what is the assembly's (named as owed), and what the specification must correct. A row the specification must correct is corrected in this plan's own pull request. The package file's own task text for Tasks 9, 11 and 12 is brought in line with the merge `27378186` (it still reads as if nothing were landed).
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety
   - Persona: implementer
2. **The automatic onboarding and its result (D7), and the aspects' engine half (D2).** As far as deliverable 1's table makes them buildable here: the proposal and result vocabulary of the automatic run (each value it chose with its reason, the items it could not decide), `change` on `auto.result` calling one aspect as a later run (`onboard_run: reconfigure`) and returning to the result, over fixtures replaying the `onboarding-contract` scenarios on the runtime.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java:java-null-safety, pm-dev-java:junit-core
   - Persona: implementer
3. **The settings states still missing (D8).** The states the landing of PLAN-V2-43 names undelivered: `settings.choose`, reconfigure an aspect as a later run, consent withdrawal (`settings.withdraw`, `settings.remove-consent`, audited, asked again at its next action), the providers checked again (`settings.verify-providers`), a credential rotated or replaced with every token derived from the old secret dropped and a held plan going on. The two writers (documents mode, cleanup durations) exist at `MachineConfigurationStore.java:149,171`; every new writer carries the revision and raises `SettingsConflict`, with a test of two changes against one revision that fails against a writer that overwrites.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java:java-null-safety, pm-dev-java:junit-core
   - Persona: implementer
4. **The axes in the composition (D10) and the landing check's contract (D11).** The composition half PLAN-V2-43 left: the landing variant composed into the step list of `phase-6-finalize`, the composed file per variant, the project-level and machine-level axis readers. `doc/Traceability.adoc` PM-WF-25 moves from "Not implemented yet" for exactly what lands. D11: the `compose(request, project)` overload is the only path a plan's composition takes in the code of this repository, or the plan states why the opt-in overload stays (the fail-open that ADR `workflow-plan/021` Alternative 3 rejects for the setup code); `LandingVariantValidation.Project` gains an unknown state so a failed provider lookup is not read as "not protected" (L3-1, L3-2 of the landing; carried to PLAN-V2-114 item 36 are the small points, not these two, which change a type).
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java:java-null-safety, pm-dev-java:junit-core
   - Persona: implementer
5. **The package closes (R01).** Delete `doc/plans/machine-onboarding-unit.adoc` and its row in `doc/plans/README.adoc`, state in the landing which Tasks PLAN-46 (2, 5, 6, 7, 10), PLAN-V2-39 (3, 4), PLAN-V2-43 (the two writers, `overrides.json`, the engine half of the landing check) and this plan landed, and name every unit owed to `plan-marshall-mcp`.
   - Skills: none
   - Persona: documenter

**Done when:** deliverable 1's table is in the pull request body with a verdict per row; each state of deliverable 3 and each branch of deliverable 2 that the table marks buildable here has a test that fails at `27378186` (the behaviour is new there) and passes at the head; two changes against one revision of the settings of deliverable 3 end with the first recorded and the second refused with `conflict` and the current value, and the test fails against a writer that overwrites; a landing variant bound by a composition that went through `compose(request)` is impossible in this repository or its absence is stated, and `LandingVariantValidation.Project` carries an unknown state with a test; `doc/Traceability.adoc` PM-WF-25 names exactly what is implemented; the package file and its index row are removed and the landing lists the units owed. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue — issues plus the security hotspots still to review — other than one dismissed with its reason, `unknown` for a failed read and never `0`, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then the targeted `-Dtest` runs of the changed classes and the per-module run of every module the plan changes (a full `verify` exceeds the host's bound), `verify -Pcoverage` and the integration tests of every changed module with tests. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main`.

**Trace starting set:** `landings/PLAN-V2-43.md` (read from the main checkout) and the package file `doc/plans/machine-onboarding-unit.adoc`; use cases UC-ONB-1 to UC-ONB-10; requirements PM-WF-26, PM-WF-25, PM-SEC-8, PM-CRED-2, PM-WORK-1; specifications State Catalogue § Machine Onboarding and § Onboarding Aspects, Workflow DSL § Machine Scope, § Plan Behaviour Axes and § Plan Overrides, CLI & Security § Automatic Onboarding.

## Standing Constraints

- The default units are `plan-marshall-mcp` resources: this repository proves the Done-when over fixtures and names the real units owed; it adds no model-facing content to a JAR.
- A class or test that the table or a Traceability line names exists (`git ls-files`).
- Pre-1.0: no deprecation notes, no backward-compatibility shims.

## Claim Labels

- OBSERVED: `MachineConfigurationStore.writeDocumentsMode` (`:149`) and `writeCleanupDurations` (`:171`) exist with the revision rule, and the other settings states do not — `pm-runtime/src/main/java/de/planmarshall/runtime/config/MachineConfigurationStore.java`; no code or fixture for `settings.choose` or `auto.result` in this repository — read by the three-layer leaf of PLAN-V2-43 at `27378186`
- OBSERVED: the composition of the landing variant into `phase-6-finalize` is "Not implemented yet" — `doc/Traceability.adoc:726` (PM-WF-25)
- OBSERVED: `PlanInstanceComposer.compose(request)` calls `compose(request, null)` and so skips the landing check — `pm-workflow/src/main/java/de/planmarshall/workflow/plan/PlanInstanceComposer.java:147` (re-read at `27378186`)
- HYPOTHESIS: D2 and D7 can be built here over fixtures once a proposal vocabulary exists in `pm-workflow` — confirm/refute at the package file's Tasks 1 and 8 and `pm-workflow/src/main/java/de/planmarshall/workflow/` (verify-at-outline)
- HYPOTHESIS: the Expected Surface below is inferred from the landing's accounting of what is owed — confirm at deliverable 1's table (verify-at-outline)
- Verify-first clause: before scoping, fetch `origin/main`, list the open pull requests for one that touches `doc/plans/machine-onboarding-unit.adoc`, `PlanInstanceComposer.java` or `MachineConfigurationStore.java` (PLAN-V2-82 shares two files with PLAN-V2-43 and may change them), and re-read the dependents of the package (PLAN-V2-17, 21, 22, 24, 69, 73, 74, 133) for which half each needs; a refutation loops back to re-scope.

## Expected Surface

- OBSERVED: `doc/plans/machine-onboarding-unit.adoc` — the package file, corrected and then deleted when the package is done
- OBSERVED: `doc/specification/state-catalogue/14-machine-onboarding.adoc` — specification part; corrected where deliverable 1's table says
- HYPOTHESIS: `doc/specification/state-catalogue/19-onboarding-aspects-models-providers.adoc` — specification part named by Tasks 1 and 12 (confirm at deliverable 1's table)
- OBSERVED: `doc/specification/workflow-dsl/01-grammar.adoc` — specification part; the implemented sections condensed
- OBSERVED: `doc/specification/workflow-dsl/09-plan-instance.adoc` — specification part; the implemented sections condensed
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/PlanInstanceComposer.java` — the composition and the `compose(request)` overload
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/LandingVariantValidation.java` — the `Project` record gains an unknown state
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/config/MachineConfigurationStore.java` — the writers of the missing settings states
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/PlanFacts.java` — the project-level and machine-level axis readers (confirm at the outline)

Shared bookkeeping, not surface (R19): `doc/Traceability.adoc`, `doc/plans/README.adoc`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-43 (shipped #462: the writers, `overrides.json`, the engine half of the landing check) and PLAN-V2-81 (`machine-stores-residue`; it hardens `MachineConfigurationCodec` and `MachineConfigurationStore` first, as it did for PLAN-V2-43).
- Overlaps with: PLAN-V2-82 (staged; two files shared with PLAN-V2-43, to be re-read at the outline) and PLAN-V2-114 item 36 (the small points of the PLAN-V2-43 landing, on `PlanOverridesCodec` and `PlanOverridesStore`: not this plan's surface, one rebase if both run).
- Blocks: the dependents of the package that name PLAN-V2-43 in prose — PLAN-V2-17, 21, 22, 24, 69, 73, 74 and 133 — in the part each needs that PLAN-V2-43 did not deliver; the gate re-reads which half each needs at its emit.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-153-machine-onboarding-remainder.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

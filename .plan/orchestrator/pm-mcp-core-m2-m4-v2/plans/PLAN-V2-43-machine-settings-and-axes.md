# PLAN-V2-43: The machine's settings, the automatic onboarding and the plan axes

epic: pm-mcp-core-m2-m4-v2
workstream: WS-04
origin: PLAN-109 of the epic pm-mcp-core-m2-m4

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-43-machine-settings-and-axes.md` and is queued as one row file, `queue/PLAN-V2-43.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.
> **Split from PLAN-46 on 2026-10-28** (operator: "Split it first, emit the first part", after PLAN-107 closed the package `token-usage`, #215 `81c51719`): the package `machine-onboarding-unit` is worked by **PLAN-46** (the machine's stores and the credential-store aspect), **PLAN-V2-39** (the harness scan, the probes and the verification of an unknown harness, the usage coverage and the remainders of the usage package) and **PLAN-V2-43** (the machine's settings, the automatic onboarding, the sub-workflows, the plan axes and the landing validation). The bullets keep the numbers of the package file (D1 to D12). The package file `doc/plans/machine-onboarding-unit.adoc` and its index row are removed by the **last** of the three to land (R01): PLAN-V2-43; the other two leave them in place. Why: the spec carried 11 deliverables, the package gained a twelfth, and PLAN-43 stalled twice on size.
> **State on 2026-10-28:** the dependency on `token-usage` is spent (closed by PLAN-107, #215) and `operator-wait-and-machine-scope` is shipped (PLAN-42, #199/#203): the package depends on nothing. PLAN-98 (`judgment-endpoint`, running) lifts the refusals of `ProfileValidator` (the remote form, `system_one`): the plan that changes `ProfileValidator` waits for it. The claim verdicts below were stamped at `f4a1bd07`, before the closure; re-read them at the outline. The onboarding's default units (`onboarding.pmwf`, the aspect sub-workflows, `landing.*`, `method.*`, `ask.*`) are `plan-marshall-mcp` resources on its classpath, not units of `pm-workflow`: on the same footing as PLAN-43 (B2/B3, 2026-10-27) this repository builds the engine, store and runtime half and proves the Done-when over fixtures, and the landing names the real units as owed.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r2`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R15, R17, R18, R19.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: the developer file of each package it changes (`{module}/doc/developer/{package}.adoc`); ADR directory: `{module}/doc/adr/{package}/`; layout: workstream WS-10.
- R12 — log message holder: `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`); `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`).

## Objective

Implement the last part of the work package `machine-onboarding-unit`: the onboarding's sub-workflows in the machine scope (D2), the automatic onboarding with its result (D7), the machine's settings UC-ONB-10 (D8), the plan behaviour axes and plan overrides in the composition (D10) and the validation of the landing (D11). Serves: roadmap Milestone 7, Milestone 10 (the landing axis, the plan's overrides); UC-ONB-1 to UC-ONB-10; the onboarding and the machine's settings in both clients of `pm-mcp-clients`. Where: pm-workflow (the sub-workflows in the machine scope, the settings, the axes and overrides in the composition); the real onboarding units are `plan-marshall-mcp` resources (see the State note). **It waits for PLAN-46 and PLAN-V2-39.** It removes the package file `doc/plans/machine-onboarding-unit.adoc` and its index row (R01), as the last of the three plans.

The composition side of D10 shipped earlier (Plan-instance alignment #107 `21939cb2` and #131 `3d4baf7d`: `CompositionEvent.OVERRIDE`, `PruneReason.MERGE_BY_OPERATOR` and `PLAN_DEFAULT`, `PlanFacts` axis levels, `composition-rules.json` without `auto-merge-off`; ADR `workflow-plan/008`, `015`): read it first, the spec says implemented.

## Deliverables

The bullets of `doc/plans/machine-onboarding-unit.adoc` § What assigned to this plan, in the package's words (numbers are the package's).

2. The onboarding's sub-workflows in the machine scope of `operator-wait-and-machine-scope`: languages, harnesses (one scan, a default), models, roles, credentials, providers, plan defaults; each aspect a compiled sub-workflow with the parameter `onboard_run` (`first_run`, `reconfigure`); the first run from `onboard.entry` through `onboard.mode` (guided or automatic), a later run of one aspect from the machine's settings (`settings.choose`) with its current values as the proposal, returning there.
7. The automatic onboarding with its consent and its result (reasons, items); _change_ on `auto.result` calls that one aspect as a later run (`onboard_run: reconfigure`, its current values as the proposal) through `onboard.next` and returns to the result; a state it cannot decide alone (a login, a token, no harness found, the model ids of a harness that cannot list its models) shown as in the guided run.
8. The machine's settings (UC-ONB-10), states of the onboarding unit reached from `onboard.completed`: `settings.choose` (the recorded aspects, the remembered consents, the documents mode, the cleanup's durations, the credentials with their status, the paired devices, which are acted on through their own endpoints); reconfigure an aspect as a later run of its sub-workflow; withdraw a consent (`settings.withdraw`, `settings.remove-consent`, audited, asked again at its next action); the documents mode (`settings.documents-mode`, `settings.record`: the machine key `sources.documents_mode`, `copy`, `move`, or `ask`, the default, which `start-source-intake` reads); the cleanup's durations (`settings.cleanup-durations`, `settings.record`: the machine's `cleanup.*` durations per kind and the reminder's days, which `cleanup` reads); the providers checked again on request (`settings.verify-providers`, `call providers` at its entry `verify` over every chosen provider); a credential rotated or replaced (`settings.credential`, `settings.store-credential`, every token derived from the old secret dropped), verified again through `providers.verify-consent`, `providers.verify`, and `providers.failed`, after which a plan held by the expired credential goes on.
10. The plan behaviour axes and plan overrides in the composition: each variant a sub-workflow named with its axis as prefix, an axis's variants a closed list; the method variant (`method.test-first`, `method.tests-after`) called by `plan.order-tasks` of `phase-4-plan`, the asking variant (`ask.every-phase`, `ask.key-points`, `ask.questions-only`) by the plan unit's `phases.ask`; a landing variant composed into the step list of `phase-6-finalize` at its landing orders, which keeps its checks and reviews, with `landing.pull-request` (push, pull request, its reviews, merge), `landing.main` (commit and push on the base branch), and `landing.files` (no commit, the changes written into the main checkout's working tree, the plan handed back), apart from the land lane; the binding of each axis at the plan's start; the composition event `override`, the prune reasons `plan_default` and `merge_by_operator`, and the removal of the shipped rule `auto-merge-off` are implemented (Plan Instance Developer Guide § The Plan Behaviour Axes); `overrides.json` with a role's harness, model, and effort, the variant of each axis and of an integration, and the optional providers.
11. Validation of the landing: `defaults.validate` checks only what the machine provides (a provider for `landing.pull-request`), never a repository; a landing variant is validated where a project is known (its setup, a plan's start, a plan's settings), refusing `landing.pull-request` without a provider and `landing.main` on a protected base branch with the reason, also a machine default that does not fit the project.

**Done when** (the part of the package's acceptance that this plan owns): the onboarding runs from its first state to `onboard.completed` and as a later run of each aspect, against the scenarios of `onboarding-contract` replayed on the runtime (here: over the fixtures); the automatic run reports each value it chose, and a _change_ on its result runs that aspect and returns to the result; each branch of the machine's settings is tested, the cleanup's durations and the providers checked on request included, a withdrawn consent asked again, a rotated credential verified and a plan held by the old one going on; a landing variant is refused with its reason where it does not fit a project, and the machine default is never checked against a repository. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (`pm-workflow`, and `pm-core` where a store gains a writer; where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and the integration tests of every changed module with tests (`verify -Pintegration-tests -pl <module> -am`, only on `pm-core` and `pm-runtime`) are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names a deleted watch item (`PM-WATCH-CLIENT-13`, `-14`, `PM-WATCH-DISP-33`, `-40`, `-43`, `-48`, `-55`, `-64`, `-65`) is the sibling's own links, not a defect of this change.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Use cases: UC-ONB-1 to UC-ONB-10
- Requirements: PM-WF-26; PM-WF-25; PM-SEC-8; PM-CRED-2; PM-WORK-1
- Specification: the sections the first bullet of _What_ names; Workflow DSL § Machine Scope; § Plan Instance; Model Work § Roles & Configuration; Store Schemas § Machine Stores; Client Profiles § Profile Schema


## Standing Constraints

None beyond the standing rules named in the First Instruction above.

## Claim Labels

- OBSERVED: the Objective, Deliverables, Done when and Trace above are the package file's own — read at `doc/plans/machine-onboarding-unit.adoc` § What, Trace, Done When on `origin/main` at `4617865` (11 bullets; the spec is regenerated from it by cleanup on 2026-10-22)
  - verdict: corroborated | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: doc/plans/machine-onboarding-unit.adoc unchanged since 88ec9c70; 11 What bullets
- OBSERVED: the package file and its index row (`open`; depends on PLAN-42 (`operator-wait-and-machine-scope`); PLAN-61 (`token-usage`) (the usage record of the verification)) — read at `doc/plans/machine-onboarding-unit.adoc` and `doc/plans/README.adoc` on `origin/main` at `4617865`
  - verdict: corroborated | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: index row open, depends on operator-wait-and-machine-scope and token-usage; token-usage open (now depends only on role-qualification)
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line only — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis: landed code touched only core/store, core/profile, runtime/worker, runtime/log, runtime/job (one case); core/config still package-info only; no consents, onboarding or settings code
- Verify-first clause: before scoping, check that the index no longer says `blocked`, that the use cases are agreed, and which of the packages this one restructures have shipped since; a refutation loops back to re-scope.
  - verdict: corroborated | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: index row open, not blocked; no open PR names it; restructured packages as stated plus #185 and #190

## Carried From The Re-grounding Records (2026-10-25, 2026-10-26)

- **Home of the default units.** `onboarding.pmwf`, the aspect sub-workflows, `landing.*`, `method.*` and `ask.*` are default units of `plan-marshall-mcp` (`classpath:/workflows/`), which the package does not list as a dependency: build the engine half over fixtures (the PLAN-43 B2/B3 footing) and name the real units owed in the landing, unless the operator decides otherwise.
- **#181** — the machine-config member `diagrams.format` (default `text`) must be kept by D2 and D3 of the original numbering; decide whether `settings.choose` or the language aspect records it.
- **D8 depends on PLAN-46's writers** (the consent store, `MachineConfiguration`, the catalogue and role writers) and **on PLAN-V2-39's harness list**; D9's revision rule is PLAN-46's and is only used here.
- **R12:** the plan parameters list `WorkflowLogMessages` and `CoreLogMessages`.

## Expected Surface

- OBSERVED: `doc/plans/machine-onboarding-unit.adoc` — the package file, deleted when the package is done
- OBSERVED: `doc/specification/state-catalogue/14-machine-onboarding.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/workflow-dsl/01-grammar.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/workflow-dsl/09-plan-instance.adoc` — specification part; the implemented sections deleted

## Dependencies and Sequencing


- **Landing note (2026-10-29):** PLAN-46 landed (#233) as part 1 of 3: the writers for settings, documents mode and cleanup durations do NOT exist; `MachineConfigurationStore` lives in `pm-runtime` and writes `providers` only, copying other members through. This plan is the last part, so it removes the package file `machine-onboarding-unit.adoc` and its index row, and reduces the four spec parts that the lane left untouched.
- Depends on: PLAN-46 (the stores and the credential-store aspect); PLAN-V2-39 (the harness verification and the harness list); `token-usage` is spent.
- Overlaps with: PLAN-43 (running) on `doc/specification/workflow-dsl/01-grammar.adoc` and PLAN-105 (staged) on `doc/specification/workflow-dsl/09-plan-instance.adoc` (documentation only; PLAN-43 merges before this plan starts).
- Blocks: PLAN-V2-17 (`project-onboarding-unit`); PLAN-V2-24 (`start-from-clients`); PLAN-V2-21 (`plan-and-epic-control`); PLAN-V2-73 (`runtime-control`); PLAN-V2-69 (`process-designer`); PLAN-V2-74 (`cleanup`); PLAN-V2-22 (`start-source-intake`) — the package's dependents, released when the package is done.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-43-machine-settings-and-axes.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

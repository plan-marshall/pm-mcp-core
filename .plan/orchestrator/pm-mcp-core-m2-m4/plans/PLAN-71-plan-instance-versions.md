# PLAN-71: Plan instance versions in force: composition events, the engine's switch, Rules 13 and 14, resources

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-71-plan-instance-versions.md` and is queued as one row file, `queue/PLAN-71.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r1`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R15, R17, R18.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: the developer file of each package it changes (`{module}/doc/developer/{package}.adoc`); ADR directory: `{module}/doc/adr/{package}/`; layout: workstream WS-10.
- R12 — log message holder: `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`); `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`); `RuntimeLogMessages` (`de.planmarshall.runtime.log`, prefix `PM_RUNTIME`).

## Objective

Implement the work package `plan-instance-versions` of `doc/plans/plan-instance-versions.adoc`: plan instance versions in force: composition events, the engine's switch, Rules 13 and 14, resources. Serves: Roadmap Milestone 3 (the plan instance: the triggers of the composition events, the engine's switch to a version in force with its read of the stored sub-unit texts, a store that refuses a version the composer did not verify, the instance resources) and Milestone 4 (Rule 13 at composition; Rule 14 joins with the skill catalogue of Milestones 7 and 14). Where: pm-workflow packages `workflow.plan` (`PlanInstanceComposer`, `PlanInstanceStore`) and `workflow.engine` (`WorkflowEngine`, the composition at the entering transaction, the version in force), with the composition-time checks in `workflow.verify`; pm-core package `core.store` (`instance_version` of the workflow state). The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

## Deliverables

The bullets of `doc/plans/plan-instance-versions.adoc` § What, in its words (regenerated at 710da61, after the specification moved into this repository).

Context: `plan-instance` built the composer, the store of the versions and of the bound sub-unit texts, and the rendering of an instance; nothing composes a version on an event yet, the engine runs the one unit it was built with (`WorkflowEngine`), and `instance_version` is a member name of the status codec without a field. This package makes the versions take effect, as Workflow DSL § Plan Instance (Versions & Composition Events) and Store Schemas § Plan Instance state. The alignment of the reasons, rules, axes, header, and `diff_summary` is `plan-instance-alignment`'s.

1. The triggers: the engine composes a version of a server event (`outline`, `plan`, `fallback`, `empty_harvest`, `replan`) in the transaction that enters its target state, so the version always contains it and is in force for the rest of its composing chain from that transition; a refused composition of a server event raises `precondition_unmet` on the composing transition with the reason. The operator and configuration events (`config_adopted`, `waiver`, `override`) enter through one entry point that composes, applies the position rule, refuses with `position_not_contained` (a configuration candidate kept, with `kept` or `identical_adopted` in the log), and puts the version in force from the next transition, never mid-state. `start` is composed in the plan's creation transaction. The callers of the operator events and of `start` and `replan` are the packages of those acts (`start-from-clients`, `operator-wait-and-machine-scope` for waivers, `process-designer` for `config_drift`, `plan-and-epic-control` for overrides).
2. The version in force: `instance_version` of `mcp/status.json` becomes a field of the workflow state, written in the transaction that puts a version in force; the engine runs a plan from the composed units of that version and, for a bound sub-unit, from its stored text `mcp/units/<digest>.pmwf` instead of the level's copy, so an edit of a project's or the machine's unit never changes a running call; a re-composition resets no counter and rewrites no history.
3. Only verified versions are stored: `PlanInstanceStore` accepts only a version the composer returned as verified, never a version built elsewhere, beside its checks of the version, the header, and the composition record.
4. Rules 13 and 14 at composition: every role a `job llm` state or a `consult role` clause of a composed unit and of every unit it calls names resolves in the effective role set with the plan's overrides applied and fits its use (`role_undefined`, `class_mismatch`: an `ask` on a `zero_tool` role, a consultation of a `zero_tool` role, a quorum's roles not `read_only` or not on pairwise different models), over the role set of `role-sets-and-model-caller`; every skill a `skill` clause names resolves in the project's skill catalogue (`skill_unresolved`), read through a port that the assembly fills; at `start` the plan is not created, at a server event the transition raises `precondition_unmet`, an operator event is refused. The static verifier's seams of Rules 13 and 14 and of the `skill` clause of Rule 3 close.
5. The instance resources: the read of a version's DSL text with its header (`pm://plan/{plan_id}/instance[/{version}]`), the diagram of the version in force with the active state, and the version with its last composition event for the status; the bindings to the resource URIs and to the plan's process resource are the assembly's in `plan-marshall-mcp`.

**Done when** (the package's own acceptance): A fixture plan entering the target state of each server event composes its version in that transaction and selects from it in the next `auto` state; an operator event's version is in force from the next transition and one that fails the position rule is refused and logged; after a version switch the engine runs the stored sub-unit text although the project's copy changed; the store refuses a version the composer did not return; a missing role, a role of the wrong class, a quorum on one model, and an unresolved skill each refuse the composition with its reason, at `start`, at a server event, and at an operator event as specified; the text of a version and the diagram of the version in force are read through the package's API; `verify`, `verify -Ppre-commit`, `verify -Pcoverage`, and `verify -Pintegration-tests` are green. **Package bookkeeping:** `doc/plans/plan-instance-versions.adoc` and its row of `doc/plans/README.adoc` are removed in the same pull request (where the implemented specification sections and watch items named in the Expected Surface are not deleted, the pull request body says why for each, as the landings of PLAN-25 and PLAN-26 did), and the links check (R17, absolute paths of the sibling checkout) reports `BROKEN LINKS 0`, the run recorded in the pull request body. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-IMPL-5; PM-WF-1; PM-WORK-1; PM-SKILL-5; PM-EXT-7
- Specification: the sections the introduction names; Workflow DSL § Static AST Verification Rules (Rules 13 and 14); Store Schemas § Workflow State Store (`instance_version`); MCP Tools § Resource URI Templates; Runtime & Interaction Model § The Plan's Process as a Structure
- Watch: PM-WATCH-DSL-21; Workflow DSL

## Standing Constraints

None beyond the standing rules named in the First Instruction above.

## Claim Labels

- OBSERVED: the Objective, Deliverables, Done when and Trace above are the package file's own — read at `doc/plans/plan-instance-versions.adoc` § What, Trace, Done When on `origin/main` at `4617865` (5 bullets; the spec is regenerated from it by cleanup on 2026-10-22)
  - verdict: contradicted | checked_at: 88ec9c7092b3f9676171cc31bc3df0d5305832f0 | by: pm-mcp-core-m2-m4/cleanup | rescoped: yes | evidence: The package file changed after 4617865: #146 (964f673d) and #151 (35fba9ed) rewrote its Context sentence (the alignment is now stated as done, linking workflow-plan.adoc, no longer plan-instance-alignment's) and its Rules 13 and 14 bullet (role-sets-and-model-caller replaced by the link to Model Work Role Sets & Precedence); the spec's Deliverables still carry the old wording. Substance of the 5 bullets, Trace and Done When is unchanged.
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line, the class names of § What and the specification parts the package links, not from a trace matrix — confirm/refute at the trace matrix of `traced-implementation` (verify-at-outline)
  - verdict: unverifiable | checked_at: 88ec9c7092b3f9676171cc31bc3df0d5305832f0 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: Hypothesis (verify-at-outline). 7 observed paths exist at HEAD; the hypothesised code and test twins and the developer files (workflow-engine, workflow-plan, workflow-verify, core-store) all exist (see surface block).
- Verify-first clause: before scoping, list the open pull requests for one that names `plan-instance-versions`, check in `doc/plans/README.adoc` that no package it depends on is still listed, and read the roadmap item this package serves for what the code already covers; a refutation of any claim above loops back to re-scope.
  - verdict: corroborated | checked_at: 88ec9c7092b3f9676171cc31bc3df0d5305832f0 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: README row plan-instance-versions at HEAD: open, depends on nothing (role-sets-and-model-caller shipped as #146); no remote branch names it. Package file last changed by #151.

## Expected Surface

- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/` — production code
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/` — tests
- HYPOTHESIS: `pm-core/doc/developer/core-store.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-core/doc/adr/core-store/` — ADRs of the package's design decisions; new directory if absent
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/engine/` — production code
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/engine/` — tests
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-engine.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-engine/` — ADRs of the package's design decisions; new directory if absent
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/` — production code
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/plan/` — tests
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-plan.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-plan/` — ADRs of the package's design decisions; new directory if absent
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/` — production code
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/verify/` — tests
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-verify.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-verify/` — ADRs of the package's design decisions; new directory if absent
- OBSERVED: `doc/plans/README.adoc` — this package's own row only
- OBSERVED: `doc/plans/plan-instance-versions.adoc` — the package file, deleted when the package is done
- OBSERVED: `doc/implementation-watch/workflow-dsl.adoc` — watch document; the covered items deleted
- OBSERVED: `doc/specification/store-schemas/02-workflow-state-store.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/14-plan-instance-waivers-questions.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/workflow-dsl/04-ast-node-model.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/workflow-dsl/09-plan-instance.adoc` — specification part; the implemented sections deleted
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/model/` — exists at HEAD `88ec9c70`; added by the re-grounding of 2026-10-25 (see Re-grounding Record)
- OBSERVED: `pm-workflow/doc/developer/workflow-model.adoc` — exists at HEAD `88ec9c70`; added by the re-grounding of 2026-10-25 (see Re-grounding Record)
- OBSERVED: `pm-workflow/src/main/resources/de/planmarshall/workflow/plan/composition-rules.json` — exists at HEAD `88ec9c70`; added by the re-grounding of 2026-10-25 (see Re-grounding Record)

## Re-grounding Record (2026-10-25, HEAD `88ec9c70`)

Written by `pm-mcp-core-m2-m4/cleanup`, measured against `main` at `88ec9c70` (the plans #172, #169 and #164 and the specification pull requests #141, #165, #168, #171, #173, #174 and #175 have landed since the grounding of `4617865`). The Objective, Deliverables, Dependencies and Hand-Off above are the earlier text and stay as the audit record; **where this section and that text differ, this section is the later reading**, and the outline records the departure. Spec-sourced items below are the specification's own content, not inferences of the orchestrator; every statement about the repository was read at the cited place.

- Claims re-read: 3: 1 corroborated, 1 contradicted (re-scoped by this section), 1 unverifiable. Verdicts are stamped on the claims above (`checked_at: 88ec9c70`).

### Premises corrected (contradicted at HEAD, re-scoped here)

- Claim 0: The package file changed after 4617865: #146 (964f673d) and #151 (35fba9ed) rewrote its Context sentence (the alignment is now stated as done, linking workflow-plan.adoc, no longer plan-instance-alignment's) and its Rules 13 and 14 bullet (role-sets-and-model-caller replaced by the link to Model Work Role Sets & Precedence); the spec's Deliverables still carry the old wording. Substance of the 5 bullets, Trace and Done When is unchanged.

### Surface

- Added to the Expected Surface above: `pm-workflow/src/main/java/de/planmarshall/workflow/model/`, `pm-workflow/doc/developer/workflow-model.adoc`, `pm-workflow/src/main/resources/de/planmarshall/workflow/plan/composition-rules.json`.
- understated: Rule 13 reuses code PLAN-26 shipped in pm-workflow/src/main/java/de/planmarshall/workflow/model/ (RoleComposition, RoleUse, CompositionRefusal) and PlanInstanceComposer does not call it yet; add that path and pm-workflow/doc/developer/workflow-model.adoc. For Rule 14 the skill port has precedent in pm-core core/role/RoleEnvironment.resolveSkill and pm-runtime launch/SkillSource; add pm-workflow/src/main/resources/de/planmarshall/workflow/plan/composition-rules.json if a rule changes.

### Shipped or superseded since the last grounding

- partly-fixed: PLAN-26 #146 (964f673d) shipped workflow.model RoleComposition, RoleUse, CompositionRefusal (role_undefined, class_mismatch for model-job roles and consulted zero_tool roles): the role half of Rule 13, not wired into PlanInstanceComposer. Unbuilt at HEAD: the engine switch (WorkflowEngine takes one unit), instance_version field (member name only in WorkflowStatusCodec), precondition_unmet, quorum clause, skill_unresolved, and the store guard (PlanInstance has a public constructor, recordComposed does not check provenance).
- superseded-by-shipped: PLAN-103 shipped (#151) so 'runs beside PLAN-103 (running)' is stale; PLAN-26 shipped #146 so the role-sets-and-model-caller reference in Deliverable 4 points at a deleted package file.

### Open questions for the outline

- Start is 'composed in the plan's creation transaction' and 'the plan is not created' on a refusal, but no plan-creation code exists at HEAD (only the bootstrap-plan address kind) and its package, start-from-clients, is not a dependency; the Done-when asks for the refusal at start.
- The quorum clause of Rule 13 (roles read_only and on pairwise different models) is specified under the quorum validation of model-work/10 line 122, not in the Composition Check of model-work/01; decision-quorum-and-calibration does not mention composition, so which package builds quorum_invalid validation is open.

## Dependencies and Sequencing

- After #150 (`ac34703`, 2026-10-23): `icon` is a required key of every unit's frontmatter, kept by `PlanInstanceComposer` and `PlanUnitMerger`; 18 test files of `pm-workflow` (`EngineFixtures`, `EngineResumeTest`, `GuardEvaluatorTest`, `WorkflowEngineTest`, `InstancePrunerTest`, `PlanFixtures` and the verify tests among them) were migrated and are the fixtures this plan builds on: read them at the head of `main`. The stored unit text `mcp/units/<digest>.pmwf` is a unit like any other and carries its `icon:` line; a stored text from before #150 does not load (no compatibility path, format 1, pre-1.0).
- Depends on: PLAN-26 (`role-sets-and-model-caller`)
- Builds on PLAN-26 (`role-sets-and-model-caller`, shipped as #146): the composition refuses a missing role, a role of the wrong class and a quorum on one model through the role sets and the effective configuration of `core.role`, which are on `main`; read them at the head of `main` and use their types.
- Runs beside PLAN-103 (`spec-coverage-pass`, running) once emitted: PLAN-103 edits `doc/plans/README.adoc` and the `Depends on` cells of the package files, `plan-instance-versions.adoc` among them (2 shared files). Read both at the head of `main` and merge the base branch (the lane's step 7) before the merge gate.
- Overlaps with: computed by `corpus cross-check` at emit time; every plan through `doc/plans/README.adoc` and `doc/Traceability.adoc`; the plans of the same module through its log holder, which is not a declared surface
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` and the client contract `pm-api` in `pm-mcp-clients`, which stay untouched — a change there is a pull request in that repository; and `plan-marshall-documentation`, where only a requirement correction or a change of `cross-cutting.adoc` or of a specification that lives there goes, in a pull request there

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-71-plan-instance-versions.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

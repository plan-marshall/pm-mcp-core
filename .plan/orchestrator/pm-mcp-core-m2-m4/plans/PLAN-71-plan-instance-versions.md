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

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R15, R18.

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

**Done when** (the package's own acceptance): A fixture plan entering the target state of each server event composes its version in that transaction and selects from it in the next `auto` state; an operator event's version is in force from the next transition and one that fails the position rule is refused and logged; after a version switch the engine runs the stored sub-unit text although the project's copy changed; the store refuses a version the composer did not return; a missing role, a role of the wrong class, a quorum on one model, and an unresolved skill each refuse the composition with its reason, at `start`, at a server event, and at an operator event as specified; the text of a version and the diagram of the version in force are read through the package's API; `verify`, `verify -Ppre-commit`, `verify -Pcoverage`, and `verify -Pintegration-tests` are green. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-IMPL-5; PM-WF-1; PM-WORK-1; PM-SKILL-5; PM-EXT-7
- Specification: the sections the introduction names; Workflow DSL § Static AST Verification Rules (Rules 13 and 14); Store Schemas § Workflow State Store (`instance_version`); MCP Tools § Resource URI Templates; Runtime & Interaction Model § The Plan's Process as a Structure
- Watch: PM-WATCH-DSL-21; Workflow DSL

## Standing Constraints

None beyond the standing rules named in the First Instruction above.

## Claim Labels

- OBSERVED: the Objective, Deliverables, Done when and Trace above are the package file's own — read at `doc/plans/plan-instance-versions.adoc` § What, Trace, Done When on `origin/main` at `4617865` (5 bullets; the spec is regenerated from it by cleanup on 2026-10-22)
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: doc/plans/plan-instance-versions.adoc re-read at 4617865 (last commit touching it: 21939cb): 5 What bullets, Trace and Done When equal the Deliverables, trace set and acceptance above (regenerated by cleanup; the corpus regeneration diff reads current)
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line, the class names of § What and the specification parts the package links, not from a trace matrix — confirm/refute at the trace matrix of `traced-implementation` (verify-at-outline)
  - verdict: unverifiable | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan; 7 OBSERVED surface paths checked at 4617865, all exist
- Verify-first clause: before scoping, list the open pull requests for one that names `plan-instance-versions`, check in `doc/plans/README.adoc` that no package it depends on is still listed, and read the roadmap item this package serves for what the code already covers; a refutation of any claim above loops back to re-scope.
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: row plan-instance-versions is open in the index at 4617865; open PRs (ci pr list): #71 (a dependabot bump) is the only open pull request, and it names no package; doc/plans/plan-instance-versions.adoc last changed in 21939cb; the claims above were re-read individually

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

## Dependencies and Sequencing

- Depends on: PLAN-26 (`role-sets-and-model-caller`)
- Overlaps with: computed by `corpus cross-check` at emit time; every plan through `doc/plans/README.adoc` and `doc/Traceability.adoc`; the plans of the same module through its log holder, which is not a declared surface
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` and the client contract `pm-api` in `pm-mcp-clients`, which stay untouched — a change there is a pull request in that repository; and `plan-marshall-documentation`, where only a requirement correction or a change of `cross-cutting.adoc` or of a specification that lives there goes, in a pull request there

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-71-plan-instance-versions.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

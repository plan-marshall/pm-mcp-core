# PLAN-67: The plan's process, running work, and ended work as resources

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-67-process-and-overview.md` and is queued as one row file, `queue/PLAN-67.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r1`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R12, R13, R14, R15, R16, R18.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: the developer file of each package it changes (`{module}/doc/developer/{package}.adoc`); ADR directory: `{module}/doc/adr/{package}/`; layout: workstream WS-10.
- R12 — log message holder: `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`); `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`); `RuntimeLogMessages` (`de.planmarshall.runtime.log`, prefix `PM_RUNTIME`).

## Objective

Implement the work package `process-and-overview` of `doc/plans/process-and-overview.adoc`: the plan's process, running work, and ended work as resources. Serves: roadmap Milestone 7 (the plan's process with its events, the overview with what needs the operator), Milestone 10 (ended work); Use Cases, UC-PLN-1 (the plan's process), UC-PLN-4 (what runs, across projects), and the ended work of UC-CTL-1; the overview, the plan view, and ended work in both clients of `pm-mcp-clients`. Where: pm-workflow (the node states derived from the plan's history, the composed process as a tree); pm-core (titles and names, the start of the current phase); pm-runtime (the resources and the events per scope). The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

## Deliverables

The bullets of `doc/plans/process-and-overview.adoc` § What, in its words (regenerated at 710da61, after the specification moved into this repository).

1. Implements Runtime Model § The Plan's Process as a Structure, § Events of the Process, § What Needs the Operator, at a Glance, § The Overview Resource and Its Events, § Events per Scope, § Ended Work, State Catalogue § Running Work, and Store Schemas § Titles and Names (`PM-ARCH-11`).
2. The process: `GET /api/v1/plans/{plan_id}/process` answers the composed process of the instance in force as a tree (the plan, its phases, every workflow's states in their order, every call expanded to the leaves), never DSL text; each node with its `node_id`, its kind, and its state derived from the plan's history of `phase-sub-workflows` (`done`, `running`, `planned`, `waiting_for_operator`, `failed`, `not_taken`, `not_in_plan` with its prune reason), how often it ran, its entry and exit times, a leaf's job or tasks with a link to their activity (`worker-activity`); the path to the current position; the context (project, epic, activity, `needs_operator`, tokens so far with split and coverage); the `instance_version`.
3. The events of the process: on the plan's stream `state.entered`, `state.left`, `subworkflow.called`, `subworkflow.returned`, `step.opened`, `step.closed`, `instance.changed`, `activity.changed`, and `tokens.changed` (at most once per metrics event interval), each with its `node_id`.
4. What needs the operator: a `needs_operator` summary per plan, epic, and project, derived on read and never stored, with its count and per item its kind and link, in the closed order `decision`, `waiver_request`, `operator_wait`, the holds (`integrity_pause`, `stalled`, `condition`, `held`, `landing_failed`), `findings`, `setup` (a project's setup repair or a step its onboarding owes); an epic's summary including its plans', a project's its plans', epics', and workspace scope's; a change of a summary announced on the runtime-wide stream as `needs_operator.changed`.
5. Running work and the overview: active plans and epics and a project's running work as the state catalogue defines them; `GET /api/v1/projects` (the projects with running work, each with its `display_name`, `needs_operator`, and counts) and `GET /api/v1/projects/{project_id}` (its active plans with title, phase, activity, `created_at`, `phase_entered_at`, task progress, `needs_operator`, `off_track`, and epic; its active epics; its open entries; its running short landings); titles and names as the stores keep them.
6. Events per scope: the streams `GET /api/v1/events`, `/plans/{plan_id}/events`, `/epics/{epic_id}/events`, `/projects/{project_id}/workspace/events`, `/machine/events`, and `/entries/{entry_id}/events`, one envelope (`event`, `id`, `data` with `at`, `scope`, and the payload of the closed event catalogue, never a model-facing representation or a secret), the resume by `Last-Event-ID` within the event buffer and otherwise one `reset`, no subscription kept; the runtime-wide stream announcing every scope's start and end, every change of running work, and every change of a `needs_operator` summary; an entry's stream announcing the scope it starts and ending, a project onboarding's entry announcing at `project.enrol` the workspace stream to follow; a stream on a web listener checked against its device secret on connect and ended by a revoked device, a closed listener, or the runtime's stop with `stream.closed` and its reason.
7. Ended work: `GET /api/v1/projects/{project_id}/ended`, newest first, paged by a cursor, each plan with its outcome (`merged`, `no_change`, `handed_back`, `returned`, `cancelled`, `abandoned`, `released`, `aborted`), reason, and end time, each epic with its `terminal_reason`; each resolving read-only from its archive to its process and history as it ended, its completion report, decisions, re-planning records, and events; a deleted one left out and resolving to its tombstone.
8. Where the stream and the resources of the runtime exist only in a first form (the runtime-wide stream of Milestone 2), this package brings them to the closed catalogue and envelope; the envelope and the event catalogue as the clients' contract are `pm-api`'s in `pm-mcp-clients` (roadmap Milestone 2), this package their runtime side; the transport of the streams on the web listener is the product's.
9. Schema parity: the members of the status store the specification added (`title`, `created_at`, `phase_entered_at`, `pause`, `terminal_reason`, `setup_aspect`, `onboard_run`, `onboard_steps`) are in its codec, the ones of the packages before this one included, and `WorkflowStatusSchemaParityTest.membersEqualSchema` is re-enabled.

**Done when** (the package's own acceptance): The process of a fixture plan answers every node with its derived state, also `failed`, `not_taken`, and `not_in_plan` with its reason, the path to its position, and its context, after a restart as before; each change of a node is announced on the plan's stream with its `node_id`; the `needs_operator` summary lists each kind in its order, `setup` included, is never stored, and each change of it is announced with `needs_operator.changed`; the overview lists exactly the projects with running work and their active plans, epics, entries, and short landings; every stream carries the one envelope, resumes from `Last-Event-ID` within the buffer and sends `reset` otherwise, and a web stream ends with `stream.closed` and its reason when its device is revoked, its listener closed, or the runtime stopped; ended work lists each outcome newest first by cursor, resolves an ended plan read-only from its archive, and a deleted one to its tombstone; `WorkflowStatusSchemaParityTest.membersEqualSchema` runs without `@Disabled` and passes. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Use cases: UC-PLN-1; UC-PLN-4; UC-CTL-1 (ended work)
- Requirements: PM-ARCH-11; PM-WF-23
- Specification: the sections the first bullet of _What_ names; Runtime Model § Event Catalogue; Store Schemas § The Start of the Current Phase; Filesystem Layout § Archives
- Watch: Runtime Model

## Standing Constraints

None beyond the standing rules named in the First Instruction above.

## Claim Labels

- OBSERVED: the Objective, Deliverables, Done when and Trace above are the package file's own — read at `doc/plans/process-and-overview.adoc` § What, Trace, Done When on `origin/main` at `4617865` (9 bullets; the spec is regenerated from it by cleanup on 2026-10-22)
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: doc/plans/process-and-overview.adoc re-read at 4617865 (last commit touching it: d1d58d2): 9 What bullets, Trace and Done When equal the Deliverables, trace set and acceptance above (regenerated by cleanup; the corpus regeneration diff reads current)
- OBSERVED: the package file and its index row (`open`; depends on PLAN-43 (`phase-sub-workflows`); PLAN-42 (`operator-wait-and-machine-scope`); PLAN-51 (`plan-and-epic-control`) (the activity, the holds)) — read at `doc/plans/process-and-overview.adoc` and `doc/plans/README.adoc` on `origin/main` at `4617865`
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: README.adoc row process-and-overview re-read at 4617865: open; depends on PLAN-43 (`phase-sub-workflows`); PLAN-42 (`operator-wait-and-machine-scope`); PLAN-51 (`plan-and-epic-control`) (the activity, the holds)
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line and the class names of § What, not from a trace matrix — confirm/refute at the trace matrix of `traced-implementation` (verify-at-outline)
  - verdict: unverifiable | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan; 5 OBSERVED surface paths checked at 4617865, all exist
- Verify-first clause: before scoping, list the open pull requests for one that names `process-and-overview`, check in `doc/plans/README.adoc` that every dependency is `done`, and read the roadmap item this package serves for what the code already covers; a refutation of any claim above loops back to re-scope.
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: row process-and-overview is open in the index at 4617865; open PRs (ci pr list): #71 (a dependabot bump) is the only open pull request, and it names no package; doc/plans/process-and-overview.adoc last changed in d1d58d2; the claims above were re-read individually

## Expected Surface

- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/` — production code
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/` — production code
- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/web/` — production code
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-plan.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-runtime/doc/developer/runtime-web.adoc` — developer documentation of the package; created or extended
- OBSERVED: `doc/plans/process-and-overview.adoc` — the package file, deleted when the package is done
- OBSERVED: `doc/specification/filesystem-layout/03-project-store.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/state-catalogue/04-plan-finalize-terminal.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/02-workflow-state-store.adoc` — specification part; the implemented sections deleted

## Dependencies and Sequencing

- Depends on: PLAN-43 (`phase-sub-workflows`); PLAN-42 (`operator-wait-and-machine-scope`); PLAN-51 (`plan-and-epic-control`) (the activity, the holds)
- Blocks: PLAN-55 (`chat`)
- Overlaps with: PLAN-01 (`lock-manager-and-leases`) in `core.store`; PLAN-06 (`store-substrate`) in `core.store`; PLAN-09 (`audit-trail`) in `core.store`; PLAN-11 (`enrolment-store`) in `core.store`; PLAN-12 (`session-log-and-generations`) in `core.store`; PLAN-13 (`workflow-engine`) in `core.store`; PLAN-14 (`operator-and-question-stores`) in `core.store`; PLAN-20 (`job-runner`) in `core.store`; PLAN-24 (`plan-instance`) in `workflow.plan`; PLAN-31 (`slots-usage-and-budgets`) in `core.store`; PLAN-42 (`operator-wait-and-machine-scope`) in `core.store`; PLAN-43 (`phase-sub-workflows`) in `core.store`, `workflow.plan`; PLAN-45 (`project-onboarding-unit`) in `core.store`; PLAN-46 (`machine-onboarding-unit`) in `core.store`; PLAN-47 (`decision-engine`) in `core.store`; PLAN-48 (`start-from-clients`) in `core.store`; PLAN-50 (`recipe-workflows`) in `workflow.plan`; PLAN-51 (`plan-and-epic-control`) in `core.store`; PLAN-53 (`epic-decomposition`) in `core.store`; PLAN-55 (`chat`) in `core.store`, `runtime.web`; PLAN-57 (`epic-auto`) in `core.store`; PLAN-58 (`operator-reports`) in `core.store`; PLAN-59 (`runtime-control`) in `core.store`; PLAN-61 (`token-usage`) in `core.store`; PLAN-62 (`process-designer`) in `core.store`; PLAN-63 (`cleanup`) in `core.store`; PLAN-64 (`removed-concepts`) in `core.store`; PLAN-66 (`worker-activity`) in `core.store`, `runtime.web`; PLAN-68 (`chat-plan-revision`) in `core.store`; every plan through `doc/plans/README.adoc`; the plans of the same module through its log holder, which is not a declared surface
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` and the client contract `pm-api` in `pm-mcp-clients`, which stay untouched — a change there is a pull request in that repository; and `plan-marshall-documentation`, where only a requirement correction or a change of `cross-cutting.adoc` or of a specification that lives there goes, in a pull request there

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-67-process-and-overview.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

# PLAN-26: Role artifacts, role sets and the model caller

epic: pm-mcp-core-m2-m4
workstream: WS-05

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-26-role-sets-and-model-caller.md` and is queued as one row file, `queue/PLAN-26.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r1`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R15, R18.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-role.adoc`, `pm-workflow/doc/developer/workflow-model.adoc`; ADR directory: `pm-core/doc/adr/core-role/`, `pm-workflow/doc/adr/workflow-model/`; layout: workstream WS-10.
- R12 — log message holder: `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`); `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`).

## Objective

Implement the work package `role-sets-and-model-caller` of `doc/plans/role-sets-and-model-caller.adoc`: role artifacts, role sets and the model caller. Serves: Roadmap Milestone 4. Where: pm-core package `core.role`; pm-workflow package `workflow.model`. The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

## Deliverables

The bullets of `doc/plans/role-sets-and-model-caller.adoc` § What, in its words (regenerated at 710da61, after the specification moved into this repository).

1. The role artifact schema (the members `off_track`, whose thresholds may only lower the compiled ones, and `questions`, cut to the harness's `headless.resumability`, both invalid on a `zero_tool` role; `harness` an open `client_id` pattern), the shipped, machine and project sets, validation against the client profiles, and the composition checks.
2. The effective configuration: the harness, model, and effort of a role taken from the shipped artifact, the machine artifact, the machine's `role-assignments.json`, the project artifact, the project's `role-assignments.json`, and the plan's override, each later level overriding only what it names; both digests computed over the effective values; read at every composition, task creation, and offer. The assignment stores, the plan's `overrides.json`, and the model catalogues are written by `machine-onboarding-unit`, `project-onboarding-unit`, and `plan-and-epic-control`; this package reads them through a port, its tests through fixture stores.
3. Validation against the effective values: the reason `model_not_allowed` against the effective model catalogue (the project's, else the machine's; never for the `steward`), no `session_first_not_permitted`, and `schema_invalid` for an `off_track` threshold above the compiled one or an `off_track` or `questions` member on a `zero_tool` role.
4. The one model caller that turns a `job llm` state into queue tasks: Resolve reads the effective configuration; the `judge` and the `steward` skip Qualify; the steward's harness comes from `harnesses.default` of `machine-config.json` and its model from that harness's tier `standard` (`headless.model_tiers`), never from its artifact; Serve has no session path.
5. The shipped set (the `interpret` and `steward` roles among them) is read from `classpath:/roles/`, which the assembly supplies; tests use fixture roles. The default assignment and its resolution `roles.default` are `machine-onboarding-unit`'s.

**Done when** (the package's own acceptance): The validation table of the specification row by row, each reason with a refusing fixture; a role of a class its harness cannot run is `harness_class_not_runnable`; a valid artifact whose effective model the catalogue does not allow is `model_not_allowed`, a `steward` never; each level of the effective configuration overrides only what it names, and a changed assignment or plan override changes both digests; a task of the `judge` and of the `steward` is queued without a qualification record. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-WORK-1, PM-WF-5, PM-CLIENT-2
- Specification: Model Work § Roles & Configuration; Client Profiles § Role Classes of a Harness; Store Schemas § Model Catalogue
- Watch: Model Work; Client Profiles; Module Structure

## Standing Constraints

None beyond the standing rules named in the First Instruction above.

## Claim Labels

- OBSERVED: the Objective, Deliverables, Done when and Trace above are the package file's own — read at `doc/plans/role-sets-and-model-caller.adoc` § What, Trace, Done When on `origin/main` at `7756dfd` (5 bullets; the spec is regenerated from it by cleanup on 2026-10-22)
  - verdict: corroborated | checked_at: 7756dfd6a98067e2e331b40211784ebc0f605c25 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: doc/plans/role-sets-and-model-caller.adoc re-read at 7756dfd (last commit touching it: d1d58d2): 5 What bullets, Trace and Done When equal the Deliverables, trace set and acceptance above (regenerated by cleanup; the corpus regeneration diff reads current)
- OBSERVED: the package file and its index row (`open`; depends on PLAN-19 (`profile-loader`)) — read at `doc/plans/role-sets-and-model-caller.adoc` and `doc/plans/README.adoc` on `origin/main` at `7756dfd`
  - verdict: corroborated | checked_at: 7756dfd6a98067e2e331b40211784ebc0f605c25 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: README.adoc row role-sets-and-model-caller re-read at 7756dfd: open; depends on PLAN-19 (`profile-loader`)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/` exists and holds `package-info.java` and the subpackage `core/role/corpus/` (source manifest, overlay, import and validation of the question corpus, #74 and #80, with its own guide `core-role-corpus.adoc` and ADR directory `core-role-corpus`); the role sets are new in `core/role/` itself and must not collide with that subpackage — directory listing at 7756dfd on 2026-10-22
  - verdict: corroborated | checked_at: 7756dfd6a98067e2e331b40211784ebc0f605c25 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: ls of pm-core core/role at 7756dfd: package-info.java and corpus/ (25 source files); guide core-role-corpus.adoc and ADR directory core-role-corpus exist
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/model/` exists and holds `package-info.java` today, so the package is greenfield apart from its package description — directory listing at 99971f3 on 2026-10-21
  - verdict: corroborated | checked_at: 7756dfd6a98067e2e331b40211784ebc0f605c25 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: ls of pm-workflow/src/main/java/de/planmarshall/workflow/model/ at 7756dfd: package-info.java
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line of the package file, not from a trace matrix — confirm/refute at the trace matrix of `traced-implementation` (verify-at-outline)
  - verdict: unverifiable | checked_at: 7756dfd6a98067e2e331b40211784ebc0f605c25 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan; 9 OBSERVED surface paths checked at 7756dfd, all exist
- OBSERVED: each module that logs has one holder with its own test, which refuses an identifier used twice and one outside the range of its level (INFO 1-99, WARN 100-199, ERROR 200-299): `CoreLogMessages` (5 records), `RuntimeLogMessages` (78), `WorkflowLogMessages` (5); `PmMcpLogMessages` is deleted — read on main at 238c823 on 2026-10-16
  - verdict: corroborated | checked_at: 7756dfd6a98067e2e331b40211784ebc0f605c25 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: static final LogRecord counted at 7756dfd: CoreLogMessages 5, RuntimeLogMessages 78, WorkflowLogMessages 5
- HYPOTHESIS: the package adds INFO, WARN or ERROR messages; its module's log holder is deliberately NOT in the Expected Surface: a record is appended at the next free identifier and a collision is resolved on rebase, as the operator decided for the shared holder on 2026-10-09 and kept for the per-module holders on 2026-10-13 — confirm/refute the need at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: 7756dfd6a98067e2e331b40211784ebc0f605c25 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- HYPOTHESIS: the package makes no HTTP call, so the two HTTP constraints bind only if the trace matrix shows one — confirm/refute at the specification sections of the trace starting set (verify-at-outline)
  - verdict: unverifiable | checked_at: 7756dfd6a98067e2e331b40211784ebc0f605c25 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- Verify-first clause: before scoping, read the roadmap item this package serves for what the code already covers, list the open pull requests for one that names `role-sets-and-model-caller`, and check in `doc/plans/README.adoc` that every dependency is `done`; a refutation of any claim above loops back to re-scope.
  - verdict: corroborated | checked_at: 7756dfd6a98067e2e331b40211784ebc0f605c25 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: row role-sets-and-model-caller is open in the index at 7756dfd; open PRs (ci pr list): #129 (PLAN-85) and #71 (a dependabot bump), none names the package; doc/plans/role-sets-and-model-caller.adoc last changed in d1d58d2; the claims above were re-read individually

## Expected Surface

- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/` — production code
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/model/` — production code
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/role/` — tests; exists (the `corpus` tests), extended beside them
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/model/` — tests; new directory
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only
- HYPOTHESIS: `pm-core/doc/developer/core-role.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-core/doc/adr/core-role/` — ADRs of the package's design decisions; new directory if absent
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-model.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-model/` — ADRs of the package's design decisions; new directory if absent
- OBSERVED: `doc/plans/role-sets-and-model-caller.adoc` — the package file, deleted when the package is done
- OBSERVED: `doc/implementation-watch/client-profiles.adoc` — watch document; the covered items deleted
- OBSERVED: `doc/implementation-watch/model-work.adoc` — watch document; the covered items deleted
- OBSERVED: `doc/specification/client-profiles/01-profile-schema.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/model-work/01-roles-and-configuration.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/12-machine-stores.adoc` — specification part; the implemented sections deleted

## Dependencies and Sequencing

- Depends on: PLAN-19 (`profile-loader`)
- Blocks: PLAN-28 (`task-queue-and-protocol`); PLAN-29 (`worker-launch`); PLAN-34 (`role-qualification`); PLAN-55 (`chat`); PLAN-62 (`process-designer`); PLAN-71 (`plan-instance-versions`); PLAN-73 (`question-continuation`)
- Overlaps with: PLAN-34 (`role-qualification`) in `core.role`, `workflow.model`; every plan through `doc/plans/README.adoc`; the plans of the same module through its log holder, which is not a declared surface
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` and the client contract `pm-api` in `pm-mcp-clients`, which stay untouched — a change there is a pull request in that repository; and `plan-marshall-documentation`, where only a requirement correction or a change of `cross-cutting.adoc` or of a specification that lives there goes, in a pull request there

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-26-role-sets-and-model-caller.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

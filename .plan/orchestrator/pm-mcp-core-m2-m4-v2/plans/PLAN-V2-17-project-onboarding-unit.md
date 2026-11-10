# PLAN-V2-17: The project onboarding unit

epic: pm-mcp-core-m2-m4-v2
workstream: WS-04
origin: PLAN-45 of the epic pm-mcp-core-m2-m4

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-V2-17-project-onboarding-unit.md` and is queued as one row file, `queue/PLAN-V2-17.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r4`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R15, R16, R18, R19, R20.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: the developer file of each package it changes (`{module}/doc/developer/{package}.adoc`); ADR directory: `{module}/doc/adr/{package}/`; layout: workstream WS-10.
- R12 — log message holder: `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`); `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`); `RuntimeLogMessages` (`de.planmarshall.runtime.log`, prefix `PM_RUNTIME`).

## Objective

Implement the work package `project-onboarding-unit` of `doc/plans/project-onboarding-unit.adoc`: the project onboarding unit. Serves: roadmap Milestone 7 (the onboarding of a project as one process); Use Cases, UC-PRJ-1, UC-PRJ-2 (UC-PRJ-3, the release, is `plan-and-epic-control`'s); the onboarding of a project in both clients of `pm-mcp-clients`. Where: pm-workflow (the unit in its own scope per project, the setup's choices as operator waits); pm-core (detection, enrolment writes, the project's values over the machine's); pm-runtime (the onboarding's entry endpoint, the projects of the machine: the list of every project, favourites, recent, most used). The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

## Deliverables

The seven tasks of `doc/plans/project-onboarding-unit.adoc` § Tasks (restructured by the design audit #286; the specification sections each task names are the single statement of what it builds) are carried by deliverables 2 to 8 below: task 1 (the gate and the refusal of an act whose onboarding step is not done) is deliverable 2, task 2 (the unit, detection, entry endpoint, abandonment) is 3, task 3 (the checkout's scripts) is 4, task 4 (the setup, first run only) is 5, task 5 (the consents of model use) is 6, task 6 (the project's values over the machine's, with the revision rule) is 7, task 7 (the projects of the machine) is 8. Deliverable 1 is the introductory sentence of the former § What, not a task; it stays as the list of specification sections the tasks implement. The detail of deliverables 2 to 8 is the epic's elaboration of the tasks (the package file no longer states it); where it differs from the Grounding corrections section, that section is the later reading.

1. Implements Credentials & Enrolment § Onboarding a Project as One Process, State Catalogue § Project Onboarding, § Project Setup, Store Schemas § Project Values over Machine Values, and Runtime Model § The Projects of the Machine with its store Store Schemas § The Operator's Projects (`PM-EXT-8`, `PM-SEC-8`).
2. The gate: the start of a project's onboarding calls the machine-onboarding guard first (`SetupGate.decide(Act.START_PROJECT_ONBOARDING, MachineOnboarding)` in `workflow.setup`, built by PLAN-42 and tested by `SetupGateTest`; this deliverable wires and tests the call and builds no second gate) and is refused while the machine's first onboarding has not reached `onboard.completed` (`machine_not_onboarded` in the setup gate, `onboarding_incomplete` (`409`) over the local API, naming the missing step and both clients).
3. The unit: `project.checkout` to `project.enrol` in a scope of its own per project being onboarded (several at once, the machine scope untouched), then the project setup; detection after the consent kind `inspect_checkout` (it reads the checkout and runs the host tools' version probes, and changes nothing): the remote, base branch, build wrappers, host tools, language servers, and scripts with a check line each; the id proposed from the remote; the enrolment written as `pm-operator enrol` writes it. The onboarding's entry is opened at the top level (`POST /api/v1/project-onboardings`), and its stream announces at `project.enrol` the workspace stream to follow; the `abandon` links of the waits before the enrolment end in `project.abandoned`, which deletes the entry, enrols nothing, and remembers nothing.
4. The checkout's scripts: `project.run-consent` (consent kind `run_checkout_scripts`) answered `yes`, `yes_remember` (for this project), or `no`; a remembered yes kept in the machine's `consents.json` keyed by the project and each script's digest (`ConsentStore` of `core.config`, shipped by PLAN-46: `grant(Consent, revision)`, `Snapshot.isRemembered(kind, projectId, scripts)`; its consents lock is separate from the enrolment's, so there is no joint transaction with `Enrolments.enrol`), never in the enrolment: the entry lists every script it covers with its digest, and `project.run-consent` computes the digest of each script it would run from the checkout and is passed without a question only when every one is listed for this project with that digest, so a script not listed, a changed script, or an entry of another project asks again, and a `yes_remember` replaces the list; a `yes_remember` given before the project has its id is held with the onboarding's entry and written under the confirmed `project_id` in the transaction of `project.enrol`, an onboarding that does not reach its enrolment remembering nothing; the scripts run in `project.probe` as jobs through the job launcher with the confinement of a job, never in the runtime's own process and never without one of the two yes links; _no_ goes on to `project.identity` with what needs the scripts unknown, not asked again in that run.
5. The setup in the specification's order, its choices operator waits served to both clients, no session driving it: `project.setup` calls the setup (`first_run`): `setup.entry`, `setup.prepare-workspace`, `setup.check-operator-files`, which ends with its exit `operator_values` (`setup.operator-values`); `project.operator`, with `project.models` and `project.roles` (`call catalogue` and `call roles` at the level `project`); the setup called again at its entry that starts at `setup.probe-integration-variants` and `setup.verify-credentials` (an invalid variant back to `project.operator` with its findings); `setup.project-settings` (base branch, branch prefix, lanes); `setup.detect-domains`; `setup.discover-modules`; `setup.seed-build-map` (derived from the modules); `setup.select-providers` (the team's: the CI provider from the remote or overridden, code analysis, review bots); `setup.configure-finalize` (the finalize steps and the project's landing variant, one of the variants the plan behaviour axes define (PLAN-V2-43 `machine-settings-and-axes`, staged), validated against the project there, a machine default that does not fit refused with the reason); `setup.probe-merge-queue` with `setup.merge-queue` where one could be enforced; `setup.qualify-consent` and `setup.qualify-roles`; `setup.enrich-consent` and `setup.enrich-architecture`; `setup.commit-changes` (commit, or leave to commit) and `setup.land-changes`; `project.completed` with its report `project.onboarded` the first time and its link `rename`. A step that cannot pass waits in `setup.needs-repair`, whose retry runs it again. The later runs from `project.completed`, `reconcile` and `aspect:<name>` (the setup at its entries `reconcile` and `aspect` with the reconcile states of the Project Setup, and the run of the operator values with the optional posture `plan_defaults.posture`), are left out here: their home is the roadmap item of Milestone 10 that implements the reconcile path of the `project-setup` workflow.
6. Consents of model use: `setup.qualify-consent` and `setup.enrich-consent` (consent kind `model_cost`), their expected use shown in tokens, never as money (`token-usage`), asked only where roles need grading or modules a description; _no_ skips the step with its reason, to be run later, and the setup goes on (the project ready without descriptions).
7. The project's values over the machine's: `project.operator` shows the operator's values only (the model language, the allowed models, the role assignment, the method and asking axes, the integration variant and credential of each provider, the commit identity, the diagram format in model text (`diagrams.format`, Store Schemas 08 lines 74-75; settled 2026-11-10: included), the cleanup durations of its plans, epics, and logs, proposed from the machine's `cleanup` and read by `cleanup`), each proposed from the machine and recorded for the project only when changed; the team's values (the landing axis, the providers' declaration) stay in the setup and `.marshall/config/`; `project_defaults` as the machine's value of the project's operator keys, one rule for all. A change of an operator value carries the revision it was made against and is refused with `conflict` and the current value when another change came first (the rule of `SettingsConflict` in `core.config`, shipped by PLAN-46: the refusal carries the current revision and the current value; CLI & Security § Concurrent Changes of a Setting).
8. The projects of the machine: `GET /api/v1/projects?all`, every enrolled project with its `display_name`, its remote, whether it is ready, its `needs_operator` summary, its counts of active plans and epics, its last activity, and whether it is released; favourites and their order in the machine store `explorer.json`, written by `PUT /api/v1/explorer/favourites` from either client; recent and most used from the operator's acts in a project.

**Done when** (the package file's Done When is a Watch bullet and a Tests bullet; the detail below elaborates the Tests bullet, and the epic adds the Sonar clause): Watch: PM-WATCH-CLI-23 (unenrol never strands live work, and a store never changes its root), PM-WATCH-CLI-25 (the lifecycle gate is a release constant, and only a pilot passes a closed gate) and PM-WATCH-CLI-29 (a project release ends its scopes `released`, and re-enrolment reopens it) hold. Tests: A test onboards a fixture checkout from `project.checkout` to a ready project without a session, and one shows its start refused while the machine's onboarding is not complete; a test shows a refused consent leaving the checkout unread, a refused script consent going on without the scripts and not asked again in the run, and a remembered one kept in `consents.json` only and written at `project.enrol`, none remembered by an onboarding that does not reach it; a remembered script consent passes the state only for the same project and the same digests, a changed script, an added script, and the entry of another project each asked again; a test abandons an onboarding before its enrolment, leaving no entry, no enrolment, and no remembered consent; a test walks the setup's states in their order, the project's allowed models and role assignment set through `project.models` and `project.roles`, refuses a landing variant that does not fit the project at `setup.configure-finalize`, and skips the qualification and the description on _no_ with their reasons; a test shows an accepted machine proposal recording nothing for the project and a changed one recorded, and a change against an older revision refused with `conflict` and the current value; the project's cleanup durations are proposed from the machine's and recorded only when changed; `GET /api/v1/projects?all` lists every enrolled project, released ones included, and the favourites keep their order; `pm-operator enrol` still enrols as a direct verb with its arguments as the answers. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Use cases: UC-PRJ-1, UC-PRJ-2; the consent kinds of Consents
- Requirements: PM-EXT-8; PM-SEC-8; PM-EXT-6
- Specification: the sections the tasks name (the former first bullet of _What_ listed the same sections); CLI & Security § Concurrent Changes of a Setting; Store Schemas § Consent Before Acting; Credentials & Enrolment § Enrolment, Lifecycle Gate & Release; Domain Extensions § Setup State Machine; Store Schemas § Enrolment Store
- Watch: Credentials & Enrolment (PM-WATCH-CLI-23, PM-WATCH-CLI-25, PM-WATCH-CLI-29)

## Standing Constraints

None beyond the standing rules named in the First Instruction above.

## Claim Labels

- OBSERVED: the Objective, Deliverables, Done when and Trace above are the package file's own — read at `doc/plans/project-onboarding-unit.adoc` § What, Trace, Done When on `origin/main` at `4617865` (8 bullets; the spec is regenerated from it by cleanup on 2026-10-22)
  - verdict: contradicted | checked_at: f9e7011fb6a1ae42f6afad1f842ca1488258e19f | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: yes | evidence: rescoped 2026-11-10: absorbed by the Grounding corrections section and the replaced deliverable, constraint and surface text of the spec. doc/plans/project-onboarding-unit.adoc on origin/main has Tasks (7), no section What and no 8 bullets; Done When is three watches plus one Tests bullet; the spec's D2 to D8 detail is not in the file and the spec does not account for the restructure
- OBSERVED: the package file and its index row (`open`; depends on PLAN-42 (`operator-wait-and-machine-scope`); PLAN-46 (`machine-onboarding-unit`)) — read at `doc/plans/project-onboarding-unit.adoc` and `doc/plans/README.adoc` on `origin/main` at `4617865`
  - verdict: contradicted | checked_at: f9e7011fb6a1ae42f6afad1f842ca1488258e19f | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: yes | evidence: the row is still open (README.adoc:108-112) but Depends on is now only machine-onboarding-unit; operator-wait-and-machine-scope is gone because PLAN-42 shipped (#203); the spec keeps PLAN-42 as already met
- OBSERVED: the enrolment store, lifecycle gate and integrity shipped with PLAN-11 (#30): `core.store` (`EnrolmentStore`, `ProjectEnrolment`, `LifecycleGate`) and the new package `runtime.enrolment` — directory listing at 99971f3 on 2026-10-21
  - verdict: corroborated | checked_at: f9e7011fb6a1ae42f6afad1f842ca1488258e19f | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: EnrolmentStore, ProjectEnrolment, LifecycleGate in core.store; Enrolments, EnrolmentCheck and related classes in runtime.enrolment with tests
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: contradicted | checked_at: f9e7011fb6a1ae42f6afad1f842ca1488258e19f | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: yes | evidence: rescoped 2026-11-10: absorbed by the Grounding corrections section and the replaced deliverable, constraint and surface text of the spec. stale premises: pm-core core/config is no longer package-info only (ConsentStore, RoleAssignmentsStore and more from PLAN-46); workflow/setup holds SetupGate and MachineOnboarding; core-config.adoc and workflow-setup.adoc exist; runtime/workspace is still WorkspaceConfinement only; the spec Expected Surface still says package-info only and to be created
- Verify-first clause: before scoping, check that the index no longer says `blocked`, that the use cases are agreed, and which of the packages this one restructures have shipped since; a refutation loops back to re-scope.
  - verdict: corroborated | checked_at: f9e7011fb6a1ae42f6afad1f842ca1488258e19f | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: README row is open not blocked (README.adoc:110); UC-PRJ-1/2/3 are AGREED in the documentation repository; PLAN-42 (#203) and PLAN-46 (#233) shipped; machine-onboarding-unit is still open

## Expected Surface

- OBSERVED: `doc/plans/project-onboarding-unit.adoc` — the package file, deleted when the package is done
- OBSERVED: `doc/specification/credentials-and-enrolment/02-enrolment-and-project-lifecycle.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/domain-extensions/04-setup-state-machine.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/state-catalogue/06-setup-epic-other.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/state-catalogue/15-project-onboarding.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/08-project-configuration-local.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/11-enrolment-store.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/12-machine-stores.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/model-work/01-roles-and-configuration.adoc` — trace only; screener repeated runs change the tokens shown before model_cost consent (D6)
- OBSERVED: `doc/specification/phase-workflows/05-finalize-review-metrics-descriptor.adoc` — trace only; the code scanning provider makes the provider list of D5 open-ended
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/enrolment/Enrolments.java` — project.enrol writes the enrolment as pm-operator enrol does; the consent write joins its transaction (D3, D4)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/QualificationSettingsStore.java` — writes local-config.json settings.qualification, "the project's onboarding writes it"; D7 revision/conflict
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/job/ProbeCandidate.java` — Kind.SCRIPT and CheckoutRoot for the checkout-script probe jobs (D3, D4); consumed, extended only if needed
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/config/` — not package-info only: `ConsentStore`, `ConsentGate`, `ConsentKind`, `Consent`, `ConsentScript`, `RoleAssignmentsStore`, `ModelCatalogueStore`, `SettingsConflict` and their codecs shipped with PLAN-46 (#233); consumed by deliverables 4 to 7, extended by deliverable 7 (the project's values over the machine's) only where a type is missing
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/setup/` — holds `SetupGate` and `MachineOnboarding` (PLAN-42, consumed by deliverable 2), not package-info only; the project.* and setup.* states are `.pmwf` default-unit content of `plan-marshall-mcp` (last section below), so this repository gets only the framework-free primitives and fixtures
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/enrolment/EnrolmentsTest.java` — pm-operator enrol still enrols as a direct verb; enrol transaction extended
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/store/QualificationSettingsStoreTest.java` — project value change against an older revision refused with conflict (D7)
- OBSERVED: `pm-workflow/doc/developer/workflow-setup.adoc` — exists (PLAN-42); extended (R06); the ADR directory `pm-workflow/doc/adr/workflow-setup/` exists, a new ADR takes the next free number
- OBSERVED: `pm-core/doc/developer/core-config.adoc` — exists (PLAN-46); extended (R06); the ADR directory `pm-core/doc/adr/core-config/` exists (001 to 003), a new ADR takes the next free number
- HYPOTHESIS: `pm-runtime/doc/adr/runtime-workspace/` — ADR directory still does not exist; `pm-runtime/src/main/java/de/planmarshall/runtime/workspace/` holds only `WorkspaceConfinement` (the path-confinement entry, not the projects of the machine), so deliverable 8 needs a new `pm-runtime` package with its own developer file and ADR directory, named at the outline; `explorer.json` belongs to `pm-core` `core.store`; the `/api/v1` routes are `pm-mcp-server` (plan-marshall-mcp)
- HYPOTHESIS: the log holder of each module the plan logs through (`WorkflowLogMessages`, `CoreLogMessages`, `RuntimeLogMessages`) and its committed generated listing `doc/log-messages/pm-<module>.adoc` (ADR `doc/adr/019`) — only if the plan adds a record; the holder test compares the listing byte for byte and fails until it is regenerated

## Re-grounding Record (2026-10-26, HEAD `f4a1bd07`)

Written by `pm-mcp-core-m2-m4/cleanup`, measured against `main` at `f4a1bd07`. This section is the **latest** reading; the earlier section below stays as the audit record, and **where they differ, this section is the later reading**.

- Claims re-read: 5: 4 corroborated, 1 contradicted (re-scoped by this section), 0 unverifiable. Verdicts are stamped on the claims above (`checked_at: f4a1bd07`).

### Premises corrected (contradicted at HEAD, re-scoped here)

- Claim 3: still wrong as at `88ec9c70`: `runtime/workspace` holds only `WorkspaceConfinement` and `package-info`; `core/config` and `workflow/setup` are package-info only; `workflow/unit` is loaders only; no `consents.json` or `explorer.json` code.

### Surface

- Added to the Expected Surface above: `runtime/job/`, `model-work/01`, `phase-workflows/05`.
- wrong: `pm-runtime/.../runtime/workspace/` is a path-confinement entry, not "the projects of the machine"; a new package is needed. `pm-workflow/.../workflow/unit/` is loaders only (the onboarding unit is default-unit content of `plan-marshall-mcp`, which holds no `.pmwf` yet). `pm-core/doc/adr/core-store/` is annotated "new directory if absent"; it exists (next 046). The guides `core-store.adoc`, `workflow-unit.adoc`, `runtime-enrolment.adoc`, `runtime-workspace.adoc` exist (extend); `core-config.adoc` and `workflow-setup.adoc` are absent; the ADR directories `runtime-workspace` and `runtime-web` do not exist.

### Specification changes carried (not yet in the Deliverables above)

- **#181 (`7f1d6a99`) — Store Schemas 08 § Project Values over Machine Values and the local-config schema: a new operator-owned key `diagrams.format` (`text` or `mermaid`).** The table's bullet says `project.operator` shows every operator aspect, but **D7's enumerated list (copied from State Catalogue 15, line 89) omits it** and no onboarding state writes it (`language.choose` writes only `language.interface` and `language.model`); Store Schemas 12 gives the machine-config member the default `text`. D7 and its Done-when ("an accepted proposal records nothing, a changed value is recorded") must either include `diagrams.format` or state its exclusion: a spec correction of State Catalogue 15 or Store Schemas 08 is needed either way (a documentation decision).
- #185 — Store Schemas 11 § Unreadable Store: `harness_store_unreadable` refuses every worker launch of a `harnesses.json` whose entries lack required members; recovery is a new enrolment of the named client that drops other clients' entries; touches no D1 to D8 sentence.
- #186 — Phase Workflows 05: the provider `github-code-scanning` with `project.json integrations.github-code-scanning.tools` (non-empty, a team value), reusing the github integration's variant and credential; State Catalogue 06 (`setup.select-providers`) is not updated, so D5 gains no behaviour but the provider list is open-ended. #189 — screener repeated runs: the expected tokens of D6 grow for that topic (`role-qualification`'s number). #187 — the worker read set: probes keep the open read set; enrolment `host_tools[].executable_path` becomes a worker read grant.

### Shipped or superseded since the last grounding

- #185: `QualificationSettingsStore` reads and writes `local-config.json` `settings.qualification` with `Snapshot(settings, revision)`, refuses a stale revision with `StoreRefusal.Outcome.CONFLICT`, never creates the file ("the project's onboarding writes it") and copies other members through; `StoreRefusal.conflict` carries only revision numbers, so D7's "conflict and the current value" needs a re-read on top. ADR numbers: `core-store` 046, `runtime-worker` 010.

### Open questions for the outline

- `diagrams.format` in `project.operator` (above); does `setup.select-providers` offer `github-code-scanning` and its `tools` list; the earlier questions stand (`ci.whoami` has no module, the git detection seam without JGit in `pm-runtime`, which store API joins the consent write and the enrolment write at `project.enrol`, the quorum/skip_decisions split with `decision-quorum-and-calibration`).

## Re-grounding Record (2026-10-25, HEAD `88ec9c70`)

Written by `pm-mcp-core-m2-m4/cleanup`, measured against `main` at `88ec9c70` (the plans #172, #169 and #164 and the specification pull requests #141, #165, #168, #171, #173, #174 and #175 have landed since the grounding of `4617865`). The Objective, Deliverables, Dependencies and Hand-Off above are the earlier text and stay as the audit record; **where this section and that text differ, this section is the later reading**, and the outline records the departure. Spec-sourced items below are the specification's own content, not inferences of the orchestrator; every statement about the repository was read at the cited place.

- Claims re-read: 5: 4 corroborated, 1 contradicted (re-scoped by this section), 0 unverifiable. Verdicts are stamped on the claims above (`checked_at: 88ec9c70`).

### Premises corrected (contradicted at HEAD, re-scoped here)

- Claim 3: One named path is misattributed: pm-runtime runtime/workspace holds only WorkspaceConfinement and its package-info and the module-structure row say it is the path-confinement entry (PM-SEC-2), not the projects of the machine; the explorer store and consents belong to pm-core store per the module-structure pm-core row. core/config and workflow/setup are package-info only as claimed and the 9 OBSERVED paths exist

### Surface

- Added to the Expected Surface above: `pm-workflow/doc/developer/workflow-setup.adoc`, `pm-core/doc/developer/core-config.adoc`.
- wrong: pm-runtime runtime/workspace/ is the path-confinement entry (WorkspaceConfinement, module-structure row) and cannot host the projects of the machine: create a new pm-runtime package for it with its developer file; the explorer.json and consents.json stores belong to pm-core core/store; GET /api/v1/projects?all, PUT /api/v1/explorer/favourites and POST /api/v1/project-onboardings are routes of pm-mcp-server (plan-marshall-mcp), outside this repository and not named as adjacent in the spec
- understated: Add developer files pm-workflow/doc/developer/workflow-setup.adoc and pm-core/doc/developer/core-config.adoc (core/config and workflow/setup are package-info only, no developer file or ADR directory) and note R10 readers of store-schemas/12: pm-core AuditSchemaParityTest and pm-runtime CredentialStoreMoveRecordParityTest must keep their blocks
- unresolvable: project-setup.pmwf, the project onboarding unit with project.* and setup.* states are compiled-only default units of plan-marshall-mcp; this repository can only deliver primitives, store and fixtures

### Specification changes carried (not yet in the Deliverables above)

- #171 — model-work/01 Judge Instrument Check and Constant Baseline: the judge check tokens are part of the expected tokens the grading shows before consent, and a grading is refused judge_unchecked without a…: Deliverable 6 (setup.qualify-consent expected tokens) must include the judge check's tokens and setup.qualify-roles must surface the judge_unchecked refusal; the check itself is role-qualification's. Add model-work/01 judge-instrument-check to the trace; no new surface path.

### Shipped or superseded since the last grounding

- partly fixed: enrolment writes: Enrolments.enrol(ProjectEnrolment, pilot), unenrol, rebaseline with EnrolmentStore sidecar pair (50109697 #30) exist: the 'written as pm-operator enrol writes it' half of Deliverable 3 needs no new store
- substrate for the scripts' confinement: JobConfinementComposer, JobEnvironmentComposer and the job runner (7903bf23 #161 PLAN-27) exist, so project.probe runs scripts as confined jobs without a new launcher; core/config and workflow/setup stay package-info only

### Open questions for the outline

- setup.verify-credentials and setup.probe-integration-variants call ci.whoami and per-integration probes (domain-extensions/04 lines 152 and 287) but no ci.whoami exists (GitLabClient javadoc only) and the CI contract has no module or work package; not named as a dependency
- setup.probe-qualification and setup.qualify-roles also list quorum declarations without a calibration record and calibrate them keyed by qualification.skip_decisions (domain-extensions/04 lines 170-172, state-catalogue/06 line 185); Deliverables 5-7 name only roles, modules and no skip_roles or skip_decisions keys, and the package decision-quorum-and-calibration owns calibration: the split is unstated
- project.checkout and detection read the remote and base branch, but pm-runtime depends on pm-core and pm-api only and JGit lives in pm-provider-git; the spec does not say through which seam (a pm-core port, a git job, or a pm-workflow primitive) detection runs
- Consent remembered under the confirmed project_id is to be written in the transaction of project.enrol, but Enrolments.enrol writes only the enrolment and sidecar pair and consents.json is a separate machine store (module-structure puts it in pm-core): which store API joins the two writes is unspecified (R14)

## Dependencies and Sequencing


- **Landing note (2026-10-29):** PLAN-46 landed (#233): `ConsentStore`, the project-scoped consent kind `RUN_CHECKOUT_SCRIPTS` and the `ConsentScript` digests exist, which answers the open question which store API to use; the consents lock is separate, so there is no joint transaction with `Enrolments.enrol`; a concurrent change of a project value uses `SettingsConflict`.
- Depends on: PLAN-V2-83 (`skills-conformance-foundation`: the dependencies and rule R20); and then: PLAN-42 (`operator-wait-and-machine-scope`, shipped #199 and #203: met); PLAN-46 (`machine-onboarding-unit`, part 1 of 3, shipped #233: met); PLAN-V2-43 (`machine-settings-and-axes`, staged: the last part of `machine-onboarding-unit`, the one package the index row names as this package's dependency; it lists this plan in its Blocks); PLAN-V2-12 (`ci-contract-module`) (`ci.whoami` for `setup.verify-credentials` and the probe variants; operator decision 2 of 18: the contract is the module `pm-provider-ci`); PLAN-V2-05 (`derived-facts-store-operations`) (the Derived Facts Store: records, kind registry, derivation and staleness rules; operator decision 12 of 18)
- Blocks: PLAN-V2-24 (`start-from-clients`); PLAN-V2-21 (`plan-and-epic-control`); PLAN-V2-74 (`cleanup`); PLAN-V2-22 (`start-source-intake`)
- Overlaps with: the `core.store` plans; PLAN-11's `runtime.enrolment`

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-17-project-onboarding-unit.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Placement of endpoints and default units (operator decision 2026-11-09)

Companion package outside, logic stays here: the `.pmwf` default units of the onboarding unit (model-facing content under `workflows/`) are not built here: `CLAUDE.md` forbids `workflows/` in a JAR of this repository and `pm-workflow` carries no unit. This plan keeps the framework-free parts (primitives, verifier, stores, fixtures); the units are owed to `plan-marshall-mcp` (`default-units-and-drift-tests` or a package of its own), with a dependency edge from there to this plan.

## Grounding corrections (cleanup 2026-11-10, main f9e7011f)

Claim 0: `doc/plans/project-onboarding-unit.adoc` on origin/main (restructured by the design audit #286) has no section What and no 8 bullets. It has 7 numbered Tasks, a Trace, and a Done When of three watches (PM-WATCH-CLI-23, -25, -29) plus one Tests bullet. The detail of deliverables 2 to 8 and the long Done-when of this spec are the epic's elaboration, not the package file's words, and the spec did not account for the restructure. The mapping:
- Task 1 (the gate, and the refusal of an act whose onboarding step is not done) is deliverable 2.
- Task 2 (the unit, detection, entry endpoint, abandonment) is deliverable 3.
- Task 3 (the checkout's scripts) is deliverable 4.
- Task 4 (the setup, first run only) is deliverable 5.
- Task 5 (the consents of model use) is deliverable 6.
- Task 6 (the project's values over the machine's, with the revision rule) is deliverable 7.
- Task 7 (the projects of the machine) is deliverable 8.
- Deliverable 1 was the introductory sentence of the old § What, not a task. It is kept as the list of specification sections.

Nothing is dropped and nothing is added. The Done-when gains the Watch bullet of the package file (PM-WATCH-CLI-23, -25, -29), which this spec lacked.

Claim 1 stays absorbed, with one addition. The index row is `open`, depends only on `machine-onboarding-unit` (`README.adoc:108-112`), and PLAN-42 (shipped #199, #203) and PLAN-46 (shipped #233, part 1 of 3) are met. The rest of `machine-onboarding-unit` is PLAN-V2-43 `machine-settings-and-axes` (staged). That plan carries the landing variants and the concurrent-change rule that deliverables 5 and 7 cite, and its queue spec lists this plan under Blocks. The Dependencies line now names it.

Claim 3: the HYPOTHESIS "package-info only" is false for two of the three packages.
- pm-core `core/config` holds `ConsentStore` (`grant` :180, `Snapshot.isRemembered` :85), `ConsentGate`, `ConsentKind` (`INSPECT_CHECKOUT`, `RUN_CHECKOUT_SCRIPTS`, `MODEL_COST`, all remembered per project), `Consent`, `ConsentScript`, `RoleAssignmentsStore`, `ModelCatalogueStore`, `SettingsConflict` (carries the current revision and value) and the codecs, with tests. ADR directory `pm-core/doc/adr/core-config/` has 001 to 003.
- pm-workflow `workflow/setup` holds `SetupGate` and `MachineOnboarding`. `SetupGate.decide(Act.START_PROJECT_ONBOARDING, ...)` (`:107`; `START_PROJECT_ONBOARDING` at `:56`) already refuses with `machine_not_onboarded` and 409. `decideStep` (`:124`) already refuses with `onboarding_incomplete`. `SetupGateTest` covers both (`:62-67`, `:90`). So deliverable 2 wires and tests the call; it builds no gate. `pm-workflow/doc/adr/workflow-setup/001` exists.
- The developer files `pm-core/doc/developer/core-config.adoc` and `pm-workflow/doc/developer/workflow-setup.adoc` exist (they are extended, not created).
- `runtime/workspace` is still only `WorkspaceConfinement` (the path-confinement entry). The projects of the machine (deliverable 8) need a new `pm-runtime` package, and `pm-runtime/doc/adr/runtime-workspace/` still does not exist. `explorer.json` belongs to pm-core `core.store`. The `/api/v1` routes are `pm-mcp-server` (operator decision 2026-11-09, last section).

Expected Surface entries replaced: the two "package-info only today" lines, the two "to be created" developer guides, and the `runtime-workspace` ADR line. A line for the generated log listing is added (ADR `doc/adr/019`: a record change regenerates `doc/log-messages/pm-<module>.adoc`).

The deliverable text replaced: the Deliverables introduction line, the deliverable 2 guard reference, the deliverable 4 consent-store reference ("the store of `machine-onboarding-unit`" is `ConsentStore`, shipped), the deliverable 5 variant reference, the deliverable 7 revision-rule reference, the Done-when opener, the Trace Specification line (and the Watch line added), and the Dependencies line.

Carried unchanged from the 2026-10-26 record: `diagrams.format` (Store Schemas 08, lines 74-75) is still missing from the list in deliverable 7. State Catalogue 15 line 89 still omits it.

Asks for the operator at refine: (1) include `diagrams.format` (an operator-owned key, Store Schemas 08 lines 74-75, #181) in deliverable 7 and its Done-when and correct State Catalogue 15 in this plan's pull request, or state its exclusion (recommended: include; Store Schemas 08 says one rule decides every aspect and the corrected line is in this repository); (2) seven tasks and eight deliverables are far over six, and the 2026-11-09 placement decision says the `.pmwf` units of deliverables 3 and 5 are `plan-marshall-mcp` content: split deliverable 8 (the projects of the machine: a new `pm-runtime` package, `explorer.json` in `core.store`, the routes in the assembly) into its own staged plan with an edge from this one (recommended: split, using the next free PLAN-V2 number; deliverables 2 to 7 then remain, with 2, 4 and 7 mostly wiring of shipped types; then name at the outline which framework-free parts of deliverables 3 and 5 are built here).

**Settled asks (2026-11-10, orchestrator, confidence above 90% after reading the code and specifications at origin/main f9e7011f; the operator asked for every question above 90% to be settled):**
- `diagrams.format` is included in deliverable 7 and its Done-when (an operator-owned key of Store Schemas 08, lines 74-75, already a member of `local-config.schema.json` and `machine-config.schema.json`), and State Catalogue 15 (`project.operator`, line 89), which omits it, is corrected in this plan's pull request; the file is in this repository and already in the Expected Surface.
- Splitting deliverable 8 (the projects of the machine) into a plan of its own (settled 2026-11-10, operator, Q10, answer "2: read it as a soft-cap"; the orchestrator had recommended the split at 70%): not split. The scope-bloat guard (about six deliverables) is read as a soft cap, and the recorded rationale for proceeding with eight deliverables is: deliverables 2, 4 and 7 are mostly wiring of types that shipped with PLAN-42 and PLAN-46, the `.pmwf` units of deliverables 3 and 5 are `plan-marshall-mcp` content (placement decision of 2026-11-09), and deliverable 8 is self-contained. The outline still records whether the plan proves oversize and cuts it then, with deliverable 8 as the natural seam (a new `pm-runtime` package, `explorer.json` in `core.store`, the routes in the assembly).

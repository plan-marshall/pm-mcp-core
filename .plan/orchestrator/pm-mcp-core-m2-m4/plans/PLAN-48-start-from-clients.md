# PLAN-48: Starting work from the clients: the bootstraps and the clients' side

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-48-start-from-clients.md` and is queued as one row file, `queue/PLAN-48.json`,
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
- R12 — log message holder: `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`); `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`).

## Objective

Implement the work package `start-from-clients` of `doc/plans/start-from-clients.adoc`: starting work from the clients: the bootstraps and the clients' side. Serves: roadmap Milestone 7 (the plan bootstrap with the sources `description`, `issue`, and `lesson`), Milestone 10 (the sources `local_changes`, `pr`, and `documents`), Milestone 12 (the epic bootstrap); Use Cases, UC-STA-1 to UC-STA-4, UC-STA-7 to UC-STA-9; starting work in both clients of `pm-mcp-clients`. Where: pm-workflow (the bootstrap and source units, the end of the session-driven units, the static verifier); pm-core (the project's value `epic.parallelization_scope`). PLAN-75 (#85) split the former package on 2026-10-17 (21 bullets): this package keeps 9 (the bootstraps and the clients' side), and the other 12 are `start-source-intake` (PLAN-76), on which this package now depends in addition. The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

## Deliverables

The bullets of `doc/plans/start-from-clients.adoc` § What at `c281f6c`, in its words.

1. Implements Workflow DSL § All Interaction in the Operator's Clients; State Catalogue § Starting from the Clients with the bootstraps `bootstrap-plan` and `bootstrap-epic`, the Source Units, Issues by Number or URL, and Asked When Needed (`PM-WF-10`, `PM-ARCH-11`).
2. The operator's clients only: the bootstraps of a plan and an epic run as units of operator waits that the clients drive; the session-driven units, the entry session, and the channel `session` of answers go from the code (`OutcomeClass`, the static verifier's rules for session units and their fixtures), the channels of an answer being `web`, `cli`, and `quorum`.
3. The setup gate: `start.open`, `start-epic.open`, and the re-check of `start.create-scope` call the machine-onboarding guard of `operator-wait-and-machine-scope` first, so no plan or epic starts while the machine's first onboarding has not reached `onboard.completed` (`machine_not_onboarded`, naming the terminal client and the web app), beside the rest of the setup gate of `start.open`. The move at creation of `start-source-intake` builds on these steps.
4. Sources as a list: `start.sources` and `start-epic.sources`, offering only the kinds that combine with those already listed (a pull request or the local changes only with descriptions and documents, never both); the description stored by the main workflow; the source units `source-documents`, `source-issue` (number or URL, always stored as its URL, the structured fields; another repository on the operator's grant, whose _no_ goes to `source-issue.failed`; without a credential for the forge a public issue is read and another fails naming the credential), and the existing ones of lesson, local changes, pull request, and epic row, each writing into the source directory. The rate limits, documents copy or move, the source directory, the screening, and failing closed of `start-source-intake` build on these source units.
5. The other sources (UC-STA-7 to UC-STA-9): pull request, lesson, and local changes as sub-workflows storing into the source directory; an issue's and a pull request's URL stored. A pull request's changed files are stored as its head and marked untrusted unless the operator authored it, never read by a model as instructions, the plan's own gates applying to them; its title and body screened as an issue's. A lesson: `source-lesson.resolve` with the premise check; a lesson claimed meanwhile, retired, or in a damaged store back to `source-lesson.choose` with the cause; a premise that no longer holds to the operator wait `source-lesson.premise` with the evidence, where _use it anyway_ stores the lesson with the evidence for the plan, _retire_ retires it in the lesson store, and _remove_ leaves it as it is, both adding nothing. The local changes: `source-local-changes.confirm` with _not now_ back to `start.sources`, a checkout without changes failing the source with that cause; the plan takes the land lane and lands them by the project's landing. The rate limits, the source directory, the screening, failing closed, and the claimed lesson of `start-source-intake` build on these sub-workflows (`source-lesson.*`, `source-local-changes.*`).
6. The start's end: the landing is not asked at the start; while the project's landing in force is `landing.files`, `start.sources` does not offer the local changes and shows the reason naming the project's setting `landing` (the landing variants are `machine-onboarding-unit`'s); `start.create` re-checks what creation needs (`landing.files` refused for a plan from local changes) and returns to `start.ready` with the cause where something changed meanwhile.
7. Chat while starting: `start.sources` and `start-epic.sources` leave room for the choice Chat that `chat` adds on top of this package, and the entry's `chat/` keeps its transcript (moved at the creation, deleted with an abandoned entry, never a source the plan reads); the chat itself and its one change, the description through `start.store-description`, are that package's.
8. Asked when needed: posture and base branch leave the start: where neither the plan's own settings, the project (`plan_defaults.posture`), nor the machine sets them, `init.classify` asks both in the one operator wait `init.options`; whether the outline is reviewed is the plan's asking variant's (`outline_review`). `parallelization_scope` becomes the project's operator value `epic.parallelization_scope` in `local-config.json` (machine default 1), which a new epic takes and which is never asked; it is raised through _Next plan_ (`epic-resource-and-release`) or the project's settings.
9. Where `entity-addressing`, `plan-instance`, or `project-units-and-invariants` have already implemented the session-driven bootstrap, this package restructures them.

**Done when** (the package's own acceptance): No unit is driven by a session, and no answer has the channel `session`; a test per source kind stores it in the source directory; a plan and an epic start from several sources and keep them after creation; a pull request's changed files are never read as instructions; a start of a plan and of an epic is refused `machine_not_onboarded` while the machine's onboarding is not complete; each branch of a lesson, unavailable and a premise that no longer holds, ends as specified; posture and base branch are asked in `init.options` only where no level sets them, and `parallelization_scope` never; `start.sources` does not offer the local changes while the project's landing is `landing.files`, with the reason, and `start.create` refuses `landing.files` for a plan from local changes. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Use cases: UC-STA-1 to UC-STA-4, UC-STA-7 to UC-STA-9
- Requirements: PM-WF-10; PM-ARCH-11
- Specification: the sections the first bullet of _What_ names; Phase Workflows § Plan Source Contracts; Model Work § Epic Workflow; Store Schemas § Lesson Record

## Standing Constraints

None beyond the standing rules named in the First Instruction above.

## Claim Labels

- OBSERVED: the Objective, Deliverables, Done when and Trace above are the package file's own — read at `doc/plans/start-from-clients.adoc` § What, Trace, Done When on `origin/main` at `7756dfd` (9 bullets; the spec is regenerated from it by cleanup on 2026-10-22)
  - verdict: corroborated | checked_at: 7756dfd6a98067e2e331b40211784ebc0f605c25 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: doc/plans/start-from-clients.adoc re-read at 7756dfd (last commit touching it: c281f6c): 9 What bullets, Trace and Done When equal the Deliverables, trace set and acceptance above (regenerated by cleanup; the corpus regeneration diff reads current)
- OBSERVED: the package file and its index row (`open`; depends on PLAN-42 (`operator-wait-and-machine-scope`); PLAN-46 (`machine-onboarding-unit`); PLAN-45 (`project-onboarding-unit`); PLAN-76 (`start-source-intake`) (coupling: its rate limits, documents copy or move, source directory and move at creation, screening, failing closed, and claimed lesson build on the setup gate, the sources as a list, and the other sources built here; recorded on this edge, not as a reverse dependency)) — read at `doc/plans/start-from-clients.adoc` and `doc/plans/README.adoc` on `origin/main` at `7756dfd`
  - verdict: corroborated | checked_at: 7756dfd6a98067e2e331b40211784ebc0f605c25 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: README.adoc row start-from-clients re-read at 7756dfd: open; depends on PLAN-42 (`operator-wait-and-machine-scope`); PLAN-46 (`machine-onboarding-unit`); PLAN-45 (`project-onboarding-unit`); PLAN-76 (`start-source-intake`) (coupling: its rate limits, documents copy or move, source directory and move at creation, screening, failing closed, and claimed lesson build on the setup gate, the sources as a list, and the other sources built here; recorded on this edge, not as a reverse dependency)
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line of the package file and the class-level surfaces of the former specs, not from a trace matrix — confirm/refute at the trace matrix of `traced-implementation` (verify-at-outline)
  - verdict: unverifiable | checked_at: 7756dfd6a98067e2e331b40211784ebc0f605c25 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan; 8 OBSERVED surface paths checked at 7756dfd, all exist
- OBSERVED: each module that logs has one holder with its own test, which refuses an identifier used twice and one outside the range of its level (INFO 1-99, WARN 100-199, ERROR 200-299): `CoreLogMessages` (5 records), `RuntimeLogMessages` (78), `WorkflowLogMessages` (5) — `static final LogRecord` counted on `origin/main` at `7756dfd`
  - verdict: corroborated | checked_at: 7756dfd6a98067e2e331b40211784ebc0f605c25 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: static final LogRecord counted at 7756dfd: CoreLogMessages 5, RuntimeLogMessages 78, WorkflowLogMessages 5
- HYPOTHESIS: the package adds INFO, WARN or ERROR messages; its module's log holder is deliberately NOT in the Expected Surface: a record is appended at the next free identifier and a collision is resolved on rebase, as the operator decided for the shared holder on 2026-10-09 and kept for the per-module holders on 2026-10-13 — confirm/refute the need at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: 7756dfd6a98067e2e331b40211784ebc0f605c25 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- HYPOTHESIS: whether the package makes an HTTP call is decided at the specification sections of the trace starting set; the two HTTP constraints bind only if the trace matrix shows one, and only in `pm-runtime` or a provider module — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: 7756dfd6a98067e2e331b40211784ebc0f605c25 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- OBSERVED: the specification and watch parts of the Expected Surface changed since the claims were last grounded at `c281f6c` only as listed in the verdict — `git diff --numstat c281f6c..7756dfd`; the executing plan re-reads them at its branch point
  - verdict: corroborated | checked_at: 7756dfd6a98067e2e331b40211784ebc0f605c25 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: git diff --numstat c281f6c..7756dfd over the declared specification and watch parts: specification/model-work/08-epic-coordination.adoc (+18/-5); specification/phase-workflows/01-planning-phases-1-2.adoc (+85/-10); specification/store-schemas/08-project-configuration-local.adoc (+14/-2); specification/store-schemas/10-job-and-lesson-records.adoc (+5/-9); specification/workflow-dsl/02-units-jobs-decisions.adoc (+2/-2)
- Verify-first clause: before scoping, read the roadmap item this package serves for what the code already covers, list the open pull requests for one that names `start-from-clients`, and check in `doc/plans/README.adoc` that every dependency is `done`; a refutation of any claim above loops back to re-scope.
  - verdict: corroborated | checked_at: 7756dfd6a98067e2e331b40211784ebc0f605c25 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: row start-from-clients is open in the index at 7756dfd; open PRs (ci pr list): #129 (PLAN-85) and #71 (a dependabot bump), none names the package; doc/plans/start-from-clients.adoc last changed in c281f6c; the claims above were re-read individually

## Expected Surface

- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/unit/` — inferred from the `Where` line
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/` — inferred from the `Where` line
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/` — inferred from the `Where` line
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-unit.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-unit/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-verify.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-verify/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- HYPOTHESIS: `pm-core/doc/developer/core-store.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-core/doc/adr/core-store/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- OBSERVED: `doc/plans/start-from-clients.adoc` — the package file, deleted when the package is done
- OBSERVED: `doc/specification/model-work/08-epic-coordination.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/phase-workflows/01-planning-phases-1-2.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/state-catalogue/09-bootstrap-and-lessons.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/08-project-configuration-local.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/10-job-and-lesson-records.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/workflow-dsl/02-units-jobs-decisions.adoc` — specification part; the implemented sections deleted

## Dependencies and Sequencing

- Depends on: PLAN-42 (`operator-wait-and-machine-scope`); PLAN-46 (`machine-onboarding-unit`); PLAN-45 (`project-onboarding-unit`); PLAN-76 (`start-source-intake`) (coupling: its rate limits, documents copy or move, source directory and move at creation, screening, failing closed, and claimed lesson build on the setup gate, the sources as a list, and the other sources built here; recorded on this edge, not as a reverse dependency)
- Blocks: PLAN-50 (`recipe-workflows`); PLAN-55 (`chat`)
- Overlaps with: computed by `corpus cross-check` at emit time; every plan through `doc/plans/README.adoc` and `doc/Traceability.adoc`; the plans of the same module through its log holder, which is not a declared surface
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` and the client contract `pm-api` in `pm-mcp-clients`, which stay untouched — a change there is a pull request in that repository; and `plan-marshall-documentation`, where only a requirement correction or a change of `cross-cutting.adoc` or of a specification that lives there goes, in a pull request there

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-48-start-from-clients.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

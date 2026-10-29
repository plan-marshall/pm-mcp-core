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

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R15, R18, R19.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: the developer file of each package it changes (`{module}/doc/developer/{package}.adoc`); ADR directory: `{module}/doc/adr/{package}/`; layout: workstream WS-10.
- R12 — log message holder: `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`); `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`).

## Objective

Implement the work package `start-from-clients` of `doc/plans/start-from-clients.adoc`: starting work from the clients: the bootstraps and the clients' side. Serves: roadmap Milestone 7 (the plan bootstrap with the sources `description`, `issue`, and `lesson`), Milestone 10 (the sources `local_changes`, `pr`, and `documents`), Milestone 12 (the epic bootstrap); Use Cases, UC-STA-1 to UC-STA-4, UC-STA-7 to UC-STA-9; starting work in both clients of `pm-mcp-clients`. Where: pm-workflow (the bootstrap and source units, the end of the session-driven units, the static verifier); pm-core (the project's value `epic.parallelization_scope`). PLAN-75 (#85) split the former package on 2026-10-17 (21 bullets): this package keeps 9 (the bootstraps and the clients' side), and the other 12 are `start-source-intake` (PLAN-76), on which this package now depends in addition. The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

## Deliverables

The bullets of `doc/plans/start-from-clients.adoc` § What at `c281f6c`, in its words.

1. Implements Workflow DSL § All Interaction in the Operator's Clients; State Catalogue § Starting from the Clients with the bootstraps `bootstrap-plan` and `bootstrap-epic`, the Source Units, Issues by Number or URL, and Asked When Needed (`PM-WF-10`, `PM-ARCH-11`).
2. The operator's clients only: the bootstraps of a plan and an epic run as units of operator waits that the clients drive; the session-driven units, the entry session, and the channel `session` of answers go from the code (`OutcomeClass`; the static verifier's rules for session units and their fixtures are already gone: PLAN-42, #199, replaced `SESSION_DRIVEN_LINKS(10)` by `OPERATOR_LINKS_APART(10)` and deleted the `session-link-*` fixtures, so this plan changes no verifier class and no verifier fixture; the same cut is made in bullet 2 of `doc/plans/start-from-clients.adoc` in this plan's pull request, R01), the channels of an answer being `web`, `cli`, and `quorum`.
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

- OBSERVED: the Objective, Deliverables, Done when and Trace above are the package file's own — read at `doc/plans/start-from-clients.adoc` § What, Trace, Done When on `origin/main` at `4617865` (9 bullets; the spec is regenerated from it by cleanup on 2026-10-22)
  - verdict: corroborated | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: doc/plans/start-from-clients.adoc unchanged since 88ec9c70; 9 What bullets and Done When equal D1 to D9 and the Done-when
- OBSERVED: the package file and its index row (`open`; depends on PLAN-42 (`operator-wait-and-machine-scope`); PLAN-46 (`machine-onboarding-unit`); PLAN-45 (`project-onboarding-unit`); PLAN-76 (`start-source-intake`) (coupling: its rate limits, documents copy or move, source directory and move at creation, screening, failing closed, and claimed lesson build on the setup gate, the sources as a list, and the other sources built here; recorded on this edge, not as a reverse dependency)) — read at `doc/plans/start-from-clients.adoc` and `doc/plans/README.adoc` on `origin/main` at `4617865`
  - verdict: corroborated | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: index row open, depends on operator-wait-and-machine-scope, machine-onboarding-unit, project-onboarding-unit, start-source-intake; all four package files exist
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line of the package file and the class-level surfaces of the former specs, not from a trace matrix — confirm/refute at the trace matrix of `traced-implementation` (verify-at-outline)
  - verdict: unverifiable | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis: all listed paths exist at f4a1bd07 (unit/ 7 files, verify/, primitive/ package-info only, ast/, engine/, core/store)
- OBSERVED: each module that logs has one holder with its own test, which refuses an identifier used twice and one outside the range of its level (INFO 1-99, WARN 100-199, ERROR 200-299): `CoreLogMessages` (5 records), `RuntimeLogMessages` (81), `WorkflowLogMessages` (5) — `static final LogRecord` counted on `origin/main` at `4617865`
  - verdict: corroborated | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: holders and range/unique tests exist; Core 5, Workflow 5, Runtime 89 at f4a1bd07 (the 81 in the text is stale, the claim holds)
- HYPOTHESIS: the package adds INFO, WARN or ERROR messages; its module's log holder is deliberately NOT in the Expected Surface: a record is appended at the next free identifier and a collision is resolved on rebase, as the operator decided for the shared holder on 2026-10-09 and kept for the per-module holders on 2026-10-13 — confirm/refute the need at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis about log need; no bootstrap or source code exists to settle it
- HYPOTHESIS: whether the package makes an HTTP call is decided at the specification sections of the trace starting set; the two HTTP constraints bind only if the trace matrix shows one, and only in `pm-runtime` or a provider module — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: trace-matrix hypothesis: state-catalogue/09 source-issue.fetch is a bounded ci.issue-view via the forge provider; no ci.* operation exists at HEAD and no work package builds it
- OBSERVED: the specification and watch parts of the Expected Surface changed since the claims were last grounded at `c281f6c` only as listed in the verdict — `git diff --numstat c281f6c..4617865`; the executing plan re-reads them at its branch point
  - verdict: corroborated | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: range c281f6c..4617865 reproduces; parts changed after 4617865 are recorded in the Re-grounding Record of 2026-10-26
- Verify-first clause: before scoping, read the roadmap item this package serves for what the code already covers, list the open pull requests for one that names `start-from-clients`, and check in `doc/plans/README.adoc` that every dependency is `done`; a refutation of any claim above loops back to re-scope.
  - verdict: corroborated | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: index row open with the same four dependencies; package file unchanged; open-PR list is only #71

## Expected Surface

- OBSERVED: `doc/plans/start-from-clients.adoc` — the package file, deleted when the package is done
- OBSERVED: `doc/specification/model-work/08-epic-coordination.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/phase-workflows/01-planning-phases-1-2.adoc` — specification part; § Plan Source Contracts
- OBSERVED: `doc/specification/state-catalogue/09-bootstrap-and-lessons.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/08-project-configuration-local.adoc` — specification part; keep diagrams.format
- OBSERVED: `doc/specification/store-schemas/10-job-and-lesson-records.adoc` — specification part; job-record block stays byte-stable
- OBSERVED: `doc/specification/workflow-dsl/02-units-jobs-decisions.adoc` — specification part; keep security_severity_caps
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/spi/OutcomeClass.java` — D2: the STEPPED_OUT wording and the session removal
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/QualificationSettingsStore.java` — D8: owns local-config.json; epic.parallelization_scope must not clobber settings.qualification
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/QualificationSettingsCodec.java` — D8: writes the document the new member sits in
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/StoreRefusal.java` — the CONFLICT outcome the write uses
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/primitive/` — plan-domain primitives home (package-info only)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/spi/SpiVocabularyTest.java` — OutcomeClass vocabulary
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/store/QualificationSettingsStoreTest.java` — coexistence of the two members
- OBSERVED: `pm-workflow/src/test/resources/dsl/valid/session-unit.pmwf` — D2: session unit fixture goes
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-primitive.adoc` — new developer guide for the primitive package

## Re-grounding Record (2026-10-26, HEAD `f4a1bd07`)

Written by `pm-mcp-core-m2-m4/cleanup`, measured against `main` at `f4a1bd07`. This section is the **latest** reading; the earlier section below stays as the audit record, and **where they differ, this section is the later reading**.

- Claims re-read: 8: 5 corroborated, 0 contradicted, 3 unverifiable. Verdicts are stamped on the claims above (`checked_at: f4a1bd07`). Nothing is contradicted; the spec equals its unchanged package file.

### Surface

- Added to the Expected Surface above: `OutcomeClass`, `QualificationSettingsStore`, `StoreRefusal`, the `workflow-primitive` guide. Also read: the verifier fixtures `session-link-forbidden.pmwf` and `session-link-transitive.pmwf` under `pm-workflow/src/test/resources/verify/refused/` that D2 names.
- No annotation is wrong: every path exists; `primitive/` is package-info only.

### Specification changes carried (not yet in the Deliverables above)

- **#189 (`1c9f366d`) — State Catalogue 09 § Source Units (`source-lesson` rows, `state-source.screen`, `.flagged`, `.unscreened`), Phase Workflows 01 § Plan Source Contracts (line 290) and Store Schemas 10 § Lesson Record:** a lesson with `derived_from_untrusted` passes the level-1 validator, is stored marked untrusted and goes outcome `stored_untrusted` → `source-lesson.screen`; the lesson front matter requires `derived_from_untrusted`. D5's lesson text (resolve, premise, use/retire/remove) does not mention it and `start-source-intake.adoc` names no lesson screening: **the owner is unstated** (this plan or `start-source-intake`).
- #181 — Store Schemas 08: `diagrams.format` beside `epic.parallelization_scope`; no effect on D8, keep it when deleting implemented text. #186 — Workflow DSL 02: `security_severity_caps`; not the clients-only section. #177, #179, #192 — Phase Workflows 01 Builder Check, Proportion, Capability Specs: outside Plan Source Contracts; no effect.

### Shipped or superseded since the last grounding

- #185: `QualificationSettings*` write `local-config.json` under the local-configuration lock with revision refusal `CONFLICT`; no general `local-config` codec exists (`core/config` is package-info only), so `epic.parallelization_scope` and `settings.qualification` must coexist in one document. No pending-entry, lesson-store, `ci.issue-view` or bootstrap code landed; `PendingEntryRecord.Bootstrap` still has one raw member `source`; `VerificationRule.SESSION_DRIVEN_LINKS(10)` (`VerificationRule.java:98`) and `WorkflowValidator.checkSessionDrivenLinks` are still present.

### Open questions for the outline

- Owner of the lesson store (reader, writer, retire verb, claim, remedy, `derived_from_untrusted`) and of `source-lesson.screen/.flagged/.unscreened` and the `stored_untrusted` outcome (this plan or `start-source-intake`); `ci.issue-view` has no provider implementation or work package (the same blocker as `forgejo-provider`); where default units live (`plan-marshall-mcp`); one owner of the verifier rule `SESSION_DRIVEN_LINKS` between PLAN-42 and this plan (R15).

## Re-grounding Record (2026-10-25, HEAD `88ec9c70`)

Written by `pm-mcp-core-m2-m4/cleanup`, measured against `main` at `88ec9c70` (the plans #172, #169 and #164 and the specification pull requests #141, #165, #168, #171, #173, #174 and #175 have landed since the grounding of `4617865`). The Objective, Deliverables, Dependencies and Hand-Off above are the earlier text and stay as the audit record; **where this section and that text differ, this section is the later reading**, and the outline records the departure. Spec-sourced items below are the specification's own content, not inferences of the orchestrator; every statement about the repository was read at the cited place.

- Claims re-read: 8: 6 corroborated, 0 contradicted (re-scoped by this section), 2 unverifiable. Verdicts are stamped on the claims above (`checked_at: 88ec9c70`).

### Surface

- Added to the Expected Surface above: `pm-workflow/src/main/java/de/planmarshall/workflow/primitive/`, `pm-workflow/src/test/resources/dsl/valid/session-unit.pmwf`, `pm-workflow/doc/developer/workflow-engine.adoc`, `pm-core/doc/developer/core-spi.adoc`, `pm-workflow/src/main/java/de/planmarshall/workflow/ast/`, `pm-workflow/src/main/java/de/planmarshall/workflow/engine/`.
- understated: Add pm-workflow/src/main/java/de/planmarshall/workflow/primitive/ (plan-domain primitives home, package-info only: start.create-scope, start.store-description), workflow/ast/ (StateBehavior.InteractiveLinks, LinkRelation step_out), workflow/engine/ (WorkflowEngine stepped_out, EntityAddressResolver, AddressResolution bootstrap kinds), pm-core core/spi/OutcomeClass.java with SpiVocabularyTest and PrimitiveRegistry reserved stepped_out, verify/WorkflowValidator.checkSessionDrivenLinks and VerificationRule.SESSION_DRIVEN_LINKS(10), fixtures pm-workflow/src/test/resources/dsl/valid/session-unit.pmwf and verify/refused/session-link-forbidden.pmwf…
- unresolvable: bootstrap-plan.pmwf, bootstrap-epic.pmwf, the source-*.pmwf units and the start.*/start-epic.* states are default workflow units = model-facing content of plan-marshall-mcp (classpath:/workflows/, package default-units-and-drift-tests there; DefaultUnitLoader says this module carries no unit; CLAUDE.md forbids workflows/ in a JAR here); only primitives, verifier, engine, store and fixtures are resolvable here and the spec says pm-mcp-server stays untouched
- understated: R06/R10: add developer files pm-workflow/doc/developer/workflow-engine.adoc, workflow-ast.adoc, a new workflow-primitive.adoc, pm-core/doc/developer/core-spi.adoc, core-store.adoc (existing); JobRecordSchemaParityTest reads the job-record block of store-schemas/10 and PendingEntrySchemaParityTest reads store-schemas/01, so those blocks must stay byte-stable when implemented sections are deleted

### Specification changes carried (not yet in the Deliverables above)

- #141 — store-schemas/10 Lesson Record, Remedy (lesson front matter now requires remedy; a plan started with the lesson among its sources carries the remedy in its intent as the change to make): Add to Deliverable 5 that source-lesson stores the lesson's remedy with the source and the plan's intent carries it as the change to make, with a Done-when fixture of a lesson of remedy kind check. Expected Surface: the lesson store reader (no lesson store class exists, only LockKey.lessonStore) and the intent record path of phase-sub-workflows.

### Shipped or superseded since the last grounding

- partly fixed: no answer has the channel session: pm-core core/store QuestionChannel is WEB, CLI, QUORUM only (added 3323ce7b #93) and store-schemas/18 admits channel web or cli; no session channel string exists in pm-core, pm-workflow, pm-runtime main sources. What remains is the session-driven verifier rule and units (VerificationRule.SESSION_DRIVEN_LINKS(10), OutcomeClass.STEPPED_OUT wording)
- partly fixed: the bootstrap's persisted state and addressing: PendingEntryRecord/Codec/Store with kind plan or epic and bootstrap{state, callStack, facts, source, options, pendingCondition, lastRefusal} (d7b622dc #51) and EntityAddressResolver with AddressResolution.BOOTSTRAP_PLAN and BOOTSTRAP_EPIC (b25d115f #54) are shipped: lastRefusal already carries the start.create re-check cause of Deliverable 6; no runtime class implements core.spi.PendingEntryReader
- partly fixed: the landing axis values: PlanFacts.Axis.LANDING lists landing.pull-request, landing.main, landing.files as a closed list (plan instance, ADR workflow-plan/015), so the compiled values behind the start.sources reason for landing.files exist; the project's setting and the asking are not built

### Open questions for the outline

- **RESOLVED 2026-10-29 (operator decision 7 of 18): PLAN-42 owned it and shipped it (#199); this spec's verifier surface and the verifier clause of D2 are deleted above.** Original text: Two owners for one verifier change: PLAN-42 claims the static verifier's rules on resting states and operator links (Rules 10, 11b) while this spec claims the rules for session units and their fixtures; at HEAD it is one piece of code (VerificationRule.SESSION_DRIVEN_LINKS(10), checkSessionDrivenLinks, StateBehavior.InteractiveLinks) and R15 item 1 wants exactly one owner
- source-issue and source-pr fetch by a bounded ci.issue-view job (phase-workflows/01 line 286, state-catalogue/09 issues) but no ci.* operation exists, pm-provider-ci was removed by #156 and the CI contract has no work package; the index row lists no such dependency although the index rule says a dependency without a work package keeps a package blocked
- The lesson record store has no implementation: only LockKey.lessonStore exists; source-lesson.resolve (claimed, retired, damaged, retire verb, claim) and the Remedy need a reader and writer, the trace names Store Schemas Lesson Record, and PLAN-76 builds only the claim ending; no deliverable says who builds the store
- PendingEntryRecord.Bootstrap has a single raw member source and no recipe while the schema in store-schemas/01 has recipe and keeps sources in the entry's source directory (spec says the schema, writers, entry endpoint, directory form are not implemented); format stays 1 (R13); owner between this package (bullet 9 restructures), PLAN-76 (directory form) and PLAN-50 (recipe) is unstated, as is the pm-runtime PendingEntryReader adapter and the entry endpoint
- Where the default units live: the bootstraps and source units are plan-marshall-mcp content (classpath:/workflows/) while the spec says pm-mcp-server stays untouched; it does not say whether this package ships fixture units here only or a companion package there

### Second pass (HEAD `8233fffc`, after #177 and #179)

The specification pull requests #177 (capability specs: Proportion, Amended after refine, Verified by, the Capability Test Guard, the replan triggers `capability_conflict` and `intent_amended`) and #179 landed after the first pass. 8 claims were re-read: 6 corroborated, 0 contradicted (still re-scoped by this record), 2 unverifiable; stamped `checked_at: 8233fffc`.

## Dependencies and Sequencing

- Depends on: PLAN-42 (`operator-wait-and-machine-scope`); PLAN-46 (`machine-onboarding-unit`); PLAN-45 (`project-onboarding-unit`); PLAN-76 (`start-source-intake`) (coupling: its rate limits, documents copy or move, source directory and move at creation, screening, failing closed, and claimed lesson build on the setup gate, the sources as a list, and the other sources built here; recorded on this edge, not as a reverse dependency); PLAN-146 (`ci-contract-module`) and PLAN-147 (`ci-contract-pull-request-view`) (`ci.issue-view` for `source-issue`; `ci.pr-view` for `source-pr`; operator decision 2 of 18); PLAN-150 (`lesson-store-operations`) (the lesson store: `LessonStore`, the resolver, claim, release and retire; operator decision 3 of 18)
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

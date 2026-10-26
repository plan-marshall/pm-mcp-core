# PLAN-42: Waiting for the operator, and the machine scope

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-42-operator-wait-and-machine-scope.md` and is queued as one row file, `queue/PLAN-42.json`,
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
- R12 — log message holder: `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`); `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`).

## Objective

Implement the work package `operator-wait-and-machine-scope` of `doc/plans/operator-wait-and-machine-scope.adoc`: waiting for the operator, and the machine scope. Serves: roadmap Milestone 7 (the operator wait as a kind of state, the machine scope); the operator waits in both clients of `pm-mcp-clients`. Where: pm-workflow (the operator wait in the language, the static verifier, the engine); pm-core (the machine scope's store). The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

## Deliverables

The bullets of `doc/plans/operator-wait-and-machine-scope.adoc` § What, in its words (regenerated at 710da61, after the specification moved into this repository).

1. Implements Workflow DSL § Waiting for the Operator and § Machine Scope (`PM-WF-20`, `PM-WF-26`).
2. The operator wait: a state of any unit and any scope may wait for the operator, in its three forms (a link state, the operator states of the decision unit, the waiver wait); it offers the operator's links to both clients, the first act winning, holds no worker and no model slot, has no time limit, and ends by an operator act through a link or by an engine-owned transition that ends the scope; its awaited events and the static verifier's rules on resting states and operator links (Rules 10, 11b) follow. Holding nothing is structural, not a release of its own: an operator wait is no model-job state, so the engine opens no task in it and offers none to a worker, and the scope reaches it only after the tasks of the state before have settled, each closed task having released its lease and its model slot where they are held (`task-queue-and-protocol`, `slots-usage-and-budgets`); the one exception is a worker holding its task for the answer to its own question within its role's hold limit, which suspends or falls back after it (Model Work § Continuing after a Question). Consent states (`<sub-workflow>.consent`, links `yes`, `yes_remember`, `no`) are operator waits of this form; their store is `machine-onboarding-unit`'s. The question modes `auto` and `none`, the question wait, the deadline, the class fallbacks, and the expiry of a waiver request go; a waiver request ends by `waive` or `decline`.
3. What halts: an operator wait halts its own scope only, shown as waiting for the operator, never as stalled; the machine scope's onboarding halts the system: while its first run has not reached `onboard.completed`, the setup gate refuses to start a plan, an epic, or a project onboarding. This package builds that guard once, reading the machine scope's state: the setup gate's reason `machine_not_onboarded` and, for an act over the local API that needs a step of the onboarding not done yet, `onboarding_incomplete` (`409`), each naming the missing step and both clients; the onboarding itself and the operator's verbs stay open. Its callers are the starts of `start-from-clients` (`start.open`, `start-epic.open`, `start.create-scope`) and the start of a project's onboarding of `project-onboarding-unit`, which build those entry points.
4. The machine scope: the fourth top-level scope, one per machine (`scope_type: machine`, `scope_id` null, `NO_PLAN` for its workers), its workflow state in `<PM_MCP_BASE>/machine/mcp/status.json`, its states of the phase `machine`, and its resident unit `onboarding.pmwf` compiled into the binary; a condition of the machine scope retried at the compiled backoff and shown as a hold.
5. Where `waits` or `operator-and-question-stores` have already implemented question deadlines and fallbacks, this package removes them, and so the mention of a deadline in the Javadoc of `EngineEvent.QuestionResolved` of `workflow-engine`.
6. Schema parity: the scope `machine` and the phase `machine` of the status store, and `WorkflowStatusSchemaParityTest.phasesEqualSchema` and `scopeTypesEqualSchema` re-enabled.

**Done when** (the package's own acceptance): A test shows a plan in an operator wait with no open task and no task offered for its scope, holding no worker and no slot, another plan running on, and no transition by time; a test shows a waiver request ended by `decline`; a test shows the machine-onboarding guard refusing with `machine_not_onboarded` and `onboarding_incomplete` while the machine's first onboarding has not reached `onboard.completed`, the onboarding itself and the operator's verbs still answered, and passing once it has, and the machine scope's unit passing the graph check of a resident unit; the static verifier accepts an operator wait in a unit of any scope and refuses a `link` clause rendered to a model; `WorkflowStatusSchemaParityTest.phasesEqualSchema` and `scopeTypesEqualSchema` run without `@Disabled` and pass. **Package bookkeeping:** `doc/plans/operator-wait-and-machine-scope.adoc` and its row of `doc/plans/README.adoc` are removed in the same pull request, and the links check (R17, absolute paths of the sibling checkout) reports `BROKEN LINKS 0`, the run recorded in the pull request body. **Build:** `verify`, `verify -Ppre-commit` (its rewrites reviewed and committed), `verify -Pcoverage` (80 % instruction and branch per module) and `verify -Pintegration-tests -pl pm-core -am` are green (the plan changes `pm-core` and `pm-workflow`; `pm-workflow` has no integration-test profile). **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Use cases: UC-PLN-2 (a decision as an operator wait); UC-ONB-1 (the machine's onboarding)
- Requirements: PM-WF-20; PM-WF-26; PM-WF-12; PM-SEC-8
- Specification: Workflow DSL § Waiting for the Operator; § Machine Scope; Workflow DSL § Waivers & Integrity; § Question States; § Static Verification; Model Work § Question Delivery

## Standing Constraints

None beyond the standing rules named in the First Instruction above.

## Claim Labels

- OBSERVED: the Objective, Deliverables, Done when and Trace above are the package file's own — read at `doc/plans/operator-wait-and-machine-scope.adoc` § What, Trace, Done When on `origin/main` at `4617865` (6 bullets; the spec is regenerated from it by cleanup on 2026-10-22)
  - verdict: contradicted | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: yes | evidence: package file unchanged since 88ec9c70 but its bullet 2 links Model Work 04 model-slots while spec D2 still names task-queue-and-protocol and slots-usage-and-budgets (shipped); other 5 bullets and the Trace match
- OBSERVED: the package file and its index row (`open`; depends on PLAN-25 (`waits`)) — read at `doc/plans/operator-wait-and-machine-scope.adoc` and `doc/plans/README.adoc` on `origin/main` at `4617865`
  - verdict: contradicted | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: yes | evidence: index row open, Depends on nothing; PLAN-25 waits shipped (a53d312d, no package file); the claim still says it depends on PLAN-25
- OBSERVED: the static verifier's Rule 10 (`SESSION_DRIVEN_LINKS`, resting link states only in session-driven units) shipped with PLAN-41 (#34) and is one of the things this package changes — read at `pm-workflow/src/main/java/de/planmarshall/workflow/verify/VerificationRule.java`
  - verdict: corroborated | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: VerificationRule.SESSION_DRIVEN_LINKS(10) at VerificationRule.java:98; WorkflowValidator emits the violations; pm-workflow has no commit since 88ec9c70
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line; the machine scope's store lands in `core.store` — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis: no machine store at f4a1bd07 (JournalScopeType has PLAN, EPIC, WORKSPACE, LESSONS); core.store is the plausible home
- Verify-first clause: before scoping, check that the index no longer says `blocked`, that the use cases are agreed, and which of the packages this one restructures have shipped since; a refutation loops back to re-scope.
  - verdict: unverifiable | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: procedure: index row open; PLAN-14 and PLAN-25 shipped; use-case agreement and open PRs not readable

## Expected Surface

- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/` — the resting-state rule
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/engine/` — the operator wait in the engine
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/dsl/` — the operator wait in the language
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/` — the machine scope's store
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-verify.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-verify/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-engine.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-engine/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-dsl.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-dsl/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- HYPOTHESIS: `pm-core/doc/developer/core-store.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-core/doc/adr/core-store/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- OBSERVED: `doc/plans/operator-wait-and-machine-scope.adoc` — the package file, deleted when the package is done
- OBSERVED: `doc/specification/model-work/06-envelopes-questions-deadlines.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/workflow-dsl/01-grammar.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/workflow-dsl/02-units-jobs-decisions.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/workflow-dsl/04-ast-node-model.adoc` — specification part; the implemented sections deleted
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/ast/` — exists at HEAD `88ec9c70`; added by the re-grounding of 2026-10-25 (see Re-grounding Record)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/store/WorkflowStatusSchemaParityTest.java` — exists at HEAD `88ec9c70`; added by the re-grounding of 2026-10-25 (see Re-grounding Record)
- OBSERVED: `doc/specification/store-schemas/02-workflow-state-store.adoc` — exists at HEAD `88ec9c70`; added by the re-grounding of 2026-10-25 (see Re-grounding Record)
- OBSERVED: `doc/specification/filesystem-layout/02-machine-runtime.adoc` — exists at HEAD `88ec9c70`; added by the re-grounding of 2026-10-25 (see Re-grounding Record)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/JournalScope.java` — a `machine` scope needs a factory beside the four that exist; added by the re-grounding of 2026-10-26 (see Re-grounding Record)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/ChangeLedger.java` — exhaustive switch over `JournalScopeType` (PLAN, EPIC, WORKSPACE, LESSONS) needs an arm for MACHINE; added by the re-grounding of 2026-10-26
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/MetricsStore.java` — same switch; added by the re-grounding of 2026-10-26
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/QuestionLog.java` — same switch; added by the re-grounding of 2026-10-26
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/WorkflowStatusStore.java` — same switch; added by the re-grounding of 2026-10-26
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/store/` — the test twins of those switches (`MetricsStoreTest`, `ChangeLedgerTest`, the `QuestionLog` tests); added by the re-grounding of 2026-10-26
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/setup/` — still package-info only; the plausible home of the setup-gate guard (`machine_not_onboarded`, `onboarding_incomplete` occur in no main source); added by the re-grounding of 2026-10-26

## Re-grounding Record (2026-10-26, HEAD `f4a1bd07`)

Written by `pm-mcp-core-m2-m4/cleanup`, measured against `main` at `f4a1bd07`. This section is the **latest** reading; the earlier section below stays as the audit record, and **where they differ, this section is the later reading**.

- Claims re-read: 5: 1 corroborated, 2 contradicted (re-scoped by this section), 2 unverifiable. Verdicts are stamped on the claims above (`checked_at: f4a1bd07`).

### Premises corrected (contradicted at HEAD, re-scoped here)

- Claim 0: the package file is unchanged since `88ec9c70`; its bullet 2 links Model Work 04 `#model-slots`, while spec D2 still names `task-queue-and-protocol` and `slots-usage-and-budgets` (both shipped); the other five bullets and the Trace match.
- Claim 1: the index row is open and depends on nothing; PLAN-25 `waits` is shipped (`a53d312d`, no package file).

### Surface

- Added to the Expected Surface above: `JournalScope`, `ChangeLedger`, `MetricsStore`, `QuestionLog`, `WorkflowStatusStore`, their tests, and `workflow/setup/`.
- wrong: `pm-core/doc/adr/core-store/` (001 to 045, next 046), `pm-workflow/doc/adr/workflow-verify/` (to 030), `workflow-engine/` (to 012), `workflow-dsl/` (to 008) are annotated "new directory if absent"; all exist. All other entries exist at HEAD; `WorkflowStatusSchemaParityTest` still has `@Disabled` on `phasesEqualSchema` and `scopeTypesEqualSchema`.

### Specification changes carried (not yet in the Deliverables above)

- #186 — Workflow DSL 02 § Unit Declarations: the finalize unit `phase-6-finalize` and its `security_severity_caps` join the compiled declaration schemas; the Operator Wait and Waivers sections are untouched.
- #188 — Workflow DSL 04 INV-ADJ-1: `quorum_not_diverse` compares by model identity; Rules 10, 11b and Static Verification for the operator wait are untouched.
- #187, #190 — Model Work 04: only read-set text and a deleted start-failure paragraph; the `#model-slots` anchor D2 links is intact (line 193).
- **Cross-cutting (#185):** `ChangeLedger`, `MetricsStore`, `QuestionLog` and `WorkflowStatusStore` take a `JournalScope` and switch exhaustively over `JournalScopeType`, which has PLAN, EPIC, WORKSPACE, LESSONS only; D4 and D6 add a MACHINE scope, which forces arms in those four switches. `StoreRefusal.Outcome.CONFLICT` is used by `QualificationSettingsStore` only. `PlanInstanceStore` already writes through a `ScopeTransaction`.

### Shipped or superseded since the last grounding

- #190: `runtime.worker` (`WorkerSupervisor`, `ModelSlots`, `WorkerEnding` releasing the slot and the lease) exists with no production caller; with PLAN-31's `ModelSlotStore` the Done-when "no worker and no slot" test can be written against real types. ADR `runtime-worker/008` (lease-aware idle) is the code for the exception of a worker held for its own question.

### Open questions for the outline

- Home of the setup-gate guard and the resident unit `onboarding.pmwf` (no `*.pmwf` under `src/main`; CLAUDE.md forbids content under `workflows/`); whether the vote-wait expiry (`deadline_expired`, `AttemptEnd.DEADLINE_EXPIRED`) stays; how `StalledScopeDetector` treats a machine scope.

## Re-grounding Record (2026-10-25, HEAD `88ec9c70`)

Written by `pm-mcp-core-m2-m4/cleanup`, measured against `main` at `88ec9c70` (the plans #172, #169 and #164 and the specification pull requests #141, #165, #168, #171, #173, #174 and #175 have landed since the grounding of `4617865`). The Objective, Deliverables, Dependencies and Hand-Off above are the earlier text and stay as the audit record; **where this section and that text differ, this section is the later reading**, and the outline records the departure. Spec-sourced items below are the specification's own content, not inferences of the orchestrator; every statement about the repository was read at the cited place.

- Claims re-read: 5: 1 corroborated, 2 contradicted (re-scoped by this section), 2 unverifiable. Verdicts are stamped on the claims above (`checked_at: 88ec9c70`).

### Premises corrected (contradicted at HEAD, re-scoped here)

- Claim 0: doc/plans/operator-wait-and-machine-scope.adoc last changed by 88ec9c70 (PLAN-31): bullet 2 no longer links the package files task-queue-and-protocol and slots-usage-and-budgets, it links Model Work 04-worker-launch.adoc#model-slots; the spec Deliverable 2 still names those two shipped packages (6 bullets otherwise unchanged)
- Claim 1: doc/plans/README.adoc at HEAD: row operator-wait-and-machine-scope is open and Depends on nothing; PLAN-25 waits shipped as a53d312d (#145)

### Surface

- Added to the Expected Surface above: `pm-workflow/src/main/java/de/planmarshall/workflow/ast/`, `pm-core/src/test/java/de/planmarshall/core/store/WorkflowStatusSchemaParityTest.java`, `doc/specification/store-schemas/02-workflow-state-store.adoc`, `doc/specification/filesystem-layout/02-machine-runtime.adoc`.
- understated: add pm-workflow/src/main/java/de/planmarshall/workflow/ast/ (StateBehavior.QuestionWait is what the operator wait reshapes), pm-core/src/main/java/de/planmarshall/core/store/WorkflowStatus.java and JournalScopeType.java (no machine yet), pm-core/src/test/java/de/planmarshall/core/store/WorkflowStatusSchemaParityTest.java (Deliverable 6, @Disabled on phasesEqualSchema and scopeTypesEqualSchema), doc/specification/store-schemas/02-workflow-state-store.adoc and doc/specification/filesystem-layout/02-machine-runtime.adoc (line 145 Machine Scope)
- unresolvable: resident unit onboarding.pmwf and the setup gate guard have no path in the surface: default units load from a classpath root workflows/ that this repository must not carry (CLAUDE.md), no .pmwf under pm-workflow/src/main, and pm-workflow/.../workflow/setup/ holds only package-info; tests need a written fixture unit

### Shipped or superseded since the last grounding

- dependency superseded and a shipped plan owns part of the neighbourhood: PLAN-25 waits shipped (#145 a53d312d): index row now depends on nothing; WorkflowStatusSchemaParityTest.phasesEqualSchema and scopeTypesEqualSchema are still @Disabled and WorkflowStatus.PHASES / JournalScopeType still lack machine, so Deliverables 4 and 6 are fully open; question wait code (StateBehavior.QuestionWait, WorkflowEngine lines 308 and 374, RestingWait QUESTION_ANSWER, QuestionClassRegistry) is shipped and is what Deliverable 2 removes

### Open questions for the outline

- Deliverable 4 and Done-when need the resident unit onboarding.pmwf compiled into the binary, but this repository carries no content under workflows/ (CLAUDE.md) so the unit cannot be written here: the spec does not say the graph check of a resident unit runs on a test fixture
- the setup gate and the guard (machine_not_onboarded, onboarding_incomplete 409) have no source home at HEAD (workflow/setup holds package-info only, no gate class anywhere): spec does not name the package, and callers are in unbuilt plans
- it is not stated whether the vote-wait expiry (deadline_expired in store-schemas/16, AttemptEnd.DEADLINE_EXPIRED, model-work/10 adjudication) stays: Deliverable 5 removes 'the deadline' and the question wait only, and the quorum expiry is still specified

## Dependencies and Sequencing

- After #150 (`ac34703`, 2026-10-23): `icon` is a required key of every unit's frontmatter (grammar `[a-z][a-z0-9._-]{0,63}`, no `..` segment, format 1). The resident unit `onboarding.pmwf` and every fixture or inline unit of this plan's tests carry an `icon:` line, and the operator-wait grammar this plan adds goes through `UnitFrontmatterReader`, `WorkflowPrinter` and the DSL tests as #150 changed them: read them at the head of `main`. The `workflow-dsl` ADR directory exists (ADRs 001 to 008, not new as the Expected Surface hypothesis says): the next free number is 009 against `origin/main` (R11).
- Runs beside PLAN-87 (`journal-format-version-reset`, running) and PLAN-94 (`sonar-main-backlog`, running): PLAN-87 owns `TransactionJournal.java`, `core-store.adoc` and ADR `core-store/007`; PLAN-94 owns the Sonar issues in `EnrolmentStore.java`, `ProjectResolver.java` and `UnitFrontmatterReader.java`. Do not fix an issue of PLAN-94's list, re-read those files at the head of `main` and merge the base branch (the lane's step 7) before the merge gate.
- Depends on: PLAN-25 (`waits`)
- Blocks: PLAN-43 (`phase-sub-workflows`); PLAN-45 (`project-onboarding-unit`); PLAN-46 (`machine-onboarding-unit`); PLAN-47 (`decision-engine`); PLAN-48 (`start-from-clients`); PLAN-51 (`plan-and-epic-control`); PLAN-53 (`epic-decomposition`); PLAN-55 (`chat`); PLAN-58 (`operator-reports`); PLAN-63 (`cleanup`); PLAN-67 (`process-and-overview`); PLAN-76 (`start-source-intake`); PLAN-92 (`epic-review`)
- Restructures: what PLAN-14 and PLAN-25 implement for question modes, deadlines and fallbacks; PLAN-41's Rule 10

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-42-operator-wait-and-machine-scope.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

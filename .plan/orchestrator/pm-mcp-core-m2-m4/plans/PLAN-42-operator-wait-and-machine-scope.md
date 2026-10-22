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

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R15, R18.

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

**Done when** (the package's own acceptance): A test shows a plan in an operator wait with no open task and no task offered for its scope, holding no worker and no slot, another plan running on, and no transition by time; a test shows a waiver request ended by `decline`; a test shows the machine-onboarding guard refusing with `machine_not_onboarded` and `onboarding_incomplete` while the machine's first onboarding has not reached `onboard.completed`, the onboarding itself and the operator's verbs still answered, and passing once it has, and the machine scope's unit passing the graph check of a resident unit; the static verifier accepts an operator wait in a unit of any scope and refuses a `link` clause rendered to a model; `WorkflowStatusSchemaParityTest.phasesEqualSchema` and `scopeTypesEqualSchema` run without `@Disabled` and pass. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Use cases: UC-PLN-2 (a decision as an operator wait); UC-ONB-1 (the machine's onboarding)
- Requirements: PM-WF-20; PM-WF-26; PM-WF-12; PM-SEC-8
- Specification: Workflow DSL § Waiting for the Operator; § Machine Scope; Workflow DSL § Waivers & Integrity; § Question States; § Static Verification; Model Work § Question Delivery

## Standing Constraints

None beyond the standing rules named in the First Instruction above.

## Claim Labels

- OBSERVED: the Objective, Deliverables, Done when and Trace above are the package file's own — read at `doc/plans/operator-wait-and-machine-scope.adoc` § What, Trace, Done When on `origin/main` at `7756dfd` (6 bullets; the spec is regenerated from it by cleanup on 2026-10-22)
  - verdict: corroborated | checked_at: 7756dfd6a98067e2e331b40211784ebc0f605c25 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: doc/plans/operator-wait-and-machine-scope.adoc re-read at 7756dfd (last commit touching it: d1d58d2): 6 What bullets, Trace and Done When equal the Deliverables, trace set and acceptance above (regenerated by cleanup; the corpus regeneration diff reads current)
- OBSERVED: the package file and its index row (`open`; depends on PLAN-25 (`waits`)) — read at `doc/plans/operator-wait-and-machine-scope.adoc` and `doc/plans/README.adoc` on `origin/main` at `7756dfd`
  - verdict: corroborated | checked_at: 7756dfd6a98067e2e331b40211784ebc0f605c25 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: README.adoc row operator-wait-and-machine-scope re-read at 7756dfd: open; depends on PLAN-25 (`waits`)
- OBSERVED: the static verifier's Rule 10 (`SESSION_DRIVEN_LINKS`, resting link states only in session-driven units) shipped with PLAN-41 (#34) and is one of the things this package changes — read at `pm-workflow/src/main/java/de/planmarshall/workflow/verify/VerificationRule.java`
  - verdict: corroborated | checked_at: 7756dfd6a98067e2e331b40211784ebc0f605c25 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: VerificationRule.SESSION_DRIVEN_LINKS(10) present in pm-workflow verify at 7756dfd
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line; the machine scope's store lands in `core.store` — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: 7756dfd6a98067e2e331b40211784ebc0f605c25 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan; 6 OBSERVED surface paths checked at 7756dfd, all exist
- Verify-first clause: before scoping, check that the index no longer says `blocked`, that the use cases are agreed, and which of the packages this one restructures have shipped since; a refutation loops back to re-scope.
  - verdict: corroborated | checked_at: 7756dfd6a98067e2e331b40211784ebc0f605c25 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: row operator-wait-and-machine-scope is open in the index at 7756dfd; open PRs (ci pr list): #129 (PLAN-85) and #71 (a dependabot bump), none names the package; doc/plans/operator-wait-and-machine-scope.adoc last changed in d1d58d2; the claims above were re-read individually

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

## Dependencies and Sequencing

- Depends on: PLAN-25 (`waits`)
- Blocks: PLAN-43 (`phase-sub-workflows`); PLAN-45 (`project-onboarding-unit`); PLAN-46 (`machine-onboarding-unit`); PLAN-47 (`decision-engine`); PLAN-48 (`start-from-clients`); PLAN-51 (`plan-and-epic-control`); PLAN-53 (`epic-decomposition`); PLAN-55 (`chat`); PLAN-58 (`operator-reports`); PLAN-63 (`cleanup`); PLAN-67 (`process-and-overview`); PLAN-76 (`start-source-intake`); PLAN-92 (`epic-review`)
- Restructures: what PLAN-14 and PLAN-25 implement for question modes, deadlines and fallbacks; PLAN-41's Rule 10

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-42-operator-wait-and-machine-scope.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

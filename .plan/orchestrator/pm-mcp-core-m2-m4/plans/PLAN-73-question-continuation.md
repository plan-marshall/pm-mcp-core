# PLAN-73: The continuation of a task after its question

epic: pm-mcp-core-m2-m4
workstream: WS-05

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-73-question-continuation.md` and is queued as one row file, `queue/PLAN-73.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r1`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R15, R16, R18.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: the developer file of each package it changes (`{module}/doc/developer/{package}.adoc`); ADR directory: `{module}/doc/adr/{package}/`; layout: workstream WS-10.
- R12 — log message holder: `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`); `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`); `RuntimeLogMessages` (`de.planmarshall.runtime.log`, prefix `PM_RUNTIME`).

## Objective

Implement the work package `question-continuation` of `doc/plans/question-continuation.adoc`: the continuation of a task after its question. Serves: Roadmap Milestone 4 (the continuation of a task after its question and the stage `resumability` of the profile verification). Where: pm-core packages `core.queue` (the statuses `awaiting_answer` and `suspended`, the member `continuation` of the queue task record), `core.profile` (the stage `resumability`), and `core.role` (the role's `questions` read for the form); pm-workflow packages `workflow.task` (the form chosen, the fallback, the answer acknowledgement, the question bound, `facts.answers`) and `workflow.dsl` (`ask fresh`); pm-runtime packages `runtime.worker` (the hold, the losses of a continuing worker) and `runtime.launch` (the launch of a resumable role, the resumed generation). The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

## Deliverables

The bullets of `doc/plans/question-continuation.adoc` § What, in its words (regenerated at 710da61, after the specification moved into this repository).

1. Implements Model Work § Continuing after a Question with its answer acknowledgement, § The Answer in the Next Task, § Question Bound, and the rows Question hold, Question resume window, and Question bound of the Timeouts & Budgets (`PM-WORK-2`, its bullet on resumability). Nothing of it exists: the parser reads the `ask` declaration of a task state (`StateBehavior.ModelJob.ask`) and no `ask fresh`, and `workflow.task`, `workflow.question`, `runtime.worker`, and `runtime.launch` hold only their `package-info.java`.
2. The form, chosen by the server: after an `ask` (or `ask:TASK-NNN` of an envelope task) the server holds while the role's `questions.hold_seconds` lasts, cut to the harness's `headless.resumability.max_hold_seconds`, then suspends where the task may resume, and falls back otherwise; the form comes from the state (`ask fresh` never continues, an envelope task may always continue), the role artifact (`questions`, absent: no continuation), and the harness (a form its profile does not declare verified counts as absent), never from the model. `ask fresh` is parsed, printed, and refused like `ask` on a `role each` state.
3. Hold: the task stays leased to the asking generation in `awaiting_answer`; its representation offers only `pm_wait`, held in windows with progress frames where the host needs them; when the record closes the pending or next `pm_wait` answers with the task's representation, `facts.answers` carrying the answer and `ack` its only link; the task's links are offered again only after the acknowledgement. The worker keeps its model slot, is not idle (a `wait_again` to a holding generation is no idle wake-up), and the hold time does not count toward its worker budget.
4. Resume: the worker of a role that may resume is launched without the harness's no-persistence flag and its session id read from the structured result; after its ask it ends with the session stored, the task `suspended` (lease released, no worker, no slot), its job-private directory and per-worker data home kept; when the record closes the server starts a new generation from `headless.resumability.resume.argv` on the stored session, leases the task to it, and offers it with `facts.answers`, the offer's acknowledgement being the answer acknowledgement. A suspension lasts at most `questions.resume_within_seconds`, cut to the harness's `max_age_seconds`.
5. The record of a continuation: the member `continuation` of the queue task record (`question_id`, `form`, `hold_until`, `session_id`, `suspended_at`, `resume_until`, `suspended_digest`, `suspended_head`, `fallback_reason`), the statuses `awaiting_answer` and `suspended`, the attempt end `suspended`; the usage of a hold's wake-ups and of a resumed generation accounted to the task, marked `continuation`.
6. Fallback: the task closes `asked`, the stored session is dropped, and the work continues in a new task of the role that carries the closed questions in `facts.answers`, with `fallback_reason` recorded: not allowed, the hold or the resume window passed (`resume_expired`), the session missing or too old, the role's configuration digest or the enrolled harness version changed, the resumed generation not acknowledging, or the plan's worktree no longer at its head of the suspension. An answer that arrives during a hold or a suspension continues the task; the scope shows the task as in its question state, never as stalled.
7. Losses of a continuing worker (Model Work § Detection): a missing answer acknowledgement within the host's `worker.ack_deadline_seconds` ends the worker as lost (by silence alone where the host's acknowledgement is informational), and the task is resumed where the session was stored, else offered again with `facts.answers`; a worker that reaches its token budget while holding ends the hold, its task suspended or fallen back, and is recycled; a fenced holding generation releases the task, offered again within its re-offer budget with the answer once the record closes; a runtime restart keeps a `suspended` task suspended and ends a hold like every held task.
8. Question bound: the `ask` link is offered to the tasks of one entry of a state at most `3` times, re-entries through `<state>.asked` continuing the entry, and `ask:TASK-NNN` at most `3` times per plan task over all its envelope tasks, whichever form each question took; after that the link is not offered.
9. The stage `resumability` of the profile verification (Client Profiles § Profile Verification) with the probe task of `plan-marshall-mcp` / `question-loop-verification`: it measures hold and resume of the harness and records the `headless.resumability` values; a form it did not measure stays `false` or `null`. The parsing and validation of `headless.resumability` are `core.profile`'s, the role artifact's `questions` member `role-sets-and-model-caller`'s; running the stage in the onboarding is `machine-onboarding-unit`'s.
10. Measured first: the values and the specification this package implements are those `question-loop-verification` settled; a result of it that proves the specification wrong is corrected there before this package's code follows.

**Done when** (the package's own acceptance): On fixture harnesses and a fixture role, a held task continues in the same generation after its answer and offers its links only after `ack`; a suspended task resumes in a new generation from the stored session; each fallback reason closes the task `asked` with its `fallback_reason` and the next task carries the answer in `facts.answers`; an `ask fresh` state and a role without `questions` never continue; a missing answer acknowledgement ends the worker as lost and the task resumes or is offered again exactly once; a token budget reached while holding suspends or falls back and recycles the worker; a restart keeps a suspended task suspended; the third `ask` of a state entry and the third `ask:TASK-NNN` of a plan task are the last offered; the hold time is not charged to the worker budget and the continuation's usage is marked `continuation`; the stage `resumability` records the measured forms of a fixture harness and leaves an unmeasured form `false`. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-WORK-2; PM-WF-12
- Specification: the sections the first bullet of _What_ names; Model Work § Worker States; § Acknowledge; § Detection; § Agent Composition per Harness (session residue); § Early End of an Envelope Task; § Task Questions; § Role Artifact Schema (`questions`); Client Profiles § Resumability; Workflow DSL § Task States (`ask fresh`); Store Schemas § Queue Task Record; Hypermedia Format § `facts.answers`; Timeouts & Budgets § Worker budget; Store Schemas § Metrics Store (`continuation`)
- Watch: PM-WATCH-DISP-28; Model Work

## Standing Constraints

None beyond the standing rules named in the First Instruction above.

## Claim Labels

- OBSERVED: the Objective, Deliverables, Done when and Trace above are the package file's own — read at `doc/plans/question-continuation.adoc` § What, Trace, Done When on `origin/main` at `4617865` (10 bullets; the spec is regenerated from it by cleanup on 2026-10-22)
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: doc/plans/question-continuation.adoc re-read at 4617865 (last commit touching it: 9c02edc): 10 What bullets, Trace and Done When equal the Deliverables, trace set and acceptance above (regenerated by cleanup; the corpus regeneration diff reads current)
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line, the class names of § What and the specification parts the package links, not from a trace matrix — confirm/refute at the trace matrix of `traced-implementation` (verify-at-outline)
  - verdict: unverifiable | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan; 14 OBSERVED surface paths checked at 4617865, all exist
- Verify-first clause: before scoping, list the open pull requests for one that names `question-continuation`, check in `doc/plans/README.adoc` that no package it depends on is still listed, and read the roadmap item this package serves for what the code already covers; a refutation of any claim above loops back to re-scope.
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: row question-continuation is open in the index at 4617865; open PRs (ci pr list): #71 (a dependabot bump) is the only open pull request, and it names no package; doc/plans/question-continuation.adoc last changed in 9c02edc; the claims above were re-read individually

## Expected Surface

- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/profile/` — production code
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/profile/` — tests
- HYPOTHESIS: `pm-core/doc/developer/core-profile.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-core/doc/adr/core-profile/` — ADRs of the package's design decisions; new directory if absent
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/queue/` — production code
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/queue/` — tests
- HYPOTHESIS: `pm-core/doc/developer/core-queue.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-core/doc/adr/core-queue/` — ADRs of the package's design decisions; new directory if absent
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/role/` — production code
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/role/` — tests
- HYPOTHESIS: `pm-core/doc/developer/core-role.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-core/doc/adr/core-role/` — ADRs of the package's design decisions; new directory if absent
- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/launch/` — production code
- HYPOTHESIS: `pm-runtime/src/test/java/de/planmarshall/runtime/launch/` — tests
- HYPOTHESIS: `pm-runtime/doc/developer/runtime-launch.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-runtime/doc/adr/runtime-launch/` — ADRs of the package's design decisions; new directory if absent
- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/worker/` — production code
- HYPOTHESIS: `pm-runtime/src/test/java/de/planmarshall/runtime/worker/` — tests
- HYPOTHESIS: `pm-runtime/doc/developer/runtime-worker.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-runtime/doc/adr/runtime-worker/` — ADRs of the package's design decisions; new directory if absent
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/dsl/` — production code
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/dsl/` — tests
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-dsl.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-dsl/` — ADRs of the package's design decisions; new directory if absent
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/question/` — production code
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/question/` — tests
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-question.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-question/` — ADRs of the package's design decisions; new directory if absent
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/task/` — production code
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/task/` — tests
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-task.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-task/` — ADRs of the package's design decisions; new directory if absent
- OBSERVED: `doc/plans/README.adoc` — this package's own row only
- OBSERVED: `doc/plans/question-continuation.adoc` — the package file, deleted when the package is done
- OBSERVED: `doc/implementation-watch/model-work.adoc` — watch document; the covered items deleted
- OBSERVED: `doc/specification/client-profiles/01-profile-schema.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/hypermedia-format/01-representation-and-link-forms.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/model-work/01-roles-and-configuration.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/model-work/02-task-protocol.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/model-work/03-supervision-and-recycling.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/model-work/04-worker-launch.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/model-work/06-envelopes-questions-deadlines.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/05-metrics-and-quality-report.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/16-task-queue.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/timeouts/01-job-budgets-and-waits.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/workflow-dsl/02-units-jobs-decisions.adoc` — specification part; the implemented sections deleted

## Dependencies and Sequencing

- After #150 (`ac34703`, 2026-10-23): `icon` is a required key of every unit's frontmatter (grammar `[a-z][a-z0-9._-]{0,63}`, no `..` segment, format 1). Every `.pmwf` fixture and inline unit this plan adds to the DSL, parser, printer and engine tests carries an `icon:` line, and the printer prints it after `type`; `UnitFrontmatterReader`, `WorkflowPrinter` and the DSL tests it extends were changed by that PR, so read them at the head of `main`. The `workflow-dsl` ADR directory exists (ADRs 001 to 008), not new as the Expected Surface hypothesis says: the next free number is 009 against `origin/main` (R11).
- Depends on: PLAN-28 (`task-queue-and-protocol`); PLAN-30 (`supervisor`); PLAN-29 (`worker-launch`); PLAN-26 (`role-sets-and-model-caller`); `plan-marshall-mcp` / `question-loop-verification`
- Blocks: PLAN-35 (`supervision-scenarios`)
- Overlaps with: computed by `corpus cross-check` at emit time; every plan through `doc/plans/README.adoc` and `doc/Traceability.adoc`; the plans of the same module through its log holder, which is not a declared surface
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` and the client contract `pm-api` in `pm-mcp-clients`, which stay untouched — a change there is a pull request in that repository; and `plan-marshall-documentation`, where only a requirement correction or a change of `cross-cutting.adoc` or of a specification that lives there goes, in a pull request there

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-73-question-continuation.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

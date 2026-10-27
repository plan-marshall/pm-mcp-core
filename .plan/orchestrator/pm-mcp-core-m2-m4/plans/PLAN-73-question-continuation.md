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

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R15, R16, R17, R18.

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

**Done when** (the package's own acceptance): On fixture harnesses and a fixture role, a held task continues in the same generation after its answer and offers its links only after `ack`; a suspended task resumes in a new generation from the stored session; each fallback reason closes the task `asked` with its `fallback_reason` and the next task carries the answer in `facts.answers`; an `ask fresh` state and a role without `questions` never continue; a missing answer acknowledgement ends the worker as lost and the task resumes or is offered again exactly once; a token budget reached while holding suspends or falls back and recycles the worker; a restart keeps a suspended task suspended; the third `ask` of a state entry and the third `ask:TASK-NNN` of a plan task are the last offered; the hold time is not charged to the worker budget and the continuation's usage is marked `continuation`; the stage `resumability` records the measured forms of a fixture harness and leaves an unmeasured form `false`. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (`pm-core`, `pm-workflow`, `pm-runtime`; where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage`, `verify -Pintegration-tests -pl pm-core -am` and `verify -Pintegration-tests -pl pm-runtime -am` are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): the checkout of the sibling lags, and a `BROKEN LINKS` count that names `PM-WATCH-CLIENT-13`, `PM-WATCH-DISP-33`, `-43` or `-55` is that lag or the sibling's own links to the watch items #190 deleted, not a defect of this change. **Docs-only change:** a specification-only pull request now runs the build (ADR `doc/adr/009`), so a schema change here is checked by the parity tests before it merges.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-WORK-2; PM-WF-12
- Specification: the sections the first bullet of _What_ names; Model Work § Worker States; § Acknowledge; § Detection; § Agent Composition per Harness (session residue); § Early End of an Envelope Task; § Task Questions; § Role Artifact Schema (`questions`); Client Profiles § Resumability; Workflow DSL § Task States (`ask fresh`); Store Schemas § Queue Task Record; Hypermedia Format § `facts.answers`; Timeouts & Budgets § Worker budget; Store Schemas § Metrics Store (`continuation`)
- Watch: PM-WATCH-DISP-28; Model Work

## Standing Constraints

None beyond the standing rules named in the First Instruction above.

## Claim Labels

- OBSERVED: the Objective, Deliverables, Done when and Trace above are the package file's own — read at `doc/plans/question-continuation.adoc` § What, Trace, Done When on `origin/main` at `4617865` (10 bullets; the spec is regenerated from it by cleanup on 2026-10-22)
  - verdict: contradicted | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: yes | evidence: D1 says workflow.task, workflow.question, runtime.worker and runtime.launch hold only package-info: runtime.worker has 34 files (#190), runtime.launch has WorkerLaunch, LaunchComposer, HeadlessBlock, workflow.task has TaskProtocol; only workflow/question and workflow/hypermedia remain package-info; D9 names role-sets-and-model-caller for the questions member while the package file links Model Work Role Artifact Schema
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line, the class names of § What and the specification parts the package links, not from a trace matrix — confirm/refute at the trace matrix of `traced-implementation` (verify-at-outline)
  - verdict: unverifiable | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis: the paths exist except the workflow/question tests, developer guide and ADR directory; ADR directories exist for core-profile, core-queue, core-role, runtime-launch, runtime-worker, workflow-dsl, workflow-task
- Verify-first clause: before scoping, list the open pull requests for one that names `question-continuation`, check in `doc/plans/README.adoc` that no package it depends on is still listed, and read the roadmap item this package serves for what the code already covers; a refutation of any claim above loops back to re-scope.
  - verdict: unverifiable | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: procedure: index row open, depends only on plan-marshall-mcp question-loop-verification (open); no open PR names question-continuation

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
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/` — exists at HEAD `88ec9c70`; added by the re-grounding of 2026-10-25 (see Re-grounding Record)
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/ast/` — exists at HEAD `88ec9c70`; added by the re-grounding of 2026-10-25 (see Re-grounding Record)
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/` — exists at HEAD `88ec9c70`; added by the re-grounding of 2026-10-25 (see Re-grounding Record)
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/engine/` — exists at HEAD `88ec9c70`; added by the re-grounding of 2026-10-25 (see Re-grounding Record)
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/hypermedia/` — exists at HEAD `88ec9c70`; added by the re-grounding of 2026-10-25 (see Re-grounding Record)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/worker/WorkerCalls.java` — the hold, answer-acknowledgement and resume entries extend this inbound port; added by the re-grounding of 2026-10-26 (see Re-grounding Record)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/worker/WorkerProtocol.java` — the outbound port the assembly adapts; added by the re-grounding of 2026-10-26
- OBSERVED: `doc/Traceability.adoc` — written by every plan; added by the re-grounding of 2026-10-26

## Re-grounding Record (2026-10-26, HEAD `f4a1bd07`)

Written by `pm-mcp-core-m2-m4/cleanup`, measured against `main` at `f4a1bd07`. This section is the **latest** reading; the earlier section below stays as the audit record, and **where they differ, this section is the later reading**.

- Claims re-read: 3: 0 corroborated, 1 contradicted (re-scoped by this section), 2 unverifiable. Verdicts are stamped on the claims above (`checked_at: f4a1bd07`).

### Premises corrected (contradicted at HEAD, re-scoped here)

- Claim 0: D1's "nothing of it exists; `workflow.task`, `workflow.question`, `runtime.worker` and `runtime.launch` hold only package-info" is false: `runtime.worker` holds 34 files and 21 tests (#190), `runtime.launch` holds `WorkerLaunch`, `LaunchComposer`, `HeadlessBlock`, `workflow.task` holds `TaskProtocol`; only `workflow/question` and `workflow/hypermedia` remain package-info; `ask fresh` is not in the parser or the AST. D9 names `role-sets-and-model-caller` for the `questions` member; the package file now links Model Work § Role Artifact Schema.

### Surface

- Added to the Expected Surface above: `WorkerCalls`, `WorkerProtocol`, `doc/Traceability.adoc`.
- wrong: `pm-runtime/src/main/java/.../runtime/worker/` and its test directory are annotated package-info only / new; they hold 34 and 21 files. The guide `runtime-worker.adoc` and ADRs `runtime-worker/001` to `009` exist (next 010); the ADR directories of `core-profile`, `core-queue`, `core-role`, `runtime-launch`, `workflow-task`, `workflow-dsl` exist (`workflow-dsl` next 009). Only the `workflow/question` tests, `workflow-question.adoc` and `adr/workflow-question/` are genuinely new.

### Specification changes carried (not yet in the Deliverables above)

- **#190 (`f4a1bd07`) deleted the sentence this plan's D7 depends on:** Model Work 03 § Recycling Warm Workers is gone, including "a worker holding its task for an answer that reaches the budget ends its hold: the task is suspended where it may resume, else it falls back, and the worker is recycled"; a grep for "ends its hold" finds 0 hits and Model Work 06 has no token-budget text, yet D7's third clause still requires it. `WorkerRecycling` only recycles a `HOLDING` generation at the budget (`:143-148`); ending the hold is not built. The rule must live somewhere in the specification again: the orchestrator does not rewrite the specification, so this is an operator or documentation decision.
- #190 — Model Work 03 § Detection: "Missing answer acknowledgement" kept verbatim (D7's source); "Silence" reworded: a held wait is an open call and suppresses the signal, the answer re-arms it. `PM-WATCH-DISP-55` deleted (the guard "wait_again to a holding generation is no idle wake-up", implemented in `WorkerRecycling`); `PM-WATCH-DISP-28` present (model-work.adoc:395).

### Shipped or superseded since the last grounding

- #160: queue statuses `AWAITING_ANSWER` and `SUSPENDED`, `Continuation`, `TaskProtocol.suspend`, `WaitAgain("awaiting_answer")`. #146 and #172: `RoleArtifact.questionBounds`, `ProfileValidator.resumability`, `HeadlessBlock.resumeArgv`, `LaunchComposer` resume slot. #190: `WorkerLifecycle.HOLDING`, lease-aware idle, `LossDetection.offered`/`acknowledged` (the ack re-arm), `WorkerStartRequest.resumeSessionId`. Not built: `ask fresh`, hold/ack/`facts.answers`, the question bound, the fallback logic, the `continuation` mark in `MetricsRow`, the `resumability` stage runner, hold and answer entries in `WorkerCalls`.
- Dependencies: PLAN-28, PLAN-30, PLAN-29, PLAN-26 shipped; `plan-marshall-mcp` `question-loop-verification` open.

### Open questions for the outline

- `WorkerStartRequest` refuses `resumeSessionId` for a fresh-form role ("a resumed generation is a warm worker's") while the Continuation text and D4 do not limit resume to warm roles; where the budget-while-holding rule now lives in the specification; whether `WorkerCalls` gains hold and ack entries or the answer ack reuses `offered`/`acknowledged`; **the supervision loop has no owner** (this plan's Where line gives `runtime.worker` "the hold, the losses of a continuing worker"): the orchestrator does not decide it.

## Re-grounding Record (2026-10-25, HEAD `88ec9c70`)

Written by `pm-mcp-core-m2-m4/cleanup`, measured against `main` at `88ec9c70` (the plans #172, #169 and #164 and the specification pull requests #141, #165, #168, #171, #173, #174 and #175 have landed since the grounding of `4617865`). The Objective, Deliverables, Dependencies and Hand-Off above are the earlier text and stay as the audit record; **where this section and that text differ, this section is the later reading**, and the outline records the departure. Spec-sourced items below are the specification's own content, not inferences of the orchestrator; every statement about the repository was read at the cited place.

- Claims re-read: 3: 0 corroborated, 1 contradicted (re-scoped by this section), 2 unverifiable. Verdicts are stamped on the claims above (`checked_at: 88ec9c70`).

### Premises corrected (contradicted at HEAD, re-scoped here)

- Claim 0: doc/plans/question-continuation.adoc changed by 964f673d (#146): the questions member of the role artifact now links Model Work Role Artifact Schema instead of role-sets-and-model-caller (shipped); the spec Deliverable 9 still names role-sets-and-model-caller, and Deliverable 1 says nothing exists, which HEAD refutes (see applicability)

### Surface

- Added to the Expected Surface above: `pm-core/src/main/java/de/planmarshall/core/store/`, `pm-workflow/src/main/java/de/planmarshall/workflow/ast/`, `pm-workflow/src/main/java/de/planmarshall/workflow/verify/`, `pm-workflow/src/main/java/de/planmarshall/workflow/engine/`, `pm-workflow/src/main/java/de/planmarshall/workflow/hypermedia/`.
- wrong: Deliverable 1 says workflow.task and runtime.launch hold only package-info; both hold code (workflow/task/TaskProtocol, WaitAnswer, TaskCall ...; runtime/launch/WorkerLaunch, LaunchComposer, HeadlessBlock ...); only runtime/worker and workflow/question are empty
- understated: add pm-core/.../core/store/ (MetricsRow and MetricsCodec have no continuation mark), pm-workflow/.../workflow/ast/ and verify/ (ask fresh on StateBehavior.ModelJob, refusal on role each), workflow/engine/ (<state>.asked re-entry, question bound), workflow/hypermedia/ (facts.answers, ack link; package-info only)
- understated: missing and to be created: pm-runtime/doc/developer/runtime-worker.adoc, pm-workflow/doc/developer/workflow-question.adoc, ADR dirs runtime-worker and workflow-question, test dirs pm-runtime/src/test/.../worker and pm-workflow/src/test/.../question

### Shipped or superseded since the last grounding

- partly fixed by PLAN-28 (#160 2709d289): core.queue QueueTaskRecord has Status AWAITING_ANSWER and SUSPENDED, AttemptEnd SUSPENDED, ContinuationForm, FallbackReason and the record Continuation(questionId, form, holdUntil, sessionId, suspendedAt, resumeUntil, suspendedDigest, suspendedHead, fallbackReason) with its codec; workflow.task TaskProtocol.suspend and a WaitAgain for awaiting_answer. Deliverable 5's record half is built; not built: ask fresh, hold, ack, facts.answers, question bound, fallback logic, the usage marked continuation
- partly fixed by PLAN-26 (#146) and PLAN-29 (#172 4806a304): core.role RoleArtifact.questionBounds and EffectiveRole cut hold and resume to the harness resumability; core.profile ProfileValidator.resumability and VerificationRecord stage name resumability; runtime.launch HeadlessBlock.resumeArgv and LaunchComposer resumeSessionId with the session_id slot and resume template. Not built: the resumability stage runner, losses of a continuing worker, runtime.worker
- dependencies superseded: README at HEAD: question-continuation depends on supervisor and question-loop-verification only; PLAN-28, PLAN-29, PLAN-26 shipped; spec Dependencies and Deliverable 9 still name them

### Open questions for the outline

- Deliverable 1 'Nothing of it exists' is the package file's own text and is false at HEAD for the queue record, profile validation, resumeArgv and TaskProtocol.suspend; the spec does not say which parts the plan only wires

## Dependencies and Sequencing

- After #150 (`ac34703`, 2026-10-23): `icon` is a required key of every unit's frontmatter (grammar `[a-z][a-z0-9._-]{0,63}`, no `..` segment, format 1). Every `.pmwf` fixture and inline unit this plan adds to the DSL, parser, printer and engine tests carries an `icon:` line, and the printer prints it after `type`; `UnitFrontmatterReader`, `WorkflowPrinter` and the DSL tests it extends were changed by that PR, so read them at the head of `main`. The `workflow-dsl` ADR directory exists (ADRs 001 to 008), not new as the Expected Surface hypothesis says: the next free number is 009 against `origin/main` (R11).
- Depends on: PLAN-28, PLAN-30, PLAN-29 and PLAN-26, all shipped (#160, #190, #172, #146); `plan-marshall-mcp` / `question-loop-verification` is **`done`** in that repository's index (#107; its follow-up package `question-loop-follow-up`, #108, is open and is not a dependency), so by the cross-repository rule nothing is open; this repository's index row still names it
- Blocks: PLAN-35 (`supervision-scenarios`)
- Runs beside (runbook adaptation 2026-10-27): PLAN-42 (`operator-wait-and-machine-scope`, running). The gate found a **code overlap** that the operator accepted: the `core/store` switch files (`ChangeLedger`, `MetricsStore`, `QuestionLog`, `WorkflowStatusStore`, `JournalScope`), `model-work/06` and `workflow-dsl/02`; PLAN-42 adds a `MACHINE` scope and its arms, this plan adds the `continuation` mark to `MetricsRow` and the hold, answer and resume entries: re-read the files at the head of `main` and merge the base branch (the lane's step 7) before the merge gate. Shipped beside it since the re-grounding: PLAN-34 (#196, `core.role.qualification`, ADRs `core-role/003` and `004`) and PLAN-66 (#198, partial: `WorkerActivityPump`, `ActivityRecorder`, `OffTrackDetector`, ADRs `core-queue/007` to `009`, `runtime-worker/010` and `011`, `runtime-launch/006`). **ADR numbers are taken at write time** against `origin/main`: `core-profile` 006, `core-queue` 010, `core-role` 005, `core-store` 046, `runtime-launch` 007, `runtime-worker` 012, `workflow-task` 005, `workflow-dsl` 009, and a new `workflow-question`.
- **Read #197 first** (`df407315`, specification only, after the re-grounding): it wrote the question-loop results into Client Profiles 01 (§ Resumability: the candidates, `max_hold_seconds` 3600, `max_age_seconds` 3600, claude, opencode 2.x, antigravity, codex) and 02, Model Work 01 ("no shipped role has a `questions` member, so a shipped role's task that asks ends `asked` and its work continues in a new task") and Model Work 06 (Question Bound; Resume refused: "a session the harness lost is known to be missing ..."; the answer acknowledgement). The candidates are not shipped values and settle no profile; the stage `resumability` still decides.
- **Open decision, the operator's, asked at refine (the orchestrator does not decide it):** #190 deleted Model Work 03 § Recycling Warm Workers, including the sentence "a worker holding its task for an answer that reaches the budget ends its hold: the task is suspended where it may resume, else it falls back, and the worker is recycled"; Deliverable 7's third clause still requires it, `WorkerRecycling` only recycles a `HOLDING` generation at the budget (`WorkerRecycling.java:143-148`) without ending the hold, and no specification part states the rule any more. A plan changes the specification in its own pull request (CLAUDE.md): the question is whether this plan restores the rule in Model Work 06 § Continuing after a Question (recommended by the orchestrator's reading) or the operator corrects it elsewhere. Also `WorkerStartRequest` refuses a `resumeSessionId` for a fresh-form role while D4 does not limit resume to warm roles.
- Overlaps with: computed by `corpus cross-check` at emit time; every plan through `doc/plans/README.adoc` and `doc/Traceability.adoc`; the plans of the same module through its log holder, which is not a declared surface
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` and the client contract `pm-api` in `pm-mcp-clients`, which stay untouched — a change there is a pull request in that repository; and `plan-marshall-documentation`, where only a requirement correction or a change of `cross-cutting.adoc` or of a specification that lives there goes, in a pull request there

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-73-question-continuation.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

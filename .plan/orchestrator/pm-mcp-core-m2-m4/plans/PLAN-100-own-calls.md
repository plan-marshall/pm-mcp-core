# PLAN-100: A writer states its own calls

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-100-own-calls.md` and is queued as one row file, `queue/PLAN-100.json`,
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

- R06 — developer documentation: the developer file of each package it changes (`{module}/doc/developer/{package}.adoc`); ADR directory: `{module}/doc/adr/{package}/`; layout: workstream WS-03.
- R12 — log message holder: a record, if any, goes into `WorkflowLogMessages` or `CoreLogMessages`, at the next free identifier of its level, and into `doc/LogMessages.adoc`.

## Objective

Implement the work package `own-calls` of `doc/plans/own-calls.adoc`: own calls of a writer: the choices a task settled that the plan left open, submitted with the task, checked by the server, and recorded as decisions. The package file is written by PLAN-96 (which must have landed before this plan is emitted) from the text this spec carries; the specification it builds against is merged and no work package built it before (found by the analysis of PLAN-93, 2026-10-22). Serves: Milestone 9 (roadmap item _Implement the own calls of a writer_; `PM-WF-11`, `PM-WF-19`, `PM-TEST-6`). Where: pm-workflow (the parameter of `submit:TASK-NNN`, its checks, and the records); pm-core (the named parameter schema `own-call`, the decision record at the point `execute.own-call`).

## Deliverables

The bullets of the package file `doc/plans/own-calls.adoc` § What, in its words (authored by the orchestrator on 2026-10-22; the file itself is PLAN-96's).

1. *Implements* Phase Workflows § Own Calls of a Writer, the schema `own-call` of Hypermedia Format § Named Parameter Schemas, the decision point `execute.own-call` of Model Work § Decision Points, and the `submit:TASK-NNN` parameter of State Catalogue § Entity Units (watch PM-WATCH-PHASE-113).
2. *The submission*: `submit:TASK-NNN` carries the required parameter `own_calls:list<schema[own-call]>`, at most `10` items; an empty list states that the task settled nothing the plan left open. Each item carries `call` (`10..500` characters), `options` (one to four alternatives, each `1..200` characters; one alternative is the choice not to do it), `reason` (`10..500` characters, naming the evidence), `touches` (identifiers of the intent record and the outline: `AC-<n>`, `CN-<n>`, `NG-<n>`, `DC-<n>`, a deliverable number; may be empty), and `kind`: `design`, or `shortcut` with `site` (a path of the task's changed paths, with a line), `limit`, and `ends_when`.
3. *The server checks*, each violation refused with `invalid_link_args` and `rejected.detail` naming the item: every identifier of `touches` exists in the plan's current intent record or outline; a `shortcut`'s `site` is a path of the task's Task Diff Record; and a call may not choose against a decision of the intent record whose decider is `operator` or `quorum` (such a choice is a task question, `ask:TASK-NNN`).
4. *The records*: in the transaction that accepts the submit, each item becomes a decision record at the decision point `execute.own-call` with the decider `role`, its `options`, its `choice` (the `call`), and its `rationale` (the `reason`). A task that ends without its submit records no own call; a re-offer submits its own.
5. *Reading is the change map's*: the pull-request body, the landing record, the completion report, and the self-review read the calls; that is the roadmap item of `change-map` (Milestone 10), which also needs the module discovery. This package records the calls and nothing reads them yet.
6. *Left to the content repository*: the guidance in the skill of the `implement` role (what an own call is and is not) and the writer scenarios for own calls and shortcuts are model-facing content and evaluation of `plan-marshall-mcp`.
7. *Left out*: the reading of the calls at the pull request, the landing record, the completion report, and the self-review (`change-map`); the guidance of the `implement` role skill and the writer scenarios (`plan-marshall-mcp`); the rendering in both clients.

**Done when** (the package's own acceptance): The guards of PM-WATCH-PHASE-113 pass: a submit without `own_calls` is refused; one with a `touches` identifier the records do not hold is refused; one with a call against a `DC` item of decider `operator` is refused and the detail names the item; one with a `shortcut` whose `site` is outside the Task Diff Record is refused; an empty list is accepted. A decision record exists per call at `execute.own-call` with the decider `role`, its options, its choice, and its rationale, written in the transaction that accepts the submit; a task that ends without its submit records none, and a re-offer submits its own. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-WF-11; PM-WF-19; PM-TEST-6
- Specification: the sections the first bullet of _What_ names; Workflow DSL § Shortcut Marker
- Watch: Phase Workflows

## Standing Constraints

None beyond the standing rules named in the First Instruction above.

## Claim Labels

- OBSERVED: no work package builds this: none of the package files under `doc/plans/` names `own_calls`, `Own Calls`, `own-call`, and the index has no row for `own-calls` — `grep` of `doc/plans/` and `ls doc/plans/own-calls.adoc` at `4617865`
  - verdict: contradicted | checked_at: 88ec9c7092b3f9676171cc31bc3df0d5305832f0 | by: pm-mcp-core-m2-m4/cleanup | rescoped: yes | evidence: doc/plans/own-calls.adoc exists at HEAD (added by 9c9d99c6 #143, PLAN-96) and doc/plans/README.adoc lines 216-220 hold the row own-calls (blocked); its 7 What bullets equal the spec Deliverables.
- OBSERVED: the specification this plan builds exists and carries the anchors it names — `doc/specification/phase-workflows/03-q-gate-and-execute.adoc`, `doc/implementation-watch/phase-workflows.adoc`, `doc/specification/hypermedia-format/02-link-grammar.adoc`
  - verdict: corroborated | checked_at: 88ec9c7092b3f9676171cc31bc3df0d5305832f0 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: Anchors present at HEAD: phase-workflows/03 #own-calls (line 161), watch PM-WATCH-PHASE-113 (phase-workflows watch line 1690), own-call row in hypermedia-format/02 (line 150), execute.own-call in model-work/11 (line 168), submit:TASK-NNN in state-catalogue/10 (line 80); 03 changed since only outside the own-calls section (#171).
- OBSERVED: the dependencies named below are queue rows of this ledger (PLAN-96, PLAN-47, PLAN-43); `PLAN-96` is the plan that writes the package file and the index row
  - verdict: corroborated | checked_at: 88ec9c7092b3f9676171cc31bc3df0d5305832f0 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: Queue rows exist: queue/PLAN-96.json status shipped (landed, no longer staged), PLAN-47 staged, PLAN-43 staged; PLAN-100 is parked.
- OBSERVED: the packages this one waits on have no work package — the index and the package files under `doc/plans/` build none of `Task Diff Record`, `plan task envelope`; the index will keep this package `blocked` on them
  - verdict: corroborated | checked_at: 88ec9c7092b3f9676171cc31bc3df0d5305832f0 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: No package file or index text names a package that builds Task Diff Record or the plan task envelope (README row own-calls still blocked on them); workflow/envelope/ is package-info only. Note the store half exists: ChangeLedgerRecord.Kind.TASK_DIFF in pm-core store.
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line and the specification parts — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: contradicted | checked_at: 88ec9c7092b3f9676171cc31bc3df0d5305832f0 | by: pm-mcp-core-m2-m4/cleanup | rescoped: yes | evidence: Surface text is false at HEAD: pm-workflow/.../workflow/task/ is not package-info only, it holds 14 files including TaskProtocol.submit (closes SUBMITTED, no argument model) and pm-workflow/doc/adr/workflow-task/ exists (001-004); the own-calls package file the first entry calls hypothetical now exists.
- HYPOTHESIS: the milestone in the `Serves` line and the edges of the `Dependencies` section are the orchestrator's reading of the roadmap and of the specification, not an operator decision — confirm/refute at `doc/plans/README.adoc` and the roadmap items of `plan-marshall-documentation` (verify-at-outline)
  - verdict: corroborated | checked_at: 88ec9c7092b3f9676171cc31bc3df0d5305832f0 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: Roadmap origin/main doc/roadmap.adoc line 555 (Implement the own calls of a writer) sits in Milestone 9 (lines 518-560); README row own-calls Serves Milestone 9 and depends on decision-engine, phase-sub-workflows and the task envelope with the Task Diff Record (no package).
- Verify-first clause: before scoping, check that PLAN-96 has landed (the package file and the index row exist), that the packages this one depends on are done or in flight, and list the open pull requests for one that names the package; a refutation loops back to re-scope.
  - verdict: contradicted | checked_at: 88ec9c7092b3f9676171cc31bc3df0d5305832f0 | by: pm-mcp-core-m2-m4/cleanup | rescoped: yes | evidence: PLAN-96 has landed (queue/PLAN-96 shipped, #143 9c9d99c6): the package file doc/plans/own-calls.adoc and the index row both exist, so the clause no package file and no index row is false at HEAD; open-PR list not re-read.

## Expected Surface

- HYPOTHESIS: `doc/plans/own-calls.adoc` — the package file, written by PLAN-96; deleted when the package is done
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/task/` — the task and its submit (`package-info.java` only today)
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/decision/` — the decision records (`package-info.java` only today)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/` — the decision record
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-task.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-task/` — ADRs of the package's design decisions; new directory
- OBSERVED: `doc/specification/phase-workflows/03-q-gate-and-execute.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/hypermedia-format/02-link-grammar.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/model-work/11-decision-records.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/implementation-watch/phase-workflows.adoc` — PM-WATCH-PHASE-113 retired with the package
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/SchemaRegistry.java` — exists at HEAD `88ec9c70`; added by the re-grounding of 2026-10-25 (see Re-grounding Record)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/launch/LinkSchema.java` — exists at HEAD `88ec9c70`; added by the re-grounding of 2026-10-25 (see Re-grounding Record)

## Re-grounding Record (2026-10-25, HEAD `88ec9c70`)

Written by `pm-mcp-core-m2-m4/cleanup`, measured against `main` at `88ec9c70` (the plans #172, #169 and #164 and the specification pull requests #141, #165, #168, #171, #173, #174 and #175 have landed since the grounding of `4617865`). The Objective, Deliverables, Dependencies and Hand-Off above are the earlier text and stay as the audit record; **where this section and that text differ, this section is the later reading**, and the outline records the departure. Spec-sourced items below are the specification's own content, not inferences of the orchestrator; every statement about the repository was read at the cited place.

- Claims re-read: 7: 4 corroborated, 3 contradicted (re-scoped by this section), 0 unverifiable. Verdicts are stamped on the claims above (`checked_at: 88ec9c70`).

### Premises corrected (contradicted at HEAD, re-scoped here)

- Claim 0: doc/plans/own-calls.adoc exists at HEAD (added by 9c9d99c6 #143, PLAN-96) and doc/plans/README.adoc lines 216-220 hold the row own-calls (blocked); its 7 What bullets equal the spec Deliverables.
- Claim 4: Surface text is false at HEAD: pm-workflow/.../workflow/task/ is not package-info only, it holds 14 files including TaskProtocol.submit (closes SUBMITTED, no argument model) and pm-workflow/doc/adr/workflow-task/ exists (001-004); the own-calls package file the first entry calls hypothetical now exists.
- Claim 6: PLAN-96 has landed (queue/PLAN-96 shipped, #143 9c9d99c6): the package file doc/plans/own-calls.adoc and the index row both exist, so the clause no package file and no index row is false at HEAD; open-PR list not re-read.

### Surface

- Added to the Expected Surface above: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/SchemaRegistry.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/launch/LinkSchema.java`.
- wrong: doc/plans/own-calls.adoc is no longer hypothetical (exists, #143) and the README row exists; pm-workflow/.../workflow/task/ is not package-info only (14 files, TaskProtocol.submit exists); pm-workflow/doc/adr/workflow-task/ exists (001-004, next 005) and workflow-task.adoc exists, neither is new.
- understated: Add pm-workflow/src/main/java/de/planmarshall/workflow/verify/SchemaRegistry.java (defaults() lists question-envelope, question-answer, vote-answer, triage-groups, simplify-proposal, commit-result, no own-call) and pm-runtime/src/main/java/de/planmarshall/runtime/launch/LinkSchema.java, LinkParameter.java (named schema parameters); no decision-record type exists in pm-core at HEAD (owner decision-engine PLAN-47); the Task Diff Record input is ChangeLedgerRecord.Kind.TASK_DIFF in pm-core/.../core/store/.

### Shipped or superseded since the last grounding

- superseded premise (package file): The Objective says the package file is written by PLAN-96 which must land first: PLAN-96 shipped as #143 9c9d99c6 and doc/plans/own-calls.adoc plus the README row exist with the same 7 bullets; the Expected Surface hypothesis on that file is now observed.
- partly present (inputs): ChangeLedgerRecord.Kind.TASK_DIFF (pm-core store, #164 lineage) is the Task Diff Record row the shortcut site check reads; TaskProtocol.submit exists without own_calls; nothing records decisions or own calls (workflow/decision and workflow/envelope are package-info only).

### Open questions for the outline

- The named schema own-call is placed in pm-core by the Where line, but named schemas are registered in pm-workflow verify SchemaRegistry and used by pm-runtime launch LinkSchema; the home of the own-call schema and the shared registry entries for intent-record and outline-record (PLAN-43) is not stated.

## Dependencies and Sequencing

- Depends on: PLAN-96 (`spec-ahead-work-packages`); PLAN-47 (`decision-engine`); PLAN-43 (`phase-sub-workflows`)
- Parked: the task envelope with its `submit` link and the Task Diff Record have no work package; the index keeps this package `blocked` on them
- Blocks: PLAN-101
- Overlaps with: computed by `corpus cross-check` at emit time
## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-100-own-calls.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

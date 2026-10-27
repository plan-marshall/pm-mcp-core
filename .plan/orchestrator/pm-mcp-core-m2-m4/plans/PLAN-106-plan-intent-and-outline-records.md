# PLAN-106: The intent and outline records, the simplify ladder and the constraint checks of the planning phases

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-106-plan-intent-and-outline-records.md` and is queued as one row file, `queue/PLAN-106.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.
> **Split from PLAN-43 on 2026-10-27** (operator: "Split it into three plans"): the package `phase-sub-workflows` is worked by PLAN-43
> (the core), PLAN-105 (the plan instance as a set of files, and the plan's history) and PLAN-106 (this plan). The package file
> `doc/plans/phase-sub-workflows.adoc` and its index row are removed by the **last** of the three to land (R01); the other two leave
> them in place. **This plan is independent of the phase restructure** (it touches the intent and outline records, the finalize
> simplify step and the validators, not the AST or the instance), so it can run beside PLAN-43.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r1`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R15, R17, R18, R19.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: the developer file of each package it changes (`{module}/doc/developer/{package}.adoc`); ADR directory: `{module}/doc/adr/{package}/`; layout: workstream WS-10.
- R12 — log message holder: `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`); `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`).

## Objective

Implement the planning phases' records and checks of the package `phase-sub-workflows`: **the plan's intent record** (`intent.json` beside `request.md`), **the outline record** (`outline.json`, `outline.md` rendered from it), **the `review-simplify` restraint ladder** (the finalize step `finalize.simplify`) and **the constraint and non-goal checks** of the planning phases. Serves: roadmap Milestone 3; UC-PLN-1. Where: pm-core (the records, `OutlineValidator`); pm-workflow (the schema registry, the finalize pipeline validator and step descriptors, the composition rules). Carved out of PLAN-43 (its Deliverables 7 to 10), which stopped on the phase restructure; these four are independent of it.

## Deliverables

1. The plan's intent record: the record `intent.json` beside `request.md` in the plan store, its schema `intent-record` (the named parameter schema the model submits), with `criteria` (`AC-<n>`, an `unchanged` one verified by a test where the plan guards behaviour), `constraints` (`CN-<n>`, each ruling something out with its `source`), non-goals (`NG-<n>`), decisions (`DC-<n>`), assumptions and source claims; `submit:refine`, the light lane's `submit:outline` and a re-planning that changes intent carry the record, a risk is written only as an `unchanged` criterion with its verification, a tested assumption or a non-goal, never as prose, and `request.md` § Clarified Request is rendered from the record; for a plan its epic started, the server seeds the required claims from the epic row's `done_when` and the epic's non-goals (`epic-intent`); every earlier revision moves to `history[]` in the accepting transaction and is never changed. The record's new link and identifier forms and the watch items `PM-WATCH-PHASE-106` and `-107` are implemented with it (Store Schemas § Intent Record; Phase Workflows § The Intent Record; Filesystem Layout § The Source Directory; Store Schemas § Epic Header; PM-WATCH-PHASE-106; PM-WATCH-PHASE-107).
2. The outline record and the specification parts of the planning phases: the outline task submits the outline record `outline:schema[outline-record]`, the server keeps `outline.json` and renders `outline.md` from it, and validation is no state of its own; the builder check asks the operator about two criteria that cannot both hold, and about a decision or criterion that permits what a constraint rules out; review results and escalation are carried by the phases; the capability specification (`.marshall/capabilities/<capability>.json`) and its measurement are read by the planning roles; the scope-held Q-Gate row, the opt-in mutation verification step (`default:mutation`) and the knowledge search are the parts of the same specification that the phases own (Phase Workflows § Phase 3 Validation; § Builder Check; § Q-Gate & Execute; Store Schemas § Project Configuration; § Capability Spec; Model Work; § Completion, Usage & Spend; § Decision Records).
3. The `review-simplify` restraint ladder: the finalize step `finalize.simplify` runs a task of the read-only role `review-simplify` (vocabulary `skill://pm/finalize/simplify`, no write tools, never the plan's writer slot) that submits `submit:simplify`, a list of proposals (`file`, `change`, `rationale`) recorded as proposal records and never prose, and edits nothing; its facts carry the clone candidates the server computes before the task in-process and without a model (token-based clone detection of the added lines against the rest of the tree, identifiers and literals normalized, a run of at least `50` tokens, at most `20`, the longest first), which are evidence for the restraint ladder's "is it already in the codebase?" and never a finding by themselves; `finalize.simplify-apply` applies the accepted subset in the plan worktree, records every other proposal `declined`, and routes the applied change through `finalize.settle-commit`; the restraint ladder and the never-simplify list (behaviour-pinning tests, trust-boundary validation, security checks, data-loss error handling, operator-kept code) are vocabulary content (Phase Workflows § Finalize Step Ordering Bands).
4. Constraints and non-goals in the planning phases: the pre-submission self-review returns one result per constraint `CN-<n>` (`respected` with its evidence, `violated` as a finding, or `not_verified`) and per non-goal `NG-<n>` (`respected`, `crossed` as a finding, or `not_verified`) beside the criteria, and a submission that omits one is refused `invalid_link_args`; the builder check always asks the operator about a decision or criterion that permits what a constraint rules out, restated as condition and response and naming both items, and a default the operator accepted as recommended does not settle it; `OutlineValidator` refuses a deliverable that satisfies an `unchanged` criterion verified by `test` downstream of the deliverable that satisfies the `new` or `fix` criterion it guards — directly or through others — naming both deliverables and the criterion, while a deliverable that satisfies both kinds is not refused. The watch items `PM-WATCH-PHASE-110` and `-111` guard the two new checks and give each its tests (Phase Workflows § Finalize Step Ordering Bands; § Builder Check; § `satisfies` Coverage; PM-WATCH-PHASE-110; PM-WATCH-PHASE-111).

**Done when:** The intent record round-trips through `submit:refine`, the light `submit:outline` and a re-planning, `request.md` § Clarified Request is rendered from it, an epic-started plan's required claims are seeded from the row's `done_when` and the epic's non-goals, and a revision moves the current record to `history[]`; the outline record round-trips and `outline.md` is rendered from it; the `review-simplify` step submits proposals with the clone candidates as evidence — a run of 49 tokens is no candidate and 50 is, at most 20 candidates are named longest first, and a candidate alone is never a finding — applies only the accepted subset, records every other proposal `declined`, routes the applied change through `finalize.settle-commit`, and its task carries no write tool and never takes the plan's writer slot; the intent record's link and identifier forms round-trip and the controls of `PM-WATCH-PHASE-106` and `-107` hold; and the builder check, the review results and escalation, the capability specification and its measurement, the scope-held Q-Gate row, the opt-in mutation step `default:mutation` and the knowledge search are each exercised by the phase that owns them; and the constraint and non-goal checks hold: a self-review that omits a `CN-<n>` or `NG-<n>` is refused, the builder check asks the operator about a default that permits what a constraint rules out, and the outline refuses an `unchanged` guard downstream of the change it guards, with the matched controls of `PM-WATCH-PHASE-110` and `-111`. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (`pm-core`, `pm-workflow`; where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and `verify -Pintegration-tests -pl pm-core -am` are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names `PM-WATCH-CLIENT-13`, `PM-WATCH-DISP-33`, `-43`, `-48`, `-55`, `-64` or `-65` is the sibling's own links to watch items deleted by #190 and #196, not a defect of this change (PLAN-42 and PLAN-102 recorded 10 and 2798 as the pre-existing figures). **Size:** CodeRabbit refuses a pull request over 100 files (PLAN-42 split its documentation into a second pull request at the operator's direction): this plan touches about 80 fixtures and 40 test classes, so ask the operator at the plan step whether to split code and documentation before opening the pull request.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Use case: UC-PLN-1
- Requirements: PM-WF-16; PM-IMPL-5
- Specification: Phase Workflows § The Intent Record; § Builder Check; § Q-Gate & Execute; § Finalize Step Ordering Bands; Store Schemas § Intent Record; § Outline Record; § Capability Spec; § Epic Header; Filesystem Layout § The Source Directory; Hypermedia Format § Link Grammar; Identifiers § Registry Stores to Web
- Watch: PM-WATCH-PHASE-106; PM-WATCH-PHASE-107; PM-WATCH-PHASE-110; PM-WATCH-PHASE-111; Phase Workflows

## Specification Added Since The Package File


Merged outside the ledger on 2026-10-21 by the operator, specification only: #134 `50322a7` (from the analysis `barely-read-the-code` of `plan-marshall-documentation`). The package file does not name it; this plan carries it (carried by the cleanup of 2026-10-22). The executing plan reads the named parts at its branch point and builds what they now say; the package file is deleted with the package as usual.

- **Trade-offs nobody named, at refine.** The refine task asks for them: a goal that improves one measure (speed, throughput, size, cost) names the measures that must not get worse, each an `unchanged` criterion or a constraint with its bound; a rewrite, a port or a replacement states whether what existing callers observe stays the same, as `unchanged` criteria or a non-goal that says it does not; a change that adds or changes a queue, a cache, a buffer, a retry or a limit states what happens when the limit is reached (what is dropped, delayed or refused) as a criterion, or asks the operator when nothing in the sources says it — `doc/specification/phase-workflows/01-planning-phases-1-2.adoc`, the refine task.
- **The builder check asks three questions.** The role skill of the builder check (class `guidance`) names the three questions a builder of a performance, rewrite or capacity change asks when the record is silent on them: which measures must not get worse while the goal's measure improves, whether existing callers observe the same behaviour afterwards, and what happens when a limit the change adds or changes is reached. A silence the record cannot answer is an `operator` question, never the builder's `minor` call — the same part, the self-scoring of refine.

Folded into the deliverable on the constraints and non-goals of the planning phases (the intent record's `unchanged` criteria, `CN-<n>` and `NG-<n>`); no new deliverable. A scenario per class of change (an improvement of one measure, a rewrite, a limit) whose record is silent: the refine task asks, and the builder check files an `operator` question, not a `minor` one.


## Standing Constraints

None beyond the standing rules named in the First Instruction above.

## Claim Labels

- OBSERVED: no `IntentRecord`, `OutlineRecord` or `PlanHistory` type exists in `pm-core` or `pm-workflow` main sources, and `SchemaRegistry.defaults()` holds no `intent-record` or `outline-record` — the first run of PLAN-43 (`git grep`) and the Re-grounding Record of 2026-10-26, read again at `82bcbc2a`
  - verdict: corroborated | checked_at: 82bcbc2ae8a6ac90c0ba01e270c68c4b423d0ce4 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: no IntentRecord, OutlineRecord or PlanHistory type exists in main sources and SchemaRegistry.defaults() names no intent-record or outline-record (git grep at origin/main)
- OBSERVED: `OutlineValidator` exists in `pm-core/src/main/java/de/planmarshall/core/spi/validation/` and has a test — `git ls-files` at `82bcbc2a`
  - verdict: corroborated | checked_at: 82bcbc2ae8a6ac90c0ba01e270c68c4b423d0ce4 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: OutlineValidator and its test exist in pm-core spi/validation at origin/main
- OBSERVED: `FinalizePipelineValidator` and `FinalizeStepDescriptor` exist in `pm-workflow/.../workflow/finalize/`, the composition rules are in `composition-rules.json` — `git ls-files` at `82bcbc2a`
  - verdict: corroborated | checked_at: 82bcbc2ae8a6ac90c0ba01e270c68c4b423d0ce4 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: FinalizePipelineValidator, FinalizeStepDescriptor and composition-rules.json exist at origin/main
- HYPOTHESIS: the record types land in `core.store` beside the other plan records, the schemas in `SchemaRegistry` — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: 82bcbc2ae8a6ac90c0ba01e270c68c4b423d0ce4 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis: the records land in core.store, the schemas in SchemaRegistry; verify-at-outline
- Verify-first clause: before scoping, read the open pull requests for one naming `phase-sub-workflows` and the landing state of PLAN-43 and PLAN-105 (this plan is independent of them but shares `WorkflowEngine`-adjacent test fixtures); a refutation of any claim above loops back to re-scope.
  - verdict: unverifiable | checked_at: 82bcbc2ae8a6ac90c0ba01e270c68c4b423d0ce4 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: procedure: the verify-first clause runs at the lane's refine

## Expected Surface

- OBSERVED: `doc/implementation-watch/phase-workflows.adoc` — PM-WATCH-PHASE-106, 107, 110, 111
- OBSERVED: `doc/specification/phase-workflows/01-planning-phases-1-2.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/phase-workflows/02-planning-lanes-phases-3-4.adoc` — specification part; keep the rows owned by finalize-pipeline-and-merge
- OBSERVED: `doc/specification/phase-workflows/04-finalize-step-bands.adoc` — specification part; DC results, simplify bands
- OBSERVED: `doc/specification/phase-workflows/07-questions-waivers-operator-influence.adoc` — specification part; Intent Amendments
- OBSERVED: `doc/specification/store-schemas/03-findings-and-review-records.adoc` — specification part; only § Phases as Sub-Workflows deleted
- OBSERVED: `doc/specification/store-schemas/06-task-and-references.adoc` — specification part; Intent Record, Outline Record, Capability Spec
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/SchemaRegistry.java` — D7/D8: no intent-record or outline-record in defaults() today
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/finalize/FinalizePipelineValidator.java` — D9/D10: the finalize.simplify bands
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/finalize/FinalizeStepDescriptor.java` — D9: the simplify steps
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/spi/validation/OutlineValidator.java` — D10: refuses an unchanged guard downstream of the change it guards
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/IntentRecord.java` — D7: the intent record intent.json
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/OutlineRecord.java` — D8: the outline record outline.json
- OBSERVED: `pm-workflow/src/main/resources/de/planmarshall/workflow/plan/composition-rules.json` — D9: default:finalize-step-simplify
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/spi/validation/OutlineValidatorTest.java` — D10 refusal with matched controls
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/IntentRecordTest.java` — round trip, revision to `history[]`, seeding from `done_when`
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/OutlineRecordTest.java` — round trip and the rendered `outline.md`
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/finalize/SimplifyStepTest.java` — clone candidates (49 and 50 tokens), the ladder

## Dependencies and Sequencing

- Depends on: nothing in this repository's index (the package `phase-sub-workflows` as a whole depends on PLAN-42, shipped)
- Blocks: PLAN-50 (`recipe-workflows`), PLAN-55 (`chat`), PLAN-62 (`process-designer`), PLAN-67 (`process-and-overview`) only through the package row (removed by the last of the three plans)
- Runs beside PLAN-43 (the core) and PLAN-105 (instance files and history, after PLAN-43): the Expected Surface shares no file with either apart from specification parts of the planning phases that PLAN-43 no longer lists
- Restructures: the planning-phase specification text (R05: delete the implemented sections of the parts above in this pull request, keep every anchor that unbuilt work still references)

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-106-plan-intent-and-outline-records.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

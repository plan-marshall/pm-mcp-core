# PLAN-105: The plan instance as a set of files, and the plan's history

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-105-plan-instance-files-and-history.md` and is queued as one row file, `queue/PLAN-105.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.
> **Split from PLAN-43 on 2026-10-27** (operator: "Split it into three plans"): the package `phase-sub-workflows` is worked by PLAN-43
> (the core), PLAN-105 (the plan instance as a set of files, and the plan's history) and PLAN-106 (the intent and outline records, the
> simplify ladder and the constraint checks). The package file `doc/plans/phase-sub-workflows.adoc` and its index row are removed by
> the **last** of the three to land (R01); the other two leave them in place.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r1`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R15, R17, R18, R19.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: the developer file of each package it changes (`{module}/doc/developer/{package}.adoc`); ADR directory: `{module}/doc/adr/{package}/`; layout: workstream WS-10.
- R12 — log message holder: `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`); `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`).

## Objective

Persist what the phases-as-sub-workflows model of PLAN-43 needs persisted: **the plan instance of a version as the set of immutable files the specification states** (`instance/plan.v<N>.pmwf` with the header, `instance/<unit>.v<N>.pmwf` per composed phase sub-workflow and bound axis variant), and **the plan's append-only history** from which the runtime derives each node's state. Serves: roadmap Milestone 3; UC-PLN-1; the drill-down of a plan in both clients of `pm-mcp-clients`. Where: pm-workflow (composition, the instance store, the engine's history writes, the diagram renderers); pm-core (the plan's history). Carved out of PLAN-43's first run, which stopped on these two items (its finding B4 and its Deliverable 5): the lane's progress note is `.plan/temp/rb-43-phase-sub-workflows/progress.md`.

## Deliverables

1. **The plan instance as a set of files (the contract the first run of PLAN-43 called unspecified; it is specified).** Implement Store Schemas 14 § Storage, § Header and § Sub-Units and Workflow DSL 09 § Plan Instance as written: a version is a set of immutable files in `plans/<plan_id>/instance/`, `plan.v<N>.pmwf` (the plan unit with the one-line canonical-JSON header, which names every input by digest and the version's composed unit files) and `<unit>.v<N>.pmwf` per composed phase sub-workflow and bound axis variant (for example `phase-6-finalize.v3.pmwf`), all written in the transaction that composes the version (`N` from `1`, never rewritten, never deleted while the plan exists); the other sub-units stay content-addressed in `mcp/units/<digest>.pmwf` (built). `PlanInstanceComposer` returns the set, `PlanInstanceStore` writes it in one transaction and refuses an inconsistent set (a header digest that names no file, a file the header does not name), the compiled invariants are checked on the composed version as a whole before anything is written (PLAN-43's call-resolved graph), the position rule applies to the set, `InstancePruner` prunes a phase by removing its call and not composing its file, and the diagram renderers read the set (`pm://plan/{plan_id}/instance[/{version}]` returns the plan unit's file with its header).
2. The plan's history: an append-only history per plan of states entered and left, sub-workflows called and returned, with their outcomes, the instance version in force, and the time; archived with the plan; the runtime derives each node's state from it: done, running, planned, waiting for the operator, failed (with its outcome), not taken, and, for a pruned phase, not in this plan with its reason.

**Done when:** a version of the plan instance is a set of files whose header digests match the files, written once in one transaction, with a refused composition writing nothing; a pruned phase has no file and its call is absent; the position rule holds over the set and an invariant that fails over the call-resolved graph refuses the version; a loop-back calls a phase again and the history records both runs; a plan's history reproduces the node states of its process, also failed and not taken, after a runtime restart. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (`pm-core`, `pm-workflow`; where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and `verify -Pintegration-tests -pl pm-core -am` are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names `PM-WATCH-CLIENT-13`, `PM-WATCH-DISP-33`, `-43`, `-48`, `-55`, `-64` or `-65` is the sibling's own links to watch items deleted by #190 and #196, not a defect of this change (PLAN-42 and PLAN-102 recorded 10 and 2798 as the pre-existing figures). **Size:** CodeRabbit refuses a pull request over 100 files (PLAN-42 split its documentation into a second pull request at the operator's direction): this plan touches about 80 fixtures and 40 test classes, so ask the operator at the plan step whether to split code and documentation before opening the pull request.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Use case: UC-PLN-1
- Requirements: PM-WF-16; PM-IMPL-5
- Specification: Store Schemas § The Plan Instance (storage, header, sub-units); § Phases as Sub-Workflows and the Phase of a Record; § The Plan's History, with § The Start of the Current Phase; Workflow DSL § Plan Instance (instance header grammar); Hypermedia Format § Link Grammar; Identifiers § Registry Stores to Web
- Watch: Workflow DSL (PM-WATCH-DSL-21); Phase Workflows

## Standing Constraints

None beyond the standing rules named in the First Instruction above.

## Claim Labels

- OBSERVED: `PlanInstanceStore` writes `instance/plan.v<N>.pmwf` (one flat unit per version) and the content-addressed sub-units `mcp/units/<digest>.pmwf` only; the per-unit files of store-schemas/14 § Storage are not written — read at `pm-workflow/src/main/java/de/planmarshall/workflow/plan/PlanInstanceStore.java` at `82bcbc2a`
  - verdict: corroborated | checked_at: 82bcbc2ae8a6ac90c0ba01e270c68c4b423d0ce4 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: PlanInstanceStore writes instance/plan.v<N>.pmwf and the content-addressed sub-units mcp/units/<digest>.pmwf only (class documentation read at origin/main): the per-unit files of Store Schemas 14 Storage are not written
- OBSERVED: Store Schemas 14 § Storage states the file set (`plan.v<N>.pmwf` with the header, `<unit>.v<N>.pmwf` per composed phase sub-workflow and bound axis variant) — read at `doc/specification/store-schemas/14-plan-instance-waivers-questions.adoc` § Storage
  - verdict: corroborated | checked_at: 82bcbc2ae8a6ac90c0ba01e270c68c4b423d0ce4 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: Store Schemas 14 Storage states the set: plan.v<N>.pmwf with the header and <unit>.v<N>.pmwf per composed phase sub-workflow and bound axis variant, all written in the composing transaction
- OBSERVED: no `PlanHistory` type exists; the history-like logs are `ActivityJournal`, `AuditLog`, `QuestionLog`, `WaiverLog`, `ChangeLedger` and `PlanInstanceStore.COMPOSITION_LOG` — `git grep` over `pm-core` and `pm-workflow` main sources, the first run of PLAN-43
  - verdict: corroborated | checked_at: 82bcbc2ae8a6ac90c0ba01e270c68c4b423d0ce4 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: git grep over pm-core and pm-workflow main sources finds no PlanHistory, IntentRecord or OutlineRecord type at origin/main
- HYPOTHESIS: the history lands in `core.store` as an append through `ScopeTransaction` (ADR `core-store/044`) — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: 82bcbc2ae8a6ac90c0ba01e270c68c4b423d0ce4 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis: the history lands in core.store through ScopeTransaction (ADR core-store/044); verify-at-outline
- Verify-first clause: before scoping, read PLAN-43's landing (or its state: the phase member removed, the call-resolved invariants) and the open pull requests for one naming `phase-sub-workflows`; a refutation of any claim above loops back to re-scope.
  - verdict: unverifiable | checked_at: 82bcbc2ae8a6ac90c0ba01e270c68c4b423d0ce4 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: procedure: the verify-first clause runs at the lane's refine

## Expected Surface

- OBSERVED: `doc/specification/store-schemas/14-plan-instance-waivers-questions.adoc` — specification part (storage, header, sub-units); the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/02-workflow-state-store.adoc` — The Plan's History, The Start of the Current Phase
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/PlanInstanceStore.java` — writes the set of files in one transaction
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/PlanInstance.java` — the instance model: the set
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/HeaderJson.java` — the header naming the composed unit files by digest
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/HeaderSchemaCheck.java` — the header against the set
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/plan/PlanInstanceStoreTest.java` — one transaction, refused inconsistent set
- OBSERVED: `doc/specification/workflow-dsl/09-plan-instance.adoc` — specification part; the implemented sections deleted
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/PlanInstanceComposer.java` — D2: composes the phases, one instance file per unit
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/diagram/InstanceMermaidRenderer.java` — D2: phase composites
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/PlanHistory.java` — D5: the plan's append-only history (history.jsonl, ADR core-store/044)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/plan/PlanInstanceComposerTest.java` — composed phases
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/engine/WorkflowEngine.java` — the history is appended where the engine enters and leaves states and calls (shared with PLAN-43: this plan follows it)
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/PlanHistoryCodec.java` — the history record codec, if the text implies a codec beside `PlanHistory`
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/PlanHistoryTest.java` — append, derivation of node states, restart

## Dependencies and Sequencing

- Depends on: PLAN-43 (`phase-sub-workflows`, the core: the AST without `phase`, the phase sub-workflows, the call-resolved invariants); it must have landed (the instance set is the composed phase units)
- Blocks: PLAN-71 (`plan-instance-versions`: it edits `PlanInstanceComposer`, `WorkflowEngine`, `WorkflowStatus*` and the same two specification parts; **sequenced after this plan**, or merged into its outline); PLAN-67 (`process-and-overview`, the process tree reads the history)
- Restructures: what PLAN-24 (`plan-instance`) implemented for the flat instance
- Adjacent to: `plan-marshall-mcp` (the default units, `default-units-and-drift-tests`, blocked), which this plan does not touch.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-105-plan-instance-files-and-history.md
```

## Write-Boundary
The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

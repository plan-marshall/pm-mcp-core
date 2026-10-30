# PLAN-V2-96: Build the grading adapter that runs role qualification

epic: pm-mcp-core-m2-m4-v2
workstream: WS-05
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-96-role-qualification-adapter.md` and is queued as one row file, `queue/PLAN-V2-96.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.
>
> Origin: the skills conformance audit of 2026-10-30 (`skills-audit-2026-10-30.md` in the epic directory), taken at `origin/main` `6847c748`, and the operator's decisions of the same day (Decisions in `epic.md`). The plan family is PLAN-V2-83 to PLAN-V2-108 (workstream WS-11, with PLAN-V2-95 and PLAN-V2-96 in WS-05); it runs first, except PLAN-V2-96, which follows PLAN-V2-31.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r4`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R06, R08, R11, R12, R14, R15, R17, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP of its own; R05, R07, R09, R10, R13, R16: no condensation, format or stored-schema change.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-runtime/doc/developer/runtime-worker.adoc`, `pm-core/doc/developer/core-role.adoc`; ADR directories: `pm-core/doc/adr/core-role/`, `pm-runtime/doc/adr/runtime-worker/`; layout: workstream WS-05.
- R12 — log message holder: `RuntimeLogMessages` (`PM_RUNTIME`).

**Read the skills first.** Before the outline, read `SKILL.md` and the standards it points to of every skill R20 names that this plan uses: `plan-marshall:../marketplace/bundles/pm-dev-java-cui/skills/{cui-logging,cui-testing,cui-http-testing}` and `../marketplace/bundles/pm-dev-java/skills/{java-core,java-lombok,java-null-safety,javadoc,junit-core,junit-integration}` (the plan-marshall checkout beside this repository: `/home/oliver/git/plan-marshall/marketplace/bundles/`). Say in the first progress note which you read. Where two skills disagree, the ADR of PLAN-V2-83 decides; where it does not, stop and report through the inbox message; do not choose.

## Objective

`RoleGrader` and `QualificationCheck` exist as library classes and are referenced from nowhere in `src/main`. ADR `pm-core/doc/adr/core-role/004` ('grading through ports, not a worker runtime') leaves to the task-protocol adapter: the offer of corpus items as tasks, the run of an item at its origin commit in a detached worktree (`Checkout.ITEM_COMMIT`), the warm-worker and contamination handling, and the entry `pm-operator roles qualify`. The review roles' planted `CN-<n>` corpus item is also open. The operator decided on 2026-10-30 (decision 4) to stage this plan after PLAN-V2-31, which delivers the supervisor calls the adapter needs.

## Deliverables

1. **Verify-first and the outline.** Re-read ADR `core-role/004`, Model Work § Role Qualification and § Launch Contract, `RoleGrader`, `QualificationCheck`, `QualificationLookup` and the task protocol (`workflow.task.TaskProtocol`), and list which of the four parts below already exist on `origin/main`; the outline keeps only what is missing. Record the finding in the first progress note.
2. **The adapter: corpus items as tasks, run at the origin commit.** A `pm-runtime` class (package `runtime.worker` or `runtime.qualification`, the outline decides) offers a corpus item as a task through the task protocol, checks the item out at its origin commit in a detached worktree (`Checkout.ITEM_COMMIT`), runs it through the supervisor calls of PLAN-V2-31 and hands the result to `RoleGrader`. Test first with the existing worker fixtures.
3. **Warm-worker and contamination handling.** A worker that served one item is not reused for a later item of the same qualification run unless the specification says so; the contamination rule is enforced and tested (the worker's workspace is a fresh detached worktree, no item output reaches the next item).
4. **The runtime side of `roles qualify` and the planted `CN-<n>` item.** The service method the entry `pm-operator roles qualify` calls (the CLI is `pm-mcp-clients`, listed under **Owed outside this repository**) and the planted `CN-<n>` corpus item of the review roles, with a test that the grader fails a role that misses it.

**Done when:** `git grep -n 'RoleGrader' -- '*/src/main/java/*.java'` finds a production caller; the adapter's tests run a corpus item end to end on the fixtures; the planted item is graded; ADR `core-role/004` gets a successor ADR for the adapter; the pull request lists what the assembly and the clients still owe. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and, for `pm-core` and `pm-runtime`, `verify -Pintegration-tests -pl <module> -am` are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main`.

**Trace starting set:** ADR `pm-core/doc/adr/core-role/004`; Model Work § Role Qualification, § Launch Contract; PM-WORK-6, PM-EXEC-5 (corpus sources); the carried item (Op48) in the owner register; PLAN-V2-31 and PLAN-V2-50 (they name the classes).

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **Corpus fetch is an outbound call** with its consent switch (R20 does not change this; PLAN-V2-53 owns the Closed List of Outbound Calls): the adapter reads the corpus through the existing source repository classes only.
- **No behaviour of the shipped library classes changes** except where the adapter shows a defect; such a change is named in the landing.
- **Everything the plan finds missing outside this repository** (the `pm-operator` command, the assembly wiring) is listed, not built.

## Claim Labels

- OBSERVED: `RoleGrader` and `QualificationCheck` are referenced only inside their packages (analysis of PLAN-34); `Checkout.ITEM_COMMIT` exists
- OBSERVED: ADR `core-role/004` leaves the adapter to the task-protocol adapter
- HYPOTHESIS: PLAN-V2-31 delivers `WorkerSupervisor` calls the adapter can use; confirm at the outline
- HYPOTHESIS: the planted `CN-<n>` item has a place in the corpus resources of `pm-core` (`core.role.corpus`); confirm at the outline
- Verify-first clause: before editing, list the open pull requests for one that touches the packages of the Expected Surface and re-run the counting commands of the claims above at the head of `main` (a plan of the family may have landed since the audit); keep only what is still open; a refutation loops back to re-scope.

## Expected Surface

- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/model/RoleGrader.java`
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/role/qualification/QualificationCheck.java` (path as found at the outline)
- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/worker/` — the adapter classes (new files)
- HYPOTHESIS: `pm-core/doc/adr/core-role/NNN-*.adoc` (new, next free number, R11)

Not surface (R19): `doc/plans/README.adoc`, `doc/Traceability.adoc`, `doc/LogMessages.adoc`, the ADR directory and developer guide of an existing package as additive bookkeeping.

## Dependencies and Sequencing

- Depends on: PLAN-V2-31 (`supervisor-calls-and-pass`), PLAN-V2-83 and PLAN-V2-84.
- Overlaps by class with PLAN-V2-50 (`r05-reduction-role-sets`, condenses the specification text this plan implements) and PLAN-V2-53 (the corpus fetch in the Closed List): this plan runs after PLAN-V2-31 and reads their text first.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-96-role-qualification-adapter.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

# PLAN-107: Closing the package `token-usage`: reduce what #204 implemented and hand the remainder to its owners

epic: pm-mcp-core-m2-m4
workstream: WS-04

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-107-token-usage-closure.md` and is queued as one row file, `queue/PLAN-107.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

> **Why this plan exists (2026-10-28).** PLAN-61 shipped the engine half of `token-usage` as #204 (`82bcbc2a`, 7 of 12 deliverables) and left the package open: the specification and
> watch reduction (its D1), a decision-calibration codec (D3), a consumer for the plan and epic token types (D8), the recorded harness runs and the evaluation (D11, D12) and the install-path handoff.
> While the package row is open, `machine-onboarding-unit`, `operator-reports`, `runtime-metrics` and, transitively, 25 packages stay blocked. The operator decided on 2026-10-28 to **close the package and move
> the remainder to named owners** rather than stage a remainder plan. This plan is documentation only: it changes no source file and no test.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r2`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R07, R08, R09, R10, R13, R14, R15, R17, R18, R19.

Plan parameters (the values the rules ask for):

- R06 (developer documentation) and R12 (log messages) do not apply: no source file is changed.
- R17 (the links check): `python3 ../plan-marshall-documentation/.claude/skills/doc-review/scripts/trace.py ../plan-marshall-documentation/doc .` over both trees; fetch `origin/main` of the sibling checkout first (it lags). The count of broken links must not rise against the base; the landing message states the number and names any that are not this plan's.

## Objective

Close the work package `token-usage` of `doc/plans/token-usage.adoc` after its engine half shipped in #204: reduce the specification parts and watch items that are implemented, move each unbuilt remainder into the package that will own it, and remove the package file and its row so the packages it blocked become ready. Serves: the onboarding chain (`machine-onboarding-unit`, `operator-reports`, `runtime-metrics`) and, through them, roadmap Milestones 4 to 14. Where: `doc/plans/`, `doc/specification/`, `doc/implementation-watch/`, `doc/Traceability.adoc`. The package file and the landing record `landings/PLAN-61.md` (read it from the main checkout: `.plan/orchestrator/pm-mcp-core-m2-m4/landings/PLAN-61.md`) are the brief; **no code is written.**

## Deliverables

1. **Reduce the implemented specification and watch** (R05): in the nine specification parts and three watch files of the Expected Surface, delete the sections and the items that #204 and the earlier packages implemented, **verified against the code at the head of `main`** (not against the landing message): the usage pointers and the `reasoning` pointer, `not_applicable`, the unified figures and the coverage, the no-limit rule, the stage `usage` with its start context and coverage, the usage columns, the token split on the qualification and judge-check records. **Keep, with every anchor that unbuilt work still references:** the sections of the remainder named in deliverable 2 (the decision-calibration record's tokens, the running plan's and epic's representation, the recorded fixtures and the repeated evaluation, the install-path handoff). A section is deleted only when `git grep` of its anchor in `doc/` finds no remaining reference, or the reference is rewired in this pull request.
2. **Hand the remainder to its owners:** one bullet each in the package file of the owner, under its _What_, naming the source section and what is owed, and the owner's _Done When_ extended by one clause where the item is acceptance:
   - **D8, the running plan's and the epic's representation consuming `PlanTokens` and `EpicTokens`** (`pm-workflow/.../plan/PlanTokens.java`, `epic/EpicTokens.java`, built, no caller): to `process-and-overview` (its `tokens.changed` event and the plan and epic representations).
   - **D3's decision-calibration record gaining tokens with the split** (the codec does not exist here; the schema is `store-schemas/16` § Decision Calibration Record): to `judgment-votes` (it fills the members of the calibration record) or, if its package file says the record is owned by `decision-quorum-and-calibration`, to that package. **Read both package files and put it where the record's codec is owned; name the choice in the landing message.**
   - **D11, the recorded output of each shipped harness and version as a parsing fixture (including the warm worker's turns), D12, the repeated usage evaluation per new harness or version, and the install-path handoff `UsageResult` to `HarnessEnrolment.UsageReport`** (`UsageReport.of` is the mapping boundary): to `machine-onboarding-unit` (the verification of an unknown harness and the enrolment).
3. **Close the package:** delete `doc/plans/token-usage.adoc`; remove its row from `doc/plans/README.adoc`; rewrite every `Depends on` cell and every package file that names `token-usage` as a dependency (`machine-onboarding-unit`, `runtime-metrics`, `operator-reports` and any other `git grep -n 'token-usage' doc/plans` finds) so that it names nothing or, where the dependency is real, the owner of the remainder it needs. **A dependent must not gain a dependency on a package that is itself blocked by it** (check the index for a cycle).
4. **Traceability:** `doc/Traceability.adoc` lines for the requirements the package named (`PM-WF-14`, `PM-WORK-3` and the others of the package file's _Trace_): implemented by, verified by, documented in, brought in line with the deletions of deliverable 1 and the new owners of deliverable 2.

Done when: `doc/plans/token-usage.adoc` and its row are gone; the specification parts and watch items of deliverable 1 hold only the remainder; each remainder item stands in the package file of one named owner; no package file or index cell names `token-usage`; `git grep -n 'token-usage' doc/ .claude 2>/dev/null` shows only links to the spec anchor `#token-usage` where it is kept; `Traceability.adoc` names the code of the requirements it traces; the links check count does not rise; the packages `machine-onboarding-unit`, `operator-reports` and `runtime-metrics` read as ready or blocked only by packages that are not this one.

## Specification Added Since The Package File

None: the package file was last grounded at #204.

## Standing Constraints

None beyond the standing rules named in the First Instruction above.

## Claim Labels

- OBSERVED: `doc/plans/token-usage.adoc` exists, the index lists it `open` and `machine-onboarding-unit`, `operator-reports` and `runtime-metrics` depend on it — `git ls-files` and `doc/plans/README.adoc` at `82bcbc2a`
  - verdict: corroborated | checked_at: 82bcbc2ae8a6ac90c0ba01e270c68c4b423d0ce4 | by: pm-mcp-core-m2-m4/analyze-61 | rescoped: n/a | evidence: doc/plans/token-usage.adoc exists; README.adoc names token-usage in four places: its own row (line 144) and the Depends cells of machine-onboarding-unit (51), operator-reports (153) and runtime-metrics (165)
- OBSERVED: `PlanTokens`, `EpicTokens` and `UsageAggregate` exist in main sources and no production class references them but their own files — `git grep` at `82bcbc2a`
  - verdict: corroborated | checked_at: 82bcbc2ae8a6ac90c0ba01e270c68c4b423d0ce4 | by: pm-mcp-core-m2-m4/analyze-61 | rescoped: n/a | evidence: grep -l over pm-*/src/main lists only UsageAggregate.java, PlanTokens.java and EpicTokens.java
- OBSERVED: the schema `decision-calibration-record` is in `store-schemas/16` and no decision-calibration codec exists in `pm-core` — `git grep` at `82bcbc2a`
  - verdict: corroborated | checked_at: 82bcbc2ae8a6ac90c0ba01e270c68c4b423d0ce4 | by: pm-mcp-core-m2-m4/analyze-61 | rescoped: n/a | evidence: store-schemas/16 line 318 holds [#decision-calibration-record]; grep for DecisionCalibration, decision-calibration and calibration_record over main sources finds nothing
- HYPOTHESIS: the owner of the decision-calibration record's codec is `judgment-votes` or `decision-quorum-and-calibration` — read both package files (verify-at-outline)
  - verdict: unverifiable | checked_at: 82bcbc2ae8a6ac90c0ba01e270c68c4b423d0ce4 | by: pm-mcp-core-m2-m4/analyze-61 | rescoped: n/a | evidence: hypothesis: judgment-votes.adoc says it fills the members of the calibration record, decision-quorum-and-calibration.adoc owns the calibration run; the lane reads both
- HYPOTHESIS: the sections of the nine specification parts that #204 implemented are the ones the landing record lists — confirm/refute against the code (verify-at-outline)
  - verdict: unverifiable | checked_at: 82bcbc2ae8a6ac90c0ba01e270c68c4b423d0ce4 | by: pm-mcp-core-m2-m4/analyze-61 | rescoped: n/a | evidence: procedure: the landing message's matrix is gone with the lane's worktree; the lane derives the implemented sections from the code
- Verify-first clause: before scoping, read the open pull requests for one naming `token-usage`, `PlanTokens` or one of the files below, and fetch `origin/main` of `plan-marshall-documentation` (it lags); a refutation of any claim above loops back to re-scope.
  - verdict: unverifiable | checked_at: 82bcbc2ae8a6ac90c0ba01e270c68c4b423d0ce4 | by: pm-mcp-core-m2-m4/analyze-61 | rescoped: n/a | evidence: procedure: the verify-first clause runs at the lane's refine

## Expected Surface

- OBSERVED: `doc/plans/token-usage.adoc` — the package file, deleted
- OBSERVED: `doc/plans/process-and-overview.adoc` — the owner of D8
- OBSERVED: `doc/plans/judgment-votes.adoc` — the candidate owner of D3's calibration record
- OBSERVED: `doc/plans/decision-quorum-and-calibration.adoc` — the other candidate owner
- OBSERVED: `doc/plans/machine-onboarding-unit.adoc` — the owner of D11, D12 and the handoff, and a dependent
- OBSERVED: `doc/plans/runtime-metrics.adoc` — a dependent
- OBSERVED: `doc/plans/operator-reports.adoc` — a dependent
- OBSERVED: `doc/implementation-watch/client-profiles.adoc` — watch document; the covered items deleted
- OBSERVED: `doc/implementation-watch/model-work.adoc` — watch document; the covered items deleted
- OBSERVED: `doc/implementation-watch/store-schemas.adoc` — watch document; the covered items deleted
- OBSERVED: `doc/specification/client-profiles/01-profile-schema.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/model-work/09-completion-usage-and-spend.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/phase-workflows/05-finalize-review-metrics-descriptor.adoc` — specification part; Metric Aggregates only, leave the later sections
- OBSERVED: `doc/specification/phase-workflows/08-dynamic-re-planning.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/state-catalogue/11-remediation-units-merge-qgate-spend.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/05-metrics-and-quality-report.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/11-enrolment-store.adoc` — specification part; the usage report member
- OBSERVED: `doc/specification/store-schemas/16-task-queue.adoc` — specification part; keep the calibration record's tokens (D3 remainder)

Shared bookkeeping, not surface (R19): `doc/plans/README.adoc` (the row and the dependency cells) and `doc/Traceability.adoc`.

## Dependencies and Sequencing

- Depends on: PLAN-61 (`token-usage`, shipped partially: #204 `82bcbc2a`); nothing else
- Blocks: the package `token-usage` row (this plan removes it) and, through it, PLAN-46 (`machine-onboarding-unit`), PLAN-58 (`operator-reports`), PLAN-60 (`runtime-metrics`) and, transitively, 25 of the open packages
- Runs beside (runbook adaptation 2026-10-28, standing rule R19): PLAN-43 (the core of `phase-sub-workflows`), PLAN-106 and PLAN-97 (`endpoint-harness`), all running. This plan edits **documentation only**; the corpus cross-check shows which of the files above they also name: read it before editing and **merge the base branch (the lane's step 7) before the merge gate**, because `doc/plans/README.adoc` and `Traceability.adoc` are edited by every plan. PLAN-43 and PLAN-106 do not touch the index until they are the last of their package.
- A documentation-only plan skips the build in CI (memory: spec-only pull requests skip the parity tests): no schema file is changed here, so nothing a parity test reads moves.
- Restructures: nothing in code.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-107-token-usage-closure.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

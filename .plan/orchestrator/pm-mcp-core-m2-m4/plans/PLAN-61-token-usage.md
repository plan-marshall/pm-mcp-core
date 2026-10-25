# PLAN-61: Tokens, not money: usage recorded, reported, and not limited

epic: pm-mcp-core-m2-m4
workstream: WS-04

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-61-token-usage.md` and is queued as one row file, `queue/PLAN-61.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r1`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R16, R18.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: the developer file of each package it changes (`{module}/doc/developer/{package}.adoc`); ADR directory: `{module}/doc/adr/{package}/`; layout: workstream WS-10.
- R12 — log message holder: `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`); `RuntimeLogMessages` (`de.planmarshall.runtime.log`, prefix `PM_RUNTIME`).

## Objective

Implement the work package `token-usage` of `doc/plans/token-usage.adoc`: tokens, not money: usage recorded, reported, and not limited. Serves: roadmap Milestone 4 (the usage rows parsed from harness output); Client Profiles § Tokens, Not Money; the token figures of a plan, an epic, the system view, and the harness list in both clients of `pm-mcp-clients`. Where: pm-core (the usage block of the profile schema and its loader, the usage columns of the metrics rows, the unified figures and their coverage as one derivation every view uses); pm-runtime (the token facts of a running plan and of an epic, the parsing of the harness output against recorded outputs per harness and version). The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

## Deliverables

The bullets of `doc/plans/token-usage.adoc` § What, in its words (regenerated at 710da61, after the specification moved into this repository).

1. Implements Client Profiles § Tokens, Not Money, the usage pointers and the stage `usage` of § Profile Verification, Model Work § Worker Result & Usage Parsing, § Usage Reporting & Spend Classes, and the usage columns and aggregates of Store Schemas § Metrics Store (`PM-WF-14`, `PM-WORK-3`, `PM-CLIENT-5`).
2. Record everything a harness reports, report usage only: every figure the harness reports is recorded raw, so reports and limits can be added later on the same rows: input, output, cache read, cache creation, reasoning, and the harness-reported cost, with the harness, its version (the new row field `harness_version`), the model, the effort, the role, and the phase. For now the product only reports, and only tokens.
3. No money shown: no price table of the product. The harness-reported cost keeps its pointer `cost_usd`, its capability `cost_field`, its column `cost_usd` in the metrics rows, and its field in the qualification and decision calibration records: it is stored as reported, or `unmeasured`, and never shown, reported, or used in a decision; the qualification and calibration records gain tokens with the split beside it, and those are what is shown. `priced_units` stays the internal figure of the migration trigger (`PM-MIG-5`) as the specification has it and is never a figure shown to the operator: `print-phase-breakdown` and every other operator view render tokens, never `priced_units`.
4. The usage pointers: `headless.usage` gains the pointer `reasoning`, the harness's reasoning or thinking figure, filling the new column `reasoning_tokens`. A profile maps it only where the harness reports reasoning apart from its output figure; where the output figure already contains it, `reasoning` is declared not applicable, so nothing is counted twice.
5. Not reported or not applicable: each figure of `headless.usage` is a JSON Pointer, `null` (the harness does not report it: the column is `unmeasured` with the reason `not_reported_by_harness`, as today), or `not_applicable` (the harness has no such figure by design, the cache creation of Antigravity for one: the column is recorded `not_applicable` and counts as `0`). A harness that lacks a figure by design is therefore still measured; a `not_applicable` column is never written as a measured `0`.
6. The unified figures: one figure for every harness, unweighted. input is input, cache read, and cache creation; output is output and reasoning; `tokens` is input plus output. A row's figure is measured when each of its five components is measured or `not_applicable`, otherwise `unmeasured`; a partial sum is never recorded. `input_tokens` and `output_tokens` can never be `not_applicable`: profile validation refuses a profile that declares either of them so, so a measured row always rests on a reported input and output. The rows keep their raw components (Raw Components Only); the persisted `total_tokens` becomes this sum, and input and output are derived in views by one derivation of pm-core that every view calls.
7. Coverage: every aggregate (per plan, phase, role, model and harness, cause class, spend class, epic) carries the total with its split and its coverage: the count of tasks measured and unmeasured with the reasons. A total over a population with unmeasured rows is a floor (`total_is_floor: true`), shown as _at least_, never as exact.
8. A running plan and an epic: the representation of a running plan carries its tokens so far with the split and the coverage; an epic's carries the sum of its plans and of its own tasks (decompose, landing, review), with the split. Neither carries a limit, a budget line, a projection, or a comparison with one: every figure the operator sees is usage only.
9. No limits: nothing compares a plan's tokens with a line; the specification has no anchor table with fallback ceilings, no planning-time projection or `split_advice`, no re-planning trigger `spend_exceeded`, no pre-merge spend check, no remediation unit `remediation.spend` and no spend waiver, and no post-run comparison with an anchor. The code built for them (the policy class `spend`, `RemediationInvariants.SPEND_UNIT`, the spend limits of the package `de.planmarshall.workflow.spend`) was removed by `spend-removal`, for roadmap Milestone 3; this package adds no limit back. The spend classes stay (they report waste), and so do the wall-clock job budgets and the token budget by which the supervisor recycles a warm worker: they bound a job or a context, not a plan's use. An operator's own limit per plan or epic is a later change on the recorded rows and not part of this package.
10. The verification records what a harness reports: a stage `usage` of the profile verification, after `faults`, checks that each pointer resolves on the real output of the `launch` stage's worker and records, per figure, `reported`, `not_applicable`, or `missing` (the cost among them), a declared pointer that does not resolve failing the stage; the record names the harness and its version. From it the harness's usage coverage follows: `complete`, `partial` with the figures missing, or `not_reported`; `machine-onboarding-unit` runs the stage and shows the coverage in the harness list.
11. Parsing fixtures: a recorded output of each shipped harness and version (one fresh run, and the turns of a warm worker) is a test fixture with its expected row: the usage is parsed through the profile into each column, the unified figures and the coverage are derived from it; a changed output format of a harness fails its fixture. The fixtures are written for the test from recorded runs, never copied from the content of the product.
12. The evaluation: the usage measurement of the Evaluation Reference (V7, Gate 13) is repeated for every new harness and harness version before its figures are trusted; until then its profile's usage stays provisional.

**Done when** (the package's own acceptance): Every figure a harness reports lands raw in its row, the cost included, with harness, harness version, model, effort, role, and phase, and no representation or view shows a cost or a price; a fixture profile with a `not_applicable` figure yields measured rows and one with a `null` figure unmeasured rows with their reason; a harness that reports reasoning apart and one that includes it in its output give the same unified figure for the same usage; the unified figures and the coverage of every aggregate come from the one derivation, a total over unmeasured rows marked a floor; each shipped harness and version has its recorded output parsed into its expected row; the verification records per figure `reported`, `not_applicable`, or `missing`, and fails for a pointer that does not resolve; the representations of a running plan and of an epic carry their tokens with split and coverage and no limit, budget line, projection, or comparison with one. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Use cases: UC-PLN-6; UC-SYS-2; UC-ONB-4
- Requirements: PM-WF-14; PM-WORK-3; PM-CLIENT-5; PM-MIG (`priced_units`, internal)
- Specification: the sections the first bullet of _What_ names; Client Profiles § Usage Pointers; § Profile Verification; Model Work § Worker Result & Usage Parsing; § Post-Completion Integrity Invariants; Phase Workflows § Re-Planning Triggers (no spend trigger); State Catalogue § Remediation Units: Merge Conflict & Q-Gate Findings (no spend unit); Module Structure Specification (`workflow.spend`); Store Schemas § Metrics Store; § Role Qualification Record; § Decision Calibration Record; Phase Workflows § Metric Aggregates; Evaluation Reference § Results
- Watch: PM-WATCH-CLIENT-13; Model Work; Store Schemas

## Standing Constraints

None beyond the standing rules named in the First Instruction above.

## Claim Labels

- OBSERVED: the Objective, Deliverables, Done when and Trace above are the package file's own — read at `doc/plans/token-usage.adoc` § What, Trace, Done When on `origin/main` at `4617865` (12 bullets; the spec is regenerated from it by cleanup on 2026-10-22)
  - verdict: corroborated | checked_at: 8233fffcaf64e1605fa4fe1b0912ac06f44bb428 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: unchanged at 8233fffc: doc/plans/token-usage.adoc untouched since 88ec9c70 (last change acb5bba9); the 12 What bullets are the Deliverables
- OBSERVED: the package file and its index row (`open`; depends on PLAN-31 (`slots-usage-and-budgets`); PLAN-34 (`role-qualification`)) — read at `doc/plans/token-usage.adoc` and `doc/plans/README.adoc` on `origin/main` at `4617865`
  - verdict: contradicted | checked_at: 8233fffcaf64e1605fa4fe1b0912ac06f44bb428 | by: pm-mcp-core-m2-m4/cleanup | rescoped: yes | evidence: unchanged at 8233fffc: README row token-usage (line 162) is open and depends on role-qualification only; PLAN-31 shipped as 88ec9c70 (#164); README untouched since
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line only — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: 8233fffcaf64e1605fa4fe1b0912ac06f44bb428 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: unchanged at 8233fffc: hypothesis verify-at-outline; no source change under pm-core, pm-workflow or pm-runtime since 88ec9c70 so the first pass reading of the surface stands
- Verify-first clause: before scoping, check that the index no longer says `blocked`, that the use cases are agreed, and which of the packages this one restructures have shipped since; a refutation loops back to re-scope.
  - verdict: unverifiable | checked_at: 8233fffcaf64e1605fa4fe1b0912ac06f44bb428 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: unchanged at 8233fffc: verification procedure; row open; open PRs not readable here (no gh); PLAN-31 shipped the usage rows so the new column and new field wording is re-scoped by the Re-grounding Record

## Expected Surface

- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/profile/` — inferred from the `Where` line
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/` — inferred from the `Where` line
- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/launch/` — inferred from the `Where` line
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/role/` — Deliverable 3 adds tokens to the qualification and calibration records that `core.role` holds; added by cleanup on 2026-10-16
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only
- HYPOTHESIS: `pm-core/doc/developer/core-profile.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-core/doc/adr/core-profile/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- HYPOTHESIS: `pm-core/doc/developer/core-store.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-core/doc/adr/core-store/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- HYPOTHESIS: `pm-runtime/doc/developer/runtime-launch.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-runtime/doc/adr/runtime-launch/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- OBSERVED: `doc/plans/token-usage.adoc` — the package file, deleted when the package is done
- OBSERVED: `doc/implementation-watch/client-profiles.adoc` — watch document; the covered items deleted
- OBSERVED: `doc/implementation-watch/model-work.adoc` — watch document; the covered items deleted
- OBSERVED: `doc/implementation-watch/store-schemas.adoc` — watch document; the covered items deleted
- OBSERVED: `doc/specification/client-profiles/01-profile-schema.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/model-work/09-completion-usage-and-spend.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/phase-workflows/05-finalize-review-metrics-descriptor.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/phase-workflows/08-dynamic-re-planning.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/state-catalogue/11-remediation-units-merge-qgate-spend.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/05-metrics-and-quality-report.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/16-task-queue.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/11-enrolment-store.adoc` — exists at HEAD `88ec9c70`; added by the re-grounding of 2026-10-25 (see Re-grounding Record)

## Re-grounding Record (2026-10-25, HEAD `88ec9c70`)

Written by `pm-mcp-core-m2-m4/cleanup`, measured against `main` at `88ec9c70` (the plans #172, #169 and #164 and the specification pull requests #141, #165, #168, #171, #173, #174 and #175 have landed since the grounding of `4617865`). The Objective, Deliverables, Dependencies and Hand-Off above are the earlier text and stay as the audit record; **where this section and that text differ, this section is the later reading**, and the outline records the departure. Spec-sourced items below are the specification's own content, not inferences of the orchestrator; every statement about the repository was read at the cited place.

- Claims re-read: 4: 1 corroborated, 1 contradicted (re-scoped by this section), 2 unverifiable. Verdicts are stamped on the claims above (`checked_at: 88ec9c70`).

### Premises corrected (contradicted at HEAD, re-scoped here)

- Claim 1: doc/plans/README.adoc at HEAD: token-usage open, Depends on role-qualification only; PLAN-31 shipped as 88ec9c70 (#164)

### Surface

- Added to the Expected Surface above: `doc/specification/store-schemas/11-enrolment-store.adoc`.
- understated: add the sites HEAD already holds: pm-core/.../core/profile/ProfileStages.java (stage usage), ProfileValidator.java (usage block), core/store/UsagePointers.java, HarnessUsage.java, MetricsRow.java, MetricsCodec.java, pm-runtime/.../launch/HarnessEnrolment.java (UsageReport), and doc/specification/store-schemas/11-enrolment-store.adoc (usage_report, #174 start_context)
- unresolvable: Deliverable 8 (token facts of a running plan and of an epic) names no path: the representation code is not in runtime/launch; candidates pm-workflow/.../workflow/plan/, epic/, hypermedia/ exist but none is named

### Specification changes carried (not yet in the Deliverables above)

- #174 — Client Profiles 01 Start Context; Store Schemas 11 usage_report.start_context; Profile Verification usage row: Add deliverable: the usage stage also records start_context per class and model (input plus cache read plus cache creation of the first turn of the launch base task, tokens or unmeasured), kept in the enrolment usage_report and shown in the harness list; Done-when: a fixture records start_context and an unreported first turn records unmeasured. Add doc/specification/store-schemas/11-enrolment-store.adoc and pm-runtime launch HarnessEnrolment.UsageReport to the Expected Surface
- #171 — Store Schemas 16 Judge Check Record items total_tokens (integer or unmeasured), Model Work 01 expected tokens of the check: Extend deliverable 3 (tokens with the split beside it in the qualification and decision calibration records) to the judge check record, whose per-item total_tokens follows the same unified derivation; no new path beyond core/role

### Shipped or superseded since the last grounding

- partly fixed by PLAN-31 (#164 88ec9c70): Measurement (measured, unmeasured, not_applicable), HarnessUsage (five components, reasoning, costUsd, total derived and a derived 0 is unmeasured), UsagePointers (reasoning pointer, not_applicable, refuses it for input, output, tool_uses at construction), MetricsRow with harness, harness_version, model, effort, role, phase, spend_class, MetricsCodec reasoning_tokens and total_tokens equal to the derived sum: so the 'new field harness_version', 'new column reasoning_tokens' and 'total_tokens becomes this sum' of Deliverables 2, 4, 5, 6 are shipped. Still open: one derivation for views (no tokens_in/out), coverage and total_is_floor aggregate…
- partly fixed by earlier profile and launch work (PLAN-19: PLAN-29 #172),core.profile ProfileStages.usage records reported, not_applicable, missing per figure, refuses a pointer that does not resolve and compares with the harness record; USAGE_FIGURES includes reasoning; pm-runtime HarnessEnrolment.UsageReport keeps the per-figure result with the enrolment. Deliverable 9 is satisfied by absence: workflow/spend holds package-info only and no spend policy class, RemediationInvariants.SPEND_UNIT or spend limit exists in main sources

### Open questions for the outline

- headless.long_context_thresholds (#174, a closed headless member the validator would refuse, and a cap of the envelope-budget fallback in model-work/06) has no owner among the staged packages; token-usage and client-profile-rendering are candidates
- the trace names Phase Workflows Metric Aggregates whole; since #165 that section also holds the Caller-Drift Rate shape/content split (not token usage, finalize-pipeline-and-merge's); the spec does not say the plan leaves it
- Deliverable 3 gives tokens to the qualification and decision calibration records but the calibration record is built by decision-quorum-and-calibration (unstaged, not a dependency) and the qualification record by PLAN-34; order and ownership of the token members are unstated

### Second pass (HEAD `8233fffc`, after #177 and #179)

The specification pull requests #177 (capability specs: Proportion, Amended after refine, Verified by, the Capability Test Guard, the replan triggers `capability_conflict` and `intent_amended`) and #179 landed after the first pass. 4 claims were re-read: 1 corroborated, 1 contradicted (still re-scoped by this record), 2 unverifiable; stamped `checked_at: 8233fffc`.

Specification changes carried (not yet in the Deliverables above):

- #177 — phase-workflows/05 Metric Aggregates bullet Planning Weight (item counts of the intent record and non-empty lines of the rendered records against lines added and removed between base_ref and HEAD; ra…: Add to Open questions beside the caller-drift note: Planning Weight sits in the section the trace names whole but is not token usage; PLAN-61 must not delete or implement it; no staged package or package file names it (grep over change-map, finalize-pipeline-and-merge, runtime-metrics, token-usage finds nothing) so the owner is unstated; no Expected Surface change

Applicability:

- Deliverable 9 (no limits) is unaffected by the Capability Test Guard and Planning Weight: both are blocking-finding and size figures with no token or spend comparison; no spend trigger or unit was added to 08 or state-catalogue/12 (the two new triggers are capability_conflict and intent_amended)

## Dependencies and Sequencing

- Depends on: PLAN-31 (`slots-usage-and-budgets`); PLAN-34 (`role-qualification`)
- Blocks: PLAN-46 (`machine-onboarding-unit`); PLAN-58 (`operator-reports`); PLAN-60 (`runtime-metrics`)

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-61-token-usage.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

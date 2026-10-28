# Landing Analysis: PLAN-61 — Token usage (partial: the package stays open)

epic: pm-mcp-core-m2-m4
workstream: WS-04
pr: #204 (merged as 82bcbc2a, 2026-10-27, merge queue)

> Landing record for one plan that shipped **partially**. Source: inbox message `rb-61-token-usage-001.md` (runbook lane, opencode,
> deepseek-v4.1-flash/high, 2026-10-27T18:44:15Z), corroborated against git at `82bcbc2a`, the pull request through `ci`, the SonarCloud public
> API and the code. The message is a lead; every fact marked "checked" was read by me; no test was re-run.

Standing rules: r2, R01 to R14 and R16 to R19 named by the landing. **R01 (the package row and file removed) and R05 are not applied** (the package is not
done). The lane read the main checkout copy of its spec (`spec_source: main_checkout`), the version landed in #200 with the class-level surface of R19.

## Deliverable Fidelity vs Spec

Twelve deliverables. The lane counts **7 of 12 fully met**; I agree with that reading, with one correction to its D2 evidence.

| Deliverable | Verdict | Evidence |
|-------------|---------|----------|
| D1 umbrella (the sections implemented, the specification and watch reduced) | not done | Checked: the diff touches **one** specification file (`store-schemas/16`, a correction) and no watch file, no `doc/plans` file. Six ADRs, seven developer guides and `doc/Traceability.adoc` are in the diff. |
| D2 record everything raw, report only | met at the codec level | Checked: `HarnessUsage`, `ActivityEventMapper` and `ActivityEntry` changed, `ActivityEventMapperTest` in the diff. **Correction:** the lane cites `MetricsRow`/`MetricsCodec` columns, but this pull request touches neither (they came with #164 and #201); no settlement producer exists here. |
| D3 no money; records gain tokens with the split | **not fully met** | Checked: `TokenTotal`, `QualificationRecordCodec`, `JudgeCheckCodec` and `RoleGrader` changed; the decision-calibration codec does not exist in this repository. |
| D4, D5 reasoning pointer, null versus not-applicable | met | Checked: `ShippedUsageFixturesTest` and `ProfileStagesTest` in the diff (the lane names `UsagePointersTest`, which this pull request does not touch: it already existed). |
| D6, D7 unified figures, coverage and floor on every aggregate | met | Checked: `UsageAggregate` (new, 12 test methods in `UsageAggregateTest`). The lane's two mutation runs are its own account. |
| D8 running plan and epic representations | **not met as written** | Checked: `PlanTokens` and `EpicTokens` are new value types; **no production caller** (referenced by their own files in `src/main` only), `PlanTokensTest` covers both. The operator chose "New types in pm-workflow". |
| D9 no limits | met by absence | Checked: no spend policy, unit or projection in the diff; `PlanTokensTest` has the no-limit guard per the lane. |
| D10 verification records per figure, coverage, start context | met at stage, record and store level | Checked: `VerificationRecord`, `ProfileStages`, `HarnessEnrolment`, `HarnessEnrolmentStore` and their tests changed; the install-path handoff is owed to `plan-marshall-mcp`. |
| D11, D12 recorded harness outputs, repeated evaluation | not met | Synthetic fixtures by the operator's choice; the evaluation is an external action. |

**Whole diff, checked** (`git diff 82bcbc2a^1 82bcbc2a`): **37 paths (14 `A`, 23 `M`); +2,387/−76**: 14 main-source files (3 new: `UsageAggregate`, `PlanTokens`, `EpicTokens`),
8 test files, 14 documentation files (6 ADRs, 7 guides, `Traceability.adoc`) and one specification file.

**Surface delta** (`inbox landing-check`): 36 declared, 37 realized, **24 added and 23 declared-but-untouched**. Added: the bookkeeping and ADR/guide files, `VerificationRecord`,
`ActivityEntry`, the three new classes and their tests. Untouched: the nine specification parts and three watch files (D1 owed), the package file, and `ProfileValidator`,
`JudgeCheck`, `QualificationRecord`, `MetricsCodec`, `MetricsRow`, `MetricsStore`, `UsagePointers`, which already carried what the plan needed.

## Metrics and Anomalies

- **Checked CI:** #204 `overall_status success` (build, integration-tests, sonar-build green on the PR-event run); **SonarCloud `pullRequest=204`: gate OK, 0 open issues**;
  `branch=main`: **2 open issues, gate ERROR** (the known `S9428` and `S112`, unchanged by this plan).
- **The red check the lane had the operator accept** ("merge despite red `build / conclusion`") **was the push-event Sonar race** (memory note: a rerun of the failing run clears
  it); at the merge head the checks read success overall. The lane asked the operator instead of rerunning.
- **CodeRabbit** reviewed four heads (all findings fixed, one advisory nitpick left), quota-limited five times; Sourcery refused on its own quota.
- **A library without a production caller, again:** `UsageAggregate`, `PlanTokens`, `EpicTokens` have none; no representation, report or view consumes them.
- **Rule breaks the lane lists:** first edits written to the main checkout and moved by `cp` (**the fourth of the last five runbook lanes**), a Links clause not read verbatim, a routed `verify` that
  hit the daemon bound. The links check reported 10 broken links in the sibling documentation checkout, which the lane calls pre-existing (not re-run).
  `total_tokens=unknown` (38th), `plugin_version=unknown`; the landing message lacks both keys.

## Routing and Merge Behavior

Personas as stated (implementer, module-tester, code-reviewer for two rounds and every comment). Four operator questions, all answered: proceed despite executor skew; synthetic fixtures with D11 partial;
D8 types in `pm-workflow`; merge with the red check accepted. Merged through the merge queue. The lane's worktree and branch are gone (`git worktree list`).

## Collision Analysis

`corpus declaration-currency` over the 37-path footprint: 46 specs collide, 58 clean. Of the running and just-started plans: **PLAN-97 shares 7 files** (`ProfileStages`, `VerificationRecord`, the profile and
launch classes; disclosed in its spec; its lane merges the base before its gate), **PLAN-43, 105 and 106 share none**. Staged: PLAN-98 (5), PLAN-99 (3, `HarnessUsage`, `UsageAggregate`, `store-schemas/16`), PLAN-46 (1).
**ADR numbers taken by this plan:** `core-store` 048 (next 049), `core-role` 005 (006), `core-profile` 007 (008), `runtime-launch` 007 (008), `runtime-worker` 013 (014), `workflow-plan` 018 (019).
**PLAN-43 names `core-store` 048 and `workflow-plan` 018 in its spec: both are now taken**; the rule R11 (numbers at write time against `origin/main`) covers it, the lane renumbers on its base merge.

## Reconciliation Actions

- Row PLAN-61: `running` → `shipped`; `pr` `#204`; `landing` `landings/PLAN-61.md`; `plan_marshall_plan_id` `rb-61-token-usage`. The status records the merged pull request, **not the package**: `token-usage` stays `open` in the index.
- `epic.md`: annotation; a remainder defect for `token-usage`.

## Follow-Ups

1. **The package `token-usage` is still open, so everything it blocks stays blocked in the index** (PLAN-46, 58, 60, transitively 25 packages). What remains is mostly not engine work: D1 (the specification and watch reduction, the package row),
   D8 (a consumer for `PlanTokens`/`EpicTokens`), D11 and D12 (recorded runs and an evaluation), D3's calibration codec (no home here) and the install-path handoff (`plan-marshall-mcp`). **An operator decision:** close the package with the
   residue moved to named owners, or stage a remainder plan.
2. The CodeRabbit nitpick (tests for four refusal branches of the non-usage stage).
3. The lane's choice to ask the operator about the Sonar race rather than rerun is worth a note in the lane's lessons-routing inbox (not filed here).

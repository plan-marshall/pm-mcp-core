envelope_version=1
sender_type=plan
sender_id=rb-61-token-usage
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-27T18:44:15Z

```landing-facts
schema=landing-facts/1
plan_id=rb-61-token-usage
epic=pm-mcp-core-m2-m4
pr=#204
merge_state=merged
cleanup_owed=false
deliverables_total=12
deliverables_done=7
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2017
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
step.sonar-roundtrip.open_issue_count=0
step.sonar-roundtrip.open_issue_rules=none
```
guard: none on this harness (unenforced)

# PLAN-61 token-usage — landing

Standing rules: r2, R01–R14, R16–R19 (read at start; the spec names r1, r2 binds a plan started 2026-10-27).
Merged in `82bcbc2a` (PR #204, merge queue, merge_group run success).

## 1. Deliverables

Seven of twelve deliverables are fully met. Evidence order is `test-first` / `mutation-after` / `none`; the words
“fail-first” are not used (no test was written before its fix).

| D | Verdict | Evidence |
|---|---|---|
| 1 — umbrella (implements the sections) | **not fully met**: the specification/implementation-watch deletion, the `doc/plans` index row and the package file are owed; D11/D12 and the D8 consumer are not done | trace matrix `.plan/temp/rb-61-token-usage/trace-matrix.md`; 6 ADRs, 6 developer guides, Traceability lines, `store-schemas/16` split correction |
| 2 — record everything raw, report only | met (model/codec level; no settlement producer exists in this repository) | `MetricsRow`/`MetricsCodec` columns, `ActivityEventMapperTest` (7-member turn usage) — order `none` |
| 3 — no money shown; records gain tokens with split | **not fully met**: `TokenTotal` + qualification/judge-check codecs carry the split and `RoleGrader` sums it, but the decision-calibration codec does not exist in this repository (schema only) | `QualificationRecordCodecTest` — order `none` |
| 4 — reasoning pointer, not-applicable where included | met | `UsagePointersTest`, `ShippedUsageFixturesTest` (claude reasoning not_applicable) — order `none` |
| 5 — null vs not_applicable | met | `UsagePointersTest` both arms, `ShippedUsageFixturesTest.absentAndNotApplicableStayDistinct` — order `none` |
| 6 — unified figures, one derivation | met | `UsageAggregateTest`, paired reasoning test — order `mutation-after`: `HarnessUsage.tokensIn()` mutated at `09adf9d4`, 3 of 10 tests failed (log `ba903a322325483b92c32002fb1384c4`), restored |
| 7 — coverage and floor on every aggregate | met | `UsageAggregateTest` (sum, floor, empty total, refused row, population refusal, groupings, `plus` identity) — order `none` |
| 8 — running plan and epic representations | **not met as written**: `PlanTokens`/`EpicTokens` are the engine value types per the operator’s choice, but no representation in this repository produces or consumes them | `PlanTokensTest`, incl. the no-limit guard — order `none` |
| 9 — no limits | met by absence | no spend policy/unit/projection added; `PlanTokensTest.carriesNoLimitBudgetOrProjection` |
| 10 — verification records per figure; coverage; start context | met at stage/record/store level; the install-path handoff to the enrolment is owed to `plan-marshall-mcp` | `ProfileStagesTest`, `VerificationRecordTest`, `HarnessEnrolmentStoreTest` — order `mutation-after`: `UsageResult.coverage()` forced `COMPLETE` at `ebfa99b3`, 2 tests failed (log `a0aa16dd0058459e84265c0178eef0cf`), restored |
| 11 — recorded-output fixtures for each shipped harness/version | **not met** (synthetic, profile-driven fixtures only, by operator decision; codex/antigravity not installed; warm-worker turns not recorded) | `ShippedUsageFixturesTest` |
| 12 — evaluation repeated per harness/version | **not met** (external evaluation action) | — |

Per-head tests: CI’s build (7908→7982 tests) and integration-tests jobs green on every head; local runs green on
`28b5ea1e`, `c045c5a2`, `8371fe58`, `581cbc9e`, `e80572b8`.

## 2. Personas and skills

`persona-code-reviewer` for both self-review rounds and every PR comment; `persona-implementer` for production code;
`persona-module-tester` for tests. The spec declares no `Skills:`/`Persona:` lines (`skills: none named`); no skill was
added for surface drift. Two new 5-line sections of the developer guides and the `workflow-epic` guide were added for the
changed packages.

## 3. Hypotheses

- HYPOTHESIS “the Expected Surface is inferred from the Where line” — **confirmed with corrections**: all 21 named
  paths exist; additions recorded: `VerificationRecord.java` (record shape), and the new classes `UsageAggregate`,
  `PlanTokens`, `EpicTokens`. Refuted in part: `core.role` holds no decision-calibration record, so D3’s calibration
  half has no codec here.
- Verify-first clause: index row was `open`; `role-qualification` shipped; the package still has no `blocked` state.

## 4. Findings

- **Self-review round 1** (agent, `persona-code-reviewer`): F1 aggregate reported `0` for a fully unmeasured population;
  F2 `RoleGrader` dropped the split; F3 report figure keys vs schema; F5 `plus(empty)`; F6 `byPhase` on null phase; F9
  missing paired reasoning test; F10–F14 doc drift; F15 start_context on any stage; F18 absent figure; F19 cost
  not_applicable; F20 missing epic guide. All fixed (`28b5ea1e` and earlier).
- **Self-review round 2**: verified the fixes, found the `plus` split identity and an invariant hole; fixed; dead
  `reportFigures()` removed. Two rounds, nothing open except the deliverable gaps.
- **CodeRabbit** (qualifying review, `quota_limited`): round 1 on `24ade5d5` — 4 findings (ADR handoff, `RoleClass`
  derivation, report figure mapping, `USAGE_KEYS`) fixed in `c045c5a2`, threads resolved. Round 2 on `8371fe58` — 3
  findings + 1 nitpick (floor wording, launch-output handoff wording, `REPORT_FIGURES` derivation, first-turn edges)
  fixed in `f1e4a5d5`, threads resolved. Round 3 on `f1e4a5d5` — outside-diff finding (unlisted start-context model)
  fixed in `581cbc9e`. Round 4 on `e80572b8` — one advisory nitpick (more refusal-branch tests), explicitly
  non-blocking; left open as residue.
- **CI**: one transient `sonar-build` failure at `346e06a4` (organization “non main branches” restriction); a rerun
  reproduced it, later heads were green; the operator accepted the red check before the merge.
- **SonarCloud**: 23 new issues after the first head, all fixed; final read on the merge head: 0 open issues, gate OK.
- **PR comments**: no human review comments; bot notices only (rate limits, Sonar).

## 5. Reviewers

- **coderabbit** (required, quota_limited): reviewed `24ade5d5` (13:05), `8371fe58` (15:50), `f1e4a5d5` (17:02),
  `e80572b8` (18:05) — each with the `final_review_risk_coverage` marker `kind=reviewed`; all actionable findings fixed
  and threads resolved. Refused on quota at 11:30, 12:43, 13:33, 14:44, 17:28 (waits ≥90 min applied once, then the
  escalated `@coderabbitai full review`); “already reviewed the last commit” at 16:02 and 17:05. Standing
  authorization applied at the merge: qualifying review on the current head, findings handled, post-push re-trigger
  recorded.
- **sourcery** (optional): rate-limited on its own 7-day budget (5 days remaining), so no review; it does not gate.

## 6. Operator questions

1. “executor-skew `could_not_check` — proceed or regenerate executor?” — options {Proceed with the run, Stop and
   regenerate executor}; **selected “Proceed with the run”** (first ask aborted by a server restart; re-asked).
2. “D11/D12 recorded harness outputs — how to handle?” — {You provide recordings, Synthetic fixtures D11 partial, Report
   D11/D12 not met}; **selected “Synthetic fixtures, D11 partial”**.
3. “D8 representation home?” — {New types in pm-workflow, Aggregate API only D8 partial, Stop and re-scope}; **selected
   “New types in pm-workflow”**.
4. “Merge despite red `build / conclusion` from the Sonar org restriction?” — {Merge with the red check accepted, Leave
   the PR open, Stop and retry later}; **selected “Merge with the red check accepted”**.

## 7. Rule check

- **Guard: none on this harness (unenforced)**, so no guard log exists; the run’s own actions were checked.
- **Kept**: no `gh`/`glab`, no direct build wrapper, no test/lint binaries, no `python3 -c`/`python3 script.py` inside the
  repository, no `sleep`, no `git stash`/`restore`/`checkout --`/`rebase`/force-push, no staging-all, temp files only
  under `.plan/temp/`, no `.plan/orchestrator` edits, no secrets read.
- **Deviations reported**: (a) my first edits (D6/D7) were made in the main checkout before I corrected them; they were
  copied into the worktree and reverted in the main checkout with `rm`/reverse edits — a rule bend, caught and fixed
  before any commit; (b) I used `cp` to move those edits into the worktree, a shell file operation; (c) the spec’s final
  Done-when Links clause exceeded the harness line cap and was not read verbatim; (d) the links check runs a sibling
  repository script by the standing rule R17 (outside the repository read, sanctioned by the rule); (e) the routed
  `verify` hit daemon bounds at `28b5ea1e`-era and `e80572b8` (explicit 930s, then learned 983s); each expiry is
  recorded and the split form is empty, so the gate canonical (`verify -Ppre-commit`, green on the head) is the run
  that counts.
- **Restarts**: one server restart (11:xx); the lane text was re-read and the aborted question re-asked.
- **Spec read**: `spec_source: main_checkout`; no `spec_differ` marker (no ledger copy existed at resolve time).

## 8. Build server

Setup preflight: `ready`. Every build was daemon-routed (`mechanism: daemon_longpoll`, requested=auto). Bound expiries:
the explicit 930s on two `verify`/pre-commit runs and one learned 983s `verify`; no `killed` job. Integration profiles
(`pm-core` 13, `pm-runtime` 30) and `-Pcoverage` green. `runtime_stamp_reason`: none; `plugin_version` resolved as
`unknown` (the flat install carries no version directory).

## 9. Residue

- D8 has no representation/view consumer in this repository; D11’s recorded runs (including warm-worker turns) and D12’s
  evaluation are owed.
- The specification/implementation-watch deletion, the `doc/plans/README.adoc` row removal and the package-file deletion
  are owed because the package is not complete.
- The install-path handoff `UsageResult` → `HarnessEnrolment.UsageReport` is owed to `plan-marshall-mcp`
  (`UsageReport.of` is the mapping boundary).
- D3’s decision-calibration codec does not exist here.
- CodeRabbit’s last review left one non-blocking nitpick (tests for the non-usage-stage, non-array, missing-tokens and
  negative-tokens refusal branches).
- The links check reports 10 broken links, all pre-existing in the sibling `plan-marshall-documentation` checkout.
- `plugin_version` is `unknown`; `executor_version` 0.1.2017.

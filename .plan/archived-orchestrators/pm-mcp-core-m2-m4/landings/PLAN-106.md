# Landing Analysis: PLAN-106 — Plan intent and outline records (partial: the library half)

epic: pm-mcp-core-m2-m4
workstream: WS-03
pr: #210 (merged as 70bee082, 2026-10-28, merge queue)

> Landing record for one plan that shipped **partially**. Source: inbox message `rb-106-plan-intent-and-outline-records-001.md` (runbook lane, opencode,
> deepseek-v4.1-flash/high, 2026-10-28T10:45:52Z), corroborated against git at `70bee082`, the pull request through `ci` and the SonarCloud public API.
> The message is a lead; every fact marked "checked" was read by me; no test was re-run. The lane read the main checkout copy of its spec
> (`spec_source: main_checkout`, no `spec_differ`).

## Deliverable Fidelity vs Spec

Four deliverables (D1 to D4 of the split: the intent record, the outline record and the planning parts, the `review-simplify` ladder, the constraints and non-goals).
The lane counts **1 of 4 fully met**; I agree with that reading.

| Deliverable | Verdict | Evidence |
|-------------|---------|----------|
| D1 the intent record | met at the library level | Checked: `IntentRecord`, `IntentStore`, `IntentCodec` exist in `pm-core/.../core/store/` on `origin/main`; `intent-record` is in `SchemaRegistry.defaults()` (`SchemaRegistry.java:71`); `IntentStore` has 4 other main-source users. The entry-point wiring (`submit:refine`, the light `submit:outline`, re-planning) is the assembly's. |
| D2 the outline record and the planning parts | **not met as written** | Checked: `OutlineRecord`, `OutlineStore`, `OutlineCodec` exist (3 other main users of `OutlineStore`); `outline-record` is in the registry. The phase-owned parts (builder check asked, review results, escalation, scope-held Q-Gate row, `default:mutation`, knowledge search) are workflow units of `plan-marshall-mcp`. |
| D3 the `review-simplify` restraint ladder | **not met as written** | Checked: `SimplifyStep` has 0 other main-source users. The server records the disposition; applying the accepted subset is the writer task's (`finalize.simplify-apply`, the assembly's). The "no write tool, never the writer slot" guarantee is declarative on `ReviewTask`, not derived from the role registry. |
| D4 constraints and non-goals | **not met as written** | Checked: `SelfReviewSubmission` and `BuilderCheck` have 0 other main-source users; `OutlineValidator` exists (`core/spi/validation`). The builder check *requires* the operator question to be the operator's; producing and asking it is the `refine.builder-check` task's. |

**Whole diff, checked** (`git show --stat 70bee082`): **54 files, +8,348/−80**: 10 plus 9 plus 2 paths under `core/store`, 7 under `workflow/finalize`, 6 under `core/spi/validation`, 2 under `workflow/verify`,
developer guides in `pm-core` and `pm-workflow`, two `phase-workflows` specification parts, one `doc/plans` file, one watch file.

**Surface delta** (`inbox landing-check`): `unmeasured` (declared and realized paths not supplied to the check). The lane reports `doc/plans/phase-sub-workflows.adoc` as an undeclared shared file
(the package file's retired-watch links); R19 treats it as expected.

## Metrics and Anomalies

- Tokens: `unknown` (the harness measured none; the 41st landing).
- Self-review: two code rounds, **budget exhausted at the three-round limit** (round 1 reported 17 findings, 13 fixed; round 2 three small regressions, fixed in `907a2e2d`).
- Anomalies: the pre-run executor check answered `could_not_check` (operator: proceed); an executor build (`install -Ppre-commit -pl pm-core -DskipTests`) and three shell `ls`/`find`/`grep` calls by a sub-agent
  are listed by the lane as rule breaks; the first routed build failed on a relative `--project-dir`, every later gate ran in-process.

## Routing and Merge Behavior

- Review: CodeRabbit formal review on `7ba5962a`, 12 actionable and 2 nitpicks, all fixed or answered, every thread resolved; it was rate-limited on the last three pushes (standing authorization used for the merge head `a5125d9e`).
  Sourcery refused on size (150,000 diff characters) three times, not required.
- CI/merge: green on every reviewed head, merged through the merge queue as `70bee082` (checked: `ci pr view` state `merged`, `git merge-base --is-ancestor` true).
- Sonar: the lane's `step.sonar-roundtrip.open_issue_count=n/a` (no local Sonar block). **Checked: `pullRequest=210` gate OK, 0 open issues**, which agrees with the lane's own R18 read (106, 6, 3, 8 along the way, 0 at the merge head).
  `branch=main` reads 9 open issues after #214 (see PLAN-97).
- Operator questions: executor check (proceed), R05 handling of the implemented specification sections ("keep schemas, add implemented note").

## Reconciliation Actions

- [x] row `status` → `shipped` — the library half shipped; the package `phase-sub-workflows` stays OPEN on PLAN-43 and PLAN-105
- [x] row `pr` stamped `#210`
- [x] row `landing` stamped `landings/PLAN-106.md`
- [x] row `plan_marshall_plan_id` stamped `rb-106-plan-intent-and-outline-records`
- [x] epic.md narrative reconciled (Queue annotation, Open Defect for the unowned assembly halves)
- [x] resume anchor updated; `queue-view.md` regenerated

## Follow-Ups

- **The assembly halves of D2 to D4 and the D1 entry points have no staged owner in this ledger.** They are `plan-marshall-mcp` resources (`refine.builder-check`, `finalize.simplify-apply`, the phase units that exercise
  the library parts). Recorded as an Open Defect; whether they belong to `plan-marshall-mcp`'s `default-units-and-drift-tests` (blocked) or to a package of their own is the operator's decision.
- The library parts (`SimplifyStep`, `BuilderCheck`, `SelfReviewSubmission`) have no production caller in this repository by design.
- Retired watch items `PM-WATCH-PHASE-106/107/110/111` are removed here; the documentation repository's articles still link two of them (a `plan-marshall-documentation` follow-up).
- The package file `phase-sub-workflows` and its index row are removed by the **last** of PLAN-43, PLAN-105 and PLAN-106 to land (R01): not this plan.
- Two CodeRabbit nitpicks accepted as trade-offs (schema-registry parity, clone-index memory).

# Landing Analysis: PLAN-91 — Close the plan instance alignment advisories

epic: pm-mcp-core-m2-m4
workstream: WS-03
pr: #131 (https://github.com/plan-marshall/pm-mcp-core/pull/131), merged as squash `3d4baf7` through the merge queue on 2026-10-21

> Landing record for one shipped plan. Lives at `landings/PLAN-91.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> reconciliation contract.

Source of the landing: inbox message `plan-91-plan-instance-advisories-008.md` (`kind: landing`, sender `plan-91-plan-instance-advisories`, `/plan-marshall` on opencode, plan-reported). `inbox landing-check`: `complete: false`, `missing_keys` all nine required keys (`schema`, `plan_id`, `pr`, `merge_state`, `cleanup_owed`, `deliverables_total`, `deliverables_done`, `total_tokens`, `steps`): the message is narrative with a front-matter block and carries no `landing-facts` block. Seven candidate-lesson messages (`-001` to `-007`) came with it.

## Deliverable Fidelity vs Spec

| # | Deliverable | Verdict | Evidence (read at `3d4baf7`) |
|---|-------------|---------|------------------------------|
| 1 | Untested arms of `PlanInstanceStore.previousHeader` | met | `PlanInstanceStoreTest` +64 lines; no production change to `PlanInstanceStore.java` (the spec's own premise: two of four arms were already covered; plan-reported) |
| 2 | Untested arms of `InstancePruner` | met | new `InstancePrunerTest` (137 lines: adopt prune, link selector, other-lane guard) |
| 3 | One definition of the lane entry ids | met | new `workflow.verify.LaneEntryIds`, read by `InstancePruner`, `InitInvariants` and `RefineInvariants` (grep of `LaneEntryIds`); `LaneEntryIdsTest` added. The spec named `InitInvariants.LIGHT_GATE` as the second holder; the plan found it was `RefineInvariants`' (plan-reported, consistent with the three call sites) |
| 4 | Per-lane rework in the fixtures | met | new fixture `pm-workflow/src/test/resources/plan/per-lane-rework.pmwf` (156 lines), `PlanFixtures.perLaneComposer()`, `PlanInstanceComposerTest.PerLaneRework`; ADR `workflow-plan/017` |
| 5 | Axes items 1 and 2 of Workflow DSL part 9 disagree | met | `09-plan-instance.adoc` item 1 now binds the variant at the plan's start through the level chain to the shipped `plan_defaults` and says no question is asked at the call site; the removed first-use rule was swept out of further documents (`machine-onboarding-unit.adoc`, `state-catalogue` 02 and 18, `store-schemas/14`, the developer guides `workflow-plan` and `workflow-verify`, `Traceability.adoc`) |

The landing's own table lists five advisories and "7 deliverables" in its front matter: the spec carries five deliverables, the plan counted two more (the sweep and the ADRs). Plan-reported; not a gap.

## Surface delta (measured by the orchestrator)

20 files merged. 6 covered by declared entries; 14 undeclared: `Traceability.adoc` (a shared file, by convention no declared surface), `machine-onboarding-unit.adoc` (a package file), two `state-catalogue` parts, `store-schemas/14`, ADRs `workflow-plan/017` and `workflow-verify/030`, the developer guide `workflow-verify.adoc`, `LaneEntryIds.java`, `RefineInvariants.java`, `PlanFixtures.java`, `PlanInstanceComposerTest.java`, `LaneEntryIdsTest.java` and the fixture `plan/per-lane-rework.pmwf`. 2 declared entries untouched: `PlanInstanceStore.java` (no production defect), and `unit-fixtures/defaults/` (the new fixture lives in `plan/`). The undeclared files all follow from deliverables 3 to 5; the spec under-declared the sweep of deliverable 5.

## Metrics and Anomalies

- `total_tokens`, `harness`, `model`: not in the message (no `landing-facts`). Stated by the plan: 9 tasks, 11 commits, 7 deliverables.
- Review (plan-reported): functional review 7/7 met, 20 criteria evidenced, two advisory false self-reports about the `pre-commit` profile, retracted and then corrected for currency (both in the decision log); code review closed after two correction commits (`ea2bd74`, `1e9da87`); one advisory (ADR 030's premise omits `InstancePruner.LANE_SPLIT`) kept in the decision log.
- Verification (plan-reported): `verify -Ppre-commit` green three times, `verify`, `-Pcoverage` and `-Pintegration-tests` green. CI: `ci checks status` `success`. SonarCloud: gate OK, 0 open issues on the pull request (API, 2026-10-21).
- Review threads: every inline thread is resolved at merge (the CodeRabbit comments on `machine-onboarding-unit.adoc`, `09-plan-instance.adoc` and ADR 017; one deletion request on the package file was withdrawn by the bot after the plan's reply).
- Environmental, plan-reported: the machine's inotify watch pool, exhausted by the editor's file watcher, failed `SubprocessJobRunnerTest.escalated` on an untouched file until a machine-level `files.watcherExclude` was added; a duplicate CI run died inside the SonarSource analyzer with an org-level non-main-branch permission error and was superseded by the green run. Neither is a defect of the code; the first matters to PLAN-84 (it runs the same test class).

## Routing and Merge Behavior

Merged through the merge queue as a squash; no ejection. PLAN-85's pull request #129 was open at the same time and shares no file with this landing.

## Staged-spec impact

- PLAN-46 `machine-onboarding-unit`: its package file `machine-onboarding-unit.adoc` changed (deliverable 10 now reads "the variant of each axis at the plan's start"); the spec was regenerated from it and its claims re-stamped.
- Specs declaring `workflow/plan/`, `workflow/verify/` or the plan-instance specification parts (PLAN-43, 62, 71): their declarations stay valid; the claims about those directories were re-grounded by `cleanup` at `99971f3` and this landing touched none of their cited symbols (`LaneEntryIds` is new).
- PLAN-71 `plan-instance-versions` stays staged and is untouched: it builds on `InstancePruner`/`PlanInstanceStore`, whose behaviour this landing did not change.

## Reconciliation Actions

- Row `PLAN-91`: `shipped`, `pr=#131`, `landing=landings/PLAN-91.md`, `plan_marshall_plan_id=plan-91-plan-instance-advisories`; message `-008` archived.
- Candidate lessons `-001` to `-007` (`plan-marshall:*` and `pm-documents:ref-documentation`) forwarded to plan-marshall's `lessons-routing` inbox as `pm-mcp-core-m2-m4-396` to `-402`, archived here.
- Open Defect: the landing carried no `landing-facts` block.
- Owed outside (plan-reported): the concept and user documentation of the slice and the roadmap check-off in `plan-marshall-documentation`, at the milestone exit.

## Follow-Ups

- Nothing in this repository is owed by PLAN-91 itself. The plan-instance Open Defect "Plan instance code behind its specification" now rests on PLAN-71 alone.

envelope_version=1
sender_type=plan
sender_id=plan-91-plan-instance-advisories
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-21T18:14:17Z

---
kind: landing
plan_id: plan-91-plan-instance-advisories
epic: pm-mcp-core-m2-m4
workstream: WS-03
spec: .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-91-plan-instance-alignment-advisories.md
pr_number: 131
pr_url: https://github.com/plan-marshall/pm-mcp-core/pull/131
merge_commit_sha: 3d4baf7dc8efc49bf1f13e1ab1d4a01b4abe534a
outcome: landed
deliverables: 7
tasks: 9
commits: 11
---

# Landing — PLAN-91: close the plan instance alignment advisories

## Outcome

All five PLAN-70 advisories are closed and merged to `main` as `3d4baf7` (PR #131, squash).

| # | Advisory | Resolution |
|---|----------|------------|
| 1 | Untested `PlanInstanceStore.previousHeader` arms | Two of the four arms were already covered; the parser-refused and header-less arms added with version-1 survival assertions |
| 2 | Untested `InstancePruner` arms | Four arms were already covered by `PlanInstanceComposerTest`; a dedicated `InstancePrunerTest` adds the adopt prune, the link selector and the other-lane guard |
| 3 | Duplicated lane entry ids | One `LaneEntryIds` holder in `workflow.verify` read by `InstancePruner`, `InitInvariants` and `RefineInvariants`; the advisory's second holder was `RefineInvariants`, not `InitInvariants` |
| 4 | Flat fixtures prove no per-lane rework | New fixture `src/test/resources/plan/per-lane-rework.pmwf` with `PlanFixtures.perLaneComposer()` and `PlanInstanceComposerTest.PerLaneRework` |
| 5 | Axes items 1 and 2 disagree | Item 1 corrected to bind at the plan's start through the level chain to the shipped `plan_defaults`; the removed rule swept out of nine further documents |

## Review record

- Functional review: 7/7 deliverables met, 20 criteria evidenced. Two advisory false self-reports about the `pre-commit` profile — retracted, then corrected once more for currency, both recorded in the decision log.
- Code review: closed after two correction commits (`ea2bd74`, `1e9da87`) that completed the axis-binding sweep. One advisory (ADR 030's "only ids read outside workflow.verify" premise omits `InstancePruner.LANE_SPLIT`) is recorded in the decision log, not filed as a finding.
- Unified finalize triage: 6 PR comments, 3 fixed, 1 fix task (TASK-009), 3 taken into account. TASK-009's fix is in the merge.

## Verification

`verify -Ppre-commit` green three times (at `aabda50`, `1e9da87`, `74d0858`), `verify`, `verify -Pcoverage` and `verify -Pintegration-tests` green. CI green on run `39635214089` for `1e9da87` and on the final run for `74d0858`.

## Environmental notes

- The machine's inotify watch pool was exhausted by the VS Code file watcher, failing `pm-runtime` `SubprocessJobRunnerTest.escalated` on an untouched file; a machine-level `files.watcherExclude` was added and the gate re-ran green.
- A duplicate CI run (`39634924569`) died inside the SonarSource analyzer on `pm-core/.../StoreSnapshot.java` and reported an org-level non-main-branch permission error; superseded by the green run.

## Owed to the milestone exit

The concept and user documentation of this slice and the roadmap check-off are owed in `plan-marshall-documentation`.

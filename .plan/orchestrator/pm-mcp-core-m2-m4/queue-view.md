<!-- GENERATED FILE — never hand-edit. Rendered from this epic's ledger (status.json, resume_anchor.md, queue/*.json) by `orchestrator regenerate-view --slug pm-mcp-core-m2-m4`. On a merge conflict in this file, do not merge it by hand: merge the source files, run `orchestrator regenerate-view --slug pm-mcp-core-m2-m4`, and `git add` the result. -->

# Queue view: pm-mcp-core work packages, roadmap Milestones 2 to 4

## START HERE

**Resume anchor**: Running: PLAN-07 workflow-dsl-parser-and-ast (PR #27; stale re-review setting, advised), PLAN-08 runtime-lifecycle, PLAN-11 enrolment-store. Emitted 2026-10-11, awaiting operator-confirmed launch: PLAN-36 code-analysis-base. Shipped: PLAN-01 (#20), PLAN-02 (#18), PLAN-03 (#19), PLAN-38 (#24), PLAN-04 (#25), PLAN-06 (#26), PLAN-05 (#28). Next: PLAN-09/12/14 wait on PLAN-11 (shared core.store); PLAN-10 and PLAN-16 wait on PLAN-07. Next action: record running on confirmation; analyze landings; land the ledger. Open for the operator: documentation of PLAN-01 (unowned), PLAN-04 (#26 there to merge), PLAN-06 (branch unpushed); one-review merge rule; unowned remainder of PLAN-02's roadmap item.
**Phase**: orchestrating
**Running**:
- PLAN-07 (WS-03) — plan=workflow-dsl-parser-and-ast
- PLAN-08 (WS-01) — plan=runtime-lifecycle
- PLAN-11 (WS-02) — plan=plan-11-enrolment-store
**Parked**:
- PLAN-19 (WS-05)
- PLAN-37 (WS-06)
**Queue** (staged, in order):
1. PLAN-09 (WS-01)
2. PLAN-10 (WS-03)
3. PLAN-12 (WS-02)
4. PLAN-13 (WS-03)
5. PLAN-14 (WS-02)
6. PLAN-15 (WS-02)
7. PLAN-16 (WS-03)
8. PLAN-17 (WS-03)
9. PLAN-18 (WS-04)
10. PLAN-20 (WS-04)
11. PLAN-21 (WS-03)
12. PLAN-22 (WS-06)
13. PLAN-23 (WS-01)
14. PLAN-24 (WS-03)
15. PLAN-25 (WS-05)
16. PLAN-26 (WS-05)
17. PLAN-27 (WS-04)
18. PLAN-28 (WS-05)
19. PLAN-29 (WS-04)
20. PLAN-30 (WS-04)
21. PLAN-31 (WS-04)
22. PLAN-32 (WS-05)
23. PLAN-33 (WS-05)
24. PLAN-34 (WS-05)
25. PLAN-35 (WS-06)
26. PLAN-36 (WS-07)
- PLAN-01 (WS-01) — plan=lock-manager-and-leases — PR #20 — landing=landings/PLAN-01.md — status: shipped
- PLAN-02 (WS-03) — plan=primitive-spi-and-validators — PR #18 — landing=landings/PLAN-02.md — status: shipped
- PLAN-03 (WS-01) — plan=workspace-confinement — PR #19 — landing=landings/PLAN-03.md — status: shipped
- PLAN-04 (WS-01) — plan=plan-04-service-unit — PR #25 — landing=landings/PLAN-04.md — status: shipped
- PLAN-05 (WS-08) — plan=http-baseline — PR #28 — landing=landings/PLAN-05.md — status: shipped
- PLAN-06 (WS-02) — plan=plan-06-store-substrate — PR #26 — landing=landings/PLAN-06.md — status: shipped
- PLAN-38 (WS-09) — plan=plan-38-review-bot-re-review — PR #24 — landing=landings/PLAN-38.md — status: shipped

## Ordered Queue

| # | Plan | Workstream | Status | Surface (expected) |
|---|------|------------|--------|--------------------|
| 1 | PLAN-07 | WS-03 | running | doc/plans/README.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/ast/; pm-workflow/src/main/java/de/planmarshall/workflow/dsl/; pm-workflow/src/test/java/de/planmarshall/workflow/ast/; pm-workflow/src/test/java/de/planmarshall/workflow/dsl/; pm-workflow/src/test/resources/ |
| 2 | PLAN-08 | WS-01 | running | doc/plans/README.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/start/; pm-runtime/src/test/java/de/planmarshall/runtime/start/ |
| 3 | PLAN-09 | WS-01 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/store/; pm-runtime/src/main/java/de/planmarshall/runtime/start/; pm-runtime/src/test/java/de/planmarshall/runtime/start/ |
| 4 | PLAN-10 | WS-03 | staged | doc/plans/README.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/verify/; pm-workflow/src/test/java/de/planmarshall/workflow/verify/ |
| 5 | PLAN-11 | WS-02 | running | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/store/; pm-runtime/src/main/java/de/planmarshall/runtime/security/; pm-runtime/src/test/java/de/planmarshall/runtime/security/ |
| 6 | PLAN-12 | WS-02 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/queue/; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/queue/; pm-core/src/test/java/de/planmarshall/core/store/ |
| 7 | PLAN-13 | WS-03 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/store/; pm-workflow/src/main/java/de/planmarshall/workflow/engine/; pm-workflow/src/test/java/de/planmarshall/workflow/engine/ |
| 8 | PLAN-14 | WS-02 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/store/ |
| 9 | PLAN-15 | WS-02 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/config/; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/config/; pm-core/src/test/java/de/planmarshall/core/store/ |
| 10 | PLAN-16 | WS-03 | staged | doc/plans/README.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/diagram/; pm-workflow/src/test/java/de/planmarshall/workflow/diagram/; pm-workflow/src/test/resources/ |
| 11 | PLAN-17 | WS-03 | staged | doc/plans/README.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/finalize/; pm-workflow/src/main/java/de/planmarshall/workflow/unit/; pm-workflow/src/main/java/de/planmarshall/workflow/verify/; pm-workflow/src/test/java/de/planmarshall/workflow/finalize/; pm-workflow/src/test/java/de/planmarshall/workflow/unit/; pm-workflow/src/test/java/de/planmarshall/workflow/verify/ |
| 12 | PLAN-18 | WS-04 | staged | doc/plans/README.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/job/; pm-runtime/src/test/java/de/planmarshall/runtime/job/ |
| 13 | PLAN-19 | WS-05 | parked | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/profile/; pm-core/src/test/java/de/planmarshall/core/profile/ |
| 14 | PLAN-20 | WS-04 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/service/; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/service/; pm-core/src/test/java/de/planmarshall/core/store/; pm-runtime/src/main/java/de/planmarshall/runtime/job/; pm-runtime/src/test/java/de/planmarshall/runtime/job/ |
| 15 | PLAN-21 | WS-03 | staged | doc/plans/README.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/engine/; pm-workflow/src/test/java/de/planmarshall/workflow/engine/ |
| 16 | PLAN-22 | WS-06 | staged | doc/plans/README.adoc; pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ |
| 17 | PLAN-23 | WS-01 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/store/; pm-runtime/src/main/java/de/planmarshall/runtime/start/; pm-runtime/src/test/java/de/planmarshall/runtime/start/ |
| 18 | PLAN-24 | WS-03 | staged | doc/plans/README.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/plan/; pm-workflow/src/test/java/de/planmarshall/workflow/plan/ |
| 19 | PLAN-25 | WS-05 | staged | doc/plans/README.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/engine/; pm-workflow/src/main/java/de/planmarshall/workflow/hypermedia/; pm-workflow/src/test/java/de/planmarshall/workflow/engine/; pm-workflow/src/test/java/de/planmarshall/workflow/hypermedia/ |
| 20 | PLAN-26 | WS-05 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/role/; pm-core/src/test/java/de/planmarshall/core/role/; pm-workflow/src/main/java/de/planmarshall/workflow/model/; pm-workflow/src/test/java/de/planmarshall/workflow/model/ |
| 21 | PLAN-27 | WS-04 | staged | doc/plans/README.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/job/; pm-runtime/src/test/java/de/planmarshall/runtime/job/ |
| 22 | PLAN-28 | WS-05 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/queue/; pm-core/src/test/java/de/planmarshall/core/queue/; pm-workflow/src/main/java/de/planmarshall/workflow/task/; pm-workflow/src/test/java/de/planmarshall/workflow/task/ |
| 23 | PLAN-29 | WS-04 | staged | doc/plans/README.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/launch/; pm-runtime/src/main/java/de/planmarshall/runtime/security/; pm-runtime/src/test/java/de/planmarshall/runtime/launch/; pm-runtime/src/test/java/de/planmarshall/runtime/security/ |
| 24 | PLAN-30 | WS-04 | staged | doc/plans/README.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/worker/; pm-runtime/src/test/java/de/planmarshall/runtime/worker/ |
| 25 | PLAN-31 | WS-04 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/store/; pm-runtime/src/main/java/de/planmarshall/runtime/launch/; pm-runtime/src/test/java/de/planmarshall/runtime/launch/ |
| 26 | PLAN-32 | WS-05 | staged | doc/plans/README.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/consult/; pm-workflow/src/test/java/de/planmarshall/workflow/consult/ |
| 27 | PLAN-33 | WS-05 | staged | doc/plans/README.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/task/; pm-workflow/src/test/java/de/planmarshall/workflow/task/ |
| 28 | PLAN-34 | WS-05 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/role/; pm-core/src/test/java/de/planmarshall/core/role/; pm-workflow/src/main/java/de/planmarshall/workflow/model/; pm-workflow/src/test/java/de/planmarshall/workflow/model/ |
| 29 | PLAN-35 | WS-06 | staged | doc/plans/README.adoc; pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ |
| 30 | PLAN-36 | WS-07 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/analysis/; pm-core/src/main/java/de/planmarshall/core/findings/; pm-core/src/test/java/de/planmarshall/core/analysis/; pm-core/src/test/java/de/planmarshall/core/findings/; src/guard-controls/ |
| 31 | PLAN-37 | WS-06 | parked | doc/plans/README.adoc; pm-core/src/test/java/de/planmarshall/core/differential/ |

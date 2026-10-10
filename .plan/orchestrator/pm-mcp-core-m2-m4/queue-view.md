<!-- GENERATED FILE — never hand-edit. Rendered from this epic's ledger (status.json, resume_anchor.md, queue/*.json) by `orchestrator regenerate-view --slug pm-mcp-core-m2-m4`. On a merge conflict in this file, do not merge it by hand: merge the source files, run `orchestrator regenerate-view --slug pm-mcp-core-m2-m4`, and `git add` the result. -->

# Queue view: pm-mcp-core work packages, roadmap Milestones 2 to 4

## START HERE

**Resume anchor**: Running: PLAN-04 service-unit (PR #25 open), PLAN-06 store-substrate, PLAN-07 workflow-dsl-parser-and-ast. Emitted, awaiting operator-confirmed launch: PLAN-05 http-baseline (scope raised to 4). Shipped: PLAN-01 (#20), PLAN-02 (#18), PLAN-03 (#19), PLAN-38 (#24). Next free slot: PLAN-36 code-analysis-base (disjoint); PLAN-08 waits on PLAN-04, PLAN-09 on PLAN-06. Watch: first review-bot re-review proof, likely on #25. Next action: record PLAN-05 running on confirmation; analyze landings; land the ledger. Open for the operator: PLAN-01 documentation unowned; unowned remainder of PLAN-02's roadmap item.
**Phase**: orchestrating
**Running**:
- PLAN-04 (WS-01) — plan=plan-04-service-unit
- PLAN-06 (WS-02) — plan=plan-06-store-substrate
- PLAN-07 (WS-03) — plan=workflow-dsl-parser-and-ast
**Parked**:
- PLAN-19 (WS-05)
- PLAN-37 (WS-06)
**Queue** (staged, in order):
1. PLAN-05 (WS-08)
2. PLAN-08 (WS-01)
3. PLAN-09 (WS-01)
4. PLAN-10 (WS-03)
5. PLAN-11 (WS-02)
6. PLAN-12 (WS-02)
7. PLAN-13 (WS-03)
8. PLAN-14 (WS-02)
9. PLAN-15 (WS-02)
10. PLAN-16 (WS-03)
11. PLAN-17 (WS-03)
12. PLAN-18 (WS-04)
13. PLAN-20 (WS-04)
14. PLAN-21 (WS-03)
15. PLAN-22 (WS-06)
16. PLAN-23 (WS-01)
17. PLAN-24 (WS-03)
18. PLAN-25 (WS-05)
19. PLAN-26 (WS-05)
20. PLAN-27 (WS-04)
21. PLAN-28 (WS-05)
22. PLAN-29 (WS-04)
23. PLAN-30 (WS-04)
24. PLAN-31 (WS-04)
25. PLAN-32 (WS-05)
26. PLAN-33 (WS-05)
27. PLAN-34 (WS-05)
28. PLAN-35 (WS-06)
29. PLAN-36 (WS-07)
- PLAN-01 (WS-01) — plan=lock-manager-and-leases — PR #20 — landing=landings/PLAN-01.md — status: shipped
- PLAN-02 (WS-03) — plan=primitive-spi-and-validators — PR #18 — landing=landings/PLAN-02.md — status: shipped
- PLAN-03 (WS-01) — plan=workspace-confinement — PR #19 — landing=landings/PLAN-03.md — status: shipped
- PLAN-38 (WS-09) — plan=plan-38-review-bot-re-review — PR #24 — landing=landings/PLAN-38.md — status: shipped

## Ordered Queue

| # | Plan | Workstream | Status | Surface (expected) |
|---|------|------------|--------|--------------------|
| 1 | PLAN-04 | WS-01 | running | doc/plans/README.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/start/; pm-runtime/src/test/java/de/planmarshall/runtime/start/; pm-runtime/src/test/resources/ |
| 2 | PLAN-05 | WS-08 | staged | doc/plans/README.adoc; doc/plans/http-baseline.adoc; pm-providers/pm-provider-ci/pom.xml; pm-providers/pm-provider-ci/src/main/java/de/planmarshall/provider/ci/CiHttpClient.java; pm-providers/pm-provider-ci/src/test/java/de/planmarshall/provider/ci/; pm-providers/pm-provider-github/pom.xml; pm-providers/pm-provider-github/src/test/java/de/planmarshall/provider/github/; pm-providers/pm-provider-gitlab/pom.xml; pm-providers/pm-provider-gitlab/src/test/java/de/planmarshall/provider/gitlab/; pom.xml |
| 3 | PLAN-06 | WS-02 | running | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/store/ |
| 4 | PLAN-07 | WS-03 | running | doc/plans/README.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/ast/; pm-workflow/src/main/java/de/planmarshall/workflow/dsl/; pm-workflow/src/test/java/de/planmarshall/workflow/ast/; pm-workflow/src/test/java/de/planmarshall/workflow/dsl/; pm-workflow/src/test/resources/ |
| 5 | PLAN-08 | WS-01 | staged | doc/plans/README.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/start/; pm-runtime/src/test/java/de/planmarshall/runtime/start/ |
| 6 | PLAN-09 | WS-01 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/store/; pm-runtime/src/main/java/de/planmarshall/runtime/start/; pm-runtime/src/test/java/de/planmarshall/runtime/start/ |
| 7 | PLAN-10 | WS-03 | staged | doc/plans/README.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/verify/; pm-workflow/src/test/java/de/planmarshall/workflow/verify/ |
| 8 | PLAN-11 | WS-02 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/store/; pm-runtime/src/main/java/de/planmarshall/runtime/security/; pm-runtime/src/test/java/de/planmarshall/runtime/security/ |
| 9 | PLAN-12 | WS-02 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/queue/; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/queue/; pm-core/src/test/java/de/planmarshall/core/store/ |
| 10 | PLAN-13 | WS-03 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/store/; pm-workflow/src/main/java/de/planmarshall/workflow/engine/; pm-workflow/src/test/java/de/planmarshall/workflow/engine/ |
| 11 | PLAN-14 | WS-02 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/store/ |
| 12 | PLAN-15 | WS-02 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/config/; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/config/; pm-core/src/test/java/de/planmarshall/core/store/ |
| 13 | PLAN-16 | WS-03 | staged | doc/plans/README.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/diagram/; pm-workflow/src/test/java/de/planmarshall/workflow/diagram/; pm-workflow/src/test/resources/ |
| 14 | PLAN-17 | WS-03 | staged | doc/plans/README.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/finalize/; pm-workflow/src/main/java/de/planmarshall/workflow/unit/; pm-workflow/src/main/java/de/planmarshall/workflow/verify/; pm-workflow/src/test/java/de/planmarshall/workflow/finalize/; pm-workflow/src/test/java/de/planmarshall/workflow/unit/; pm-workflow/src/test/java/de/planmarshall/workflow/verify/ |
| 15 | PLAN-18 | WS-04 | staged | doc/plans/README.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/job/; pm-runtime/src/test/java/de/planmarshall/runtime/job/ |
| 16 | PLAN-19 | WS-05 | parked | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/profile/; pm-core/src/test/java/de/planmarshall/core/profile/ |
| 17 | PLAN-20 | WS-04 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/service/; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/service/; pm-core/src/test/java/de/planmarshall/core/store/; pm-runtime/src/main/java/de/planmarshall/runtime/job/; pm-runtime/src/test/java/de/planmarshall/runtime/job/ |
| 18 | PLAN-21 | WS-03 | staged | doc/plans/README.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/engine/; pm-workflow/src/test/java/de/planmarshall/workflow/engine/ |
| 19 | PLAN-22 | WS-06 | staged | doc/plans/README.adoc; pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ |
| 20 | PLAN-23 | WS-01 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/store/; pm-runtime/src/main/java/de/planmarshall/runtime/start/; pm-runtime/src/test/java/de/planmarshall/runtime/start/ |
| 21 | PLAN-24 | WS-03 | staged | doc/plans/README.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/plan/; pm-workflow/src/test/java/de/planmarshall/workflow/plan/ |
| 22 | PLAN-25 | WS-05 | staged | doc/plans/README.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/engine/; pm-workflow/src/main/java/de/planmarshall/workflow/hypermedia/; pm-workflow/src/test/java/de/planmarshall/workflow/engine/; pm-workflow/src/test/java/de/planmarshall/workflow/hypermedia/ |
| 23 | PLAN-26 | WS-05 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/role/; pm-core/src/test/java/de/planmarshall/core/role/; pm-workflow/src/main/java/de/planmarshall/workflow/model/; pm-workflow/src/test/java/de/planmarshall/workflow/model/ |
| 24 | PLAN-27 | WS-04 | staged | doc/plans/README.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/job/; pm-runtime/src/test/java/de/planmarshall/runtime/job/ |
| 25 | PLAN-28 | WS-05 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/queue/; pm-core/src/test/java/de/planmarshall/core/queue/; pm-workflow/src/main/java/de/planmarshall/workflow/task/; pm-workflow/src/test/java/de/planmarshall/workflow/task/ |
| 26 | PLAN-29 | WS-04 | staged | doc/plans/README.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/launch/; pm-runtime/src/main/java/de/planmarshall/runtime/security/; pm-runtime/src/test/java/de/planmarshall/runtime/launch/; pm-runtime/src/test/java/de/planmarshall/runtime/security/ |
| 27 | PLAN-30 | WS-04 | staged | doc/plans/README.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/worker/; pm-runtime/src/test/java/de/planmarshall/runtime/worker/ |
| 28 | PLAN-31 | WS-04 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/store/; pm-runtime/src/main/java/de/planmarshall/runtime/launch/; pm-runtime/src/test/java/de/planmarshall/runtime/launch/ |
| 29 | PLAN-32 | WS-05 | staged | doc/plans/README.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/consult/; pm-workflow/src/test/java/de/planmarshall/workflow/consult/ |
| 30 | PLAN-33 | WS-05 | staged | doc/plans/README.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/task/; pm-workflow/src/test/java/de/planmarshall/workflow/task/ |
| 31 | PLAN-34 | WS-05 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/role/; pm-core/src/test/java/de/planmarshall/core/role/; pm-workflow/src/main/java/de/planmarshall/workflow/model/; pm-workflow/src/test/java/de/planmarshall/workflow/model/ |
| 32 | PLAN-35 | WS-06 | staged | doc/plans/README.adoc; pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ |
| 33 | PLAN-36 | WS-07 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/analysis/; pm-core/src/main/java/de/planmarshall/core/findings/; pm-core/src/test/java/de/planmarshall/core/analysis/; pm-core/src/test/java/de/planmarshall/core/findings/; src/guard-controls/ |
| 34 | PLAN-37 | WS-06 | parked | doc/plans/README.adoc; pm-core/src/test/java/de/planmarshall/core/differential/ |

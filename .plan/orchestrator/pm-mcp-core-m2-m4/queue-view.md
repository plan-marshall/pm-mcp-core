<!-- GENERATED FILE — never hand-edit. Rendered from this epic's ledger (status.json, resume_anchor.md, queue/*.json) by `orchestrator regenerate-view --slug pm-mcp-core-m2-m4`. On a merge conflict in this file, do not merge it by hand: merge the source files, run `orchestrator regenerate-view --slug pm-mcp-core-m2-m4`, and `git add` the result. -->

# Queue view: pm-mcp-core work packages, roadmap Milestones 2 to 4

## START HERE

**Resume anchor**: Run /plan-orchestrator next slug=pm-mcp-core-m2-m4. Ready now: PLAN-01 lock-manager-and-leases, PLAN-02 primitive-spi-and-validators, PLAN-03 workspace-confinement, PLAN-04 service-unit, PLAN-05 http-baseline, PLAN-36 code-analysis-base (queue end; ask for it by id). Parallelization scope 3.
**Phase**: orchestrating
**Parked**:
- PLAN-19 (WS-05)
- PLAN-37 (WS-06)
**Queue** (staged, in order):
1. PLAN-01 (WS-01)
2. PLAN-02 (WS-03)
3. PLAN-03 (WS-01)
4. PLAN-04 (WS-01)
5. PLAN-05 (WS-08)
6. PLAN-06 (WS-02)
7. PLAN-07 (WS-03)
8. PLAN-08 (WS-01)
9. PLAN-09 (WS-01)
10. PLAN-10 (WS-03)
11. PLAN-11 (WS-02)
12. PLAN-12 (WS-02)
13. PLAN-13 (WS-03)
14. PLAN-14 (WS-02)
15. PLAN-15 (WS-02)
16. PLAN-16 (WS-03)
17. PLAN-17 (WS-03)
18. PLAN-18 (WS-04)
19. PLAN-20 (WS-04)
20. PLAN-21 (WS-03)
21. PLAN-22 (WS-06)
22. PLAN-23 (WS-01)
23. PLAN-24 (WS-03)
24. PLAN-25 (WS-05)
25. PLAN-26 (WS-05)
26. PLAN-27 (WS-04)
27. PLAN-28 (WS-05)
28. PLAN-29 (WS-04)
29. PLAN-30 (WS-04)
30. PLAN-31 (WS-04)
31. PLAN-32 (WS-05)
32. PLAN-33 (WS-05)
33. PLAN-34 (WS-05)
34. PLAN-35 (WS-06)
35. PLAN-36 (WS-07)

## Ordered Queue

| # | Plan | Workstream | Status | Surface (expected) |
|---|------|------------|--------|--------------------|
| 1 | PLAN-01 | WS-01 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/service/; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/service/; pm-core/src/test/java/de/planmarshall/core/store/; pm-runtime/src/main/java/de/planmarshall/runtime/lock/; pm-runtime/src/test/java/de/planmarshall/runtime/lock/ |
| 2 | PLAN-02 | WS-03 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/spi/; pm-core/src/test/java/de/planmarshall/core/spi/ |
| 3 | PLAN-03 | WS-01 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/config/; pm-core/src/test/java/de/planmarshall/core/config/; pm-runtime/src/main/java/de/planmarshall/runtime/security/; pm-runtime/src/test/java/de/planmarshall/runtime/security/ |
| 4 | PLAN-04 | WS-01 | staged | doc/plans/README.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/start/; pm-runtime/src/test/java/de/planmarshall/runtime/start/; pm-runtime/src/test/resources/ |
| 5 | PLAN-05 | WS-08 | staged | doc/plans/README.adoc; doc/plans/http-baseline.adoc; pm-providers/pm-provider-ci/pom.xml; pm-providers/pm-provider-ci/src/main/java/de/planmarshall/provider/ci/CiHttpClient.java; pm-providers/pm-provider-ci/src/test/java/de/planmarshall/provider/ci/; pm-providers/pm-provider-github/pom.xml; pm-providers/pm-provider-github/src/test/java/de/planmarshall/provider/github/; pm-providers/pm-provider-gitlab/pom.xml; pm-providers/pm-provider-gitlab/src/test/java/de/planmarshall/provider/gitlab/; pom.xml |
| 6 | PLAN-06 | WS-02 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/store/ |
| 7 | PLAN-07 | WS-03 | staged | doc/plans/README.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/ast/; pm-workflow/src/main/java/de/planmarshall/workflow/dsl/; pm-workflow/src/test/java/de/planmarshall/workflow/ast/; pm-workflow/src/test/java/de/planmarshall/workflow/dsl/; pm-workflow/src/test/resources/ |
| 8 | PLAN-08 | WS-01 | staged | doc/plans/README.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/start/; pm-runtime/src/test/java/de/planmarshall/runtime/start/ |
| 9 | PLAN-09 | WS-01 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/store/; pm-runtime/src/main/java/de/planmarshall/runtime/start/; pm-runtime/src/test/java/de/planmarshall/runtime/start/ |
| 10 | PLAN-10 | WS-03 | staged | doc/plans/README.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/verify/; pm-workflow/src/test/java/de/planmarshall/workflow/verify/ |
| 11 | PLAN-11 | WS-02 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/store/; pm-runtime/src/main/java/de/planmarshall/runtime/security/; pm-runtime/src/test/java/de/planmarshall/runtime/security/ |
| 12 | PLAN-12 | WS-02 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/queue/; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/queue/; pm-core/src/test/java/de/planmarshall/core/store/ |
| 13 | PLAN-13 | WS-03 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/store/; pm-workflow/src/main/java/de/planmarshall/workflow/engine/; pm-workflow/src/test/java/de/planmarshall/workflow/engine/ |
| 14 | PLAN-14 | WS-02 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/store/ |
| 15 | PLAN-15 | WS-02 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/config/; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/config/; pm-core/src/test/java/de/planmarshall/core/store/ |
| 16 | PLAN-16 | WS-03 | staged | doc/plans/README.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/diagram/; pm-workflow/src/test/java/de/planmarshall/workflow/diagram/; pm-workflow/src/test/resources/ |
| 17 | PLAN-17 | WS-03 | staged | doc/plans/README.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/finalize/; pm-workflow/src/main/java/de/planmarshall/workflow/unit/; pm-workflow/src/main/java/de/planmarshall/workflow/verify/; pm-workflow/src/test/java/de/planmarshall/workflow/finalize/; pm-workflow/src/test/java/de/planmarshall/workflow/unit/; pm-workflow/src/test/java/de/planmarshall/workflow/verify/ |
| 18 | PLAN-18 | WS-04 | staged | doc/plans/README.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/job/; pm-runtime/src/test/java/de/planmarshall/runtime/job/ |
| 19 | PLAN-19 | WS-05 | parked | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/profile/; pm-core/src/test/java/de/planmarshall/core/profile/ |
| 20 | PLAN-20 | WS-04 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/service/; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/service/; pm-core/src/test/java/de/planmarshall/core/store/; pm-runtime/src/main/java/de/planmarshall/runtime/job/; pm-runtime/src/test/java/de/planmarshall/runtime/job/ |
| 21 | PLAN-21 | WS-03 | staged | doc/plans/README.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/engine/; pm-workflow/src/test/java/de/planmarshall/workflow/engine/ |
| 22 | PLAN-22 | WS-06 | staged | doc/plans/README.adoc; pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ |
| 23 | PLAN-23 | WS-01 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/store/; pm-runtime/src/main/java/de/planmarshall/runtime/start/; pm-runtime/src/test/java/de/planmarshall/runtime/start/ |
| 24 | PLAN-24 | WS-03 | staged | doc/plans/README.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/plan/; pm-workflow/src/test/java/de/planmarshall/workflow/plan/ |
| 25 | PLAN-25 | WS-05 | staged | doc/plans/README.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/engine/; pm-workflow/src/main/java/de/planmarshall/workflow/hypermedia/; pm-workflow/src/test/java/de/planmarshall/workflow/engine/; pm-workflow/src/test/java/de/planmarshall/workflow/hypermedia/ |
| 26 | PLAN-26 | WS-05 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/role/; pm-core/src/test/java/de/planmarshall/core/role/; pm-workflow/src/main/java/de/planmarshall/workflow/model/; pm-workflow/src/test/java/de/planmarshall/workflow/model/ |
| 27 | PLAN-27 | WS-04 | staged | doc/plans/README.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/job/; pm-runtime/src/test/java/de/planmarshall/runtime/job/ |
| 28 | PLAN-28 | WS-05 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/queue/; pm-core/src/test/java/de/planmarshall/core/queue/; pm-workflow/src/main/java/de/planmarshall/workflow/task/; pm-workflow/src/test/java/de/planmarshall/workflow/task/ |
| 29 | PLAN-29 | WS-04 | staged | doc/plans/README.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/launch/; pm-runtime/src/main/java/de/planmarshall/runtime/security/; pm-runtime/src/test/java/de/planmarshall/runtime/launch/; pm-runtime/src/test/java/de/planmarshall/runtime/security/ |
| 30 | PLAN-30 | WS-04 | staged | doc/plans/README.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/worker/; pm-runtime/src/test/java/de/planmarshall/runtime/worker/ |
| 31 | PLAN-31 | WS-04 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/store/; pm-runtime/src/main/java/de/planmarshall/runtime/launch/; pm-runtime/src/test/java/de/planmarshall/runtime/launch/ |
| 32 | PLAN-32 | WS-05 | staged | doc/plans/README.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/consult/; pm-workflow/src/test/java/de/planmarshall/workflow/consult/ |
| 33 | PLAN-33 | WS-05 | staged | doc/plans/README.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/task/; pm-workflow/src/test/java/de/planmarshall/workflow/task/ |
| 34 | PLAN-34 | WS-05 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/role/; pm-core/src/test/java/de/planmarshall/core/role/; pm-workflow/src/main/java/de/planmarshall/workflow/model/; pm-workflow/src/test/java/de/planmarshall/workflow/model/ |
| 35 | PLAN-35 | WS-06 | staged | doc/plans/README.adoc; pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ |
| 36 | PLAN-36 | WS-07 | staged | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/analysis/; pm-core/src/main/java/de/planmarshall/core/findings/; pm-core/src/test/java/de/planmarshall/core/analysis/; pm-core/src/test/java/de/planmarshall/core/findings/; src/guard-controls/ |
| 37 | PLAN-37 | WS-06 | parked | doc/plans/README.adoc; pm-core/src/test/java/de/planmarshall/core/differential/ |

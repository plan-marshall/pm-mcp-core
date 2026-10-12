<!-- GENERATED FILE — never hand-edit. Rendered from this epic's ledger (status.json, resume_anchor.md, queue/*.json) by `orchestrator regenerate-view --slug pm-mcp-core-m2-m4`. On a merge conflict in this file, do not merge it by hand: merge the source files, run `orchestrator regenerate-view --slug pm-mcp-core-m2-m4`, and `git add` the result. -->

# Queue view: pm-mcp-core work packages, roadmap Milestones 2 to 4

## START HERE

**Resume anchor**: Running: PLAN-13 workflow-engine, PLAN-18 verify-not-resolve (#43). Emitted, awaiting operator-confirmed launch: PLAN-16 diagram-generator, PLAN-54 static-verifier-rule-gaps-completion (Claude Code only). Shipped: PLAN-01..08, 10, 11, 36, 38, 41, 44 (41/44 incomplete -> PLAN-54). After PLAN-54: PLAN-17, PLAN-39, PLAN-40; after PLAN-13: one of PLAN-09/12/14/15 at a time, PLAN-21, PLAN-22; after PLAN-18: PLAN-20. Parked: PLAN-19, 37, 42, 43, 45-53. Next action: record running on confirmation; analyze landings (ask the harness; extended check for non-Claude-Code); land the ledger. Open for the operator: doc PR for PLAN-41's watch items; one-review merge rule; unowned remainder of PLAN-02's roadmap item; schema parity test in CI; PLAN-11's seven follow-ups without owner.
**Phase**: orchestrating
**Running**:
- PLAN-13 (WS-03) — plan=workflow-engine
- PLAN-18 (WS-04) — plan=verify-not-resolve
**Parked**:
- PLAN-19 (WS-05)
- PLAN-37 (WS-06)
- PLAN-42 (WS-03)
- PLAN-43 (WS-03)
- PLAN-45 (WS-03)
- PLAN-46 (WS-03)
- PLAN-47 (WS-03)
- PLAN-48 (WS-03)
- PLAN-49 (WS-08)
- PLAN-50 (WS-03)
- PLAN-51 (WS-03)
- PLAN-52 (WS-03)
- PLAN-53 (WS-03)
**Queue** (staged, in order):
1. PLAN-09 (WS-01)
2. PLAN-12 (WS-02)
3. PLAN-14 (WS-02)
4. PLAN-15 (WS-02)
5. PLAN-16 (WS-03)
6. PLAN-17 (WS-03)
7. PLAN-20 (WS-04)
8. PLAN-21 (WS-03)
9. PLAN-22 (WS-06)
10. PLAN-23 (WS-01)
11. PLAN-24 (WS-03)
12. PLAN-25 (WS-05)
13. PLAN-26 (WS-05)
14. PLAN-27 (WS-04)
15. PLAN-28 (WS-05)
16. PLAN-29 (WS-04)
17. PLAN-30 (WS-04)
18. PLAN-31 (WS-04)
19. PLAN-32 (WS-05)
20. PLAN-33 (WS-05)
21. PLAN-34 (WS-05)
22. PLAN-35 (WS-06)
23. PLAN-39 (WS-10)
24. PLAN-40 (WS-10)
25. PLAN-54 (WS-03)
- PLAN-01 (WS-01) — plan=lock-manager-and-leases — PR #20 — landing=landings/PLAN-01.md — status: shipped
- PLAN-02 (WS-03) — plan=primitive-spi-and-validators — PR #18 — landing=landings/PLAN-02.md — status: shipped
- PLAN-03 (WS-01) — plan=workspace-confinement — PR #19 — landing=landings/PLAN-03.md — status: shipped
- PLAN-04 (WS-01) — plan=plan-04-service-unit — PR #25 — landing=landings/PLAN-04.md — status: shipped
- PLAN-05 (WS-08) — plan=http-baseline — PR #28 — landing=landings/PLAN-05.md — status: shipped
- PLAN-06 (WS-02) — plan=plan-06-store-substrate — PR #26 — landing=landings/PLAN-06.md — status: shipped
- PLAN-07 (WS-03) — plan=workflow-dsl-parser-and-ast — PR #27 — landing=landings/PLAN-07.md — status: shipped
- PLAN-08 (WS-01) — plan=runtime-lifecycle — PR #29 — landing=landings/PLAN-08.md — status: shipped
- PLAN-10 (WS-03) — plan=static-verifier-and-fuzzer — PR #32 — landing=landings/PLAN-10.md — status: shipped
- PLAN-11 (WS-02) — plan=plan-11-enrolment-store — PR #30 — landing=landings/PLAN-11.md — status: shipped
- PLAN-36 (WS-07) — plan=code-analysis-base — PR #31 — landing=landings/PLAN-36.md — status: shipped
- PLAN-38 (WS-09) — plan=plan-38-review-bot-re-review — PR #24 — landing=landings/PLAN-38.md — status: shipped
- PLAN-41 (WS-03) — plan=plan-41-static-verifier-remaining-rules — PR #34 — landing=landings/PLAN-41.md — status: shipped
- PLAN-44 (WS-03) — plan=plan-44-static-verifier-rule-gaps — PR #38 — landing=landings/PLAN-44.md — status: shipped

## Ordered Queue

| # | Plan | Workstream | Status | Surface (expected) |
|---|------|------------|--------|--------------------|
| 1 | PLAN-09 | WS-01 | staged | doc/plans/README.adoc; pm-core/doc/adr/core-store/; pm-core/doc/developer/core-store.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/store/; pm-runtime/doc/adr/runtime-start/; pm-runtime/doc/developer/runtime-start.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/start/; pm-runtime/src/test/java/de/planmarshall/runtime/start/ |
| 2 | PLAN-12 | WS-02 | staged | doc/plans/README.adoc; pm-core/doc/adr/core-queue/; pm-core/doc/adr/core-store/; pm-core/doc/developer/core-queue.adoc; pm-core/doc/developer/core-store.adoc; pm-core/src/main/java/de/planmarshall/core/queue/; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/queue/; pm-core/src/test/java/de/planmarshall/core/store/ |
| 3 | PLAN-13 | WS-03 | running | doc/plans/README.adoc; pm-core/doc/adr/core-store/; pm-core/doc/developer/core-store.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/store/; pm-workflow/doc/adr/workflow-engine/; pm-workflow/doc/developer/workflow-engine.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/engine/; pm-workflow/src/test/java/de/planmarshall/workflow/engine/ |
| 4 | PLAN-14 | WS-02 | staged | doc/plans/README.adoc; pm-core/doc/adr/core-store/; pm-core/doc/developer/core-store.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/store/ |
| 5 | PLAN-15 | WS-02 | staged | doc/plans/README.adoc; pm-core/doc/adr/core-config/; pm-core/doc/adr/core-store/; pm-core/doc/developer/core-config.adoc; pm-core/doc/developer/core-store.adoc; pm-core/src/main/java/de/planmarshall/core/config/; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/config/; pm-core/src/test/java/de/planmarshall/core/store/ |
| 6 | PLAN-16 | WS-03 | staged | doc/plans/README.adoc; pm-workflow/doc/adr/workflow-diagram/; pm-workflow/doc/developer/workflow-diagram.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/diagram/; pm-workflow/src/test/java/de/planmarshall/workflow/diagram/; pm-workflow/src/test/resources/ |
| 7 | PLAN-17 | WS-03 | staged | doc/plans/README.adoc; pm-workflow/doc/adr/workflow-finalize/; pm-workflow/doc/adr/workflow-unit/; pm-workflow/doc/adr/workflow-verify/; pm-workflow/doc/developer/workflow-finalize.adoc; pm-workflow/doc/developer/workflow-unit.adoc; pm-workflow/doc/developer/workflow-verify.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/finalize/; pm-workflow/src/main/java/de/planmarshall/workflow/unit/; pm-workflow/src/main/java/de/planmarshall/workflow/verify/; pm-workflow/src/test/java/de/planmarshall/workflow/finalize/; pm-workflow/src/test/java/de/planmarshall/workflow/unit/; pm-workflow/src/test/java/de/planmarshall/workflow/verify/ |
| 8 | PLAN-18 | WS-04 | running | doc/plans/README.adoc; pm-runtime/doc/adr/runtime-job/; pm-runtime/doc/developer/runtime-job.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/job/; pm-runtime/src/test/java/de/planmarshall/runtime/job/ |
| 9 | PLAN-19 | WS-05 | parked | doc/plans/README.adoc; pm-core/doc/adr/core-profile/; pm-core/doc/developer/core-profile.adoc; pm-core/src/main/java/de/planmarshall/core/profile/; pm-core/src/test/java/de/planmarshall/core/profile/ |
| 10 | PLAN-20 | WS-04 | staged | doc/plans/README.adoc; pm-core/doc/adr/core-service/; pm-core/doc/adr/core-store/; pm-core/doc/developer/core-service.adoc; pm-core/doc/developer/core-store.adoc; pm-core/src/main/java/de/planmarshall/core/service/; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/service/; pm-core/src/test/java/de/planmarshall/core/store/; pm-runtime/doc/adr/runtime-job/; pm-runtime/doc/developer/runtime-job.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/job/; pm-runtime/src/test/java/de/planmarshall/runtime/job/ |
| 11 | PLAN-21 | WS-03 | staged | doc/plans/README.adoc; pm-workflow/doc/adr/workflow-engine/; pm-workflow/doc/developer/workflow-engine.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/engine/; pm-workflow/src/test/java/de/planmarshall/workflow/engine/ |
| 12 | PLAN-22 | WS-06 | staged | doc/plans/README.adoc; pm-workflow/doc/adr/workflow-scenario/; pm-workflow/doc/developer/workflow-scenario.adoc; pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ |
| 13 | PLAN-23 | WS-01 | staged | doc/plans/README.adoc; pm-core/doc/adr/core-store/; pm-core/doc/developer/core-store.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/store/; pm-runtime/doc/adr/runtime-start/; pm-runtime/doc/developer/runtime-start.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/start/; pm-runtime/src/test/java/de/planmarshall/runtime/start/ |
| 14 | PLAN-24 | WS-03 | staged | doc/plans/README.adoc; pm-workflow/doc/adr/workflow-plan/; pm-workflow/doc/developer/workflow-plan.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/plan/; pm-workflow/src/test/java/de/planmarshall/workflow/plan/ |
| 15 | PLAN-25 | WS-05 | staged | doc/plans/README.adoc; pm-workflow/doc/adr/workflow-engine/; pm-workflow/doc/adr/workflow-hypermedia/; pm-workflow/doc/developer/workflow-engine.adoc; pm-workflow/doc/developer/workflow-hypermedia.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/engine/; pm-workflow/src/main/java/de/planmarshall/workflow/hypermedia/; pm-workflow/src/test/java/de/planmarshall/workflow/engine/; pm-workflow/src/test/java/de/planmarshall/workflow/hypermedia/ |
| 16 | PLAN-26 | WS-05 | staged | doc/plans/README.adoc; pm-core/doc/adr/core-role/; pm-core/doc/developer/core-role.adoc; pm-core/src/main/java/de/planmarshall/core/role/; pm-core/src/test/java/de/planmarshall/core/role/; pm-workflow/doc/adr/workflow-model/; pm-workflow/doc/developer/workflow-model.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/model/; pm-workflow/src/test/java/de/planmarshall/workflow/model/ |
| 17 | PLAN-27 | WS-04 | staged | doc/plans/README.adoc; pm-runtime/doc/adr/runtime-job/; pm-runtime/doc/developer/runtime-job.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/job/; pm-runtime/src/test/java/de/planmarshall/runtime/job/ |
| 18 | PLAN-28 | WS-05 | staged | doc/plans/README.adoc; pm-core/doc/adr/core-queue/; pm-core/doc/developer/core-queue.adoc; pm-core/src/main/java/de/planmarshall/core/queue/; pm-core/src/test/java/de/planmarshall/core/queue/; pm-workflow/doc/adr/workflow-task/; pm-workflow/doc/developer/workflow-task.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/task/; pm-workflow/src/test/java/de/planmarshall/workflow/task/ |
| 19 | PLAN-29 | WS-04 | staged | doc/plans/README.adoc; pm-runtime/doc/adr/runtime-launch/; pm-runtime/doc/adr/runtime-security/; pm-runtime/doc/developer/runtime-launch.adoc; pm-runtime/doc/developer/runtime-security.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/launch/; pm-runtime/src/main/java/de/planmarshall/runtime/security/; pm-runtime/src/test/java/de/planmarshall/runtime/launch/; pm-runtime/src/test/java/de/planmarshall/runtime/security/ |
| 20 | PLAN-30 | WS-04 | staged | doc/plans/README.adoc; pm-runtime/doc/adr/runtime-worker/; pm-runtime/doc/developer/runtime-worker.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/worker/; pm-runtime/src/test/java/de/planmarshall/runtime/worker/ |
| 21 | PLAN-31 | WS-04 | staged | doc/plans/README.adoc; pm-core/doc/adr/core-store/; pm-core/doc/developer/core-store.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/store/; pm-runtime/doc/adr/runtime-launch/; pm-runtime/doc/developer/runtime-launch.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/launch/; pm-runtime/src/test/java/de/planmarshall/runtime/launch/ |
| 22 | PLAN-32 | WS-05 | staged | doc/plans/README.adoc; pm-workflow/doc/adr/workflow-consult/; pm-workflow/doc/developer/workflow-consult.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/consult/; pm-workflow/src/test/java/de/planmarshall/workflow/consult/ |
| 23 | PLAN-33 | WS-05 | staged | doc/plans/README.adoc; pm-workflow/doc/adr/workflow-task/; pm-workflow/doc/developer/workflow-task.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/task/; pm-workflow/src/test/java/de/planmarshall/workflow/task/ |
| 24 | PLAN-34 | WS-05 | staged | doc/plans/README.adoc; pm-core/doc/adr/core-role/; pm-core/doc/developer/core-role.adoc; pm-core/src/main/java/de/planmarshall/core/role/; pm-core/src/test/java/de/planmarshall/core/role/; pm-workflow/doc/adr/workflow-model/; pm-workflow/doc/developer/workflow-model.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/model/; pm-workflow/src/test/java/de/planmarshall/workflow/model/ |
| 25 | PLAN-35 | WS-06 | staged | doc/plans/README.adoc; pm-workflow/doc/adr/workflow-scenario/; pm-workflow/doc/developer/workflow-scenario.adoc; pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ |
| 26 | PLAN-37 | WS-06 | parked | doc/plans/README.adoc; pm-core/doc/adr/core-differential/; pm-core/doc/developer/core-differential.adoc; pm-core/src/test/java/de/planmarshall/core/differential/ |
| 27 | PLAN-39 | WS-10 | staged | CLAUDE.md; README.md; doc/adr/; doc/plans/README.adoc; pm-core/README.adoc; pm-core/doc/adr/; pm-providers/README.adoc; pm-providers/pm-provider-ci/README.adoc; pm-providers/pm-provider-git/README.adoc; pm-providers/pm-provider-github/README.adoc; pm-providers/pm-provider-gitlab/README.adoc; pm-providers/pm-provider-sonar/README.adoc; pm-runtime/README.adoc; pm-runtime/doc/adr/; pm-workflow/README.adoc; pm-workflow/doc/adr/ |
| 28 | PLAN-40 | WS-10 | staged | pm-core/doc/developer/; pm-providers/pm-provider-ci/doc/developer/; pm-providers/pm-provider-git/doc/developer/; pm-providers/pm-provider-github/doc/developer/; pm-providers/pm-provider-gitlab/doc/developer/; pm-providers/pm-provider-sonar/doc/developer/; pm-runtime/doc/developer/; pm-workflow/doc/developer/ |
| 29 | PLAN-42 | WS-03 | parked | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-workflow/src/main/java/de/planmarshall/workflow/dsl/; pm-workflow/src/main/java/de/planmarshall/workflow/engine/; pm-workflow/src/main/java/de/planmarshall/workflow/verify/ |
| 30 | PLAN-43 | WS-03 | parked | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-workflow/src/main/java/de/planmarshall/workflow/diagram/; pm-workflow/src/main/java/de/planmarshall/workflow/engine/; pm-workflow/src/main/java/de/planmarshall/workflow/plan/; pm-workflow/src/main/java/de/planmarshall/workflow/unit/; pm-workflow/src/main/java/de/planmarshall/workflow/verify/ |
| 31 | PLAN-45 | WS-03 | parked | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-runtime/src/main/java/de/planmarshall/runtime/enrolment/; pm-runtime/src/main/java/de/planmarshall/runtime/workspace/; pm-workflow/src/main/java/de/planmarshall/workflow/unit/ |
| 32 | PLAN-46 | WS-03 | parked | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-workflow/src/main/java/de/planmarshall/workflow/unit/ |
| 33 | PLAN-47 | WS-03 | parked | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-workflow/src/main/java/de/planmarshall/workflow/engine/ |
| 34 | PLAN-48 | WS-03 | parked | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-workflow/src/main/java/de/planmarshall/workflow/unit/; pm-workflow/src/main/java/de/planmarshall/workflow/verify/ |
| 35 | PLAN-49 | WS-08 | parked | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/findings/; pm-providers/pm-provider-forgejo/ |
| 36 | PLAN-50 | WS-03 | parked | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/profile/; pm-workflow/src/main/java/de/planmarshall/workflow/plan/ |
| 37 | PLAN-51 | WS-03 | parked | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-workflow/src/main/java/de/planmarshall/workflow/engine/ |
| 38 | PLAN-52 | WS-03 | parked | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-workflow/src/main/java/de/planmarshall/workflow/unit/ |
| 39 | PLAN-53 | WS-03 | parked | doc/plans/README.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-workflow/src/main/java/de/planmarshall/workflow/unit/ |
| 40 | PLAN-54 | WS-03 | staged | doc/plans/README.adoc; doc/plans/project-units-and-invariants.adoc; doc/plans/static-verifier-rule-gaps.adoc; pm-workflow/doc/adr/workflow-verify/; pm-workflow/doc/developer/workflow-verify.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/verify/; pm-workflow/src/test/java/de/planmarshall/workflow/verify/; pm-workflow/src/test/resources/verify/ |

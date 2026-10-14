<!-- GENERATED FILE — never hand-edit. Rendered from this epic's ledger (status.json, resume_anchor.md, queue/*.json) by `orchestrator regenerate-view --slug pm-mcp-core-m2-m4`. On a merge conflict in this file, do not merge it by hand: merge the source files, run `orchestrator regenerate-view --slug pm-mcp-core-m2-m4`, and `git add` the result. -->

# Queue view: pm-mcp-core work packages, roadmap Milestones 2 to 4

## START HERE

**Resume anchor**: EMITTED PLAN-09 (audit-trail), PLAN-24 (plan-instance), PLAN-56 (static-verifier-residuals) on 2026-10-14 — awaiting the operator's launch confirmation (auto_emit=false; rows still staged). Transition each to launched once started. Held back: PLAN-14/15/20/23 (core.store, after PLAN-09), PLAN-40. Ledger commits 50e1ee0 + this round not yet landed.
**Phase**: orchestrating
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
- PLAN-55 (WS-05)
- PLAN-57 (WS-03)
- PLAN-58 (WS-03)
- PLAN-59 (WS-01)
- PLAN-60 (WS-01)
- PLAN-61 (WS-04)
- PLAN-62 (WS-03)
- PLAN-63 (WS-01)
**Queue** (staged, in order):
1. PLAN-09 (WS-01)
2. PLAN-14 (WS-02)
3. PLAN-15 (WS-02)
4. PLAN-20 (WS-04)
5. PLAN-23 (WS-01)
6. PLAN-24 (WS-03)
7. PLAN-25 (WS-05)
8. PLAN-26 (WS-05)
9. PLAN-27 (WS-04)
10. PLAN-28 (WS-05)
11. PLAN-29 (WS-04)
12. PLAN-30 (WS-04)
13. PLAN-31 (WS-04)
14. PLAN-32 (WS-05)
15. PLAN-33 (WS-05)
16. PLAN-34 (WS-05)
17. PLAN-35 (WS-06)
18. PLAN-40 (WS-10)
19. PLAN-56 (WS-03)
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
- PLAN-12 (WS-02) — plan=session-log-and-generations — PR #51 — landing=landings/PLAN-12.md — status: shipped
- PLAN-13 (WS-03) — plan=workflow-engine — PR #45 — landing=landings/PLAN-13.md — status: shipped
- PLAN-16 (WS-03) — plan=diagram-generator — PR #49 — landing=landings/PLAN-16.md — status: shipped
- PLAN-17 (WS-03) — plan=plan-17-project-units-and-invariants — PR #56 — landing=landings/PLAN-17.md — status: shipped
- PLAN-18 (WS-04) — plan=verify-not-resolve — PR #43 — landing=landings/PLAN-18.md — status: shipped
- PLAN-21 (WS-03) — plan=plan-21-entity-addressing — PR #54 — landing=landings/PLAN-21.md — status: shipped
- PLAN-22 (WS-06) — plan=scenario-replay-harness — PR #50 — landing=landings/PLAN-22.md — status: shipped
- PLAN-36 (WS-07) — plan=code-analysis-base — PR #31 — landing=landings/PLAN-36.md — status: shipped
- PLAN-38 (WS-09) — plan=plan-38-review-bot-re-review — PR #24 — landing=landings/PLAN-38.md — status: shipped
- PLAN-39 (WS-10) — plan=plan-39-module-readmes-and-adrs — PR #53 — landing=landings/PLAN-39.md — status: shipped
- PLAN-41 (WS-03) — plan=plan-41-static-verifier-remaining-rules — PR #34 — landing=landings/PLAN-41.md — status: shipped
- PLAN-44 (WS-03) — plan=plan-44-static-verifier-rule-gaps — PR #38 — landing=landings/PLAN-44.md — status: shipped
- PLAN-54 (WS-03) — plan=static-verifier-rule-gaps-completion — PR #48 — landing=landings/PLAN-54.md — status: shipped

## Ordered Queue

| # | Plan | Workstream | Status | Surface (expected) |
|---|------|------------|--------|--------------------|
| 1 | PLAN-09 | WS-01 | staged | doc/plans/README.adoc; pm-core/doc/adr/core-store/; pm-core/doc/developer/core-store.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/store/; pm-runtime/doc/adr/runtime-start/; pm-runtime/doc/developer/runtime-start.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/start/; pm-runtime/src/test/java/de/planmarshall/runtime/start/ |
| 2 | PLAN-14 | WS-02 | staged | doc/plans/README.adoc; pm-core/doc/adr/core-store/; pm-core/doc/developer/core-store.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/store/ |
| 3 | PLAN-15 | WS-02 | staged | doc/plans/README.adoc; pm-core/doc/adr/core-config/; pm-core/doc/adr/core-store/; pm-core/doc/developer/core-config.adoc; pm-core/doc/developer/core-store.adoc; pm-core/src/main/java/de/planmarshall/core/config/; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/config/; pm-core/src/test/java/de/planmarshall/core/store/ |
| 4 | PLAN-19 | WS-05 | parked | doc/plans/README.adoc; pm-core/doc/adr/core-profile/; pm-core/doc/developer/core-profile.adoc; pm-core/src/main/java/de/planmarshall/core/profile/; pm-core/src/test/java/de/planmarshall/core/profile/ |
| 5 | PLAN-20 | WS-04 | staged | doc/plans/README.adoc; pm-core/doc/adr/core-service/; pm-core/doc/adr/core-store/; pm-core/doc/developer/core-service.adoc; pm-core/doc/developer/core-store.adoc; pm-core/src/main/java/de/planmarshall/core/service/; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/service/; pm-core/src/test/java/de/planmarshall/core/store/; pm-runtime/doc/adr/runtime-job/; pm-runtime/doc/developer/runtime-job.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/job/; pm-runtime/src/test/java/de/planmarshall/runtime/job/ |
| 6 | PLAN-23 | WS-01 | staged | doc/plans/README.adoc; pm-core/doc/adr/core-store/; pm-core/doc/developer/core-store.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/store/; pm-runtime/doc/adr/runtime-start/; pm-runtime/doc/developer/runtime-start.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/start/; pm-runtime/src/test/java/de/planmarshall/runtime/start/ |
| 7 | PLAN-24 | WS-03 | staged | doc/plans/README.adoc; pm-workflow/doc/adr/workflow-plan/; pm-workflow/doc/developer/workflow-plan.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/plan/; pm-workflow/src/test/java/de/planmarshall/workflow/plan/ |
| 8 | PLAN-25 | WS-05 | staged | doc/plans/README.adoc; pm-workflow/doc/adr/workflow-engine/; pm-workflow/doc/adr/workflow-hypermedia/; pm-workflow/doc/developer/workflow-engine.adoc; pm-workflow/doc/developer/workflow-hypermedia.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/engine/; pm-workflow/src/main/java/de/planmarshall/workflow/hypermedia/; pm-workflow/src/test/java/de/planmarshall/workflow/engine/; pm-workflow/src/test/java/de/planmarshall/workflow/hypermedia/ |
| 9 | PLAN-26 | WS-05 | staged | doc/plans/README.adoc; pm-core/doc/adr/core-role/; pm-core/doc/developer/core-role.adoc; pm-core/src/main/java/de/planmarshall/core/role/; pm-core/src/test/java/de/planmarshall/core/role/; pm-workflow/doc/adr/workflow-model/; pm-workflow/doc/developer/workflow-model.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/model/; pm-workflow/src/test/java/de/planmarshall/workflow/model/ |
| 10 | PLAN-27 | WS-04 | staged | doc/plans/README.adoc; pm-runtime/doc/adr/runtime-job/; pm-runtime/doc/developer/runtime-job.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/job/; pm-runtime/src/test/java/de/planmarshall/runtime/job/ |
| 11 | PLAN-28 | WS-05 | staged | doc/plans/README.adoc; pm-core/doc/adr/core-queue/; pm-core/doc/developer/core-queue.adoc; pm-core/src/main/java/de/planmarshall/core/queue/; pm-core/src/test/java/de/planmarshall/core/queue/; pm-workflow/doc/adr/workflow-task/; pm-workflow/doc/developer/workflow-task.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/task/; pm-workflow/src/test/java/de/planmarshall/workflow/task/ |
| 12 | PLAN-29 | WS-04 | staged | doc/plans/README.adoc; pm-runtime/doc/adr/runtime-launch/; pm-runtime/doc/adr/runtime-security/; pm-runtime/doc/developer/runtime-launch.adoc; pm-runtime/doc/developer/runtime-security.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/launch/; pm-runtime/src/main/java/de/planmarshall/runtime/security/; pm-runtime/src/test/java/de/planmarshall/runtime/launch/; pm-runtime/src/test/java/de/planmarshall/runtime/security/ |
| 13 | PLAN-30 | WS-04 | staged | doc/plans/README.adoc; pm-runtime/doc/adr/runtime-worker/; pm-runtime/doc/developer/runtime-worker.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/worker/; pm-runtime/src/test/java/de/planmarshall/runtime/worker/ |
| 14 | PLAN-31 | WS-04 | staged | doc/plans/README.adoc; pm-core/doc/adr/core-store/; pm-core/doc/developer/core-store.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-core/src/test/java/de/planmarshall/core/store/; pm-runtime/doc/adr/runtime-launch/; pm-runtime/doc/developer/runtime-launch.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/launch/; pm-runtime/src/test/java/de/planmarshall/runtime/launch/ |
| 15 | PLAN-32 | WS-05 | staged | doc/plans/README.adoc; pm-workflow/doc/adr/workflow-consult/; pm-workflow/doc/developer/workflow-consult.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/consult/; pm-workflow/src/test/java/de/planmarshall/workflow/consult/ |
| 16 | PLAN-33 | WS-05 | staged | doc/plans/README.adoc; pm-workflow/doc/adr/workflow-task/; pm-workflow/doc/developer/workflow-task.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/task/; pm-workflow/src/test/java/de/planmarshall/workflow/task/ |
| 17 | PLAN-34 | WS-05 | staged | doc/plans/README.adoc; pm-core/doc/adr/core-role/; pm-core/doc/developer/core-role.adoc; pm-core/src/main/java/de/planmarshall/core/role/; pm-core/src/test/java/de/planmarshall/core/role/; pm-workflow/doc/adr/workflow-model/; pm-workflow/doc/developer/workflow-model.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/model/; pm-workflow/src/test/java/de/planmarshall/workflow/model/ |
| 18 | PLAN-35 | WS-06 | staged | doc/plans/README.adoc; pm-workflow/doc/adr/workflow-scenario/; pm-workflow/doc/developer/workflow-scenario.adoc; pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ |
| 19 | PLAN-37 | WS-06 | parked | doc/plans/README.adoc; pm-core/doc/adr/core-differential/; pm-core/doc/developer/core-differential.adoc; pm-core/src/test/java/de/planmarshall/core/differential/ |
| 20 | PLAN-40 | WS-10 | staged | pm-core/doc/developer/; pm-providers/pm-provider-ci/doc/developer/; pm-providers/pm-provider-git/doc/developer/; pm-providers/pm-provider-github/doc/developer/; pm-providers/pm-provider-gitlab/doc/developer/; pm-providers/pm-provider-sonar/doc/developer/; pm-runtime/doc/developer/; pm-workflow/doc/developer/ |
| 21 | PLAN-42 | WS-03 | parked | doc/plans/README.adoc; pm-core/doc/adr/core-store/; pm-core/doc/developer/core-store.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-workflow/doc/adr/workflow-dsl/; pm-workflow/doc/adr/workflow-engine/; pm-workflow/doc/adr/workflow-verify/; pm-workflow/doc/developer/workflow-dsl.adoc; pm-workflow/doc/developer/workflow-engine.adoc; pm-workflow/doc/developer/workflow-verify.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/dsl/; pm-workflow/src/main/java/de/planmarshall/workflow/engine/; pm-workflow/src/main/java/de/planmarshall/workflow/verify/ |
| 22 | PLAN-43 | WS-03 | parked | doc/plans/README.adoc; pm-core/doc/adr/core-store/; pm-core/doc/developer/core-store.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-workflow/doc/adr/workflow-diagram/; pm-workflow/doc/adr/workflow-engine/; pm-workflow/doc/adr/workflow-plan/; pm-workflow/doc/adr/workflow-unit/; pm-workflow/doc/adr/workflow-verify/; pm-workflow/doc/developer/workflow-diagram.adoc; pm-workflow/doc/developer/workflow-engine.adoc; pm-workflow/doc/developer/workflow-plan.adoc; pm-workflow/doc/developer/workflow-unit.adoc; pm-workflow/doc/developer/workflow-verify.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/diagram/; pm-workflow/src/main/java/de/planmarshall/workflow/engine/; pm-workflow/src/main/java/de/planmarshall/workflow/plan/; pm-workflow/src/main/java/de/planmarshall/workflow/unit/; pm-workflow/src/main/java/de/planmarshall/workflow/verify/ |
| 23 | PLAN-45 | WS-03 | parked | doc/plans/README.adoc; pm-core/doc/adr/core-store/; pm-core/doc/developer/core-store.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-runtime/doc/adr/runtime-enrolment/; pm-runtime/doc/adr/runtime-workspace/; pm-runtime/doc/developer/runtime-enrolment.adoc; pm-runtime/doc/developer/runtime-workspace.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/enrolment/; pm-runtime/src/main/java/de/planmarshall/runtime/workspace/; pm-workflow/doc/adr/workflow-unit/; pm-workflow/doc/developer/workflow-unit.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/unit/ |
| 24 | PLAN-46 | WS-03 | parked | doc/plans/README.adoc; pm-core/doc/adr/core-store/; pm-core/doc/developer/core-store.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-workflow/doc/adr/workflow-unit/; pm-workflow/doc/developer/workflow-unit.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/unit/ |
| 25 | PLAN-47 | WS-03 | parked | doc/plans/README.adoc; pm-core/doc/adr/core-store/; pm-core/doc/developer/core-store.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-workflow/doc/adr/workflow-engine/; pm-workflow/doc/developer/workflow-engine.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/engine/ |
| 26 | PLAN-48 | WS-03 | parked | doc/plans/README.adoc; pm-core/doc/adr/core-store/; pm-core/doc/developer/core-store.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-workflow/doc/adr/workflow-unit/; pm-workflow/doc/adr/workflow-verify/; pm-workflow/doc/developer/workflow-unit.adoc; pm-workflow/doc/developer/workflow-verify.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/unit/; pm-workflow/src/main/java/de/planmarshall/workflow/verify/ |
| 27 | PLAN-49 | WS-08 | parked | doc/plans/README.adoc; pm-core/doc/adr/core-findings/; pm-core/doc/developer/core-findings.adoc; pm-core/src/main/java/de/planmarshall/core/findings/; pm-providers/pm-provider-forgejo/ |
| 28 | PLAN-50 | WS-03 | parked | doc/plans/README.adoc; pm-core/doc/adr/core-profile/; pm-core/doc/developer/core-profile.adoc; pm-core/src/main/java/de/planmarshall/core/profile/; pm-workflow/doc/adr/workflow-plan/; pm-workflow/doc/developer/workflow-plan.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/plan/ |
| 29 | PLAN-51 | WS-03 | parked | doc/plans/README.adoc; pm-core/doc/adr/core-store/; pm-core/doc/developer/core-store.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-workflow/doc/adr/workflow-engine/; pm-workflow/doc/developer/workflow-engine.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/engine/ |
| 30 | PLAN-52 | WS-03 | parked | doc/plans/README.adoc; pm-core/doc/adr/core-store/; pm-core/doc/developer/core-store.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-workflow/doc/adr/workflow-unit/; pm-workflow/doc/developer/workflow-unit.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/unit/ |
| 31 | PLAN-53 | WS-03 | parked | doc/plans/README.adoc; pm-core/doc/adr/core-store/; pm-core/doc/developer/core-store.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-workflow/doc/adr/workflow-unit/; pm-workflow/doc/developer/workflow-unit.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/unit/ |
| 32 | PLAN-55 | WS-05 | parked | doc/plans/README.adoc; pm-core/doc/adr/core-store/; pm-core/doc/developer/core-store.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-runtime/doc/adr/runtime-web/; pm-runtime/doc/developer/runtime-web.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/web/; pm-workflow/doc/adr/workflow-unit/; pm-workflow/doc/developer/workflow-unit.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/unit/ |
| 33 | PLAN-56 | WS-03 | staged | doc/plans/README.adoc; doc/plans/static-verifier-residuals.adoc; pm-workflow/doc/adr/workflow-verify/; pm-workflow/doc/developer/workflow-verify.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/verify/; pm-workflow/src/test/java/de/planmarshall/workflow/verify/; pm-workflow/src/test/resources/verify/ |
| 34 | PLAN-57 | WS-03 | parked | doc/plans/README.adoc; pm-core/doc/adr/core-store/; pm-core/doc/developer/core-store.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-workflow/doc/adr/workflow-engine/; pm-workflow/doc/developer/workflow-engine.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/engine/ |
| 35 | PLAN-58 | WS-03 | parked | doc/plans/README.adoc; pm-core/doc/adr/core-store/; pm-core/doc/developer/core-store.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-workflow/doc/adr/workflow-engine/; pm-workflow/doc/developer/workflow-engine.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/engine/ |
| 36 | PLAN-59 | WS-01 | parked | doc/plans/README.adoc; pm-core/doc/adr/core-store/; pm-core/doc/developer/core-store.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-runtime/doc/adr/runtime-start/; pm-runtime/doc/developer/runtime-start.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/start/ |
| 37 | PLAN-60 | WS-01 | parked | doc/plans/README.adoc; pm-core/doc/adr/core-metrics/; pm-core/doc/developer/core-metrics.adoc; pm-core/src/main/java/de/planmarshall/core/metrics/; pm-runtime/doc/adr/runtime-start/; pm-runtime/doc/developer/runtime-start.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/start/; pm-workflow/doc/adr/workflow-engine/; pm-workflow/doc/developer/workflow-engine.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/engine/ |
| 38 | PLAN-61 | WS-04 | parked | doc/plans/README.adoc; pm-core/doc/adr/core-profile/; pm-core/doc/adr/core-store/; pm-core/doc/developer/core-profile.adoc; pm-core/doc/developer/core-store.adoc; pm-core/src/main/java/de/planmarshall/core/profile/; pm-core/src/main/java/de/planmarshall/core/store/; pm-runtime/doc/adr/runtime-launch/; pm-runtime/doc/developer/runtime-launch.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/launch/ |
| 39 | PLAN-62 | WS-03 | parked | doc/plans/README.adoc; pm-core/doc/adr/core-store/; pm-core/doc/developer/core-store.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-workflow/doc/adr/workflow-unit/; pm-workflow/doc/adr/workflow-verify/; pm-workflow/doc/developer/workflow-unit.adoc; pm-workflow/doc/developer/workflow-verify.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/unit/; pm-workflow/src/main/java/de/planmarshall/workflow/verify/ |
| 40 | PLAN-63 | WS-01 | parked | doc/plans/README.adoc; pm-core/doc/adr/core-store/; pm-core/doc/developer/core-store.adoc; pm-core/src/main/java/de/planmarshall/core/store/; pm-runtime/doc/adr/runtime-start/; pm-runtime/doc/developer/runtime-start.adoc; pm-runtime/src/main/java/de/planmarshall/runtime/start/; pm-workflow/doc/adr/workflow-unit/; pm-workflow/doc/developer/workflow-unit.adoc; pm-workflow/src/main/java/de/planmarshall/workflow/unit/ |

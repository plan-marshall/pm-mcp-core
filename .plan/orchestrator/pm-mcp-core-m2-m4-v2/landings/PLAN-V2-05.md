# Landing Analysis: PLAN-V2-05 — Derived facts store operations

epic: pm-mcp-core-m2-m4-v2
workstream: WS-05
pr: #272 (merge commit `1331370a`, `1331370aee4c8a74f13f59fa72e4c76a25d2a379`)

> Landing record for one shipped plan. Lives at `landings/PLAN-V2-05.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source: inbox message `rb-v2-05-derived-facts-store-operations-001.md` (runbook lane, harness opencode, model
`deepseek-v4.1-flash/high`, effort high, executor 0.1.2041, standing rules r4, R01–R14, R16–R20). Corroborated against
`origin/main` (the merge diff of `1331370a`, 30 files) and the PR state read through `ci pr view` and
`ci checks status`.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 — the store and its reads | shipped-as-specified | `DerivedFactStore` (343 lines), `DerivedFactReading` (102) and `DerivedFactStoreTest` (272) in the merge diff of `pm-core/.../core/store/` |
| D2 — the recompute rule | shipped-as-specified | `DerivedFactDerivation` (87) and `DerivedFactRefreshTest` (309) in the merge diff |
| D3 — `stale` and `failed` | shipped-as-specified | `DerivedFactFailureTest` (278) in the merge diff; the 600→500 code-point cut and the kept digest are the lane's test evidence, not re-run here |
| D4 — one lock, no lost update (PM-WATCH-STORE-43) | shipped-as-specified | `DerivedFactStoreConcurrencyTest` (201), `SerializingLockManager` (59) and `pm-runtime` `DerivedFactStoreLockTest` (153) are in the diff; `LockOrderTest` is the existing `pm-core` class and is not edited. `doc/implementation-watch/store-schemas.adoc` loses 12 lines (the item). `RecordingAuditSink` already existed in `pm-runtime` test scope |
| D5 — the operator-only fact | shipped-as-specified | `DerivedFactModelFacingTest` (136) in the merge diff |
| D6 — guide, ADRs, condensed spec, closing the package | shipped-as-specified | `core-store.adoc` (+227), ADRs `056`–`060` (five new files), spec 09 (+8/-… condensed), `doc/Traceability.adoc` (+51), `doc/plans/derived-facts-store.adoc` deleted (-28), `doc/plans/README.adoc` (+10/-…) |
| D7 — the guard mapping | shipped-as-specified | `DerivedFactGuards` (53) and `DerivedFactGuardsTest` (182) in `pm-workflow`; the lane records the test-first failing run (6 of 7 against a stub), which is its evidence and was not re-run here |

Surface delta: **30 files**, 2,706 insertions, 79 deletions. Inside the declaration: all seven main/test classes of
`core.store`, `SerializingLockManager`, `DerivedFactGuards` and its test, `DerivedFactStoreLockTest`, spec 09, the watch
item, the package file. **Outside it**, all consequential and named by the lane: both `package-info.java` files
(`core.store`, `workflow.engine`; R05), `store-schemas/13-leases-integrity-testing.adoc` (drops the inbound link to the
deleted watch item), the three edited ADRs `053`–`055` (their `Source:` repointed off the deleted package file),
`pm-workflow/doc/developer/workflow-engine.adoc` (D7's guide duty). `doc/Traceability.adoc`, `doc/plans/README.adoc`, the
guide and the ADR directory are shared bookkeeping (R19). The declared `DerivedFactFixtures`-style helper was not needed.

## Metrics and Anomalies

- Tokens: `unknown` (unobserved, not zero). Duration: not measured. `plugin_version` is `unknown` as well — the same
  incomplete landing message as PLAN-V2-02 and PLAN-V2-107 (the open defect in `epic.md`).
- Anomalies: none in the delivery. Sonar: the landing reports `open_issue_count=0`, `open_issue_rules=none`; the PR's
  `SonarCloud Code Analysis` check is `SUCCESS` — consistent. The lane fixed four new-code issues on the way
  (S6878, S7467, S5778, S1130).
- The lane's rule-check section lists read-only shell habits (a `for` loop, `ls`, `find | head`) it did not follow. No
  source was changed by them; not a delivery defect.

## Routing and Merge Behavior

- Review: CodeRabbit reviewed `dd385719`, `2cccf6ce` and `d7e666f4` (two inline findings, both handled, both threads
  resolved); the final head `00b9e0b0` was triggered and not reviewed (silence recorded). The `CodeRabbit` check on the PR
  reads `SUCCESS`. Sourcery refused on its budget (optional). The lane merged under the standing review authorization.
- CI/merge: `ci pr view` reads `merged` with `1331370a`; `ci checks status --pr-number 272` reads
  `overall_status: success` over 20 checks, `build (25)` and `build (26)` both green, `integration-tests / test` green.
- Sonar on `main`: unchanged by this plan. The gate on `main` still carries the null-rule issues of PLAN-V2-84
  (PLAN-V2-110 is working them down); none is attributable to this change.
- Collision check: PLAN-V2-05 ran beside PLAN-V2-110, PLAN-V2-01 and PLAN-V2-08 as predicted disjoint. No collision was
  reported; the prediction held.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-V2-05 --status shipped`
- [x] row `pr` stamped — `272`
- [x] row `landing` stamped — `landings/PLAN-V2-05.md`
- [x] row `plan_marshall_plan_id` stamped — `v2-05-derived-facts-store-operations` (taken from the sender `rb-v2-05-derived-facts-store-operations`)
- [x] epic.md narrative reconciled — the incomplete-landing defect records the fourth recurrence; the sibling link is a Watch
- [x] resume anchor updated in `resume_anchor.md`
- [x] `queue-view.md` regenerated and committed with the row change

## Follow-Ups

- The package `derived-facts-store` is closed (package file and index row gone). PLAN-V2-12 (`ci-contract-module`) and
  PLAN-V2-14 (`module-discovery`) waited for this plan; the 05 dependency is met.
- One owed fix in the sibling repository: `plan-marshall-documentation` `doc/requirements/14-implementation.adoc` line 135
  still links `PM-WATCH-STORE-43`, which no longer exists (`trace.py` reports `BROKEN LINKS 1`). Recorded as a Watch; it
  needs a documentation PR, not a plan here.
- Owed outside this repository (the lane's residue): `plan-marshall-mcp` must serve only `modelFacing()` for every
  representation (`pm://project/config`, tool results) with its `ModelFacingFieldAuditTest`, the operator API serves
  `operatorFacts()`, and the guard consumer that reads a fact and calls `DerivedFactGuards.evaluate` is still to be wired.
  The consumer plans (PLAN-V2-12, 14, 17) call `refresh`; nothing reads a fact at HEAD.

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
| D4 — one lock, no lost update (PM-WATCH-STORE-43) | shipped-as-specified; its tests do not cover the watch item's guard as worded (see Three-Layer Analysis, S1 and I1) | `DerivedFactStoreConcurrencyTest` (201), `SerializingLockManager` (59) and `pm-runtime` `DerivedFactStoreLockTest` (153) are in the diff; `LockOrderTest` is the existing `pm-core` class and is not edited. `doc/implementation-watch/store-schemas.adoc` loses 12 lines (the item). `RecordingAuditSink` already existed in `pm-runtime` test scope |
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

## Three-Layer Analysis (standing rule R21, r5)

Done by reading at the merge commit `1331370a`: `DerivedFactStore`, `DerivedFactReading`, `DerivedFactDerivation`,
`DerivedFactGuards`, the seven new test classes, ADR `core-store/056`, the guide section, spec 09, the deleted watch item
(`git show 1331370a`), `FileLockManager`, `SchemaSubset`. No build or test was run. Not shown by reading: the 80 %
coverage, the crash windows of `AtomicStoreFile`, and the behaviour under the operating-system lock; those rest on the
lane's runs and on CI.

### Layer 1 — Specified: sound, one premise of the spec is wrong

- **S1 (finding, spec premise).** Deliverable 4 says the three test classes "cover the guard" of PM-WATCH-STORE-43, so the
  watch item is deleted. The item's guard is: a plan transaction and a second refresher refresh the same fact at once,
  both serialize on `locks/derived-facts.lock`, "the lock-order checker rejects any acquisition outside the total order",
  no update is lost. The spec assumed a refresh made inside a scope transaction is order-checked by the manager.
  `FileLockManager` asserts the order **within one transaction only** (class Javadoc, lines 77 to 78) and `refresh` opens
  its **own** transaction, so a held plan or workspace lock of the caller is never seen. The lane found this (comment in
  `DerivedFactStoreLockTest`, lines 54 to 57) and substituted a direct call of the pure function `LockOrder.check` on a
  hand-built list. The watch item was deleted all the same, as the spec demanded. The hazard's deadlock leg is in fact
  excluded by the structure (the store holds one lock, calls no foreign code under it, and the derivation runs outside),
  but nothing tests that structure. To correct: a narrowed watch item ("the order is by convention across
  transactions") or a store API that takes the caller's transaction; and the sentence of spec 09 line 15 and of the
  guide (lines 1218 to 1220), which read as enforced ("a violation the lock manager refuses").
- **S2 (precision).** "a later `derived_at`" compares the caller's **start** instant (`now`), not the completion
  instant, and says nothing of equal instants (last writer wins; the lane's residue). Both are now written in ADR 056,
  not in spec 09.
- **S3 (nit).** ADR 057 gives "keeps the last good digest so the retry is automatic" as the reason; the retry is already
  guaranteed by the table (`stale` and `failed` derive again whatever the digest). The kept digest is harmless, but it is
  not what makes the retry automatic.
- The three HYPOTHESIS claims were confirmed by the lane; the premise "no consumer reads a fact at HEAD" holds (reading).

### Layer 2 — Implemented: every deliverable has code and a test; two tests are weaker than they read

- Each Done-when traces to code and a test (D1 `DerivedFactStore` and its `reading`; D2 `mustDerive` with the one-second
  boundary cases at `DerivedFactRefreshTest` 194 to 230; D3 `entryOf` with the six transitions and the 600→500 cut; D5
  `modelFacing` through `DerivedFactKind.operatorOnly()` with a registry that never registered `agent_identity`; D7
  `DerivedFactGuards`). The code matches the spec's wording on D1 to D3, D5 and D7.
- **I1.** `DerivedFactStoreLockTest.planLockThenRefresh` cannot fail on the lock order: refresh opens its own
  transaction, so it succeeds whatever the level of `DERIVED_FACTS` is. The order is carried by the two `LockOrder.check`
  assertions, which restate `LockOrderTest` (existing). The test is single-threaded, so it does not exercise "a plan
  transaction and a refresher at once" either (see S1).
- **I2.** "No update is lost" across keys (`twoDifferentKeys`, `twoKeysAtOnce`) races two threads with no forced
  interleaving; against an implementation that wrote from the pre-derivation snapshot it would pass whenever the
  threads happen not to overlap. Only the same-key path is proven deterministically (`updateKeepsANewerStoredResult`
  refreshes inside the derivation). A nested refresh of the second key inside the first key's derivation would force it.
  `twoRefreshersOfOneKey` accepts either winner and checks the entry's consistency only.
- **I3 (R20, observation).** `DerivedFactStoreConcurrencyTest` and `DerivedFactStoreLockTest` use no generator; the
  64-digit digest is the literal `"a".repeat(64)` and the instants are fixed in all seven classes. The digest is a format
  contract and the lane marks the name literals; the conformance family (PLAN-V2-85 to 89) will rework them anyway.
- Surface: as in the delta above; no file is unexplained.

### Layer 3 — Implementation correctness: no defect that breaks the specified behaviour; three consequences to know

- Read and found right: the decision table and the reading use the same boundary (`!now.isBefore(derivedAt + 86400 s)`),
  so `refresh` and `reading` never disagree; the write re-reads under the lock and calls no foreign code while holding it
  (so no deadlock from this class); the code-point cut is on code points, not chars; a `stale` entry keeps value,
  source, digest and `derived_at`; `modelFacing()` is decided by the enum, not the registry; `DerivedFactGuards` is
  fail-closed for every variant but `Value`; a derivation that throws writes nothing (documented as the consumer's fault).
- **C1 (consequence of S2).** Because `derived_at` is the caller's start instant and a `failed` entry's `derived_at` is
  its attempt instant, a slow success that started earlier loses to a failure that started later for the same digest:
  `update` returns the stored `failed` entry, so the successful caller gets `Indeterminate`. The next call derives again
  (state `failed`), so it self-heals and fails closed.
- **C2 (plausible, not triggered today).** `SchemaSubset` prints the offending value in its enum and const violations
  (`SchemaSubset.java` 103 and 106) and the store copies the violations into `failure.reason`; the reason travels in
  `couldNotEvaluate()` into `GuardResult.CouldNotEvaluate`. For an operator-only kind (`agent_identity`) whose registered
  schema has an enum or a const, a model-facing guard result could carry the operator-only value. No consumer or guard
  reads `agent_identity` yet. To pass on to the plan that registers the kind and to the assembly: do not echo violations
  of an operator-only kind.
- **C3.** A last-writer-wins write of two different digests can replace a newer snapshot's value by an older one
  (ADR 056 says so); acceptable for a cache, recorded.

### Verdicts

| Layer | Verdict | Open items |
|-------|---------|-----------|
| Specified | sound, one wrong premise | S1 (watch item deleted on a coverage the tests do not give; spec 09 and guide read as enforced), S2, S3 |
| Implemented | complete, two tests weaker than named | I1, I2, (I3) |
| Correct | no defect found by reading | C1, C2, C3 as consequences; nothing demands an inline fix |

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-V2-05 --status shipped`
- [x] row `pr` stamped — `272`
- [x] row `landing` stamped — `landings/PLAN-V2-05.md`
- [x] row `plan_marshall_plan_id` stamped — `v2-05-derived-facts-store-operations` (taken from the sender `rb-v2-05-derived-facts-store-operations`)
- [x] epic.md narrative reconciled — the incomplete-landing defect records the fourth recurrence; the sibling link is a Watch
- [x] three-layer analysis (R21) written above; findings S1, I1, I2, C2 carried to `epic.md` § Watches (one Watch)
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

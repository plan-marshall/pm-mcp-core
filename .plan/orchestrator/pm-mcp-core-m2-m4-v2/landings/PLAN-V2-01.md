# Landing Analysis: PLAN-V2-01 — A primitive's failure, skip and indeterminacy carry a typed, closed reason

epic: pm-mcp-core-m2-m4-v2
workstream: WS-01
pr: #271 (merge commit `44cfe8d7`, `44cfe8d71f562576e21f7a7f6d71470a8180c6c0`; the first PR #267 was closed and replaced by the quota policy's fresh-PR step)

> Landing record for one shipped plan. Lives at `landings/PLAN-V2-01.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source: inbox message `rb-v2-01-primitive-failure-reasons-002.md` (runbook lane, harness opencode, model
`deepseek-v4.1-flash/high`, effort high, executor 0.1.2041, standing rules r4, R01–R12, R17–R20). Corroborated against
`origin/main` (the merge diff of `44cfe8d7`, 21 files, +1822/-15) and the PR state read through `ci pr view` and
`ci checks status`; the documentation PR read through `ci pr view` with `GH_REPO`.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 — the contract types | shipped-as-specified | `OutcomeReason` (54 lines, new), `PrimitiveOutcome.reasonType()` (+13), `PrimitiveResult` (+128, fourth component, three-component constructor, pairing invariant, hand-written builder), `package-info.java`; `OutcomeReasonTest` (98) and `PrimitiveResultTest` (239) are new; `PrimitiveContractTest` is not in the diff (unchanged) |
| D2 — the registry-time checks | shipped-as-specified | `PrimitiveRegistry` (+114: `checkReason` after the outcome's own checks), five new `PrimitiveRegistrationException.Reason` constants (+21); `PrimitiveRegistryTest` (+29: the union of both `refused()` lists, an acceptance test for FAILURE/SKIP/INDETERMINATE, an old-refusal-first test); `ReasonPrimitives` is new, 606 lines on `main` (the lane's message says 574; its figure is stale, the over-budget deviation it reports stands). `TestPrimitives` is not in the diff |
| D3 — the specification | shipped-as-specified | `05-primitives.adoc` +39: the SPI block, the `SKIP` bullet, the new `[#outcomes-and-reasons]` subsection; the concrete example below it is untouched in the diff |
| D4 — the developer guide and eight ADRs | shipped-as-specified | `core-spi.adoc` +127; eight new ADRs `005`–`012` in `pm-core/doc/adr/core-spi/`; `doc/Traceability.adoc` +24/-… |
| D5 — the documentation hand-off | shipped-as-specified, open on the operator | `plan-marshall/plan-marshall-documentation#138` is **open** (read through `ci pr view`, title "docs: carry the outcome reason in the primitive contract"); merging it is the operator's |

Surface delta: **21 files**. Inside the declaration: all five main classes of `core.spi` plus `OutcomeReason`, the three
test classes (two new, `PrimitiveRegistryTest` extended) and `ReasonPrimitives`, and `05-primitives.adoc`. The rest is the
declared shared bookkeeping (R19: `Traceability.adoc`, the guide, the ADR directory). No file outside the declaration. No
fixture enum, primitive fixture or `new PrimitiveResult(` site was edited (`TestPrimitives`, `PrimitiveContractTest` and the
`pm-workflow` fixtures are not in the diff), which is the spec's additive constraint.

## Metrics and Anomalies

- Tokens: `unknown` (unobserved, not zero). Duration: not measured. `plugin_version` is `unknown` as well — the open
  defect in `epic.md`, fifth recurrence.
- Anomalies: none in the delivery. Sonar: the landing reports `open_issue_count=0`; the PR's `SonarCloud Code Analysis`
  check is `SUCCESS` — consistent. The lane fixed ten first-head issues and kept several `@SuppressWarnings` with a stated reason (see C2).
- The lane ran every build on the daemon; one `verify -Pcoverage` hit the explicit bound and was retried with
  `--timeout 1500` (848 s). No degraded fallback.

## Routing and Merge Behavior

- Review: CodeRabbit refused repeatedly on quota; after the fresh-PR step (#267 closed, #271 opened) it reviewed
  `7e06dce0`, `feb6346a` and `af30c70c`; the final head `9477cc85` was refused at 2026-11-01T18:17:47Z. The lane merged
  under the standing review authorization because a qualifying review of an earlier head and every handled finding
  existed. This is the same shape as PLAN-V2-05 (the final head is unreviewed). Sourcery refused (optional).
- CI/merge: `ci pr view --pr-number 271` reads `merged` with `44cfe8d7`; `ci checks status --pr-number 271` reads
  `overall_status: success` over 20 checks (`build (25)`, `build (26)`, `sonar-build`, `integration-tests / test`,
  SonarCloud all green; `deploy-snapshot` skipped on the PR, as always).
- Sonar on `main`: not read for this landing. The gate on `main` still carries the null-rule issues of PLAN-V2-84
  (PLAN-V2-110 is working them down).
- Collision check: PLAN-V2-01 ran beside PLAN-V2-110, 08, 11 and (earlier) 05. The spec predicted disjointness; no
  collision was reported and none is visible in the diff.

## Three-Layer Analysis (standing rule R21, r5)

Done by reading at the merge commit `44cfe8d7`: `OutcomeReason`, `PrimitiveOutcome`, `PrimitiveResult`, the registry diff
(`checkReason` and its helpers), `OutcomeReasonTest`, `PrimitiveResultTest`, the `PrimitiveRegistryTest` diff, the refusal
list of `ReasonPrimitives`, the spec diff of `05-primitives.adoc`, and the two watch items the subsection cites. **Not
read:** the developer guide text, the eight ADR bodies (only their file names and sizes), the `Traceability.adoc` lines.
No build or test was run. Not shown by reading: that the tests pass, the 80 % coverage, and the Sonar state of `main`; those
rest on the lane's runs and CI.

### Layer 1 — Specified: sound; two points the spec carried without saying so

- **S1 (wording, low).** The new subsection says `PM-WATCH-DSL-15` and `PM-WATCH-DSL-22` "keep their text, and they state
  exactly that" (that nothing routes on a reason). The items say something narrower: DSL-22, one target per outcome and a
  router for a branch; DSL-15, result guards partition every `done` transition. Neither mentions a reason. The claim holds
  by implication (the outcome is the only edge, a guard reads a boolean or optional field of `result`, a reason is neither),
  but "state exactly that" is stronger than the items. Spec text only; the code does not depend on it.
- **S2 (known conflict, accepted).** Decision 4 makes the reason an `Optional` record component. `CLAUDE.md` and R20 say
  `Optional` is a return type only and a record of four components carries `@Builder`. The spec did not name the conflict;
  the lane found it, followed the spec (it matches `spawnedJobId`) and reported both deviations. The consequence is a
  hand-written builder (Lombok's `@Builder.Default` does not apply to record components). Accepted in the ledger already;
  recorded here so the next record with an optional component does not rediscover it.
- **S3 (design note).** The pairing is "reason present iff the outcome declares a type", so a primitive that declares
  reasons on `failed` can never return a bare `failed`; it needs a catch-all constant of its own. The spec does not say so.
  This is a consequence of the rule (every non-routing cause is a reason), not a flaw; `PLAN-V2-20`, the first user, will
  show whether a catch-all (`unspecified`-style) constant is what authors reach for.
- The rest is sound: the decisions are consistent with each other and with the SPI as it was; the closed condition table
  matches `WorkflowStatus`'s persisted kinds and the test pins it there; the additive constraint was achievable and held.

### Layer 2 — Implemented: every deliverable has code and a test; the message assertions are weak

- All five deliverables exist. The five refusal reasons each have fixtures in `ReasonPrimitives.refused()` (the list reads:
  five `MALFORMED_REASON_TYPE` cases — a non-enum, a non-class, a null Optional, an enum that does not implement
  `OutcomeReason`, an enum without a constant —, three `REASON_ON_UNADMITTING_CLASS` cases, two `MALFORMED_REASON_WIRE_NAME`, one `DUPLICATE_REASON_WIRE_NAME`, two
  `UNMAPPABLE_REASON_CONDITION`), and `fixtureCoversEveryReason` now reads the union, so a sixth reason without a fixture
  fails the build. The "old refusal comes first" test pins the ordering the spec asked for.
- **I1 (weak assertion, low).** `PrimitiveResultTest` `refusesMissingReason`, `refusesForeignReason` and
  `refusesReasonOnUnadmittedOutcome` each assert only that the message `contains("reason")`. All three messages contain it,
  so the tests prove the exception type and the input that triggers it, not which of the three branches fired. A swap of the
  branches' messages would pass. The branch is still distinguished by input, so a swapped *condition* would fail one test.
- **I2 (coverage nicety, low).** The pairing is tested at the result with a FAILURE outcome only; SKIP and INDETERMINATE are
  covered at the registry (an acceptance test for all three). The pairing code does not look at the class, so this is not a gap.
- **I3 (stale figure).** The message states `ReasonPrimitives` at 574 lines; `main` has 606. The lane reported the
  over-budget deviation, so its substance is right; the number is from an earlier head.
- `OutcomeReasonTest` states the two kinds literally and checks each against the real `PendingCondition` constructor — the
  guard the spec asked for. One assertion passes the arguments to `assertEquals` as (actual, expected); harmless.

### Layer 3 — Implementation correctness: no defect found by reading; two consequences to know

- Registry: `checkReason` runs after every older check, `checkReasonType` short-circuits before `getEnumConstants()` can be
  null, the wire-name and condition checks tolerate a raw-typed null, and the class check uses an `EnumSet` of the three
  admitting classes. The order of refusals follows the spec. The pairing invariant in `PrimitiveResult` refuses the three
  wrong shapes and a non-`Class` payload; `declaredType.isInstance` is the right test for a constant-bodied enum.
- **C1 (asymmetry, harmless).** A `null` from `reasonType()` is refused at registration (`MALFORMED_REASON_TYPE`) but read
  as "none" by the result constructor. A registered primitive cannot return `null` there, so only an unregistered outcome
  can reach it, and then it is refused as a pairing error if a reason is passed. No wrong behaviour; two code paths with
  one meaning.
- **C2 (suppressions, direction).** The change adds `@SuppressWarnings` for `java:S2583`, `S2789`, `S1905`, `S2589` (the raw-type null checks in
  `PrimitiveResult` and `PrimitiveRegistry`) and `S2637` (the builder's unset `outcome`), each with a comment. They guard against a raw-typed caller in a `@NullMarked`
  package. `PLAN-V2-110` is bringing exactly these rules on `main` to zero by fixing sites, not ignoring them (operator
  decision 2026-10-31). The PR reads 0 open issues, so the suppressions work. Whether defensive checks behind a suppression
  are the stance `PLAN-V2-110` sets for new code is not shown by reading; it is a convention question for the operator, not
  a defect. If the answer is no, the fix is to delete the raw-type checks, not to suppress.
- **C3 (builder, consequence).** `Builder.outcome` is unset until set, though the package is `@NullMarked`; `build()` without
  an outcome throws `NullPointerException("outcome")`, documented in the Javadoc. That is the S2637 suppression above.

### Verdicts

| Layer | Verdict | Open items |
|-------|---------|-----------|
| Specified | sound; two points carried silently | S1 (watch wording), S2 (accepted), S3 (design note for `PLAN-V2-20`) |
| Implemented | complete; assertion messages weak | I1, I2, I3 |
| Correct | no defect found by reading | C1, C2, C3 as consequences; nothing demands a fix plan |

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-V2-01 --status shipped`
- [x] row `pr` stamped — `271`
- [x] row `landing` stamped — `landings/PLAN-V2-01.md`
- [x] row `plan_marshall_plan_id` stamped — `v2-01-primitive-failure-reasons` (from the sender `rb-v2-01-primitive-failure-reasons`)
- [x] epic.md narrative reconciled — the incomplete-landing defect records the fifth recurrence; the "PLAN-V2-01 unmerged" Watch is retired and replaced by the open documentation PR
- [x] three-layer analysis (R21) written above; no layer-3 defect, so no fix plan; C2 carried to `epic.md` as a question for the operator
- [x] resume anchor updated in `resume_anchor.md`
- [x] `queue-view.md` regenerated and committed with the row change

## Follow-Ups

- `plan-marshall/plan-marshall-documentation#138` (three hunks) was merged on the operator's word on 2026-11-01 (`3b04f805`). The `trace.py`
  count the lane reports (`BROKEN LINKS 2`) is the sibling's own `model-work` links.
- Plans that waited for this one are now unblocked on it: PLAN-V2-19 (`engine-carries-reasons`), PLAN-V2-20
  (`git-commit-primitives`), PLAN-V2-25 (`command-record-and-dispatch`), and through them PLAN-V2-24, 29, 75, 22, 64. Their
  other dependencies are unchanged. PLAN-V2-20 should read S3 above at its outline.
- The lane worktree was removed and the branch refs pruned (the message says so); nothing owed.

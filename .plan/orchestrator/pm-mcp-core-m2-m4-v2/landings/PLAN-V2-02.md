# Landing Analysis: PLAN-V2-02 — Derived facts entry records, codec and kind registry

epic: pm-mcp-core-m2-m4-v2
workstream: WS-05
pr: #266 (merge commit `f14b4ff3`, `f14b4ff303e4ab20995eacece5e1bc70bd082b9a`)

> Landing record for one shipped plan. Lives at `landings/PLAN-V2-02.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source: inbox message `rb-v2-02-derived-facts-records-and-kinds-001.md` (runbook lane, harness opencode, model
`deepseek-v4.1-flash/high`, effort high, executor 0.1.2041, standing rules r4, R01–R14, R17–R20). Corroborated against
`origin/main` (the merge diff of `f14b4ff3`) and the PR state read through `ci pr view` and `ci checks status`.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 — package file `derived-facts-store`, index row | shipped-as-specified | `doc/plans/derived-facts-store.adoc` (+28) and `doc/plans/README.adoc` (+6/-4) are in the merge diff |
| D2 — `DerivedFactKind`, `DerivedFactKey`, `DerivedFact`, `DerivedFacts` with their tests | shipped-as-specified | the four classes and `DerivedFactKindTest`, `DerivedFactKeyTest`, `DerivedFactTest` are in the merge diff of `pm-core/.../core/store/` |
| D3 — `DerivedFactsCodec`, strict round trip, canonical bytes | shipped-as-specified | `DerivedFactsCodec.java` (305 lines) and `DerivedFactsCodecTest` (281 lines) in the merge diff |
| D4 — parity test against the schema block | shipped-as-specified | `DerivedFactsSchemaParityTest` (173 lines) in the merge diff; its mutation-after evidence (a changed bound failing on a scratch copy) is the lane's, not re-run here |
| D5 — kind registry, no concrete kind | shipped-as-specified | `DerivedFactKindSpec`, `DerivedFactKinds`, `DerivedFactKindsTest` in the merge diff; no `DerivedFactKinds` registration of a concrete kind in the diff |
| D6 — guide section, ADRs, reduced specification | shipped-as-specified | `pm-core/doc/developer/core-store.adoc` (+114), three ADRs `core-store/053`, `054`, `055`, `09-derived-facts-and-build-timings.adoc` (+3/-2), `doc/Traceability.adoc` (+40) |

Surface delta: **22 files** in the merge diff — 7 main sources, 6 test classes, `package-info.java` (the conditional D6
edit the spec allows), the specification file, the package file and index, the guide, three ADRs and Traceability. Every
code and test entry is inside the declared `core.store` surface; the one declared helper `DerivedFactFixtures` was not
needed and is not in the diff. Nothing outside the declaration was touched.

## Metrics and Anomalies

- Tokens: `unknown` (the opencode harness measured none; recorded as unobserved, not as zero). Duration: not measured.
- Test count of the lane (3,896 at `12d74851`) and the 0-new-issues Sonar claim were not re-measured here; the PR's own
  SonarCloud check reads `SUCCESS` (below).
- Anomalies: none. One hypothesis refuted by the lane (a number's digits do not survive a round trip for an
  exponent-spelled number or a negative zero; the canonical form is the writer's) and recorded in ADR 054 and the codec
  test — a finding about the spec's premise, not a defect of the delivery.

## Routing and Merge Behavior

- Review: CodeRabbit reviewed `6d6bf2ad` (all three threads resolved, every finding handled) and was quota-refused on the
  three later heads; the lane merged under the standing review authorization. Sourcery refused on its own budget
  (optional).
- CI/merge: `ci pr view` reads `merged` with `f14b4ff3`; `ci checks status --pr-number 266` reads `overall_status: success`
  over 20 checks. No rebase conflict reported.
- Sonar: the landing reports `open_issue_count=0`, `open_issue_rules=none`, and the PR's `SonarCloud Code Analysis`
  check is `SUCCESS` — consistent. That is a measure of the PR's new code only; `main` carries the open null-rule issues
  PLAN-V2-84 caused (133 of `java:S2583`/`S2589`/`S2637` at this analysis, falling with PLAN-V2-110), none introduced by
  this change.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-V2-02 --status shipped`
- [x] row `pr` stamped — `266`
- [x] row `landing` stamped — `landings/PLAN-V2-02.md`
- [x] row `plan_marshall_plan_id` stamped — `v2-02-derived-facts-records-and-kinds` (taken from the sender `rb-v2-02-derived-facts-records-and-kinds`)
- [x] epic.md narrative reconciled — the incomplete-landing defect (`total_tokens`, `plugin_version` unknown) records the recurrence
- [x] resume anchor updated in `resume_anchor.md`
- [x] `queue-view.md` regenerated and committed with the row change

## Follow-Ups

- The package `derived-facts-store` stays open: PLAN-V2-05 (`derived-facts-store-operations`) delivers the store and closes
  it. PLAN-V2-05 already exists in the queue (staged); nothing new to stage.
- Owed outside this repository by the lane's own residue: concrete kind registrations and the facts' serving
  (`plan-marshall-mcp`), concept and user documentation at the milestone exit. Recorded here, not as a new item.

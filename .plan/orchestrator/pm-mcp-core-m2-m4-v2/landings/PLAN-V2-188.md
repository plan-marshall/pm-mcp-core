# Landing Analysis: PLAN-V2-188 — schema and store removals: the members and enum values the merged specification dropped

epic: pm-mcp-core-m2-m4-v2
workstream: WS-12
pr: #621 (https://github.com/plan-marshall/pm-mcp-core/pull/621), merge `88680dd7` (squash through the merge queue, parent `3c7122f6`), analysed on the fortieth run (second pass), 2026-11-26

> Landing record. Source: the lane's inbox message `rb-v2-188-schema-and-store-removals-002` (landing-facts present, `merge_state=merged`, `complete: false`, see Metrics), read against git (`git diff 3c7122f6 88680dd7`: 24 files, +404/-80) by one read-only leaf (`execution-context-level-3`, `orchestrator.analyze`, R21) that was given the spec, the lane's message and the orchestrator's mailbox item as data copies and opened no `.plan/` path. The orchestrator re-read the layer-3 points at their lines (`outline-record-v1.schema.json:84-88`, `phase-workflows/03:136-144`, `decision-record.schema.json:50-55`, `model-work/11:113-117`, `RemovedSchemaMembersTest.java:205-226`, `UnreadSchemaFilesTest.java:49`, `phase-workflows/01:118-122`). The leaf's severities are the orchestrator's after that re-read.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 schema-only removals and wording | shipped-as-specified, with the mailbox additions; one wording not as specified (L3-2) | counter `assessment_id`, `metadata.request_aspect`, `pr_title_stale`, `assessment-record.schema.json` (file gone), the `UnreadSchemaFilesTest` CODECLESS entry, ADR 018 (27 to 26, amended and not rewritten), the quality-report words, queue-row `regrounded_at_sha` and `reground_verdict` (also from `required`), `epic.overlap_found`, the message kind `overlap_found`, the edge resolver `python`, `structural_caps`; every enclosing object is still closed (`additionalProperties: false`, leaf read each) |
| D2 the derived-fact kinds | shipped-as-specified with the mailbox | `DerivedFactKind` holds thirteen constants in the schema's order (leaf compared them element by element; `derived-facts.schema.json:25`); `branch_up_to_date_required`, `required_checks` and `checks_visible` gone; `DerivedFactsCodecTest:229-259` refuses each removed kind UNREADABLE with the key named; `MERGE_QUEUE_ENFORCED` stays and its Javadoc is reworded |
| D3 the "Unicode fixpoint" wording | shipped-as-specified | `IngestionValidator.java:36-37`, `runtime-ingest.adoc:18-20, :77-78` say removes hidden characters, then NFC, no pass limit; no "fixpoint" is left |
| Mailbox item (`orchestrator-pm-mcp-core-m2-m4-v2-001`) | all three asks delivered | the two kinds removed with a control per kind; the count is thirteen in the Javadoc, `core-store.adoc:1424` and the ADR 053 stamp; `finding-record.schema.json:40` and `Finding.java:237` reworded |
| Done-when "fails at `e451ed04`" | proof missing, substance present | the lane's red runs are at its own branch base (13 of 14 cases red, the positive control green) and mutation runs; the literal-SHA run was never made because the base tree has no executor (the epic's recurring executor gap); the leaf read the 14 cases against the parent tree's diff: 13 assert something false on `3c7122f6` |
| Done-when builds, coverage, integration, Sonar | not shown by reading | the lane reports `verify -Ppre-commit` 12929, full `verify` 12993, coverage, integration (21), package; Sonar `0` open new issues, quality gate `ok` |

`deliverables_done=1` of 3 in the landing facts is the lane's conservative count for the unrun base-SHA clauses. By reading, no substantive clause is missing: D1 and D2 are implemented and proved by tests that fail on the parent tree; D3 is met.

## Three-Layer Analysis

### Layer 1 — Specified

26 rows read, all shipped as specified or shipped-modified by the mailbox. One minor: **L1-1** the spec said to *replace* the `outline.classify-change-type` example of `decision-record.schema.json:53`; the lane dropped it (see L3-2). One cosmetic: the `change_type` description cites `phase-workflows/03, Server-Derived change_type` and not the anchor id `#change-type-derivation` (the heading and anchor exist, `phase-workflows/03:136-137`).

### Layer 2 — Implemented

Checked (leaf read, orchestrator re-read where marked in the header): the thirteen constants in the schema's order; no reference to a removed constant (`MERGE_QUEUE_ENFORCED` only); no `assessment-record` or `assessment_id` outside the new test and the ADR 018 amendment; every removed member gone at its line; the 14 cases of `RemovedSchemaMembersTest` against the parent tree; the codec refusal (`DerivedFactsCodec.readFacts` `:227-228`). Not shown by reading: every test, build and coverage result and the counts 12929, 12993, 8560 and 21; the red-first and mutation logs; Sonar and the review gate; whether `UnreadSchemaFilesTest`'s completeness check and the other whole-resource scans stay green after the deletion.

### Layer 3 — Correct

No major defect. Re-read at the lines: the leaf's L3-1 and L3-2 hold; its L3-3 is downgraded; its L3-8 stays a lead.

| Defect | File:line | Reading | Effect | Severity |
|--------|-----------|---------|--------|----------|
| L3-1 | `outline-record-v1.schema.json:88`; `RemovedSchemaMembersTest.java:270` | the per-deliverable `change_type` still reads "reconciled with the plan's change_type"; `phase-workflows/03:140` has the server derive the plan's type from the deliverables, and the only reconciliation left is the read-only refusal (`:142`); the test pins the word "reconciled with the plan" | the wording keeps a concept the plan set out to remove; nothing fails | minor |
| L3-2 | `decision-record.schema.json:53` | the example list lost `outline.classify-change-type` and gained nothing; `outline.change-type` is a closed decision point (`model-work/11:115`) and matches the pattern | the list is one example short | minor |
| L3-3 | `RemovedSchemaMembersTest.java:217` | `Path.of("src/main/resources")` depends on the working directory; **re-read: `UnreadSchemaFilesTest.java:49` uses the same form**, so it is the module's convention; the footprint-description case under the nested class `AssessmentRecordSchema` is the lane's declined placement item | none | info (leaf: minor) |
| L3-4 | `RemovedSchemaMembersTest.java:35-40`, `:276` | the class Javadoc says each description case asserts the statement it keeps beside the one it loses; `scopeClassReadsTheRealizedFootprint` asserts only the new statement | the test still fails on the parent tree | info |
| L3-5 | `WorkflowStatusCodec.java:175,177,552-592` | a stored old `assessment_id`, `request_aspect` or `pr_title_stale` is a nested member the codec keeps as raw text: only a derived-fact kind is refused at read; the negative controls of these members are schema-level, as the spec said | none by design | info |
| L3-6 | `queue-row`, `report-event`, `message-event`, `domain-descriptor`, `review-bot-registry` | no Java reader or writer of any removed member in the four modules; no fixture carries one; the spec `include::` lines carry no ranges that moved | clean | info (clean) |
| L3-7 | counts | no fifteen, sixteen (as a kind count) or twenty-seven survives; the historical ones are in ADR 053 `:17, :34` and ADR 018 `:19` under their stamps | clean | info (clean) |
| L3-8 | `phase-workflows/01-planning-phases-1-2.adoc:120` | the `qgate` row still says "Assessments and findings" (re-read: the line is there); whether it names the removed assessment store or the Q-gate's own assessments is not decidable from the line | a lead, not a fact | info |
| L3-9 | `finding-record.schema.json:40`, `Finding.java:237` | the two descriptions of `required_check` say slightly different things (the Javadoc drops "for the base branch"); neither contradicts `phase-workflows/04:246` | wording | info |

**Layer 3 verdict:** findings — 9 (two minor, five info, two clean confirmations); not shown by reading — the runs above. Nothing is reported at once: no major finding.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md Code Standards | JUnit 5, `@DisplayName`, three or more variants as `@ParameterizedTest` | deviates (style) | `DerivedFactsCodecTest:229-259` has two near-identical single-variant tests and a helper (the lane's declined item 2) |
| CLAUDE.md Code Standards | every assertion carries a message of 20 to 60 characters | not shown by reading | the lane's own `assertion-messages` scan is part of its green `verify` |
| CLAUDE.md Module Documentation | a plan states the developer documentation and the ADR stamps it changes | conforms | `core-store.adoc:1424`, ADR 018 and 053 stamped, `runtime-ingest.adoc` |
| CLAUDE.md Git Workflow | commit trailer, merge through the queue | conforms | the squash merge `88680dd7`; the first merge-group run was ejected on a third-party HTTP 429 and the PR was re-enqueued with no content change |

## Metrics and Anomalies

- Tokens `unknown`, `plugin_version unknown` (unobserved facts, never 0): `complete: false` for these two, as in every landing of this epic. Model `deepseek-v4.1-flash/high`, harness opencode, executor 0.1.2183 (lane text 0.1.2183, `executor-skew match`). `guard: none on this harness (unenforced)`.
- The reviewer quota: CodeRabbit (required) refused four times on its free quota (90-minute floors honoured), accepted on the fifth trigger at 06:32:33Z; reviewed with no findings; Sourcery (optional) approved. Five triggers over 6 h 52 min.
- The mailbox: the lane's `inbox read` channel answered `no_mailbox` for its plan id because the file names the sender id, but the lane read the item from the file and delivered all of it (the Watch's worry is closed: the folds arrived).
- Self-review three rounds (14, 8, 1 findings), four declined with reasons.

## Routing and Merge Behavior

- CI/merge: merged through the merge queue as `88680dd7`; the first merge-group run (`45784844793`) failed in `quarkus-alignment` on `HTTP Error 429` from Maven Central, a job that passed on the PR head; re-enqueued, merged on the second attempt. `cleanup_owed=false`; the worktree and branches are removed.
- Sonar: `sonar_open_new_issues=0`, quality gate `ok` (the lane's read, not re-read by the orchestrator).
- Operator word during the run: "land it" (free text), acted on through the ordinary gate.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-V2-188 --status shipped`
- [x] row `pr` stamped `#621`; row `landing` stamped `landings/PLAN-V2-188.md`; row `plan_marshall_plan_id` stamped `rb-v2-188-schema-and-store-removals` (from the sender)
- [x] the lane's message archived (`inbox archive`); the mailbox item `inbox/to/rb-v2-188-schema-and-store-removals/orchestrator-pm-mcp-core-m2-m4-v2-001.md` is consumed (all three asks delivered)
- [x] the Watch of PLAN-V2-188 retired in `epic.md`; the Decisions entry and the Open Defects of the fortieth run (second pass) written
- [x] L3-1, L3-2, L3-4 and L3-9 staged as deliverable 4 of PLAN-V2-209 (same schema family, same tests); L3-3, L3-5, L3-6, L3-7 need no action; L3-8 recorded as a lead
- [x] PLAN-V2-189 is unblocked by this merge (the "after 188" hold is released; the overlap prompt still applies where files are shared); 190 to 192 still wait for 189
- [x] resume anchor updated; `queue-view.md` regenerated and committed

## Follow-Ups

- **Staged:** the wording leftovers (L3-1, L3-2, L3-4, L3-9) are deliverable 4 of PLAN-V2-209.
- **Evidence gap, the epic's own:** every "fails at `{sha}`" clause is unprovable while a detached base tree answers `executor_in_worktree: absent` (the marketplace's `prepare_execute` raises silently on a checkout without a vendored `marketplace/bundles`; the same gap as PLAN-V2-82, 152, 153, 185 and 195). Owed to the marketplace, not to this epic: recorded as an Open Defect.
- **Parked specs:** PLAN-V2-03, 38 and 41 still plan or bless `regrounded_at_sha`, `reground_verdict`, a queue-row `overlap_found` and `epic.overlap_found`; they need re-grounding when revived (Open Defect).
- **L3-8** is a lead: whether `phase-workflows/01:120` "Assessments and findings" names the removed assessment store is the specification's owner's to read; no plan is staged on it.

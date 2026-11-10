# PLAN-V2-56: Bring the qualification and judge-check codecs to the token-usage closure and fix the comments it left stale

epic: pm-mcp-core-m2-m4-v2
workstream: WS-05
origin: PLAN-113 of the epic pm-mcp-core-m2-m4
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-56-qualification-token-split.md` and is queued as one row file, `queue/PLAN-V2-56.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r4`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R13, R14, R17, R18, R19, R20. (R10 binds Deliverable 2, which reads the schema files that `store-schemas/16` includes; R13 binds Deliverable 1, which changes a record in place; R14 because `AttemptEnd` and `UsageAggregate` are in `core.store`; R12 is dropped, no message is added; R15 and R16 are dropped, neither `pm-workflow` nor `pm-runtime` changes; R17 because the plan runs the links check.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-role.adoc` (Deliverable 1; § Tokens and the Split); ADR directory: `pm-core/doc/adr/core-role/` (the next free number); layout: workstream WS-05.

## Objective

PLAN-107 (#215, `81c51719`) closed the token-usage package and, by design, removed `tokens_in` and `tokens_out` from the role-qualification record and the judge-check record in `store-schemas/16` (both objects are closed, `additionalProperties: false`; `total_tokens` stays). The codecs of `pm-core` `core.role.qualification` still write and require the two members, so a record the code writes is invalid against the specification, and no test notices: no parity test reads these two schemas. The same pull request also left source comments naming sections it deleted. Bring the two codecs to the specification, add the missing parity test so the drift cannot recur unnoticed, and correct the stale comments.

## Deliverables

1. **The codecs follow the schema.** `QualificationRecordCodec` stops writing and stops requiring `tokens_in` and `tokens_out` on the form result and on the items of the qualification record (`FORM_MEMBERS`, `ITEM_MEMBERS`, the two read loops, the writer), and `JudgeCheckCodec` stops writing and requiring them on its items (`ITEM_MEMBERS`, the read loop, the writer). A document that still carries either member is refused as an undeclared member like any other (R13: no reader for the old shape, no migration, the format version stays 1). `TokenTotal` keeps its split: the decision calibration record (`decision-calibration-record.schema.json`, lines 33 to 43, included by `store-schemas/16`) carries `tokens_in` and `tokens_out`, and its codec is owed with PLAN-V2-60 (`decision-calibration-record`, parked; its Deliverable 1 reuses the split of `TokenTotal`); `judgment-votes` (PLAN-V2-76) was retired on 2026-11-09 and builds nothing. Until PLAN-V2-60 runs, the split has no production user once the two codecs stop writing it; it stays, with its `TokenTotal` tests, rather than be deleted and re-added; only the two codecs change. Test first: the codec test names an item written without the two members and refuses a document that carries them; `QualificationRecordCodecTest` lines 74 and 75 (which assert the members in the output) and its split cases that go through the codec change with it; the cases of `TokenTotal` itself (the pair sums to the total, an unmeasured total carries no split, `plus`) stay.
2. **A parity test for the two records.** Add `QualificationSchemaParityTest` (new, `core.role.qualification`) in the pattern of `TaskQueueSchemaParityTest` (`core.queue`), reading the two schema files that `store-schemas/16-task-queue.adoc` includes (#323, ADR `doc/adr/016`) from the class path through the test helper `SchemaResource` (`pm-core/src/test/java/de/planmarshall/core/SchemaResource.java`: `load`, `text`): `de/planmarshall/core/role/qualification/qualification-record.schema.json` for the role qualification record and `de/planmarshall/core/role/qualification/judge-check-record.schema.json` for the judge check record (the headings `== Role Qualification Record` and `=== Judge Check Record` and `SpecificationDocument.jsonBlockAfter` are not used: nothing calls that method any more). It asserts the members the codecs write and require in schema order (`QualificationRecordCodec.FORM_MEMBERS` and `ITEM_MEMBERS`, `JudgeCheckCodec.ITEM_MEMBERS`, the top-level and nested members), the required set, the enumerations, `format_version` 1, and that every document the codecs write validates against its schema (the `validator` and `assertValid` helpers of `TaskQueueSchemaParityTest`, JSON Schema 2020-12). `decision-calibration-record.schema.json` is read by no test and has no codec yet (PLAN-V2-60, parked): it is not asserted here. R10: the three schema files and the `include::` lines of `store-schemas/16` stay byte-stable (this plan edits no specification part and no schema file). The test is red against the codecs before Deliverable 1 and green after.
3. **The stale comments.** `AttemptEnd.java` (the metrics enum of `core.store`, line 17) is not edited: PLAN-V2-129 (`deferred-code-removal`, staged, runs before this plan) deletes it with `AttemptEndParityTest`, and the queue enum `QueueTaskRecord.AttemptEnd` carries no such citation. `UsageAggregate.java` (line 33) and `UsageAggregateTest.java` (line 36) cite *Store Schemas § Metrics Store, Aggregates*, a bullet #215 deleted (the section keeps *Populations* and the sentence "Aggregates are never persisted"). The Javadoc of the two codecs and of `TokenTotal`/`package-info` of `core.role.qualification` that says the qualification and judge-check records carry the split is corrected with Deliverable 1. Sweep `src/main` and `src/test` of `pm-core`, `pm-workflow` and `pm-runtime` with `git grep` for the other titles and numbers #215 removed (*Unmeasured Usage Never Fabricates Zero*, *Per-Column Usage Persistence*, *token-usage* used as a package or section name, *Spend*, the six invariants) and correct each hit that is not in `ProfileValidator.java` (the comment at its line 933 belongs to the staged plan PLAN-V2-68 (`profile-wait-margin`), which shares that class with `machine-harness-verification`). A hit is corrected to the section that now holds the statement, or the citation is dropped; no behaviour changes.
4. **Document the decision.** One ADR in `pm-core/doc/adr/core-role/` (next free number; Status, Date, Context, Decision, Alternatives considered, Consequences, Source) records that the grading records follow `store-schemas/16`: the split is not persisted on the qualification and judge-check records (the view derives it, ADR `core-store/048`), `TokenTotal` keeps it for the calibration record, and ADR `005-token-total-carries-the-split` is superseded in the part that says these two records write it (change its Status line to point to the new ADR). Extend § Tokens and the Split of `core-role.adoc` (it still says "The qualification and judge-check records write the split").

**Done when:** `QualificationRecordCodec` and `JudgeCheckCodec` write and read neither member and refuse a document that carries one; `QualificationSchemaParityTest` exists, is green, and fails when a member is added to or removed from one side only; the comments of Deliverable 3 name sections that exist; the ADR and the developer documentation are written; `verify`, `verify -Ppre-commit` (its rewrites reviewed and committed), `verify -Pcoverage` and `verify -Pintegration-tests -pl pm-core -am` are green. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (`pm-core`; where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and `verify -Pintegration-tests -pl pm-core -am` are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set:** PM-WF-14 and PM-CLIENT-5 (Tokens, Not Money); PM-WORK-6 (Role Qualification, `doc/specification/model-work/01-roles-and-configuration.adoc` and `store-schemas/16-task-queue.adoc` § Role Qualification Record and § Judge Check Record). The lead is the PLAN-107 landing (`landings/PLAN-107.md`, residue 1 and 2).

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **The specification is the authority.** `store-schemas/16` and the three schema files it includes (`qualification-record`, `judge-check-record` and `decision-calibration-record`, under `pm-core/src/main/resources/de/planmarshall/core/role/qualification/`) are not edited: if the trace matrix shows that the split is wanted on these records after all, that is a finding for the inbox message and a pull request against the specification, not a reason to keep the codecs as they are.
- **No log message and no new dependency.**

## Claim Labels

- OBSERVED: `store-schemas/16` lists no `tokens_in`/`tokens_out` on the role-qualification item (`required` at line 265, no member), the form result (`required` at line 292) and the judge-check item (`required` at line 415); the three objects are closed; the members appear only in the decision calibration item (lines 353 to 363)
  - verdict: corroborated | checked_at: f9e7011fb6a1ae42f6afad1f842ca1488258e19f | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: qualification-record.schema.json and judge-check-record.schema.json carry no tokens_in or tokens_out; the members appear only in decision-calibration-record.schema.json:33,42,43; the old line numbers are obsolete since #323
- OBSERVED: the codecs still carry them — `QualificationRecordCodec.java` lines 91 to 92 (constants), 113 and 120 (`FORM_MEMBERS`, `ITEM_MEMBERS`), 243 to 245 and 315 to 317 (read), 531 to 535 (write); `JudgeCheckCodec.java` lines 72 to 73, 81 to 82, 163 to 165, 174, 277 to 279; `QualificationRecordCodecTest.java` lines 67 and 68 assert the members in the output
  - verdict: corroborated | checked_at: f9e7011fb6a1ae42f6afad1f842ca1488258e19f | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: QualificationRecordCodec and JudgeCheckCodec still read and write the split members; QualificationRecordCodecTest asserts them at :74-75
- OBSERVED: no test reads these two schemas — `grep -rln "16-task-queue" pm-*/src` finds `TaskQueueSchemaParityTest` only, which covers the queue task record and the activity journal; `SpecificationDocument.jsonBlockAfter(document, anchor)` is the reader it uses
  - verdict: corroborated | checked_at: f9e7011fb6a1ae42f6afad1f842ca1488258e19f | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: no test under pm-*/src reads the two schema files; the jsonBlockAfter mechanism is obsolete (TaskQueueSchemaParityTest reads queue-task.schema.json through SchemaResource)
- OBSERVED: `TokenTotal`'s split is used in `src/main` only by the two codecs (`TokenTotal.measured(long,long,long)` has no other caller); `pm-core/doc/developer/core-role.adoc` § Tokens and the Split says the qualification and judge-check records "write the split beside `total_tokens`" and that the calibration record's codec "is owed with the package that builds it"
  - verdict: corroborated | checked_at: f9e7011fb6a1ae42f6afad1f842ca1488258e19f | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: outside TokenTotal the split is used in main only by QualificationRecordCodec and JudgeCheckCodec; core-role.adoc:259-261 says the calibration codec is owed
- OBSERVED: the stale citations — `AttemptEnd.java` line 17 ("item 5"); the invariant list of `model-work/09-completion-usage-and-spend.adoc` has five items since #215 and *One Row per Attempt* (item 4, line 121) carries `attempt_end`; `UsageAggregate.java` line 33 and `UsageAggregateTest.java` line 36 cite "Aggregates", whose bullet `git show 81c51719` deletes from `store-schemas/05-metrics-and-quality-report.adoc`
  - verdict: corroborated | checked_at: f9e7011fb6a1ae42f6afad1f842ca1488258e19f | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: AttemptEnd.java:17 still cites item 5 of the integrity invariants while One Row per Attempt is item 4 (model-work/09 :121); UsageAggregate.java:33 and UsageAggregateTest.java:40 cite a deleted Aggregates bullet
- HYPOTHESIS: the Javadoc of the two codecs and of `QualificationRecord` carries more stale wording than the grep of this draft found (the draft found no section title in them) — confirm/refute at the sweep of Deliverable 3 (verify-at-outline)
  - verdict: unverifiable | checked_at: f9e7011fb6a1ae42f6afad1f842ca1488258e19f | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: a hypothesis about further stale wording; the cited headings still exist and the Javadoc wording about the split cannot be judged without the plan's own sweep
- Verify-first clause: before editing, list the open pull requests for one that touches `pm-core/src/main/java/de/planmarshall/core/role/qualification/` or `AttemptEnd`/`UsageAggregate`, re-read the schema blocks and the codecs at the head, and check that `judgment-votes` (parked) has not started a calibration codec that expects the split on the other two records; a refutation loops back to re-scope.
  - verdict: contradicted | checked_at: f9e7011fb6a1ae42f6afad1f842ca1488258e19f | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: yes | evidence: rescoped 2026-11-10: absorbed by the Grounding corrections section and the replaced deliverable, constraint and surface text of the spec. the schema blocks are no longer in store-schemas/16: qualification-record, judge-check-record and decision-calibration-record schema files under pm-core core/role/qualification are included by the adoc (#323); Deliverable 2 and the byte-stable constraint presume blocks in the adoc; PLAN-V2-76 is retired and no decision-calibration codec exists

## Expected Surface

- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/qualification/QualificationRecordCodec.java` — members, read, write (D1)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/qualification/JudgeCheckCodec.java` — members, read, write (D1)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/qualification/TokenTotal.java` — Javadoc only (D3)
- NOT surface: `pm-core/src/main/java/de/planmarshall/core/store/AttemptEnd.java` — the metrics enum is deleted by PLAN-V2-129; its comment is not edited here
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/UsageAggregate.java` — comment (D3)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/role/qualification/QualificationRecordCodecTest.java` — the output cases and the refusal of the members (D1)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/store/UsageAggregateTest.java` — comment (D3)
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/role/qualification/JudgeCheckTest.java` and `QualificationRecordStoreTest.java` — only where a fixture carries a document with the members (the draft found none; confirm at the trace matrix)
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/role/qualification/QualificationSchemaParityTest.java` — new (D2)
- OBSERVED: `pm-core/doc/developer/core-role.adoc` — § Tokens and the Split, extended
- OBSERVED: `pm-core/doc/adr/core-role/005-token-total-carries-the-split.adoc` — Status line points to the new ADR
- HYPOTHESIS: `pm-core/doc/adr/core-role/` — one new ADR, the next free number of the directory

Read, not edited (R10): `pm-core/src/main/resources/de/planmarshall/core/role/qualification/qualification-record.schema.json` and `judge-check-record.schema.json` (read by D2 through `SchemaResource`), `decision-calibration-record.schema.json`, and `doc/specification/store-schemas/16-task-queue.adoc` (its `include::` lines for the three, byte-stable).

Not surface (R19): `doc/plans/README.adoc`, `doc/Traceability.adoc`, the ADR directory and developer guide above as bookkeeping where they exist.

## Dependencies and Sequencing

- Depends on: PLAN-V2-83 (`skills-conformance-foundation`: the dependencies and rule R20); and then: nothing (PLAN-107 shipped, #215)
- Overlaps with the live plans (class level): `core.store.AttemptEnd` (PLAN-V2-129 deletes it; this plan does not edit it) and `UsageAggregateTest` (PLAN-V2-129 edits line 102, this plan the comment at line 36: whichever merges second merges the base branch first). `store-schemas/16` and the three schema files are read, not edited; PLAN-V2-116 moved the blocks (shipped), PLAN-V2-59 (the activity-journal block) and PLAN-V2-124 (the prose at line 48) edit the document, and PLAN-V2-60 (parked) adds the codec of the calibration record beside `JudgeCheckCodec` and reads `TokenTotal`; a plan that edits the two schema files changes the parity test's input: whichever merges second merges the base branch first. PLAN-V2-76 is retired and writes nothing.
- Disjoint from the other drafts of this cluster (no shared class): PLAN-V2-46 (`runtime-endpoint-residues`), PLAN-V2-65 (`corpus-schema-length-bounds`), PLAN-V2-67 (`main-hygiene-leftovers`) (`StoreDigest` is a different class of `core.store`), PLAN-V2-68 (`profile-wait-margin`).
- Adjacent to: the decision calibration codec (owed to PLAN-V2-60, parked, since `judgment-votes` was retired) for which `TokenTotal`'s split stays.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-56-qualification-token-split.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Carried Open Items

Open items of the previous epic that this plan owns (register: `owner-register.md` of the epic; the full text is in the archived ledger `pm-mcp-core-m2-m4/epic.md`). They are not deliverables and extend no Done-when: read each while outlining, re-check it against `origin/main` (Claim Labels) and either adopt it into a deliverable of the outline or say in the landing why not.

- **Two code residues of the token-usage closure (analysis of #215, 2026-10-28)** (Op12) — store-schemas/16 no longer lists tokens_in/tokens_out on the qualification and judge-check records while QualificationRecordCodec and JudgeCheckCodec still write and require them: change the code to match; and the source comments that still name the deleted sections or the closed package (AttemptEnd, UsageAggregate, UsageAggregateTest, the two codecs, ProfileValidator)

## Rescope and carries (2026-11-09)

Added by the orchestrator after the operator's walk-through of the open defects (decision log, 2026-11-09). Read at the outline, re-check each statement against `origin/main` (Claim Labels) and adopt it into a deliverable of the outline or say in the landing why not. It extends no Done-when by itself.

- **Rescope (audit, operator 2026-11-09):** run after the Z3 cut of the `store-schemas/16` qualification blocks. D2 reads schema blocks through `SpecificationDocument.jsonBlockAfter` from `store-schemas/16`; PLAN-V2-116 to 118 moved those blocks into schema files, so D2 reads the schema files (`qualification-record.schema.json`, `judge-check-record.schema.json`) through `SchemaResource` instead. Drop the `AttemptEnd` source comment (the class is deleted by PLAN-V2-129). `QualificationRecordCodec` and `JudgeCheckCodec` still write and require `tokens_in`/`tokens_out` while the schema files carry `total_tokens` only (HYPOTHESIS, verify-at-outline).

## Grounding corrections (cleanup 2026-11-10, main f9e7011f)

The re-grounding against `origin/main` f9e7011f contradicted one claim of this spec that the spec had not absorbed (claim 6, the verify-first clause). The claim bullets stay as written. This section and the replaced text govern.

Claim 6: The schema blocks are no longer in `doc/specification/store-schemas/16-task-queue.adoc`. #323 (81b023dd) moved them to three files under `pm-core/src/main/resources/de/planmarshall/core/role/qualification/`: `qualification-record.schema.json`, `judge-check-record.schema.json` and `decision-calibration-record.schema.json`. The adoc includes them with `include::` lines at lines 71, 83 and 95. Nothing calls `SpecificationDocument.jsonBlockAfter` (only its definition remains), and no test reads these three files (a search of pm-core tests for the three file names finds nothing). Read from the files:
- `qualification-record.schema.json` carries `total_tokens` and no `tokens_in` or `tokens_out`, on the item (`required` at line 47) and on the form result (`required` at line 75).
- `judge-check-record.schema.json` carries `total_tokens` only (`required` at line 23).
- `decision-calibration-record.schema.json` still carries `tokens_in` and `tokens_out` (required at line 33, members at lines 42 and 43).

The codecs still write and require the two members: `QualificationRecordCodec` at lines 92 to 93, 114, 121 to 122, 245 to 246, 317 to 318 and 534 to 537, and `JudgeCheckCodec` at lines 73 to 74, 82 to 83, 165 to 166 and 278 to 281. `QualificationRecordCodecTest` asserts the members at lines 74 and 75. This confirms the HYPOTHESIS of the Rescope line.

Replaced: Deliverable 2 now reads the two schema files through the test helper `SchemaResource` in the pattern of `TaskQueueSchemaParityTest` (`validator` and `assertValid`, JSON Schema 2020-12). It no longer uses `jsonBlockAfter` or the headings. The R10 mentions (rules list, Standing Constraints, "Read, not edited") now name the three schema files and the `include::` lines instead of "blocks". The statement that the specification is the authority extends to the schema files.

The decision calibration record is owned by retired PLAN-V2-76 only in this spec's text. The queue says PLAN-V2-76 `judgment-votes` is retired (2026-11-09), so it builds nothing. No decision-calibration codec exists at the head, and no test reads its schema file. Its codec and store are now the scope of PLAN-V2-60 (`decision-calibration-record`, parked, outside the staging window). That spec's Deliverable 1 reuses the split of `TokenTotal`. For this plan it means three things. First, nothing here depends on or waits for 76. Second, `TokenTotal`'s split keeps no production user once Deliverable 1 lands and until PLAN-V2-60 runs, so the spec states the default explicitly (keep it, with its `TokenTotal` tests). Third, the calibration schema file is read-only here, and no parity assertion is added for it, because it has no codec and no owner running.

Also, per the Rescope line, `AttemptEnd.java` is dropped from Deliverable 3 and from the Expected Surface. It is the metrics enum of `core.store`, which PLAN-V2-129 (staged, runs before this plan) deletes with `AttemptEndParityTest`. The queue enum `QueueTaskRecord.AttemptEnd` carries no such citation. The `UsageAggregate` comment fix stays. The overlap with PLAN-V2-129 on `UsageAggregateTest` (129 edits line 102, this plan line 36) is recorded under Dependencies and Sequencing.

Deliverable count: four before and four after; none added.

Asks for the operator at refine (recommendations of the drafter, none blocks emission):
- After Deliverable 1, `TokenTotal`'s split has no production user. Its only consumer, the decision calibration codec, belongs to PLAN-V2-60, which is parked outside the staging window and may not run soon. Shall `TokenTotal` keep the split (default as drafted) or shall it be reduced to the total and re-extended when PLAN-V2-60 runs? I recommend keeping it. PLAN-V2-60's spec already reuses it, ADR core-role/005 stays correct for the calibration record, and removal would be churn. The orchestrator should confirm, because a dead-code sweep such as PLAN-V2-129's orphan list could otherwise flag it.

**Settled 2026-11-10 (operator, Q16 of the issues pass, answer "1"; recommended by the orchestrator at 80%):** `TokenTotal` keeps its input/output split, with its tests, after Deliverable 1 stops the two codecs from writing it. Its planned consumer is the decision calibration record (`decision-calibration-record.schema.json` still requires `tokens_in` and `tokens_out`; the open package `decision-quorum-and-calibration`, Task 8, "the token split included"; the parked PLAN-V2-60 reuses it; ADR `core-role/005` stays correct). The pull request body says why the split stays, so that no dead-code sweep (for example PLAN-V2-129's orphan list) removes it; `HarnessUsage.tokensIn()` and `tokensOut()` are a separate derivation (ADR `core-store/048`) and are untouched.

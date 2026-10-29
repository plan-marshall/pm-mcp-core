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

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r2`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R13, R14, R17, R18, R19. (R10 binds Deliverable 2, which reads blocks of `store-schemas/16`; R13 binds Deliverable 1, which changes a record in place; R14 because `AttemptEnd` and `UsageAggregate` are in `core.store`; R12 is dropped, no message is added; R15 and R16 are dropped, neither `pm-workflow` nor `pm-runtime` changes; R17 because the plan runs the links check.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-role.adoc` (Deliverable 1; § Tokens and the Split); ADR directory: `pm-core/doc/adr/core-role/` (the next free number); layout: workstream WS-05.

## Objective

PLAN-107 (#215, `81c51719`) closed the token-usage package and, by design, removed `tokens_in` and `tokens_out` from the role-qualification record and the judge-check record in `store-schemas/16` (both objects are closed, `additionalProperties: false`; `total_tokens` stays). The codecs of `pm-core` `core.role.qualification` still write and require the two members, so a record the code writes is invalid against the specification, and no test notices: no parity test reads these two schemas. The same pull request also left source comments naming sections it deleted. Bring the two codecs to the specification, add the missing parity test so the drift cannot recur unnoticed, and correct the stale comments.

## Deliverables

1. **The codecs follow the schema.** `QualificationRecordCodec` stops writing and stops requiring `tokens_in` and `tokens_out` on the form result and on the items of the qualification record (`FORM_MEMBERS`, `ITEM_MEMBERS`, the two read loops, the writer), and `JudgeCheckCodec` stops writing and requiring them on its items (`ITEM_MEMBERS`, the read loop, the writer). A document that still carries either member is refused as an undeclared member like any other (R13: no reader for the old shape, no migration, the format version stays 1). `TokenTotal` keeps its split: the decision calibration record of `store-schemas/16` (lines 353 to 363) carries `tokens_in` and `tokens_out`, and its codec is owed with the package that builds it (`judgment-votes`); only the two codecs change. Test first: the codec test names an item written without the two members and refuses a document that carries them; `QualificationRecordCodecTest` lines 67 and 68 (which assert the members in the output) and its split cases that go through the codec change with it; the cases of `TokenTotal` itself (the pair sums to the total, an unmeasured total carries no split, `plus`) stay.
2. **A parity test for the two records.** Add `QualificationSchemaParityTest` (new, `core.role.qualification`) beside `TaskQueueSchemaParityTest`, reading the JSON blocks after `== Role Qualification Record` and `=== Judge Check Record` of `store-schemas/16-task-queue.adoc` through `SpecificationDocument.jsonBlockAfter`: the members the codecs write and require in schema order, the required set, the enumerations, `format_version` 1, and every document the codecs write validates against its block. R10: both blocks are read files of this deliverable and stay byte-stable (this plan edits no specification part). The test is red against the codecs before Deliverable 1 and green after.
3. **The stale comments.** `AttemptEnd.java` (line 17) cites *Model Work § Post-Completion Integrity Invariants, item 5* for `attempt_end`; since #215 the invariant that carries it is item 4 (*One Row per Attempt*; the old items 1 and 6 were removed and the list renumbered). `UsageAggregate.java` (line 33) and `UsageAggregateTest.java` (line 36) cite *Store Schemas § Metrics Store, Aggregates*, a bullet #215 deleted (the section keeps *Populations* and the sentence "Aggregates are never persisted"). The Javadoc of the two codecs and of `TokenTotal`/`package-info` of `core.role.qualification` that says the qualification and judge-check records carry the split is corrected with Deliverable 1. Sweep `src/main` and `src/test` of `pm-core`, `pm-workflow` and `pm-runtime` with `git grep` for the other titles and numbers #215 removed (*Unmeasured Usage Never Fabricates Zero*, *Per-Column Usage Persistence*, *token-usage* used as a package or section name, *Spend*, the six invariants) and correct each hit that is not in `ProfileValidator.java` (the comment at its line 933 belongs to the staged plan PLAN-V2-68 (`profile-wait-margin`), which shares that class with `machine-harness-verification`). A hit is corrected to the section that now holds the statement, or the citation is dropped; no behaviour changes.
4. **Document the decision.** One ADR in `pm-core/doc/adr/core-role/` (next free number; Status, Date, Context, Decision, Alternatives considered, Consequences, Source) records that the grading records follow `store-schemas/16`: the split is not persisted on the qualification and judge-check records (the view derives it, ADR `core-store/048`), `TokenTotal` keeps it for the calibration record, and ADR `005-token-total-carries-the-split` is superseded in the part that says these two records write it (change its Status line to point to the new ADR). Extend § Tokens and the Split of `core-role.adoc` (it still says "The qualification and judge-check records write the split").

**Done when:** `QualificationRecordCodec` and `JudgeCheckCodec` write and read neither member and refuse a document that carries one; `QualificationSchemaParityTest` exists, is green, and fails when a member is added to or removed from one side only; the comments of Deliverable 3 name sections that exist; the ADR and the developer documentation are written; `verify`, `verify -Ppre-commit` (its rewrites reviewed and committed), `verify -Pcoverage` and `verify -Pintegration-tests -pl pm-core -am` are green. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (`pm-core`; where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and `verify -Pintegration-tests -pl pm-core -am` are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set:** PM-WF-14 and PM-CLIENT-5 (Tokens, Not Money); PM-WORK-6 (Role Qualification, `doc/specification/model-work/01-roles-and-configuration.adoc` and `store-schemas/16-task-queue.adoc` § Role Qualification Record and § Judge Check Record). The lead is the PLAN-107 landing (`landings/PLAN-107.md`, residue 1 and 2).

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **The specification is the authority.** `store-schemas/16` is not edited: if the trace matrix shows that the split is wanted on these records after all, that is a finding for the inbox message and a pull request against the specification, not a reason to keep the codecs as they are.
- **No log message and no new dependency.**

## Claim Labels

- OBSERVED: `store-schemas/16` lists no `tokens_in`/`tokens_out` on the role-qualification item (`required` at line 265, no member), the form result (`required` at line 292) and the judge-check item (`required` at line 415); the three objects are closed; the members appear only in the decision calibration item (lines 353 to 363)
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: `grep tokens_in doc/specification/store-schemas/16-task-queue.adoc` finds lines 353, 362, 363 only; lines 255 to 275 read
- OBSERVED: the codecs still carry them — `QualificationRecordCodec.java` lines 91 to 92 (constants), 113 and 120 (`FORM_MEMBERS`, `ITEM_MEMBERS`), 243 to 245 and 315 to 317 (read), 531 to 535 (write); `JudgeCheckCodec.java` lines 72 to 73, 81 to 82, 163 to 165, 174, 277 to 279; `QualificationRecordCodecTest.java` lines 67 and 68 assert the members in the output
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: read at a8da4104
- OBSERVED: no test reads these two schemas — `grep -rln "16-task-queue" pm-*/src` finds `TaskQueueSchemaParityTest` only, which covers the queue task record and the activity journal; `SpecificationDocument.jsonBlockAfter(document, anchor)` is the reader it uses
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: `grep` and the test header read
- OBSERVED: `TokenTotal`'s split is used in `src/main` only by the two codecs (`TokenTotal.measured(long,long,long)` has no other caller); `pm-core/doc/developer/core-role.adoc` § Tokens and the Split says the qualification and judge-check records "write the split beside `total_tokens`" and that the calibration record's codec "is owed with the package that builds it"
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: `grep "TokenTotal.measured\|new TokenTotal"` over `pm-*/src/main`; the guide read at lines 248 to 262
- OBSERVED: the stale citations — `AttemptEnd.java` line 17 ("item 5"); the invariant list of `model-work/09-completion-usage-and-spend.adoc` has five items since #215 and *One Row per Attempt* (item 4, line 121) carries `attempt_end`; `UsageAggregate.java` line 33 and `UsageAggregateTest.java` line 36 cite "Aggregates", whose bullet `git show 81c51719` deletes from `store-schemas/05-metrics-and-quality-report.adoc`
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: read; the epic names `ProfileValidator.java` too (line 933, "`token-usage`'s"), which this plan leaves to PLAN-V2-68 (`profile-wait-margin`)
- HYPOTHESIS: the Javadoc of the two codecs and of `QualificationRecord` carries more stale wording than the grep of this draft found (the draft found no section title in them) — confirm/refute at the sweep of Deliverable 3 (verify-at-outline)
  - verdict: unverifiable | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: hypothesis, verify-at-outline by the executing plan
- Verify-first clause: before editing, list the open pull requests for one that touches `pm-core/src/main/java/de/planmarshall/core/role/qualification/` or `AttemptEnd`/`UsageAggregate`, re-read the schema blocks and the codecs at the head, and check that `judgment-votes` (parked) has not started a calibration codec that expects the split on the other two records; a refutation loops back to re-scope.
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: no package file and no index row for this residue; no live spec names a class below

## Expected Surface

- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/qualification/QualificationRecordCodec.java` — members, read, write (D1)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/qualification/JudgeCheckCodec.java` — members, read, write (D1)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/qualification/TokenTotal.java` — Javadoc only (D3)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/AttemptEnd.java` — comment (D3)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/UsageAggregate.java` — comment (D3)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/role/qualification/QualificationRecordCodecTest.java` — the output cases and the refusal of the members (D1)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/store/UsageAggregateTest.java` — comment (D3)
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/role/qualification/JudgeCheckTest.java` and `QualificationRecordStoreTest.java` — only where a fixture carries a document with the members (the draft found none; confirm at the trace matrix)
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/role/qualification/QualificationSchemaParityTest.java` — new (D2)
- OBSERVED: `pm-core/doc/developer/core-role.adoc` — § Tokens and the Split, extended
- OBSERVED: `pm-core/doc/adr/core-role/005-token-total-carries-the-split.adoc` — Status line points to the new ADR
- HYPOTHESIS: `pm-core/doc/adr/core-role/` — one new ADR, the next free number of the directory

Read, not edited (R10): `doc/specification/store-schemas/16-task-queue.adoc` (the blocks after `== Role Qualification Record` and `=== Judge Check Record`, byte-stable).

Not surface (R19): `doc/plans/README.adoc`, `doc/Traceability.adoc`, the ADR directory and developer guide above as bookkeeping where they exist.

## Dependencies and Sequencing

- Depends on: nothing (PLAN-107 shipped, #215)
- Overlaps with the live plans (class level): none. `store-schemas/16` is read, not edited; the staged plans that edit it (PLAN-V2-76 `judgment-votes`, parked, and others) are not blocked, but a plan that edits the two blocks changes the parity test's input: whichever merges second merges the base branch first. PLAN-V2-76 reads `core.role.corpus.ImportedItem` and writes the calibration record, no class of this plan.
- Disjoint from the other drafts of this cluster (no shared class): PLAN-V2-46 (`runtime-endpoint-residues`), PLAN-V2-65 (`corpus-schema-length-bounds`), PLAN-V2-67 (`main-hygiene-leftovers`) (`StoreDigest` is a different class of `core.store`), PLAN-V2-68 (`profile-wait-margin`).
- Adjacent to: the decision calibration codec (owed to `judgment-votes`) for which `TokenTotal`'s split stays.

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

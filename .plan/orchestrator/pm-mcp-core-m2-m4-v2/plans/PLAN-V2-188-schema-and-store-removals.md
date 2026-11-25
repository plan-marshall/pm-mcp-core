# PLAN-V2-188: The schema members and enum values the merged specification dropped are removed, with the tests that pin them and a refusal of their stored old values

epic: pm-mcp-core-m2-m4-v2
workstream: WS-12
origin: doc-review-2026-11-24-001.md (section 1, "the schemas are specification text", and its wording-only items) and doc-review-2026-11-25-001.md (section 1: V1, V2, V10); the operator's cut "seven plans plus folds", plan 1.
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off. Lives at `plans/PLAN-V2-188-schema-and-store-removals.md`, queued as one row file in the epic ledger (lane `runbook`). The orchestrator EMITS the command below; it never launches the plan inline.

## First Instruction: Read The Standing Rules

Before the rest, read `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` completely (`../standing-rules.md` beside `plans/`); say in the first progress note that you read them, with their revision, and repeat revision and rule ids in the landing message. Drafted set: R02, R05, R06, R08, R10, R11, R13, R14, R18, R19, R20 (as PLAN-V2-187; the orchestrator confirms at staging; R13 binds fully because stored formats shrink). Plan parameters: R06 developer documentation `pm-core/doc/developer/core-store.adoc` and `pm-runtime/doc/developer/runtime-ingest.adoc`; ADR directory `pm-core/doc/adr/core-store/` (an amendment of ADR 053, no new ADR unless the outline takes a design decision); layout WS-12. R12: no message is added.

## Objective

The merged specification renders the schema files by `include::`, so the specification still shows members and values whose prose is gone. This plan deletes them: the derived-fact kind `branch_up_to_date_required`, the assessment-record schema and counter, the request aspect, the pr-title staleness flag, the epic overlap re-check and re-grounding members, the edge resolver `python`, the review bots' `structural_caps`, and corrects four wording-only descriptions. Pre-1.0: no read shim; a stored old value is refused and named.

## Deliverables

Three deliverables, about 22 files, no split needed. Schema files come first.

1. **Schema-only removals and wording (no codec reads these members).** In `status.schema.json`: counter `assessment_id` (:223), `metadata.request_aspect` (:354), `metadata.pr_title_stale` (:361), the `change_type` description (:356: single writer is the server's derivation at outline acceptance, phase-workflows/03 `#change-type-derivation`). Delete `assessment-record.schema.json` with its entry in `UnreadSchemaFilesTest` (:66) and its row 03 in `doc/adr/018-...` (:56). `quality-report.schema.json:58` drops "and the assessment comparison state". `queue-row.schema.json`: `regrounded_at_sha`, `reground_verdict` (:40-53) and both names in `required` (:6). `report-event.schema.json:17`: `epic.overlap_found`. `message-event.schema.json`: kind `overlap_found` (:25), its sentence in :26 and in the `payload` description :30. `domain-descriptor-v1.schema.json:144`: edge resolver `python`. `review-bot-registry.schema.json:45-50`: `structural_caps`. Wording: `decision-record.schema.json:53` (replace example `outline.classify-change-type`), `outline-record-v1.schema.json:88` (no "Scope Disagreement Refusal"), `finding-record.schema.json:102` (`scope_class` reads the realized footprint, unplanned changes included, store-schemas/03:103).
   - Skills: pm-dev-java:java-core, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
   - Test order: first one new test class `RemovedSchemaMembersTest` (negative controls: each removed member or value is absent from its schema file and its enclosing object is still closed, so a stored old document fails the schema; positive control: `register-item.schema.json` `base_advanced`, `epic.schema.json` `overlap_max_files` and report kind `epic.waits_overlap` are still there), red at e451ed04; then the schema edits. Delete the `UnreadSchemaFilesTest` entry in the same commit as the file.
2. **The derived-fact kind `branch_up_to_date_required` (V1).** `derived-facts.schema.json:25` drops the enum value; `DerivedFactKind` drops the constant (:42-43) and "sixteen" becomes "fifteen" (Javadoc :19); `core-store.adoc:1418` "sixteen" becomes fifteen; ADR core-store/053 (:14, :31 say "sixteen") is amended with a stamp, not rewritten. The generic 24 h validity of network facts stays. Schema and Java enum change in ONE commit (`DerivedFactsSchemaParityTest.enumerationsEqualSchema` compares them).
   - Skills: pm-dev-java:java-core, pm-dev-java:javadoc, pm-dev-java-cui:cui-testing
   - Persona: implementer
   - Test order: first the negative control in `DerivedFactsCodecTest` (a stored `derived-facts.json` entry keyed `branch_up_to_date_required` is refused UNREADABLE and the key is named); then schema and constant together.
3. **The "Unicode fixpoint" wording and the guide corrections.** `IngestionValidator.java:36` and `runtime-ingest.adoc:18,76`: the stage removes hidden characters, then applies NFC, with no pass limit; the Unicode stage is not part of this slice.
   - Skills: pm-dev-java:javadoc, pm-documents:ref-asciidoc
   - Persona: documenter

**Done when** (against `origin/main` e451ed04): a stored derived-facts entry keyed `branch_up_to_date_required` is refused and named — fails at e451ed04 (accepted today); `RemovedSchemaMembersTest` — fails at e451ed04 (every listed member exists); `DerivedFactKind` has fifteen constants and `DerivedFactsSchemaParityTest` is green; the fifteen other kinds read and write unchanged, `base_advanced`, `overlap_max_files` and `epic.waits_overlap` stay — holds at e451ed04. `verify -Ppre-commit` (rewrites reviewed and committed, then a full verify again), `verify -Pcoverage`, `verify -Pintegration-tests -pl pm-core -am`, `verify -pl pm-core,pm-runtime -am` green (targeted `-Dtest` where a full verify exceeds the host budget). Where Sonar is configured: the open new-code findings of the pull request at the head (issues plus security hotspots to review, the rules left open, `unknown` for a failed read, never 0) are 0 or each key is selected by the operator.

## Standing Constraints

- Every removal deletes the tests that pin it and adds a negative control; no read shim (ADR core-store/049, 081); schema and parity test in one commit.
- Read every cited line at the head first; drop what a merge settled and name the commit.
- No dependency or plugin is added; no more than 100 files per pull request; no model-facing content.
- Where Sonar is configured: an open finding is fixed in a pushed commit or DISMISSED ONLY ON THE OPERATOR'S SELECTION (`operator-selected: {issue-key}: {reason}`, `sonar_rest transition --issue-key {key} --transition {falsepositive|wontfix}`); an undecidable read blocks; `sonar-gate --dismissed-keys` is refused.

## Claim Labels

- OBSERVED: constant `BRANCH_UP_TO_DATE_REQUIRED` at `DerivedFactKind.java:43`, "sixteen" at `:19`; enum at `derived-facts.schema.json:25`; the parity test derives both sides from `values()` and holds no literal count — read at `DerivedFactsSchemaParityTest.java:92-98`; `DerivedFactKindTest` also derives from `values()` (:29).
- OBSERVED: `core-store.adoc:1418` and ADR core-store/053 `:14,:31` say "sixteen".
- OBSERVED: `assessment_id` is read by no Java: only `status.schema.json:223`, the schema file, `UnreadSchemaFilesTest.java:66`, `quality-report.schema.json:58`, `doc/adr/018:56`.
- OBSERVED: `counters` and `metadata` of the status are retained verbatim (`WorkflowStatus.retained()`), so no codec refuses a stored `assessment_id`, `request_aspect` or `pr_title_stale` — read at `WorkflowStatus.java:71,96`, `WorkflowStatusCodec.java:174-177`.
- OBSERVED: `queue-row`, `report-event`, `message-event`, `domain-descriptor-v1` are read by no Java class (`git grep` finds schema text only); `register-item.schema.json:30` `base_advanced` and `epic.schema.json:47` `overlap_max_files` are other members and stay.
- OBSERVED: the Unicode stage is not implemented: `IngestionValidator.java:36` "not part of this slice"; `runtime-ingest.adoc:18,76`.
- HYPOTHESIS: `EffectiveConfigKeysSchemaParityTest` and `RoleNameSingleDefinitionTest:87` do not pin `structural_caps` or `python` — confirm at those two tests (verify-at-outline).
- HYPOTHESIS: a negative control for a retained status member can only be schema-level (member absent, enclosing object `additionalProperties: false`) — confirm what `SchemaResource` (`pm-core/src/test/java/de/planmarshall/core/SchemaResource.java`) and `SchemaSubset` (`pm-core/src/main/java/de/planmarshall/core/role/corpus/SchemaSubset.java`) can express (verify-at-outline).
- HYPOTHESIS: `DerivedFactsCodecTest` (UNREADABLE cases :184-268) has no case for a removed kind — confirm there.
- Verify-first clause: re-read each cited line at the head; a member already gone is reported, not re-removed.

## Expected Surface

- OBSERVED: `pm-core/src/main/resources/de/planmarshall/core/store/derived-facts.schema.json`, `pm-core/src/main/resources/de/planmarshall/core/store/status.schema.json`, `pm-core/src/main/resources/de/planmarshall/core/store/quality-report.schema.json`, `pm-core/src/main/resources/de/planmarshall/core/store/decision-record.schema.json`, `pm-core/src/main/resources/de/planmarshall/core/store/outline-record-v1.schema.json`, `pm-core/src/main/resources/de/planmarshall/core/store/queue-row.schema.json`, `pm-core/src/main/resources/de/planmarshall/core/store/report-event.schema.json`, `pm-core/src/main/resources/de/planmarshall/core/store/message-event.schema.json`
- OBSERVED: `pm-core/src/main/resources/de/planmarshall/core/findings/assessment-record.schema.json` (deleted), `pm-core/src/main/resources/de/planmarshall/core/findings/finding-record.schema.json`, `pm-core/src/main/resources/de/planmarshall/core/findings/review-bot-registry.schema.json`, `pm-core/src/main/resources/de/planmarshall/core/domain/domain-descriptor-v1.schema.json`
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/DerivedFactKind.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/ingest/IngestionValidator.java`
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/store/UnreadSchemaFilesTest.java`, `pm-core/src/test/java/de/planmarshall/core/store/DerivedFactsCodecTest.java`, `pm-core/src/test/java/de/planmarshall/core/store/DerivedFactsSchemaParityTest.java`
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/RemovedSchemaMembersTest.java` (new)
- OBSERVED: `pm-core/doc/developer/core-store.adoc`, `pm-runtime/doc/developer/runtime-ingest.adoc`, `pm-core/doc/adr/core-store/053-the-fact-kinds-are-an-enumeration-bound-by-a-registry.adoc`, `doc/adr/018-placement-of-schema-files-without-a-codec-of-their-own.adoc`

Not surface (R19): `doc/plans/README.adoc`, `doc/adr/README.adoc`

## Dependencies and Sequencing

- Depends on: none. Runs FIRST (the schemas render into the specification).
- Overlaps with: the epic's other plans on `status.schema.json` (P2, P3, P5, P6), `outline-record-v1.schema.json` and `finding-record.schema.json` (P2): P1 lands first, the others rebase; `core-store.adoc` (P3 protected paths, P5 freshness) and PLAN-V2-185 (core-store.adoc, ADR core-store/087-089): 185 lands first or P1 rebases; the sections are disjoint.
- Adjacent to: `register-item.schema.json`, `epic.schema.json` (stay untouched).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-188-schema-and-store-removals.md
```

## Write-Boundary

The plan touches only its own repository source and tests. It creates and edits NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message and reports through its PR and inbox message (`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary).

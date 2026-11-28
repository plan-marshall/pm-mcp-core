# Landing Analysis: PLAN-V2-211 — the stored question blocks and the finding evidence classes removed

epic: pm-mcp-core-m2-m4-v2
workstream: WS-12
pr: #654 (https://github.com/plan-marshall/pm-mcp-core/pull/654), merge `f991a679` (squash through the merge queue), analysed on the forty-sixth run, 2026-11-28

> Landing record. Source: the plan's inbox message `rb-v2-211-question-blocks-and-finding-evidence-classes-removed-001` (landing-facts present, `merge_state=merged`, `complete: false`, see Metrics; no candidate lesson), read against git (`git diff f991a679^ f991a679`: 16 files, +183/-55) by one read-only leaf (`execution-context-level-3`, R21) that was given the spec and the message as data and ran no build. The orchestrator re-read D1 at `Finding.java:455-485`, swept the repository for the removed names (`grep` over `*.java`, `*.json`, `*.adoc`, `*.toon`, `*.jsonl`: only the new tests and the generic word "blocks" remain), re-read `store-schemas/03:88-105` and searched the local `plan-marshall-mcp` checkout for the removed API.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1(a) stored `blocks` refused, key named | shipped (stored arm) | `QuestionRecordRefusalsTest.removedBlocksMemberIsRefused` asserts `UNREADABLE` and the key in the message; the refusal is `QuestionRecordCodec` `default -> throw undeclared(field)` (`StrictJson.java:210-212`), unconditional on the value, no duplicate-key bypass |
| D1(a) envelope payload refused `invalid_link_args` | partly, by design | nothing in this repository loads the envelope schema; `DecisionEnvelopeSchemaTest` pins member absence and closure (`additionalProperties:false`); the refusal lives in the `pm-mcp-server` assembly |
| D1(b) decision without `blocks` round-trips | shipped | `QuestionRecordRoundTripTest.decisionWithoutBlocksRoundTrips`; `QuestionRecord.Decision` has five components, `@Builder` kept |
| D1(c) schema enum has no `verifier` | shipped | `FindingSchemaParityTest.evidenceClassListsNoRemovedValue`; `finding-record.schema.json:130` is `["none"]` |
| D1(c) a finding with `verifier` "refused and the value named" | partly | there is no finding codec here; `FindingTest.removedEvidenceClassesAreRefused` pins `fromWireName` empty only |
| D1 failing run per test | not shown by reading | the run ids are in the message only |
| D2 removals (enum, component, codec, envelope schema, `verifier`, `substitute`, `claim_refuted` text, moved tests) | shipped | `QuestionRecord`, `QuestionRecordCodec`, `decision-envelope-v1.schema.json` (`required` without `blocks`, closed), `Finding.EvidenceClass` only `NONE` |
| D2 hypothesis "the specification removes `substitute` with `verifier`" | confirmed | `store-schemas/03:96-97` (the job-id rule), no `substitute` left in the specification |
| D3 documents | shipped as a no-op | no `pm-core/doc` text lists the decision's members or the classes; no ADR names them |
| Done-when "existing codec and schema tests hold unchanged" | unachievable as written, met in substance | `blocks` was a required member, so every codec path had to change; no other member or value changed |

## Three-Layer Analysis

### Layer 1 — Specified

All three deliverables shipped; two clauses are met on the stored arm and the vocabulary only (the payload refusal, the finding refusal). The Expected Surface was wrong in one path: `decision-envelope-v1.schema.json` lives in `pm-workflow`, not in `pm-core` (the spec labelled it observed); four test files were touched that the surface did not list (`QuestionRecordRefusalsTest`, `QuestionRecordRoundTripTest`, `RemovedSchemaMembersTest`, the new `DecisionEnvelopeSchemaTest`), each traceable to a deliverable. `core-store.adoc` is declared and unchanged, correctly.

### Layer 2 — Implemented

16 files, +183/-55. The diff is the removal and one description rewrite. Every new assertion message is 20 to 60 characters; no mock or fake added; the literals are wire names. The negative control the removal would lose (`readDecision`'s required members) is kept as the row `decision without reversibility`.

### Layer 3 — Correct

No major defect.

| Defect | File:line | Reading | Effect | Severity |
|--------|-----------|---------|--------|----------|
| L3-1 | `Finding.java:461-475`, `finding-record.schema.json:128-130` | **re-read:** `Evidence(EvidenceClass evidenceClass, ref, claimRefuted)` still requires `class`, whose closed enum is `NONE` alone, documented "No evidence."; the evidence a resolution rests on is now a job id or a `file:line` with `claim_refuted`, and `store-schemas/03:96-105` never mention `class` | a vestigial one-valued discriminator: a writer emits `class: none` beside a job-id ref and `claim_refuted: true`, and the Javadoc contradicts the record; whether the member goes is a specification question (the Done-when said "no other member changes") | minor |
| L3-2 | `QuestionRecord.java` (public `Blocks`, the six-argument `Decision`), `QuestionFixtures` of the `pm-runtime` test JAR | the public API and a fixture of a JAR the assembly consumes changed shape | a compile break in `plan-marshall-mcp` if it uses them; the local checkout (`a4b5aaf`) has no use of `QuestionRecord.Blocks`, `QuestionRecord.Decision` or `.blocks(`, so none is shown | info |
| L3-3 | `finding-record.schema.json:132` | the `claim_refuted` description drops "the server resolves", "whose result the record cites", "the server writes the flag" | the text is weaker than the specification's rule | info |
| L3-4 | `DecisionEnvelopeSchemaTest` | closure is pinned at the top level only; `keptMembersStayDeclared` checks four members | coarse | info |
| L3-5 | existing stores | a stored question line whose decision carries `blocks` now reads `UNREADABLE`, and a refused line blocks the cancel append (ADR core-store/039) | R13 accepts it; the pull request body states it | info |
| clean | | no `Blocks`, `FIELD_BLOCKS`, `VERIFIER` or `SUBSTITUTE` in any source, schema, test fixture or document of this repository; `enumerationsEqualSchema` pins the enum both ways; the envelope schema stays closed; R13 (no bump, no migration) holds | | clean |

**Layer 3 verdict:** findings — 5 points (one minor, four info); not shown by reading — the failing run of each test and its id, `verify`, `verify -Ppre-commit` and Sonar greens, the callers of the removed API in `plan-marshall-mcp` at its head on `origin`. No major finding, nothing reported at once.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md Code Standards | assertion messages 20 to 60 characters, EasyMock only, generators | conforms | the sample above |
| CLAUDE.md Git Workflow | merge through the queue | conforms | squash `f991a679` |
| Standing rules R10, R13 | tests first, format change in place | conforms | six failing runs recorded; no version bump |

## Metrics and Anomalies

- Harness opencode, model `deepseek-v4.1-flash/high`, effort `high`, plugin `unknown`, executor `0.1.2184`; `total_tokens=unknown`; `complete: false` for tokens and plugin version, both recorded `unknown`, never 0. Review: one CodeRabbit inline finding (the payload refusal has no in-repo handler) answered and resolved; Sourcery's own budget exhausted (optional reviewer, a quota notice).
- The lane's self-reported shell-rule breaches (a directory search outside the repository, shell `grep`/`sed`/`tail`/`ls`, one `cp`) are recorded, no remedy owed. No tool-call guard on this harness.

## Routing and Merge Behavior

- Merge: through the merge queue as `f991a679`.
- No candidate lesson in the message; none forwarded.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#654`; `landing` `landings/PLAN-V2-211.md`; `plan_marshall_plan_id` `rb-v2-211-question-blocks-and-finding-evidence-classes-removed`
- [x] the message archived
- [x] L3-1 joins the owner question of the findings write seam (question 1: `evidence.class` is the same unowned package); L3-2 to L3-5 recorded here without an owner (all info)
- [x] the Decisions entry and the Open Defects written; resume anchor updated; `queue-view.md` regenerated

## Follow-Ups

- **Owed outside this repository:** the submit-time `invalid_link_args` refusal of a `blocks` payload (the `pm-mcp-server` assembly must apply `decision-envelope-v1.schema.json`); a build of the assembly against the SNAPSHOT shows whether the removed API is used.
- **Owner question (asked first, one at a time):** the findings write seam is an unowned package in THIS repository (new `Finding` members `rule_id`, `domain`, `evidence_ref`; the `claim_refuted` job-id resolution; the `FIX` to `fixed` transmit; the derived `decision.blocks` read; now also whether `evidence.class` stays). The lane called it "outside this repository"; the specification calls it ownerless, and the leaf found it in-repo.

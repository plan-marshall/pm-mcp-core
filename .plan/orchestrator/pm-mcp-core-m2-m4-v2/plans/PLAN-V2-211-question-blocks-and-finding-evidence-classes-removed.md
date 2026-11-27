# PLAN-V2-211: The stored `blocks` of a question and the `verifier` and `substitute` evidence classes of a finding leave the records the fourth doc-review round removed them from

epic: pm-mcp-core-m2-m4-v2
workstream: WS-12
origin: `inbox/doc-review-2026-11-25-004.md` §1 (the doc-review session's fourth round, core #628 `d961c9df`, bullets 3 and 4 of its Section 1 list: the `QuestionRecord` `blocks` member and the worker's declaration, and the `evidence.class` values), staged by the orchestrator on the forty-first run, 2026-11-27. It supersedes the third round's §1 (M3 §1) where that round said a decision's `blocks` is "written by the server from what waits" (PLAN-V2-23, parked): the fourth round says it is derived on every read from the scope's runnable work and never stored.
lane: plan-marshall

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-V2-211-question-blocks-and-finding-evidence-classes-removed.md` and is queued as one row file, `queue/PLAN-V2-211.json`,
> in the epic ledger (lane `plan-marshall`). The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`). They bind this plan exactly as if they were written below. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R05, R06, R08, R10, R11, R13, R14, R18, R19, R20. (R12: no log message is added; R13: the stored forms lose a member and a value, no format version moves.)

Plan parameters: R06 — developer documentation `pm-core/doc/developer/core-store.adoc` where it lists the question record and the finding evidence; ADR directory `pm-core/doc/adr/core-store/` (the outline reads it and amends the ADR that names the member or the class with a stamp; no new ADR); layout WS-12. **Read the skills first (R20):** `java-core`, `javadoc`, `junit-core`, `cui-testing` of `/home/oliver/git/plan-marshall/marketplace/bundles/`, and `ref-asciidoc` of `pm-documents`; say in the first progress note which you read.

## Objective

The fourth doc-review round decided that what a decision blocks is derived on every read from the scope's current runnable work, never stored, and that `rejected` rests on a server-resolved fact, so the evidence classes `verifier` and the verifier-backed resolution go. The records of this repository still carry both: `QuestionRecord.Decision` has a required `blocks` member (the enum `Blocks`) that the codec writes and reads and that the worker's decision envelope declares, and the finding record's `evidence.class` lists `verifier` and `substitute`. This plan removes the stored `blocks` and the two evidence classes from the records, the codecs, the envelope schema and their tests, tests first, and makes a stored or declared `blocks` refused. It builds no derivation of `decision.blocks` (that is the specification's read, built where the decision is read) and no new finding member.

## Deliverables

All references are read at `939578b6` (the head after #628) and re-read at the head of `main` before editing; drop what a merge since then settled and name the commit.

1. **The failing tests first (R10).** Each fails at the head before any production change, with a failing run id recorded in the progress note. (a) A decision envelope or a stored question record that carries `blocks` is refused `invalid_link_args` with the member named; today the codec reads it (`QuestionRecordCodec.java:105, 340-356`). (b) A stored `QuestionRecord.Decision` without `blocks` is read and written back unchanged; today `Objects.requireNonNull(blocks, "blocks")` (`QuestionRecord.java:305`) refuses it. (c) The finding record's `evidence.class` enumeration no longer contains `verifier`; a finding with `evidence.class: verifier` is refused and the value named.
   - Skills: pm-dev-java:junit-core, pm-dev-java:java-core, pm-dev-java-cui:cui-testing
   - Persona: module-tester
2. **The removals.** (a) `QuestionRecord.java:179-190` the enum `Blocks`, `:284-305` the component and its null check; `QuestionRecordCodec.java:46, 105, 222, 340-358` the field, the write and the read; `decision-envelope-v1.schema.json:6, 13` the member and its `required` entry; a payload that carries `blocks` is refused `invalid_link_args`, the stored form is refused UNREADABLE with the key named, by the pattern of `DerivedFactsCodecTest` (PLAN-V2-188). (b) `finding-record.schema.json:124-133` `evidence.class` loses `verifier`; the outline reads the specification (`store-schemas/03`, Finding Record) for `substitute` and takes the value out only if the specification does (HYPOTHESIS below), and rewrites the `claim_refuted` description (`:132`) to the rule of the fourth round: `claim_refuted` needs `evidence.ref` to be a job id of this plan executed at or after the finding's `raised_at_head`, a `file:line` or URL ref resolves `suppressed`; `Finding.java:461, 473-477` `EvidenceClass` follows the schema, in the same commit as the schema (parity pin `FindingSchemaParityTest:52`). (c) The tests that name the removed things move with them: `QuestionRecordCodecTest:41,120,125,275,281`, `QuestionRecordBuilderContractTest:23,40`, `QuestionLogRevisionTest:31,49`, `QuestionRecordMembersOnTheirEventsTest:178`, `pm-runtime/src/test/java/de/planmarshall/runtime/question/QuestionFixtures.java:31,82`, `FindingTest:213-222`.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety, pm-dev-java:javadoc
   - Persona: implementer
3. **The documents.** `pm-core/doc/developer/core-store.adoc` where it lists the question record's decision members and the finding's evidence classes; an ADR of `pm-core/doc/adr/core-store/` that names `blocks` or the evidence classes is amended with the stamp of this plan (the outline names it by reading).
   - Skills: pm-documents:ref-asciidoc, pm-dev-java:javadoc
   - Persona: documenter

**Done when** (against `origin/main` `939578b6`): the tests of deliverable 1 fail at `939578b6` (or the head the plan names) and hold after deliverable 2 — fails at `939578b6` (the codec reads `blocks` and the schema lists `verifier`); `QuestionRecord.Decision` has no `blocks`, the envelope schema declares none, a payload or a stored record that carries it is refused with the key named; `evidence.class` lists no `verifier` and `Finding.EvidenceClass` equals the schema's list (the parity pin holds); no other member or value of either record changes (the existing codec and schema tests hold unchanged at the head); `verify -Ppre-commit` (its rewrites reviewed, those of any file that is not this plan's not shipped) and `verify -pl pm-core,pm-runtime -am` are green, targeted `-Dtest` where a full verify exceeds the host's budget; the open Sonar issues of the pull request are fixed or dismissed with a reason.

## Standing Constraints

- **Read every cited line at the head first** and drop what a merge settled. The line numbers are those of the fourth round's message at `88680dd7` and were read by the orchestrator at `939578b6`: the `Blocks` enum, the component, the codec field and the three schema leads held; the `retry_counters`, `LessonStore` and `FinalizeInvariants` leads belong to PLAN-V2-209 and PLAN-V2-212.
- **Tests first**: the failing run id of each test of deliverable 1 is in the progress note.
- **Work to the Done-when.** Do not stop to ask the operator which part to continue with.
- **Every carried item gets an outcome** in the landing message and the pull request: adopted into a deliverable, split off to a NAMED existing plan, or dropped, each with its reason.
- **No derivation is built here.** `decision.blocks` as a derived read, the new finding members the fourth round's `duplicate` test needs (`rule_id`, `domain`, `evidence_ref`), the `claim_refuted` job-id resolution and the `FIX` to `fixed` transmit are the findings write seam, which has no owner yet (the operator's question): this plan reports them in the landing message as "owner open" and builds none.
- **No dependency or plugin is added.** No more than 100 files per pull request.

## Claim Labels

All lines read at `origin/main` `939578b6` by the orchestrator on 2026-11-27.

- OBSERVED: `QuestionRecord.java` has the enum `Blocks` (`:183`), the `Decision` component documented "what the decision blocks while open" (`:284`) and `Objects.requireNonNull(blocks, "blocks")` (`:305`); `QuestionRecordCodec.java` writes the field (`:222`) and reads it (`:340-358`) — read at those lines
- OBSERVED: the tests that name `Blocks` are `QuestionLogRevisionTest`, `QuestionRecordBuilderContractTest`, `QuestionRecordCodecTest`, `QuestionRecordMembersOnTheirEventsTest` and `pm-runtime/src/test/java/de/planmarshall/runtime/question/QuestionFixtures.java` — checked by `git grep -ln "Blocks\b"` over the test trees on 2026-11-27 (three other test files match the word for another reason)
- OBSERVED: `finding-record.schema.json:130` `evidence.class` is `["verifier", "substitute", "none"]` and `:132` names the refutation sub-state — read at those lines
- OBSERVED: the decision record (`decision-record.schema.json`) holds no `blocks`; the stored form is the question record — the message of the fourth round says so, read against `git grep` of the schema on 2026-11-27
- HYPOTHESIS: the specification removes `substitute` together with `verifier` — confirm/refute at `doc/specification/store-schemas/03-findings-and-review-records.adoc` (Finding Record, evidence) and `phase-workflows` (finding triage); the message names `verifier` alone (verify-at-outline)
- HYPOTHESIS: `Finding.java:461, 473-477` `EvidenceClass` and `FindingTest:213-222` are the only Java places that name the classes — confirm/refute by `git grep -n "EvidenceClass\|VERIFIER"` (verify-at-outline)
- Verify-first clause: a removal the specification does not name (`substitute`) is NOT made inline; the outline reports it and the question goes back to the orchestrator.

## Expected Surface

- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/QuestionRecord.java`, `pm-core/src/main/java/de/planmarshall/core/store/QuestionRecordCodec.java`, `pm-core/src/main/resources/de/planmarshall/core/store/decision-envelope-v1.schema.json`, `pm-core/src/main/resources/de/planmarshall/core/findings/finding-record.schema.json`, `pm-core/src/main/java/de/planmarshall/core/findings/Finding.java`, `pm-core/src/test/java/de/planmarshall/core/store/QuestionRecordCodecTest.java`, `pm-core/src/test/java/de/planmarshall/core/store/QuestionRecordBuilderContractTest.java`, `pm-core/src/test/java/de/planmarshall/core/store/QuestionLogRevisionTest.java`, `pm-core/src/test/java/de/planmarshall/core/store/QuestionRecordMembersOnTheirEventsTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/question/QuestionFixtures.java`, `pm-core/src/test/java/de/planmarshall/core/findings/FindingTest.java`, `pm-core/src/test/java/de/planmarshall/core/findings/FindingSchemaParityTest.java`, `pm-core/doc/developer/core-store.adoc`.

## Dependencies and Sequencing

- Depends on: none (the specification is merged, #628).
- Overlaps with (class level): PLAN-V2-189 (names `finding-record.schema.json` for the `dependency_delta` source; a single shared file is no block, whichever merges second re-reads it), PLAN-V2-209 (names `core-store.adoc`), PLAN-V2-192 (the `claim_refuted` rule needs a way to tell that a job ran at or after `raised_at_head`: its build row's `head` is the only head-bearing record; the question is the findings write seam's).
- Adjacent to: PLAN-V2-23 (parked: its line "written by the server from what waits" is superseded by this plan's premise, noted there by the orchestrator), PLAN-V2-188 (shipped, #621: the removal pattern).

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-211-question-blocks-and-finding-evidence-classes-removed.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates and edits NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports its outcome through its PR and its inbox message. See `persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

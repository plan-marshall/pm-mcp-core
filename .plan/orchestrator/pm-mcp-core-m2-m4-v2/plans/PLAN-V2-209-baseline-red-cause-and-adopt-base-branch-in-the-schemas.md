# PLAN-V2-209: The gap cause baseline_red and the adopt plan's base branch reach the schemas and the fallback enum that the specification now names

epic: pm-mcp-core-m2-m4-v2
workstream: WS-12
origin: `inbox/doc-review-2026-11-25-003.md` §1 (the doc-review session's decisions of 2026-11-25, merged here as #622 `5ae4fd1c`: the base branch of an `adopt` plan, the `checks_pending` wait, the baseline-red hand-back), staged by the orchestrator on the thirty-ninth run, 2026-11-26. Ask 1 of the message ("fold §1 into the removal and correction plan, schemas first") is answered by staging: PLAN-V2-188, the removal plan, is running and its spec stays its scope (the message of the third round said so), and PLAN-V2-190 to PLAN-V2-192 sit behind it.
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-209-baseline-red-cause-and-adopt-base-branch-in-the-schemas.md` and is queued as one row file, `queue/PLAN-V2-209.json`,
> in the epic ledger (lane `runbook`). The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`). They bind this plan exactly as if they were written below. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R05, R06, R08, R10, R11, R13, R18, R19, R20. (R12: no log message is added; R13: no format version changes, the enumerations grow by one value and one description changes; R14 and R17 do not apply.)

Plan parameters: R06 — developer documentation `pm-core/doc/developer/core-store.adoc` and the developer documentation of `pm-workflow`'s plan package where it lists the enumerations; ADR directories `pm-core/doc/adr/core-store/` and `pm-workflow/doc/adr/` (no new ADR: the decision is the specification's, and each new value is stated where its enumeration is; the outline reads the ADR directories and amends an ADR that names the closed enumeration); layout WS-12. R12 — none.

**Read the skills first (R20).** `java-core`, `javadoc`, `junit-core` of `/home/oliver/git/plan-marshall/marketplace/bundles/pm-dev-java/skills/`, and `ref-asciidoc` of `pm-documents`; say in the first progress note which you read.

## Objective

The specification merged on 2026-11-25 (#622) gives a pre-merge barrier that finds every red required check explained by baseline failures its own end, `baseline_red_required`, which hands the plan back with the gap `cause: baseline_red`, and it lets an `adopt` plan's base branch be written by its start and by `finalize.adopt-pr`. Two closed enumerations and one description in the schemas of this repository do not say so yet: the gap record's `cause`, the composition record's `fallback_cause` with its Java twin `CompositionCause.FallbackCause`, and the `base_branch` description of the plan overrides. This plan adds the value and the words, in the same commit as each parity pin, tests first, and checks the one neighbour the review asked about (`deliverable_removed`). It builds none of the merge path or the plan sources: those are packages (PLAN-V2-210 edits their texts), not this plan.

## Deliverables

All references are read at `2b4bdee8` and re-read at the head of `main` before editing; drop what a merge since then settled and name the commit.

1. **The failing tests first (R10).** Each fails at the head before any production change, with a failing run id recorded in the progress note. (a) The gap record's `cause` enumeration contains `baseline_red`: a gap record with `cause: baseline_red` validates against `gap-record.schema.json`; today it is refused (`gap-record.schema.json:9` lists eleven values and not this one). No test in `pm-core` pins that enumeration (`git grep` for `files_not_applicable` finds only the schema, the composition schema and `CompositionCause.java`): the outline names the class that takes the pin, beside the parity tests of `pm-core/src/test/java/de/planmarshall/core/store/`. (b) `CompositionCause.FallbackCause` has a member with the wire name `baseline_red` and the composition record schema's `fallback_cause` enum contains it; a composition record with `fallback_cause: baseline_red` validates (the pattern of `PlanInstanceHeaderTest.newFallbackCauseValidates`, `:683`, and the parity pin `:674-677`, which stays green because both sides change in one commit).
   - Skills: pm-dev-java:junit-core, pm-dev-java:java-core
   - Persona: module-tester
2. **The enumerations and the description (the specification's own wording).** (a) `gap-record.schema.json:9` `cause` gains `baseline_red` and its description one sentence: the cause of a gap whose pre-merge barrier ended `baseline_red_required` (every red required check of `HEAD` covered by `baseline_red` rows of the change ledger), the hand-back of the plan; the sentence names what the gap lists (the checks and the base commit that shows them red) as the specification words it (`phase-workflows/06` Merge Path item 5, `state-catalogue/04` `finalize.hand-back`, `state-catalogue/07` `finalize.merge-barrier`). (b) `composition-record.schema.json:19` `fallback_cause` gains `baseline_red`, and `CompositionCause.java:80-104` `FallbackCause` gains `BASELINE_RED("baseline_red")` with its Javadoc, in the enumeration's order (after `FILES_NOT_APPLICABLE`, as the schema lists it). (c) `plan-overrides.schema.json:35` `base_branch` description gains "or, for a plan of the `adopt` lane, by its start from the pull request's base and by `finalize.adopt-pr` when it observes a retargeted base"; the schema is included in `store-schemas/14`, which the specification already words so. (d) `deliverable_removed`, which the review asks to be checked in the same step: it is in the gap record's `cause` enum and NOT in `fallback_cause`, and `composition-record.schema.json:19` says why ("deliverable_removed is no fallback, its re-planning composes a replan version"); the outline confirms that reading against `Fallback Cause` in the specification and records "settled, no change" in the progress note, or, if the specification says otherwise, adds the value with its test (a refutation loops back here, not into silence).
   - Skills: pm-dev-java:java-core, pm-dev-java:javadoc
   - Persona: implementer
3. **The fixtures and the documents.** (a) `InvariantFixtures.java:707` `BarrierOutcome` (`pass`, `pass_hand_back`): the enum models the barrier's outcomes for the invariant fixtures; the outline reads where the barrier's failing outcomes are built and gives `baseline_red_required` its member where the fixtures name a failing outcome, or records that this enum names the passing outcomes alone and the failing ones are built elsewhere (then no edit). (b) `pm-core/doc/developer/core-store.adoc` and the developer documentation of the plan package of `pm-workflow` state the new cause and the new fallback cause where they list the enumerations; an ADR of the two directories that names the closed enumeration is amended with the stamp of this plan.
   - Skills: pm-documents:ref-asciidoc, pm-dev-java:javadoc
   - Persona: documenter

**Done when:** the tests of deliverable 1 fail at `2b4bdee8` (or the head the plan names) and hold after deliverable 2; the two schema files, `CompositionCause.java` and the specification name the same causes in the same order (`PlanInstanceHeaderTest`'s pin and the new pin of the gap record hold); `deliverable_removed` has a recorded outcome; no behaviour of any other enumeration value changes (the existing schema and composition tests hold unchanged at the head); `verify -Ppre-commit` (its rewrites reviewed, those of any file that is not this plan's not shipped) and `verify -pl pm-core,pm-workflow -am` are green, targeted `-Dtest` where a full verify exceeds the host's budget; the open Sonar issues of the pull request are fixed or dismissed with a reason.

## Standing Constraints

- **Read every cited line at the head first** and drop what a merge settled. The line numbers are those of `2b4bdee8`.
- **Tests first**: the failing run id of each test of deliverable 1 is in the progress note.
- **Work to the Done-when.** Do not stop to ask the operator which part to continue with.
- **Every carried item gets an outcome** in the landing message and the pull request: adopted into a deliverable, split off to a NAMED existing plan, or dropped, each with its reason.
- **The enumerations grow by one value and one description changes.** No other value is added or removed (the removals are PLAN-V2-188 to PLAN-V2-192's), no format version moves (R13), no consumer of these enumerations outside this repository is edited (the assembly `plan-marshall-mcp` holds none at `fd7e252` for `FallbackCause`; the outline re-checks with `git grep` and names the commit).
- **No dependency or plugin is added.** No more than 100 files per pull request.

## Claim Labels

All lines read at `origin/main` `2b4bdee8` by the orchestrator.

- OBSERVED: `gap-record.schema.json:9` `cause` lists `exhausted, stepped_out, decided, infeasible, condition, provider_refused, merge_by_operator, operator_path, local_only, files_not_applicable, deliverable_removed` and no `baseline_red` — read at `pm-core/src/main/resources/de/planmarshall/core/store/gap-record.schema.json:9`
- OBSERVED: `composition-record.schema.json:19` `fallback_cause` lists the same values without `deliverable_removed` and without `baseline_red`, and says `deliverable_removed` is no fallback — read at `pm-workflow/src/main/resources/de/planmarshall/workflow/plan/composition-record.schema.json:19`
- OBSERVED: `CompositionCause.FallbackCause` has ten members, `EXHAUSTED` to `FILES_NOT_APPLICABLE`, and none for `baseline_red` or `deliverable_removed` — read at `CompositionCause.java:80-106`
- OBSERVED: `PlanInstanceHeaderTest` pins the file's `fallback_cause` enum against the Java enum (`:674-677`) — read at `PlanInstanceHeaderTest.java:83-87, 674-683`
- OBSERVED: `plan-overrides.schema.json:35` `base_branch` says it is "written by the answer of init.options or from the plan's settings before the base is stamped" — read at `pm-core/src/main/resources/de/planmarshall/core/store/plan-overrides.schema.json:35`
- OBSERVED: `InvariantFixtures.java:707` `BarrierOutcome` has two members, `PASS` and `PASS_HAND_BACK` — read at `pm-workflow/src/test/java/de/planmarshall/workflow/verify/InvariantFixtures.java:707-710`
- OBSERVED: the specification names the outcome and the cause: `baseline_red_required` and `cause: baseline_red` — read at the message `inbox/doc-review-2026-11-25-003.md` (the decisions) and `doc/specification/state-catalogue/07`, `phase-workflows/06` (the outline re-reads them at the head)
- OBSERVED: no `.java` or schema file of this repository holds the string `baseline_red` as a gap or fallback cause (`ChangeLedgerRecord.Kind.BASELINE_RED` is the change-ledger kind, a different enumeration) — checked by `git grep` on 2026-11-26
- HYPOTHESIS: the gap record has no member for "the checks and the base commit" the specification says the gap lists, so the specification's sentence is carried by existing members (`gate`, `evidence_uri`) or needs a member — confirm/refute at `gap-record.schema.json` `properties` and the Gap Record section of `store-schemas` (verify-at-outline)
- HYPOTHESIS: `BarrierOutcome` of `InvariantFixtures` names the passing outcomes only and needs no `baseline_red_required` — confirm/refute at `InvariantFixtures.java:700-760` and its users (verify-at-outline)
- Verify-first clause: the gap record's missing members, if the first hypothesis is refuted, are a change of the closed schema and are NOT made inline: the outline reports it, and the member is a deliverable of this plan only if the specification names it; otherwise the question goes back to the orchestrator before any schema member is added.

## Expected Surface

- OBSERVED: `pm-core/src/main/resources/de/planmarshall/core/store/gap-record.schema.json`, `pm-core/src/main/resources/de/planmarshall/core/store/plan-overrides.schema.json`, `pm-workflow/src/main/resources/de/planmarshall/workflow/plan/composition-record.schema.json`, `pm-workflow/src/main/java/de/planmarshall/workflow/plan/CompositionCause.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/plan/PlanInstanceHeaderTest.java`, `pm-core/doc/developer/core-store.adoc`.
- HYPOTHESIS (swept: the file exists on the main branch, the line named above): `pm-workflow/src/test/java/de/planmarshall/workflow/verify/InvariantFixtures.java`.
- HYPOTHESIS (the outline names the class; none pins the gap enumeration today): one test class beside the parity tests of the store test package of the core module; it is declared here by that sentence and not by a path, because the class does not exist yet.

## Dependencies and Sequencing

- Depends on: none (the specification is merged, #622).
- Overlaps with (class level): PLAN-V2-190 (names `InvariantFixtures`; the gate decides at emit, a single shared file is no block), PLAN-V2-161 (names `InvariantFixtures`), PLAN-V2-188 (running; its schemas are the derived-fact kinds and the finding record, none of this plan's files).
- Adjacent to: `ChangeLedgerRecord.Kind.BASELINE_RED` (a different enumeration, read only), PLAN-V2-210 (the package texts that build the barrier outcome and the adopt base write; it states the cause by name once this plan has landed).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-209-baseline-red-cause-and-adopt-base-branch-in-the-schemas.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates and edits NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports its outcome through its PR and its inbox message. See `persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

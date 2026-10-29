# PLAN-126: Apply R05 to the shipped role sets, model caller and role qualification

epic: pm-mcp-core-m2-m4
workstream: WS-10

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-126-r05-reduction-role-sets.md` and is queued as one row file, `queue/PLAN-126.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

> **Why this plan exists (2026-10-29).** PLAN-26 (#146, role artifacts, role sets and the model caller) kept the implemented sections of `model-work/01`, `client-profiles/01` and `store-schemas/12` and the covered items of `implementation-watch/model-work.adoc` and `client-profiles.adoc`, with reasons (over 100 link sites anchor them; `model-work/01` still holds text the package does not implement); PLAN-34 (#196, role qualification) deleted three watch items and removed 4 lines of `model-work/01` and none of `store-schemas/16`. This is the second half of the R05 sweep; the first half (waits, launch, task protocol, consultation) is the sibling plan PLAN-127 (`r05-reduction-waits-launch-protocol`), **whose practice (condense and point, delete a watch item only when a named test covers its guard and nothing remains, never change a block a test reads) and repository ADR this plan applies unchanged**. The operator confirms that practice before either plan is emitted. This plan is documentation only: no source file, no test.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r2`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R05, R07, R08, R10, R13, R17, R18, R19. (R10 is the heart of the plan: `model-work/01`, `store-schemas/12` and `store-schemas/16` are read by tests; R13 because examples quote versioned records; R17 because the plan runs the links check; R18 only as a check, no Java is touched.)

Plan parameters (the values the rules ask for):

- R06 (module documentation) and R12 (log messages) do not apply: no source file changes and no design decision is taken (the practice is the sibling plan's ADR, cited).
- R17 (the links check): `python3 /home/oliver/git/plan-marshall-documentation/.claude/skills/doc-review/scripts/trace.py /home/oliver/git/plan-marshall-documentation/doc .` from the checkout of this repository, after fetching and fast-forwarding the sibling; the count of broken links must not rise.
- Layout: workstream WS-10.

## Objective

Apply the practice of the sibling plan to the slice of the role sets, the model caller and the role qualification, verified against the code at the head of `main`: condense the implemented prose of `model-work/01`, `client-profiles/01`, `store-schemas/12` and `store-schemas/16` to "Implemented by `<class>`, verified by `<test>`, Remaining: …" paragraphs under the kept anchors, and decide the covered watch items. Serves: the package bookkeeping of roadmap Milestone 4. Where: the four specification parts and the watch documents `model-work.adoc` and `client-profiles.adoc`. **No code is written. No JSON schema block and no table that a test reads is changed.**

## Deliverables

1. **The classification table** (in the pull request body), one row per candidate: the section or watch item, its inbound reference count, the verdict (`implemented`, `mixed`, `open`), the implementing class and the verifying test with the line read at the head, the action. Candidates: of `model-work/01-roles-and-configuration.adoc` the sections `role-sets` (line 115), `role-assignment` (141), `role-composition` (205), `role-class-fit` (211), `closed-task-in-band` (216), `model-caller` (219), `qualification-topics` (350), `constant-baseline` (345), `grading` (355) with `screener-repeated-runs` (382) and `judge-instrument-check` (385); of `client-profiles/01-profile-schema.adoc` the profile-set and verification-stage prose that `profile-loader` and the role factory implement; of `store-schemas/12-machine-stores.adoc` the role-assignment store (`role-assignments-store`, line 341); of `store-schemas/16-task-queue.adoc` the qualification and judge-check records; of the watch documents `PM-WATCH-DISP-49` (composition check), `-51` (judge score), `-57` (open: `judge` as the one fixed role: keep), `-66` (mixed: its grading half is shipped, its corpus-export half is owned by `decision-quorum-and-calibration`: keep the export half) and the items of `client-profiles.adoc` that the landing of #146 names. `PM-WATCH-DISP-64`, `-65`, `-48` and `PM-WATCH-CLIENT-13` are already gone at the head (record that).
2. **`model-work/01` and the role-set slice.** Condense the `implemented` sections of the list above, naming for each the classes of `pm-core` package `core.role` (`RoleSetLoader`, `RoleSets`, `RoleResolver`, `RoleAssignments`, `RoleCatalog`, `RoleEffectiveConfiguration` as found), of `pm-workflow` package `workflow.model` (`RoleComposition`, `CompositionRefusal`, `ModelCaller`, `ModelCallResult`, `RoleGrader`) and of `core.role.qualification` (`ConstantBaseline`, `GradingProfile` with `SCREENER_RUNS`, `GradingOffer`, `JudgeInstrumentCheck`, `JudgeCheck`, `RoleTopics`), and the tests that cover them; a section whose class has no test of its own (found: no `GradingOfferTest`, no `JudgeInstrumentCheckTest` name exists at the head) is `mixed` until the table names the test that exercises it, or is left. The validation table of `#role-validation` (read by `RoleValidationTableTest` and `RoleArtifactValidatorTest`) is not touched.
3. **`client-profiles/01`, `store-schemas/12`, `store-schemas/16`.** Condense only prose outside the JSON blocks (`SpecificationDocument.jsonBlockAfter` reads the block after an anchor such as `[#queue-task-record]` or `[#activity-journal]`; before the first edit list every anchor a test passes for these files with `git grep -n 'jsonBlockAfter\|ANCHOR =' -- '*Test.java'` and keep the blocks byte-identical); the member descriptions inside the blocks stay as they are. `client-profiles/01` is shared with PLAN-108 and `store-schemas/12` with PLAN-46: the lane edits the smallest set of paragraphs and reports each file it left untouched because of the overlap.
4. **The watch items.** Delete each covered item whose guard has a passing named test and no remainder, with its backlinks; rewrite a mixed item to its remainder; keep `PM-WATCH-DISP-57`, which is open, and record that the decision it asks (state `judge` as the one compiled exception, or an operator key `qualification.judge_role`) is the operator's and is not taken here.
5. **The check.** No dangling reference to a deleted id or an anchor; the lines of `doc/Traceability.adoc` for the requirements the touched sections name (`PM-WORK-1`, `PM-WORK-3`, `PM-WORK-6`, `PM-CLIENT-5` and the others of the sections) are brought in line (R08: only those); the targeted parity tests that read the three pinned files are run and pass: `-Dtest=RoleValidationTableTest+RoleArtifactValidatorTest+AuditSchemaParityTest+TaskQueueSchemaParityTest` in `pm-core` and `CredentialStoreMoveRecordParityTest` in `pm-runtime` (`test -pl <module> -am`).

**Done when:** the table of Deliverable 1 is in the pull request body with a verdict, evidence and action for every candidate; each condensed section names an implementing class and a verifying test that exist at the head (`git ls-files`); every deleted watch id has no remaining reference; `PM-WATCH-DISP-57` stays; `git diff origin/main` on the three pinned files shows no changed line inside a JSON block or inside the table of `#role-validation`; the five targeted parity tests pass; the links check does not rise. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason (none expected: no Java file changes), and its quality gate is green, before the pull request is reported ready. **Build:** a documentation plan: the pull request builds in CI (the skip for documentation-only pull requests is off since #185, `.github/project.yml:31`); the lane runs the targeted tests of Deliverable 5 and of every class a condensed paragraph names, reported in the pull request body (a full `verify` exceeds the host's bound). **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main`; a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set:** the landing records `landings/PLAN-26.md` and `landings/PLAN-34.md` as leads only (read from the main checkout: `.plan/orchestrator/pm-mcp-core-m2-m4/landings/`); the sibling plan's ADR and table; requirements `PM-WORK-1`, `PM-WORK-3`, `PM-WORK-6`, `PM-CLIENT-5`; the Open Defect "PLAN-25's package bookkeeping is partial … recurred in PLAN-26".

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **R10 binds every edit.** `model-work/01` (role tests), `store-schemas/12` (`AuditSchemaParityTest`, `CredentialStoreMoveRecordParityTest`) and `store-schemas/16` (`TaskQueueSchemaParityTest`) are read by tests; a docs-only change to a block they read once broke `main` (#171, repaired in #172 by `FindingSchemaParityTest`). The CI skip is off since #185, but the lane still runs the readers.
- A condensed paragraph names a class and a test that exist (`git ls-files`); a name taken from a landing record and not found at the head is not written.
- Nothing is decided that the operator owns: `PM-WATCH-DISP-57` (the fixed role `judge`) stays open.

## Claim Labels

- OBSERVED: `model-work/01-roles-and-configuration.adoc` holds the anchors `role-sets` (line 115), `role-assignment` (141), `role-validation` (163), `role-composition` (205), `role-class-fit` (211), `closed-task-in-band` (216), `model-caller` (219), `qualification` (318), `constant-baseline` (345), `qualification-topics` (350), `grading` (355), `judge-instrument-check` (385) — `grep -n '^\[#'` at `a8da4104`; `screener-repeated-runs` is at line 382
  - verdict: corroborated | checked_at: a8da4104e3efad83b2ca9c1b28ec724ad402fd8a | by: pm-mcp-core-m2-m4/spec-bookkeeping | rescoped: n/a | evidence: grep -n at a8da4104
- OBSERVED: the implementing classes exist: `pm-workflow/src/main/java/de/planmarshall/workflow/model/` has `RoleComposition`, `CompositionRefusal`, `ModelCaller`, `ModelCallResult`, `RoleGrader`, `QualificationLookup`; `pm-core/src/main/java/de/planmarshall/core/role/` has `RoleSetLoader`, `RoleSets`, `RoleResolver`, `RoleAssignments`, `RoleCatalog`; `core/role/qualification/` has `ConstantBaseline`, `GradingOffer`, `GradingProfile` (`SCREENER_RUNS = 5` at line 23), `JudgeInstrumentCheck`, `JudgeCheck`, `RoleTopics` — `ls` at `a8da4104`
  - verdict: corroborated | checked_at: a8da4104e3efad83b2ca9c1b28ec724ad402fd8a | by: pm-mcp-core-m2-m4/spec-bookkeeping | rescoped: n/a | evidence: ls of the three directories; GradingProfile.java line 23 read
- OBSERVED: the tests that exist: `RoleCompositionTest` and `ModelCallerTest` in `pm-workflow/src/test/java/de/planmarshall/workflow/model/`; `RoleSetLoaderTest`, `RoleValidationTableTest`, `RoleArtifactValidatorTest`, `RoleEffectiveConfigurationTest` in `pm-core/src/test/java/de/planmarshall/core/role/`; `ConstantBaselineTest`, `JudgeCheckTest`, `RoleTopicsTest`, `QualificationCheckTest`, `ScoringTest`, `QualificationRecordStoreTest` in `core/role/qualification/`; no test class named after `GradingOffer`, `GradingProfile` or `JudgeInstrumentCheck` — `ls` at `a8da4104`
  - verdict: corroborated | checked_at: a8da4104e3efad83b2ca9c1b28ec724ad402fd8a | by: pm-mcp-core-m2-m4/spec-bookkeeping | rescoped: n/a | evidence: ls of the test directories
- OBSERVED: `PM-WATCH-DISP-49` (line 21), `-51` (37), `-57` (52, "Open: `judge` as the one fixed role"), `-66` (893, with the corpus-export half) stand in `doc/implementation-watch/model-work.adoc`; `PM-WATCH-DISP-48` and `PM-WATCH-CLIENT-13` do not exist at the head
  - verdict: corroborated | checked_at: a8da4104e3efad83b2ca9c1b28ec724ad402fd8a | by: pm-mcp-core-m2-m4/spec-bookkeeping | rescoped: n/a | evidence: grep -n of the watch headings; grep for the two absent ids returns nothing
- OBSERVED: the schema-parity tests pin blocks of `store-schemas/12` (`AuditSchemaParityTest.java:48`, `CredentialStoreMoveRecordParityTest.java:47`) and `store-schemas/16` (`TaskQueueSchemaParityTest.java:60`, anchors `[#queue-task-record]` line 114 and `[#activity-journal]` line 188) through `SpecificationDocument.jsonBlockAfter`; `RoleValidationTableTest` reads the validation table of `model-work/01#role-validation` (its Javadoc, line 33)
  - verdict: corroborated | checked_at: a8da4104e3efad83b2ca9c1b28ec724ad402fd8a | by: pm-mcp-core-m2-m4/spec-bookkeeping | rescoped: n/a | evidence: the test files read at the cited lines
- OBSERVED: the shared-file overlaps — `model-work/01` is on the Expected Surface of PLAN-46, PLAN-45, PLAN-55, PLAN-92, PLAN-99; `store-schemas/12` of PLAN-46, PLAN-45, PLAN-58, PLAN-49, PLAN-59, PLAN-63; `client-profiles/01` of PLAN-108, PLAN-58, PLAN-60; `store-schemas/16` of PLAN-99; `implementation-watch/client-profiles.adoc` of PLAN-108; `implementation-watch/model-work.adoc` of PLAN-60, PLAN-99 — awk over the `## Expected Surface` sections of the specs in `plans/`
  - verdict: corroborated | checked_at: a8da4104e3efad83b2ca9c1b28ec724ad402fd8a | by: pm-mcp-core-m2-m4/spec-bookkeeping | rescoped: n/a | evidence: scripted read of the ledger specs
- HYPOTHESIS: the sections and watch items of Deliverable 1 are `implemented` or `mixed` as the landing records say; the classification is the plan's work — confirm/refute per row (verify-at-outline). `model-work/01` also holds text that the package does not implement (the lane of #146 said so), which stays
  - verdict: unverifiable | checked_at: a8da4104e3efad83b2ca9c1b28ec724ad402fd8a | by: pm-mcp-core-m2-m4/spec-bookkeeping | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- Verify-first clause: before editing, fetch `origin/main`, list the open pull requests for one that touches the six files of the Expected Surface (PLAN-46 and PLAN-108 edit `store-schemas/12` and `client-profiles/01` and may be open), re-read the files at the head, and read the sibling plan's landing for the ADR number and the practice as shipped; a refutation of any claim above loops back to re-scope.
  - verdict: unverifiable | checked_at: a8da4104e3efad83b2ca9c1b28ec724ad402fd8a | by: pm-mcp-core-m2-m4/spec-bookkeeping | rescoped: n/a | evidence: procedure: runs at the lane's refine

## Expected Surface

- OBSERVED: `doc/specification/model-work/01-roles-and-configuration.adoc` — specification part; implemented prose condensed, the validation table untouched (read by tests)
- OBSERVED: `doc/specification/client-profiles/01-profile-schema.adoc` — specification part; implemented prose only
- OBSERVED: `doc/specification/store-schemas/12-machine-stores.adoc` — specification part; the role-assignment prose only, blocks untouched (read by tests)
- OBSERVED: `doc/specification/store-schemas/16-task-queue.adoc` — specification part; the qualification-record prose only, blocks untouched (read by tests)
- OBSERVED: `doc/implementation-watch/model-work.adoc` — watch document; the covered items decided
- OBSERVED: `doc/implementation-watch/client-profiles.adoc` — watch document; the covered items decided

Shared bookkeeping, not surface (R19): `doc/Traceability.adoc`. No `doc/plans/` file and no ADR: the packages `role-sets-and-model-caller` and `role-qualification` are done and their files are gone, and the practice's ADR is the sibling's.

## Dependencies and Sequencing

- Depends on: the sibling plan PLAN-127 (`r05-reduction-waits-launch-protocol`) (it writes the ADR this plan cites and edits `doc/implementation-watch/model-work.adoc`, the one shared file) and the operator's confirmation of the practice; PLAN-46 (running; `model-work/01` and `store-schemas/12`: run after it lands, or the lane leaves those two files for a later pass and says so)
- Overlaps with live plans: `model-work/01` with PLAN-46, 45, 55, 92, 99 (5); `store-schemas/12` with PLAN-46, 45, 58, 49, 59, 63 (6); `client-profiles/01` with PLAN-108, 58, 60 (3); `store-schemas/16` with PLAN-99 (1); `implementation-watch/client-profiles.adoc` with PLAN-108 (1); `implementation-watch/model-work.adoc` with PLAN-60 and PLAN-99 (2). Of the running plans only PLAN-46 shares files. PLAN-105 and PLAN-109 share none.
- Pairwise with the other drafts of this cluster: not disjoint from PLAN-127 (`r05-reduction-waits-launch-protocol`) (one file: `implementation-watch/model-work.adoc`; sequence it after); disjoint from PLAN-125 (`package-files-carry-unowned-spec`).
- A documentation plan, but three of its files are read by parity tests; see Standing Constraints.
- Restructures: nothing in code.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-126-r05-reduction-role-sets.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

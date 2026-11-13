# PLAN-V2-141: Rework the test design in steps: the kept findings of ADR 025, the shared fixtures, the builder check, and the retention table PLAN-V2-140 left empty

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
origin: the three-layer analysis of PLAN-V2-140 (`landings/PLAN-V2-140.md`, layer 2 "D4 narrow" and "D6 narrow"), the merged pull request #403 (`91a131f9`), ADR `doc/adr/025-test-design-layering-base-classes-and-fixtures.adoc` (findings F1 to F14, the target design S1 to S9, § Leads for the Operator) and ADR `doc/adr/024-verify-duration-measured-and-cut.adoc`, and the operator's request of 2026-11-12 to cut the verify time "across all modules, quality must not fall, no test duplication, eventually rework the test design"
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-141-test-design-rework.md` and is queued as one row file, `queue/PLAN-V2-141.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R05, R06, R07, R08, R11, R17, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R09: no confinement; R10: no specification part is edited; R12: no message is added; R13: no stored format changes; R14, R15, R16: test code, with one named exception, no protocol, DSL or runtime behaviour changes. R05 because the retention table and the deleted tests may leave a requirement without a verifier: `doc/Traceability.adoc` is read per touched requirement. R11 because Deliverable 1 and, if the operator decides so, Deliverable 3 amend or add an ADR.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: the guides of the packages whose tests change (`pm-core/doc/developer/core-store.adoc`, `pm-runtime/doc/developer/runtime-question.adoc` and the guides the outline finds by searching `*.adoc` for the names of the moved fixtures); ADR directory `doc/adr/` (amend ADR 012 only if Deliverable 3 is decided; otherwise a new ADR with the next free number at `origin/main`, 026 at `62bf986d`; 020 is a gap, do not fill it).
- R12 — none (no message).

**Read the skills first (R20).** `cui-testing`, `junit-core`, `java-core`, `java-lombok`, `java-null-safety` and `javadoc` of `/home/oliver/git/plan-marshall/marketplace/bundles/{pm-dev-java,pm-dev-java-cui}/skills/`; say in the first progress note which you read. R20 applies to every test line this plan touches.

## Objective

PLAN-V2-140 measured the verify time and cut it (the pre-commit gate from 650 s to about 235 s, the coverage lane from 1278 s to 123 s, by one setting of the root POM), audited the test design module by module and recorded the result in ADR 025, but it changed three test classes only. Everything else it found it kept, with a reason, or put to the operator as a lead; its retention table is empty (`doc/adr/024-...adoc` lines 663-668 and ADR 025 lines 461-466 read "none"), so the operator's two other conditions, **no test duplication** and **quality must not fall**, were never exercised. This plan takes the kept findings up, one finding per deliverable, and for every removed, merged or moved test fills the retention table: the removed test, the retained test, the assertions that carry the behaviour, and a break-the-code run that shows the retained test fails when the behaviour breaks (a mutation in the production line, reverted). A finding that the reading of the code does not support is closed again with its evidence, not forced.

Targets, from ADR 025: the layers and lanes of § target-lanes, the structure of § needed-structure, the fixture rules S2, S4 and S7. The time target is not a goal of this plan: the 600 s was met by PLAN-V2-140; the goal is the design and the retention table, and the measured time after the plan is reported (the median of three idle `verify -Ppre-commit` runs, as ADR 024 § The Final Measurement) so that a regression is visible.

## Deliverables

Five deliverables, one commit each, in this order; the cut line, if the outline wants one, is Deliverable 4. Each reads the finding's row in ADR 025 first and says in the progress note what it found at the head.

1. **The scope read and the retention table opened (order 1).** Re-read findings F1, F5, F7 and the structure rule S7 of ADR 025 at the head of `origin/main` (the tree moved after the ADR's snapshot: the ADR counts 18 and 2 `*SchemaParityTest` classes where the tree has 21 and 3, and 55 `*RecordsTest` in `pm-workflow` where it has 56) and list, per finding, the classes it moves, with the line counts; write the retention table (`Removed test | Retained test | Assertions carrying the behaviour | Break-the-code evidence`) in the pull request body and in the new ADR, one row per deletion or merge, **before** the first deletion; the rows are filled as the deliverables land. Done when: the list and the empty-by-design table exist before any test is removed, and the ADR has its number.
2. **The generator-based builder check (F1).** The 79 `*BuilderContractTest` classes of `pm-core`, the 44 `*RecordsTest` of `pm-runtime` and the 56 of `pm-workflow` repeat one check per record by hand. Replace the population by one generator-driven check per module that finds the records with a builder by reflection (a closed list: the records of four or more components, CLAUDE.md Code Standards) and proves builder-equals-canonical-constructor for every component, so that a record with a builder and no check cannot exist. Each hand-written class goes only if the generated check carries every assertion it carried (the retention table row names the assertions, and the break-the-code run drops a component from one builder). Test order: the generated check first, then the deletions.
3. **The shared fixtures (F7, S2, S4).** One home for the enrolment composition (`JobEnrolmentFixture` and its twins), the git layout of a worktree and the fixtures that more than one test class of a module repeats, sized per stage. **Ask-at-refine:** whether `pm-core:tests` publishes fixtures beside its generators (a change of ADR 012 and of CLAUDE.md § Generator Artifacts, the operator's decision); the alternative is one fixture class per module in its own test tree, which needs no decision. See Carried Open Items.
4. **The `@Nested` form of the question-log and project-resolution tests (F5, S7).** Merge the classes of one unit into `@Nested` classes where ADR 025 F5 names them, and take the `TestBases` temp directory of the remaining classes to `@TempDir` (S7: 8 integration tests, 4 unit classes and the fixture still use `TestBases`, ADR 025 F10; the three `QuestionLogRecordReader*Test` classes were done by PLAN-V2-140). A class that needs a Unix-domain socket path stays on `TestBases` (a `@TempDir` path may exceed the socket path limit; ADR 025 F10 gives the reason). No test is removed by the merge; the method count of each module is the same before and after unless a retention row says otherwise.
5. **The result (R05, R06).** The final measurement as in ADR 024 (three idle runs of `verify -Ppre-commit` and one `verify -Pcoverage`, the host load per run, the module times), the coverage per module against the baseline of ADR 024 (no module below its figure), the test counts before and after with every difference explained (PLAN-V2-140's runs 4 and 6 differ by 39 tests that nobody explained), the retention table complete, the ADR (amended or new) and the guides.

**Done when:** Deliverables 1 to 5; the retention table lists every removed, merged or moved test with its break-the-code evidence and is not "none" unless the reading closed every finding without a deletion (then the ADR says so with the evidence); no module is below its coverage baseline (`verify -Pcoverage` per module, the figures in the pull request body); the test count before and after is explained row by row; `verify -Ppre-commit` is not slower than the final measurement of ADR 024 (about 235 s) by more than the run-to-run variance; the guide lines that name a moved fixture are changed. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready.

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`):

- **Quality must not fall.** No assertion is weakened to make a merge fit; a test that is removed is removed only with its retention row and a break-the-code run; the per-module coverage of ADR 024 is a floor (80 % per module is the repository's minimum, the baseline of ADR 024 is the plan's).
- **No test duplication.** The plan removes duplication it proves (the retention table), it does not add any; a hand-written class kept beside a generated check that carries its assertions is a defect.
- **The guard is the diff of the test counts.** Per module: the method count before and after, with every difference named.
- **No new hand-written double.** Doubles are EasyMock (CLAUDE.md Code Standards); the existing hand-written doubles are PLAN-V2-136's.
- **One production change, named:** `toBuilder` on `ProjectEnrolment` only if the operator decides it (Carried Open Items 2); no other production file changes.
- **No dependency or plugin is added**, and `pom.xml` is not edited (the `reuseForks` override of PLAN-V2-140 stays as it is).
- **Static state.** The classes of one module share a JVM since #403 (`pom.xml:173-186`); a fixture this plan shares must not hold mutable static state, and every default it changes (locale, system property, JUL handler) is restored in both arms.
- **Where the project analyses its pull requests with Sonar** (read it over plain HTTP: `pullRequest=N`, never `branch=<feature>`): an open Sonar issue is fixed in a pushed commit, or dismissed with a stated reason recorded as `{issue-key}: {reason}` and transmitted through `plan-marshall:workflow-integration-sonar:sonar_rest transition --issue-key {key} --transition {falsepositive|wontfix}`. A dismissal the run cannot transmit is a blocker.

## Claim Labels

All claims read at `62bf986d` (`origin/main`) through the landing analysis of PLAN-V2-140; the outline re-reads each before it acts.

- OBSERVED: the retention tables of ADR 024 and ADR 025 are empty ("none"): `doc/adr/024-verify-duration-measured-and-cut.adoc` lines 663-668, `doc/adr/025-test-design-layering-base-classes-and-fixtures.adoc` lines 461-466.
- OBSERVED: PLAN-V2-140 changed `LspClientTest.java`, `QuestionLogRecordReaderBoundaryTest.java`, `QuestionLogRecordReaderReadTest.java`, `QuestionLogRecordReaderTest.java` and `pom.xml` (+14 lines: `reuseForks` true in the root `pluginManagement`), no test was removed or merged, the method counts of the three question-log classes are 5, 20 and 9 before and after.
- OBSERVED: the repository holds 79 `*BuilderContractTest` in `pm-core`, 44 `*RecordsTest` in `pm-runtime`, 56 `*RecordsTest` in `pm-workflow` (ADR 025 F1 states 55 for `pm-workflow`).
- OBSERVED: ADR 025 § Leads for the Operator names four leads: fixtures in `pm-core:tests` (a change of ADR 012), `toBuilder` on `ProjectEnrolment`, the fakes statement of ADR 012 (PLAN-V2-136 owns it), and the follow-up for F1, F5 and F7.
- HYPOTHESIS: the generated builder check can find every record with a builder by reflection without a hand-kept list; verify-first: count the records with `@Builder` in `src/main` of the module and compare it with the count of the hand-written classes; a record the reflection misses (a nested record, a generic one) is a finding of the first deliverable.
- HYPOTHESIS: a `@TempDir` path stays under the Unix-domain socket path limit (108 bytes) for the classes that open no socket; the three question-log classes prove it for those three (ADR 025 F10).

## Expected Surface

Declared by directory at the staging; the first deliverable replaces each directory by the class list it finds (R19), with full repo-relative paths:

- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/` — the `*BuilderContractTest` classes and the fixtures of `core.store`, `core.role`, `core.config`
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/` — the `*RecordsTest` classes, `JobEnrolmentFixture`, the `TestBases` users, the question-log and project-resolution tests
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/` — the `*RecordsTest` classes
- OBSERVED: `doc/adr/025-test-design-layering-base-classes-and-fixtures.adoc` — read, amended only in the lines this plan makes false (the counts, the leads); history stays
- HYPOTHESIS: `doc/adr/026-the-test-design-follows-the-layers-and-the-retention-table.adoc` — one new ADR (or an amendment of `doc/adr/012-generator-artifacts.adoc` if the operator decides the fixtures in `pm-core:tests`)
- OBSERVED: `pm-core/doc/developer/core-store.adoc`, `pm-runtime/doc/developer/runtime-question.adoc` — the guide lines that name a moved fixture
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/config/ProjectEnrolment.java` — `toBuilder`, only if Carried Open Items 2 is decided

Not surface (R19): `doc/plans/README.adoc`, `doc/adr/README.adoc`, `doc/Traceability.adoc`. No `pom.xml`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-140 (shipped, #403, `91a131f9`), and **runs after PLAN-V2-136, 125 and 126**: they edit the same test trees (136 converts the hand-written doubles of `core.store`, `engine`, `scenario`, `model`, `task`, `lock`, `start`, `worker`; 125 and 126 conform the assertion messages, draws and statements of the classes this plan merges), and a merge of classes under them would conflict on every class; a class a conformance plan has just rewritten is merged once, not twice.
- Also after PLAN-V2-101 to 105 (assertion messages: the merged `@Nested` classes carry the messages those plans give) and PLAN-V2-137 (the builder residue: the records this plan's generated check iterates).
- Overlaps at file level are decided by the disjointness gate at emit from the Expected Surface (every plan of WS-11 touches the same test trees; the gate prompts, and the order above answers it).
- Not here: the doubles (PLAN-V2-136), the assertion messages and draws of untouched lines (101 to 105, 125, 126), the parent POM's `reuseForks=false` and the forked `pre-commit` lifecycles (the repository `pm-mcp-parent`, an operator lead of PLAN-V2-140 with no plan here), the `ProbeBudget` clock seam (PLAN-V2-114 item 19).

## Carried Open Items

Ask-at-refine, one at a time, each with a recommendation (R07):

1. **Does `pm-core:tests` publish fixtures (Deliverable 3)?** One home for the enrolment composition, the git layout of a worktree and the shared fixtures needs `pm-core:tests` to carry fixtures beside its generators; that changes ADR 012 and the CLAUDE.md section "Generator Artifacts" (a decision of the operator, `doc/adr/025` § Leads for the Operator). Options: publish, or one fixture class per module in its own test tree (twins stay, one per module). Recommendation: one fixture class per module and no publication now, about 60%; the twins are two classes, the artifact change is a repository-wide decision, and the saving of publishing is one class.
2. **`toBuilder` on `ProjectEnrolment` (Deliverable 3).** It lets `JobEnrolmentFixture` change one component without repeating the fifteen-component constructor (ADR 025 F7); it is a production change of `pm-core` for the sake of a test. Options: add it, or keep the constructor call in the fixture. Recommendation: add it, about 65%, because the record already has `@Builder` and the fixture is the only caller that needs a changed copy; if the operator refuses, the fixture keeps its constructor call and the finding is closed with that reason.
3. **How far Deliverable 2 goes.** Replace the hand-written classes only where the generated check carries every assertion they carried, or all of them. Recommendation: only where the retention table can name the assertions (the first option), about 80%; a builder class that tests something the generated check cannot (a validation in the compact constructor) stays and is named in the table.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-141-test-design-rework.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

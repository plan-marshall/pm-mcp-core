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
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-documents:ref-asciidoc
   - Persona: implementer
2. **The generator-based builder check (F1).** The 79 `*BuilderContractTest` classes of `pm-core`, the 44 `*RecordsTest` of `pm-runtime` and the 56 of `pm-workflow` repeat one check per record by hand. Replace the population by one generator-driven check per module that finds the records with a builder by reflection (a closed list: the records of four or more components, CLAUDE.md Code Standards) and proves builder-equals-canonical-constructor for every component, so that a record with a builder and no check cannot exist. Each hand-written class goes only if the generated check carries every assertion it carried (the retention table row names the assertions, and the break-the-code run drops a component from one builder). Test order: the generated check first, then the deletions.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-documents:ref-asciidoc
   - Persona: implementer
3. **The shared fixtures (F7, S2, S4).** One home for the enrolment composition (`JobEnrolmentFixture` and its twins), the git layout of a worktree and the fixtures that more than one test class of a module repeats, sized per stage. **Ask-at-refine:** whether `pm-core:tests` publishes fixtures beside its generators (a change of ADR 012 and of CLAUDE.md § Generator Artifacts, the operator's decision); the alternative is one fixture class per module in its own test tree, which needs no decision. See Carried Open Items.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-documents:ref-asciidoc
   - Persona: implementer
4. **The `@Nested` form of the question-log and project-resolution tests (F5, S7).** Merge the classes of one unit into `@Nested` classes where ADR 025 F5 names them, and take the `TestBases` temp directory of the remaining classes to `@TempDir` (S7: 8 integration tests, 4 unit classes and the fixture still use `TestBases`, ADR 025 F10; the three `QuestionLogRecordReader*Test` classes were done by PLAN-V2-140). A class that needs a Unix-domain socket path stays on `TestBases` (a `@TempDir` path may exceed the socket path limit; ADR 025 F10 gives the reason). No test is removed by the merge; the method count of each module is the same before and after unless a retention row says otherwise.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-documents:ref-asciidoc
   - Persona: implementer
5. **The result (R05, R06).** The final measurement as in ADR 024 (three idle runs of `verify -Ppre-commit` and one `verify -Pcoverage`, the host load per run, the module times), the coverage per module against the baseline of ADR 024 (no module below its figure), the test counts before and after with every difference explained (PLAN-V2-140's runs 4 and 6 differ by 39 tests that nobody explained), the retention table complete, the ADR (amended or new) and the guides.

**Done when:** Deliverables 1 to 5; the retention table lists every removed, merged or moved test with its break-the-code evidence and is not "none" unless the reading closed every finding without a deletion (then the ADR says so with the evidence); no module is below its coverage baseline (`verify -Pcoverage` per module, the figures in the pull request body); the test count before and after is explained row by row; `verify -Ppre-commit` is not slower than the final measurement of ADR 024 (about 235 s) by more than the run-to-run variance; the guide lines that name a moved fixture are changed. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-documents:ref-asciidoc
   - Persona: implementer

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
  - verdict: corroborated | checked_at: 3ba79c4f96dab1f6954cbaf2b5a7a145f8d4556e | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: ADR 024:664 and :656, ADR 025:465 and :457 still read none; no commit since 8c7eac6f touched either ADR
- OBSERVED: PLAN-V2-140 changed `LspClientTest.java`, `QuestionLogRecordReaderBoundaryTest.java`, `QuestionLogRecordReaderReadTest.java`, `QuestionLogRecordReaderTest.java` and `pom.xml` (+14 lines: `reuseForks` true in the root `pluginManagement`), no test was removed or merged, the method counts of the three question-log classes are 5, 20 and 9 before and after.
  - verdict: corroborated | checked_at: 3ba79c4f96dab1f6954cbaf2b5a7a145f8d4556e | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: historical (#403, 91a131f9), unchanged; root pom.xml reuseForks override :175-182; ADR 024:675
- OBSERVED: the repository holds 79 `*BuilderContractTest` in `pm-core`, 44 `*RecordsTest` in `pm-runtime`, 56 `*RecordsTest` in `pm-workflow` (ADR 025 F1 states 55 for `pm-workflow`).
  - verdict: contradicted | checked_at: 3ba79c4f96dab1f6954cbaf2b5a7a145f8d4556e | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: yes | evidence: counted at the head: 80 pm-core *BuilderContractTest, 57 pm-workflow and 44 pm-runtime *RecordsTest; ADR 025 :71,:78,:136-137,:158 still say 79, 55, 18 and 2
- OBSERVED: ADR 025 § Leads for the Operator names four leads: fixtures in `pm-core:tests` (a change of ADR 012), `toBuilder` on `ProjectEnrolment`, the fakes statement of ADR 012 (PLAN-V2-136 owns it), and the follow-up for F1, F5 and F7.
  - verdict: corroborated | checked_at: 3ba79c4f96dab1f6954cbaf2b5a7a145f8d4556e | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: ADR 025 heading :482 and four bullets :484-492
- HYPOTHESIS: the generated builder check can find every record with a builder by reflection without a hand-kept list; verify-first: count the records with `@Builder` in `src/main` of the module and compare it with the count of the hand-written classes; a record the reflection misses (a nested record, a generic one) is a finding of the first deliverable.
  - verdict: unverifiable | checked_at: 3ba79c4f96dab1f6954cbaf2b5a7a145f8d4556e | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: Lombok @Builder has SOURCE retention, so a static builder() is the only reflective discriminator; nested @Builder records exist (ProjectEnrolment 5, WorkflowStatus 9, QueueTaskRecord 6)
- HYPOTHESIS: a `@TempDir` path stays under the Unix-domain socket path limit (108 bytes) for the classes that open no socket; the three question-log classes prove it for those three (ADR 025 F10).
  - verdict: unverifiable | checked_at: 3ba79c4f96dab1f6954cbaf2b5a7a145f8d4556e | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: depends on the host java.io.tmpdir; seven ITs still use TestBases, PeriodicSweepKilledRuntimeIT uses @TempDir :73

## Expected Surface

Declared by directory at the staging; the first deliverable replaces each directory by the class list it finds (R19), with full repo-relative paths:

- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/` — the `*BuilderContractTest` classes and the fixtures of `core.store`, `core.role`, `core.config`
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/` — the `*RecordsTest` classes, `JobEnrolmentFixture`, the `TestBases` users, the question-log and project-resolution tests
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/` — the `*RecordsTest` classes
- OBSERVED: `doc/adr/025-test-design-layering-base-classes-and-fixtures.adoc` — read, amended only in the lines this plan makes false (the counts, the leads); history stays
- HYPOTHESIS: `doc/adr/034-the-test-design-follows-the-layers-and-the-retention-table.adoc` — one new ADR (or an amendment of `doc/adr/012-generator-artifacts.adoc` if the operator decides the fixtures in `pm-core:tests`)
- OBSERVED: `pm-core/doc/developer/core-store.adoc`, `pm-runtime/doc/developer/runtime-question.adoc` — the guide lines that name a moved fixture
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/ProjectEnrolment.java` — `toBuilder`, only if Carried Open Items 2 is decided

Not surface (R19): `doc/plans/README.adoc`, `doc/adr/README.adoc`, `doc/Traceability.adoc`. No `pom.xml`.

- OBSERVED (cleanup 2026-11-19, understated surface; the files the Deliverables name and the surface did not): `pm-core/src/test/resources/hand-written-doubles.txt`, `pm-runtime/src/test/resources/hand-written-doubles.txt`, `pm-workflow/src/test/resources/hand-written-doubles.txt`.
- OBSERVED (understated, added by the cleanup re-ground of 2026-11-23 at `origin/main` `8c7eac6f`): `doc/adr/012-generator-artifacts.adoc`, `CLAUDE.md`, `pm-core/pom.xml`, `pm-runtime/pom.xml`, `pm-runtime/doc/developer/runtime-job.adoc`, `pm-runtime/doc/developer/runtime-start.adoc`, `pm-runtime/README.adoc`, `doc/adr/README.adoc`.
- OBSERVED (added 2026-11-23 for carried item 98, PLAN-V2-175 landing, at `origin/main` `7259b039`): `doc/adr/README.adoc` (line 34; it is also edited for the new ADR row, so the "Not surface" line above does not apply to it) and `doc/adr/007-specifications-of-the-engine-in-this-repository.adoc` (the Status sentence, line 6).

- OBSERVED (understated, added by the cleanup re-ground of 2026-11-25 at `origin/main` `3ba79c4f`; each path checked to exist): `pm-core/src/test/java/de/planmarshall/core/testing/HandWrittenDoubleScanTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/testing/HandWrittenDoubleScanTest.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/testing/HandWrittenDoubleScanTest.java`, `pm-core/src/test/java/de/planmarshall/core/guard/NullMarkedPackagesTest.java`, `doc/adr/024-verify-duration-measured-and-cut.adoc`, `doc/adr/026-the-kept-hand-written-test-doubles-are-a-closed-list.adoc`

## Dependencies and Sequencing

- Depends on: PLAN-V2-140 (shipped, #403, `91a131f9`), and **runs after PLAN-V2-125 and 126** (PLAN-V2-136 is shipped, #411 `6aa55a56` and #416 `df666305`: that predecessor is met): they edit the same test trees (136 converts the hand-written doubles of `core.store`, `engine`, `scenario`, `model`, `task`, `lock`, `start`, `worker`; 125 and 126 conform the assertion messages, draws and statements of the classes this plan merges), and a merge of classes under them would conflict on every class; a class a conformance plan has just rewritten is merged once, not twice.
- Also after PLAN-V2-101 to 105 (assertion messages: the merged `@Nested` classes carry the messages those plans give) and PLAN-V2-137 (the builder residue: the records this plan's generated check iterates).
- Overlaps at file level are decided by the disjointness gate at emit from the Expected Surface (every plan of WS-11 touches the same test trees; the gate prompts, and the order above answers it).
- Not here: the doubles (PLAN-V2-136), the assertion messages and draws of untouched lines (101 to 105, 125, 126), the parent POM's `reuseForks=false` and the forked `pre-commit` lifecycles (the repository `pm-mcp-parent`, an operator lead of PLAN-V2-140 with no plan here), the `ProbeBudget` clock seam (PLAN-V2-156 item 19).

## Carried Open Items

Ask-at-refine, one at a time, each with a recommendation (R07):

1. **Does `pm-core:tests` publish fixtures (Deliverable 3)?** One home for the enrolment composition, the git layout of a worktree and the shared fixtures needs `pm-core:tests` to carry fixtures beside its generators; that changes ADR 012 and the CLAUDE.md section "Generator Artifacts" (a decision of the operator, `doc/adr/025` § Leads for the Operator). Options: publish, or one fixture class per module in its own test tree (twins stay, one per module). Recommendation: one fixture class per module and no publication now, about 60%; the twins are two classes, the artifact change is a repository-wide decision, and the saving of publishing is one class.
2. **`toBuilder` on `ProjectEnrolment` (Deliverable 3).** It lets `JobEnrolmentFixture` change one component without repeating the fifteen-component constructor (ADR 025 F7); it is a production change of `pm-core` for the sake of a test. Options: add it, or keep the constructor call in the fixture. Recommendation: add it, about 65%, because the record already has `@Builder` and the fixture is the only caller that needs a changed copy; if the operator refuses, the fixture keeps its constructor call and the finding is closed with that reason.
3. **How far Deliverable 2 goes.** Replace the hand-written classes only where the generated check carries every assertion they carried, or all of them. Recommendation: only where the retention table can name the assertions (the first option), about 80%; a builder class that tests something the generated check cannot (a validation in the compact constructor) stays and is named in the table.

**Carried 2026-11-23 from the landing analysis of PLAN-V2-175 (`landings/PLAN-V2-175.md`, item 98; a documentation nit, placed here because this plan edits the ADR index `doc/adr/README.adoc` for its new ADR, lines 53-56).** (a) `doc/adr/README.adoc:34` lists ADR 007 as "amended by ADR 016, by ADR 027, and by the documentation's local link check (no `links.yml` workflow)": add the removal of the pre-merge links-check requirement of 2026-11-23 (PLAN-V2-175, #582) that ADR 007's Status (line 6) records. (b) The Status sentence of ADR 007 (line 6) is now one long sentence with inconsistent list items ("by ADR 016, by the setup of the documentation, which has no CI: ..., and by the removal ..."): split it into a list; wording only. Both are edits of the index and of one ADR's Status, with no behaviour; read `origin/main` first and drop them if a merge settled them. Recorded, not fixed: the stale `source-sha256` of `.opencode/rules/plan-marshall-standing-rules.md:4` (a bridge re-emission refreshes it).

**Ordering decision of 2026-11-14 (issues pass Q3, operator answer 1):** this plan's Deliverable 2 owns the 68 `java:S5778` issues that SonarCloud reports in the `*BuilderContractTest` classes of `pm-core` (45 classes; they are about to be replaced by the generated check, so no edit of them is made to silence the rule): PLAN-V2-156 item 10 (a) to (d) wait for this plan and are cut to what survives its retention table. The count of open issues of `main` therefore stays at about 70 until this plan lands; the gate does not depend on it. State the number of `java:S5778` issues before and after in the pull request body.

**Carried 2026-11-14 from the landing analysis of PR #416 (`landings/PR-416.md`; the scan of the hand-written doubles is a test-design matter).** The assembly check of `runtime.test.AuditSinkDoubles` is done (no reference on `plan-marshall-mcp` `origin/main`). **To add to Deliverable 3 or as its own deliverable (the outline decides, a ruling for the retention table):** (a) **classify the baselines** `hand-written-doubles.txt` of `pm-core`, `pm-workflow` and `pm-runtime`: they hold 81, 81 and 91 entries and 89 of the first 162 are stateless `*Outcome` and `*Reason` enums (for example `pm-core/src/test/java/de/planmarshall/core/spi/TestPrimitives.java:438`) that are data, not doubles: separate the closed-list entries (K1 to K9, F1 to F3), the data types (excluded by a scan rule or listed as data) and the conversion debt, so that "until PLAN-V2-125 and 126 convert it" is true; (b) **widen the scan** (doubles of JDK, third-party and `pm-api` types such as `ScriptedRandom`, test-local supertypes such as `TestPrimitives.Capped extends Fixed`, returned and argument lambdas, the enclosing class in the entry identity, and K1 `FakeBus` and K3 `FakePmExec`, which implement no production type); (c) **find the repository root like `pm-core/src/test/java/de/planmarshall/core/guard/NullMarkedPackagesTest.java` does** (the three scan tests read `Path.of("src/test/java")` relative to the working directory and fail from the reactor root or an IDE) and **share one parser** (the three copies of `HandWrittenDoubleScanTest` are 516 to 518 lines each: a helper in `pm-core:tests`, an ADR 012 question that item 1 of the Carried Open Items already asks); (d) `pm-runtime:tests` has no includes and now publishes `HandWrittenDoubleScanTest` and `hand-written-doubles.txt` to the assembly: exclude them or say why not; (e) align the `CLAUDE.md` sentence with what the test demands (the test demands a baseline line, the sentence says the ADR is amended).

## Re-ground record (cleanup 2026-11-17, BINDING over any contradicting older text of this spec)

Written by `pm-mcp-core-m2-m4-v2/cleanup`, measured against `main` at `3d453e6c`. The verdicts are stamped on the claims above (`checked_at: 3d453e6c`). Where this section and an older text of this spec differ, this section is the later reading; the outline re-reads every cited line at `main` before it relies on it. The evidence is the re-ground leaf's one-line reading, not a re-derivation.

- The HYPOTHESIS ADR number 026 is taken (ADR 026 is the closed list of hand-written doubles; 027 and 028 exist too): the new ADR takes the next free number at the time of the outline.

## Re-ground record (cleanup 2026-11-19 at `e2ae8627`, BINDING over any contradicting older text of this spec)

Written by `pm-mcp-core-m2-m4-v2/cleanup` from the verdicts of a read-only leaf over this spec (its statements are not re-read here; the executing plan re-reads each at its outline). Since the first pass (`d506e818`) #484, #490, #494, #495, #496, #503, #504, #506, #511 and #512 merged. Where this section and an older text of this spec differ, this section is the later reading.

- pm-workflow `*RecordsTest` is 57 (`PolicyCallRecordsTest`, #503), not 56; pm-core 79 and pm-runtime 44 hold; ADR 025 `:78,137` still say 55; the pm-workflow hand-written-doubles baseline lost one line (#503). ADR number 026 is taken (027 and 028 too).
- Understated: the three `src/test/resources/hand-written-doubles.txt`, `CLAUDE.md` (Carried item (e), Open Item 1), `HandWrittenDoubleScanTest` (x3). Twelve files overlap the running PLAN-V2-15: emit after 15 lands.

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

## Re-Ground Record of 2026-11-23 (cleanup)

Read by a read-only leaf at `origin/main` `8c7eac6faa00b26d0e11f0307c768d85e4fd044b`; the orchestrator did not re-read each point (the verdicts above carry the producer `pm-mcp-core-m2-m4-v2/cleanup`). The outline re-reads every line it acts on. Where this record and an older statement of the spec disagree, this record wins; the older text is kept as the audit record.

- Counts at the head: pm-core 80 `*BuilderContractTest` (the new one is `profile/HarnessVerificationBuilderContractTest`, #552) not 79; pm-workflow 57 `*RecordsTest` not 56; pm-runtime 44 plus `config/ProviderDefaultBuilderContractTest`; `*SchemaParityTest` 27 pm-core, 4 pm-runtime, 1 pm-provider-git (ADR 025 says 18 and 2); ADR 025 :71,78,93-94,136-138,158 still carry 79, 55, 18, 2. Hand-written-double baselines: pm-core 85, pm-workflow 91, pm-runtime 89 non-comment lines (spec 81, 81, 91).
- The new ADR number is NOT 026 (taken by `026-the-kept-hand-written-test-doubles-are-a-closed-list.adoc`; 027 to 033 exist): the next free number is 034, indexed in `doc/adr/README.adoc` (lines 53-56).
- D4: seven integration tests use `TestBases` (`BoundedVerificationKilledRuntimeIT`, `JobRecoveryKilledRuntimeIT`, `ProjectRecoveryKilledRuntimeIT`, `RecoveryStepsKilledRuntimeIT`, `RelayVersionCheckKilledRuntimeIT`, `RuntimeDrainKilledRuntimeIT`, `StartupColdStartIT`); `PeriodicSweepKilledRuntimeIT` already uses `@TempDir` (:73). `JobEnrolmentFixture` is referenced by 12 test classes (ADR 025 F7 says 9); its four `with*` helpers :192, 201, 210, 233.
- The hand-written doubles are now PLAN-V2-125 and 126's (PLAN-V2-136 merged, #411). `pm-core:tests` already publishes a non-generator helper package (`de/planmarshall/core/testing/assertions/**`, `pm-core/pom.xml:144-149`): a precedent for the helper share, and the reason the constraint "`pom.xml` is not edited" conflicts with Carried Open Item 1 and item (d) (`pm-runtime/pom.xml:147-153` test-jar execution): both POMs are now in the surface and the constraint is the operator's to lift or keep.
- Surface corrections: `ProjectEnrolment` is `pm-core/src/main/java/de/planmarshall/core/store/ProjectEnrolment.java` (`@Builder` :73, 15 components, no `toBuilder`), not `.../config/`; `pm-core/doc/developer/core-store.adoc` names none of the fixtures; `runtime-job.adoc:416` names `JobEnrolmentFixture`, `runtime-start.adoc:557` and `pm-runtime/README.adoc:59-69` name `TestBases`. `reuseForks` override now `pom.xml:175-184`. Lombok `@Builder` has SOURCE retention: a static `builder()` is the only reflective discriminator.

## Re-Ground Record of 2026-11-25 (cleanup, BINDING over any contradicting older text of this spec)

Read by a read-only leaf at `origin/main` `3ba79c4f96dab1f6954cbaf2b5a7a145f8d4556e` after #594, #598, #599, #603 to #606, #609, #612 and #613; the orchestrator persisted every verdict through `corpus set-verdict` (producer `pm-mcp-core-m2-m4-v2/cleanup`; contradicted claims are re-scoped by this record). The outline re-reads every line it acts on. Where this record and an older statement differ, this record wins.

- Contradicted and absorbed: (2) the repository holds 80 `*BuilderContractTest` in pm-core (new `HarnessVerificationBuilderContractTest`), 57 `*RecordsTest` in pm-workflow and 44 in pm-runtime plus `ProviderDefaultBuilderContractTest`; ADR 025 `:71`, `:78`, `:136-137`, `:158` still say 79, 55, 18 and 2.
- Surface: the Expected Surface now lists `HandWrittenDoubleScanTest` of the three modules, `NullMarkedPackagesTest`, ADR 024 and ADR 026 (the retention table of ADR 024 `:664` and the baselines of ADR 026 are what the Done-when fills). The "Not surface (R19)" line still names `doc/adr/README.adoc` although two later bullets put it on the surface (new ADR row, `:34` amendment): the later bullets win.
- Sequencing: 126 D1 merged (#612), 125 has not landed (hand-written-double baselines unchanged at 85 pm-core, 89 pm-runtime, 91 pm-workflow); PLAN-V2-136 and 140 are shipped. Carried item 98 (README.adoc:34 still ends "no links.yml workflow"; ADR 007:6 is one long sentence) is not settled by any merge and stays open. ADR 034 is still the next free `doc/adr/` number.

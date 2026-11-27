# PLAN-V2-194: A failed judge check, or one without a verdict, refuses the grading with judge_unfit

epic: pm-mcp-core-m2-m4-v2
workstream: WS-05
origin: doc-review-2026-11-24-001.md section 1 ("Judge check", model-work/01:363-370; owner D4 of section 4); operator's cut, plan 7.
lane: runbook

> Staged plan spec — ready for `/plan-marshall-runbook-lane` hand-off. Lives at `plans/PLAN-V2-194-judge-check-refuses-grading.md`; one queue row, lane `runbook`. The orchestrator EMITS the command; it never launches the plan inline.

## First Instruction: Read The Standing Rules

Read `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` completely first; say so with the revision in the first progress note and repeat revision and rule ids in the landing message. Drafted set: R02, R05, R06, R08, R10, R11, R13, R14, R18, R19, R20 (orchestrator confirms; R13 binds: the stored `judge_check` value `failed` becomes unreadable). Plan parameters: R06 developer documentation `pm-core/doc/developer/core-role.adoc` and `pm-workflow/doc/developer/workflow-model.adoc`; ADR directory `pm-core/doc/adr/core-role/` (a new ADR: the check gates the grading); layout WS-05.

## Objective

Today a grading records the judge check's result beside its measurements and is never refused on it. The specification now says: while the corpus holds the topic `judge/`, a judge configuration whose check failed refuses the grading with `judge_unfit`, and a check without a verdict refuses it until the check is run again; the graded role keeps its conservative fallback; without the topic no check runs and the grading proceeds with no check result. The qualification record can therefore never carry `judge_check: failed`.

## Deliverables

Three deliverables, about 14 files, no split needed. Schema first.

1. **The record: `failed` can no longer be written.** `qualification-record.schema.json:41` (`judge_check` enum `["passed"]`, description "It blocks no grading" removed), `QualificationRecord.JudgeCheckResult` (:258-267: `FAILED` removed), `QualificationRecordCodecTest` (:72-90, the round trip of a failed check) becomes the negative control, parity tests follow.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety, pm-dev-java-cui:cui-testing
   - Persona: implementer
   - Test order: first the control (a stored record with `judge_check: "failed"` is refused whole, naming the member and value), red at e451ed04; then schema and enum in one commit (`QualificationRecordSchemaParityTest`).
2. **The refusal.** `GradingOutcome.Refused` gains the reason `JUDGE_UNFIT = "judge_unfit"` (beside :62-71); `RoleGrader.judgeCheck` (`pm-workflow/.../model/RoleGrader.java:127-184`) refuses with it when the stored or just-run check has verdict failed, and refuses when the check has no verdict (an unscored answer, `JudgeInstrumentCheck` Javadoc :25-27), while the corpus holds `judge/`; no `judge/` item: no check, grading proceeds, record without result (`JudgeCheckResult` absent). `JudgeInstrumentCheck` Javadoc says the check gates the grading.
   - Skills: pm-dev-java:java-core, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
   - Test order: first `RoleGraderTest` cases (failed check refuses `judge_unfit`; no-verdict check refuses; passed check grades and records `passed`; no `judge/` topic grades with no check result — the matched control), `QualificationValueTest` (the reason code), `JudgeCheckTest`; then the code.
3. **Documentation.** `core-role.adoc:237-242` ("`GradingOutcome.Refused` declares no refusal on ..."), `workflow-model.adoc:104` ("The check blocks no grading"), the new ADR, `doc/Traceability.adoc` row of `JudgeInstrumentCheck` (:3576) if its text says "never refused".
   - Skills: pm-documents:ref-asciidoc, pm-dev-java:javadoc
   - Persona: documenter

**Done when** (against `origin/main` e451ed04): a grading whose judge check failed returns `Refused` with reason `judge_unfit` and writes no qualification record — fails at e451ed04 (it grades); a check without a verdict refuses until rerun — fails at e451ed04; a stored record with `judge_check: failed` is refused and named — fails at e451ed04; a grading without a `judge/` topic proceeds with no check result, and a passed check records `passed` — holds at e451ed04 (`RoleGrader.java:167-184`). `verify -Ppre-commit`, `verify -Pcoverage`, `verify -Pintegration-tests -pl pm-core -am`, `verify -pl pm-core,pm-workflow -am` green (targeted `-Dtest` where a full verify exceeds the host budget). Sonar: 0 open new-code findings at the head or each key selected by the operator.

## Standing Constraints

- Every removal deletes its pinning tests and adds a negative control; no read shim; schema and parity test in one commit.
- Read cited lines at the head first. No dependency or plugin; at most 100 files per pull request; no model-facing content.
- Sonar: fix or dismiss only on the operator's selection; an undecidable read blocks.

## Claim Labels

- OBSERVED: the wiring is in `pm-workflow/src/main/java/de/planmarshall/workflow/model/RoleGrader.java` (:73-74,89-91,127-184; `JudgeCheckResult.FAILED` at :184); `GradingHarness` (`pm-core/.../qualification/GradingHarness.java`) has no judge-check reference.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: RoleGrader.java:73-74 (judgeChecks, instrumentCheck fields), :89-91 (ctor), :127-184 (judgeCheck call and method), FAILED returned at :184; GradingHarness.java has no 'judge' text (git grep empty).
- OBSERVED: `GradingOutcome.Refused` constants at `GradingOutcome.java:62-71` (four reasons, no `judge_unfit`); `QualificationRecord.java:258-267`; schema `:41` enum `["passed","failed"]` with "It blocks no grading".
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: GradingOutcome.java:62-71 four constants (corpus_import_failed, scorer_not_independent, no_topics, no_graded_items); QualificationRecord.java:256-267 JudgeCheckResult enum (PASSED, FAILED); schema line 41 enum ["passed","failed"], description ends "It blocks no grading."
- OBSERVED: the specification states the target at `model-work/01-roles-and-configuration.adoc:364`; the watch `doc/implementation-watch/model-work.adoc:57` already names `judge_unfit`.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: model-work/01-roles-and-configuration.adoc:364 states the gate and judge_unfit; doc/implementation-watch/model-work.adoc:57 names judge_unfit (in the PM-WATCH-DISP-51 Remaining text).
- OBSERVED: `QualificationRecordCodecTest.java:72-90` round-trips a failed check today.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: QualificationRecordCodecTest.java: failedJudgeCheck() at :73 (@Test at :71-72, method ends :89) builds a FAILED record, writes it, reads it back and asserts "judge_check":"failed"; span is :72-89, one line off the cited :72-90.
- HYPOTHESIS: the runtime adapters (`QualificationService`, `WorkerGradingHarness`) pass a `Refused` reason through unchanged — confirm at `pm-runtime/src/main/java/de/planmarshall/runtime/worker/QualificationService.java:94-117` and `WorkerGradingHarness.java` (verify-at-outline).
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: QualificationService.java (112 lines) only delegates: grade() returns engine.grade(plan, consent) at :87-89, no Refused handling; WorkerGradingHarness.java has no GradingOutcome/Refused reference. Cited range :94-117 is past the file end; the delegation sits at :75-89.
- HYPOTHESIS: "a check without a verdict refuses until the check is run again" means the grading refuses and the operator or a new grading call reruns the check, rather than the grading rerunning it inline as `RoleGrader.java:179-181` does today — settle against `model-work/01:364` (verify-at-outline; see open questions).
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: RoleGrader.java:179-181 reruns the check inline (instrumentCheck.run then judgeChecks.write); spec model-work/01:364 and :367 say a no-verdict check "refuses it until the check is run again". Note JudgeCheck.Verdict (JudgeCheck.java:204-236) is only passes+reasons, no failed-vs-no-verdict distinction yet.
- Verify-first clause: the refusal reason code and the detail text are contracts; the outline records the choice for the no-verdict case in the ADR.
  - verdict: unverifiable | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: Future-tense statement about what the outline will record in the ADR; nothing to read. (The code judge_unfit is fixed by spec model-work/01:364; no detail text is specified.)

## Expected Surface

- OBSERVED: `pm-core/src/main/resources/de/planmarshall/core/role/qualification/qualification-record.schema.json`
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/qualification/GradingOutcome.java`, `pm-core/src/main/java/de/planmarshall/core/role/qualification/QualificationRecord.java`, `pm-core/src/main/java/de/planmarshall/core/role/qualification/JudgeInstrumentCheck.java`, `pm-workflow/src/main/java/de/planmarshall/workflow/model/RoleGrader.java`
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/role/qualification/QualificationRecordCodec.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/worker/QualificationService.java`
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/role/qualification/QualificationRecordCodecTest.java`, `pm-core/src/test/java/de/planmarshall/core/role/qualification/QualificationRecordSchemaParityTest.java`, `pm-core/src/test/java/de/planmarshall/core/role/qualification/QualificationValueTest.java`, `pm-core/src/test/java/de/planmarshall/core/role/qualification/JudgeCheckTest.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/model/RoleGraderTest.java`
- OBSERVED: `pm-core/doc/developer/core-role.adoc`, `pm-workflow/doc/developer/workflow-model.adoc`, `doc/Traceability.adoc`

Not surface (R19): `doc/plans/README.adoc`, `doc/adr/README.adoc`

## Dependencies and Sequencing

- Depends on: none. Independent of the other plans of the cut (no shared file).
- Overlaps with: PLAN-V2-126 (tests of runtime and providers) only if the outline edits `QualificationService` tests; PLAN-V2-162 none.
- Adjacent to: the conformance harness (`judge_unchecked` stays its own refusal).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-194-judge-check-refuses-grading.md
```

## Write-Boundary

The plan touches only its own repository source and tests; it creates and edits NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message (`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary).

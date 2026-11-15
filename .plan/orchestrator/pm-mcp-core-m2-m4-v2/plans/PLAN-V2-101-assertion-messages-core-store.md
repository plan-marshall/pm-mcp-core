# PLAN-V2-101: A message on every assertion of the pm-core store tests

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-101-assertion-messages-core-store.md` and is queued as one row file, `queue/PLAN-V2-101.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.
>
> Origin: the skills conformance audit of 2026-10-30 (`skills-audit-2026-10-30.md` in the epic directory), taken at `origin/main` `6847c748`, and the operator's decisions of the same day (Decisions in `epic.md`). The plan family is PLAN-V2-83 to PLAN-V2-108 (workstream WS-11, with PLAN-V2-95 and PLAN-V2-96 in WS-05); it runs first, except PLAN-V2-96, which follows PLAN-V2-31.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r4`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R06, R08, R11, R17, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R05, R07, R09, R10, R12 to R16: test-only.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: none; ADR directory: none; layout: workstream WS-11.
- R12 — none.

**Read the skills first.** Before the outline, read `SKILL.md` and the standards it points to of every skill R20 names that this plan uses: `plan-marshall:../marketplace/bundles/pm-dev-java-cui/skills/{cui-logging,cui-testing,cui-http-testing}` and `../marketplace/bundles/pm-dev-java/skills/{java-core,java-lombok,java-null-safety,javadoc,junit-core,junit-integration}` (the plan-marshall checkout beside this repository: `/home/oliver/git/plan-marshall/marketplace/bundles/`). Say in the first progress note which you read. Where two skills disagree, the ADR of PLAN-V2-83 decides; where it does not, stop and report through the inbox message; do not choose.

## Objective

The `junit-core` skill wants a message of 20 to 60 characters on every assertion; about 91 % of the roughly 13,000 assertions in the repository have none. The operator decided on 2026-10-30 (decision 10) that every assertion everywhere gets one. This plan covers the test classes of `pm-core/src/test/java/de/planmarshall/core/store/` (the audit counted about 2000 assertions without a message; at the head of 2026-11-15 only 16 have none and 29 literal messages are out of range, see the section State at the emit). It no longer runs last: PLAN-V2-125 and 114 run after it and read its tree.

## Deliverables

1. **A message on every assertion of the scope (class by class).** For each top-level `assertTrue`, `assertFalse`, `assertEquals`, `assertNotEquals`, `assertNull`, `assertNotNull`, `assertSame`, `assertThrows`, `assertInstanceOf`, `assertDoesNotThrow` and `assertArrayEquals` without a message, add a message of 20 to 60 characters that says what is checked in the language of the domain (not 'should be true'). The message goes in the position the JUnit signature gives it; inside `assertAll` each lambda's assertion carries its own. Assertions of `LogAsserts` already name the record and are left.
   - Skills: pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
2. **A guard against regression.** A source-scanning test in `pm-core` (the plan of the family that lands first creates it; the others extend it) fails when a top-level assertion of the scanned test packages has no message; the packages of this plan are added to its list. The scan is by source text, the same way as the `@NullMarked` guard of PLAN-V2-84.
   - Skills: pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-dev-java:java-core
   - Persona: implementer

**Done when:** the scanner test passes for the packages of the scope and fails on a fixture assertion without a message; the pull request body gives the number of messages added per module; the test method count is the same. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and, for `pm-core` and `pm-runtime`, `verify -Pintegration-tests -pl <module> -am` are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main`.

**Trace starting set:** `junit-core` (SKILL.md and standards: assertion messages); R20; decision 10 in `epic.md`; the audit `skills-audit-2026-10-30.md` § 2.7.

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **Test code only;** no assertion is changed except by adding the message argument (and reordering arguments where JUnit needs it).
- **Messages say what is checked, not that it failed:** 'the refusal names the store file', not 'assertion failed'.
- **Mechanical, so conflict-prone:** the plan starts from the head of `main` after its dependencies and merges the base before its gate.
- **Read the landing analyses of PLAN-V2-85 and PLAN-V2-86 at the outline** (`landings/PLAN-V2-85.md`, Wa68; `landings/PLAN-V2-86.md`, Wa72): PLAN-V2-86 already gave every assertion of its 58 second-half classes a message of 20 to 60 characters (a scanner found 0 missing or out of range in 1406), so this plan's scope shrinks to what the section State at the emit lists (the residue of PLAN-V2-85 at its shifted lines, and the classes added since); count again at the head of `main` before editing and drop what is already met.
- **One pull request:** the surface is 16 test files and the scanner (about 18 files), far below the 100-file cap of the review bots; no split.

## Claim Labels

- OBSERVED: assertions without a trailing message — core 4352, workflow 2587, runtime 4377, providers 477 (parsed, `skills-audit-2026-10-30.md` § 2.7)
- HYPOTHESIS (refuted at the emit): a plain regex is NOT enough: a tokenizer is needed (strings, text blocks, comments, generics, lambdas, overloads); see the section State at the emit
- Verify-first clause: before editing, list the open pull requests for one that touches the packages of the Expected Surface and re-run the counting commands of the claims above at the head of `main` (a plan of the family may have landed since the audit); keep only what is still open; a refutation loops back to re-scope.

## Expected Surface

- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/store/{AuditLogTest,LeaseStoreTest,LessonRevisionTest,DigestSidecarTest,JournalRecoveryTest,JsonlStoreFileTest,LessonRetirementTest,LinkedWorktreeConfinementTest,LockOrderTest,DerivedFactsCodecTest,LessonResolverTest,BuildTimingsStoreTest,DerivedFactsSchemaParityTest,LeaseCodecTest,LessonClaimTest,LessonStoreTest}.java` — messages added or corrected at the lines of the section State at the emit (Deliverable 1); shared with PLAN-V2-114 (seven of them, its D5) and PLAN-V2-125 (five of them; it runs after this plan)
- HYPOTHESIS: the assertion-message scanner test in `pm-core/src/test/java/de/planmarshall/core/guard/` (beside `NullMarkedPackagesTest`) or `core/testing/` (beside `HandWrittenDoubleScanTest`), with its list of scanned packages and a planted fixture without a message (Deliverable 2)
- Read, not edited: `NullMarkedPackagesTest`, `HandWrittenDoubleScanTest`, `doc/adr/026-the-kept-hand-written-test-doubles-are-a-closed-list.adoc`

Not surface (R19): `doc/plans/README.adoc`, `doc/Traceability.adoc`, `doc/LogMessages.adoc`, the ADR directory and developer guide of an existing package as additive bookkeeping.

## Dependencies and Sequencing

- Depends on: PLAN-V2-83 (the dependencies and rule R20) and PLAN-V2-85, PLAN-V2-86, PLAN-V2-97 and PLAN-V2-106 (they rewrite the same files).
- Creates the scanner test of Deliverable 2; PLAN-V2-102 to 105 extend it.

## State at the emit

**State at the emit (2026-11-15, `origin/main` `87e14af3`; re-grounded by a read-only leaf, the offenders re-read at `LockOrderTest:95-98`, `DigestSidecarTest:64-67`, `AuditLogTest:278`), binding over any contradicting older text of this spec:** The roughly 2000 assertions without a message of the audit are gone. The 158 classes of `pm-core/src/test/java/de/planmarshall/core/store/` carry 3832 messages of 20 to 60 characters; 16 assertions have no message, 19 literal messages are shorter than 20 characters, 10 are longer than 60, and 44 are dynamic (a supplier such as `refusal::getMessage`, the exception message, a concatenation) and cannot be measured. 16 files hold the 45 measurable offenders: AuditLogTest 7 (`:278,542,555,630,646` short, `:482,600` long), LeaseStoreTest 6 (`:567,570,573` none, `:108,109,519` short), LessonRevisionTest 5 (`:265,280,299` none, `:266,281` long), DigestSidecarTest 3 (`:64,66,67` none), JournalRecoveryTest 3 (`:503,819` short, `:864` long), JsonlStoreFileTest 3 (`:140,401,402` short), LessonRetirementTest 3 (`:359,377` none, `:341` long), LinkedWorktreeConfinementTest 3 (`:435,456,494` long), LockOrderTest 3 (`:95,97,98` none, in the helper `assertViolation`), DerivedFactsCodecTest 2 (`:116,166` short), LessonResolverTest 2 (`:496` short, `:480` long), BuildTimingsStoreTest 1 (`:138` short), DerivedFactsSchemaParityTest 1 (`:138` short), LeaseCodecTest 1 (`:87` none, the helper `refusalOf`), LessonClaimTest 1 (`:462` none), LessonStoreTest 1 (`:184` short). The statement that the scope shrinks to the first half is stale: the offenders include classes added after the audit (the `Lesson*`, `Derived*`, parity and builder tests). PLAN-V2-137 (#432) changed nothing in pm-core and nothing in `core/store`; 85, 86, 97, 106 and 136 are shipped, so every dependency is met. No assertion-message scanner exists yet and this plan creates it, following `NullMarkedPackagesTest` (source walk plus `@TempDir` fixtures) and `HandWrittenDoubleScanTest` (a pure `scan(path, source)` over planted sources): it needs a tokenizer, not a plain regex, that tolerates dynamic messages (count them present), generics, lambdas, text blocks, comments and string literals, `assertEquals(a, b, delta)`, `assertAll("heading", ...)`, `fail()` against `fail(msg)`, qualified `Assertions.assertX`, and helper methods named `assertX` of the tests themselves (scan the JUnit names only); the scanner scans its own planted fixtures as strings. The Done-when says whether the 20 to 60 range is checked or only presence: decide at the outline, recommendation presence plus the range for literal messages (the project rule is the range), and say so in the pull request body. The helpers whose assertions have no message (`assertViolation` of `LockOrderTest`, `refusalOf` of `LeaseCodecTest`, the `assertInstanceOf` casts of `LessonClaimTest`, `LessonRetirementTest`, `LessonRevisionTest`) need a message too. Outside this plan (for 102 to 105): the messageless assertions of the other pm-core packages (config 244, role/corpus 272, queue 210, role/qualification 173, service 157, profile 147, role 129, spi 108, analysis 104, findings 86, spi/validation 81, toon 30, build 8), pm-workflow (2081) and pm-runtime (1599) by the scan of that leaf (directional; the counting method differs from the audit). Shared files: PLAN-V2-114 (AuditLogTest, JsonlStoreFileTest, LessonStoreTest, LessonResolverTest, LessonRetirementTest, LessonClaimTest, LessonRevisionTest; no declared order, the later one merges the base first) and PLAN-V2-125 (AuditLogTest, BuildTimingsStoreTest, LeaseCodecTest, LessonResolverTest, LinkedWorktreeConfinementTest and a sweep of the directory; it runs after this plan); none with PLAN-V2-143 (running), 63, 94, 130, 59 or 13. No ADR is owed.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-101-assertion-messages-core-store.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.


## Sequencing note (cleanup 2026-11-10, PLAN-V2-106 landing)

PLAN-V2-136 `easymock-for-fakes-residue` is staged (the weak keeps of PLAN-V2-106, `landings/PLAN-V2-106.md`). Shared surface: it owns the `core.store` test directory, which 136 converts (`RecordingAuditSink` and its 21 users): this plan takes 136 in place of 106 as its predecessor. The dependency is the oracle: emit this plan only in that order.

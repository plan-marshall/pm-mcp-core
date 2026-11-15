# PLAN-V2-102: A message on every assertion of the remaining pm-core tests

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-102-assertion-messages-core-rest.md` and is queued as one row file, `queue/PLAN-V2-102.json`,
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

The `junit-core` skill wants a message of 20 to 60 characters on every assertion; about 91 % of the roughly 13,000 assertions in the repository have none. The operator decided on 2026-10-30 (decision 10) that every assertion everywhere gets one. This plan covers the test classes of `pm-core` outside the store (about 2300 assertions without a message, from the audit). It runs last in the wave: the other plans rewrite the same test files.

## Deliverables

1. **A message on every assertion of the scope (class by class).** For each top-level `assertTrue`, `assertFalse`, `assertEquals`, `assertNotEquals`, `assertNull`, `assertNotNull`, `assertSame`, `assertThrows`, `assertInstanceOf`, `assertDoesNotThrow` and `assertArrayEquals` without a message, add a message of 20 to 60 characters that says what is checked in the language of the domain (not 'should be true'). The message goes in the position the JUnit signature gives it; inside `assertAll` each lambda's assertion carries its own. Assertions of `LogAsserts` already name the record and are left.
   - Skills: pm-dev-java:java-core, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-dev-java:java-null-safety, pm-dev-java:javadoc
   - Persona: implementer
2. **A guard against regression.** A source-scanning test in `pm-core` (the plan of the family that lands first creates it; the others extend it) fails when a top-level assertion of the scanned test packages has no message; the packages of this plan are added to its list. The scan is by source text, the same way as the `@NullMarked` guard of PLAN-V2-84.
   - Skills: pm-dev-java:java-core, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-dev-java:java-null-safety
   - Persona: implementer
3. **Harden the scanner while its first large package set is added (carried from the landing of PLAN-V2-101, `landings/PLAN-V2-101.md` Layer 3).** In `AssertionMessageScanTest`: (a) track angle brackets in `argumentSpans` (`:276-298`) so that `assertEquals(Map.of(), new HashMap<String, String>())` and `assertTrue(Map.<String, String>of().isEmpty())` are measured, or state the limit in the Javadoc and make the scan refuse such a call; (b) read a tolerance as the third argument of an assertion on floating-point operands rather than only a numeric literal (`assertEquals(1.0, v, DELTA)` currently passes with `DELTA` as the message, `:242-244`); (c) add `fail`, `assertNotSame`, `assertIterableEquals`, `assertLinesMatch`, `assertTimeout`, `assertTimeoutPreemptively` and `assertThrowsExactly` to the known names (`ASSERTIONS`, `ONE_ARGUMENT_ASSERTIONS`); (d) print the offenders, or the first fifty with the count, in the failure message instead of only the path of `target/assertion-message-violations.txt` (`:94-95`): with about 1 750 offenders per run the CI log must show them; (e) controls at the boundaries (a 20-character and a 60-character message accepted, a 19-character and a 61-character refused) and one planted control each for a text-block message, a type-argument comma, a multi-line call and a qualified `Assertions.assertX`; (f) the two offenders of `core/guard` (`ProviderNeutralityArchTest:181`, no message; `RoleNameSingleDefinitionTest:152`, 62 characters) go into the scanned set. The class is 402 lines against the 400-line budget of `junit-core`: keep it at or under 400 or record the reason in its Javadoc. The 44 dynamic messages of `core/store` (34 of them `refusal::getMessage`-style) stay unmeasured by design.
   - Skills: pm-dev-java:java-core, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-dev-java:java-null-safety
   - Persona: implementer

**Done when:** the scanner test passes for the packages of the scope and fails on a fixture assertion without a message, with the hardening and the controls of Deliverable 3 in place; the pull request body gives the number of messages added per module; the test method count is the same. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and, for `pm-core` and `pm-runtime`, `verify -Pintegration-tests -pl <module> -am` are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main`.

**Trace starting set:** `junit-core` (SKILL.md and standards: assertion messages); R20; decision 10 in `epic.md`; the audit `skills-audit-2026-10-30.md` § 2.7.

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **Test code only;** no assertion is changed except by adding the message argument (and reordering arguments where JUnit needs it).
- **Messages say what is checked, not that it failed:** 'the refusal names the store file', not 'assertion failed'.
- **Mechanical, so conflict-prone:** the plan starts from the head of `main` after its dependencies and merges the base before its gate.
- **Read the landing analysis of PLAN-V2-87 at the outline** (`landings/PLAN-V2-87.md`, owner-register Wa70): about 1830 of 2218 assertions in the scope carry no message; the five contract tests added by PLAN-V2-87 carry messages of 72 to 80 characters (`JsonNodeContractTest:45`, `LoadedProfileContractTest`, `ProfileSetsContractTest`, `RoleSetsContractTest:60`) and 18 literal messages sit outside 20 to 60 (`FindingSchemaParityTest:228`, `SchemaSubsetTest:189-190`, `ProfileLoaderTest:300`); count again at the head of `main`, the numbers are approximate.
- **No more than 100 files per pull request** (the watch of the epic ledger; recurred for PLAN-V2-87, 97, 98, 99). CodeRabbit refuses a larger pull request ("Too many files"): cut the plan into pull requests by deliverable or by module before the first one is opened, as PLAN-V2-84, 87 and 125 were; each pull request is complete (a green build) on its own.

## Claim Labels

- OBSERVED: assertions without a trailing message — core 4352, workflow 2587, runtime 4377, providers 477 (parsed, `skills-audit-2026-10-30.md` § 2.7)
- HYPOTHESIS: the scanner can be a plain regex over `src/test/java` because the assertions are top-level calls; nested lambdas are handled by the same parser as the audit's
- Verify-first clause: before editing, list the open pull requests for one that touches the packages of the Expected Surface and re-run the counting commands of the claims above at the head of `main` (a plan of the family may have landed since the audit); keep only what is still open; a refutation loops back to re-scope.

## Expected Surface

- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/role/` (directory; R19 replaced)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/role/corpus/` (directory; R19 replaced)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/role/qualification/` (directory; R19 replaced)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/queue/` (directory; R19 replaced)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/profile/` (directory; R19 replaced)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/config/` (directory; R19 replaced)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/spi/` (directory; R19 replaced)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/spi/validation/` (directory; R19 replaced)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/analysis/` (directory; R19 replaced)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/findings/` (directory; R19 replaced)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/service/` (directory; R19 replaced)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/toon/` (directory; R19 replaced)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/guard/` (directory; R19 replaced)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/log/` (directory; R19 replaced)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/build/` (directory; four files, added 2026-11-15)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/guard/AssertionMessageScanTest.java` — the package list and the hardening (D2, D3)

Not surface (R19): `doc/plans/README.adoc`, `doc/Traceability.adoc`, `doc/LogMessages.adoc`, the ADR directory and developer guide of an existing package as additive bookkeeping.

## Dependencies and Sequencing

- Depends on: PLAN-V2-83 (the dependencies and rule R20) and PLAN-V2-87, PLAN-V2-97, PLAN-V2-106 and PLAN-V2-101 (the scanner test).

## State at the emit

**State at the emit (2026-11-15, `origin/main` `2813564b`; measured by a read-only leaf with a port of the scanner that reproduced the 45 offenders of PLAN-V2-101's census exactly before #438 and 0 after, so the counts are good to a few; re-measure at the outline with the real scanner once the packages are in its list), binding over any contradicting older text of this spec:** PLAN-V2-101 has landed (#438, `2813564b`): the scanner exists, `pm-core/src/test/java/de/planmarshall/core/guard/AssertionMessageScanTest.java`, scanning `SCANNED_PACKAGES = List.of("de/planmarshall/core/store")` (the walk is recursive); Deliverable 2 is therefore "add the packages of this plan to that list" and nothing else; the scanner fails on a literal message outside 20 to 60 characters as well as on a missing one. **Counts at `2813564b`** (messageless assertions, files with a violation): `analysis` 104 / 6; `config` 244 / 8; `findings` 86 / 6; `guard` 1 / 1; `log` 3 / 1; `profile` 146 / 14; `queue` 210 / 12; `role` 129 / 10; `role/corpus` 272 / 15; `role/qualification` 173 / 8; `service` 157 / 4; `spi` 108 / 5; `spi/validation` 81 / 6; `toon` 30 / 2; **`build` 8 / 4, which the Expected Surface lacks and this plan adds (`UnparsedEvidenceTest` 7, `ParsedBuildTest` 1, plus the short messages of `TypeScriptOutputParserTest:67` and `IstanbulSummaryParserTest:60`)**; total about 1 750 messageless in 103 files, plus about 24 literal messages outside 20 to 60 characters (`profile` 9, `role/corpus` 5, `queue` 2, `role` 3, `build` 2, `findings` 1, `guard` 1 (`RoleNameSingleDefinitionTest:152`), `toon` 1). The older figures of this spec ("about 2300", "1830 of 2218", "18 literal messages", `FindingSchemaParityTest:228`, `SchemaSubsetTest:189-190`, `ProfileLoaderTest:300`) are superseded. `core/id`, `core/testing`, `guard/fixtures` and the top level are clean. **The 100-file limit is hit with 103 files and the scanner:** cut before the first pull request. PR A: the scanner hardening (Deliverable 3) and the packages `queue`, `profile`, `config`, `spi`, `spi/validation`, `analysis`, `findings`, `service`, `toon`, `guard`, `log`, `build` (about 70 files); PR B: `role`, `role/corpus`, `role/qualification` (33 files). Each is green on its own; the scanner list grows with each. **Overlaps:** open ad-hoc PR #418 edits `core/profile/HarnessListTest` and `HarnessVerificationTest` (about 26 and 22 assertion calls): list the open pull requests at the outline and merge the base; PLAN-V2-59 edits five `core/queue` tests this plan rewrites (`TaskQueueSchemaParityTest` 26 messageless, `ActivityEntryTest` 4, `ActivityEntryValidationTest` 20, `ActivityJournalMaskingTest` 10, `SecretRedactorTest` 22) and PLAN-V2-151 edits `SecretRedactorTest`: whichever lands second merges the base; PLAN-V2-67 (running together with this plan) may touch `core/spi/PrimitiveResultTest` and `PrimitiveRegistryTest`: merge the base before the gate. PLAN-V2-94 (pull request #437) touches no `pm-core` test file.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-102-assertion-messages-core-rest.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

# Landing Analysis: PLAN-V2-88 — pm-workflow tests: generated test data

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #291 (merge commit `461a94b9`, `461a94b9167b59a9c6259f98f7f629d4b11a5770`; 91 files, +1037/-481, 75 test classes touched)

> Landing record for one shipped plan. Lives at `landings/PLAN-V2-88.md`. A pasted claim is a lead, never a fact.

Source: inbox message `rb-v2-88-workflow-tests-001.md` (runbook lane, harness opencode, model `deepseek-v4.1-flash/high`, effort
high, executor 0.1.2041). Corroborated: `ci pr view 291` `merged`, merge sha as above; `ci checks status`: `overall_status:
success`. Lane-stated Sonar read: 0 new-code issues at the merge head (after hoisting three `java:S5778` calls); Sonar `main`
(analysis of `11ef1250`): gate OK, 0 open BUGs.

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|---|---|---|
| D1 convert the 94 workflow test classes to generated data | **partly** | generator calls 19 to 343, controller classes 8 to 54, but string literals 10551 to 10169 (-3.6 %); 36 classes rely on an unchecked "every datum is a contract" statement; 0 `@GeneratorsSource` |
| D2 display names and LogRecord assertions | shipped | every class has `@DisplayName`; all 5 records asserted by constant (`PlanInstanceStoreTest:188,360`, `DefaultUnitLoaderTest:85`, `ProjectUnitLoaderTest:108,132`) |

Surface delta: none outside pm-workflow test sources. Test count unchanged (970 annotations before and after; lane's 6513/6581 not
re-run).

## Metrics and Anomalies

- Tokens / plugin_version `unknown` (open defect). CodeRabbit (required) reviewed four heads, every finding fixed; Sourcery refused
  (150 000-character diff limit, optional). No operator question.
- The PR table's per-class call counts are off (`AstNodeModelTest` 81 measured, 112 claimed; `QuestionBoundTest` 6, claimed 4).
  The lane's "four fixture helpers without a statement fixed" is wrong: `GraderFixtures` still has no comment and no controller
  although it calls `Generators`.

## Three-Layer Analysis (standing rule R21, r6)

Done at `461a94b9`/`11ef1250` by a read-only sub-agent; points marked **checked** re-read by the orchestrator. Nothing was built.

### Layer 1 — Specified
- **S1 (medium).** The scope count is inconsistent: Objective "all 94 test classes", Claim Labels "3 of 71", tree 75 `*Test.java`
  and 101 java files. "About 5000/5184 literals" has no counting command.
- **S2 (medium, checked).** The exemption makes Done-when nearly unfalsifiable: a class passes with a one-line statement that every
  datum is a contract, and nothing checks it. `TaskProtocolOfferTest.java:35` states "Every datum in this class is a contract:
  queue ids in the wire format ..." while `"project-a"` appears on 7 lines of that file (41 times across `src/test`).
- **S3 (low).** `@GeneratorsSource`, the 20-60 character message rule and "equivalent assertions" are named in the Objective but not
  measurable in Done-when; `testing/generators` is not in the 17 packages.

### Layer 2 — Implemented
- **I1 (medium, checked).** Literals barely moved; 14 of the 54 controller classes call `Generators` once or twice; no file was added
  (only modifications), `letterStrings(1, 8).next()` appears 113 times inline instead of a generator in the artifact.
- **I2 (medium, checked).** `@GeneratorsSource`: 0 uses (`git grep -c` over `pm-workflow/src/test`); 145 `@ParameterizedTest` remain.
- **I3 (medium).** Assertion-message rule essentially unmet: of 2773 assertion calls about 2750 carry no message; 4 of 117 added
  assertion lines end in a message literal; `verify/AdditionalRulesTest.java:367` message `"no caller"` is 9 characters (checked);
  `PlanInstanceStoreTest.java:184` has none. The lane's "assertion-message rule (fixed, 08213238)" reached only lines CodeRabbit named.
- **I4 (low, checked).** One `new Random(seed)` remains, `task/TaskProtocolInterleavingTest.java:87`, a deterministic seeded driver
  (`@ValueSource` seeds), correctly stated by the lane. 32 generated constants are initialised at class load; whether seed
  reporting covers them is not shown by reading.

### Layer 3 — Correct
- **C1 (medium, checked).** Ordering tests lost their discriminating power: `AstNodeModelTest.statesKeepOrder` (`:338-339`) used the
  deliberately unsorted `List.of("zeta", "alpha", "mu", "beta")` (diff shows the removed literals) and now `List.of(text(), text(),
  text(), text())`; `entryBoundStates` and `entryBindingsAreCopied` changed the same way. A production change that sorts keys
  would pass by chance. `bindingsKeepOrder` kept `z, a, m` "deliberately unsorted", so the conversion is inconsistent.
- **C2 (low).** Round-trip texts lost punctuation: `WaiverDeclineTest` ("the runner is flaky") and `AnswersTest` ("the operator's
  note") now `letterStrings`, letters only, no spaces, quotes or escapes, in a status-document round trip.
- **C3 (low).** Residual flake surface, not shown to bite: `TaskProtocolReOfferTest` `MEASUREMENT`/`THRESHOLD` independent 1-10 draws
  (safe today); collision guards elsewhere are done well (`PlanInstanceStoreTest:219`, `OperatorWaitTest`, `AnswersTest`, `LOW`/`HIGH`
  ranges). `AnswersTest.idempotentDelivery` was strengthened.
- No test among roughly 20 sampled classes passes whatever production does.
- CLAUDE.md conformance: partly. No Mockito/Hamcrest/PowerMock, no AAA comments, `@DisplayName` everywhere, LogRecords by constant;
  not met: assertion messages, `@GeneratorsSource`, generators in the artifact.

Owner: PLAN-V2-103 (assertion messages, workflow and providers) owns I3; the ordering/punctuation restorations (C1, C2) and the
unchecked-contract statements (S2, I1, I2) have no owner yet — operator decision (a second conversion pass, or accept).

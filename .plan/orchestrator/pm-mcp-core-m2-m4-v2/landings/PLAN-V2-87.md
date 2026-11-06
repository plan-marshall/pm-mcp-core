# Landing Analysis: PLAN-V2-87 — Generated test data in the remaining pm-core test packages

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #294 (part 1, merge commit `5c133ad3`; 46 files, +861/-349) and #295 (part 2, merge commit `e5d5a117`; 71 files); the original #290 (117 files) was closed and split because CodeRabbit refuses to review more than 100 files

> Landing record for one shipped plan. Lives at `landings/PLAN-V2-87.md`. A pasted claim is a lead, never a fact.

Source: inbox message `rb-v2-87-core-tests-rest-001.md` (runbook lane, harness opencode, model `deepseek-v4.1-flash/high`, effort high,
executor 0.1.2041; **self-review `budget-exhausted`**; Sonar 0 open on both PRs). Tokens and plugin version `unknown` (open defect).
Corroborated: `ci pr view` of both PRs `merged`; `ci checks status` of #295 `overall_status: success`; Sonar `main` (analysis of `e5d5a117`,
2026-11-06 04:25 UTC) gate OK, 0 open BUGs; `Maven Build` and `Integration Tests` push runs of `e5d5a117` green. The queue row carried no PR
and no landing before this record (set to both PRs below). The operator's free-text "retry" in the message answered a network outage only.

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|---|---|---|
| D1 convert the remaining core test packages | **partly** | literals 9401 -> 9006 (-4.2 %), generator calls 7 -> 563, controller classes 3 -> 74 in the scope (sub-agent's count; the landing says 103, not reproduced; 136 over the whole `pm-core` test tree); 0 `@GeneratorsSource`; false "contract" statements below |
| D2 contract tests for hand-written `equals` | met in form, weak | five tests implement `ShouldHandleObjectContracts` (I3) |
| D3 AAA phase comments removed | met | `SchemaSubsetTest` 14 -> 0, 18 test methods before and after; no test annotation lost (932 -> 940) |
| D4 log records asserted by constant | met | `CoreLogMessagesTest:105-114`, `AuditLogTest:494`, `EnrolmentStoreTest:931-937`, `CorpusImportTest:528` |

## Three-Layer Analysis (standing rule R21, r6)

Done at `e5d5a117` by a read-only sub-agent (counting and reading on the merged tree, `javap` of the library; no build), then the points
marked **checked** re-read by the orchestrator. Nothing in `src/main` changed in the plan's 117 files (sub-agent: all under `src/test`).

### Layer 1 — Specified
- **S1 (medium).** The baseline contradicts the audit: Objective "about 4000 string literals", audit 8140, the scope counts 9401. Same defect as
  PLAN-V2-85 S1; the Done-when sets no literal-reduction threshold.
- **S2 (medium).** Done-when is satisfied by form: a controller plus one `Generators` call, or a one-line statement that nothing checks. Of 133 scope files:
  74 controller classes with calls, 7 fixture helpers calling `Generators` without a controller, about 35 with a statement (worded differently), 17 fixtures with neither.
- **S3 (medium).** The Expected Surface omits `core/build` (20 test files, 8 with neither controller nor statement, e.g. `MavenOutputParserTest`, `GradleOutputParserTest`,
  `PortedExpectations`) and `testing/generators`. **Checked:** 12 of the 20 `build` test files carry the controller or a statement; no staged plan names the package
  (only PLAN-V2-18 mentions it): nobody owns its conversion.
- **S4 (low).** The spec says "the pull request body"; the work shipped as two PRs. The ledger row has one `pr` string (now `#294, #295`).

### Layer 2 — Implemented
- **I1 (medium, partly checked).** "Every datum is a contract" is false or partial in sampled classes: `PrimitiveRegistryTest:36` covers arbitrary ids such as
  `test.complete` (checked: the statement names "fixture ids" as contract), `HarnessProfileTest:32` (checked: statement present; its `acme-cli`/`acme-model` names are
  not wire values, sub-agent), `ConventionalCommitValidatorTest:24` free text, `OutlineCoverageTest:29`, `CorpusValidatorTest:59` (schema mixed with titles); true for
  `SourceFetchTest:27` and `Sha256Test`. Unlabelled non-contract literals stay in generating classes (`"project-a"` 7 times in `ActivityJournalTest`, 8 in `QueueStoreTest`).
  The PR tables' literals-kept column is "~N" estimates.
- **I2 (medium).** `@GeneratorsSource` 0 uses over 141 parameterized tests (62 `@MethodSource`, 44 `@ValueSource`, 19 `@CsvSource`, 16 `@EnumSource`); the lists read are mostly
  boundary or wire values, so the lane's "contract tables" claim is largely true; some numeric boundaries could be a fixed value plus a generated in-range one.
- **I3 (medium, partly checked).** The contract tests inherit the library arm that runs the basic contract on one instance (sub-agent, `javap`): it does not prove value
  equality. **Checked:** `RoleSets.ShippedRole.equals` is `Arrays.equals(document, role.document)` (`RoleSets.java:70`); a regression to `==` would pass the single-instance
  check, and the sub-agent found no other test of `ShippedRole`/`ShippedProfile` value equality (`JsonNode` has only indirect cover). The `shouldImplementSerializable`
  override asserts "not Serializable" (sound as a design statement, replaces the library arm). **Checked:** the messages at `JsonNodeContractTest:45` (72 chars) and
  `RoleSetsContractTest:60` (about 80) break the 20 to 60 rule in the plan's own new files; the sub-agent counts four of the five tests so.
- **I4 (medium).** Assertion messages: of about 2218 assertion calls in scope about 1832 carry none (approximate, by script); 18 literal messages outside 20-60, mostly pre-existing
  (`FindingSchemaParityTest:228` = 17, `SchemaSubsetTest:189-190` = 7-8, `ProfileLoaderTest:300` = 62 new).
- **I5 (medium, partly checked).** **Checked:** 87 single-line `static final ... = Generators.` fields in the test tree (sub-agent: 226 in the non-store scope including multi-line, 0 at the
  baseline); they run at class load, outside the per-test seed, so a failure from such a value cannot be replayed from the reported seed; `@GeneratorSeed` 0 uses. The landing's
  residue understates this.
- No `Random`, `UUID.randomUUID`, Mockito, Hamcrest or `Dispatcher` in scope; `FakeRepository` and `FakeLfs` (`CorpusImportTest:965,1001`) are pre-existing hand-written fakes;
  `CorpusImportTest:530` hand-builds a log template instead of using the record.

### Layer 3 — Correct
- **C1 held.** Order-pinning tests read are still discriminating (`CodeAnalysisProviderRegistryTest:39-41,82` unsorted `r, p, q`; `RoleTopicsTest:86`); the rest of the "order"-named tests was not read.
- **C2 (low).** Free-text round trips lost punctuation, spaces and escapes: `ActivityEntryCodecTest.everyKind` (`"ls -la"`, `"hello"` became letter strings, the `ue` was kept), `FindingTest` text is letters only,
  `AuditEventTest.TRANSACTION_ID` was a UUIDv7-shaped literal and is now `letterStrings(8,20)`, so the wire shape is no longer exercised (no production validation rejects it).
- **C3 (low).** `FindingTest:213-214` draws `spanStart` and `spanEnd` independently from 0..10 (start may exceed end); harmless today (`Span` checks only both >= 0). No operand collision found in the pairs read.
- **C4/C5 (low).** No vacuous or mirror-computed expectation in about 10 diffs read in full; about 120 changed files were not diffed. `%040x`/`%064x` ids carry only 16 significant hex digits.

## Verdict

D3 and D4 met; D1 partly (a small literal reduction, a controller plus one call satisfies the Done-when, several contract statements are not true per datum); D2 met in form with a value-equality gap
for `ShippedRole`/`ShippedProfile`/`JsonNode`. No weakened order test or flake found in the diffs read. The `build` test package has no owner.

## Ownership

- PLAN-V2-102 (assertion messages, core rest): I4 and the contract-test messages (bullet added).
- PLAN-V2-106 (EasyMock for fakes): `FakeRepository`, `FakeLfs`.
- No owner: S3 (`core/build` conversion), I1 (false statements, unlabelled literals), I2, I3 (value-equality tests for `ShippedRole`, `ShippedProfile`, `JsonNode`), I5 (static fixture seeding), C2. A fix plan only on the operator's word (Wa70).

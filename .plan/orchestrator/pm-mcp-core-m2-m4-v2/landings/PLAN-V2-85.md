# Landing Analysis: PLAN-V2-85 — pm-core store tests, first half: generated test data

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #298 (merge commit `59fee682`; 55 files, +4024/-2490)

> Landing record for one shipped plan. Lives at `landings/PLAN-V2-85.md`. A pasted claim is a lead, never a fact.

Source: inbox message `rb-v2-85-core-store-tests-a-001.md` (runbook lane, harness opencode, model `deepseek-v4.1-flash/high`,
effort high, executor 0.1.2041; **self-review step `budget-exhausted`**, 5 SonarCloud issues found and fixed, then 0).
Corroborated: `ci checks status` of #298 `overall_status: success`; Sonar `main` gate OK at the time of reading.

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|---|---|---|
| D1 convert the first-half store test classes to generated data | **partly** | all 54 in-scope files touched; test and assertion counts equal per file (no test lost); but literals rose (below), `@GeneratorsSource` 0 uses, 9 classes carry the "contract" statement |
| D2 contract test for hand-written `equals` | shipped | `JsonlStoreFileContractTest` (`ShouldHandleObjectContracts<JsonlStoreFile.Line>`, Serializable contract inverted); the rest deferred to PLAN-V2-86 |

## Metrics and Anomalies

- Tokens / plugin_version `unknown` (open defect). The PR body says tests 4338 to 4342, the landing 4356: unexplained +14.
- Per-file: all string literals 4633 to 6235 (765 removed, 2369 added, mostly assertion messages); the PR reports a "quote lines"
  proxy, not the numbers the spec asked for.
- The lane's self-review ran out of budget and says so; its §9 lists the unreviewed residue (partial literals, the
  `@GeneratorsSource` question, the generator-reuse question, helper messages).

## Three-Layer Analysis (standing rule R21, r6)

Done at `59fee682` by a read-only sub-agent (it ran one read-only script from the scratchpad), then the points marked
**checked** re-read by the orchestrator. Nothing was built.

### Layer 1 — Specified
- **S1 (medium, checked).** The spec contradicts itself on the baseline: Objective (l.33) "about 4000 string literals", claim
  (l.56) "about 8140"; "89 files" is 112 before / 113 after; "0 Generators calls" is false (9 in-scope classes already generated).
  The Done-when sets no literal-reduction threshold and is satisfiable by form alone (one `Generators` call); the
  assertion-message requirement sits only in Standing Constraints.
- **S2 (low).** D2 says records are not value objects in this sense, yet `JsonlStoreFile.Line` is a record
  (`JsonlStoreFile.java:87`) and got the contract test; "any other class with a hand-written equals joins" overlaps PLAN-V2-86 D2.
- **S3 (low).** Sonar clause: PR body "not_configured, n/a" vs landing §4 (5 issues fixed, then 0).

### Layer 2 — Implemented
- **I1 (medium, checked).** The "every assertion carries 20 to 60 characters" claim is false. Out of range: `AuditLogTest:269`
  `"nothing was written"` (19), `BuildTimingsStoreTest:138` `"2t is capped at C"` (17), `LessonStoreTest:181` `"its n is 1"` (10),
  and by the sub-agent's reading `AuditLogTest:473` (70), `LeaseStoreTest:108-109`, `DerivedFactsCodecTest:116`,
  `JournalRecoveryTest:812,856`, `JsonlStoreFileTest:396`, `LessonResolverTest:477,493`, `LessonRetirementTest:338`,
  `DerivedFactsSchemaParityTest:140`. No message at all: `LockOrderTest:95-98` (`assertViolation`, checked: `assertInstanceOf` and
  two `assertEquals`), `DigestSidecarTest:64-67`, `LeaseCodecTest:87`, `LessonRetirementTest:356`, `LessonClaimTest:459`.
- **I2 (medium).** `@GeneratorsSource` 0 uses (of 74 parameterized tests, 17 `@EnumSource`; the `@ValueSource` lists read are invalid
  inputs, which supports the lane's residue claim); `JournalScopeTest:390` and `LinkedWorktreeConfinementTest:124` valid path lists
  stayed literal. Thin conversions: `AtomicStoreFileTest` (12 tests, one generated call), `AuditLogTest` (25 tests, one).
- **I3 (low).** `@EnableGeneratorController` on the non-test classes `IntentFixtures` and `LessonFixtures` has no effect.
  Two `UUID.randomUUID()` uses in `EnrolmentStoreTest` were correctly replaced.

### Layer 3 — Correct
- **PLAN-V2-88 weakness (a), order pinning: held.** `LockKeyTest:234-236` keeps `"abc"`, `"abc-d"`, `"abd"`; `LockOrderTest:182-224`
  keeps `alpha/beta`, `abc/abc-d`; `EnrolmentStoreTest` uses `zeta-`/`alpha-` prefixes; `LeaseStoreTest:409` insertion order.
- **Weakness (b), round trips: partly held.** `LessonCodecTest` and `CapabilitySpecTest` keep quotes and `&`; `IntentFixtures.valid()`
  now draws letters-only goal, problem and constraint text round-tripped by `IntentRecordTest` (no escape coverage lost, punctuation
  and spaces were).
- **Weakness (c), "every datum is a contract": false in places.** `EnrolmentCodecTest:460-463` (checked) builds
  `new ProjectEnrolment("pm-mcp-core", "/home/dev/git/pm-mcp-core", Instant.parse("2026-10-11T08:00:00Z"), ...)` from literals
  although `CoreGenerators.projectEnrolments()` exists and `EnrolmentStoreTest:93` uses it; `"pm-mcp-core"` stays as a non-contract
  name in `DerivedFactStoreTest:48`, `LessonStoreCrashTest:62`, `ChangeLedgerTest:53`, `BuildTimingsStoreTest:56,173`,
  `LeaseCodecTest:161`; `LockKeyTest:281,287` keep `project-a/b` without saying why. 9 classes carry the statement (checked).
- **C1 (low, checked).** `IntentFixtures.java:42` `AT` changed from `2026-10-27T10:00:00Z` to `2026-10-28T11:00:00Z` without a stated
  reason; no pinning test found.
- **C2 (low).** `LessonResolverTest:539` `Generators.integers(0, 5).next() + seed % 6`: the failure message carries the loop seed
  (l.520) but not the drawn value, so a failure is harder to reproduce.
- No expected value weakened in the 7 diffs read in full; equal assert counts are the only evidence for the other ~45 classes
  (not diffed line by line). Whether `CoreGenerators.leaseRecords()` always yields `leaseExpiresAt` after `orphanedAt`
  (`LeaseStoreTest:97`) is not shown by reading.
- CLAUDE.md conformance: met — JUnit 5 only, no Random/UUID/Mockito/Hamcrest/AAA comments, no new fakes, contract test present;
  violated — the 20-60 character assertion-message rule; non-contract literals not labelled; `@GeneratorsSource` not used.

Owner: PLAN-V2-86 (second half) carries the same standing constraint and now this analysis (bullet added); PLAN-V2-101 (assertion
messages, core store) owns I1; the remaining items have no owner — operator decision with the PLAN-V2-88 items (Wa66).

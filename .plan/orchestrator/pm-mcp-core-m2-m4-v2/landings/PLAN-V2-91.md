# Landing Analysis: PLAN-V2-91 — Generated test data in the pm-runtime worker, endpoint, credentials, enrolment and question tests

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #327 (squash merge commit `5f0e74be`, single parent `186a92ce`; 74 files, +1771/-575, every file below `pm-runtime/src/test/`; runbook lane, harness opencode, `deepseek-v4.1-flash/high`)

> Landing record for one shipped plan. Lives at `landings/PLAN-V2-91.md`. A pasted claim is a lead, never a fact.

Source: the plan's landing message `rb-v2-91-runtime-tests-workers-and-endpoints-001.md` (2026-11-08T21:31Z). `landing-check` `complete: false`: `total_tokens` and `plugin_version` are `unknown` (folded into the standing Open Defect on the runbook lane); `surface_delta` `unmeasured`. Reconciled in full. The landing reports `spec_differ=true` at setup (the main-checkout copy of the spec differed from the ledger copy; expected, the adapted spec reaches the main checkout only after a land) and executor skew `match`.

Corroborated by the orchestrator: `ci pr view 327` reads `merged`, merge commit `5f0e74bea569…`; `ci checks status 327` reads 20 checks, overall success (`build (25)` 584 s, `build (26)` 590 s, `sonar-build` 1180 s, Integration Tests and SonarCloud pass; CodeRabbit `NEUTRAL`, the quota refusal the landing reports; Sourcery skipped). Sonar over plain HTTP (`pullRequest=327`): 0 open issues, gate `OK`. The landing's lane Sonar step is `n/a`; its own public read of 0 agrees with mine. The diff touches no file outside `pm-runtime/src/test/` (checked: 0 files elsewhere).

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 generated data in every test class of the scope, no test removed | shipped-modified | the five scope directories hold 74 Java files at the merge (re-counted with `git ls-tree`), of which 6 are the excluded HTTP classes; 52 of the 74 carry `@EnableGeneratorController` (46 outside the excluded six). Per class, no test annotation count fell (55 classes present in both, identical). Not reproduced: the landing's "65 in-scope classes" (the leaf counts 68 Java files, some fixtures). Two classes that state "every datum is a contract" still build a fixture that draws ids with no controller (layer 2). |
| D2 `WorkerGenerationContractTest` through `ShouldHandleObjectContracts` | shipped-as-specified | the class exists with 3 tests; the OBSERVED premise (no contract test, hand-written `equals`/`hashCode`) held at the parent. Whether the library contract passes is not shown by reading. |
| D3 log assertions of the scope | shipped-as-specified | the leaf derived 31 constants logged by the main classes of the five scope packages and found all 31 referenced in test sources; the eleven the landing lists are asserted through `LogAsserts` in `@EnableTestLogger` classes. The spec's own examples of D3 (`SERVICE_UNIT_UNINSTALLED`, `HARNESS_SELF_UPDATE_ACCEPTED`, `BOUNDED_VERIFICATION_FINISHED`) are not logged by any scope package (re-read: they are logged in `launch/HarnessVerifier.java`, `start/ServiceUnitInstaller.java`, `start/StartupSequence.java` at the parent). |

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`, surface `orchestrator.analyze`) — reason: a full-ship landing is read on three layers by its own leaf, with no size qualifier; it shared no context with the leaves of PLAN-V2-120, 117 or 118. The leaf wrote nothing. By reading, no build, no test run, no write. The spec and the landing message were passed as data.
- **Re-read by the orchestrator at `git show 5f0e74be:<file>` (or the parent):** `WorkerSupervisorLaunchTest.java:36-62`, `WorkerDrainParticipantTest.java:33-40`, `SupervisorTestFixture.java:80-94`, `EndpointHarnessReadinessTest.java:30-54`, `QuestionLogRecordReaderBoundaryTest.java:62-68`, `ProviderVerificationTest.java:70-76`, `ActivityEventMapperTest.java:32-38,71-77`, `DbusConnectionTest.java:33-37,71-75`, `FileSecretStoreTest.java:64-70,97-112`, `WorkerGenerationContractTest.java:76-82`; the directory counts; the UUID, `ShouldHandleObjectContracts` and log-constant searches at the parent. **Not re-read:** `VerifyingBinaryCheckTest` (the leaf's `:53-55` helper copy), `SupervisorEnvironmentTest`, `EnrolmentFileCheckTest`, `FileSecretStoreTest` siblings (`SecretServiceStoreTest`, `KeychainSecretStoreTest`, `SecretServiceIT`), the per-class method counts, the boundary literals, the 31-constant derivation and the assertion-message sampling; they are the leaf's own reading and are recorded as such.
- **Verdict:** layer 1 findings — 4; layer 2 findings — 5; layer 3 findings — 5.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence |
|---------|-------|---------|----------|----------|
| L1-1 | OBSERVED: `WorkerGeneration` has `equals`/`hashCode` and no contract test; `UUID` suffixes at `EnrolmentStoreTest` 984 and 1131, `SecretServiceIT` 39, `KeychainSecretStoreTest` 34 | checked | refutes (in part) | confirmed for `WorkerGeneration`, `SecretServiceIT:39` and `KeychainSecretStoreTest:34`; `EnrolmentStoreTest` is a `pm-core` class and `pm-core/src/test` holds no `randomUUID` at the parent; the in-scope sites are `CredentialStoreMoveTest:164` and `EnrolmentSidecarsTest:115`. Persisted as `contradicted`, `rescoped: no`. |
| L1-2 | D3 premise: the constants logged by the scope packages, with three named examples | checked | refutes | the three examples are logged outside the scope packages; the derivation by package is sound, the examples are wrong. Not a corpus claim. |
| L1-3 | Expected Surface names six test directories | checked | refutes | the diff also adds seven files in `pm-runtime/src/test/.../testing/generators/` (three generators with tests, plus 15 lines in `RuntimeGenerators`), which the Standing Constraint required but the surface did not name. `CoreGenerators.projectEnrolments()` is not in the diff (it existed at the parent). |
| L1-4 | `spec_differ=true` at setup | not_shown_by_reading | none | the two copies cannot both be read here; the ledger copy is the adapted one, so the difference is expected |

**Layer 1 verdict:** findings — 4 (L1-1 to L1-3 refute a premise, none blocks the shipped work; L1-4 not shown).

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| D1 | the 74-file diff, all tests | the converted tests keep their boundary literals (`Q-001`/`Q-999999`, `Integer.MAX_VALUE`, the 30-second bound, one second before the deadline) per the leaf; diagnostics kept in the 7 files that use them | `WorkerSupervisorLaunchTest.java:41` and `WorkerDrainParticipantTest.java:37` state "Every datum of this test is a contract" and carry no controller, yet `setUp` builds `SupervisorTestFixture`, whose fields draw project, plan, sha and generation ids (`SupervisorTestFixture.java:84-91`, re-read): the statement is false and the draw is outside any seed report. The helper the Standing Constraint says to share (lowercased `letterStrings` word, `"plan-" +` prefix, `major.minor.patch`) is still copied inline: `QuestionLogRecordReaderBoundaryTest.java:65-66` and `ProviderVerificationTest.java:73-74` re-read; the leaf names three more classes (`EnrolmentsTest`, `EnrolmentKeyTest`, `DecoyServersTest`, `SupervisorEnvironmentTest`, `VerifyingBinaryCheckTest`, not re-read) | checked, refutes |
| D2 | `WorkerGenerationContractTest` | the contract test; the equality-ignores-state and generation-id cases | built with `"qt-" + Generators.integers(1, 99)` (`:79-80`, re-read), not the `qt-<16 hex>` grammar `RuntimeGenerators.queueTaskIds()` produces (the lane's residue N5) | checked |
| D3 | 11 new log assertions | each by constant with `@EnableTestLogger` | `EndpointHarnessReadinessTest` exercises a real `EndpointClient` GET against `127.0.0.1:<port>` (`:39-42`), an HTTP test in a plan that scopes HTTP out (the lane's N1, confirmed), and `closedPort()` frees its port before the probe (`:49-53`: the try-with-resources closes the socket on return) — the race of the lane's N2, confirmed | checked, refutes |
| landing claim | "all 65 in-scope classes carry the controller or the statement" | — | the leaf counts 68 Java files in scope (74 less the six excluded), 46 with the controller, 20 with the statement, 2 with fixture calls; the 65 is **not reproduced** (it may count test classes only). 2 of the 20 statements are false (above) and `EnrolmentFileCheckTest` keeps arbitrary names (`someone-else`, `link.json`) under the statement (leaf, not re-read) | checked, refutes |

Not shown by reading: the 28 failures after the conversion and their fixes; the green-run claims (`verify`, `-Pcoverage`, integration tests; CI is green, above); whether `ShouldHandleObjectContracts` passes on `WorkerGeneration` (including the serializable override); the timing assertions of `HarnessStartSpacingTest`; `KeychainSecretStoreTest` (macOS only) and `SecretServiceIT` (needs a session bus), which the unit run skips.

**Layer 2 verdict:** findings — 5 (two false contract statements; the helpers copied; the HTTP readiness test and its port race; the count of 65 not reproduced; the failing runs not shown).

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `ActivityEventMapperTest.java:35-36` against `:73-75`; `DbusConnectionTest.java:35-36` against `:73` | the class javadoc | says the tool names are the wire contract while `toolName`, `command` and `argumentValue` are generated; says the unique name's numeric part is generated while `:73` asserts the literal `:1.7` (re-read). The lane's "false doc claims fixed" (F6) is not true here; `SupervisorEnvironmentTest.java:35-36` likewise (leaf, not re-read) | minor |
| L3-2 | `FileSecretStoreTest.java:66-69`, asserted at `:99-105,110-112` | two independent `letterStrings(1,20)` draws for the first and the second secret, and for the global and project value | on equal draws `replace()` and the project-first resolution pass without discriminating; the leaf estimates about 1 draw in 10^4 per pair (my reading of the length range gives the same order; **not measured**); the same pattern in `SecretServiceStoreTest`, `KeychainSecretStoreTest` and `SecretServiceIT` (leaf, not re-read) | minor |
| L3-3 | `WorkerRecyclingTest.java:141` against `SupervisorTestFixture.java:128-130,158` (leaf, not re-read) | a generated `words()` worktree name that equals a fixture directory (`run`, `home`, `worktree`, `harness`) | the test reuses that directory; probability tiny | minor |
| L3-4 | `ActivityEventMapperTest.java:127-158` (leaf, not re-read) | the generated `argumentValue` and the `secret` content | placed in the input but never asserted absent, so "never the arguments" stays unasserted; pre-existing | minor |
| L3-5 | converted assertion lines in about 25 changed files (leaf, not counted) | assertions with no message at all (for example `CredentialStoreMoveTest` `assertEquals(Optional.of(githubSecret), …)`) | CLAUDE.md asks 20 to 60 characters on every assertion; the lack is the pre-existing style carried through, not introduced; the added classes' messages (12 counted) are 35 to 60 characters; the assertion-message plans PLAN-V2-101 to 105 own it | info |

Also read and noted: no production file changed; `UUID.randomUUID()`/`new Random` is gone from the five directories (`SecureRandom` instances are production constructor arguments; `TransactionId.generate` and `JobId.random` are production factories used as unasserted data); the new generators match the production grammars (`Q-\d{3,6}`, `^qt-[0-9a-f]{16}$`); `EndpointRequestTest` was strengthened (the body-contains check became an equality); no sleep, Mockito or AAA comment was added.

**Layer 3 not shown by reading:** the runs above; the flakiness of the readiness test under the port race.

**Layer 3 verdict:** findings — 5, all minor or info; none is a defect in production code (no production Java changed).

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md | generated test data, or a one-line contract statement per class | deviates | two false statements (`WorkerSupervisorLaunchTest`, `WorkerDrainParticipantTest`); `EnrolmentFileCheckTest` has non-contract literals under the statement (leaf) |
| CLAUDE.md | no `Random` or `UUID.randomUUID()` as test data | conforms | 0 hits in the five directories at the merge |
| CLAUDE.md | JUnit 5 only, no Mockito or Hamcrest | conforms | none found |
| CLAUDE.md | every `LogRecord` a class logs is asserted by its constant | conforms | 31 of 31 constants referenced (the leaf's regex; a constant reached by static import would be missed) |
| CLAUDE.md | a class with a hand-written `equals`/`hashCode` has a contract test | conforms | `WorkerGenerationContractTest`; the run is not shown |
| CLAUDE.md | every assertion carries a message of 20 to 60 characters | deviates | the new classes comply; many converted lines carry none (pre-existing, owned by PLAN-V2-101 to 105) |
| spec Standing Constraint 3 | shared helpers go to the module generator artifact, not copied per class | deviates | the lowercased word helper in 5 classes, the version helper in 2 |
| CLAUDE.md | HTTP tests use `@EnableMockWebServer` | not_shown_by_reading | `EndpointHarnessReadinessTest` does real HTTP; the spec scoped HTTP out, so PLAN-V2-93 settles which rule governs |
| Spec Done-when | `verify`, `-Ppre-commit`, `-Pcoverage` green | not_shown_by_reading | only a run shows it; CI is green (above) |

## Metrics and Anomalies

- Tokens: `unknown` (unobserved, never zero).
- Duration: the landing gives none; the landing message was filed 21:31Z; `build (25)` 584 s, `build (26)` 590 s, `sonar-build` 1180 s (`ci checks`).
- Anomalies: self-review `budget-exhausted` with open findings the lane lists (N1 to N5); two Sonar findings and a seed-sensitive one-letter worker-state name failed `sonar-build` in earlier heads and were fixed; three red local builds (two compile errors, one red test run); queue waits above 20 minutes on the machine-global build slot; CodeRabbit refused on quota on the last two heads and the PR merged on the standing authorization; Sourcery refused on size. One shell `rg` call returned "command not found" (disclosed, no workaround).

## Routing and Merge Behavior

- Review: CodeRabbit (required) reviewed `8110bd83`, six inline findings fixed in `f3754d32`, all threads resolved; rate-limited on the later heads. Sourcery refused on size (optional).
- CI/merge: success on 20 checks; merge commit `5f0e74be`.
- Sonar: the lane's step is `n/a`; the orchestrator's plain-HTTP read of `pullRequest=327` returns 0 open issues and gate `OK`.

## Reconciliation Actions

- [x] row `status` → `shipped`
- [x] row `pr` stamped `#327`
- [x] row `landing` stamped `landings/PLAN-V2-91.md`
- [x] row `plan_marshall_plan_id` stamped `v2-91-runtime-tests-workers-and-endpoints` (from the sender `rb-v2-91-…`)
- [x] layer-1 verdict persisted on the spec (claim 0 `contradicted`, `rescoped: no`)
- [x] `epic.md`: recurrence folded into the runbook-lane `unknown` Open Defect; findings recorded as one Open Defect
- [x] resume anchor, `queue-view.md`, committed in the ledger worktree

## Follow-Ups

No fix plan is staged; the operator decides. Candidates: (1) one cleanup of the five copied helpers and the two false contract statements, with the fixture's id draws under a controller (L2, L3); (2) the false javadoc of three classes (L3-1); (3) the discrimination of the paired secret draws in the four secret-store tests (L3-2); (4) the readiness test: PLAN-V2-93 decides its HTTP form and the port race. PLAN-V2-99 (next in the conformance family) is not blocked by these findings; PLAN-V2-101 to 105 own the assertion messages.

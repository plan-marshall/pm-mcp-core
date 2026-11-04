# Landing Analysis: PLAN-V2-110 — Make the null contract of the 99 `@NullMarked` files true (main green)

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #264 (`5b665f9b`, D1, 60 files), #268 (`2f942feb`, D2, 35 files), #274 (`5bed38c4`, D3 + D4, 33 files), #282 (`65ee72ac`, completion, 8 files, +20/-18); #280 (first completion attempt) closed unmerged

> Landing record for one shipped plan. Lives at `landings/PLAN-V2-110.md`. Written by the `analyze` verb after verifying
> claims against ground truth (actual code, artifacts, PR state); a pasted claim is a lead, never a fact.

Source: inbox messages `rb-v2-110-null-contract-main-green-001.md` (partial, observed 2026-11-02) and `-002.md` (final, which
declares `-001`'s `merge_state=open` stale). Runbook lane, harness opencode, model `deepseek-v4.1-flash/high`, effort high,
executor 0.1.2041, standing rules **r4**, R02, R06–R08, R11, R13–R20. Corroborated against `origin/main` (the four merge diffs;
`ci checks status` on #282: `overall_status: success`) and SonarCloud `main` at its analysis of `70bc999f`.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 — `core.store`, `core.queue` | shipped; the PR-scoped Done-when met, the plan's goal not | #264 + `e09fbb8f` in #282; the lane's post-merge self-review found two sites the PR-scope analysis did not surface |
| D2 — other `pm-core` packages | shipped; same | #268 + #282 (seven S2589 in `ProfileValidator`, `Scoring.threshold`) |
| D3 — `pm-workflow`, `pm-runtime`, providers | shipped | #274; 36 sites read as addressed |
| D4 — ADR | shipped | `doc/adr/013-the-null-contract-of-the-null-marked-packages.adoc`, index row |
| Terminal measurement (spec): `main` reads 0 S2583/S2589/S2637, gate `OK` | **not met** | see below |

**Terminal measurement, read by the orchestrator (Sonar `main`, analysis of `70bc999f`, which contains `65ee72ac`):** the
S2583 and S2589 issues are gone (0 open). **Two S2637 bugs remain:** `Scoring.java:80` ("return value is marked `@NullMarked`
at package level but null is returned", issue created 2026-10-27) and `UnitFrontmatterReader.java:191` ("`modificationDate`
is not initialized in this constructor", created 2026-10-18). The gate of `main` stays **ERROR** on `new_reliability_rating`
(value 2). The lane's #282 reached `Scoring.threshold` (now `Objects.requireNonNullElse(item.grading().passThreshold(),
DEFAULT_PASS_THRESHOLD)`, checked at `origin/main`) and the `UnitFrontmatterReader` Javadoc line only; neither change clears
the analyzer. Total open issues on `main`: 21 (was 27 before the merges); no S2583/S2589 left.

Surface delta (R19): 18 files outside the declaration and 7 declared files not edited — disclosed by the lane in §6.

## Metrics and Anomalies

- Tokens `unknown`, `plugin_version` `unknown`: the open defect in `epic.md`, eleventh recurrence. The first message was an
  incomplete landing (`merge_state=open`); the second supersedes it — ninth recurrence of that defect, closed by this one.
- The spec was read at **r4**; rules since changed: R21 (r5) and the sub-agent clause (r6) bind the orchestrator only.
- Self-review `budget-exhausted`, and run **after** the three merges (disclosed deviation, lane §9). Reviews: CodeRabbit
  (required, quota-limited) reviewed #264, #268, #274 in part; #280 and #282 were refused until the quota policy's step 2;
  #282 merged under the standing authorization on a qualifying summary review of `6be9ac49`. Sourcery approved. Three
  operator questions, two answered "retry now" in free text.
- Sonar: 0 open issues on each PR (lane-stated) while 10 sites stayed open on `main` — the PR scope cannot see untouched lines.

## Routing and Merge Behavior

- Four PRs plus one closed attempt; the merge queue merged `main` into the branch twice (#283, #286).
- Collision check: ran beside 111, 16, 08 and 28; no collision in the diffs (110 is the 99-file null-contract plan, finished
  before the others merged).

## Three-Layer Analysis (standing rule R21, r6)

Done at the merge commits by a read-only sub-agent (the whole of #282 line by line; the class-(c) signature changes, the
`@Nullable` annotation-only sites, the codec write path, ADR 013, sampled across the 99 files), then the points marked
**checked** re-read by the orchestrator. Nothing was built or run. The sample is not the plan: 99 files were not read in full.

### Layer 1 — Specified: the measurement design cannot show the goal

- **S1 (high, checked).** The per-PR Done-when ("`pullRequest=N&resolved=false` lists no S2583/S2589/S2637") cannot see
  untouched lines. The lane's post-merge self-review found ten sites this way; the only sound check is `branch=main`, which
  the spec gave to the orchestrator and which, today, reads two bugs.
- **S2 (high, checked).** The spec's test of "no behaviour change" is "the unchanged suite stays green". An annotation changes
  no bytecode, so no suite can show a `@Nullable` true or false, and no nullness checker is required. The layer-3 findings
  C2–C4 slipped through for this reason.
- **S3 (medium, checked).** "(c) = `Optional` return" combined with "`Optional` is return-type only" produces about 200
  `nullableX(...).orElse(null)` round trips back into `@Nullable` locals and builders: the signature changed, the null did not
  go away. The core-store guide and ADR 013 bless the pattern implicitly.
- **S4 (medium, checked).** The spec allows `@SuppressWarnings("java:S2637")` for an external type only. It names no rule for
  sentinel initialisers (`DbusConnection.uniqueName = ""`, `LossDetection.ackFrom = Instant.EPOCH`), for
  `Optional.ofNullable(x).filter(…)` laundering, or for `Objects.requireNonNull` to satisfy a return. All occur; ADR 013 lists none.
- **S5 (low).** The stated population (99 files) is stale: 18 undeclared files touched, 7 declared not edited.

### Layer 2 — Implemented as specified: traced; the goal's evidence is thinner than the PRs' green

- **I1 (medium, checked).** The spec asks for a test where a `@Nullable` or `Optional` change adds an observable state. D1
  changed one test file (`SecretRedactorTest`), D2 none, D3 `JsonTest`, `TaskProtocolReOfferTest` and five provider tests.
  No new null-case test for `Scoring.threshold`, `StrictJson.nullable*`, `RoleAssignmentValue.of(effort=null)` or the
  `QualificationSettingsCodec` write-after-read; existing suites may cover some (not shown by reading).
- **I2 (medium, checked).** `PrimitiveRegistry` (#268) launders nulls with `Optional.ofNullable(id).filter(…)` and a
  `present()` helper — the "ignore in disguise" the spec rejects. `PrimitiveRegistry.java:214,273,290` carry
  `@SuppressWarnings` for S2583/S2589 (checked: `:273` is `S2589` with no reason on the annotation line); they were added by
  #271 (`44cfe8d7`), not by this plan, and contradict the "no ignore" intent. `PrimitiveResult.Builder:121` suppresses S2637
  for `private R outcome`, which is not an external type (reader; outside the spec's one permitted suppression).
- **I3 (low).** `Enrolments.logRemoval` (extraction to dodge a Sonar false positive) and `Objects.requireNonNull(state)` at
  `AuditVerification:187` exist to satisfy the analyzer.
- **I4 (low).** `core-spi.adoc` says an outcome class goes "through `Optional.ofNullable`" where it goes through
  `requirePresent`; the new paragraph sits between the `== The Registry` heading and its `_Requirements_` line.
- **I5 (low).** Assertion messages missing or an `orElseThrow()` helper only, in the lines #274 touched in `GlabCommandsTest`
  and #274/#264 touched in `SecretRedactorTest`.
- "No test was deleted" is borne out (no `@Test`, `@ParameterizedTest` or `@DisplayName` line removed in the four PRs); the
  class (a) count is 0. "The sibling `plan-marshall-mcp` has no caller" — not shown by reading.

### Layer 3 — Implementation correctness: annotations that are not true, and a null that still arrives

- **C1 (high for the plan's goal, checked).** The terminal measurement: `Scoring.java:80` and `UnitFrontmatterReader.java:191`
  are still open S2637 bugs and the gate is still ERROR (above). `requireNonNullElse` does not satisfy the analyzer for a
  `@Nullable` `BigDecimal` argument; an explicit `if`/ternary branch might. Not shown by reading.
- **C2 (medium, checked).** `EffectiveRole.java:44,79`: `idleWakeups(…)` returns `Optional<Integer>` and is fed through
  `.orElse(null)` into the **unannotated** record component `Integer idleWakeups`. Null still reaches a `@NullMarked` component;
  `EffectiveRoleFactsTest:104` ("neither sets it") passes null and `LaunchComposer:362` null-checks it.
- **C3 (medium, checked).** `SubprocessJobRunner.java:1657`: `private volatile @Nullable JobRecord latest`, assigned a non-null
  `first` at construction (`:1688`) and, per the reader, never nulled, then dereferenced unguarded at about 15 sites
  (`job.latest.jobId()` at 939, 964, 991, 1160, 1175 …). The annotation is false; not a class (b) case; probably added to clear
  S2637 on a volatile field. Whether it clears the analyzer: not shown by reading.
- **C4 (medium, partly checked).** `QuestionRecord.java:79-81`: `questionClass`, `origin`, `sourceEntity`, `questionSet` are
  unannotated while null on non-ASKED events (the reader: `onlyOn(event, ASKED, …)` enforces it and the codec writes
  `wire(…).orElse(null)`), and `requireAsked` re-asserts them with `requireNonNull`; `askingRole` and `decision` are annotated
  `@Nullable` (checked) but the landing says only `planTaskId` was fixed. Whether Sonar flags the rest: not shown by reading.
- **C5 (low, reader).** `HarnessProfile.askUserTool` is `@Nullable` but the factory always supplies `""` (the lane's own F13,
  kept); `Json.read` puts `read(parser).orElse(null)` into a tree of `Object`; `SecretRedactor.redactText` returns `Optional`
  that its three production callers unwrap with `.orElseThrow()` after a null guard; `DbusConnection.uniqueName()` now returns
  `""` before connect.
- **Checked and fine:** `#282` in full (the `QualificationSettingsCodec` `byte @Nullable []` write path cannot overwrite a
  settings file with an empty array any more, `ProfileValidator` probe parameters, `QuestionRecord.requireAsked`, the ADR and
  Javadoc fixes, the one test change at `JsonTest:76`); `TaskProtocol`, `JournalProvider` and its absent-journal test,
  `ConsultationService`, `ProfileWorkerEligibility`, `LoadedProfile.verificationRecord`, `GradedConfiguration.effort`,
  `JudgeCheck.durationMs`, `PlanFacts.Builder`, `WorkerEgressProxy.drainHeaders`, `LfsBatchClient`.

### CLAUDE.md conformance

No `Optional` parameter, field or record component introduced; no `@Nullable` method return introduced; no Mockito or
Hamcrest in the touched tests; no new `NOSONAR` (the old `LessonId:36` stays; `sonar.issue.ignore` at `pom.xml:71-75`
pre-dates the plan); every other `@SuppressWarnings` in the diffs carries a reason except `PrimitiveRegistry:273`.

### Verdict

The four deliverables are shipped as specified and #282 is clean. **The plan's goal is not reached:** `main` still reads two
open reliability bugs and the gate is ERROR. Three annotations/flows keep a null where the contract says none (C2, C3, C4).
Owners: C1 and Wa58 — nobody (operator decision: a one-to-two-site fix plan); C2–C4, S3, I2 — nobody; S1/S2 — the
specification of the next null-contract plan (PLAN-V2-97..99 convert `Optional` parameters, fields and components, and are
the natural place to retire the `orElse(null)` round trips). They go to the operator, and become a staged fix-plan spec only
on the operator's word.

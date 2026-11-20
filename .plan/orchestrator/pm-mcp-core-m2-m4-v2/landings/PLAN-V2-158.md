# Landing Analysis: PLAN-V2-158 — null-contracts-and-optional-conversion

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #525 (deliverable 1, squash merge `c9d9c5a4`, 61 files, +349/-318) and #529 (deliverables 2 to 6, squash merge `4917a034`, 81 files, +1353/-338)

> Landing record, plan-marshall form (`plan-v2-158-null-contracts-and-optional-conversion`, harness claude, model `claude-sonnet-5-5`, 6 deliverables by the lane's count: 4 met, 2 met with residuals). One landing message (`-006`, 2026-11-20T16:49Z, `merge_state=merged`, `pr=#529`) and five candidate-lesson messages (`-001` to `-005`). The plan was cut into two pull requests by the operator at the outline gate (the 100-file review limit): the message names #525 in its text only.
> Landing facts: `unresolved_review_threads=0`, `review_body_findings_total=2`, `handled=2`, `sonar_open_new_issues=0`, `step.code-review.advisory_count=8`, no blocker, `total_tokens` 27 250 816 (a floor: envelope 4's rate-limited attempt was not measured), `total_wall_seconds` 44 577 (about 12 h 23 min), `effort` unknown.

## Deliverable Fidelity vs Spec

The spec's four deliverables became six at the outline: D1 the `pm-core` conversion, D2 the `pm-runtime` conversion, D3 the null contracts, D4 the ADRs and guide lines, D5 the residues of PLAN-V2-128 and 81, D6 the residue of PLAN-V2-63.

| Deliverable | Verdict | Evidence |
|-------------|---------|----------|
| D1 `pm-core`: 12 components of `OptionalInt`/`OptionalLong` become `@Nullable Integer/Long` | shipped-modified | #525; `EffectiveRole.idleWakeups` carries no `requireNonNull`: the outline reasoned null is the legitimate absent state and added a null-state test. |
| D2 `pm-runtime`: the five components | shipped-as-specified | #529; the `VerifiedUsage` and `HarnessListings` bridges that #525 needed were removed in #529. |
| D3 the null contracts | shipped-as-specified | the Sonar S2637 suppression was kept (see L1-2). |
| D4 ADRs and guide lines | shipped | ADRs 029 to 033, `provider-ci/011`, `runtime-credentials/014`, `runtime-config/007`; two lines over 120 columns remain in `core-role.adoc` (`:187`, `:241`). |
| D5 residues of PLAN-V2-128 and 81 | shipped-modified | the `ForgeUrl` strings were left unchanged and "accepted"; the Traceability pr-view hunk was kept, not corrected; the marker lives in the entry value, not in an account name. |
| D6 residue of PLAN-V2-63 | shipped-as-specified | |

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (general-purpose, three layers in one brief; it wrote no file and ran no build or test; it ran the links check once). Its measurements are the leaf's; the orchestrator re-read none of them. A side effect: the leaf detached HEAD in the main checkout to read `origin/main` and restored it with `git switch main`; the checkout was verified on `main` at `4917a034`, clean.
- **Verdict:** no blocker. Layer 1 shipped with two recording gaps and a spec-versus-landing mismatch; layer 2 sound; layer 3 no NPE or wire-format break, some stated contracts are not yet true.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence |
|---------|-------|---------|----------|----------|
| L1-1 | the outcome of each carried item is stated "in the landing message and the pull request" | checked | refutes (partly) | the `ForgeUrl` "accepted" outcome (`ForgeUrl.java:271`, `:322` unchanged) and the S2637 "kept" outcome are in no ADR, no PR body, no landing text; the landing points at decision-log entries `7254e8` and `6d2e07` of the untracked `.plan/local`. |
| L1-2 | the S2637 hypothesis is settled | checked | refutes (partly) | `SubprocessJobRunner.java:1875` still carries the suppression; main shows 2 CLOSED issues hidden by it; the re-fire on the current shape is inferred from PR #512 at `3b566897`, not tried on #529. |
| L1-3 | the `MachineConfigurationCodec` guard is "outside the declared surface" | checked | refutes | `MachineConfigurationCodec.java` IS in the spec's Expected Surface (read-only in the outline); the guard at `:499-502` changes the refusal message of every bounded integer, not only `lsp_idle_timeout_seconds`. |
| L1-4 | the surface | checked | info | 132 non-ledger files; undeclared: the compiler-driven callers (`VerificationRecord`, `JournalRecovery`, `ScopeTransaction`, `QuestionLogRecordReader`, `StartupSequence`, `AuditVerification`, `HarnessEnrolmentStore`, `LossDetection`, their tests), formatter-only files in their own commit `c01343e8` (`JGitOperations`, `SupervisionPass`, `WorkerEnding`, three `pm-workflow` tests), the ADRs and several guides. |
| L1-5 | the PR A / PR B cut leaves main consistent | checked | confirms (one window) | the bridges kept compiling; ADR 029 merged in A already says the five runtime components are converted, true only after B. |

**Layer 1 verdict:** findings — 2 major (L1-1, L1-2), rest info.

### Layer 2 — Implemented

- No `OptionalInt`/`OptionalLong`/`OptionalDouble` remains as a record component in the four modules' `src/main`; the remaining Optional parameters and fields (`MetricsCodec.java:529`, `:639`; `HarnessUsage.fromEndpoint` `:128`; `HeaderRefusal` at `InstanceHeaderReader.java:327`, `:332`; `FinalizeInvariants.java:228`; `WorkflowValidator.java:2189`) are outside ADR 029's scope and unowned (L2-3).
- The three riskiest consumers hold: `TransactionJournal`/`JournalRecovery` (`sizeBefore`: the codec omits the field when null and accepts a JSON null; `requireNonNull` where `orElseThrow` stood), `HarnessEnrolmentStore:309-318,402` (`tokens`: the wire form is unchanged, the string "unmeasured" for null), `LaunchComposer:415` with `EffectiveRole:142-144` (`requireNonNullElse(…, 0)` is the old `orElse(0)`). `LossDetection:262` and `AuditVerification:203-215` check null before unboxing. `0` stays distinct from absent everywhere read.
- ADRs 029 to 033 match the code (the 032 message text, the 033 overload list `ToonValue.java:188-218`, the 031 `""` fallback `HarnessProfile:165,183`, the 029 component list, the `runtime-credentials/014` residual-risk wording).
- New tests: no added single-line assertion message outside 20 to 60 characters, no AAA comments; mutation runs are logged for eight tests; **no failing-first run is evidenced for the `OutlineCodec`, `RoleAssignmentValue`, `RunningJob(latest)` and `requireAsked` tests** (decision-log entry `c4dbd5`); they pin existing refusals.
- **L2-1 (minor):** of the 8 advisory findings, six still stand: the refusal texts at `AuditChainVerification.java:118` and `TerminationProtocol.java:158` (pre-existing, test-pinned), the untested not-JSON branch of `GitHubClient.isPullRequest` (`:345`), the `RUNS` lock at `CredentialStoreCheck.java:59` asserted by no test, the `LongContextThresholds.java:31` behaviours (a JSON-null member, `forModel(null)` NPE) untested, `HarnessProfileSchemaParityTest.java:152` comparing bounds with literals, `GitHubClient.java:347` answering false for any JSON body without a `pull_request` key (not recorded in ADR `provider-ci/011`).
- **L2-2 (minor):** `MachineConfigurationStoreRootLockTest`: the third assertion is redundant after `assertThrows` and the test assumes a POSIX root.
- **L2-3 (minor):** the primitive-optional rule is half applied (the Optional sites above), nobody owns the rest.

### Layer 3 — Correct

- No NPE and no wire-format break found in what was read.
- **L3-1 (minor):** ADR `runtime-credentials/012` Decision lines 39-41 still say a leftover entry is "not deleted" and checks "must be serialized by the caller", while `CredentialStoreCheck.java:59` serializes and `:199-203` deletes a leftover on the initial read; its Status says amended but the waived commit `d5f1d6ac` touched ADR 014 only; ADR 014 does not mention that a pre-marker leftover still counts as foreign and blocks.
- **L3-2 (minor):** builders that omit `idleWakeups` or `ackDeadlineSeconds` (`EffectiveRole`, `TransactionJournal.Target`) now yield null where they threw an NPE; ADR 029 intends it, no document names it as a behaviour change, and the #525 body says "No behaviour change beyond the conversion".
- **L3-3 (minor, statements the record narrows):** the #529 body says code review closed with six advisory findings (the last round had eight, F1 to F8 at `749b0051`); it says "No wire-format change beyond the two maxima the schemas already implied" while the keyring test value now carries the `pm-mcp-credential-store-check:` prefix, the GitHub comment guard accepts any 2xx JSON body without a `pull_request` key, and the codec guard changes a refusal message.
- **L3-4 (info):** the links check from the main checkout: `BROKEN LINKS 3`, `INLINE SCHEMA BLOCKS 0`, `UNIMPLEMENTED 2` (a pre-existing item in `mcp-tools/02-service-and-skill-tools.adoc`); the three links (`roadmap.adoc:250`, `core-config/010…:62`, `harness-profiles.adoc:13`) are outside both pull requests. The criterion D4.C4 is correctly reported as not met literally; the operator closed the functional review on it by override (`749b0051`) and waived the review re-run for the docs-and-Javadoc-only commit `d5f1d6ac`.
- The assembly claim holds: `plan-marshall-mcp` at `origin/main` `bde0939f` has no dereference of the 17 accessors that became nullable.

### Review State

#525: 1 thread, 0 unresolved; #529: 4 threads, 0 unresolved. SonarCloud analysed #529 (last PR analysis 16:24Z, merge commit 16:46Z): 0 open issues, 0 hotspots, gate OK; the `build / sonar-build` check shows SKIPPED while "SonarCloud Code Analysis" is SUCCESS. #525 also shows 0 open issues.

## Metrics and Anomalies

Two pull requests, 12 h 23 min of wall time, token total a floor (one rate-limited attempt unmeasured, one placeholder usage entry reversed before the metrics were generated). An operator override and a waived review re-run are recorded above.

## Routing and Merge Behavior

Both merged through the merge queue. `cleanup_owed=false`. The five retrospective candidate lessons (`plan-marshall:phase-6-finalize` twice, `plan-retrospective`, `phase-3-outline`, `workflow-integration-git`) were forwarded to the `lessons-routing` inbox of the plan-marshall repository (`pm-mcp-core-m2-m4-v2-042` to `-046`) and archived here.

## Reconciliation Actions

- Row PLAN-V2-158 transitioned to `shipped`; `pr` `#529` (A is `#525`), `landing`, `plan_marshall_plan_id` `plan-v2-158-null-contracts-and-optional-conversion`.
- Residue placed, none new staged: L1-1, L1-2, L2-3, L3-1 to L3-3 and the `GitHubClient` statement into PLAN-V2-163 as carried item 60; the two refusal texts into PLAN-V2-162 as item 61; the `LongContextThresholds` and parity-literal tests into PLAN-V2-125 as item 62; the `isPullRequest`, `RUNS` lock, root-lock test and the failing-first gap into PLAN-V2-126 as item 63.

## Follow-Ups

- Operator: nothing new; the three broken links of the documentation repository (`roadmap.adoc:250`, `core-config/010…:62`, `harness-profiles.adoc:13`) are pre-existing and nobody owns them.

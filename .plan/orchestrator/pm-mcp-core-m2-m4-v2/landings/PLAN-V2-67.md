# Landing Analysis: PLAN-V2-67 — Settle the unowned SonarCloud issues of main

epic: pm-mcp-core-m2-m4-v2
workstream: WS-10
pr: #445 (merge `8e93002f`, head `0289ba5f`), `merged` through the merge queue; 9 files, +128 / −39

> Landing record written by the `analyze` drain of 2026-11-16 from the message `rb-v2-67-main-hygiene-leftovers-001.md` of the runbook lane (harness opencode, model deepseek-v4.1-flash/high, effort high; `total_tokens` unknown, `plugin_version` unknown: `landing-check` reports `complete: false`, missing `total_tokens` and `plugin_version`, the known defect E27). Every point below marked re-read was read again by the orchestrator at its `file:line` at `8e93002f` (`git show`), not taken from the lane or the leaf.

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|---|---|---|
| D1 `StoreDigest.Builder` `java:S9428` (dropped, settled by PLAN-V2-109) | shipped-as-specified (nothing to do) | the lane read `StoreDigest.java:98` and ADR `core-store/052`; no change in the diff. |
| D2 `MacSecurity.Downcall` `java:S112` | shipped-as-specified | `@SuppressWarnings("java:S112")` with its reason on the interface `Downcall` (`MacSecurity.java:299-308`, re-read); `T call() throws Throwable` mirrors `MethodHandle.invokeExact`; S1181 at `call(...)` untouched; the Javadoc wording on the run-time descriptor check is accurate (`WrongMethodTypeException`); ADR `runtime-credentials/013` written. No UI dismissal. |
| D4 the five null-rule suppressions of `core.spi` | shipped-modified | the suppressions are gone; `PrimitiveResult` and `PrimitiveRegistry` match `Object` by pattern (`PrimitiveResult.java:70-76`, re-read); every refusal keeps its reason code (leaf); **behaviour changed:** the result constructor refuses a `null` reason type with `IllegalArgumentException` where it read absence before (`PrimitiveResult.java:69-76`, re-read), pinned by `PrimitiveResultTest.refusesNullReasonType` (`:143-152`, re-read, messages of 31 and 43 characters). The spec's premise that the constructor "keeps the refusal" was wrong for the constructor. |
| Carried 2026-11-14 (S3516, S1117, S1144, S1612) | shipped-as-specified | `Enrolments.java:400-401` (re-read: the returns are the hand-over count, the local, and `0` for a project whose enrolment file exists, so the finding is a false positive and the inline suppression carries that reason); `SubprocessJobRunner.java:957-960` local renamed `runningJob` (leaf); `ModelSlotCodec.scopeType` deleted (no caller: `ModelSlotCodec.java:155` writes the field and `LeaseCodec.java:215` reads it, re-read); `QuestionRecordCodec.java:198` method reference (leaf). |
| D3 | dropped 2026-10-29 | no work. |

## Three-Layer Analysis

Read by one read-only leaf (`execution-context-level-3`, `orchestrator.analyze`), no build, no test run, no write; reason: every full ship is read on its own leaf. The orchestrator re-read the points marked above.

- **Dispatch:** one read-only leaf per landing — reason: reading merged code is the depth the dispatch rule exists for.
- **Verdict:** layer 1 findings (4: 2 minor, 2 info); layer 2 findings (documentation, ADR and test points, 4 minor and 1 info); layer 3 findings (1 info behaviour change, 1 info documentation gap; no defect).

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence |
|---|---|---|---|---|
| L1-1 | D2 text "the class has no unit test (`@EnabledOnOs`)" | checked | refutes | `KeychainSecretStoreTest` (`@EnabledOnOs(OS.MAC)`, `:37-139`) drives `KeychainSecretStore` and reaches `MacSecurity.call`/`Downcall`; it does not run on Linux (leaf) |
| L1-2 | D4 groups the result constructor with the registry as guards that keep the named refusal | checked | refutes | the constructor read a null reason type as absence (leaf, from the deleted line); `PrimitiveResult.java:69-76` now refuses it |
| L1-3 | the grounding corrections told the outline to decide and record the sixth `S2637` site | checked | refutes | `RefusingFS.java:50-53` (re-read) still carries `@SuppressWarnings("java:S2637")` with a comment giving the reason; the landing records no decision; outside the D4 grep scope, the Done-when holds |
| L1-4 | PLAN-V2-67 sits nine lines from PLAN-V2-94's merged hunk at `MacSecurity.java:313`, so merge the base | checked | refutes | `git log` of `MacSecurity.java` shows only `8e93002f` and older commits (re-run): PLAN-V2-94 never edited the file (its landing cited `:313` as a conforming pre-existing `Throwable` catch); no collision existed |

**Layer 1 verdict:** findings — 4 (2 minor, 2 info). The Standing Constraint "no credential, no UI dismissal" is stale against the epic's 2026-11-14 retirement (the lane says so); nothing followed from it.

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|---|---|---|---|---|
| D2 | `MacSecurity.java:306-307` | none new (the S112 suppression changes no behaviour; `KeychainSecretStoreTest` is macOS only) | none | checked |
| D4 | `PrimitiveResult.java:57-110`, `PrimitiveRegistry.java:213-305` | `PrimitiveResultTest.refusesNullReasonType` can fail (asserts the exception where the base returned a result); the five fixtures stay green with their reason codes | none; the test diff is +10 lines, nothing removed or weakened; it reuses `ReasonPrimitives.NullReasonOutcome`, already on the doubles baseline (`hand-written-doubles.txt:41`), so no new double | checked |
| carried singles | `Enrolments.java`, `SubprocessJobRunner.java`, `ModelSlotCodec.java`, `QuestionRecordCodec.java` | none changed behaviour | every diff file is explained; no `doc/plans`, Traceability or LogMessages change (none required) | checked |

- **Untested new branches:** the non-`String` payload of `conditionKindName` (`PrimitiveRegistry.java:300-304`) and the registry's "no Optional" branch are reachable only by a raw cast and no fixture reaches them (`ReasonPrimitives.refused()` holds only the null-Optional condition, `:123-126`); the spec required only the existing fixtures to stay green (leaf; coverage not shown by reading).
- **Developer documentation:** `core-spi.adoc:89-96` (re-read) describes the pairing mismatch but not the new null-reason-type refusal; CLAUDE.md asks a plan to write the guide of a package it changes; D4 named none (minor).
- **ADR `runtime-credentials/013`:** next free number, all template sections present (leaf); **Date `2026-11-15` against a merge on 2026-11-16** (re-read, `:6-9`; the template says the merge date); its Source has no link and cites the epic's rule R16 (4) that lives in the ignored `.plan/` ledger; `runtime-credentials.adoc` links ADRs inline and not this one (leaf).
- **The lane's claim** that `scope_type` "stays write-only as before" is misleading: the read side is `LeaseCodec.readOwner` (`LeaseCodec.java:215`, re-read); the deletion is safe.

**Layer 2 verdict:** findings — 5 (minor and info), none a missing deliverable.

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|---|---|---|---|---|
| L3-1 | `PrimitiveResult.java:69-76` (re-read) | an outcome compiled raw whose `reasonType()` returns `null` | now `IllegalArgumentException` ("a reason type that is no Optional") instead of absence; consistent with the registry, which already refused it (`PrimitiveRegistry.java:218`); no caller in this repository is hit (only the fixture overrides `reasonType()` to `null`; production outcomes use the default `Optional.empty()`, `PrimitiveOutcome.java:49`); callers in `plan-marshall-mcp` not shown by reading; documented in the Javadoc and pinned by the test | info (a behaviour change the spec asked for only if D4's "keeps the refusal" is read so) |
| L3-2 | `doc/adr/013-the-null-contract-of-the-null-marked-packages.adoc:60-62` | — | the sentence "Behaviour does not change except where a class changes a signature" is narrower than the tree after this merge; it describes the PLAN-V2-110 sweep, so it is not false for its scope | info |

No defect found in the S3516 claim, the S1117 rename, the method reference, the builder (`@Nullable R outcome` plus `Objects.requireNonNull(outcome, "outcome")`, same exception as the constructor threw), or the suppression placement. **Layer 3 not shown by reading:** that Sonar honours the suppressions (the lane's read of 0 open at `0289ba5f` and my re-read below say it does), the test results (10658 / 7518 / 21 / 43), coverage, the failing run of the new test.

**Layer 3 verdict:** findings — 2 (info; none a defect of what shipped).

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|---|---|---|---|
| CLAUDE.md Code Standards | a generic `Throwable` carries a reasoned `@SuppressWarnings` | conforms | `MacSecurity.java:306` (S112), `:311` (S1181) |
| CLAUDE.md Code Standards | every assertion 20 to 60 characters; JUnit 5; no new hand-written double; no random data | conforms | `PrimitiveResultTest.java:148,151` (31 and 43 characters, re-read) |
| CLAUDE.md Code Standards | absence as `Optional`, a nullable field is `@Nullable` | conforms | `PrimitiveResult.java:113` |
| CLAUDE.md Module Documentation | a plan writes the developer documentation of the packages it changes and one ADR per decision | deviates (minor) | ADR written for D2; no `core-spi.adoc` line for D4 (D4 named none) |
| `doc/adr/template.adoc` | date = merge date, Source as links | deviates (minor) | ADR `runtime-credentials/013:6-9` |
| CLAUDE.md Dependencies | no new dependency | conforms | no pom in the 9 files |

## Metrics and Anomalies

- Tokens: unknown (the harness measured none; recorded as unobserved, not zero).
- Duration: not carried.
- Anomalies: one local-gate miss — CI builds the pull request's merge commit, and the `main` merged meanwhile (`cd9da049`, PLAN-V2-103's scanner extraction) made the scan cover `core/spi`, so the new `assertThrows` without a message failed `AssertionMessageScanTest` at `3a8b8ce5`; fixed in `a27dbff9`. A fast-moving base, not a false positive. `sonar-facts` refuses `--rules none` at a confirmed zero (`confirmed_zero_with_rules`) although the skill text says `none` is written at a measured `0`: a skill/verb mismatch reported by the lane (not routed: a plan-marshall lessons matter, see the standing rule on foreign lessons).

## Routing and Merge Behavior

- Review: CodeRabbit (required) refused on quota six times (17:51Z to 01:15Z) and reviewed at 02:52Z with one finding (`invokeExact` checks the descriptor at run time), fixed in `3a8b8ce5`; Sourcery (optional) refused on its budget. Re-read against the provider: `review-gate` on the merged head reports 0 unresolved threads and 1 body finding, equal to the landing; `review_body_findings_handled: 1` re-read from the lane's progress file (one `finding:` line: `coderabbit inline inline-1 — 3a8b8ce5`) — consistent; the gate command alone reports `body_findings_undisposed` because it is not given the progress file, which is not a difference.
- CI/merge: merged through the merge queue as `8e93002f`. Executor skew at the start (`0.1.2133` vs `0.1.2135`), regenerated.
- Sonar: landing `sonar_open_new_issues=0`; re-read for `pullRequest=445`: total 0, gate `OK` — equal.

## Reconciliation Actions

- [x] row `status` → `shipped`
- [x] row `pr` stamped `#445`
- [x] row `landing` stamped `landings/PLAN-V2-67.md`
- [x] row `plan_marshall_plan_id` stamped `rb-v2-67-main-hygiene-leftovers` (the sender, not typed by hand)
- [x] epic.md narrative reconciled
- [x] leftovers placed (below); `landings_incomplete`: `total_tokens`, `plugin_version` unknown, recorded under the known Open Defect E27
- [x] resume anchor updated, `queue-view.md` regenerated

## Follow-Ups

- The development-guide line, the ADR 013 sentence and the date and links of the new ADR, the two untested branches, the `RefusingFS` decision: PLAN-V2-114 item 31 (new; surface extended in the same edit).
- The overlap by hunk with PLAN-V2-94 was a false expectation: recorded here; the stale line in PLAN-V2-94's landing (Follow-Ups) is left as written, it recorded the prediction and "none observed".

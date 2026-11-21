# Landing Analysis: PLAN-V2-160 — assertion-message-residue

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #532 (A: scanner, pm-core, schema; squash merge `ce7900ba`, 75 files), #533 (B: pm-runtime; `f648753a`, 34 files) and #534 (C: pm-workflow; `1037e0a8`, 69 files)

> Landing record, plan-marshall form (`plan-v2-160-assertion-message-residue`, harness claude, model `claude-sonnet-5-5`, 5 deliverables: 4 shipped as specified, 1 shipped modified). One landing message (`-001`, 2026-11-21T08:34Z, `merge_state=merged`); no candidate-lesson message (the retrospective step was skipped). The plan was cut into three stacked pull requests opened by hand outside the finalize pipeline (177 files against the 100-file limit); the finalize steps sync-baseline, functional-review, code-review, architecture-refresh, pre-push-quality-gate, preference-emitter and retrospective did not run.
> Landing facts: `unresolved_review_threads=unknown` (the leaf found 4 of the 15 CodeRabbit threads on #532 unresolved after merge), `review_body_findings_total=15`, `handled=15`, `sonar_open_new_issues=0`, `total_tokens` 18 767 374, `total_wall_seconds` 66 458 (about 18 h 28 min), `effort` unknown, no blocker.

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|-------------|---------|----------|
| D1 the scanner and its exemptions (items 29, 32a, 32d) | shipped-as-specified | controls in `AssertionMessagesTest` for the three CodeRabbit shapes; the core guard has `Map.of()` and no `EXEMPTIONS` constant; the scanner is split into `AssertionMessages`, `MessageMeasurement`, `ArgumentSpans`, `CodeView`, `ConstantScopes`; `pm-core/pom.xml` unchanged. |
| D2 `pm-core` messages (item 28) | shipped-as-specified, with a hidden remainder (L1-3) | 37 unmeasured outside `store` at main. |
| D3 `pm-runtime` (item 32b to e) | shipped-as-specified | 238 to 11, reproduced exactly; weak messages reworded (`OffTrackScenarioTest:219`, `QuestionLogRecordReaderBoundaryTest:226`, 11 sites in `CredentialStoreMoveTest`, 3 in `EnrolmentKeyTest`). |
| D4 `pm-workflow` (item 45a to e) | shipped-as-specified | 477 to 2, reproduced exactly. |
| D5 schema suppliers, `$ref`, ADRs (item 46) | shipped-modified | suppliers became a `violationsIn` helper with a literal message; `harnessReferenceToEndpointIsRefused` pins the `SchemaException`; `core-analysis/001:33` corrected; ADR 028 shipped a check count that #537 removed after the merge; the "if cheap" sub-items have no recorded outcome. |

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (general-purpose, three layers in one brief; it wrote no file, ran no build and did not move HEAD). It counted with an inline read-only approximation of the scanner (not the real scanner); its numbers are the leaf's, the orchestrator re-read none of them.
- **Verdict:** no blocker. The work shipped as specified, the scanner is sound, and the mass edit weakened no assertion (34 non-message diffs read: all benign or stronger). The weak points are traceability of the kept remainders, an unpinned unmeasured list, and diagnostics lost from many assertions.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence |
|---------|-------|---------|----------|----------|
| L1-1, L1-2 | `pm-runtime` 238 to 11, `pm-workflow` 477 to 2 | checked | confirms | counted at `84f602af` and `origin/main`; the 11 are `OutboundCallsArchTest` x8, `SocketOwnerTest:57`, the runtime scan test `:55`, `HandWrittenDoubleScanTest:123`. |
| L1-3 | `pm-core` 242 to 37 | checked | info | 37 is the non-store figure; 49 more unmeasured remain in `core/store` (`AuditCodecTest:191`, `JobRecordCodecTest:408`, `LeaseCodecTest:266`: mostly `refusal.getMessage()`, the shape item 28 wanted replaced) which item 28 excluded; the whole tree has 86; the start figure 242 is not reproduced (about 274). |
| L1-4 | remainders are "kept with a reason" | checked | refutes (partly) | no baseline or allow-list file exists; the guards write `target/assertion-message-unmeasured.txt` and compare nothing; the reasons are in the PR bodies #533 and #534 and the plan's untracked decision log. |
| L1-5 | item 32(e): 45 against 44, no file edited | checked | confirms | 45 message literals changed at `19b0ae6b` (35 distinct, none misaligned); the #451 table sums to 44, its text says 29 plus 15. |
| L1-8 | item 46(b) ADR 028 | checked | refutes (partly) | the shipped count "18 positive checks and 18 negative controls" was removed by #537; the "if cheap" sub-items (upstream changelog, `SchemaValidation.java:25-26`) have no recorded outcome. |
| L1-9 | every carried item has an outcome in the landing and the PR | checked | refutes (partly) | the landing has no per-item outcomes; the PR bodies cover 28, 29, 32, 45, 46 but not the 46(b) sub-items; 28's kept-37 reasons point to the plan log. |
| L1-10 | surface of 177 files | checked | info | squash stats A 75, B 34, C 69 (178; the #532 body says 81); B and C touch only their module's test tree; 15 of the 20 files the Expected Surface names are touched, the 5 untouched are by design. |
| L1-11, L1-12 | "a full verify and the quality gate ran per branch"; links check 3 broken | unchecked / checked | info | the PR bodies show targeted runs plus a whole-tree quality gate (#533) and `verify -pl pm-workflow -am` (#534); a fresh links run reports 50 against a documentation checkout 6 commits behind (none in this plan's files). |

**Layer 1 verdict:** findings — 4 minor (L1-3, L1-4, L1-8, L1-9), rest info.

### Layer 2 — Implemented

- Semantics: every message span replaced by a placeholder and old diffed against new over 170 changed test files; 34 files differ outside messages and all 34 diffs were read: `isEmpty()` became `assertEquals(List.of() / Set.of(), x, msg)` (stronger), helper renames, variables only messages used removed, `QualificationRecordCodecTest.unreadable` made parameterized over 13 cases (a latent `byte[] + "{}"` bug fixed). No argument swapped, no assertion removed, no `@DisplayName` changed, no AAA comment added.
- Message quality: 60 sampled and a regex over 1 116 added literals: no padding; weak ones are `LfsBatchClientTest` ("the refused download has the expected outcome") and eight "the expected …"; some repeat 3 to 10 times on equal-shape checks.
- The scanner is sound (read `ArgumentSpans`, `CodeView`, `ConstantScopes`, `MessageMeasurement`); controls exist for each CodeRabbit shape (all 13 in one commit `b80f6b70`, failing-first order not checkable after the squash). New test code uses no hand-written fake and no literal that is not the contract.
- **L2-1 (minor):** the fixed `ASSERTIONS` list (`AssertionMessages.java:77`) equals the Jupiter `Assertions` names except `assertAll`; the decline of "derive from the JUnit API" is defensible for the message position, but the reply "needs reflection-based overload data" overstates it: a names-only drift guard is cheap and absent.
- **L2-3 (minor):** the unmeasured list is report-only: 86 (core), 11, 2 and 6 (providers) are pinned by nothing; a new `"x" + y` message passes silently.
- **L2-4 (minor):** diagnostics lost: at least 58 single-line `assertTrue(x.contains(..), rendered)` became a fixed literal (`WorkflowDiagramGeneratorTest:115-135`), the golden `assertArrayEquals` at `:81`, `:470`, `CorpusValidatorTest:589` (`report(violations)`), loop labels in `PortedExpectations` and `UnitGraphCycleScaleTest`; the declined `FindingTest:453` is the same trade-off. Not a rule violation; it hurts failure triage.
- **L2-2, L2-7 (info):** the `assertAll` heading exemption is spec-allowed (813 headings, 161 under 20 characters, 2 over 60) and `CLAUDE.md` is not amended; `ArgumentSpans.java:112`: a top-level `x instanceof Map<String, String> m` has no type context (silent only when the call has no message).

### Layer 3 — Correct

- Main is consistent after A alone: the runtime and workflow guards existed before A; the leaf's approximation at `ce7900ba` finds 0 violations in core, runtime, workflow and the providers; #532 CI was green; the runtime guard edit shipped in B and the workflow one in C.
- Squash scope is clean: the merge-forward commits (#533 has 9, #534 has 12) left no stray files.
- **L3-3 (minor):** ADR 012 does not record the scanner split, the constants-and-suppliers measuring, the unmeasured report or the `assertAll` exemption; `pm-core/pom.xml:141-143` omits pm-runtime; ADR 028's check count needed #537.
- **L3-4, L3-5 (info):** 4 of the 15 threads on #532 are unresolved after merge (the landing says `unknown`); the two declined comments have no tracked follow-up; the landing's "full verify per branch" and "3 broken links" do not match what the leaf could see.

### Review State

#532: CodeRabbit 15 comments (13 fixed with failing-first controls, 2 declined with a reason: derive the names from the JUnit API, the `FindingTest` helper message); 4 threads unresolved after merge. #533 and #534: no bot review (CodeRabbit rate-limited, Sourcery refused #534 for its size). CI green and Sonar 0 open issues on all three.

## Metrics and Anomalies

`total_tokens` 18 767 374, 18 h 28 min of wall time, plugin 0.1.2173. Three stacked pull requests by hand outside the pipeline; a force-push of the rebased B and C was denied, so main was merged forward and the merge commits were squashed away. The landing reports an overlap of tests with PLAN-V2-158 and PLAN-V2-19.

## Routing and Merge Behavior

All three merged through the merge queue. `cleanup_owed=false`. No candidate lesson was sent.

## Reconciliation Actions

- Row PLAN-V2-160 transitioned to `shipped`; `pr` `#532, #533, #534`, `landing`, `plan_marshall_plan_id` `plan-v2-160-assertion-message-residue`.
- Residue placed, none new staged: items (a) to (f) of the test side (the 49 `store` messages, the ratchet for core and workflow, the drift guard, the lost payloads, `ArgumentSpans:112`, the weak messages) into PLAN-V2-125 as item 68; the `pm-runtime` ratchet and allow-list into PLAN-V2-126 as item 69; the rule and ADR statements (`CLAUDE.md` and `junit-core` for the `assertAll` exemption, ADR 012, `pom.xml:141-143`, the 46(b) sub-items, the 44-or-45 figure) into PLAN-V2-163 as item 67.

## Follow-Ups

- Operator: none new. The `plan-marshall` finalize pipeline ships one PR per branch, so a plan over the 100-file limit must be cut by hand; the plan knew of this at the outline and cut it into three.

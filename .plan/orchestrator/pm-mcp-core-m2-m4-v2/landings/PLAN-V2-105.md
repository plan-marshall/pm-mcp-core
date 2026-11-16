# Landing Analysis: PLAN-V2-105 — A message on every assertion of the pm-runtime tests, and the guard that keeps it

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #451 (merge `19b0ae6b`, 56 files) and #455 (merge `1d89f357`, 2 files), both `merged`

> Landing record written by the `analyze` drain of 2026-11-16 from the message `rb-v2-105-assertion-messages-runtime-b-001.md` of the runbook lane (harness opencode, model deepseek-v4.1-flash/high, effort high). `landing-check` reports `complete: false`: `total_tokens` and `plugin_version` are `unknown`, the known defect E27 (recurrence, no new entry). Every point below marked re-read was read again by the orchestrator at its `file:line` at `1d89f357`, not taken from the lane or the leaf.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|---|---|---|
| D1: a message on every assertion of the scope (six packages plus the folded `config` and `corpus` of PLAN-V2-104) | shipped-as-specified | #451 touches 56 files, all `pm-runtime/src/test/java` (stat re-read: 2557 insertions, 1638 deletions); the whole-tree guard is green at the merge head per the landing, and the leaf's own approximate scan of 263 files finds 0 missing and 0 out of 20 to 60 |
| D1 Done-when: test method count the same | met | `git diff -G '@(Test\|ParameterizedTest\|RepeatedTest\|Nested)'` over `7200dddf..19b0ae6b` is empty (re-read); #455 adds exactly one `@Test` (`AssertionMessageScanTest.java:38`, re-read) |
| D2: a guard against regression, as the thin test over the shared scanner of `pm-core:tests` | shipped-as-specified | `AssertionMessageScanTest.java:36-51` re-read: `testSourceRoot`, `scan`, `Map.of()` exemptions, offenders written to `target/`, failure message from the shared `failureMessage`; no scanner copy |
| Spec's own Expected Surface | shipped-modified | one undeclared extension, declared by the lane: `runtime/launch/LaunchComposerTest.java:312,329` (two messages; `:312-314` re-read, the message is there). The whole-tree guard needs it |

## Three-Layer Analysis

Read on three layers by one read-only leaf (`execution-context-level-3`, resolved for `orchestrator.analyze`), because reading merged code is the depth the dispatch rule exists for and this landing is its own; the orchestrator re-read the points below and records which. **By reading, no build, no test run, no write.** What a run would show is `not shown by reading`.

- **Dispatch:** one read-only leaf — reason: every full ship gets its own leaf, one landing per leaf.
- **Verdict:** layer 1 findings (2 minor); layer 2 findings (2 minor, 1 stated not-shown); layer 3 findings (3 minor, message quality only; no logic defect).

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---|---|---|---|---|
| L1-1 | Spec scope is six packages, the binding guard walks the whole tree, so two assertions outside the scope had to be fixed | checked (re-read) | refutes | `LaunchComposerTest.java:312-314` |
| L1-2 | The objective says every message states what is checked; the shared scanner measures only a single string literal, so a supplier, method reference, constant or exception text passes unmeasured: the Done-when is weaker than the objective | checked (re-read through L2-4) | refutes | `AssertionMessages.java` (scanner, `pm-core:tests`) |
| — | HYPOTHESIS "the scanner can be a plain regex": refuted, it is a lexer plus argument splitter | checked, not re-read | refutes | `AssertionMessages.java:539` |
| — | "fails on a fixture assertion without a message" is met by the `pm-core` controls, not by the `pm-runtime` test | checked, not re-read | confirms | `AssertionMessagesTest.java:46` |

**Layer 1 verdict:** findings — 2 (both minor). No corpus verdict persisted: the spec's claim section carries no claim these settle (the HYPOTHESIS about the scanner was refuted before the emit by the carried section, which overrides the wording).

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|---|---|---|---|---|
| D1 | 56 files of `pm-runtime/src/test/java` | `AssertionMessageScanTest` | none beyond the declared `LaunchComposerTest` | checked |
| D2 | `AssertionMessageScanTest.java` | itself; the fixture control is `pm-core` `AssertionMessagesTest` | none | checked |
| Lane claim: "~29 reworded" | — | — | the leaf counts 45 removed message literals (35 distinct) in 22 files by word diff; the landing's figure is low. **Not reproduced by the orchestrator, an unverified lead.** | not_shown_by_reading |
| Lane claim: wrong first-round messages were corrected | — | — | #451 is a squash, the intermediate texts are not in git; the final texts at the named sites read correct | not_shown_by_reading |
| Lane claim: `assertAll` is not in the scanner's name set | `AssertionMessages.java:66-70` (re-read: absent; `fail` and `assertNotSame` are present) | — | each lambda assertion inside `assertAll` is still measured, so no in-scope assertion relies on a heading | checked (re-read) |
| Objective: every message says what is checked | `CredentialStoreMoveTest.java:253` (re-read: `old.entries().toString()` as the message) | — | 214 non-literal messages remain in the tree (106 non-supplier, 108 supplier or method reference), pre-existing, accepted by the guard; examples `id.value()` (`:271`), `thrown.getMessage()`, `outcome::detail` | checked |

**Layer 2 verdict:** findings — 2 (minor): the objective is not met for the 214 non-literal messages, and the rewording figure is understated. Routed to residue.

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|---|---|---|---|---|
| L3-1 | `worker/OffTrackScenarioTest.java:219` (re-read) | `assertFalse(observed.ended(), "the signal neither ends the plan nor waits")` | `ended()` is a generation fence or a task close, not "the plan", and nothing here measures "waits": the message overclaims | minor |
| L3-2 | `question/QuestionLogRecordReaderBoundaryTest.java:225-228` (re-read) | the message "a log of only a refused line reads unreadable" sits on `assertEquals("the question log holds a refused line 1", unreadable.reason())`; it restates the type check one line above | the message does not say that the reason text is checked | minor |
| L3-3 | `credentials/CredentialStoreMoveTest.java:291` and about 12 sites (`Reason.FAILED`), `EnrolmentKeyTest` 166 and 608, `SupervisorScenarioTest` 274 and 300, `WorkerRecyclingTest` 326 | messages that restate the assertion or describe the setup | true and in range, low informativeness | minor |

**Layer 3 not shown by reading:** the guard passing at HEAD, compilation and overload resolution, and `surefire` working-directory behaviour; several large files (`EnrolmentsTest` with 731 diff lines, `FileSecretStoreTest`, `SecretServiceStoreTest`, `KeychainSecretStoreTest`, `DbusMessageTest`, `DbusConnectionTest`, `ActivityEventMapperTest`, `OffTrackDetectorTest`, the remaining worker and endpoint tests, the `SecretServiceIT` diff) were checked only by the logic-equality normalisation and the heuristic polarity scan, not message by message.

**Layer 3 verdict:** findings — 3 (all minor); no logic, argument-position or polarity defect found. Test logic is unchanged: a normalised old-against-new comparison of 57 files differs only in `CredentialStoreMoveRecordTest`, which the leaf read by hand (trailing messages only). The thin test is correct; `assertTrue(violations.isEmpty(), …)` after the early return is always false, so `fail(…)` would read better (`AssertionMessageScanTest.java:45-48`, re-read), and `testSourceRoot` walks up from the working directory, so an IDE run from the repository root throws `IllegalStateException` (not the build path).

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|---|---|---|---|
| CLAUDE.md | Every assertion message 20 to 60 characters | conforms (by an approximate scan; the real guard not run) | 5337 literal messages in range, 0 out of range, 0 missing, 263 files |
| CLAUDE.md / junit-core | Message says what is checked, every assertion | deviates (pre-existing) | 214 non-literal messages, 106 state dumps; not introduced here |
| CLAUDE.md | JUnit 5 only, `@DisplayName` | conforms | `AssertionMessageScanTest.java:36-39` |
| CLAUDE.md | No AAA comments, no Mockito, Hamcrest, `System.out` | conforms | `git diff -G` over `7200dddf..1d89f357` empty |
| CLAUDE.md | The scan logic is the shared scanner, not a copy | conforms | the thin test imports `AssertionMessages` from `pm-core:tests`; `pm-runtime/pom.xml` already declares the test JAR |
| CLAUDE.md | Test code only, no assertion changed but by its message | conforms | logic-equality comparison above |

## Metrics and Anomalies

- Tokens: `unknown` (the runbook lane measures none; recorded as unobserved, not zero).
- Duration: not stated by the lane.
- Anomalies: the lane's own shell file operations before it switched to the file tools, reported by the lane (its § 7), not a repository effect; the lane ran no guard (`guard: none`, OpenCode). Twelve implementer and four reviewer sub-agents dispatched; no guard log corroborates their tool use.
- The lane reports a check-only edit of the `pm-core` scanner's package list, reverted by the edit tool; `git show --stat 1d89f357` and the #451 stat carry no `pm-core` path (re-read), so the claim holds.

## Routing and Merge Behavior

- Review: CodeRabbit required; #451 head `1c74306c` reviewed with 8 inline findings, all fixed in `2b2722e3`; #455 head `76c72e54` one outside-diff note (`assertAll` not in the scanner's name set), answered, not fixed (the scanner is PLAN-V2-103's). Sourcery (optional) refused both pull requests on its own limits. Self-review: four rounds, 16 findings, all fixed.
- Review numbers re-read from the provider: `unresolved_review_threads` 0 for both pull requests (equal to the landing's 0); `review_body_findings_total`: #451 reads 8 and #455 reads 0, so 8 in total, equal to the landing's 8. `review_body_findings_handled`: the provider cannot reproduce it; the progress file `.plan/temp/rb-v2-105-assertion-messages-runtime-b/progress.md` carries 8 `finding:` lines for #451 (`2b2722e3`) and one for the #455 outside-diff note, so the landing's 8 handled is corroborated. No false claim.
- CI/merge: #451 merged `19b0ae6b`, #455 merged `1d89f357` through the merge queue; `cleanup_owed=false`; the worktree and branch refs are gone per the lane (the worktree `v2-105-assertion-messages-runtime-b` was still listed under `.plan/local/worktrees/` at the start of this session, before the merge was read).
- Sonar: the landing's `sonar_open_new_issues=0` and quality gate `ok` were not re-measured by this analysis (a measured read was not run); recorded as the lane's claim, not re-verified.
- Surface collisions: none observed; the two plans in flight that share a file with 105 (31, per the anchor) are still staged.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-V2-105 --status shipped`
- [x] row `pr` stamped — `#451, #455`
- [x] row `landing` stamped — `landings/PLAN-V2-105.md`
- [x] row `plan_marshall_plan_id` stamped — `rb-v2-105-assertion-messages-runtime-b` (from the sender, not typed)
- [x] epic.md narrative reconciled: a queue annotation for PLAN-V2-105; E27 gains no new line (recurrence)
- [x] residue folded into PLAN-V2-114 as item 32 (the epic's standing rule: leftovers go into an existing staged plan), with its Expected Surface widened in the same edit
- [x] resume anchor updated; `queue-view.md` regenerated

## Follow-Ups

- PLAN-V2-114 item 32: the `assertAll` heading in the shared scanner, the 214 non-literal messages of `pm-runtime`, the three weak messages, the thin test's `fail(…)`, and the unverified rewording count. Item 28 (about 71 dynamic messages of `pm-core`) and item 29 (three silent scanner shapes) are the same family and the same decision per shape.
- PLAN-V2-104 stays `superseded`: its packages were folded into this plan and the whole-tree guard proves them.

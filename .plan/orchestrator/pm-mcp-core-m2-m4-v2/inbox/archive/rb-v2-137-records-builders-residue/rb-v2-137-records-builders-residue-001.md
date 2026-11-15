envelope_version=1
sender_type=plan
sender_id=rb-v2-137-records-builders-residue
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-15T04:37:06Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-137-records-builders-residue
epic=pm-mcp-core-m2-m4-v2
pr=#432
merge_state=merged
cleanup_owed=false
deliverables_total=7
deliverables_done=7
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2125
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=0
review_body_findings_handled=0
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

# PLAN-V2-137 — the records, builders and guard residue of PLAN-V2-108, 100 and 53

Merged as PR #432, merge commit `87e14af3985609e36ffc86ab9c87265a0f0b7a69`. Standing rules: revision r7 (2026-11-10), rules R02, R05, R06, R07, R08, R10, R17, R18, R19, R20 (R21 binds the orchestrator, not this plan). R06 value: `pm-runtime/doc/developer/runtime-guard.adoc`; no ADR.

## 1. Deliverables

| D | Done-when | Verdict | Order | Test / evidence | Failing run | Commit |
|---|---|---|---|---|---|---|
| 1 | the scan returns no positional record site; the calls are listed per record; `test -pl pm-workflow,pm-runtime -am` green with the base's method count | met | `none` | 37 sites in 16 files converted (12 of the PLAN-V2-108 list, 25 of the PLAN-V2-100 list); scan described in the progress note and in the PR body; whole reactor `test` green with 10548 tests at `c5c3ab09` | — | `c5c3ab09` |
| 2 | the test fails when the builder omits any one component, passes otherwise; the Javadoc names the mechanism | met | `mutation-after` | `WorkerGenerationRecordsTest.workerGenerationBuilderEqualsConstructor` — `getRecordComponents()` over all 18, every value drawn away from its default; four mutations: `4dc074f6` (omitted `.model(...)`, NPE at build), `11e5f837` (`wires model`), `2c181dd6` (omitted `.planId(...)`, "Only a plan-worktree generation names its plan"), `64ac7d94` (`wires idleWakeups ==> 978 vs 979`) | `4dc074f6`, `11e5f837`, `2c181dd6`, `64ac7d94` | `8e19517a`, `13deceed`, `bca83765`, `41ce3e7e` |
| 3 | no `assertThrows` without a message; no message outside 20–60; the four-module run green with the base's method count; no other line of a `*RecordsTest` changes | met | `none` | 81 calls in 75 classes, messages 31–42 characters; a scan for a complete messageless `assertThrows(…);` in `*RecordsTest.java` returns none; four-module `test` green with 10162 tests at `f2b37066`; whole reactor 10548 = base | — | `f2b37066` |
| 4 | the paragraph is added, each claim probe-proved; the R17 check reports `BROKEN LINKS 0`; the lane says whether the run would have caught the old target | met | `none` | the section "What the rule does not see" at `runtime-guard.adoc:98-109`; a throw-away probe fixture with one class per gap was evaluated by rule A and the rule refused none (run `90aa52c8`, 7423 tests green); R17 run as the rule prescribes: `BROKEN LINKS 0` | — | `17cbe3f9` |
| 5 | no assertion without a 20–60 message; the failure report of a failing control still prints; `test -pl pm-runtime -am` green with the base's method count | met | `mutation-after` | `OutboundCallsArchTest` with `withReport(check, result)`; the DatagramChannel removed from `ChannelFixture` made `failsOnFixture` fail and the surefire XML `pm-runtime/target/surefire-reports/TEST-de.planmarshall.runtime.guard.OutboundCallsArchTest.xml` carries the full Architecture Violation listing after the message | `4b2edba3` | `8c69fda3` |
| 6 | one definition of the connection predicate; the fixture, symmetry and allow-list test counts unchanged; removing a type from the map still fails the symmetry test | met | `mutation-after` | `connectionEvidence` is the single definition, read by `connectionFree` and `opensConnection`; removing `core.profile.ShippedProfiles` from the map made `exemptionsAreLoadBearing` fail, naming exactly the missing entry | `1e742ecc` | `e336d77d` |
| 7 | the three edits are the only `CLAUDE.md` change; the link target exists; (c) names the decision and its date | met | `none` | `CLAUDE.md:127-129` (ArchUnit, both modules, decision A1 of 2026-10-29), `:147` "exemption map", `:148` "proves both within its set", `:149` Markdown link; the target exists; the diff of the file is those edits alone | — | `6d1e6494`, `61c11de9` |

Plan-level: no `pom.xml` in the diff; `OutboundCallsArchTest` is the only Java file D5 and D6 touch; no `*RecordsTest` class changed outside D3; `03-audit-and-outbound-https.adoc` is not modified (the 2026-11-14 correction).

Per-head test runs: `c5c3ab09` 10548, `8e19517a`/`f2b37066` 10162 (four modules), `17cbe3f9`/`e336d77d`/`6d1e6494`/`13deceed`/`41ce3e7e` 10548, `43c8eaca` (the merge with `origin/main` `f82f3e73`) 10555. `13deceed` is the pre-commit rewrite of the D2 test (`throws ReflectiveOperationException` → `throws Exception`), committed as the gate produced it.

## 2. Personas and skills

Per step: `persona-implementer` for D1, D2, D3, D5 and D6 (production code, unit tests, test refactor); `persona-documenter` for D4 and D7; `persona-code-reviewer` for the three self-review rounds and for every PR comment.

Per deliverable, the skills the spec declared, all read before the work: D1 `pm-dev-java:java-core`, `pm-dev-java:java-lombok`, `pm-dev-java:java-null-safety`; D2 `pm-dev-java:junit-core`, `pm-dev-java-cui:cui-testing`; D3 the same two; D4 `pm-documents:ref-asciidoc`, `pm-dev-java:junit-core`; D5 `pm-dev-java:junit-core`, `pm-dev-java-cui:cui-testing`; D6 `pm-dev-java:java-core`, `pm-dev-java:junit-core`; D7 `pm-documents:ref-asciidoc`. `skills added: none` — no deliverable left the spec's Expected Surface.

## 3. Hypotheses

- `JudgmentClient.java:195`, `:200`, `EndpointEnrolment.java:96`, `:103`, `ProviderVerification.java:218`, `:242` — **confirmed** at the branch base for `EndpointEnrolment` and `ProviderVerification`; `JudgmentClient` no longer exists (#396), and `EndpointEnrolment`'s four sites and `ProviderVerification`'s two were converted.
- The leaf's parse found every positional site — **refuted in part**: `ForgeUrl:291,344` and the five `KindMapping` constructions of `ActivityEventMapping:86-93` are positional but sit in the parser and codec-adjacent classes the scan exempts; the lane decided both stay and says so here and in the PR body.
- The R17 check did not catch the dead cross-repository target — **confirmed empirically**: with the old `{docs}/specification/client-profiles/04-judgment-endpoint.adoc` link temporarily restored the check still reported `BROKEN LINKS 0`, while a dead in-repo target produced `BROKEN LINKS 1`. The run this plan made would not have caught the old target.

## 4. Findings

Self-review, three rounds:

- Round 1 (no blocker, 8 items): D2's comparison was blind to the two components constructed `null` (`planId`, `worktreeMark`) and to a zero `idleWakeups` — **fixed** in `bca83765` (every value drawn away from its builder default, re-proved with `2c181dd6` and `64ac7d94`). `CLAUDE.md`'s "proves both" contradicted the new blind-spot section — **fixed** in `61c11de9` ("within its set"). Left as reported: the D5 message is check text plus the report by the spec's own "failure-detail method"; the D6 sentence test compares two literals by the spec's prescription; the redundant `equals` assertion kept; six D3 messages name the component loosely but stay in range; 10 commits for 7 deliverables (D2 and D7 own two each).
- Round 2: both fixes re-graded closed; no defect; the round reported clear of defects.
- Round 3: Javadoc wording of D2 corrected in `41ce3e7e`; the round reported no defect open and clear.

PR comments: no human or bot finding was posted as a review body or a thread (0 threads, 0 review-body findings); the only bot comments are the two rate-limit refusals and Sonar's gate summary.

## 5. Reviewers

- **coderabbit** (required, `quota_limited: true`, `review_complete_after_handling: true`): refused on quota at 2026-11-15T00:37Z ("Next included review available in 26 minutes") and again at 02:22Z. The quota policy was followed: a 90-minute wait spent with `ci pr wait-for-comments` (12 calls), then the trigger again at 02:22Z, then the escalated `@coderabbitai full review` at 04:09Z, which it accepted ("Full review triggered") and completed as a review in progress. A qualifying review is on record and `review-gate` reports `all_handled` with 0 unresolved threads and 0 body findings — the standing review authorization for a `review_complete_after_handling` reviewer; no re-trigger was posted for the merge head.
- **sourcery** (optional): refused on its own budget (250,000 diff characters in 7 days; next in 1 day 11 hours). Optional, so no gate.

## 6. Operator questions

None asked — no blocker, no rule needed bending, the required reviewer was obtained through the quota policy's own steps. No free-text operator message was received. (A question tool was available; none was needed.)

## 7. Rule check

- Guard: `none` on this harness (unenforced) — the resolve result reported `guard: none`, so no guard log was consulted and nothing is claimed about enforcement.
- Executor skew: `match` (executor 0.1.2123, lane 0.1.2123) before `resolve`.
- Spec read: `spec_source: ledger_worktree`, no `spec_differ` marker.
- The R17 check ran exactly as the standing rule prescribes — `python3 /home/oliver/git/plan-marshall-documentation/.claude/skills/doc-review/scripts/trace.py …` from the main checkout after a `git fetch` of the documentation checkout, with the worktree as the project directory. That is not a plan-marshall script and has no executor notation; the epic's R17 names this command as its only form. Two probe runs used it the same way (with a temporarily restored old link, and with a dead in-repo link).
- Shell discipline: every plan-marshall script went through `python3 .plan/execute-script.py` from the main checkout; no `git grep`, no inline Python, no `cat`/`ls`/`find`/`sed`; files were read and edited with the file tools; the two probe fixtures were created and deleted as files in the worktree, never in the main checkout.
- Two verb refusals, both corrected without a workaround: `maven run --command-args "test -pl pm-runtime -am -Dtest=WorkerGenerationRecordsTest …"` answered `indeterminate` (`test_selector_matched_nothing`, the selector matched no test in `pm-core`), so the module run was used instead; `sonar-facts --open-count 0 --rules none` refused `confirmed_zero_with_rules` ("a confirmed zero carries no rule left open"), so the flag was dropped.
- Nothing under `.plan/` was written except `.plan/temp/`, the prepared CI body slots, and this landing message; no file under `.plan/orchestrator/` was edited.
- Builds: every routed build went to the daemon (`mechanism=daemon_longpoll`), none degraded, no launch-fault retry, no killed job.

## 8. Build server

Setup preflight on the worktree: `ready` (registered, version 1). Per-mechanism build count: 20 builds, all `daemon_longpoll`, `0` `in_process_fallback`. Fallbacks: none (no degraded submit, no `executor_absent_from_worktree`, no launch-fault retry). The one `indeterminate` result was the wrong `-Dtest` selection, not a build failure. At `43c8eaca`: `verify` green (10555 tests), after `43c8eaca` was produced by merging `origin/main` `f82f3e73` on the lane's pre-merge check. `verify -Ppre-commit`, `test`, `verify` and `verify -Pcoverage` were all green at `41ce3e7e` as well.

## 9. Residue

Nothing open. CI green at `43c8eaca` (20 checks, 0 failing); SonarCloud `pullRequest=432`: 0 new issues, 0 hotspots, gate OK, 97.8 % new-code coverage; `sonar-gate` `merge_allowed: true` (`no_open_issues`); `review-gate` `all_handled` with 0 unresolved threads and 0 review-body findings. The base worktree used for no evidence (§1's recipe was not needed) — `cleanup_owed: false`; the plan worktree was removed, the local and remote branch pruned, and the main checkout fast-forwarded to `87e14af3`.

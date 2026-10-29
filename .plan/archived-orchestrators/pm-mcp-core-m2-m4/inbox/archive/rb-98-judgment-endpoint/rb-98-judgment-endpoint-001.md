envelope_version=1
sender_type=plan
sender_id=rb-98-judgment-endpoint
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-28T21:53:23Z

```landing-facts
schema=landing-facts/1
plan_id=rb-98-judgment-endpoint
epic=pm-mcp-core-m2-m4
pr=#222
merge_state=merged
cleanup_owed=false
deliverables_total=8
deliverables_done=8
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2028
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
step.sonar-roundtrip.open_issue_count=0
step.sonar-roundtrip.open_issue_rules=none
```

guard: none on this harness (unenforced)

## 1. Deliverables

PLAN-98 `judgment-endpoint`, merged as `d4d25e2a566f479c67ef12a6742f21f99df4ff1d` (PR #222). Every Done-when is graded **met**; the six fixture clauses carry the `mutation-after` order (a failing run was produced by reverting the committed fix and recorded by commit and surefire report).

- **D1 — Implements Client Profiles § Judgment Endpoint.** met. order: none (documentation and traceability). Commit `322d1b26` and `a01a316a`. The specification part's sections are marked implemented, `PM-WATCH-CLIENT-15` retired, the package file and index row removed, and `PM-CLIENT-2` gained its `Implementation:`/`Verified by:`/`Documentation:` lines.
- **D2 — The request.** met. order: mutation-after; test `SystemOneRequestTest.oppositeOrders` and `JudgmentEndpointTest.bothOrders`; failing run: mutation at `2a5e006a` (second body composed ascending) → both tests failed (surefire `TEST-…SystemOneRequestTest.xml`, `TEST-…JudgmentEndpointTest.xml`); passed at `1d628223`.
- **D3 — The answer.** met. order: mutation-after; tests `JudgmentEndpointTest.refusesProbabilitiesThatDoNotSum`, `SystemOneAnswerTest.invalidAnswer[8]`, `SystemOneAnswerTest.tolerance`; failing run: mutation at `2ce910f8` (sum tolerance removed) → three tests failed; passed at `1d628223`.
- **D4 — Versioned ids only.** met. order: mutation-after; tests `JudgmentEndpointTest.refusesAnAlias`, `SystemOneAnswerTest.modelAlias`; failing run: mutation at `2ce910f8` (model-id check skipped) → `refusesAnAlias` failed; passed at `1d628223`.
- **D5 — The remote form.** met. order: mutation-after; tests `JudgmentEndpointRemoteTest.ownOriginSucceeds`, `redirectToAnotherOrigin`, `redirectToHttp`, `refusesAProgramEnrolment`, `refusesAnotherOrigin`, `refusesAnotherCredential`, `missingCredential`, `originMismatch`; failing run: mutation at `2bc1fdb1` (redirect hosts allowlisted) → `redirectToAnotherOrigin` failed with a second request reaching the target; passed at `1d628223`. The operator-only enrolment of the origin (no project configuration adds or changes `base_url`) is the onboarding's, in another repository; this package reads the enrolled binding and refuses a mismatch.
- **D6 — The request body is never logged.** met. order: mutation-after; tests `JudgmentEndpointTest.leavesTheBodyOutOfLogsAndAudit`, `JudgmentEndpointRemoteTest.leavesBodyAndKeyOut` (log line, audit record **and** the error detail); failing run: mutation at `2bc1fdb1` (response body appended to the detail) → `leavesBodyAndKeyOut` failed; passed at `1d628223`.
- **D7 — Verification of a judgment endpoint.** met. order: none (no fixture clause names it; covered by `JudgmentVerificationTest`, 6 tests: listing/identity, the four probes, free text refused, `limits` only on the choice, a `400` with `max_tokens_exceeded` as the bound, any other refusal ending the stage).
- **D8 — Left out.** met (nothing built for the vote, the class rule, the calibration, the profile/harness, the credential entry or the user documentation).

Per-head test runs against pushed heads (`module-tests`/`test-compile` are not published as canonicals by this project; the gate canonical is `verify -Ppre-commit`, run by CI's `build` job on every head): green on `1d628223` (5803 tests, local), `3043ac68` (CI), `52fcdd89` (CI), `a01a316a` (CI), `05260fe1` (CI, merge head).

Sonar: `open_issue_count=0` confirmed on the merge head `05260fe1` (quality gate passed, 0 new issues, 82.1 % coverage on new code). The 31 issues the first analysis reported were fixed in `3043ac68`, `271f3805` and `a01a316a`.

Links (R17): `BROKEN LINKS 18`, every entry a deleted watch item in the sibling (`roadmap`, `requirements`, `articles`) or the pre-existing in-repo `PM-WATCH-CLIENT-14` link in `model-work/01`; the two links this plan would have broken (`judgment-endpoint.adoc`, `PM-WATCH-CLIENT-15` in `client-profiles/04`) are fixed.

Build: `verify -Ppre-commit`, `verify` and `verify -Pcoverage` green at `3043ac68` (5803 tests each, local, in-process); after that head only review fixes and two base merges followed, and CI's `build` job ran the project gate green on `52fcdd89` and on the merged head `05260fe1`. The two integration profiles were attempted locally three times and stop at the pre-existing environment-dependent `BuildGuardsIT.fixturePasses[1]` (registry connect timeout, reproduced on the pristine `main` checkout); CI's `integration-tests` job is green on every head.

## 2. Personas and skills

- Persona `implementer` for the production code, `module-tester` for the unit tests, `code-reviewer` for the self-review rounds and the triage of every PR comment, `security-expert` for the remote form's credential and origin rules.
- skills: none named (the spec declares no `Skills:` or `Persona:` lines); `skills added: none`.

## 3. Hypotheses

- The spec's Expected-Surface hypothesis was confirmed and corrected at the outline: the endpoint package exists on `main` (PLAN-97, #214), the enrolment lives in `runtime.launch` (not `runtime.enrolment`), and `runtime-endpoint.adoc`/`runtime-endpoint/adr` exist. No HYPOTHESIS line in the spec's Claim Labels remained unconfirmed: the two `OBSERVED` lines marked contradicted were re-scoped by the spec's own Re-grounding Record before this run and were read as facts, not hypotheses.
- Claim 0 (spec exists): confirmed — `doc/specification/client-profiles/04-judgment-endpoint.adoc`, `PM-WATCH-CLIENT-15` at `client-profiles.adoc:173`.
- Claim 1 (no write package builds it): refuted for `endpoint-harness` (shipped) and confirmed that no other package implements `system_one` before this run.

## 4. Findings

- Self-review round 1 (sub-agent, `persona-code-reviewer`): no blocker survived; majors F1 (the loopback judgment task had no owner check), F2 (mixed enrolment forms read silently) and F3 (readers of a program entry derefernced a null path) fixed in `ed6f64ef`; minors F4–F18 fixed or recorded.
- Self-review round 2: N1–N11, N13 fixed in `1d628223`; N12 (two limits bisections) deferred and recorded.
- Self-review round 3: nothing open.
- CodeRabbit (round 1, head `271f3805`): major "answered task recorded failed" → fixed `52fcdd89` (thread answered and resolved, reviewer confirmed); minor "remote `argv` weights probe runs a local command" → fixed `52fcdd89` (confirmed); minor "unreachable reports HTTP 0" → fixed `52fcdd89` (confirmed); trivial nitpick "guard before file writes" → fixed `52fcdd89`.
- CodeRabbit (round 2, head `52fcdd89`): minor "remote `base_url` port range" → fixed `a01a316a` (thread answered and resolved, reviewer accepted); unverified advisory "link the remaining production classes in Traceability" → applied `a01a316a`.
- Sourcery (optional reviewer): refused every trigger — "your pull request is larger than the review limit of 150,000 diff characters" (hard quota, four times). Recorded; optional and not a merge gate.

## 5. Reviewers

- `coderabbit` (required, `quota_limited`): reviewed `271f3805` (3 actionable findings) and `52fcdd89` (1 actionable finding, merge risk Low, `final_review_risk_coverage.coveredCommitId=52fcdd89`, `kind=reviewed`). The heads `a01a316a` and `05260fe1` were re-triggered after every push and answered "Review rate limited" (recorded at 21:16 and 21:36); under the standing authorization a qualifying review exists, every finding is fixed and every thread resolved, and every other gate holds on the merge head.
- `sourcery` (optional, `quota_limited`): refused for diff size, four times (16:14, 16:15, 20:22, 20:22); no review exists.

## 6. Operator questions

1. "The executor (0.1.2025) is older than the installed runbook-lane text (0.1.2028), so the lane refuses to run. How should I proceed?" — options: regenerate the executor now / stop the run. Selected: **"Regenerate the executor now"**.
2. "The local `verify -Ppre-commit` was externally killed (exit -9 …). How should I proceed?" — options: push and use the CI gate / retry the local gate. Selected: **"Push and use the CI gate"**.
3. Free text "retry" (twice) after `ci pr create` and the CI reads first answered "Not authenticated": the retries succeeded after the operator restored `gh` authentication; no option was selected in either, and the run continued on the answers it received.

## 7. Rule check

- `guard: none on this harness (unenforced)` — the resolve result reports `guard: none`; no guard log exists for this run.
- Own actions that broke a lane rule, recorded honestly: one `sed -n '15p'` read of the spec; one `sed -i` edit of `JudgmentEndpointTest`; one `git add -u` (immediately unstaged and re-staged by name); one `tail -n` on a build log and one `grep -n` on a worktree file. The first source edits landed in the **main checkout** by mistake (relative paths before the worktree was the CWD); they were copied to the worktree, the main checkout was restored with `git show HEAD:<path>` redirects and the new files removed there, and `git status --porcelain` of the main checkout is empty again. No user work was touched.
- `spec_source: main_checkout`, no `spec_differ` (one copy was read).
- `executor-skew`: `match` at 0.1.2028 after regeneration; the stale-executor start and the regeneration are recorded above.
- Restarts and re-dispatches: none (no server restart, no killed sub-agent); the self-review sub-agent was reused across its three rounds (`ses_e7d3765c…`).

## 8. Build server

- Setup preflight: **ready** (registered: true) for the worktree.
- Every routed build (`auto`) failed with `execution_failed` (the daemon runs `mvn`, which its environment cannot execute; empty log, exit −1). Every build of this run therefore used the resolved command with `--execution-mode in_process`; the wrapper applied its own bound: `timeout` at 300 s (floor) and once at 965 s/1500 s, and one build was **killed** externally (exit −9, 262 s in) — the operator chose to proceed on the CI gate. No degraded result carried a wrong mechanism: every result reported `in_process_fallback`.
- `runtime_stamp_reason`: none; `plugin_version` resolves to `unknown` (the seam reported no version), `executor_version` 0.1.2028.

## 9. Residue

- Deferred: the two limits bisections (`EndpointVerification` and `JudgmentVerification`) are not one helper; the `judgment_task_completed` audit record's identity shape is not stated in `store-schemas/12` (the schema admits it); the code review's three optional nits (no WARN line for a pre-send refusal, no negative fixture for a `400` without `max_tokens_exceeded`, `TASK_INVALID.wireName()` unused).
- Owed to the milestone exit: the repository-wide architecture rule and the recording-test-network integration test of Credentials & Enrolment § Closed List of Outbound Calls (#188); the user documentation that tells the operator to read the service's terms.
- Surface delta beyond the spec's declaration (R19): `AuditEvent`, `EndpointClient`, `EndpointOutcome`, `EndpointEnrolment`, `EndpointHarness`, `HarnessVerifier`, `LaunchComposer`, `AgentStartedDetector`, `ProfileStages`, `HarnessEnrolment`, `HarnessEnrolmentStore`, `ProfileValidator`, four `package-info` files, `EndpointProfileValidatorTest`, `EndpointProfileFixtures`, `LaunchComposerTest`, `AgentStartedDetectorTest`, and `doc/plans/decision-quorum-and-calibration.adoc` (its dead link to the deleted package file had to be corrected; it lies inside PLAN-107's declared surface — PLAN-107 has since landed, so the overlap is spent).

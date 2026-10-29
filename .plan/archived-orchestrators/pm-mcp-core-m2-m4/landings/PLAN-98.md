# Landing Analysis: PLAN-98 — The protocol `system_one` and the judgment endpoint (8 of 8 met)

epic: pm-mcp-core-m2-m4
workstream: WS-05
pr: #222 (merged as d4d25e2a, 2026-10-28, merge queue)

> Landing record. Source: inbox message `rb-98-judgment-endpoint-001.md` (runbook lane, opencode, deepseek-v4.1-flash/high, 2026-10-28T21:53:23Z), corroborated against git at
> `origin/main` and the SonarCloud public API. The message is a lead; every fact marked "checked" was read by me; no test was re-run and CI was not read through `ci`.
> The lane read the spec from the main checkout (`spec_source: main_checkout`, no `spec_differ`).

## Deliverable Fidelity vs Spec

The lane counts 8 of 8, each graded met with an order (`mutation-after` for the six fixture clauses: a failing run produced by reverting the committed fix, recorded by commit and surefire report).

| Deliverable | Verdict | Evidence |
|-------------|---------|----------|
| D1 implements Client Profiles § Judgment Endpoint | met | Checked on `origin/main`: `doc/plans/judgment-endpoint.adoc` is gone and the index has no row; `PM-WATCH-CLIENT-15` appears in no watch file; `PM-CLIENT-2` is in `doc/Traceability.adoc`. |
| D2 the request (opposite orders) | met | `SystemOneRequest.java` on `main`; lane cites `oppositeOrders`, `bothOrders` with a mutation run. |
| D3 the answer (sum, tolerance) | met | `SystemOneAnswer.java`; three tests failed under the mutation. |
| D4 versioned ids only | met | `refusesAnAlias`, `modelAlias`. |
| D5 the remote form | met | `JudgmentEndpointRemoteTest` (own origin succeeds, redirect to another origin and to `http` refused, no secret sent). Operator-only enrolment of the origin is the onboarding's (PLAN-46 family), not this package's. |
| D6 the request body is never logged | met | log line, audit record and error detail covered; one mutation run. |
| D7 verification of a judgment endpoint | met, order `none` | `JudgmentVerification.java`, 6 tests; no fixture clause names it. |
| D8 left out | met | nothing built for the vote (PLAN-99), the class rule, the calibration, the profile/harness, the credential entry, the user documentation. |

**Whole diff, checked:** 53 files (+3,813/−248); `pm-runtime` `endpoint` 10 main + 8 test, `launch` 5 + 3, `credentials` 3, `worker` 1 + 1, `pm-core` `profile` 3 + 3, three ADRs in `pm-runtime/doc/adr/runtime-endpoint`, the developer documents, `store-schemas`, the watch file. Under the 6-deliverable guard (8 deliverables, D1 and D8 are documentation/negative clauses): not a stall.

**Surface delta** (`inbox landing-check`): `unmeasured` (declared and realized sets not supplied to the check; the lane's own R19 list is in the message, section 9). The extra classes the lane names (`LaunchComposer`, `AgentStartedDetector`, `HarnessEnrolmentStore`, `ProfileValidator`, `EndpointClient`/`EndpointOutcome`/`EndpointHarness`, …) are the ripple of lifting the `ProfileValidator` refusals and the remote form; none is a surprise of another plan's.

## Metrics and Anomalies

- Tokens: `unknown` (the 45th landing), `plugin_version` `unknown`; `executor_version` 0.1.2028. Self-review: `clear` after three rounds.
- Anomalies: (1) the executor (0.1.2025) was older than the installed lane text (0.1.2028); regenerated on the operator's answer. (2) The first source edits landed in the **main checkout** (relative paths before the worktree was the working directory); copied over, the main checkout restored, `git status --porcelain` empty again (checked at the start of this session: `main` is clean). (3) Build daemon again could not launch `mvn` (`execution_failed`, empty log): every build ran in-process; one build was killed externally (exit −9) and the operator chose to continue on the CI gate. (4) The two integration profiles stop locally at the pre-existing `BuildGuardsIT.fixturePasses[1]` (registry connect timeout, reproduced on pristine `main`); CI's `integration-tests` was green on every head. (5) `gh` was unauthenticated twice; the operator restored it.
- Rule breaks disclosed: one `sed -n`, one `sed -i`, one `git add -u` (undone), one `tail -n`, one `grep -n`.

## Routing and Merge Behavior

- Review: **CodeRabbit (required)** reviewed `271f3805` (3 actionable) and `52fcdd89` (1 actionable, risk Low), all fixed and threads resolved; the heads `a01a316a` and `05260fe1` were answered "Review rate limited" twice. A qualifying review exists under the standing authorization, so this land is not the PLAN-43 gap. Sourcery (optional) refused four times on size (150,000 diff characters).
- CI: `build` and `integration-tests` green on the merge head `05260fe1` by the lane's account; not re-read. Checked: `d4d25e2a` is an ancestor of `origin/main`.
- Sonar: **checked: `pullRequest=222` quality gate OK, new coverage 82.1 %, 0 open issues; `branch=main` 9 open** (unchanged). The first analysis found 31, fixed in `3043ac68`, `271f3805`, `a01a316a`.
- Collision: PLAN-107 overlap on two documents, accepted on 2026-10-28, spent (PLAN-107 shipped, #215); no conflict recorded. Links (R17): `BROKEN LINKS 18`, all in the sibling documentation or the pre-existing `PM-WATCH-CLIENT-14` link; not re-run by me.

## Reconciliation Actions

- [x] row `status` → `shipped`
- [x] row `pr` `#222`, `landing` `landings/PLAN-98.md`, `plan_marshall_plan_id` `rb-98-judgment-endpoint`
- [x] PLAN-46 Dependencies line: the PLAN-98 sequencing is spent; PLAN-46 is ready to emit
- [x] epic.md Analyzed bullet; resume anchor; `queue-view.md` regenerated

## Follow-Ups

- **Unblocked:** PLAN-46 (its overlap with PLAN-98 on `credentials/` and `01-provider-credentials.adoc` is spent), and with it PLAN-108 and PLAN-109. PLAN-99 stays parked on PLAN-47 and PLAN-34 (the decision quorum has no package).
- **Deferred by the lane:** the two limits bisections (`EndpointVerification` / `JudgmentVerification`) are not one helper; the `judgment_task_completed` audit record's identity shape is not stated in `store-schemas/12`; three optional review nits (no WARN for a pre-send refusal, no negative fixture for a `400` without `max_tokens_exceeded`, `TASK_INVALID.wireName()` unused). No owner; small, recorded as Open Defects.
- **Owed to the milestone exit:** the repository-wide architecture rule and the recording-test-network integration test of Credentials & Enrolment § Closed List of Outbound Calls (#188); the user documentation telling the operator to read the service's terms.

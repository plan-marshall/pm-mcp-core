# Landing Analysis: PLAN-05 — HTTP baseline

epic: pm-mcp-core-m2-m4
workstream: WS-08
pr: #28 (https://github.com/plan-marshall/pm-mcp-core/pull/28), merge commit 268d2c0

> Landing record for one shipped plan. Lives at `landings/PLAN-05.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source: inbox message `http-baseline-010.md` (landing-facts complete, no missing key),
corroborated on 2026-10-11 against the PR state (MERGED 14:38Z), the file list of 268d2c0, the
`/review` comments and `cuioss-review-bot` runs on #28, and the plan's archived decision log.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| Provider HTTP tests on `cui-test-mockwebserver-junit5`; the hand-made `FakeServer` classes removed | shipped-as-specified | `FakeServer.java` deleted in `pm-provider-ci`, `-github`, `-gitlab`; `CiHttpClientTest`, `GitHubClientTest`, `InstallationTokensTest`, `GitLabClientTest`, `GlabCommandsTest` rewritten; test dependency added in the three provider POMs |
| `CiHttpClient` goes through `cui-http` | shipped-modified | Verify-first found it already does; shipped as the guard test `sendsThroughCuiHttp` instead of a production change |
| Own package file and index row | shipped-as-specified | `doc/plans/http-baseline.adoc` added; index row added |
| Documentation | not needed | The package's trace matrix names no document to change; no documentation pull request |

Surface: within the declared entries except `GitLabClient` and `GlabCommands` (production,
whitespace-only import-block edits from `verify -Ppre-commit`, operator-approved). The root
`pom.xml`, declared, was not touched: the dependency went into the provider POMs only.

## Metrics and Anomalies

- Tokens: 5,134,663 recorded; wall time 72,409 s (about 20.1 hours).
- Two review-driven fix rounds: single-use mock responses (a stubbed route answered a duplicate
  mutation with success), and IPv6-safe mock server URLs (71c8256).
- `scope_creep_check` failed twice on the same finding-type defect as PLAN-01.

## Routing and Merge Behavior

- Review: first plan whose manifest carried `re_review_on_loopback: true` from the start. After
  each fix commit plan-marshall posted `/review` itself (09:05:33Z after 4d6b08f, 09:48:26Z after
  71c8256); both `cuioss-review-bot` runs (38127136728, 38129685654) succeeded. No stall, no operator
  override — the PLAN-38 setting works end to end.
- CI/merge: merge queue, 268d2c0.
- Collisions: none.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#28`; `landing` `landings/PLAN-05.md`
- [x] 9 lessons dispositioned: 8 routed to plan-marshall (`pm-mcp-core-m2-m4-057.md` to `-064.md`, including the two `cui-http-testing` rules of the `pm-dev-java-cui` bundle), 1 folded as a recurrence into lesson 2026-10-10-14-001 (pre-commit recipe)
- [x] epic.md narrative reconciled; resume anchor; `queue-view.md` regenerated

## Follow-Ups

- WS-08 closes with this plan.
- Every later HTTP test follows this baseline: route responses are single-use and the client URL
  comes from the mock server, never a hard-coded `127.0.0.1`.

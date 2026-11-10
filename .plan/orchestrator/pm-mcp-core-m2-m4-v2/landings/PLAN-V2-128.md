# Landing Analysis: PLAN-V2-128 — make a pull-request number mean a pull request

epic: pm-mcp-core-m2-m4-v2
workstream: WS-09
pr: #348 (merge `c2b839c9b426954210ae9a784e2b5b1c731e0ca4`)

> Landing record for one shipped plan, written by the `analyze` inbox drain of 2026-11-10 from the runbook-lane
> message `rb-v2-128-ci-contract-pull-request-numbers-001.md` (harness opencode, model deepseek-v4.1-flash/high).
> The message is a lead; every figure below was re-read from git, the CI abstraction or the Sonar public API.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 a pull-request tab URL names its pull request | shipped-as-specified | `ForgeUrl.java:269-349` (tab grammar per forge), `ForgeUrlTest` `namesPullRequestOfTabUrl` (9 cases) and `refusesTabsOutsideThePullRequestGrammar`; the pinned 22-group refusal stays. The segment bound became tab-aware (`ForgeUrl.java:256-257`), a decision the spec left open (L1-1). |
| D2 `ForgeUrl.issue()` | shipped-as-specified | `ForgeUrl.java:188-190`; `ForgeUrlTest.java:92-100, 178-184`; `IssueRef` is constructed in one place only (`ForgeUrl.java:189`). |
| D3 `issueComment` on a pull-request number | shipped-as-specified | `GitHubCiProvider.java:177-201` (guard read, same client for read and post, failure propagated, `is a pull request` refusal); `GitLabCiProvider` unchanged, limit documented in ADR 010. |
| D4 the statements say what the code does | shipped-modified | ADR 010 added, ADR 009 status note, guides and `CiProvider` Javadoc updated. The spec's "Javadoc of `GitLabCiProvider.issueComment`" item was not done (the GitLab limit is stated in `CiProvider.issueComment` and the guides only). The ADR 010 link sits in PM-IMPL-1 (`doc/Traceability.adoc:1775`), not in the PM-EXEC-5 Documentation lines (`1031-1033`) the narrative names (L2-1). |

Facts block: `deliverables_total=4`, `deliverables_done=4`: borne out. No file outside the spec's Expected Surface was touched (12 files).

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`, resolved on `orchestrator.analyze`), reason: the landing is a 12-file, three-module change whose Done-when rests on parsing and on a two-request guard; reading it is the depth the dispatch rule names, and its context must not mix with the 97 and 99 reads of the same drain.
- **Verdict:** layer 1 findings (minor); layer 2 findings (minor); layer 3 clean of defects by reading (minor residues, two points not shown by reading).
- Points re-read by the orchestrator before recording: `Traceability.adoc:1031-1033` and `1773-1776`, `ForgeUrlTest.java:150-155`, `ForgeUrl.java:254-258` and `331-335`, `GitHubClient.java:303-311`, `GitHubCiProvider.java:185-194`, and the `new IssueRef(` search. All held. Points not re-read are recorded as the leaf's reading only.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence |
|---------|-------|---------|----------|----------|
| L1-1 | The spec never mentions `MAX_SEGMENTS = 25`; how a tab interacts with the bound at the 21-group limit was decided by the lane, not by the spec. No clash with the pinned 22-group refusal. | checked | refutes | `ForgeUrl.java:61`, `256-257` |
| L1-2 | Traceability scope is stated three ways ("the one line", "the lines of the requirement", "the lines of the PM-EXEC-5 paragraph"), and the correction of lines 981-982 is conditional on R08. | checked | refutes | spec R08, D4, Expected Surface |
| L1-3 | The OBSERVED premises (resolver shapes, no `issue()`, unguarded GitHub `issueComment`) held on the pre-change code. | checked | confirms | `ForgeUrl.java:244-294` (pre-change) |
| L1-4 | The closed tab lists and "`issues: write` implies the issue read" are HYPOTHESIS; the forge documentation cannot be read from the repository. The operator confirmed the lists at refine. | not_shown_by_reading | none | — |

**Layer 1 verdict:** findings — 2 minor.

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|-----------|--------------------|----------------------------------|---------|
| D1 | `ForgeUrl.java:269-349` | `ForgeUrlTest` tab cases (every tab URL was `INVALID` before) | none | checked |
| D2 | `ForgeUrl.java:188-190` | `ForgeUrlTest.java:92-100, 178-184` | none | checked |
| D3 | `GitHubCiProvider.java:185-194` | `refusesCommentOnPullRequest`, `propagatesFailedGuardRead` (404/403/429/500 each with a 201 POST available), `postsComment` (2 requests), `postsAppCommentWithUserToken`; GitLab `postsCommentWithoutReadingAMergeRequest` (1 request) | none | checked |
| D4 | docs, ADR 009/010, `CiProvider` Javadoc | — | ADR 010 link placed at PM-IMPL-1 instead of PM-EXEC-5 (L2-1); the pr-view paragraph addition is a third Traceability hunk outside the R08 reasoning (L2-2) | checked |

- **L2-1 (minor):** the narrative says the ADR link is in "that section's Documentation line"; it is at `Traceability.adoc:1775` under PM-IMPL-1, and PM-EXEC-5's own list (`1031-1033`) ends at ADR 009. The requirement the plan implements carries no link to the new ADR.
- **L2-2 (minor):** the Traceability edit is three hunks against R08's "the one line"; the correction of the false sentence is defensible, the pr-view paragraph addition is not covered by that reasoning.
- **L2-3 (minor):** the narrative lists "Forgejo tab on GitLab list" as a negative case; the test has the reverse (`ForgeUrlTest.java:151-154`, a GitLab tab on Forgejo). Untested negatives: an unknown tab on a GitLab merge request (the `!GITLAB_PULL_REQUEST_TABS.contains` disjunct at `ForgeUrl.java:333`: deleting it passes every test), GitLab two trailing segments, GitLab zero/padded number with a tab, Forgejo `issues/N/files`, a 22-group path with a tab (the `MAX_SEGMENTS + 1` clause at `ForgeUrl.java:257`).
- **L2-4 (not shown):** the failing-first claims rest on job ids; test and production are in one commit per deliverable (`a9c978b8`, `a6007ea5`, `e48cd00e`), so the order cannot be read.
- **L2-5 (info):** the PR body says Sonar was `not_configured` while the narrative reports a plain-HTTP read (0 issues, gate OK); the two differ for a reader of the PR alone.

**Layer 2 verdict:** findings — 4 minor, 1 not shown by reading.

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| none that is a defect | — | — | — | — |
| L3-1 | `GitHubClient.java:305-310` | a `200` answer to the guard read without a non-blank title, state or `html_url` | the guard read returns `FAILED` and no comment is posted on a plain issue; before the change the post did not depend on the full issue parse. Probably unreachable for a real GitHub issue. | minor |
| L3-2 | `ForgeUrl.java:256-257, 306-310` | a 26-segment path whose last segment is a tab of any forge | the bound pre-filter passes it (union of the three tab lists); the forge resolvers then refuse it. No wrong acceptance found. | info |
| L3-3 | `ForgeUrl.java:271, 322` | any refused URL | the `INVALID` detail strings changed; no test asserts them, but they are observable values outside "no behaviour change beyond the named ones". | minor |

The tab parsing, the segment bound, the path-injection route (tab matched against a closed list; owner, name and group segments through `RepositoryRef.isSegment`), the GitHub guard ordering (refusals before any request, same `userClient()` for read and post, failure propagated without a post) and the GitLab limit were read and are correct.

**Layer 3 not shown by reading:** L3-4 whether the installation or user token needs more than `issues: write` for `GET issues/N` (a mock server cannot enforce permissions), and whether the `403/429/500` mapping matches real GitHub bodies (rate-limit `403` vs permission `403`).

**Layer 3 verdict:** clean of defects — 2 minor residues, 1 info, 1 not shown by reading.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md Code Standards | `@NullMarked`; `Optional` only as return type | conforms | `ForgeUrl.java:188`; the new local is a primitive (`ForgeUrl.java:314`) |
| CLAUDE.md Code Standards | no generic catch | conforms | `ForgeUrl.java:292/346` catch `IllegalArgumentException` |
| CLAUDE.md Code Standards | `LogRecord` constants asserted with `LogAsserts` | conforms | no log call added |
| CLAUDE.md Code Standards | assertion messages of 20 to 60 characters | conforms | added messages measure 21 to 49 |
| CLAUDE.md Code Standards | test data from the generators, a literal only where it is the contract | deviates | `GitHubCiProviderTest.java:547-560` uses the literals `7` and `/repos/cuioss/pm/issues/7` with no static config forcing them; the 22-letter nesting path and `issueBody(5, …)` are unlabelled literals |
| CLAUDE.md Code Standards | HTTP tests without a hand-written `Dispatcher` | conforms | existing `ModuleDispatcherElement` and static `@MockResponseConfig` |
| CLAUDE.md Module Documentation | developer guide and one ADR per decision in the plan's PR | conforms | ADR 010, ADR 009 status note |
| CLAUDE.md Build Rules | build through Maven, `failOnWarning` | not_shown_by_reading | no build run in a reading; CI green on the heads per the narrative |
| spec Standing Constraints | the split is closed by types; no merge-request request on GitLab; no new log record | conforms | `IssueRef`, `PullRequestRef` and the GitLab adapter unchanged |

## Metrics and Anomalies

- Tokens: `unknown` (the opencode harness measured none; recorded as unobserved, not as zero). `plugin_version` `unknown`.
- Duration: not supplied.
- Anomalies: the reactor `verify -Ppre-commit` hit its 936 s bound after 7,441 tests; the plan's own fallback (per-module gates of the three changed modules, 344 tests) was run at three heads. One job queued 1,006 s. CodeRabbit re-triggers at 23:33, 00:03 and 00:46 were rate-limited or answered "already reviewed".

## Routing and Merge Behavior

- Review: `runbook_lane review-gate --pr-number 348`: `unresolved_review_threads: 0`, `review_body_findings_total: 0`, `review_body_findings_handled: 0`, `merge_allowed: true`: equal to the landing's claim. `handled` is trivially reproduced (nothing to handle); the progress file has no `finding:` lines.
- CI/merge: merged through the queue as `c2b839c9`; queue state read from the PR: `pr_state: merged`. No rebase conflict reported; no collision with the concurrently running 97 and 99 observed (128 touched `pm-provider-ci`, `-github`, `-gitlab` and docs; 99's `#342` touched `GitHubClient.java` in a different region).
- Sonar: the landing claims `sonar_open_new_issues=0`; the public API read of `pullRequest=348` gives 0 open issues, gate OK: equal.

## Reconciliation Actions

- [x] row `status` → `shipped`
- [x] row `pr` stamped `#348`
- [x] row `landing` stamped `landings/PLAN-V2-128.md`
- [x] row `plan_marshall_plan_id` stamped `v2-128-ci-contract-pull-request-numbers`
- [x] `epic.md`: the Open Defect for missing `total_tokens` and `plugin_version` counts this landing
- [x] folds: the untested negatives, the literal test data and the Traceability link placement into the owning plans (see Follow-Ups)
- [x] resume anchor and `queue-view.md` regenerated

## Follow-Ups

- L2-3 (untested negatives) and the literal test data of `GitHubCiProviderTest` fold into PLAN-V2-126 (test conformance of the runtime and the providers), Expected Surface extended in the same edit.
- L2-1 and L2-2 (the ADR 010 link on PM-EXEC-5; the third Traceability hunk) are one documentation correction; they ride PLAN-V2-125's carried items as a recorded note (no code surface).
- L3-1 and L3-3 are minor and recorded here only; no staged fix plan is requested.

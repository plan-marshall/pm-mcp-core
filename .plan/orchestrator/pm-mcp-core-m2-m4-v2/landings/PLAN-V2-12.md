# Landing Analysis: PLAN-V2-12 — The CI contract module `pm-provider-ci` with the GitHub and GitLab adapters

epic: pm-mcp-core-m2-m4-v2
workstream: WS-09
pr: #281 (merge commit `51f551fc`, `51f551fc05ec8c68bcef768ae14360a17556fb4e`; 60 files, +3960/-53)

> Landing record for one shipped plan. Lives at `landings/PLAN-V2-12.md`. Written by the `analyze` verb after verifying
> claims against ground truth (actual code, artifacts, PR state); a pasted claim is a lead, never a fact.

Source: inbox message `rb-v2-12-ci-contract-module-001.md` (runbook lane, harness opencode, model `deepseek-v4.1-flash/high`,
effort high, executor 0.1.2041, standing rules r5 R02–R08, R10–R12, R17–R20). Corroborated against `origin/main` (the merge
diff of `51f551fc`) and the PR state read through `ci pr view` and `ci checks status`.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 — the module and its three dependency rules | shipped-as-specified | new `pm-providers/pm-provider-ci` (POM, README, package), `pom.xml` (+36: `provider` include, `provider-analysis` enforcer execution, `dependencyManagement`), three fixtures under `src/guard-controls`, `BuildGuardsIT` (+13); `CLAUDE.md` § Dependency Rules and § Build Commands |
| D2 — the contract and the URL grammar | shipped-as-specified, one signature deviation | `CiProvider`, `CiProviders`, `ForgeUrl`, `Identity`, `Issue`, `IssueComment`, `IssueRef`, `RepositoryRef`, `TokenKind`, `package-info`; `ForgeUrlTest`, `CiProvidersTest`, `ValueTypesTest`. `whoami(@Nullable RepositoryRef)` replaces the spec's `whoami(Optional<RepositoryRef>)` (CLAUDE.md: `Optional` is a return type only), recorded in the guide |
| D3 — the GitHub adapter | shipped-as-specified | `GitHubCiProvider` (294), `GitHubClient` (+78), `GitHubOperation` (+`ISSUE_READ`); `GitHubCiProviderTest` (491), `GitHubClientTest` (+57), `GitHubOperationTest` |
| D4 — the GitLab adapter | shipped-as-specified, one behaviour beyond the spec | `GitLabCiProvider` (238), `GitLabClient` (+55); `GitLabCiProviderTest` (337). The merge-request probe on a missing issue is not in the spec (see C1) |
| D5 — the documents | shipped-as-specified | `provider-ci.adoc` (172), ADRs `001`–`005` of the module, repo ADR `014`; the ADR index |
| D6 — the specification and the index | shipped-as-specified | `doc/plans/README.adoc` (the `forgejo-provider` row `open`), `01-provider-credentials.adoc`, `Traceability.adoc` (+45), the stale "no module yet" statements |

Surface delta: **60 files**. Inside the declaration: the new module, the two adapters and their clients, the root POM, the
fixtures, `BuildGuardsIT`, the specification and the ADRs. Outside it (named by the lane or visible in the diff):
`pm-provider-http` (`HttpClient` +37, `ResponseWithHeaders` new, `HttpClientTest`), `pm-provider-sonar/pom.xml`,
`.opencode/rules/plan-marshall-standing-rules.md`, four specification files that carry one-line pointers. No source file of a
package other than the CI module, the two adapters and the HTTP base was edited.

## Metrics and Anomalies

- Tokens `unknown`, duration not measured, `plugin_version` `unknown`: the open defect in `epic.md`, eighth recurrence.
- Self-review `budget-exhausted` after three rounds (operator accepted); the lane's own rule check lists the shell file
  operations it issued, one first edit made in the main checkout and reverted, and several intermediate heads pushed untested
  and superseded by a tested one.
- Sonar: the lane's `resolve` reports `not_configured` (`open_issue_count=n/a`), the PR's `SonarCloud Code Analysis` check is
  `SUCCESS`: consistent; the `n/a` is the lane's unreadable Sonar.
- One `verify -Pcoverage` hit the daemon's 545 s bound; the per-module coverage runs are the lane's coverage evidence
  (`pm-provider-ci` 96.26 % line / 89.63 % branch, lane-stated, not re-read).

## Routing and Merge Behavior

- Review: CodeRabbit (required, quota-limited) reviewed `c4814e19` (and earlier `60ece3af`); the final head `c01f2419` was
  refused after the last push; the lane merged under the standing review authorization because a qualifying review exists and every finding of two rounds is fixed or answered. Same shape as PLAN-V2-01 and 05. Sourcery refused on size.
- CI/merge: `ci pr view --pr-number 281` reads `merged` with `51f551fc`; `ci checks status` reads `overall_status: success`
  over 20 checks (`deploy-snapshot` skipped on the PR, as always).
- Collision check: ran beside PLAN-V2-110, 08, 11. No collision visible in the diff.

## Three-Layer Analysis (standing rule R21, r5)

Done at the merge commit `51f551fc` by a read-only review (a delegated reader over the CI module, both adapters, the HTTP
diff, the root POM, `BuildGuardsIT`, the fixtures, the tests and the documents), then the points marked **checked** re-read by
the orchestrator in the code. Nothing was built or run. What only a run could show is marked "not shown by reading".

### Layer 1 — Specified: sound; the pull-request ambiguity is under-specified

- **S1 (low, accepted).** The spec's `whoami(Optional<RepositoryRef>)` conflicts with CLAUDE.md; the lane used `@Nullable`
  (`CiProvider.java:44`). The staged spec still carries the `Optional` signature, so a plan that reads it (PLAN-V2-62, the
  consumers) inherits it. The guide names the null rule "R20"; CLAUDE.md's R20 is the HTTP-test rule and the null contract is
  ADR 013 (documentation slip).
- **S2 (medium).** The `issueComment` pull-request gap the lane listed is real and ADR 004 covers only `issue-view`. On GitHub
  `issues/{n}/comments` takes a pull request number (an `issues: write`-only installation token is refused on a pull request, so
  the caller gets `PERMISSION_DENIED`, not an "is a pull request" rejection). On GitLab `issues/{iid}/notes` with a merge
  request's iid comments on an unrelated issue, because the two are numbered apart. `IssueRef` carries no kind
  (`IssueRef.java:21`), so a consumer converting a `ForgeUrl` of kind `PULL_REQUEST` loses the guard.
- **S3 (low).** `Identity.permissions` is a `Map<String,String>` with no portable vocabulary (GitHub `push`/`pull`/`admin` as
  `"true"`/`"false"`, GitLab `project_access` as a number); the `checks_visible` and `head_writable` consumers cannot read it
  uniformly.
- **S4 (low).** PLAN-V2-62 does not match what PLAN-V2-12 built: it says `ForgeUrl` "returns a `PullRequestRef`" with its own
  type, PLAN-V2-12 returns a `ForgeUrl` record with a `Kind` over a `RepositoryRef`, and it says the first plan's grammar tests
  stay unchanged. Its own text tells the lane to follow the merged code where a name changed.
- **S5 (low).** The spec says the token is "narrowed by a new `ISSUE_READ`", but the provider receives a pre-built `HttpClient`;
  the narrowing sits outside the module and nothing in it tests it.

### Layer 2 — Implemented: every deliverable has code and a test; the App routing and the hostile-URL breadth are weakly tested

- **I1 (medium).** `GitHubCiProviderTest.readsAppIdentity` (:154) serves `/app`, `/user` and `/repos` from one mock and
  asserts no `Authorization` header, so the App JWT, the installation token and the user token could be swapped and the test
  would pass. Only the comment path checks which token is used (:472); `issueView` is never checked for the token it sends.
- **I2 (medium).** `GitLabCiProviderTest.refusesMergeRequest` pins the merge-request probe, but neither ADR 004 nor the guide
  states the refusal for GitLab (see C1).
- **I3 (low).** The URL-grammar tests miss named hostile cases: the spec's "over-long path" is covered only by a 300-character
  segment, the `MAX_PATH_LENGTH` branch (`ForgeUrl.java:191`), a backslash, percent-encoded traversal (`%2e%2e`), an uppercase
  host and a trailing-dot host have no test; the guide's Testing section claims "an over-long path".
- **I4 (low).** Missing or weak: the GitLab rate limit asserts only `resetAt().isPresent()`; no `issueComment` test on another
  origin or forge; no GitLab subgroup test for `whoami` or comment; the anonymous-issue tests never assert that no
  `Authorization` header was sent; no null GitHub `body`; `GitLabClient.issue` and `issueComment` have no `GitLabClientTest`
  case although the Expected Surface listed them.
- **I5 (low).** "Remove `pm-provider-ci` from the include and `provider-ci-allowed` fails" is a manual mutation (the lane ran it
  once), not an automated check; no positive control that `provider-analysis` admits `pm-provider-http`.
- **I6 (low).** The `PM-SEC-1` trace line cites `ValueTypesTest`, which only checks that records store their fields.

### Layer 3 — Implementation correctness: one behavioural defect, no credential leak shown

- **C1 (medium, checked).** `GitLabCiProvider.java:116-137`: on a `NOT_FOUND` for issue N the provider probes
  `merge_requests/N` and answers `REJECTED` ("is a pull request, or an issue the credential cannot see") when that exists. A
  missing issue #N beside an existing merge request !N therefore reads `REJECTED`, not `NOT_FOUND`, although GitLab numbers the
  two apart (the `GitLabClient.issue` Javadoc and ADR 004 say so). It also costs a request on every 404. The URL already says
  whether it is a merge request.
- **C2 (low-medium).** GitHub expiry header parsing (`GitHubCiProvider.java:61,279`) accepts the pattern
  `yyyy-MM-dd HH:mm:ss z`; an offset form such as `-0500` fails both parsers and `expiresAt` silently becomes `null`,
  indistinguishable from "no expiry". Whether GitHub sends offsets: not shown by reading.
- **C3 (low).** "Never a thrown exception" has two holes: GitLab `whoami` falls back to `identity.userId()`, which can be `""`,
  and `Identity` then throws `IllegalArgumentException` (`GitLabCiProvider.java:102`, `Identity.java:55`); `Issue` throws on a
  blank milestone title (`Issue.java:52`), reachable from the milestone JSON.
- **C4 (low).** Origin binding is by convention: both providers take a web `origin` and an independent `HttpClient` and never
  check that the client's endpoint belongs to the origin. The token stays confined to its client's endpoint (`Endpoint.sameOrigin`,
  refused redirects, path segments limited to `[letter digit . - _]`); no path to another host was found, but wiring the wrong
  client would leak and nothing here stops it. `https://github.com:8443/…` resolves as forge `github` (asserted); the origin
  check refuses it later.
- **C5 (low).** `TokenKind`: `ghs_`, `gho_`, `ghu_` and 40-hex classic tokens give `FAILED` "prefix names no kind"
  (`GitHubCiProvider.java:264`, per the spec's no-guessing hypothesis); every GitLab `personal_access_tokens/self` 400 is
  labelled OAuth, which also covers job tokens (`GitLabCiProvider.java:211`); `whoami` calls `GET /user` twice on that path.
- **C6 (low).** `ForgeUrl` accepts non-ASCII digits (`Character.isDigit`, `ForgeUrl.java:293`): fullwidth `１２` resolves to
  12; `%2F` in a path is decoded by `URI.getPath`. Neither is a security issue.
- **Code-standard misses (checked):** `@Nullable` returns, which CLAUDE.md forbids, at `GitHubCiProvider.java:165-171`
  (`refusalFor`) and `:264` (`kindOf`) and `GitLabCiProvider.java:158,163` (`refusalFor`); one class-level
  `@SuppressWarnings("java:S1075")` (`GitLabCiProvider.java:48`), broader than the one constant it targets — the lane said two
  suppressions were added and only this one is in the module; test data not from generators (`ForgeUrlTest` and
  `CiProvidersTest` enable the controller and use none; the other tests use unmarked literals); several assertion messages
  under 20 characters (read, not re-counted); HTTP tests build the URI by hand; `GitHubClientTest` extends a hand-written
  dispatcher that already existed.
- **Checked and fine (by the reader):** userinfo, `..`, `.`, empty segments, backslash and `http` are refused; tokens are not
  echoed in details; collections are copied immutably; no generic catch; the enforcer rules are correct (`provider` admits
  `pm-provider-ci`, `provider-contract` still bans `pm-provider-*` for git and http, `provider-analysis` closes Sonar to every
  provider module but `pm-provider-http`; each fixture switches on exactly one rule).
- Not shown by reading: the coverage figure, the Sonar status of the final head, the three mutation runs, the GraalVM native
  build of the module.

### Verdicts

| Layer | Verdict | Open items |
|-------|---------|-----------|
| Specified | sound; the pull-request/issue ambiguity under-specified; the staged spec never corrected | S1–S5 |
| Implemented | complete; App-token routing and hostile-URL breadth weakly tested | I1–I6 |
| Correct | one behavioural defect (C1); no credential leak shown by reading | C1 for the operator; C2–C6 as notes; the `@Nullable` returns are a CLAUDE.md miss |

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-V2-12 --status shipped`
- [x] row `pr` stamped — `281`
- [x] row `landing` stamped — `landings/PLAN-V2-12.md`
- [x] row `plan_marshall_plan_id` stamped — `v2-12-ci-contract-module` (from the sender `rb-v2-12-ci-contract-module`)
- [x] epic.md narrative reconciled — eighth recurrence on the incomplete-landing defect; a Watch for the layer-3 and layer-1 findings
- [x] three-layer analysis (R21) written above; the C1 defect goes to the operator, no fix plan staged without the operator's word
- [x] resume anchor updated; `queue-view.md` regenerated and committed with the row change

## Follow-Ups

- PLAN-V2-62 (`ci-contract-pull-request-view`) is unblocked by this landing and extends the same classes; S2 and S4 are
  its to read at the outline (the pull-request/issue split, and the type names it assumed).
- C1 (GitLab false `REJECTED`) and the `@Nullable` returns are small; they fit PLAN-V2-62's files (`GitLabCiProvider`) or a
  fix plan, at the operator's word.
- Documentation: no documentation pull request was opened by this plan (the specification edits are in the same PR).

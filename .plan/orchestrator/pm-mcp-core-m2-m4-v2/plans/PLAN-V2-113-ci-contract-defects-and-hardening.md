# PLAN-V2-113: Fix the GitLab refusal defect of the CI contract and close the gaps its first plan left

epic: pm-mcp-core-m2-m4-v2
workstream: WS-09
origin: the three-layer analysis of PLAN-V2-12 (`landings/PLAN-V2-12.md`, 2026-11-02, C1 to C3, C6, I1 to I4, the checked CLAUDE.md misses) and the operator's decision of 2026-11-04 ("PLAN-V2-12's: yes" — its own fix plan before PLAN-V2-62); owner-register Wa56
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-113-ci-contract-defects-and-hardening.md` and is queued as one row file, `queue/PLAN-V2-113.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r6`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R03, R04, R05, R06, R07, R08, R11, R17, R18, R19, R20. (R01: no work package covers this plan; R09: no confinement; R10: no specification text is read as test input; R12: no message is added — a refusal is a returned value; R13: no format change; R14, R15, R16: neither `core.store`, `pm-workflow` nor `pm-runtime` is touched. R03 and R04 because every change is in an adapter that calls through `cui-http` and every test of it runs on `cui-test-mockwebserver-junit5`.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-providers/pm-provider-ci/doc/developer/provider-ci.adoc`, `pm-providers/pm-provider-github/doc/developer/provider-github.adoc`, `pm-providers/pm-provider-gitlab/doc/developer/provider-gitlab.adoc` (all exist; correct the statements named in Deliverable 5); ADR directory: `pm-providers/pm-provider-ci/doc/adr/provider-ci/` (001 to 005 exist at `70bc999f`; the next free number at `origin/main` is taken, R11); layout: workstream WS-09.
- R12 — none (no message).

**Read the skills first (R20).** `java-core`, `java-null-safety`, `javadoc`, `junit-core`, `cui-testing` and `cui-http-testing` of `/home/oliver/git/plan-marshall/marketplace/bundles/{pm-dev-java,pm-dev-java-cui}/skills/`; say in the first progress note which you read. R20 applies to every test line this plan touches: data from `cui-test-generator` where it is not the contract, a message of 20 to 60 characters on every assertion; an HTTP path, a header name or a wire value stays a literal where it is the contract.

## Objective

PLAN-V2-12 (#281) shipped the CI contract module `pm-provider-ci` with the GitHub and GitLab adapters. The three-layer analysis of the merged code (`landings/PLAN-V2-12.md`) found one defect that corrupts an answer, three places where a documented "never throws" is not true, an input check that accepts what it should refuse, `@Nullable` method returns the project rules forbid, and tests that pass whatever the property they name. The operator decided that these get their own plan, before PLAN-V2-62 (the pull-request view) builds on the same files. All line numbers are those of `origin/main` at `70bc999f`. This plan changes no public signature of `CiProvider`; the private methods that return `@Nullable` are the only signatures that change.

## Deliverables

1. **The GitLab refusal defect.** `GitLabCiProvider.issueView` (lines 115 to 120) calls `client(...).issue(n)`; on `NOT_FOUND` it calls `mergeRequestOutcome` (lines 126 to 137), which asks `GET projects/<enc>/merge_requests/<n>` (lines 183 to 188) and, on a `200`, answers `REJECTED` ("is a pull request, or an issue the credential cannot see"). GitLab numbers issues and merge requests apart (the Javadoc of `GitLabClient` line 231 says so), so a missing issue N while a merge request N exists is answered `REJECTED` instead of `NOT_FOUND`, and every 404 costs a second request. The default: remove the probe, so a missing issue is `NOT_FOUND`, a rate limit is `RATE_LIMITED` and a transport failure or 5xx is `FAILED`, as for GitHub. (The alternative — keep the probe and report it as a separate detail of `NOT_FOUND` — is recorded in the ADR and asked at refine, R07.) The split of an issue number from a pull-request number belongs to the pull-request view (PLAN-V2-62, finding S2) and is not decided here. Tests in `GitLabCiProviderTest`: `refusesMergeRequest` (lines 305 to 317) pins the defect and is replaced by a case that a missing issue is `NOT_FOUND` although a merge request with that number exists (the mock serves both paths and the case asserts that the merge-request path was not requested); `keepsProbeRateLimit` (lines 319 to 330) is removed with the probe; `mapsRefusals` (lines 173 to 189) keeps its `NOT_FOUND` and now asserts it without an unmocked path. **Done when:** the cases pass, no request to a `merge_requests` path is made by `issueView`, and the replaced cases are named in the pull request body.
2. **"Never throws" and the inputs that are accepted too widely.** (a) GitLab `whoami` (`GitLabCiProvider.java` lines 101 to 106): `.userId(Json.string(json,"id").orElse(identity.userId()))` passes a blank id through (`"id":""`, or no id and a token record whose `user_id` is `""`, `GitLabClient.java` lines 210 and 211) to `Identity.build()`, whose `requireText` (`Identity.java` lines 55 and 56) throws `IllegalArgumentException` out of the adapter; filter a blank as absent the way `GitHubCiProvider` does (lines 199, 224 and 225) and answer a returned value. (b) A blank milestone title: `GitLabClient.java` line 253 and `GitHubClient.java` line 260 pass `Json.string(json,"milestone","title").orElse(null)` to `Issue` (`Issue.java` lines 52 and 53), which throws on a blank; treat a blank title as no milestone in both clients. (c) `GitHubCiProvider` lines 61, 62, 210 and 279 to 293: the token expiry is parsed with `"yyyy-MM-dd HH:mm:ss z"`, then as ISO; an offset form (`+0000`, `+00:00`) falls through to `Optional.empty()` and `.orElse(null)` makes an unreadable value indistinguishable from an absent header. Accept the offset forms of the same instant, keep `not-a-date` as absent, and say in the Javadoc and the guide that an unreadable header is reported as no expiry (no message is added, R12). Whether GitHub sends an offset form is not shown by the code; the point is that the parser must not drop a valid instant (verify-at-outline: `GitHubClientTest` and the recorded header). (d) `ForgeUrl.java` lines 289 to 297: `Character.isDigit` and `Integer.parseInt` accept non-ASCII digits (a fullwidth `１２` is 12) and the leading-zero guard looks for ASCII `'0'` only; accept ASCII digits only. Tests: each of (a) to (d) gets an adapter-level case on the mock web server (`ValueTypesTest` covers the records only, lines 122 to 137 and 166 to 176); the cases name the input in the display name. **Done when:** the adapters answer a returned value for the four inputs and the cases pass.
3. **The CLAUDE.md misses.** Every `@Nullable` method return in the three modules is private and called only inside its own class: `GitHubCiProvider.refusalFor(IssueRef)` (lines 165 to 168, callers lines 130 and 146), `refusalFor(RepositoryRef)` (170 to 182, callers 121 and 167), `kindOf` (264 to 273, caller 189 with its null check at 190); `GitLabCiProvider.refusalFor(IssueRef)` (158 to 161, callers 111 and 141) and `refusalFor(RepositoryRef)` (163 to 175, callers 78 and 160). Convert each to `Optional<T>` (CLAUDE.md: absence is returned as `Optional`, never as a `@Nullable` return; `Optional` is a return type only, so no parameter, field or component becomes one). The class-level `@SuppressWarnings("java:S1075")` at `GitLabCiProvider.java` line 48 (its reason is a two-line comment above, lines 46 and 47) is narrowed to the one field that needs it, `WEB_ISSUE_PATH = "/-/issues/"` (line 56), with the reason on the annotation line; whether Sonar also flags `PROJECT_PATH = "projects/"` (line 54) is checked and recorded. **Done when:** no `@Nullable` method return is left in `pm-provider-ci`, `pm-provider-github` or `pm-provider-gitlab` (a search is reported), the suppression is on the field, and the existing cases stay green.
4. **Tests that can fail.** (a) `GitHubCiProviderTest.readsAppIdentity` (lines 145 to 171) uses three token sources (`ghs_installation`, `ghu_operator`, a JWT) behind one mock server with no `Authorization` assertion, so swapped clients pass; assert the token each request carries (the installation token for the identity read, the user token for `postsAppCommentWithUserToken`, the JWT for the installation lookup) and the token `issueView` sends. (b) `ForgeUrlTest` (`reportsInvalidUrls` lines 110 to 141, `coversRemainingGrammarBranches` lines 153 to 202) lacks: the `MAX_PATH_LENGTH` branch (line 191), the backslash branch (line 191), `%2e%2e` (refused at line 228), `%2F`, an uppercase host (lowercased at line 199), a trailing-dot host, a segment count above 25 on GitHub, a query-only and an empty URL, and the non-ASCII digit of Deliverable 2(d); add each. (c) The weak or missing assertions of the analysis: the rate-limit case asserts only `resetAt().isPresent()` (`GitLabCiProviderTest.java` line 170) — assert the instant; the two anonymous cases (`GitLabCiProviderTest.java` line 259, `GitHubCiProviderTest.java` line 219) assert no absence of an `Authorization` header — assert it; `GitLabClientTest` has no `issue` or `issueComment` case; `GitHubClientTest.readsIssue` (lines 206 to 223) has no null `body`; `issueComment` has no test on another origin or forge (the refusal tests use `issueView`); GitLab has no subgroup test for `whoami` or `issueComment` (`readsNestedProjectIssue` covers `issueView` only). **Done when:** each listed case exists and fails when the property it names is broken — show it for (a) and for the `Authorization` assertions by a mutation of a scratch copy (the failing run is reported, the production code is not changed for it).
5. **Documentation and decisions.** One ADR in `pm-providers/pm-provider-ci/doc/adr/provider-ci/` (the next free number at `origin/main`, R11; 006 at the time of writing): GitLab answers a missing issue `NOT_FOUND` and does not probe merge requests (ADR 004, "a pull request is no issue", names only the GitHub `pull_request` flag and never mentions the GitLab probe; the new ADR supersedes the part of it the probe represented and says what the pull-request view will decide). Correct the guides: the Testing section of `provider-ci.adoc` (about line 137) claims "an over-long path", which is the `ForgeUrl` case only; `provider-ci.adoc` line 62 calls the null rule "R20", which is ADR `doc/adr/013-the-null-contract-of-the-null-marked-packages.adoc`; the guides of `provider-github` and `provider-gitlab` state the blank-as-absent rules and the expiry forms. **Done when:** the ADR follows `doc/adr/template.adoc`, the statements are corrected, and every link resolves (R17).

**Done when:** Deliverables 1 to 5; the public signatures of `CiProvider` are unchanged. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of `pm-provider-ci`, `pm-provider-github` and `pm-provider-gitlab` (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body) and `verify -Pcoverage` are green; `verify -Pintegration-tests -pl pm-core -am` is not owed (no `pm-core` file). **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main` first; the sibling checkout lags).

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **Default and its alternatives.** The default for Deliverable 1 is: no probe. The alternative (keep the probe, report it as a detail of `NOT_FOUND`, not as `REJECTED`) is ask-at-refine (R07). The split of an issue number from a pull-request number (`IssueRef` carries no kind, so `issueComment` on a pull-request number is unguarded: GitHub answers `PERMISSION_DENIED` to an `issues: write`-only token, GitLab comments on an unrelated issue) is finding S2 of the analysis and stays in PLAN-V2-62; this plan does not decide it and says so in the ADR.
- **Production code is touched in the adapters, `ForgeUrl` and the two clients only.** `CiProvider`, `IssueRef`, `Issue`, `Identity` and the other records keep their shape (their refusals of a blank are right; the adapters must not hand them one).
- **No new log record** (R12): an unreadable expiry is reported as no expiry, in the Javadoc and the guide.
- **Nothing the analysis found fine is changed:** the enforcer rules, the credential handling (`PM-SEC-1`: no credential in a message, a detail or a log) and the origin binding stay as they are; the analysis found no leak path.
- **Where the project analyses its pull requests with Sonar** (`runbook_lane resolve` reports `sonar: configured`): an open Sonar issue is fixed in a pushed commit, or dismissed with a stated reason recorded as `{issue-key}: {reason}` in the run's progress record and transmitted through `plan-marshall:workflow-integration-sonar:sonar_rest transition --issue-key {key} --transition {falsepositive|wontfix}`. A dismissal the run cannot transmit is a blocker, never a silent pass. The merge gate reads the open-issue count at the head — `0` confirmed, or every remaining issue dismissed — and an `undecidable` read blocks.
- **No dependency or plugin is added.**

## Claim Labels

- OBSERVED: `GitLabCiProvider.issueView` probes `merge_requests/<n>` on a `NOT_FOUND` and answers `REJECTED` on a `200` (lines 115 to 137 and 183 to 188); `refusesMergeRequest` (lines 305 to 317) pins it; no test asserts `NOT_FOUND` for "issue missing, merge request exists" — read at `70bc999f`
- OBSERVED: `GitLabCiProvider.whoami` passes `Json.string(json,"id").orElse(identity.userId())` to `Identity.build()` without a blank check (lines 101 to 106); `Json.string` does not filter blanks (`Json.java` line 161); `GitHubCiProvider` filters blank `userId` and `botId` (lines 199, 224 and 225) — read at `70bc999f`
- OBSERVED: `GitLabClient.java` line 253 and `GitHubClient.java` line 260 pass the milestone title through without a blank check; `Issue.java` lines 52 and 53 throw on a blank milestone — read at `70bc999f`
- OBSERVED: the expiry pattern is `"yyyy-MM-dd HH:mm:ss z"` (`GitHubCiProvider.java` lines 61 and 62), `instantOf` tries `ZonedDateTime.parse`, then `Instant.parse`, then `Optional.empty()` (lines 279 to 293), and `.expiresAt(...orElse(null))` (line 210) hides the difference between absent and unreadable; tests: `readsTokenIdentity` (UTC), `readsExpiryVariants` (ISO `Z`), `dropsUnreadableExpiry` (`not-a-date`) — no offset form
- OBSERVED: `ForgeUrl.java` uses `Character.isDigit` (line 293) and `Integer.parseInt` (line 297); the leading-zero guard (line 289) looks for ASCII `'0'` only — read at `70bc999f`
- OBSERVED: the five private `@Nullable` method returns and their callers listed in Deliverable 3; the other `@Nullable` hits of the three modules are parameters, fields or record components (`CiProvider.java` line 44, `Identity.java` lines 39 and 40, `Issue.java` line 34, `ForgeUrl.java` line 88, `GitHubAppJwt.java` line 156, `GitLabCiProvider.java` line 77, `GlabCommands.java` line 178, `GitHubClient.java` line 127)
- OBSERVED: ADR directory `pm-providers/pm-provider-ci/doc/adr/provider-ci/` holds 001 to 005; ADR 004 names the GitHub `pull_request` flag and not the GitLab probe; repository-level ADRs run to `doc/adr/014-the-ci-contract-is-the-module-pm-provider-ci.adoc`
- HYPOTHESIS: GitHub sends the token expiry in the `github-authentication-token-expiration` header in the form `yyyy-MM-dd HH:mm:ss UTC`, and an offset form is a form of the same header seen elsewhere: confirm/refute at the recorded header of `GitHubClientTest` (verify-at-outline; the fix accepts the offset forms either way)
- HYPOTHESIS: `PROJECT_PATH = "projects/"` (`GitLabCiProvider.java` line 54) is not flagged by `java:S1075` while `WEB_ISSUE_PATH` is: confirm/refute at the live issue list of `main`
- Verify-first clause: before editing, list the open pull requests for one that touches `pm-providers/**` (the conformance family PLAN-V2-92, 93, 99, 103 and PLAN-V2-62 may), re-read the nine files at the head, and re-read the analysis `landings/PLAN-V2-12.md`; a refutation loops back to re-scope

## Expected Surface

Code and tests:

- OBSERVED: `pm-providers/pm-provider-ci/src/main/java/de/planmarshall/provider/ci/ForgeUrl.java` — the digit rule (D2)
- OBSERVED: `pm-providers/pm-provider-ci/src/test/java/de/planmarshall/provider/ci/ForgeUrlTest.java` — the added grammar cases (D2, D4)
- OBSERVED: `pm-providers/pm-provider-github/src/main/java/de/planmarshall/provider/github/GitHubCiProvider.java` — expiry parse, three private returns (D2, D3)
- OBSERVED: `pm-providers/pm-provider-github/src/main/java/de/planmarshall/provider/github/GitHubClient.java` — blank milestone title (D2)
- OBSERVED: `pm-providers/pm-provider-github/src/test/java/de/planmarshall/provider/github/GitHubCiProviderTest.java` — token assertions, expiry offset, blank cases, anonymous header (D2, D4)
- OBSERVED: `pm-providers/pm-provider-github/src/test/java/de/planmarshall/provider/github/GitHubClientTest.java` — blank milestone, null body (D2, D4)
- OBSERVED: `pm-providers/pm-provider-gitlab/src/main/java/de/planmarshall/provider/gitlab/GitLabCiProvider.java` — the probe, `whoami`, two private returns, the suppression (D1 to D3)
- OBSERVED: `pm-providers/pm-provider-gitlab/src/main/java/de/planmarshall/provider/gitlab/GitLabClient.java` — blank milestone title (D2)
- OBSERVED: `pm-providers/pm-provider-gitlab/src/test/java/de/planmarshall/provider/gitlab/GitLabCiProviderTest.java` — the replaced and added cases (D1, D2, D4)
- OBSERVED: `pm-providers/pm-provider-gitlab/src/test/java/de/planmarshall/provider/gitlab/GitLabClientTest.java` — `issue`, `issueComment`, blank milestone (D2, D4)

Documentation:

- OBSERVED: `pm-providers/pm-provider-ci/doc/developer/provider-ci.adoc`, `pm-providers/pm-provider-github/doc/developer/provider-github.adoc`, `pm-providers/pm-provider-gitlab/doc/developer/provider-gitlab.adoc` — the corrected statements (D5)
- HYPOTHESIS: `pm-providers/pm-provider-ci/doc/adr/provider-ci/006-*.adoc` — new (D5; the number is renumbered at `origin/main`, R11)

Not surface (R19): `doc/plans/README.adoc`, `doc/Traceability.adoc`, `doc/adr/README.adoc`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-12 (`ci-contract-module`; shipped, #281) and PLAN-V2-83 (shipped). Nothing live.
- Overlaps with (class level): PLAN-V2-62 (`ci-contract-pull-request-view`) edits the same adapters, clients, `ForgeUrl`, their tests, the three guides and the ADR directory; **PLAN-V2-62 waits for this plan** (its spec says so), and the gate (`corpus cross-check`) decides at emit. PLAN-V2-92 (`providers-tests`), 93 (`http-tests-annotations`), 99 (`optional-conversion-runtime-and-providers`) and 103 (`assertion-messages-workflow-and-providers`) declare provider test classes and sources by module; sequence this plan before them if the gate reports a multi-file overlap.
- Adjacent to: PLAN-V2-62 (the pull-request view; takes finding S2 and S3 of the analysis), PLAN-V2-12's guides.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-113-ci-contract-defects-and-hardening.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

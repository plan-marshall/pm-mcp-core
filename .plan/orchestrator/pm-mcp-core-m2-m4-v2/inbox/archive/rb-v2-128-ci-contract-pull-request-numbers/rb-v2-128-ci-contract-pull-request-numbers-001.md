envelope_version=1
sender_type=plan
sender_id=rb-v2-128-ci-contract-pull-request-numbers
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-10T01:31:13Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-128-ci-contract-pull-request-numbers
epic=pm-mcp-core-m2-m4-v2
pr=#348
merge_state=merged
cleanup_owed=false
deliverables_total=4
deliverables_done=4
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2088
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=0
review_body_findings_handled=0
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced)

# PLAN-V2-128 landing — make a pull-request number mean a pull request

Standing rules read: **r6 (2026-11-02)**; rules applied: R02, R03, R04, R05, R06, R07, R08, R11, R17, R18, R19, R20. Skills read before work (R20): `java-core`, `java-null-safety`, `javadoc`, `junit-core`, `cui-testing`, `cui-http-testing`.

PR #348 merged as `c2b839c9b426954210ae9a784e2b5b1c731e0ca4` on `main`. Branch `feature/v2-128-ci-contract-pull-request-numbers` and its worktree were pruned; `switch-and-pull` moved the main checkout to the merge commit.

## Deliverables

D1. **A pull-request tab URL names its pull request — met.**
- Cases for the three forges pass and failed before: `ForgeUrlTest.namesPullRequestOfTabUrl` (9 parameterized cases: GitHub `files`/`commits`/`checks`, Forgejo `files`/`commits`, GitLab `diffs`/`commits`/`pipelines`/`changes`) and `ForgeUrlTest.refusesTabsOutsideThePullRequestGrammar`. Order `test-first`; failing run commit `85ff213b`, job `b7da8497946c4064a3cd7c34c0d8456b` (10 failures); passing head `a9c978b8`; later commits `8350fcbb`, `54e09980`, `8298a28c`.
- Negative cases (issue path with a tab, unknown tab, Forgejo tab on GitLab list, two trailing segments, zero/padded number with a tab) pass in `refusesTabsOutsideThePullRequestGrammar`; query and fragment beside a tab are ignored.
- `reportsInvalidUrls` and `coversRemainingGrammarBranches` are untouched and green at `8298a28c`/`7808c713`.
- `ForgeUrl` class Javadoc and `provider-ci.adoc` state the grammar, the per-forge tab lists and their sources (GitHub pull-request reference, GitLab merge-request changes guide, Forgejo web interface). The segment bound is tab-aware: one tab above 25 segments, which keeps the pinned 22-group refusal and accepts a tab at the 21-group nesting limit (`ForgeUrlTest` pins it).
- Self-review finding: the first `52`-segment idea of a global bound raise would have flipped the pinned case; fixed with `8350fcbb` as a tab-aware bound.

D2. **`ForgeUrl.issue()` — met.**
- `ForgeUrlTest.namesNoPullRequestForIssue` (issue URL has `issue()`, no `pullRequest()`) and `namesPullRequestOfEachForge` (pull-request URL has `pullRequest()`, no `issue()`) pass. Order `test-first`; the failing run is a compile failure at `a9c978b8`, job `2bfdeb4127084ad284970f7db85a26b2` (`Symbol: Methode issue()`), since the test names a method that did not exist; passing head `a6007ea5`.
- `provider-ci.adoc` names `issue()` beside `pullRequest()` and tells a consumer to obtain an `IssueRef` from `issue()` and a `PullRequestRef` from `pullRequest()`.
- Search: no production class of this repository builds an `IssueRef` from a `ForgeUrl` by hand; the only match is the new accessor itself (and the test). The consumers live in the assembly.

D3. **`issueComment` on a pull-request number — met, as the operator answered (default).**
- (a) `GitHubCiProviderTest.refusesCommentOnPullRequest`: `REJECTED` "is a pull request", the read target is `GET /repos/cuioss/pm/issues/7`, request count 1, no post. Order `test-first`; failing run `a6007ea5`, job `ae839f9956b84c4d8d3719ae139fcc68` (6 failures); passing head `e48cd00e`.
- (b) `postsComment` posts with the read in its dispatcher (request count 2); `refusesComment` keeps the 404 mapping and the blank-body refusal.
- (c) `propagatesFailedGuardRead` (404/403/429/500, each with a 201 POST config available, so the no-post assertion discriminates), same order and failing run; passed `e48cd00e`.
- (d) `GitLabCiProviderTest.postsCommentWithoutReadingAMergeRequest`: posts to `/projects/cuioss%2Fpm/issues/9/notes`, request count 1, no merge-request path. Order `none`; green `e48cd00e`.
- New module ADR `provider-ci/010-the-github-issue-comment-reads-the-issue.adoc` supersedes ADR 009 in part and states the GitLab limit; `CiProvider.issueComment` Javadoc states both behaviours. ADR 009's Status records "superseded in part by ADR 010".
- Hypothesis `issues: write` covers the read: confirmed for a personal access token, a GitHub App user access token (asserted in `postsAppCommentWithUserToken`: both the read and the post carry `ghu_operator`) and an installation token (permission model), recorded in `provider-github.adoc`.

D4. **The statements say what the code does — met.**
- `provider-ci.adoc` § "A comment on a pull request is not `issueComment`" rewritten to the D3 outcome and linked to ADR 010; ADR 010 added to the Decisions and Testing bullets.
- `CiProvider.issueView` Javadoc points to `ForgeUrl.issue()`; `prView` points to `ForgeUrl.pullRequest()`.
- `provider-github.adoc` (authentication paragraph and The CI Adapter) and `provider-gitlab.adoc` (The CI Adapter) state the guard and the GitLab limit.
- ADR 009 Status note added; its Consequences bullet is corrected by ADR 010, not edited in place.
- `doc/Traceability.adoc`: the now-false "the pull-request read … not implemented yet" sentence is corrected (within R08: it is the PM-EXEC-5 paragraph this plan implements, and the edit corrects a proven-false statement); ADR 010 is linked from that section's Documentation line; the pull-request-read Implementation line now states the tab grammar and `ForgeUrl.issue()`.
- R17 links check: `trace.py` from the plan worktree against the documentation checkout at its `origin/main` (`49f785d`). No broken link in the changed documentation; four pre-existing `missing-include` links in `doc/specification/mcp-tools` (the `plan-marshall-mcp` sibling is absent) were reported and are not this plan's.

**Per-head test runs (module tests).** `ForgeUrlTest`/`GitHubCiProviderTest` green at `a6007ea5` (after D2) and `e48cd00e` (after D3); the three changed modules' `verify -Ppre-commit` (344 tests) was green at `8298a28c`, `dd4b0e12` and the merge head `7808c713`; every pushed head also ran in CI (`build / build (25)/(26)` green at `8298a28c`, `dd4b0e12`, `7808c713`).

**Plan-level Done-when.** Deliverables 1, 2 and 4 and Deliverable 3 as the operator answered. Public signatures of `CiProvider`, `IssueRef` and `PullRequestRef` unchanged; `ForgeUrl` gains one method (`issue()`). Sonar: 0 open new-code issues, quality gate OK (see Build server and the rule check). Build: reactor `verify -Ppre-commit` bound expiry plus the plan's permitted per-module runs (see Build server). `verify -Pintegration-tests -pl pm-core -am` not owed (no `pm-core` file). Links check as above.

## Personas and skills

- D1/D2 (production `ForgeUrl` and its test): `persona-implementer` then `persona-module-tester`; the spec declares `Skills:` empty → `skills: none named`.
- D3 (GitHub/GitLab adapter code and their tests): `persona-implementer` and `persona-module-tester`; `skills: none named`.
- D4 (documentation, ADR, Traceability): `persona-documenter`; `skills: none named`.
- Every self-review round applied `persona-code-reviewer`'s adversarial-refute lenses.
- No surface drift: no module outside the spec's Expected Surface was touched (`skills added: none`).

## Hypotheses

- OBSERVED: `resolveGitHub` requires exactly four segments and `resolveGitLab` the `-` separator at the third-from-last segment — **confirmed** at `d1fb719f`/`85ff213b`, `ForgeUrl.java:244-294` (pre-change); the two tab URLs were `INVALID` by reading and by the failing run `b7da…`.
- OBSERVED: `pullRequest()` exists and no `issue()` exists; no production class converts a `ForgeUrl` to an `IssueRef` — **confirmed**, `ForgeUrl.java:161-166` (pre-change) and the search report.
- OBSERVED: GitHub `issueComment` was unguarded and posts through `userClient().postComment`; GitLab posts through `issueComment(long)` — **confirmed**, `GitHubCiProvider.java:176-192` and `GitLabCiProvider.java:143-160` (pre-change).
- HYPOTHESIS: the tab lists are the forges' web-interface segments — **confirmed** against GitHub's pull-request reference (Commits, Checks, Files), GitLab's "Changes in merge requests" guide (Commits, Pipelines, Changes/diffs) and Forgejo's web interface (Files, Commits); recorded in the `ForgeUrl` Javadoc.
- HYPOTHESIS: `issues: write` implies the issue read for the installation token and the user token — **confirmed** for the user token by `postsAppCommentWithUserToken` and for the permission model (read included in write) in `provider-github.adoc`. The guard always uses the comment's credential; a mock server cannot enforce the permission implication, so the confirmation is the model plus the credential assertion.
- Verify-first clause: the open PRs enumerated (346, 343, 71) touch none of `pm-provider-ci`, `GitHubCiProvider*`, `GitLabCiProvider*`, `ForgeUrl*` or the ADR directory; the files were re-read at `85ff213b`.

## Findings

Self-review round 1 (sub-agent, `persona-code-reviewer`):
- 404 arm of the failed-read test did not discriminate → **fixed** (201 POST config in `propagatesFailedGuardRead`).
- Tab sources not named in the Javadoc → **fixed** (names and routes recorded).
- `issues: write` hypothesis not evidenced → **fixed** (guide statement; user-token assertion).
- ADR 010 said "two requests per plan" → **fixed** ("per comment, four per plan").
- GitLab `INVALID` messages stale → **fixed** (mention the optional tab and the issue-with-tab case).
- Test dispatcher Javadoc stale → **fixed**.
- Nullable local in a `@NullMarked` package → **fixed** (`boolean tabbed`).
- ADR 010 unlinked / missing from lists → **fixed**.
- Traceability ADR link beyond the named hunk → **dispositioned**: within R08 as the Documentation line of the PM-EXEC-5 section this plan implements.
- Tab at the maximum nesting depth → round 1 disposition "won't fix" refuted as too strong; **fixed** in round 2 with a tab-aware bound (`8350fcbb`).

Self-review round 2: tab-aware bound, source citation and installation-token clause → **fixed** (`8350fcbb`, `54e09980`); run-state (push/gate/progress) → **done**.
Self-review round 3: comment-line split and `tabOfAny` Javadoc → **fixed** (`8298a28c`). Verdict: no Medium/High open; no code/doc item open.

CI / review findings: CodeRabbit generated no actionable comments on any head (its summary says "No actionable comments were generated"); Sourcery's assessment is "Approved". Sonar S1192 ("commits" duplicated 3 times) was the only open Sonar issue; **fixed** in `dd4b0e12` (one `COMMITS` constant) and re-read at 0. No unresolved review threads.

## Reviewers

- **coderabbit** (required, `quota_limited: true`): reviewed `8298a28c` (marker-bearing summary, `kind=reviewed`) and then `7808c713` (marker `sourceCommitId`/`coveredCommitId` = `7808c713`, current head). Triggered at 23:33 (`reviewed`), re-triggered at 00:03 and 00:46 (the first was rate-limited, the second answered "Already reviewed the last commit"); the automatic review produced the current-head marker and the CI check is green. Findings: none.
- **sourcery** (optional, `quota_limited: true`): review body `APPROVED` re-stamped on `7808c713`; direct trigger answered with the 7-day budget notice at 23:33, 00:03 and 00:46. Optional, so it does not gate.
- Standing review authorization for coderabbit: qualifying review on a PR head (`8298a28c`) and on the current head (`7808c713`), every finding handled (none), re-triggered after the last push with the refusal recorded, every other gate condition green on `7808c713`.

## Operator questions

1. Asked with the `question` tool: Deliverable 3 reopens ADR 009 — options "Default: gh guard, GitLab unchanged (Recommended)" and "Alternative: keep ADR 009, skip D3"; **selected "Default: gh guard, GitLab unchanged"**. Deliverable 1 tab list — options "Confirm the stated lists (Recommended)" and "Change the lists"; **selected "Confirm the stated lists"**. Deliverable 2 `issue()` — options "Add `issue()` (Recommended)" and "Do not add `issue()`"; **selected "Add `issue()`"**.
No free-text operator messages.

## Rule check

- **Guard: none on this harness (unenforced).** No guard-log line exists for this run because OpenCode carries no pre-tool-use hook; the run's own actions were checked instead. No guard refusal occurred.
- Rule breaks by the run (all read-only): the self-review sub-agent reported one `awk` read of the spec and one `find -name Result.java`; both were inside the repository, produced no writes, and are counted here as the run's own breaks of the "file tools only" rule. Every executor call, build, CI action and git operation used the prescribed form.
- The R17 links check ran the documentation checkout's own `trace.py` by its absolute path at `origin/main` — the one consciously taken deviation from the executor-only rule, because R17 gives that invocation and the plan requires the check; no shell file read was used for repository content.
- Spec read: `spec_source: ledger_worktree`; no `spec_differ` marker was reported. `executor-skew`: `match` (executor `0.1.2088`, lane `0.1.2088`).
- Plan-level Sonar: `resolve` reported `sonar: not_configured` because the local configuration names no project key, but the project analyses every pull request in CI (the SonarCloud check and the bot comment are live). R18 therefore required the plain-HTTP read of `pullRequest=348`: 1 open issue (S1192) and gate OK on the first head, 0 open issues and gate OK on `dd4b0e12` and on the merge head `7808c713`. The landing facts carry `sonar_open_new_issues=0` and `step.sonar-roundtrip.quality_gate_status=ok`.
- Build server: setup preflight `ready`; every build ran with the default routing. Mechanism: `daemon_longpoll` for every routed build; `in_process_fallback` 0; no `degraded` result. Fallbacks/retries: one bound expiry — the reactor `verify -Ppre-commit` hit the explicit 936 s bound after 7,441 tests and 0 failures at `8aa78bf8`; per the plan's fallback clause the changed modules were then gated per module (`verify -Ppre-commit`, `verify`, `verify -Pcoverage`, 344 tests each) at `8298a28c`, `dd4b0e12` and `7808c713`. One test-selection run (`9c06…`) failed after a self-review fix raised the segment bound; the pinned cases were restored and the run repeated green (`e46f…`). One job queued 1,006 s and was re-attached with `build_server wait` (`a2a7…`). No killed job. `runtime_stamp_reason`: none carried.

## Residue

- None open. The only open definitional edge is intentional: a pull-request tab is accepted one segment above the 25-segment bound, so a tab at the extreme 21-group GitLab nesting is accepted while the pinned 22-group refusal stands.
- Deferred by the plan and untouched: the `RepositoryRef` Unicode-segment item, the pull-request view residues (Wa71), the consumer's own-repository check, wider URL shapes, and `doc/Traceability.adoc`'s carried items — all owned elsewhere or awaiting the operator, as the spec's Carried Open Items states.
- The four `missing-include` links reported by the R17 check are pre-existing in the documentation checkout (`plan-marshall-mcp` absent); they are not this plan's change.

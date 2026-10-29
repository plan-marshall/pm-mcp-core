# PLAN-V2-62: The pull-request read `ci.pr-view` on the CI contract, on GitHub and GitLab

epic: pm-mcp-core-m2-m4-v2
workstream: WS-09
origin: PLAN-147 of the epic pm-mcp-core-m2-m4

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-62-ci-contract-pull-request-view.md` and is queued as one row file, `queue/PLAN-V2-62.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r3`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R03, R04, R05, R06, R07, R08, R11, R12, R17, R18, R19. (R01 does not apply as a package rule: the plan works no package of `doc/plans/`. R12 applies only if the trace matrix shows a message to add. R17 because the plan runs the links check. R09 to R10 and R13 to R16 do not apply: no project path, no versioned artifact, no specification part is edited, and neither `core.store`, `pm-runtime` nor `pm-workflow` changes.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: the developer file of each package it changes (`pm-providers/pm-provider-ci/doc/developer/provider-ci.adoc`, `pm-providers/pm-provider-github/doc/developer/provider-github.adoc`, `pm-providers/pm-provider-gitlab/doc/developer/provider-gitlab.adoc`, all created or extended by the plan PLAN-V2-12 (`ci-contract-module`)); ADR directory: `pm-providers/pm-provider-ci/doc/adr/provider-ci/` (the next free number of the directory, renumbered against `origin/main` per R11); layout: workstream WS-10.
- R12 — log message holder: none expected (a provider module has no holder; see the plan PLAN-V2-12 (`ci-contract-module`), which states the rule if a record becomes necessary).

## Objective

Add the operation `ci.pr-view` to the contract module `pm-provider-ci` that the plan PLAN-V2-12 (`ci-contract-module`) creates, and implement it natively in `pm-provider-github` and `pm-provider-gitlab`. It is the bounded read of a pull request (merge request) that the plan source `pr` of a start needs (State Catalogue 09 `source-pr.observe`, Phase Workflows 01 § Plan Source Contracts, `pr`): the pull request must be open (`pr_not_open`) and its head branch writable by the agent identity (`head_not_writable`, for example the head of a fork without push access); its title and body as text, its base and head, and its changed files. It is cut from the first plan because "writable by the agent identity" is the one design point of the read (it joins the pull-request read with the credential's permissions) and `source-pr` is the one consumer: the issue source of a start does not wait for it.

## Deliverables

1. **The contract.** `CiProvider.prView(PullRequestRef)` returning the closed `Result<PullRequest>` of `pm-provider-http`; `PullRequest` (record): the state (`open`, `closed`, `merged`), the title and body as text, the base branch, the head branch and the head commit, the head repository, `headWritable` (the provider's answer for the credential of the provider instance, see D2 and D3), and the changed paths (merge base to head) with the completeness flag of the list (`complete`, or the bound that cut it: `page_cap`, `provider_result_cap`); `PullRequestRef` (forge id, origin, repository, number); `ForgeUrl` already recognises a pull-request URL (GitHub `/pull/<n>`, Forgejo `/pulls/<n>`, GitLab `/-/merge_requests/<n>`) and returns a `PullRequestRef` for it. A pull request that is not found is `NOT_FOUND`, a state other than `open` is returned as a value (the consumer decides `pr_not_open`), not as a failure. **Done when:** unit tests for the new records' invariants and for `ForgeUrl`'s pull-request grammar of every forge (including the hostile URLs of the first plan's tests); the grammar tests of the first plan stay green unchanged.
2. **GitHub.** `GitHubCiProvider.prView`: `GET /repos/{owner}/{repo}/pulls/{n}` for state, title, body, base, head and head repository; the changed files from `GET /repos/{owner}/{repo}/pulls/{n}/files` followed through the base's `Link` pagination with the page cap (`PagedResult.complete`), an incomplete list reported as `incomplete`, never as the whole list; `headWritable` from the head repository's permissions for the credential (`GET /repos/{head owner}/{head repo}`, `permissions.push`), and for a fork head only where the pull request allows maintainer edits and the credential may push to the base. The missing client methods are added to `GitHubClient`; the permission set of the installation token for the read is a new constant of `GitHubOperation` (`pull_requests: read`, `contents: read`, `metadata: read`). **Done when:** `GitHubCiProviderTest` on `cui-test-mockwebserver-junit5` (R04) covers: an open pull request of the project's repository (writable), a closed and a merged one (state only, no failure), a fork head without push (`headWritable` false), a fork head with maintainer edits and push to the base, a list beyond the page cap (incomplete), a missing pull request (`NOT_FOUND`), a rate limit with its reset instant, a `403`, a malformed body (`FAILED`, no thrown exception), a pull request on another origin (`REJECTED`).
3. **GitLab.** `GitLabCiProvider.prView`: `GET /projects/{id}/merge_requests/{iid}` for state (`opened`, `closed`, `merged`, `locked`), title, description, `target_branch`, `source_branch`, `sha` and `source_project_id`; the changed paths from the merge request's diffs endpoint followed through the pagination of the base with the page cap; `headWritable` for a source project equal to the target project from the credential's access level on the project (developer or above), and for a fork from `allow_collaboration` and the credential's access to the target project. **Done when:** `GitLabCiProviderTest` on the mock web server covers the same cases for GitLab, a locked merge request included, and the existing `GitLabClientTest` and `GitLabCiProviderTest` cases of the first plan stay green unchanged.
4. **Developer documentation and decisions.** Extend `provider-ci.adoc` (the operation, the mapping a consumer applies from `PullRequest` and `Result` to the outcomes `observed`, `pr_not_open` with `state: closed | merged | absent`, `head_not_writable`, `not_found`, `rate_limited` with `resets_at`, `provider_unavailable`; what `headWritable` is and is not: the provider's statement for the credential of the instance, re-read by the consumer at `start.create`, the specification says the head "is observed again"), and the guides of `provider-github` and `provider-gitlab`; one ADR in `pm-providers/pm-provider-ci/doc/adr/provider-ci/` for the definition of `headWritable` per forge and for the choice that a non-open state is a value. `doc/Traceability.adoc`: the lines of the requirements the trace matrix names. **Done when:** every link of the new text resolves (R17), the ADR follows `doc/adr/template.adoc` and is renumbered against `origin/main` (R11).

**Done when:** `CiProvider.prView` exists and is implemented by `GitHubCiProvider` and `GitLabCiProvider` against recorded-shape responses on the mock web server, the three guides and the ADR are written, and the module `pm-provider-ci` and both adapters keep at least 80 % instruction and branch coverage. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (`pm-provider-ci`, `pm-provider-github`, `pm-provider-gitlab`; where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body) and `verify -Pcoverage` are green; `verify -Pintegration-tests -pl pm-core -am` is not owed (no `pm-core` file and no build rule changes). **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-IMPL-1, PM-TECH-2, PM-EXEC-5, PM-SEC-1 (a pull request's title and body are external text) — the trace matrix confirms or corrects the list.
- Specification: State Catalogue 09 § `source-pr.name`, `source-pr.observe` (line 263 at the head of `main`); Phase Workflows 01 § Plan Source Contracts, `pr` (line 298) and Phase Workflows 02 § Adopt Lane (`pr`); Module Structure § `pm-provider-ci` (documentation repository). These specification parts are read, not edited: the plan `start-from-clients` owns them.
- Watch: none read for this plan beyond the first plan's.

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **Depends on the plan PLAN-V2-12 (`ci-contract-module`), which creates the module, `CiProvider`, `ForgeUrl`, `CiProviders`, `GitHubCiProvider`, `GitLabCiProvider`, their tests, the developer guide `provider-ci.adoc` and the ADR directory.** Every path of the Expected Surface that is labelled "after PLAN-V2-12 (`ci-contract-module`)" does not exist at the head of `main` today; the plan reads the merged first plan, and a first plan that changed one of the names (the class set is its HYPOTHESIS) is followed, not the names written here.
- **No package file, no index row.** The plan works no package (as the first plan). It edits no specification part and not `doc/plans/README.adoc`.
- **Remaining, not started here:** `ci.pr-find`, `ci.pr-create`, `ci.pr-observe` (the merge confirmation `merged` with the merge commit on the base branch, and the re-observation of `finalize.adopt-pr`, which the specification words as "re-observes" and which the outline of the owning plan assigns to `ci.pr-view` or `ci.pr-observe`), `ci.merge`, `ci.enqueue`, `ci.queue-membership`, `ci.checks` and the review threads (the finalize and review slice), the CLI variant, the Forgejo adapter, and the `Primitive` wrapper `ci.pr-view` with its outcomes in the assembly.
- **The read is external text.** Title and body are returned as the provider gave them; the level-1 validator and the credential-shaped-text check belong to the consumer (`source-pr.store`), not to this module. The guide says so.
- **No dependency or plugin is added.**

## Claim Labels

- OBSERVED: `ci.pr-view` is named by exactly two specification places and has no implementation — `doc/specification/state-catalogue/09-bootstrap-and-lessons.adoc` line 263 ("A bounded `ci.pr-view`: the pull request must be open and its head branch writable by the agent identity; its title, body, and changed files. The head is observed again by `start.create`.") and `doc/specification/phase-workflows/01-planning-phases-1-2.adoc` line 298 (`pr_not_open`, `head_not_writable`, the changed paths as `declared_files`, the base branch); `git grep -n 'pr-view\|prView' pm-*/src pm-providers/*/src` finds nothing
- OBSERVED: the outcomes the consumer expects are `observed`, `pr_not_open`, `head_not_writable`, `not_found`, `rate_limited` (with `resets_at`), `provider_unavailable`, `cancelled` — `state-catalogue/09-bootstrap-and-lessons.adoc` line 263, the sixth cell
- OBSERVED: the clients have no pull-request read — `GitHubClient` has `reviewThreads`, `resolveReviewThread`, `enqueue`, `postComment` (lines 105, 138, 155, 182) and `GitLabClient` has `merge`, `mergeTrainAdd`, `discussions`, `reply`, `resolve`, `jobTrace`, `tokenIdentity` (lines 104 to 199); `HttpClient.getAll(String, int)` follows `Link` pagination and reports completeness (`pm-provider-http/…/HttpClient.java` line 162)
- OBSERVED: the module and the first-plan classes are absent at the head of `main` (`pm-providers/pom.xml` lines 19 to 23; `git ls-files pm-providers/pm-provider-ci` lists nothing) — they are created by PLAN-V2-12 (`ci-contract-module`)
- HYPOTHESIS: GitHub's `GET /repos/{o}/{r}/pulls/{n}/files` is paginated at 100 entries and ends at 3000 files; a list that reaches the provider's cap is `incomplete` with `provider_result_cap` — confirm/refute against the GitHub REST reference or a recorded response (verify-at-outline)
- HYPOTHESIS: `headWritable` on GitHub is `head.repo.permissions.push` of the head repository for a same-repository pull request, and for a fork `maintainer_can_modify` together with push permission on the base repository; a pull request whose head repository was deleted (`head.repo` null) is not writable — confirm/refute at the outline (a user access token and an installation token report `permissions` differently)
- HYPOTHESIS: on GitLab the changed paths come from `GET /projects/{id}/merge_requests/{iid}/diffs` (paginated) or the older `/changes` (one response, truncated by the provider at a size, with an `overflow` flag), and `headWritable` is the access level of the credential on the source project (`GET /projects/{id}` `permissions`) or, for a fork, `allow_collaboration` — confirm/refute which endpoint the targeted GitLab version serves (verify-at-outline)
- HYPOTHESIS: the new classes are `PullRequest`, `PullRequestRef` in `de.planmarshall.provider.ci` and the two methods named above; the exact set is the trace matrix's (R19)
- Verify-first clause: before editing, list the open pull requests for one that touches `pm-providers/`; confirm that the plan PLAN-V2-12 (`ci-contract-module`) is merged (`git ls-tree origin/main pm-providers/pm-provider-ci` lists the module) and read its `CiProvider`, `ForgeUrl`, the two adapters and the guide at the head; re-read `state-catalogue/09` line 263 and `phase-workflows/01` line 298. A refutation of any claim above loops back to re-scope.

## Expected Surface

All paths below are created by the plan PLAN-V2-12 (`ci-contract-module`) (not at the head of `main` today, labelled HYPOTHESIS, to be re-read after it merges) unless the line says OBSERVED:

- HYPOTHESIS (after PLAN-V2-12 (`ci-contract-module`)): `pm-providers/pm-provider-ci/src/main/java/de/planmarshall/provider/ci/CiProvider.java` — `prView`
- HYPOTHESIS (new): `pm-providers/pm-provider-ci/src/main/java/de/planmarshall/provider/ci/PullRequest.java`, `…/PullRequestRef.java`, and their tests under `pm-providers/pm-provider-ci/src/test/java/de/planmarshall/provider/ci/`
- HYPOTHESIS (after PLAN-V2-12 (`ci-contract-module`)): `pm-providers/pm-provider-ci/src/main/java/de/planmarshall/provider/ci/ForgeUrl.java` and `pm-providers/pm-provider-ci/src/test/java/de/planmarshall/provider/ci/ForgeUrlTest.java` — the pull-request grammar, if the first plan returned the URL as a plain kind
- HYPOTHESIS (after PLAN-V2-12 (`ci-contract-module`)): `pm-providers/pm-provider-github/src/main/java/de/planmarshall/provider/github/GitHubCiProvider.java` and its test `GitHubCiProviderTest.java`
- OBSERVED: `pm-providers/pm-provider-github/src/main/java/de/planmarshall/provider/github/GitHubClient.java`, `…/GitHubOperation.java` and their tests `GitHubClientTest.java`, `GitHubOperationTest.java` — the pull-request read and its permission constant
- HYPOTHESIS (after PLAN-V2-12 (`ci-contract-module`)): `pm-providers/pm-provider-gitlab/src/main/java/de/planmarshall/provider/gitlab/GitLabCiProvider.java` and its test `GitLabCiProviderTest.java`
- OBSERVED: `pm-providers/pm-provider-gitlab/src/main/java/de/planmarshall/provider/gitlab/GitLabClient.java` and `pm-providers/pm-provider-gitlab/src/test/java/de/planmarshall/provider/gitlab/GitLabClientTest.java` — the merge-request read and the diffs read

Not surface (R19, bookkeeping): `doc/Traceability.adoc`, the ADR directory `pm-providers/pm-provider-ci/doc/adr/provider-ci/` (created by the first plan), the developer guides `provider-ci.adoc`, `provider-github.adoc`, `provider-gitlab.adoc`. Not touched: every specification part, `doc/plans/README.adoc`, `pom.xml`, `pm-providers/pom.xml`, `CLAUDE.md`, `src/guard-controls/`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-12 (`ci-contract-module`) (it creates the module and every class this plan extends; the surfaces of the two plans share `CiProvider`, `ForgeUrl`, `GitHubCiProvider`, `GitLabCiProvider`, `GitHubClient`, `GitHubOperation`, `GitLabClient`, their tests and the guides, 14 entries, so they cannot run beside each other).
- **The plans that need `ci.pr-view` depend on this one (the orchestrator edits their specs):** PLAN-V2-24 `start-from-clients` for `source-pr` (with PLAN-V2-12 (`ci-contract-module`) for `source-issue`; no shared file with this plan). The adopt lane's re-observation is the owning plan's outline (Standing Constraints).
- Overlaps with the live specs: none beyond the first plan; disjoint from the running PLAN-46 and PLAN-105 and from the staged PLAN-V2-39, PLAN-V2-43, PLAN-V2-13, PLAN-V2-17, PLAN-V2-72 in every file (PLAN-V2-72 `forgejo-provider` implements `CiProvider` in its own module after both plans and adds its `prView` then or leaves it Remaining).
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp`, which wires the primitive (list what it needs in the pull request body as **Owed outside this repository**).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-62-ci-contract-pull-request-view.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

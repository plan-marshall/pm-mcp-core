# Landing Analysis: PLAN-V2-62 — The CI contract pull-request read (`ci.pr-view`)

epic: pm-mcp-core-m2-m4-v2
workstream: WS-09
pr: #299 (merge commit `00570830`; 37 files, +3382/-78)

> Landing record for one shipped plan. Lives at `landings/PLAN-V2-62.md`. A pasted claim is a lead, never a fact.

Source: inbox message `plan-v2-62-ci-contract-pull-request-view-001.md` — the first landing of a plan lifecycle run in the plan-marshall
form (harness claude, model claude-sonnet-5-5, effort low, executor 0.1.2041, total tokens 35,458,799 spanning populations; the plan wrote the
message by hand after it was archived, because it was started with a spec path as its task so `source_id` was no orchestrator pointer and
`emit-landing` never ran). All four deliverables reported met, `deliverables_met_with_residuals=4`, code review 0 blocking and 5 advisory.
Corroborated: `ci pr view 299` `merged`; `ci checks status` `overall_status: success` (20 checks); Sonar `main` (analysis of `00570830`,
2026-11-06 08:45 UTC) gate OK, 0 open BUGs; `Maven Build` and `Integration Tests` push runs of `00570830` and of the ledger land `56ef0b95` green.
The landing's own `sonar-roundtrip` recorded `work_performed=false` (no credentials); the plain HTTP read above stands in for it.
The merge went through the merge queue under a recorded barrier-ask-override (the required reviewer had no review of the rebased head `32d5a139`;
CodeRabbit reviewed `628a1a72` with 13 comments, all triaged; Sourcery out of quota). The links check was not clean (BROKEN LINKS 450, 449 from a
documentation checkout three commits behind, 1 upstream at `Traceability.adoc:1544`); a re-run is owed.

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|---|---|---|
| D1 contract (`CiProvider.prView`, `PullRequest`, `PullRequestRef`, `ForgeUrl.pullRequest()`) | met | `PullRequestRef(repository, number)` with forge and origin on `RepositoryRef` (the S4 point of the PLAN-V2-12 analysis, followed); `@Builder` on the 10-component record |
| D2 GitHub | met | `GitHubCiProvider.java:196`, `GitHubClient.java:329/368/400`; `locked` and fork handling per ADR 007 |
| D3 GitLab | met | `GitLabCiProvider.java:162`, `GitLabClient.java:350/386/416`; `locked` maps to CLOSED (ADR 008) |
| D4 documentation | met with a stale line | guides, ADRs, watch; `doc/Traceability.adoc` still carries the old sentence (S5) |

## Three-Layer Analysis (standing rule R21, r6)

Done at `00570830` by a read-only sub-agent (nothing built or run), then the points marked **checked** re-read by the orchestrator.

### Layer 1 — Specified
- **S1 (low).** D2 says `headWritable` comes from `GET /repos/{head}` `permissions.push` for every head, but a fork needs the base repository
  (`maintainer_can_modify` plus push on base); ADR 007 resolved it. D1's "ForgeUrl returns a PullRequestRef (forge id, origin, repository, number)"
  is loose against the built `PullRequestRef(repository, number)`.
- **S2 (low, checked).** "Grammar tests of the first plan stay unchanged" was broken: `ForgeUrlTest.java:37` (the issue-URL test) now says "the pull request
  URL is resolved" (the advisory the landing names).
- **S3 (medium).** S2 of the PLAN-V2-12 analysis (`issueComment` on a pull-request number) was decided in ADR 009 as unguarded and deferred to "the review slice";
  GitLab `issues/{iid}/notes` with an MR iid still comments on an unrelated issue. No staged plan owns it.
- **S4 (low).** Dropped without a decision: S5 (`PULL_REQUEST_READ` is never wired, `ISSUE_READ` likewise), C4 (origin binding by convention) and C5 (GitLab `/user` read twice,
  `whoami` untouched) of the PLAN-V2-12 analysis; and of the PLAN-V2-113 leftovers `RepositoryRef.java:111` still uses `Character.isLetterOrDigit` (**checked**), `GitLabClient.userIdentity`
  has no blank filter, ADR 006 is unchanged.
- **S5 (medium, checked).** `doc/Traceability.adoc` lines 981-982 still say "the CLI variant, the four cross-repository reads, the grant check and the pull-request read are not
  implemented yet"; the new paragraph at 993 says the read is implemented. The "additions-only rule" the lane cites is **standing rule R08** (`standing-rules.md:57-61`:
  "in `doc/Traceability.adoc` add only the sections of the requirements this package implements"); it is not in `CLAUDE.md`. R08 is about sections, so correcting a now-false
  sentence of the plan's own PM-EXEC-5 paragraph is a stretch of it; the lane read it literally. Ownerless: a one-line ride-along.

### Layer 2 — Implemented
- **I1 (medium, checked).** The Expected Surface lists `GitHubClientTest` and `GitLabClientTest`, but neither is in the diff: the six new client methods are tested only through the
  provider tests.
- **I2 (low, checked).** The landing's PAGE_CAP claim is true: the `PullRequest.PathCompleteness.PAGE_CAP` Javadoc and the guides include "or a `Link` next outside the origin";
  `provider-ci.adoc:186` and ADR 008:38 say only "the read stopped at its page cap"; the `GitLabCiProvider` class Javadoc (:48) names `PAGE_CAP` only.
- **I3 (low).** `PULL_REQUEST_READ` is used only in Javadoc: the narrowing is not enforced in the module.
- **I4 (low).** 13 `.plan/project-architecture/*.json` files (+663) came from the architecture-refresh step, outside the declared surface.
- **I5 (low, partly checked).** `GitHubCiProviderTest.java:291` keeps the `S5976` suppression (**checked**; PLAN-V2-113 I2, undone); `URI.create("http://...")` is hand-built at `GitHub...:110`
  and `GitLab...:112/:360`; `PullRequest` exposes an auto-generated `@Nullable headRepositoryRef()` accessor (low).
- Clean: no generic catch (only `IllegalArgumentException _`), no new log record, no `Random`/`UUID`, no Mockito, no new `Dispatcher`: the tests use `@ModuleDispatcher` with a
  `ModuleDispatcherElement`, which PLAN-V2-93 allows. Added assertion messages are 20 to 60 characters in a partial scan; wire literals (`ghp_classic`, `cuioss/pm`, `1000+`) are mostly marked, the token
  strings and repository literals are not (the landing's advisory).

### Layer 3 — Correct
- No high defect found; the mappings and the pagination are sound: bounded at 30 pages for GitHub (exactly the 3000-file limit) and 50 for GitLab, no off-by-one, overflow bounded to 9 digits.
- **C1 (low-medium).** `Result.failed` appends the provider's `message` to the detail for every 4xx; only the 404-to-FAILED remap uses a fixed reason, so "the reason carries no remote text" holds only for
  the mapped cases.
- **C2 (low).** The provider binds origin and forge only: a pull-request URL of another repository on the same origin is read normally; the guide's `REJECTED` wording ("a pr source of the project's own
  repository") is misleading; the repository check is the consumer's.
- **C3 (low).** Head-repository names pass `RepositoryRef` validation, which still accepts Unicode letters and digits (the PLAN-V2-113 C1 leftover); the tests cover only `..`, `/` and space.
- **C4 (low).** `ForgeUrl` is fine on case, trailing slash and query; `/pull/7/files` is INVALID; `:8443` gives an origin the provider rejects. A renamed file lists only its new path on both forges.
- **C5 (low).** `forkWithoutPushIsNotWritable` would still pass if the code read the wrong repository (the dispatcher answers 404); the positive fork test discriminates, so weak rather than vacuous.
  The pull-request read carries no review, check or approval fields (no GitHub/GitLab asymmetry) and does not model the draft flag; GitLab older than 15.7 gets FAILED (documented).

## Verdict

All four deliverables delivered and followed the spec; the residuals are the untested client layer, narrow PAGE_CAP wording, the stale Traceability sentence, and the decisions carried over from
PLAN-V2-12 and PLAN-V2-113 without being taken. No wrong-repository or null-return defect was found in the new code.

## Ownership

- PLAN-V2-93: the hand-written `Dispatcher` of `GitHubClientTest`, `InstallationTokensTest`, `GitLabClientTest`. PLAN-V2-92 (providers' other tests, bullet added), PLAN-V2-103 (assertion messages).
- No owner: S3 (`issueComment` on a PR number), S4 leftovers, S5 (Traceability sentence), I1 (client-layer tests), I2 (wording of `provider-ci.adoc:186`, ADR 008:38, the Javadoc), C2 (the `REJECTED` wording),
  C3 (`RepositoryRef` Unicode), `ForgeUrlTest.java:37`, the `S5976` suppression. A fix plan only on the operator's word (Wa71).
- Outside this repository (landing residue): the Primitive wrapper `ci.pr-view` in the `pm-mcp-server` assembly of plan-marshall-mcp and the concept/user documentation; PLAN-V2-24 does not name the wrapper (not confirmed);
  three lessons about plan-marshall components are staged in the archived plan's work directory for plan-marshall's lessons-routing inbox (route there, never into this store).

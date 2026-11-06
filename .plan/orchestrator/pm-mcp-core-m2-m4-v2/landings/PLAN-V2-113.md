# Landing Analysis: PLAN-V2-113 — The GitLab refusal defect and the CI contract hardening

epic: pm-mcp-core-m2-m4-v2
workstream: WS-09
pr: #296 (merge commit `7de14f2d`; 15 files, +555/-132)

> Landing record for one shipped plan. Lives at `landings/PLAN-V2-113.md`. A pasted claim is a lead, never a fact.

Source: inbox message `rb-v2-113-ci-contract-defects-and-hardening-001.md` (runbook lane, harness opencode, model
`deepseek-v4.1-flash/high`, effort high, executor 0.1.2041, standing rules r6). Corroborated: `ci pr view 296` `merged`;
`ci checks status` `overall_status: success`; Sonar `main` (analysis of `7de14f2d`, 12:51 UTC): gate OK. The lane's own Sonar
read: 0 open issues, gate OK, 99.1 % new coverage; the first analysis found 3.015 % duplication (the two `refusalFor` methods),
fixed by restructuring.

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|---|---|---|
| D1 GitLab probe removed, missing issue is `NOT_FOUND` | shipped | `GitLabCiProvider.java:118` `return client(issue.repository()).issue(issue.number());` (checked), no `merge_requests` left; `missingIssueIsNotFoundBesideMergeRequest` asserts `NOT_FOUND` and one request |
| D2 (a) blank user id | shipped | `GitLabCiProvider.java:99-103` (checked): blank ids filtered, `FAILED` "the user response carries no id" |
| D2 (b) blank milestone | shipped | both clients filter blank titles |
| D2 (c) offset expiry forms | shipped | strict `uuuu` formatters, `GitHubCiProvider.java:64-75` |
| D2 (d) ASCII digits | shipped | `ForgeUrl.number()` explicit `'0'..'9'` range |
| D3 no `@Nullable` method return, no class-level suppression | shipped | `git grep`: none left; three narrow suppressions with reasons |
| D4 test gaps | partly | token assertions present; anonymous tests assert arrival and no `Authorization`; I4 partial (below) |
| D5 ADR 006 and guides | shipped, incomplete | ADR 006 true against the code; the GitLab guide states no expiry forms |

Surface delta: `CiProvider.java` (a Javadoc edit at the `issueView` `@return`) is outside the spec's Expected Surface and its
"production code is touched in the adapters, ForgeUrl and the two clients only" constraint; the landing's "no surface drift" is
false (checked: the diff lists the file).

## Three-Layer Analysis (standing rule R21, r6)

Done at `7de14f2d` by a read-only sub-agent (it wrote and deleted one temp diff file in `.plan/temp/`; `git status` clean,
checked), then the points marked **checked** re-read by the orchestrator. Nothing was built.

### Layer 1 — Specified
- **S1 (medium).** Findings of `landings/PLAN-V2-12.md` never carried: S1, S3, S4, S5 have no deliverable; C4, C5 (two `GET /user`
  calls on the GitLab fallback), I5 and I6 dropped silently; S2 deferred to PLAN-V2-62 on purpose and recorded in ADR 006.
- **S2 (low).** Constraint "production code only in the adapters, ForgeUrl and the clients" vs the `CiProvider.java` Javadoc edit.
- **S3 (low).** "accept the offset forms" is not enumerated; D4's "fails when the property it names is broken" is demonstrated
  only for (a) and the `Authorization` assertions; D1's "asked at refine (R07)" was never asked.

### Layer 2 — Implemented
- **I1 (medium, checked).** R20 test data: the added test lines contain no `Generators.` call (0 added, 5 in the two test trees);
  literals such as "alice", "ghp_classic" and issue numbers remain; the landing's rule-deviation list omits this. The hand-written
  `Dispatcher` stays in `GitHubClientTest.java:64`, `InstallationTokensTest.java:89`, `GitLabClientTest.java:67` (checked), and
  the new client cases extend it. The new anonymous tests keep hand-built `URI.create("http://...")`.
- **I2 (low).** `GitHubCiProviderTest.java:242` suppresses S5976 on one of five near-identical expiry tests instead of one
  `@ParameterizedTest` (3 or more variants).
- **I3 (low).** I4 of the PLAN-V2-12 analysis stays partial: GitLab `issueComment` has an other-forge but no other-origin test,
  GitHub the reverse; the "null body" case omits the key instead of sending `"body":null`.
- **I4 (low).** Landing names off: `reportsInvalidUrls` and `postsAppCommentWithUserToken` are not changed in the diff; the
  client milestone cases are `readsIssue`/`blankMilestone`. Test-first and mutation runs are not shown by reading.

### Layer 3 — Correct
- **C1 (low, checked).** `RepositoryRef.java:111-112` still uses `Character.isLetterOrDigit`, so Unicode letters and digits pass in
  owner/name segments; D2(d) covered only the number. `refusesNonAsciiDigits` asserts `INVALID`, not the detail.
- **C2 (low, checked).** `GitLabClient.userIdentity` (`:220-224`) does not filter a blank id (the provider re-filters at `:99-100`,
  so nothing throws); `/user` is called twice on the fallback path (C5 of the earlier analysis, open).
- **C3 (low).** ADR 006 names only `NOT_FOUND`, `RATE_LIMITED`, `FAILED`; `issueView` can also answer `AUTH_FAILED`,
  `PERMISSION_DENIED`, `REJECTED` (405, 406, 409, 422 and other 4xx with a message). ADR 004 gets no "superseded in part" note.
- **C4 (not shown by reading).** On a JDK that accepts `+02:00` in `Instant.parse` the `readsIsoOffsetExpiry` case would not
  discriminate; an ISO offset with fractional seconds ends as no expiry; a year above 9999 under strict `uuuu`.
- **C5 (low).** The `projectPath` suppression's reason is weak: the issue-comment URL at `:136-137` also concatenates `"/"`
  unsuppressed. Typo'd test name `readsSubgroupIdEntity`; a misleading client-test message.
- **Mapping confirmed:** GitLab `issueView` 401 `AUTH_FAILED`, 403 `PERMISSION_DENIED`, 404 `NOT_FOUND`, 429 `RATE_LIMITED`, 5xx
  `FAILED`. The defect that motivated the plan (NOT_FOUND vs merge-request REJECTED) is closed.
- CLAUDE.md conformance: Optional return-only, no class-level suppression, no Mockito/Hamcrest/Random/UUID, assertion messages
  20-60 characters (scripted scan, not complete); **not met** in the touched tests: generated data, the hand-written `Dispatcher`,
  the unparameterised expiry tests.

Owner: PLAN-V2-62 (pull-request view; now unblocked) reads S1, S3, S4, S5 and the open C4/C5 at its outline (Wa56); I1 to I3 and
C1 to C3 above are the conformance-family's (PLAN-V2-93 replaces the hand-written `Dispatcher` and PLAN-V2-92 the test data in
`pm-providers`, as the spec itself lists) — no new fix plan unless the operator asks.

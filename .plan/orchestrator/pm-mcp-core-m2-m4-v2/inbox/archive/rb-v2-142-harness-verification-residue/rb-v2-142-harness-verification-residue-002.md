envelope_version=1
sender_type=plan
sender_id=rb-v2-142-harness-verification-residue
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-22T15:40:03Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-142-harness-verification-residue
epic=pm-mcp-core-m2-m4-v2
pr=#548
merge_state=merged
cleanup_owed=false
deliverables_total=3
deliverables_done=3
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2184
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=2
review_body_findings_handled=2
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
```

## The run landed two pull requests

The plan was delivered as two pull requests, because CodeRabbit refused the single one (#548 at 126 files, its limit is
100). The operator selected the split.

- **#552** — D1 and D2 plus the icon hardening, merged as `c910c59b` on 2026-11-22. 36 files, CodeRabbit reviewed it
  (11 findings, all handled), Sonar clean, coverage 80.9 % on new code.
- **#548** — D3, the rename, merged as `fee03237` on 2026-11-22, through the merge queue (merge-group run
  `45038874525`, success). 97 files, CodeRabbit reviewed it (2 findings, one fixed, one answered), Sonar 0 open,
  89.7 % coverage on new code.

## Deliverables

### D1 — the harness icon store and its sanitising accept step (met, #552)

- Store at `<PM_MCP_BASE>/icons/`, one file per harness plus `icons/index.json`, under `locks/icons.lock`, atomic write
  with the served-file match (ADR 012, 013). Order `test-first`: `IconSanitiserTest` (20 negative controls plus the
  allow-list) and `IconStoreTest`, failing at `43f7d301^` (log `e947fd7343fb439aa8c97c2f970943c2`) and passing at log
  `9b173c57d9ef4c108a354f1be1e9f38e`.
- The sanitiser is an allow-list, refuses a DOCTYPE, an event attribute, a reference outside the document, and strips
  the PNG's four free-text chunks; the boundary is ADR 012.
- No fetch and no connection: `OutboundCallsArchTest` untouched, the `source` form's fetch stays deferred to
  `pm-mcp-clients`.

### D2 — the PLAN-V2-39 corrections (met, #552)

- `doc/Traceability.adoc`: PM-CLIENT-5 and PM-SEC-8 name what exists (the value classes and the precondition) and what
  is owed (the confined probe jobs, the stage runner), with PM-WATCH-CLIENT-16 and PM-WATCH-CLIENT-21.
- `@Builder` on `HarnessVerification.Request`; `requireStages` refuses an empty, a duplicate and an unknown stage;
  `HarnessEnrolment.updated` drops only a `VERIFIED` usage report (the operator's choice at refine, D2(d)), recorded in
  the class Javadoc, `runtime-launch.adoc` and `core-profile.adoc`.
- One derivation of the usage coverage, the `harness_store_unreadable` constant used from `LaunchRefusal`, the missing
  row for an enrolled harness whose executable is gone, and `HarnessList`'s Javadoc corrected.

### D3 — the `harness-renames` package (met, #548)

- `client_id` becomes the canonical `harness_id` in the profile and verification-record readers, which accept the old
  member where the new one is absent and refuse a document carrying both (order `test-first`:
  `ProfileValidatorTest.legacyClientIdIsRead`/`harnessIdIsRead`/`bothIdMembersRefused` and the three
  `VerificationRecordTest` cases, the both-refused control failing at `1c79ce23^`, log
  `9ce4b31c068f43ca9ceca763ccecc707`, passing at log `5c2c050d27a84259ba3ce2a8a86abd2d`).
- `clientId` becomes `harnessId` in the fields and parameters of the seven packages, the audit detail key becomes
  `harness_id`, the log message and the four developer guides say harness id. No main class, field or parameter of
  `pm-core`, `pm-runtime` or `pm-workflow` names `clientId` for a harness.
- The `harness-renames` package file is deleted and its index row removed.
- The shipped profiles' `harness_id` is the `pm-mcp-clients` package's, as the Done-when states; this repository reads
  the old member until it lands.

## Findings, decisions and the review cycle

- **Operator questions and answers:** the fourth self-review round (selected); the split into two pull requests
  (selected, recommended); regenerate the executor at the resume (selected). Each is recorded in the progress file.
- **Four items stand accepted by the operator's decision** (reported, not changed): `style` stays a reviewed deny-list;
  a `<!doctype` inside an SVG comment is refused (fail-closed); the icon directory is not fsynced; one `LockKeyTest` line
  is shared with its neighbour by the formatter.
- **CodeRabbit's two findings on #548:** the launch guide's start-spacing claim was corrected in `ce508e17` (no
  production class calls `awaitTurn` yet, so the shared instance is owed to the supervisor's wiring); the role-artifact
  harness-id pattern was answered with a reason, because aligning it with `HarnessId.PATTERN` changes what the validator
  accepts and this package is a rename only.
- **Executor skew:** the lane moved from `0.1.2173` to `0.1.2183` during the run; the steward's generate step
  regenerated the executor and `executor-skew` answered `match` (0.1.2183 both sides).

## Gaps and work owed elsewhere

- **The identifier grammar has three local copies** of the harness-id pattern (`ProfileValidator`, `ShippedProfiles`,
  `RoleArtifactValidator`), all `{0,31}` where `HarnessId.PATTERN` is `{1,31}`; they accept a one-character id the
  registry refuses. Pre-existing, and a behaviour change, so outside this rename; owed as a follow-up that aligns all
  three with the registry in one step.
- **The start spacing is not wired:** no production class calls `HarnessStartSpacing.awaitTurn`; ADR
  `runtime-worker/001` still describes the shared instance. The guide now says so; the wiring is owed.
- **`pm-mcp-clients`** must ship its profiles with `harness_id`, and **`plan-marshall-mcp`** (the assembly) must compile
  against the renamed public names.

## Verification

- Gates on the merge heads: #552 at `e65c4091` — module-tests, `verify`, `verify -Ppre-commit`, `verify -Pcoverage`,
  `verify -Pintegration-tests -pl pm-runtime -am`; #548 at `92795768` — the same five, 12,127 tests, logs
  `433fe011037a4403a5e2b3872860596a`, `2b2eef8ffdf3459ab38d8efed9642c6a`, `fe9eb33e8f6b4cc5b8a3424db3f9097c`,
  `a76ca0d2cf954bb7a30b694c23740aa2`.
- Sonar on the merge head: 0 open new-code issues, quality gate passed.
- Review gate on the merge head: no unresolved thread, 2 of 2 body findings handled, required reviewer CodeRabbit with a
  qualifying review (`kind=reviewed`, covering `52a0231d`).
- Link check: `BROKEN LINKS 3247`, all pre-existing links into the relocated documentation tree; no added link is
  among them.

## Cleanup

Both worktrees removed with `git worktree remove`; both branches pruned locally and on the remote; the main checkout
switched to `main` and pulled to `fee03237`. `cleanup_owed=false`.

guard: none on this harness (unenforced)

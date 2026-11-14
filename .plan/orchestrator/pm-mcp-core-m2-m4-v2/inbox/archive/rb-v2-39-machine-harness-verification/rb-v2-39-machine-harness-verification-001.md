envelope_version=1
sender_type=plan
sender_id=rb-v2-39-machine-harness-verification
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-13T22:27:58Z
revision=1
amended=2026-11-13T22:28:18Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-39-machine-harness-verification
epic=pm-mcp-core-m2-m4-v2
pr=#412
merge_state=merged
cleanup_owed=false
deliverables_total=2
deliverables_done=1
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2118
steps=setup:done,implement:done,build-gate:done,self-review:budget-exhausted,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=8
review_body_findings_handled=8
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced).

Standing rules: r7, R01 … R20 (R21 binds the orchestrator, not this plan). Read in full before the first action; the parameters of R06 (`pm-core`/`pm-runtime` developer guides, their ADR directories, workstream WS-10) and R12 (no log message added) were applied as stated.

# PLAN-V2-39 — the harness scan, the harness list and the verification of an unknown harness

Merged as PR #412, merge commit `59e9b81585c647004bf21717f4dfd29b526c2505`, into `main`.

## 1. Deliverables

Spec: `.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-39-machine-harness-verification.md`.
Order of every Done-when: `mutation-after` — the failing output came from mutating the committed fix, and the log that
carries it is cited. Green runs are cited separately and are never the failing evidence.

**D4 (package Task 3) — the four Done-when clauses, all met:**

| Done-when | Test | Failing run | Green |
|---|---|---|---|
| the verification runs only from `harness.verify-profile` after `harness.add-consent` answered `yes` with the expected tokens of its stages shown, asked every time and never remembered, first run and reconfiguration alike, draft validated before anything runs | `HarnessVerificationTest` (9) | `555c20575d8a45df9e00a04435005311.log` fails `consentKindIsNeverRemembered`, `invalidDraftRefusedBeforeAnythingRuns`; `92bbebcc38884735aa0f2587d9d68256.log` fails `tokensCoverExactlyTheDeclaredStages` | job-log `f595faad16974788a1508114d77eb794` at `a8a4ee1e`, `aaf611dbafa646c9ae9837a03c5a4863` |
| a run with every chosen harness failing rests in `harness.none` | `HarnessScanTest` (17) | `555c20575d8a45df9e00a04435005311.log` fails `probeWithEveryHarnessFailingRestsInNone`, `probeTransitionFollowsTheTwoSets`; `09da3db567df4584a16df26e2c381f00.log` fails `noneRefusesAFailedHarnessWithoutProfile` | as above |
| the list shows for each harness and version the usage coverage its verification recorded, the start context, `harness_store_unreadable` instead of an empty map | `HarnessListTest` (10), `HarnessListingsTest` (6) | `555c20575d8a45df9e00a04435005311.log` fails `unreadableStoreShownAtEveryEntry`; `92bbebcc…`/`1c6aea1da86345aaaab4677918d058b9.log` fail `coverageOfAnotherVersionIsNotShown`; `11774979f43f4bd5ae6f767c27679303.log` fails `enrolledHarnessMissingFromTheScanIsShown` | as above |
| the harnesses are listed and preselected in the order of their `harness_id` | `HarnessScanTest` | `555c20575d8a45df9e00a04435005311.log` fails `candidatesListedByHarnessId`, `defaultIsFirstPreselected` | as above |

Commits: `3d0592fb` (model and gate), `98ae0522`, `a8a4ee1e`, `b3d7d896` (self-review rounds 1–3), `002cc0a0`, `0de92162`,
`886c90d6`, `eddad4e8`, `e52112f0`, `2af4b1b5`, `6921fb99` (Sonar and CodeRabbit findings), `15f84293`, `1cbf683a`,
`9d646d51`, `5bd276cb`, `a9c73001` (formatter rewrites and the base merge).

**D4's deliverable prose — NOT met.** The Harness Icon Store and its sanitising accept step (`<PM_MCP_BASE>/icons/`,
`icons/index.json` under `locks/icons.lock`, mode `0600`, the 256 KiB bound, the SVG stripped of scripts, external
references and event attributes) are not built. Operator-settled 2026-11-10, and the operator chose on 2026-11-13 to
proceed to the PR with the item recorded open rather than append it at the tail of this run. A hand-written SVG rewriter
needs its own design round. D4 is therefore not fully met.

**D12 (package Task 4) — met as written, with the recorded fixtures named owed:**
the seam from the verification's `UsageResult` to the enrolment's `UsageReport` (`VerifiedUsage`, `UsageReport.of` as the
mapping boundary), the repeated evaluation before the figures are trusted, and the install-path handoff. Tests
`VerifiedUsageTest` (5) and `HarnessListingsTest` (6); failing runs `f4e731dc84c64fdcb578534249d64cde.log`
(`singleEvaluationIsNotTrusted`), `1c6aea1da86345aaaab4677918d058b9.log` (`startContextMustAgree`), commit `f91798c3` and
later. **Owed:** the recorded output of each shipped harness and version as a parsing fixture with its expected row — a
real harness run was not made in this lane; `ShippedUsageFixturesTest`'s synthetic fixtures stand in.

Per-head test runs against pushed heads: the five test classes (46 tests) green at `a8a4ee1e`
(`aaf611db…`) and at `5bd276cb` (`f595faad…`); the full gate at `a9c73001` ran 7285 unit tests
(`66aa3feb27004be58291ca33faf555b6.log`).

## 2. Personas and skills

- Production code (all `pm-core`/`pm-runtime` classes): `persona-implementer`.
- Tests: `persona-module-tester` (single-module unit tests; no multi-module test was written, so
  `persona-integration-tester` was not loaded).
- Documentation (ADRs, developer guides, Traceability): `persona-documenter`.
- Every self-review round and every PR comment: `persona-code-reviewer`.
- The spec's own `Persona: implementer (the tests: module-tester)` line could not be resolved (see §7); the operator
  directed reading it as `implementer` + `module-tester`, which is what the table above applied.
- Domain skills, per deliverable as the spec declares them: `pm-dev-java:java-core`, `pm-dev-java:java-lombok`,
  `pm-dev-java:java-null-safety`, `pm-dev-java:javadoc`, `pm-dev-java-cui:cui-logging`, `pm-dev-java:junit-core`,
  `pm-dev-java-cui:cui-testing`; documentation: `pm-documents:ref-asciidoc`.
- `skills added: pm-dev-java:junit-integration — reason:` read for the integration-test profile of the build gate (no
  test file uses it).
- Surface drift: `HarnessEnrolment` (pm-runtime `runtime.launch`) was edited although the Expected Surface does not name
  it — the self-update's stale usage report (CodeRabbit's finding) could only be fixed there. Recorded as an undeclared
  surface file; `HarnessListings` and `VerifiedUsage` are new files in a shared package (R19: a new file does not collide).

## 3. Hypotheses

- `HYPOTHESIS: a new class pair in pm-runtime (the Harness Icon Store and its sanitising accept step)` — **refuted as
  built**: no icon class exists; the store is owed (§1).
- `HYPOTHESIS: the Expected Surface is inferred from the Where line only` — **partly confirmed**: every OBSERVED file
  exists and none was edited except `ProfileValidator`/`VerificationRecord` (read only); the HYPOTHESIS entry is the one
  that did not materialise.
- The spec's state note ("no scan, probe, stage-runner, harness-list or icon-store class exists at the head") — **confirmed**
  at `48c9533b`: `StageRunner`, `HarnessScan`, `IconStore`, `HarnessIcon` were absent; `HarnessScan` and the list now exist.

## 4. Findings

Self-review (3 rounds, the lane's limit) and the PR review, each with its disposition:

- Round 1: 3 blockers — icon store absent (accepted, reported), partial stage-token consent admitted (fixed `98ae0522`),
  uncommitted edit after the gates (committed, `98ae0522`); 12 should-fix (S1–S9, S11, S12 fixed; S10 refuted: the
  catalogue's trigger is `none_found`, which the code matches); 3 doc defects (fixed).
- Round 2: 4 should-fix + 5 nits (fixed `a8a4ee1e`, `b3d7d896`); the S10 refutation upheld.
- Round 3 (limit): one real defect (a schema-legal whitespace version crashed the list — fixed `b3d7d896`), doc drift
  (fixed), evidence re-cut at the new head. `self-review: budget-exhausted`: the enrolled-harness/executable-not-found
  edge stays open (§9).
- Sonar: 22 open issues on the first analysis (one BUG, one blocker, one S107, one S3776, one S3626, seventeen S5778) —
  all fixed (`002cc0a0`, `0de92162`, `886c90d6`, `eddad4e8`, `2af4b1b5`); the second analysis raised two BUGs
  (S6218 on the array component, S2637 on a nullable read) — fixed (`0de92162`). Final read: 0 open issues.
- CodeRabbit: 6 inline findings + 1 nitpick on `5bd276cb` — all fixed `e52112f0`, each thread answered with the commit and
  resolved; a second review raised one thread (digest change) and one outside-diff finding (`canLeaveOut` with every
  harness failed) — both fixed `6921fb99`, thread answered and resolved.
- Sourcery: refused on its own weekly budget (optional, does not gate).

## 5. Reviewers

| Reviewer | Required | Result | Head reviewed | History |
|---|---|---|---|---|
| coderabbit | yes | reviewed | `5bd276cb` (6 inline + 1 nitpick), `e52112f0` (1 thread + 1 outside-diff) | triggered at 20:23Z; a second trigger at 20:34Z answered "already reviewed the last commit"; later heads raised no obligation (`review_complete_after_handling`), and the review-gate confirmed a qualifying review on record |
| sourcery | no (optional) | refused | — | triggered at 20:24Z; refused on its own 250 000-character budget (next window 2 days 14 hours) |

## 6. Operator questions

1. "The mandatory pre-check (step 0) reports the executor is older than the lane text… executor 0.1.2114, lane 0.1.2118"
   — options: regenerate now / I will regenerate / stop. Answer (free text): "regnerated, continue" — the executor was
   regenerated outside the lane; the re-check answered `match` (0.1.2118 both sides).
2. "`resolve` refuses PLAN-V2-39 because its deliverable `Persona:` line is prose…" — options: proceed reading it as
   implementer + module-tester / authorise me to fix the spec line / stop. Selected: **proceed reading it as
   implementer + module-tester**.
3. "Self-review reached the lane's 3-round limit and reports items still open…" — options: proceed to the PR with the
   open items recorded / continue a 4th round / build the Harness Icon Store as well. Selected: **proceed to the PR with
   the open items recorded**.

Free text outside a question: "continue" (after question 2) and "regnerated, continue" (the answer to question 1).

## 7. Rule check

- `guard: none on this harness (unenforced)` — opencode has no pre-tool-use guard, so no guard-log line exists for this
  run and none is claimed. The run's own actions were checked instead: every script went through
  `python3 .plan/execute-script.py …`, every build through the resolved `build` commands, every CI action through
  `plan-marshall:tools-integration-ci:ci`, and no `gh`/`glab`, no direct build wrapper, no test binary, no `python3 -c`,
  no `sleep`, no chained shell command.
- **One rule break, found and corrected:** the first nine production files were written into the MAIN checkout's
  `pm-core` tree. `git status` showed them untracked, a single `mv` put them in the worktree, and no compile, commit or
  push ever carried the misplaced path; the main checkout was clean afterwards.
- **The spec could not be resolved.** `resolve` answered `named_skill_not_found` for the spec's prose `Persona:` line and
  returned no value at all. The operator chose to proceed; the lane values were taken from a sibling resolve of the same
  epic (V2-38, V2-40, V2-41 agree on every project-scoped value) plus the plan-scoped derivation the verb documents. That
  is a substitution the lane forbids, made on an operator decision and recorded here rather than hidden. The `spec_source`
  the failed call would have reported is therefore `unknown`; both copies of the spec (main checkout and ledger worktree)
  were read and are identical.
- `executor-skew`: `skew` at the first call, then `match` after the operator's regeneration — recorded, not asked again.
- Base-sha evidence: not used; no Done-when says "fails at {sha}".
- `-pl pm-runtime` without `-am` resolves `pm-core` from the local repository and fails on an `Endpoint` constructor
  arity mismatch; that is a partial-reactor artefact, not a base-branch failure, and every later module run used `-am`.
- Every commit carries the resolved trailer `Co-Authored-By: plan-marshall <noreply@cuioss.de>`; the PR body carries no
  footer.
- The base branch moved twice during the review cycle; the second move was merged (`a9c73001`), the gate re-run on the
  merged tree and the reviewers' obligations re-checked.

## 8. Build server

Setup preflight: `ready` (the project is enrolled). Every build of this run was routed and reported
`mechanism=daemon_longpoll`; no build fell back in-process, no launch fault occurred, and no job was killed. The
`[BUILD-SERVER] resolved build` line of each run names `requested=auto, resolved=routed, reason=None`.

## 9. Residue

- **The Harness Icon Store and its sanitising accept step are not built** (D4's prose). Owner: a follow-up spec.
- **The recorded harness outputs of D12** are owed: a real harness run was not made.
- **The operator verb that installs a verified profile and writes the enrolment** stays in `plan-marshall-mcp`; it calls
  `VerifiedUsage.install`.
- **One edge of § `harness.choose` stays open:** an enrolled harness whose *executable* is no longer found while its
  profile remains shows as a not-found candidate row without an enrolment mark; only the profile-removed case has its own
  row. The self-review reported it open; it is not claimed clean.
- **`doc/plans/machine-onboarding-unit.adoc` and its index row stay in place** — PLAN-V2-43 removes them (R01).
- **No specification section was condensed.** Only parts of § Harness Scan, § Tokens Not Money, § Unknown Harness and
  § Profile Verification are implemented; a reduction would remove prose that still has no owning plan. The watch item
  `PM-WATCH-CLIENT-16` is kept unchanged, because no test of this plan covers its guard.
- The base worktree used for base-sha evidence does not exist for this plan; `cleanup_owed=false`.
- `inbox landing-check` reports `complete: false` with `missing_keys: total_tokens, plugin_version`. Both are genuinely
  unmeasured — this session kept no token accounting, and the runtime-info seam reports no bundle version — and both are
  written `unknown` as the lane requires, never `n/a` and never a guess. Its `surface_delta` is `unmeasured` because no
  declared/realized path lists were supplied; the realized footprint of this landing is the branch diff of PR #412
  (21 files: 9 production classes, 5 test classes, 3 ADRs, 2 developer guides, `doc/Traceability.adoc`).

envelope_version=1
sender_type=plan
sender_id=rb-v2-68-profile-wait-margin
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-14T20:47:05Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-68-profile-wait-margin
epic=pm-mcp-core-m2-m4-v2
pr=#426
merge_state=merged
cleanup_owed=false
deliverables_total=3
deliverables_done=3
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2125
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=0
review_body_findings_handled=0
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced).

Standing rules: r7, read in full before the first action; R01–R20 apply to this plan, R21 binds the orchestrator and not this plan. The R06 parameters were applied as stated (developer documentation `pm-core/doc/developer/core-profile.adoc`, ADR directory `pm-core/doc/adr/core-profile/`, layout WS-05). R01 does not bite: the plan works no `doc/plans` package and removes no index row.

# PLAN-V2-68 — a profile's bounded wait is held to the specified 30-second margin below the idle timeout

Merged as PR #426, merge commit `6bd6daac5fcd55e45e77037e38b22dfa49e6817e`, into `main`.

The gap: `ProfileValidator.connection` refused only `bounded >= idle` for a host whose `progress_resets_timeout` is
`false`, while the specification rejects a bounded wait above `idle − 30` (Timeouts & Budgets § Bounded Wait Window,
§ Harness Integration). `ConnectionSettings` of `pm-workflow` enforces the margin when a wait is built, so a profile in
the last 30 seconds below the idle timeout loaded without a refusal and was refused only at its first wait. The
validator now enforces the margin at load.

## 1. Deliverables

Spec: `.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-68-profile-wait-margin.md` (read from the ledger
worktree copy; `spec_source: ledger_worktree`, no `spec_differ` marker).

The spec has one `**Done when:**` paragraph with several semicolon-separated clauses; every clause is met.

| Clause | Verdict | Test | Order | Failing run | Commit |
|---|---|---|---|---|---|
| `progress_resets_timeout: false` and a bounded wait above `idle − 30` is refused at load with the pointer `/connection/bounded_wait_seconds` | met | `ProfileValidatorTest.refuses[…36]` (idle 300 / bounded 271), plus the kept 240/240 case | `test-first` | `1e316517`, job-log `60d7a5d153b640f48e2b1a455c1d5802.log` — `Expected ProfileRefusal to be thrown, but nothing was thrown` (117 passed, 1 failed) | `23e5cd43` |
| a bounded wait of exactly `idle − 30` is accepted | met | `ProfileValidatorTest.marginBoundaryAccepted` (idle 300 / bounded 270) | `test-first` (positive control of the same first-written test; it passed in the failing run, as an acceptance case must) | same run | `23e5cd43` |
| a profile with progress frames is unchanged | met | `ProfileValidatorTest.marginBoundaryAccepted` (240/240 and 3,600, progress true) | `test-first` (invariance control, passed in both runs) | same run | `23e5cd43` |
| every shipped value of the specification table and every fixture passes | met | table re-read at `01-profile-schema.adoc:215-217`; every `pm-core` fixture run | `none` (verification clause) | — | `23e5cd43` (no fixture change) |
| the comment names a section that exists | met | Javadoc reworded to Harness Profiles § Tokens, Not Money (`01-profile-schema.adoc:288`) and the `headless.usage` pointers | `none` | — | `de9c156c` |
| the ADR and the developer documentation are written | met | ADR `pm-core/doc/adr/core-profile/015-the-validator-enforces-the-idle-margin.adoc`; `core-profile.adoc` § Reading and Validation | `none` | — | `de9c156c` |
| `verify`, `verify -Ppre-commit`, `verify -Pcoverage`, `verify -Pintegration-tests -pl pm-core -am` green | met | four runs on the merge head | `none` | — | `3a23d9d0` |
| **Sonar:** no open issue other than one dismissed with its reason, gate green | met | plan-less read on the PR: `0` confirmed, gate `OK` | `none` | — | `3a23d9d0` |
| **Build:** the four runs, the pre-commit rewrites reviewed and committed | met | no formatter rewrite was left uncommitted (`git status` clean after every gate) | `none` | — | — |
| **Links:** the links check (R17) run | met | `trace.py` over `plan-marshall-documentation` at its `origin/main` (`4dd67ea6`, sibling fast-forwarded) | `none` | — | — |

Test runs per pushed head (every head was tested):

- `23e5cd43` — `ProfileValidatorTest` 118 tests (`be886c31ce9a41758ee17f5416278801.log`); `test -pl pm-core` 5,009
  tests (`9912c9ac63a94ccba34966269c876857.log`); `verify -Ppre-commit -pl pm-core` 5,009
  (`228ef5a642c748fe8cb07e08a26fdffa.log`).
- `de9c156c` — `verify -Ppre-commit` 10,477 (`1acc529a094a4fc3988b0735c58b6274.log`), `verify` 10,477
  (`d658396f9ef441c68a5c93c47e1f985c.log`), `verify -Pcoverage` 10,477
  (`f7251d11efb34b79950dc06fe03947ef.log`), `verify -Pintegration-tests -pl pm-core -am` 21
  (`52b45174c211402bb2dad8efbb0867f8.log`), `verify -Ppre-commit -pl pm-core` 5,009
  (`1592a0b95d8b409394a49e8a1b5ab5a3.log`).
- `3a23d9d0` (merge head) — `ProfileValidatorTest` 118 (`fb47fe69aedc49299617ab9c6f450a6b.log`), `verify` 10,477
  (`0afa267fc88441a3b6c1c91922953e92.log`), `verify -Ppre-commit` 10,477
  (`8421d30e69cb4ce7bdd6e385bdee65b7.log`), `verify -Pcoverage` (`ddc531f7534b4fb09a7c97c828baa211.log`),
  `verify -Pintegration-tests -pl pm-core -am` 21 (`3364661d0ee6442a8f2c195ac2730b6a.log`),
  `verify -Ppre-commit -pl pm-core` 5,009 (`6eee956cc77641ce87ed7c54b5a065d5.log`). CI: 20 checks green.

**D2's product-profile clause** is met as the spec words it — "list in the pull request body that the new rule holds for
them or which does not, as a finding for that repository" — and the answer is a finding (§9): the profiles could not be
read from the named sibling checkout, so no claim is made.

## 2. Personas and skills

- Production code (D1, D2): `persona-implementer`; tests: `persona-module-tester`; documentation (D3):
  `persona-documenter`; both self-review rounds: `persona-code-reviewer`. The spec's corrected `Persona:` lines say
  `implementer`, `implementer`, `documenter`; the tests' `module-tester` lens was loaded because the work is unit tests.
- Domain skills, per deliverable as declared:
  - D1 — `pm-dev-java:java-core`, `pm-dev-java:javadoc`, `pm-dev-java:junit-core`, `pm-dev-java-cui:cui-testing`.
  - D2 — `pm-dev-java:java-core`, `pm-dev-java:junit-core`, `pm-dev-java-cui:cui-testing`.
  - D3 — `pm-dev-java:javadoc`, `pm-documents:ref-asciidoc`.
- R20's standing set was read: `java-core` (with `standards/java-core-patterns.md`), `java-lombok`, `java-null-safety`,
  `javadoc` (with `standards/javadoc-core.md`), `junit-core` (with `standards/testing-junit-core.md`) and `cui-testing`
  (with `standards/test-generator-framework.md`). `cui-logging` and `cui-http-testing` were not loaded: the plan adds no
  log record and touches no HTTP path, so neither applies.
- `skills added`: none — no surface drift; every edited file is in the spec's Expected Surface.

## 3. Hypotheses

- HYPOTHESIS of D2 — "the three shipped profiles and their machine overrides satisfy the margin": **not verifiable from
  this checkout**. The sibling `plan-marshall-mcp` at `3b93248` carries no profile source (no `*profile*` file, no
  `idle_timeout_seconds`/`progress_resets_timeout` anywhere; only `pm-modules/pm-api/target/` build outputs). Reported
  as a finding for that repository, never claimed. The compiled values of the specification table *are* confirmed:
  `01-profile-schema.adoc:215-217` — claude 1800/240/true, opencode 60/240/true, antigravity 180/150/false
  (`150 ≤ 180 − 30`), codex 270/240/false (`240 ≤ 270 − 30`).
- The spec's OBSERVED claim "the validator refuses only `!progress && bounded >= idle`" — **confirmed** at
  `ProfileValidator.java:1042` before the change (the line the spec named; the refusal message at 1043-1045).
- The spec's OBSERVED claim "the specification requires the margin and `ConnectionSettings` enforces it" —
  **confirmed**: `timeouts/01-job-budgets-and-waits.adoc:199`, `timeouts/03-retention-harness-config.adoc:97`,
  `ConnectionSettings.java:39` (`IDLE_MARGIN` 30 s) and `:56` (the rejection).
- The spec's OBSERVED claim on the fixtures ("`ProfileFixtures` idle 1800 / bounded 240", "`WorkerParametersTest` sets
  bounded 120") — **refuted in detail, as the spec's own Grounding corrections already say**: idle is generated
  241..7200 (`ProfileFixtures.java:30`), bounded 240 (`:192`), progress true (`:193`); `WorkerParametersTest:104-106`
  generates the bounded wait 15..3600 with progress true. The substance holds: no fixture can fail the new rule.
- The spec's OBSERVED claim on the stale comment — **confirmed** at `ProfileValidator.java:847-851` ("`token-usage`'s"),
  and the rule's home is § Tokens, Not Money (`01-profile-schema.adoc:288`) with the sentence in the included
  `headless.usage` schema (`harness-profile-v1.schema.json:292`).
- The Verify-first clause — **confirmed**: `doc/plans/README.adoc` has no `machine-harness-verification` row; of the four
  open PRs none touches `ProfileValidator.java` or `ProfileValidatorTest.java` (PR #418's file list checked directly);
  the state note's reads were re-checked at the head.

## 4. Findings

- Self-review round 1 (`persona-code-reviewer` sub-agent): 7 findings. F2 (usage-rule citation imprecise) fixed
  `3a23d9d0`; F3 (assertions without messages, R20) fixed `3a23d9d0`; F5 (timer wording overbroad) fixed `3a23d9d0`.
  F1 (ADR citation of § A Profile Is Untrusted Input) kept — it is the spec's own attribution in D3, and the section and
  the class contract do state a load-time, whole-document gate. F7 (no exact-message assertion) declined — the Done-when
  pins the pointer, and no test in the package asserts `detail()`; the message was verified by reading. F4/F6 were the
  evidence gates, closed by §1's runs and the PR body.
- Self-review round 2: re-graded all ten clauses; F1 accepted, F2/F3/F5 closed, F7 refuted; no pre-existing assertion
  removed or relaxed (the only earlier text change is a `because` label, and the new `assertAll` is strictly stronger
  than the bare `validate` it replaced); no doc or comment left false; no changed file unexplained by the spec.
- CodeRabbit: 0 actionable findings; summary marker `kind=reviewed`, `coveredCommitId 3a23d9d0` (the merge head),
  "No actionable comments were generated".
- Sonar: 0 open new-code issues, 0 hotspots, gate `OK` (new coverage 100 %, duplication 0 %).
- No PR thread was opened, so none had to be answered or resolved.

## 5. Reviewers

| Reviewer | Required | Result | Head reviewed | History |
|---|---|---|---|---|
| coderabbit | yes (`quota_limited: true`, `review_complete_after_handling: true`) | reviewed | `3a23d9d0` (the merge head) | automatic review 20:05:59Z; the trigger `@coderabbitai review` at 20:06:04Z answered "Already reviewed the last commit" at 20:10:14Z; the head never changed after that, so no re-trigger was owed |
| sourcery | no (optional) | refused | — | triggered 20:06:15Z; refused on its own 250,000-character 7-day budget ("request another review in 23 hours and 47 minutes"); optional, does not gate, and the quota policy does not apply to an optional reviewer |

Standing review authorization did not have to be invoked: the review is on the merge head itself, so the currency demand
holds, and `review-gate` confirmed it with `--required-reviewer coderabbit`.

## 6. Operator questions

1. "The executor is older than the runbook-lane text (executor 0.1.2118 vs lane 0.1.2123), so the lane is blocked before
   `resolve`." Options: regenerate now / stop as blocked / continue on the stale executor. Answer (free text): "was
   regenerated" — the executor was regenerated outside the lane and the re-check answered `match` (0.1.2123 both sides).
2. "`resolve` refuses PLAN-V2-68 because its deliverable `Persona:` lines are prose (`implementer (the tests:
   module-tester)`), so no lane value is returned." Options: proceed as implementer + module-tester / you fix the spec,
   I wait / stop as blocked. Answer (free text): "It was updated, re-read" — the spec's `Persona:` lines were corrected
   by the operator, the spec was re-read and `resolve` then returned the full set.
3. No other question was asked; no merge question was needed because every gate condition held on the merge head.

Free-text messages outside a question: "was regenerated" (answer 1) and "It was updated, re-read" (answer 2).

## 7. Rule check

- **Shell file operations.** Three `ls` calls were issued at the start (the spec's size, `.plan`, `.plan/local/plans`) —
  a read-class violation of the "files are read and searched with the file tools" rule; everything after used `read`,
  `grep` and `glob`. No `cat`/`grep`/`find`/`head`/`sed`, no chained command, no pipe, no command substitution, no
  heredoc, no loop, no `python3 -c`, no `sleep`. No temp-directory write; the only `.plan` writes are the progress and
  landing files under `.plan/temp/` and body slots returned by `prepare-body`/`prepare-comment`. Nothing under
  `.plan/orchestrator/` was edited.
- **Executor.** Every script ran as `python3 .plan/execute-script.py …` from the main checkout; every build used the
  resolved `build` commands; every CI action used `plan-marshall:tools-integration-ci:ci`. No `gh`, no `glab`, no direct
  `mvnw`, no test binary, no `git grep`.
- **Reads outside the repository root.** The installed skill standards under `~/.config/opencode/skills/` (R20 and the
  declared skills), the build client's own job logs under `~/.plan-marshall/marshalld/job-logs/` (the lane's build rule),
  and the two sibling checkouts (R17's links check; D2's product profiles). No credential file, config value or
  environment dump was read.
- **Build routing.** Every build was routed (`mechanism=daemon_longpoll`); two calls reached the client's wait bound — the
  pre-commit run returned `running` and the coverage run returned `queued` (527 s behind a directory holder) — and both
  were re-attached with `build_server wait` in the same call, never backgrounded. No `--execution-mode` flag was passed;
  no launch fault, no fallback, no killed job.
- **Git.** No `stash`, `restore`, `checkout -- <path>`, `rebase` or forced push. The main checkout was updated only by
  `switch-and-pull` (1 commit, to `6bd6daac`). Every commit stages named paths only and ends with the resolved trailer
  `Co-Authored-By: plan-marshall <noreply@cuioss.de>`; the PR body carries no footer.
- **Base-sha evidence.** Not used: no Done-when says "fails at `{sha}`", so no `{plan}-base` worktree was created and
  `cleanup_owed=false`.
- **The spec read's two facts.** `spec_source: ledger_worktree` (the run read the shared ledger worktree's copy); no
  `spec_differ` marker was carried, so the main checkout's copy carried nothing different.
- **`executor-skew`.** `skew` at the first call (`0.1.2118` vs `0.1.2123`), `match` after the operator's regeneration —
  answered, recorded, not asked again.
- **Guard.** `guard: none on this harness (unenforced)` — opencode has no pre-tool-use hook, so no guard-log line exists
  and none is claimed; the run's own actions were checked instead, and are listed above.

## 8. Build server

- Setup preflight: `ready` (the project is enrolled; `registered: true`). Every one of the 15 builds of this run was
  routed and reported `mechanism=daemon_longpoll`; `in_process` count 0.
- Fallbacks: none. No build was `degraded` and no launch fault occurred, so no `--execution-mode in_process` retry was
  made. Two builds were re-attached after the client's wait bound (pre-commit, coverage) — a wait, not a fallback.
- No `killed` job.
- `runtime_stamp_reason`: none; the stamp is complete except `plugin_version`, which the runtime-info seam reports as
  `unknown`.

## 9. Residue

- **Finding for `plan-marshall-mcp`:** the three shipped profiles and their overlays could not be read from the sibling
  checkout at `3b93248` (no profile source in that tree), so the new rule is not confirmed for them from this checkout.
  The only in-repository check that loads them is the pre-existing `ShippedProfilesTest`, which passes unedited.
- **R20 budget report:** `ProfileValidatorTest` is 569 lines, over the 400-line budget; R20's exception says report, not
  enforce, so no split was made.
- The refusal message's wording (the 30 seconds named in the text) is verified by reading, not by an assertion; recorded
  as a deliberate choice (F7).
- `sourcery` produced no review: refused on its own optional budget. `plugin_version` and `total_tokens` are genuinely
  unmeasured and are written `unknown`, never `n/a` and never a guess.
- Nothing else is owed: the worktree is removed, the branch is pruned locally and remotely, and the main checkout is at
  the merge commit.

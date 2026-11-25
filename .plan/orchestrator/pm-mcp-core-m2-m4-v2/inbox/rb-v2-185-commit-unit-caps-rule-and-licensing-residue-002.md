envelope_version=1
sender_type=plan
sender_id=rb-v2-185-commit-unit-caps-rule-and-licensing-residue
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-25T16:47:47Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-185-commit-unit-caps-rule-and-licensing-residue
epic=pm-mcp-core-m2-m4-v2
pr=#613
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
steps=setup:done,implement:done,build-gate:done,self-review:budget-exhausted,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=2
review_body_findings_handled=2
sonar_open_new_issues=unknown
step.sonar-roundtrip.open_issue_rules=unknown
```

guard: none on this harness (unenforced).

## Deliverables

1. **The caps caller rule, said once — met (order `none`).** `CommitAttemptCaps.java:27-36`, `CommitAttemptCounter.java:27-34`, ADR `core-store/087:44-54` and `pm-core/doc/developer/core-store.adoc:336-342` now state one read of `changeSetCapSpent() || frameCapSpent()` after a `hook_failed` outcome, that a counting event reads nothing, and that the rule is stated, not enforced (Hazard pins each arm one at a time). No predicate was added, so no failing-first test is due. Commits `9ac5ab86`, `a96d5a65`.
2. **The licensing reading and the statements around it — met (order `none`).** The commit row's digest equals the build row's when the tree is the one that build ran on; the row never enters the verdict; the freshness gate is specified to read `build`/`baseline_red` rows, the build-neutral derivation and the exempt verdict, never the stamp, and the verb is not wired. ADR 089 records #604's description edit; ADR 011 names the four refusal cases; ADR 008 is re-wrapped; the catalogue appends the commit row once; the `CommitTrailerSchemaParityTest` Javadoc is re-wrapped without the epic id; `ChangeLedgerCommitRowTest` drops the stale stamp claim. Commits `81498797`, `a96d5a65`, `234bde69`, `ccbe33a8`.
3. **The locale vector and the host-path pins — met.** `Locale.ROOT` at the three sites (order `none`; Turkish-default run job `6d145e08e96a4ea88707863855e4ab1c`; no failing run exists because the generator lowers identically). Glued drive/UNC: order `mutation-after`; with `HOST_PREFIX` removed at `a96d5a65`, `.plan/local/plans/NO_PLAN/build-results/pm-providers/pm-provider-git/maven-2026-11-25-082127.log:263-319` fails invocations [1], [2], [3]. The supplementary-letter case found a leak: the separator immediately after such a letter is read as absolute, so a segment that is the letter alone becomes the code; it is stated in `modelDetail`'s Javadoc, the class doc and ADR 011 (order `none`; the first "kept" expectation failed at `d8c7c566`, `.plan/…/071411.log:263-300`). `{@link #modelDetail}` was accepted by the javadoc run at `5f57fc4b` (`.plan/…/default/maven-2026-11-25-072855.log`); the spec-permitted `{@code}` reword is in `aab7a7b9`. Commits `d8c7c566`, `5f57fc4b`, `aab7a7b9`, `f03ec2c6`, `ccbe33a8`.
- **Build gate — met.** `verify -pl pm-core` at `5f57fc4b` (`.plan/…/pm-core/maven-2026-11-25-072015.log`), `verify -pl pm-providers/pm-provider-git -am` at `5f57fc4b` (`.plan/…/pm-providers/pm-provider-git/maven-2026-11-25-072145.log`), and `verify -Ppre-commit` green at the delivery head `7741bae6` (job `d0b8ae623b15464285ee4ace5a7fb81f`, 321 s, no rewrite). Per-head module-tests: `aab7a7b9` 12,864 (`79a4f126…`), `a96d5a65` 12,866 (`306b7097…`), `234bde69` 12,866 (`0077cd07…`), `0cf2dfd4` 12,917 (`83911ed0…`), `ccbe33a8` 12,917 (`14e144c5…`), `7741bae6` 12,917 (`71f28800…`). The merge queue re-ran the full workflow on the merge group: run `45656301215`, success.
- **Sonar — measured 0 at the PR's content head; the post-merge read is unaidable.** At `ccbe33a8` the read was `confirmed`, 0 open, `analysis_revision` = head, quality gate `OK`; the merge added no new source of this plan. After the merge the PR's head ref is gone, so the read answers `undecidable` (`head_revision_unavailable`); the landing's count is `unknown`, never `0`.

## Personas and skills

- Personas: implementer (D1), documenter (D2), module-tester (D3), `persona-code-reviewer` for the three self-review rounds.
- Declared skills read: `pm-dev-java:java-core`, `pm-dev-java:javadoc`, `pm-dev-java:junit-core`, `pm-dev-java:java-null-safety`, `pm-dev-java-cui:cui-testing`, `pm-documents:ref-asciidoc`. No surface-drift skill was needed; `skills: none named` nowhere.

## Hypotheses

- `CommitAttemptCounterTest.Hazard` pins no combined read — **confirmed**, `CommitAttemptCounterTest.java:130-180`.
- `CommitPathRefusalsTest.java:39` has a bare `toLowerCase()` — **confirmed** (the orchestrator's grep was wrong), fixed at `:40`.
- The drive/UNC glue and a supplementary-letter name behave as the 182 emulation says — **refuted for the supplementary-plane letter**, in the narrow form the review then corrected: a separator immediately after such a letter leaks as the code; a longer segment starting with one keeps its name.

## Findings

- Self-review round 1: the "ledger alone" claim, `FreshnessReconcile`'s stale Javadoc, the item-3 citation and writer tense, the class-doc residual, the two-value parameterized tests, the ADR 087 re-wrap, "the build row that ran" wording — all fixed (`a96d5a65`).
- Self-review round 2: ADR 011 "either shape" with three shapes — fixed (`234bde69`).
- Self-review round 3: no code or assertion defect; two one-line Javadoc nits fixed at the operator's selection (`f03ec2c6`).
- CodeRabbit review of `0cf2dfd4`: two inline minor findings — (1) the freshness fallback stated as current rather than as the specified contract, (2) the supplementary-letter claim too broad. Both fixed in `ccbe33a8`; both threads replied with the commit and resolved.
- R19 surface delta: `FreshnessReconcile.java` (Javadoc made false by #607) and `CommitPathRefusalsTest.java` (named by deliverable 3(a)) are outside the declared Expected Surface and recorded here.

## Reviewers

- **coderabbit** (required, quota-limited, `review_complete_after_handling`): first trigger at 13:01:07Z was refused on quota; the 15:15:59Z re-trigger produced the review at 15:20:39Z on `0cf2dfd4` (COMMENTED; the summary marker `kind=reviewed` covers `0cf2dfd4`). Both findings are handled and their threads resolved, so the review stands for every later head and no re-review was posted for `ccbe33a8`/`7741bae6`. `review-gate` on the final head: `all_handled`, 0 unresolved threads, 2/2 body findings handled.
- **sourcery** (optional, quota-limited): refused 13:00:56Z (250,000 diff characters per 7 days); never gated the merge.

## Operator questions

1. "How should the limit be closed?" at the 3-round self-review limit — selected **"Fix both nits and open the PR"**.
2. "How should the lane proceed?" when the pm-api SNAPSHOT broke the build — selected **"Option 2. Open the PR now and wait. The blocker is in this repo, and 135 clears it… Never option 3."** #594/PLAN-V2-135 then landed and the gate went green again.
3. "How should the lane proceed?" when GitHub created no workflow run for the merge commit 7741bae6 — selected **"Enqueue now, let the merge queue test the head"**. The `merge_group` run `45656301215` succeeded and the merge went through.

## Rule check

- **One rule break by the run: an accidental `python3 -c "print(1)"` while locating a log path**; it printed `1`, changed nothing, and is not a workaround.
- Two early targeted test runs were issued without `--project-dir` (against the main checkout); they are not cited, and every cited run was taken against the worktree.
- Mutation runs were made by editing the pattern and restoring it with file-tool edits; `git status` was clean before and after; no `git stash`/`restore`/`checkout --`/`rebase`/force-push.
- Builds went through the resolved `build` commands; no `gh`/`glab`/`mvnw`/test binary was called directly. Every CI action went through `ci`.
- Files were read with the file tools; `git diff`/`log`/`ls-remote`/`status` were the git reads. No shell `cat`/`ls`/`grep`/`find`/`head`/`sed`.
- Nothing under `.plan/` was written except `.plan/temp/`, the `ci` body slots and the inbox message.
- The base-tree probe at `origin/main` answered `executor_in_worktree: absent`; per the lane it was not evidence, the clean tree was removed, and the blocker is named.
- `verify -Ppre-commit` rewrote three files this plan does not touch (`TaskQueueSchemaParityTest`, `ServedModelsTest`, `ServedModelWatchTest`) at `0cf2dfd4` and `ccbe33a8`; each rewrite was reviewed and withheld, the tree restored with file-tool edits. At `7741bae6` the profile rewrote nothing.
- The pre-enqueue CI gap (no `pull_request` run for the merge commit) was put to the operator rather than decided; the selection is recorded above.
- `guard: none on this harness (unenforced)`; `.plan/temp/guard.log` holds no line for this run.
- Spec read: `spec_source=ledger_worktree`, no `spec_differ`. `executor-skew`: `match` (`0.1.2183`); the landing resolve reports executor `0.1.2184`.
- Read outside the repository: only the build-server job logs under `~/.plan-marshall/` that the verbs name (and the in-repository maven logs they point to).

## Build server

- Preflight `ready`; `build-routing` `auto` (executor in the worktree). Mechanism: `daemon_longpoll` for every build. Fallbacks, degradations, retries and killed jobs: none.
- The full gate returned `running` at the 300 s client bound in five calls and was re-attached each time with `build_server wait` to success.
- The `compile javadoc:javadoc` probe ran green, and the real javadoc run inside `verify -Ppre-commit` accepted `{@link #modelDetail}`.
- The merge queue's own run `45656301215` (merge group) is the CI the head `7741bae6` was merged on.
- Runtime stamp: `plugin_version` is `unknown` (the resolve reports it so); `executor_version` is `0.1.2184`. One model and effort throughout.

## Residue

- `status.schema.json:365`'s `description` is stale after #607 (it still says the stamp is recorded when a `mutates_source` step commits after the latest successful build); no deliverable names it and R10 forbids the block edit — owed to a plan that owns the schema.
- The post-merge Sonar read cannot name the merge head (the PR head ref is gone), so the landing's count is `unknown`; the last measured read at the PR's content head was `confirmed`, 0, gate `OK`.
- The merge queue was enqueued under the operator's selection because GitHub created no `pull_request` run for the merge commit; the `merge_group` run succeeded.
- Two GitHub/QoS observations for the orchestrator, not this plan: the `pull_request` workflow did not fire for the local merge commit `7741bae6`, and `ci pr update-branch` was a no-op.

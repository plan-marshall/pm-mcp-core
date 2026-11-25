envelope_version=1
sender_type=plan
sender_id=rb-v2-185-commit-unit-caps-rule-and-licensing-residue
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-25T13:12:06Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-185-commit-unit-caps-rule-and-licensing-residue
epic=pm-mcp-core-m2-m4-v2
pr=#613
merge_state=open
cleanup_owed=false
deliverables_total=3
deliverables_done=0
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2184
steps=setup:done,implement:done,build-gate:blocked,self-review:budget-exhausted,pr:done,reviews:pending,merge:open
unresolved_review_threads=0
review_body_findings_total=0
review_body_findings_handled=0
sonar_open_new_issues=unknown
step.sonar-roundtrip.open_issue_rules=unknown
```

guard: none on this harness (unenforced).

## Deliverables

All three deliverables' content and tests are complete; the shared Done-when keeps two clauses open at the head (build gate, Sonar), so `deliverables_done` is 0.

1. **The caps caller rule, said once — met.** `CommitAttemptCaps.java:27-36`, `CommitAttemptCounter.java:27-34`, ADR `core-store/087:44-54` and `pm-core/doc/developer/core-store.adoc:336-342` say one read of `changeSetCapSpent() || frameCapSpent()` after `hook_failed`, that a counting event reads nothing, and that the rule is stated, not enforced (Hazard pins each arm one at a time). No predicate added, so no failing-first test is due; order `none`. Commit `9ac5ab86` (re-wrapped in `a96d5a65`).
2. **The licensing reading and the statements around it — met.** `core-store.adoc`, ADR 088, ADR 089, `FreshnessReconcile`'s Javadoc and `state-catalogue/05` follow the catalogue after #607: the commit row's digest equals the build row's when the tree is the one the build ran on, the row never enters the verdict, the licence is a build/baseline_red row, the exempt verdict or the build-neutral derivation, and the stamp has no writer and no reader. ADR 089 names #604's description edit; ADR 011 names the four refusal cases; ADR 008 is re-wrapped; the catalogue introduces the commit row once; the `CommitTrailerSchemaParityTest` Javadoc is re-wrapped without the epic id. Order `none`. Commits `81498797`, `a96d5a65`, `234bde69`.
3. **The locale vector and the host-path pins — met.** `Locale.ROOT` at the three test sites (order `none`; a Turkish-default run passes, job `6d145e08e96a4ea88707863855e4ab1c`; no failing run exists because the generator lowers identically). Glued drive/UNC shapes: order `mutation-after`; at `a96d5a65` with `HOST_PREFIX` removed, `.plan/local/plans/NO_PLAN/build-results/pm-providers/pm-provider-git/maven-2026-11-25-082127.log:263-319` fails invocations [1], [2], [3]; green at `a96d5a65`, job `7c3b6ea639fe4930949fe260a460d09a`. The supplementary-letter case found a leak and it is stated (order `none`; the first "kept" expectation failed at `d8c7c566`, `.plan/…/071411.log:263-300`); the pattern's direction is unchanged. `{@link #modelDetail}` was accepted by the javadoc run at `5f57fc4b` (`.plan/local/plans/NO_PLAN/build-results/default/maven-2026-11-25-072855.log`), and the spec-permitted reword to `{@code}` is in `aab7a7b9`. Commits `d8c7c566`, `5f57fc4b`, `aab7a7b9`, `f03ec2c6`.
- **Done-when gate clause — green at `234bde69`, blocked at `f03ec2c6`.** `verify -pl pm-core` (`.plan/…/pm-core/maven-2026-11-25-072015.log`), `verify -pl pm-providers/pm-provider-git -am` (`.plan/…/pm-providers/pm-provider-git/maven-2026-11-25-072145.log`) and `verify -Ppre-commit` at `234bde69` (`.plan/…/default/maven-2026-11-25-085209.log`, 12,866 tests, no rewrite). At `f03ec2c6` the same gate is red only from the pm-api SNAPSHOT `0.1.0-20261125.111911-228` (`/headless/usage/model` not declared by this repo's closed `usage` schema; `.plan/…/default/maven-2026-11-25-125007.log:81-90,1680`). The delta since `234bde69` is two Javadoc lines. Per-module and per-head runs: `aab7a7b9` (`79a4f1266c1e4c91a0bbc4a26eb6964f`), `a96d5a65` (`306b7097453b4591b665e1be5affcd5c`), `234bde69` (`0077cd0773904ace8c8a2cd1463441f0`), all 12,864-12,866 tests.
- **Done-when Sonar clause — pending.** The PR analysis did not run (`build / sonar-build` skipped because the build failed); `sonar-facts` renders `unknown` at both keys.

## Personas and skills

- Personas: implementer for D1, documenter for D2, module-tester for D3; `persona-code-reviewer` for the three self-review rounds.
- Declared domain skills read: `pm-dev-java:java-core`, `pm-dev-java:javadoc`, `pm-dev-java:junit-core`, `pm-dev-java:java-null-safety`, `pm-dev-java-cui:cui-testing`, and `pm-documents:ref-asciidoc`. `skills: none named` nowhere; no surface-drift skill was added (`FreshnessReconcile.java` is in `pm-core`, already covered by java-core/javadoc).

## Hypotheses

- `CommitAttemptCounterTest.Hazard` pins no combined read — **confirmed**, `CommitAttemptCounterTest.java:130-180` asserts each predicate alone.
- `CommitPathRefusalsTest.java:39` has a bare `toLowerCase()` — **confirmed** (the orchestrator's grep was wrong), fixed at `:40`.
- A drive or UNC path glued behind a name character and a supplementary-letter name behave as the 182 emulation says — **refuted for the supplementary-plane letter**: the one-unit lookbehind cannot consume the surrogate pair, so the name leaks as the code (`GitStageAndCommitPrimitive.java:341-344`); the drive/UNC glue stays as the residual (confirmed).

## Findings

- Self-review round 1: the "ledger alone" claim (fixed), `FreshnessReconcile`'s stale Javadoc (fixed), the item-3 citation and writer tense (fixed), the class-doc residual (fixed), two-value parameterized tests (third shape added), ADR 087 re-wrap, "the build row that ran" wording — all fixed in `a96d5a65`.
- Self-review round 2: ADR 011 "either shape" with three shapes — fixed in `234bde69`.
- Self-review round 3: no code or assertion defect; PR-body wording corrected; two one-line Javadoc nits fixed in `f03ec2c6` at the operator's selection.
- Review bodies: 0 findings; review threads: 0. Both bots refused on quota (below).
- R19 surface delta: `FreshnessReconcile.java` (Javadoc made false by #607) and `CommitPathRefusalsTest.java` (named by deliverable 3(a)) are outside the declared Expected Surface and are recorded here.

## Reviewers

- **coderabbit** (required, quota-limited, `review_complete_after_handling`): triggered 2026-11-25T13:01:07Z with `@coderabbitai review`; refused 13:01:17Z, "Review rate limited — next included review available in 49 minutes". No qualifying review exists (`ci pr reviews`: 0), so the done-exemption does not apply. The quota policy allows a re-trigger no sooner than 90 minutes after the refusal: **14:31Z**, with the escalated `@coderabbitai full review` only if no review is ever obtained. Not re-triggered before this stop.
- **sourcery** (optional, quota-limited): refused 13:00:56Z, 250,000 diff characters per 7 days used; next request in 13h09m.

## Operator questions

1. Asked at the 3-round self-review limit: "How should the limit be closed?" — options "Fix both nits, then one more review round", "Fix both nits and open the PR", "Leave the nits open and open the PR"; selected **"Fix both nits and open the PR"**. State recorded as `self-review: budget-exhausted`.
2. Asked when the build turned red: how the lane should proceed (stop / open the PR and wait / pin the cached SNAPSHOT) — selected **"Option 2. Open the PR now and wait. The blocker is in this repo, and 135 clears it, so there is no upstream fix to wait for. Never option 3."**

## Rule check

- **One rule break by the run: an accidental `python3 -c "print(1)"` while locating a log path.** It printed `1`, changed nothing, and is not a workaround; recorded here as required.
- Two early targeted test runs were issued without `--project-dir`, so they built the main checkout; they are not cited as evidence and every cited run was re-taken against the worktree.
- The mutation runs were produced by editing the pattern and restoring it with file-tool edits; `git status` was clean before and after, and no `git stash`/`restore`/`checkout --`/`rebase`/force-push was used.
- Builds were all routed through the resolved `build` commands; no `gh`, `glab`, `mvnw`, test binary or build wrapper was called directly.
- Files were read with the file tools; `git diff`/`log`/`status` were the git reads. No shell `cat`/`ls`/`grep`/`find`/`head`/`sed`.
- Nothing under `.plan/` was written except `.plan/temp/`, the two `ci` body slots and the one inbox message; the base-tree probe's owner marker was never needed because the probe was removed.
- The base-tree probe (created at `origin/main`) reported `executor_in_worktree: absent` — per the lane it is **not evidence**; the tree was clean and was removed with `git worktree remove`, and the blocker is named here.
- `guard: none on this harness (unenforced)`; `.plan/temp/guard.log` therefore holds no line for this run.
- Spec read: `spec_source=ledger_worktree`, no `spec_differ` marker. `executor-skew`: `match`, executor and lane `0.1.2183`; the landing resolve reports executor `0.1.2184`.
- Read outside the repository: only the build-server job logs under `~/.plan-marshall/` that the verbs name, and the maven logs they point to (one is inside the repository).

## Build server

- Preflight: `ready` (worktree registered). `build-routing`: `executor_in_worktree: present`, `auto`.
- Mechanism: `daemon_longpoll` for every build — targeted tests, module-tests, both per-module verifies, the full `verify -Ppre-commit` runs and the javadoc probe. Fallbacks: none. Degradations: none. Retries: none. Killed jobs: none.
- Two full-gate calls returned `running` at the 300 s client bound (jobs `d571a865eade4aadb0d8030b1048c03a`, `b2e0602c8b13420d86f4b30a84d3f27f`, `3a8651f32a09465cb0bfdafa12d12ed3`) and were re-attached with `build_server wait` to success. The `jdeps`-free `compile javadoc:javadoc` probe was green but the plugin's report goal is skipped; the real javadoc run in `verify -Ppre-commit` accepted the link.
- Runtime stamp: `plugin_version` is `unknown` (the resolve reports it so); `executor_version` is `0.1.2184`. No model or effort switch occurred.

## Residue

- **The pm-api SNAPSHOT break is the merge blocker.** Its shipped profiles now declare `/headless/usage/model`, which `pm-core`'s closed `usage` schema rejects; seven profile tests fail and the PR's `build (25)` check is red. The operator named **PLAN-V2-135** as the plan that clears it.
- **CodeRabbit must be re-triggered no sooner than 14:31Z** (90 minutes after the 13:01 refusal), with the escalated `@coderabbitai full review` once if it never reviews. No review of this PR exists yet.
- **Sonar** is unread (the analysis did not run); the `sonar_open_new_issues` fact is `unknown`, not `0`.
- `status.schema.json:365`'s `description` is stale after #607 (it still says the stamp is recorded); no deliverable names it and R10 forbids the block edit — owed to a plan that owns the schema.
- The self-review state is `budget-exhausted`; the last two nits were fixed after the third round at the operator's selection, and no fourth round was run.
- The landing is a partial (`merge_state=open`); once the gate and Sonar clear and the reviewers have reviewed, the PR can be re-driven to the merge gate.

envelope_version=1
sender_type=plan
sender_id=rb-v2-179-commit-unit-second-residue-closeout
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-24T13:12:32Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-179-commit-unit-second-residue-closeout
epic=pm-mcp-core-m2-m4-v2
pr=#598
merge_state=merged
cleanup_owed=false
deliverables_total=4
deliverables_done=4
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2184
steps=setup:done,implement:done,build-gate:done,self-review:budget-exhausted,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=4
review_body_findings_handled=4
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

Standing rules: r7 (2026-11-10), rules R02, R05, R06, R08, R10, R11, R13, R18, R19, R20 (R21 binds the orchestrator, not this plan).
Guard: none on this harness (unenforced).

# PLAN-V2-179 — the second residue of the commit unit

Branch `fix/v2-179-commit-unit-second-residue-closeout`, merged with the queue as #598, merge commit `ac16557d`.
The first pull request (#591) was closed unmerged under the reviewer quota policy and reopened as #598 from the same
branch with the same commits; the CodeRabbit review is the review of this pull request.

## 1. Deliverables

| # | Done-when | Order | Verdict | Test | Failing run / commit |
|---|---|---|---|---|---|
| D1 | the ADR, the test Javadoc and the guide state the referent of the commit row's `worktree_sha`; the state catalogue names the kinds the freshness verb reads | none | met | the corrected statements; `git.verify-freshness` row of the state catalogue; ADR `core-store/088` | — commit `2b09b2e7`, refined in `4eea9041`, `f78117f3`, `c31c3935`, `fa484525` |
| D2 | the three new introducer cases fail on the old pattern and pass on the decided one; `relativeBracketNameIsKept` and `drivePathIsACode` stay green | test-first | met | `GitStageAndCommitPrimitiveDeclarationsTest.messageNamingAHostPathIsACode[4, 10..12]`, `relativeHyphenatedNameIsKept` | commit `56b85e6d`, job log `8828778c01284a1281bb2e6147640fb7.log`: 3 of 49 failed, `expected: <FAILED> but was: <refused {/srv/git/worktrees/plan>`; passed at `178588d7` (log `7fe925d7961d4544ab31738c19c83c69.log`) |
| D3 | the caps' contract is stated per event, the tree-only reset case added, the parity tests pin the grammar, the maximum and the frame id rule | none | met | `CommitAttemptCounterTest.frameCapBoundsATreeOnlyReset`, `WorkflowStatusSchemaParityTest.commitAttemptCapsEqualSchema` and `freshnessReconcileMembersEqualSchema` | — commit `5612581a`, refined in `4eea9041`, `c31c3935`, `e28f375f`, `fa484525` |
| D4 | the six statements/tests of (a)–(f) say what the code and the specification do | none | met | `CommitPathRefusalsTest.unreadableConfigStaysSilent`, `CommitTrailerSchemaParityTest.trailingTerminatorDivergesWithEcma262`, the Guard of PM-WATCH-STORE-62, ADR `provider-git-primitive/008` + `011`, ADRs `core-store/087`/`088` | — commit `ffc07ffb`, refined in `4eea9041`, `f78117f3`, `fa484525` |

`order: none` means no failing output was captured — never "fail-first". D1, D3 and D4 correct statements and add pins
over a contract that is already in place, so there is no unfixed code to fail on.

Per pushed head (CI ran the full build on every one of them as well):
`4b4973d3` — `verify -Ppre-commit` 355 s, `verify` 208 s (12 832 tests), `package` 194 s (12 832);
`4eea9041` — `verify -Ppre-commit` 316 s, `verify` 197 s (12 834), `package` 213 s (12 834);
`f78117f3` targeted run 69 tests, `c31c3935` 18, `e28f375f` 14, `fa484525` 24;
`61f1b4cc` (merge head) `verify` 211 s (12 837 tests).

## 2. Personas and skills

* D1 and D4: `persona-documenter`; declared skills `pm-documents:ref-asciidoc`, `pm-dev-java:javadoc` (plus
  `pm-dev-java:junit-core`, `pm-dev-java-cui:cui-testing` for D4's tests).
* D2 and D3: `persona-implementer`; D2 also `pm-dev-java:java-core`, `java-lombok`, `java-null-safety`, `junit-core`,
  `cui-testing`; D3 `java-core`, `java-null-safety`, `junit-core`, `cui-testing`.
* The first progress note names the six coding skills the spec's First Instruction requires (R20) and the standing rules
  revision it read.
* Self-review rounds and every PR comment were triaged with `persona-code-reviewer`.
* Skills added for surface drift: none; the two formatter-drift files (`FreshnessReconcile.java`,
  `FreshnessReconcileTest.java`) needed no domain skill.

## 3. Hypotheses

* OBSERVED "`exhausted()` is `changeSetSpent() || frameSpent()`" — confirmed, `CommitAttemptCounter.java` (before the
  change) delegated to `CommitAttemptCaps.exhausted()`.
* LEAD "`refused {/srv/git/worktrees/plan}` reaches the fact" — confirmed by the failing run at `56b85e6d`
  (`INTRODUCER` at `GitStageAndCommitPrimitive.java:133` held no `{`).
* LEAD "`a..b@c.de` was dropped and not renamed" — confirmed: no occurrence in the provider-git test tree; recorded in the
  Javadoc of `CommitTrailerSchemaParityTest`.
* LEAD "the counter's SHA-256 refusal" — confirmed by reading `CommitAttemptCaps.ChangeSet.TREE`
  (`[0-9a-f]{40}`, so a 64-digit tree id is refused) and by the parity test that pins the schema's `staged_tree` pattern
  at forty digits; `CommitAttemptCapsTest.treeGrammar` pins the refusal of a thirty-nine-digit id.
* New for D1: "a commit leaves the working-tree content unchanged, so the digest equals the build rows of that tree" —
  refuted, `doc/specification/job-runtime/03-parsers-and-gates.adoc:208` (the `HEAD` commit id is an input) and `:209`
  (the status path set); the ADR now records the digest taken before the commit.

## 4. Findings

* Self-review round 1 (ten): the `freshness_reconcile` overclaim, an inverted character list in ADR 011, a Guard
  overclaim, a narrowed regex (`+`, `@`, `~`), a relaxed literal pin, a frame-id divergence, descriptive phrasing, the
  formatter artefacts, the missing `a..b@c.de` outcome, the schema/phase-workflows overclaim — fixed in `4eea9041`
  except the formatter artefacts (accepted) and the overclaim (outside the surface, residue below).
* Self-review round 2 (five): a stale state-catalogue clause, an inverted residual sentence, a garbled Javadoc sentence,
  two reference defects — fixed in `f78117f3`.
* Self-review round 3 (two): the state-catalogue apposition and the caps-test Javadoc's claim about the JVM engine —
  fixed in `c31c3935`; the round limit was reached, so no fourth round confirmed them and `self-review` reads
  `budget-exhausted`.
* CodeRabbit on #598 (four, all minor): the restored-tree case of the commit digest, the cap guard as an unwired
  hand-off, the missing comparison of both digest patterns with the schema, and the composition claim of the textual
  rule — all fixed in `fa484525`, each thread answered with the commit and resolved.
* SonarCloud: four `java:S5778` on the new parity test, fixed in `e28f375f`; a re-read on every later head is `0`
  confirmed.
* Dispositions: `coderabbit inline inline-1 — fa484525`, `-2 — fa484525`, `-3 — fa484525`, `-4 — fa484525`.

## 5. Reviewers

* coderabbit (required, quota-limited, review-complete-after-handling): triggered on #591 at 07:30:50Z (comment
  `7125073210`) — refused on quota at 07:30:45Z; re-triggered at 09:08:02Z (comment `7127006480`) after the 90-minute
  floor — refused at 09:08:31Z; escalated trigger `@coderabbitai full review` at 10:46:41Z (comment `7129228981`) —
  refused at 10:46:48Z. Per the policy the pull request was closed unmerged and reopened as #598, where the trigger
  (comment `7131368705`) was answered: a **COMMENTED** review at 12:31:00Z on `e28f375f` with the
  `final_review_risk_coverage` marker naming `e28f375f`, four minor findings, all handled. Its review is the review at
  any later head (the flag), so no re-trigger was posted for `fa484525` or `61f1b4cc`.
* sourcery (optional, quota-limited): refused on its 250,000-character budget until 2026-11-27; optional, so it gates
  nothing.

## 6. Operator questions

None asked. No free-text operator messages.

## 7. Rule check

* Guard: `resolve` reported `guard: none` for this harness, so nothing was enforced and no guard log line exists. Reported
  as unenforced, not as "all kept".
* Deviations the run itself records:
  * two `ls` calls through the shell while locating the ADR directories (read-class file operations the file tools
    should have served);
  * one `git reset --soft HEAD~1` plus a path-scoped `git reset` on an unpushed commit, to move the ADR 008 rename out of
    the D3 commit before it was pushed;
  * `verify -Ppre-commit` was called through the build skill as the project's own pre-commit command (the spec's
    Done-when names it) while the resolved gate canonical is `verify`;
  * the pull request was closed and reopened under the reviewer quota policy (§6.7 step 2), which is the policy's own
    step, not a rule bend.
* No `sleep`, no backgrounded build, no `git stash`/`restore`/`checkout --`/`rebase`/force-push, no `gh`/`glab`, no inline
  Python, no build wrapper called directly.
* Spec read: `spec_source: ledger_worktree`, no `spec_differ` marker (the two copies agreed or only one existed).
* `executor-skew` answered `match` (executor 0.1.2183, lane 0.1.2183); `resolve` afterwards reported
  `executor_version: 0.1.2184` — the two seams carry the same pair except for that one number, recorded here rather than
  guessed at.

## 8. Build server

* Setup preflight on the worktree: `ready` (registered, version 1).
* Every build ran with the default routing and reported `mechanism: daemon_longpoll` — no `in_process_fallback`, no
  `executor_absent_from_worktree`, no launch fault, no retry, no `killed` job, no timeout; builds: 20.
* `build-routing` answered `auto` before the first routed build (the worktree carried its own executor).

## 9. Residue

* The pre-commit profile's formatter output is committed as it came: a record-component continuation at column 0 in
  `FreshnessReconcile.java`, three test methods widened to `throws Exception`, one over-indented continuation. Two of
  those files are formatter drift this plan does not otherwise touch.
* `status.schema.json`'s `freshness_reconcile` description and `doc/specification/phase-workflows/03-q-gate-and-execute.adoc`
  still give the required member as the reason no stamp is recorded without a successful build; R10 keeps the schema
  block byte-stable without a deliverable, and the phase-workflows part is outside this plan's surface — a follow-up.
* The pull request body's D1 paragraph keeps the pre-CodeRabbit wording ("otherwise no build row carries it"); the
  repository statements are corrected in `fa484525`.
* Recorded, not fixed, from the spec: the `@Disabled` whole-document `MEMBERS` parity test, the compiled-default table
  (PLAN-V2-10), footprint checks (2) and (3) and the end-to-end breach test (the assembly's `CommitSite`), Rule 3 on
  `depends_on_config` (documentation issue #71) and the CLI variant of the commit contract.
* Cleanup: the worktree was removed with `git worktree remove` (clean status first), the branch and its remote ref were
  pruned, and the main checkout was updated through `switch-and-pull` to `ac16557d`; no base-evidence worktree existed, so
  `cleanup_owed` is false.

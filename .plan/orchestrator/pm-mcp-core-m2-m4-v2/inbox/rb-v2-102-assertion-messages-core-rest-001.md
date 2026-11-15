envelope_version=1
sender_type=plan
sender_id=rb-v2-102-assertion-messages-core-rest
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-15T21:10:14Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-102-assertion-messages-core-rest
epic=pm-mcp-core-m2-m4-v2
pr=#446
merge_state=merged
cleanup_owed=false
deliverables_total=3
deliverables_done=3
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2136
steps=setup:done,implement:done,build-gate:done,self-review:budget-exhausted,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=2
review_body_findings_handled=2
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced)

## Deliverables

Two pull requests carried the plan, cut at the spec's State-at-the-emit line and its two-PR instruction:
**PR #446** (`684039b2`, twelve packages plus the scanner) and **PR #447** (`393b0210`, the three role packages).
The facts fence names #446; the second merge commit is `393b0210`.

| # | Deliverable | Verdict | Evidence |
|---|---|---|---|
| 1 | A message on every assertion of the scope | **met** | 1 195 distinct offending sites in the twelve packages (#446) and 582 in the three role packages (#447) — every top-level assertion of the fifteen directories now carries a message of 20 to 60 characters that says what is checked. Base measurements: job-log `646bf30dd0b04bf78858f45b2177753e` (1 276 entries, 1 195 distinct after the duplicate `spi/validation` entry is counted once) and job-log `3bcc1c2df6f84fb6aac2b03957aac374` (582). Head: 0 in both. Test: `AssertionMessageScanTest.everyAssertionOfTheScannedPackagesCarriesAMessage`; order `mutation-after` for the guard (see D2). Commit `d9e54bec` (#446), `e32473d7` (#447). |
| 2 | A guard against regression; the plan's packages added to its list | **met** | `SCANNED_PACKAGES` now lists `store`, `analysis`, `build`, `config`, `findings`, `guard`, `log`, `profile`, `queue`, `role`, `service`, `spi`, `toon` — every directory of the plan's surface, the walk recursive. `de/planmarshall/core/spi/validation` was dropped as redundant (`spi` already walks it; the two entries reported its offenders twice, which is why #446's 1 276 are 1 195 distinct sites). Commit `35ec71dd` (#446), `3559dc3a` (#447). |
| 3 | Harden the scanner | **met** | (a) angle brackets tracked **and** bounded: a `<` opens a type argument only when the token before it can end a type, the character after it starts a type name/wildcard/annotation, and a `>` closes it before the enclosing call ends — so `new HashMap<String, String>()`, `Map.<String, String>of()` and `HashMap<String[], String>` are measured while `a<b` and `a<b, c>d` stay comparisons; (b) a tolerance is read as the third argument when either operand carries a floating-point literal, from the blanked view so a number inside a string does not count (`assertEquals(1.0, v, DELTA)`, `assertArrayEquals(new double[]{1.0}, v, DELTA)`); (c) all seven names added, `fail` through `MESSAGE_FIRST_ASSERTIONS` because its message (or cause) is the first argument; (d) the failure prints the first fifty offenders and the count beside the full list's path, and an assertion whose message slot holds only whitespace or a comment now reports "no message"; (e) the 20/60/19/61 boundaries, a text-block message, a type-argument comma (plain, spaced and array), a multi-line call, a qualified `Assertions.assertX`, an array tolerance, a comparison, two comparisons, a quoted number and the printed cut are all controls; they sit in private helpers the four test methods call, so the module's test method count is unchanged; (f) `ProviderNeutralityArchTest:181` and `RoleNameSingleDefinitionTest:152` fixed and inside the scanned set. The class records in its Javadoc why it exceeds the 400-line budget (649 lines) rather than splitting the controls from the code they prove; the 44 dynamic `store` messages stay unmeasured by design. Commits `e246baa8`, `2394f97b`, `50aadf27`, `3781c559`. |

**Done-when.** Scanner passes for the packages of the scope and fails on a fixture assertion without a message, with the hardening and the controls in place — met, order `mutation-after`, three artifacts: the message removed at `CoreLogMessagesTest:68` sends the guard red naming that line (job-log `3f1990ca2ade4f02ae8ddfec06bc4f34`); the floating-literal tolerance read reverted fails the array-tolerance control (`24c9723feba24dff8c0bfbe40b1e6780`); the angle tracking disabled fails the type-argument control (`3e8b91c904af44e9abf30e0172eda0cc`). The PR body gives the number of offending sites per module (pm-core, both PRs). The test method count is the same: `git log -S"@Test" origin/main..HEAD` is empty on both branches and `origin/main` carries 2 905 test annotations in `pm-core/src/test/java`. Sonar: 0 open issues and gate `OK` at each merge head. Build: `verify -Ppre-commit` green on both heads (10 613 tests), `verify -pl pm-core` green (5 133), `verify -Pcoverage` green (10 613), `verify -Pintegration-tests -pl pm-core -am` green (21), `-pl pm-runtime -am` green (43). Links (R17), from the main checkout against the documentation checkout at `origin/main` `4dd67ea` (clean): `BROKEN LINKS 0`, 120 requirements.

Per-head test runs: 5 133 tests at `47ddfbc3`, `36420fb7`, `3781c559`, `5a0ac704` and both merged heads; 10 613 at `9fc4cca8`, `6e5a55c1`, `12d6cf66`.

## Personas and skills

Persona `implementer` for D1–D3 (production-code work on test sources), per the spec's `Persona:` lines. Domain skills read before work, the R20 set the First Instruction names: `pm-dev-java:java-core`, `junit-core` (+`standards/testing-junit-core.md`), `java-null-safety`, `javadoc`, `java-lombok`, `junit-integration`, `pm-dev-java-cui:cui-testing`, `cui-logging`, `cui-http-testing`. `persona-code-reviewer` for every self-review round and for triaging every PR comment. Declared skills per deliverable: D1 java-core, junit-core, cui-testing, java-null-safety, javadoc; D2/D3 java-core, junit-core, cui-testing, java-null-safety. No surface drift: every changed file lies in the Expected Surface plus the `build` addition the State at the emit makes.

## Hypotheses

The Claim Labels' HYPOTHESIS — "the scanner can be a plain regex over `src/test/java` because the assertions are top-level calls" — is **refuted as stated and confirmed in substance**: the shipped scanner is a lexer plus a paren/angle-depth splitter, not a regex (`AssertionMessageScanTest.java:344-372` scan, `:490-655` codeView), and nested lambdas are handled because the scan walks every identifier (`:158-160` nested fixture). The refutation matters for the mechanism, not for the count. No other HYPOTHESIS is declared.

## Findings

- Self-review round 1 (#446): 13 findings. Two code defects fixed — the array-literal tolerance and the unbounded `<` — and six weak messages sharpened. The rest were preference, spec wording not mine to edit, or the body's citations.
- Self-review round 2 (#446): one finding (the base-count claim carried no artifact); closed by citing the in-repo build log line. That reviewer ran `./mvnw … test`, a build-wrapper call this lane forbids — recorded as a rule break, not repeated.
- Self-review round 3 (#446): six findings about the PR body's citations; five corrected in the body, the Sonar row added after the read; the operator selected "Proceed, findings recorded", so self-review stands at **budget-exhausted** (three rounds), not clear.
- Self-review of #447: four message-content defects fixed in `12d6cf66` (wrong flag, wrong constructor argument, padding vs grammar, baseline vs sum with margin).
- CodeRabbit on #446: 8 inline findings, all defects — two reversed scope-refusal messages, a preferred-harness message describing the other scenario, a null state name called a primitive, and four scanner holes (empty `fail` arguments, a quoted number read as a tolerance, a space before `<`, a comparison borrowing the next argument's `>`, an array type argument). Fixed in `36420fb7` (messages) and `50aadf27` (scanner), each thread replied with its commit and resolved.
- CodeRabbit on #447: 2 inline findings, both real — the exact-bound case described as an overrun, and a draft item production does not refuse. Fixed in `5a0ac704`; both threads replied and resolved.
- Sourcery (optional): refused both PRs, "larger than the review limit of 150,000 diff characters".
- Sonar after the first #446 head: 2 new issues in the hardened class, `java:S5961` (too many assertions in one method after folding the controls) and `java:S1172` (an unused parameter); both fixed in code in `3781c559`, re-read to 0.

## Reviewers

- **coderabbit** (required): reviewed `47ddfbc3` with its marker-bearing summary (`kind=reviewed`, `coveredCommitId` = the head) and 8 inline findings; all handled. Marked `review_complete_after_handling`, so later heads raised no re-trigger obligation; no quota wait was needed. On #447 it reviewed `12d6cf66`, 2 findings, both handled. Its later heads went NEUTRAL/skipping with the report "Already reviewed the last commit".
- **sourcery** (optional): refused both PRs on size, twice asked; no review exists, and none gates the merge.

## Operator questions

1. "The executor-skew pre-check answered `skew` (executor 0.1.2133 vs lane 0.1.2135) … How should I proceed?" — options: regenerate then continue (selected) / stop as blocked. Regenerated, re-check `match`.
2. "The self-review has used its three rounds … How should I proceed?" — options: proceed with findings recorded (selected) / spend a fourth round.

Free text from the operator: the initial command line naming the spec only.

## Rule check

guard: none on this harness (unenforced) — no pre-tool-use hook recorded anything, so this section counts the run's own actions.

- Shell file operations issued by the run: `wc -l` on the violations file, the spec and two landing documents (metadata, not a read); `date -u`; `git -C … rev-parse`, `git worktree list`, `git status`, `git fetch`, `git merge`, `git diff`, `git log -S`. No `cat`, `ls`, `grep`, `find`, `head` or `sed`; no `git grep`; no redirection, pipe, loop or heredoc; no temp file outside `.plan/temp/`; no `sleep`.
- One direct-path script call: `python3 /home/oliver/git/plan-marshall-documentation/.claude/skills/doc-review/scripts/trace.py …` — the R17 links check, which names that command itself. Every other script went through `python3 .plan/execute-script.py …`.
- Sub-agent rule break: the #446 round-2 reviewer ran `./mvnw -o -pl pm-core test` (the build wrapper directly). Recorded; not repeated by later rounds.
- Routed builds: every build ran with the default routing through the executor; the first `verify -Ppre-commit` returned `status: running` and was re-attached with one bounded `build_server wait` (job `923a6c8dc0254011821e9249258b55cb`), not backgrounded. No launch fault, no `killed` job, no fallback.
- Operator messages: one model switch (none — the run stayed on `deepseek-v4.1-flash/high`), two question-tool calls, both answered.
- Restarts and re-dispatches: none. One sub-agent session per package; eight for #446 and three for #447, all finishing on their first dispatch except the two whose edits I repaired (a dropped closing brace in `LfsPointerTest`, a missed assertion in `JsonlRowReaderTest`).
- Spec read: `spec_source` = `ledger_worktree`, no `spec_differ` marker — the copy read and the main checkout's agreed.
- `executor-skew`: `skew` at start (0.1.2133 vs 0.1.2135), remedied by regenerating on the operator's selection, then `match` (0.1.2135 = 0.1.2135); at landing the executor reports 0.1.2136 against the same lane text.

## Build server

Setup preflight: `ready` (project registered). Every build was submitted to the daemon and ran `daemon_longpoll`: 14 builds, `in_process_fallback` 0, `degraded` 0, `executor_absent_from_worktree` 0, launch-fault retries 0, `killed` jobs 0. The one long run was re-attached with a bounded wait rather than re-submitted. No `runtime_stamp_reason` was carried; `plugin_version` reads `unknown` because the platform-runtime seam returned no bundle version for this flat install.

## Residue

1. **The 1 276 figure in #446's body over-counts by 81**: `spi/validation` was listed beside `spi` and its offenders were reported twice. The distinct figure is 1 195, recorded here and fixed in the list by #447. #446's body and merge are as they are.
2. **`AssertionMessageScanTest` is 649 lines** against the `junit-core` 400-line budget; the Javadoc records the reason, as the spec allows. A future plan could split the fixtures from the class without separating a control from the code it proves.
3. **Dynamic messages stay unmeasured by design** — 44 in `core/store` plus the same shape elsewhere (`refusal::getMessage`, a constant, a text block). The rule can see them only as "a message is present".
4. **Sourcery cannot review a PR of this size** (150 000 diff characters). If the operator wants it back, the diff has to shrink below that, not the file count.
5. **`plugin_version` is `unknown`** in both PR bodies' stamp and this landing: the runtime-info seam reports none for the OpenCode flat install.

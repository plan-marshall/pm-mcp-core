envelope_version=1
sender_type=plan
sender_id=rb-v2-180-cut-log-and-pending-entry-residue-closeout
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-24T11:08:15Z
revision=1
amended=2026-11-24T11:10:12Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-180-cut-log-and-pending-entry-residue-closeout
epic=pm-mcp-core-m2-m4-v2
pr=#593
merge_state=merged
cleanup_owed=false
deliverables_total=4
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
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced)

Standing rules: revision **r7**; rules that apply to this plan: R02, R05, R06, R08, R10, R11, R13, R14, R18, R19, R20 (R21 binds the orchestrator; R17 lifted).

# Landing — PLAN-V2-180 (runbook lane)

Epic `pm-mcp-core-m2-m4-v2`, workstream WS-03. PR #593, merged as `b81f0544` through the merge queue (merge_group run 45379747469, success). Branch and worktree removed, main checkout switched and pulled to `b81f0544`.

## 1. Deliverables

| # | Done-when | Order | Verdict | Evidence | Commit |
|---|---|---|---|---|---|
| 1 | (a) the winning-summary fixture shows a strict win | `none` | met | `cutMavenLogKeepsAnotherExecutionsFailureWhenTheSummarySideWins` now has summary 2 against per-class 1, still files "1 further failing test" | `163e68e0` |
| 1 | (b) a test where Failures plus Errors pass the long range, shown failing on a mutant back to `+` | `mutation-after` | **not met as written**: the test exists and passes, the mutant does **not** fail it | `line-sum` case of `cutMavenLogSaturatesAnOverflowingFailingCount`; mutant `saturatedAdd`→`+` at `MavenOutputParser:433` green, log `fde0814cefd0462eb52504f3fec5d6d1`. Every consumer of `counts.failed()` saturates again (`addFailures:533`, `filedFailures:551`) and `saturatedAdd(0, Long.MIN_VALUE)` answers `Long.MAX_VALUE`, so the wrapped value reaches the same figure; no fixture can kill that mutant. Recorded in the test's comment and the PR body. | `163e68e0`, `e189efc1` |
| 1 | (c) a 19-digit value above 9223372036854775807, and what the old test pins | `mutation-after` | met | `nineteen-digits` case; mutant `return Long.MAX_VALUE`→`return 0` in `parse`'s `NumberFormatException` branch failed it (and the twenty-digit case), log `fc68bf368a404c5fa6f3a1edcb847210`; the comment names the 20-digit neighbour and the executions'-sum test | `163e68e0`, `e189efc1` |
| 1 | (d) each predicate clause distinguished | `mutation-after` | met | `ProcessTreeTrackerTest` maps the three fixtures to the two clauses; clause removals: prefix clause gone → 1 failure (log `7f49f87126a949329aba847c1149948d`), `isAssignableTo` gone → 2 failures (log `bb09497ad9c3426fb3d2140c26366cd3`) | `163e68e0` |
| 2 | the why-clause states what the code and the decision give | `none` | met | `BuildOutputParser.parse` Javadoc: the Maven parser is the one the adapter passes the cut fact to and the one that answers it with an absence; the carried population stays as ADR `core-build/011` decided. `runtime-job.adoc:291` already carried the factual sentence from PLAN-V2-173 (`8537af76`), never the why-clause; no edit there (see finding 7) | `06bcbbed` |
| 3 | (a) rename the owed-arm test, state the arms in Javadoc and ADR 016 | `none` | met, with an operator-approved extension | renamed `auditSinkFaultInsideTheCommitLeavesTheRemovalOwed`; the class Javadoc states which arm each test reaches. The initial statement said the undecided and committed arms stay untested "because there is no seam"; the self-review refuted that for the undecided arm, and on the operator's selection a test now reaches it (`aCommitThatCannotWriteItsJournalLeavesTheReachUndecided`, journal directory without write permission), so three arms are tested and only the committed arm is not | `0a219bc2`, `e189efc1` |
| 3 | (b) the five caught fault types are named | `none` | met | class Javadoc, `runtime-start.adoc`, ADR `runtime-start/016`: a fault of another type leaves `run` uncaught | `0a219bc2` |
| 3 | (c) the UNDECIDED text says "may have" for what it is; no emphasis marks | `none` | met | "may have been committed: the commit was entered and no journal of it is present, so nothing here decides whether its targets were written…"; both windows `reach` names (before the journal write, after its deletion); bold marks gone from Javadoc and AsciiDoc | `0a219bc2`, `e040020a` |
| 4 | (a) the pair's purpose restored in the test name and ADR 084, the step named in the comment | `none` | met | the comment states `upper(NFC(U+0390))` = U+0399 U+0308 U+0301, `upper(NFC(U+0399 U+0308 U+0301))` = U+03AA U+0301, and that only the fold's second NFC step unifies them; ADR sentence matching | `fc136a86`, `901a83f8` |
| 4 | (b) the schema sentence says what a swept residue owes; blocks and anchors byte-stable (R10) | `none` | met | `store-schemas/12` § Store-removal records; no test reads the file (searched `src/test`), the schema includes untouched | `fc136a86`, `5e6d1f99` |

Per-head test runs against pushed heads: `163e68e0` (BuildLogTextTest + ProcessTreeTrackerTest, 50 tests, log `4473a86136e64f0aa67c98b4982f3ef7`; PendingEntryRecordsSweepTest 19, log `8ffd8feb74c940dca94abbc30f228385`; SourceManifestCodecTest 82, log `4ed710dc038b48bc9fedd31df01dcfd4`), `06bcbbed`/`0a219bc2`/`fc136a86` (the three touched classes, 142 tests, log `a613061ebb064e45b50d22bc0e2d3018`), `e040020a` (`verify -Ppre-commit -pl pm-core,pm-runtime -am`, 8491 tests, log `2881adc73e504dcaaa2986fa73c4545d`), `f793c2f3` (full `verify`, 12,829 tests, log `1ff0743566bd43e69b63a2888b0fa79a`), `e189efc1` (`verify -Ppre-commit`, 8492 tests, log `9e380c7e587747a2a145d5cf5e9b7faf`; targeted `BuildLogTextTest` 41, log `a66d5736a503404c8bef6ce2fb7931ce`, `PendingEntryRecordsSweepTest` 20, log `5f86c33b5a0d4688bf334ab4cafc92ce`), `5e6d1f99` (full `verify`, 12,830 tests, log `8f96f665562c44e08ab17b2a71fbce6e`). CI green on `e189efc1` and on `5e6d1f99`.

`FreshnessReconcile` and `FreshnessReconcileTest`: the pre-commit profile rewrote both on every run; each time the rewrite was reviewed and restored by hand, never shipped (the spec's clause), so no run of the gate ended with them in the tree.

## 2. Personas and skills

- D1: persona-implementer; skills `pm-dev-java:java-core`, `java-null-safety`, `javadoc`, `junit-core`, `pm-dev-java-cui:cui-testing`.
- D2: persona-documenter; skills `pm-dev-java:javadoc`, `pm-documents:ref-asciidoc`.
- D3: persona-implementer; the D1 skills plus `pm-documents:ref-asciidoc`.
- D4: persona-implementer; `pm-dev-java:junit-core`, `pm-dev-java-cui:cui-testing`, `pm-documents:ref-asciidoc`.
- Self-review rounds: persona-code-reviewer (one sub-agent, three rounds).
- `skills: none named` does not apply (every deliverable declared its skills). `skills added: none` (no surface drift; the ten changed files are all in the declared Expected Surface).

## 3. Hypotheses

- "A mutant back to `+` at `MavenOutputParser:433` survives" — **confirmed**: run at `f793c2f3`'s tree, log `fde0814cefd0462eb52504f3fec5d6d1`; `addFailures:533` and `filedFailures:551` saturate again and `saturatedAdd(0, Long.MIN_VALUE)` answers `Long.MAX_VALUE`.
- "`KillBindingDependentControl` resolves its interface through ArchUnit" — confirmed by reading: `isAssignableTo(ProcessGroupSignals.class)` catches both the interface and its implementations; the two clause-removal runs discriminate them (logs `7f49f871…`, `bb09497a…`).
- "Any runtime exception other than the five listed escapes `run()`" — confirmed by reading: `run` catches `UncheckedIOException | IllegalArgumentException | IllegalStateException | InternalFaultException | StoreRefusal` only.
- "`runtime-job.adoc:291` carries the unsupported why-clause" — **refuted**: the file never carried it at the analysed heads (`git log -S` on the phrase is empty; `8537af76` introduced the factual sentence), and the Javadoc did carry it. Reported in D2.

## 4. Findings

Self-review (persona-code-reviewer, three rounds, all dispositioned):

| # | Finding | Disposition |
|---|---|---|
| R1-1 | the new test comment claimed `NFC(U+0390)` is U+0390, a claim this plan cannot settle | comment rewritten to state only the two upper-case forms and the fold's second NFC step — `901a83f8` |
| R1-2 | the line-sum comment claimed a wrapping addition "would report the line as a passing run", which the mutant run refutes | comment now says the test pins the observed figure and that the mutant survives — `901a83f8` |
| R1-3 | the redundancy of the `:433` addition was not recorded in the code | recorded in the test comment and this report — `901a83f8` |
| R1-4 | the 19-digit test duplicates the 20-digit branch | the comment now names the pre-existing test and what the boundary adds — `901a83f8` |
| R1-5 | the "undecided arm has no seam" claim was too strong | corrected, and then superseded by the undecided-arm test — `901a83f8`, `e189efc1` |
| R1-6 | ADR 084 reflow left "canonical" alone on a line | re-wrapped — `901a83f8` |
| R1-7 | commit `06bcbbed`'s attribution of the guide sentence was wrong | corrected in the PR body and here: `8537af76` (PLAN-V2-173), not `baa730ec`; the pushed commit is not rewritten |
| R2-A | the overflowing-executions comment still said the 1(b) test pins the addition | rewritten to match the sibling comment — `e040020a` |
| R2-B | "the erroneous-sum test" named no test | names `cutMavenLogSaturatesAnOverflowingFailingCount` and "the two executions' figures" — `e040020a` |
| R2-C | the undecided arm has two causes; the sentence named one | both windows named in Javadoc, ADR and guide — `e040020a` |
| R2-D | the build evidence predated the review commits | the gate was re-run at `e040020a` and again at `f793c2f3` and `5e6d1f99` |
| R3 | "one line's overflowing two counts" wording | "two counts whose sum overflows" — `f793c2f3` |

CI and PR comments:

| # | Finding | Disposition |
|---|---|---|
| S1 | SonarCloud `java:S5976` on `BuildLogTextTest:566`: "Replace these 4 tests with a single Parameterized one" | fixed: the four fixtures are one `@ParameterizedTest` with a `@MethodSource` — `e189efc1`; re-read 0 |
| S2 | SonarCloud quality gate `ERROR`, `new_coverage` 0.0 (the only new executable production line is the undecided report text, untested) | operator selected adding the undecided-arm test; gate `OK`, `new_coverage` 100.0 — `e189efc1` |
| CR1 | CodeRabbit (minor): the schema sentence named only the record's return as the discard's refusal | fixed: the sentence names both, the reopen and the failed delete — `5e6d1f99`; replied and resolved |
| CR2 | CodeRabbit (minor): the reach recomputed after a lock-release fault can read another writer's journal as the sweep's `OWED` | not fixed here: pre-existing code from PLAN-V2-178 (`RemovalState`, `remove`'s catch), a behaviour change needing its own test and the outline's word; replied with that reason, thread resolved, recorded in the PR body as a staged follow-up |
| CR3 | CodeRabbit pre-merge title check inconclusive | title made specific: "parameterize the cut-log saturation cases and correct the residue's statements (PLAN-V2-180)" |

`finding:` lines: `coderabbit inline inline-1 — 5e6d1f99`; `coderabbit inline inline-2 — out of scope: pre-existing v2-178 reach-recomputation race, staged follow-up`.

## 5. Reviewers

| Reviewer | Required | Outcome | Head | History |
|---|---|---|---|---|
| coderabbit | yes | reviewed (2 minor inline findings, both handled; review-body findings 2/2 disposed) | `e189efc1`; the marker-bearing summary names `coveredCommitId e189efc1`, and the review is the `review_complete_after_handling` review, so no re-trigger was owed for the later doc-only head `5e6d1f99` | trigger 08:16:46Z refused on quota; trigger 09:03:14Z refused on quota (next allowed 10:33Z); waited 90 min in two `wait-for-comments` calls, trigger 10:30:44Z accepted, review at 10:36:51Z |
| sourcery | no | reviewed and approved (`e189efc1`) | `e189efc1` | triggered 08:16:58Z (refused on its budget), 09:03:26Z (refused); its `review_body` "Approved." on `e189efc1` stands |

Standing review authorization was not needed: CodeRabbit holds a qualifying review and every finding is handled, so `review-gate` answered `merge_allowed: true` (`all_handled`, 0 unresolved threads, 2/2 body findings handled) on `5e6d1f99`.

## 6. Operator questions

1. Asked: "PR #593's SonarCloud quality gate is red, and the decision is yours … How should I proceed?" with the facts read on `f793c2f3` (gate ERROR on `new_coverage` 0.0, the other five conditions OK; one new-code issue `java:S5976` fixed in the working tree; the only new executable production line is the undecided report text and no test reaches it). Options: "Add the UNDECIDED-arm test (Recommended)", "Keep the spec, merge on a red gate", "Stop here". Selected: **"Add the UNDECIDED-arm test (Recommended)"**. Acted on exactly that selection: the test was added and the statements updated.
2. No other question was asked; no free-text operator messages were sent.

## 7. Rule check

`guard: none on this harness (unenforced)` — the resolve result reported `guard: none`, so nothing was enforced and no guard-log line exists for the run; this section counts the run's own actions.

- **Shell rule violations (mine, read-class included):** the run issued shell file operations, which the rule routes to the file tools — about a dozen `grep`/`ls`/`head`/`sed` reads of Java sources, surefire reports and the build log, several of them with a pipe (`git log … | head`, `grep … | sed`), one `;`-separated pair of `ls` commands at the start, one stray no-op `python3 .plan/execute-plain 2>/dev/null; true`, and one `cp` of the PR-body slot from the `pr-create` to the `pr-edit` path (the correct path was `pr prepare-body --for edit`, which was then used). No `cat`, no `find`, no git `grep` subcommand, no temporary file outside `.plan/temp/`.
- Everything else was kept: the executor for every script, the resolved `build` commands (with `-Dtest` scoping the spec allows) for every build, `ci` for every CI action, no `gh`/`glab`/`mvn`/`git`-direct build, no `git stash`/`restore`/`checkout`/`rebase`/force-push, no shell file edits (the `FreshnessReconcile` restorations were file-tool edits), no write under `.plan/` beyond `.plan/temp/`, the two `ci` body slots and the one inbox message, and no read outside the repository except the build/PR logs the verbs name.
- `spec_source`: `ledger_worktree`, with no `spec_differ` marker (the main checkout's copy did not exist at run time, so no older copy could be read silently).
- `executor-skew`: `match` at setup (executor and lane text both 0.1.2183); the resolve re-run at landing reports `executor_version 0.1.2184`, the version the worktree preparation wrote.
- Sub-agent: one `general` sub-agent, three rounds, no restarts, no killed agent, no re-dispatch; its rule breaks, if any, would count as mine and are not separately observable (it reported no shell use; I did not verify its log).
- Spec deviations, both reported above: the undecided-arm test (operator-selected) and nothing else; `JobLog.java` untouched, no production behaviour change beyond the Javadoc/string statements.

## 8. Build server

Setup preflight: `ready` (registered, worktree path). Build routing: `auto` with `executor_in_worktree: present` — every build was submitted and ran `daemon_longpoll`; **no** `in_process_fallback`, no launch-fault retry, no `killed` job, no fallback at all. Twenty-one builds in total: sixteen targeted `-Dtest` runs, four `verify -Ppre-commit -pl pm-core,pm-runtime -am` runs (one red: the assertion-message scanner caught a 62-character message in my new test, fixed), three full `verify` runs, and one `indeterminate` run that executed nothing (a `-Dtest=A+B` separator Maven does not accept; re-run with commas, which is why the targeted count changed). `runtime_stamp_reason`: none reported. `plugin_version: unknown` is what the resolve result carries.

## 9. Residue

- **Owed to the epic's analysis as a staged fix:** CodeRabbit's second finding — `PendingEntryRecordsSweep.remove` recomputes `reach` after a lock-release fault, so another writer's journal can be read as the sweep's `OWED`. Pre-existing (PLAN-V2-178), needs its own test.
- D1(b)'s Done-when stays **not met as written** (the mutant cannot be killed); the test and its comment record why, and a spec correction may be owed to the epic's ledger.
- The recorded-not-fixed items of carried item 100 are unchanged and listed in the PR body (`-T` interleaving and the count key's cost, the double `pending_entry_removed` record, the `ent-` prefix shadowing, the creation cut and the live-plan enumerator, the `JobLog` Javadoc owed to PLAN-V2-162, the 178 M4 mutation count).
- The Sonar facts are the head's own measurement (`0`, `confirmed`, gate `ok`, 100 % new-code coverage); the base branch did not move between the last read and the enqueue.
- `inbox landing-check` on this message answers `complete: false` with `missing_keys: total_tokens, plugin_version`: both are the values the resolve result carries (`unknown`), and the checker treats `unknown` as an unsupplied read — the harness exposes no per-session token total, and the runtime-info seam returned no plugin version. The drain gap is real and is reported here rather than papered over.
- No base-evidence worktree was created, so no cleanup is owed; branch and worktree are gone and the main checkout is at the merge commit.

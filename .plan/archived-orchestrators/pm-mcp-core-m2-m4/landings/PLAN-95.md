# Landing Analysis: PLAN-95 — Check the length of a string before its pattern in the verification corpus

epic: pm-mcp-core-m2-m4
workstream: WS-05
pr: #144 (merged as 3cc1585460fff241755d50d09b86c812da52750a)

> Landing record for one shipped plan. Source: inbox message `rb-95-schema-subset-length-first-001.md`
> (runbook lane, opencode, 2026-10-22T21:30:30Z), corroborated against git at `3cc1585`, the PR through the
> `ci` abstraction, and the SonarCloud pull-request API (plain HTTP, read only). The message is a lead; the
> facts marked "checked" were read from those sources, the rest is the lane's own report.

Standing rules: r1, R01 to R15, R17 and R18 named by the landing. This is the first landing to name R18
(the SonarCloud rule) and to carry the optional `sonar_open_issues` fact (`0`).

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 — `maxLength` tested before `pattern` in `SchemaSubset.checkString` | shipped-as-specified | Checked: `git show 3cc1585` moves the `maxLength` block to the head of `checkString`, adds `return` after the length error, leaves the pattern block untouched; message text `%s: longer than %s` unchanged; class Javadoc states the order. One method changes, no signature. |
| D2 — the tests in `SchemaSubsetTest` | shipped-as-specified | Checked: +79 lines in `SchemaSubsetTest.java`, a nested `LengthBeforePattern` group. The lane maps (a), (b) with its counterpart, (c) with the exact-bound case and (d) to named tests; (a) and (b) are `test-first` with the failing output recorded (tree `0540031` plus the uncommitted tests), the rest `none`. Test run by the lane, not re-run here: 120 tests in the scoped run with `-Dpm.product.root` set, so `ProductCorpusValidationTest` ran; 6958 tests across the modules at the final head. |
| D3 — ADR and developer documentation | shipped-as-specified | Checked: `pm-core/doc/adr/core-role-corpus/008-length-checked-before-pattern.adoc` (+60) and `core-role-corpus.adoc` (+5/-) are in the diff. The lane reports all seven ADR sections and that the text says the length error replaces only the pattern error (`const` and `enum` are still reported), after a CodeRabbit finding. |
| D4 — the read-only sweep in the PR body | shipped-as-specified | Reported by the lane, three sites (`SchemaSubset.checkString`, `ArgumentSchemaValidator.matches`, `CompositionRule.Disjoint`); nothing outside `checkString` was changed for it (checked: no further production change in the diff). The PR body was not re-read. |

Done-when builds, reported by the lane and not re-run here: `verify`, `verify -Ppre-commit` (no rewrites afterwards), `verify -Pcoverage`, `verify -Pintegration-tests -pl pm-core -am`, all green at the final head `e37a7b8`. Checked: the SonarCloud pull-request search for #144 lists 0 open issues and the quality gate reads `OK` (new coverage 100.0).

**One deviation from the spec's own Done-when sentence "no file other than …"** is not applicable (PLAN-95's Done-when had no such sentence), but the diff holds five files its Expected Surface did not name; see Anomalies.

## Metrics and Anomalies

- Tokens: unknown (opencode measured none; `inbox landing-check` reports `missing_keys: [total_tokens]`).
- Duration: not supplied.
- **Undeclared surface (checked, `surface_delta`: `expansion_detected`, 4 declared, 9 realized, 5 added, 0 missing).** Five `pm-runtime` files are in the merge: `SubprocessJobRunner.java` (five `// cui-rewrite:disable InvalidExceptionUsageRecipe` markers inside catch blocks, one realigned continuation line) and four test files (`ProbeJobTest`, `SubprocessJobRunnerTest`: `throws IOException` widened to `throws Exception`; `ProcessTreeTrackerTest`: a blank line and a joined line; `JobRecoveryStepTest`: not read). They are the output of `verify -Ppre-commit`, which rewrites files of `pm-runtime` that PLAN-95 never touched. The lane asked the operator, who chose "commit them as produced". PLAN-96 met the same rewrites one day earlier and reverted them. They are now on `main`, so the next lane's pre-commit run should not rewrite those files again.
  - The widening of test signatures to `throws Exception` is the recipe's output and sits against the project rule against generic `Exception` in production code; it is test code, so the rule does not bind it, but it is a rewrite nobody chose on its merits.
  - The six `java:S1135` TODO markers the recipe also produced were replaced by the lane with the recipe's own suppression comment (commit 40f4a3d); the SonarCloud result for the PR is 0 issues.
- Build: the routed build failed once (`executor_absent_from_worktree`, a lane worktree with no `.plan/execute-script.py`), every later build ran `in_process`; one run hit the learned 776 s bound after 3047 passing tests and was re-run with `--timeout 1800`. `/tmp` inode exhaustion blocked a build again (operator chose "clean /tmp build scratch"). The setup preflight reported `ready`, so the in-process fallback came from the missing executor in the worktree, not from a down daemon (as the lane states).
- Rule deviations, all disclosed by the lane (guard: none on this harness): one edit of the main checkout at the start (reverted before any commit), one `git grep`, one `;` in a shell call, reads of `/tmp` and of the daemon's job log outside the repository.

## Routing and Merge Behavior

- Review: CodeRabbit (required, quota-limited) reviewed `f0248bd` with one minor finding (limit the length-alone claim): valid, docs and PR body corrected in `3248e29`, thread resolved and confirmed; re-triggered once after the last push. Sourcery (optional) refused on quota. Merged under the standing review authorisation.
- CI/merge: checked, #144 reads `merged`, head `fix/95-schema-subset-length-first`, merge commit `3cc1585460fff241755d50d09b86c812da52750a`, first parent `9c9d99c`. Merge queue; no rebase conflict reported.
- Collisions (checked with `corpus declaration-currency` against the 102 other specs; 23 touch the footprint, mostly through the directory `core/role/corpus/` or one shared document):
  - **PLAN-94** (launched): `SubprocessJobRunner.java`, `SubprocessJobRunnerTest.java` and `JobRecoveryStepTest.java` changed under it (3 files). Its lane must merge the base and re-read them; its Sonar findings in `SubprocessJobRunner` may have moved.
  - **PLAN-26** (running): `SchemaSubset.java` and `SchemaSubsetTest.java` (2 files), the overlap the operator accepted. Its lane was told to merge the base before the gate.
  - **PLAN-27** (staged): four of the five `pm-runtime` job files; **PLAN-84** (shipped): five; not live.
  - **PLAN-40** (staged): `core-role-corpus.adoc`, which it will write.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-95 --status shipped`
- [x] row `pr` stamped — `#144`
- [x] row `landing` stamped — `landings/PLAN-95.md`
- [x] row `plan_marshall_plan_id` stamped — `rb-95-schema-subset-length-first` (from the sender)
- [x] epic.md narrative reconciled: the `SchemaSubset.checkString` Open Defect moved to settled, queue annotation for PLAN-95, `total_tokens` recurrence, one new Open Defect (the unbounded patterns of the shipped corpus schemas, the lane's lead), watches updated
- [x] resume anchor updated in `resume_anchor.md`
- [x] `queue-view.md` regenerated and committed with the row change

## Follow-Ups

- **Lead from the lane, not owned by any plan:** `imported-item.schema.json` of the shipped corpus has nine `pattern` members and a `maxLength` only on `title`, a field without a pattern, so the nine are unbounded before the regex engine. The enrolled path bounds this with `PATTERN_MAX_LENGTH_CEILING`; the corpus path does not. The lane did not check it against the files and this verb has not either; recorded as an unverified lead (Open Defects), the operator decides whether it becomes a plan.
- **`worktree-create` supplies no `.plan/execute-script.py`** in the lane worktree (second landing in a row; also PLAN-96). Already a Watch; this adds the second observation and the cost (every build in process, a learned timeout bound hit). A candidate for plan-marshall's lessons-routing inbox, still the operator's call.
- **The pre-commit rewrite of untouched `pm-runtime` files** is the root of the undeclared surface; it is on `main` now. Recorded so the next lane that sees `verify -Ppre-commit` rewrite files outside its surface knows to ask, not assume.
- **PLAN-94 must re-read** the three files named above before it edits them.

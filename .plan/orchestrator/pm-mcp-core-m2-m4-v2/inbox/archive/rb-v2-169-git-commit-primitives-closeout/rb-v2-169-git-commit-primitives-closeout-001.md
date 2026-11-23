envelope_version=1
sender_type=plan
sender_id=rb-v2-169-git-commit-primitives-closeout
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-23T02:49:42Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-169-git-commit-primitives-closeout
epic=pm-mcp-core-m2-m4-v2
pr=#568
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2184
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=6
review_body_findings_handled=6
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```
guard: none on this harness (unenforced)

# PLAN-V2-169 landing: the closeout of the commit primitives

Standing rules: r7, rule ids R02, R05, R06, R07, R08, R09, R10, R11, R13, R17, R18, R19, R20 (R21 binds the orchestrator, not this plan). The spec was read from the ledger-worktree copy (`spec_source: ledger_worktree`, no `spec_differ`). `executor-skew`: `match` at 0.1.2183.

## Deliverables

| # | Deliverable | Verdict | Evidence |
|---|---|---|---|
| 1 | The staging set against the path refusals | met | `CommitPaths` shared by `JGitOperations.commit` and `StagingSets.derive`; `StagingSetsTest.refusedNameIsOutside` (5 values) and `embeddedRepositoryIsOutside`, `CommitPathsTest`; the `commit_failed` detail made repository-relative/coded with `modelDetail`, `GitStageAndCommitPrimitiveDeclarationsTest` (8 tests). Orders and failing runs: staging set **test-first** at `f0afe6c6` → `e8de8ce7` (log `532a2aa804094022992f3308ac84f7de`); detail **test-first** at `46181f0d` → `c1e86a34` (log `9ea5404d06744f06a26a3907f560646e`); scrub/relativize **mutation-after** at `cef1f085` and `288483b2` (logs `cf9e0e0daca6415289665ecfbd8dda3f`, `aaa74a5546a44db7a4e993e73f1c860c`). Commits `e8de8ce7`, `c1e86a34`, `cef1f085`, `288483b2`. |
| 2 | The bound `files` outside the staging set | met | Javadoc, guide and ADR 003; `GitStageAndCommitPrimitiveTest.boundPathOutsideTheSetIsDropped`, **mutation-after** at `2c88d4ae` (log `f212161b19d54c34bf5412a5723963bb`). Commit `2c88d4ae`. |
| 3 | The clean-tree route and the missing identity | met | `Params.message` `@Nullable`, `nothing_to_commit` before the message, `commit_failed` without one; `workflow-dsl/06`, `state-catalogue/05`, `store-schemas/08`; ADRs 009/010. Tests `cleanTreeCommitsNothing`, `missingMessageFails`, **test-first** at `2c88d4ae` → `277c0aa6` (log `f722e05854644e95a97135cef52772e9`). Commit `277c0aa6`. |
| 4 | The tests that do not exist and the classes over the budget | met | `JGitOperationsPathTest.refusesAPathBelowADanglingAncestor` and `JGitOperationsTest.nothingToCommitLeavesTheIndexUntouched`, both **mutation-after** at `10615a9f` (logs `8fc3e7d4970a437d9cbe09d7a669eb32`, `de9a6c346f2a4f6195cb04753d052671`); the staged-drift restoration with its two tests and the stat data (**mutation-after**, logs `b44942dfc912428a80430ced2795ba7a`); the four test classes split by behaviour, order **none** (a pure move, 721 tests on both sides, `d1627d80`); no file over the 400-line budget (largest 391). Commits `10615a9f`, `d1627d80`, `76b3e9bd`, `288483b2`. |
| 5 | The statements that over-claim and the schema | met | `doc/Traceability.adoc` (`:846`, `:1432`), ADR workflow-verify/043, `local-config.schema.json` patterns and `CommitTrailerSchemaParityTest`, **mutation-after** at `46181f0d` (log `ecccb7c89a1640cd8f5cb6e5ac9973da`). Commits `46181f0d`, `c1e86a34`. |

Build: `verify -Ppre-commit` green at `288483b2` (12 731 tests, log `3a4532c88fad434586256efb6211847f`), no rewrite left in the worktree; `verify` green at `288483b2` (log `0a92d93ac8d54d7ca8c6d611ace35985`). The merge of `origin/main` (`57f9d33f`) was gated in its own run (`797ad4e03bd34fd884d142e29e787d73`, `04a55b75a2c744748d9295957e6354b4`, 12 730 tests) and its two out-of-scope rewrites were committed alone (`c0c23581`).

## Personas and skills

- Personas: `persona-code-reviewer` for the three self-review rounds and for triaging every CodeRabbit finding; `persona-implementer` for the production code; `persona-module-tester` for the tests; `persona-documenter` for the guides, ADRs and specification texts.
- The spec names no per-deliverable `Skills:`/`Persona:` lines: `skills: none named`. The coding skills R20 names were read as standards (java-core, java-null-safety, javadoc, junit-core, cui-testing).
- `skills added`: none.

## Hypotheses

- D1: `StagingSets` accepted names the commit refuses — **confirmed** (`StagingSets.java:105-126` vs `JGitOperations.java:487-495` before the change).
- D3: the catalogue routes `ready` unconditionally and the primitive required a message — **confirmed**; the first reading of "how the engine binds an absent fact" was **refuted** by the self-review (the engine does bind a call's inputs; the unit declares `message` optional) and ADR 009 now states the correct premise (`WorkflowEngine.java:1747-1786`, `unit-fixtures/defaults/git-commit-and-push.pmwf:12-13`).
- D4: `git.add` ran before the change check and the dangling-ancestor case had no test — **confirmed** (`JGitOperations.java:374-391`, `:529-545` before the change).
- D5: the schema patterns were wider than the code — **confirmed** (`local-config.schema.json:161-162` before).

## Findings

Self-review round 1 (14 findings): the missing D1 `detail` clause (fixed `c1e86a34`), ADR 009's premise, the `commit_failed` facts row, the schema over-claim, six stale test-class references, `DETAIL_FACT`'s Javadoc, the parity test's engine note, `assertFailed`'s aggregation, the quoted-path statement; F7 (no ADR for D5) was withdrawn as refuted (the spec's plan parameters allow ADRs only for D1–D3), F8 and F12 became the Watch below, F13 was accepted.
Round 2 (7): the `-Ppre-commit` artifact (added), the over-claim on absolute paths (scoped, then widened), the sibling mangle, the untested branches and wiring (four tests), the links artifact (recorded), the four residual stale references (fixed `c1e86a34`). Round 3: nothing material; the three info notes were fixed in `cef1f085` and `288483b2`.
CodeRabbit (two reviews, 6 findings): the staged-drift index overwrite (**major**, fixed `76b3e9bd`, stat data `288483b2`), the `quotePath` statement (`76b3e9bd`), the relativized-detail scan (`76b3e9bd`), the traversal in the detail (`288483b2`), the index stat fields (`288483b2`), and the schema-enforcement request, answered with the operator's D5 decision and carried as a Watch.
SonarCloud: `java:S1130` on the split test (`d38fbb9e`) and `java:S1192` on the detail literal (`80bcc583`), both fixed.

## Reviewers

- `coderabbit` (required, quota-limited, `review_complete_after_handling`): refused three triggers on quota (`21:14Z`, `21:44Z`, `23:25Z`), the escalated `@coderabbitai full review` at `00:56Z` triggered the review, delivered at `01:10Z` on `d38fbb9e`; a second review covered `c0c23581`. All 6 findings have a disposition (5 commits, 1 stated operator reason), all 5 threads are resolved, and the review-gate read on `288483b2` answers `all_handled` with 0 unresolved threads.
- `sourcery` (optional): refused at `21:14Z` — the PR is larger than its 150 000-diff-character limit.

## Operator questions

One question-tool call, seven ask-at-refine decisions of the spec, each with the analysis's recommendation first:

1. D1, a commit-refused dirty name: "Put it in `outside`" (selected) vs "Leave it staged".
2. D1, how a rename is listed: "Both sides of the rename" (selected) vs "The new path only".
3. D2, a bound `files` entry outside the set: "Keep the narrowing, document it" (selected) vs "Refuse in the primitive".
4. D3, the clean tree: "The primitive handles it" (selected) vs "The specification names the route".
5. D3, the missing-identity code: "The specification follows the code" (selected) vs "The code follows the specification".
6. D4, the index left staged: "Stage after `hasChanges`" (selected) vs "Assert the current behaviour".
7. D5, the trailer schema: "Waiver + refuse controls in the code" was recommended but the operator selected "**Change the schema pattern**"; the code therefore keeps its textual rule and the store's schema is the gate.

No free-text operator messages.

## Rule check

`guard: none on this harness (unenforced)`. The run's own actions, checked against the lane's rules:

- **Breaks, reported as such.** One `git grep -n '"no changes"'` (a forbidden git subcommand). One `ls ~/.m2/repository/org/eclipse/jgit/org.eclipse.jgit/` and one `find ~/.m2/repository/org/eclipse/jgit -name …` (shell file listings outside the repository, used only to locate the JGit jar) and one `javap` on that jar to read `DirCacheBuilder`/`DirCacheEditor` (a tool outside the build seam). All four were compensated: the information is reproducible from the compiler errors and the JGit API, and no secret-bearing path was read. One `git commit --amend --no-edit` on the unpushed docs commit `8f6bf046` (no rebase, no force), to add the ADR 004 alternative line to the same commit.
- Everything else went through the executor, the resolved build commands and `ci`; one command per shell call; files read and edited with the file tools; temporary files only under `.plan/temp/` and the sanctioned `NO_PLAN` CI body slot; edits only inside the worktree; the merge of `origin/main` and `git worktree remove` are the sanctioned forms.
- The links check (R17) ran with the absolute paths from the worktree: `BROKEN LINKS 3252` — the pre-existing cross-repository set this host cannot resolve (the epic has that caveat recorded); re-run after the fixes, the same count, with the two links this run introduced fixed in `2e0dbc63`. Output: `/home/oliver/.local/share/opencode/shell/7dab1c2204d6bcf492523491c26dd34d59d10998/sh_204fa9c460010LhZlODRcLpgYq.out`.
- Skill, persona and script files were loaded through the `skill` tool and the executor; the ledger was read only.

## Build server

Setup preflight: `ready` (registered). Every build ran with the default routing; the client's `[BUILD-SERVER] resolved build` line reported `requested=auto, resolved=routed, mechanism=daemon_longpoll` on every submit — 41 build-seam runs, all `daemon_longpoll`, no `in_process_fallback`, no `executor_absent_from_worktree`, no `launch-fault-retry`, no `killed` job. Three runs were compile failures of the run's own edits (`403da575`, `e8f56257`, `943d030a`), fixed and re-run; the two runs that exceeded the client's first long-poll window (`3ffea9fd`, `0ee89978`) were re-attached by re-issuing the same command. No `runtime_stamp_reason` was returned; `plugin_version` resolved as `unknown`.

## Residue and Watch

- **Watch (assembly `plan-marshall-mcp`).** `CommitSite.dirtyPaths` must list an embedded repository and a modified submodule by their directory, list a rename by both sides, and read the status NUL-separated (`-z`; `core.quotePath=false` alone still escapes quotes, backslashes and controls). Its `localTrailer`/`machineTrailer` values are only as clean as the store's schema check: a value written before the new patterns, or without that check, can still carry a C0/C1 control that the code's textual rule admits.
- **Residue named, not fixed.** `commit_identity` in the same `local-config.schema.json` keeps the wider pre-change patterns (`:130-131`); the plan's D5 names only `commit_trailer`. The `broken links 3252` baseline is the host's cross-repository state, unchanged.
- No file over the 400-line budget; no new hand-written fake; no dependency or plugin added.

## Residue owed to the milestone exit

None beyond the Watch above.

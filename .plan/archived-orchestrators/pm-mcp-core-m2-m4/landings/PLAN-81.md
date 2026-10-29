# Landing Analysis: PLAN-81 — Harden the unit frontmatter reader and the project unit loader

epic: pm-mcp-core-m2-m4
workstream: WS-03
pr: #114 (https://github.com/plan-marshall/pm-mcp-core/pull/114), merged as squash `afc918b` on 2026-10-20

> Landing record for one shipped plan. Lives at `landings/PLAN-81.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source of the landing: inbox message `rb-81-unit-frontmatter-hardening-001.md` (`kind: landing`, sender `rb-81-unit-frontmatter-hardening`, runbook lane on opencode, model `deepseek-v4.1-flash`, plan-reported; `landing-check`: `complete: false`, `missing_keys: [total_tokens]`). Corroborated by the orchestrator against `ci pr queue-state --pr-number 114` (`merged`, `afc918bc…`, merge-group run 39446550903 `success`), `ci pr comments --pr-number 114` (27 entries: 3 reviews, 22 comments, 2 inline threads), `git show --stat afc918b` (17 files, +887 −83, all `pm-workflow` plus documents), the diff of `doc/specification/workflow-dsl/01-grammar.adoc` and `doc/Traceability.adoc`, the constants and the author comparison in the merged source, the four touched test classes run by the orchestrator on `main` (550 tests, `status: success`), `trace.py` run by the orchestrator on `main`, `git status` of the main checkout, and the branch and worktree lists. The harness session store and the lane's progress file were not read; the build-gate figures (6780 tests, coverage, the failing runs by log name) are plan-reported and not re-run (the merge-group run is `success`).

## Deliverable Fidelity vs Spec

The landing counts seven deliverables (`deliverables_total=7`, `deliverables_done=7`): D1–D6 and T, all reported met.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 Decide in the specification | shipped-as-specified | The operator was asked with the question tool (plan-reported, the first call aborted and re-asked): "Refuse everywhere + raw author pre-check", bounds 1 MiB / 200 lines / 80 characters. The decision is in `doc/specification/workflow-dsl/01-grammar.adoc` § Unit Frontmatter as three bullets (admitted characters, bounds, the reserved author) and the old "stays specified here" sentence is split from them |
| D2 The reader refuses Cf / Zl / Zp | shipped-as-specified | The 01-grammar bullet names control, format (Cf), line separator (Zl) and paragraph separator (Zp) characters, refused "with the code point and the line"; `UnitFrontmatterReader` is +121 lines, `isPlain` stays the one definition (the `WorkflowPrinter` test calls it); three new malformed fixtures (`frontmatter-format-character`, `-line-separator`, `-paragraph-separator`) |
| D3 Reserved author | shipped-as-specified | `ProjectUnitLoader` compares `Normalizer.normalize(author, NFKC)` with format characters removed, `strip()`, then `toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT)` against `RESERVED_AUTHOR = "plan-marshall"`. The landing's self-review fix (NFKC before the space strip) is visible in the order of the calls. The spec says "case-folded"; the code upper-then-lowercases, which is the usual stand-in and is ADR 006's decision, not a deviation |
| D4 Bounds | shipped-as-specified | `MAX_UNIT_FILE_BYTES = 1_048_576` and a bounded read (`readNBytes(MAX + 1)`, refusal when the length exceeds it), `MAX_FRONTMATTER_LINES = 200`, `MAX_ECHO_LENGTH = 80`, each a named constant equal to the specification's number. A test (`UnitFrontmatterReaderTest`, line 671) reads `01-grammar.adoc` and compares, as the matrix asked. The frontmatter is read from the head (a bounded loop, the landing says; the body is never lexed for it) |
| D5 Tests | shipped-as-specified | Four test classes extended (`UnitFrontmatterReaderTest` +237, `ProjectUnitLoaderTest` +166, `WorkflowParserRefusalTest` +47, `WorkflowPrinterTest` +25); the orchestrator ran them: **550 tests, success**. CRLF twins and the enum-derived completeness test are reported; the three new fixtures are LF files and their twins are parameterised in-memory (the landing says so itself) |
| D6 Documents | shipped-as-specified | `pm-workflow/doc/developer/workflow-dsl.adoc` (+37) and `workflow-unit.adoc` (+27), ADR 006 of `workflow-unit` (new, 61 lines), ADR 005 of `workflow-unit` extended (+2), one new ADR of `workflow-dsl` (62 lines, the refused characters and the frontmatter bounds), and two `PM-IMPL-5` blocks of `doc/Traceability.adoc` rewritten. Specification and code are in the one pull request |
| T Lane test proof | shipped-modified | T1: test-first for D2 and D4, mutation-after for D3 and the far echo (plan-reported, with log names; not re-run). T3: the diff removes 83 lines, none read as an assertion (not exhaustively checked here). T4: `verify -Ppre-commit`, `verify` and `verify -Pcoverage` on the merged head `ead74de` (plan-reported, 6780 tests); the integration profile was not run because `pm-core` and `pm-runtime` are untouched (the diff confirms it), the PR's own workflow passed. T5: three self-review rounds, clear. T6: counts and coverage reported (`pm-workflow` 1826 → 1966, 94 % instruction / 87 % branch). Modified because of the rule breaks listed below |

Hypotheses of the spec, as the landing settles them: the control-only check (`UnitFrontmatterReader` ~405–412), the exact-equals author comparison (`ProjectUnitLoader` 266), the unbounded read (223) and the whole-source split (126) were all confirmed against `993a7e7`; "neither finding is exploitable alone" was confirmed by use. The spec's claims were not re-read line by line here.

Surface delta (`inbox landing-check`): `unmeasured`. Orchestrator count: 17 files; beyond the spec's hint the plan added `UnitFinding.java`, `WorkflowParserRefusalTest.java`, `WorkflowPrinterTest.java` (the landing names them), the three fixtures, `doc/specification/workflow-dsl/01-grammar.adoc` (the decision's home, which the spec names) and the two developer files. No file in `pm-core`, `pm-runtime` or `doc/plans/`.

## Metrics and Anomalies

- Tokens: `unknown` (the opencode harness measured none). The third runbook landing on opencode carrying it. It is the one missing key of `landing-check`.
- Tests: `pm-workflow` 1826 → 1964 at `8a81a24` → 1966 at `5ca7772` (plan-reported); the orchestrator's own run of the four touched classes: 550 tests, success. Coverage of the touched module (plan-reported): 94 % instruction, 87 % branch.
- Link check: the lane had read access to the documentation checkout (operator-selected) and chose not to read requirement texts or run `trace.py`, stating that the changed documents link only to in-repository paths (Residue). The orchestrator ran it on `main` at `afc918b`: `BROKEN LINKS 0`, `WATCH ITEMS NOT REFERENCED FROM REQ/SPEC []`, `UNDEFINED REQ IDS REFERENCED []`. The `CLAUDE.md` rule ("check them with trace.py") was therefore not followed by the lane, though the outcome is clean; the same finding as at PLAN-88.
- Rule breaks the lane reported itself (guard: none on this harness, so nothing enforced them): six files were first edited in the **main checkout** by mistake, copied into the worktree with `cp` and the added blocks removed from the main checkout by hand; one scoped test run (`maven-2026-10-20-054327.log`) ran against the main checkout; build logs were read with `tail` twice; `cp` and `rm` were used. Verified: `git status --short` of the main checkout is empty, no worktree or branch for the plan remains, and the merged diff holds only the 17 files above, so the correction left no stray change. The rules the lane broke ("repository files are edited only inside the worktree", "files are read with the file tools", one command per call) are the lane contract's, not this epic's.
- Two pre-commit runs were stopped by the 30-minute shell timeout and re-run to completion. One CI flake: `LspClientTest.stubborn` in `pm-runtime` (2-second initialize timeout) under a concurrent reactor build, untouched module, green on re-run — the same test that flaked at PLAN-88.
- Executor: this lane read the same stale `runbook_lane` (pinned at 0.1.1961 on this checkout) but the landing reports no `unknown_verb` and no build-name rejection; the gates ran. Whether the lane used `CLAUDE.md`'s literal commands is not stated.

## Routing and Merge Behavior

- Reviewers (read from the PR): `coderabbit` — three reviews, five findings over the run (4 on `8a81a24`, 1 on `92223c2`, 1 nitpick on `0e6de69`), all fixed; the completion summary names `ead74de` as the covered commit (`coveredCommitId`), "No actionable comments", merge risk minimal. `cuioss-review-bot` — "Review updated until commit 5ca7772", no findings (comment last updated 13:06:44Z); **it did not review the merged head `ead74de`**, which is the merge of `origin/main` into `5ca7772` (documentation commits only, per the landing's note that the merge added no `pm-workflow` test). `sourcery-ai` (optional) refused on quota. Inline threads: 2, the first `resolved: true`; the second marked addressed by the bot (plan-reported).
- The gap was put to the operator with the question tool, naming the reviewer, `5ca7772` and `ead74de`, and the operator selected "Merge with the gap" (plan-reported, after a first aborted call). That meets the lane's own rule for a rule-bending merge (an operator-selected option naming the exact gap). The recommended option was "Re-trigger cuioss and wait". The bot had been triggered twice on `ead74de` and stayed silent for 300 s; the same bot updated to the merged head at PLAN-88.
- CI/merge: merged by the merge queue as `afc918b`; `in_queue: false`; no worktree and no branch for the plan remains (`git worktree list`, `git branch -a`), so `cleanup_owed=false` holds.
- Collision with the plans that ran beside it: none observed. PLAN-88 touched `pm-runtime` and the Traceability block of `PM-ARCH-4` and the `QuestionLog` passage of another block; PLAN-81 touched two other blocks of `doc/Traceability.adoc` (`PM-IMPL-5`) and the ADR README is untouched by it. The merge of `origin/main` into the PLAN-81 branch had no conflict.
- Breaking for another repository: none; the project unit loader is not yet called on a lifecycle path here, and the bounds and the author refusal reach the daemon only when `pm-mcp-server` applies them (the lane's Residue; consistent with the epic's earlier note that nothing in `plan-marshall-mcp` imports `de.planmarshall.workflow`).

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-81 --status shipped`
- [x] row `pr` stamped — `#114`
- [x] row `landing` stamped — `landings/PLAN-81.md`
- [x] row `plan_marshall_plan_id` stamped — `rb-81-unit-frontmatter-hardening` (the sender, per the runbook rule)
- [x] epic.md: the two audit findings "owed after PLAN-69" noted as closed by #114; the token defect folded; the opencode watch gets its third landing; the executor watch updated
- [x] the landing carries no candidate lesson for this store. Two items belong to the lane contract of `plan-marshall:runbook-lane` and go to plan-marshall's lessons-routing inbox, not this one: the lane edited the main checkout before the worktree (a repeat of the mistake the contract forbids, with no guard to stop it on opencode), and the cuioss-review-bot silence on the merge head after a merge of `origin/main`
- [x] resume anchor updated; `queue-view.md` regenerated

## Follow-Ups

- **Wiring.** The project unit loader and its bounds are applied by `pm-mcp-server` in `plan-marshall-mcp` once `default-units-and-drift-tests` wires `pm-workflow`; nothing here calls them on a lifecycle path.
- **Evidence the lane called structural.** The head-read clause (the body after the closing line is never lexed) is proved by the code's shape and by a test that the body is never refused; no test can tell it from a whole-source split. Accepted by the lane, accepted here.
- **Requirement texts** were not read by the lane (Residue). The Traceability blocks were rewritten from the spec and the code; the orchestrator's `trace.py` run shows no broken link or undefined id.
- **The opencode watch** keeps its entry: the third landing is clean in substance but carries the same two lane findings (no link-check run, a gate path that needed operator decisions at PLAN-88) and a new one (edits in the wrong checkout, undetected by any guard).

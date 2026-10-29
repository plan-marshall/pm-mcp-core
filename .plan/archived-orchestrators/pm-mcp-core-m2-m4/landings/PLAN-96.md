# Landing Analysis: PLAN-96 — The five packages for the specification ahead of the code

epic: pm-mcp-core-m2-m4
workstream: WS-10
pr: #143 (merged as 9c9d99c68ab09911f1212f6245737ba50087c755)

> Landing record for one shipped plan. Source: inbox message `rb-96-spec-ahead-work-packages-001.md`
> (runbook lane, opencode, 2026-10-22T19:38:14Z), corroborated against git at `9c9d99c` and the PR
> through the `ci` abstraction. The message is a lead; every fact below marked "checked" was read
> from git or the PR, not taken from it.

Standing rules: r1, R01 to R15 and R17 named by the landing. R16 (facts about `pm-runtime`) is not
applicable to a documents-only plan. R18 (SonarCloud, open issues of the PR) is not named and not
explained; the plan changes no Java, so it has nothing to clear, but the landing does not say so.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 — the five package files | shipped-as-specified | Checked: `git show --stat 9c9d99c` adds `change-map.adoc` (32), `endpoint-harness.adoc` (34), `judgment-endpoint.adoc` (32), `judgment-votes.adoc` (33), `own-calls.adoc` (31) below `doc/plans/`. The landing's "162 added lines" matches (193 insertions in all, 31 of them in the index). Verbatim text per the lane's line-by-line comparison (not re-run here); the correction licence was not used. |
| D2 — the five index rows | shipped-as-specified, one deliberate drop | Checked: rows in `doc/plans/README.adoc` after `cleanup`, statuses `open`, `open`, `blocked`, `blocked`, `blocked`, dependency cells as staged, `(no work package)` kept on what has none. `profile-loader` is dropped from the `endpoint-harness` row, as the spec's re-read rule asks: its row and file went with #137 (checked: no `profile-loader.adoc` in `doc/plans/`). The index introduction (`README.adoc:13`) was also corrected in `90a575d`, a change the spec did not list but its Done-when allows (the file is named). |
| D3 — the links check, recorded | shipped-as-specified | The PR body records the run. Not re-run here: the check needs the sibling documentation checkout, which the lane fast-forwarded `8c1ddf4..7bbbce5`; baseline on the stale checkout was `BROKEN LINKS 1` (a link of the sibling roadmap to a file #122 deleted), then 0. |
| D4 — nothing owed outside this repository | shipped-as-specified | Checked: the diff holds six paths, all in this repository. |

Done-when "no file other than the five package files and `doc/plans/README.adoc` changes": met. Checked: the six paths above are the whole diff of #143 (`M README.adoc`, five `A`).

## Metrics and Anomalies

- Tokens: unknown (opencode harness measured none; `inbox landing-check` reports `missing_keys: [total_tokens]`).
- Duration: not supplied.
- Anomalies:
  - The pre-commit gate rewrote five unrelated `pm-runtime` files twice; the lane reverted them both times, so the branch stayed at six documentation paths (checked: the merged diff holds none of them).
  - `/tmp` inode exhaustion (3 of 1,048,576 free) blocked the local gate; the operator authorised cleaning stale test directories under `/tmp`; the lane freed about 508k inodes. `/tmp` is still at 52% inode use and the cause beyond accumulated test artifacts is unknown.
  - The first routed build failed with `executor_absent_from_worktree` (the lane worktree's `.plan/` has no `execute-script.py`), so every build ran in process. The gate then passed twice (6913 tests; 1503 s and 366 s).
  - Two actions outside the repository, each on an operator-selected option and disclosed: the fast-forward of the sibling `plan-marshall-documentation` checkout and the removal of stale `/tmp` directories.
  - The guard was `none on this harness (unenforced)`, as on the other opencode landings.

## Routing and Merge Behavior

- Review: CodeRabbit (required) reviewed `59a8c33` with three findings. Two asked to extend the Done-when of later plans (`change-map`, `own-calls`); the lane answered both as not applicable (the files are the verbatim briefs of those plans) and the reviewer withdrew them. The third (index introduction) was a real defect and was fixed in `90a575d`. Re-triggered once after the last push, refused as rate limited; its incremental run on `90a575d` confirmed the fix. Sourcery (optional) refused: its review budget is spent. Merged under the standing review authorisation, with a qualifying review on `59a8c33`.
- CI/merge: checked, PR #143 reads `merged`, head `chore/96-spec-ahead-work-packages`, merge commit `9c9d99c68ab09911f1212f6245737ba50087c755`, first parent `0540031`. Merged through the merge queue. No rebase conflicts, no re-verify signal.
- Collision: none observed. PLAN-94 and PLAN-95, launched in the same round, did not touch `doc/plans/`.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-96 --status shipped`
- [x] row `pr` stamped — `#143`
- [x] row `landing` stamped — `landings/PLAN-96.md`
- [x] row `plan_marshall_plan_id` stamped — `rb-96-spec-ahead-work-packages` (from the sender)
- [x] epic.md narrative reconciled: queue annotation for PLAN-96, the `total_tokens` recurrence folded into the existing Open Defect, two new Watches
- [x] resume anchor updated in `resume_anchor.md`
- [x] `queue-view.md` regenerated and committed with the row change

## Follow-Ups

- **The Claim Labels of PLAN-97, 98, 99, 100 and 101 are stale by construction.** Each carries an OBSERVED claim "PLAN-96 is staged and writes the package files" (verdict `corroborated` at `4617865`); it is now false because PLAN-96 shipped. The staleness flag reports it (`declared_surface_touched`) and does not block. Each spec's own verify-first clause ("check that PLAN-96 has landed") is satisfied by this landing. Re-grounding belongs to the next `cleanup`, not to this verb: PLAN-97 and PLAN-98 are launchable first, and the launched plan settles its own clause.
- **PLAN-96's own hypothesis (claim 4, dependency edges)** stays `unverifiable` on the spec. The lane reports it confirmed against the dependency package files; this verb checked only the index cells, so it does not stamp `corroborated`.
- **PLAN-103** (`spec-coverage-pass`) depended on PLAN-96 and has no blocker left; it replaces the `(no work package)` notes of `judgment-votes`, `own-calls` and `change-map`.
- **`total_tokens=unknown`**: folded into the standing Open Defect (opencode runbook landing).
- **`executor_absent_from_worktree` and the `/tmp` inode exhaustion** are recorded as Watches. The first is a property of the lane's build routing, not of this repository; it is a candidate for plan-marshall's lessons-routing inbox, which is the operator's to file.

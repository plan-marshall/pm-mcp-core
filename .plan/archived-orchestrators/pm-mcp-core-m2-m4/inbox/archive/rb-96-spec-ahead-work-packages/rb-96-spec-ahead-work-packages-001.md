envelope_version=1
sender_type=plan
sender_id=rb-96-spec-ahead-work-packages
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-22T19:38:14Z

# PLAN-96 landing — the five packages for the specification ahead of the code

```landing-facts
schema=landing-facts/1
plan_id=rb-96-spec-ahead-work-packages
epic=pm-mcp-core-m2-m4
pr=#143
merge_state=merged
cleanup_owed=false
deliverables_total=4
deliverables_done=4
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=0.1.1986
executor_version=0.1.1986
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
```

Standing rules: r1, R01 – R15 and R17 (read before the run; R12 log holder none, R06 developer documentation none, both documents-only).

guard: none on this harness (unenforced).

## 1. Deliverables

**D1 — the five package files: met.** `doc/plans/endpoint-harness.adoc`, `judgment-endpoint.adoc`, `judgment-votes.adoc`, `own-calls.adoc` and `change-map.adoc` carry the spec's *Package Files* text verbatim (162 added lines; compared line by line in the self-review). No link or anchor needed correction, so deliverable 1's correction licence was not used. Commit `3ff741b`.

**D2 — the five index rows: met.** Appended after the `cleanup` row in the spec's order, with the given statuses (`open`, `open`, `blocked`, `blocked`, `blocked`) and dependency cells, keeping `(no work package)` on what has none. `profile-loader` was dropped from the `endpoint-harness` row because its row is gone from the table and `doc/plans/profile-loader.adoc` is deleted at the head of `main` (#137). Commits `59a8c33` and `90a575d` (the latter makes the index's introduction true for the new scope; see Findings).

**D3 — the links check, recorded: met.** `python3 /home/oliver/git/plan-marshall-documentation/.claude/skills/doc-review/scripts/trace.py /home/oliver/git/plan-marshall-documentation/doc .` run from the worktree reports `BROKEN LINKS 0`. Baseline on unchanged `main` with the stale sibling checkout: `BROKEN LINKS 1` — a pre-existing link in the sibling's `doc/roadmap.adoc:242` to `pm-core/doc/developer/core-queue.adoc`, deleted by #122; this plan added no broken link. With the operator's selection the sibling checkout was fast-forwarded `8c1ddf4..7bbbce5` and the check re-run to 0. The run, the sibling SHA and the baseline are recorded in the PR body.

**D4 — nothing owed outside this repository: met.** The roadmap items exist in `plan-marshall-documentation`; their check-off stays with the milestone exit. No pull request there.

| Done-when clause | Result | Order | Test / check | Commit |
|---|---|---|---|---|
| five files exist with the spec's text, every link resolves | met | none | file comparison + link resolution | `3ff741b` |
| index has the five rows with the statuses and dependencies | met | none | row-by-row comparison; head of `main` re-read | `59a8c33`, `90a575d` |
| links check reports `BROKEN LINKS 0`, run recorded in the PR body | met | none | the trace.py command above | recorded in PR #143 |
| no file other than the five files and `doc/plans/README.adoc` changes | met | none | `git diff --name-status origin/main...HEAD` | all commits |

`order: none` for every clause: documents-only, no clause names a failing-at-a-sha test, and the checks are structural (file text, index cells, link resolution). Per-head test runs: `verify -Ppre-commit` (the only gate canonical the resolve result supplies; no `module-tests` or `test-compile` part) at `59a8c33` — success, 6913 tests, 1503 s — and at `90a575d` — success, 6913 tests, 366 s.

## 2. Personas and skills

- Package files and index rows: `persona-documenter`.
- Self-review and PR-comment triage: `persona-code-reviewer`, with its adversarial-refute lenses.
- `skills: none named` by the spec; `skills added:` none (no surface drift — only the six declared paths changed).

## 3. Hypotheses

- The dependency edges are the orchestrator's reading, while the roadmap cuts the judgment endpoint and the judgment vote as one item — **confirmed**. The two package files name the two halves of one roadmap item (`doc/plans/judgment-endpoint.adoc:7`, `doc/plans/judgment-votes.adoc:7`), and the edges match the briefs of `role-sets-and-model-caller.adoc:13-16`, `worker-launch.adoc:15-17`, `role-qualification.adoc:13-18`, `decision-engine.adoc:14-20` and `phase-sub-workflows.adoc:13-19`.
- `profile-loader` is done and its dependency cell is dropped — **confirmed**: no row in `doc/plans/README.adoc` and no `doc/plans/profile-loader.adoc` at `0540031`; `landings/PLAN-19.md` records its removal.

## 4. Findings

Self-review round 1 (six findings, none left open):

1. The pre-commit gate rewrote five unrelated `pm-runtime` files in the worktree (should-fix) — reverted with a reverse-applied patch; the branch stayed exactly six documentation paths; the same happened on the second gate run and was reverted again.
2. `doc/plans/README.adoc:13` said the index serves only Milestones 2 to 4 and client work (should-fix) — fixed in `90a575d`.
3. The links-check run was not yet recorded in a PR body (should-fix) — done at PR creation.
4. The sibling-checkout fast-forward was undisclosed (preference) — disclosed in the PR body and here.
5. The dependency-edge confirmation cited the index only (preference) — the dependency package files were read; evidence above.
6. The spec's Claim Labels are now stale by construction (preference, orchestrator-owned) — left alone; the lane must not edit `.plan/orchestrator/`.

PR comments (three, all resolved): two asked to extend a later plan's Done-when (`change-map` HEAD-side fixture, `own-calls` quorum fixture); both are **not applicable** — the file is the verbatim brief of a later plan and its *What* bullet already carries the requirement; answered and withdrawn by the reviewer. The third (index introduction) was a real defect and was fixed in `90a575d`.

## 5. Reviewers

- **coderabbit** (required, quota-limited): reviewed `59a8c33` at 18:21Z with three findings, all handled; threads resolved. Re-triggered at 18:28Z after the `90a575d` push and refused with "Review rate limited" (next included review in 47 minutes); its incremental run on `90a575d` selected the one changed file and confirmed the fix. Standing review authorization applied: a qualifying review exists on `59a8c33`, every finding is fixed or answered, it was re-triggered once after the last push, and every other gate condition holds on `90a575d`.
- **sourcery** (optional, quota-limited): refused — the 250,000-character review budget is spent, next request in 2 days 4 hours. Optional, so it did not gate.

CI green on `90a575d`; the merge queue merged it as `9c9d99c68ab09911f1212f6245737ba50087c755`.

## 6. Operator questions

1. Asked: the prescribed links check reports `BROKEN LINKS 1` (a pre-existing sibling-repo link), while Done-when 3 needs 0. Options: record as pre-existing and proceed / update the sibling checkout to `origin/main` / stop now as blocked. **Selected: update sibling checkout to `origin/main`.**
2. Asked: the local gate is blocked because `/tmp` has 12G free but only 3 of 1,048,576 inodes left; options: the operator frees `/tmp` / authorize me to clean stale JUnit and OpenRewrite temp directories / redirect the forked JVM temp dir. **Selected: authorize me to clean `/tmp` artifacts.** I removed stale `build-map-seed-tree-*`, `junit-*`, `java-template*`, `base-probe-*`, `executor-mappings-*`, `guard-node-*` and `cuioss-review-bot-artifact-guard-*` directories under `/tmp` (inodes 3 free → 508k free).

Free text: none.

## 7. Rule check

guard: none on this harness (unenforced) — no pre-tool-use hook exists here, so nothing was enforced and no guard-log line can be reported.

Two actions outside the repository were taken, each on a selected operator option and disclosed above: the fast-forward of the sibling `plan-marshall-documentation` checkout and the deletion of stale `/tmp` test artifacts. No other rule was broken; the sub-agent kept the section 1 rules (file tools only, no writes).

## 8. Build server

Setup preflight: `ready` (the project is enrolled). The first routed gate failed with `executor_absent_from_worktree` — the worktree's `.plan/` holds `local/`, `orchestrator/`, `project-architecture/` and `marshal.json` but no `execute-script.py` — so every build ran in-process with that fallback recorded (`mechanism: in_process_fallback`). No killed job. Gate history: `/tmp` inode exhaustion (OpenRewrite temp dir; 156 later `/tmp/junit-*` errors), then the learned bound expired at 776 s mid-build, then `--timeout 3600` succeeded twice (1503 s and 366 s).

## 9. Residue

- The spec's Claim Labels still say the five files do not exist; the orchestrator reconciles them at landing (the lane may not edit `.plan/orchestrator/`).
- `/tmp` is still at 52% inode use; the machine-wide cause was not identified beyond the accumulated test artifacts.

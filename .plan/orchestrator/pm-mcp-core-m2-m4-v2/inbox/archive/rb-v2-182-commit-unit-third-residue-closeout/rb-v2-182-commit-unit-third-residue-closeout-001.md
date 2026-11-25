envelope_version=1
sender_type=plan
sender_id=rb-v2-182-commit-unit-third-residue-closeout
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-24T20:17:09Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-182-commit-unit-third-residue-closeout
epic=pm-mcp-core-m2-m4-v2
pr=#604
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
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=3
review_body_findings_handled=3
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced)

# PLAN-V2-182 — the commit unit's third residue: landing

**Standing rules: r7 (2026-11-10), rules R02, R05, R06, R08, R10, R11, R13, R18, R19, R20 (R21 binds the orchestrator, not this plan; R17 lifted).** Read completely at session start; the spec's First Instruction revisions and rule ids are repeated here as it asks.

Merge commit `4c6fb31a387b6e167bdb80e97a8b5bd0c21a16ed`, PR #604, merged through the merge queue. Base `main` at the merge: `d1ce3bf8` (the branch was cut from `31d97799`; the analysis read `ac16557d`). No cited line was settled by a merge between `ac16557d` and `31d97799`, so no item was dropped.

## 1. Deliverables

| # | Done-when clause | Verdict | Test | Order | Failing run | Commit |
|---|---|---|---|---|---|---|
| 1 | The design record and its timing say what the code and the specification do | met | none (statements only); read-reviewed | `none` | none — no behaviour change | `0954a546`, corrections `49eb0525`, `0259ca60` |
| 2 | Every behaviour change has a failing-first case, **or** the residual is pinned by passing cases and the statements say it | met, second branch | `gluedHostPathStaysAsStated`, `relativeNameEndingInANonPathCharacterIsACode`, `messageNamingAHostPathIsACode`, `relativePathInAMessageIsKept` | `none` | none — the residual is pinned by cases that pass; no production behaviour changed | `d4595dfb`, corrections `49eb0525`, `2dc9c3f3`, `52394552`, `0259ca60` |
| 3 | The statements and pins say what the code does | met | `WorkflowStatusSchemaParityTest` (frame-id `minLength`, empty id, U+2000 both sides), `CommitTrailerSchemaParityTest` (Javadoc), ADR 011 links | `none` | none — statements and pins only | `bc59284a`, corrections `49eb0525`, `2dc9c3f3`, `0259ca60` |

Per-head test runs: `bc59284a` module-tests `package` 12,854 tests (job `13c81279…`) and targeted 101 tests (job `f79e5fd1…`); `49eb0525` `verify` 12,854 (job `4f723462…`) and `verify -Ppre-commit` (job `645f1233…`); `2dc9c3f3` `verify` (job `b3ca7721…`) and `verify -Ppre-commit` (job `beab9f53…`); `52394552` `verify` (job `1cac8491…`) and `verify -Ppre-commit` (job `361606eb…`); `0259ca60` targeted 105 (job `bdc63b88…`), `verify` 12,858 (job `765d9460…`), `verify -Ppre-commit` (job `672d3318…`); merge head `38daae84` `verify` 12,859 (job `e103526e…`) and `verify -Ppre-commit` (job `0771c1d7…`). The open Sonar issues are fixed or dismissed: none were open (0 measured at three heads).

## 2. Personas and skills

- D1 — persona `documenter`; skills `pm-documents:ref-asciidoc`, `pm-dev-java:javadoc`.
- D2 — persona `implementer`; skills `pm-dev-java:java-core`, `java-lombok`, `java-null-safety`, `javadoc`, `junit-core`, `pm-dev-java-cui:cui-testing`.
- D3 — persona `documenter`; skills `pm-documents:ref-asciidoc`, `pm-dev-java:javadoc`, `junit-core`, `pm-dev-java-cui:cui-testing`.
- Self-review rounds and the PR-comment triage — persona `persona-code-reviewer`.
- Skills read at session start (R20 + First Instruction): the six Java skills and `pm-documents:ref-asciidoc`. `skills added: none` — the spec's Expected Surface matched the footprint exactly.

## 3. Hypotheses

- D1 (OBSERVED) — the contradiction inside ADR 088: **confirmed**, `088:14-18` justified the kind by a gate while `088:65-68` and `state-catalogue/05:110` said no verb reads it.
- D1 (OBSERVED) — the timing stated only in the reader's row: **confirmed**; the writer's row `05:86` did not carry it.
- D3 (OBSERVED) — the two `required member` sites give the required digest as the reason: **confirmed**, `status.schema.json:365` and `03-q-gate-and-execute.adoc:264` against `FreshnessReconcile`'s Javadoc and `core-store.adoc:366-368`.
- D2 (LEAD) — the residual leaks under the real Java lookbehind: **confirmed by the pinned cases** (`gluedHostPathStaysAsStated`), which pass; the emulation's reading matches the run.
- D2 (LEAD) — the listed relative names are refused: **confirmed by `relativeNameEndingInANonPathCharacterIsACode`** (`{`, `\`, combining mark, `$`, `+`, `@`, `~`, emoji, `c++/src/main.cpp`).
- D3 (LEAD) — a Turkish default locale breaks `stamp()`: the unanchored `toLowerCase()` was the vector; **fixed** with `Locale.ROOT`. No Turkish-locale run was performed, so the LEAD is **refuted as a live risk but not observed**: the fix removes the dependence on the default locale rather than reproducing the failure.

## 4. Findings

- **Self-review round 1** (persona-code-reviewer, read-only): 4 minor text items — wrong stage (4→5) in ADR 088, README did not name/link ADR 011, `attempts_exhausted` called a "condition", the non-sequitur surviving in `core-store.adoc`. All fixed in `49eb0525`.
- **Self-review round 2**: the four resolved; 3 nits (Javadoc re-wrap stub, duplicated sentence, README bullet shape). Fixed in `2dc9c3f3`.
- **Self-review round 3**: 2 nits resolved, one new nit (dangling "that cap"); round limit reached and the operator chose "Proceed to the PR"; the nit is fixed in `52394552`.
- **CodeRabbit, 3 inline findings on `52394552`** (all minor, all real): `coderabbit inline inline-1 — 0259ca60`, `inline-2 — 0259ca60`, `inline-3 — 0259ca60`. Each thread got a reply naming the commit and was resolved.
- **CI**: green on `52394552`, `0259ca60` and `38daae84`; no CI finding.
- **Sonar**: 0 open new-code issues at `52394552`, `0259ca60` and `38daae84`; gate `OK`, 0 hotspots.

## 5. Reviewers

- **coderabbit** (required, `quota_limited`, `review_complete_after_handling`) — reviewed `52394552` (summary marker `kind=reviewed`, `coveredCommitId=52394552`), 3 inline findings. Every finding handled in `0259ca60`; the review therefore covers the merge head under the done-exemption, and no re-trigger was posted. Merge gate: `required_review_missing` never raised.
- **sourcery** (optional, `quota_limited`) — triggered at `2026-11-24T19:17:48Z`, refused on its own budget (250,000 diff characters per 7 days; next allowed `2026-11-26T02:17Z`). Not a gate; recorded.

## 6. Operator questions

1. Asked: "The lane allows 3 self-review rounds. Round 3 found one wording nit ("that cap" with no antecedent); I fixed it in commit 52394552, and `verify` and `verify -Ppre-commit` are green at that head. No finding is open now, but no round has yet confirmed the fix (rounds are exhausted). How should I proceed?" — options: **Proceed to the PR (Recommended)**, Run a 4th confirmation round, Stop here. Selected: **Proceed to the PR**.
2. No other question was asked. Free text from the operator: none.

The review surfaces were re-read in the step of the question and again before the enqueue; the question carried no merge recommendation.

## 7. Rule check

- **guard: none on this harness (unenforced)** — the resolve result reported `guard: none` (OpenCode has no pre-tool-use hook here), so no guard-log line is claimed and "all kept" is not reported.
- **Spec read**: `spec_source: ledger_worktree`; no `spec_differ` marker. `executor-skew: match` (executor `0.1.2183`, lane `0.1.2183`).
- **Shell/executor**: every script ran through `python3 .plan/execute-script.py …`; every build through the resolved `build` commands; every CI action through `ci`. No `gh`, `glab`, direct build wrapper, `python3 -c`, `git grep`, or `sleep`. File contents were read with the Read/Grep/Glob tools; the only shell file-adjacent calls were `git`, `date` and the build client.
- **Build selection error (recorded, not a rule break)**: the first targeted provider-git run used `-am` with a selector that matched nothing in `pm-core`, and the build client answered `indeterminate` / `test_selector_matched_nothing` — a wrong selection, not a red build. Re-run with a selector that matches in every reactor module: 101 tests green.
- **`.plan/` writes**: only `.plan/temp/rb-v2-182-commit-unit-third-residue-closeout/progress.md`, the landing payload, the allocated `ci` body slots, and the one inbox message. No spec or orchestrator file was edited; the merge brought `origin/main`'s ledger files in, which is the merge's content, not an edit.
- **Git**: staged by name per commit; no `git stash`, `restore`, `checkout --`, `rebase` or force-push; the base branch was brought in by merge. Every commit ends with the resolved trailer.
- Nothing was read outside the repository except the build logs the build client named and the installed skill text the spec's First Instruction names.

## 8. Build server

- Setup preflight: `ready` (registered). `build-routing`: `auto`, `executor_in_worktree: present`.
- Mechanism: `daemon_longpoll` for every build (15 submissions: 5 module-tests — one of them the selector miss — 5 `verify`, 5 `verify -Ppre-commit`). No `in_process_fallback`, no `degraded` result, no launch-fault retry, no `killed` job.
- `runtime_stamp_reason`: absent.

## 9. Residue

- The spec's "recorded, not fixed" tail of carried item 102 (M13/M14 formatter drift and three widened `throws` clauses, M16 files outside the surface, the lane's unverified log ids and counts, `membersEqualSchema` `@Disabled`, the compiled-default table, footprint checks (2) and (3), the end-to-end breach test, Rule 3 on `depends_on_config`, the CLI variant of the commit contract) is unchanged by this plan: no deliverable of the spec names it.
- The mailbox reported `mailbox_state: no_mailbox`, `delivery_state: never_delivered` at all three reads; nothing was delivered, so no item needed a disposition.
- The sourcery quota refusal stands until `2026-11-26T02:17Z`; it is an optional reviewer and blocks nothing.
- `plugin_version` is `unknown` because the resolve result reported it `unknown`; no fact could measure it.

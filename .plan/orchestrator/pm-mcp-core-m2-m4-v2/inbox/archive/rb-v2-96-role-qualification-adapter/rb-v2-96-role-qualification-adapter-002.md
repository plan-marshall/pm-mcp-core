envelope_version=1
sender_type=plan
sender_id=rb-v2-96-role-qualification-adapter
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-18T00:30:20Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-96-role-qualification-adapter
epic=pm-mcp-core-m2-m4-v2
pr=#486
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=4
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2149
steps=setup:done,implement:done,build-gate:done,self-review:budget-exhausted,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=11
review_body_findings_handled=11
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced)

# PLAN-V2-96 — the grading adapter (merged)

Supersedes `rb-v2-96-role-qualification-adapter-001.md`. Pull request **#486**
(https://github.com/plan-marshall/pm-mcp-core/pull/486) merged into `main` as **`70c21e5a`** through the merge queue
(`settle: merged`, merge-group run 44207508098 green). Branch and worktree cleaned up; `main` fast-forwarded to the merge
commit.

Standing rules: **r7**, R02, R06, R08, R11, R12, R14, R15, R17, R18, R19, R20.

## 1. Deliverables

| # | Deliverable | Verdict |
|---|---|---|
| D1 | Verify-first and the outline | **met** |
| D2 | The adapter | **not fully met as written** — the adapter is built and tested, but "`git grep RoleGrader -- '*/src/main/java/*.java'` finds a production caller" cannot be met in this repository: the caller is the assembly in `plan-marshall-mcp`, and `pm-runtime` may depend on `pm-core` and `pm-api` alone (ADR `core-role/007`). The orchestrator confirmed this is an authoring defect of the plan, not a miss of the run |
| D3 | Warm worker and contamination | **met** |
| D4 | The runtime side of `roles qualify` and the planted `CN-<n>` item | **met as a test-tree item** (no product corpus tree exists here) |
| D5 | The `qualification-evidence` package | **met** |

Done-when evidence (every artifact green; no clause names a sha a test must fail at, so no `test-first`/`mutation-after`
order is claimed): the adapter on the worker fixtures (`WorkerGradingHarnessTest`, 14 cases) and the judge
(`WorkerJudgeScorerTest`, 5 cases); the planted item (`PlantedCnItemTest`, 2 cases); the record against the schema
(`QualificationSchemaParityTest.documents` with and without `judge_check`, `QualificationRecordCodecTest.thinMark`,
`failedJudgeCheck`, `judgeCheckAbsent`, `unreadable`); the judge-check guard (`RoleGraderTest.judgeCheckAbsent`,
`judgeCheckAbsentDespiteAStoredPass`, `judgeUnchecked`); ADR `core-role/007`, with 004's partial supersession recorded.

## 2. Personas and skills

`persona-implementer` (production), `persona-module-tester` (tests), `persona-code-reviewer` (the self-review round).
Domain skills: **none named** by the spec; R20's `cui-logging`, `cui-testing`, `cui-http-testing`, `java-core`,
`java-lombok`, `java-null-safety`, `javadoc`, `junit-core` were read (`junit-integration` not: no `*IT` added). Surface: one
undeclared but additive edit, `pm-workflow/doc/developer/workflow-model.adoc`, forced by the D5 behaviour change.

## 3. Hypotheses

- PLAN-V2-31's supervisor calls are fit for the adapter — **confirmed**.
- The planted `CN-<n>` item has a place in `core.role.corpus` — **refuted**: that package holds the three schema files
  only and the repository ships no `test/model/verification/` tree.
- The `thin`/`judge_check` observations — **corroborated** at the head and fixed.

## 4. Findings

Self-review (one round, read-only sub-agent): 16 findings; the ship-blocking ones fixed — the single-form start bug (the
second form threw at the launch contract), the abandoned-warm-worker paths, the judge answer that aborted the run, the
missing `judge_check` schema validation, the builder and Javadoc gaps, the mis-verified mock. Items 1, 6, 8, 9, 11, 13, 14,
15 remain reported open (dead adapter until the assembly lands; planted-item discrimination lives in the judge mock; warm
reuse depends on the worktrees port returning one path per commit; a warm run shares the checkout for `CURRENT` items;
`CORPUS_CONTEXT` mapping; `@EnableGeneratorController`; `JUDGE_UNCHECKED` now unreferenced here; parity test's set-order
wording).

CodeRabbit, first review (`370b5558`): 5 findings (4 inline + 1 nitpick), all handled in `ff8ad407`/`c133365e`, threads
replied and resolved.
CodeRabbit, second review (`c9833175`): 5 inline findings and 1 additional — the fresh-offer rotation, the warm
replacement bookkeeping, the mirrored role-member list, the judge zero-tool guard and the derived optional-member set were
fixed in `f4b8b86c`/`a9f2131f`; the `#484` traceability enumeration was answered with a stated reason (it is that review's
text, not this plan's row). All threads resolved.
Sonar: 5 issues on the first analysis, then 1, then 0; each fixed (`ff8ad407`→`a9f2131f`) — record patterns, the unnamed
pattern, the `@Nullable` reference of the blind prompt, the unused import, the two-call lambda.

## 5. Reviewers

| Reviewer | Role | Outcome | Heads reviewed |
|---|---|---|---|
| coderabbit | required | **reviewed** (twice; `final_review_risk_coverage` marker names `c9833175`, kind `reviewed`) | `370b5558`, `c9833175` |
| sourcery | optional | **refused** (hard quota, 5 days 15 hours) | — |

## 6. Operator questions

1. Executor skew (0.1.2135 vs 0.1.2149): selected "regenerated"; the re-check answered `match`.
2. Sonar `undecidable` before the conflict was known: selected "Wait, then re-read Sonar". The wait produced no analysis;
   the orchestrator's note identified the cause (a conflicting PR takes no `pull_request` run), and the merge of `main`
   fixed it.

## 7. Rule check

Deviations, stated by the run against itself:

- The first D5 edits were written into the **main checkout** and then moved into the worktree; main was restored from its
  index and verified clean.
- One commit used `commit -am` (staging every modified tracked file at once) at `370b5558`.
- The **first merge resolution was wrong**: it restored HEAD's whole `doc/Traceability.adoc`, which dropped `main`'s
  cleanly merged blocks in that file. The second merge surfaced them as a conflict; the file was rebuilt from
  `origin/main` with this plan's rows re-applied. The final file keeps both sides.
- Two routed builds hit the explicit 330 s bound and continued in the split form; later runs raised the bound
  (`--timeout 900`), and one `LspClientTest` run failed on a loaded host and passed on retry (an environment flake, not
  this plan's code).
- The self-review is reported `budget-exhausted` because findings remain open, though no three-round limit was reached.
- Shell file operations (`cp`, `mkdir -p`, `git show > .plan/temp/…`) and `grep`/`find` for locating lines; file contents
  were read with file tools.
- Builds: 26 routed jobs, all `daemon_longpoll`; no in-process fallback, no launch-fault retry, no `killed` job.

Spec read: `spec_source=ledger_worktree`, no `spec_differ`. Executor skew: `match` at `0.1.2149`. Guard: `none` on this
harness (unenforced).

## 8. Build server

Setup preflight **ready**; every build `daemon_longpoll`; three bound expiries (split form continued them) and one
environment-flake retry. At the merged head: `verify` green with 11,028 tests locally, and CI green end to end — Maven
Build, `sonar-build`, SonarCloud (0 issues, gate OK, 92.0% new-code coverage), integration tests, dependency review.
`plugin_version` is `unknown` as `resolve` returned it.

## 9. Residue

- **D2's Done-when gap** — the assembly in `plan-marshall-mcp` is the production caller of `RoleGrader`; the plan counts 4
  of 5 deliverables and the gap is the orchestrator's authoring defect.
- **Links check (R17): 6 broken anchors, all pre-existing on `main`** — `roadmap.adoc` and four requirements files of the
  documentation checkout link to `PM-WATCH-HYP-11`, `PM-WATCH-JOB-1`, `PM-WATCH-JOB-11`, `PM-WATCH-PHASE-104` and
  `store-schemas/13#plan-deletion-lessons`, which `#484` removed from this repository. None is in a file this plan touched;
  the plan's guides and ADR carry no broken link. That is a documentation-repo follow-up, not this plan's.
- Open self-review items (section 4) and `sourcery`'s quota refusal stand.
- Nothing was written under `.plan/orchestrator/`; this closing message is the run's env-modified landing, amended.

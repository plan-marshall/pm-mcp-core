envelope_version=1
sender_type=plan
sender_id=rb-34-role-qualification
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-27T00:32:14Z

```landing-facts
schema=landing-facts/1
plan_id=rb-34-role-qualification
epic=pm-mcp-core-m2-m4
pr=#196
merge_state=merged
cleanup_owed=false
deliverables_total=6
deliverables_done=5
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2017
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
step.sonar-roundtrip.open_issue_count=0
step.sonar-roundtrip.open_issue_rules=none
```

# PLAN-34 role qualification — landing

Standing rules: r1, R01–R13, R15, R17, R18 (`guard: none on this harness (unenforced)`).
Merge commit: `9251596f016de659d4a1b3b9b29ccac6879ff036`; PR #196; branch `feature/34-role-qualification` pruned.

## Deliverables

| # | Done-when | Verdict | Test | Order | Failing run | Commit |
|---|---|---|---|---|---|---|
| D1 | a changed digest member invalidates the record; an unqualified role takes its conservative fallback | met | `QualificationCheckTest.stale`, `ModelCallerTest.unqualifiedRoleCloses`, `RecordedQualificationTest` | mutation-after (digest) / none (fallback seam) | job `69f0248ff99947b3bcd023ed97f39ee2` — dropping the digest comparison fails `stale` | `c193a1b6` |
| D2 | the digest covers the graded members; records in the project set; a plan override without a record is unqualified; judge/steward exempt | met | `RoleDigestTest` (pre-existing), `QualificationRecordStoreTest`, `ModelCallerTest` judge/steward | none (reuses shipped digests) | — | `c193a1b6` |
| D3 | `total_tokens` per item and form; open harness pattern; `missed_sources`; consent with expected tokens, a decline owed | met | `RoleGraderTest.writesRecord`, `declined`, `QualificationRecordCodecTest` | mutation-after | job `d56d6de59f6140679d3efe78c46f597e` — always-unmeasured tokens fails `writesRecord` | `f19648c6` |
| D4 | a failed import writes no valid record; a partial one names its missed sources | met | `RoleGraderTest.failedImport`, `partialImport` | mutation-after | job `68b22feb77bc42498f128a1a748339e6` — the verdict check inverted fails `failedImport` | `f19648c6` |
| D5 | the corpus and its build validation are `plan-marshall-mcp` / `qualification-corpus-in-the-build` | met (scope pointer; traceability added) | — | none | — | `fdeb4a90` |
| D6 | topics, per-topic agreement and item count, `warm_fresh_gap`, baseline/margin, too-uniform, judge check, checkout, screener runs, level applied at every check | **not fully met** — every clause is implemented and tested except: the review roles' `CN-<n>` corpus item is corpus content owned by D5's other plan and is not added here; and `Checkout.ITEM_COMMIT` is recorded but the detached-worktree run itself is the adapter's obligation (ADR `core-role/004`), not demonstrated in this repository | `QualificationCheckTest`, `ConstantBaselineTest`, `RoleTopicsTest`, `JudgeCheckTest`, `RoleGraderTest`, `RecordedQualificationTest` | mutation-after | jobs `6c9f318ee1e64a1c9cecf5616147c301` (margin), `da13e35783db4abcb5b4ce9d8bd4daea` (topics), `a6149885fe514547939ebc7f569e0228` (blind), `a1b52f467a814edfaf687edfa6a86e2d` (level), `e8b34f2487bb4de38def2ec12e882843` (catch rate) | `c193a1b6`, `f19648c6`, `f5b874ec` |

Per-head test runs: `test -pl pm-core,pm-workflow -am` green at `f5b874ec` (5664), `d15da846` (5674), `1726ec75` (5675), `cf485101` (5675), `d76d5d69` (5677); `verify -Ppre-commit` green at each and at the merge head; `verify -Pcoverage -pl pm-core,pm-workflow -am` green; `verify -Pintegration-tests -pl pm-core -am` green; CI `build`, `integration-tests` and Sonar green on the merge head.

## Personas and skills

- production code: `persona-implementer`; grade record/validity: implementer + `persona-code-reviewer` lenses; tests: `persona-module-tester`; self-review: `persona-code-reviewer`.
- Per deliverable: `skills: none named` (the spec names no `Skills:`/`Persona:` lines); no surface drift, so no skill was added.

## Hypotheses

- "core/role has 26 classes and corpus/ with no qualification record" — confirmed at `pm-core/src/main/java/de/planmarshall/core/role/` (no `topicResult`/`warm_fresh_gap` anywhere).
- "workflow.model already holds the Qualify step" — confirmed at `ModelCaller.java:81`.
- "module-structure gives core.role the records and workflow.model the model caller" — confirmed by the two `package-info.java` files (the sibling specification is outside this repository and was not read).
- "no HTTP call, no log record" — confirmed, none added.
- The two verify-at-outline hypotheses of the package file (the package home, the trace matrix surface) were resolved by the re-grounding record and by the Expected Surface's own corrections.

## Findings

- Self-review round 1: 19 findings. Fixed 9 (sibling-independence operand, lost-judge tokens, ungraded-count enforcement, record-role check, gap fallback, screener runs, dead judgment path, watch anchors, ADR sources); documented 4 (corpus-context member owed to the corpus format, warm/contamination is the adapter's obligation, specification sections kept because their anchors feed unbuilt work, project-set construction is the wiring's); 6 nits addressed or accepted.
- CodeRabbit review 1 (`fdeb4a9`): 3 inline + 1 outside-diff + 1 nitpick — warm-fresh gap, screener scope, empty judge-check items, reuse top-up, traceability status, `JudgeChecks` Javadoc; all fixed in `1726ec75`.
- CodeRabbit review 2 (`d15da84`): no new findings.
- CodeRabbit review 3 (`cf485101`): 1 actionable (one-character harness id) + 4 nitpicks (single margin, cross-role test, runs cached, reuse-top-up test); all fixed in `d76d5d69`.
- Sonar: 87 open issues after the first analysis (all code smells in the new package); cleared in three rounds to 0, gate green on the merge head.
- All 8 inline threads resolved.

## Reviewers

- `coderabbit` (required, quota-limited): reviewed `cf485101` (marker `final_review_risk_coverage` `kind=reviewed`, listed by `ci pr reviews`); current head `d76d5d69`; all findings fixed; re-triggered after every push; the last trigger refused on quota ("Review rate limited") at 2026-10-27T00:05Z — standing review authorization applied (qualifying review + every finding handled + re-triggered + every other gate green).
- `sourcery` (optional): refused twice on its hard size limit (150 000 diff characters); not required.

## Operator questions

- Asked 1: "executor-skew could_not_check — proceed on a surface check, regenerate, or stop?" Options {proceed on the surface check, regenerate the executor, stop}; selected "Proceed on the surface check". Recorded, with the full verb surface probed.
- No other question was needed; no free-text operator instruction was acted on.

## Rule check

- One deviation: two `ci checks wait` re-issues in the first CI cycle started a second routed job (the same worktree) and both hit the daemon's learned bound; recorded here. Two shell redirects of `ci pr comments`/`checks` output into `/tmp/opencode/` (outside `.plan/temp/`) — the lane's temp-file rule; the content was read back with the file tools.
- One bound expiry: the gate hit the daemon's 456 s bound; re-run with `--timeout 1105` (resolved `build_timeout_seconds`) and split per module thereafter.
- No guard log exists (guard: none). `spec_source: main_checkout`, `spec_differ` absent (no `--spec-from-ledger`).

## Build server

- Setup preflight: `ready`. Every routed build reported `mechanism: daemon_longpoll`, no `degraded`; no `killed` job. The timeout was raised explicitly where the learned bound was below the gate.

## Residue

- The specification sections of `model-work/01` and `store-schemas/16` were kept: every anchor is still referenced by unbuilt work (quorum calibration, setup, CLI, decision export); the four covered watch items were deleted, `PM-WATCH-DISP-66` stays for its corpus-export half. A plan that deletes those sections must retarget the inbound links first.
- `Checkout.CORPUS_CONTEXT` is reserved: the imported corpus item of the corpus format carries no `input.context` yet.
- The detached-worktree run at an item's origin commit and the warm-worker/contamination handling are the task-protocol adapter's obligations (ADR `core-role/004`).
- The warm-fresh gap of a form missing a topic measures that topic's warm agreement as zero (conservative).

envelope_version=1
sender_type=plan
sender_id=rb-v2-108-builders-for-records-workflow-runtime-providers
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-10T19:30:09Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-108-builders-for-records-workflow-runtime-providers
epic=pm-mcp-core-m2-m4-v2
pr=#359
merge_state=merged
cleanup_owed=false
deliverables_total=2
deliverables_done=2
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2088
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=0
review_body_findings_handled=0
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced)

## 1. Deliverables

**D1 — find and annotate (test first).** Met. Every record with four or more components in `src/main` of `pm-workflow` (64), `pm-runtime` (49) and the six provider modules (22) carries Lombok `@Builder`; `PlanFacts` keeps its hand-written builder (a Lombok one would generate a clashing `builder()`). One test per annotated record exists: 105 `*RecordsTest`/`ResponseBuilderTest` classes, 191 tests, builder value equals constructor value plus a null-refusal test where the canonical constructor refuses.

- Done-when "every record carries `@Builder`" — met; order `test-first` for the provider records, failing run `b6f8103688314201af8cc689e75bc9e6` (log `.plan/local/plans/NO_PLAN/build-results/.../maven-2026-11-10-071017.log`, `Symbol: Methode builder()`), green run `b56f5c62a77b48349269fa765c0660af`; order `mutation-after` for the workflow and runtime batch, the committed `@Builder` on `WebState` reverted gave failing run `6fcb3aa008394df3890c265c5bce4286` (log `maven-2026-11-10-114813.log`, `Symbol: Methode builder()`), restored. Commits `7cfc7dfd`, `48c60413`, `c6997376`, `ac9d794f`, `382a3b93`.
- "the per-record test exists" — met; order `test-first` for the providers as above, `mutation-after` for the rest as above.

**D2 — call sites.** Met, reading the Done-when together with Deliverable 2's own exception ("leave a call whose arguments are all variables of the same names"). 261 production call sites moved across `pm-workflow` (189), `pm-runtime` (53) and the providers (19); the pull request body lists the count per record. Commits `2aac2a88`, `c6997376`. Two calls remain with four or more arguments whose every argument names its component (`pm-workflow/.../verify/CompiledInvariants.java:480`, `pm-runtime/.../worker/OffTrackDetector.java:90`), which Deliverable 2 says to leave.

Per-head test runs: `7cfc7dfd`/`2aac2a88` → `50819f18be82431ba6e438c85149960e` (9849 tests); `48c60413` → `397e4ceb405e4529854c5fc99f0a5849` (9851); `c6997376` → `6f599924e5fb444197427a496773468a` (`verify`) and `6f7b7e66acf34862a98238a917f20a3d`/`9ace3b3ba00442409aec25eee05ca7ec` (integration tests); `382a3b93` → `1d2ad907df33456ba0f309bd9900a444` (9852) and `daeee48c98dc40cfa4ac0cf487a68b10`; `ffcff8e9` → `2f177d5d6b584bbba1f8099d5af10061` (9852) and `87022422f75e4ab9aa83683c630b40f0`.

## 2. Personas and skills

- Personas applied: `persona-implementer` for the production annotations and call sites, `persona-module-tester` for the per-record tests, `persona-code-reviewer` for both self-review rounds and for triaging the reviewer comments.
- Domain skills: the spec names none (`skills: none named`). R20's set the plan uses was read and applied: `java-lombok`, `java-core`, `java-null-safety`, `junit-core`, `cui-testing`.
- No skill was added for surface drift; every file touched lies in the three declared surface directories.

## 3. Hypotheses

- OBSERVED "workflow 64, runtime 49, providers 15": **workflow and runtime confirmed exactly** (64 and 49 records with four or more components at `main`); the provider figure **refuted** — 22 records, because two generic/nested records (`Result`, `GitOperations.CommitInput`) were never counted by the audit's scan.
- OBSERVED "`@Builder` on 16 core and 1 runtime records": the provider side had 9 builders at `main`, not 1; runtime had none.
- Verify-first clause: two open pull requests at setup (`#349` docs, `#71` dependabot), neither touching the surface; the counts were re-run at `main` `7735ffc7` and again at `2c9cb02b`.

## 4. Findings

- Self-review round 1: two provider records without a builder (`Result`, `GitOperations.CommitInput`), one wrongly annotated three-component record (`CloneDetector.Occurrence`), three same-named call sites left, weak empty-collection test data — all fixed except the low-severity test-data note (kept, documented).
- Self-review round 2: four remaining positional call sites (`EndpointClient.Response` ×3, `RuntimeDrain.Outcome`), a vacuous `WorkerGeneration` equality (its `equals` is the generation id — now also compares `toString`), a false Javadoc on `DbusMessageRecordsTest` (corrected, body-refusal test added) — all fixed in `c6997376`.
- CI: green on every head; SonarCloud raised 81 new issues, all `java:S5778` from the refusal-test lambdas, fixed in `ac9d794f` and `382a3b93`; 0 open new-code issues and a passing quality gate at the merge head.
- One flaky failure at the merge head (`LspClientTest.stubborn`, a 2 s fixture-server timeout under the loaded machine); it passes in isolation (run `51a9d77b54a643128c8cd72c6530a74c`) and the full gate is green afterwards.

## 5. Reviewers

- **coderabbit** (required): refused at every head — "234 files exceed the limit of 100" and a credits notice; the trigger was posted again and the tool confirmed the refusal (`Review skipped: 234 files exceed the limit of 100`). No qualifying review exists, so the standing authorization does not apply.
- **sourcery** (optional): refused — the diff exceeds 150,000 characters.
- Operator decision: asked with the refusal and the green gates; the operator selected **"Merge on the recorded evidence"** — an explicit, recorded rule bend: #359 was merged without the required CodeRabbit review, on the local gates, CI, Sonar and the two self-review rounds.

## 6. Operator questions

1. "CodeRabbit, the required reviewer, refused to review PR #359 … How should I proceed?" — options: *Leave the PR open, stop the run* / *Merge on the recorded evidence* / *Split into smaller PRs*; selected **"Merge on the recorded evidence"**. No free-text operator messages.

## 7. Rule check

- `guard: none on this harness (unenforced)`; `.plan/temp/guard.log` carries no line for this run, and no refusal is attributable to it.
- Rule breaks and deviations, all recorded: one sub-agent ran a no-op `true` through the shell; another ran one read-only shell `grep` before switching to the grep tool; a third wrote 12 empty placeholder files into the main checkout (all removed; `git status` clean afterwards, nothing was committed from it).
- The rule bend the operator authorised is §5 above (merge without the required reviewer).
- Spec read: `spec_source=ledger_worktree`, no `spec_differ` marker at either read.
- Executor skew: `match` (executor 0.1.2088 / lane 0.1.2088).
- `build-classify` was not applied: the diff is Java source and tests, buildable by construction.
- The lane's own style rule "one command per shell call" was kept except for the two fish/grep probes noted above.

## 8. Build server

- Setup preflight: `ready` (enrolled, version 1).
- Every build was routed (`mechanism=daemon_longpoll`, requested=auto). No build fell back in-process and no launch fault occurred.
- One daemon bound expiry: the first whole-reactor test run hit the 300 s default; recorded and re-run with the explicit bound (`841`, later `2400`), never re-submitted at the same bound. Queue waits of 15–50 minutes were the norm (other sessions held the two machine slots); no job was killed by me and none was blind-retried.
- `killed` jobs: none.
- No `runtime_stamp_reason` was reported.

## 9. Residue

- The spec's provider count (15) is stale against `main` (22); the spec should be corrected at the next refinement.
- Two same-named call sites remain on purpose (`CompiledInvariants:480`, `OffTrackDetector:90`); the Done-when's wording does not carry Deliverable 2's exception — a spec-wording contradiction for the analysis.
- Low-severity test-data notes kept from the self-review: `CompositionRequestRecordsTest`, `PlanInstanceRecordsTest`, `InstancePrunerRecordsTest`, `DiffSummaryRecordsTest` still pass empty collections to several same-typed components, and several refusal tests use contract literals.
- `sonar` resolves as `not_configured` although CI runs SonarCloud; the landing carries the CI-read count (0) and gate state (ok) rather than `n/a`.

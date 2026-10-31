# Landing Analysis: PLAN-V2-84 — Every production package `@NullMarked`, absence as `Optional`

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #255, #256, #257, #258, #261 (five pull requests; the landing message names #261 as its `pr`). Merge commits `9ef53d41`, `45f16f3c`, `b03cefcd`, `37fb864f`, `efb1250d`.

> Landing record for one shipped plan. Lives at `landings/PLAN-V2-84.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source: inbox message `rb-v2-84-null-safety-nullmarked-001.md` (runbook lane, harness opencode, model `deepseek-v4.1-flash/high`, effort high, executor 0.1.2041, standing rules r4, rules R02, R06, R08, R11, R13, R17, R18, R19, R20). Corroborated against `origin/main` (merge diffs, `git grep`), the `ci`/`gh` PR state, the CI runs of `main` and the public SonarCloud API. The lane's progress file and guard log were not read (`guard: none on this harness`).

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 — `@NullMarked` on every production package, with a guard test | shipped-as-specified | `git grep @NullMarked origin/main -- '*package-info.java'`: **67** files, and 67 production `package-info.java` exist; #255 (`9ef53d41`) |
| D2 — the 79 `return null;` sites become `Optional` | shipped-as-specified | `git grep 'return null;'` on `origin/main`: **4** sites, exactly the four the lane lists with reasons (`RefusingFS.java:52`, `MacSecurity.java:192` and `:215`, `PeriodicSweep.java:110`); #256 (`45f16f3c`) |
| D3 — nullable states annotated or restructured | shipped-as-specified, with a residue | #257 (`b03cefcd`), #258 (`37fb864f`), #261 (`efb1250d`); the lane counts 1,004 annotated sites. Not re-counted here. **Residue that matters:** Sonar still finds 44 `java:S2637` ("marked `@NullMarked` but null is returned") sites on `main`, so null is returned by methods other than the 79 literal `return null;` ones (ternaries, `orElse(null)` and the like), which the lane lists as residue |
| Added, unplanned | shipped-modified | the repository-wide Sonar ignore of `java:S4449` (root `pom.xml`, ADR 011, the operator's answer 3 below) |

Surface delta (union of the five merge diffs): **295 files** — 264 main sources and 20 tests, all inside the spec's directory-level declaration of `core`, `workflow`, `runtime` and the providers; and 11 outside it: root `pom.xml` (the S4449 ignore), `doc/adr/011-ignore-s4449-for-jspecify-null-safety.adoc` and its index row, and eight developer guides (`core-profile`, `core-queue`, `core-role`, `provider-http`, `runtime-endpoint`, `runtime-worker`, `workflow-model`, `workflow-setup`). The guides and the ADR are R19 bookkeeping; **`pom.xml` is a real undeclared change** and it changes what the analysis reports.

## Metrics and Anomalies

- Tokens: `unknown`. Duration: not measured.
- **The plan was split into five pull requests at the operator's direction (2026-10-31)** because CodeRabbit refuses a PR of more than 100 files ("Too many files"); the first, single PR #254 had 285 files and never merged. The split is not a defect of the plan, it is a sizing signal for every later plan of the family (see Follow-Ups).
- Self-review ended `budget-exhausted` after three rounds; every finding was fixed, round 3 was post-merge, and the fixes of #261 are not re-reviewed.
- The lane merged #257 and #258 after the operator's decisions on a red Sonar gate (answers 2 and 3).

## Routing and Merge Behavior

- Review: CodeRabbit commented on #255, #256, #257 and #261 (findings fixed) and reviewed #258 clean; Sourcery refused on quota throughout (optional).
- CI/merge: all five PRs read `MERGED` with the merge commits above. **The push builds of `main` have failed since the first of them:** `Maven Build` at `9ef53d41`, `45f16f3c`, `b03cefcd` and `37fb864f` all fail in `sonar-build` (step "Build and analyze"), the only failing job each time; the build at `efb1250d` and the later docs commit were still running when read.
- Sonar (PR): the landing reports `open_issue_count=0`, `open_issue_rules=none`. That is a measure of the pull request's new code only. **Measured on `main` at the analysis of `efb1250d` (2026-10-31 16:22 UTC): 329 open issues, 157 of type BUG** — `java:S2583` 113 ("change this condition so that it does not always evaluate to false/true"), `java:S2637` 44 — and 172 code smells, of which `java:S2589` 156. Before PLAN-V2-84 (analysis at `03f182c9`) `main` read 13 open issues and one bug. The gate of `branch=main` reads **ERROR**: `new_reliability_rating` actual **3** (threshold 1); every other condition is `OK`. The landing's `0` and the state of `main` are not in conflict as measurements (different scopes) but the landing's reading of "clean" is wrong for the epic: the plan left `main` red.
- **Cause (established from the log of when the gate first failed, not inferred):** the gate was green at `03f182c9` and `f8abbfc5` and failed at `9ef53d41`, the merge of D1, which changes only `package-info.java` files. Marking a package `@NullMarked` makes Sonar's nullness analysis treat every non-annotated parameter and value of the package as non-null, so the existing defensive `if (x == null)` checks become "always false" bugs; the pull request gate did not see them because it scores only the lines a PR changes.
- **Consequence beyond the gate:** `deploy-snapshot` is skipped when the build fails. It ran (`deploy-snapshot / deploy` success) at `03f182c9` and `f8abbfc5`, and has been skipped at every `main` push since `9ef53d41`: the `0.1.0-SNAPSHOT` that `plan-marshall-mcp` builds against has not been deployed since the first merge of this plan. The lane searched `plan-marshall-mcp` for callers of the changed public signatures and found none, so nothing downstream breaks, but nothing downstream sees these changes either.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-V2-84 --status shipped`
- [x] row `pr` stamped — `--set-row PLAN-V2-84 --field pr --value "255, 256, 257, 258, 261"`
- [x] row `landing` stamped — `--field landing --value landings/PLAN-V2-84.md`
- [x] row `plan_marshall_plan_id` stamped — `--field plan_marshall_plan_id --value v2-84-null-safety-nullmarked`
- [x] epic.md narrative reconciled: recurrence on the open defect for `unknown` facts; new Open Defects for the red `main` (with the SNAPSHOT deploy) and for the residue; Watches for the S4449 ignore and the CodeRabbit 100-file limit
- [x] message archived after the disposition
- [x] resume anchor updated; `queue-view.md` regenerated and committed with the row change
- [ ] **red `main` (open, the operator's decision):** see the Open Defect in `epic.md`; no plan is staged for it yet

## Follow-Ups

- **Red `main` caused by this plan** — an Open Defect with three ways to settle it (fix the sites, ignore the rules as ADR 011 did for `java:S4449`, or sequence the fix behind PLAN-V2-97 to 99, which convert `Optional` parameters, fields and record components to `@Nullable` and so change the same population). Not staged until the operator picks.
- **Sizing:** later plans that touch many files (PLAN-V2-85 to 93, 97 to 99, 100, 101 to 105, 108) will meet the same CodeRabbit limit of 100 files per pull request; their specs are not edited here, the Watch asks the lane to split by module or directory before opening a pull request.
- **ADR 011** (S4449 ignored for every Java source) is recorded as a Watch, because an ignore of a rule for the whole repository is a standing weakening of the analysis.

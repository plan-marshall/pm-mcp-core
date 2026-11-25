envelope_version=1
sender_type=plan
sender_id=rb-v2-126-test-conformance-runtime-and-providers
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-25T15:35:11Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-126-test-conformance-runtime-and-providers
epic=pm-mcp-core-m2-m4-v2
pr=#612
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=1
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2184
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=0
review_body_findings_handled=0
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

## Deliverables

The operator re-cut this plan on 2026-11-25 to Deliverable 1 only; D2 is blocked, D3–D5 were not started.

- **D1 — met as written.** PR #612 merged at merge commit `1ae69d3f78150cc7721979ac32291465c655590f`. All four sub-items are in: (a) the boundaries (`JobRecoveryStepTest` `BOOT.minusSeconds(1)` ×2; `MachineConfigurationStoreTest` `revision - 1`; `HeadlessBlockTest`/`HarnessVerifierTest` `2.0.0` and `0.9.9`, plus `HeadlessBlockTest` pinning the inclusive `1.0.0`; `OutcomeDerivationTest` `declaredCode + 1`), (b) the orders (`RuntimeDrainTest` name order, `GitHubCiProviderTest`/`GitLabCiProviderTest` descending paths), (c) the provider cap (`changed_files == listed + 1`), (d) 32 restored diagnostics across 12 classes, carried as `assertAll` headings. The Done-when's evidence half is met in the PR body: one failing mutation run per item (order `mutation-after`) and a quoted failure message per restored diagnostic site.
  - After self-review, `HeadlessBlockTest` also pins the inclusive lower bound `1.0.0` (a mutation that changes `>=` to `>` fails it).
  - The one site not separately mutated is the `removes()` `removed.detail()` heading (`JGitOperationsWorktreeTest.java:240`): it follows five refusal assertions in an earlier `assertAll`, so a blanket `worktreeRemove` mutation stops at that block; the identical `assertAll(removed.detail(), …)` pattern is proven by `prunable` (`scratch worktree-remove diagnostic`).
- **D2 — blocked.** It depends on the `pm-core:tests` word generator that PLAN-V2-125 adds; that plan is not on `main` (`CoreGenerators` has no word generator). The spec's own re-ground record marks the dependency unmet.
- **D3, D4, D5 — not started.** The decisions already taken are kept for the follow-up rows: D3(a) a shared routing helper in `pm-provider-http:tests` (operator, overriding the "no helper" default); D5(e) the package-private seam in `StalledScopeDetector` is allowed; the `CLAUDE.md` and `.opencode/rules/plan-marshall-standing-rules.md` edits are allowed.

## Personas and skills

- Persona: `implementer` (the deliverable's `Persona:` line).
- Skills loaded: `junit-core`, `cui-testing`; the deliverable's declared set is `java-core`, `java-lombok`, `java-null-safety`, `javadoc`, `junit-core`, `cui-testing`.
- The spec declares those six; no skill was added for surface drift (the D1 files are all on the Expected Surface).

## Hypotheses

- before-boot edge is `BOOT.minusSeconds(1)` — confirmed (`JobRecoveryStep.java:310`).
- supported range `>=1.0.0 <2.0.0` — confirmed (`HeadlessBlock.supports`, `:200`; `SupportedVersions.java:119`).
- stale-revision edge is `revision - 1` — confirmed (`MachineConfigurationStore.java:231`).
- adjacent undeclared exit code — confirmed (`OutcomeDerivation.java:123`).
- provider cap edge is `listed + 1` — confirmed (`GitHubCiProvider.java:273`).
- `RuntimeDrain` iterates registration order — confirmed (`RuntimeDrain.java:173,194,211`).

## Findings

- Self-review round 1 (`persona-code-reviewer`): the inclusive lower bound was not pinned (F1), and two class Javadocs still called the out-of-range versions generated; both fixed before the PR. No pre-existing assertion was removed or relaxed; no changed file was outside the Expected Surface. It noted the `ColdStartComparisonTest` `describe(0, 300)` heading as the parent's illustrative shape (kept) and the duplicated `unsortedPaths()` as D2's to fold (D2 blocked).
- CodeRabbit: no actionable comments on PR #611 (head `8070bb56`) or PR #612 (head `69a1e10c`, carried to `6fc5abc0`).
- No PR review threads; `review-gate` = `all_handled`, 0 unresolved, 0 body findings.

## Reviewers

- `coderabbit` (required): reviewed PR #611 at `8070bb56` (`kind=reviewed`, no actionable comments) and PR #612 at `69a1e10c` (`kind=reviewed`, no actionable comments); the final head `6fc5abc0` passes `review-gate` (merge_allowed). One `@coderabbitai full review` was quota-refused at `10:38:59Z`; the PR was re-created on the operator's "retry now" instruction, which produced a fresh `kind=reviewed` marker.
- `sourcery` (optional): hard-quota refusal (about 18 hours remaining); optional, so no gate.

## Operator questions

1. D2 dependency unmet — options {do D1/D3/D4/D5, add generator here, stop}; selected "Do D1, D3, D4, D5; D2 blocked".
2. D5(e) production seam — options {allow package-private seam, test-level only, drop 5(e)}; selected "Allow the package-private seam".
3. D3(a) shared routing helper — options {no helper (default), shared helper in pm-provider-http:tests}; selected "Shared helper in pm-provider-http:tests".
4. `CLAUDE.md` / standing-rules edits — options {allow both, docs/ADR only}; selected "Allow both edits".
5. PR structure — options {four PRs per spec, one PR}; selected "Four PRs per spec".
6. Base red blocks the merge — options {wait, authorize a schema fix, stop}; selected "main should be fixed by now. verify and continue".
Free-text operator message: "retry now (re-create the PR)" — closed #611 and re-created it as #612 to obtain a `kind=reviewed` marker.

## Rule check

guard: none on this harness (unenforced). No guard refusals. Spec read: `spec_source: ledger_worktree`, no `spec_differ` marker. `executor-skew`: match (executor 0.1.2183, lane 0.1.2183), recorded, not asked about. Standing rules read at r7 (the spec's First Instruction names r6; the file at the head is r7 — the R05/R12 amendments only).

Two build rewrites occurred outside the D1 surface (the pre-commit gate rewrote `TaskQueueSchemaParityTest`, `ServedModelsTest`, `ServedModelWatchTest`, all from the merged #594): import order/formatting only, committed as `6fc5abc0`, and the gate re-ran green with no further rewrite.

The first PR (#611) was closed unmerged on the operator's instruction; #612 is the landing. CI on #612 briefly failed in `pm-core` because `origin/main` `3aea14c2` itself failed `ShippedProfilesTest` on the external `pm-api` SNAPSHOT (`usage.model`); I reproduced it on the untouched main checkout (log `ddfba5b039ca41cca0a3d65b9f2e506f`) and it was fixed on `main` by #594 (`e451ed04`). It was not caused by D1.

## Build server

Setup preflight: `ready`. Every build was routed to the daemon (`daemon_longpoll`). No build fell back or retried; no `killed` job. The base-sync merge rebuilt and re-gated at `91a034c9` and `6fc5abc0`.

## Residue

- The `removes()` diagnostic site (`JGitOperationsWorktreeTest.java:240`) is pattern-proven, not separately mutated (see Deliverables).
- D2 stays blocked on PLAN-V2-125 (the `pm-core:tests` word generator).
- D3, D4, D5 are unstarted; the follow-up rows keep the decisions in the Deliverables section above.
- The progress file `.plan/temp/rb-v2-126-test-conformance-runtime-and-providers/progress.md` is left in place for the follow-up rows.

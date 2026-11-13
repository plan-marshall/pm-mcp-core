# Landing Analysis: PLAN-V2-40 — the envelope packing: cost sizing, the Next-Fit packer and the envelope budget

epic: pm-mcp-core-m2-m4-v2
workstream: WS-04
pr: #402 (merge `d503d8e3`), `merged` through the merge queue

> Landing record for one shipped plan, written by the `analyze` inbox drain of 2026-11-13 from the messages
> `plan-v2-40-envelope-packing-001.md` to `-006.md` of the plan lifecycle (harness claude, model claude-sonnet-5-5/medium,
> executor 0.1.2114). The landing message is a lead. The three layers were read by one read-only leaf from git at
> `origin/main` `d503d8e3` (no build, no test run); the orchestrator re-read the points marked "re-read"; the provider
> figures were re-read from the SonarCloud public API and from `gh pr view 402`.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 package file `doc/plans/envelope-packing.adoc` and its index row | met, one wrong owner | the file follows `own-calls.adoc`, the row is `open`, depends on nothing, Milestone 9 (`doc/plans/README.adoc:342-346`); **the Left-out text names PLAN-V2-63 as the owner of "the role view that shows a budget above the threshold" (re-read, `envelope-packing.adoc:21`), but PLAN-V2-63 leaves the role view out** (`PLAN-V2-63` Dependencies: "the role view in plan-marshall-mcp/the assembly (untouched)"). |
| D2 `TaskCostEstimator`, `SizeTokenTable` | met | score `10*steps + W + 3*skills + 4*files` in `long` (`TaskCostEstimator.java:110-116`), weights 12/12/4/8 (`:36-39,147-153`), bands 30/60/150/300/700 (`:41-45,125-145`), table 5K to 520K with basis `uncalibrated` (`SizeTokenTable.java:32-38`), the table is an argument. |
| D3 `EnvelopePacker` | met | Next-Fit by head module and role with `<=` (`EnvelopePacker.java:137-139,161-163`), profile not a criterion, merge preceding first, then following, else alone (`:194-206`), sealing `envelope-N`, sorted distinct skills, summed cost (`:230-244`), `belowMinimum()` (`:118-120`). `CostSize` is top-level, not nested as the spec said (recorded in ADR 001). |
| D4 `EnvelopeBudget` | met | operator, role, profile, compiled 400 000 (`EnvelopeBudget.java:94-103`); the cap lowers PROFILE and COMPILED only (`:115-123`); `aboveThreshold` for a kept value above the threshold (`:106-113`). Inputs are `@Nullable Long`, not `OptionalInt` (the repository rule: `Optional` is a return type only; recorded in ADR 003). |
| D5 documentation | met, one literal falsehood | guide, ADRs 001 to 003, condensed `model-work/06` with its anchors, `Traceability` PM-WF-14, both README entries, `package-info` `@NullMarked`. **ADR 001 line 23 says "The algorithm of the block is kept statement for statement" (re-read), and the merge loop was restructured into `mergeTarget`/`mergeInto` after the bot round**; the leaf traced every branch and found the behaviour unchanged, so the sentence is false in its letter only (line 36 of the same ADR, "differ in shape, not behaviour", is right). |

"Not in the declared surface": none beyond bookkeeping (`doc/adr/README.adoc`, `pm-workflow/README.adoc`, `Traceability.adoc` are R19 bookkeeping).

Not shown by reading: the four verify runs of the Done-when, the coverage figure and the links check (the narrative says green; the merge gate reported `findings_gate: clean`, `participation_gate: complete`).

## Three-Layer Analysis

Read at `d503d8e3` (head of `origin/main`) by one read-only leaf (`execution-context-level-3`, 40 tool uses); the orchestrator re-read the ADR line, the package-file line, the packer's loop and the budget-boundary test at their `file:line`.

### Layer 1 — Specified

The block, the prose and the plan agree: the old item 6 ("every merge exceeds the budget") holds because Next-Fit closes only between compatible groups on the budget, and it stays true across cascades. Gaps, all minor:

- **The specification is silent on edge inputs**: a budget of zero or below, a negative `predictedCostTokens`, null fields, and a check that the input is in dependency order. The operator-over-role precedence is only implied by "the operator may override" (old item 4); `aboveThreshold` and "role and operator values are taken as set" are the plan's own and are now normative in the condensed item 4 without an original source.
- **`PlannedTaskSummary.costSize` and `dependsOnTaskIds` are carried and never read by the packer**, yet `costSize` is required non-null (`EnvelopePacker.java:74`).
- **The condensation moved the algorithm's authority into code.** D5 asked for "condense and point"; the Java block is gone from the specification and the condensed text says the former block is the implementation. The merge semantics (compatibility judged on the group's head, the index kept after a merge so a merged group is re-examined, a lone group never revisited) now live only in private-method Javadoc (`EnvelopePacker.java:165-173`); the guide §3 does not mention the cascade. The sentence "a merge only enlarges envelopes" was dropped from item 6.

### Layer 2 — Implemented

Conformance (read): `@Builder` on every record of four or more components (`TaskSignals`, `Resolution`, `PlannedTaskSummary`, `PackedEnvelope`), `@UtilityClass` on the three static classes, `@NullMarked`, no generic catch, `Generators` for data, no AAA comments. Deviations (minor):

- four `assertAll` headings are 19 characters, one short of the 20 to 60 rule (by hand count, no tool): "The merged envelope" (`EnvelopePackerTest.java:255,272`), "The shared envelope" (`:159`), "The resolved budget" (`EnvelopeBudgetTest.java:57`);
- `Set.copyOf(skillUris)` and `List.copyOf(dependsOnTaskIds)` (`EnvelopePacker.java:75-76`) throw a message-less NPE on null while the other fields use `requireNonNull` with a name;
- the code adds `IllegalArgumentException` refusals for non-positive budget inputs, negative counts and negative table values the spec did not name (sound; they are in the guide for the estimator, the table and the budget, **not for `packEnvelopes`**).

The plan's report of two differences against plan-marshall's `_tasks_cost.py` is **confirmed**: it counts distinct `steps[].target` (`:117,205`) where the specification counts distinct `target_files`, and it raises `ValueError` on a negative count (`:125-131`); weights, bands and table are identical. The Java `TaskSignals` takes an already counted `files`, so the specification's meaning is the contract; it refuses negatives like the sibling and refuses a null profile with an NPE where the sibling defaults to weight 8. The guide reports both (`workflow-envelope.adoc:46`); the specification text still says "re-verified".

### Layer 3 — Correct

No behavioural bug found by reading. Merge cascades, a merge into the following group when the preceding one is incompatible, and the cap when the threshold lies under a role or operator value (kept and flagged, as specified) are right; arithmetic is `long` throughout; returned collections are copies; sealing sorts, so the output is deterministic. What the reading does show:

- **`packEnvelopes` accepts a budget of zero or below and a negative `predictedCostTokens`** (guide "Refusals", invariant 4, covers only the estimator, the table and the budget); a negative cost makes `belowMinimum()` true and breaks the budget logic. Guide invariant 3 ("no task precedes one of its dependencies") holds only if the caller orders the input; the packer does not check it. `SizeTokenTable` accepts a non-monotone table (XS above S).
- **The Done-when says "every Next-Fit closure, every merge and every precedence step is pinned by a named test", and the boundaries are not:**
  - `closesOnTheBudget` (`EnvelopePackerTest.java:168`, re-read) has `full = task(budget)` as the first task, which the packer accepts whatever the comparison says; no test makes two tasks sum to exactly the budget, so `<=` to `<` survives;
  - `keepsTheProfileValueUpToTheThreshold` (`EnvelopeBudgetTest.java:126`), `keepsTheCompiledFallback` (`:148`) and `doesNotFlagAValueUpToTheThreshold` (`:184`) draw from `atMost` or `longs(0, ...)`, so the equality case is hit with probability about 1e-6 and the mutations `>=` and `<=` survive;
  - not tested: a merge into the following group while an incompatible preceding group exists, a merge cascade (the generators keep the budget at 100K or more, so it is unreachable), a budget below `MIN_JOB_TOKENS`, determinism ("same input, same output"), a generated contiguity-and-order property (`keepTheTasksContiguousAndInOrder` is hand-built); `mergesIntoThePreceding` carries a message saying "exceeds the budget" and asserts equality;
  - the monotonicity test (`neverLowerTheSizeWhenACountIsRaised`, `TaskCostEstimatorTest.java:152`) covers three counts, not four signals, on one random draw with a fixed profile.
  - Pinned: all band edges (`mapEachBandEdge`: 0, 29/30, 59/60, 149/150, 299/300, 699/700), every precedence step and both fallback caps, the negative and zero refusals.

## Conformance to the Project's Own Rules

See Layer 2. Review: CodeRabbit raised two findings and one issue comment on the first push; the loop-back fixed two (`4d163b`, `3e61a3`) and accepted one (`a36ad6`); the plan's functional review closed clean (5 deliverables, 16 criteria) and its code review with 11 advisories, none blocking. The two CodeRabbit findings are test-quality gaps the plan's own reviews missed (a hard-coded list mirroring production, a zero boundary the generators almost never hit) and are the same class as the boundary gaps above, which the bot did not find.

## Unverified leads

- Build, test, coverage and the links check (not run); the requirement anchors `PM-WF-5` and `PM-WF-14` in the documentation repository.
- `doc/adr/008` still lists `workflow.envelope` as a package-info-only package; historical, left.
- The squash-message setting of the merge queue is not recorded in the PR or the repository settings (the landing message says so; same open question as 2026-11-11).

## Metrics and Anomalies

`total_tokens` 20 695 505 (includes cache reads and sub-agent transcripts after `enrich`; the retrospective's pre-enrich figure is 3.72 M, a different population), `total_wall_seconds` 45 524 (about 12.6 h), harness claude, claude-sonnet-5-5/medium, plugin and executor 0.1.2114 (the start-time stamp; the run's work log also shows scripts from 0.1.2115, so the stamp may name an older version than the one that finished the run). The four review and Sonar measurements are `unknown` (no finalize step records them; the producer gap the payload spec names). Sonar over plain HTTP, `pullRequest=402`: 0 open issues, gate `OK`; `main` open 83 (unchanged). `gh pr view 402`: merged 2026-11-13T08:45:15Z, no label, reviews by CodeRabbit (two) and the operator account. The `final_merge_without_asking=false` prompt was not asked: the operator's "continue unattended" and the `CLAUDE.md` "Who merges" rule were taken as the permission.

## Routing and Merge Behavior

One pull request through the merge queue. Five `candidate-lesson` messages routed to plan-marshall's `lessons-routing` inbox as `pm-mcp-core-m2-m4-v2-023` to `-027` (written uncommitted in that repository's ledger worktree): `phase-5-execute` (gate-rewrite commits leave no change-ledger row, so the transition to finalize is refused), `automatic-review` (`review_completeness` traceback and a stale 0.1.2114 cache), `junit-core` (boundary values tested beside generated ones; a test list that mirrors a production set is derived from it), `plan-marshall` (`record-dispatch-boundary` without `--step-id` and context-load totals), `platform-runtime` (harness-injected subagent hand-backs counted as operator turns: 29 of 32).

## Reconciliation Actions

Row PLAN-V2-40 `running` -> `shipped`, `pr` `#402`, `landing` `landings/PLAN-V2-40.md`, `plan_marshall_plan_id` `plan-v2-40-envelope-packing`; the six messages archived; Watch entry in `epic.md`; decision log; resume anchor.

## Follow-Ups

Placed 2026-11-13 under the operator's rule "every leftover goes into a staged plan" (no new plan needed):

1. **PLAN-V2-63** (`long-context-thresholds`, it extends this package, edits `workflow-envelope.adoc` and removes `envelope-packing.adoc`): ADR 001 line 23; the cascade semantics in guide §3 or ADR 001; the validation of `packEnvelopes` input (budget above zero, cost not negative, or the documented caller duty) and the `requireNonNull` messages; the exact-equality tests of `EnvelopeBudgetTest` and the 19-character heading at `:57`; the `OptionalInt` to `@Nullable Long` adaptation of its own Deliverable 3; the wrong role-view owner is removed with the file (named so the plan does not copy it into its own text).
2. **PLAN-V2-125** (test conformance of `pm-core` and `pm-workflow`): the `EnvelopePackerTest` and `TaskCostEstimatorTest` gaps (exact-budget pair, determinism, generated contiguity property, merge into the following group behind an incompatible one, cascade, budget below the minimum, the `mergesIntoThePreceding` assertion, the four-signal monotonicity, the three 19-character headings, the import blank lines).
3. The two differences against `_tasks_cost.py` are facts for the specification's "re-verified" sentence; they ride with item 1 (the guide already states them) and need no plan of their own.

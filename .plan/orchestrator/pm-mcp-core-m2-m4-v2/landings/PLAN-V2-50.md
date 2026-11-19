# Landing Analysis: PLAN-V2-50 — r05-reduction-role-sets

epic: pm-mcp-core-m2-m4-v2
workstream: WS-10
pr: #504 (merge `f19d2a1b`, documentation only, four files)

> Landing record, runbook lane (`rb-v2-50-r05-reduction-role-sets`, harness opencode, model `deepseek-v4.1-flash/high`).
> Drained from `inbox/rb-v2-50-r05-reduction-role-sets-001.md` on 2026-11-19. `inbox landing-check`: `complete: false`,
> missing `total_tokens`, `plugin_version` and `sonar_open_new_issues` (all `unknown`; the third is an honest failed read:
> a documentation-only pull request gets no Sonar analysis, the lane read HTTP 404 and chose `unknown` over `n/a`).

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 classification table in the PR body | not shown by reading | the body is not readable by a leaf; the lane says 22 rows |
| D2 `model-work/01` and the role-set slice condensed | shipped-modified | eleven hunks at 64, 79, 83, 134, 137, 155, 336, 341, 353, 365-371; every `[#…]` anchor kept, the Fit step kept; `judge-instrument-check` condensed and pointed instead of left (the spec's premise was refuted) |
| D3 `store-schemas/12` and `/16` condensed outside the schema blocks; `harness-profiles/01` untouched and reported | shipped-as-specified | `/12` changes only line 91, `/16` only line 65; no `include::`, `[source,json]`, `----` or `#role-validation` row in the diff (leaf read all hunks) |
| D4 covered watch items deleted or rewritten to their remainder | shipped-modified | `-49` and `-51` rewritten, `-57` and `-66` not in the diff, `-48`/`-64`/`-65` gone, CLIENT-13 and -16 kept; no deletion (see L3-1, L3-2) |
| D5 no dangling reference | shipped-as-specified | no anchor or id deleted, so none can dangle; the 25 test classes the PR names exist (`git ls-files`) |
| purpose of the plan: a reduction | **not delivered** | merged blobs, before to after (re-measured with `git show f19d2a1b^1` and `f19d2a1b`): `model-work/01` 76409 → 76382 (−27), `store-schemas/12` 63430 → 63506 (+76), `store-schemas/16` 18913 → 18973 (+60), `implementation-watch/model-work.adoc` 84585 → 85116 (+531): net **+640 B**, the three specification parts +109 B |

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`, `orchestrator.analyze`) — reason: a full-ship landing is read by its own leaf with no size qualifier; the leaf wrote nothing. The layers were applied to documentation: layer 3 asks whether each statement the merged text makes about the code is true at the merged head.
- **Re-read by the orchestrator** (all held): the four blob sizes, `harness-profiles.adoc:89` (`[#PM-WATCH-CLIENT-13]` present), `GradingOutcome.java:65` (the constant is `Refused.JUDGE_UNCHECKED`), no source use of `SCREENER_RUNS`, `git grep` of `NO_GRADED_ITEMS` over tests (none), `CompositionChecksTest.java:107-115` (a missing role refuses), `model-work.adoc:20-34` and `:36-55` (DISP-49 and DISP-51 as merged), the two removed sentences in the diff of `model-work/01`.
- **Not re-read, recorded as unverified leads:** `JudgeInstrumentCheck.java:25-27` and `RoleGrader.java:160-185` (the premise refutation: only the constant was re-read), `WorkerJudgeScorer.java:110`, `:140-146`, `ModelCallResult.java:26-32`, the `escalation` member claim, the Traceability lines 2906-2946, the link targets, the PR body's figures.
- **Verdict:** layer 1 findings (3 info, 1 minor); layer 2 findings (1 minor, rest info); layer 3 findings (3 minor, 1 info). No blocker, no major.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L1-1 | "`PM-WATCH-CLIENT-13` already gone" is false; it stands, its Source names PLAN-V2-145; true only of DISP-48/-64/-65 | checked | refutes | `doc/implementation-watch/harness-profiles.adoc:89` |
| L1-2 | "`judge-instrument-check` is open, the code still refuses `judge_unchecked`" is false: the check never refuses a grading and `grade()` proceeds; the constant is `Refused.JUDGE_UNCHECKED`, not `GradingOutcome.JUDGE_UNCHECKED` | checked (constant re-read, the refusal logic not) | refutes | `GradingOutcome.java:65`, `JudgeInstrumentCheck.java:25` |
| L1-3 | "`SCREENER_RUNS` and `GradingOffer` raise the run count" is false: `GradingProfile` carries only `runsPerItem` and `SCREENER_RUNS` occurs nowhere | checked | refutes | `GradingProfile.java:19` |
| L1-4 | the spec's own record is stale: it cites `RoleGrader:168/177` as the refusal (those are the `PASSED`/`FAILED` returns), a `+` test separator surefire does not read as OR, and a Done-when ("every deleted watch id has no remaining reference") that is vacuous because nothing was deleted | checked (not re-read) | refutes | spec |

Persisted by `corpus set-verdict`: none; the spec is of a shipped plan and no finding names a labelled claim (the three refuted premises are prose, and the lane carried them back in the PR body).

**Layer 1 verdict:** findings — 4

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| D2 | the hunks above | n/a (documentation) | **L2-1** the reduction is nominal: net +640 B against the PR body's "specs −85" and the lane message's 76867/63523/19390/85259 bytes, **which match no merged blob**; the Done-when sets no size criterion, so it is formally met and the R05 purpose is not | checked (sizes re-measured) |
| D3, D4, D5 | as above | n/a | none | checked |
| D1 | n/a | n/a | the table in the PR body, the pass/green claims (11 187 and 11 280 tests, 226 and 21 targeted tests, `BROKEN LINKS 130`) and the Sonar state cannot be shown by reading | not_shown_by_reading |
| report | n/a | n/a | the lane's "5 findings" lists six ids (`inline-1` to `inline-5`, `outside-diff-1`); the progress file holds six `finding:` lines, the provider total is 5 | recorded |

**Layer 2 verdict:** findings — 1 (and 3 not shown)

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `doc/implementation-watch/model-work.adoc:49` | reading `PM-WATCH-DISP-51` | the Guard credits `RoleGraderTest` with "a run in which every item is ungraded closes `no_graded_items` and writes no record"; no test references `NO_GRADED_ITEMS` (re-read: only `GradingOutcome.java:74` and `RoleGrader.java:139`), and the code refuses when any one form has no graded item (`RoleGrader.java:138-140`, unverified lead): the item breaks ADR 027's named-test evidence rule | minor |
| L3-2 | `model-work.adoc:29` | reading `PM-WATCH-DISP-49` | its "Remaining" asks for a plan-instance composition test per refusal; `CompositionChecksTest.java:105-141` already refuses a missing role with `role_undefined` and a wrong-class role with `class_mismatch` for `START`, `OUTLINE` and `OVERRIDE` with matched controls (`:107-115` re-read, the rest not), and Traceability lists the composition check as implemented: the real remainder is the epic workflow and the project setup workflow, and the message naming the state and the required class | minor |
| L3-3 | `model-work/01-roles-and-configuration.adoc:83`, `:137` (diff re-read) | reading the condensed bullets | two sentences were condensed away with no covering class: "an override to a configuration without a current qualification record is shown to the operator as unqualified when it is set" (the `core.role` guide § Role Qualification does not state it) and the role-class-fit rule of thumb "a kind that changes the plan worktree or runs builds `writer`, a kind that reads and submits `read_only`" (it survives only in the `RoleClass` Javadoc): R05 did not allow dropping them | minor |
| L3-4 | `model-work/01:64` with `pm-core/doc/developer/core-role.adoc:71` | following the pointer | circular: the spec hands "the fresh read at every composition, task creation and offer" to the guide, the guide says "the specification requires" it; the normative statement is owned by neither (unverified lead) | info |
| L3-5 | `model-work.adoc:46` | `WorkerJudgeScorerTest` is cited as "submits `{"score": 2}` and gets an ungraded item" | the test asserts only an empty `Optional` (`outOfRangeScoreLeavesTheAnswerUnscored`); the code claim itself is true (unverified lead) | minor |

The code-facing statements the leaf checked are true: the judge reply schema is `{"type":"object"}` and `grade()` reads `score` alone, `ModelCallResult` has four closure reasons and no `input_too_large`, no `escalation` member exists in `core.role`.

**Layer 3 not shown by reading:** the targeted and full runs, the links check, Sonar.

**Layer 3 verdict:** findings — 5 (no blocker)

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| ADR 027 / spec D4 | rewrite a watch item only with a passing named test for the covered guard and nothing owed | **deviates** | L3-1, L3-2 |
| spec R05 / ADR 027 | condense implemented prose to a pointer, keep every anchor, keep what the code does not yet do | **deviates** | anchors kept; L3-3, and the reduction is net +640 B |
| spec R10 | no `include::`, frame, inline block, schema file or `#role-validation` row changes | conforms | all eleven hunks read by the leaf |
| CLAUDE.md § Work Packages | specification, watch and Traceability change in the plan's own PR | conforms | four `.adoc` files |
| CLAUDE.md § Git Workflow | commit trailer `Co-Authored-By: plan-marshall` | conforms | the merge commit carries it |
| CLAUDE.md § Build | targeted tests, links check, Sonar | not_shown_by_reading | needs a run |

## Metrics and Anomalies

- Tokens: `unknown`. Duration: not in the message.
- Anomalies: the first targeted run used the spec's `+` separator (surefire 3.6 reads no OR: `No tests matching pattern`, rerun with `,`); the machine was busy (queue waits up to 1384 s); CodeRabbit refused on quota at 19:49Z and reviewed after the 90-minute window; `sonar-build` is skipped for a documentation-only change, so the pull request has no Sonar analysis.

## Routing and Merge Behavior

- Review: coderabbit required (`review_complete_after_handling`), sourcery optional (approved, no findings). `runbook_lane review-gate --pr-number 504 --required-reviewer coderabbit` on the merged head reads `unresolved_review_threads: 0` and `review_body_findings_total: 5`: **both equal the landing's claim**. `review_body_findings_handled=5` is **re-verified from the progress file** (six `finding:` lines, `inline-1` to `inline-5` and `outside-diff-1`, covering all five provider findings; the gate's `handled: 0` is a difference in form).
- CI/merge: merge group run `44476380775`, `f19d2a1b`. Sonar: `unknown` (no analysis exists: `analysis_revision_unavailable`, quality-gate HTTP 404); the operator chose "record no analysis exists and enqueue" at the lane's question.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#504`; `landing` stamped; `plan_marshall_plan_id` `rb-v2-50-r05-reduction-role-sets`
- [x] epic.md: the incomplete-landing Open Defect folded (a third key, `sonar_open_new_issues`, named), the narrative entry added
- [x] the residue carried to PLAN-V2-114 item 57

## Follow-Ups

- The documentation defects (L3-1 to L3-5) and the nominal reduction (L2-1) are PLAN-V2-114 item 57.
- `PM-WATCH-DISP-51`'s deletion, `harness-profiles/01`'s `profile-verification` reduction and CLIENT-13/-16 belong to the plans whose surface includes `doc/ImplementationWatch.adoc` (PLAN-V2-63, 71, 119, 145), as the lane says.
- The dead `Refused.JUDGE_UNCHECKED` constant is a code plan's to remove (PLAN-V2-114 item 57).

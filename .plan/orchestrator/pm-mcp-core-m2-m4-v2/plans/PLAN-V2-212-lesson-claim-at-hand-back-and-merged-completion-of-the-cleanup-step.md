# PLAN-V2-212: A handed-back plan releases its lesson claim and the reconciliation retires the lesson `applied`; the cleanup step also completes on a pull request merged at the gate

epic: pm-mcp-core-m2-m4-v2
workstream: WS-12
origin: `inbox/doc-review-2026-11-25-004.md` §1 (core #628 `d961c9df`; the epic queue observes `handed_back` rows, the gate entry's `pr_merged`), staged by the orchestrator on the forty-first run, 2026-11-27.
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-212-lesson-claim-at-hand-back-and-merged-completion-of-the-cleanup-step.md` and is queued as one row file, `queue/PLAN-V2-212.json`,
> in the epic ledger (lane `runbook`). The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`). They bind this plan exactly as if they were written below. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R05, R06, R08, R10, R11, R13, R14, R18, R19, R20. (R12: no log message is added unless the outline finds one the guard needs.)

Plan parameters: R06 — developer documentation `pm-core/doc/developer/core-store.adoc` (the lesson store section) and the finalize invariants' developer documentation of `pm-workflow` where it lists INV-FIN-6; ADR `pm-core/doc/adr/core-store/076-the-end-of-a-claim-clears-the-claim-and-keeps-the-retirement.adoc` amended with a stamp, no new ADR; layout WS-12. **Read the skills first (R20):** `java-core`, `javadoc`, `junit-core`, `cui-testing`, `java-null-safety` of `/home/oliver/git/plan-marshall/marketplace/bundles/`, and `ref-asciidoc` of `pm-documents`; say in the first progress note which you read.

## Objective

The specification now has a plan that ended `handed_back` release its lesson claim, lets the epic queue's reconciliation retire the lesson `applied` in the step that marks the row `merged` provided the lesson is unclaimed or still claimed by that plan, and lets `default:branch-cleanup` complete `done` also when the merge gate was entered on an already merged pull request (`pr_merged`). Two things built in this repository disagree: `LessonStore.endClaimApplied` refuses an unclaimed lesson and acts only on the plan's own claim, and the finalize invariant INV-FIN-6 lets the cleanup step complete `done` only on a transition named `merged`. This plan changes both, tests first. It builds no part of the epic queue's observation (`epic.next-work`, `ci.pr-observe`): that is the package `epic-auto` and the CI contract.

## Deliverables

All references are read at `939578b6` (the head after #628) and re-read at the head of `main` before editing; drop what a merge since then settled and name the commit.

1. **The failing tests first (R10).** Each fails at the head before any production change, with a failing run id recorded in the progress note. (a) `endClaimApplied` on a lesson whose claim was released (unclaimed) retires it `applied`, and on a lesson still claimed by the same plan does the same; on a lesson claimed by ANOTHER plan it refuses as today (`LessonClaimTest:398-406, 422-441, 444-461` hold the present refusal cases; the new cases sit beside them). (b) INV-FIN-6: a transition of the merge gate that completes `default:branch-cleanup` `done` on the outcome `pr_merged` passes the invariant; a transition on `merged` keeps passing; an edge into a delivery terminal on any other outcome that does not complete the cleanup step is still reported (`CompiledInvariantsTest:837-843` holds the present case).
   - Skills: pm-dev-java:junit-core, pm-dev-java:java-core, pm-dev-java-cui:cui-testing
   - Persona: module-tester
2. **The changes.** (a) `LessonStore.java:542-585` `endClaimApplied` and the resolution it uses (`:559` refuses `claim() == null` as `UNCLAIMED`) accept an unclaimed lesson for the retire-`applied` end; the refusal for another plan's claim stays; the Javadoc and the class comment (`:63`) say so. (b) `FinalizeInvariants.java` INV-FIN-6 (`cleanupFallbackAndLanding`, `:253-264`): widen the invariant to the outcome `pr_merged` of the merge gate, or, if the specification names the outcome `merged` there, say so in the landing message and change nothing (the outline reads `workflow-dsl/03` excerpt, the `completes` clause under `pr_merged`, and `workflow-dsl/04`, and decides; the decision is recorded in the progress note). The hand-back's release of the claim is the existing `release` of the store; the outline names the call site by reading and builds none that the epic queue's step owns.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety, pm-dev-java:javadoc
   - Persona: implementer
3. **The documents.** `pm-core/doc/developer/core-store.adoc:956-960` (the lesson claim's end) and ADR core-store/076 (stamp), the developer documentation of the finalize invariants where it lists INV-FIN-6.
   - Skills: pm-documents:ref-asciidoc, pm-dev-java:javadoc
   - Persona: documenter

**Done when** (against `origin/main` `939578b6`): the tests of deliverable 1 fail at `939578b6` (or the head the plan names) and hold after deliverable 2 — fails at `939578b6` (an unclaimed lesson is refused `UNCLAIMED`; a gate transition on `pr_merged` is reported by INV-FIN-6); a lesson claimed by another plan is still refused (holds at `939578b6`, must keep holding); no behaviour of the other invariants or of a claimed lesson's other ends changes (the existing lesson and invariant tests hold unchanged at the head); `verify -Ppre-commit` (its rewrites reviewed, those of any file that is not this plan's not shipped) and `verify -pl pm-core,pm-workflow -am` are green, targeted `-Dtest` where a full verify exceeds the host's budget; the open Sonar issues of the pull request are fixed or dismissed with a reason.

## Standing Constraints

- **Read every cited line at the head first** and drop what a merge settled. The line numbers are those of the fourth round's message at `88680dd7`; the orchestrator read `LessonStore.java:559` (the `UNCLAIMED` refusal) and the INV-FIN-6 check (`FinalizeInvariants.java:253-264`, the message cites `:247-264`) at `939578b6`.
- **Tests first**: the failing run id of each test of deliverable 1 is in the progress note.
- **Work to the Done-when.** Do not stop to ask the operator which part to continue with.
- **Every carried item gets an outcome** in the landing message and the pull request: adopted into a deliverable, split off to a NAMED existing plan, or dropped, each with its reason.
- **The guard stays narrow.** A lesson claimed by another plan, a retired lesson and a missing lesson keep their refusals; the new acceptance is the unclaimed lesson alone, and only for the retire-`applied` end.
- **No dependency or plugin is added.** No more than 100 files per pull request.

## Claim Labels

All lines read at `origin/main` `939578b6` by the orchestrator on 2026-11-27.

- OBSERVED: `LessonStore.endClaimApplied` is at `LessonStore.java:542` and the resolution refuses an unclaimed lesson (`frontMatter.claim() == null` -> `LessonResolution.Refusal.UNCLAIMED`) at `:559`, and another plan's claim at the next check — read at `:555-562`
- OBSERVED: INV-FIN-6 is `cleanupFallbackAndLanding` and reports an edge whose outcome is `MERGED` (the constant) from a non-archive state that does not complete `BRANCH_CLEANUP` as done — read at `FinalizeInvariants.java:253-264`
- OBSERVED: the tests named by the message exist: `LessonClaimTest`, `CompiledInvariantsTest`, `RetryCountersTest` — checked by `git ls-files` on 2026-11-27
- HYPOTHESIS: the specification says the cleanup step completes `done` on the gate-entry outcome `pr_merged` and does not rename that outcome `merged` — confirm/refute at `doc/specification/workflow-dsl/03-reference-excerpt.adoc` (the `completes` clause under `pr_merged`) and `workflow-dsl/04-ast-node-model.adoc` (verify-at-outline)
- HYPOTHESIS: the claim release at a hand-back is the lesson store's existing `release` and the epic queue's pass (not this plan) calls it — confirm/refute at `LessonStore.java` `release` and `doc/plans/epic-auto.adoc` (verify-at-outline)
- Verify-first clause: the acceptance of an unclaimed lesson is a change of the store's guard; if the specification's text (`store-schemas/10-job-and-lesson-records.adoc`, Lesson Record) says the retire-`applied` end needs the plan's own claim, the question goes back to the orchestrator before the guard is touched.

## Expected Surface

- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/LessonStore.java`, `pm-core/src/test/java/de/planmarshall/core/store/LessonClaimTest.java`, `pm-core/doc/developer/core-store.adoc`, `pm-core/doc/adr/core-store/076-the-end-of-a-claim-clears-the-claim-and-keeps-the-retirement.adoc`, `pm-workflow/src/main/java/de/planmarshall/workflow/verify/FinalizeInvariants.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/verify/CompiledInvariantsTest.java`.

## Dependencies and Sequencing

- Depends on: none (the specification is merged, #628).
- Overlaps with (class level): PLAN-V2-209 (names `core-store.adoc`, a single shared file is no block), PLAN-V2-211 (same), PLAN-V2-190 (names the invariant fixtures).
- Adjacent to: the package `epic-auto` (the epic queue's observation is its task, PLAN-V2-210 edits the package text), PLAN-V2-38 and PLAN-V2-27 (parked).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-212-lesson-claim-at-hand-back-and-merged-completion-of-the-cleanup-step.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates and edits NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports its outcome through its PR and its inbox message. See `persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

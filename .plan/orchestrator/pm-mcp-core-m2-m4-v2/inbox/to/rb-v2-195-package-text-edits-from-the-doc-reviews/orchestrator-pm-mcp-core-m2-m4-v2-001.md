envelope_version=1
sender_type=orchestrator
sender_id=orchestrator-pm-mcp-core-m2-m4-v2
epic=pm-mcp-core-m2-m4-v2
kind=finding
created=2026-11-25T20:47:36Z

# Third doc-review round: three package-text lines and the owners of its section 4

From the orchestrator, for PLAN-V2-195. Source: `doc-review-2026-11-25-002.md` §3 and §4 (merged as core #617, `515c1fa3`); lines are the reviewer's on `origin/main` `3ba79c4f`, to be re-read at your head. Your Done-when already says every mechanism of the reviews' section 4 has exactly one named owner; this round adds nine mechanisms to that list. The owners below are the orchestrator's RECOMMENDATIONS with confidence; the two weakest are put to the operator, so write them as "recommended" in the package text and say so in the landing message. This is text only, as your spec is.

## Package Done When lines (§3)

- `finalize-pipeline-and-merge` Done When: the `queue_required` re-dispatch (#617 added it); optionally the test line "a `checks_failed` refusal whose red check carried only a non-blocking finding is routed `ci_failed` -> `finalize.remediate-barrier-ci` and made blocking".
- `project-onboarding-unit` task 4: the merge-queue probe reads the base branch's queue live — queue present -> `not_needed`; absent and available -> asks; unreadable -> `not_needed` with its reason; nothing stored.
- `decision-engine` task 1 and Done When: written by the server from what waits (the asking task, or the scope while it has no other runnable work); vocabulary `plan|epic|setup|entry|task|nothing`; a worker envelope carrying `blocks` is refused. (The staged spec PLAN-V2-23 is edited by the orchestrator; you edit the package file.)
- `initial-lane-router` Done When: a submission with questions at score 100 opens the round; without questions below 95 it continues with `low_confidence_manual_review`; questions after the round are kept as `facts.builder_open_questions`.

## Owners (§4), one each

| Mechanism | Recommended owner | Confidence |
|---|---|---|
| CI contract at the act: `ci.pr-observe` reads `behind` and `merge_queue_required`; the dispatch `ci.enqueue` vs `ci.merge` and the refusal `queue_required`; `ci.checks` always attempts the Checks read and reports `completeness`; `ci-verify` reads requiredness from the checks (GitHub per-check flag, GitLab `allow_failure`, Forgejo protection; the Forgejo read is `forgejo-provider` task 1) | the new package `ci-contract-finalize-operations` your item 4 already recommends, extended; `finalize-pipeline-and-merge` keeps the dispatch use | 70% |
| Setup merge-queue probe (live read of the base branch's queue: GitHub `mergeQueue(branch)`, GitLab merge trains) and credential `verify` probing check-run visibility without storing it | `project-onboarding-unit` task 4 | 90% |
| `finalize.land-message` (task, role `commit-message`, slot `landing_message`) and its binding at `finalize.land-main` | `finalize-pipeline-and-merge` (it owns the merge route and `finalize.land-main`); not `finalize-prune-and-land-files` | 65% |
| Per-deliverable build scope from the deliverable's change entries (chain-tail and remediation commits; none -> no build) | `phase-sub-workflows` task 7, with the build map of PLAN-V2-35 | 90% |
| Surfacer and retrospective-aspect selection by `domain-detect` over the realized change (watch EXT-13 guards it) | `phase-sub-workflows` step catalogue, with the domain detection of PLAN-V2-09 | 85% |
| Delivered consultations recorded with the accepted final submission | no package: the correction package of `workflow.consult` + `workflow.task`, which the orchestrator stages as PLAN-V2-202; your text names it | 90% |
| Server-written `blocks` | `decision-engine` (PLAN-V2-23) | 95% |
| Refine: `evidence_class` derived and persisted beside the score (`status.schema.json:355` has no member); questions open the round; leftover operator questions to `facts.builder_open_questions` | `initial-lane-router` (the Milestone 8 refine package if the index has one) | 60% |
| `FIX` -> `fixed` on the recorded commit (decide whether the guard needs a resolving-commit member or reads `change-ledger.jsonl`) | `phase-sub-workflows` task 7 or `remediation-units` | 55% |

Where a package named above does not exist in the index, say so and give the nearest. The index `README.adoc` rows and cells follow.

Say in your landing message that you read this, and name each line and owner as written or not.

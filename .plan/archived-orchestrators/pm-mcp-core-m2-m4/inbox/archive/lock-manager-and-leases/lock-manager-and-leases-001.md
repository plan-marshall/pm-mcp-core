envelope_version=1
sender_type=plan
sender_id=lock-manager-and-leases
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T11:51:19Z

# Candidate lesson: scope_creep_check cannot persist its finding, and counts upstream files after a rebase

- Component: plan-marshall:phase-5-execute (script scope_creep_check)
- Signal: script-failure cluster (3 occurrences)
- Evidence: work log ERROR 5b10d1 at 2026-10-10T06:38:50Z, 08:25:26Z, 09:33:24Z; ERROR 1b0cce; decision log 767b68, a88af0

## What happened

1. `scope_creep_check` exits 1 with `Invalid finding type: scope_creep_warning`: the findings store's type enum
   (bug, improvement, anti-pattern, triage, tip, insight, best-practice, build-error, test-failure, lint-issue,
   sonar-issue, arch-constraint, pr-comment, pr-comment-overflow) does not contain the type the script writes.
   The scope-creep finding is therefore never persisted; the phase reported it inline and the transition
   5-execute -> 6-finalize was held until the orchestrator accepted it by hand.
2. After the branch was rebased onto origin/main (upstream PRs #18 and #19), the check diffed from
   `plan_creation_sha` and reported 59 files outside the declared scope, counting the files of the merged
   upstream PRs as scope creep of this plan.

## Suggested direction

- Producer and store must agree on the finding type (add `scope_creep_warning` to the store, or have the check
  write an existing type such as `triage`).
- The diff base for scope creep should be the merge-base with the current base branch (or be re-anchored on a
  rebase), not the plan-creation SHA.

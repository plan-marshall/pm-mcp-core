envelope_version=1
sender_type=plan
sender_id=lock-manager-and-leases
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T11:52:26Z

# Candidate lesson: commit-add rejects deliverable 0 although triage.md prescribes 0 for fix tasks

- Component: plan-marshall:manage-tasks (commit-add) vs plan-marshall:plan-marshall workflow triage.md
- Signal: script-failure cluster (reported by the finalize dispatcher)
- Evidence: dispatcher observation in the finalize run (fix tasks TASK-016..TASK-019 added at 07:13 and 09:09).
  NOT independently confirmed in this plan's work or decision log; the record would be in the script-execution log.

## What happened (as reported)

triage.md tells the triage leaf to file fix tasks with `deliverable: 0` (no outline deliverable), but
`commit-add` refuses `deliverable: 0`. The workflow doc and the script contract disagree, so a leaf that follows
the doc verbatim fails and has to improvise a deliverable number.

## Suggested direction

Make the two agree: either accept `0` as the "no deliverable / fix task" sentinel in commit-add, or change
triage.md to prescribe the value commit-add accepts. Add a test that runs the triage-documented invocation.

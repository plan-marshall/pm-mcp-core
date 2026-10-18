envelope_version=1
sender_type=plan
sender_id=work-package-redistribution
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-17T20:08:52Z

# Candidate lesson: open the sibling-repository link fix during execute when a recut deletes a linked file

Component: this repository's work-package practice (doc/plans recuts; plan specs of epic pm-mcp-core-m2-m4)
Category: improvement
Source: plan-retrospective of work-package-redistribution

## Context

The outline treated corrections in other repositories as "owed": deliverable 11 would list them and open a pull
request there only where a link would break. Execute saw from TASK-3 on that `plan-marshall-documentation`
`doc/roadmap.adoc:233` links to `doc/plans/lane-route-pruning.adoc`, which this plan deletes, and left it to the
operator. But this repository's `links.yml` workflow runs `trace.py` over the documentation repository as well, so
the `Links` check failed on PR #85. The sibling pull request was only opened during finalize, after an extra operator
question; ci-verify then ran 38 minutes (18:35 to 19:13). Functional review graded deliverable 11
`met_with_residuals` for the same reason.

## Root cause

A link from a sibling repository into a file this change deletes is not "owed later": the CI of this repository
checks it, so it blocks this pull request until the sibling fix merges.

## Proposed action

- When a plan deletes or renames a file under `doc/`, run the trace script during execute; for every broken link
  it reports in a sibling repository, open that repository's fix pull request before create-pr (the target already
  exists here, so the fix can merge first).
- Name this in the plan spec template for recuts, so the outline makes it a deliverable instead of an owed item.

## Evidence

- work.log 17:37:50Z, 17:40:05Z, 17:42:32Z, 18:03:03Z: BROKEN LINKS warnings naming roadmap.adoc:233
- functional-review D11 residual: "The pull request in plan-marshall-documentation ... is not opened, so trace.py
  reports BROKEN LINKS 1"
- operator decision at finalize: "The Links check on pull request #85 fails on one link ... Should I open it?" =
  "Open it"

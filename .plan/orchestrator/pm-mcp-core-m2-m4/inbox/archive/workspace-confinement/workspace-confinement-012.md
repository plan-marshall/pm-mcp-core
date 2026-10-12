envelope_version=1
sender_type=plan
sender_id=workspace-confinement
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T07:22:50Z

# Candidate: a required review bot that reviews only on request went stale after every fix commit and blocked the review step

Source: run observation backing the review signal, not a finding record of its own. Verified against the logs.
Plan: `workspace-confinement` (PLAN-03), pull request https://github.com/plan-marshall/pm-mcp-core/pull/19, merged.

## Evidence

- Work log `610d05` (2026-10-09T23:53:27Z): "2 new pr-comment findings filed; cuioss-review-bot participated_stale at head 252b4b1, recorded loop_back".
- Decision log `217eb0`: the review step was recorded with outcome `loop_back`.
- Decision log `572828` (2026-10-10T06:16:10Z, WARNING): "cuioss-review-bot moved from required_bots to optional_bots for this plan only (plan-local step-params; marshal.json unchanged) — its review is stale at the new head and re_review_on_loopback=false blocks the re-trigger".
- Work log `4650b6` (07:13:34Z): the pre-merge review check passed with "required-bot participation complete" only after that change.

## What happened

`cuioss-review-bot` is configured as a required reviewer and reviews only when it is asked to. With `re_review_on_loopback=false` the run does not ask again after a fix commit. The first fix commit (`252b4b1`, TASK-7) therefore left its review stale, the review step could not complete, and the run waited from 23:53Z to 06:16Z until the operator made the bot optional for this plan. `marshal.json` still lists the bot as required.

## Why it may be a lesson

The two settings contradict each other for any plan that needs at least one fix commit after review: required participation at the current head, and no re-request after a fix. The next plan of this repository meets the same block unless the project configuration changes.

Classification is left to the pickup.

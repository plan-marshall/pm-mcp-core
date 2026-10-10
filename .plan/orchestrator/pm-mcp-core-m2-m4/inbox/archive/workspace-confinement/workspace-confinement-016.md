envelope_version=1
sender_type=plan
sender_id=workspace-confinement
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T07:22:56Z

# Candidate (reported, not corroborated by the logs): the CodeRabbit check stays pending for about 20 minutes after each push

Source: run observation reported by the main session when it handed this step over. It is NOT a finding record, and the plan's logs do not confirm the figures.
Plan: `workspace-confinement` (PLAN-03), pull request https://github.com/plan-marshall/pm-mcp-core/pull/19, merged.

## What was reported

After each push the CodeRabbit check stays `PENDING` for about 20 minutes, which consumes two full CI waits per round.

## What the logs show

The logs record one CI wait per round, not two, and none of them names CodeRabbit:

| Round | Push recorded | CI wait recorded as done | Elapsed |
|-------|---------------|--------------------------|---------|
| 1 | 2026-10-09T23:07:34Z | 23:17:59Z | about 10 min |
| 2 | 2026-10-09T23:37:22Z | 23:48:30Z | about 11 min |
| 3 | 2026-10-10T06:40:09Z | 06:46:43Z | about 6.5 min |

After each CI wait the review step ran for about 4.5 minutes (decision log `0c73a8`, `217eb0`, `9a29d5`: 258 s, 271 s, 258 s). The CodeRabbit review of commit `252b4b1` carries an edit time of 23:43:59Z, about 6.5 minutes after the round-2 push.

Nothing in the work log or decision log states a 20-minute pending period or a second CI wait within one round. If the wait was repeated inside the main session without a new log entry, the logs would not show it.

## Status

The 20-minute figure and the "two waits per round" rest on the main session's account alone. The pickup should treat them as a lead to check against the session transcript and the check history of the pull request, not as an established fact.

## Why it may be a lesson

If the account holds: a check that reports late keeps the CI wait from finishing on its first pass in every round, in a repository where each fix commit already costs a full round.

Classification is left to the pickup.

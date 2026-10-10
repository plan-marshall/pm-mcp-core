envelope_version=1
sender_type=plan
sender_id=lock-manager-and-leases
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T11:51:57Z

# Candidate lesson: required cuioss-review-bot does not re-review after a fix commit

- Component: plan-marshall:automatic-review (required_bots configuration); pm-mcp-core review-bot setup
- Signal: automatic-review outcome=loop_back at HEAD ab766d8
- Evidence: work log 2026-10-10T09:02:39Z (automatic-review loop_back); decision log 44e2f6 (2026-10-10T09:04:27Z)

## What happened

cuioss-review-bot is listed in required_bots. After the in-run fix commits it stayed `participated_stale` for
the new HEAD because it does not re-review after a push. The required-participation barrier could therefore
never clear; the plan only proceeded after the operator moved the bot from required_bots to optional_bots for
this plan (coderabbit required and sourcery optional had both participated at the new HEAD).

## Suggested direction

Either make cuioss-review-bot optional in the project configuration, or teach the participation check that a
bot which reviews once per PR counts as participated after its first review; otherwise every plan with a fix
round hits the same manual override.

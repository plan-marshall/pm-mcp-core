envelope_version=1
sender_type=plan
sender_id=plan-v2-199-supervisor-t3-test-gaps
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-28T12:48:13Z

component=plan-marshall:automatic-review
category=improvement
confidence=high
source_plan=plan-v2-199-supervisor-t3-test-gaps

# Bound close-and-reopen recovery across PR numbers for account-scoped quota

## Context

CodeRabbit, the required review bot, refused PR 639 on its free-OSS quota. The rate-window recovery claimed windows, then took Branch 5 (close_and_reopen) three times, so the plan's pull request went 639 -> 643 -> 646 -> 648 over about five hours before CodeRabbit reviewed. The operator had to answer three escalations ("Turn on waiting", "Wait another 3600s", "Wait out 23 minutes and try once more") and type "retry" twice.

## Root cause

The quota is account-scoped, so closing and reopening the pull request re-delivers the request but buys no quota. The attempt counter is keyed per PR number and restarts at 1/10 on every replacement PR, so the cap does not bound the loop across PR numbers. Refusal ETAs ("15 minutes", "54 minutes", "23 minutes") were wrong more than once.

## Proposed action

Key the rate-window attempt counter on the plan (or on bot + head branch) rather than on the PR number, so close-and-reopen cycles count against one cap. Classify a quota refusal whose cause is account-scoped as non-recoverable by close-and-reopen and go straight to the operator decision instead. Consider a cross-plan signal: another plan (plan-v2-200) held the same bot window at the same time.

## Evidence

- aspect: chat_history_analysis - three operator decisions, PR numbers 639/643/646/648, "the reopen re-delivered the request but bought no quota, because the limit is account-scoped"
- aspect: log_analysis - merge_lock cumulative 12.06M ms and ci 10.62M ms of script time; automatic-review firing_count 6 with 3 loop-backs
- aspect: logging_gap_analysis - 4 blocked_session_restart dispatches, 622498 retryable tokens in 6-finalize
- aspect: plan_efficiency - 6-finalize holds 53% of 6.7M tokens

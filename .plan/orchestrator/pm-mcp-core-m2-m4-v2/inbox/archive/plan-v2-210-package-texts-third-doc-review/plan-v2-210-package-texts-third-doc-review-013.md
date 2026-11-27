envelope_version=1
sender_type=plan
sender_id=plan-v2-210-package-texts-third-doc-review
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-27T19:26:26Z

# Run review-bot waits synchronously with a timeout below the harness auto-background limit

- Source: plan-retrospective (aspects chat-history-analysis, log-analysis)
- Component: plan-marshall:automatic-review
- Category: bug
- Confidence: high

## Context

In three automatic-review rounds of this plan, leaf calls were auto-moved to the background by the host after 180 s: `ci pr wait-for-comments --timeout 180` (twice) and a `github_re_review` call for sourcery whose output was still empty when the leaf finished. One leaf (iteration 5) then stopped "waiting on its own background work" and reported only on a later notification. tools-integration-ci:ci and github_re_review are among the five slowest scripts of the plan (cumulative 2.7M ms and 1.6M ms).

## Root cause

The wait verbs are invoked without an explicit Bash timeout above their own wait, so the host's default limit moves them to the background, which the execution-context leaf contract forbids for result-bearing work.

## Proposed action

Have the automatic-review workflow pass an explicit Bash timeout (wait seconds plus margin, within the harness ceiling) on every wait-for-comments and re-review await call, and state that a backgrounded result-bearing call is a contract violation, not a tolerated outcome.

## Evidence

- aspect: chat_history_analysis — iteration 1 and iteration 4 hand-backs report the auto-background move; iteration 5 notification "waiting on its own background work"
- aspect: log_analysis — script_cost_rollup ranks tools-integration-ci:ci (20 calls, 2715180 ms) and github_re_review (5 calls, 1634910 ms)

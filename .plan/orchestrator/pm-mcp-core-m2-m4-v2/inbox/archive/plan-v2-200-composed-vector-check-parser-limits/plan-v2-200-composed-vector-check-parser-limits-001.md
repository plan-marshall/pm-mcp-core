envelope_version=1
sender_type=plan
sender_id=plan-v2-200-composed-vector-check-parser-limits
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-27T21:26:17Z

component=plan-marshall:automatic-review
category=improvement
title=Stop charging required-bot quota waits to the fix-and-recheck loop-back budget

# Stop charging required-bot quota waits to the fix-and-recheck loop-back budget

## Context

In plan-v2-200-composed-vector-check-parser-limits the required review bot CodeRabbit answered with a free-OSS quota refusal on PR 638. automatic-review fired six times: four loop_back firings were spent purely waiting on the quota (no code change between them), the third posted a re-trigger and claimed a 1740 s rate window, the fourth ran close-and-reopen (PR 638 -> PR 641). The loop-back budget (3) was exhausted and the operator had to grant one more round to continue.

## Root cause

The participation-guard loop_back for an awaitable infrastructure refusal (quota, rate window) is counted against the same loop_back budget that bounds fix-and-recheck rounds, so an external wait exhausts the budget meant for defect rounds.

## Proposed action

Count refused_awaitable / rate-window loop-backs in their own wait budget (or not at all) and keep the fix-and-recheck budget for rounds that follow a fix commit; escalate on the wait budget with its own operator question.

## Evidence

- status.metadata.loop_back_budgets.plan-marshall:automatic-review: granted 1, spent 4; phase_steps automatic-review firing_count 6
- chat: four loop_back hand-backs citing coderabbit refused_awaitable (quota), operator decision "Give it one more round"
- metrics: 6-finalize 2.92M of 5.40M tokens (54%)

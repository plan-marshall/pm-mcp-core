envelope_version=1
sender_type=plan
sender_id=plan-v2-189-outline-record-scope
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-27T16:09:05Z

component=plan-marshall:platform-runtime
category=bug
confidence=high
source_aspects=chat-history-analysis

# Do not count subagent hand-backs as operator turns in chat extract-signal

## Context

For session 93f04353 (plan-v2-189-outline-record-scope), chat extract-signal reported operator_turn_count 17 and gate_decision_count 2. Of the 17 kept turns, nearly all are harness-delivered subagent hand-backs ("Another Claude session sent a message: <agent-message ...> [Subagent hand-back]") that arrive in the user role. The genuine operator input was the slash command plus the two gate decisions.

## Root cause

The operator-provenance predicate classifies by message role, and the harness delivers subagent reports as user-role turns, so model output is counted as operator-authored signal.

## Proposed action

Exclude user-role turns whose body is an agent-message hand-back frame (and other harness injections) from operator_turn_count, and count them under residual_counts instead.

## Evidence

- extract-chat-signal: raw_turn_count 1083, operator_turn_count 17, gate_decision_count 2; reduced transcript turns are mostly "[Subagent hand-back]" frames

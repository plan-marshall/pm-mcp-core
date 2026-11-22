envelope_version=1
sender_type=plan
sender_id=plan-v2-20-git-commit-primitives
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-22T17:22:06Z

component=plan-marshall:platform-runtime
category=bug

# Stop counting subagent hand-backs as operator turns in chat extract-signal

## Context

For the PLAN-V2-20 session, `chat extract-signal` reported operator_turn_count 84 and no_signal false. The retained "user:" turns are almost all harness `<agent-message>` frames, the final reports of subagents the session dispatched, which the harness itself labels "not typed by your user". The real operator signal is in the 24 gate decisions. Any consumer reading operator_turn_count as the number of operator corrections is misled, and the reduction spends most of its 341 KB on agent reports.

## Root cause

The operator-provenance predicate treats every user-role turn as operator-authored and does not exclude the harness's subagent hand-back frames.

## Proposed action

Classify `<agent-message>` subagent hand-back frames (and similar harness injections) as non-operator, count them separately (e.g. agent_handback_count), and drop them from the reduced transcript or move them to a separate budgeted section.

## Evidence

- extract-chat-signal run for session f84087f1-b27f-4d1d-8abd-05184ec10de1: operator_turn_count 84, gate_decision_count 24, reduced 341 KB
- sampled reduced transcript (lines 1900-2100 and the tail): every retained user turn is a subagent hand-back frame

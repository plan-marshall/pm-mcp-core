envelope_version=1
sender_type=plan
sender_id=runtime-lifecycle
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-11T20:19:08Z

component=plan-marshall:platform-runtime
category=bug
signal=plan-retrospective chat-history pre-pass (verified)
evidence=extract-chat-signal run --session-id 02d8321e-a5bd-49e0-b83a-16172617f9b4: operator_turn_count=25, reduced_turn_count=25; of those 25 kept turns only 3 were typed by the operator (the /plan-marshall command and two "did you stop?"), the other 22 are "Another Claude session sent a message: <agent-message ...> [Subagent hand-back]" frames

# chat extract-signal counts subagent hand-back frames as operator-authored turns

## Observation

The platform-runtime `chat extract-signal` reduction classifies every harness-delivered subagent hand-back (`user:` role, body starting "Another Claude session sent a message: <agent-message from=...>") as an operator turn. On this plan it reported `operator_turn_count: 25` where the operator actually typed 3 lines. The harness text itself says the frame "was not typed by your user".

Consequences: `no_signal` can never be true on an orchestrated session (every dispatch return counts as operator signal), and the reduced transcript is dominated by leaf reports rather than operator corrections, which is what the chat-history aspect is meant to surface (pivots, nudges, clarifications).

## Suggested direction

- Exclude the `<agent-message>` / "[Subagent hand-back]" frame from the operator-provenance predicate; count it under its own residual bucket (e.g. `subagent_handback`).
- Keep the frames available as a separate, optionally included stream so the retrospective can still read leaf reports when budget allows.
- Add a fixture test with a hand-back frame asserting it does not raise `operator_turn_count`.

envelope_version=1
sender_type=plan
sender_id=plan-v2-138-secret-redactor-hardening
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-13T18:19:00Z

component=pm-core
category=improvement
route=pm-mcp-core
confidence=high

# Check the ported real-tool fixture before fixing a parser marker rule

## Context

PLAN-V2-138 outline decision D6(c) fixed the TAP success-marker rule as "a top-level `1..N` plan line that does not match the summed `# tests` total keeps the marker false". In 5-execute the envelope found that the ported real `node --test` fixture `pm-core/src/test/resources/ported/build/npm-tap/npm-tap-success.txt` (from `npm-tap-test-real.log`) prints `1..4` (four top-level suites) beside `# tests 5` (leaf tests) and expects the marker true. The literal rule would have made every green `node --test` run with describe blocks indeterminate, so the envelope stopped with an escalate_ask and the comparison was changed to top-level `ok` / `not ok` points.

## Root cause

The outline settled a parser rule from the tool's documented output shape without reading the ported real-tool fixtures that pin the current behaviour.

## Proposed action

When an outline deliverable changes a success-marker or count rule in a `core.build` parser, the outline (or the refine probe) reads the ported fixtures under `pm-core/src/test/resources/ported/build/{tool}/` for that tool and states in the decision which fixture confirms the rule. A rule that contradicts a real-tool fixture is a decision for refine, not a discovery in execute.

## Evidence

- aspect: chat_history_analysis - escalate_ask d3-tap-plan-comparand in 5-execute, accepted with rationale, recorded in ADR core-build/009 Decision 3
- aspect: request_result_alignment - D6 fulfilled with an accepted scope deviation

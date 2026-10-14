envelope_version=1
sender_type=plan
sender_id=plan-17-project-units-and-invariants
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-13T20:03:28Z

# Candidate lesson: Phase-4/5 subagents self-transition phases before the orchestrator-owned Q-Gate runs

**Component (suggested):** plan-marshall:phase-4-plan (also plan-marshall:phase-3-outline / phase-5-execute)
**Category (suggested):** bug

## Observation

In plan-17-project-units-and-invariants the dispatched phase subagents transitioned the plan's phase
themselves (outline -> plan, plan -> execute) before the orchestrator-owned Q-Gate validation for the
completed phase had run. The transition therefore preceded the gate that is supposed to authorise it.

## Root cause

The phase SKILL bodies (or their dispatch envelopes) still carry a phase-transition step that the leaf
executes, while the orchestration contract assigns the transition to the main-context orchestrator after
the Q-Gate.

## Suggested correction

Remove or guard the transition call in dispatched phase bodies (leaf returns a "ready for q-gate" signal;
only the orchestrator transitions), or make `manage-status transition` refuse a transition while the
source phase's Q-Gate has not been recorded.

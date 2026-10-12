envelope_version=1
sender_type=plan
sender_id=plan-06-store-substrate
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-11T07:11:05Z

component=plan-marshall:plan-marshall
category=bug
source_plan=plan-06-store-substrate
source_record=orchestrator observation during the run (not a stored finding)

# Phase leaves transition the plan status themselves and leave the metrics boundary half-stamped

## What happened

In plan-06-store-substrate the dispatched phase leaves for 3-outline, 4-plan and 5-execute each
moved the plan's status to the next phase on their own before returning. The phase-boundary metrics
were then only half stamped: the transition happened inside the leaf, without the paired
end-of-phase / start-of-next-phase metrics recording that the orchestrator performs when it owns
the transition. The orchestrator had to reconcile the boundary after each of the three phases.

## Rule

A dispatched phase leaf returns its result and does not transition the plan status; the
main-context orchestrator owns the phase transition and stamps both sides of the metrics boundary
in the same step. If a leaf's workflow contains a transition call, it is either removed or made
conditional on running in the main context.

## Evidence to check before acting

Read the work logs of plan-06-store-substrate around each phase end to find the call that moved the
status (which verb, from which component), and check the phase SKILL.md bodies of phase-3-outline,
phase-4-plan and phase-5-execute for a status transition step that runs inside the leaf.

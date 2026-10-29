envelope_version=1
sender_type=plan
sender_id=workflow-engine
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-13T06:40:07Z

# Candidate lesson: pre-commit OpenRewrite recipes rename test methods in files outside the change

- Component: pm-mcp-core build (`verify -Ppre-commit` profile from pm-mcp-parent) / plan-marshall:phase-5-execute
- Suggested category: improvement
- Signal: orchestrator observation (not recorded in the plan logs)

## Observation

In phase 5, the OpenRewrite recipes of the pre-commit profile renamed test methods in files that
upstream had also touched. Those changes were outside the plan's scope, so they had to be restored
after every pre-commit run.

## Evidence

- Orchestrator observation only. The plan logs contain no line for it, so the affected files and the
  recipe are not named here.

## Suggested correction

Find out which recipe renames the methods and either apply it once to the whole tree in its own
change or restrict it. Until then, phase 5 limits the pre-commit rewrite to the plan's footprint, or
restores the paths outside the footprint automatically and logs them.

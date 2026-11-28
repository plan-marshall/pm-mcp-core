envelope_version=1
sender_type=plan
sender_id=plan-v2-199-supervisor-t3-test-gaps
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-28T12:48:18Z

component=plan-marshall:phase-3-outline
category=bug
confidence=high
source_plan=plan-v2-199-supervisor-t3-test-gaps

# Author Maven -Dtest verification filters with commas, not plus signs

## Context

The outline stamped per-deliverable verification commands such as `test -pl pm-runtime -am -Dtest=SupervisionPassTest+WorkerActivityPumpTest -Dsurefire.failIfNoSpecifiedTests=false` (deliverables 3, 4 and 5). In this repository's Surefire the `+` form selects no test: run 9ea71659 came back indeterminate with zero tests executed. The execute agents switched to the comma form `-Dtest=A,B,C` by hand on every later run.

## Root cause

The outline (and the phase-4 verification-command deriver that copied it) treats `+` as a class separator for `-Dtest`. Surefire's documented multi-class separator is the comma; with `-Dsurefire.failIfNoSpecifiedTests=false` the empty selection does not fail, so the error is silent.

## Proposed action

Emit the comma form when composing Maven `-Dtest` filters for several classes, and add a validator check (outline Q-Gate or verification-command lint) that rejects `-Dtest=` values containing `+` between class names.

## Evidence

- aspect: chat_history_analysis - execute envelope report: "Several tasks filter tests as -Dtest=A+B+C. In this Surefire that form runs no test at all: run 9ea71659bb464e30b8408acf8bd51db3 came back indeterminate with zero tests executed"
- aspect: artifact_consistency / list-deliverables - verification commands of deliverables 3, 4 and 5 carry the `+` form

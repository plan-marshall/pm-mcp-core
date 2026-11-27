envelope_version=1
sender_type=plan
sender_id=plan-v2-210-package-texts-third-doc-review
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-27T19:22:00Z

# Candidate lesson: Done When said "no item anchored" after a new task named an anchored section

- Source: Q-Gate finding dc789b (phase 6-finalize, rule code-review, severity error)
- Component: doc/plans/epic-auto.adoc:35
- Resolution in run: taken_into_account (code review closed at 0b5dad4a with no blocking finding)

## Observation

epic-auto Done When said no item is anchored, yet new task 7 named the Epic Workflow and Lesson Claim sections; Lesson Claim is anchored to PM-WATCH-DISP-37.

## Candidate rule

Same pattern as finding d28099 (forgejo-provider): adding or relinking a task must re-derive the Done When's anchored watch items. A mechanical check (sections linked by tasks vs watch items named in Done When) would catch both.

Classification deferred to the orchestrator.

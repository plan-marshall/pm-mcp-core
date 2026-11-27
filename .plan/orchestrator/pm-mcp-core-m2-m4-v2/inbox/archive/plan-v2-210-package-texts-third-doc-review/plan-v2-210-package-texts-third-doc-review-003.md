envelope_version=1
sender_type=plan
sender_id=plan-v2-210-package-texts-third-doc-review
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-27T19:21:59Z

# Candidate lesson: Done When said "no watch item anchored" after a task added anchored sections

- Source: Q-Gate finding d28099 (phase 6-finalize, rule code-review, severity error)
- Component: doc/plans/forgejo-provider.adoc:32
- Resolution in run: taken_into_account (code review closed at 0b5dad4a with no blocking finding)

## Observation

forgejo-provider Done When said no watch item is anchored, yet task 1 was changed to link a Store Schemas section carrying PM-WATCH-DISP-19, PM-WATCH-PHASE-82 and PM-WATCH-STORE-50.

## Candidate rule

Whenever a package task gains a link to a specification section, re-derive the Done When's watch-item list from the linked sections' anchors in the same edit. Recurs with finding dc789b (epic-auto) in the same run, so it is a pattern rather than a one-off.

Classification deferred to the orchestrator.

envelope_version=1
sender_type=plan
sender_id=plan-20-job-runner
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-18T10:20:22Z

component=pm-runtime
category=improvement
created=2026-10-18

# Owed architecture hint: pm-runtime, Sonar suppressions (preference-emitter, plan-20-job-runner)

## Context

The preference-emitter found one disposition recurrence in plan-20-job-runner that cleared the within-plan threshold and the attribution gate: the finding class "Sonar java" was `suppressed` eight times, all on files of pm-runtime (the job runner and its termination and launch classes). The step is `post_run_review: true` and runs after the merge, so it does not call `architecture enrich`; the hint is owed.

## Proposed action

Record one hint for module `pm-runtime`, verb `best-practice` (disposition `suppressed`), verbatim:

> In pm-runtime, prefer to suppress a Sonar java rule inline at the declaration, with a stated reason beside it, where the design requires the flagged shape (the rules seen: S2142, S3077, S6539, S1075), rather than reshaping the design to satisfy the rule or excluding the rule for the file.

## Evidence

- Eight `suppressed` Sonar findings on pm-runtime files in plan-20-job-runner, of which seven are distinct Sonar issues; one issue was filed more than once by successive roundtrips, so the count overstates the number of decisions.
- The generalisation names the preference only; no finding identifier or title is copied (privacy invariant of the disposition-to-hint contract).

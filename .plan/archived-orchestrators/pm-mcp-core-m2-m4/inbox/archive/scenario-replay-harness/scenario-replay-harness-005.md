envelope_version=1
sender_type=plan
sender_id=scenario-replay-harness
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-13T10:46:35Z

# Candidate lesson: Sonar S1130 on a `throws Exception` the pre-commit rewrite introduced

Source: Sonar finding 19cd34 (java:S1130, MINOR code smell, fixed in-run, commit f56a7f6). Related: Q-Gate 0efb60.

## What happened

ScenarioReplayRunnerTest.java:151 declared `throws Exception` although the body throws no checked exception
(`Files.readAllBytes` runs inside an `assertAll` lambda). The clause originated in the `verify -Ppre-commit`
OpenRewrite rewrite (previously `throws IOException`), which was accepted as mechanical in 5-execute.

## Fix applied in-run

The `throws` clause was removed.

## Candidate rule

A Sonar finding that traces back to a quality-gate rewrite is a signal that the rewrite acceptance step missed a
semantic change; see the 0efb60 candidate for the review rule. Possible upstream follow-up: check whether the
parent POM recipe that widens `throws` clauses should instead remove unthrowable ones.

Likely owner: pm-mcp-parent recipe set / project quality-gate guidance.

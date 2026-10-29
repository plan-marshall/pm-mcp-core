envelope_version=1
sender_type=plan
sender_id=scenario-replay-harness
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-13T10:46:38Z

# Candidate lesson: whole-tree pre-commit verify after the finalize rebase failed on stale test-class fixtures

Source: dispatcher-reported script-level friction (not tallied as a script-failure cluster)

## What happened

The first whole-tree `verify -Ppre-commit` after the finalize rebase onto main failed because `target/test-classes`
still held fixtures that upstream #48 had removed from the sources. The build passed only after `clean` was added
to the command.

## Candidate rule

After a rebase that pulls in upstream deletions of test resources or fixtures, run the quality gate with `clean`
(or the finalize rebase step should do so by default for Maven), since incremental builds keep deleted resources
in `target/`.

Likely owner: plan-marshall:phase-6-finalize (post-rebase verification) or plan-marshall:build-maven.

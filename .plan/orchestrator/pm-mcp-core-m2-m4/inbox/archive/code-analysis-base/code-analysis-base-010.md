envelope_version=1
sender_type=plan
sender_id=code-analysis-base
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-11T20:09:46Z

# Candidate lesson: a guarantee stated in Javadoc or "Done When" shipped without the mechanism that enforces it

Component: pm-mcp-core implementation practice (pm-core; self-review before PR)
Category (proposed): anti-pattern
Source: plan code-analysis-base, PR #31 review: 3 CodeRabbit findings fixed in-run (138e45, af2e0d, a6f260), plus 26 Sonar FIX items across two fix rounds

## Observation

All three CodeRabbit findings had the same shape: the code or its documentation claimed a property that the code did not enforce.

- `AnalysisResultCodec`: the Javadoc said the reader "accepts exactly that shape" and that a read line is "written again byte for byte", but `read` accepted whitespace, alternative escapes and `-0`, so `write(read(line))` could differ from the line. Fix: compare `write(result)` with the input and refuse a non-canonical spelling, with a refusal fixture per case.
- `AnalysisBoundaryArchTest`: the work package said the guard fails when a class "writes a file or references a network API", but its denylist missed `java.io.File` mutators, `PrintWriter`/`PrintStream` on a file, `Files.newByteChannel`, `AsynchronousFileChannel`, `FileSystemProvider` and the NIO socket channels. Fix: extend the rules and add a control fixture per family.
- `Finding` vs `FindingScope`: two types validating the same commit-SHA value disagreed (40 vs 40-or-64 characters).

The fix rounds also had to clear 26 Sonar items, which a local quality pass before opening the PR would have caught earlier.

## Proposed rule

Before opening the PR, check every "never / exactly / always / fails when" claim in Javadoc, package-info and the work package's Done-When against a test that would fail if the claim were false (a refusal fixture or a negative control fixture per covered family). Validation patterns for the same value must come from one constant shared by all types that check it. Run the quality gate (and Sonar-equivalent rules where available) before the PR rather than relying on the review bots.

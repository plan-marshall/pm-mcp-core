# Landing Analysis: PLAN-V2-170 — cut-log-and-statement-closeout

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #570 (merge `1328d390`, 30 files, +743/-162, after a base merge carrying PLAN-V2-167)

> Landing record, runbook-lane form (`rb-v2-170-cut-log-and-statement-closeout`, harness opencode, model `deepseek-v4.1-flash/high`, effort high, executor 0.1.2184, standing rules r7, 4 of 4 deliverables by the lane's count). Landing message `-001` (2026-11-22T22:51:25Z, `merge_state=merged`). `landing-check`: incomplete on `total_tokens` and `plugin_version` (the standing opencode Open Defect). Read by a read-only three-layer leaf; the orchestrator re-read F5 (`ProcessTreeTrackerTest.java:281-292`: the new check `imported.contain(ProcessGroupSignals.class)` is true by construction, the test imports that class itself) and the counting code of F1/F2 (`MavenOutputParser.java:422-452`), and confirmed that no reference to the old ADR file name remains. Stated by the lane, not re-verified: every mutation and failing-run log id, the test counts (12 663), Sonar 0 open, CodeRabbit dispositions, the links figure (`BROKEN LINKS 3253`, local 6, cross-repository 3247).

## Deliverable Fidelity vs Spec

| Deliverable (spec) | L1 specified | L2 implemented | L3 correct | Verdict |
|--------------------|--------------|----------------|------------|---------|
| D1 the cut flag in the daemon path (88a) | matches; the operator's "Pass the cut fact" | `JobLog.java:146-148` (`cut()` is `length > MAX_BYTES`), `SubprocessJobRunner.java:633,1585`, `BuildOutputParser.java:119,148`, `MavenOutputParser.java:201-204`; `atTheCap`, `overTheCap`, `cutMavenLogContradicts`, `parserReceivesTheCutFact`, `parserReceivesTheRecord` | the boundary is consistent; but `JobEvidence.ParseResult` (`:170`) carries no `TestCounts`, so `Unmeasured` never leaves `BuildOutputParser`: the only visible effect is the synthetic diagnostic and the contradiction flag; the Javadoc `BuildOutputParser.java:108-111` ("the one parser ... the others carry no population") is false (F3) | met (F3) |
| D2 the failing count of a cut tail (88b) | matches; the rule changed later in the lane | `MavenOutputParser.java:228-233,426-452`, seven `BuildLogTextTest` cases | the figure cannot go negative; but "never files twice" and "never loses a visible failure" are false in constructed cases (F1, F2); ADR `core-build/011:44-51` describes a precedence rule the code does not implement (F4) | partial |
| D3 statements, the rename, the restored sentences (88c) | matches | old file name gone everywhere; ADR 007, `package-info`, `03-parsers-and-gates.adoc:25`, ADR `workflow-consult/005`, `store-schemas/10` restored with working anchors | "filed as one error diagnostic" is inexact (F7) | met (statements partial) |
| D4 guards and races (88d) | matches | `RoleNameSingleDefinitionTest` decodes `\uXXXX` (really fails on the old code); `JobLog.readWhole` re-reads the size; `ConsultationService.java:185-226,594-607` draws the id under the queue lock | the consultation race between two opens is closed; the `ProcessTreeTrackerTest` guard is still vacuous (F5); the `JobLog` Javadoc over-claims a shrink-and-regrow (F6) | partial |

Surface outside the Expected Surface (`pm-runtime/doc/developer/runtime-job.adoc`, ADR `runtime-job/015`, seven lambda-arity tests, `ConsultTestLocks`) is mechanical consequence and package documentation.

## Findings

| id | severity | where | fact | in the lane's report |
|----|----------|-------|------|----------------------|
| F1 | minor | `MavenOutputParser.java:429-432` | a filed line on the side the rule did not choose is not subtracted: a per-class `[WARNING]` with 1 failure beside a summary `[ERROR]` with 1 failure gives a synthetic "1 further" for a failure already filed; a summary side that wins while per-class `[ERROR]` lines of another execution were filed counts those twice | partly (its residue admits an over-count by one block while saying "never files twice") |
| F2 | minor | `MavenOutputParser.java:429-430`, `DiagnosticCollector.java:56` | a visible failure can be lost: execution A's `[WARNING]` summary with 2 failures beside execution B's per-class `[ERROR]` lines with 2 and no summary give 2 - 2 = 0; `filed()` counts every `[ERROR]` line while the collector drops an identical one | no |
| F3 | minor | `BuildOutputParser.java:108-111`, `JobEvidence.java:170` | the Javadoc claim is false (Gradle, pyproject, Jest and TAP also build counts from the cut tail); `ParseResult` has no `TestCounts` | no |
| F4 | minor | ADR `core-build/011:47-49` | states the precedence "summary, else ambiguous, else per-class"; the code sums summary and ambiguous and takes the larger of that and the per-class lines; the over-count approximation is not recorded; the PR body repeats the stale rule | no |
| F5 | minor | `ProcessTreeTrackerTest.java:281-292` | the non-vacuity check is true by construction; the target is matched by the string constant `GROUP_SIGNALS` (`:63`), so a rename passes vacuously; the subject filter excludes `Member` and `Survivor`; the lane's mutation proves nothing about the guard | no |
| F6 | nit | `JobLog.java:221-224,246-249` | "caught if it shrank and regrew between two size reads" holds only when a read saw the shorter file | no |
| F7 | nit | `03-parsers-and-gates.adoc:25`, `core-build.adoc:195`, ADR 011:72 | "filed as one error diagnostic": per-class `[ERROR]` lines are each their own diagnostic | no |
| F8 | nit | `MavenOutputParser.java:255-256` | "1 further failing tests" | yes |
| F10 | nit | `ConsultationService.java:224` | the open is two transactions (record, then `queue.create`); a failed create relies on `reconcile`'s orphan grace and the asker stays `consulting` until it runs | no |

## Public API changes (the assembly `plan-marshall-mcp` follows)

`SubprocessJobRunner.OutputParser.parse(JobRecord, String)` becomes `parse(JobRecord job, String output, boolean outputCut)`: every assembly lambda must add the parameter after the `SNAPSHOT` deploys (ADR `runtime-job/015`). New public `JobLog.Complete.cut()`, `MavenOutputParser.parse(String, boolean cut)` (the one-argument form delegates with `false`), `BuildOutputParser.parse` with three arguments. Package-private: `JobLog.readWhole(FileChannel, long)`, the record `Window`. No schema or stored-format change. Test fixture `ConsultTestLocks.holdsAnywhere` (`pm-workflow:tests`).

## Residue and its placement

No staged plan owns `MavenOutputParser`, `BuildOutputParser`, `ProcessTreeTrackerTest` or the cut-log statements (PLAN-V2-162, running, edits `JobLog` for other items; PLAN-V2-125 and 126 own test conformance of their trees, not these guards). A new regular cleanup plan, **PLAN-V2-173** `cut-log-residue-closeout` (WS-11, runbook), carries item 91: F1 and F2 (the counting rule), F3 (the Javadoc and the question whether `Unmeasured` should reach `ParseResult`), F4 and F7 (the statements), F5 (the guard), F6 (the Javadoc), F8, F10. **Overlap:** PLAN-V2-162's PR #567 conflicts with the restructured `JobLog.readWhole` / `Window` (known; the continuation spec names it). **Recorded, not fixed:** the enum-body re-indentation and `throws Exception` of #545's code (the parent POM's pre-commit output; a Watch of the lane); the assembly Watch on the three-argument `OutputParser`; `Sourcery` is rate-limited for 16 h 31 min (optional). The links clause stays the operator's.

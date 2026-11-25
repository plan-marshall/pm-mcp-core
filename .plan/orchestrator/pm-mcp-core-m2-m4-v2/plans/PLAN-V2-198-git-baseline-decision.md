# PLAN-V2-198: The git-metadata baseline survives a restart and follows a legitimate git job: the operator's choice, its ADR, and the chosen implementation

epic: pm-mcp-core-m2-m4-v2
workstream: WS-06
origin: PLAN-V2-162 (#567) landing revision 25, section 9: item 41(a)
lane: runbook

> Staged plan spec, one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off. SELF-SUFFICIENT together with the standing rules.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`). They bind this plan exactly as if they were written below. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R05, R06, R10, R11, R13, R14, R19, R20 (the drafter's candidates, confirmed by the orchestrator against the rules file at staging; an id the lane finds not applicable it says so).

## Objective

`GitMetadataGuard` keeps the baseline in an in-memory `ConcurrentHashMap`.
- After a restart `verify` answers `Unchanged` with no baseline, and the next worker spawn baselines the tree as it is. Ending a worker does not revert what it wrote, so there is a window of undetected change.
- The baseline is only taken (`putIfAbsent`) or replaced by the operator (`rebaseline`). A legitimate server `git worktree add` or `push -u` after the first spawn changes the digested config, so the next `git` job is refused until the operator rebaselines.

This plan puts the choice to the operator, records it in an ADR first, and then implements exactly that choice with tests.

## Deliverables

1. **The decision (ASK-AT-REFINE, R07; the plan does not pick silently).** The outline asks one question with three options, the evidence below and the recommendation. The answer is recorded in a new ADR before any code.
   - **(1) Persist the baseline outside the process.** ADR 019 option C: a file below the project's store. Closes the restart window. It adds a layout entry, a schema, a codec and a parity test, and it must say what a torn or foreign file means.
   - **(2) Advance the baseline after a `git` job whose pre-check read `Unchanged`** (Q2-A, not built). Closes the false refusal. Hazard: ADR 019 keeps the baseline fixed so that a concurrent worker's write is not re-blessed. The advance must therefore be limited to the parts the job's own argv can change.
   - **(3) Refuse `git` jobs after a restart until the first record.** Closes the window by refusing. It adds a state and a refusal reason.
   - Evidence: options 1 and 3 address the restart window, and option 2 addresses the false refusal, so they are not alternatives for the same problem. The question must allow a combination (restart window: 1 or 3; legitimate change: 2 or keep the operator's `rebaseline`).
   - Recommendation: option 1 for the restart window (about 60 % sure) and keep `rebaseline` for the legitimate change unless the operator wants option 2.
   - Skills: plan-marshall:manage-adr is not needed; use pm-documents:ref-asciidoc
   - Persona: documenter
2. **The implementation of the answer, test first.** Controls per option (the chosen ones only):
   - (1): a second guard over the same store reads the tampered tree as `Changed`. The first spawn of a never-recorded project takes the baseline. A torn file, a foreign file or a version mismatch is refused fail-closed. `rebaseline` replaces the stored baseline. Also test two projects, and a store that cannot be written.
   - (2): a `git worktree add` and a `push -u` through the server advance the baseline. A worker's write between the pre-check and the post-check is NOT advanced (negative control). An `Unchanged` pre-check is the only trigger.
   - (3): the first `git` job after a restart is refused with its reason until the first record. A job after the record is admitted. A project without git is not refused.
   - Production change in `GitMetadataGuard`, its callers in `SubprocessJobRunner` (`record`, `verify`, the pre-check) and, for (1), a new store type with its schema (R10, R13: new format starts at version 1).
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
3. **Documents.** Guide `runtime-job.adoc` and `doc/plans/job-confinement-and-environment.adoc`, the confinement specification section on git metadata, PM-WATCH-JOB-48, Traceability, and an amended-by note in ADR `runtime-job/019` pointing at the new ADR.
   - Skills: pm-documents:ref-asciidoc
   - Persona: documenter

**Done when:**
- Deliverable 1 is answered and recorded before any code. Deliverables 2 and 3 are done for that answer only.
- Each new behaviour test fails at the post-#567 head of main with a failing run id. At 3ba79c4f the restart window is OBSERVED in the code (the guard's `baselines` map), so the window test is claimable as "fails at 3ba79c4f" once it exists.
- The existing guard and job tests keep their count per class and stay green.
- Build: `verify -Ppre-commit`, `verify` of `pm-runtime` (and `pm-core` for option 1), `verify -Pcoverage`, `verify -Pintegration-tests -pl pm-runtime -am` (and `-pl pm-core -am` for option 1). Use per-module runs with log ids if a full run exceeds the host's bound.
- Sonar: the open-finding read at the head is `0` or each key is selected for dismissal by the operator (`unknown` for a failed read, never `0`).

## Standing Constraints

- Read every cited line at the head first; name the commit of anything a merge settled. The plan starts after PR #567 merged, so claims on the branch are HYPOTHESIS.
- Do not pick an option before the operator answers. Do not widen the answer.
- No new dependency. No more than 100 files per PR. Pre-1.0: no shim, no migration of the in-memory form.
- A stored baseline never holds file contents, only the digest parts the guard already names.
- CLAUDE.md test and logging rules apply (a new `LogRecord` is a constant in `RuntimeLogMessages`, asserted with `LogAsserts`, with the next free id of its level). Each new test fails on the code it guards.
- Sonar dismissal only on the operator's selection, through `sonar_rest transition`; the lane never records one of its own.

## Claim Labels

- OBSERVED: `GitMetadataGuard` stores baselines in `ConcurrentHashMap<Path, SortedMap<String, String>>`. `record` uses `putIfAbsent`, `rebaseline` uses `put`, and `verify` answers `Unchanged` when no baseline exists. Read at 3ba79c4f.
- OBSERVED: callers in `SubprocessJobRunner` at `:560` (verify before a git job), `:1047` (record at spawn), `:1674` (verify after the fate).
- OBSERVED: ADR `runtime-job/019` at main names option C (a file below the project's store) and says "the guard's interface is the seam: a later plan changes only its storage". It also says "accepted because the recovery ends such a worker".
- HYPOTHESIS: on the 162 branch the same ADR and the guard Javadoc are reworded to say that ending the worker does not revert its writes. Confirm at `GitMetadataGuard.java` and the ADR once #567 merged.
- HYPOTHESIS: option 1 needs a project-store entry (`ProjectStore`), a schema and codec in `pm-core`, and a parity test. Confirm at `pm-core/src/main/java/de/planmarshall/core/store/ProjectStore.java`.
- Verify-first clause: the outline reads `SubprocessJobRunner` callers at the head. A caller added since may change which option is cheap.

## Expected Surface

9 existing files in every case. Option 1 adds about 6 new files (HYPOTHESIS), and the new ADR is 1.

- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/job/GitMetadataGuard.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/job/SubprocessJobRunner.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/job/GitMetadataGuardTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/job/GitMetadataJobTest.java`, `pm-runtime/doc/adr/runtime-job/019-git-metadata-digest-and-baseline.adoc`, `pm-runtime/doc/developer/runtime-job.adoc`, `doc/plans/job-confinement-and-environment.adoc`, `doc/specification/job-runtime/02-confinement-and-environment.adoc`, `doc/implementation-watch/job-runtime.adoc`
- HYPOTHESIS (option 1 only): `pm-core/src/main/java/de/planmarshall/core/store/ProjectStore.java`, `doc/specification/store-schemas.adoc`, a new baseline codec and schema in `pm-core/src/main/java/de/planmarshall/core/store/`, and their tests under `pm-core/src/test/java/de/planmarshall/core/store/`.
- HYPOTHESIS: a new ADR in `pm-runtime/doc/adr/runtime-job/`, numbered as the next free number of that directory at the head of main when the outline is written.

## Dependencies and Sequencing

- Depends on: PR #567 merged. This plan starts after it.
- Overlaps with: PLAN-V2-193 (`JobConfinementComposer`, `SubprocessJobRunner`) on `SubprocessJobRunner`. Sequence after it or rebase over it; the gate decides.
- Adjacent to: PLAN-V2-125 (not touched).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-198-git-baseline-decision.md
```

## Write-Boundary

The plan touches only its own repository source and tests. It creates and edits NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message. See `persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

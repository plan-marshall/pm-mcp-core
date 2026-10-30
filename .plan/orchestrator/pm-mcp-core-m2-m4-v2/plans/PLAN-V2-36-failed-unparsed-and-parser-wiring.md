# PLAN-V2-36: Wire the build output parsers into the job runner and make `failed_unparsed` carry its evidence

epic: pm-mcp-core-m2-m4-v2
workstream: WS-07
origin: PLAN-138 of the epic pm-mcp-core-m2-m4

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-36-failed-unparsed-and-parser-wiring.md` and is queued as one row file, `queue/PLAN-V2-36.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r4`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R16, R17, R18, R19, R20. Package bookkeeping (R01, R05): this plan is 4 of 7 plans of the package `build-parsers-and-gate-honesty` (`doc/plans/build-parsers-and-gate-honesty.adoc`). It deletes from the package file the bullets of _What_ and the _Done When_ paragraphs it delivers and drops its deliverables from the covered sections of the specification and watch items; the plan that delivers the last bullet removes the package's row from `doc/plans/README.adoc` and deletes the package file. The plans of the package edit different sections of the same documents; whichever merges second merges the base branch (the lane's step 7) before the merge gate. R14 and R16 apply: the plan changes `pm-runtime`.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-runtime/doc/developer/runtime-job.adoc` (existing, extended); `pm-core/doc/developer/core-build.adoc` (extended); ADR directory: `pm-runtime/doc/adr/runtime-job/` (the next free number of the directory); layout: workstream WS-10.
- R12 — log message holder: `RuntimeLogMessages` (`de.planmarshall.runtime.log`, prefix `PM_RUNTIME`) for a message of `runtime.job`; `CoreLogMessages` for `core.build`; next free identifier of the level, ranges INFO 1-99, WARN 100-199, ERROR 200-299 (ADR `doc/adr/005-one-log-message-holder-per-module.adoc`).

## Objective

Connect the nine native parsers to the job runner and deliver the `failed_unparsed` fallback of the work package `build-parsers-and-gate-honesty`: a build that exits non-zero with no structured diagnostic is `failed_unparsed` (never folded into `failed`), keeps its raw log beside the job record, and hands the gathering of its failure a bounded, redacted log-tail excerpt with the resource URI `pm://plan/{plan_id}/builds/{job_id}/log` and no invented diagnostic. The plan also opens the two seams of `SubprocessJobRunner` that PLAN-V2-51 (`adaptive-timeout-learning`) and PLAN-V2-55 (`gate-baseline-separation`) use, so that those plans add only new step classes.

## Deliverables

1. **The parser seam receives the job.** `SubprocessJobRunner.OutputParser.parse(JobId.Kind, String)` cannot tell which build tool wrote the log; change it to receive the job record (its kind and budget key `build:<system>:<verb>:<module>`), and call it with `current` at the existing call site in `settle`. Tests: the existing `SubprocessJobRunnerTest` cases stay green; a build key of each system reaches its parser.
2. **`BuildOutputParser`, the adapter** (new, `runtime.job`): selects the native parser by the build system of the key (Maven, Gradle, npm — with the TypeScript, ESLint, Jest or TAP scanner the log's tool markers select — and pyproject) and maps the parsed build to `JobEvidence.ParseResult`: the positive success marker of the tool, the number of located diagnostics, the contradicting evidence (parsed errors, failing test counts). A log no parser recognises yields `ParseResult.NOTHING`. Tests: one log per tool through the adapter into `OutcomeDerivation`; `succeeded` only with the positive marker (a zero count alone is not one).
3. **`failed_unparsed` with its evidence.** `OutcomeDerivation` already derives `failed_unparsed` for a build job with a non-zero exit and no parsed diagnostic (row 10); this deliverable adds `UnparsedEvidence` (new, `core.build`): the bounded excerpt — the last 50 lines of the raw log, at most 4 KB, secrets redacted through the existing `SecretRedactor` of `core.queue` — and the resource URI `pm://plan/{plan_id}/builds/{job_id}/log`; it carries no diagnostic, so the model cannot be handed one that was never parsed. The raw log stays beside the job record (`<job_id>.log`, as `JobLog` reads it). Tests: a 200-line log, a 10 KB single line and a line holding a secret; a build ended by a signal is `killed`, never `failed_unparsed` (the pair of row 4 and row 10).
4. **The settlement seam.** `JobSettlement.of(JobRecordStore, LockManager)` builds the fixed steps; add an overload that takes steps replacing the named seams (`CHANGE_LEDGER_ROW`, `TIMING_OBSERVATION`) and let `SubprocessJobRunner` be constructed with them; the existing factory and its callers (`JobRecoveryStep`, the runner, the tests) stay source-compatible, the default is the seams as today. Tests: a replaced step runs in the order of `SettlementStep.Name` and its `Failed` stops the settlement.
5. **Documentation.** Extend `pm-runtime/doc/developer/runtime-job.adoc` (the parser seam, the adapter, the unparsed evidence, the settlement overload) and `core-build.adoc`; the ADR of the seam change; delete the implemented § Unparseable Output Fallback of `doc/specification/job-runtime/03-parsers-and-gates.adoc` and PM-WATCH-JOB-7 and -14 where covered. Name, as a follow-up owed to `plan-marshall-mcp` after the `SNAPSHOT` deploy, the construction of the runner with the new parser and the settlement steps in the assembly (R16(2)). The failure envelope (`FailureEnvelope`, `evidence/`) and the gathering of the other classes are the package `remediation-units`; this plan produces the `unparsed` excerpt it will wrap.

**Done when:** A build log of each tool reaches its parser through the runner and decides `succeeded`, `failed` or `failed_unparsed` as the table of `OutcomeDerivation` says; a non-zero exit with no parseable diagnostic ends `failed_unparsed` with the raw log kept, an excerpt of at most 50 lines and 4 KB with secrets redacted, the resource URI, and no invented diagnostic; a build ended by a signal is `killed`; the settlement can be given replacement steps without a change to its existing callers; the developer guide and the ADR are written; the follow-up in `plan-marshall-mcp` is named in the pull request body. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (`pm-core`, `pm-runtime`; where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and the integration tests of every changed module with tests (`verify -Pintegration-tests -pl <module> -am`, only on `pm-core` and `pm-runtime`; here: `pm-core`, `pm-runtime`) are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-IMPL-6, PM-EXEC-6, PM-SEC-1
- Specification: `doc/specification/job-runtime/03-parsers-and-gates.adoc` § Unparseable Output Fallback and § Evidence Gathering per Failure Class (the `build` row); `doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc` § Outcome Derivation
- Watch: `doc/implementation-watch/job-runtime.adoc` PM-WATCH-JOB-7, PM-WATCH-JOB-14

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- Log messages: if a message is added for a refused or unreadable log, it goes into the holder of the module that logs it (R12); the plan says so in its inbox message.
- A change to `SubprocessJobRunner.OutputParser` is a change to a public interface of `pm-runtime` used by the assembly in `plan-marshall-mcp` (R16(2)): the outline says whether a source-compatible overload suffices and names the follow-up there.

## Claim Labels

- OBSERVED: the package file and its index row (`open`, depends on nothing) — `doc/plans/build-parsers-and-gate-honesty.adoc` (What, Trace, Done When) and `doc/plans/README.adoc` at `origin/main` `a8da4104`; the package is cut into seven plans because its _What_ has six bullets with about twenty testable parts
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/build/package-info.java` is the only file of the package `core.build` (15 lines, Javadoc naming `doc/specification/job-runtime/03-parsers-and-gates.adoc`) — read at `a8da4104`
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/job/OutcomeDerivation.java` line 134: `new Outcome(process.kind() == JobId.Kind.BUILD ? Fate.FAILED_UNPARSED : Fate.FAILED, 10)` — the non-zero exit without a parsed diagnostic; the table is documented at lines 27-44
- OBSERVED: `SubprocessJobRunner.java` line 439 `public interface OutputParser`, line 446 `ParseResult parse(JobId.Kind kind, String output)`; the call at lines 1462-1463 `parser.parse(current.kind(), complete.reading().jobOutput())` where `current` is the job record; `JobSettlement.of(store, lockManager)` is called at line 1531 of the runner, in `JobRecoveryStep.java` line 248 and in `JobSettlementTest.java` (lines 226, 243, 288, 302)
- OBSERVED: `SettlementStep.java` lines 60-62 name `CHANGE_LEDGER_ROW` and `TIMING_OBSERVATION`; `JobSettlement.java` lines 78-83 map them with `WORKER_QUEUE_TASK`, `METRICS_ROW` and `JOB_PRIVATE_DIRECTORY` to `SettlementStep.notImplemented(name)` — seams that do nothing
- OBSERVED: the raw log is `<job_id>.log` beside the job record (`JobLog.java` line 45); `SecretRedactor` exists in `pm-core/src/main/java/de/planmarshall/core/queue/SecretRedactor.java`; no `FailureEnvelope` type exists in `pm-core` or `pm-runtime` (`grep -rn 'FailureEnvelope\|excerpt_uri'` finds none)
- HYPOTHESIS: the build tool of a log is known from the budget key of the job record (`build:<system>:<verb>:<module>`, Job Runtime § Adaptive Timeout Learning) — confirm at `JobRequest`/`JobBudget` before changing the seam; the alternative is a parser registry keyed by the launched argument vector (verify-at-outline)
- HYPOTHESIS: a source-compatible overload of the `OutputParser` is not possible for a functional interface used as a lambda; the plan changes the interface and its callers in `pm-runtime`, and the assembly's construction is the named follow-up
- Verify-first clause: before editing, confirm that PLAN-V2-08 (`native-build-parsers-jvm`) and PLAN-V2-28 (`native-build-parsers-npm-and-python`) are merged, list the open pull requests touching `SubprocessJobRunner.java` or `JobSettlement.java` (PLAN-V2-42 `runtime-metrics` names the runner), and re-read the call sites above at the head; a refutation loops back to re-scope.

## Expected Surface

- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/job/SubprocessJobRunner.java` — the `OutputParser` interface and its call; the settlement construction
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/job/SubprocessJobRunnerTest.java` — adapted to the new parser signature
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/job/JobSettlement.java` — the overload that replaces seams
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/job/SettlementStep.java` — only if the overload needs a helper
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/job/JobSettlementTest.java` — the replacement-step cases
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/start/JobRecoveryStep.java` — only if its `JobSettlement.of` call must change (expected: no)
- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/job/BuildOutputParser.java` (new) and its test; `pm-core/src/main/java/de/planmarshall/core/build/UnparsedEvidence.java` (new) and its test
- OBSERVED: `doc/plans/build-parsers-and-gate-honesty.adoc`, `doc/specification/job-runtime/03-parsers-and-gates.adoc` (§ Unparseable Output Fallback), `doc/implementation-watch/job-runtime.adoc` (PM-WATCH-JOB-7, -14) — edited, section-disjoint with the sibling plans
- OBSERVED: `pm-runtime/doc/developer/runtime-job.adoc` — extended (existing guide, additive, shared bookkeeping once it exists)

## Dependencies and Sequencing

- Depends on: PLAN-V2-83 (`skills-conformance-foundation`: the dependencies and rule R20); and then: PLAN-V2-08 (`native-build-parsers-jvm`) and PLAN-V2-28 (`native-build-parsers-npm-and-python`) (the nine parsers it dispatches).
- Sequenced 2026-10-29 (decision 14 of 18): PLAN-V2-59 (`egress-record`) is the only plan of this group that edits the pinned job-record block of `store-schemas/10` (and `JobRecord`, `JobRecordCodec`); run it first and merge the base before your gate.
- Overlaps with: PLAN-V2-42 `runtime-metrics` (staged, depends on `plan-and-epic-control`) declares `SubprocessJobRunner.java` and `SubprocessJobRunnerTest.java`: 2 shared entries; whichever merges second merges the base branch first. PLAN-V2-51 (`adaptive-timeout-learning`) and PLAN-V2-55 (`gate-baseline-separation`) depend on this plan for the seams, so they share nothing with it. PLAN-46, PLAN-105, PLAN-V2-39 and PLAN-V2-43 do not name `runtime/job/`. PLAN-27 (job confinement) is shipped. PLAN-84 and PLAN-87, which named the runner, are shipped.
- Adjacent to: `remediation-units` (the failure envelope and the `build` gathering that wrap `UnparsedEvidence`; blocked, no spec); `plan-marshall-mcp` `pm-mcp-server` (the construction of the runner).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-36-failed-unparsed-and-parser-wiring.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

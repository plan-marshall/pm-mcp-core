# PLAN-20: Subprocess job runner

epic: pm-mcp-core-m2-m4
workstream: WS-04

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-20-job-runner.md` and is queued as one row file, `queue/PLAN-20.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `job-runner` of `doc/plans/job-runner.adoc`: subprocess job runner. Serves: Roadmap Milestone 4. Where: pm-runtime package `runtime.job`; job records in pm-core `core.store`. The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

## Deliverables

The bullets of `doc/plans/job-runner.adoc` § What, in its words (regenerated at 710da61, after the specification moved into this repository).

1. The runner through `pm-exec`: in-memory `stdin`, termination escalation, the write-ahead job record and log, the orphan rules of recovery, the fixed write order at job end. It implements the engine port `JobStarter` of pm-core; the module structure names the service interface `JobRunner`, which does not exist: the trace matrix settles whether the runner implements `JobStarter` or `JobRunner` is created in `core.spi`.
2. Termination for every cause of the outcome table, a pause of the plan that holds now among the engine's cancels (`cancelled`); the job log readable while the job runs, the source the worker's output is read from (its mapping is `worker-activity`'s).
3. Probe jobs (kind `probe`, `job_id` prefix `v`): a program found on the machine or in a checkout and not enrolled yet, run only after the consent of its kind; step 0 of the pipeline is the verification against the probe's candidate record (`executable_digest_mismatch`, `path_traversal_rejected`, `argument_rejected`), extending `verify-not-resolve`; the record and log in the store of its scope (`<PM_MCP_BASE>/machine/jobs/`, `<PM_MCP_BASE>/onboardings/<entry_id>/jobs/`) with `scope.type` `machine` or `entry`; the fixed budgets `probe:version`, `probe:models`, `probe:login`, and `probe:script`, without learning; rows 1 to 4 of the outcome derivation and the probe's success marker, otherwise the exit code and output read by the step that asked; never retried by itself. Its confinement and environment are `job-confinement-and-environment`'s.
4. `pm-exec` already makes the job the leader of its process group and reports its PID and start instant.

**Done when** (the package's own acceptance): A job that ignores `SIGTERM` is killed after the grace period with its whole group; a runtime killed at each point of the job's life recovers to the specified fate; the outcome table of the specification is covered row by row, the `probe` success marker included; a job of a plan paused now ends `cancelled`; a probe whose executable digest differs from its candidate record is refused `executable_digest_mismatch`, a probe over its fixed budget ends `timed_out` and is not started again, and its record and log lie in the store of its scope.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-EXEC-1, PM-EXEC-3, PM-EXEC-4, PM-ARCH-7
- Specification: Job Runtime § Pipeline & Subprocesses; Store Schemas § Job & Lesson Records; Identifiers; Job Runtime § Probe Jobs; Timeouts § Probe Budget
- Watch: Job Runtime; Store Schemas

## Standing Constraints

Set by the operator for every plan of this epic on 2026-10-09, beside the rules of `CLAUDE.md`:

- **Java 25 is the baseline.** Write for Java 25 and use its language and library features where they fit; no fallback for an older release. The release comes from the parent POM and is not overridden here.
- **HTTP calls go through `cui-http`.** No direct `java.net.http` or other HTTP client. The build forbids `cui-http` in `pm-core` and `pm-workflow`, so an HTTP call belongs in `pm-runtime` or a provider module, behind an interface the foundation declares.
- **HTTP tests use `cui-test-mockwebserver-junit5`.** Every test that involves HTTP runs against it; no hand-made fake server. The operator's statement is the consent `CLAUDE.md` requires for adding it as a test-scope dependency.
- **Work the package as the index says** (`doc/plans/README.adoc` § How to Work a Package, as reworded by #19): the usual plan-marshall flow — a branch, the commits of the plan, a pull request opened at finalize. No draft pull request claims the package; before starting, list the open pull requests for one that names it. The pull request removes the package's row from the index and deletes its package file within its commits, and drops the package from the _Depends on_ column of every row that names it. Build the trace matrix of `traced-implementation` before code, deliver the remainder the roadmap item leaves, and finish with `verify`, `verify -Ppre-commit`, `verify -Pcoverage` and `verify -Pintegration-tests` green.
- **Documents (operator decision 2026-10-15, pm-mcp-core ADR 007).** The specifications, the implementation watch and the traceability of the engine live in this repository: `doc/specification/`, `doc/implementation-watch/`, `doc/Traceability.adoc`, and the log message reference `doc/LogMessages.adoc`. This plan's one pull request carries the code, the tests, Javadoc and `package-info.java`, the developer documentation and ADRs, the deletion of the implemented specification sections and watch items, a correction of a specification statement it proves wrong, and the `Implementation:`, `Verified by:` and `Documentation:` lines of the requirements it implements in `doc/Traceability.adoc`. It opens a pull request in `plan-marshall-documentation` only to correct a requirement or to change `cross-cutting.adoc` or a specification that lives there; merging that one is the operator's. The concept and user documentation of the slice and the roadmap check-off are written there at the milestone exit: list them in the pull request body as **Owed to the milestone exit**.
- **Module documentation (operator decision 2026-10-11).** Documentation of the code lives in its module, in this plan's pull request, not in `plan-marshall-documentation`: the developer documentation of each package this plan changes in `pm-runtime/doc/developer/runtime-job.adoc`, `pm-core/doc/developer/core-store.adoc`, `pm-core/doc/developer/core-service.adoc` (create or extend it: what the package is for, its main types and how they work together, how to use and extend it, invariants and failure modes, links to the specification sections it implements), and one ADR per design decision taken — every operator decision and every choice between alternatives — in `pm-runtime/doc/adr/runtime-job/`, `pm-core/doc/adr/core-store/`, `pm-core/doc/adr/core-service/` as `NNN-{slug}.adoc`, the next free number of that directory, with the sections of `doc/adr/template.adoc` (Status, Date, Context, Decision, Alternatives considered, Consequences, Source) or, before that template exists, those same sections. A decision binding the whole repository goes to `doc/adr/`. The module's `README.adoc` gets one line only when this plan adds a new package; it is otherwise left alone. Layout: workstream WS-10.
- **Settled design: a refused project path is a value.** The confinement check that landed with #19 lives in `pm-core` package `core.store` (`PathConfinement`, `WorkspaceRoots`, `ConfinementOutcome`) and `pm-runtime` package `runtime.workspace` (`WorkspaceConfinement`). A refusal is returned as `ConfinementOutcome` carrying `path_traversal_rejected`; no checked exception type exists or is added. No consumer is wired to it yet (`pm-provider-git` included): a package that performs a file or repository operation for a project adopts the check itself.
- **Do not leave a design choice open that changes the file set.** Where two readings of a deliverable differ in the files they create, ask the operator at refine instead of deferring the choice to implementation.
- **Files shared by every plan.** In `doc/plans/README.adoc` remove only this package's own row (and its name from the _Depends on_ column); in `doc/Traceability.adoc` add only the sections of the requirements this package implements. Neither is a declared surface.
- **Log messages: the holder of your module, next free identifier** (operator decision 2026-10-13; pm-mcp-core #64, ADR `doc/adr/005-one-log-message-holder-per-module.adoc`). A new INFO, WARN or ERROR record goes into the holder of the module whose production code logs it, at the next free identifier of its level (INFO 1-99, WARN 100-199, ERROR 200-299, the same ranges in every holder); for this plan: `RuntimeLogMessages` (`de.planmarshall.runtime.log`, prefix `PM_RUNTIME`); `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`). A record two modules log goes to the lower one (`pm-core`); a module logs only through its own holder and `CoreLogMessages`. There are no workstream blocks and nothing is renumbered. A provider's first record creates its holder `de.planmarshall.provider.<x>.log.<X>LogMessages` (prefix `PM_PROVIDER_<X>`) with a `package-info.java`, and adds the prefix to `KNOWN_PREFIXES` in `CoreLogMessagesTest`. After a rebase, run the holder's test, which refuses an identifier used twice or outside its level's range, and move to the next free one. The same identifiers go into `doc/LogMessages.adoc` of this repository, under the holder's section.

- **Carried from PLAN-18 (#43, 2026-10-13).** (a) The caller closes its `LockTransaction` as soon as `VerifyNotResolvePipeline.verify` returns, never while the job launches or runs (`pm-runtime/doc/developer/runtime-job.adoc`). (b) Open review concern recorded in ADR runtime-job/001: `maxLength` bounds the input but not the regex matching time under the caller's enrolment lock; a per-match time budget (no new dependency) or a non-backtracking engine (new dependency — ask first) would close it. Decide at refine whether this package takes it or leaves it to `job-confinement-and-environment`, and say so.

- **Format versions stay 1 (operator decision 2026-10-13).** Nothing is released yet, so every versioned artifact (`format_version` of a store document, record, sidecar, journal, codec) stays at version 1. A format change replaces version 1 in place: no version bump, no reader for an older version, no migration. Do not raise a `FORMAT_VERSION` constant, a `format_version` in a fixture, schema or specification example, or an ADR that records a bump.

## Claim Labels

- OBSERVED: the Objective, Deliverables, Done when, Trace and Depends on above are the package file's and its index row's own — regenerated from `doc/plans/job-runner.adoc` and `doc/plans/README.adoc` at 710da61 (branch `docs/specification-into-core`, pm-mcp-core #77), after the specification moved into this repository and the package was aligned with the changed specification; the claims below were checked before that
  - verdict: corroborated | checked_at: 710da6150c6da19d4335643ca6070ef5517f0896 | by: pm-mcp-core-m2-m4/restructure | rescoped: n/a | evidence: regenerated by the orchestrator from the package file and its index row at 710da6150c6da19d4335643ca6070ef5517f0896 after the specification moved into pm-mcp-core (#77); § What, Trace, Done When and Depends on are the package's own
- OBSERVED: the deliverables, the trace starting set and the acceptance above are the package file's own — read at `doc/plans/job-runner.adoc` § What, Trace, Done When (origin/main at 7d99648)
  - verdict: corroborated | checked_at: a8a421408bfa31a4a64630c5139fd9e50bfac6f3 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: doc/plans package file re-read at a8a421408bfa31a4a64630c5139fd9e50bfac6f3: § What, Trace and Done When match the Deliverables above (corrected by cleanup where #70/#68/#72 changed them)
- OBSERVED: the index gives this package the status `open` and the dependencies `store-substrate`, `runtime-lifecycle`, `verify-not-resolve` — read at `doc/plans/README.adoc` § Packages
  - verdict: corroborated | checked_at: a8a421408bfa31a4a64630c5139fd9e50bfac6f3 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: index row re-read at a8a421408bfa31a4a64630c5139fd9e50bfac6f3: status and dependencies match the claim
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/job/` is no longer greenfield: it holds the verify-not-resolve pipeline of PLAN-18 (#43) — `JobRequest`, `VerifiedJob`, `JobVerification`, `VerifyNotResolvePipeline`, `ArgumentCheck`, `ArgumentSchemaValidator`, `ExecutableCheck`, `WrapperVerification`, `FileDigest`, `JobRefusal`, `DefaultTemplates`; this plan builds on those types instead of introducing its own — directory listing at fa58b0d on 2026-10-13 (re-grounded by cleanup)
  - verdict: corroborated | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: claim re-written by this cleanup from the directory listing at fa58b0d
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/` exists and holds `ConfinementOutcome.java`, `PathConfinement.java`, `WorkspaceRoots.java`, `package-info.java` today, so the package is an extension of those declarations — directory listing on 2026-10-09
  - verdict: unverifiable | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: not mechanically checkable at cleanup; verify-at-outline
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/service/` is no longer greenfield: it holds `LockManager`, `LockTransaction`, `AuditEvent`, `AuditSink` and `InternalFaultException` of PLAN-01 (#20), with `AuditEvent` extended by PLAN-06 (#26); PLAN-11 (#30) added `LiveWork` there. The job-runner interface joins these types — directory listing at fa58b0d on 2026-10-13 (re-grounded by cleanup)
  - verdict: corroborated | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: claim re-written by this cleanup from the directory listing at fa58b0d
- HYPOTHESIS: part of this package lands in `pm-core` package `core.service`; the package file does not name it, but the description of that package in the package table covers the subject — confirm/refute at `../plan-marshall-documentation/doc/specification/module-structure.adoc` § the package table of `pm-core` (verify-at-outline)
  - verdict: unverifiable | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line of the package file, not from a trace matrix — confirm/refute at the trace matrix of `traced-implementation` (verify-at-outline)
  - verdict: unverifiable | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- OBSERVED: each module that logs has one holder with its own test, which refuses an identifier used twice and one outside the range of its level (INFO 1-99, WARN 100-199, ERROR 200-299): `CoreLogMessages` (3 records), `RuntimeLogMessages` (54), `WorkflowLogMessages` (3); `PmMcpLogMessages` is deleted — read at branch `refactor/log-messages-per-module` head a777427 (pm-mcp-core #64, open on 2026-10-14); re-read on main before scoping
  - verdict: unverifiable | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: not mechanically checkable at cleanup; verify-at-outline
- HYPOTHESIS: the package adds INFO, WARN or ERROR messages; its module's log holder is deliberately NOT in the Expected Surface: a record is appended at the next free identifier and a collision is resolved on rebase, as the operator decided for the shared holder on 2026-10-09 and kept for the per-module holders on 2026-10-13 — confirm/refute the need at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- HYPOTHESIS: the package makes no HTTP call, so the two HTTP constraints bind only if the trace matrix shows one — confirm/refute at the specification sections of the trace starting set (verify-at-outline)
  - verdict: unverifiable | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- Verify-first clause: before scoping, read the roadmap item this package serves for what the code already covers, list the open pull requests for one that names `job-runner`, and check in `doc/plans/README.adoc` that every dependency is `done`; a refutation of any claim above loops back to re-scope.
  - verdict: unverifiable | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: verify-first clause is executed by the plan at refine

## Expected Surface

- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/job/` — production code
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/` — production code
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/service/` — production code; package inferred, verify-at-outline
- HYPOTHESIS: `pm-runtime/src/test/java/de/planmarshall/runtime/job/` — tests; new directory
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/store/` — tests
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/service/` — tests; new directory; package inferred, verify-at-outline
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only
- HYPOTHESIS: `pm-runtime/doc/developer/runtime-job.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-runtime/doc/adr/runtime-job/` — ADRs of the package's design decisions; new directory if absent
- HYPOTHESIS: `pm-core/doc/developer/core-store.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-core/doc/adr/core-store/` — ADRs of the package's design decisions; new directory if absent
- HYPOTHESIS: `pm-core/doc/developer/core-service.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-core/doc/adr/core-service/` — ADRs of the package's design decisions; new directory if absent
- OBSERVED: `doc/plans/job-runner.adoc` — the package file, deleted when the package is done
- OBSERVED: `doc/implementation-watch/job-runtime.adoc` — watch document; the covered items deleted
- OBSERVED: `doc/implementation-watch/store-schemas.adoc` — watch document; the covered items deleted
- OBSERVED: `doc/specification/identifiers.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/job-runtime/02-confinement-and-environment.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/10-job-and-lesson-records.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/timeouts/01-job-budgets-and-waits.adoc` — specification part; the implemented sections deleted

## Dependencies and Sequencing

- Depends on: nothing
- Overlaps with: PLAN-01 (`lock-manager-and-leases`) in `core.service`, `core.store`; PLAN-03 (`workspace-confinement`) in `core.store`; PLAN-06 (`store-substrate`) in `core.store`; PLAN-09 (`audit-trail`) in `core.store`; PLAN-11 (`enrolment-store`) in `core.store`; PLAN-12 (`session-log-and-generations`) in `core.store`; PLAN-13 (`workflow-engine`) in `core.store`; PLAN-14 (`operator-and-question-stores`) in `core.store`; PLAN-15 (`project-machine-store`) in `core.store`; PLAN-18 (`verify-not-resolve`) in `runtime.job`; PLAN-23 (`stalled-scope-detection`) in `core.store`; PLAN-27 (`job-confinement-and-environment`) in `runtime.job`; PLAN-31 (`slots-usage-and-budgets`) in `core.store`; every plan through `doc/plans/README.adoc`; the plans of the same module through its log holder, which is not a declared surface
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` and the client contract `pm-api` in `pm-mcp-clients`, which stay untouched — a change there is a pull request in that repository; and `plan-marshall-documentation`, where only a requirement correction or a change of `cross-cutting.adoc` or of a specification that lives there goes, in a pull request there

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-20-job-runner.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

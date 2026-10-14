# PLAN-56: Static verifier — the residual gaps after PLAN-54

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-56-static-verifier-residuals.md` and is queued as one row file, `queue/PLAN-56.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Close the residual gaps of the static verifier that PLAN-54 (#48, 36a0bcd) left. PLAN-54 completed
`static-verifier-rule-gaps` faithfully in most respects; an independent audit found a short list of
specification-defined checks still missing or deviating, and three documentation statements still
over-claiming (`landings/PLAN-54.md` of this epic). None of them blocks a running or next plan. This is
a small package: add it to the index as `static-verifier-residuals` with its own package file.

## Deliverables

Re-verify each item against HEAD and the specification (Workflow DSL § Static AST Verification Rules,
02-units-jobs-decisions, 08 for remediation) first; drop a refuted one and name it in the PR body.

1. **Package file and index row** `doc/plans/static-verifier-residuals.adoc`, depending on `static-verifier-rule-gaps`, set to `done` in this plan's commits.
2. **Rule 9 firing per step.** A firing ends at the next opening *of the same step* (specification), not at any other opening; refuse the duplicate "complete a, enter B's opening, complete a"; stop over-refusing a path that passes another step's opening before completing. Add a matched control for a re-entry that continues an open firing and for `skipped` on `infeasible`. Tighten STEP_LIST ("on the dispatch hub, every pass settles one step") and ARCHIVE_STEPS (the commit/land path) to the specification, or name the unchecked part as a seam in the package file.
3. **Rule 6 result guards everywhere.** Refuse a result guard on any transition other than a call's `done` (specification: "a result guard on any other transition … fails validation"); refuse a result guard on a callee that declares no `result` schema. The test catalog's `git.commit_and_push` either declares its result schema or its guarded callers change.
4. **Remediation budget re-check.** Replace the fail-open "any `remediation.*` unit" admission of `stepped_out` targets with the specification's state (`remediation-<class>.budget-check`) and primitive (`remediation.check-budgets`); correct ADR 010.
5. **Rule 2 `lost` omission** also in a sub-workflow that only plan units call (specification), with a fixture and a control; correct the checklist entry that restates the old code.
6. **Rule 3 activation.** Remove the invented all-keys-true activation that ADR 006 itself rejects, or record it as an explicit interim choice in an ADR with the specification issue's link; the issue "define how a `depends_on_config` value activates a guard" must exist in `plan-marshall-documentation` (file it if the operator has not).
7. **Documentation.** The developer guide's l.12 claim and ADR 001's "each atomic check has one refusing fixture" match the code; name the remaining unchecked parts (Rule 4 worker callees and the consultation-by-id heuristic, the 11a measure inside a callee, the per-phase fallback, escalate by id) as seams in the package file and the guide.

8. **Documentation repository.** Open the documentation PR the verifier plans never opened: retire or trim the Workflow DSL watch items PM-WATCH-DSL-3, 4, 5, 8, 9, 11, 15, 16, 29 and 32 that PLAN-41, PLAN-44, PLAN-54 and this plan implement, in `plan-marshall-documentation` `doc/implementation-watch/workflow-dsl.adoc` and the roadmap item; it must be merged before the landing message (the orchestrator checks it).

**Done when:** each item above is implemented to the specification or named as a seam with an ADR in the package file; each new check has a refusing fixture with an exact assertion and an entry in the `shouldCoverEveryCheck` checklist; no assertion weakened; the four gates green on the pushed head with their logs in the build results.

## Standing Constraints

Set by the operator for every plan of this epic on 2026-10-09, beside the rules of `CLAUDE.md`:

- **Java 25 is the baseline.** Write for Java 25 and use its language and library features where they fit; no fallback for an older release. The release comes from the parent POM and is not overridden here.
- **HTTP calls go through `cui-http`.** No direct `java.net.http` or other HTTP client. The build forbids `cui-http` in `pm-core` and `pm-workflow`, so an HTTP call belongs in `pm-runtime` or a provider module, behind an interface the foundation declares.
- **HTTP tests use `cui-test-mockwebserver-junit5`.** Every test that involves HTTP runs against it; no hand-made fake server. The operator's statement is the consent `CLAUDE.md` requires for adding it as a test-scope dependency.
- **Work the package as the index says** (`doc/plans/README.adoc` § How to Work a Package, as reworded by #19): the usual plan-marshall flow — a branch, the commits of the plan, a pull request opened at finalize. No draft pull request claims the package; before starting, list the open pull requests for one that names it. The pull request sets the package's row to `done` within its commits. Build the trace matrix of `traced-implementation` before code, deliver the remainder the roadmap item leaves, and finish with `verify`, `verify -Ppre-commit`, `verify -Pcoverage` and `verify -Pintegration-tests` green.
- **Documents.** Javadoc, `package-info.java` and the work package file live here. Requirement and specification documents, the user documentation, the deletion of implemented specification sections and watch items and the roadmap update are made directly in `plan-marshall-documentation` by this plan, in a pull request there that need not name the code pull request; merging it is the operator's.
- **Module documentation (operator decision 2026-10-11).** Documentation of the code lives in its module, in this plan's pull request, not in `plan-marshall-documentation`: the developer documentation of each package this plan changes in `pm-workflow/doc/developer/workflow-verify.adoc` (create or extend it: what the package is for, its main types and how they work together, how to use and extend it, invariants and failure modes, links to the specification sections it implements), and one ADR per design decision taken — every operator decision and every choice between alternatives — in `pm-workflow/doc/adr/workflow-verify/` as `NNN-{slug}.adoc`, the next free number of that directory, with the sections of `doc/adr/template.adoc` (Status, Date, Context, Decision, Alternatives considered, Consequences, Source) or, before that template exists, those same sections. A decision binding the whole repository goes to `doc/adr/`. The module's `README.adoc` gets one line only when this plan adds a new package; it is otherwise left alone. Layout: workstream WS-10.
- **Settled design: a refused project path is a value.** The confinement check that landed with #19 lives in `pm-core` package `core.store` (`PathConfinement`, `WorkspaceRoots`, `ConfinementOutcome`) and `pm-runtime` package `runtime.workspace` (`WorkspaceConfinement`). A refusal is returned as `ConfinementOutcome` carrying `path_traversal_rejected`; no checked exception type exists or is added. No consumer is wired to it yet (`pm-provider-git` included): a package that performs a file or repository operation for a project adopts the check itself.
- **Do not leave a design choice open that changes the file set.** Where two readings of a deliverable differ in the files they create, ask the operator at refine instead of deferring the choice to implementation.
- **One file is shared by every plan.** In `doc/plans/README.adoc` change only this package's own row.
- **Log messages: the holder of your module, next free identifier** (operator decision 2026-10-13; pm-mcp-core #64, ADR `doc/adr/005-one-log-message-holder-per-module.adoc`). A new INFO, WARN or ERROR record goes into the holder of the module whose production code logs it, at the next free identifier of its level (INFO 1-99, WARN 100-199, ERROR 200-299, the same ranges in every holder); for this plan: `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`). A record two modules log goes to the lower one (`pm-core`); a module logs only through its own holder and `CoreLogMessages`. There are no workstream blocks and nothing is renumbered. A provider's first record creates its holder `de.planmarshall.provider.<x>.log.<X>LogMessages` (prefix `PM_PROVIDER_<X>`) with a `package-info.java`, and adds the prefix to `KNOWN_PREFIXES` in `CoreLogMessagesTest`. After a rebase, run the holder's test, which refuses an identifier used twice or outside its level's range, and move to the next free one. The same identifiers go into `doc/LogMessages.adoc` of `plan-marshall-documentation`, under the holder's section, in the documentation pull request.

- **Build only through the project's commands.** Formatting comes from `verify -Ppre-commit` alone; never invoke a plugin the project does not declare (PLAN-41 ran Spotless from Maven Central). Run the four gates on the head you push, after the last commit.
- **Do not weaken a test to get green.** A failing existing test is either a real defect of this plan or a fixture that must be corrected to fit its own rule; record which.
- **Never restate the target to fit the result.** The package file, the ADRs and the developer guide follow the work order and the specification; where the code falls short, they say so.

- **Format versions stay 1 (operator decision 2026-10-13).** Nothing is released yet, so every versioned artifact (`format_version` of a store document, record, sidecar, journal, codec) stays at version 1. A format change replaces version 1 in place: no version bump, no reader for an older version, no migration. Do not raise a `FORMAT_VERSION` constant, a `format_version` in a fixture, schema or specification example, or an ADR that records a bump.

## Claim Labels

- OBSERVED: the firing ends at any other opening (`openings.contains(target) && !target.equals(opening)`), while the specification says "the next opening of the same step" — read at `pm-workflow/src/main/java/de/planmarshall/workflow/verify/WorkflowValidator.java` (580815e) and `../plan-marshall-documentation/doc/specification/workflow-dsl/04-ast-node-model.adoc`, re-checked by the orchestrator
  - verdict: corroborated | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: pm-workflow/src/main/java/de/planmarshall/workflow/verify/WorkflowValidator.java exists at fa58b0d (presence checked, content not re-read)
- OBSERVED: result guards are checked only inside the handling of `call` states, while the specification refuses a result guard on any other transition — read at `pm-workflow/src/main/java/de/planmarshall/workflow/verify/WorkflowValidator.java` (580815e), re-checked by the orchestrator
  - verdict: corroborated | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: pm-workflow/src/main/java/de/planmarshall/workflow/verify/WorkflowValidator.java exists at fa58b0d (presence checked, content not re-read)
- OBSERVED: deliverables 4 to 7 are findings of the independent audit of 36a0bcd, recorded in `landings/PLAN-54.md`, not each re-checked by the orchestrator — read at `pm-workflow/doc/adr/workflow-verify/010-*.adoc`
  - verdict: unverifiable | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: pm-workflow/doc/adr/workflow-verify/010-*.adoc not addressable at fa58b0d as written
- Verify-first clause: re-run every item against HEAD; list open PRs naming `static-verifier-residuals`; a refuted item is dropped and named.
  - verdict: unverifiable | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: verify-first clause is executed by the plan at refine

## Expected Surface

- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/` — production code
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/verify/` — tests
- OBSERVED: `pm-workflow/src/test/resources/verify/` — fixtures
- OBSERVED: `doc/plans/README.adoc` — this package's own row only
- HYPOTHESIS: `doc/plans/static-verifier-residuals.adoc` — new package file
- OBSERVED: `pm-workflow/doc/developer/workflow-verify.adoc` — developer documentation
- OBSERVED: `pm-workflow/doc/adr/workflow-verify/` — ADRs corrected and added

## Dependencies and Sequencing

- Depends on: PLAN-54 (`static-verifier-rule-gaps-completion`), shipped
- Blocks: nothing (orchestrator judgement 2026-10-13: the residuals are minor and do not block PLAN-17)
- Overlaps with: PLAN-17 (`project-units-and-invariants`) in `workflow.verify` — run after it or before it, never together; PLAN-40 through `pm-workflow/doc/developer/`

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-56-static-verifier-residuals.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

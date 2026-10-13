# PLAN-40: Developer documentation in every module for the packages already shipped

epic: pm-mcp-core-m2-m4
workstream: WS-10

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-40-module-developer-docs.md` and is queued as one row file, `queue/PLAN-40.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Write the developer documentation of every package shipped so far, one file per package below its
module's `doc/developer/`, so that code and its documentation live and change together. Several
such guides were written into `plan-marshall-documentation` by the plans; they move here. Layout of
WS-10: `{module}/doc/developer/{package}.adoc`, the package name with dots replaced by dashes.

## Deliverables

1. **`pm-core/doc/developer/`** (PLAN-13 created `core-store.adoc` there on 2026-10-13 — extend it, do not overwrite):
   - `core-store.adoc`, which takes over `doc/developer/store-substrate.adoc` and the store part of
     `locks-and-leases.adoc` from `plan-marshall-documentation`, and covers the confinement
     (`PathConfinement`, `WorkspaceRoots`, `ConfinementOutcome`) and the enrolment store if PLAN-11
     shipped;
   - `core-service.adoc` (lock manager interface, audit events and sink, internal fault);
   - `core-spi.adoc`, which takes over `doc/developer/primitive-spi.adoc` (`plan-marshall-documentation`
     #20, merged or not — read it from its branch if open) and covers `core.spi.validation`;
   - `core-log.adoc`: how to add a log record, the per-workstream identifier blocks, `PmMcpLogMessagesTest`;
   - `core-analysis.adoc` and `core-findings.adoc`, if PLAN-36 shipped;
   - one file for every other package with production code.
2. **`pm-workflow/doc/developer/`**: `workflow-dsl.adoc` (takes over `doc/developer/workflow-dsl.adoc`
   of `plan-marshall-documentation`, covering `workflow.dsl` and `workflow.ast`), and
   `workflow-verify.adoc` if PLAN-10 shipped.
3. **`pm-runtime/doc/developer/`**:
   - `runtime-lock.adoc` (takes over the runtime part of `locks-and-leases.adoc`; one instance per
     runtime; the POSIX channel-close caveat of `JsonlStoreFile`);
   - `runtime-start.adoc` (service unit; runtime lifecycle if PLAN-08 shipped);
   - `runtime-workspace.adoc`;
   - one file for every other package with production code.
4. **The provider modules**: `doc/developer/` in `pm-provider-ci` (the shared HTTP base through
   `cui-http`, testing on `cui-test-mockwebserver-junit5` with single-use route responses and
   mock-server URLs), `-github`, `-gitlab`, `-git` and `-sonar`, one file per package with
   production code.
5. **A report of the moved guides** in the PR body: for each guide taken over from
   `plan-marshall-documentation`, its old path, its new path, and what was dropped as a copy of the
   specification. The deletion there and the pointer from its `DeveloperGuide.adoc` are made by the
   documentation-repository session (`.plan/temp/doc-structure-single-pr-plan.md`), not by this
   plan.

Each file explains what the package is for, its main types and how they work together, how to use
and extend it (with the shape a new implementation follows, where there is one), its invariants
and failure modes, and links to the specification sections it implements (absolute GitHub links
into `plan-marshall-documentation`) and to the package's ADRs. It does not repeat Javadoc
type-by-type; it links the types relatively.

**Done when:** every package with production code in every module has its developer file; the
taken-over guides are complete here; relative links resolve.

## Standing Constraints

- Documentation only: no production code, no test, no POM change. The Maven gates have nothing to
  build; say so rather than reporting them green.
- Write from the code as it is on `main`; a statement the code contradicts is corrected, not
  copied from an older guide.
- Never copy the module listing or the dependency rules of the Module Structure Specification; link them.
- The usual flow of `CLAUDE.md` § Git Workflow. No pull request in `plan-marshall-documentation`.
- Log messages: none.

## Claim Labels

- OBSERVED: `plan-marshall-documentation` holds `doc/developer/store-substrate.adoc` and `locks-and-leases.adoc` on main; `primitive-spi.adoc` is in its open #20; `workflow-dsl.adoc` came with its merged #27 — read on 2026-10-11
  - verdict: unverifiable | checked_at: c0d4b92f568923098a36992b6c22e78100b84028 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: not mechanically checkable at cleanup; verify-at-outline
- OBSERVED: no `doc/` exists below any module of this repository — directory listing at d091527
  - verdict: unverifiable | checked_at: c0d4b92f568923098a36992b6c22e78100b84028 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: not mechanically checkable at cleanup; verify-at-outline
- HYPOTHESIS: the package list per module is the set of `package-info.java` files below `src/main/java` — confirm/refute at the first module (verify-at-outline)
  - verdict: unverifiable | checked_at: c0d4b92f568923098a36992b6c22e78100b84028 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- Verify-first clause: before writing, check which of PLAN-08, PLAN-10, PLAN-11 and PLAN-36 have shipped and whether PLAN-39 already created any module `doc/`; check whether the documentation-repository session already removed or moved a guide, and take the content from wherever it is now.
  - verdict: unverifiable | checked_at: c0d4b92f568923098a36992b6c22e78100b84028 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: verify-first clause is executed by the plan at refine

## Expected Surface

- HYPOTHESIS: `pm-core/doc/developer/` — new directory
- HYPOTHESIS: `pm-workflow/doc/developer/` — new directory
- HYPOTHESIS: `pm-runtime/doc/developer/` — new directory
- HYPOTHESIS: `pm-providers/pm-provider-ci/doc/developer/` — new directory
- HYPOTHESIS: `pm-providers/pm-provider-github/doc/developer/` — new directory
- HYPOTHESIS: `pm-providers/pm-provider-gitlab/doc/developer/` — new directory
- HYPOTHESIS: `pm-providers/pm-provider-git/doc/developer/` — new directory
- HYPOTHESIS: `pm-providers/pm-provider-sonar/doc/developer/` — new directory

## Dependencies and Sequencing

- Depends on: PLAN-08, PLAN-10, PLAN-11, PLAN-36 (running when staged; their packages belong in the backfill)
- Overlaps with: PLAN-39 none; later plans write `doc/developer/{package}.adoc` of their own packages — a later plan in a package this plan documents (for example `core.store`) extends the same file, so the two are sequenced by the gate
- Adjacent to: `plan-marshall-documentation`, untouched by this plan

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-40-module-developer-docs.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

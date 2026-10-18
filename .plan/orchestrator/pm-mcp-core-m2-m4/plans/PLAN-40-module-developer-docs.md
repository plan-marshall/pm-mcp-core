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

Re-grounded by cleanup on 2026-10-16 against main at 238c823. Since staging, every module plan writes
the files of its own packages (CLAUDE.md § Module Documentation), so fifteen files exist already:
`pm-core` `core-queue`, `core-role-corpus`, `core-store`; `pm-runtime` `runtime-audit`, `runtime-corpus`,
`runtime-job`, `runtime-start`; `pm-workflow` `workflow-diagram`, `workflow-engine`, `workflow-finalize`,
`workflow-plan`, `workflow-scenario`, `workflow-unit`, `workflow-verify`; `pm-provider-git` `provider-git`.
25 packages with production code still have none. This plan writes the missing ones, and folds into the existing ones the guides
that `plan-marshall-documentation` still holds below `doc/developer/` (ten files on its main;
`primitive-spi.adoc` merged with #20).

1. **`pm-core/doc/developer/`** — new: `core-analysis.adoc` and `core-findings.adoc` (take over
   `findings-and-analysis.adoc`), `core-service.adoc` (lock manager interface, `LiveWork`, audit
   events and sink, internal fault; the core part of `locks-and-leases.adoc`), `core-spi.adoc`
   (takes over `primitive-spi.adoc`, covers `core.spi.validation`), `core-log.adoc` (how to add a
   log record — written from the holder layout the log-holder session plan lands, see Dependencies),
   `core-toon.adoc`. Extend `core-store.adoc`, never overwrite it: take over `store-substrate.adoc`
   and `enrolment-store.adoc` (`EnrolmentStore`, `EnrolmentSidecar`, `EnrolmentCodec`,
   `ProjectEnrolment`) and the confinement types, where the file does not cover them yet.
2. **`pm-workflow/doc/developer/`** — new: `workflow-dsl.adoc` and `workflow-ast.adoc` (take over
   `workflow-dsl.adoc` of `plan-marshall-documentation`, split by package), `workflow-classification.adoc`.
   The seven existing files (`workflow-plan` among them) stay; correct only ADR references they get wrong.
3. **`pm-runtime/doc/developer/`** — new: `runtime-credentials.adoc` (with `runtime.credentials.dbus`),
   `runtime-enrolment.adoc` (the runtime half of `enrolment-store.adoc`: check, HMAC, key, policy,
   snapshot), `runtime-ingest.adoc`, `runtime-lock.adoc` (the runtime part of `locks-and-leases.adoc`;
   one instance per runtime; the POSIX channel-close caveat of `JsonlStoreFile`), `runtime-lsp.adoc`,
   `runtime-security.adoc`, `runtime-web.adoc`, `runtime-workspace.adoc`. Extend `runtime-start.adoc`
   with what `runtime-lifecycle.adoc` holds and the file does not.
4. **The provider modules** — new `doc/developer/` in each: `provider-ci.adoc` (the shared HTTP base
   through `cui-http`, testing on `cui-test-mockwebserver-junit5` with single-use route responses and
   mock-server URLs), `provider-github.adoc`, `provider-gitlab.adoc`,
   `provider-sonar.adoc`; `provider-git.adoc` exists already and is extended where it does not cover the module, never overwritten.
5. **A report of the moved guides** in the PR body: for each of the seven package guides taken over
   (`store-substrate`, `enrolment-store`, `locks-and-leases`, `primitive-spi`, `findings-and-analysis`,
   `runtime-lifecycle`, `workflow-dsl`), its old path, its new path(s), and what was dropped as a copy
   of the specification. `project-setup.adoc` and `registry-setup.adoc` are process documents and
   stay; `assembly-boundary.adoc` describes plan-marshall-mcp code and is not taken over. The deletion
   in `plan-marshall-documentation` and the pointer from its `DeveloperGuide.adoc` are made by the
   documentation-repository session (`.plan/temp/doc-structure-single-pr-plan.md`), not by this plan.

Scope-bloat note: five deliverables, about 22 new files and three extended ones. Documentation only,
one file per package, so it stays one plan; split by module at outline if the plan judges otherwise.

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

- **Format versions stay 1 (operator decision 2026-10-13).** Nothing is released yet, so every versioned artifact (`format_version` of a store document, record, sidecar, journal, codec) stays at version 1. A format change replaces version 1 in place: no version bump, no reader for an older version, no migration. Do not raise a `FORMAT_VERSION` constant, a `format_version` in a fixture, schema or specification example, or an ADR that records a bump.

- **Carried from PLAN-39 (#53).** PLAN-39 backfilled ADRs and renumbered them after collisions (at 238c823 core-store runs to 028, runtime-start to 011, workflow-verify to 025). Developer files that cite ADR ranges (for example `workflow-verify.adoc`: "ADRs 001 to 012") must cite the current ones; check every ADR reference in the files you write.

## Claim Labels

- OBSERVED: `plan-marshall-documentation` main holds ten files below `doc/developer/`: `assembly-boundary`, `enrolment-store`, `findings-and-analysis`, `locks-and-leases`, `primitive-spi`, `project-setup`, `registry-setup`, `runtime-lifecycle`, `store-substrate`, `workflow-dsl` — read on 2026-10-13 (re-grounded by cleanup)
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: git ls-files doc/developer in plan-marshall-documentation at 6881b7e lists exactly the ten files named; none has moved or been removed yet
- OBSERVED: fifteen module developer files exist (`core-queue`, `core-role-corpus`, `core-store`; `runtime-audit`, `runtime-corpus`, `runtime-job`, `runtime-start`; `workflow-diagram`, `workflow-engine`, `workflow-finalize`, `workflow-plan`, `workflow-scenario`, `workflow-unit`, `workflow-verify`; `provider-git`) and 25 packages with production code have none; none of the seven taken-over guides has a counterpart in a module yet — `git ls-files` of main at 238c823 on 2026-10-16 (re-grounded by cleanup)
  - verdict: contradicted | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: yes | evidence: at 238c823 fifteen module developer files exist, not ten: also core-role-corpus, provider-git, runtime-audit, runtime-corpus, workflow-plan; 25 packages with production code still have none; claim rewritten
- HYPOTHESIS: the package list per module is the set of packages with production code below `src/main/java` — confirm/refute at the first module (verify-at-outline)
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: the plan lists equal the packages with a non-package-info class below src/main/java; package-info-only packages are rightly absent
- Verify-first clause: before writing, check that the log-holder session plan has landed, which module plans landed developer files since 2026-10-13, and whether the documentation-repository session already removed or moved a guide; take the content from wherever it is now.
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: the preconditions the clause checks hold at 238c823: the index row is open and not blocked, and no open pull request names the package

## Expected Surface

- OBSERVED: `pm-core/doc/developer/` — new files; `core-store.adoc` extended
- OBSERVED: `pm-workflow/doc/developer/` — new files
- OBSERVED: `pm-runtime/doc/developer/` — new files; `runtime-start.adoc` extended
- HYPOTHESIS: `pm-providers/pm-provider-ci/doc/developer/` — new directory
- HYPOTHESIS: `pm-providers/pm-provider-github/doc/developer/` — new directory
- HYPOTHESIS: `pm-providers/pm-provider-gitlab/doc/developer/` — new directory
- HYPOTHESIS: `pm-providers/pm-provider-git/doc/developer/` — exists (`provider-git.adoc`); extended
- HYPOTHESIS: `pm-providers/pm-provider-sonar/doc/developer/` — new directory
- HYPOTHESIS: `{module}/README.adoc` — the links to the new developer files

## Dependencies and Sequencing

- Depends on: nothing in the index. The log-holder plan has landed (ADR `doc/adr/005-one-log-message-holder-per-module.adoc`; `CoreLogMessages`, `RuntimeLogMessages`, `WorkflowLogMessages` exist), so `core-log.adoc` is written from that result. PLAN-08, PLAN-10, PLAN-11 and PLAN-36 have shipped (re-grounded 2026-10-16).
- **Precondition, found by cleanup on 2026-10-16: this plan has no package file and no index row.** `doc/plans/module-developer-docs.adoc` and a row of `doc/plans/README.adoc` do not exist, and neither ever did, although the Standing Constraints of every plan have the pull request remove the row and delete the file. The index is the only place that states a package's status, so the row stays parked until a `docs(plans)` change in this repository adds the package file and its row; then it is staged again, and the Standing Constraints hold as written.
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

# PLAN-40: Developer documentation in every module for the packages already shipped

epic: pm-mcp-core-m2-m4
workstream: WS-10

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-40-module-developer-docs.md` and is queued as one row file, `queue/PLAN-40.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r1`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R13.

## Objective

Write the developer documentation of every package shipped so far, one file per package below its
module's `doc/developer/`, so that code and its documentation live and change together. Several
such guides were written into `plan-marshall-documentation` by the plans; they move here. Layout of
WS-10: `{module}/doc/developer/{package}.adoc`, the package name with dots replaced by dashes.

## Deliverables

Re-grounded by cleanup on 2026-10-21 against main at 3d4baf7; the package file `doc/plans/module-developer-docs.adoc` and its index row came with #126 (PLAN-89). Since staging, every module plan writes
the files of its own packages (CLAUDE.md § Module Documentation), so 21 files exist already:
`pm-core` `core-role-corpus`, `core-service`, `core-store`; `pm-runtime` `runtime-audit`, `runtime-corpus`,
`runtime-credentials`, `runtime-enrolment`, `runtime-job`, `runtime-question`, `runtime-start`; `pm-workflow` `workflow-ast`, `workflow-diagram`,
`workflow-dsl`, `workflow-engine`, `workflow-finalize`, `workflow-plan`, `workflow-scenario`, `workflow-unit`, `workflow-verify`; `pm-provider-git` `provider-git`, `provider-git-ported`
(`core-queue` is gone with PLAN-64). A directory scan finds 16 packages of the three engine modules with production code and none
(`core-analysis`, `core-findings`, `core-log`, `core-spi`, `core-spi-validation`, `core-toon`; `runtime-credentials-dbus`, `runtime-ingest`, `runtime-lock`, `runtime-log`, `runtime-lsp`, `runtime-security`, `runtime-web`, `runtime-workspace`; `workflow-classification`, `workflow-log`), beside the four provider modules `pm-provider-ci`, `-github`, `-gitlab` and `-sonar`. This plan writes the missing ones, and folds into the existing ones the guides
that `plan-marshall-documentation` still holds below `doc/developer/` (ten files on its main;
`primitive-spi.adoc` merged with #20).

1. **`pm-core/doc/developer/`** — new: `core-analysis.adoc` and `core-findings.adoc` (take over
   `findings-and-analysis.adoc`), `core-spi.adoc`
   (takes over `primitive-spi.adoc`, covers `core.spi.validation`), `core-log.adoc` (how to add a
   log record — written from the holder layout the log-holder session plan lands, see Dependencies),
   `core-toon.adoc`. Extend `core-service.adoc` (it exists now) with what the core part of `locks-and-leases.adoc` holds and it does not: the lock manager interface, `LiveWork`, audit events and sink, internal fault. Extend `core-store.adoc`, never overwrite it: take over `store-substrate.adoc`
   and `enrolment-store.adoc` (`EnrolmentStore`, `EnrolmentSidecar`, `EnrolmentCodec`,
   `ProjectEnrolment`) and the confinement types, where the file does not cover them yet.
2. **`pm-workflow/doc/developer/`** — new: `workflow-classification.adoc` (`workflow-log` is covered by `core-log.adoc`'s section on the holders, or gets its own file: decide at outline). `workflow-dsl.adoc` and `workflow-ast.adoc` exist now (PLAN-69): fold into them what `workflow-dsl.adoc` of `plan-marshall-documentation` still holds and they do not, split by package.
   The nine existing files (`workflow-plan` among them) stay; correct only ADR references they get wrong.
3. **`pm-runtime/doc/developer/`** — new: `runtime-ingest.adoc`, `runtime-lock.adoc` (the runtime part of `locks-and-leases.adoc`;
   one instance per runtime; the POSIX channel-close caveat of `JsonlStoreFile`), `runtime-lsp.adoc`,
   `runtime-security.adoc`, `runtime-web.adoc`, `runtime-workspace.adoc` (`runtime-log` as for `workflow-log`). Extend `runtime-credentials.adoc` with `runtime.credentials.dbus`, `runtime-enrolment.adoc` with the runtime half of `enrolment-store.adoc` (check, HMAC, key, policy, snapshot) where they do not cover it, and `runtime-start.adoc`
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

Scope-bloat note: five deliverables, about 14 new files and eight extended ones. Documentation only,
one file per package, so it stays one plan; split by module at outline if the plan judges otherwise.

Each file explains what the package is for, its main types and how they work together, how to use
and extend it (with the shape a new implementation follows, where there is one), its invariants
and failure modes, and links to the specification sections it implements (absolute GitHub links
into `plan-marshall-documentation`) and to the package's ADRs. It does not repeat Javadoc
type-by-type; it links the types relatively.

**Done when:** every package with production code in every module has its developer file; the
taken-over guides are complete here; relative links resolve.

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- Documentation only: no production code, no test, no POM change. The Maven gates have nothing to
  build; say so rather than reporting them green.

- Write from the code as it is on `main`; a statement the code contradicts is corrected, not
  copied from an older guide.

- Never copy the module listing or the dependency rules of the Module Structure Specification; link them.

- The usual flow of `CLAUDE.md` § Git Workflow. No pull request in `plan-marshall-documentation`.

- Log messages: none.

- **Carried from PLAN-39 (#53).** PLAN-39 backfilled ADRs and renumbered them after collisions (at 238c823 core-store runs to 028, runtime-start to 011, workflow-verify to 025). Developer files that cite ADR ranges (for example `workflow-verify.adoc`: "ADRs 001 to 012") must cite the current ones; check every ADR reference in the files you write.

## Claim Labels

- OBSERVED: `plan-marshall-documentation` main holds ten files below `doc/developer/`: `assembly-boundary`, `enrolment-store`, `findings-and-analysis`, `locks-and-leases`, `primitive-spi`, `project-setup`, `registry-setup`, `runtime-lifecycle`, `store-substrate`, `workflow-dsl` — `git ls-tree origin/main doc/developer` at `b8c61b3` on 2026-10-22 (re-grounded by cleanup)
  - verdict: corroborated | checked_at: 7756dfd6a98067e2e331b40211784ebc0f605c25 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: git ls-tree origin/main doc/developer at plan-marshall-documentation b8c61b3: the same ten files
- OBSERVED: 21 module developer files exist (`core-role-corpus`, `core-service`, `core-store`; `runtime-audit`, `runtime-corpus`, `runtime-credentials`, `runtime-enrolment`, `runtime-job`, `runtime-question`, `runtime-start`; `workflow-ast`, `workflow-diagram`, `workflow-dsl`, `workflow-engine`, `workflow-finalize`, `workflow-plan`, `workflow-scenario`, `workflow-unit`, `workflow-verify`; `provider-git`, `provider-git-ported`) and a directory scan finds 16 packages of `pm-core`, `pm-runtime` and `pm-workflow` with production code and none: `core-analysis`, `core-findings`, `core-log`, `core-spi`, `core-spi-validation`, `core-toon`, `runtime-credentials-dbus`, `runtime-ingest`, `runtime-lock`, `runtime-log`, `runtime-lsp`, `runtime-security`, `runtime-web`, `runtime-workspace`, `workflow-classification`, `workflow-log` (the provider modules `pm-provider-ci`, `-github`, `-gitlab`, `-sonar` are not scanned); one of the seven taken-over guides has a counterpart in a module now: `workflow-dsl` (PLAN-69) — `find . -path '*/doc/developer/*.adoc'` and the package directories at 99971f3 on 2026-10-21 (re-grounded by cleanup)
  - verdict: corroborated | checked_at: 7756dfd6a98067e2e331b40211784ebc0f605c25 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: ls */doc/developer at 7756dfd: the same 21 files; the 16 packages without one were counted by directory scan on 2026-10-21 and no package gained or lost production code since (git diff --stat 99971f3..HEAD over src/main is test-and-fix only)
- HYPOTHESIS: the package list per module is the set of packages with production code below `src/main/java` — confirm/refute at the first module (verify-at-outline)
  - verdict: unverifiable | checked_at: 7756dfd6a98067e2e331b40211784ebc0f605c25 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- Verify-first clause: before writing, check that the log-holder session plan has landed, which module plans landed developer files since 2026-10-13, and whether the documentation-repository session already removed or moved a guide; take the content from wherever it is now.
  - verdict: corroborated | checked_at: 7756dfd6a98067e2e331b40211784ebc0f605c25 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: row None is open in the index at 7756dfd; open PRs (ci pr list): #129 (PLAN-85) and #71 (a dependabot bump), none names the package; None last changed in None; the claims above were re-read individually
- OBSERVED: the package file `doc/plans/module-developer-docs.adoc` exists and the index row `module-developer-docs` is `open` and depends on nothing — read at `origin/main` `7756dfd` (added by #126, PLAN-89)
  - verdict: corroborated | checked_at: 7756dfd6a98067e2e331b40211784ebc0f605c25 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: module-developer-docs.adoc exists and the index row is open at 7756dfd

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
- **Precondition met (2026-10-21).** The package file `doc/plans/module-developer-docs.adoc` and its index row (`open`, depends on nothing, Milestones 2 to 4) were added by #126 (PLAN-89, `c45a968`); the Standing Constraints hold as written: the plan's pull request removes the row and deletes the package file. The package file's four bullets (one file per package, the guides that move here, existing files extended and never overwritten, written from `main`) are covered by the deliverables below, which are the finer-grained brief.
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

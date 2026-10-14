# PLAN-39: Module READMEs, the ADR template, and ADRs for the decisions already shipped

epic: pm-mcp-core-m2-m4
workstream: WS-10

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-39-module-readmes-and-adrs.md` and is queued as one row file, `queue/PLAN-39.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Give every module of this repository its `README.adoc` and the decisions already shipped their
ADRs, and state the module documentation rule where plans read it. The operator found on
2026-10-11 that the modules have no README, no module documentation and no ADRs. This plan is the
backfill for READMEs and ADRs; PLAN-40 backfills the developer documentation. Plans started after
the rule write their own share as they implement.

## Deliverables

1. **The rule and the layout.**
   - State the layout of WS-10 in `CLAUDE.md` (a new short section "Module Documentation") and in
     `doc/plans/README.adoc` § How to Work a Package (the "Documents" bullet):
     - `{module}/README.adoc`;
     - `{module}/doc/developer/{package}.adoc`;
     - `{module}/doc/adr/{package}/NNN-{slug}.adoc`;
     - `doc/adr/NNN-{slug}.adoc` for repository-wide decisions.
   - A plan writes the developer documentation of the packages it changes and one ADR per design
     decision it takes, in its own pull request.
   - Edit only that section of the index, no package row.
2. **The ADR template** `doc/adr/template.adoc` and the index `doc/adr/README.adoc`.
   - The template has the sections: Status (proposed / accepted / superseded by …), Date, Context,
     Decision, Alternatives considered, Consequences, Source (plan, pull request, decision-log
     entry).
   - The index explains the numbering per directory and links the module ADR directories.
3. **A `README.adoc` for every module**: `pm-core`, `pm-workflow`, `pm-runtime`, the aggregator
   `pm-providers`, and `pm-provider-git`, `-ci`, `-github`, `-gitlab`, `-sonar`. Each has:
   - one paragraph on its purpose;
   - a link to the Module Structure Specification
     (`../plan-marshall-documentation/doc/specification/module-structure.adoc` as an absolute
     GitHub link) for responsibilities and allowed dependencies, which are never copied;
   - its packages with one line each, taken from their `package-info.java`;
   - the build and test commands of `CLAUDE.md` for that module;
   - links to `doc/developer/` and `doc/adr/`.

   Link the module READMEs from the root `README.md`.
4. **ADRs for the decisions already shipped.** One ADR per decision below that the verification
   confirms. Each cites its source (pull request, landing record, archived plan decision log under
   `.plan/local/archived-plans/*/logs/decision.log`) and states the alternatives that were on the
   table. A candidate the sources do not bear out is dropped and listed in the PR body.

**Done when:**
- every module has a README;
- `doc/adr/` has the template and the index;
- every confirmed decision has an ADR;
- `CLAUDE.md` and the index state the rule;
- the AsciiDoc renders without broken links (relative links resolve).

## Candidate decisions (verify each; drop what the sources do not confirm)

- Repository-wide (`doc/adr/`):
  - the Java 25 baseline;
  - HTTP calls through `cui-http` and HTTP tests on `cui-test-mockwebserver-junit5`, route responses single-use (#28);
  - log identifiers per workstream in the three-digit scheme, not four digits per plan (epic decision 2026-10-09);
  - module documentation layout (this plan).
- `pm-core`:
  - `core.store`:
    - a refused project path is a `ConfinementOutcome` value, no checked exception (#19);
    - the parent directory is forced after the atomic move (#20);
    - leases are released owner-checked (#20);
    - transaction journal `format_version` 2 with owed audit events, and `ScopeTransaction.commit` refuses while an unrecovered journal exists (#26).
  - `core.spi`: the decisions of #18 the decision log records.
- `pm-runtime`:
  - `runtime.lock`: one `FileLockManager` per runtime, with reference-counted eviction of per-key entries (#20);
  - `runtime.start`:
    - the systemd uninstall order disable → delete → unload (#25);
    - owner-only file modes 0600/0700 (#25).
- `pm-workflow`:
  - `workflow.dsl` / `workflow.ast`:
    - the instance header follows the `PlanInstanceHeader` store schema;
    - `question` is a reserved word and the adjudication unit is `adjudicate.*`;
    - the scope clause is mandatory;
    - `jackson-core` in `pm-workflow` (#27).
- The plans running when this was staged — PLAN-08 `runtime-lifecycle` (#29), PLAN-10 `static-verifier-and-fuzzer`, PLAN-11 `enrolment-store` (#30), PLAN-36 `code-analysis-base` (#31): the decisions their landings and decision logs record.

## Standing Constraints

- Documentation only: no production code, no test, no POM change. The Maven gates have nothing to
  build; say so rather than reporting them green.
- Write in the style of the existing AsciiDoc of this repository (`doc/plans/`): short sentences,
  no marketing.
- Never copy the module listing or the dependency rules of the Module Structure Specification;
  link them (CLAUDE.md § Project).
- An ADR records a decision that was taken. Invent no rationale: where the sources give none, say
  "rationale not recorded".
- The usual flow of `CLAUDE.md` § Git Workflow: branch, commits, PR, checks, review comments
  answered. No documentation PR in `plan-marshall-documentation`.
- Log messages: none.

- **Concurrent ADR numbering (operator decision 2026-10-13).** PLAN-12, PLAN-17 and PLAN-21 run alongside and write ADRs into `pm-core/doc/adr/core-store/`, `pm-workflow/doc/adr/workflow-unit/` / `workflow-verify/` / `workflow-finalize/` and `pm-workflow/doc/adr/workflow-engine/`. Several package ADR directories already exist from PLAN-13, PLAN-16, PLAN-18, PLAN-22, PLAN-41/44/54 (`ls pm-*/doc/adr/`). Backfill into the existing numbering (next free number per directory); immediately before pushing, rebase onto `origin/main` and renumber any backfilled ADR whose number a merged plan took meanwhile, fixing every link to it. Do not renumber another plan's ADR. Several module `doc/developer/` files and `doc/adr/` directories exist already — the READMEs link to them; extend, never overwrite.

## Claim Labels

- OBSERVED: no module has a README (2026-10-13); module `doc/` trees now exist in `pm-core`, `pm-workflow` and `pm-runtime` from the plans shipped since WS-10 — directory listing of `main` at 115d7ed (re-grounded 2026-10-13)
  - verdict: unverifiable | checked_at: c0d4b92f568923098a36992b6c22e78100b84028 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: not mechanically checkable at cleanup; verify-at-outline
- OBSERVED: the modules are `pm-core`, `pm-workflow`, `pm-runtime` and `pm-providers` with `pm-provider-git`, `-ci`, `-github`, `-gitlab`, `-sonar` — read at `pom.xml` and `pm-providers/pom.xml` § `modules`
  - verdict: corroborated | checked_at: c0d4b92f568923098a36992b6c22e78100b84028 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: pom.xml exists at c0d4b92 (presence checked, content not re-read)
- OBSERVED: the candidate decisions come from the landing records `landings/PLAN-01` to `PLAN-07` of this epic and the epic decisions — read on 2026-10-11
  - verdict: unverifiable | checked_at: c0d4b92f568923098a36992b6c22e78100b84028 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: not mechanically checkable at cleanup; verify-at-outline
- HYPOTHESIS: the archived plan decision logs below `.plan/local/archived-plans/` of the main checkout carry the operator decisions with their alternatives — confirm/refute at the first ADR (verify-at-outline)
  - verdict: unverifiable | checked_at: c0d4b92f568923098a36992b6c22e78100b84028 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- Verify-first clause: before writing, check whether PLAN-08, PLAN-10, PLAN-11 and PLAN-36 have shipped (their decisions belong in the backfill) and whether any module already gained a README or `doc/` since 2026-10-11; extend rather than overwrite.
  - verdict: unverifiable | checked_at: c0d4b92f568923098a36992b6c22e78100b84028 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: verify-first clause is executed by the plan at refine

## Expected Surface

- OBSERVED: `CLAUDE.md` — the new section "Module Documentation"
- OBSERVED: `README.md` — links to the module READMEs
- OBSERVED: `doc/plans/README.adoc` — § How to Work a Package only, no package row
- HYPOTHESIS: `doc/adr/` — template, index, repository-wide ADRs; new directory
- HYPOTHESIS: `pm-core/README.adoc` — new file
- HYPOTHESIS: `pm-workflow/README.adoc` — new file
- HYPOTHESIS: `pm-runtime/README.adoc` — new file
- HYPOTHESIS: `pm-providers/README.adoc` — new file
- HYPOTHESIS: `pm-providers/pm-provider-git/README.adoc` — new file
- HYPOTHESIS: `pm-providers/pm-provider-ci/README.adoc` — new file
- HYPOTHESIS: `pm-providers/pm-provider-github/README.adoc` — new file
- HYPOTHESIS: `pm-providers/pm-provider-gitlab/README.adoc` — new file
- HYPOTHESIS: `pm-providers/pm-provider-sonar/README.adoc` — new file
- HYPOTHESIS: `pm-core/doc/adr/` — new directory
- HYPOTHESIS: `pm-workflow/doc/adr/` — new directory
- HYPOTHESIS: `pm-runtime/doc/adr/` — new directory

## Dependencies and Sequencing

- Depends on: PLAN-08, PLAN-10, PLAN-11, PLAN-36 (running when staged; their decisions belong in the backfill)
- Overlaps with: PLAN-40 none (it writes `doc/developer/` only); every later plan writes into `{module}/doc/adr/{package}/` of its own packages, never into this plan's files except the README row of a new package
- Adjacent to: `plan-marshall-documentation`, untouched; the session plan `.plan/temp/doc-structure-single-pr-plan.md` states the matching rule there

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-39-module-readmes-and-adrs.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

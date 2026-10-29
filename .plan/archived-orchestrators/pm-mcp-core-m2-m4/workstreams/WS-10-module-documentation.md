# WS-10: Module documentation

epic: pm-mcp-core-m2-m4

> Charter document for one workstream — a coherent slice of the epic with its own goal
> and surface. Lives at `workstreams/WS-10-module-documentation.md` and is tracked in the
> `workstreams[]` field of the epic header, `status.json`. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier contract.

## Charter

Every module of this repository documents itself: a `README.adoc` at the module root, and all
other documentation below the module's `doc/` — the developer documentation per package and the
architecture decision records (ADRs). The workstream backfills what the plans already shipped
without it, and states the rule so that every later plan writes its share as it implements.
It closes when every module has its README and its `doc/`, and the backfill covers every plan
shipped before the rule.

## Layout (operator decision 2026-10-11, details by the orchestrator)

- `{module}/README.adoc` — what the module is for (one paragraph, with a link to the Module
  Structure Specification for its responsibilities and dependency rules, never a copy of them),
  its packages with one line each, how to build and test it, and links into its `doc/`. This
  covers the aggregator `pm-providers/` too.
- `{module}/doc/developer/{package}.adoc` — the developer documentation of one package, named
  after the package (`core.store` → `core-store.adoc`). It explains how the package is meant to be
  used and extended; Javadoc and `package-info.java` stay the reference of the types.
- `{module}/doc/adr/{package}/NNN-{slug}.adoc` — one ADR per decision: context, decision,
  alternatives considered, consequences, status, date, and the plan and pull request it came from.
  Numbered per package directory, so two plans of different packages never take the same number.
- `doc/adr/NNN-{slug}.adoc` — decisions that bind the whole repository, with the shared template
  `doc/adr/template.adoc`.

## Scope

- In scope: the module READMEs, the module `doc/` trees, `doc/adr/`, the rule in `CLAUDE.md` and
  in `doc/plans/README.adoc` § How to Work a Package
- Out of scope: product code and tests; the requirements, specifications, concepts and user
  documentation of `plan-marshall-documentation`

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-39-module-readmes-and-adrs | staged | The rule, the template, every module README, ADRs for the decisions already shipped |
| PLAN-40-module-developer-docs | staged | `doc/developer/` for every package already shipped, taking over the guides now in `plan-marshall-documentation` |

## Sequencing and Surface Notes

- PLAN-39 and PLAN-40 touch disjoint files and can run together.
- Both wait for the plans running when they were staged (PLAN-08, PLAN-10, PLAN-11, PLAN-36), so
  the backfill covers every plan shipped before the rule. Plans started after 2026-10-11 write
  their own module documentation as a Standing Constraint.
- The plain Claude Code session plan `.plan/temp/doc-structure-single-pr-plan.md` (documentation
  repository rules) decided, by this layout, where code documentation goes; its moves of guides
  into this repository are done by PLAN-40.

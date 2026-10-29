# WS-09: Project configuration

epic: pm-mcp-core-m2-m4

> Charter document for one workstream — a coherent slice of the epic with its own goal
> and surface. Lives at `workstreams/WS-09-project-configuration.md` and is tracked in the
> `workstreams[]` field of the epic header, `status.json`. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier contract.

## Charter

Changes to how this repository is worked rather than to the product: plan-marshall settings and
CI configuration that the landings of the other workstreams show to be in the way. It closes when
no Open Defect of the epic names a configuration cause.

## Scope

- In scope: `.plan/marshal.json`, the caller workflows below `.github/workflows/`, `.github/project.yml`
- Out of scope: product code and the work packages of `doc/plans/`; the reusable workflows of `cuioss/cuioss-organization`, which are changed in that repository

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-38-review-bot-re-review | staged | Ask the review bot again after a fix commit |

## Sequencing and Surface Notes

- Plans here touch configuration files no product plan declares, so they pair with anything.

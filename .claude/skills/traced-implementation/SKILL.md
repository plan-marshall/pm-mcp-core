---
name: traced-implementation
description: Pointer. The mandatory frame for every concrete implementation in plan-marshall-mcp lives in the repository plan-marshall/plan-marshall-documentation; read and follow .claude/skills/traced-implementation/SKILL.md there before planning or implementing anything here.
---

# Traced Implementation (pointer)

The skill lives with the documents it works on, in the repository `plan-marshall/plan-marshall-documentation`:

- checked out beside this repository: `../plan-marshall-documentation/.claude/skills/traced-implementation/SKILL.md`
- on GitHub: https://github.com/plan-marshall/plan-marshall-documentation/blob/main/.claude/skills/traced-implementation/SKILL.md

Read that file and follow it. If the documentation repository is not checked out, clone it beside this one first
(`gh repo clone plan-marshall/plan-marshall-documentation ../plan-marshall-documentation`).

What it means here: the specifications, the implementation watch and the traceability of this repository live here
(`doc/Specification.adoc`, `doc/ImplementationWatch.adoc`, `doc/Traceability.adoc`; ADR
`doc/adr/007-specifications-of-the-engine-in-this-repository.adoc`). The plan that implements a change makes one pull
request in this repository with the code, the tests, the developer documentation, the deletion of the implemented
specification sections and watch items, and the traceability lines (the skill's § Where the Documents Live). It opens
a documentation pull request there only to correct a requirement or to change `cross-cutting.adoc` or a specification
that lives there; that one is merged after the pull request here.

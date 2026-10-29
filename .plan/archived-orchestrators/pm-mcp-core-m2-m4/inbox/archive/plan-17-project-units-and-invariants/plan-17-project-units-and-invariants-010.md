envelope_version=1
sender_type=plan
sender_id=plan-17-project-units-and-invariants
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-13T20:09:39Z

# Index non-standard resource files such as .pmwf in the architecture inventory

**Component (suggested):** plan-marshall:manage-architecture
**Category (suggested):** improvement

## Context

In plan-17-project-units-and-invariants the 4-plan Q-Gate leaf had to check the success criterion "no
fixture parsed as a default unit carries `derived_from`". It could not list the `.pmwf` fixtures:
`architecture find --pattern "*.pmwf"` returned 0, `architecture search --content` never hit a `.pmwf`
file, and the harness denied `Grep` and `Glob` to the subagent. The leaf reported the gap correctly, but the
criterion was only partly checked; a third offending fixture would only have shown up when tests ran.

## Root cause

The architecture inventory walks only file types it recognises, so project-specific resource formats (here
the workflow DSL `.pmwf` under `src/test/resources`) are invisible to the structured search that is the
leaf's sanctioned discovery path.

## Proposed action

Include every git-tracked file under a module root in the inventory (or let the project declare extra
extensions), so `find` and `search --content` cover resource files a plan edits.

## Evidence

- aspect: chat_history_analysis: Q-Gate leaf report, "find --pattern *.pmwf returned 0 and search --content never hit them; Grep and Glob were denied"
- aspect: permission_prompt_analysis: tool denial without a prompt, recorded as a coverage gap
- aspect: outline_vs_shipped: 11 `.pmwf` fixtures under unit-fixtures/defaults are in the realized footprint

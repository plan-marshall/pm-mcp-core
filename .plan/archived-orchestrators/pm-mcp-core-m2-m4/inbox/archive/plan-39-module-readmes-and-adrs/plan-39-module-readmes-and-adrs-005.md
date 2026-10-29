envelope_version=1
sender_type=plan
sender_id=plan-39-module-readmes-and-adrs
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-13T18:10:00Z

component=plan-marshall:phase-3-outline
category=anti-pattern
source_plan=plan-39-module-readmes-and-adrs

# An index of survey results was placed in a deliverable that runs before the survey

## What happened

In plan 39's outline, deliverable 2 (no dependencies) wrote the ADR index `doc/adr/README.adoc`. The index was supposed to link only the per-package ADR directories that deliverables 3 to 6 would create, and those deliverables would also decide which candidates to drop. But deliverables 3 to 6 depended on deliverable 2, so the index would have been written before the survey decided its contents, and no later deliverable touched the index again. The 3-outline Q-Gate caught this (finding 31452c). The fix moved the index into deliverable 7, which depends on deliverables 3 to 6.

## Rule

An artifact that summarizes or lists the results of other deliverables (an index, a README listing, a registry) must be in a deliverable that depends on all of them. Do not put it in a setup deliverable just because it is a "scaffolding" file. Check this when writing the outline: for each aggregate file, the deliverables whose outcomes it reflects must come before it in the dependency order.

## Evidence

3-outline Q-Gate finding 31452c in plan-39-module-readmes-and-adrs, resolved taken_into_account.

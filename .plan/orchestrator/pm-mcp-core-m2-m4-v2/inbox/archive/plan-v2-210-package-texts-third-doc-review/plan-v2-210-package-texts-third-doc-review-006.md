envelope_version=1
sender_type=plan
sender_id=plan-v2-210-package-texts-third-doc-review
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-27T19:22:02Z

# Candidate lesson: package said no package builds an event that a sibling package builds

- Source: review-bot finding ff1860 (PR #636, coderabbitai, Minor, Data Integrity), resolution fixed (TASK-8, commit bc1440f2)
- Component: doc/plans/finalize-prune-and-land-files.adoc:15 and doc/plans/README.adoc:301

## Observation

finalize-prune-and-land-files and its index row said no package of the index builds the `realized` composition event, while phase-sub-workflows.adoc:21 assigns its composition and line 40 lists its test. The index row listed no dependency, so the mutation-prune task could be scheduled before the event it reads.

## Candidate rule

Before stating "no package of this index builds X", search the sibling package texts under doc/plans/ for X; when one owns it, name it as owner and add it as a dependency in the index row.

Classification deferred to the orchestrator.

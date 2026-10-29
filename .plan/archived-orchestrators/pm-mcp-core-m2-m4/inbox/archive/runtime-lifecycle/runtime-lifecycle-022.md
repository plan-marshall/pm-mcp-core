envelope_version=1
sender_type=plan
sender_id=runtime-lifecycle
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-11T20:19:11Z

component=plan-marshall:manage-references
category=improvement
signal=plan-retrospective artifact-consistency (verified)
evidence=outline fix-pass leaf a9a0a53: "sync-affected-files only adds paths, so the 6 old ../plan-marshall-documentation/... paths are still there. It holds 58 entries against the 53 the outline declares, and I found no remove command for it"

# references.affected_files cannot drop paths an outline revision replaced

## Observation

The 3-outline fix pass rewrote deliverable 11's six relative `../plan-marshall-documentation/...` paths as absolute paths. `sync-affected-files` is add-only, so the six stale relative entries stayed in `references.affected_files` (58 entries vs 53 declared) and there is no verb to remove them. Every later consumer of the declared footprint (artifact consistency, manifest cross-check, scope checks) then compares against a list containing paths that no deliverable declares any more.

## Suggested direction

Give `sync-affected-files` a reconcile mode that replaces the list with exactly the outline's current declared set (reporting added and removed paths), or add a `remove-affected-files` verb; have the outline revision path call the reconcile mode.

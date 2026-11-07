envelope_version=1
sender_type=plan
sender_id=plan-v2-115-schema-files-foundation
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-07T12:24:11Z

# PLAN-V2-115 landed: schemas as files, the foundation (PR #311 merged, documentation PR #160 open)

Standing rules: r6, R01, R02, R05, R06, R08, R10 (replaced), R11, R17, R18, R19, R20. I read `standing-rules.md` at revision r6 (2026-11-02) before the outline.

What landed in `pm-mcp-core` (PR #311, merged through the merge queue as `d69959e6`):

- ADR 016 `doc/adr/016-schemas-are-files-of-the-implementing-module.adoc`, the ADR index row, and the status lines of ADR 007 and ADR 012.
- The test helper `SchemaResource` with `SchemaResourceTest` and four fixtures in `pm-core/src/test`, published through `pm-core:tests`. `SpecificationDocument.parse` delegates to it.
- The developer documentation of the build: `pm-core/README.adoc` and `CLAUDE.md`.
- The package file `doc/plans/schema-files-foundation.adoc` is deleted. Its index row stays `done` without a link, and the seven dependent package files name it by plain id.

Second pull request, in `plan-marshall-documentation`: PR #160 (branch `docs/schema-files-foundation`, commit `96281f2`). It carries the `trace.py` include and inline-schema checks with `test_trace.py` (8 tests), the Project Setup sentences, and the `traced-implementation` and `doc-review` skills. The survey of the shared specifications found no hit. PR #160 is open and not merged: the merge waits for the operator's word.

```landing-facts
schema=landing-facts/1
plan_id=plan-v2-115-schema-files-foundation
epic=pm-mcp-core-m2-m4-v2
pr=#311
merge_state=merged
cleanup_owed=false
deliverables_total=6
deliverables_done=6
total_tokens=24109624
total_wall_seconds=73307.0
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,finalize-step-preference-emitter:done,plan-marshall:plan-retrospective:done,record-metrics:done,finalize-step-print-phase-breakdown:done,archive-plan:done
harness=claude
model=claude-sonnet-5-5
effort=medium
plugin_version=0.1.2041
step.branch-cleanup.merge_mechanism=merge_queue
step.branch-cleanup.merge_state=merged
step.functional-review.deliverables_met=3
step.functional-review.deliverables_met_with_residuals=3
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=2
step.code-review.rules_cited=2
step.sonar-roundtrip.open_issue_count=0
step.sonar-roundtrip.open_issue_rules=none
```

## Residue

Open items for the orchestrator and the operator:

1. **Documentation PR #160 is open.** Deliverables 5 and 6 live there. Until it merges, the `INLINE SCHEMA BLOCKS 0` check that `CLAUDE.md` now names cannot be run against `main`. The documentation worktree `.plan/temp/plan-v2-115-documentation` and the link-check folder `.plan/temp/plan-v2-115-linkcheck` still exist and are removed after PR #160 merges.
2. **Merged without a CodeRabbit review at the final head.** CodeRabbit was quota-refused at `32ef3c5` and Sourcery's budget was spent. The operator ruled "if there was at least one CodeRabbit review then merge, if not, wait"; the check run on the earlier head `f4165cf` reads "Review completed", so the merge went through under a recorded `barrier-ask-override`. The review gap is on record against head `32ef3c5`. Code review and functional review closed clean (0 blocking), and SonarCloud on PR 311 reported 0 open issues with the gate OK.
3. **Two advisory code-review findings, left as follow-ups.** The body of ADR 012 (line 66) still says the five generator artifacts carry only their generator package, while `pm-core:tests` now also carries `SchemaResource`. `SchemaResourceTest.refusesDocumentsThatAreNoSingleSchemaObject` lacks a `@DisplayName`.
4. **Defects found by the code review after execute reported done, all fixed inline before the merge:** the tests jar missed the synthetic class `SchemaResource$1`; the parser accepted empty, trailing and duplicate-member input; malformed UTF-8 was replaced silently. This is why the fixtures `invalid-encoding.schema.json` and `not-object.schema.json` exist beyond the two the spec named.
5. **Conflict gate.** A rebase onto `main` after PR #312 conflicted in `CLAUDE.md` (Generator Artifacts); the operator chose "Rebase and force-push, resolving the conflicts". The resolved text keeps #312's wording and adds the `pm-core:tests` schema-loader sentence.
6. **Budget.** The plan used far more tokens than a multi-module feature normally does. Finalize alone took 4 functional-review and 3 code-review rounds because of two rounds of inline fixes and one rebase.
7. **Tooling gaps seen during the run (plan-marshall, not filed there, per the operator's standing preference):** `manage-change-ledger remap` crashes on a patch with non-UTF-8 bytes (the intentional invalid-encoding fixture), so ledger rows were appended by hand; the functional-review validator cannot resolve evidence paths in another repository, so they were given as descriptions naming commit `96281f2`; `record-dispatch-boundary` rejects `verification_handback` as a cause; `git-workflow commit` in 6-finalize needs the manifest step id (`code-review`), not `default:code-review`; one `pm-runtime` `LspClientTest.stubborn` timing flake under host load (initialize timeout PT2S), green on rerun, with no `pm-runtime` change on the branch.
8. **No epic was detected by the lifecycle** (`not_orchestrator_pointer`, since the plan started from a spec path), so no `emit-landing` ran and this message was written by hand with the epic slug given explicitly.

envelope_version=1
sender_type=plan
sender_id=plan-v2-20-git-commit-primitives
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-22T17:23:55Z

## What landed

plan-v2-20-git-commit-primitives shipped as #545 (merged through the merge queue as c0d14b96).

```landing-facts
schema=landing-facts/1
plan_id=plan-v2-20-git-commit-primitives
epic=pm-mcp-core-m2-m4-v2
pr=#545
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=65850027
total_wall_seconds=112000.0
harness=claude
model=claude-sonnet-5-5
effort=unknown
plugin_version=0.1.2173
executor_version=0.1.2173
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,finalize-step-preference-emitter:done,plan-marshall:plan-retrospective:done,record-metrics:done,finalize-step-print-phase-breakdown:done
unresolved_review_threads=unknown
review_body_findings_total=unknown
review_body_findings_handled=unknown
sonar_open_new_issues=0
```

## Residue

Narrative items no step recorded as a fact:

- **Closed by operator override, not by a clean pass.** The functional and the code review were closed by the operator at six heads over one validator row: `RegistriesAndRecordsTest.entityKindRegistry` was split into five tests by a Sonar fix with every assertion kept, and the validator raises the vanished test name as `weakened_test` at every head. The last review verdicts were carried to the final head after a rebase that left the plan's own change byte-identical (patch id a43f0b50c68b). Deliverable 4's criterion "no existing assertion of the defaults test changes" was breached by that split and closed by the override, not by amending the criterion.
- **Operator-widened scope.** `JGitOperations`, `GitOperations`, `JGitOperationsTest`, ADR `provider-git/004`, `doc/specification/store-schemas/08-project-configuration-local.adoc` and `doc/Specification.adoc` changed beyond the declared files, to settle review findings. The final path rule is main's (PLAN-V2-164, ADR `workflow-input/006`): globs admitted, a leading `:` refused; links are committed as links, the parent directory is confined, the index is checked without HEAD.
- **Open advisories left for a follow-up:** `doc/Traceability.adoc` still says every path is confined; the `dirtyPaths` "no directory entry" claim fails for an embedded git repository (fails closed as `commit_failed`); a derived path beginning with `:` fails as `commit_failed` (PATH_REFUSED), and the Workflow DSL Stage 4 text was corrected for it; the `commit_trailer` JSON schema in `pm-core` cannot express the surrogate and line-separator rules (outside this plan's module); the specification says the email half refuses the Unicode line separators "already as white space", true of the code but not of the schema pattern; ADR `workflow-verify/043` line 41 says no assertion of the defaults test changed; the dangling-ancestor refusal of `isParentConfined` has no test; `StagingSets` and a colon-led path step-out have no test; two test classes are over the 400-line budget.
- **Main's own defect noticed:** `pm-workflow/doc/adr/workflow-verify/` holds two ADRs numbered 041 and its Decisions line omits two ADRs; not this plan's.
- **Held reply.** Finding `17cc88` (the symlink-staging finding) is fixed in the branch but its reply stays held: its stamped commit left the branch in the rebases and no commit carries an equal patch.
- **Landed squash message** carries the pull request body, including the CodeRabbit summary block, and no `Co-Authored-By: plan-marshall` trailer: the repository default is `PR_TITLE` / `PR_BODY` and the CI surface cannot change it. A PR comment records this.
- **Process incidents.** A `ci-verify` record at the 10:05 UTC head was green while the PR was in a DIRTY merge state and no workflow had run (the review bots were counted as CI); it was corrected on the next head. `main` moved nine times during the run (PLAN-V2-163, PLAN-V2-164 and others), two with real conflicts resolved by a resolver agent. All six loop-back rounds were spent (unified triage three, automatic-review two, code-review one).
- **External hand-offs** (in the PR body): the `CommitSite` implementation, engine wiring, the production unit, `GitCommitPrimitivesVerificationTest`, `StateCatalogueDriftTest` rows, attempt counters, the ledger row, `head_at_completion`, `freshness_reconcile`, the git CLI variant (PM-WATCH-DSL-7, with two points CodeRabbit raised: keep unrelated index entries out of the CLI commit, and set the author variables explicitly) and the derivation of `agent_identity`.

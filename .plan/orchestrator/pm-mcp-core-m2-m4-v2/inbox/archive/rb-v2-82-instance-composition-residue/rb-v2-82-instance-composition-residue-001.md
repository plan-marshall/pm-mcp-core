envelope_version=1
sender_type=plan
sender_id=rb-v2-82-instance-composition-residue
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-18T10:10:44Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-82-instance-composition-residue
epic=pm-mcp-core-m2-m4-v2
pr=#492
merge_state=merged
cleanup_owed=false
deliverables_total=4
deliverables_done=4
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2152
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=19
review_body_findings_handled=19
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced)

# PLAN-V2-82 landing — the plan-instance position per (unit, state) and the nested policy calls

Merge commit `55a37c2f8f7f4628eb205140c6011590edae7457` (PR #492). Standing rules read: r7 (2026-11-10); the spec's
First Instruction names r4, the file's current revision r7 binds. Rules applied: R02, R05, R06, R07, R08, R10, R11,
R13, R15, R17, R18, R19, R20.

## 1. Deliverables

**D1 — the restored call-pruned position test (test only).** Met.
- Done-when: a `ComposedSet` test prunes a call inside a composed unit, passes the position `(replan, replan.assess)`
  and is refused `position_not_contained` naming the state and, after D2, its unit; the three #236 tests and every
  passing `ComposedSet` case still pass.
- Test: `PlanInstanceComposerTest.ComposedSet.prunedCallInsideAComposedUnit`. Order: **none** (a restored regression
  test of behaviour `main` already has; it passed at its commit). Commit `e4888a9d` (message assertion migrated by
  `152a527d`).
- Operator decision: the original case pruned `execute.land-entry/land`, which #236's entry guard now protects; the
  test uses `execute.replan/replan`, the only non-entry-bound call inside a composed unit.

**D2 — the position rule over `(unit, state)`.** Met.
- Done-when: `InstancePosition(UnitState current, List<UnitState> callStack)`; `at(String)` = plan unit; unit-aware
  factory; `missingFrom` by unit; the refusal names the unit; `positionIsPerUnit` with a contained counterpart and a
  call-stack frame judged in its own unit; `positionOverTheSet` and `prunedCallInsideAComposedUnit` pass with the new
  message.
- Test: `PlanInstanceComposerTest.positionIsPerUnit`, plus the migrated messages of `positionOverTheSet`. Order:
  **mutation-after** — the merged-set `contains` restored in `InstancePosition` at the D2 commit; failing run job
  `3df1c46b7c8441b691c11acef46350dd`, `PlanInstanceComposerTest.refused:83` saw a `Composed` result for the plan-unit
  position; passed with 7853 tests at `152a527d`. Commit `152a527d`.
- Operator decisions: the `completed` prune scenario cannot be produced by the specified design (a rule prune applies
  to every unit; the only asymmetry is entry-state protection), so the witness is `finalize.push-barrier`; the `at()`
  = plan-unit semantics force the positions of other units' states to the unit-aware factory, and the operator
  accepted that clause as met.
- Base-sha note: the `{plan}-base` tree at `13a1236c` reported `executor_in_worktree: absent`; the tree was removed
  and the pre-fix behaviour restored by mutation instead, which the lane permits as `mutation-after`.

**D3 — the nested policy calls in the header and the ceiling diff.** Met.
- Done-when: the four `remediation.*` sites of `refine`, `outline`, `plan` and `execute` in `header.policyCalls()`
  with their `in_unit` and effective arguments; `headerAndComposedBodyAgree`; the nested ceiling with its unit and two
  same-id calls kept apart; `in_unit` round-trip and refusal; the plan unit's calls serialise without `in_unit`.
- Tests: `nestedPolicyCallsInTheHeader`, `headerAndComposedBodyAgree`, `planUnitCallBeforeComposedCall`,
  `planUnitCallsFirst`, `nestedCeiling`, `sameStateInTwoUnits`, `nestedCeilingJson`, `inUnitSurvives`,
  `inUnitOfNoComposedUnitRefused`, `PolicyCallRecordsTest`. Order: **mutation-after** — failing runs: job
  `b2c98017c048457cbc0c5a0caecd021b` (composed calls dropped), `887adfa635964ee7940d382819d4ead4` (order sorted),
  `720401be125d451ab9fd30d5cb0cabdc` (merge arguments swapped), `8aececa8573b45b9b1e055702808d3e8`
  (`byCallSite` keyed by call site), `6ad13499544f4f889ced7db3095621e1` (reader ignored `in_unit`),
  `126d2483306b4183a3e7dedb579ae2ac` (writer emitted `in_unit:null`); passed 7860 at `8d1873f3`. Commits `8d1873f3`,
  `76b903af`, `d40f936d`, `0db731bd`.
- Resolution recorded: the spec's prose says `Optional<String> inUnit`; R20 forbids `Optional` record components, so
  the component is `@Nullable String inUnit`. The Done-when does not depend on the type.

**D4 — the documents and the Watch.** Met.
- Workflow DSL 09 (position rule, policy-call bullet), Store Schemas 14 (Position Rule, Storage, Sub-Units), the
  developer guide, ADRs 024–027 (next free numbers against `origin/main`: 021–023 pre-existed), the store-schemas Watch
  item `PM-WATCH-STORE-60`. Links (R17, sibling checked out at `origin/main` `79ec3a6`, worktree as the project dir):
  `BROKEN LINKS 6`, all the sibling's own deleted items (`PM-WATCH-HYP-11`, `PM-WATCH-JOB-1`, `PM-WATCH-JOB-11`,
  `PM-WATCH-PHASE-104`, `plan-deletion-lessons`); the new item's backlink from Store Schemas 02 added.
- The PR body maps each specification/guide statement to the code line it matches, and lists **Owed to the milestone
  exit**. Order: **none** (documentation and a recorded links run). Commits `514d44aa`, `d40f936d`, `0db731bd`.

**Whole-plan Done-when.** Met. The four deliverables' tests pass beside every existing test; `verify -Ppre-commit`,
`verify`, `verify -Pcoverage` and `verify -Pintegration-tests -pl pm-core -am` are green on the final head
`0db731bd` (jobs `9b79c6bd03e4449096ebbb6174a26645`, `4e161fa80e214574923e436ba652d357`,
`4a5c6dde99bf4a2b8ca4c0bcbd16559a`, `13166fcab68a480e810d19da3a081c29`); SonarCloud's pull-request search is empty
(0 issues, gate OK, new coverage 96.0%); CI green at every pushed head, with the head `ccaaa117` needing one rerun
after a Maven Central HTTP 429 in `quarkus-alignment`.

Per-head test runs: every pushed head was tested before its push (targeted suites while iterating; `verify` at
`afdcd7e5`, `4d8d8935`, `df97ea50`, `eaff90a8`, `ccaaa117`, `0db731bd`; 11054 tests at the last three).

## 2. Personas and skills

- Production code: `persona-implementer`; tests: `persona-module-tester`; documentation: `persona-documenter`;
  every self-review round and the PR-comment triage: `persona-code-reviewer`.
- Domain skills: the spec declares none (`skills: none named`). R20 skills read: `java-core`, `junit-core`,
  `java-null-safety`, `java-lombok`, `javadoc`, `cui-testing`; `cui-http-testing` and `junit-integration` have no
  bearing (no HTTP, no ITs).
- Surface drift: `doc/specification/store-schemas/02-workflow-state-store.adoc` (the watch item's backlink),
  `doc/Traceability.adoc` (R05/R08 bookkeeping), the two JSON schema resources (the re-ground record's "schema files
  since PLAN-V2-115–119"), `PlanInstanceComposerRecordsTest` (the `Composed` arity), `PlanInstanceStoreTest` and
  `PlanInstanceComposerTest`'s position constants (the `at()` semantics), `CompositionRequest` javadoc,
  `PolicyCallRecordsTest` (the builder's contract test), `PlanFixtures` (one test-only request helper).

## 3. Hypotheses

- "`git cherry-pick 852e58d3` applies without conflict": **refuted** — the commit is not an object in this
  repository; its content shipped as `ea2aeef5` (#236), as the spec's re-ground record already states.
- "The composed files' call-stack frames carry exactly the unit ids the composer files under": **corroborated** —
  `PlanInstanceComposer.composedUnits` keys `byId` by `subUnit.definition().id()` and `ComposedUnit.of` uses
  `definition.id()`; the `ComposedSet` fixtures and the per-unit position tests exercise it.

## 4. Findings

- Self-review: 3 rounds, 22 findings (16 + 6, plus the round-limit item F2 closed by a purpose-built fixture). All
  fixed or, for one private-record nit, refuted as no defect. Commits `5f5fa55c`, `e0750df0`, `4d8d8935`, `76b903af`.
- CodeRabbit: two reviews (at `76b903af` and `ccaaa117`), 22 findings, all Minor. 21 fixed; 1 refuted with a stated
  reason (`HeaderSchemaCheck.POLICY_UNITS` vs the schema enum — a pre-existing follow-up, out of this plan's scope).
  Fix commits `d40f936d`, `0db731bd`; 18 replies posted and 18 threads resolved.
- Sonar: `java:S6126` (duplicate literals in the test fixture) and `java:S1612` (lambda to method reference), both
  fixed (`d40f936d`, `eaff90a8`).
- CI: `quarkus-alignment` failed once on `ccaaa117` with a Maven Central HTTP 429 (transient); the workflow was
  re-run and passed.

## 5. Reviewers

- `coderabbit` (required, `quota_limited`, `review_complete_after_handling`): reviewed `76b903af` (13 inline + 1
  additional) and `ccaaa117` (5 inline + 3 outside-diff); the qualifying review's marker names `ccaaa117`
  (`kind=reviewed`). Every finding handled; a later head triggers no obligation. Final `review-gate`:
  `merge_allowed: true`, 0 unresolved threads, 19/19 body findings handled.
- `sourcery` (optional): refused on its own budget (250000 diff characters, next in 1 day 6 hours); optional, so no
  gate.

## 6. Operator questions

1. "Deliverable 2's Done-when `completed` scenario cannot be produced by the specified design…" — options {use
   finalize.hand-back; force the literal completed case via a mutation; report not met as written}; selected "use
   finalize.hand-back".
2. "Deliverable 1's restored test prunes `execute.land-entry/land`, now protected by #236's entry guard…" — options
   {use the replan call; add a land call via mutation; report not met as written}; selected "use the replan call".
3. "No fixture-realizable prune makes one unit lose a shared state without breaking the merged fixtures'
   invariants…" — options {use finalize.push-barrier; fabricate a shared id; report not met as written}; selected
   "use finalize.push-barrier".
4. "Deliverable 2's `PlanInstanceStoreTest`/`at()` clause is technically unmet as written…" — options {count D2 met;
   count D2 not met as written}; selected "count D2 met".
5. "Round 3 leaves one low test-strength gap (the plan-unit-first order is pinned only at the helper)…" — options
   {accept F2, report budget-exhausted; continue with a 4th round}; selected "continue with a 4th round".
No free-text operator messages.

## 7. Rule check

- `guard: none on this harness (unenforced)` — no tool-call guard is installed for OpenCode, so no guard-log line
  exists for this run; the rule check rests on the run's own record.
- Spec read: `spec_source = ledger_worktree`; no `spec_differ` marker was reported.
- `executor-skew`: `match` (executor 0.1.2149, lane 0.1.2149).
- Every script call went through `python3 .plan/execute-script.py …`; every build through the resolved `maven`
  canonical; every CI action through `ci`; no `gh`/`glab`, no build wrapper, no test binary, no `python3 -c`, no
  `git grep`, no `sleep`. Files were read with the file tools; temp files live under `.plan/temp/`.
- Restarts: one server restart mid-run (recorded; the lane text was re-read and the run resumed at the CI wait).
  No killed sub-agent; three self-review sub-agent rounds, each re-dispatched with the round's findings.
- One provider-API action outside the usual verbs: two pending review shells created by `thread-reply` were published
  with `ci pr submit-review --event COMMENT` (the provider refused further replies while they were stuck); no content
  changed by that action.
- The base worktree attempts: created at `13a1236c`, reported `executor_in_worktree: absent` (generation wrote
  nothing new), removed with `git worktree remove` before any build; no base evidence was claimed from it.

## 8. Build server

- Setup preflight: `ready` (registered). Every build was routed (`mechanism=daemon_longpoll`); no
  `in_process_fallback`, no `degraded`, no launch-fault retry, no `killed` job. The only retried job was the CI
  workflow (`quarkus-alignment`, Maven Central 429), re-run through `ci checks rerun`.
- Builds: `verify -Ppre-commit` 506s / 321s / 316s / 367s / 285s at the successive heads; `verify` 10989–11054 tests;
  `verify -Pcoverage` 10989–11054 tests; `verify -Pintegration-tests -pl pm-core -am` 21 tests.
- `runtime_stamp_reason`: none reported; `plugin_version` is `unknown` in the resolve result.

## 9. Residue

- Spec layer-1 findings for the orchestrator: D2's "`PlanInstanceStoreTest` … need no change beyond the message"
  is contradicted by the deliverable's own `at(String)` = plan-unit semantics; D1's premise (pruning
  `execute.land-entry/land`) is stale after #236's entry guard; D2's `completed` prune scenario is not producible by
  the specified design. The operator accepted each adaptation; the spec text should be corrected at the next re-ground.
- `HeaderSchemaCheck.POLICY_UNITS` remains a hand-maintained mirror of the schema's `unit` enum (pre-existing;
  CodeRabbit's outside-diff follow-up, deliberately not taken here).
- The engine's `instance` history line and its `CallStack`→`InstancePosition` mapping remain Owed to PLAN-V2-13; the
  local branch `fix/instance-composition-guards` is redundant (the commit it named never existed) and the operator
  deletes it.

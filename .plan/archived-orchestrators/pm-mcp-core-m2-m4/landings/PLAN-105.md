# Landing Analysis: PLAN-105 — The plan instance as a set of files, and the plan's history

epic: pm-mcp-core-m2-m4
workstream: WS-03
pr: #230

> Landing record for PLAN-105 (`plan-instance-files-and-history`, runbook lane, harness not stated). NO inbox message of the
> lane exists; everything below is read from ground truth: the merge commit `13a1236c55e46929b94381327faf7d12502734e8`
> (squash, merge queue, 29 files, +2,420/-74, merged 2026-10-29 19:04Z by `plan-marshall-oliver`, base `main`, confirmed by
> `ci pr view`: `state: merged`, same sha), the PR body (which is the lane's report), the review threads read through GraphQL
> and `ci pr comments`, the CI checks, and SonarCloud. The package is delivered in the main, with named gaps: D1 and D2 shipped
> modified/partial, the lane's own PR body reports one Done-when clause "not met as written".

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 a version as a set of immutable files, written in one transaction (`plan.v<N>.pmwf` + `<unit>.v<N>.pmwf`, header names every file by digest) | shipped-as-specified | `ComposedUnit` (new), `PlanInstance.units()`, `PlanInstanceComposer` composes the set (header `inputs.templates` names each composed file by path and text digest; `inputs.sub_units` keeps only digest-bound units), `PlanInstanceStore.recordComposed` writes the whole set in the composing transaction. `PlanInstanceStoreTest.ComposedSet` (`writtenAsOneSet`, `headerNamesNoFile`, `fileNotNamed`), `PlanInstanceComposerTest.ComposedSet.headerNamesTheSet`, `phaseFileKeepsItsTemplate`. Deviation inside the clause: the plan unit's header entry keeps the TEMPLATE digest (the file embeds its own header), recorded in ADR workflow-plan/020. |
| D1 store refuses an inconsistent set; refused composition writes nothing | shipped-as-specified | `PlanInstanceStore` refuses a header digest naming no file, a file the header does not name, a file twice, a unit name disagreeing with its file, a composed unit named `plan`; validates before staging (PR body, `PlanInstanceStoreTest`). `readUnit(version, unit)` added. |
| D1 compiled invariants over the composed version as a whole; position rule over the set | shipped-modified | Invariants run on the plan unit over the call-resolved catalog; composed files are NOT verified standalone (the lane tried it, reverted: synthetic phase fixtures fail Rule 2 standalone). `PlanInstance` javadoc and ADR 020 § Consequences scope ADR 001's "verified in full" to the plan unit. Position rule: `ComposedSet.positionOverTheSet` and `prunedCallInsideAComposedUnit` (refuses `land.verify-sweep` as `position_not_contained`). BUT the check compares bare state ids against ONE merged set (open CodeRabbit Major thread, see Residues). |
| D1 `InstancePruner` prunes a phase by removing its call and not composing its file | shipped-as-specified | `InstancePruner` unchanged in the diff; `ComposedSet.prunedCallComposesNoFile`, `sharedStatesSurvive`. |
| D1 the four lane-pruning cases PLAN-43 removed are back over the new shape | shipped-modified (partial) | Not re-delivered under the original names (`deepPrunesLightRoute`, `lightKeepsEscalation`...). Present: `ComposedSet.prunedCallComposesNoFile`, `sharedStatesSurvive`, `prunedCallInsideAComposedUnit`, plus `PerLaneRework.deepPrunesLightCalls`/`lightKeepsBothRoutes`, `LaneRoutes` (the last two pre-exist; `per-lane-rework.pmwf` +4/-2). The PR body states this as residue. The Done-when clause "the four cases are back" is met in substance (lane pruning over the composed set is tested), not as four cases. |
| D1 the diagram renderers read the set | shipped-modified | The three instance forms (table, AsciiDoc, Mermaid) LIST the composed files with digests beside the pruned elements (`InstanceAnnotations.Composed`, `InstanceMermaidRenderer` +2 lines, `WorkflowDiagramGeneratorTest.shouldNameTheComposedSet`). The phase composites / grouped rendering (Expected Surface "D2: phase composites", ADR workflow-diagram/012) is NOT done: the plan unit is still rendered ungrouped. |
| D1 `pm://plan/{plan_id}/instance[/{version}]` returns the plan unit's file with its header | dropped (moved) | Not in this repository (the resource is the assembly's, `plan-marshall-mcp`); the lane records "the assembly must read the stored version files" and "no `instance` line is written yet (the composing path is plan-marshall-mcp's)". `PlanInstanceStore.readVersion/readUnit` are the read surface. |
| D1 landing variant composed into the finalize phase | dropped (moved) | Composed as its OWN file of the version, states NOT inlined into `phase-6-finalize` (needs the real landing units of `plan-marshall-mcp`); ADR 020 Alternatives 2 "Deferred". PLAN-43 had named this PLAN-105's. |
| D2 append-only history per plan, archived with the plan | shipped-as-specified | `PlanHistoryLine`, `PlanHistoryCodec`, `PlanHistory` (`core.store`; `mcp/history.jsonl`, one target of the plan's `ScopeTransaction`, covered by the digest sidecar); `PlanHistoryTest` (append, read, `appendedNotRewritten`); ADR core-store/051. The HYPOTHESIS of the spec (history in `core.store` through `ScopeTransaction`) is CONFIRMED. Added `PlanHistoryLine` (spec expected only `PlanHistory` + codec). |
| D2 states entered/left, sub-workflows called/returned, with outcomes, instance version, time | shipped-modified | Record: `PlanHistoryLine(formatVersion=1, seq, event, node, callPath, phase, outcome, instanceVersion, at)`; events `entered`, `left`, `called`, `returned`, `instance`. `WorkflowEngine` gets history-bearing `advance(status, store, transaction, history, instanceVersion)` and `advance(status, event, store, transaction, history, instanceVersion)` overloads; lines are written in the same transaction as `mcp/status.json` (`PlanHistoryEngineTest`: lines, `sequenceContinues`, `eventResumeWritesHistory`). GAPS: `start`, `stepOut` and `unwindToFallback` carry no history lines (lane residue); the `instance` event exists in the enum but NO production code writes it (nothing in `src/main` produces `Event.INSTANCE`), although the PR text says an instance version "taking force" is recorded. |
| D2 the runtime derives each node's state (done, running, planned, waiting, failed with outcome, not taken, not in this plan with reason) | shipped-as-specified | `PlanProcessStates.derive(PlanInstance, List<PlanHistoryLine>)` (new, 216 lines) with `State` incl. `NOT_IN_THIS_PLAN` + prune reason; `PlanProcessStatesTest` `doneRunningPlannedPruned`, `failedAndNotTaken`, `waitingForOperator`, `waitingForOperatorAtALinkState`, `afterRestart` (written to a plan scope, re-read from disk). Scope: plan unit and composed files only, not the digest-bound sub-units' internal states (lane residue). |
| Done-when: a loop-back calls a phase again and the history records both runs | dropped (reported "not met as written") | PR body says so. No test drives a loop-back through the engine and reads two phase runs; only `sequenceContinues` and `appendedNotRewritten` assert the mechanism. |
| Done-when: Sonar, build, links | shipped-modified | Sonar: see below (green, measured by this analysis). `verify -Ppre-commit` green at `0b6516ee` per the PR body (8,267 tests); later heads ran `test -pl pm-workflow -am` (5,974 tests) because the machine SIGKILLed the full verify twice (out of memory); CI `build / build (25)`, `build (26)`, `sonar-build` and `integration-tests / test` are green on the merge head `280b7646`. `verify -Pcoverage` NOT run by the lane (CI `covering-run-check` was skipped; Sonar new-code coverage 87.7%). Links check (R17) NOT run ("not run in this session"). Size 29 files (under 100): no split needed, the "ask the operator" step is moot. |
| Documents (R05/R06/R10/R11) | shipped-modified | ADR workflow-plan/020, ADR core-store/051 (next free numbers as the spec said), `workflow-plan.adoc` +41, `core-store.adoc` +17, `core.store` `package-info`, `Traceability.adoc` (PM-IMPL-5, PM-WF-16 Implementation/Verified-by). The "implemented sections deleted" of `store-schemas/14`, `store-schemas/02`, `workflow-dsl/09` was NOT done (see Declared but untouched). `doc/ImplementationWatch.adoc`, `doc/LogMessages.adoc` untouched (no log record added: consistent). |

Open items PLAN-43 handed over (spec § State after PLAN-43, item 6): `handed_back`/`returned` end with the phase `aborted`
(`WorkflowEngine.ended`) NOT touched (0 `aborted` lines in the engine diff); `CallGraphExpansion` bare-id collision untouched; no
engine test runs the default plan end to end (still absent). The package file `doc/plans/phase-sub-workflows.adoc` and its
index row were left in place (no `doc/plans/` file in the diff): R01 says the LAST of the three plans removes them; PLAN-43 and PLAN-106
have landed, so PLAN-105 is the last — the omission is defensible only because the package's assembly halves stay open
(`plan-marshall-mcp`); the operator should confirm the package stays open.

Declared but untouched (Expected Surface): `doc/specification/store-schemas/14-plan-instance-waivers-questions.adoc`,
`store-schemas/02-workflow-state-store.adoc`, `workflow-dsl/09-plan-instance.adoc` (the lane did not delete the implemented
sections: the schema blocks are read by parity tests, R10, and the operator must say which blocks stay); `HeaderJson.java`,
`HeaderSchemaCheck.java` (the header shape needed no change).

Undeclared surface (in the merge diff, not in the Expected Surface): `ComposedUnit.java`, `CompositionRequest.java` (duplicate
sub-unit ids refused), `PlanProcessStates.java` + `PlanProcessStatesTest`, `PlanHistoryLine.java`, `PlanHistoryEngineTest`,
`InstanceAnnotations.java`, `InstanceAsciiDocTableRenderer.java`, `InstanceAsciiTableRenderer.java`, `DiagramFixtures.java`,
`WorkflowDiagramGeneratorTest.java`, `PlanFixtures.java`, `src/test/resources/plan/per-lane-rework.pmwf`, `core.store/package-info.java`,
and the documents (two ADRs, two developer guides, `Traceability.adoc`; required by CLAUDE.md, not listed in the spec).

## Metrics and Anomalies

- Tokens: `unknown` (no inbox message; the harness measured none reported). Plugin version unknown.
- Duration: PR opened 2026-10-29 11:25Z, merged 19:04Z (7 h 39 m); 10 commits (8 work commits, 1 merge of `origin/main`
  `280b7646`); 4 CodeRabbit rounds plus 5 inline answers by the lane.
- Anomalies: (1) no landing message from the lane; the facts come from the PR body only. (2) Full `verify -Ppre-commit` was
  SIGKILLed twice by the host (642 s and 847 s, 0 failures, "294 MB free, swap exhausted"), the build daemon could not launch `mvn`;
  gate evidence for the later heads is a per-module `test` run plus CI. (3) The lane reported the loop-back Done-when as not met;
  it is the only clause reported so. (4) A POST-MERGE fix exists locally and is unpushed: branch `fix/instance-composition-guards`,
  commit `852e58d3` (1 ahead of `origin/main`, no PR, remote branch absent) "entry states survive a prune and an unnamed callee is
  refused (D1)" (`InstancePruner`, `PlanInstanceComposer`, `PlanInstanceComposerTest`, +82/-21): the two "unverified" CodeRabbit
  findings of the last review (callee absent from the request's sub-units silently skipped; a prune removing a state a retained entry
  binds to) were therefore real and are fixed only there. The worktree is `.plan/local/worktrees/105-plan-instance-files-and-history`.
  (5) The `instance` history event has no producer (see D2 row). (6) Lane plan id not recorded anywhere reachable (see below).

## Routing and Merge Behavior

- Review: coderabbit reviewed in rounds up to head `280b7646` (17 review events). 5 inline findings at the first round
  (PM-IMPL-5 note, composed-unit verification scope, pruned-state traversal, `InteractiveLinks` waiting state, retained axis call
  site), all answered/fixed in `6e5458fc` and resolved; visited set in `ce070a44`; prune records of composed units in `bd8e3348`;
  duplicate sub-unit ids, `called()` removal and the link-state test in `ce070a44`; nested-callee position refusal in `6b8a87af`;
  Sonar findings cleared in `e0e5eed3`. Sourcery refused on quota (twice). CodeRabbit paused/rate-limited several `@coderabbitai review`
  triggers; its last review (18:22Z, head `280b7646`) is the one whose findings are residues. Merged by the orchestrator at the
  operator's word with the review state below unresolved.
- Unresolved at merge ("27"): decomposed, the GraphQL `reviewThreads` hold 7 threads, 6 resolved, 1 UNRESOLVED. The other 26 are
  not threads: 5 CodeRabbit review bodies carrying "Follow-ups"/"Nitpicks" and 21 issue comments (bot status notes, the lane's
  `@coderabbitai review` triggers and its answers, the Sonar comment), which the tool counts as unresolved by construction.
  Actionable AND not fixed on `main`:
  1. MAJOR, open inline thread (`PlanInstanceComposer.java:208`, last review, never answered): the position check compares bare
     state ids against one set that merges all composed units (`InstancePosition.missingFrom`); a current state pruned from its own
     sub-workflow passes when another reached unit declares the same id, so a version can be composed without the plan's state and
     strand it. Needs unit-qualified position sets (CompositionRequest + composer). Same family as the ownerless
     `CallGraphExpansion` bare-id collision.
  2. MINOR (flagged twice): `PlanHistory.nextSeq()` decodes the whole `mcp/history.jsonl` on every advance (quadratic cumulative I/O);
     answered "not changed, residue" (no tail-seek primitive in `JsonlStoreFile`).
  3. MINOR: nested policy calls of a composed phase are not in the header nor in `DiffSummary.ceilingsChanged` (header and composed
     body disagree on a raised remediation argument). Predates the PR; the lane assigned it to PLAN-71 (= PLAN-V2-13) but
     PLAN-V2-13 does NOT name it (no hit for policy call, DiffSummary or nested in the spec): it has no owner in the new epic.
  4. Unverified-but-real: callee absent from the request sub-units skipped silently, and entries bound to a pruned state: fixed
     only in the unpushed `852e58d3`.
  5. Composed files are not verified standalone (documented, ADR 020), nitpick: `shouldNameTheComposedSet` does not cover the
     header-derived plan entry.
  Fixed on `main` (not residues): PM-IMPL-5 note, composed-unit traversal over pruned states, `waiting_for_operator` for link
  states, retained axis call sites, visited set, duplicate unit ids, instance-version >= 1 check in `Run`, header prune records of
  composed units, digest assertion in all diagram forms, event-resume history test, nested-callee position refusal.
- CI/merge: squash-merged through the merge queue; `ci checks status`: overall success, 20 checks (`build (25)`, `build (26)`,
  `sonar-build`, `integration-tests / test`, `quarkus-alignment`, `dependency-review`, `CodeRabbit`, `SonarCloud Code Analysis` all
  SUCCESS; `deploy-snapshot`, `covering-run-check`, `Sourcery review` skipped). No red `sonar-build` push-run noted. Head
  `280b7646` is a merge of `origin/main` (conflicts, if any, resolved there; not inspected).
- Sonar: measured here by plain HTTP (no token needed): `pullRequest=230&resolved=false` -> `total: 0` open issues; quality gate
  `OK`; the Sonar comment of 18:28Z reads Quality Gate passed, 0 new issues, 0 accepted, 0 hotspots, 87.7% new-code coverage, 1.2%
  duplication. The lane had no Sonar figure in the PR body ("not run ... handled in the review cycle"); its intermediate 11 new-code
  issues were cleared in `e0e5eed3`. No discrepancy. `branch=main` not re-read.
- Collisions: PLAN-46 (#233) merged right after (`a7027600`), no collision seen; `main` moved `0a131ead` -> `004fbcec` during the run
  (merge commit `280b7646`).

## Reconciliation Actions

- [ ] row `status` → `shipped` — `orchestrator queue --transition PLAN-105 --status shipped`
- [ ] row `pr` stamped — `orchestrator queue --set-row PLAN-105 --field pr --value #230`
- [ ] row `landing` stamped — `orchestrator queue --set-row PLAN-105 --field landing --value landings/PLAN-105.md`
- [ ] row `plan_marshall_plan_id` stamped — `orchestrator queue --set-row PLAN-105 --field plan_marshall_plan_id --value rb-105-plan-instance-files-and-history` (INFERRED from the convention `rb-<n>-<slug>` of PLAN-35/43/61/106 and the worktree/branch names `105-plan-instance-files-and-history`; no ledger field, `.plan/local/plans` (only `NO_PLAN`), PR body or commit trailer states it: if the operator wants an exact value, ask the lane; else stamp `unknown`)
- [ ] epic.md narrative reconciled against the queue rows (queue annotations, retired items): the old epic stays open for PLAN-46 and PLAN-105 only; with this landing and PLAN-46's analysis, run `close`
- [ ] Open Defect opened: unit-qualified position check / bare-id state collision (thread `PlanInstanceComposer.java:208`; joins the ownerless `CallGraphExpansion` bare-id defect) — carry to the successor epic
- [ ] Open Defect/Watch opened: unpushed post-merge fix `852e58d3` on `fix/instance-composition-guards` (no PR); operator to say push-and-PR or discard
- [ ] Open Defect retired: "PLAN-105 owns the `aborted`/`completed` phase of `handed_back`/`returned`" is NOT delivered -> re-open as ownerless (PLAN-V2-57 assumes PLAN-105 does it)
- [ ] Watch opened: loop-back through the engine with both phase runs in the history (Done-when not met); `instance` history event has no producer; `start`/`stepOut`/`unwindToFallback` write no history
- [ ] Watch opened: implemented sections of `store-schemas/14`, `store-schemas/02`, `workflow-dsl/09` not deleted (operator call on which blocks stay); R17 links check and `verify -Pcoverage` not run by the lane (CI ran the integration tests)
- [ ] Watch: package `phase-sub-workflows` (`doc/plans/phase-sub-workflows.adoc` + index row) left in place by the last of the three plans; operator to confirm it stays open (assembly halves, landing-variant inlining, composed-file verification)
- [ ] resume anchor updated in `resume_anchor.md` — `manage-status update-field --field resume_anchor --store orchestrator`
- [ ] `queue-view.md` regenerated and committed with the row change — `orchestrator regenerate-view` (START HERE carries no `(!) missing:` marker once `pr` and `landing` above are stamped)

## Follow-Ups

- Unit-qualified position set (thread 1): a small plan or fold into PLAN-V2-13 (it edits `PlanInstanceComposer` and the position rule). Not staged yet.
- Landing-variant inlining into `phase-6-finalize`, standalone verification of composed files, an `instance` line writer and the assembly reading the stored version files: `plan-marshall-mcp` (`default-units-and-drift-tests`, blocked).
- `PlanHistory.nextSeq()` tail read: needs a bounded-seek primitive in `core.store` (`ActivityJournal` keeps one privately); fold into the plan that first makes history long (PLAN-V2-26) or a small core plan.
- Nested policy calls in header and ceiling diff: PLAN-V2-13 must add it to its scope (`PolicyCall` needs its containing unit; `DiffSummary.byCallSite` a unit+call-site key) or it stays ownerless.

### Changes the staged plans of `pm-mcp-core-m2-m4-v2` must absorb (one line each)

- PLAN-V2-13 plan-instance-versions: the composer already returns a SET (`PlanInstance.units()`, `ComposedUnit`) written by `PlanInstanceStore.recordComposed` in one transaction; `instance_version` is still NOT a field of `WorkflowStatus` (the history-aware `advance` takes it as an int parameter), the engine must append the `instance` history line when a version takes force (no producer today), the header merges nested prune records but not nested policy calls, and "only verified versions" must say plan-unit-verified (composed files are pruned bodies of verified units).
- PLAN-V2-19 engine-carries-reasons D2: the merged `left` line is `PlanHistoryLine(formatVersion=1, seq, event, node, callPath, phase, outcome, instanceVersion, at)` with `outcome` a lowercase identifier and NO reason member; a reason needs a new member and a `FORMAT_VERSION` decision in `PlanHistoryCodec`; the append sites are `Run.record`/`recordHistory` in `WorkflowEngine` (history-bearing overloads only; `start`, `stepOut`, `unwindToFallback` write nothing).
- PLAN-V2-04 unit-input-interface: `PlanInstanceComposer` is no longer "one mechanical line": `called()` is gone, `composeUnit` builds a `WorkflowDefinition` per composed unit (grep `new WorkflowDefinition(` again; the line numbers 220/249 are stale) and every composed phase unit must carry the new frontmatter input interface.
- PLAN-V2-15 call-time-inputs-and-slots: the `Run` creation sites are the five of PLAN-V2-25 plus the two history-bearing `advance` overloads (`new Run(status, history, transaction, instanceVersion)`); any new `CallFrame` member must also flow into `PlanHistoryLine.callPath` (a list of sub-workflow ids, max 5 frames), which stores ids only, not frame data.
- PLAN-V2-25 command-record-and-dispatch: the "five places that create a `Run`" become seven (two overloads with `PlanHistory`); `Run` already holds `PlanHistory`, `ScopeTransaction` and `instanceVersion`, so the command log can share the transaction; the history is the process view of the "three linked records".
- PLAN-V2-26 process-and-overview: `PlanProcessStates.derive(instance, history)` already yields done/running/waiting/failed/planned/not_taken/not_in_this_plan with reason for the plan unit and composed files, so the process tree reads it instead of deriving states; it does not cover the digest-bound sub-units' inner states, and `Lines` of `PlanHistory.read()` is the read surface.
- PLAN-V2-57 default-plan-lanes-and-run: "Not taken by decision of the epic: the `aborted` phase of `handed_back`/`returned` (PLAN-105), the lane pruning of the composed instance (PLAN-105)" — the first is NOT done by PLAN-105 (still open, ownerless); the second is done over the composed set (`ComposedSet` cases), so the end-to-end test can rely on it.
- PLAN-V2-21 plan-and-epic-control: a composition event override under `position_not_contained` now checks the set of composed units, with the bare-id caveat of the open thread; pause points record via the history-bearing advance only.
- PLAN-V2-44 positioned-layout / PLAN-V2-54 / PLAN-V2-69 (diagram, recipes, process designer): "a unit of the composed instance has no pruned states left" holds for the files; the Mermaid instance form lists composed files but has no phase composites yet, the rendering `InstanceMermaidRenderer` is still ungrouped (ADR workflow-diagram/012 unmet).
- PLAN-V2-10 production-registries-and-wiring: `PlanInstanceComposer` verifies only the plan unit over the call-resolved catalog; a registry change reaches composed units only through their already-verified templates.

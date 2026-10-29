# Landing Analysis: PLAN-34 — Role qualification

epic: pm-mcp-core-m2-m4
workstream: WS-05
pr: #196 (merged as 9251596f016de659d4a1b3b9b29ccac6879ff036)

> Landing record for one shipped plan. Source: inbox message `rb-34-role-qualification-001.md` (runbook lane, opencode,
> 2026-10-27T00:32:14Z), corroborated against git at `9251596f`, the PR and its checks through the `ci` abstraction, the
> SonarCloud public API (`pullRequest=196` and `branch=main`) and the code the deliverables name. Every fact marked
> "checked" was read from git, the PR or the API by me; no test was re-run, the test counts are the lane's.

Standing rules: r1, R01 to R13, R15, R17, R18 named by the landing (R14 is not in the plan's list). R05 is **not applied
to the specification text** (the lane kept the sections and deleted three watch items), see Reconciliation.
The lane read its spec from the main checkout (`spec_source: main_checkout`): the version adapted and re-grounded on
2026-10-26 and landed in #194, so the Re-grounding Record of that day was in front of it.

## Deliverable Fidelity vs Spec

The spec carried the package file's six bullets (the spec text predates #171, whose three additions the Re-grounding Record
carried). The lane reports 6 deliverables, 5 done.

| Deliverable | Verdict | Evidence |
|-------------|---------|----------|
| D1 invalidation and conservative fallback | shipped-as-specified | Checked: `QualificationCheck` and `QualificationRecords` in the new package `core.role.qualification`; `ModelCaller` keeps its Qualify step (`ModelCaller.java:28`, `:46`, `:57` `QualificationLookup`); `RecordedQualification` in `workflow.model` implements the lookup over the stored records; tests `QualificationCheckTest`, `RecordedQualificationTest`, `ModelCallerTest` exist. |
| D2 digest, project set, exemptions | shipped-as-specified | Checked: the digest is the shipped `RoleDigests`; `QualificationRecordStore` and `QualificationRecordCodec` write the project set; judge and steward exempt (`ModelCallerTest`). |
| D3 record: tokens, open harness pattern, `missed_sources`, consent | shipped | Checked: `QualificationRecord`, `TokenTotal`, `GradingConsent`, `GradingOffer`; `RoleGraderTest.writesRecord` and `declined`. |
| D4 corpus completion, failed and partial import | shipped | Checked: `RoleGrader` (workflow.model) and `GradingOutcome`; `RoleGraderTest.failedImport` and `partialImport`. |
| D5 the corpus and its build validation are `plan-marshall-mcp`'s | met as a pointer | Checked: traceability line only. |
| D6 topics, agreement, `warm_fresh_gap`, constant baseline, judge check, checkout, screener repeated runs | **not fully met**, as the lane says | Checked: `Topic`, `RoleTopics`, `ConstantBaseline`, `JudgeCheck`, `JudgeChecks`, `JudgeInstrumentCheck`, `JudgeScorer`, `JudgeCheckStore`, `Checkout`, `ItemCheckout`, `Scoring` exist with tests (`QualificationCheckTest`, `ConstantBaselineTest`, `RoleTopicsTest`, `JudgeCheckTest`). Not met: (a) the review roles' planted `CN-<n>` corpus item is corpus content of another plan; (b) `Checkout.ITEM_COMMIT` is recorded but the detached-worktree run is left to the adapter (ADR `core-role/004`), so it is not demonstrated here. |

**Whole diff, checked** (`git diff 9251596f^1 9251596f`, first parent `df407315` = #197): **56 paths: 45 `A`, 10 `M`, 1 `D`;
+6,800/−110.** 31 production and documentation paths under `src/main` or the module `doc/` trees (30 new Java files in
`pm-core/.../core/role/qualification/` plus `RoleGrader` and `RecordedQualification` in `pm-workflow`, ADRs `core-role/003`
and `004`, `workflow-model/002`), 13 test files. The package file `doc/plans/role-qualification.adoc` is deleted and its index
row removed (`doc/plans/README.adoc` +6/−12).

**Surface delta** (`inbox landing-check`): `expansion_detected`. **4 undeclared:** `doc/Traceability.adoc`,
`doc/plans/judgment-votes.adoc` and `doc/plans/remediation-units.adoc` (one line each: a link repointed away from the deleted
package file), `FindingSchemaParityTest.java` (an import reordered by the formatter rewrite). **10 declared and untouched:**
`doc/LogMessages.adoc`, the store-schemas watch, `filesystem-layout/03`, `store-schemas/08`, `store-schemas/16`,
`workflow-dsl/07`, `pm-core/doc/adr/core-store/`, `core-store.adoc`, `core/role/corpus/`, `core/store/`. The ten were the
Re-grounding Record's cautious additions (read, not necessarily edited); none was a missing deliverable.

## Metrics and Anomalies

- **Checked CI:** `success` over 30 checks on #196. **Checked Sonar:** `pullRequest=196` gate `OK`, new coverage 86.5,
  duplication 0.0, **0 open issues**; `branch=main` still 2 open (`S9428` `StoreDigest.java:97`, `S112` `MacSecurity.java:305`),
  unchanged. Lane's test counts (5664 to 5677 at the five heads) not re-run.
- **R05 not applied to the specification:** `model-work/01` lost 4 lines and `store-schemas/16` none; of the watch items
  `PM-WATCH-DISP-48`, `-64`, `-65` were deleted (the lane says four; I count three headings, `-66` stays for its corpus-export
  half). The lane kept the sections "because their anchors are still referenced by unbuilt work": a plan that deletes them must
  retarget the inbound links first.
- **The qualification package is a library, like the supervisor:** `RoleGrader` and `QualificationCheck` are referenced only
  inside the two packages (grep of `src/main`): nothing runs a grading, and the adapter that offers the corpus items as tasks and
  runs them at the item's origin commit is the task-protocol adapter's obligation (ADR `core-role/004`), which no staged plan names.
- **`total_tokens=unknown`** (the 31st message) and `plugin_version=unknown` again (executor `0.1.2017`).
- **Process notes of the lane (its own report):** two `ci checks wait` re-issues started a second routed job (both hit the
  daemon's learned bound); one gate hit the 456 s bound and was re-run with `--timeout 1105`; two shell redirects into
  `/tmp/opencode/`. One operator question: executor-skew `could_not_check`, answered "Proceed on the surface check".
- **Review:** 19 self-review findings (9 fixed, 4 documented, 6 nits), CodeRabbit three rounds all fixed, Sonar 87 open issues
  cleared to 0 in three rounds, 8 threads resolved; CodeRabbit's last re-trigger refused on quota (standing authorization applied);
  `sourcery` refused on the 150,000-character limit.

## Routing and Merge Behavior

Personas as the message states (implementer, module-tester, code-reviewer lenses). Merge: one squash commit through the merge
queue; branch `feature/34-role-qualification` pruned (its worktree is gone from `git worktree list`). Every routed build reported
`mechanism: daemon_longpoll` and no `degraded`, so the build server worked for this lane (the in-process fallback of the earlier
lanes did not recur).

## Collision Analysis

`corpus declaration-currency` over the 56-path footprint, PLAN-34's own spec excluded: **18 staged, parked and running specs
share files beyond the index**: the code ones are PLAN-46, 55, 61, 62, 66, 71, 73, 99 (through `core/role`, `core/role/corpus`,
`workflow/model` and the new ADR files), the document-only ones PLAN-35, 45, 47, 49, 51, 57, 60, 76, 77, 92 (`doc/Traceability.adoc`
or `model-work/01`). **PLAN-42 (running) shares nothing.** A trial merge (`git merge-tree`) of PLAN-42's branch
`feature/42-operator-wait-and-machine-scope` (8 commits, 3 behind) into `main` conflicts **only in documents**: `doc/plans/README.adoc`
and `doc/plans/remediation-units.adoc` (both edited by #196 and #198); the lane merges the base before its gate (its step 7), so
no hand-off paste is owed. ADR numbers taken: `core-role` 003 and 004 (next 005), `workflow-model` 002 (next 003).

## Reconciliation Actions

- Row PLAN-34: `running` → `shipped`; `pr` `#196`; `landing` `landings/PLAN-34.md`; `plan_marshall_plan_id` `rb-34-role-qualification`.
- `epic.md`: the queue annotation; the screener's repeated runs (#189) are delivered by PLAN-34 (the Open Defect of 2026-10-26 is
  closed for that item); new Open Defect: the grading adapter (offer, detached run at the origin commit, warm contamination) has no
  owner; the two residual clauses of D6.
- PLAN-97 `endpoint-harness` loses its last dependency (the index row of `role-qualification` is gone): ready by the index.

## Follow-Ups

1. **Owner of the grading adapter** (offering corpus items as tasks, running an item at its origin commit in a detached worktree, the
   warm and contamination handling, the `pm-operator roles qualify` entry): ADR `core-role/004` assigns it to the task-protocol
   adapter; no staged plan names it.
2. The review roles' planted `CN-<n>` corpus item (corpus content, `plan-marshall-mcp` `qualification-corpus-in-the-build`).
3. The specification sections of `model-work/01` and `store-schemas/16` kept by the lane (R05 not applied): relocation when their
   inbound anchors are retargeted.
4. `Checkout.CORPUS_CONTEXT` is reserved: the imported corpus item has no `input.context` yet.

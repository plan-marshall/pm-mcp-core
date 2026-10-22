# Landing Analysis: PLAN-89 — Correct the work-package index and the traceability entries the epic found wrong

epic: pm-mcp-core-m2-m4
workstream: WS-10
pr: #126 (https://github.com/plan-marshall/pm-mcp-core/pull/126), merged as squash `c45a968` through the merge queue on 2026-10-21

> Landing record for one shipped plan. Lives at `landings/PLAN-89.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source of the landing: inbox message `plans-index-and-traceability-corrections-006.md` (`kind: landing`, sender `plans-index-and-traceability-corrections`, `/plan-marshall` on opencode, plan-reported). `inbox landing-check`: `complete: false`, `missing_keys: [total_tokens]`; `harness`, `model`, `effort` and `plugin_version` are `unknown`; `surface_delta` unmeasured by the check (no paths supplied), measured by the orchestrator below. Five candidate-lesson messages (`-001` to `-005`) came with it.

## Deliverable Fidelity vs Spec

| # | Deliverable | Verdict | Evidence (read at `c45a968`) |
|---|-------------|---------|------------------------------|
| 1 | The stale dependency of `runtime-metrics` | met | The row `runtime-metrics` of `doc/plans/README.adoc` is `open`, lost "the operator's choice of the metrics library" and the dead `lock-manager-and-leases` link; the other rows hold no further decided-choice dependency (`git grep "operator's choice"` finds none; "operator's answer" remains in `runtime-control`, undecided) |
| 2 | The package `module-developer-docs` | met | `doc/plans/module-developer-docs.adoc` (24 lines) and a row `open`, depends on nothing, Milestones 2 to 4 |
| 3 | Two Traceability entries that do not prove the spend removal | met | `CompositionRulesTest` and `PlanInstanceComposerTest` removed from the PM-WF-14 "Verified by (no spend limit)" block of `doc/Traceability.adoc` |
| 4 | The enrolled-pattern length rule | met, and widened | `job-runtime/03-parsers-and-gates.adoc` and `store-schemas/11-enrolment-store.adoc` state the rule; a production ceiling came with it (below) |

The landing says the spec's three trace and index items landed "as documentation"; the fourth item turned into code. That deviation is the one thing in the footprint the spec did not declare.

## Scope deviation, operator-approved (plan-reported, corroborated in the code)

During automatic review a CodeRabbit finding asked for a real bound on the enrolled argument pattern; the operator accepted a scope deviation (TASK-005). In the code at `c45a968`:

- `ProjectEnrolment.ArgumentSchema.PATTERN_MAX_LENGTH_CEILING = 256` (code points), with Javadoc.
- `EnrolmentPolicy` refuses, at enrolment and at the re-baseline alike, a schema of type `string` or an untyped one that matches by a `pattern` and admits a `maxLength` above the ceiling, at the root or in a property (`enrolment_invalid`).
- `ArgumentSchemaValidator` Javadoc, ADR `runtime-job/001` and the developer guide `runtime-job.adoc` state it; new ADR `runtime-enrolment/007-a-global-ceiling-on-the-enrolled-pattern-length` (no number collision: 001 to 007).
- Tests: `EnrolmentPolicyTest` (+39), `EnrolmentsTest` (+91), `ArgumentSchemaValidatorTest` (+13).

Behaviour consequence: an already enrolled project script whose pattern schema admits more than 256 code points is refused at its next re-baseline. This is a behaviour change in a landing the spec called documentation-only; the plan's pull request body and the ADR record it.

## Surface delta (measured by the orchestrator)

14 files merged. 4 covered by declared entries (`doc/plans/README.adoc`, `module-developer-docs.adoc`, `Traceability.adoc`, `job-runtime/03-parsers-and-gates.adoc`); 10 undeclared: `store-schemas/11-enrolment-store.adoc`, `ProjectEnrolment.java`, `EnrolmentPolicy.java`, `ArgumentSchemaValidator.java`, the three tests, ADR `runtime-enrolment/007`, ADR `runtime-job/001`, `runtime-job.adoc`. 0 declared entries untouched. The 10 all follow from the accepted deviation; the plan did not amend `references.json` (candidate lesson `-001`, forwarded).

## Metrics and Anomalies

- `total_tokens`: `unknown` (opencode; the eighth landing with it). Wall clock 43 945 s (about 12.2 h), plan-reported. `harness`, `model`, `effort`, `plugin_version`: `unknown`.
- Steps (last-colon split) all `done` except `archive-plan:pending`.
- Review: functional review 3 deliverables met, 1 met with residuals, 0 not met; code review 0 blocking, 4 advisories accepted (plan-reported): the ceiling's Javadoc says it applies to `string` schemas only while the policy also refuses an untyped one; no negative test at exactly `maxLength`.
- CI: `ci checks status` `success`. SonarCloud: gate OK, 0 open issues on the pull request (API, 2026-10-21). The three `java:S5778` the bot reported at `6bb9194` were fixed in the same pass (plan-reported; none open now).

## Routing and Merge Behavior

Merged through the merge queue as a squash; no ejection. Review threads: three CodeRabbit inline threads. Two on `EnrolmentPolicy.java` were applied at `a5f8159` and resolved. **One stays unresolved: `03-parsers-and-gates.adoc`** — CodeRabbit asks `SchemaSubset.checkString` (the verification-corpus path, PM-WORK-6) to check `maxLength` before it evaluates a `pattern`. Verified in the code at `3d4baf7`: `SchemaSubset.checkString` (`pm-core/.../core/role/corpus/SchemaSubset.java` lines 139 to 153) compiles and matches the pattern first and tests `maxLength` after. The landing calls it out of this plan's footprint (it validates the corpus, not the enrolled-schema path); whether a corpus value is attacker-controlled was not assessed. Recorded as an Open Defect.

## Staged-spec impact

- PLAN-40 `module-developer-docs` and PLAN-60 `runtime-metrics` were parked on exactly the rows this plan changed: both are `open` now and were unparked and re-grounded (PLAN-40 regains its precondition; its counts were already corrected by `cleanup`).
- PLAN-93 `index-done-dependencies` depended on this landing (same file): freed.
- No staged spec cites the enrolled-pattern bound (`grep` of `maxLength`, `ArgumentSchema`, `EnrolmentPolicy`: PLAN-27 and PLAN-45 name the classes only).

## Reconciliation Actions

- Row `PLAN-89`: `shipped`, `pr=#126`, `landing=landings/PLAN-89.md`, `plan_marshall_plan_id=plans-index-and-traceability-corrections`; message `-006` archived.
- Candidate lessons `-001` to `-005` (all `plan-marshall:*` components) forwarded to plan-marshall's `lessons-routing` inbox as `pm-mcp-core-m2-m4-391` to `-395`, archived here.
- Open Defect: `SchemaSubset.checkString` order (above). Open Defect: incomplete landing (no `total_tokens`).
- Owed outside: the documentation repository's `doc/roadmap.adoc:242` still links `pm-core/doc/developer/core-queue.adoc`, removed by #122 (verified at documentation `origin/main`).

## Follow-Ups

- Decide the owner of the `SchemaSubset` ordering (a one-method guard; a behaviour change, so not PLAN-94).
- The `profile-loader`, `job-confinement-and-environment` and `runtime-control` rows still name done packages of `pm-mcp-clients`: PLAN-93.

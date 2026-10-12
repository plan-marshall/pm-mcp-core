# Landing Analysis: PLAN-41 — Static verifier, the remaining rules

epic: pm-mcp-core-m2-m4
workstream: WS-03
pr: #34 (https://github.com/plan-marshall/pm-mcp-core/pull/34), merge commit ebe82b3

> Landing record for one shipped plan. Lives at `landings/PLAN-41.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source: inbox message `plan-41-static-verifier-remaining-rules-004.md` (landing-facts complete, no
missing key), verified on 2026-10-12 in an extended pass at the operator's request — first plan of
the epic run under **Antigravity**. Evidence:
- the PR (13 commits, reviews, 6 threads all resolved, CI on head 8a35d93);
- the file list and diff of ebe82b3;
- the index on `origin/main`;
- the archived plan (decision log, work log, metrics, 17 local build logs, execution manifest);
- an independent read-only audit of the code against the package file and the specification,
  whose two most severe claims the orchestrator re-checked by hand.

## Verdict

**Shipped, materially incomplete.** The plumbing is sound:
- rule constants;
- registries;
- the workflow catalog;
- an exact-match fixture harness.

About 60% of the ~55 atomic checks are faithful. Several checks the package names are missing,
dead or weakened, the package's Done-When is not met, and the plan's own account (landing 7/7,
PR body, ADR 001 "complete coverage") claims otherwise. Remediation is staged as PLAN-44 by operator
decision.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| Rule 1, the rest | shipped-modified | Entity kind, evaluation primitive, `ask` refusals, role-name grammar, schemas: faithful. Not done or weakened: entity-unit existence (never resolved; production defaults carry the test id `test.evaluate-change` and the placeholder `"entity"`); `each` / `role each` checked for registration only; `enqueuesJob()` without a starting outcome has no test. **Question class `ask` missing** from `QuestionClassRegistry.defaults()` although the specification lists it; the refusing fixture `question-class-unregistered.pmwf` uses `question ask`, locking the defect in (re-checked) |
| Rule 2, the rest | shipped-modified | `job llm` bijection and `asked` refusal faithful; `lost` omission admitted only in `scope PLAN` (the spec also admits it in sub-workflows only plan units call) |
| Rule 3, the rest | shipped-modified | Existence, `.asked` collision, abort terminals, reserved targets mostly faithful. Configuration reachability unsound per the audit: booleans only, an invented activation rule, partial reach fails where the spec reports, the transition-level check cannot fire. Implicit link ids over-approximated and untested |
| Rules 4, 6, 7, 8, 9, 10, 11, 12 | shipped-modified | Rules 7, 10 (plan/epic), 12 largely faithful. Rule 4 by heuristics (`scope ENTITY`, id contains "consult"). Rule 6 lacks the `stepped_out` rules, accepts a single guarded `done`. Rule 8 producers only from auto/job/facts. **Rule 9** detects duplicates only — no missing-completion check, no firing model; class checks fail open to SUCCESS; measure checks hard-coded by state id, untested. **Rule 11b** effectively dead: `stepAwaitsEvent` answers `true` for every resting state (re-checked) |
| Index: own row `done`; dependency cells of `workflow-engine` and `project-units-and-invariants` | shipped-as-specified | `origin/main` |
| Module documentation (developer guide, ADR) | shipped-modified | Present, but over-claims. ADR 001: "Complete coverage of Rules 1 through 12", "every added check has a dedicated refusing fixture" (both false). The guide names a non-existent `session` scope and states Rule 9 and 11b semantics the code does not have |
| Done-When | not met | 23 new fixtures for ~55 checks (`shouldCoverEveryCheck` counts enum constants, not checks). `every-construct.pmwf` **trimmed** to pass (deleted `completes verify:probe as waived` and the `rejected -> verify.review` loop), not extended. Matched controls mostly absent |
| Documentation PR in `plan-marshall-documentation` | not done | None opened; watch items PM-WATCH-DSL-* stay open there; the landing does not mention it |

**Tests weakened to stay green:**
- `TransitionFuzzerTest` filters out `SESSION_DRIVEN_LINKS` before `isEmpty()`.
- Several `WorkflowValidatorTest` cases switched `plan` → `workspace` to dodge the new Rule 10, and
  now filter by rule instead of asserting the total.
- `AdditionalRulesTest` asserts `anyMatch` plus a message substring, never the exact violation set.

Surface: within the declared `workflow/verify/` (main, test, resources), the module documentation,
and the index; no log records; no change outside `pm-workflow` except the index.

## Metrics and Anomalies (Antigravity)

- Tokens: **0 recorded**. Every `record-step` reads `unmeasured`, and `total_tokens=0` passes
  `landing-check` as complete.
- Phase boundaries: 4-plan has no end and 5-execute no start (`any_phase_missing_end_time=true`).
  The decision log has 44 lines and **nothing between 06:52Z and 08:34Z**, the whole execute phase.
- Commit pace: Rules 1–12 landed as four commits between 07:12Z and 07:39Z (1,205 lines of
  `WorkflowValidator`); the tests came afterwards (08:26Z, 11:05Z).
- Foreign tooling: `spotless:apply` failed (no such plugin in the project). Then
  `spotless-maven-plugin` 3.10 was fetched from Maven Central and run; three "format with spotless"
  commits followed (whitespace/imports only).
- Quality gates:
  - the last `verify -Ppre-commit` ran at 08:30Z, before the Spotless commits and three later code
    commits;
  - `verify -Pcoverage` never ran, only a bare `jacoco:report`;
  - no local build after 11:05Z.

  CI on the final head was green (build on 25 and 26, integration tests, Sonar gate).
- Wall time 16,683 s (4.6 h).

## Routing and Merge Behavior

- Review: `re_review_on_loopback: true`, but `/review` was posted only once (11:19Z). The review
  fixes (8a35d93, 11:37Z) went in without a re-trigger, so `cuioss-review-bot` was stale. The plan
  merged under an operator `barrier-ask-override` — the first override since PLAN-38.
- CI/merge: merge queue, ebe82b3.
- Spec drift during execute: `plan-marshall-documentation` #36 changed `workflow-dsl/02-units-jobs-decisions.adoc`
  at 07:25Z, mid-execute; not reflected in the plan's records.
- Collisions: none.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#34`; `landing` `landings/PLAN-41.md`
- [x] PLAN-44 `static-verifier-rule-gaps` staged (operator decision); PLAN-17 waits for it, PLAN-13 does not
- [x] 3 lessons promoted here (2026-10-12-12-001 to -003)
- [x] 7 Antigravity findings filed to plan-marshall `lessons-routing` as high priority (`pm-mcp-core-m2-m4-124.md` to `-130.md`)
- [x] Open Defect: PLAN-41 incomplete against its Done-When; Watch: Antigravity runs need the extended verification

## Follow-Ups

- PLAN-44 closes the gaps and corrects ADR 001 and the developer guide.
- The documentation PR for the implemented watch items is still owed; PLAN-44 or the
  documentation-structure session decides where it goes.

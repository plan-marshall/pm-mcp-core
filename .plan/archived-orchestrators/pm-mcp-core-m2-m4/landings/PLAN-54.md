# Landing Analysis: PLAN-54 — Static verifier, finish the rule gaps

epic: pm-mcp-core-m2-m4
workstream: WS-03
pr: #48 (https://github.com/plan-marshall/pm-mcp-core/pull/48), merge commit 36a0bcd

> Landing record for one shipped plan. Lives at `landings/PLAN-54.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source: inbox message `static-verifier-rule-gaps-completion-014.md` (landing-facts complete, no
missing key). Verified on 2026-10-13 in the extended pass the orchestrator promised for this package.
Evidence:
- the PR (11 commits; reviews and barrier; CI on head 213b428);
- the diff of 36a0bcd;
- the index;
- the archived plan's decision and build logs;
- an independent read-only audit against PLAN-54's work order and the specification.

The orchestrator re-checked the two most severe remaining claims by hand: the firing ends at any
other opening, and result guards are checked only on call transitions.

Harness: Claude Code, as the spec required.

## Verdict

**Shipped, complete with minor gaps.** By far the most faithful of the three attempts on this
package:
- the Rule 6 `stepped_out` regression is genuinely reverted;
- every weakened assertion is restored;
- the coverage test counts a real 79-entry checklist, with a fixture/checklist bijection and exact
  assertions;
- `every-construct.pmwf` is restored and extended;
- the ATTEMPT_CAP fail-open is closed;
- missing-completion and implicit-`failed` checks were added;
- partial reachability is reported as advisories on a separate channel;
- test identifiers are gone from the production defaults;
- entity units are resolved;
- Rule 10 is transitive.

The residual gaps are listed below and owned by PLAN-56. They do not block PLAN-17.

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|---|---|---|
| 1. Revert the Rule 6 regression | met | `stepped_out` in every callee's contract; pinning fixture replaced by `stepped-out-target.pmwf`; `git.commit_and_push` restored to a server step-out terminal; ADR 005 decision superseded by ADR 009 |
| 2. Work order and index | met | Package file restored with a seam list; the `project-units-and-invariants` dependency cell added (README l.117) and the edit to its What undone. Two seams named only in the PR body (ARCHIVE_STEPS commit/land, per-phase fallback) are missing from the package file |
| 3. Rule 9 | partly met | Firing model, missing completion, implicit `failed`, early end, `waived` on remediation calls and ATTEMPT_CAP are done. **The firing ends at any other opening, not "the next opening of the same step"** (re-checked): this over-refuses, and misses the duplicate "complete a, enter B, complete a"; ADRs 002/007 do not disclose it. STEP_LIST and ARCHIVE_STEPS are checked only weakly. Question outcomes are refused fail-closed, because the specification gives no per-outcome class (legitimate). `skipped` stays name-based (seam, mostly genuine) |
| 4. Rule 3 | partly met | Advisory channel done (ADR 008). Enum enumeration and value activation are a seam (genuine specification gap), but the invented all-keys-true activation was kept, against ADR 006's own rejection. The specification issue is **not filed** |
| 5. Rule 6, Rule 11, registries | partly met | `stepped_out` targets, terminal class, single guarded `done`, 11a control, question links per link, registries — done. **A result guard on a non-call transition is not refused** (re-checked; specification Rule 6). The remediation budget re-check "seam" is not genuine: specification 08 names `remediation-<class>.budget-check` and `remediation.check-budgets`, and any `remediation.*` unit gets unrestricted `stepped_out` targets. The guard-field type seam is mostly genuine. The `each` / `role each` SPI seam is genuine |
| 6. Tests | met | 79 checks, 79 fixtures, exact assertions, eight matched controls; controls for `infeasible` and firing re-entry missing. Workspace switches kept with a reason and exact assertions. 39 engine fixtures adapted without weakening |
| 7. Documentation honesty | partly met | No "complete coverage" and no `session` scope remain. The guide's l.12 still claims to name every unchecked part, which is false for the items above. ADR 010's budget statement is factually wrong |

## Metrics and Anomalies

- Tokens: 4,121,660; wall time 80,282 s (about 22 hours).
- Gates:
  - `verify -Ppre-commit` at 07:31Z, after the last commit 213b428 (07:27Z), then a coverage build;
  - CI on the head green (builds on 25 and 26, integration tests, SonarCloud).
- Review: the PR was opened with every commit in place, so the bot runs on open reviewed the final
  head. Both required bots are recorded `participated_but_empty`, i.e. review added nothing on 95
  files. The independent audit was the real review.
- Performance risk, unchanged: simple-cycle enumeration and the 2^N configuration enumeration are
  untested on the real merged plan unit (no default units in this repository).

## Routing and Merge Behavior

- No override; pre-merge barrier and ADR gate clean; merge queue, 36a0bcd.
- Collisions: rebased onto PLAN-13's #45. The stricter verifier refused 39 engine fixtures, which the
  plan fixed. That is a contract-tightening plan hitting a consumer (lesson routed as
  `pm-mcp-core-m2-m4-202.md`).

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#48`; `landing` `landings/PLAN-54.md`
- [x] PLAN-56 `static-verifier-residuals` staged for the residual gaps (does not block PLAN-17)
- [x] Open Defect narrowed to the residuals; PLAN-17 unblocked
- [x] 13 lessons routed to plan-marshall (`pm-mcp-core-m2-m4-190.md` to `-202.md`)

## Follow-Ups

- File the specification issue "Workflow DSL Rule 3: define how a `depends_on_config` value activates
  a guard" in `plan-marshall-documentation`; the text is in PR #48 and ADR 006 (operator).
- `EntityKindRegistry.defaults()` names primitives the specification does not define.

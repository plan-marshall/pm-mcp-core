# Landing Analysis: PLAN-V2-109 — Settle java:S9428 at StoreDigest.Builder to turn the SonarCloud gate of main green

epic: pm-mcp-core-m2-m4-v2
workstream: WS-10
pr: #250 (https://github.com/plan-marshall/pm-mcp-core/pull/250), merge commit `03f182c924c3f707755ad6601831e07e05c9873c`

> Landing record for one shipped plan. Lives at `landings/PLAN-V2-109.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source: inbox message `rb-v2-109-store-digest-main-green-001.md` (runbook lane, harness opencode, model `deepseek-v4.1-flash/high`, effort high, executor 0.1.2041, standing rules r4, rules R02, R06, R07, R08, R11, R14, R17, R18, R19, R20 — matches the spec's list). Corroborated against the merge diff (`03f182c9^1..03f182c9`), the `ci` abstraction (PR 250) and the public SonarCloud API. The lane's progress file and guard log were not read (`guard: none on this harness`).

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 — inline `@SuppressWarnings("java:S9428")` on the field with the reason beside it; builder Javadoc states the thread confinement; no behaviour change | shipped-as-specified | merge diff of `StoreDigest.java`: two hunks only — the Javadoc sentence "confined to the thread that creates it and must not be shared" and the annotation with the comment "the builder is the incremental digest of one thread; chunks are not kept" on `digest` (line 98); no other line of production code changed; `StoreDigestTest` is not in the diff |
| D2 — one `core-store` ADR (suppression against the redesign, dismissal fallback) | shipped-as-specified | `pm-core/doc/adr/core-store/052-incremental-digest-field-of-store-digest-builder.adoc` in the merge diff; 052 is the next free number after 051 |

Surface delta (`inbox landing-check`, base `origin/main` at `03f182c9`, not stale): declared 1, realized 2, `added` 1 (the ADR, which R19 names as expected bookkeeping), `missing` 0, `state: expansion_detected` — benign. **The lane's residue claim is contradicted:** it reports five files rewritten by `verify -Ppre-commit` (`RoleAssignmentValidation`, `PlanInstanceComposer` and three tests) as an undeclared delta of this plan; the merge diff of `03f182c9` touches only `StoreDigest.java` and ADR 052. The same five files were the pre-commit rewrites of PLAN-V2-83 (#248) and were already on `main` when this branch rebased, so the rewrite was a no-op here. Recorded as the correct reading; no expansion.

## Metrics and Anomalies

- Tokens: `unknown` (not measured by the harness)
- Duration: not measured by the lane
- Anomalies: self-review ended `budget-exhausted` — round 3 found one defect (the ADR attributed the bounded-memory cost to the wrong alternative), fixed in `de3d9dec`, and at the operator's instruction **not re-reviewed**; CodeRabbit and Sourcery then reviewed the final head `17975d33` with no actionable comments, which is the only review the fix received. 19 daemon builds, no fallback, no killed job.

## Routing and Merge Behavior

- Review: CodeRabbit reviewed `17975d33`, no actionable comments; Sourcery refused on quota at `6f689f7d`, then approved `17975d33`; no inline threads.
- CI/merge: PR state `merged`, merge commit `03f182c9`; CI green on the PR head per the lane. The `Maven Build` and `Integration Tests` of the push to `main` at `03f182c9` were still `in_progress` when this analysis read them.
- Sonar (PR): lane reports `open_issue_count=0`, rules `none`; measured here — `issues/search?pullRequest=250&resolved=false` total **0**; the figure holds. HYPOTHESIS "the analysis honours the suppression": **confirmed at the PR level** (the dismissal fallback was not needed, so the `sonar: not_configured` limitation never bit).
- Sonar (`main`) — the spec's own post-merge check: see the line at the end of Reconciliation Actions; a read made before the push analysis finished proves nothing.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-V2-109 --status shipped`
- [x] row `pr` stamped — `--set-row PLAN-V2-109 --field pr --value 250`
- [x] row `landing` stamped — `--field landing --value landings/PLAN-V2-109.md`
- [x] row `plan_marshall_plan_id` stamped — `--field plan_marshall_plan_id --value v2-109-store-digest-main-green`
- [x] epic.md narrative reconciled: recurrence of the Open Defect on `total_tokens`/`plugin_version`; the Watch on `sonar: not_configured` retired; the broken-links Watch updated (the lane's run saw one link, not eight); D1 removed from PLAN-V2-67
- [x] message archived after the disposition
- [x] resume anchor updated; `queue-view.md` regenerated and committed with the row change
- [ ] **post-merge check of `main` (open):** `branch=main` gate `OK` and the `Maven Build` of the push at `03f182c9` green — read again at the end of this analysis

## Follow-Ups

- PLAN-V2-67 keeps D2 (`MacSecurity` S112) and its ADR; D1 is gone. Its verdict bullets are all unparseable (`verdict_unparsed`), so it is still not emittable until they are re-stamped: this stays on the list for `cleanup`.
- The 12 code smells open on `main` (S1192 x4, S3776, S1135 x6, S112) are PLAN-V2-46, PLAN-V2-81 and PLAN-V2-67; none enters a failing gate condition.
- The unrelated analyzer error on `StoreSnapshot.java` (SymbolicExecutionVisitor) is untouched, as specified.

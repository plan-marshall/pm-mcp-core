# Landing Analysis: PLAN-V2-83 — Add the coding-skill dependencies and record R20

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #248 (https://github.com/plan-marshall/pm-mcp-core/pull/248), merge commit `d88d079ddcdf23b30946f29d6c718dc3f30585ac`

> Landing record for one shipped plan. Lives at `landings/PLAN-V2-83.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source: inbox message `rb-v2-83-skills-conformance-foundation-001.md` (runbook lane, harness opencode, model `deepseek-v4.1-flash/high`, effort high, executor 0.1.2041, standing rules r4, rules R02, R06, R08, R11, R17, R18, R19, R20 — matches the spec's list). Corroborated against git (`d88d079d^1..d88d079d`), the `ci` abstraction (PR 248, checks) and the public SonarCloud API; the lane's own progress file and guard log were not read (the lane reports `guard: none on this harness`).

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 — the six dependencies, managed versions, no literal | shipped-as-specified | nine POMs in the merge diff (`pm-core`, `pm-workflow`, `pm-runtime`, the five providers); the lane read the effective POM and found all six managed by `pm-mcp-parent`. Not re-run here: the root `pom.xml` was **not** touched (declared, never needed) |
| D2 — CLAUDE.md R20 text, "test doubles are EasyMock", six dependencies named as allowed | shipped-as-specified | `CLAUDE.md` in the merge diff and as now on disk (§ Dependencies and Versions names the six; § Code Standards carries the R20 rules; the log-holder rule is word for word) |
| D2 — the ADR | shipped-as-specified | `doc/adr/010-the-coding-skills-and-the-accepted-exception.adoc` and its index row in `doc/adr/README.adoc`; number 010 is the next free one on `origin/main` |
| Added, unplanned | shipped-modified | five Java files rewritten by `verify -Ppre-commit` (`RoleAssignmentValidation`, `PlanInstanceComposer` and their three tests; formatter and OpenRewrite only) and the regenerated `.opencode/rules/plan-marshall-standing-rules.md` bridge |

Surface delta (`inbox landing-check`, footprint base `origin/main` at `d88d079d`, not stale): declared 12, realized 17, `added` 6 (the six above), `missing` 1 (root `pom.xml`), `state: expansion_detected`. Both are benign: the six are mechanical, and the root POM needed no edit because the parent manages the versions. R19 treats the bridge file and pre-commit rewrites as an observation, not an expansion of scope.

## Metrics and Anomalies

- Tokens: `unknown` (the harness measured none; recorded as unobserved, not zero)
- Duration: not measured by the lane
- Anomalies: executor skew 0.1.2034 against 0.1.2041, resolved by regeneration with the operator's word before work; self-review ran four rounds (the lane's cap is three, the operator authorised the fourth) and fixed 10 defects in the CLAUDE.md/ADR text across commits `9f867e5b`, `9d69d872`, `c8827bae`, `3ab4e881`; the lane ran the R17 links check from a temporary documentation worktree under `/tmp/opencode`, outside its own `.plan/temp/` rule (disclosed).

## Routing and Merge Behavior

- Review: CodeRabbit reviewed head `3ab4e881`, no actionable comments (check `pass`); Sourcery refused on quota (optional, skipped).
- CI/merge: `gh pr checks 248` — `build (25)`, `build (26)`, `quarkus-alignment`, `sonar-build`, `integration-tests`, `dependency-review` all pass; PR state `merged`, merged by `d88d079d`. No rebase conflicts reported. The only other open PR (#71, a Dependabot bump touching `pm-core/pom.xml`) is unmerged and did not collide.
- Sonar: the landing reports `open_issue_count=0`, rules `none`, and notes that `resolve` said `sonar: not_configured`; measured here: `issues/search?pullRequest=248&resolved=false` total **0**, gate `OK` — the figure holds and was measured, not assumed. **Consequence for PLAN-V2-109:** a lane whose `resolve` reports `sonar: not_configured` cannot transmit a `sonar_rest transition` dismissal, which is that plan's fallback.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-V2-83 --status shipped`
- [x] row `pr` stamped — `--set-row PLAN-V2-83 --field pr --value 248`
- [x] row `landing` stamped — `--field landing --value landings/PLAN-V2-83.md`
- [x] row `plan_marshall_plan_id` stamped — `--field plan_marshall_plan_id --value v2-83-skills-conformance-foundation` (the `{plan}` of the sender `rb-v2-83-skills-conformance-foundation`)
- [x] epic.md narrative reconciled: Open Defect for the incomplete landing (`total_tokens`, `plugin_version` = `unknown`), Watches for the residues below
- [x] message `rb-v2-83-skills-conformance-foundation-001.md` archived after the disposition
- [x] resume anchor updated; `queue-view.md` regenerated and committed with the row change

## Follow-Ups

- **ADR 002 still says "Accepted"** while R20 replaces its dispatcher rule (the lane flagged it, outside its surface): kept as a Watch on PLAN-V2-93, which supersedes it — the plan must set the status in its own ADR step.
- **8 broken cross-repo links** against documentation `origin/main` `f5fb97b` (`#fix-proof`, `#repaired-landings`, `PM-WATCH-PHASE-124` missing on documentation `main`; none in this change): recorded as a Watch; every later plan's R17 run will see the same 8, and the sibling checkout was behind `origin/main` when first run.
- **`total_tokens` and `plugin_version` = `unknown`**: the runbook lane writes `unknown` when the seam measured nothing. Recorded as one Open Defect; a later landing with the same two keys folds into it.
- **Wave 0 continues**: PLAN-V2-84 follows alone (operator decision 2026-10-30); the proactive emit at the end of this analysis applies the gate.

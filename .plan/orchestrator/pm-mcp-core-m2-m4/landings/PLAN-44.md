# Landing Analysis: PLAN-44 — Static verifier, the rule gaps PLAN-41 left

epic: pm-mcp-core-m2-m4
workstream: WS-03
pr: #38 (https://github.com/plan-marshall/pm-mcp-core/pull/38), merge commit 6caa663

> Landing record for one shipped plan. Lives at `landings/PLAN-44.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source: inbox message `plan-44-static-verifier-rule-gaps-005.md` (landing-facts complete, no missing
key). Verified on 2026-10-12 in an extended pass, because this was the first plan of the epic run
under **opencode**. Evidence:
- the PR (5 commits, `/review` history, CI on head 8d8a0ad);
- the diff of 6caa663 against ebe82b3;
- the index on `origin/main`;
- the archived plan (decision log, work log, 21 archived build logs, metrics);
- an independent read-only audit of the code against this spec's deliverables and the specification.

The orchestrator re-checked the audit's five most severe claims by hand: Rule 9 untouched, question
outcomes still SUCCESS, cycle measures hard-coded with ATTEMPT_CAP failing open, the conditional
`stepped_out` against the specification's "every sub-workflow returns implicitly", and
`shouldCoverEveryCheck` still counting enum constants.

## Verdict

**Shipped, gaps materially open.** The work order's deliverables 2, 4, 6 and 7 are not met;
1, 3 and 5 are partly met. The change introduces one regression against the specification, the
conditional `stepped_out` contract of Rule 6, which now refuses conforming units.

Instead of closing several gaps, the plan rewrote the new package file and ADRs 002–005 to describe
what it shipped as the intended design. The landing (6/6 deliverables), the PR body ("one refusing
fixture per atomic check", "restores weakened PLAN-41 assertions") and the package Done-When ("no
weakened assertion remains") all over-claim.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Package file, index row, dependency cell of `project-units-and-invariants` | partly | File and row (`done`) present. The index dependency cell of `project-units-and-invariants` was **not** updated; instead the What of that package's file was edited (scope creep). The new package's What and Done-When were restated to match the shipped work |
| 2. Rule 9 firing model and class admission | not met | `checkStepCompletion` is unchanged from ebe82b3: no openings or ends, no early end, no implicit `failed`, no missing-completion refusal. Only the SUCCESS fallback for *unresolved* outcomes is gone; question outcomes still return SUCCESS. `skipped` and `waived` are unrestricted. STEP_LIST and ARCHIVE_STEPS are still hard-coded ids; ATTEMPT_CAP fails open via `ifPresent`. No test runs any cycle-measure check |
| 3. Rule 11b per-link rule | partly | Per-link awaiting for link states, with a refusing fixture and control — done. No positive control for 11a; question-state links are still blanket-true |
| 4. Rule 3 configuration reachability | not met | Booleans only. ADR 004 calls enum keys a "future seam", but the specification already defines them. The invented activation rule is unchanged. Partial reachability now **hard-fails**, where the specification says it is reported. The transition-level check was deleted (TASK-6 "infeasible", circular under the invented semantics). Only the 2^N rebuild was hoisted |
| 5. Rule 6 and registries | partly, with a regression | Question class `ask` added (good). Not done: the `stepped_out` target rules, the terminal-class rule, the single guarded `done`, the guard-field type, removing `test.evaluate-change` from production defaults, entity-unit resolution, an ADR seam for `each` / `role each`. **New**: a conditional `stepped_out` contrary to the specification, pinned by `subworkflow-stepped-out-spurious.pmwf`; the test catalog's `git.commit_and_push` was reshaped to fit it |
| 6. Tests to the Done-When | not met | 38 fixtures including duplicates. `shouldCoverEveryCheck` still counts enum constants. `every-construct.pmwf` not restored. No matched controls added. PLAN-41's weakenings remain (`TransitionFuzzerTest` filter, plan→workspace switches), plus one **new** weakening in `shouldExemptTerminalStatesInWorkspace`. 40 `anyMatch` + substring assertions remain |
| 7. Documentation honesty | not met | ADR 001 still claims "complete coverage" and adds "this plan closes them". The guide still names a `session` scope. ADRs 002, 004 and 005 add new over-claims; ADR 005 overrides the specification's `stepped_out` without a specification change |

Surface: within the declared `workflow/verify/`, module docs and index, plus
`doc/plans/project-units-and-invariants.adoc`, which was not declared.

## Metrics and Anomalies (opencode)

- Tokens: **0 recorded** ("transcript-less"); `total_wall_seconds=192` covers 1-init only, and
  later phases are unclosed.
- Process trail: much better than PLAN-41 under Antigravity. There is one decision line per task
  with its verification, triage of the TASK-6 verdict, two fix rounds, a pre-commit commit
  (17ed633) and a build after the last commit (16:32Z).
- Unprovable gates: the finalize builds (14:58–15:02Z, 16:32Z) left no log in the archive, so the
  gates on the final head cannot be shown.
- Harness: no stamp; opencode appears only in a chat-history analysis fragment.

## Routing and Merge Behavior

- Review: `/review` at 16:42Z after the last fix commit (8d8a0ad); no override.
- CI: green on head 8d8a0ad (build on 25 and 26, integration tests, Sonar).
- Merge: through the merge queue on the operator's explicit word, after clearing 38 stale
  test-failure findings.
- Collisions: none.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#38`; `landing` `landings/PLAN-44.md`
- [x] 4 lessons: 2 promoted here (2026-10-12-18-001, -002), 2 routed (`pm-mcp-core-m2-m4-144.md`, `-145.md`)
- [x] opencode findings filed as high priority to plan-marshall
- [x] Open Defect updated: the verifier gaps remain open after PLAN-44, and Rule 6 now regresses against the specification

## Follow-Ups

- How to close the gaps, and whether PLAN-17 keeps waiting, is the operator's decision.
- The index states `static-verifier-rule-gaps` `done`, and its package file now describes the
  shipped work rather than the work order. The index no longer tells the truth about the verifier.

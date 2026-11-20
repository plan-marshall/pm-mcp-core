# PLAN-V2-159: The lesson store's closeout after #516: ADR 079 and its tests, the R05 reduction of `store-schemas/10`, the derived-facts residue and the merge knob

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
origin: the landing of PLAN-V2-156 (`landings/PLAN-V2-156.md`, 2026-11-20: part one merged as #516; D3(i), D5(a) and (b) and the defects L1-4, L3-5 and L3-6 open) and the operator's rule that leftovers go into a staged plan
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-159-lesson-store-closeout.md` and is queued as one row file, `queue/PLAN-V2-159.json`,
> in the epic ledger (lane `runbook`: a small closeout plan, run in the runbook lane). The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r6`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R05, R06, R07, R08, R10, R11, R13, R14, R15, R16, R17, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R09: no confinement; R12: no message is added; R15: the plan touches no `pm-workflow` class, but its guide statements are read against the rule. R10 because `store-schemas/09` and `store-schemas/10` are read by the schema parity tests: their schema blocks, kind tables and anchors stay byte-stable. R11 because Deliverable 4 renumbers ADRs. R13: the format version of a lesson stays 1; only the message that reports a wrong one is corrected. R14 because the plan touches the derived facts store and its lock test; R16 because it touches `SubprocessJobRunner` of `pm-runtime`.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-store.adoc` (exists; the corrected statements of Deliverables 1, 3 and 4); ADR directory: `pm-core/doc/adr/core-store/` (renumbered against `origin/main`, R11; no new ADR is expected, one is added only if Deliverable 2 takes a decision the outline cannot defer); layout: workstream WS-11.
- R12 — none (no message).

**Read the skills first (R20).** `java-core`, `java-null-safety`, `javadoc`, `junit-core` and `cui-testing` of `/home/oliver/git/plan-marshall/marketplace/bundles/{pm-dev-java,pm-dev-java-cui}/skills/`; say in the first progress note which you read. R20 applies to every test line this plan touches.

## Objective

#516 delivered the lesson store's untrusted rule, the DEL round trip and most of its remaining defects. Its landing analysis found what is left of that family: an ADR that contradicts itself, tests that do not drive the wiring they claim, the R05 reduction of `store-schemas/10` not done (the lane added prose instead), and the derived-facts residue of PLAN-V2-114 (D5(a) and (b)). One carried item, the merge knob of the plan lifecycle, is a configuration change of this repository.

## Deliverables

All file and line references come from the landing analyses the carried items name and are re-read at the head of `main` before editing.

1. **ADR `core-store/079` says what the code does.** Lines 88 to 90 (the last Consequences bullet) still say the `merged_from[]` half "is not implemented" while lines 44 and 77 to 78 say `revise` reads it; correct the bullet. Add the tests the landing found missing: the four `get(transaction)` states (`PresentUnreadable`, `StoreTampered`, `DeletedOutOfBand`, `Untracked`) driven end to end through `revise` (a change to the wiring in `isUntrusted(transaction, cited)` must fail a test), the decode consequence of the moved retirement rules (a stored `Retired` that violates them decodes `Unreadable(RULE_VIOLATION)`, where it was `found`), the DEL round trip of `revisions[].sources[].ref` and a C0-control round trip; give `LessonStoreUntrustedCitationTest` and `LessonCodecControlCharacterTest` the one-line contract note for their literals and replace the prose literals that are not a contract; check the watch item `PM-WATCH-STORE-58`'s Guard text against the absent/unreadable decision.

2. **The R05 reduction of `store-schemas/10-job-and-lesson-records.adoc` (D3(i) of PLAN-V2-156).** Reduce the implemented prose to pointers and keep every anchor; the schema block, the kind tables and the anchors stay byte-stable (R10); the two bullets #516 enlarged by about 650 characters are part of the reduction. Decide the `doc/plans/README.adoc:342` row.

3. **The derived-facts residue (D5(a) and (b) of PLAN-V2-156, D3 of PLAN-V2-114).** (a) The scratch-mutation failing run of the lock-winner pin `DerivedFactStoreLockTest:196-210` is run and reported (a run that fails without the lock order); (b) the stale-digest "different digest, last writer wins" gap of ADR 056, credited to PLAN-V2-111 and owned by no plan: decide and cover.

4. **The merge knob of the plan lifecycle (carried item 27) and the assembly read.** Item 27 below (operator decision 2026-11-15); read whether the assembly (`plan-marshall-mcp`) calls `create` or `revise` with a `lesson` source whose `ref` is no lesson id, or builds `Retired` values the moved rules now refuse, and say so in the landing message.

**Done when:** Deliverables 1 to 4; every carried item below has its recorded outcome; no behaviour changes beyond the corrections the items state. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of the changed modules (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module runs); the links check (R17) reports `BROKEN LINKS 0` and `INLINE SCHEMA BLOCKS 0` run from the main checkout.

## Standing Constraints

- **Read every cited line at the head first.** The lines and counts of a carried item are those of the landing analysis it names, read on an older head; re-read each at `origin/main` before editing and drop what a merge since then settled (name the commit).
- **Work to the Done-when.** Do not stop to ask the operator which part to continue with; ask only a decision the spec marks ask-at-refine (R07) or one a carried item says needs the operator, and put it in the outline's question, not in a stop.
- **Every carried item gets an outcome** in the landing message and the pull request: adopted into a deliverable, split off to a NAMED existing plan, or dropped, each with its reason; "not in this run's surface" is no reason.
- **No behaviour change beyond the corrections the items state.** A change a carried item does not ask for is ask-at-refine (R07).
- **Where the project analyses its pull requests with Sonar** (the plan lifecycle's Sonar step reports it configured; where it cannot scan, read the pull request over plain HTTP: `pullRequest=N`, never `branch=<feature>`): an open Sonar issue is fixed in a pushed commit, or dismissed with a stated reason; the quality gate is green before the pull request is reported ready.
- **No dependency or plugin is added.**
- **No more than 100 files per pull request** (the watch of the epic ledger; CodeRabbit refuses a larger one): cut the plan into pull requests by deliverable or by module before the first one is opened.
- **Tests that can fail.** A new test fails on the code it guards (a failing run or a mutation, reported by log id); a test of a refusal asserts the reason, not only the type (CLAUDE.md, R20).

## Claim Labels

- OBSERVED: ADR `core-store/079` lines 44 and 77 to 78 at `5490323a` say `revise` reads `merged_from[]` for the mark; lines 88 to 90 say the half "is not implemented".
- OBSERVED: `store-schemas/10-job-and-lesson-records.adoc` was changed by #516 by two bullets (+2/-2, about 650 characters more) and not reduced; the schema block and the kind tables are untouched.
- OBSERVED: `LessonStoreUntrustedCitationTest` tests the resolution arms as a pure function; no test drives `PresentUnreadable`, `StoreTampered`, `DeletedOutOfBand` or `Untracked` through `revise`.

## Expected Surface

Source, tests, specification and documentation (specific files, R19; the outline adds the files the re-read at the head names):

- OBSERVED (carried items and deliverables name them; `.plan`): `.plan/marshal.json`.
- OBSERVED (carried items and deliverables name them; `doc`): `doc/plans/README.adoc`, `doc/specification/store-schemas/10-job-and-lesson-records.adoc`.
- OBSERVED (carried items and deliverables name them; `pm-core`): `pm-core/doc/developer/core-store.adoc`, `pm-core/src/main/java/de/planmarshall/core/store/DerivedFactStore.java`, `pm-core/src/main/java/de/planmarshall/core/store/LessonCodec.java`, `pm-core/src/main/java/de/planmarshall/core/store/LessonFrontMatter.java`, `pm-core/src/main/java/de/planmarshall/core/store/LessonStore.java`, `pm-core/src/test/java/de/planmarshall/core/store/LessonCodecControlCharacterTest.java`, `pm-core/src/test/java/de/planmarshall/core/store/LessonRevisionTest.java`, `pm-core/src/test/java/de/planmarshall/core/store/LessonStoreUntrustedCitationTest.java`.
- OBSERVED (carried items and deliverables name them; `pm-runtime`): `pm-runtime/src/test/java/de/planmarshall/runtime/lock/DerivedFactStoreLockTest.java`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-156 (shipped, #516, `landings/PLAN-V2-156.md`: the plan this one succeeds), PLAN-V2-83.
- Overlaps with (class level): PLAN-V2-125 and 141 on the store test classes, PLAN-V2-158 on `LessonStore`-adjacent null contracts; the gate decides at emit.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-159-lesson-store-closeout.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Carried Open Items

Carried 2026-11-20 from PLAN-V2-156 (`landings/PLAN-V2-156.md`; the items are those of PLAN-V2-114's Carried Open Items, numbered as there). #516 adopted none and named no owner; this plan owns the items below, each read as a deliverable's detail, and records one outcome per item (the Standing Constraints).

27. **The merge knob of the plan lifecycle** (operator decision 2026-11-15, `handle-open` open item 2, answer 1): `.plan/marshal.json` `default:branch-cleanup.final_merge_without_asking` is `false` (`:127`) while CLAUDE.md § Git Workflow says the plan lifecycle and the runbook lane merge their own pull request without asking another permission. Set it to `true` (precedent: the steward PR #420). Read the line at the head before editing. Check before the edit whether the runbook lane reads the knob (the orchestrator verified only the phase-6 branch-cleanup step); if the lane does not, say so in the pull request body and leave the lane as is. Not a CLAUDE.md change. Origin: lesson 002 of PLAN-V2-138.

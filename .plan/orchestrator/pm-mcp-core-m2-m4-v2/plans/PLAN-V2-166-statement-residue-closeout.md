# PLAN-V2-166: The closeout of the statement residue: the lost requirement sentences of the lesson record specification, the cut-log label, ADR 009's tense, three test losses and the missing outcomes

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
origin: the three-layer analysis of the landing of PLAN-V2-163 (`landings/PLAN-V2-163.md`, 2026-11-22, #553 to #557 and #559, head `16264668`) and the operator's rule that leftovers go into a staged plan (no staged plan owns `pm-core` and documentation statements)
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-166-statement-residue-closeout.md` and is queued as one row file, `queue/PLAN-V2-166.json`,
> in the epic ledger (lane `runbook`: the operator runs it with the runbook lane). The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r6`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.


Rules that apply to this plan: R02, R05, R06, R07, R08, R10, R11, R13, R17, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R09: no confinement; R12: no message is added; R10 because `store-schemas/09` and `store-schemas/10` are read by the schema parity tests: their schema blocks, kind tables and anchors stay byte-stable; R13: the format version of a lesson stays 1.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-store.adoc` and `pm-core/doc/developer/core-build.adoc` (exist; the corrected statements); ADR directory: `pm-core/doc/adr/core-store/` (renumbered against `origin/main`, R11; no new ADR is expected); layout: workstream WS-11.
- R12 — none (no message).

**Read the skills first (R20).** `java-core`, `java-null-safety`, `javadoc`, `junit-core` and `cui-testing` of `/home/oliver/git/plan-marshall/marketplace/bundles/{pm-dev-java,pm-dev-java-cui}/skills/`; say in the first progress note which you read. R20 applies to every test line this plan touches.

## Objective

PLAN-V2-163 shipped its corrections in six pull requests and left a small residue that no staged plan owns (`landings/PLAN-V2-163.md`, layer 3 and layer 2). The principal one: the R05 reduction of `store-schemas/10-job-and-lesson-records.adoc` deleted requirement sentences instead of pointing to a place that states them, so four requirements of the lesson record are stated nowhere in the specification any more. The rest are a label nobody reads, an ADR context in the wrong tense, three small test losses, an unbounded loop, and the per-item outcomes the plan owed. None changes a stored datum.

## Deliverables

All file and line references come from the landing analysis and are re-read at the head of `main` before editing; drop what a merge since then settled and name the commit.

1. **The requirement sentences the R05 reduction deleted (carried item 82a).** In `doc/specification/store-schemas/10-job-and-lesson-records.adoc` restore, or relocate to the guide `core-store.adoc` or ADR core-store/079 with a link left in the bullet, each of: `completely_covered` also requires `covering_clause` and a concrete `covering_input`, whitespace-only counts as missing, and `into` is set exactly for `merged` and `superseded`; a cited lesson that does not exist is read as no evidence and marks nothing; two concurrent writers never allocate the same `lesson_id` (the Lock bullet); the `stalled_lessons` harvester wording of the stale-claim sentence. Schema blocks, kind tables and anchors stay byte-stable (R10). Read the bullets of Lock, Claim, Retirement, Maintenance and Derived-from-Untrusted at the head and diff them against `4d4ada41` to find every deleted sentence, not only these.

2. **The cut-log label and the compatibility shims (carried item 82b).** `TestCounts.Measured.tailOnly` (`TestCounts.java:57`, set at `MavenOutputParser.java:196-198`) is read by no code of this repository and `population()` still answers `MEASURED`, so the contradiction check item 11(b) of PLAN-V2-163 worried about is unchanged. Decide with the operator (R07; a result-type change is the operator's word): make the cut count `Unmeasured` or consumed by the contradiction check, or record that the assembly reads it (ask in the outline's question what `plan-marshall-mcp` does with it). Drop the 5-argument compatibility constructor and the 4-argument `from` overload if no caller needs them (pre-1.0, no shims).

3. **ADR 009's tense and the three test losses (carried items 82c and 82d).** `doc/adr/009-no-build-skip-for-a-specification-only-pull-request.adoc:15-24`: the Context is still present tense although the Status reads "Superseded by 015; premise retired by 016" (item 39(b)). Restore a test that fails on the code for each of: `ProfileStagesTest.endpointStagesWithoutClassesRefused` (a null `classes` now throws `NullPointerException`; assert the refusal and its message), the refusal of a submit that omits the reference and the asker-status and lease assertions that `ScenarioReplayRunnerTest.shouldAcceptTheSubmitThatNamesTheReference` lost (or name the test that covers them, in `ConsultationServiceTest`), and one case that the sweep of `ProcessTreeTracker` sends no signal to the group (the five `verify(signals)` pins were removed with the parameter; a structural test or a named reason).

4. **The unbounded loop, the long lines, the links check and the outcomes (carried items 82e and 82f).** `ConsultationService.java:568` `freeId()` loops forever if its supplier keeps returning taken ids: bound it and refuse with a named reason. The lines over 120 columns that #553 to #556 added (item 24(d)): count characters, not bytes, and break the ones that exceed. Run the links check from the main checkout (R17, `BROKEN LINKS 0`, `INLINE SCHEMA BLOCKS 0`) over the documents the six PRs changed. Record the outcomes PLAN-V2-163 never wrote: 14(d), 16 (does `ProductCorpusValidationTest` run or skip), 67(c) (`SchemaValidation.java:25-26` thread-safety claim, ADR 028 count), 20(a) (the counts of ADR core-id/001: 23 classes, 25 literals, the `{0,31}` sweep) and the six weakened-test removals PLAN-V2-163 called accepted (name them in the landing message).

**Done when:** Deliverables 1 to 4; every carried item below has its recorded outcome; no behaviour change beyond the corrections the items state. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of the changed modules (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module runs); the links check (R17) reports `BROKEN LINKS 0` and `INLINE SCHEMA BLOCKS 0` run from the main checkout.

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

- OBSERVED: every point below was read by the analysis leaf at `16264668`; the lost sentences of deliverable 1 were searched for in `doc/specification`, the guide and ADR 079 and found nowhere; `covering_clause` survives only in the schema JSON and ADR 079.
- LEAD (unverified): that the assembly does not read `tailOnly` (deliverable 2).

## Expected Surface

Source, tests, specification and documentation (specific files, R19; the outline adds the files the re-read at the head names):

- OBSERVED (`doc`): `doc/specification/store-schemas/10-job-and-lesson-records.adoc`, `doc/implementation-watch/store-schemas.adoc`, `doc/adr/009-no-build-skip-for-a-specification-only-pull-request.adoc`, `doc/Traceability.adoc` (read).
- OBSERVED (`pm-core`): `pm-core/doc/developer/core-store.adoc`, `pm-core/doc/developer/core-build.adoc`, `pm-core/doc/adr/core-store/079-*.adoc` (read), `pm-core/src/main/java/de/planmarshall/core/build/TestCounts.java`, `pm-core/src/main/java/de/planmarshall/core/build/MavenOutputParser.java`, `pm-core/src/main/java/de/planmarshall/core/build/BuildLogText.java`, `pm-core/src/main/java/de/planmarshall/core/profile/ProfileStages.java` (read), `pm-core/src/test/java/de/planmarshall/core/profile/ProfileStagesTest.java`, `pm-core/src/test/java/de/planmarshall/core/SchemaValidation.java` (read).
- OBSERVED (`pm-runtime`): `pm-runtime/src/main/java/de/planmarshall/runtime/job/ProcessTreeTracker.java` (read), `pm-runtime/src/test/java/de/planmarshall/runtime/job/ProcessTreeTrackerTest.java`.
- OBSERVED (`pm-workflow`): `pm-workflow/src/main/java/de/planmarshall/workflow/consult/ConsultationService.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/consult/ConsultationServiceTest.java`, `pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ScenarioReplayRunnerTest.java`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-163 (shipped, `landings/PLAN-V2-163.md`: the plan this one succeeds), PLAN-V2-83.
- Overlaps with (class level): PLAN-V2-161 (`pm-core` role classes, `core-store.adoc`), 162 and every staged plan by guide, ADR and Traceability file: the gate decides at emit; one shared file does not block.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-166-statement-residue-closeout.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Carried Open Items

82. **The residue of the landing of PLAN-V2-163, the deleted requirement sentences, the unread label, ADR 009's tense, three test losses and the missing outcomes** (`landings/PLAN-V2-163.md`, carried 2026-11-22; merged as #553 `cb992f27`, #554 `f87a5f98`, #555 `480a43d4`, #556 `1339e53f`, #557 `e8a585f3`, #559 `16264668`; read by the analysis leaf at `16264668`; no blocker). OBSERVED unless marked. Deliverables 1 to 4 above carry each point in detail: (a) the deleted sentences of `store-schemas/10`; (b) `tailOnly` and the compatibility shims; (c) ADR 009; (d) the three test losses; (e) `freeId()` and the long lines; (f) the links check and the outcomes. The reserved-keyword drift, the duplicate ADR number, the landing check and the `decisions` example are PLAN-V2-161 item 80; the `JobLog` first line is PLAN-V2-162 item 81. The API changes of #555 and #556 that the assembly must read (`TestCounts.Measured.tailOnly`, `ProfileStages.EndpointVerification` without `stages`, `HarnessUsage.fromEndpoint(@Nullable Long, @Nullable Long)`, `ProcessTreeTracker(long root)`, `PlanHistoryLine` admitting a hyphenated outcome) are a Watch of the epic, not a deliverable.

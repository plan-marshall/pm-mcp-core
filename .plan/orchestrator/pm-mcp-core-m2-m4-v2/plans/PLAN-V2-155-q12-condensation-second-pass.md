# PLAN-V2-155: The second pass of the Q12 condensation: the three model-work parts get fewer sentences and a byte target, and a table of what was cut

epic: pm-mcp-core-m2-m4-v2
workstream: WS-10
origin: the landing of PLAN-V2-34 (`landings/PLAN-V2-34.md` L1-2, L1-3, L2-1 to L2-4; PR #456 `a62d4db2`) and the operator's decision of 2026-11-17 (`handle-open` item 3, answered 1: "Q12 gets a second pass as its own plan, after its own carried item 35 (below), with 'condensed' defined as fewer sentences and a byte target per part, and a table of what was cut"; the word "stage it" of the same day)

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-155-q12-condensation-second-pass.md` and is queued as one row file, `queue/PLAN-V2-155.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r7`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R05 (the reduction rule this plan finishes: condense and point, no "Implemented by" or "Remaining:" paragraph), R06, R07, R08, R11, R17. (R01 does not apply. R02, R03, R04, R09 to R16, R18 to R20 do not apply: a documents-only plan with no code. **R18 and the Sonar clause: SonarCloud analyses the pull request; the open-finding read at the head still runs and a documents-only pull request is expected to show none.**)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: none changes unless a statement in `pm-workflow/doc/developer/workflow-consult.adoc` or `pm-runtime/doc/developer/runtime-worker.adoc` repeats a sentence this plan cuts (then it is brought to agree); ADR directory: none expected (a size criterion is not a design decision of a package); layout: workstream WS-10.

## Objective

Q12 of the issues pass (settled 2026-11-10, answer 3) asked for a complete, sentence-by-sentence condensation of the three `model-work` parts, with a floor that leaves no discretion to the lane. PLAN-V2-34 (#456) met the floor in its tables and not in the text: `02-task-protocol` grew from 29 911 to 30 956 bytes, `05-consultation` from 6 701 to 6 977, `04-worker-launch` fell from 42 055 to 41 622 (+888 bytes, +1.13 % together), and a row counted as condensed when the sentence only gained a guide link. This plan does the pass again with a definition the first one lacked: **"condensed" means fewer sentences, and each part ends below a byte target; every cut is listed in a table in the pull request body.** The false and lost statements of the first pass are carried item 35 (below), which runs first.

## Deliverables

1. **The definition and the targets (first; no spec is edited before it is written).** The plan states, in its first progress note and at the top of the pull request body, for each of the three parts: the sentence count and the byte size at `origin/main` at the start, the sentence count and byte size at the parent of `a62d4db2` (the state before the first pass), and its target. The floor of the target is the parent's size: `02` below 29 911 bytes, `05` below 6 701, `04` below 42 055; the lane sets its own target inside that bound per part and records it. A sentence that only gained a link, or that is reworded to the same length, is not condensed and is counted as unchanged.
   **Done when:** the three targets and the two baselines are in the pull request body before any file diff is described, the measurement commands are named (a sentence count the reviewer can repeat, `wc -c` for the bytes), and the baselines match what `git show` of the two commits prints.
   - Skills: pm-documents:ref-asciidoc, pm-documents:ref-documentation, pm-documents:ref-narrative-styles
   - Persona: documenter
2. **The three parts, sentence by sentence.** `doc/specification/model-work/02-task-protocol.adoc`, `doc/specification/model-work/05-consultation.adoc` and `doc/specification/model-work/04-worker-launch.adoc` (all of it, and in `04` the three sections the first pass never tabled: `worker-states`, `model-and-effort` (`=== Model & Effort`) and `job-prompt` (`=== Launch Prompt & Contract Envelope`)) are condensed under R05: a sentence the guide, the code or another part already states is replaced by a pointer or removed; an anchor and every `Implementation watch` link that other documents cite survive (search first); no statement of behaviour is lost (what moves is where it is said, not whether). The `worker-states` class tie the first pass left unsupported (`WorkerState.State` has no `awaiting_answer`, `pm-core/.../store/WorkerState.java`) is corrected or removed, whichever item 35 of PLAN-V2-114 did not already settle.
   **Done when:** each part is below its target of Deliverable 1 and has fewer sentences than at the start; a script the plan runs in `.plan/temp/` (not committed) compares the anchors (`[[...]]` and `=== ` headings) of each part before and after and lists none missing that another document links to; `model-and-effort` and `job-prompt` are condensed against the code that implements them (`LaunchComposer` and `PromptComposer` and their tests: read them, name each statement kept because a test pins it).
   - Skills: pm-documents:ref-asciidoc, pm-documents:ref-documentation, pm-documents:ref-narrative-styles
   - Persona: documenter
3. **The table of what was cut.** The pull request body carries one row per cut: part, section, the sentence (quoted, or its first words), the reason (restated by a named sibling, pointed to, duplicated, false), and where the statement lives now. A sentence kept unchanged is not a row; a sentence that only gained a pointer is a row of its own kind ("pointed"), counted apart from "removed". The table is complete over the three parts: the totals of the rows (removed, pointed, merged) plus the sentences kept equal the sentences at the start.
   **Done when:** the table's totals add up to the Deliverable 1 baselines, and the pull request body names how many sentences the pass removed and how many it only pointed.
   - Skills: pm-documents:ref-asciidoc, pm-documents:ref-documentation
   - Persona: documenter

**Done when:** the links check (R17) reports `BROKEN LINKS 0` or only links of the sibling's own deleted items; the three parts meet Deliverables 1 to 3; the specification, the guides and the code say the same about the statements kept (the pull request body lists the line of the code each behaviour sentence of `04`'s `Model & Effort` and `Launch Prompt` matches); the pull request body lists **Owed to the milestone exit**. Documents-only: `verify` is not required, the build of the pull request's own checks is. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready.

**Trace starting set:** Model Work 02, 04 and 05; the R05 reduction rule of `standing-rules.md`; `landings/PLAN-V2-34.md` (the first pass's tables and the measured sizes).

## Standing Constraints

- **Behaviour is not condensed away.** A cut is a restatement removed, never a requirement removed; a statement a test pins (`LaunchComposer`, `PromptComposer`, `TaskProtocol`, `ConsultationService` tests) stays where it is unless the plan says where it moved.
- **The first pass's tables are not trusted as a baseline:** the plan measures the parts itself (Deliverable 1) and does not take a count from `landings/PLAN-V2-34.md` without re-measuring it.
- **No new part, no renamed anchor, no new dependency.** Pre-1.0: no pointer left for an anchor removed (a removed anchor with a citing link is a finding, not a deletion).
- **The item-35 residue is carried into this plan:** the false and lost statements the first pass left are fixed first, as this plan's carried item 35 (below); the three parts are condensed and measured after it. The edit set of item 35 and of this plan is the same three files, so they are one plan.

## Claim Labels

- OBSERVED: the sizes at the first pass — `02-task-protocol` 29 911 to 30 956 bytes, `05-consultation` 6 701 to 6 977, `04-worker-launch` 42 055 to 41 622 (+888 bytes, +1.13 % together), measured 2026-11-17 against the parent of `a62d4db2` at `origin/main` `58f53566` (Decisions 2026-11-17, item 3); **re-measured at the start of this plan** (carried from the Decisions record, not re-measured at `63e5e80a`; Deliverable 1 re-measures)
- OBSERVED: `04-worker-launch.adoc` holds `=== Model & Effort` (line 119) and `=== Launch Prompt & Contract Envelope` (line 207) at `origin/main` `63e5e80a`; no `worker-states` heading was found by the heading scan (`^=\+ ` lines 12 to 266), so the "worker-states" of the first pass's tables names a different anchor or a text block — confirm or refute at the file (verify-at-outline)
- HYPOTHESIS: `WorkerState.State` has no `awaiting_answer` constant — confirm or refute at `pm-core/src/main/java/de/planmarshall/core/store/WorkerState.java` (verify-at-outline; the landing's reading, not re-read here)
- HYPOTHESIS: carried item 35 (below) is done first inside this plan and leaves the three parts in a state this plan can measure; its edit set overlaps this plan's three files — confirm at `plans/PLAN-V2-156-analysis-findings-fixes.md` item 35 and the queue row (verify at the emit)
- Verify-first clause: before editing, list the open pull requests for one that touches `doc/specification/model-work/` (its own carried item 35 (below) and PLAN-V2-150's `model-work/04:258` are the plans of this epic that do); re-read each cited line at `origin/main`; a refutation loops back to re-scope.

## Expected Surface

- OBSERVED: `doc/specification/model-work/02-task-protocol.adoc`, `doc/specification/model-work/04-worker-launch.adoc`, `doc/specification/model-work/05-consultation.adoc` — the three parts, condensed
- Read, edited only for a statement this plan cuts: the guides workflow-consult of pm-workflow and runtime-worker of pm-runtime, and the implementation-watch documents model-work and job-runtime.
- Read as the pinning sources, never edited: the classes WorkerState, TaskProtocol and ConsultationService.
- Not surface (R19, shared bookkeeping): the Traceability document and the plan index. No code, no build file, no package file of the plans directory.

- OBSERVED (cleanup 2026-11-19, understated surface; the files the Deliverables name and the surface did not): `pm-runtime/src/main/java/de/planmarshall/runtime/launch/LaunchComposer.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/launch/PromptComposer.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/launch/LaunchComposerTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/launch/PromptComposerTest.java`.

- OBSERVED (the carried item of PLAN-V2-156, added 2026-11-20): `doc/implementation-watch/model-work.adoc`, `doc/specification/model-work.adoc`, `pm-runtime/doc/developer/runtime-launch.adoc`, `pm-runtime/src/main/java/de/planmarshall/runtime/worker/StartFailureGuard.java`, `pm-workflow/doc/developer/workflow-consult.adoc`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-34 (shipped, #456), PLAN-V2-83. Item 35 (below) is done first inside this plan; nothing outside it blocks the emit.
- Note 2026-11-20: PLAN-V2-114 and PLAN-V2-156 shipped (#512, #516) without item 35; it moved into this plan (the carried item below).
- **Overlaps with live plans (read 2026-11-17, re-read at the emit by `corpus cross-check`):** PLAN-V2-156 (shipped, #516, no longer live; formerly `model-work/02`, `04`, `05`, `job-runtime.adoc`, `workflow-consult.adoc`: the same spec files, 3 or more; sequenced after it), PLAN-V2-150 (`model-work/04` line 258: one file, the same document part, and `doc/implementation-watch/model-work.adoc`), PLAN-V2-149 (documents of `job-runtime`: `doc/implementation-watch/job-runtime.adoc`). The gate decides; a multi-file overlap with a plan in flight is the operator's.
- Adjacent to: PLAN-V2-34 (shipped; its landing is the baseline), PLAN-V2-154 (its documents are the guides of `runtime-worker`, not these parts).

## Re-ground record (cleanup 2026-11-19 at `e2ae8627`, BINDING over any contradicting older text of this spec)

Written by `pm-mcp-core-m2-m4-v2/cleanup` from the verdicts of a read-only leaf over this spec (its statements are not re-read here; the executing plan re-reads each at its outline). Since the first pass (`d506e818`) #484, #490, #494, #495, #496, #503, #504, #506, #511 and #512 merged. Where this section and an older text of this spec differ, this section is the later reading.

- The Objective, the header and line 28 say "carried item 35 (below), which runs first": PLAN-V2-114 shipped (#512) without it; it is now this plan's carried item 35 (2026-11-20).
- Deliverable 2 names `worker-states` in `04`: the anchor `[#worker-states]` is `== Worker States` in `02-task-protocol.adoc:128-129` (cited by `05:29`, `06:229`, `doc/plans/question-continuation.adoc:34`).
- Sizes at HEAD: 02 = 30 956, 05 = 7 001, 04 = 42 451 bytes (04 +829 and 05 +24 since `a62d4db2`); targets: 02 below 29 911 (cut at least 1 046 B), 05 below 6 701 (301 B), 04 below 42 055 (397 B): reachable, but item 35 (d) and (f) restore text into 04 and 02: measure after item 35. `WorkerState.java:169-185` has no `AWAITING_ANSWER`; the spec defines it for the unbuilt question-continuation package: "corrected or removed" would delete a state that package, 05 and 06 cite, so keep it and mark it unbuilt.
- Understated: `LaunchComposer`, `PromptComposer` and their tests (read-only), `landings/PLAN-V2-34.md`, `doc/Traceability.adoc` (the class tie `:2987`, `:3066`).

## Carried item from PLAN-V2-156 (2026-11-20)

Carried 2026-11-20 from PLAN-V2-156 (`landings/PLAN-V2-156.md`: #516 adopted none of the 57 carried items; this one belongs to this plan by the operator's decision and by its surface). It is a deliverable's detail, not an extra deliverable; the outline records its outcome with a reason. Numbered as in PLAN-V2-114's Carried Open Items.

35. **The residue of the landing of PLAN-V2-34, statements the reduction made false or lost, an unenforced refusal presented as built, and the two Q12 gaps that need no decision** (`landings/PLAN-V2-34.md` L2-4 to L2-7, L3-1 to L3-3, carried 2026-11-16; read at the head before editing, the lines are those of `a62d4db2`): (a) `model-work/05-consultation.adoc:55` and `02-task-protocol.adoc:67` present the `consultation_ids` refusal and the schema step as implemented, but `ConsultationService.validateSubmit` (`:376`) has no caller in any `src/main` of this repository, `TaskProtocol.submit` (`:386-403`) checks status, kind and skills only, and `TaskRefusal.INVALID_LINK_ARGS` has no producer; the local assembly checkout (`3b93248`, 2026-10-27, may lag origin) has no caller either: at the outline read `plan-marshall-mcp` at its `origin/main` for the call; if it is absent there, either wire the check into `TaskProtocol.submit` with a test that fails without it (the specification's refusal), or state the two sections as not built — the outline decides on what the assembly shows and records which; (b) `pm-workflow/doc/developer/workflow-consult.adoc:99-100` says "The server calls it in its submit path before it accepts the submission", false in this repository until (a) is settled: correct it to what (a) leaves; (c) `pm-runtime/doc/developer/runtime-launch.adoc:250-258` (Owed) still says the launch sections "carry an `Implemented by` pointer" (ADR 027 and the merged specs deny it) and lists the start-failure guard as owed though `StartFailureGuard.java` exists: correct both; (d) `model-work/04-worker-launch.adoc` job-token step lost "the relay must be the release of the daemon it connects to" and "the relay sends both on every request", which no class of this repository covers (ADR 027: a sentence no class and no test covers stays): restore them unless the sibling `runtime-lifecycle/01-startup-recovery-drain.adoc:75` and `08-security.adoc:72` own them, and say which; (e) `implementation-watch/job-runtime.adoc` `PM-WATCH-JOB-5` was relabelled, not rewritten to its remainder: its guard still states the engine behaviours `WaitSelection`, `ModelWait` and `ModelWaitTest` cover (monotonic deadlines, `pm_wait` answers); reduce it to the `provider_rate_limit` clause and the wait population; (f) the three `pm_wait` answer bullets of `02-task-protocol.adoc:32` folded into one sentence with three nested parentheticals: restore the bullets or split the sentence; (g) the PR body's table for `04` has no row at the merge: the table is not recoverable from git, so a re-grounding that tests the `keep` reasons of `04` starts from the file — record the 04 sentence table in the pull request body of this item's plan. The documentation sibling's `implementation-watch` index counts 61 items for `model-work.adoc` against 60 and its `GAPS WATCH PM-WATCH-DISP` is said to list 55 and 56: read it at `origin/main` of `plan-marshall-documentation` and correct there only if still true (a documentation pull request, R-docs). **The floor of Q12 itself** (`worker-states` and the sections of `04` that `LaunchComposer`/`PromptComposer` and their tests cover, the "condensed" rows that are unchanged, the growth of `02` and `05` by 888 bytes) is not here: it is the operator's question recorded as an Open Defect of the epic.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-155-q12-condensation-second-pass.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

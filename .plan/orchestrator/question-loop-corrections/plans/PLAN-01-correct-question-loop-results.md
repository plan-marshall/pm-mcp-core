# PLAN-01: Correct the published question-loop results

epic: question-loop-corrections
workstream: WS-01

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-01-correct-question-loop-results.md` and is queued as one row file, `queue/PLAN-01.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.

## Objective

The question-loop smoke run (`plan-marshall-mcp` package `question-loop-verification`) published its results in
`plan-marshall-documentation` #124 (`58294a8`: `doc/specification/evaluation.adoc`,
`doc/developer/question-loop-experiment.adoc`) and in this repository #197 (`df407315`: four specification parts). A
review found statements stronger than the runs support, measurement figures inside normative specification text, a
developer document that describes code not on `main`, and missing limits, among them that the raw evidence was deleted.
This plan corrects those documents so that every statement matches its evidence, the specifications state the target
only, and the decisions the results raise are open questions for the operator. It changes documents only, in two pull
requests: this repository first, then `plan-marshall-documentation`.

## Deliverables

1. **Overstated result statements corrected**, in the evaluation reference, the developer document and this
   repository's specification alike, each to what the runs show:
   - Q2: "No host's question tool was called in any run" and "did not call `ask_question` in its 36 runs" are replaced by
     what was observable: Claude Code and OpenCode ran with the tool withheld, and Antigravity's output carries no tool
     call, so a call of its question tool was not observable (evaluation reference § Results Q2 and § Limits; Client
     Profiles 02 § Placeholders).
   - Q3 and Q7: the fresh askers that did not end but waited for the answer (OpenCode 9 of 12, Antigravity 10 of 12;
     Q7: 4 of 6 ended by the driver) are reported against the criterion as written ("a fresh worker ends with its
     job-result"); the Substantiates cell no longer cites § Continuation for a sentence that section does not contain.
     Whether a fresh asker that waits is acceptable is an open question (Deliverable 5), not a pass.
   - The held-call cut: the same worker waited again in 12, 12 and 9 of 12 cuts; every "lost nothing on any of the
     three", "on every host the worker survives the cut" is corrected to those figures, the 3 Antigravity cuts named as
     unexplained, and Model Work 06 § Answer acknowledgement no longer states the cut both as the reason for the check
     and as harmless.
   - R3: the detection times equal the driver's supervision values, so "within the deadline, never earlier" holds by
     construction; the evaluation reference says so and the specification no longer states it as a measured property.
   - Q5: the bound `3` is the specified value, not a measured one; that a run reached the bound was not read from the
     event log.
   - Q1: the two warm Claude Code runs that timed out (whose continuation no generation took) are named in the Q1 row.
2. **Measurement out of normative text.** The run counts, medians, "the measurement ran three runs …" paragraphs and the
   "proposal for the next profile verification" are removed from Client Profiles 01 § Resumability, Client Profiles 02,
   Model Work 01 and Model Work 06 and kept (or added) as rows or limits of the evaluation reference, which the
   specification links. The specification keeps only the target: the shipped values, the rules, and a link to the
   evaluation row for the candidates. The proposal becomes an open question (Deliverable 5).
3. **Limits completed** in the evaluation reference: the raw evidence of the smoke run (`runs.jsonl`,
   `invocations.jsonl`, the stub logs, the per-invocation archives and `answer-audit.json`, kept under
   `target/question-loop/` of the plan's worktree) was deleted when the worktree was removed and no copy exists, so no
   figure can be re-checked against its source; the Sonnet repeat the package's method requires for a failed item
   (R1 240 s, Q3 warm, Q5 fresh) was not run; Codex was not measured; the OpenCode versions are stated the same way
   everywhere (2.1.2 to 2.1.4).
4. **Developer document.** `doc/developer/question-loop-experiment.adoc` describes the implemented system only: it says
   at its top that the code it describes lives on the branch `verification/question-loop-experiment` (`488ce47`) of
   `plan-marshall-mcp` and not on `main`, until the follow-up package extracts it; its history narration ("In a first
   smoke pass …", "Before this rule …", "the first detector …") is removed; its Proposals section moves to
   `doc/discussions/open-questions.adoc` (Deliverable 5). No new document is created.
5. **Open questions** added to `doc/discussions/open-questions.adoc` of `plan-marshall-documentation`, each with what it
   blocks, the options and a recommendation:
   - Is a resume of a missing session that starts a new session and exits `0` a refused resume (the rule #197 wrote into
     Client Profiles 01 § Profile Verification and Model Work 06, under which OpenCode and Antigravity can never resume),
     or should the stage recognise that outcome? Blocks the shipped `headless.resumability` values.
   - Is a fresh asker that waits for its answer instead of ending acceptable (Q3, Q7), and if so, which specification
     states it (§ Continuation)? Blocks the Q3 and Q7 verdicts of the re-measurement.
   - The developer document's proposals (Deliverable 4), each as its own question or folded into the two above.

**Done when**: every statement listed in Deliverable 1 reads as the runs show in all three documents; the four
specification parts carry no run count, median or measurement narration and link the evaluation rows instead; the
evaluation reference names the deleted evidence, the missing Sonnet repeat and Codex in its limits; the developer
document states where its code lives and carries no history or proposals; the open questions exist; `trace.py` reports
`BROKEN LINKS 0` and no undefined identifier on the final commit of the `plan-marshall-documentation` pull request,
against this repository's `main` after its pull request merged.

## Standing Constraints

- Normative documents state the target only: no dates, decision ids, run counts or history narration in a
  specification or requirement; figures belong in the evaluation reference (status `REFERENCE`), which records evidence.
- Change no requirement and no shipped value: the corrections narrow statements to their evidence; a value the operator
  has to decide is an open question, never a silent change.
- Read the evaluation rows against the run records only where they still exist (the run records and the worker outputs
  were reported to survive the reboot of 2026-10-24, the stub logs did not, and the plan's worktree is gone); where a
  figure cannot be re-checked, the text says so instead of restating it.
- Two pull requests: `pm-mcp-core` first (label `skip-bot-review`, document-only), then `plan-marshall-documentation`
  (squash-merge, link check run locally on its final commit). Commits end with
  `Co-Authored-By: plan-marshall <noreply@cuioss.de>`; pull request bodies carry no tool footer. Merge only on the
  operator's word.

## Claim Labels

- OBSERVED: the Q2 statements — read at `plan-marshall-documentation` `doc/specification/evaluation.adoc` (at
  `58294a8`) line 165 ("No host's question tool was called in any run") against line 642 (Antigravity's output "carries
  no tool call, so … a call of its own question tool (Q2) … [is] not visible in it").
- OBSERVED: the Q3 fresh-asker figures — read at `evaluation.adoc` line 170 (Claude Code ended 8 of 12, OpenCode 3 of
  12, Antigravity 2 of 12; the others waited "until the answer came at their silence limit, then ended (pass)").
- OBSERVED: Model Work 06 § Answer acknowledgement opens with a host that ended its held call as the reason for the check
  — read at `doc/specification/model-work/06-envelopes-questions-deadlines.adoc` line 431 at `df407315`.
- HYPOTHESIS: the call-cut figures "12, 12, and 9 of 12" and the "lost nothing on any of the three" wording — confirm at
  `evaluation.adoc` lines 205–206 and 590, `doc/developer/question-loop-experiment.adoc` lines 124 and 166, and Model Work
  06 line 431 (verify-at-outline).
- HYPOTHESIS: R3 detection times equal the driver's supervision values (15 s, 270 s, 180 s) — confirm at
  `evaluation.adoc` line 205 and `question-loop-experiment.adoc` line 127 (verify-at-outline).
- HYPOTHESIS: the measurement narration in normative text sits at Client Profiles 01 lines 691–712, Model Work 06 line
  405, Model Work 01 line 239 and Client Profiles 02 lines 90 and 96 at `df407315` (verify-at-outline).
- HYPOTHESIS: the Q5 reach of the bound is not read from the event log — confirm at `evaluation.adoc` line 646
  (verify-at-outline).
- OBSERVED: the raw evidence is gone — `plan-marshall-mcp/.plan/local/worktrees/question-loop-verification` does not
  exist and a disk search found no `runs.jsonl` or `invocations.jsonl` (2026-10-27); the experiment code is kept on the
  remote branch `verification/question-loop-experiment` at `488ce47`, which also places the evidence under
  `target/question-loop/` (`QuestionEvidence.java`).
- Verify-first clause: before scoping, re-read every cited line at the HEAD of both repositories (a later pull request
  may have moved them) and list any other sentence of the three documents that states a smoke-scale figure as settled;
  a refuted claim loops back to re-scope.

## Expected Surface

- OBSERVED: `doc/specification/client-profiles/01-profile-schema.adoc` — § Resumability and § Profile Verification
- OBSERVED: `doc/specification/client-profiles/02-placeholders.adoc` — the question-tool rows
- OBSERVED: `doc/specification/model-work/01-roles-and-configuration.adoc` — the `questions` paragraph
- OBSERVED: `doc/specification/model-work/06-envelopes-questions-deadlines.adoc` — § Question Bound, § Continuation, § Answer acknowledgement
- OBSERVED: `../plan-marshall-documentation/doc/specification/evaluation.adoc` — in the sibling repository (not resolvable from this checkout)
- OBSERVED: `../plan-marshall-documentation/doc/developer/question-loop-experiment.adoc` — in the sibling repository
- OBSERVED: `../plan-marshall-documentation/doc/discussions/open-questions.adoc` — in the sibling repository

## Dependencies and Sequencing

- Depends on: none.
- Overlaps with: none within this epic (PLAN-02 touches `doc/plans/` only). PLAN-73 of `pm-mcp-core-m2-m4` declares
  Client Profiles 01, Model Work 01 and Model Work 06 as surface; it is staged, not in flight.
- Adjacent to: `doc/plans/question-continuation.adoc` (PLAN-02's), the evaluation reference's other rows (Part A),
  which stay untouched.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/question-loop-corrections/plans/PLAN-01-correct-question-loop-results.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

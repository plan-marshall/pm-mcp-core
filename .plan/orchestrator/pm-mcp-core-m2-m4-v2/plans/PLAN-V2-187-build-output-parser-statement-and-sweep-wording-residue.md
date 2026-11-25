# PLAN-V2-187: The residue of the sweep's sixth closeout: a parenthetical that still says too much, a decision that stands away from its catch, an ADR addition without its stamp and an orphan line

epic: pm-mcp-core-m2-m4-v2
workstream: WS-03
origin: the three-layer analysis of the landing of PLAN-V2-186 (`landings/PLAN-V2-186.md`, #609, merge `3aea14c2`), 2026-11-25, and the operator's rule that leftovers go into a staged plan (none of the staged plans names the `BuildOutputParser` Javadoc, `PendingEntryRecordsSweep`, ADR `runtime-start/016` or `runtime-start.adoc`; PLAN-V2-126 names `BuildOutputParser` for its tests only).
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-187-build-output-parser-statement-and-sweep-wording-residue.md` and is queued as one row file, `queue/PLAN-V2-187.json`,
> in the epic ledger (lane `runbook`). The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`). They bind this plan exactly as if they were written below. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R05, R06, R08, R10, R11, R13, R14, R18, R19, R20. (R12: no message is added; R13: no stored format changes; R14 because the plan touches the pending-entry sweep's statements; R17 is lifted.)

Plan parameters: R06 — developer documentation `pm-runtime/doc/developer/runtime-start.adoc` (a corrected statement only); ADR directory `pm-runtime/doc/adr/runtime-start/` (corrections edit the existing ADR `016-the-sweep-removes-pending-entry-residue-by-evidence.adoc`; no new ADR); layout WS-03. R12 — none.

**Read the skills first (R20).** `java-core`, `javadoc` of `/home/oliver/git/plan-marshall/marketplace/bundles/pm-dev-java/skills/`, and `ref-asciidoc` of `pm-documents`; say in the first progress note which you read.

## Objective

PLAN-V2-186 closed the sweep's fifth residue; its analysis found no blocker and no major, and what is left is wording: one sentence of the `BuildOutputParser` Javadoc that says no rule is shared between the parsers while all five pass one representability rule, a decision about the `run` catch that stands on a private method's Javadoc and not at the catch, an ADR addition without the amended stamp its three predecessors carry, and an orphan short line in the guide. This plan changes statements only, no behaviour, and is meant to be the last of this stream.

## Deliverables

All references are read at `3aea14c2` and re-read at the head of `main` before editing; drop what a merge since then settled and name the commit.

1. **The Javadoc statements say what the code does (carried item 107 a, b; statements only).** (a) `BuildOutputParser.java:113-116` says "Each parser owns its rule and states it beside its count scan; no rule is shared between the parsers": every parser's count passes `TestCounts.from` (`pm-core/.../build/TestCounts.java:138-147`: negative, beyond the `int` range or a total beyond it is `Unmeasured`; called at `GradleOutputParser.java:156`, `JestOutputParser.java:227`, `MavenOutputParser.java:635`, `PyprojectOutputParser.java:695`, `TapOutputParser.java:468`), and only Jest's parser has a Javadoc statement of its own rule (Gradle `:143-145`, pyproject `:678-695`, TAP `:462-471` have none, Maven an inline comment on `parse`). Say what is true: one shared representability rule in `TestCounts.from`, plus each parser's own additional rule where it has one, each named against its source line; drop "states it beside its count scan" or make it true. Keep it consistent with `TestCounts.java:100-101` ("measured or unmeasured by its own rules"). (b) `PendingEntryRecordsSweep.described`'s `@return` says "the type label": the code returns `<SimpleName> with no message`; say that, and that a blank message is returned as it is. (c) Put the decision "the `run` catch needs no guard" where a reader of the catch (`:130-132`) finds it: a one-line comment at the catch pointing to `described`'s Javadoc, or move the sentence; no test is added.
   - Skills: pm-dev-java:javadoc, pm-dev-java:java-core
   - Persona: documenter
2. **The ADR and the guide (carried item 107 c, d; statements only).** (a) ADR `runtime-start/016:61-66` carries the new scoping without an "amended 2026-11-25 by `PLAN-V2-186`" stamp, as `:51`, `:55`, `:56` do for their plans: add it in the same form. (b) `runtime-start.adoc:135-136` has an orphan short line "A fault of" before "another type leaves `run` uncaught": re-flow the paragraph. (c) The literal `IllegalStateException with no message` in `PendingEntryRecordsSweepTest` is the report contract, but its display name does not say so (CLAUDE.md: a literal stays only where the test says it is the contract): add the words to the display name or a one-line comment, if the test method that uses it names no contract yet; if the outline finds it already says so, drop and say why.
   - Skills: pm-documents:ref-asciidoc, pm-dev-java:javadoc, pm-dev-java:junit-core
   - Persona: documenter

**Done when:** the statements of deliverables 1 and 2 say what the code does and agree with each other and with `TestCounts.java`; no behaviour changed (the diff of main code is comment lines only); `verify -Ppre-commit` (its rewrites reviewed, those of any file that is not this plan's not shipped) and `verify -pl pm-runtime -am` are green, targeted `-Dtest` where a full verify exceeds the host's budget; the open Sonar issues of the pull request are fixed or dismissed with a reason.

## Standing Constraints

- **Read every cited line at the head first** and drop what a merge settled.
- **Work to the Done-when.** Do not stop to ask the operator which part to continue with.
- **Every carried item gets an outcome** in the landing message and the pull request: adopted into a deliverable, split off to a NAMED existing plan, or dropped, each with its reason.
- **No behaviour change.** No test is added or changed beyond the display name or comment of deliverable 2(c). `JobLog.java` and `runtime-job.adoc` are not here (owed to PLAN-V2-162, #567).
- **Build state at the time of staging:** the new pm-api SNAPSHOT (`-228`) declares `headless.usage.model`, which `pm-core`'s closed `usage` validator on `main` refuses until PLAN-V2-135 (#594) merges; seven `pm-core` profile tests are red on `main` against that SNAPSHOT. If PLAN-V2-135 has not merged when this plan runs, the gate is red for that reason alone: report it, do not pin the old SNAPSHOT.
- **No dependency or plugin is added.** No more than 100 files per pull request.
- **Do not repeat the lane's claims of PLAN-V2-186 as facts:** counts and run ids are its statement, not re-run.

## Claim Labels

All lines read at `origin/main` `3aea14c2` (the merge of #609) by the analysis leaf; the orchestrator re-read `BuildOutputParser.java:106-120`, `TestCounts.java:98-102,136-148`, `PendingEntryRecordsSweep.java:128-134,336-354`, ADR `016:55-67`, `runtime-start.adoc:126-138` and the five callers of `TestCounts.from`.

- OBSERVED: the Javadoc says "no rule is shared between the parsers" while the five parsers call `TestCounts.from` — read at the lines above
- OBSERVED: the decision about the `run` catch stands at `PendingEntryRecordsSweep.java:342-345`, a private method's Javadoc; `@return` says "the type label" (`:347`) — read
- OBSERVED: the ADR's new text at `:61-66` has no amended stamp; the guide has an orphan line at `:135-136` — read
- HYPOTHESIS: the display name of the test that uses the literal `IllegalStateException with no message` does not name the contract — confirm/refute at the test class (verify-at-outline)

## Expected Surface

- OBSERVED (`pm-runtime`): `pm-runtime/src/main/java/de/planmarshall/runtime/job/BuildOutputParser.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/start/PendingEntryRecordsSweep.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/start/PendingEntryRecordsSweepTest.java`, `pm-runtime/doc/developer/runtime-start.adoc`, `pm-runtime/doc/adr/runtime-start/016-the-sweep-removes-pending-entry-residue-by-evidence.adoc`.
- Read only: `pm-core/src/main/java/de/planmarshall/core/build/TestCounts.java` and the five parsers (a statement may cite them; none is edited).

## Dependencies and Sequencing

- Depends on: PLAN-V2-186 (shipped, #609).
- Overlaps with (class level): PLAN-V2-126 (test conformance: names `BuildOutputParser` for tests; runs after this plan or rebases over it), PLAN-V2-162 (`runtime-job.adoc`, `JobLog`; neither is touched here) and the staged plans by guide, ADR and Traceability file: the gate decides at emit.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-187-build-output-parser-statement-and-sweep-wording-residue.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates and edits NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports its outcome through its PR and its inbox message. See `persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Carried Open Items

107. **The residue of the landing of PLAN-V2-186** (`landings/PLAN-V2-186.md`, carried 2026-11-25; merged as #609 `3aea14c2`; read by the analysis leaf; no blocker, no major): deliverables 1 and 2 carry each point: (a) the parenthetical's "no rule is shared" and "beside its count scan" (186 L2-1, L3-1), (b) the `@return` wording (186 L2-2), (c) the decision away from its catch (186 L3-2), (d) the ADR stamp and the guide's orphan line (186 L3-3, L3-4), (e) the literal's display name (186 L3-5); recorded, not fixed: the null-message corner of the codec path (a `NullPointerException` through `StoreRefusal.unreadable`'s `requireNonNull`; no producer does it), the anonymous-class `getSimpleName()` corner (184 L2-4), the lane's counts (`stated_not_verified`), the `unknown` figures (an open record, owner E27), the `runtime-job.adoc:291` example owed to PLAN-V2-162.

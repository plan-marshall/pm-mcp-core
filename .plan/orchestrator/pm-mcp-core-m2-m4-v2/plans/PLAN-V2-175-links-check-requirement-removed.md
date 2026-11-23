# PLAN-V2-175: The links-check requirement leaves the repository's rules: `BROKEN LINKS 0` and `INLINE SCHEMA BLOCKS 0` are no longer asked of any pull request

epic: pm-mcp-core-m2-m4-v2
workstream: WS-10
origin: the operator's answer of 2026-11-23 in `handle-open` (item Q10, the `BROKEN LINKS 0` clause): "remove the requirement from CLAUDE.md". The lanes of PLAN-V2-164 to 171 reported 97, 3252 and 3253 broken links by tool root (local 6, cross-repository 3247 at 170) against a Done-when of `0`, because the cross-repository links need sibling checkouts that lag their `origin`; every lane had to call the clause unmeetable.
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-175-links-check-requirement-removed.md` and is queued as one row file, `queue/PLAN-V2-175.json`,
> in the epic ledger (lane `runbook`: the operator runs it with the runbook lane). The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R05, R08, R10, R13, R18, R19. (R01: no work package covers this plan; R03, R04: no HTTP; R06: no package is changed; R07: nothing is ask-at-refine; R09: no confinement; R11: no ADR; R12: no message is added; R17 is the rule this plan retires from the repository's rules, and its text in the standing rules was amended by the orchestrator on 2026-11-23 (the check is optional); R20: no Java is touched.)

Plan parameters: none.

## Objective

`CLAUDE.md` asks that every pull request runs the traceability checker of the documentation repository and reports `BROKEN LINKS 0` and `INLINE SCHEMA BLOCKS 0`. On this host the check cannot report `0`: the numbers differ by tool root (97, 3252, 3253) and the lanes of 164 to 171 each reported them as pre-existing and unmeetable. The operator's decision of 2026-11-23: the requirement leaves `CLAUDE.md`. This plan removes it from the two places of this repository that state it.

## Deliverables

All file and line references are those of `origin/main` `8c7eac6f` and are re-read at the head of `main` before editing.

1. **`CLAUDE.md`, section "Project".** Remove the requirement: the clause "check them with `python3 ../plan-marshall-documentation/.claude/skills/doc-review/scripts/trace.py ../plan-marshall-documentation/doc .` (`BROKEN LINKS 0`, `INLINE SCHEMA BLOCKS 0`); no workflow runs it, so it runs locally before every merge." Keep the sentence it ends: "Links here are relative, also into the documentation (sibling paths)". Re-read the paragraph so it still reads as one (the sentence before it ends at "the skills of the same names here only point to them."). Nothing else in `CLAUDE.md` changes.
   - Skills: pm-documents:ref-asciidoc, pm-documents:ref-documentation
   - Persona: documenter
2. **`.opencode/rules/plan-marshall-standing-rules.md`, lines 36-37 at `origin/main`.** The same sentence in the copy of the repository rules that the lanes read: remove it the same way, and keep that file otherwise unchanged. Then run `git grep -n -i "BROKEN LINKS\|INLINE SCHEMA BLOCKS" -- CLAUDE.md .opencode .claude` and name every remaining hit in the pull request: the traceability tool itself (`.claude/skills/doc-review/`) stays; a hit that states the requirement is removed, a hit that only describes the tool stays.
   - Skills: pm-documents:ref-asciidoc, pm-documents:ref-documentation
   - Persona: documenter

**Done when:** `git grep -n -i "BROKEN LINKS 0" -- CLAUDE.md .opencode` returns nothing; the paragraph of `CLAUDE.md` it came from still states that links are relative and may point into the documentation sibling paths; the pull request body lists the remaining hits of the grep of deliverable 2 with a decision for each; `verify -Ppre-commit` is not needed (no Java, no resource is touched) and the plan's own check is the diff: two files, a few lines.

## Standing Constraints

- **Read every cited line at the head first** and drop what a merge since then settled (name the commit).
- **Work to the Done-when.** Do not stop to ask the operator which part to continue with.
- **No behaviour change beyond the removal.** The traceability tool, its skill and the sentence that links here are relative stay. Re-adding a different link requirement is the operator's decision, not this plan's.
- **The documentation repository is not edited.** `plan-marshall-documentation` (`doc/developer/project-setup.adoc` and the shared specifications) may restate the requirement: name each place you find in the landing message as owed outside this repository, with the file and line; do not open a pull request there.
- **No dependency or plugin is added.** No more than 100 files per pull request (two files here).
- **Documents only.** No Java is touched; the Sonar and coverage steps have nothing to scan.

## Claim Labels

All lines read at `origin/main` `8c7eac6f` by the orchestrator (`CLAUDE.md` lines 30-32 and `.opencode/rules/plan-marshall-standing-rules.md` lines 36-37 hold the sentence); the outline re-reads each before it acts.

- OBSERVED: `CLAUDE.md` (section "Project") and `.opencode/rules/plan-marshall-standing-rules.md` (lines 36-37) each state the requirement in the same sentence; `git grep` over `origin/main` finds no other statement of it outside the archived ledger and the traceability tool's own documentation.
- LEAD (unverified): `plan-marshall-documentation` states the requirement as well (`doc/developer/project-setup.adoc` or a shared specification); it is another repository and was not read.

## Expected Surface

Specific files (R19):

- OBSERVED (`pm-mcp-core`): `CLAUDE.md`, `.opencode/rules/plan-marshall-standing-rules.md`.

## Dependencies and Sequencing

- Depends on: nothing.
- Order: any time; PLAN-V2-172, 173 and 174 are running and read `CLAUDE.md` from `main`: they keep the requirement they started with and report the clause as before (their own constraint); a pull request of this plan that merges while they run changes the file they read, which affects no code. No file is shared with a running plan (162: `JobLog`, runtime and worker files; 172: the commit primitives; 173: the cut log; 174: the pending-entry store).
- Overlaps with: none of the surfaces of the staged plans (`CLAUDE.md` is named by PLAN-V2-141 as a file its ADR may edit (Carried Open Item 1, the `Generator Artifacts` section) and by nothing else); the gate decides at emit.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-175-links-check-requirement-removed.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Carried Open Items

93. **The links-check requirement of `CLAUDE.md`** (the operator's word of 2026-11-23, `handle-open` Q10: "remove the requirement from CLAUDE.md"; the counts of the lanes of 164 to 171: 97, 3252 and 3253 by tool root). Deliverables 1 and 2 carry it; the places in the documentation repository are owed outside.

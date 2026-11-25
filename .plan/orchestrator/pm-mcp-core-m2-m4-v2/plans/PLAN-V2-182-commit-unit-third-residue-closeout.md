# PLAN-V2-182: The residue of the commit unit's second closeout: a design record that contradicts itself, a host-path pattern whose residual leaks are neither closed nor pinned, and statements that outlive the code

epic: pm-mcp-core-m2-m4-v2
workstream: WS-01
origin: the three-layer analysis of the landing of PLAN-V2-179 (`landings/PLAN-V2-179.md`, #598, merge `ac16557d`), 2026-11-24, and the operator's rule that leftovers go into a staged plan (no staged plan owns the sources named here: PLAN-V2-141 owns the ADR index and the ADR 007 wording, PLAN-V2-125 and 126 own only test conformance).
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-182-commit-unit-third-residue-closeout.md` and is queued as one row file, `queue/PLAN-V2-182.json`,
> in the epic ledger (lane `runbook`: the operator runs it with the runbook lane). The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R05, R06, R08, R10, R11, R13, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R07: nothing is ask-at-refine; R09: no confinement; R12: no message is added; R10 because deliverable 3 edits the `description` of `status.schema.json`: the schema blocks, anchors and every other member stay byte-stable and the parity tests stay green; R13: no stored format changes; R17 is lifted, the links check is not a requirement, operator 2026-11-23.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-store.adoc` and `pm-providers/pm-provider-git/doc/developer/provider-git-primitive.adoc` (exist; corrected statements and the ADR list); ADR directories: `pm-core/doc/adr/core-store/` and `pm-providers/pm-provider-git/doc/adr/provider-git-primitive/` (corrections edit the existing ADR; a new ADR only for a decision the outline cannot defer, the next free number at `origin/main`, R11); layout: workstream WS-01.
- R12 — none (no message).

**Read the skills first (R20).** `java-core`, `java-lombok`, `java-null-safety`, `javadoc`, `junit-core` and `cui-testing` of `/home/oliver/git/plan-marshall/marketplace/bundles/{pm-dev-java,pm-dev-java-cui}/skills/`; say in the first progress note which you read. R20 applies to every test line this plan touches.

## Objective

PLAN-V2-179 closed the commit unit's second residue. Its analysis found no blocker and no major. It found one design record that argues against its own consequences, one writer's row and one Store Schemas section that do not carry the timing the other statements now state, a host-path pattern that is a strict widening of the old one but still lets a path glued behind a name character reach the model-facing detail fact and refuses relative names the Javadoc does not name, a public predicate removed with its contract left to prose, and a handful of statements and pins that say more or less than the code. This plan closes those points and no others.

## Deliverables

All file and line references come from the landing analysis at `ac16557d` and are re-read at the head of `main` before editing; drop what a merge since then settled and name the commit.

1. **The commit row's design record and its timing (carried item 102 a; statements only).** (a) ADR `core-store/088` (`:13-17`, `:53-60` against `:63-67`): the Context and the Alternatives justify the new kind by a gate that reads the commit row; the Consequences and `state-catalogue/05:110` say no verb reads it. Rewrite the Context and the two rejected alternatives to the reason that holds (the row is the attribution record; the freshness verb reads `build` and `baseline_red` rows and the stamp), and name `fresh` and `exempt` as verdicts, not kinds. Say once that a pre-commit digest can never equal the post-commit live digest (the `HEAD` id is an input of the Working-Tree Hash, `job-runtime/03:208-209`), so that after a commit only a new build row or the stamp licenses the push. (b) State the timing "taken before the commit" in the writer's row (`doc/specification/state-catalogue/05-git-commit-and-push.adoc:86`) and add the commit-row bullet to `doc/specification/store-schemas/04-epic-and-change-ledger.adoc` (about `:141-160`) that `:110` cites; or change `:110` to cite what exists. No schema block changes (R10). (c) Lighten the dangling apposition at `state-catalogue/05:110` ("...rather than entering the verdict").
   - Skills: pm-documents:ref-asciidoc, pm-dev-java:javadoc
   - Persona: documenter
2. **The host-path pattern's residual and the `exhausted` contract (carried item 102 b; tests first where behaviour changes).** (a) `GitStageAndCommitPrimitive.java:139-174`: a multi-segment absolute path glued behind a path-name character or behind a closing bracket that itself follows one reaches the detail fact (`refused -/srv/git/worktrees/plan`, `refused 1/srv/git/worktrees/plan`, `refused (x)/srv/git/worktrees/plan`; hand-traced by the analysis and by an ASCII emulation, not run). Decide: either close it (a path of two or more segments that starts with `/` and whose first segment is a known host root or whose depth exceeds what a relative name legitimately has: only with the outline's word and with failing-first cases) or pin it, by cases that state the residual, and say so in the Javadoc and ADR `provider-git-primitive/011`. Whichever it is, state the relative names the pattern now refuses (a segment ending in `{`, `\`, a combining mark, a symbol or an emoji) in the Javadoc, and keep `relativeBracketNameIsKept`, `relativeHyphenatedNameIsKept` and `drivePathIsACode` green. (b) Add the cases the analysis found missing to `GitStageAndCommitPrimitiveDeclarationsTest` (about `:131-146`): a drive path, a UNC path and a bracket path behind a new introducer (`{C:\x`), a control character, and the relative names `src/main/x` and `a/b`; each case that guards a refusal asserts the reason. (c) Drop or qualify the class-level sentence at `:69-73` that is true by definition (no absolute path "that follows an introducer" reaches a fact). (d) `CommitAttemptCaps.java:27-31` and ADR `core-store/087` ("It holds no combined predicate, so both caps are read together only after the hook failure"): the sentence is a non-sequitur and the rule is unenforced prose; say what a caller must write (`changeSetCapSpent() || frameCapSpent()` after a hook failure, the single cap on the other events) and in which test or catalogue row that is pinned, or give the catalogue's two events their own named predicates (an API addition; only with a test).
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
3. **The statements and the pins that say more or less than the code (carried item 102 c).** (a) `pm-core/src/main/resources/de/planmarshall/core/store/status.schema.json:365-366` (the `description` of `freshness_reconcile`) and `doc/specification/phase-workflows/03-q-gate-and-execute.adoc:264` give the required member as the reason no stamp is recorded without a successful build; the record checks that a digest is present and the writer confirms the build (ADR `core-store/089`, the `FreshnessReconcile` Javadoc, the corrected `core-store.adoc`): say that in both. Only the `description` string of the schema changes (R10); the parity tests stay green. (b) ADR `provider-git-primitive/011` is linked from nowhere: add it to the guide's ADR list (`provider-git-primitive.adoc`, about `:365-376`) and the README or index; remove the orphan line of the ADR 008 re-wrap ("commit path existed only in"). (c) `WorkflowStatusSchemaParityTest` (about `:150`, `:493`): pin the frame id rule beyond one case (the schema's `minLength` and a second refusal), assert the divergences the Javadoc describes (U+2000 against `isBlank()`, the characters Java `\S` admits and ECMA-262 refuses) or say in the Javadoc that they are described only, and lower-case `stamp()`'s generated string with `Locale.ROOT`. (d) `CommitTrailerSchemaParityTest` Javadoc: drop the process artefacts ("the spec of PLAN-V2-176", "the LEAD of PLAN-V2-179"); state the outcome for `a..b@c.de` (never added, no descendant found) without them.
   - Skills: pm-documents:ref-asciidoc, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: documenter

**Done when:** every behaviour change of deliverable 2 has a case that fails on the code before it (a failing run or a mutation, by log id), or the residual is pinned by cases that pass and the statements say it; the statements of deliverables 1 and 3 say what the code and the specification do; `verify` per module (`pm-core`, `pm-providers/pm-provider-git`) and `verify -Ppre-commit` (its rewrites reviewed and committed; formatter-only drift in a file this plan does not otherwise touch is not shipped) are green, targeted `-Dtest` runs where a full verify exceeds the host's budget; the open Sonar issues of the pull request are fixed or dismissed with a reason.

## Standing Constraints

- **Read every cited line at the head first.** The lines of a carried item are those of the landing analysis, read at `ac16557d`; re-read each at `origin/main` before editing and drop what a merge since then settled (name the commit).
- **Work to the Done-when.** Do not stop to ask the operator which part to continue with.
- **Every carried item gets an outcome** in the landing message and the pull request: adopted into a deliverable, split off to a NAMED existing plan, or dropped, each with its reason.
- **No change of what is refused or stored beyond the corrections the items state.** The format versions stay as the operator decided them; the pattern's direction stays fail-closed.
- **Nothing here is consumed yet.** The assembly (`plan-marshall-mcp`) has no reference to `CommitSite`, `git.stage-and-commit`, the counters or `exhausted`: add no caller of your own.
- **Where the project analyses its pull requests with Sonar:** an open Sonar issue is fixed in a pushed commit, or dismissed with a stated reason; the quality gate is green before the pull request is reported ready.
- **No dependency or plugin is added.** No more than 100 files per pull request.
- **Tests that can fail.** A new test fails on the code it guards (a failing run or a mutation, by log id); a test of a refusal asserts the reason, not only the type (CLAUDE.md, R20).
- **Do not repeat the lane's claims of PLAN-V2-179 as facts:** the analysis did not re-run its failing run or its counts; the hand traces of the pattern are by reading and an ASCII emulation.

## Claim Labels

All lines read at `origin/main` `ac16557d` (the merge of #598) by the analysis leaf; the orchestrator re-read `GitStageAndCommitPrimitive.java:136-178` (and emulated the ASCII subset: `refused {/srv/git/worktrees/plan` is refused, `-/srv/...`, `1/srv/...`, `(x)/srv/...` are not), ADR `core-store/088:11-18,52-68`, and the two description sites of deliverable 3(a). The outline re-reads each before it acts.

- OBSERVED: the contradiction inside ADR 088; the timing stated only in the reader's row; the two `required member` sites; the removal of the public `exhausted()` from `CommitAttemptCaps` and `CommitAttemptCounter` (no caller in the sibling checkouts).
- LEAD (unverified): that the residual leaks hold under the real Java lookbehind over supplementary characters (the emulation was ASCII); that a Turkish default locale breaks `stamp()` (read, not run); that the relative names deliverable 2(a) lists are refused (read from the character class, not run).

## Expected Surface

Specific files (R19; the outline adds the files the re-read names):

- OBSERVED (`pm-core`): `pm-core/doc/adr/core-store/088-a-commit-is-a-kind-of-the-change-ledger.adoc`, `pm-core/doc/adr/core-store/087-the-commit-attempt-caps-are-a-member-of-the-workflow-state.adoc`, `pm-core/src/main/java/de/planmarshall/core/store/CommitAttemptCaps.java`, `pm-core/src/main/resources/de/planmarshall/core/store/status.schema.json` (the `description` only), `pm-core/src/test/java/de/planmarshall/core/store/WorkflowStatusSchemaParityTest.java`.
- OBSERVED (`pm-providers/pm-provider-git`): `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/primitive/GitStageAndCommitPrimitive.java`, `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/primitive/GitStageAndCommitPrimitiveDeclarationsTest.java`, `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/primitive/CommitTrailerSchemaParityTest.java`, `pm-providers/pm-provider-git/doc/developer/provider-git-primitive.adoc`, `pm-providers/pm-provider-git/doc/adr/provider-git-primitive/008-the-staging-set-and-the-commit-share-the-path-rule.adoc`, `pm-providers/pm-provider-git/doc/adr/provider-git-primitive/011-the-introducer-is-the-complement-of-the-path-name-characters.adoc`.
- OBSERVED (`doc`): `doc/specification/state-catalogue/05-git-commit-and-push.adoc`, `doc/specification/store-schemas/04-epic-and-change-ledger.adoc`, `doc/specification/phase-workflows/03-q-gate-and-execute.adoc`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-179 (shipped, #598).
- Overlaps with (class level): PLAN-V2-125 and 126 (test conformance of `WorkflowStatusSchemaParityTest` and the provider-git tests: they run after this plan or rebase over it), PLAN-V2-141 (the ADR index and ADR 007 wording), PLAN-V2-78 (`workflow-dsl.adoc`, `WorkflowStatusSchemaParityTest`), PLAN-V2-161 and 149 (`store-schemas`), and every staged plan by guide, ADR and Traceability file: the gate decides at emit; one shared file does not block.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-182-commit-unit-third-residue-closeout.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Carried Open Items

102. **The residue of the landing of PLAN-V2-179** (`landings/PLAN-V2-179.md`, carried 2026-11-24; merged as #598 `ac16557d`; read by the analysis leaf; no blocker, no major): deliverables 1 to 3 carry each point: (a) the design record and the timing (179 M1, M2, M3, M17), (b) the host-path residual, its tests and the `exhausted` contract (179 M4, M5, M6, M7, M8, M11), (c) the statements and the pins (179 M9, M10, M12, M15); recorded, not fixed: the formatter drift of `FreshnessReconcile` and its test and the three widened `throws` clauses (179 M13, M14), the files outside the spec's surface (179 M16), the lane's failing-run log ids and counts (`stated_not_verified`), and the items the lane lists as recorded: `membersEqualSchema` `@Disabled`, the compiled-default table (PLAN-V2-10), footprint checks (2) and (3) and the end-to-end breach test (the assembly's `CommitSite`), Rule 3 on `depends_on_config` (documentation issue #71) and the CLI variant of the commit contract.

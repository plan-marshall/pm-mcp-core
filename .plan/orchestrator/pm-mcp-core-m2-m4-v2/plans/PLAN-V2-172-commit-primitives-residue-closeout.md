# PLAN-V2-172: The residue of the commit primitives' closeout: the staging set against the commit's file-system and tree refusals, the index restore's gaps, and the statements and tests that over-claim

epic: pm-mcp-core-m2-m4-v2
workstream: WS-01
origin: the three-layer analysis of the landing of PLAN-V2-169 (`landings/PLAN-V2-169.md`, 2026-11-23, #568, merge `f1db02cd`) and the operator's rule that leftovers go into a staged plan (no staged plan owns `pm-provider-git` code)
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-172-commit-primitives-residue-closeout.md` and is queued as one row file, `queue/PLAN-V2-172.json`,
> in the epic ledger (lane `runbook`: the operator runs it with the runbook lane). The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R05, R06, R07, R08, R09, R10, R11, R13, R17, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R09 because the path rules of `StagingSets` and `JGitOperations` are a confinement; R10 because `local-config.schema.json` is read by the schema parity tests; R12: no message is added; R13: no stored format changes unless deliverable 4 widens a schema pattern, which is ask-at-refine.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-providers/pm-provider-git/doc/developer/provider-git-primitive.adoc` and `provider-git.adoc` (exist; the corrected statements); ADR directories: `pm-providers/pm-provider-git/doc/adr/provider-git-primitive/` (next free number after 010 at `origin/main`, R11) and `pm-providers/pm-provider-git/doc/adr/provider-git/`; an ADR is written only for a decision the outline cannot defer; layout: workstream WS-01.
- R12 — none (no message).

**Read the skills first (R20).** `java-core`, `java-null-safety`, `javadoc`, `junit-core` and `cui-testing` of `/home/oliver/git/plan-marshall/marketplace/bundles/{pm-dev-java,pm-dev-java-cui}/skills/`; say in the first progress note which you read. R20 applies to every test line this plan touches.

## Objective

PLAN-V2-169 made the staging set refuse what the commit refuses at the TEXT level (`CommitPaths`), with no blocker and one major left (`landings/PLAN-V2-169.md`): the commit also refuses by file system and by tree, and the staging set does not, so a modified submodule or a directory replaced by a file is staged and the whole commit fails `commit_failed`, the failure D1 was to remove. The rest is small: an index restore that skips an empty path list, a conflicted index, statements that say more than the code, and a parity test that checks a sample. This plan closes them.

## Deliverables

All file and line references come from the landing analysis and are re-read at the head of `main` before editing; drop what a merge since then settled and name the commit.

1. **The staging set against the commit's file-system and tree refusals (carried item 90a, F1 major, F2).** (a) `git status --porcelain` lists a modified submodule as ` M sub` with no slash (read it; reproduce it with a test on a real repository fixture, not a string): `StagingSets.isConfinedName` (`StagingSets.java:111-132`) stages it and `JGitOperations.isRefusedLocation` (`:582-584`, `Files.isDirectory(..., NOFOLLOW_LINKS)`) refuses it, so the whole commit is `commit_failed`: make the two agree (decide, ask-at-refine, R07, recommendation: the staging set consults the same file-system and tree refusals as the commit through one shared helper, so a name the commit refuses is `outside`) and test a modified submodule. (b) A tracked directory replaced by a file (`foo/` becomes `foo`): `JGitOperations.refusedTrackedTree` (`:633-662`) refuses it and `StagingSets` stages it: the same decision, the same helper, a test. (c) ADR `provider-git-primitive/008` (`:16`, "git status lists both by their directory, `dir/`"), `workflow-dsl/06` and `state-catalogue/05` state that git lists a submodule by `dir/`: correct them to what git lists (an embedded repository by `dir/`, a modified submodule by its name) and say what the helper refuses; the title of ADR 008 and `doc/Traceability.adoc` (~`:851`, "a name the commit cannot stage is `outside`") say no more than the code does. (d) Read, then decide: `CommitSite.dirtyPaths` (`CommitSite.java:53-69`) is the assembly's contract for the list: this plan states it, the assembly follows (recorded as a Watch, not changed here).

2. **The index restore's gaps (carried item 90b, F3, F7).** (a) With an empty path list, a change that is staged and reverted in the worktree makes the status dirty, `add .` overwrites the index and the empty commit returns `NOTHING_TO_COMMIT` with the index changed (`JGitOperations.java:386-405`; the restore runs only for `!paths.isEmpty()`): the primitive never sends an empty list, a direct caller can: restore for the empty list too or refuse it with a reason (decide, ask-at-refine, recommendation: restore, so `NOTHING_TO_COMMIT` always leaves the index untouched), and correct `provider-git.adoc` (~`:287-290`) and ADR `provider-git/004`; test it. (b) `DirCache.getEntry(path)` returns the first stage: in a conflicted index the restore (`:448-492`) writes the base entry as stage 0: restore all stages or refuse a conflicted index with a reason; test it. (c) `GitOperations.java:83-86` and ADR `provider-git/004` name the index restore the CodeRabbit major fix added.

3. **The statements that over-claim (carried item 90c, F4, F6, F8).** (a) ADR `provider-git-primitive/009` (`:16-19`, `:44`) says `WorkflowEngine.resolve` binds the primitive's `message` "as an absent optional input": `resolve` (`WorkflowEngine.java:1747-1786`) resolves a sub-workflow call's inputs, the primitive's parameters come from the assembly's `ParameterBinder`: state the premise as it is and record the claim "the assembly binds `message = null` only on the clean-tree route" as an assembly Watch nothing here enforces. (b) `workflow-dsl/06` Stage 3 and `state-catalogue/05`: say the footprint check runs before `nothing_to_commit` (`GitStageAndCommitPrimitive.java:184-188`). (c) The Javadoc "no absolute path reaches a fact" (`GitStageAndCommitPrimitive.java:129`, `:280`): `ABSOLUTE_PATH` matches a `/` only after a fixed character list: widen the pattern with a test, or narrow the sentence.

4. **The schema and its parity test (carried item 90d, F5, residue).** (a) `CommitTrailerSchemaParityTest` (`pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/primitive/`, `:53-56,108-109,146-152`) checks about 20 characters, one generated name and one address, compiled with `java.util.regex`: check the whole C0/C1 and White_Space classes (a loop over the code points, with the one deliberate exception named) and drop or verify the comment "as a JSON-schema validator on the JVM compiles them"; the validator (`json-schema-validator`, a test dependency) is the check that no production code here enforces: record where the assembly enforces the schema as a Watch. (b) `commit_identity` in `local-config.schema.json` (`:130-131`) keeps the patterns `^[^<>\r\n]+$` and `^[^<>\s@]+@[^<>\s@]+$`: the plan of 169 named `commit_trailer` only: decide, ask-at-refine (R07, a stored value that is valid today could fail; recommendation: tighten to the same classes and say so in `store-schemas/08`), and test it.

**Done when:** every deliverable above; every carried item below has its recorded outcome; no behaviour change beyond the corrections the items state. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed, files outside the plan's scope in their own cleanup commit), then `verify` of the changed modules (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module runs).

## Standing Constraints

- **Read every cited line at the head first.** The lines and counts of a carried item are those of the landing analysis it names, read on an older head; re-read each at `origin/main` before editing and drop what a merge since then settled (name the commit).
- **Work to the Done-when.** Do not stop to ask the operator which part to continue with; ask only a decision the spec marks ask-at-refine (R07) or one a carried item says needs the operator, and put it in the outline's question, not in a stop.
- **Every carried item gets an outcome** in the landing message and the pull request: adopted into a deliverable, split off to a NAMED existing plan, or dropped, each with its reason; "not in this run's surface" is no reason.
- **No behaviour change beyond the corrections the items state.** A change a carried item does not ask for is ask-at-refine (R07).
- **Where the project analyses its pull requests with Sonar** (the plan lifecycle's Sonar step reports it configured; where it cannot scan, read the pull request over plain HTTP: `pullRequest=N`, never `branch=<feature>`): an open Sonar issue is fixed in a pushed commit, or dismissed with a stated reason; the quality gate is green before the pull request is reported ready.
- **No dependency or plugin is added.**
- **No more than 100 files per pull request** (the watch of the epic ledger; CodeRabbit refuses a larger one): cut the plan into pull requests by deliverable or by module before the first one is opened.
- **Tests that can fail.** A new test fails on the code it guards (a failing run or a mutation, reported by log id); a test of a refusal asserts the reason, not only the type (CLAUDE.md, R20).
- **The links-check clause is the operator's.** PLAN-V2-164 to 170 reported `BROKEN LINKS` counts (97, 3252 and 3253 by tool root) against a Done-when of `0`: this plan reports the count of local broken links and the count of the rest separately, until the operator words the clause.
- **Do not repeat the mutation claims of 169** as facts: they were not re-run by the analysis. A new test of this plan is shown to fail by its own failing run or mutation, by log id.

## Claim Labels

- OBSERVED: every point was read by the analysis leaf at `origin/main` `f1db02cd`; the orchestrator re-read the asymmetry of F1 (`StagingSets.java:111-132` has no directory test, `JGitOperations.java:582-584` does) and F3 (`JGitOperations.java:386-405`: the restore runs for `!paths.isEmpty()` only).
- LEAD (unverified): that `git status --porcelain` lists a modified submodule without a trailing slash (git's documented form; reproduce it on a fixture); F2 (the directory-to-file case, read in `refusedTrackedTree`, not run); F7 (the conflicted-index restore); the assembly's `ParameterBinder` behaviour on an absent message.

## Expected Surface

Source, tests, specification and documentation (specific files, R19; the outline adds the files the re-read at the head names):

- OBSERVED (`pm-provider-git`): `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/primitive/StagingSets.java`, `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/primitive/CommitSite.java`, `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/primitive/GitStageAndCommitPrimitive.java`, `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/primitive/CommitPaths.java`, `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/JGitOperations.java`, `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/GitOperations.java`, `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/primitive/StagingSetsTest.java`, `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/primitive/CommitTrailerSchemaParityTest.java`, `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/primitive/GitStageAndCommitPrimitiveTest.java`, `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/JGitOperationsTest.java`, `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/JGitOperationsPathTest.java`, `pm-providers/pm-provider-git/doc/developer/provider-git-primitive.adoc`, `pm-providers/pm-provider-git/doc/developer/provider-git.adoc`, `pm-providers/pm-provider-git/doc/adr/provider-git-primitive/008-the-staging-set-refuses-what-the-commit-refuses.adoc`, `pm-providers/pm-provider-git/doc/adr/provider-git-primitive/009-an-absent-message-is-the-primitives-clean-tree-route.adoc`, `pm-providers/pm-provider-git/doc/adr/provider-git/004-a-directory-is-refused-and-emptiness-is-typed.adoc`.
- OBSERVED (`pm-core`): `pm-core/src/main/resources/de/planmarshall/core/config/local-config.schema.json`.
- OBSERVED (`doc`): `doc/specification/workflow-dsl/06-reusable-sub-workflows.adoc`, `doc/specification/state-catalogue/05-git-commit-and-push.adoc`, `doc/specification/store-schemas/08-local-config.adoc` (file name read at the head), `doc/Traceability.adoc`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-169 (shipped, `landings/PLAN-V2-169.md`: the plan this one succeeds), PLAN-V2-20.
- Overlaps with (class level): PLAN-V2-126 (test-conformance of the provider tests: `JGitOperations*Test`), PLAN-V2-78 (the change map, `pm-provider-git` sources), PLAN-V2-161 (specification files `workflow-dsl/06`, `state-catalogue/05`) and every staged plan by Traceability: the gate decides at emit; one shared file does not block.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-172-commit-primitives-residue-closeout.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Carried Open Items

90. **The residue of the landing of PLAN-V2-169, the staging set against the commit's file-system and tree refusals, the index restore, the statements and the schema** (`landings/PLAN-V2-169.md`, carried 2026-11-23; merged as #568 `f1db02cd`; read by the analysis leaf at `f1db02cd`; no blocker, one major). Deliverables 1 to 4 carry each point: (a) the staging set against the commit (a modified submodule, a directory replaced by a file, the statements that say git lists a submodule by `dir/`); (b) the index restore (the empty path list, the conflicted index, the statement); (c) the statements that over-claim (ADR 009's premise, the footprint check before `nothing_to_commit`, the absolute-path Javadoc); (d) the parity test that checks a sample and the `commit_identity` patterns.

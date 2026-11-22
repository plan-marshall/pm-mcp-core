# PLAN-V2-169: The closeout of the commit primitives: the staging set against the path refusals, the clean-tree route, the missing tests and the statements that over-claim

epic: pm-mcp-core-m2-m4-v2
workstream: WS-01
origin: the three-layer analysis of the landing of PLAN-V2-20 (`landings/PLAN-V2-20.md`, 2026-11-22, #545, merge `c0d14b96`) and the operator's rule that leftovers go into a staged plan (no staged plan owns `pm-provider-git` code)
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-169-git-commit-primitives-closeout.md` and is queued as one row file, `queue/PLAN-V2-169.json`,
> in the epic ledger (lane `runbook`: the operator runs it with the runbook lane). The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R05, R06, R07, R08, R09, R10, R11, R13, R17, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R09 because the path rule of `StagingSets` and `JGitOperations` is a confinement; R10 because `local-config.schema.json` is read by the schema parity tests; R12: no message is added; R13: no stored format changes unless deliverable 5 changes the schema pattern, which is ask-at-refine.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-providers/pm-provider-git/doc/developer/provider-git-primitive.adoc` and `provider-git.adoc` (exist; the corrected statements); ADR directory: `pm-providers/pm-provider-git/doc/adr/provider-git-primitive/` (renumbered against `origin/main`, R11; an ADR is written only for a decision deliverables 1 to 3 take); layout: workstream WS-01.
- R12 — none (no message).

**Read the skills first (R20).** `java-core`, `java-null-safety`, `javadoc`, `junit-core` and `cui-testing` of `/home/oliver/git/plan-marshall/marketplace/bundles/{pm-dev-java,pm-dev-java-cui}/skills/`; say in the first progress note which you read. R20 applies to every test line this plan touches.

## Objective

PLAN-V2-20 shipped the four commit primitives with no blocker and no confirmed major, and left a short residue that no staged plan owns (`landings/PLAN-V2-20.md`, layers 1 to 3). The principal points: the staging set accepts names that the commit refuses, so a whole plan commit fails closed on one odd file name; the clean-tree route of `none_assert_clean` is not shown to bind; two refusals diverge from the specification's wording; three tests the Done-when and the advisories name do not exist; and two statements say more than the code does. None changes a stored datum unless deliverable 5 chooses a schema change (ask-at-refine).

## Deliverables

All file and line references come from the landing analysis and are re-read at the head of `main` before editing; drop what a merge since then settled and name the commit.

1. **The staging set against the path refusals (carried item 87a, L3-2, L3-7, L3-8).** `StagingSets.derive` (`StagingSets.java:105-126`) accepts a dirty name that `JGitOperations.commit` refuses with `PATH_REFUSED` (`JGitOperations.java:487-513`): a name that starts with `:`, contains a backslash, a control character or a newline, an embedded repository `sub/` or a modified submodule path (`git status` lists both as `dir/`). The whole plan commit then fails closed as `failed`/`commit_failed` before any staging. Decide (ask-at-refine, R07; recommendation: the staging set puts such an entry in `outside`, so the footprint check refuses it with `footprint_violation` and names it, a typed answer instead of a git detail, ~65%) and implement it; state in `CommitSite.dirtyPaths`, the guide and the two specification texts (`workflow-dsl/06`, `state-catalogue/05`) that an embedded repository or a submodule is listed by its directory and that a rename lists both sides (or say the site lists the new path only, whichever the assembly's `CommitSite` can honour: ask in the outline's question). Test first: `StagingSetsTest` for a colon-led, a backslash and a control-character name and a `sub/` entry, each failing on the code before the change. The `detail` fact carries an absolute root path (`GitStageAndCommitPrimitive.java:241-242`): carry a repository-relative detail or a code, and say why in the Javadoc.

2. **The bound `files` outside the staging set (carried item 87b, L3-3).** `GitStageAndCommitPrimitive.java:180-182` (`toCommit.retainAll(files)`) drops a path outside the staging set silently, so a typo commits a partial set, or `nothing_to_commit` when nothing remains; the specification places the refusal at the bind (`path.staging`). Decide (ask-at-refine; recommendation: keep the narrowing, state in the Javadoc, the guide and ADR `provider-git-primitive/003` that the slot's rule owns the refusal and this primitive narrows only, and pin it with a test that fails if the primitive starts refusing, ~60%), or refuse in the primitive with a named reason (the reason set is closed: ADR `005`).

3. **The clean-tree route and the missing-identity code (carried item 87c, L3-1, L3-6).** `GitResolveMessagePrimitive.java:97-99` answers `ready` with no `message` fact for `none_assert_clean` on a clean tree, and `GitStageAndCommitPrimitive.Params.message` is `requireNonNull` (`:117-124`); the specification (`workflow-dsl/06:268-290`) does not say where a clean tree goes after `ready`. Read how the engine binds an absent fact in `pm-workflow` (`InputBinding`, the state catalogue `05` route of `ready`) and decide: either the primitive handles it (an empty staging set returns `nothing_to_commit` before the message is read; `message` becomes `@Nullable` with its rule), or the specification names the route (`ready` without `message` goes to `git.push-barrier-decision`). A test that fails on the chosen behaviour. Align the missing-identity code: the specification (`store-schemas/08`) says `identity_missing`, the primitive returns `failed`/`commit_failed` with a `detail` (`:210-213`); recommendation: the specification follows the code (the reason set is closed, ADR `005`), ~75%.

4. **The tests that do not exist and the classes over the budget (carried item 87d).** A test in which the nearest existing ancestor of a path is itself a dangling link (`JGitOperations.isParentConfined`, `:529-545`; `JGitOperationsTest` has `commitsADanglingLink` and `refusesAPathBelowAnOutwardLink` only), a test for `git.add` leaving the listed paths staged on `NOTHING_TO_COMMIT` (L3-4: either assert it as the documented behaviour or stage after `hasChanges`; recommendation: stage after the check, ~60%), and the four test classes over the 400-line budget (`JGitOperationsTest` 988, `GitStageAndCommitPrimitiveTest` 597, `CommitTrailerTest` 411, `GitResolveMessagePrimitiveTest` 407): split by nested class into files, a pure move in its own commit with no assertion changed.

5. **The statements that over-claim and the schema (carried item 87e).** `doc/Traceability.adoc` (`PM-EXEC-5` near `:1432` and the `846` entry) says every path is confined: state that the primitive confines the directory of a name and that `JGitOperations` refuses the rest. ADR `workflow-verify/043:41` says no assertion of the defaults test changed: say no assertion was removed, the test was split. `pm-core/src/main/resources/de/planmarshall/core/config/local-config.schema.json:155-162` (`commit_trailer` name `^[^<>\r\n]+$`, email `^[^<>\s@]+@[^<>\s@]+$`) is wider than the code: it admits U+0085, U+2028, U+2029, an unpaired surrogate and C0/C1 controls (a stored NUL or ESC reaches the trailer line, L3-5), and the code's `\s` includes U+0085 where a JSON-schema `\s` does not. Decide (ask-at-refine, R07/R10; recommendation: state a waiver in `store-schemas/08` and the guide that the schema pattern is a shape check and the code is the authority, and refuse C0/C1 controls (other than the ones already refused) in `CommitTrailer.isValidName`, ~65%; a schema change is the alternative and needs the parity tests).

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
- **No new hand-written fake** (CLAUDE.md, ADR `doc/adr/026`); the primitives' tests use EasyMock of `CommitSite`, as #545 did.
- **The assembly is outside this repository.** What `plan-marshall-mcp` implements (`CommitSite`, the engine counters, the CLI variant, the production unit) is not a deliverable; a point that needs its word is a Watch, named in the landing message.

## Claim Labels

- OBSERVED: every file and line of the deliverables was read by the analysis leaf at `origin/main` `cc52de5f`; the orchestrator re-read `GitResolveMessagePrimitive.java:88-103`, `GitStageAndCommitPrimitive.java:117-124` and the specification text `workflow-dsl/06:268-290`.
- LEAD (unverified): that the engine refuses an absent `message` fact for a clean tree (deliverable 3: nothing in this repository shows it either way); that a NUL in a trailer truncates a message in some git consumers (deliverable 5); that a symlinked ancestor swap between the check and the add is not exploitable (L3-11, recorded and dropped).

## Expected Surface

Source, tests, specification and documentation (specific files, R19; the outline adds the files the re-read at the head names):

- OBSERVED (`pm-provider-git`): `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/primitive/StagingSets.java`, `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/primitive/CommitSite.java`, `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/primitive/GitStageAndCommitPrimitive.java`, `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/primitive/GitResolveMessagePrimitive.java`, `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/primitive/CommitTrailer.java`, `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/JGitOperations.java`, `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/primitive/StagingSetsTest.java`, `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/primitive/GitStageAndCommitPrimitiveTest.java`, `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/primitive/GitResolveMessagePrimitiveTest.java`, `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/primitive/CommitTrailerTest.java`, `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/JGitOperationsTest.java` (all read).
- OBSERVED (documentation): `pm-providers/pm-provider-git/doc/developer/provider-git-primitive.adoc`, `pm-providers/pm-provider-git/doc/developer/provider-git.adoc`, `pm-providers/pm-provider-git/doc/adr/provider-git-primitive/003-the-staging-set-is-derived-from-the-scope.adoc`, `pm-providers/pm-provider-git/doc/adr/provider-git-primitive/005-one-failed-outcome-with-a-closed-reason.adoc`, `pm-workflow/doc/adr/workflow-verify/043-the-commit-message-kind-is-a-single-submit-default.adoc`, `doc/Traceability.adoc`, `doc/specification/store-schemas/08-project-configuration-local.adoc`, `doc/specification/workflow-dsl/06-reusable-sub-workflows.adoc`, `doc/specification/state-catalogue/05-git-commit-and-push.adoc`.
- OBSERVED (`pm-core`, only if deliverable 5 changes the schema): `pm-core/src/main/resources/de/planmarshall/core/config/local-config.schema.json` and its parity test.
- LEAD (`pm-workflow`, read for deliverable 3): the input-binding code that binds a fact absent from a primitive's outcome.

## Dependencies and Sequencing

- Depends on: PLAN-V2-20 (shipped, `landings/PLAN-V2-20.md`: the plan this one succeeds), PLAN-V2-15 and PLAN-V2-164 (the path contract the primitives build on).
- Overlaps with (class level): PLAN-V2-126 (the provider tests of `pm-provider-git`: `GitStageAndCommitPrimitiveTest`, `JGitOperationsTest`), PLAN-V2-161 (`workflow-dsl/06`, `state-catalogue/05`); every staged plan by Traceability: the gate decides at emit; one shared file does not block.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-169-git-commit-primitives-closeout.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Carried Open Items

87. **The residue of the landing of PLAN-V2-20, the staging set against the path refusals, the unbound `files`, the clean-tree route, the missing tests and the over-claiming statements** (`landings/PLAN-V2-20.md`, carried 2026-11-22; merged as #545 `c0d14b96`; read by the analysis leaf at `cc52de5f`; no blocker, no confirmed major). OBSERVED unless marked. Deliverables 1 to 5 carry each point: (a) L3-2, L3-7, L3-8; (b) L3-3; (c) L3-1 (LEAD, major if confirmed), L3-6; (d) the dangling-ancestor test, L3-4, the four test classes; (e) the Traceability and ADR 043 wording, the `commit_trailer` schema and L3-5. L3-9 (a literal-string scan), L3-10 (`hook_failed` and `attempts_exhausted` have no producer until the CLI variant and the engine counters exist, documented in ADR 005) and L3-11 are recorded and need no change. The open hand-offs (`CommitSite` per scope, the production unit and its drift rows, the engine counters and the change-ledger row, the CLI variant PM-WATCH-DSL-7 with its two review points, the `agent_identity` derivation) belong to `plan-marshall-mcp` and are a Watch of the epic, not a deliverable.

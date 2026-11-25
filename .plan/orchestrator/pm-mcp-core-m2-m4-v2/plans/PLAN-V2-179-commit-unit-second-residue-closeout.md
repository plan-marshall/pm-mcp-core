# PLAN-V2-179: The second residue of the commit unit: the referent of the commit row's `worktree_sha`, the host-path pattern of the primitive's detail fact, the exhausted-caps contract and the statements that overclaim

epic: pm-mcp-core-m2-m4-v2
workstream: WS-01
origin: the three-layer analysis of the landing of PLAN-V2-176 (`landings/PLAN-V2-176.md`, 2026-11-23, #586, merge `a1fe8740`) and the operator's rule that leftovers go into a staged plan (no staged plan owns `GitStageAndCommitPrimitive`, `CommitPathRefusals`, `CommitTrailerSchemaParityTest` or ADR `core-store/088`: PLAN-V2-161 shares only `WorkflowStatusCodec` and is over the file cap).
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-179-commit-unit-second-residue-closeout.md` and is queued as one row file, `queue/PLAN-V2-179.json`,
> in the epic ledger (lane `runbook`: the operator runs it with the runbook lane). The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R05, R06, R08, R10, R11, R13, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R07: nothing is ask-at-refine except the referent of deliverable 1, which the outline settles from the specification; R09: no confinement; R12: no message is added; R10 because `status.schema.json` and `change-ledger-record.schema.json` are read by the schema parity tests: their blocks stay byte-stable unless a deliverable names the change; R13: no stored format changes here; R17 is lifted, the links check is not a requirement, operator 2026-11-23.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-store.adoc` and `pm-providers/pm-provider-git/doc/developer/provider-git-primitive.adoc` (exist; the corrected statements); ADR directories: `pm-core/doc/adr/core-store/` (a correction of ADR 088 is an edit of that ADR, or a new ADR that supersedes its paragraph; the next free number after 089 at `origin/main`, R11) and the primitive's ADR directory; layout: workstream WS-01.
- R12 — none (no message).

**Read the skills first (R20).** `java-core`, `java-lombok`, `java-null-safety`, `javadoc`, `junit-core` and `cui-testing` of `/home/oliver/git/plan-marshall/marketplace/bundles/{pm-dev-java,pm-dev-java-cui}/skills/`; say in the first progress note which you read. R20 applies to every test line this plan touches.

## Objective

PLAN-V2-176 built the commit unit's counters, the commit row and the `freshness_reconcile` writer, and closed the PLAN-V2-172 residue. Its analysis found one statement that is false against the specification (the referent of the commit row's `worktree_sha`), one regression of the primitive's fail-closed direction (the host-path pattern), and a few statements and tests that say more than the code shows. This plan closes those points and no others.

## Deliverables

All file and line references come from the landing analysis at `a1fe8740` and are re-read at the head of `main` before editing; drop what a merge since then settled and name the commit.

1. **The referent of the commit row's `worktree_sha` (carried item 99 M1; ask-at-refine only if the specification does not decide it).** ADR `core-store/088` (the `worktree_sha` bullet of its Decision), the Javadoc of `ChangeLedgerCommitRowTest` ("the tree it was made on") and the ChangeLedger paragraph of `core-store.adoc` say the digest "is over the working-tree content, which a commit leaves unchanged ... the same digest the build rows of that tree carry". Rule 1 of the Working-Tree Hash (`doc/specification/job-runtime/03-parsers-and-gates.adoc:208`, `PM-SVC-1`) takes the `HEAD` commit id as an input and every path the status reports: a commit changes `HEAD` and the status path set, so the digest after the commit is not the digest the build rows of the pre-commit tree carry. Decide which digest the row carries (taken before the commit, so it equals the build rows of the tree it was made on, or taken after it, so it is the digest the freshness gate recomputes at the new `HEAD`), from what `git.pre-commit-verify-freshness` (`state-catalogue/05-git-commit-and-push.adoc:110`) needs; correct the ADR, the test Javadoc and the guide to say it, and state in the state catalogue which kinds the freshness verb reads (it still lists `fresh`, `exempt`, `baseline_red` and `freshness_reconcile`, and no reader of the commit row is specified). No code changes unless the decision needs a member the record lacks.
   - Skills: pm-documents:ref-asciidoc, pm-dev-java:javadoc
   - Persona: documenter
2. **The host-path pattern of the primitive's detail fact (carried item 99 M2; tests first).** `GitStageAndCommitPrimitive` (`pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/primitive/GitStageAndCommitPrimitive.java:131-170`): `INTRODUCER` is a positive list (`[\\s"'`|;,!?*#&+@%$^~(\\[<=:>]`) and lacks `{`, non-ASCII quotes and punctuation and NBSP, so `refused {/srv/git/worktrees/plan}` matches none of `ABSOLUTE_PATH`, `DRIVE_PATH`, `UNC_PATH` or `BRACKET_PATH` and a multi-segment host path reaches the model-facing detail fact; the old negative lookbehind caught it, and the reworded sentence "No multi-segment absolute path ... reaches a fact" is false for it. Decide the pattern (admit a `/` as a path start after anything but the characters a relative name legitimately precedes it with, or extend the list), keep `relativeBracketNameIsKept` and `drivePathIsACode` green, add the cases (`{`, a non-ASCII quote, NBSP) to `messageNamingAHostPathIsACode` and show by a failing run that they fail on the current pattern.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
3. **The contract of `exhausted()` and the parity tests (carried item 99 M3, M4).** `CommitAttemptCounter.exhausted()` is true once either cap is spent, so after the third `git.commit-fix` task it is true before the fourth commit attempt runs, while `PM-WATCH-DSL-28`'s Guard expects the fourth hook failure to take `attempts_exhausted`: say which event a caller checks which cap on (or give the two caps their own predicates and drop the combined one), and add the tree-only reset case to the hazard test (`frameCapBoundsAResetChangeSet` changes tree and message in every round). `WorkflowStatusSchemaParityTest.commitAttemptCapsEqualSchema` and `freshnessReconcileMembersEqualSchema` compare member names only: add the pattern, the maximum 3 against `CommitAttemptCaps.CAP` and the frame id rule, or state in the test's Javadoc that names are all they pin.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
4. **The statements and the test Javadoc that say more than the code (carried item 99 M5 to M8, D6 f and g).** (a) `core-store.adoc` section `_freshness_reconcile`: "the record requires all five members, so a stamp is never written when no successful build exists" overclaims; the record checks that a digest is present, and the writer must confirm the build (ADR `core-store/089`, the `FreshnessReconcile` Javadoc): make the guide say so. (b) `CommitPathRefusalsTest` class Javadoc (`:30`) says the tracked-tree rule "fails closed on a repository it cannot read"; the method Javadoc, `CommitSite` and the test say it stays silent: fix the sentence; add a test for the `JGitInternalException` branch of `refusedTrackedTrees(Path, ...)` or say it is not reached. (c) `PM-WATCH-STORE-62`'s Guard (`doc/implementation-watch/store-schemas.adoc`, around `:848-854`) says "pinned code point by code point" and the admit side of `CommitTrailerSchemaParityTest` is an enumerated sample: align one with the other (complete the test, or reword the Guard); the literal `a..b@c.de` is not in the provider-git test tree at `a1fe8740` (find where the spec of PLAN-V2-176 meant it); state the ECMA-262 behaviour (a trailing terminator refused) in the test, not only the JVM divergence. (d) ADR `provider-git-primitive/008`: the file name still says "the staging set refuses what the commit refuses" while the title is now "The Staging Set and the Commit Share the Path Rule": rename the file (and every link to it) or leave the name and say why; wrap its Context line (over 120 characters). (e) `JGitOperations` now calls `CommitPathRefusals.isRefusedText`, which delegates to `CommitPaths.isRefusedText`: collapse to one entry point or say why two stay. (f) ADR `core-store/087` cites R13 as having fixed the status member's version at 1 while ADR `088` says the question was ask-at-refine: make the two agree on what the operator decided (only the ledger's version question was asked, operator answer 2 of PLAN-V2-176), and state in ADR 087 that the tree id grammar is SHA-1 only (`[0-9a-f]{40}`; a SHA-256 tree id is refused) and that a change of frame restarts the frame count at one.
   - Skills: pm-documents:ref-asciidoc, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: documenter

**Done when:** a test for deliverable 2 fails on the code before it (a failing run or a mutation, by log id); the statements of deliverables 1, 3 and 4 say what the code and the specification do; `verify` per module (`pm-core`, `pm-providers/pm-provider-git`) and `verify -Ppre-commit` (its rewrites reviewed and committed) are green, targeted `-Dtest` runs where a full verify exceeds the host's budget; the open Sonar issues of the pull request are fixed or dismissed with a reason.

## Standing Constraints

- **Read every cited line at the head first.** The lines and counts of a carried item are those of the landing analysis, read on an older head; re-read each at `origin/main` before editing and drop what a merge since then settled (name the commit).
- **Work to the Done-when.** Do not stop to ask the operator which part to continue with.
- **Every carried item gets an outcome** in the landing message and the pull request: adopted into a deliverable, split off to a NAMED existing plan, or dropped, each with its reason.
- **No change of what is refused or stored beyond the corrections the items state.** The format versions stay as the operator decided them.
- **Nothing here is consumed yet.** The assembly (`plan-marshall-mcp`) has no reference to `CommitSite`, `git.stage-and-commit` or the counters: add no caller of your own.
- **Where the project analyses its pull requests with Sonar:** an open Sonar issue is fixed in a pushed commit, or dismissed with a stated reason; the quality gate is green before the pull request is reported ready.
- **No dependency or plugin is added.** No more than 100 files per pull request.
- **Tests that can fail.** A new test fails on the code it guards (a failing run or a mutation, by log id); a test of a refusal asserts the reason, not only the type (CLAUDE.md, R20).
- **Do not repeat the mutation claims of 176 as facts:** the analysis re-read the named test of the counter mutation and no log id.

## Claim Labels

All lines read at `origin/main` `a1fe8740` (the merge of #586) by the analysis leaf; the orchestrator re-read: ADR `core-store/088` lines 29-30 against `job-runtime/03-parsers-and-gates.adoc` rule 1 (the `HEAD` commit id is an input of the digest), the `INTRODUCER` constant (`GitStageAndCommitPrimitive.java:133`, no `{`), the `CommitPathRefusalsTest` Javadoc (`:30`) and `core-store.adoc:360`. The outline re-reads each before it acts.

- OBSERVED: the four points above; `exhausted()` is `changeSetSpent() || frameSpent()`.
- LEAD (unverified): that `refused {/srv/git/worktrees/plan}` reaches the fact (the leaf did not run it; the pattern read says so); that `a..b@c.de` was dropped and not renamed; the counter's SHA-256 refusal.

## Expected Surface

Specific files (R19; the outline adds the files the re-read names):

- OBSERVED (`pm-core`): `pm-core/doc/adr/core-store/087-*.adoc`, `pm-core/doc/adr/core-store/088-a-commit-is-a-kind-of-the-change-ledger.adoc`, `pm-core/doc/adr/core-store/089-*.adoc`, `pm-core/doc/developer/core-store.adoc`, `pm-core/src/main/java/de/planmarshall/core/store/CommitAttemptCounter.java`, `pm-core/src/test/java/de/planmarshall/core/store/CommitAttemptCounterTest.java`, `pm-core/src/test/java/de/planmarshall/core/store/ChangeLedgerCommitRowTest.java`, `pm-core/src/test/java/de/planmarshall/core/store/WorkflowStatusSchemaParityTest.java`.
- OBSERVED (`pm-provider-git`): `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/primitive/GitStageAndCommitPrimitive.java`, `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/CommitPathRefusals.java`, `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/JGitOperations.java`, `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/primitive/GitStageAndCommitPrimitiveDeclarationsTest.java`, `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/primitive/CommitTrailerSchemaParityTest.java`, `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/CommitPathRefusalsTest.java`, `pm-providers/pm-provider-git/doc/adr/*the-staging-set-refuses-what-the-commit-refuses.adoc`.
- OBSERVED (`doc`): `doc/specification/state-catalogue/05-git-commit-and-push.adoc`, `doc/implementation-watch/store-schemas.adoc`, `doc/implementation-watch/workflow-dsl.adoc`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-176 (shipped, #586).
- Overlaps with (class level): PLAN-V2-161 (`WorkflowStatusCodec`, `store-schemas`, `workflow-dsl`), PLAN-V2-125 and 126 (test conformance of the trees these tests live in), and every staged plan by guide, ADR and Traceability file: the gate decides at emit; one shared file does not block.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-179-commit-unit-second-residue-closeout.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Carried Open Items

99. **The residue of the landing of PLAN-V2-176** (`landings/PLAN-V2-176.md`, carried 2026-11-23; merged as #586 `a1fe8740`; read by the analysis leaf at `a1fe8740`; no blocker, two majors): deliverables 1 to 4 carry each point: M1 the referent of the commit row's `worktree_sha`, M2 the host-path pattern, M3 and M4 the `exhausted()` contract and the name-only parity tests, M5 to M8 and the PLAN-V2-176 D6 f and g residue the statements; recorded, not fixed: the whole-document `MEMBERS` parity test stays `@Disabled` for the process-and-overview work package, the compiled-default table (PLAN-V2-10), footprint checks (2) and (3) and the end-to-end breach test (the assembly's `CommitSite`), Rule 3 on `depends_on_config` (documentation issue #71), the CLI variant of the commit contract.

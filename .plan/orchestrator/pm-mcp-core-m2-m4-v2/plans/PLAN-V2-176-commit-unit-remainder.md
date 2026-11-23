# PLAN-V2-176: The remainder of the commit unit that lives in this repository: the commit attempt counters, the change-ledger commit row, the `freshness_reconcile` writer and the `footprint_violation` mapping

epic: pm-mcp-core-m2-m4-v2
workstream: WS-01
origin: the Open Defect of 2026-10-29 "Remainder of the commit unit with an owner but no plan" and the operator's decision of 2026-11-14 (issues pass Q10, answer 1): "leave it until PLAN-V2-20 (`git-commit-primitives`) is emitted, then stage it"; PLAN-V2-20 landed (#545 `c0d14b96`, #564, #568 `f1db02cd`), the trigger fired; staged on the operator's answer 1 of 2026-11-23 (`handle-open`, item 5).
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-176-commit-unit-remainder.md` and is queued as one row file, `queue/PLAN-V2-176.json`,
> in the epic ledger (lane `runbook`: the operator runs it with the runbook lane). The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R05, R06, R07, R08, R10, R11, R13, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R09: no confinement; R12: no message is added unless a deliverable adds a log record, then its holder and `doc/LogMessages.adoc`; R10 because `status.schema.json` and `change-ledger-record.schema.json` are read by the schema parity tests: their schema blocks, kind tables and anchors stay byte-stable unless a deliverable names the change; R13: a stored format changes only where a deliverable says so, and the format version is then decided at the outline (ask-at-refine); R17 is lifted (the links check is no longer a requirement, operator 2026-11-23).)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-store.adoc` (exists) and `pm-providers/pm-provider-git/doc/developer/provider-git-primitive.adoc` (exists); ADR directories: `pm-core/doc/adr/core-store/` (next free number after 087 at `origin/main`, R11, re-read at the head) and `pm-providers/pm-provider-git/doc/adr/` (the directory of the primitive's ADRs; the number re-read at the head); one ADR per decision taken; layout: workstream WS-01.
- R12 — a log record only where a deliverable adds one.

**Read the skills first (R20).** `java-core`, `java-lombok`, `java-null-safety`, `javadoc`, `junit-core` and `cui-testing` of `/home/oliver/git/plan-marshall/marketplace/bundles/{pm-dev-java,pm-dev-java-cui}/skills/`; say in the first progress note which you read. R20 applies to every test line this plan touches.

## Objective

PLAN-V2-20 and its closeouts (#545, #564, #568) built the commit primitive and its staging set, footprint check (1) and the commit-message rules. The pieces of the commit unit that live in this repository and still have no code are the attempt counters of `git.commit-fix`, the change-ledger row of a commit, the writer of `freshness_reconcile` and the mapping of a footprint breach to the `footprint_violation` condition. This plan builds them. What is not here: the compiled-default table (needs the operator's decision, PLAN-V2-10), footprint checks (2) and (3) (the assembly implements them as `CommitSite` methods), Rule 3 on `depends_on_config` (waits for documentation issue #71).

## Deliverables

Every file and line below is the state at `origin/main` `8c7eac6f` (2026-11-23) from the orchestrator's reading of the Open Defect and the landings; re-read each at the head of `main` before specifying, and drop what a merge since then settled (name the commit). "No code" means: no hit in `pm-core`, `pm-workflow`, `pm-runtime` or `pm-providers` `src/main` at that head.

1. **The commit attempt counters (tests first).** The specification (`timeouts/01-job-budgets-and-waits.adoc:536-537`, `state-catalogue/05-git-commit-and-push.adoc:87`): `git.commit-fix` allows `3` failed commit attempts per identical staged change set (the staged tree id plus the message), reset when either changes (CLI variant only), and a second cap per identity. No code holds either counter. Specify and build the counter as a pure class with its store shape decided at the outline (ask-at-refine, R07: where it persists and whether it is a derived fact or a status member); the hazard of `implementation-watch/workflow-dsl.adoc:566` (the identical-attempt counter resets whenever a fix changes the staged tree) is a test: a counter that resets on a changed staged tree is refused by the spec's own words only when the message is unchanged.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
2. **The change-ledger row of a commit.** `ChangeLedgerRecord.Kind` has `build`, `change`, `job`, `task_diff` and `baseline_red` and no commit kind (`ChangeLedgerRecord.java:73-83`); `git.pre-commit-verify-freshness` reads `change-ledger.jsonl` for `fresh`, `exempt` and a `baseline_red` row of the same `worktree_sha` (`state-catalogue/05-git-commit-and-push.adoc:110`). Specify the row (its members, the `worktree_sha` it carries, the codec, `change-ledger-record.schema.json`, the parity test) in one commit; a new kind is a stored-format change, so the format version question is the outline's (R13, ask-at-refine).
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
3. **The `freshness_reconcile` writer.** The stamp exists only as a schema member (`status.schema.json`, around :336) and as a whitelist entry of the codec (`WorkflowStatusCodec.java`, around :164); no code writes it (`phase-workflows/05-finalize-review-metrics-descriptor.adoc:233`: the stamp for commits after the latest successful build). Write it through the status codec and its parity test; the reader side is the freshness verb of deliverable 2.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
4. **The mapping of `footprint_violation` to a condition.** The condition kind exists (`OutcomeReason.java:31-38` in `pm-core/.../spi/`, `MAPPABLE_CONDITION_KINDS`; `WorkflowStatus.java:124`, `CONDITION_KINDS`) and nothing maps to it. Map the footprint breach reasons of `FootprintBoundary` (`pm-provider-git`) to it through the reason registry, as `implementation-watch/phase-workflows.adoc:222,427` describe, with a test that a breach raises the condition and a clean commit does not.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
5. **Statements and the ADRs.** The corrected statements in `core-store.adoc` and `provider-git-primitive.adoc`, one ADR per decision of deliverables 1 to 4, `doc/Traceability.adoc` only for the lines of the requirements this plan implements (R08).
   - Skills: pm-documents:ref-asciidoc, pm-dev-java:javadoc
   - Persona: documenter

6. **The residue of the landing of PLAN-V2-172 in `pm-provider-git` (carried item 95).** Read at `3eb66471` (`landings/PLAN-V2-172.md`); each point is re-read at the head first. (a) The Javadoc of `CommitPathRefusals.refusedTrackedTrees(Repository, ...)` (`:117-120`) says a symbolic link that replaces a tracked directory is refused; the code skips it (`:138-140`) and the class Javadoc says "not refused": make the method Javadoc say what the code does. (b) ADR `provider-git-primitive/008` `:15-16` still says git lists a modified submodule and an embedded repository "both by their directory, `dir/`" (the rest of the ADR says the opposite for a submodule) and its title says more than the shared rule covers (an unresolved conflict stays the commit's own, `:31-34`): correct both. (c) `GitStageAndCommitPrimitive.ABSOLUTE_PATH` (`:133-134`) now takes a `/` after any punctuation other than `. _ ~ / \ -` for an absolute path, so a name such as `app/[slug]/page.tsx` or `(group)/x` in a detail is replaced by the bare git code, while a backslash or drive path passes: decide the pattern (a test of an admitted relative name with a bracket, a test of the drive form) and drop or qualify the sentence "No absolute path therefore reaches a fact" (`:286`). (d) `CommitPathRefusals.refusedTrackedTrees(Path, ...)` (`:105-114`) swallows `IOException` and `JGitInternalException` (also `RefusingFS.CapabilityRefusedException`) without a trace: the staging set then stages a tracked-tree name and the commit fails `commit_failed`, the failure the shared rule removed; decide whether an unreadable repository is `outside` for every name or stays silent, and say it in the Javadoc of `CommitSite`. (e) The Hazard of `PM-WATCH-DSL-48` names a failure that cannot happen ("stage a directory the commit refuses") and contradicts its Guard; `PM-WATCH-STORE-62` says a commit trailer admits "a C0 or C1 control": the name rule refuses CR, LF, U+0085, U+2028 and U+2029 and the e-mail rule refuses TAB, VT, FF, CR, LF and U+0085, so only the rest is admitted: state it exactly; ADR `009` `:18-19` states the binder's null message as a fact where the spec calls it an unverified lead. (f) `CommitTrailerSchemaParityTest`: the admit side is a sample while `store-schemas.adoc:854` says "pinned code point by code point"; `Matcher.matches()` is used where a `find()` validator with `$` would admit a trailing LF, U+0085, U+2028 or U+2029; U+FEFF is admitted by `java.util.regex` and refused by ECMA-262 (the test names the exception, not the engine split); the literals `noreply@cuioss.de`, `operator@example.org` and `a..b@c.de` carry no contract comment; Javadoc with backticks instead of `{@code}`. (g) No test for an EMPTY path list with a conflicted index (`JGitOperations.java:659`), no direct test of `CommitPathRefusals` (`isParentConfined`, the swallowed exception); the double indirection `JGitOperations.isRefusedPathText` -> `CommitPathRefusals.isRefusedText` -> `CommitPaths.isRefusedText` may collapse to one entry point.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer

**Done when:** each of the four pieces and each point of deliverable 6 has a test that fails (documents excepted) on the code before it (a failing run or a mutation, by log id); the stored shapes are named in their parity tests; `verify` per module (`pm-core`, `pm-workflow`, `pm-providers/pm-provider-git`) and `verify -Ppre-commit` (its rewrites reviewed and committed) are green, targeted `-Dtest` runs where a full verify exceeds the host's budget; the open Sonar issues of the pull request are fixed or dismissed with a reason.

## Standing Constraints

- **Read every cited line at the head first** and drop what a merge since then settled (name the commit).
- **Work to the Done-when.** Do not stop to ask the operator which part to continue with; ask only a decision the spec marks ask-at-refine (R07), and put it in the outline's question, not in a stop.
- **Nothing here is consumed yet.** The assembly (`plan-marshall-mcp` `02639c1`) has no reference to `CommitSite`, `git.stage-and-commit` or `FootprintBoundary`: build each piece as a library the assembly wires later, with the public API stated in the landing message; add no caller of your own.
- **The deferrals are not this plan's:** the compiled-default table of PLAN-V2-10 (an operator decision), footprint checks (2) pinned build files changed and (3) the main-checkout baseline (`CommitSite` methods of the assembly), Rule 3 on `depends_on_config` (ADR `workflow-verify/004` and `006` wait for documentation issue #71). Name each in the landing message as still owed, with where.
- **Where the project analyses its pull requests with Sonar:** an open Sonar issue is fixed in a pushed commit, or dismissed with a stated reason; the quality gate is green before the pull request is reported ready.
- **No dependency or plugin is added.** No more than 100 files per pull request: cut by deliverable if needed.
- **Tests that can fail.** A new test fails on the code it guards; a test of a refusal asserts the reason, not only the type (CLAUDE.md, R20).

## Claim Labels

All read at `origin/main` `8c7eac6f` by the orchestrator from the Open Defect of the ledger (its re-reads of 2026-11-10 and 2026-11-14) and a `git grep` of 2026-11-23; the outline re-reads each before it acts.

- OBSERVED: `ChangeLedgerRecord.Kind` has the five kinds `build`, `change`, `job`, `task_diff`, `baseline_red` (`pm-core/src/main/java/de/planmarshall/core/store/ChangeLedgerRecord.java:73-83`); `freshness_reconcile` is named in `status.schema.json`, `WorkflowStatusCodec.java` and prose, and no code writes it; `footprint_violation` is in `OutcomeReason.MAPPABLE_CONDITION_KINDS` (`:38`) and `WorkflowStatus.CONDITION_KINDS` (`:124`).
- OBSERVED: the change-set cap is specified (`timeouts/01-job-budgets-and-waits.adoc:536-537`); no commit attempt counter exists in any `src/main`.
- LEAD (unverified): the second cap per identity (`state-catalogue/05-git-commit-and-push.adoc:87`, "both caps left") is the same counter keyed differently; the outline reads the state-catalogue row before specifying.

## Expected Surface

Specific files (R19; the outline adds the files the re-read names):

- OBSERVED (`pm-core`): `pm-core/src/main/java/de/planmarshall/core/store/ChangeLedgerRecord.java`, `pm-core/src/main/java/de/planmarshall/core/store/ChangeLedgerCodec.java`, `pm-core/src/main/resources/de/planmarshall/core/store/change-ledger-record.schema.json`, `pm-core/src/main/java/de/planmarshall/core/store/WorkflowStatusCodec.java`, `pm-core/src/main/java/de/planmarshall/core/store/WorkflowStatus.java`, `pm-core/src/main/resources/de/planmarshall/core/store/status.schema.json`, `pm-core/src/main/java/de/planmarshall/core/spi/OutcomeReason.java`, `pm-core/src/test/java/de/planmarshall/core/store/ChangeLedgerTest.java`, `pm-core/src/test/java/de/planmarshall/core/store/ChangeLedgerSchemaParityTest.java`, `pm-core/src/test/java/de/planmarshall/core/store/ChangeLedgerRecordBuilderContractTest.java`, `pm-core/src/test/java/de/planmarshall/core/store/WorkflowStatusCodecTest.java`, `pm-core/src/test/java/de/planmarshall/core/spi/OutcomeReasonTest.java`, `pm-core/doc/developer/core-store.adoc`.
- OBSERVED (`pm-provider-git`): `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/primitive/FootprintBoundary.java`, `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/primitive/GitStageAndCommitPrimitive.java`, `pm-providers/pm-provider-git/doc/developer/provider-git-primitive.adoc`.
- OBSERVED (specification and watch, read and corrected where the code makes a statement false): `doc/specification/timeouts/01-job-budgets-and-waits.adoc`, `doc/specification/state-catalogue/05-git-commit-and-push.adoc`, `doc/implementation-watch/workflow-dsl.adoc`, `doc/implementation-watch/phase-workflows.adoc`, `doc/Traceability.adoc`.
- HYPOTHESIS (new): the attempt counter class and its test under `pm-core/src/main/java/de/planmarshall/core/store/` or `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/primitive/` (the place is the outline's), and one ADR per decision under `pm-core/doc/adr/core-store/` and the primitive's ADR directory.
- OBSERVED (`pm-provider-git`, added for deliverable 6 on 2026-11-23, merged by #576): `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/CommitPathRefusals.java`, `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/JGitOperations.java`, `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/primitive/StagingSets.java`, `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/primitive/CommitSite.java`, `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/JGitOperationsTest.java`, `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/JGitOperationsPathTest.java`, `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/primitive/CommitTrailerSchemaParityTest.java`, `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/primitive/GitStageAndCommitPrimitiveDeclarationsTest.java`, `pm-providers/pm-provider-git/doc/adr/provider-git-primitive/008-the-staging-set-refuses-what-the-commit-refuses.adoc`, `pm-providers/pm-provider-git/doc/adr/provider-git-primitive/009-an-absent-message-is-the-primitives-clean-tree-route.adoc`, `doc/implementation-watch/store-schemas.adoc`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-20 (landed, #545), PLAN-V2-169 (#568, landed), PLAN-V2-10 (shipped, #495: it pins only whether a default exists).
- Overlaps with: PLAN-V2-172 (shipped, #576): read `origin/main` for its merged files; PLAN-V2-161 (staged): `doc/Traceability.adoc` and `WorkflowStatusCodec`-adjacent items; every staged plan by guide, ADR and Traceability file: the gate decides at emit.
- Sequence: PLAN-V2-172 merged (#576, `3eb66471`, 2026-11-23); the surface of `pm-provider-git` is free of running plans.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-176-commit-unit-remainder.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Carried Open Items

94. **The remainder of the commit unit** (the Open Defect of 2026-10-29, re-read 2026-11-10, 2026-11-14 and 2026-11-23; the operator's decision of 2026-11-14 issues pass Q10 answer 1, and the answer 1 of `handle-open` 2026-11-23): deliverables 1 to 4 carry the pieces that live in this repository; the compiled-default table, footprint checks (2) and (3) and Rule 3 are owed elsewhere (named in the Standing Constraints).
95. **The residue of the landing of PLAN-V2-172** (`landings/PLAN-V2-172.md`, carried 2026-11-23; merged as #576 `3eb66471`; read by the analysis leaf at `3eb66471`; no blocker, no major): deliverable 6 carries each point (a) to (g); the recorded-not-fixed ones are the assembly Watch and the CLI porcelain form of a modified submodule.

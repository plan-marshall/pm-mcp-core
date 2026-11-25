# PLAN-V2-185: The residue of the commit unit's third closeout: a caller rule that contradicts the catalogue and a sibling left on the old prose, a licensing reading three statements still hold, a locale vector at three test sites and the pins the host-path residual lacks

epic: pm-mcp-core-m2-m4-v2
workstream: WS-01
origin: the three-layer analysis of the landing of PLAN-V2-182 (`landings/PLAN-V2-182.md`, #604, merge `4c6fb31a`), 2026-11-25, and the operator's rule that leftovers go into a staged plan (none of the staged plans names `CommitAttemptCaps`, `CommitAttemptCounter`, ADR `core-store/087`, `088` or `089`, `FreshnessReconcileTest` or `ChangeLedgerCommitRowTest`: PLAN-V2-125 and 126 own the test conformance of other classes).
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-185-commit-unit-caps-rule-and-licensing-residue.md` and is queued as one row file, `queue/PLAN-V2-185.json`,
> in the epic ledger (lane `runbook`: the operator runs it with the runbook lane). The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R05, R06, R08, R10, R11, R13, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R07: nothing is ask-at-refine; R09: no confinement; R12: no message is added; R13: no stored format changes; R17 is lifted, the links check is not a requirement, operator 2026-11-23.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-store.adoc` (exists; corrected statements) and `pm-providers/pm-provider-git/doc/developer/provider-git-primitive.adoc` (a corrected statement only if a deliverable changes one); ADR directories: `pm-core/doc/adr/core-store/` and `pm-providers/pm-provider-git/doc/adr/provider-git-primitive/` (corrections edit the existing ADR; a new ADR only for a decision the outline cannot defer, the next free number at `origin/main`, R11); layout: workstream WS-01.
- R12 — none (no message is added).

**Read the skills first (R20).** `java-core`, `java-null-safety`, `javadoc`, `junit-core` and `cui-testing` of `/home/oliver/git/plan-marshall/marketplace/bundles/{pm-dev-java,pm-dev-java-cui}/skills/`; say in the first progress note which you read. R20 applies to every test line this plan touches.

## Objective

PLAN-V2-182 closed the commit unit's third residue. Its analysis found no blocker and no major. It found a caller rule for the commit attempt caps that says two things at once and cites two pins that do not pin it, a sibling Javadoc left on the old prose, a reading of "licensing" that three statements still hold against the design record the same plan corrected, the same default-locale vector at the test sites the plan fixed in one, and two shapes of the host-path residual that no case states. This plan closes those points and no others.

## Deliverables

All file and line references come from the landing analysis at `4c6fb31a` and are re-read at the head of `main` before editing; drop what a merge since then settled and name the commit.

1. **The caps caller rule, said once (carried item 105 a; tests first only if a predicate is added).** (a) `CommitAttemptCaps.java:27-34`, ADR `core-store/087:44-53` and `core-store.adoc:335-341` say a caller reads both predicates after a `hook_failed` outcome and then that "each counting event reads its own cap — the change-set cap at a failed commit attempt, the frame cap at a `git.commit-fix` task": the catalogue has no frame-cap read at the fix task (`state-catalogue/05:94` routes `fixed` to `git.stage-and-commit` unconditionally; the only guard is the `hook_failed` row, `05:87`). State the rule once and as the catalogue has it: one read of `changeSetCapSpent() || frameCapSpent()` after a `hook_failed` outcome of `git.stage-and-commit`, and counting a fix task reads nothing. (b) `CommitAttemptCounter.java:27-33` carries the old prose beside it: bring its sentences in line with (a) so the two classes of one package say the same. (c) Say what pins the rule: `CommitAttemptCounterTest.Hazard` (`:130-180`) asserts the two predicates one at a time and nothing evaluates the disjunction or the read after `hook_failed`, and the catalogue row is specification, not a test. Either say that in the statement ("stated, not enforced; the arms are pinned one at a time"), or give the two events named predicates with a test that fails without them (an API addition: only with a failing-first test and the outline's word; nothing in the assembly consumes the counters, so no caller is added).
   - Skills: pm-dev-java:java-core, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
2. **The licensing reading and the statements around it (carried item 105 b; statements only).** ADR `core-store/088` now says rows keyed to the pre-commit tree no longer match after the commit, so only a new `build` or `baseline_red` row, an `exempt` verdict or the stamp licenses the push. Three statements still read the pre-commit build row as the licence: `core-store.adoc:1328-1329` ("the pair that licensed the push"), ADR `088:66-67` ("the licensing build row's digest") and `state-catalogue/05:110` ("the licensing build row's digest"): say "the build row the digest equals" or what the catalogue's freshness verb reads, as the design record does. Also: ADR `core-store/089:34` ("the schema file is not changed") is stale since #604 edited the `description`; the Consequences of ADR `provider-git-primitive/011` (`:59-62`) do not name the refusal cases #604 added (a drive path, a UNC path, a bracket path behind an introducer, a control character); `state-catalogue/05:86` introduces the commit row before the sentence that appends it and names it twice; ADR `provider-git-primitive/008:19-20` keeps a short dangling line after the re-wrap (`L3-2, L3-7, L3-8). The`); the `CommitTrailerSchemaParityTest` Javadoc (`:47-50`) keeps ragged short lines from the re-wrap and still names the epic id (drop the id if no statement needs it). No schema block changes (R10).
   - Skills: pm-documents:ref-asciidoc, pm-dev-java:javadoc
   - Persona: documenter
3. **The locale vector and the host-path pins (carried item 105 c; tests first where behaviour changes).** (a) `FreshnessReconcileTest.java:37` and `ChangeLedgerCommitRowTest.java:47` lower-case a generated string with the default locale, the vector `WorkflowStatusSchemaParityTest.stamp()` lost in #604: lower-case with `Locale.ROOT`, or draw a lower-case generator; check `CommitPathRefusalsTest.java:39` and the other `toLowerCase()` sites of the two touched modules' tests at the outline (the analysis named that line and the orchestrator's grep did not find a bare call there: drop it if absent, and name the commit). (b) `GitStageAndCommitPrimitiveDeclarationsTest` (about `:196-233`): add a case that pins the drive and UNC shapes glued behind a name character (`refused xC:\Users\plan`; `DRIVE_PATH` and `UNC_PATH` share `HOST_PREFIX`, `GitStageAndCommitPrimitive.java:158`, `:165`) with the reason asserted, and a relative name that starts with a supplementary letter (the 182 cases are ASCII plus one emoji); if a case shows a leak that the Javadoc does not state, state it there and in ADR `provider-git-primitive/011`, or close it only with the outline's word and a failing-first case. Confirm that `{@link #modelDetail}` (`GitStageAndCommitPrimitive.java:74`, a package-private target from a public class doc) is accepted by the javadoc run, or reword it.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: module-tester

**Done when:** the statements of deliverables 1 and 2 say what the catalogue and the code do and agree with each other across `CommitAttemptCaps`, `CommitAttemptCounter`, ADR 087, 088, 089 and `core-store.adoc`; any predicate added by deliverable 1(c) has a test that fails without it (a failing run or a mutation, by log id), otherwise the statement says the rule is not enforced; the test sites of deliverable 3(a) no longer depend on the default locale; the new cases of 3(b) assert the reason and pass, or a leak they show is stated; `verify` per module (`pm-core`, `pm-providers/pm-provider-git`) and `verify -Ppre-commit` (its rewrites reviewed and committed; formatter-only drift in a file this plan does not otherwise touch is not shipped) are green, targeted `-Dtest` runs where a full verify exceeds the host's budget; the open Sonar issues of the pull request are fixed or dismissed with a reason.

## Standing Constraints

- **Read every cited line at the head first.** The lines of a carried item are those of the landing analysis, read at `4c6fb31a`; re-read each at `origin/main` before editing and drop what a merge since then settled (name the commit). `state-catalogue/05`, `store-schemas/04` and `03-q-gate` moved on `main` after #604 (#607): re-read them, do not trust a line number.
- **Work to the Done-when.** Do not stop to ask the operator which part to continue with.
- **Every carried item gets an outcome** in the landing message and the pull request: adopted into a deliverable, split off to a NAMED existing plan, or dropped, each with its reason.
- **No change of what is refused or stored beyond what the items state.** The format versions stay as the operator decided them; the host-path pattern's direction stays fail-closed.
- **Nothing here is consumed yet.** The assembly (`plan-marshall-mcp`) has no reference to `CommitSite`, `git.stage-and-commit`, the counters or `exhausted`: add no caller of your own.
- **Where the project analyses its pull requests with Sonar:** an open Sonar issue is fixed in a pushed commit, or dismissed with a stated reason; the quality gate is green before the pull request is reported ready.
- **No dependency or plugin is added.** No more than 100 files per pull request.
- **Tests that can fail.** A new test fails on the code it guards (a failing run or a mutation, by log id); a test of a refusal asserts the reason, not only the type (CLAUDE.md, R20).
- **Do not repeat the lane's claims of PLAN-V2-182 as facts:** the analysis did not re-run its failing runs or its counts; the pattern verdicts are hand traces, not runs.

## Claim Labels

All lines read at `origin/main` `4c6fb31a` (the merge of #604) by the analysis leaf; the orchestrator re-read `CommitAttemptCaps.java:24-36`, `CommitAttemptCounter.java:24-36`, `state-catalogue/05:84-96`, `FreshnessReconcileTest.java:37`, `ChangeLedgerCommitRowTest.java:47`, `core-store.adoc:1325-1331` and ADR `provider-git-primitive/008:15-22`. The outline re-reads each before it acts.

- OBSERVED: the caps Javadoc says the frame cap is read "at a `git.commit-fix` task" (`CommitAttemptCaps.java:30-32`) while `state-catalogue/05:94` routes `fixed` to `git.stage-and-commit` with no cap read — read at those lines
- OBSERVED: `CommitAttemptCounter.java:27-33` still says "Both caps are read at the one event … the counter carries no combined predicate" — read at those lines
- OBSERVED: `core-store.adoc:1328-1329`, ADR `088:66-67` and `state-catalogue/05:110` use "licensing" for the pre-commit build row
- OBSERVED: `FreshnessReconcileTest.java:37` and `ChangeLedgerCommitRowTest.java:47` call `toLowerCase()` without a locale — read at those lines
- HYPOTHESIS: `CommitAttemptCounterTest.Hazard` pins no combined read — confirm/refute at `CommitAttemptCounterTest.java:130-180` (verify-at-outline)
- HYPOTHESIS: `CommitPathRefusalsTest.java:39` has a bare `toLowerCase()` — refute/confirm at that line (the orchestrator's grep found none)
- HYPOTHESIS: a drive or UNC path glued behind a name character, and a relative name starting with a supplementary letter, behave as the 182 emulation says — confirm/refute by the new cases (verify-at-outline)

## Expected Surface

Specific files (R19; the outline adds the files the re-read names):

- OBSERVED (`pm-core`): `pm-core/src/main/java/de/planmarshall/core/store/CommitAttemptCaps.java`, `pm-core/src/main/java/de/planmarshall/core/store/CommitAttemptCounter.java`, `pm-core/src/test/java/de/planmarshall/core/store/CommitAttemptCounterTest.java` (only if a predicate is added or the pin is reworded), `pm-core/src/test/java/de/planmarshall/core/store/FreshnessReconcileTest.java`, `pm-core/src/test/java/de/planmarshall/core/store/ChangeLedgerCommitRowTest.java`, `pm-core/doc/developer/core-store.adoc`, `pm-core/doc/adr/core-store/087-the-commit-attempt-caps-are-a-member-of-the-workflow-state.adoc`, `pm-core/doc/adr/core-store/088-a-commit-is-a-kind-of-the-change-ledger.adoc`, `pm-core/doc/adr/core-store/089-*.adoc`.
- OBSERVED (`pm-providers/pm-provider-git`): `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/primitive/GitStageAndCommitPrimitive.java` (a Javadoc line only), `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/primitive/GitStageAndCommitPrimitiveDeclarationsTest.java`, `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/primitive/CommitTrailerSchemaParityTest.java`, `pm-providers/pm-provider-git/doc/adr/provider-git-primitive/008-the-staging-set-and-the-commit-share-the-path-rule.adoc`, `pm-providers/pm-provider-git/doc/adr/provider-git-primitive/011-the-host-path-introducer-is-the-complement-of-the-path-name-characters.adoc`.
- OBSERVED (`doc`): `doc/specification/state-catalogue/05-git-commit-and-push.adoc`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-182 (shipped, #604).
- Overlaps with (class level): PLAN-V2-125 and 126 (test conformance of the provider-git and core tests: they run after this plan or rebase over it), PLAN-V2-141 (the ADR index), PLAN-V2-78 (`WorkflowStatusSchemaParityTest`; this plan does not touch it), PLAN-V2-161 and 149 (`store-schemas`; this plan does not touch it) and every staged plan by guide, ADR and Traceability file: the gate decides at emit; one shared file does not block.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-185-commit-unit-caps-rule-and-licensing-residue.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Carried Open Items

105. **The residue of the landing of PLAN-V2-182** (`landings/PLAN-V2-182.md`, carried 2026-11-25; merged as #604 `4c6fb31a`; read by the analysis leaf; no blocker, no major): deliverables 1 to 3 carry each point: (a) the caps caller rule, the sibling Javadoc and the pin claim (182 L3-1, L3-2, L3-3), (b) the licensing reading and the stale or clumsy statements (182 L3-4, L3-5, L3-7, L3-8, L3-11), (c) the locale vector and the host-path pins (182 L3-6, L3-9, L3-10); recorded, not fixed: the lane's counts of runs and logs (`stated_not_verified`), the runbook-lane `unknown` figures (an open record, owner E27), and the items PLAN-V2-182 already listed as recorded (formatter drift of `FreshnessReconcile` and its test, the three widened `throws` clauses, files outside the spec's surface, `membersEqualSchema` `@Disabled`, the compiled-default table (PLAN-V2-10), footprint checks (2) and (3) and the end-to-end breach test, Rule 3 on `depends_on_config` (documentation issue #71), the CLI variant of the commit contract).

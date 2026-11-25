# Landing Analysis: PLAN-V2-185 — The commit-unit caps rule, the licensing reading and the host-path pins, residue closeout

epic: pm-mcp-core-m2-m4-v2
workstream: WS-01
pr: #613 (https://github.com/plan-marshall/pm-mcp-core/pull/613), merge `3ba79c4f96dab1f6954cbaf2b5a7a145f8d4556e` (2026-11-25T16:39:22Z), 16 files (+149/-89)

> Landing record for one shipped plan (runbook lane, opencode, `deepseek-v4.1-flash/high`). Written by the `analyze` verb of the
> thirty-seventh run (2026-11-25, second analyze) from the inbox message `rb-v2-185-commit-unit-caps-rule-and-licensing-residue-002.md`
> (revision 0), corroborated against git (`git show --stat 3ba79c4f`), the PR (`ci pr view`: merged, merge commit `3ba79c4f`) and the
> merged text and code, read by one read-only level-3 leaf and re-read by the orchestrator. Message `-001` (the earlier partial
> landing, PR open) is superseded by `-002` and is archived with it.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 the caps caller rule, said once | shipped-modified: said once, but the new sentence contradicts the rule inside the same paragraph (L3-1) | `CommitAttemptCaps.java:27-36`, `CommitAttemptCounter.java:27-34`, ADR core-store/087:44-54, `core-store.adoc:336-342`; `CommitAttemptCounterTest.java:130-180` pins each arm of the disjunction alone (borne out) |
| D2 the licensing reading and the statements around it | shipped-as-specified in the commit-row / digest / verdict reading; the "future writer" statements contradict "superseded" (L3-3) | ADR 088, ADR 089, `FreshnessReconcile.java:18-27`, catalogue `05` row 110, `ChangeLedgerCommitRowTest`; the reading agrees with the catalogue and with `store-schemas/04:153` |
| D3 the locale vector and the host-path pins | shipped-as-specified, one hardening with no failing control, the leak statement too narrow (L3-4) | `Locale.ROOT` at three sites; `GitStageAndCommitPrimitiveDeclarationsTest` (two new `@ParameterizedTest`s of three values); `HOST_PREFIX` and the production pattern are unchanged, Javadoc only |

## Three-Layer Analysis

- **Dispatch:** one read-only leaf per landing (`execution-context-level-3`, `orchestrator.analyze` surface), 23 tool calls, no write. Reason: the landing is mostly statements about code, and each statement has to be tested against the code (the stamp's writers and readers, the pattern, the catalogue rows).
- **Orchestrator re-read** at `3ba79c4f`: `CommitAttemptCaps.java:25-38`, `CommitAttemptCounter.java:25-36`, `FreshnessReconcile.java:16-30`, ADR core-store/089 lines 28, 42, 56-57, `GitStageAndCommitPrimitive.java:335-346`, and the line lengths of `core-store.adoc` (121 at 357 and 373, 153 at 377, 140 at 1337, 122 at 1342). Every point recorded below as `checked` was re-read; the JDK lookbehind mechanism of L3-4 and every run, job id and mutation log are `not shown by reading`.
- **Verdict:** layer 1 clean; layer 2 clean apart from not-shown runs; layer 3 findings — **no blocker, no major**, five minors.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence |
|---------|-------|---------|----------|----------|
| L1-1 | "state-catalogue/05:94 routes `fixed` to `git.stage-and-commit` with no cap read" | checked | confirms | the `git.commit-fix` row: only the `hook_failed` row carries the cap guard |
| L1-2 | HYPOTHESIS "`CommitPathRefusalsTest.java:39` has a bare `toLowerCase()`" | checked | confirms | the diff removes `Generators.letterStrings(1, 12).next().toLowerCase() + ".txt"` (the orchestrator's earlier grep was wrong); only `toUpperCase()` sites remain, `FindingSchemaParityTest:71` the one that could carry the same vector |
| L1-3 | "the commit row is named twice, before the sentence that appends it" | checked | confirms | after the diff `kind = "commit"` appears once |

The numbered claims of this spec's `## Claim Labels` follow the section's own form; no finding names a claim the parser addresses, so no corpus verdict is persisted from this layer (the persisted verdicts of earlier cleanups stand).

**Layer 1 verdict:** clean.

### Layer 2 — Implemented

| Finding | Deliverable | What | Verdict |
|---------|-------------|------|---------|
| L2-1 | D2 | `FreshnessReconcile.java` and the `FreshnessReconcileTest` class Javadoc are outside the Expected Surface and named by no deliverable; the Done-when ("statements agree across … ADR 089") explains the first, because its old Javadoc contradicted the revised ADR. `CommitPathRefusalsTest` is outside the surface list but named in D3(a). The lane's R19 delta is right for the surface list only. | checked, info |
| L2-2 | D1 | "Hazard pins each arm one at a time" is borne out: `frameCapBoundsAResetChangeSet` and `frameCapBoundsATreeOnlyReset` assert `assertFalse(changeSetCapSpent())` beside `assertTrue(frameCapSpent())`, `changeSetAloneEndsTheLoop` the reverse; no test has both spent or reads after `hook_failed`; the line citations match the hunks. | checked, confirms |
| L2-3 | D3(a) | "a Turkish-default run passes" is not evidence: the lane says no failing run exists because the generator lowers identically, so the run passes on the old code too. Acceptable for order `none`; the job ids and mutation logs live under `.plan/` and cannot be read. | not_shown_by_reading, minor |

**Layer 2 verdict:** clean; not shown by reading — 1 (L2-3).

### Layer 3 — Correct

| Defect | File:line | Triggering reading | Effect | Severity |
|--------|-----------|--------------------|--------|----------|
| L3-1 | `CommitAttemptCaps.java:30-32`, `CommitAttemptCounter.java:30-31`, ADR core-store/087:49, `core-store.adoc:340` | "the change-set cap [is counted] at a failed commit attempt … but a counting event reads nothing" | A failed commit attempt is the `hook_failed` outcome, the one event at which the same paragraph says both predicates are read, so the generalisation contradicts the rule in four places. The spec's wording was narrower (counting a fix task reads nothing). | minor |
| L3-2 | `CommitAttemptCounter.java:31-33` | "…so the frame cap being spent after the third task cannot end the loop before the fourth attempt runs … no combined predicate that would let a caller ask elsewhere" | Stated unconditionally beside "stated, not enforced": the public `frameCapSpent()` can be read anywhere, and the absence of a combined predicate does not stop a caller. Holds only if the caller follows the rule. | minor |
| L3-3 | `FreshnessReconcile.java:24-27`, ADR core-store/089:28 and :56-57, `core-store.adoc:356,372` | The same text says the rule is superseded and that "a future writer will name the commit", "a writer must confirm a successful build exists", "the writer must write the stamp in the same status transaction", "supplies the typed carrier a future writer will read" | A superseded rule has no future writer. "No writer and no reader are wired" is also ambiguous: `WorkflowStatusCodec` (`:273` write, `:580` read) does read and write the member. Checked by grep: no gate reads `freshnessReconcile()`. | minor |
| L3-4 | `GitStageAndCommitPrimitive.java:339-344`, ADR provider-git-primitive/011:44-48 and :71 | "a segment that is such a letter alone becomes the code" | The stated mechanism (a one-unit lookbehind that cannot consume the surrogate pair) applies to any segment that ENDS in a supplementary letter, not only to one that is the letter alone; the three new cases are all "letter alone", and neither the ends-with case nor the "longer segment keeps its name" case is pinned. The JDK mechanism (`NotBehind` rather than `NotBehindS`) is `not shown by reading`; no run was made. | minor, not shown |
| L3-5 | `pm-core/doc/developer/core-store.adoc:357,373,377,1337,1342` | The re-wrap | Lines of 121, 121, 153, 140 and 122 characters and short ragged lines nearby: the same re-wrap residue D2 meant to remove. | minor |

**Layer 3 not shown by reading:** whether each glued-shape test fails without `HOST_PREFIX` (a run), the supplementary-letter mechanism (JDK semantics), the lane's job and mutation logs.

**Layer 3 verdict:** findings — 5 (all minor); no blocker, no major.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md § Code Standards | Assertion messages of 20 to 60 characters | conforms | new messages 33–56 characters |
| CLAUDE.md | JUnit 5, `@ParameterizedTest` for 3+ variants, no AAA comments | conforms | both new tests are `@ParameterizedTest` with three values |
| CLAUDE.md | A literal stays only where it is the contract, with a one-line comment | conforms (cosmetic) | the comments naming ADR 011 run to two lines |
| CLAUDE.md | No dependency or plugin; no more than 100 files; R10 schema block untouched | conforms | 16 files, no pom, no resource file |
| spec Standing Constraints | No change of what is refused or stored | conforms | `GitStageAndCommitPrimitive` changed Javadoc only |
| spec Standing Constraints | Tests that can fail; a refusal test asserts the reason | not_shown_by_reading | the new glued cases assert outcome, reason and detail; failing without `HOST_PREFIX` needs a run |

## Metrics and Anomalies

- Tokens `unknown`, `plugin_version` `unknown`, `sonar_open_new_issues` `unknown`: the landing-check reports `complete: false` on these three keys (`unknown` reads as missing at every key). The first two are the known recurrence (Open Defect 7); the third is the honest value after the merge (below). The landing is reconciled in full.
- Anomalies: no `pull_request` run was created for the merge commit `7741bae6`; the lane asked the operator, who selected "enqueue now, let the merge queue test the head"; the `merge_group` run `45656301215` succeeded. `ci pr update-branch` was a no-op. Both are GitHub/QoS observations recorded here; they are not this plan's.
- The pre-commit gate rewrote three files of #594 (`TaskQueueSchemaParityTest`, `ServedModelsTest`, `ServedModelWatchTest`) twice; the lane withheld the rewrites on this plan's branch. PLAN-V2-126's D1 (#612) carried them as `6fc5abc0` (see `landings/PLAN-V2-126.md`).

## Routing and Merge Behavior

- Review: CodeRabbit (required) reviewed `0cf2dfd4` after a quota refusal and a re-trigger; two inline minors, both handled in `ccbe33a8`. Re-read through `review-gate --pr-number 613` at 2026-11-25 after the merge (16:39:22Z): `unresolved_review_threads: 0` (matches), `review_body_findings_total: 2` (matches), `review_body_findings_handled` re-reads `0` because the dispositions live in the lane's progress file, so the `2` is **not re-verified**, not a difference. No record post-dates the merge. Sourcery refused on quota.
- CI/merge: merged through the merge queue; the merge-group run is the CI the head was merged on.
- Sonar: the lane measured `0` at `ccbe33a8`; the read-side re-read answers `undecidable` after the merge (the PR head ref is gone). The landing's `unknown` is therefore honest; the `0` stays the lane's measured claim at the content head, not re-verified.

## Reconciliation Actions

- [x] row `status` → `shipped`, `pr` `#613`, `landing` `landings/PLAN-V2-185.md`, `plan_marshall_plan_id` `rb-v2-185-commit-unit-caps-rule-and-licensing-residue`
- [x] messages `-001` and `-002` archived by their exact names
- [x] residue placed (below); `epic.md` reconciled; resume anchor and `queue-view.md` regenerated

## Follow-Ups

- **L3-3 and L3-5 and the stale `status.schema.json:365` description** (R10 forbids a block edit, owed to a plan that owns the schema) go into the STAGED PLAN-V2-192: it deletes `FreshnessReconcile`, supersedes ADR core-store/089, adjusts 088 and removes `status.schema.json:363-375` (the stale description is inside it), so the "future writer" statements and the ragged text leave with the thing they describe; recorded in 192's spec as a note so the outline does not re-word them first.
- **L3-1, L3-2 and L3-4 and the two missing pins** (ends-with a supplementary letter; a longer segment that keeps its name) have no staged plan that owns the files: staged as the NEW PLAN-V2-196 `commit-unit-fourth-residue-closeout` (WS-01, runbook, two deliverables). Recommendation to the operator: the commit-unit residue stream ends after 196 (it shrinks each round: 182, 185, 196).
- The pre-enqueue CI gap and the no-op `update-branch` are recorded here only; plan-marshall's lessons inbox is the place for them (the operator decides).

# Landing Analysis: PLAN-V2-182 — The residue of the commit unit's second closeout

epic: pm-mcp-core-m2-m4-v2
workstream: WS-01
pr: #604 (https://github.com/plan-marshall/pm-mcp-core/pull/604), merge `4c6fb31a387b6e167bdb80e97a8b5bd0c21a16ed`, 16 files (+150/-65)

> Landing record for one shipped plan (runbook lane, opencode, `deepseek-v4.1-flash/high`). Written by the `analyze` verb
> of the thirty-sixth run (2026-11-25) from the inbox message `rb-v2-182-commit-unit-third-residue-closeout-001.md`
> (revision 0), corroborated against git (`git show --stat 4c6fb31a`), the PR (`ci pr view`, `ci checks status`: 20 checks,
> overall success, SonarCloud success) and the merged code, read by one read-only level-3 leaf and re-read by the orchestrator.

## Deliverable Fidelity vs Spec

The 16 files are the spec's Expected Surface plus `core-store.adoc` and the provider-git README that the spec's R06 names.
The only production change is comments (`GitStageAndCommitPrimitive.java:72-74`, `:334-340`; `CommitAttemptCaps.java:27-34`)
and one line of data (`status.schema.json:365`, the `description` string only; no parity test reads a `description`, R10 holds).

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1(a) ADR `core-store/088` Context and alternatives re-based, `fresh`/`exempt` as verdicts, pre-commit digest never equals the post-commit one | shipped-as-specified | `088:14-20`, `:33-39`, `:54-59` |
| D1(b) timing in the writer's row and the commit-row bullet | shipped-as-specified | `state-catalogue/05:86`, `store-schemas/04:153`; the anchors exist; no schema block changed |
| D1(c) the dangling apposition | shipped-as-specified | `state-catalogue/05:110` ends "The commit row does not enter the verdict." |
| D2(a) the host-path residual: pin (not close), state the refused relative names | shipped-as-specified (pin branch) | `GitStageAndCommitPrimitive.java:72-74`, `:334-340`; ADR `provider-git-primitive/011:39-46,64-66`; the three kept tests still present |
| D2(b) the missing cases, refusals assert the reason | shipped-as-specified | `GitStageAndCommitPrimitiveDeclarationsTest.java:144-147`, `:184`, `:196-233`; the leaf traced `gluedHostPathStaysAsStated` and `relativeNameEndingInANonPathCharacterIsACode` against the real patterns: both pass |
| D2(c) the class-level tautology | shipped-as-specified | `GitStageAndCommitPrimitive.java:72-74` names the residual |
| D2(d) the `exhausted` contract | partly | the caller rule and the pins are named (`CommitAttemptCaps.java:27-34`, ADR 087 `:44-53`, `core-store.adoc:335-341`), but the rule reads inconsistently and "pins" is overclaimed (L3-1, L3-3) |
| D3(a) the `description` and `03-q-gate:264` | shipped-as-specified | `status.schema.json:365`; `03-q-gate:264`; ADR 089 `:29-31` agrees |
| D3(b) ADR 011 linked, the ADR 008 orphan line removed | shipped-as-specified, one nit | `provider-git-primitive.adoc:377`, `README.adoc:53`; the re-wrap leaves a short line (L3-8) |
| D3(c) the frame-id pins, U+2000, `Locale.ROOT` | shipped-as-specified | `WorkflowStatusSchemaParityTest.java:165-177`, `:110-118`, `:510` |
| D3(d) the `CommitTrailerSchemaParityTest` Javadoc | shipped-as-specified | `CommitTrailerSchemaParityTest.java:47-48`; no `PLAN-V2` or "LEAD" text left |

## Three-Layer Analysis

Read on three layers by one read-only level-3 leaf (`execution-context-level-3`, `orchestrator.analyze`), by reading — no
build, no test run, no write. The pattern verdicts are hand traces of `NAME_CHARACTER` and `HOST_PREFIX`, as the spec's own
LEAD. The orchestrator re-read at the merged head: `CommitAttemptCaps.java:24-36`, `CommitAttemptCounter.java:24-36`,
`state-catalogue/05:84-96` (at `4c6fb31a`), `FreshnessReconcileTest.java:37`, `ChangeLedgerCommitRowTest.java:47`,
`core-store.adoc:1325-1331`, ADR 008 `:15-22` and the "licensing" sites. Taken from the leaf's reading, recorded as such: the
hand traces of the patterns, the assertion-message counts (33 to 58), the `CommitPathRefusalsTest.java:39` site (the
orchestrator's grep of that file found no bare `toLowerCase()`: not confirmed).

- **Dispatch:** one read-only leaf for this landing (one of two landings of this drain, in parallel with the leaf of 184).
- **Verdict:** layer 1 sound (D2(d) partial); layer 2 info only; layer 3 four minors and seven info. No blocker, no major;
  the leaf rates L3-1 "borderline minor/major"; the orchestrator holds it minor: documentation only, nothing consumes the
  code (spec Standing Constraints).

### Layer 1 — Specified

All items met except D2(d), which is partial (L3-1, L3-3). ADR `provider-git-primitive/011` is named in the spec as
`011-the-introducer-…`; the real file is `011-the-host-path-introducer-…` and the links point at the real file (info).

### Layer 2 — Implemented

Every cited line is real at the merge head. "No production behaviour changed" holds: the Java diff in main code is comments
only, the schema diff is one `description`. The parity tests read no `description`. No finding above info.

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `CommitAttemptCaps.java:30-32`; ADR 087 `:47-49`; `core-store.adoc:335-341` | the rewritten caller rule says a caller reads both predicates after `hook_failed`, then that "each counting event reads its own cap — the change-set cap at a failed commit attempt, the frame cap at a `git.commit-fix` task. So the frame cap may be spent after the third task and the loop still ends only at the next failed commit attempt" | the catalogue has no frame-cap read at the fix task: `state-catalogue/05:94` routes `fixed` to `git.stage-and-commit` unconditionally and the only guard is the `hook_failed` row; the "So" does not follow; a non-sequitur replaced by another | minor |
| L3-2 | `CommitAttemptCounter.java:27-33` | the sister Javadoc still carries the old prose ("Both caps are read at the one event … the counter carries no combined predicate that would let a caller ask elsewhere") | it contradicts "each counting event reads its own cap" in the same package; a sibling the spec's surface did not list | minor |
| L3-3 | `CommitAttemptCaps.java:33-34` | "`CommitAttemptCounterTest.Hazard` pins both arms of the disjunction", "the catalogue row pins that guard" | `Hazard` asserts `changeSetCapSpent()` and `frameCapSpent()` separately; nothing evaluates `||` or models the read after `hook_failed`; the catalogue row is specification, not a pin; the rule stays unenforced prose | minor |
| L3-4 | `core-store.adoc:1328-1329`; ADR 088 `:66-67`; `state-catalogue/05:110` | "the pair that licensed the push", "the licensing build row's digest" | ADR 088 now says old tree-keyed rows no longer match after the commit, so only a new build row, an exempt verdict or the stamp licenses the push: three places still use the old reading | minor |
| L3-6 | `FreshnessReconcileTest.java:37` (re-read), `ChangeLedgerCommitRowTest.java:47` (re-read) | `Generators.letterStrings(...).next().toLowerCase()` with the default locale | the same Turkish-locale vector D3(c) fixed in `stamp()` stays in these sites (a third site named by the leaf, `CommitPathRefusalsTest.java:39`, is not confirmed) | minor |
| L3-5 | ADR 089 `:34`; ADR 011 `:59-62` | "the schema file is not changed" is now stale; the Consequences of 011 do not name the new refusal cases | info |
| L3-7 | `state-catalogue/05:86` | the commit row is introduced before the sentence that says it is appended, and named twice | info |
| L3-8 | ADR 008 `:19-20` | the re-wrap leaves a short dangling line (`L3-2, L3-7, L3-8). The`; re-read) | info |
| L3-9 | `GitStageAndCommitPrimitive.java` lookbehind | supplementary letters (e.g. `𝒜/x`) not exercised; `{@link #modelDetail}` from a public class doc to a package-private target not run through javadoc | info (not checked) |
| L3-10 | `GitStageAndCommitPrimitive.java:158`, `:165`; test `:197-201` | `DRIVE_PATH` and `UNC_PATH` share `HOST_PREFIX`, so `refused xC:\Users\plan` also leaks; the Javadoc says it only generically; the pinned cases are POSIX shapes | info |
| L3-11 | `CommitTrailerSchemaParityTest.java:47-50` | the epic id still named (pre-existing); ragged re-wrap | info |

Boundaries checked and clean: the new `store-schemas/04:153` bullet and its anchors; the timing in `05:86`; `Locale.ROOT` in
`stamp()` (`:510`, import `:35`); the frame-id pins; every new assertion message is 33 to 58 characters.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md § Code Standards | assertion messages 20 to 60 characters | conforms | 33 to 58 (leaf count) |
| CLAUDE.md § Code Standards | a refusal test asserts the reason | conforms | `:152-157`, `:227-232` |
| CLAUDE.md § Code Standards | Javadoc, ADR and guide say what the code does | deviates (minor) | L3-1, L3-2, L3-3, L3-4 |
| standing rules R10 | only the `description` string of the schema changes | conforms | one line, `status.schema.json:365` |
| CLAUDE.md § Dependencies | no dependency or plugin, 100 files | conforms | 16 files |

## Metrics and Anomalies

- Tokens: `unknown`; `plugin_version` `unknown`: the known recurrence of Open Defect 7 (owner E27), no new defect.
- Fifteen builds, all `daemon_longpoll`; one targeted run answered `indeterminate` / `test_selector_matched_nothing` (a wrong
  selector with `-am`, re-run green); the lane asked the operator once (self-review round limit; answer: proceed to the PR).
  Counts and job ids are the lane's statement, not re-run.

## Routing and Merge Behavior

- Review: CodeRabbit (required) reviewed `52394552` with three inline findings, all handled in `0259ca60`; Sourcery
  (optional) refused on its quota until 2026-11-26. Counts claimed: 0 unresolved threads, 3 review-body findings handled,
  0 Sonar issues (`stated, not verified`; CI re-read: SonarCloud success).
- CI/merge: merged through the merge queue as `4c6fb31a`; 20 checks, overall success.
- Collisions: none observed with the running plans 162, 184 and 126.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#604`; `landing`; `plan_marshall_plan_id` `rb-v2-182-commit-unit-third-residue-closeout`
- [x] landing message `-001` archived by its exact name
- [x] residue staged — see Follow-Ups
- [x] resume anchor updated; `queue-view.md` regenerated

## Follow-Ups

No blocker and no major, so nothing goes to the operator at once. The minors and info points fit no staged plan
(`CommitAttemptCounter`, `CommitAttemptCaps` and ADR 088 are named only by the shipped 179 and 182; 125 and 126 own other
tests). They are carried as item 105 into the NEW staged PLAN-V2-185 `commit-unit-caps-rule-and-licensing-residue` (WS-01,
runbook). Recorded, not fixed: the lane's counts of runs and logs (`stated_not_verified`), the `unknown` figures (owner E27),
the 179 items 182 already listed as recorded (formatter drift, widened `throws`, `membersEqualSchema` `@Disabled`, the
compiled-default table, footprint checks, `depends_on_config`, the CLI variant of the commit contract).

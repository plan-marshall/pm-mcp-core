# Landing Analysis: PLAN-V2-164 — call-bind-closeout

epic: pm-mcp-core-m2-m4-v2
workstream: WS-01
pr: #551 (merge `ac75994b88b9235eccad13bbd574963fff4f34d0`, first parent `4d4ada41`, 40 files, +1948/-100)

> Landing record, runbook-lane form (`rb-v2-164-call-bind-closeout`, harness opencode, model `deepseek-v4.1-flash/high`, effort high, plugin version unknown, executor 0.1.2173, 5 deliverables, standing rules r7). One landing message (`rb-v2-164-call-bind-closeout-001.md`, 2026-11-21T23:17:27Z, `merge_state=merged`). `landing-check`: `complete: false`, missing `total_tokens` and `plugin_version` (both `unknown`, the same two keys as every opencode landing; folded into the standing Open Defect). Self-review `budget-exhausted` (four rounds, the round-4 fix not re-reviewed). No blocker, no major defect.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 the call bind validates the arguments | shipped-as-specified | `undeclared_param` is raised before any declared input resolves (`WorkflowEngine.java:1677-1682`), `missing_param` replaces `input_unbound` for a required input with no source (`:1785`, `missing()` `:1853`), a missing binding context on a required fallback input is `input_refused` / `input.no-context` (`noBindingContext`); the ownership split is ADR `workflow-engine/028`; `InputBindingTest` cases per refusal with a matched control. |
| D2 the tests the documentation cites exist | shipped-modified | Codec refusals, the typed slot reference, `fills`, PathConfinement cases and the weakened types are present. Modified: the typed slot reference changed the persisted header form (see L1-1); the case "a slot bound to two inputs of one call" exists only as the static Rule 6 case, not at bind level (L2-1). |
| D3 Rule 6, the measurement, the reserved words | shipped-modified | Rule 6 models consumption (pairwise, order-independent, fill-free cycle, ADR `workflow-verify/042`); one reserved set `ReservedKeywords.ALL` read by `WorkflowParser:130` and `WorkflowValidator:144` with a parity test against the specification bullet. Modified: the measurement is one reader of one slot (L2-2). |
| D4 the small fixes of #522 | shipped-as-specified | `LessonStore.isUntrusted` (`:808-815`) lists the seven `LessonResolution` variants with no `default`; the dead `InputRefusal` and the misleading reason are gone; the `push_target` column added; the duplicate import was already settled. |
| D5 the three behaviours | shipped-as-specified | Glob characters admitted (premise measured in `JGitOperationsTest`), tab refused in a commit message, edge spaces and trailing dots refused (`PathRules.checkSegments:91-110`); operator answers in ADR `workflow-input/006`. |

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`, `orchestrator.analyze` surface, resolved `level-3` from `plan.effort`); no file written, no build run; the spec was passed as data from a file outside the repository. Reason: reading merged code across the engine, the validator and the input rules is the depth the dispatch rule exists for, and the landing is one of two in this drain.
- **Re-read by the orchestrator at `ac75994b`:** `HeaderJson.java:455-475` (typed object form), `InputValidator.java:388-393`, `WorkflowEngine.java:1819-1830`, `LessonStore.java:806-815`, `PathRules.java:88-112`, `WorkflowValidator.java:1988-2004` and `:2060-2090`, `SlotDominanceCostTest.java:40-75`, and the test names of `WorkflowValidatorTest` `:1280-1600` and `InputBindingTest` `:835-900`. **Not re-read, kept as the leaf's claim (unverified leads):** the `WorkflowEngine` bind rollback line numbers beyond `:1677-1700`, the conformance row on assertion messages, `HeaderJson` `:570` strictness, and the ADR statements about the CLI variant.
- **Verdict:** layer 1 findings (one minor); layer 2 findings (three minor, test gaps); layer 3 findings (minor only, three points not shown by reading). No blocker and no major in any layer.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L1-1 | The Objective says none of the items changes a stored datum; D2 (a plain enum value that starts with `slot.` must not read as a slot reference) can only be met by changing the persisted header form: a slot reference is now written `{"slot": name}`, and a header written before reads the string `slot.x` back as an `EnumValue` (ADR `workflow-dsl/017` admits it, no migration kept) | checked | refutes | `HeaderJson.java:464`, `:565` |
| L1-2 | A slot filled once and read by two calls, or bound to two inputs of one call, verified before and failed at run time with `input_unbound` | checked | confirms | `WorkflowEngine` resolve removes the slot on the first read |
| L1-3 | The `RESERVED_KEYWORDS` of the validator is used (the spec called it unused) | checked | confirms | `WorkflowValidator.java:144` (was `:143`) |

**Layer 1 verdict:** findings — 1 (minor, a spec premise inconsistent with its own deliverable; defensible under the pre-1.0 rule). The two confirming claims belong to a spec that is now shipped, so no `corpus set-verdict` is persisted: the verdict field exists for the prep-ready test of staged specs.

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| D1 | `WorkflowEngine.java:1677-1700`, `:1785`, `:1853` | `InputBindingTest` (refusal cases, matched control); reasons asserted by `String.contains` over the serialized facts (looser than a parsed assertion) | none | checked |
| D2 | `HeaderJson`, `PathConfinement` use | `PlanInstanceHeaderTest`, `WorkflowStatusCodecTest`, `InputBindingTest` | **L2-1** no bind-level test of a slot bound to two inputs of one call: the shape `second = slot.message` exists only in the static Rule 6 case; `InputBindingTest:879` binds two different slots | checked |
| D3 | `WorkflowValidator.java:1988-2090`, `ReservedKeywords` | `WorkflowValidatorTest` "Slot consumption" (seven cases incl. the fill-free cycle), `ReservedKeywordsSpecificationParityTest` | **L2-2** `SlotDominanceCostTest` has k = 1, asserts no bound, and the ADR's 72 to 115 ms is testcase time of parse plus verify; **L2-3** no matched control for two readers on exclusive branches after one fill (admitted only by `dominatedByAny` returning true on an unreachable target); no test that a role named `phase`, `inherit` or `decide` now verifies | checked |
| D4 | `LessonStore.java:808-815` | `LessonStoreUntrustedCitationTest` | none | checked |
| D5 | `PathRules.java:91-110`, `InputValidator.java:390` | `PathRulesTest`, `InputValidatorTest`, `JGitOperationsTest` | the lane named two undeclared surfaces (`workflow-plan.adoc`, `04-ast-node-model.adoc`); the real list is longer: `HeaderJson`, `PlanInstanceHeaderTest`, `09-plan-instance.adoc`, `ReservedKeywords` and its parity test | checked |

**Layer 2 verdict:** findings — 3 (L2-1 to L2-3, all minor test gaps). The lane's narrative otherwise holds against the diff: 40 files, 1 948 insertions, 100 deletions, one commit on the first parent.

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `InputValidator.java:390-393` (`controlInBody`) | `feat: x` then a lone CR then more text | The rule tests the whole message, subject included, and exempts LF and CR; a carriage return that can overwrite a terminal line is admitted while a tab is refused. ADR `workflow-input/006` says the line break is the one control character admitted. Doc and code disagree (the operator's tab decision is implemented). | minor |
| L3-2 | `WorkflowEngine.java:1765-1775` | optional input with `input_fallback`, a configured value for the key, `bindingContext == null` | The configured value is skipped without a diagnostic and the default or no value is bound. A conscious boundary (ADR `workflow-engine/028`); recorded so the assembly's `InputBindingContext` is not assumed optional. | minor |
| L3-3 | `WorkflowValidator.java:1992-2001` | two readers unprotected in both directions | Reported twice, once per direction (later and earlier swapped). Noise only. | minor |

**Layer 3 not shown by reading:** (a) `WorkflowEngine.confined` (`:1821-1830`) tests the staging set on the raw text and discards the canonical path of `Confined`; the symlink-out cases are refused, a symlink inside the worktree to a target inside it is accepted under the link's name; whether that can be abused depends on how the assembly builds the staging set. (b) `PathRules.checkSegments`: NTFS forms `.git::$INDEX_ALLOCATION` and `GIT~1` pass the segment rules, and glob characters would be refused by `PathConfinement` on Windows; only a Windows run shows it. (c) The literal staging of `*`, `?` and `[` rests on the native variant (measured by `JGitOperationsTest`) and on a CLI variant that lives in the assembly; the deletion form (`setUpdate(true)` on a glob-named path) is not in the premise test. (d) A header written before D2 reads as `EnumValue`; whether any persisted header exists is not visible in the repository. The Rule 6 walk itself (cycles, diamonds, declaration order, the round-4 fix `9417e20e`) read as sound; the dominance is over states, not outcomes, as its Javadoc states.

**Layer 3 verdict:** findings — 3 (minor); not shown by reading — 4. No blocker, no major.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md Code Standards | Optional only as a return type | conforms | `Optional.<String>empty()` local at `WorkflowEngine.java:1773`; no Optional parameter or field added |
| CLAUDE.md Code Standards | No generic catch or throw | conforms | no catch added; `IllegalArgumentException` already there |
| CLAUDE.md Code Standards | `@UtilityClass` for static-only classes; `@NullMarked` packages | conforms | `ReservedKeywords` in the existing package `workflow.ast` |
| CLAUDE.md Code Standards | Records of four or more components carry `@Builder` | conforms | no record added or changed |
| CLAUDE.md Code Standards | Logging through CuiLogger and the holders | conforms | no logging added |
| CLAUDE.md Code Standards | JUnit 5, display names, literals only as the contract | conforms | new tests carry display names; contract literals marked |
| CLAUDE.md Code Standards | Assertion messages of 20 to 60 characters | not_shown_by_reading | sampled messages fit; the scanner in `pm-core:tests` guards it and only a run proves it over the diff |
| CLAUDE.md Dependency Rules | No dependency added; module boundaries kept | conforms | no pom file among the 40 changed files |

## Metrics and Anomalies

- Tokens: `unknown` (opencode measures none; recorded as unobserved, not zero).
- Duration: not reported by the landing.
- Anomalies: self-review ended `budget-exhausted` after four rounds with the last fix not re-reviewed (the leaf read that fix as sound); the whole-plan `BROKEN LINKS 0` clause was not met as written (97 cross-repository `pm-mcp-clients` URLs the offline tool cannot verify, 0 relative links broken, `INLINE SCHEMA BLOCKS 0`); the premise of the first operator answer on glob characters was wrong and re-decided on the measured evidence.

## Routing and Merge Behavior

- Review: CodeRabbit (required, quota-limited) reviewed head `9417e20e` with 2 minor body findings; sourcery refused on its budget (optional). Read back against the provider on 2026-11-22 (`runbook_lane review-gate --pr-number 551`): `unresolved_review_threads` 0 (matches), `review_body_findings_total` 2 (matches), `review_body_findings_handled` re-read as 0 by the provider, which cannot see dispositions; the lane's progress file names both findings with their fixing commits (`9ee4544e`, `6e63fced`) but carries no `finding:` lines, so the number 2 is **not re-verified**, not a difference.
- CI/merge: merged through the merge queue as `ac75994b`; the lane merged main into its branch once.
- Sonar: the lane reports `sonar_open_new_issues=0`, quality gate green, 95.9 % new-code coverage at `9b483652`; the read-back answered `count_status: undecidable` (`head_revision_unavailable` on a merged pull request, analysed revision `9b483652`), so the figure is **not re-verified**, and not `0` by this analysis.
- Parallelization: the merge made the pull requests #545 (PLAN-V2-20) and #550 (PLAN-V2-163) conflicting (`dirty`) against the new main on 2026-11-22; their lanes rebase them. The cross-check had predicted 8 shared files with 163 and 4 with 20, which the operator accepted at the emit.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-V2-164 --status shipped`
- [x] row `pr` stamped — `#551`
- [x] row `landing` stamped — `landings/PLAN-V2-164.md`
- [x] row `plan_marshall_plan_id` stamped — `rb-v2-164-call-bind-closeout` (from the sender)
- [x] epic.md narrative reconciled (Decisions line, Watch for the landing, recurrence on the incomplete-landing Open Defect)
- [x] residue folded into PLAN-V2-161 item 79 with its Expected Surface updated in the same edit (89 resolved entries, `declarative`)
- [x] resume anchor updated and `queue-view.md` regenerated

## Follow-Ups

- Layer-2 and layer-3 residue (L1-1, L2-1 to L2-3, L3-1 to L3-3 and the four points not shown by reading): PLAN-V2-161 item 79, staged; none is a blocker, so nothing is reported as a fix plan.
- Owed by the milestone exit (the lane's residue): the concept and user documentation and the roadmap check-off; PLAN-V2-161 already owes carried item 77.
- Undeclared surfaces (R19): the spec's Expected Surface under-declared by roughly seven files; the gate's reading for a later pairing uses the corrected list above.

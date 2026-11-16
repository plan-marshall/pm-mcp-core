# Landing Analysis: PLAN-V2-04 — A unit declares its inputs and what it reads in its frontmatter

epic: pm-mcp-core-m2-m4-v2
workstream: WS-01
pr: #460 (merge `998f08ff`, 63 files, +2537/-373, `merged` through the merge queue at 2026-11-16T12:22:18Z)

> Landing record written by the `analyze` drain of 2026-11-16 from the message `rb-v2-04-unit-input-interface-001.md` of the runbook lane (harness opencode, model deepseek-v4.1-flash/high, effort high). `landing-check` reports `complete: false`: `total_tokens` and `plugin_version` are `unknown`, the known defect E27 (recurrence, no new entry). Every point marked re-read was read again by the orchestrator at its `file:line` at `998f08ff`, not taken from the lane or the leaf.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|---|---|---|
| D1: the frontmatter keys (`reads_config`, `reads_fact`, `input` with four sub-keys, caps, closed value forms, CRLF twin, round trip) | shipped-as-specified, one Done-when clause names a method that does not exist | reader caps at `UnitFrontmatterReader.java:119-125`; the Done-when's `constantsAreTheSpecificationValues` is in neither tree (PLAN-V2-120 deleted it), the real `constantsAreReadFromTheLimitsResource` (`UnitFrontmatterReaderTest.java:944`) is byte-unchanged and `pinsTheSpecificationNumbers` (`:802`) pins 16/32/500 (leaf read) |
| D2: body `param` removed, `ParamDecl` deleted, inputs read from the frontmatter | shipped-modified | `git grep ParamDecl 998f08ff -- pm-workflow` finds one prose mention in ADR `workflow-ast/001:21` (re-read) and no class; the parser refuses a body `param` naming the frontmatter (`WorkflowParser.java:203-206`, leaf read); three `param mode : int` lines remain as the inputs of the refusal cases (`WorkflowParserRefusalTest.java:319,354,356`), and the migration changed assertions (nine param-shape refusal cases replaced by two, fixture counts and line numbers moved, `duplicate-param.pmwf` renamed with its expectation `DUPLICATE_ID` to `FRONTMATTER`), so both Done-when clauses "no `param` line left" and "no assertion changed" hold in substance only, as the lane reported |
| D3: Rule 6 on inputs and `INV-GEN-1` on the interface | shipped-modified | Rule 6 and `compareInputs` exist and match the spec (leaf read, `WorkflowValidator.java:1621-1840`, `GeneralInvariants.java:162-195`); the refusing fixtures cover a widened enum, a widened range and a dropped input only; a changed optional form, default or fallback, an added input, a reorder, and the omission of a `?` or fallback input have none, though the Done-when asks "a refusing fixture per case" |
| D4: the static reads (registries, three Rule 1 bindings, loaded on every unit load) | shipped-modified | registries, bindings and the empty-registry default (fails closed) as specified (leaf read: `ConfigKeyRegistry.java:74-76`, `UnitInterfaceChecksTest.java:84-178`, `VerificationResultTest.java:59-61`); "no list repeats a value" is delivered by the reader (`UnitFrontmatterReader.java:471-479,392,542`), not the verifier, which the lane reported as resolved toward R15 item 1 |
| D5: seven ADRs, guides, grammar, Rule 6, two DSL blocks, identifiers, watch, Traceability | shipped-as-specified, one text overclaim | `workflow-ast/004`, `workflow-dsl/012-014`, `workflow-verify/035-037` in the stat (re-read); `BROKEN LINKS 0` is the lane's, not re-run; ADR `workflow-dsl/014` and the `InputDecl` Javadoc claim engine-side resolution that no code does (see L2-2) |
| Surface | shipped-modified | files outside the declared surface, every one explained by a deliverable (leaf read): Javadoc consequences `CallValue.java`, `ParamType.java`, `ast/package-info.java`, `dsl/package-info.java`; the test consequence `unit/VerificationContextRecordsTest.java` (the spec said `model/`); shared bookkeeping `doc/Specification.adoc`, `doc/ImplementationWatch.adoc`, `doc/Traceability.adoc`; no `pom.xml`, no log message |

## Three-Layer Analysis

Read on three layers by one read-only leaf (`execution-context-level-3` on the `orchestrator.analyze` surface), one landing per leaf; the orchestrator re-read the points marked below. **By reading, no build, no test run, no write.** The leaf disclosed two of its own shell searches (a recursive `grep -l` and a `git grep` over `.`) that listed `.plan/` paths by mistake; it used nothing from them and wrote nothing.

- **Dispatch:** one read-only leaf — reason: every full ship gets its own leaf.
- **Verdict:** layer 1 findings (4 minor, stale or inconsistent spec points); layer 2 findings (1 refuted lane claim, 4 minor test gaps); layer 3 findings (1 real minor defect, 2 judgement points, one conformance deviation). No blocker, no major.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence |
|---|---|---|---|---|
| L1-1 | D1's Done-when names `constantsAreTheSpecificationValues`; no such method exists (PLAN-V2-120 deleted it); the real one is unchanged | checked | refutes | `UnitFrontmatterReaderTest.java:944` |
| L1-2 | D2 says "no `param` line is left" and "no assertion changed" while also requiring a tested refusal of a body `param`: the premise cannot hold literally | checked | refutes | `WorkflowParserRefusalTest.java:319,354,356` |
| L1-3 | D4 lists "no list repeats a value" under the verifier; the reader owns it (R15 item 1) | checked | refutes | `UnitFrontmatterReader.java:471-479` |
| L1-4 | `input_default` for a `list<enum>` can only be `[]` (the reader produces nothing else), and Rule 6 refuses `[]` bound at a call, so the default is a value no call site may bind and a non-empty list default is inexpressible; the spec treats the type's value set as one | checked (re-read through `InputType.java:90-93` by the leaf, not by the orchestrator) | refutes | `UnitFrontmatterReader.java:324`, `WorkflowValidator.java:1816` |

**Layer 1 verdict:** findings — 4 (minor). No corpus verdict persisted: the spec's claim section carries three HYPOTHESIS labels, two confirmed by the lane (caps as constants, composer edits "corrected") and one refuted (the method name); all four were settled at the emit or by the lane, and a staged-spec persistence applies to staged specs only; PLAN-V2-04 is shipped.

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|---|---|---|---|---|
| Lane claim (f): the runtime resolution of an omitted input "is left to PLAN-V2-15's call-time channel; `InputDecl`'s javadoc says so" | `InputDecl.java:19-23` (re-read: states the order as the resolution of the value, names no plan), ADR `workflow-dsl/014:42` (re-read: "the engine resolves the rest at the call"), `WorkflowEngine.java:998` (re-read: `.boundArgs(call.paramBindings())` only) | none | **refuted.** Neither text names PLAN-V2-15, no `git grep PLAN-V2-15` hit in `pm-workflow` or `doc` (re-read), and no code does the resolution: `fallback()` and `defaultValue()` are read only in `dsl/`, `verify/`, `ast/`. Rule 6 accepts a call that omits a defaulted, fallback or `?` input and the engine runs with the value absent. The overclaim is in the documentation; the behaviour is the second spec's by its Decision 6 | checked (re-read) |
| D3 | `GeneralInvariants.java:162-195` | `InvariantFixtures.java:116-126`, `CompiledInvariantsTest.java:413-419` | a changed optional form, default, fallback, an added input and a reorder have no failing test | checked |
| D3 | `WorkflowValidator.java:1644` via `InputDecl.required()` | `AdditionalRulesTest.java:632` | the omission of a `?` input and of a fallback input is untested; the fixture change that gave `attempts` a default also dropped it from the "missing required inputs" assertion (`:668`), a changed assertion | checked |
| D1 | `UnitFrontmatterReader.java` | `UnitFrontmatterReaderTest` | no case for an unclosed `enum[`, an unclosed `list<enum[`, `input_type: text`, an empty `input_description`, an empty `input_default`, a CRLF twin of a refusal (`readsTheCrlfTwin`, `:655`, is the positive case only); `WorkflowPrinterTest.java:256` holds the round trip with no refusal and no CRLF twin (a printer cannot refuse, so arguably not applicable) | checked |
| Lane claim (c): "reworded three remediation messages parameter to input" | `RemediationInvariants.java:199-210,226-230,262`, `WorkflowValidator.java:1627-1840` | — | refuted as to the count: five `INV-REM-1` sites and nine Rule 6 messages carry the reword; all are parameter to input and the two tests were updated to match | checked |
| Lane claims (a), (b), (d), (e), (g), (i) | see L1 and the layer 3 rows | — | all hold; (i) is pre-existing: `08-remediation-units.adoc:142-147` omits `waiver_wait_seconds`, `RemediationInvariants.java:91` requires it, and the plan rewrote the block without adding it | checked |
| Surface / `ParamDecl` | no `ParamDecl.java`; `PlanInstanceComposer.java:309,416` no longer call `.params(...)` | — | none unexplained | checked |

**Layer 2 verdict:** findings — 5 (1 refuted lane claim, 4 minor test and trace gaps).

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|---|---|---|---|---|
| L3-1 | `UnitFrontmatterReader.java:592-593` (re-read) | `input: a` / `input_type: enum[v1|v2|…|vN]` (any length) followed by `input_default: z` outside the set | the refusal formats `input.type.sourceText()` without `echo()`, while every other value of a refusal in the class goes through it and the guide states the bound (`workflow-dsl.adoc:182-184`, ADR `workflow-dsl/007:28`); the diagnostic carries the whole type, bounded only by the 1 MiB unit file bound. Line and column stay correct. CodeRabbit named it on #460 at 12:23:52Z, 90 seconds after the merge (12:22:18Z), on head `9f60090b`; the thread is unresolved and the defect is in the merged code | minor |
| L3-2 | `RemediationInvariants.java:204` (re-read) | a remediation unit declares `retry_budget: int[0..5]?` or gives it an `input_default` | `INV-REM-1` compares `InputType` only, so the loosened input satisfies it; the contract and the `param` form made every budget required and call-bound; a project copy is held by `INV-GEN-1`, a new default unit is not. Whether intended is not stated | minor (judgement) |
| L3-3 | `ConfigKeyRegistry.java:40` | a hand-built map whose key differs from the `ConfigKey.key` it holds | `contains` and `find` use the map key, the domain comes from the value; `of(Collection)` is safe; no test | info |
| L3-4 | `UnitFrontmatterReaderTest.java:591-650,733-790`, `AstNodeModelTest.java:204-228`, `WorkflowPrinterTest.java:256` | new assertions with no message or a supplier carrying the diagnostic | CLAUDE.md "every assertion carries a message of 20 to 60 characters" is violated; `pm-workflow` has no scan guard yet, so nothing machine-checks it | minor |
| — | `InputDecl`, `UnitFrontmatter` | a tree built by a builder or by the composer's copy of template frontmatter | no invariant of the reader (duplicates, caps, default beside `?`) applies; the consequence of the chosen ownership, not a defect against the spec | info |

**Layer 3 not shown by reading:** the build, test and coverage results; the links check (`BROKEN LINKS 0` is the lane's); the `subworkflow-param-binding.pmwf` expectation (fixture not in the diff); whether `UnitInterfaceChecksTest.java:193` (`CONFIG.empty()`, a static method through an instance) builds clean under `failOnWarning` (it merged green, so it did); any consumer of `VerificationContext.builder()` in `plan-marshall-mcp` (a builder call without `.configKeys` and `.factKinds` would now fail with a `NullPointerException`; the spec records no consumer outside the module).

**Layer 3 verdict:** findings — 4 (2 minor, 1 judgement, 1 info beside the conformance deviation); no blocker, no major, no concurrency or resource defect. LF and CRLF order handling read identical; the empty-registry default fails closed.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|---|---|---|---|
| CLAUDE.md | package-info with Javadoc and `@NullMarked`; public types documented | conforms | no new package; `ast` and `dsl` package-infos updated |
| CLAUDE.md | absence as `Optional`; a field that may be absent is `@Nullable` | conforms | `InputDecl.java:37-38` (re-read) |
| CLAUDE.md | four or more components carry `@Builder` | conforms | `InputDecl`, `UnitFrontmatter`, `VerificationContext` |
| CLAUDE.md | no generic `Exception` in production code | conforms | only `NumberFormatException` is caught |
| CLAUDE.md | no new dependency, no log message outside the holder | conforms | the stat names no `pom.xml` (re-read) |
| CLAUDE.md | JUnit 5, `@DisplayName`, no AAA, no Mockito, no new fake | conforms | leaf read |
| CLAUDE.md | every assertion message 20 to 60 characters | deviates | L3-4 |
| CLAUDE.md | ADR per decision, guides updated | conforms, one overclaim | seven ADRs; ADR `workflow-dsl/014` and the `InputDecl` Javadoc state a resolution no code performs (L2) |

## Metrics and Anomalies

- Tokens: `unknown` (the runbook lane measures none; an unobserved fact, not a zero).
- Duration: not stated by the lane. 36 builds, all `daemon_longpoll`; one daemon bound expiry at 330 s on the merged head, re-run at 900 s (372 s), not a fallback; several builds waited up to about 25 minutes in the shared daemon's queue.
- Anomalies: the lane's shell file operations (read-class `grep`, one `ls`, one `echo`, two `rm` recorded by git, one `perl -0pi` import swap, one `cp` into a `ci` body slot) were issued although the lane's rule asks for the file tools; the lane owned them; `guard: none` (OpenCode). The lane filed three open spec items for the orchestrator to correct; the spec is shipped, so they route as L1-1 to L1-3 and need no spec edit.

## Routing and Merge Behavior

- Review: CodeRabbit required (quota-limited): refused at 10:10:32Z, reviewed `aba58d89` at 10:45:34Z with 6 inline findings (five fixed in `e54a38c1`, one answered), merged two heads later under `review_complete_after_handling`; Sourcery optional, refused on its diff limit. Self-review: 3 rounds, 28 findings, all fixed, `self-review: budget-exhausted` (round limit).
- **Review numbers re-read:** `review-gate` on #460 now reads `unresolved_review_threads` 1, `review_body_findings_total` 7, `merge_allowed false` (`unresolved_review_threads`). The landing says 0 and 6. **This is not a false claim:** the extra thread and the second review body are one CodeRabbit review on head `9f60090b` created at 12:23:52Z, after the merge commit's time of 12:22:18Z and before the lane's message of 12:29:22Z; at the merge the landing's 0 and 6 were true. It is a late review of the merged content (L3-1). `review_body_findings_handled`: the progress file carries 6 `finding:` lines (five `e54a38c1`, one answered), so the landing's 6 is corroborated for the six it counted; the seventh finding is unhandled.
- CI/merge: merged through the merge queue as `998f08ff`; `cleanup_owed=false`; no `v2-04-…` worktree remains under `.plan/local/worktrees/` (re-read).
- Sonar: the landing's `sonar_open_new_issues=0` and gate `ok` were not re-measured by this analysis; the lane's claim, not re-verified.
- Surface collisions: none observed. The spec's overlap list (PLAN-V2-13, 15, 54, 69, 21, 43, 29) is unchanged: PLAN-V2-15, now unblocked, carries the resolution gap.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-V2-04 --status shipped`
- [x] row `pr` stamped — `#460`
- [x] row `landing` stamped — `landings/PLAN-V2-04.md`
- [x] row `plan_marshall_plan_id` stamped — `rb-v2-04-unit-input-interface` (from the sender)
- [x] epic.md narrative reconciled (queue annotation; E27 recurrence, no new entry)
- [x] residue routed: the unbuilt resolution of an omitted input and the `InputDecl` Javadoc into PLAN-V2-15's Carried Open Items with `ast/InputDecl.java` added to its Expected Surface; the rest into PLAN-V2-114 item 34 with its Expected Surface widened in the same edit (`corpus surfaces` re-read: both specs parse, declarative)
- [x] resume anchor updated; `queue-view.md` regenerated

## Follow-Ups

- PLAN-V2-15 (staged, now unblocked by this landing): the resolution of an omitted input, to be built with the one binder.
- PLAN-V2-114 item 34: the unbounded echo (a real, small defect), the `INV-REM-1` optional-form decision (ask-at-refine, ~75% yes), the untested corners, the list default (ask-at-refine, ~60% allow a non-empty enum-list default), four stale statements and the registry key.
- PLAN-V2-152 owns the missing assertion messages of the `pm-workflow` test tree (L3-4).
- Operator: the unresolved CodeRabbit thread `PRRT_kwDOVBd2NM6361P-` on the merged #460 stays open on GitHub; it is answered by item 34 (a) when that plan lands, or can be resolved by the operator.

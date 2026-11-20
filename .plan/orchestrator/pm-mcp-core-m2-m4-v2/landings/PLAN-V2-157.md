# Landing Analysis: PLAN-V2-157 — call-time-bind-and-input-residue

epic: pm-mcp-core-m2-m4-v2
workstream: WS-01
pr: #522 (squash merge `d65dbc66`, 78 files, +2856/-314)

> Landing record, runbook lane (`rb-v2-157-call-time-bind-and-input-residue`, harness opencode, model `deepseek-v4.1-flash/high`). Drained from `inbox/rb-v2-157-call-time-bind-and-input-residue-001.md` on 2026-11-20.
> `inbox landing-check`: `complete: false`, missing `total_tokens`, `plugin_version`, `sonar_open_new_issues` (all `unknown`; the third is stale, see Routing). The lane says 5 of 6 deliverables met (D2's measurement clause not met); the hold on PLAN-V2-156 was overridden by the operator, and the merge brought in PLAN-V2-156's four commits with one conflict in `LessonStore.java`.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 call-time bind: resolution order (call site, `input_fallback`, `input_default`, absence under `?`, else `input_unbound`), validation, `arg_sources` both ways, qualified slot key `<unit id>:<slot>`, consumption, `submitted`-only fill, JSON list parse, `TasksSettled` hardening | shipped-as-specified | `WorkflowEngine.call` and `resolve`; `CallStack.toStoreSource`/`fromStoreSource`; `InputBindingTest` (14 cases, the message says 15), `SlotFillTest`; the operator's answers match the code (an empty `files` list refuses the bind; only U+0020 is admitted as whitespace in a path) |
| D1 validate "at the task submission" | shipped-modified | validated at the engine's settle (`storeSlot`), `invalid_link_args` is only an exception message prefix; a bad value on a settled `submitted` task throws and the state rests (unverified lead) |
| D1 codec refusal tests (`command_id`, `correlation_id`, unknown provenance, an invalid slot) | **not delivered** | `WorkflowStatusCodecTest` is not in the diff and has only `slotRoundTrip` (re-read) |
| D2 pruner, invariants, composer, `HeaderJson`, the fixtures (six refused, passing control, CRLF twin, parser refusals) | shipped-as-specified | `.fills(...)` at `InstancePruner` and `SubWorkflowInvariants`, `.slots(...)` at `PlanInstanceComposer`; the `HeaderJson` round trip and the invariants' fills carry have no test of their own |
| D2 Rule 6 dominance | shipped-modified | state level only: the graph cannot see outcomes, a `lost` route into a call still verifies and the engine refuses it at run time (ADR 039 says so); consumption is not modelled |
| D2 "measure the cost and record the result" | **not delivered** | ADR `workflow-verify/039`: "no separate benchmark was taken" |
| D2 `RESERVED_KEYWORDS` | shipped-modified | updated with `slot`, `fills`, `from`; still differs from `WorkflowParser`'s by `decide`, `phase`, `inherit`; the spec called the copy unused, it is used at `WorkflowValidator.java:1025` |
| D3 caps, reader, `bounds()` clamp, path rules, echo, `slots.source` | shipped-as-specified | whitespace modified per the operator; three more behaviours decided without asking (below) |
| D4 spec blocks, state catalogue, unit fixture, provider | shipped-as-specified | the provider refuses a directory, `:` magic, a glob, `\`, a tracked directory whose copy is gone; `hasChanges` compares object id and mode, the `"No changes"` string match is gone; the call-site matrix is a paragraph instead of the column |
| D5 documentation and tests that can fail | shipped-modified | **false citations remain** (L3-1); the two tautologies are replaced; coverage and integration run; "Owed to the milestone exit" is in the PR body |
| D6 the carried items | shipped-modified | the landing message has no D6 section ("this section" is absent); the outcomes are in the progress file and the PR body; item 1 (free-parameter runtime validation) "partly adopted": the call-time binder is built, the submit-side `undeclared_param`/`missing_param` half is split to PLAN-V2-25 and the assembly, and PLAN-V2-25's spec says "the binder keeps its shape" and names it nowhere (re-read): unowned |

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`, `orchestrator.analyze`) — reason: a full-ship landing is read by its own leaf with no size qualifier; the leaf wrote nothing.
- **Re-read by the orchestrator** (all held): `LessonStore.java:815` (`default -> false;` at `d65dbc66`); `WorkflowStatusCodecTest` (no refusal case for the ids, provenance or an invalid slot; `core-store.adoc:182,206` cite one); no implementation of `InputBindingContext` in any `src/main` (`git grep`); `PLAN-V2-25-command-record-and-dispatch.md:34,68` ("the binder keeps its shape"); the review gate (`unresolved_review_threads: 0`, `review_body_findings_total: 13`: both equal the landing; `review_body_findings_handled=13` re-verified from the progress file: 13 `finding:` lines, the gate's `handled: 0` is a form difference); the provider (10 review threads, 0 unresolved; the SonarCloud check below).
- **Not re-read, recorded as unverified leads:** the Sonar issue list for #522 and any rule hits (the duplicate import, an always-false null check in `CommitInput`); JGit `addFilepattern`/`setOnly` doing no glob expansion (from memory); whether `UnitGraph.successors` includes fallback, guard and link edges (dominance completeness); whether a test covers the invariants' `fills` carry; recovery after an invalid fill; the assembly's mapping of `invalid_link_args`; the coverage figures and the 3 092 sibling-link reports.
- **Verdict:** layer 1 two minor; layer 2 findings (of the 23 findings of `landings/PLAN-V2-15.md`: 17 fixed, 4 partly, 2 n/a); layer 3 two major (one documentation, one residue), minor and info. No blocker.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L1-1 | Decision 4, the resolution order, the caps and `precondition_unmet` with `input_refused`/`input_unbound` are built as the spec states | checked | confirms | `WorkflowEngine` `storeSlot`, `resolve`, `unbound()`/`refused()`; `InputValidator.java:38-47` |
| L1-2 | the operator's two answers (empty `files` list refuses; U+0020 only) match the code | checked | confirms | `validateInput` `RULE_EMPTY`; `PathRules.isRefusedWhitespace` |
| L1-3 | three behaviours the spec does not state were decided without asking: paths with `* ? [` refused, any control character (a tab too) refused in a commit body, spaces at segment edges admitted (`.git ` passes the `.git` rule on NTFS) | checked | refutes (R07) | `PathRules.checkSimple`, `InputValidator.controlInBody`, ADR 005 |
| L1-4 | the D2 measurement clause is not met | checked | refutes | ADR 039 |

No finding names a labelled claim of a staged spec that this landing refutes; nothing is persisted by `corpus set-verdict`.

**Layer 1 verdict:** findings — 4

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| D1 | the loop over `callee.frontmatter().inputs()` resolves, validates, pushes the frame with `argSources`; the slot is consumed from a local copy and `status` is written only after every input resolved, so a refusal on the second input leaves the status unchanged | `shouldBindTheFilledSlot`, unfilled, consumed, staging-changed and empty-list fail on the old `call()` (which pushed `slot.x` and stored the bare key); gaps: `PathConfinement` rejection, a null `bindingContext`, a multi-input case, a slot bound to two inputs, a slot with an invalid value | `InputBindingContext` has an interface only (no implementation, no caller outside tests: a spec "Not here"); with a null context every `path`/`list<path>` input is refused and `input_fallback` is skipped | checked |
| D2 | as above | `keptStateKeepsFills` (pruner), `instanceFrontmatter` (composer) | `HeaderJson`, the invariants: no test of their own | checked |
| D3 | reader refuses a bare range and a widening; `bounds()` clamps | `InputValidatorTest` (46), `PathRulesTest` (45), `UnitFrontmatterSlotTest` | none | checked |
| D4 | `JGitOperations.refusedCommitPath`, `refusedTrackedTree` | `refusesADirectory` and six more; `CompiledInvariantsTest`, `DefaultPlanUnitTest`, `ProjectUnitLoaderTest` | the default-unit mode changes (`execute.pmwf`, `finalize.pmwf`, `remediation-qgate-findings.pmwf`, the plan template fixture) follow from `message_mode` becoming a required input and match the specification's call-site rows; existing sites still verify (`message` and `files` are `?`) | checked |
| report | n/a | n/a | the landing says 15 `InputBindingTest` cases (14) and seven new provider cases (4 against 6 in the diff); the progress file marks D2 "done" where the message says "not met as written"; `LessonStore.java` is changed by the merge resolution (L3-3); `CommandId.PATTERN` made public (explained in the PR body) | recorded |

**Layer 2 verdict:** findings — 5

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `core-store.adoc` (`:182`, `:206`), watch DSL-41, Traceability (re-read: `WorkflowStatusCodecTest` has only `slotRoundTrip` and the goldens) | reading the guide | cites a refusal test (malformed ids, unknown provenance, a slot without `value`/`filled_at`) that does not exist: the defect class D5 was meant to remove | major (documentation) |
| L3-2 | `progress.md:28`, PR body D6, `PLAN-V2-25` (re-read) | the free-parameter runtime validation (`undeclared_param`, `missing_param`, the `ParameterBinder` default refusal) | split off to a plan that says "the binder keeps its shape"; the PR body's Owed list names PLAN-V2-20 for the same item: no plan owns it | major (residue) |
| L3-3 | `LessonStore.java:815` (re-read) | a new `LessonResolution` variant | the merge brought back a `default -> false;` arm that #516 had removed; the switch covers all seven variants, so the arm defeats the exhaustiveness check and trusts a new variant silently; the PR body calls it "a reindent of one switch arm"; otherwise `LessonStore` equals `5490323a` (absent not untrusted; unreadable, tampered, deleted, untracked mark) | minor |
| L3-4 | `JGitOperations.java:~59` | build | duplicate `import org.jspecify.annotations.Nullable;` (a Sonar smell) | minor |
| L3-5 | `WorkflowValidator` `~1930-1960`, `WorkflowEngine.resolve` `~1510` | `a = slot.m, b = slot.m`; a slot filled once and read by two calls | Rule 6 does not model consumption: verifies, then the second read is refused `input_unbound` | minor |
| L3-6 | `WorkflowEngine` `~1523` | production until the assembly supplies the port | `input_fallback` silently skipped and a required input gets a misleading `input_unbound` (fail-closed) | minor |
| L3-7 | `PathRules.checkSimple`, `JGitOperations.refusedCommitPath` | `pages/[id].tsx` | the glob refusal (CodeRabbit's finding, not in Decision 3) makes bracketed names unbindable; JGit's `addFilepattern` and `setOnly` are, I believe, literal prefixes (unverified lead) | minor |
| L3-8 | `HeaderJson` `~566` | an enum value that starts with `slot.` | read as a `SlotRef` (no test) | info |
| L3-9 | `InputValidator.parse` | a huge submission | the slot text is parsed before the cap applies (bounded by the assembly) | info |
| L3-10 | `WorkflowEngine` `~1067` | `invalidLinkArgs` | builds an `InputRefusal` and discards it | info |

No behaviour change for existing calls inside this repository by reading: literal binding and the frame push are unchanged; the one change is that declared defaults and fallbacks now appear in `bound_args` and a call-site binding the callee does not declare is no longer pushed (Rule 6 already refuses those).

**Layer 3 not shown by reading:** the build and coverage runs (the lane's counts 11 559 and 11 624), the integration tests (21), the Sonar issue list, the links check.

**Layer 3 verdict:** findings — 10 (no blocker)

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| spec R07 | ask at refine before a behaviour the spec does not state | **deviates** (partly) | L1-3: two asked (the empty list, whitespace), three decided without |
| spec D2 Done-when | "measure it and record the result" | **deviates** | L1-4 |
| spec D5 | documentation cites no test that does not exist | **deviates** | L3-1 |
| CLAUDE.md § Git Workflow | the review gate met | conforms | 0 unresolved threads, 13 findings handled; the Sonar check is green (below) |
| CLAUDE.md § Tool Usage | no shell file operations | **deviates**, reported by the lane | shell `grep`/`wc`, an inline `python3 -` heredoc edit, a build against `main`'s sources by mistake (discovered at once), one `git checkout -- <path>` on an unmodified file, an `rm` of a stale `index.lock` |

## Metrics and Anomalies

- Tokens: `unknown`. Duration: not in the message. The executor reports 0.1.2173 at landing (regenerated by the merge's sync, not by the lane).
- Anomalies: the lane merged the base once with one conflict in `LessonStore.java` (resolved to main's newer semantics); two long builds returned a live job after the bounded wait and were re-attached by the same job id; CodeRabbit was triggered once and not again on later heads (exemption: a qualifying review on record); Sourcery refused (diff above its 150 000-character limit).

## Routing and Merge Behavior

- Review: coderabbit required (`review_complete_after_handling`), reviewed `3837de17`: 10 inline, 1 outside-diff major, 1 minor, 1 nitpick, all 13 handled (10 fixed in `849bbe43`, 1 in `99f23b73`, 3 answered with a reason); both gate numbers equal the landing's claim; **`review_body_findings_handled=13` re-verified** from the progress file.
- CI/merge: merge queue, `d65dbc66`; the merge group run completed `success`. **Sonar: the lane's `unknown` is stale.** The check "SonarCloud Code Analysis" on #522 completed `SUCCESS` at 2026-11-20T10:36:24Z, after the last head (`a55c20af`, 10:25Z) and before the merge (10:43Z): the quality gate was green (read through the provider, not recalled); the open-issue list of the pull request was not read. The merge was made on the operator's selection at a moment the lane's read was undecidable.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#522`; `landing` stamped; `plan_marshall_plan_id` `rb-v2-157-call-time-bind-and-input-residue`
- [x] epic.md: the decision, the Watch "landed"
- [x] the residue staged as PLAN-V2-164 `call-bind-closeout` (WS-01, plan-marshall: a medium plan; the leftovers rule, no staged plan owns the free-parameter validation, PLAN-V2-25 keeps the binder's shape)
- [x] PLAN-V2-19, 20 and 25 re-read: 157 has shipped; 164 shares the engine files with them (the gate decides)

## Follow-Ups

- Every open point is PLAN-V2-164's; the external hand-offs (the assembly's `InputBindingContext` implementation, the `TasksSettled` producer, the production `git-commit-and-push.pmwf`, `StateCatalogueDriftTest`, the `invalid_link_args` mapping) stay with `plan-marshall-mcp`, PLAN-V2-20 and 10.

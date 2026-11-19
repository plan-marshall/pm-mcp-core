# Landing Analysis: PLAN-V2-15 — call-time-inputs-and-slots

epic: pm-mcp-core-m2-m4-v2
workstream: WS-01
pr: #513 (squash merge `bb6a9d33`, parent `8a5ff352`, 94 files, +4797/-150)

> Landing record, runbook lane (`rb-v2-15-call-time-inputs-and-slots`, harness opencode, models `beam-free/high` (the first eight commits: D6, D1, parts of D2, a free model the operator had selected by mistake) and `deepseek-v4.1-flash/high`). Drained from `inbox/rb-v2-15-call-time-inputs-and-slots-001.md` on 2026-11-19.
> `inbox landing-check`: `complete: false`, missing `total_tokens`, `plugin_version`, `unresolved_review_threads`, `review_body_findings_total`, `review_body_findings_handled` (the three review keys `unknown`: the gate answered `undecidable_read` on a stale CodeRabbit body). The lane says `deliverables_done=2` of 6 and merged with the review gate overridden by the operator's selection.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 types `Text`, `CommitMessage`, `Path`, `PathList` | shipped-as-specified | `InputType`, frontmatter read and print |
| D1 `InputValidator`, `InputRefusal`, `PathRules` | shipped-modified | no production caller outside their package (re-read: `git grep` at `bb6a9d33`); whitespace refused in every path (not in Decision 3) |
| D1 compiled caps 2000/1024/500, the reader refuses a missing range | **not delivered** | `UnitFrontmatterReader` accepts bare `text`, `commit_message`, `list<path>` and `[1..99999]`; `InputValidator.bounds()` defaults to `Integer.MAX_VALUE`, `unboundedAdmitsEmpty` pins it; the grammar spec and the guide state the caps |
| D2 grammar (`slot`, `slot_type`, `slot_description`, `slot_ref`, `fills_clause`), `SlotRef`, `StateNode.fills`, printer, reserved words | shipped-as-specified | `SlotGrammarTest` (4 positive tests, no parser refusal test) |
| D2 Rule 6 slot checks | shipped-modified | dominance replaced by "filled somewhere", a `lost` outcome counts as filler (`WorkflowValidator.java:1846-1965`) |
| D2 `InstancePruner` and `SubWorkflowInvariants` follow the new `StateNode` | **not delivered** | neither file is in the diff and neither carries `fills` (re-read at `bb6a9d33`): every pruned or composed instance loses its fills |
| D2 `verify/refused/*.pmwf` per check, passing fixture, CRLF twin | **not delivered** | inline unit tests instead |
| D3 `CallFrame` ids, `WorkflowStatus.slots`, codec, schema, frame ids per run | shipped-modified | `ast.CallFrame` has no `argSources` (`CallStack.toStoreFrame` writes `argSources(Map.of())`, re-read); slot key is the bare name (spec Decision 4: `<unit id>:<slot>`) |
| D3 `TasksSettled.submissions`, slot fill | shipped-modified | no null-check, no validation, no `invalid_link_args` |
| D3 **call-time bind** (resolve, validate, provenance, consume) | **not delivered** | `WorkflowEngine.call` still builds the frame with `.boundArgs(call.paramBindings())` (re-read); `CallStack.toBoundArg(SlotRef)` yields `StringArg("slot." + name)` (re-read): a call that binds a slot pushes the literal placeholder and the callee runs on it; `bindingContext` is assigned and never read (re-read) |
| D4 `CommitInput.paths`, JGit `setOnly` subset commit | shipped-modified | `commitsOnlyListedPaths` fails on the old code; no directory test; a bound empty list commits everything |
| D4 `06-reusable-sub-workflows`, `state-catalogue/05`, `git-commit-and-push.pmwf`, call-site matrix | **not delivered** | |
| D5 ten ADRs, guides, grammar, Traceability, watch | shipped-modified | numbers right (core-store 077/078 after 076), link labels still read 075/076; ADRs 015 and 017 and `workflow-engine.adoc` (section "The Input Bind at a Call"), `provider-git.adoc:82` and watch DSL-42/43 state unbuilt behaviour in the present tense; "Owed to the milestone exit" missing from the PR body |
| D6 `CommandId`, `CorrelationId`, `Ports.ids`, registry rows | shipped-as-specified | tests not generator-driven; `IdentifierSpecificationParityTest` does not read the registry rows |

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`, `orchestrator.analyze`) — reason: a full-ship landing is read by its own leaf with no size qualifier; the leaf wrote nothing. Extra scrutiny: free model, four stops, review gate overridden.
- **Re-read by the orchestrator** (all held): `CallStack.java:160-192` (`argSources(Map.of())`, `toBoundArg(SlotRef)` = `"slot." + name`); `WorkflowEngine.java:1360-1368` (`boundArgs(call.paramBindings())`); `git grep` of `InputValidator`, `PathRules`, `InputRefusal` outside their package (none) and of `bindingContext` (assigned `:263`, never read); `fills` absent from `InstancePruner.java` and `SubWorkflowInvariants.java` at `bb6a9d33`; the review threads of #513 through the provider (11 total, **10 unresolved at merge**); the lane's progress file (26 lines, no `finding:` line).
- **Not re-read, recorded as unverified leads:** JGit `setOnly("dir")` and `addFilepattern("dir")` expanding below a directory; the `"No changes"` string match under another locale; whether `Character.isWhitespace` lets U+00A0 through; the composed-instance slot loss (`PlanInstanceComposer.java:515-529`) and the pruned-instance fills loss by running them (read only); the R17 links check, Sonar and coverage claims (88.5 % new code), `verify -Pcoverage` and `-Pintegration-tests` (not run by the lane).
- **Verdict:** layer 1 two major, rest minor; layer 2 findings; layer 3 eight major, minor ones. No blocker (the unbound slot is fail-open only for plans that use slots, none exist yet).

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L1-1 | The lane's D3 blocker, "no text carrier for the in-memory `ast.CallFrame`; Decision 9 keeps text out of the grammar", is not real: `CallFrame.boundArgs` is `Map<String,CallValue>`, `CallValue.EnumValue(String)` and `EnumList` carry text, `CallStack` maps them both ways (`:176-186`), `WorkflowStatus.BoundArg` has `StringArg`, `IntegerArg`, `StringSetArg`; Decision 9 is about the lexer; the only gap is `argSources` on `ast.CallFrame` (mechanical), and the diff itself added `CallValue.SlotRef` | checked (the carrier types re-read by the leaf) | refutes the lane | `ast/CallFrame.java:38`, `ast/CallValue.java:28,44`, spec Decision 9 |
| L1-2 | Decision 4's slot key `<declaring unit id>:<slot name>` is not what the engine stores (bare name), though the schema description says the qualified form | checked | refutes the implementation | `WorkflowEngine.storeSlot` |
| L1-3 | "compiled caps 2000/1024/500, a unit narrows and never widens" is stated in the grammar specification and the guide; nothing enforces it | checked | refutes the specification text | `UnitFrontmatterReader`, `InputValidator.bounds()` |
| L1-4 | The hypothesis "dominance check over `UnitGraph` is affordable" was never measured (`CompiledInvariants.dominates` exists); the landing says the weaker check is recorded in ADR workflow-engine/017, which holds the staging-set decision, not this one; `workflow-verify.adoc:154` and ADR 015 claim a runtime `input_unbound` safety net that does not exist | checked | refutes | ADR 015, 017 |
| L1-5 | Carried Open Items "resolution of omitted inputs (fallback, default, `?`)" and "one binder, not two" were not adopted and the landing gives no reason | checked | confirms the gap | `WorkflowEngine.call` |

No finding names a labelled claim of a staged spec that this landing refutes; nothing is persisted by `corpus set-verdict`.

**Layer 1 verdict:** findings — 5 (two major)

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| D1 | `InputType`, `InputValidator`, `PathRules`, reader | `PathRulesTest`, `InputValidatorTest` | `InputValidator` etc. have no production caller; `unboundedAdmitsEmpty` pins the missing cap | checked |
| D2 | grammar, `SlotRef`, `fills`, Rule 6 | `SlotGrammarTest` positives only; **no** refused fixture per check | Traceability "Verified by" lists `UnitFrontmatterReaderTest`, `WorkflowValidatorTest`, `WorkflowPrinterTest`, which were not extended | checked |
| D3 | `CallFrame` ids, slots, codec | `InputBindingTest` (3 tests, two tautological and duplicating each other; the seeded-engines test is the only one that can fail meaningfully); `SlotFillTest` fills a `commit_message[1..2000]` slot with a non-Conventional-Commit string and asserts the wrong key: it pins the missing validation; **no** codec refusal test for a malformed `command_id`, `correlation_id` or an unknown provenance; `WorkflowStatusSchemaParityTest` has no case reading the frame or slots | watch DSL-42/43 cite `InputBindingTest` and `CompiledInvariantsTest` cases that do not exist | checked |
| D4 | `JGitOperations.commit` `setOnly` per path (`:357-395`) | `commitsOnlyListedPaths` | no directory test; no empty-list test | checked |
| D5 | ten ADRs | n/a | the landing says the docs state D3 as deferred: true only in `core-store.adoc`, ADR 018 and Traceability | checked |
| report | n/a | n/a | the landing says `WorkflowStatusSchemaParityTest` is re-enabled: only the `@Disabled` text changed (the seven schema-only members the lane names are real: title, created_at, phase_entered_at, pause, terminal_reason, setup_aspect, outline_review_revisions); the progress file marks "D4 done" and "full verify success" where the landing says NOT MET and coverage and integration were not run; "all 25 CodeRabbit findings triaged and addressed" against 10 of 11 threads unresolved | recorded |

**Layer 2 verdict:** findings — 6

### Layer 3 — Correct

The ten threads unresolved at merge are all real at `bb6a9d33` (the leaf read each; the first three re-confirmed by my re-reads above).

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 (T6) | `WorkflowEngine.java:1366`, `CallStack.java:188` (re-read) | a call that binds `slot.x` | the frame stores the text `"slot.x"`, the slot is neither validated nor consumed, the callee runs on a placeholder for a required input: fail-open | major |
| L3-2 (T5) | `CallStack.java:164` (re-read) | any engine advance over a stored frame | `fromStoreFrame` drops `argSources`, `toStoreFrame` writes `Map.of()`: provenance is rewritten to empty at every `Run.finish` (`:1440`) | major (latent) |
| L3-3 (T1) | `WorkflowEngine.storeSlot` | two units each declaring a slot `message` | the second overwrites the first (bare key) | major |
| L3-4 (T3) | `WorkflowEngine` slot fill, ADR 015:35 | a fill with an invalid link or non-Commit message | stored raw, no `InputValidator` call anywhere in the engine (re-read: no caller), `SlotFillTest` enshrines it | major |
| L3-5 (T4) | `WorkflowEngine.java:1364` | a call with an omitted input | still resolved by `paramBindings()` only; ADR 018's "deferred" paragraph hides an unadopted carried item | major |
| L3-6 (T9) | `WorkflowValidator.java:1915` | a `lost` route into a call whose state `fills` the slot | verification passes (any `fills` on a model job counts as a filler regardless of outcome), the engine enters the callee with `"slot.x"` | major |
| L3-7 (T2) | `JGitOperations.java:~371-377`, `provider-git.adoc:82` | `paths = ["src"]` | `PathRules` is syntactic: a directory passes and JGit's pathspec is prefix-based; "a directory never arrives" is not enforced (unverified lead U1) | major (latent) |
| L3-8 (T10) | `workflow-engine.adoc:312`, `provider-git.adoc:82`, ADR 015/017, watch DSL-42 | reading the guide | resolution, validation, provenance, consumption and refusal stated in the present tense; none exists | major (documentation false) |
| L3-9 (N1) | `InstancePruner.java:267-277`, `:319-326`; `SubWorkflowInvariants.java:155-160` (re-read: no `fills`) | any pruned or composed plan instance | every `fills` clause is silently dropped | major |
| L3-10 (N2) | `PlanInstanceComposer.instanceFrontmatter` `:515-529` | a plan or epic unit that declares slots | composed instance carries `inputs` and no slots: Rule 6 reports "slot not declared"; the composition would be refused (not run) | major |
| L3-11 (N3) | reader, `InputValidator.bounds()` | `text` without a range, `list<path>[1..99999]` | no cap enforced anywhere but the path length | major (a false specification statement) |
| L3-12 (T7) | `HeaderJson.java:464` | a `SlotRef` argument | written as `StringNode("slot.x")`, read back as `EnumValue`: the type is lost | minor |
| L3-13 (T8) | `WorkflowEngine.java:1002` | outcome `lost`, `asked` or `infeasible` on an awaited entity | the slot is filled although nothing was submitted; no test | minor |
| L3-14 (N4) | `PathRules`, `InputValidator` | `docs/my file.md`; a lone surrogate; ESC or NUL in a commit message body; U+00A0 | whitespace paths can never be bound (not in Decision 3); length counts UTF-16 units; control characters and lone surrogates in a body pass; `check()` has `@Nullable` parameters and return against the project's Optional rule | minor |
| L3-15 (N5) | `JGitOperations`, `CommitInput` | a bound empty `files` list; `CommitInput.builder()` without `paths` | empty means commit everything; the builder throws `NullPointerException` (`List.copyOf(null)`); `catch JGitInternalException ... contains("No changes")` matches a translatable message | minor (major once bound) |
| L3-16 (N6) | `status.json` slot value, `InputRefusal` | a huge or bidi/ESC slot value | no length cap, the refusal echo is raw; the schema's `slots.source` admits `literal`, `config`, `default`, which a slot never has | minor |
| L3-17 (N8) | `TasksSettled` | `submissions` null | no `requireNonNull` or copy, no short constructor; the assembly's producers break on the SNAPSHOT (external, in the hand-offs) | minor |
| L3-18 (N7) | Traceability 767-768, ADR 018, `workflow-engine.adoc:327`, `workflow-input.adoc`, `core-store.adoc` | following a link | the labels still read "ADR core-store/075/076" while the targets are 077/078 and 075/076 are real, other ADRs; the dominance note cites ADR 017 | minor |

No behaviour change for existing calls inside this repository by reading (literal binding and the frame push are unchanged; CI green per the landing). Breaking surface changes: `Ports.ids` is required; `StateNode`, `UnitFrontmatter` and `TasksSettled` are positional; `slot`, `fills` and `from` are reserved words; every persisted frame now needs the three new members (R13-accepted); the unused `WorkflowValidator.RESERVED_KEYWORDS` copy (`:143`) drifts.

**Layer 3 not shown by reading:** the build and coverage runs, the Sonar gate, the integration tests, the links check.

**Layer 3 verdict:** findings — 18 (no blocker)

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| spec Done-when D2 | a refused fixture per check, the reader refuses a missing range | **deviates** | L1-3, D2 rows |
| spec D5 Done-when | the PR body lists "Owed to the milestone exit" | **deviates** | only "External hand-offs" |
| CLAUDE.md § Code Standards | no `@Nullable` return, absence is `Optional` | **deviates** | `PathRules.check` |
| CLAUDE.md § Code Standards | test data from generators | **deviates** | `CommandIdTest`, `CorrelationIdTest` are not generator-driven |
| CLAUDE.md § Code Standards | a test that can fail on the old code | **deviates** (partly) | `InputBindingTest` (two tautological), `SlotFillTest` |
| CLAUDE.md § Work Packages / Module Documentation | documentation states what the code does | **deviates** | L3-8 |
| CLAUDE.md § Git Workflow | the review gate met before merge | **deviates**, by the operator's explicit override at the lane's question | the gate answered `undecidable_read`; 10 of 11 threads open |
| CLAUDE.md § Build | coverage and integration profiles | not_shown_by_reading | not run by the lane |

## Metrics and Anomalies

- Tokens: `unknown`. Duration: not in the message. Models: `beam-free/high` for the first eight commits, `deepseek-v4.1-flash/high` for the rest.
- Anomalies: the lane stopped four times asking the operator which part to continue with; the CodeRabbit allowance ran out and its stale body kept `review-gate` at `undecidable_read` (the progress file says "no actionable comments (review allowance exhausted)", the landing "8 actionable + 13 minor + 3 nitpick"); the lane merged with the gate overridden after two operator questions; one transient `quarkus-alignment` 404 cleared on rerun; `LspClientTest` timeouts in a local full verify (module untouched).

## Routing and Merge Behavior

- Review: coderabbit required (`review_complete_after_handling`), sourcery skipped (diff above its limit). `runbook_lane review-gate --pr-number 513 --required-reviewer coderabbit` on the merged head reads `merge_allowed: false`, `undecidable_read` (unreadable body section `Minor comments: 13`). The provider reads 11 review threads, **10 unresolved**: the landing's `unresolved_review_threads=unknown` is honest, "all 25 findings triaged and addressed" is not re-verified and not supported by the progress file.
- CI/merge: merge queue, `bb6a9d33`; CI green on the head per the landing. Sonar: `sonar_open_new_issues=0`, `ok` claimed; not re-read, it stays the lane's claim.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#513`; `landing` stamped; `plan_marshall_plan_id` `rb-v2-15-call-time-inputs-and-slots`
- [x] epic.md: the decision, the Watch "landed", the Open Defects of the unresolved threads
- [x] the residue staged as PLAN-V2-157 `call-time-bind-and-input-residue` (the leftovers rule: no staged plan owns the engine bind)
- [x] PLAN-V2-19, 20 and 25 re-pointed to wait for PLAN-V2-157

## Follow-Ups

- Every open point of this record is PLAN-V2-157's deliverable; the external hand-offs (the assembly's `TasksSettled` producer and `InputBindingContext`, the production `git-commit-and-push.pmwf`, `StateCatalogueDriftTest`) stay with `plan-marshall-mcp` and PLAN-V2-20 and 10.
- The review gate was overridden on a stale CodeRabbit body: a lane-side finding for the lessons routing (the gate cannot decide on a body whose sections it cannot read), not filed here.

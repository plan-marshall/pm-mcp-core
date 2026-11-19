# Landing Analysis: PLAN-V2-46 — runtime-endpoint-residues

epic: pm-mcp-core-m2-m4-v2
workstream: WS-06
pr: #494 (merge `bb8456d8`, read at that commit; the checkout contains it)

> Landing record, runbook lane (`rb-v2-46-runtime-endpoint-residues`, harness opencode, model `deepseek-v4.1-flash/high`).
> Drained from `inbox/rb-v2-46-runtime-endpoint-residues-001.md` on 2026-11-19. `inbox landing-check`: `complete: false`,
> missing `total_tokens` and `plugin_version` (both `unknown`, a failed read, not a zero). The PR state, merge SHA and the
> review numbers were read back against the provider.

## Deliverable Fidelity vs Spec

The binding rescope of 2026-11-14 dropped D1 and D4a to D4c (PLAN-V2-129 deleted the judgment endpoint); the lane graded D2, D3, D5.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D2 `AnswerSchema.check` under the complexity bound, one case per untested refusal | shipped-as-specified | `AnswerSchema.java:68-89` (`check` now six guards in the old order: `guardDepthAndShape` 95, `guardKeywords` 109, `followReference` 130, `guardType` 156, `guardConstantAndEnum` 168, `dispatch` 184), five new parameterised refusals and a type-before-const order pin in `AnswerSchemaTest` |
| D3 no `TODO: Catch specific` in `pm-runtime`; a refusing fixture per catch | shipped-as-specified | `git grep 'TODO: Catch specific'` at `bb8456d8` over `pm-runtime`, `pm-core`, `pm-workflow` finds none (re-run); the sixth catch in `measureSchema` deleted, `contentOf` returns the parsed node (`EndpointVerification.java:240-246`, `:263-281`) |
| D5 a missing, `null` or non-object top-level `harnesses` is `harness_store_unreadable` | shipped-as-specified | `HarnessEnrolmentStore.java:244-248` (re-read), `topLevelHarnessesRefused` and `emptyHarnessesObjectReadsAsEmptyStore` |
| docs and one ADR per decision | shipped-as-specified | both guides, ADR `runtime-launch/011`, Traceability PM-SEC-5 |
| the PR body lists the D1 duplication finding | shipped-modified | it names three helpers; only `listing` is duplicated (L2-4) |

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`, `orchestrator.analyze`) — reason: a full-ship landing is read by its own leaf with no size qualifier; the leaf wrote nothing.
- **Re-read by the orchestrator at the file and line** (all held): `HarnessEnrolmentStore.java:233-250`; `StartFailureGuard.java:204` and the `startFailed(` grep over `src/main` (one definition, no caller); `EndpointVerification.java:240-246` and `:290-313`, `EndpointHarness.java:124-139`; `AnswerSchema.java:105-116`, `:298-306`, the `KEYWORDS`/`ANNOTATIONS` declarations and guard order; `HarnessEnrolmentStoreTest.java:148-166`; `store-schemas/11-enrolment-store.adoc:54-58`; ADR `011` lines 41-56; `JsonDocument.java:62-80` and `JsonPointer.java` (every throw is `ProfileRefusal`); `AnswerSchemaTest.java:228-236`.
- **Not re-read, recorded as unverified leads:** `EndpointHarnessTest.java:274` and `EndpointVerificationTest.java:266`, `:279` (the `{` wire-literal fixtures and the missing pointer-half fixtures), `EndpointOutcome.java:168`, `:184` (the idle `IllegalArgumentException` arm), the 14 assertion-message lengths, the `minProperties` refutation (the leaf says it is in neither set; I saw `guardKeywords` refuse a member outside both sets, not the sets' contents).
- **Verdict:** layer 1 findings (2 minor, 2 info); layer 2 findings (3 minor, rest info); layer 3 no defect introduced (1 pre-existing minor, 2 minor disclosed). No blocker, no major.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L1-1 | D5 says the Unreadable Store paragraph and `required` of store-schemas/11 cover the top-level member; the paragraph lists only entry members, the root `harnesses` requirement lives only in `harnesses.schema.json`; the spec's "spec part not edited" rested on PLAN-V2-39 being staged | checked | refutes | `doc/specification/store-schemas/11-enrolment-store.adoc:56` |
| L1-2 | The Done-when still carries the two clauses of the dropped D1 and D4; only the rescope reconciles them | checked | confirms | spec Done-when |
| L1-3 | D3 names `ProfileRefusal | IllegalArgumentException` while its own hypothesis says only `ProfileRefusal` is thrown; the lane followed the evidence | checked | confirms | spec D3 |

No finding names a labelled claim beyond those the cleanup already settled; nothing is persisted by `corpus set-verdict`.

**Layer 1 verdict:** findings — 3

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| D2 | `AnswerSchema.java:68-89` | five parameterised refusals; order pinned only for type-before-const (`AnswerSchemaTest.java:232`), a swap of `guardKeywords` and `followReference` or of const and enum would pass (L2-1, minor) | none | checked (re-read) |
| D3 | the five narrowed sites | fixtures per site; the new listing fixtures use the body `{`, which exercises only the parse half, the `JsonPointer.resolve` half of both listing catches has no fixture and the literal carries no contract comment (L2-2, minor, unverified lead) | none | checked |
| D5 | `HarnessEnrolmentStore.java:244-248` | `topLevelHarnessesRefused` fails on the old code; the four documents are four inline calls in one `@Test`, not a `@ParameterizedTest`, and the message naming the member is not asserted (L2-3, minor, re-read) | none | checked |
| report | n/a | n/a | **L2-4** the PR body's follow-up lists three duplicated helpers; `modelIds` and `allowedModels` exist once (`EndpointVerification.java:305`, `:313`), only `listing` is duplicated (`:292-303`, `EndpointHarness.java:126-139`, differing in return type) (re-read); **L2-5** the narrative discloses a formatter commit `44f16e3a` on `pm-core` `QualificationSchemaParityTest` that the merge diff does not hold (the diff has no `pm-core` file); **L2-6** the single-parse change of `contentOf`/`measureSchema` is in neither a guide nor an ADR | checked |

**Layer 2 verdict:** findings — 6

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 (owed, **confirmed**) | `StartFailureGuard.java:204`, `HarnessVerifier.storeRefused` (`:155-159`) | a launch refused for `harness_store_unreadable` or `harness_tampered` | `startFailed(` has one definition and no caller in any `src/main`; the refusal path logs `WORKER_LAUNCH_REFUSED` and returns `Refused` without a guard call, so Store Schemas § Unreadable Store ("counts as a start failure") and the tampered-file clause are unmet in this repository for the new refusal and the old ones alike; whether `pm-mcp-server` wires it is not shown | minor |
| L3-2 (pre-existing) | `AnswerSchema.java:302` | `{minItems: 4294967296}` or `{minItems: 2.0}` | `integer()` casts a long to `int` and ignores a non-integer: the first reads as 0, the second as no bound, so an answer `[]` is valid; a `required`/`properties` value of the wrong kind is skipped by `elements()` (`:283`, unverified lead) | minor |
| L3-3 | `HarnessEnrolmentStore.java:249` (unverified lead) | `{format_version:1, harnesses:{}, x:1}` with a valid sidecar | an unknown root member is ignored although the schema says `additionalProperties:false`; ADR 011 states the limit itself (62-65) | minor |
| L3-4 | `HarnessEnrolmentStore.java:235`, `EndpointOutcome.java:168`, `:184` | the same parse | the `IllegalArgumentException` arm is dead for the parse: two idioms for one call | info |
| L3-5 | ADR `011` Alternatives (43-44) against Consequences (52-55) (re-read) | reading the ADR | the Alternatives justify the guard as catching "a truncated document", the Consequences say a truncated file never reaches `decode` | info |

The catch narrowing is sound (re-read): `JsonDocument.parse` wraps both Jackson exceptions as `ProfileRefusal`, `JsonPointer.resolve` throws only `ProfileRefusal`, so "no behaviour can change" holds. The lane's "`minProperties` passes unenforced" is refuted for the present code by the leaf (not re-read in the sets).

**Layer 3 not shown by reading:** the 11291 tests, the Sonar gate (98.2 % new-code coverage), the mutation evidence.

**Layer 3 verdict:** findings — 5 (no defect introduced; L3-1 is the owed finding the lane named)

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md § Code Standards | no generic `Exception`/`RuntimeException` caught or thrown | conforms | every added or narrowed catch is `ProfileRefusal`; older catches outside the diff remain |
| CLAUDE.md § Code Standards | `@ParameterizedTest` for three or more variants | **deviates** | `HarnessEnrolmentStoreTest.java:151` (re-read) |
| CLAUDE.md § Code Standards | literals only where the contract, said in a display name or comment | **deviates** (minor, unverified lead) | the `{` bodies at `EndpointHarnessTest.java:274`, `EndpointVerificationTest.java:266`, `:279` |
| CLAUDE.md § Code Standards | no new hand-written fake, assertion messages 20-60 characters, log records, `@Builder`, `@NullMarked` | conforms | leaf read, not re-read |
| CLAUDE.md § Module Documentation | a guide and one ADR per decision | conforms, with L2-6 | ADR 011, both guides |

## Metrics and Anomalies

- Tokens: `unknown`. Duration: not in the message; the run waited on CodeRabbit's quota in 90-minute cycles (the operator chose to keep waiting).
- Anomalies: the routed `-Ppre-commit` hit the daemon's 600 s bound and was re-run at 1800 s; `LspClientTest` failed twice under load (an untouched package) and passed in isolation; the `quarkus-alignment` check failed once on a transient 404 and passed on rerun.

## Routing and Merge Behavior

- Review: coderabbit required (quota-limited), sourcery optional (refused on its own budget). `runbook_lane review-gate --pr-number 494 --required-reviewer coderabbit` on the merged head reads `unresolved_review_threads: 0` and `review_body_findings_total: 10`: **both equal the landing's claim**. `review_body_findings_handled=10` is **re-verified from the lane's progress file**: ten `finding:` lines, `nitpick-1` to `nitpick-3` and `inline-1` to `inline-7` (the gate's own `handled: 0` reads a disposition form the file does not hold; a difference in form, not a false claim).
- CI/merge: merge queue, `bb8456d8`. Sonar: `sonar_open_new_issues=0`, `quality_gate_status=ok` claimed; not re-read here, so it stays the lane's claim.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#494`; `landing` stamped; `plan_marshall_plan_id` `rb-v2-46-runtime-endpoint-residues`
- [x] epic.md: the incomplete-landing Open Defect folded, the narrative entry added
- [x] the residue carried to PLAN-V2-114 item 56

## Follow-Ups

- L3-1 (the start-failure wiring) is an owed behaviour change: PLAN-V2-114 item 56(a), and its production caller is the assembly's if `pm-mcp-server` owns the launch path.
- The duplicated `listing`, the `KEYWORDS` refactor and the `integer()` bound are PLAN-V2-114 item 56.

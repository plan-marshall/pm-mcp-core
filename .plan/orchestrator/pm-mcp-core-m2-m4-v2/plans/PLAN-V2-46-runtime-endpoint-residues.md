# PLAN-V2-46: Close the residues of the endpoint packages: one limits bisection, a lower-complexity answer check, specific catches, one store decode

epic: pm-mcp-core-m2-m4-v2
workstream: WS-06
origin: PLAN-114 of the epic pm-mcp-core-m2-m4
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-46-runtime-endpoint-residues.md` and is queued as one row file, `queue/PLAN-V2-46.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r4`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R16, R17, R18, R19, R20. (R12 applies only if the trace matrix shows a message to add, see Deliverable 4; R14 and R16 apply because the plan changes `pm-runtime`, `HarnessEnrolmentStore` being a durable store reader; R15 is dropped, the plan does not change `pm-workflow`; R17 because the plan runs the links check.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-runtime/doc/developer/runtime-endpoint.adoc` (Deliverables 1 to 4) and `pm-runtime/doc/developer/runtime-launch.adoc` (Deliverable 5); ADR directories: `pm-runtime/doc/adr/runtime-endpoint/` and `pm-runtime/doc/adr/runtime-launch/` (the next free number of each); layout: workstream WS-05.
- R12 — log message holder: `RuntimeLogMessages` (`de.planmarshall.runtime.log`, prefix `PM_RUNTIME`), only if Deliverable 4 needs a record that `ENDPOINT_ATTEMPT_ENDED` (WARN 143) does not cover.

## Objective

PLAN-97 and PLAN-98 shipped the endpoint harness and the judgment endpoint and left five small residues in `pm-runtime`, package `runtime.endpoint` (and one in `runtime.launch`), for which no plan exists: the two `limits` bisections are two copies of one algorithm; `AnswerSchema.check` has a cognitive complexity of 25 against 15 allowed (SonarCloud `java:S3776`); six `catch (RuntimeException _)` clauses (forbidden in production code by `CLAUDE.md`) carry the generated `/*TODO: Catch specific not RuntimeException ...*/` comment that SonarCloud reports as six `java:S1135`; three optional review nits of #222 are open; and `HarnessEnrolmentStore.decode` reads a missing, `null` or non-object top-level `harnesses` member as an empty store although the schema requires it. Settle all of them in one pull request with no change of behaviour except the last one (a refusal where an empty map was returned). Together D2 and D3 clear 7 of the 9 open issues of `main`.

## Deliverables

1. **One bisection for the `limits` stage.** Extract the shared algorithm of `EndpointVerification.limits` and `JudgmentVerification.limits` (the bounds 1024 and 65 536 tokens, at most 8 probes, the midpoint step, the `best` clamp, the result `best >= MINIMUM_CONTEXT_TOKENS`) into one package-private class of `runtime.endpoint` (new, `LimitsBisection`), which takes the probe as a function returning one of three results: accepted with an optional measured token figure, too large, or another refusal that ends the stage with what is settled. The two stages keep their own probe: the chat endpoint treats every fate but `ANSWERED` as too large and counts with the endpoint's usage figure; the judgment endpoint takes only `400` with `max_tokens_exceeded` as too large, counts with `usage.input_tokens`, and ends the stage on any other refusal. The three duplicated constants (`MINIMUM_CONTEXT_TOKENS`, `MAXIMUM_CONTEXT_TOKENS`, `MAXIMUM_BISECTION_PROBES`) live once; `JudgmentVerificationTest` names `JudgmentVerification.MINIMUM_CONTEXT_TOKENS` and `MAXIMUM_CONTEXT_TOKENS` today, so its references move with them (pre-1.0: no alias). Test first: a `LimitsBisectionTest` (new) pins the three probe results, the probe bound, the clamp and the empty case (the first probe too large: `best` stays 0); the existing `EndpointVerificationTest` and `JudgmentVerificationTest` limits cases stay green unchanged except for the moved constants. The duplicated models-listing helpers (`listing`, `modelIds`, `allowedModels`, also in `EndpointHarness.listing`) are not part of this deliverable: list them in the pull request body as a finding.
2. **`AnswerSchema.check` below the complexity bound.** Split `AnswerSchema.check(JsonNode, JsonNode, JsonNode, String, int)` (cognitive complexity 25, `java:S3776`) into private steps of the same class (the depth and shape guard; the keyword and type guard; the `$ref` branch; the `const` and `enum` guard; the dispatch on the kind of the answer), each returning the `Check` it returns today. The order of the checks and every refusal message stay byte-identical: `AnswerSchemaTest` stays green unchanged, and the plan adds one case per refusal message it finds untested (read the test first, add only what is missing). The method is not suppressed (R16 (4) names the cases where the design requires the flagged shape; this one does not).
3. **Specific catches, no generated TODO.** Replace the six `catch (RuntimeException _)` clauses with the exceptions the guarded call throws, and delete the generated `TODO` comment above each: `AnswerSchema.resolve` (line 125), `EndpointHarness.listing` (line 142), `EndpointVerification.measureSchema`, `contentOf` and `listing` (lines 221, 254, 281) and `WeightsReader` (line 80). Read what `JsonDocument.parse` and `JsonPointer.resolve` throw (`JudgmentVerification.listing` and `HarnessEnrolmentStore.decode` already catch `ProfileRefusal | IllegalArgumentException`) and catch exactly that set; a catch that cannot name its exception is reported, not widened. Test first: a refusing fixture per catch (a malformed models listing, a malformed answer, a pointer that does not resolve, a malformed weights listing), the existing ones where they exist. `git grep "TODO: Catch specific"` over `src/main` of the three modules finds nothing in `pm-runtime` afterwards.
4. **The three review nits of #222.** (a) A task refused before any request is sent because the entity does not fit (`SystemOneRequest.compose` not composed, `JudgmentEndpoint` lines 99 to 106) logs nothing, while a refused harness logs `WORKER_LAUNCH_REFUSED` and a task that ended after a request logs `ENDPOINT_ATTEMPT_ENDED` (`appendRecord`, line 209: `answers.request() != null`): log `ENDPOINT_ATTEMPT_ENDED` for the composed-refusal too (model, endpoint, `composed.detail()`; never the task text) and keep the not-enrolled path logging once; a new `RuntimeLogMessages` record only if the text of 143 does not fit, then per R12 and in `doc/LogMessages.adoc`. Test with `cui-test-juli-logger`. (b) `JudgmentVerificationTest` has the `500` case (`limitsFailOnAnotherRefusal`) and the `400` with `max_tokens_exceeded` case, and no `400` without the marker: add it (the stage ends without a bound, `not_run`, the draft is not refused). (c) `SystemOneRequest.Refusal.wireName()` has no production caller (`JudgmentEndpoint` maps `ENTITY_EXCEEDS_BUDGET` to `INPUT_TOO_LARGE` and every other refusal to `REQUEST_REFUSED`; the strings `task_invalid` and `input_too_large` of the enum appear in no specification as a record value): remove the field and the accessor of `Refusal`, and `SystemOneRequestTest` keeps asserting the refusal constants.
5. **`HarnessEnrolmentStore.decode` refuses a missing top-level `harnesses`.** `decode` reads `root.member(HARNESSES).orElse(JsonNode.nullValue())` (line 248) and iterates `members()`, which is empty for `null`, a number, a string or an array: a document `{"format_version":1}` or `{"format_version":1,"harnesses":null}` reads as an empty store. `store-schemas/11` has `required: ["format_version", "harnesses"]` and the paragraph *Unreadable Store* refuses an unreadable store instead of reading it as an empty map. Refuse a missing, `null` or non-object `harnesses` with `Refusal.UNREADABLE` (`harness_store_unreadable`), with the message naming the member; `{"format_version":1,"harnesses":{}}` stays a valid empty store. The re-baseline path (`read(transaction, true)` returns an empty map for `UNREADABLE`) is unchanged. Test first in `HarnessEnrolmentStoreTest`: the four malformed documents of `assertUnreadable` and the valid empty object. The specification part `store-schemas/11-enrolment-store.adoc` is not edited (the schema already states the requirement; it belongs to the staged plan `machine-harness-verification`).

**Done when:** the `limits` algorithm exists once and both stages call it with their own probe, each stage's existing limits cases green; `AnswerSchema.check` is under the complexity bound with `AnswerSchemaTest` unchanged and green; no `TODO: Catch specific` remains in `pm-runtime` and each replaced catch has a refusing fixture; the three nits are closed as described; a missing, `null` or non-object top-level `harnesses` is `harness_store_unreadable`; the developer documentation and one ADR per design decision (the shared probe contract of D1; the removal of `Refusal.wireName`, D4c; the refusal of the top-level member, D5, in `runtime-launch`) are written; the pull request body lists the finding of D1 (the duplicated models-listing helpers). **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready; the 7 issues of `main` in this package (`java:S3776` at `AnswerSchema.java:60` and six `java:S1135`) are gone. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of `pm-runtime` (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and `verify -Pintegration-tests -pl pm-runtime -am` are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set:** PM-CLIENT-2 (Client Profiles § Judgment Endpoint and § Profile Verification, the `limits` stage); Store Schemas § Harness Enrolment Store, *Unreadable Store*. The leads are the analysis of #222 (landings/PLAN-98.md, Follow-Ups), of #214 (the `AnswerSchema` complexity and the six `java:S1135`) and the CodeRabbit lead of the PLAN-104 landing for D5.

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **Behaviour is unchanged by D1 to D3 and D4 (b), (c).** A refactor or a catch narrowing that needs a changed message, a changed order of checks or a changed result is a finding for the inbox message, not a change of this pull request. D4 (a) adds a log line, D5 adds a refusal; nothing else changes behaviour.
- **The request body is never logged** (PLAN-98 D6, ADR `runtime-endpoint/006`): the log line of D4 (a) carries the model, the endpoint and the reason, never the task text.
- **Log messages: none expected beyond a reuse of `ENDPOINT_ATTEMPT_ENDED`.** A new record, if the trace matrix finds one, goes into `RuntimeLogMessages` at the next free identifier of its level (ADR `doc/adr/005-one-log-message-holder-per-module.adoc`) and the plan says so in its inbox message.

## Claim Labels

- OBSERVED: `EndpointVerification.limits` and `JudgmentVerification.limits` are two bisections over the same bounds with the same three constants declared twice — `pm-runtime/src/main/java/de/planmarshall/runtime/endpoint/EndpointVerification.java` lines 46 to 52 and 178 to 198, `JudgmentVerification.java` lines 47 to 53 and 169 to 196; the only outside references to the constants are `JudgmentVerificationTest.java` lines 167, 184 and 185
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: both files read at a8da4104; `grep` of the three constant names over `pm-*/src` finds the two classes and `JudgmentVerificationTest` only
- OBSERVED: `java:S3776` is open at `AnswerSchema.java:60` (complexity 25 against 15) and `java:S1135` is open at `AnswerSchema.java:125`, `EndpointHarness.java:142`, `EndpointVerification.java:221`, `:254`, `:281` and `WeightsReader.java:80`; `branch=main` reads 9 open issues in all (these seven, `StoreDigest.java:97` `java:S9428`, `MacSecurity.java:305` `java:S112`)
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: `https://sonarcloud.io/api/issues/search?componentKeys=plan-marshall_pm-mcp-core&branch=main&resolved=false` read on 2026-10-29; `git grep "TODO: Catch specific"` finds exactly those six lines in `pm-runtime/src/main`
- OBSERVED: the generated comment sits above `catch (RuntimeException _)` in all six places, contrary to the `CLAUDE.md` rule against catching generic exceptions in production code; `JudgmentVerification.listing` (line 257) and `HarnessEnrolmentStore.decode` (line 239) already catch `ProfileRefusal | IllegalArgumentException` around the same parse calls
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: read at the six lines
- OBSERVED: the nits — no log for the composed refusal (`JudgmentEndpoint.java` lines 99 to 106 append the record and return without logging; the WARN of line 210 is guarded by `answers.request() != null`); no `400` fixture without the marker (`JudgmentVerificationTest.java` lines 171 to 202 hold the `400` with marker and the `500`); `SystemOneRequest.Refusal.wireName()` (lines 133 to 142) has no caller in `src/main`
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: `grep wireName` over `pm-runtime/src/main` finds no use of `SystemOneRequest.Refusal`'s accessor
- OBSERVED: `HarnessEnrolmentStore.decode` line 248 is `root.member(HARNESSES).orElse(JsonNode.nullValue())` and `JsonNode.members()` returns an empty map for any value that is not an object (`pm-core/.../profile/JsonNode.java` line 183); `store-schemas/11-enrolment-store.adoc` line 68 has `"required": ["format_version", "harnesses"]`; the test `malformedMembers` covers entry-level members only
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: read; the epic's line `:229` has moved to 248
- HYPOTHESIS: `JsonDocument.parse` and `JsonPointer.resolve` throw only `ProfileRefusal` and `IllegalArgumentException` — confirm/refute at the trace matrix (verify-at-outline); a third exception type is named in the pull request and caught by name
  - verdict: unverifiable | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: hypothesis, verify-at-outline by the executing plan
- HYPOTHESIS: the staged plan `machine-harness-verification` (PLAN-V2-39) may edit `HarnessEnrolmentStore` at its outline although its Expected Surface does not name it (its text asks that enrolling several harnesses in a scan loses no entry); D5 is one method and one test, so whichever plan merges second merges the base branch — confirm/refute at the trace matrix
  - verdict: unverifiable | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: hypothesis
- Verify-first clause: before editing, list the open pull requests for one that touches `pm-runtime/src/main/java/de/planmarshall/runtime/endpoint/` or `HarnessEnrolmentStore`, re-read the cited lines at the head (line numbers drift), and re-read the SonarCloud list of `main`; a refutation of any claim loops back to re-scope.
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: no package file and no index row names these residues; the live plans PLAN-46 and PLAN-105 do not name a class below

## Expected Surface

- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/endpoint/EndpointVerification.java` — `limits`, the constants, the two catches (D1, D3)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/endpoint/JudgmentVerification.java` — `limits`, the constants (D1)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/endpoint/AnswerSchema.java` — `check` split, `resolve` catch (D2, D3)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/endpoint/EndpointHarness.java` — `listing` catch (D3)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/endpoint/WeightsReader.java` — pointer catch (D3)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/endpoint/JudgmentEndpoint.java` — the composed-refusal log line (D4a)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/endpoint/SystemOneRequest.java` — `Refusal.wireName` removed (D4c)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/launch/HarnessEnrolmentStore.java` — `decode` (D5)
- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/endpoint/LimitsBisection.java` — new, the shared algorithm (D1)
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/endpoint/EndpointVerificationTest.java` — limits and catch fixtures
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/endpoint/JudgmentVerificationTest.java` — moved constants, the `400` without marker (D4b)
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/endpoint/AnswerSchemaTest.java` — unchanged cases, a refusal case per untested message
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/endpoint/EndpointHarnessTest.java` — malformed listing
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/endpoint/WeightsReaderTest.java` — pointer that does not resolve
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/endpoint/JudgmentEndpointTest.java` — the logged composed refusal
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/endpoint/SystemOneRequestTest.java` — refusal constants without the accessor
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/launch/HarnessEnrolmentStoreTest.java` — the four malformed top-level documents (D5)
- HYPOTHESIS: `pm-runtime/src/test/java/de/planmarshall/runtime/endpoint/LimitsBisectionTest.java` — new (D1)
- OBSERVED: `pm-runtime/doc/developer/runtime-endpoint.adoc` and `pm-runtime/doc/developer/runtime-launch.adoc` — developer documentation, extended
- HYPOTHESIS: `pm-runtime/doc/adr/runtime-endpoint/` and `pm-runtime/doc/adr/runtime-launch/` — one ADR each, the next free number of the directory

Not surface (R19): `doc/plans/README.adoc`, `doc/Traceability.adoc`, `doc/LogMessages.adoc`, the ADR directories and developer guides above as bookkeeping where they exist.

## Dependencies and Sequencing

- Depends on: PLAN-V2-83 (`skills-conformance-foundation`: the dependencies and rule R20); and then: nothing
- Overlaps with the live plans (class level, from the Expected Surface sections of `plans/`): none with PLAN-46 (its `pm-runtime/.../credentials/` entry names no class of this plan), PLAN-105, PLAN-V2-39 (its `pm-core/.../profile/` and `pm-runtime/.../worker/` classes; `HarnessEnrolmentStore` is not declared there, see the hypothesis above), PLAN-V2-43. No staged plan names a class of `runtime.endpoint`.
- Disjoint from the other drafts of this cluster: PLAN-V2-56 (`qualification-token-split`), PLAN-V2-65 (`corpus-schema-length-bounds`), PLAN-V2-67 (`main-hygiene-leftovers`), PLAN-V2-68 (`profile-wait-margin`) (no shared class). PLAN-V2-67 (`main-hygiene-leftovers`) touches `MacSecurity`, a class of the same module but of `runtime.credentials`.
- Adjacent to: the client contract and the assembly (`EndpointEnrolment`, `EndpointHarness`, `EndpointVerification` have no production caller in this repository; the callers are the onboarding units and `pm-mcp-server`), which stay untouched; the six generated comments are inserted by an OpenRewrite recipe of the parent POM, whose change is a pull request there and is not needed once the catches are specific.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-46-runtime-endpoint-residues.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Carried Open Items

Open items of the previous epic that this plan owns (register: `owner-register.md` of the epic; the full text is in the archived ledger `pm-mcp-core-m2-m4/epic.md`). They are not deliverables and extend no Done-when: read each while outlining, re-check it against `origin/main` (Claim Labels) and either adopt it into a deliverable of the outline or say in the landing why not.

- **PLAN-97 shipped 9 of 10; three residues (analysis of #214, 2026-10-28)** (Op3) — Sonar: java:S3776 in AnswerSchema.check (complexity 25 against 15) is a refactor of this plan's surface; the six java:S1135 issues are the generated TODO comments above the six catch (RuntimeException _) clauses that Deliverable 3 replaces: they disappear with it (decision 6, closed 2026-10-30: no parent-POM change); the unwired seams EndpointEnrolment, EndpointHarness and EndpointVerification are wired by the assembly (external E3)
- **HarnessEnrolmentStore.decode reads a missing, null or non-object top-level harnesses member as** (Op43) — HarnessEnrolmentStore.decode reads a missing, null or non-object top-level harnesses member as an empty map although store-schemas/11 requires it; refuse with harness_store_unreadable

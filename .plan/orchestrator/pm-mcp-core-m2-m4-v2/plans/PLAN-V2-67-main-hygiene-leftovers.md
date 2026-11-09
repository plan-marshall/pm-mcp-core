# PLAN-V2-67: Settle the two SonarCloud issues of main that no plan owns

epic: pm-mcp-core-m2-m4-v2
workstream: WS-10
origin: PLAN-111 of the epic pm-mcp-core-m2-m4
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-67-main-hygiene-leftovers.md` and is queued as one row file, `queue/PLAN-V2-67.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r4`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R13, R14, R16, R17, R18, R19, R20. (R14 because `StoreDigest` is in `core.store`; R16 because `MacSecurity` is in `pm-runtime`, and R16 (4) is the rule Deliverable 2 applies; R12 is dropped, no message is added; R15 is dropped; R17 because the plan runs the links check.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-store.adoc` (Deliverable 1) and `pm-runtime/doc/developer/runtime-credentials.adoc` (Deliverable 2), each only if the decision changes what the guide says; ADR directories: `pm-core/doc/adr/core-store/` and `pm-runtime/doc/adr/runtime-credentials/` (the next free number of each); layout: workstream WS-02.

## Objective

SonarCloud reads 9 open issues on `main`. Seven are in `pm-runtime` `runtime.endpoint` and belong to the staged plan PLAN-V2-46 (`runtime-endpoint-residues`); the other two belong to no plan: `java:S9428` at `StoreDigest.java:97` ("Make this MessageDigest a local variable") and `java:S112` at `MacSecurity.java:305` ("Replace generic exceptions"). The epic recorded that both need either a dismissal, for which this checkout holds no SonarCloud write credential, or a design change. Both can be settled in the code with a stated reason beside the declaration, which the Sonar analysis honours and which needs no credential. Added 2026-11-01 (operator, from the three-layer analysis of PLAN-V2-01, `landings/PLAN-V2-01.md` C2): the five `@SuppressWarnings` of the null rules that PLAN-V2-01 left in `PrimitiveResult` and `PrimitiveRegistry` are the only ones of their kind in the repository (every other site was fixed by PLAN-V2-110); Deliverable 4 removes them by fixing the sites, as a later cleanup. The same pull request clears the stale name `pm-provider-ci`, a module the HTTP move (#156) removed, from the places no other plan owns.

## Deliverables

1. **`StoreDigest.Builder`: S9428 — DROPPED 2026-10-30, settled by PLAN-V2-109 (#250, merge `03f182c9`, ADR `pm-core/doc/adr/core-store/052`).** The inline `@SuppressWarnings("java:S9428")` with the reason and the one-thread Javadoc are on `main`; the analysis honours the suppression (confirmed on the pull request). Nothing of D1 is left to do and its ADR is written; the paragraph below is kept as the record of the reasoning only. Original text: `StoreDigest.Builder` holds `private final MessageDigest digest = StoreSnapshot.newSha256();` (line 97) because the builder *is* the incremental digest: `JournalRecovery` (lines 216 and 222) and `ScopeTransaction` (line 396) feed it chunks over several calls and read it once at `build()`, with a bounded buffer for files and no chunk kept (its Javadoc states it). A local variable would force the builder to keep its chunks, which changes the documented bounded-memory property and reads a file at `build()` instead of at `update`. Settle the issue by an inline `@SuppressWarnings("java:S9428")` on the field with the reason beside it ("the builder is the incremental digest of one thread; chunks are not kept"), in the style `QueueTaskRecordCodec` and `ActivityEntryCodec` use for `java:S3776`. Record the choice between this and the redesign (a `StoreDigest.compute(consumer)` that keeps the digest local and moves the three call sites) in an ADR of `core-store`. No behaviour changes; `StoreDigestTest` stays green unchanged, and the Javadoc of the builder says it is confined to one thread.
2. **`MacSecurity.Downcall`: S112.** `private interface Downcall<T> { T call() throws Throwable; }` (line 305) mirrors `MethodHandle.invokeExact`, which declares `Throwable`; the interface cannot declare less and still compile every call site, and `call(Downcall)` already maps the failure and carries the inline `@SuppressWarnings("java:S1181")` for the same reason. R16 (4) is the rule: suppress inline at the declaration with the reason beside it rather than reshape the design. Add `@SuppressWarnings("java:S112")` with the reason to the interface, record the decision in an ADR of `runtime-credentials`. The class is OS-specific and has no unit test (`@EnabledOnOs`); no test changes, and the descriptor registration (`reachability-metadata.json`, R16 (1)) is untouched.

4. **The five null-rule suppressions of `core.spi` (added 2026-11-01; a later code cleanup, run last).** `PrimitiveResult` (compact constructor, `@SuppressWarnings({"java:S2583", "java:S2789", "java:S1905"})` at line 57; `Builder` constructor, `"java:S2637"` at line 121) and `PrimitiveRegistry` (`declaredReasonType` line 214, `{S2583, S2789, S1905}`; `isReasonWireName` line 273, `S2589`; `conditionKindOf` line 290, `{S2583, S2789}`), all at `44cfe8d7`. They guard against a raw-typed implementor of `PrimitiveOutcome` or `OutcomeReason` that answers `null` or a payload of the wrong type; the package is `@NullMarked`, so Sonar reads the checks as dead code. Remove each suppression by fixing the site, keeping the named `PrimitiveRegistrationException` refusal: read the untrusted value into an `Object` and pattern-match it (`Object raw = outcome.reasonType(); if (!(raw instanceof Optional<?> declared)) refuse`; `raw instanceof String` for the wire name; the same for the condition kind), which has no `null` comparison and no cast; declare the builder field `private @Nullable R outcome;` and keep `Objects.requireNonNull(outcome, "outcome")` in `build()`. Test first: the fixtures that return `null` or a non-`Class` payload (`ReasonPrimitives` `test.reason-null`, `test.reason-not-class`, `test.reason-null-condition`, `test.reason-unnamed`; `PrimitiveResultTest.refusesNonClassReasonType`) must stay green and keep their refusal reason; add the missing case for a `null` `reasonType()` at the result constructor if the outline finds none. **Re-count before editing:** PLAN-V2-97 (the `Optional` record components of `core.spi` become `@Nullable`, which rewrites `PrimitiveResult.reason` and `spawnedJobId`) and PLAN-V2-100 (a record of four or more components carries Lombok `@Builder`, which replaces the hand-written `Builder`) change these very sites; run this deliverable after both, and drop every suppression that has gone by then.

**Done when:** `java:S112` is gone from the SonarCloud list of the pull request without a dismissal in the SonarCloud UI (the inline suppression carries the reason; `java:S9428` and its ADR are PLAN-V2-109's, already on `main`); for Deliverable 4 `git grep -n -E 'java:S(2583|2589|2637|2789)' -- '*.java'` finds no suppression in `pm-core/src/main/java/de/planmarshall/core/spi/` and the SonarCloud search of the pull request lists none of those rules; the `runtime-credentials` ADR is written; `verify`, `verify -Ppre-commit` (its rewrites reviewed and committed), `verify -Pcoverage` and both integration profiles are green. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (`pm-core`, `pm-runtime`; where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and the integration tests of every changed module with tests (`verify -Pintegration-tests -pl <module> -am`, only on `pm-core` and `pm-runtime`) are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set:** no requirement is implemented (hygiene); the leads are the epic's Open Defects "Two SonarCloud items of PLAN-94 are unsettled" (`landings/PLAN-94.md`, `landings/PLAN-87.md`) and "`pm-provider-ci` still appears where the HTTP move could not reach" (`landings/PLAN-86.md`).

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **Read the landing analysis of PLAN-V2-110 at the outline** (`landings/PLAN-V2-110.md` I2, owner-register Wa60; decide at refine, R07): `PrimitiveRegistry.java:214,273,290` suppress S2583/S2589/S2789/S1905 (added by #271; `:273` has no reason on the annotation) and `PrimitiveResult.Builder:121` suppresses S2637 for a field that is not an external type; D4 takes them with the five suppressions of Wa55. The expected surface is unchanged.
- **Default and its alternative.** The default settlement of D1 and D2 is the inline suppression with a stated reason. If the operator prefers the redesign of `StoreDigest.Builder`, the plan's Expected Surface grows by `JournalRecovery`, `ScopeTransaction` and their tests; ask at refine (R07), do not decide silently.
- **Deliverable 4 is the opposite stance to Deliverable 2, on purpose.** D2 suppresses inline because `MethodHandle.invokeExact` declares `Throwable` and the interface cannot declare less (R16 (4)). D4 fixes the sites because the operator decided on 2026-10-31 to fix the dead null checks, not to ignore S2583, S2589 and S2637 (`epic.md` Decisions). Default: the pattern-match and `@Nullable` fix above, which keeps the named refusal. The alternative, the operator's at refine (R07): delete the raw-type checks, so a bad primitive gets a `NullPointerException` instead of a named refusal; ask, do not decide silently. Unverified: that Sonar clears the rule on the pattern-match form (the plan verifies it on its own pull request, R18).
- **No credential, no UI dismissal.** The plan does not call the SonarCloud write API; a dismissal in the UI is not part of it.

## Claim Labels

- OBSERVED: `branch=main` reads 9 open issues; the two of this plan are `java:S9428` at `pm-core/.../store/StoreDigest.java:97` and `java:S112` at `pm-runtime/.../credentials/MacSecurity.java:305`; `java:S5778` (`ProfileValidatorTest`) and `java:S9412` (`LockKeyTest`) are no longer listed (fixed by #185, PLAN-104)
  - verdict: contradicted | checked_at: 363ecb48 | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: yes | evidence: java:S9428 suppressed at StoreDigest.java:98 with reason (#250); only java:S112 at MacSecurity.java:305 open in code; covered by the rescope note
- OBSERVED: `StoreDigest.Builder` is used incrementally — `JournalRecovery.java` lines 216 and 222, `ScopeTransaction.java` line 396 (`StoreDigest.builder()` then `update` calls); `StoreDigestTest.java` exists; the module already suppresses Sonar rules inline with a reason (`QueueTaskRecordCodec.java:303`, `ActivityEntryCodec.java:197`, `ActivityEntry.java:330`, `JsonlStoreFile.java:176`)
  - verdict: corroborated | checked_at: 363ecb48 | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: StoreDigest.builder() used at JournalRecovery.java:216,222 and ScopeTransaction.java:432-440; StoreDigestTest exists
- OBSERVED: `MacSecurity.call` already carries `@SuppressWarnings("java:S1181")` with the reason "MethodHandle.invokeExact declares Throwable; Errors are rethrown" (line 310); R16 (4) names the inline suppression for the cases where the design requires the flagged shape
  - verdict: corroborated | checked_at: 363ecb48 | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: MacSecurity.java:308 suppression java:S1181 with reason; interface at :304-306
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: `git grep` and `grep -c` at a8da4104; the epic's line numbers (`:129`, `:141`) have moved to 99
- HYPOTHESIS: the Sonar analysis honours `@SuppressWarnings("java:S9428")` and `("java:S112")` as it does the `java:S3776` and `java:S1181` ones already in the code — confirm/refute at the run of the pull request (R18); if S9428 is not honoured the redesign of the constraint above is the fallback and the operator is asked
  - verdict: unverifiable | checked_at: 363ecb48 | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: needs a Sonar run on the pull request; suppressions of S3776 and S1181 exist in code
  - verdict: unverifiable | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: hypothesis
- OBSERVED (Deliverable 4): `git grep -n -E 'java:S(2583|2589|2637|2789)' origin/main -- '*.java'` lists exactly five sites, all in `pm-core/.../core/spi/` (`PrimitiveRegistry.java` 214, 273, 290; `PrimitiveResult.java` 57, 121), read at `44cfe8d7` on 2026-11-01; the root `pom.xml` ignores only `java:S4032` and `java:S4449`
  - verdict: contradicted | checked_at: 363ecb48 | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: yes | evidence: sixth java:S2637 suppression RefusingFS.java:53 in pm-provider-git beside the five core.spi sites; see Grounding corrections
- Verify-first clause: before editing, list the open pull requests for one that touches `StoreDigest.java`, `MacSecurity.java` or `.plan/project-architecture/`, and re-read the Sonar list of `main` (a plan that landed meanwhile may have cleared or added one); a refutation loops back to re-scope.
  - verdict: corroborated | checked_at: 363ecb48 | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: no open PR touches StoreDigest.java or MacSecurity.java

## Expected Surface

- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/credentials/MacSecurity.java` — the `Downcall` interface (D2)
- HYPOTHESIS: `pm-runtime/doc/adr/runtime-credentials/` — one ADR, the next free number of the directory (the `core-store` ADR and `StoreDigest.java` of D1 left with PLAN-V2-109)

- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/spi/PrimitiveResult.java` and `PrimitiveRegistry.java` — the five suppressions (D4)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/spi/PrimitiveResultTest.java`, `PrimitiveRegistryTest.java`, `ReasonPrimitives.java` — the refusal fixtures that must stay green (D4)

Not surface (R19): `doc/plans/README.adoc`, `doc/Traceability.adoc`, the ADR directories and developer guides above as bookkeeping where they exist.

## Dependencies and Sequencing

- Depends on: PLAN-V2-83 (`skills-conformance-foundation`: the dependencies and rule R20); and then: nothing
- **Deliverable 4 runs last in the conformance wave:** after PLAN-V2-110 (it shares `PrimitiveRegistry.java`), PLAN-V2-97 and PLAN-V2-100 (they rewrite `PrimitiveResult`'s `Optional` components and builder). Until those have landed the plan may be emitted for Deliverable 2 alone; if it is, D4 is cut out and re-staged, not run early. PLAN-V2-19, 20 and 25 build on `PrimitiveResult` and the registry as well: re-read the surface at refine.
- Overlaps with the live plans (class level): none with a declared class. PLAN-46 declares the directory `pm-runtime/src/main/java/de/planmarshall/runtime/credentials/` as a HYPOTHESIS entry (the provider verification and any change to the delivered credential store): `MacSecurity` is in it, one interface of one class; D2 is an annotation. If the orchestrator reads a directory hypothesis as an overlap, emit this plan after PLAN-46 lands; D1 and D3 are free regardless. PLAN-V2-72 `forgejo-provider` (parked) owns `doc/plans/forgejo-provider.adoc` and the row of the index, which this plan leaves alone.
- Disjoint from the other drafts of this cluster (no shared class): PLAN-V2-46 (`runtime-endpoint-residues`) (the seven Sonar issues of `runtime.endpoint`), PLAN-V2-56 (`qualification-token-split`) (`core.store` `AttemptEnd`, `UsageAggregate`, other classes), PLAN-V2-65 (`corpus-schema-length-bounds`), PLAN-V2-68 (`profile-wait-margin`).
- Adjacent to: the parent POM (the OpenRewrite recipe that inserts the `TODO` comments is its, and is not needed once PLAN-V2-46 (`runtime-endpoint-residues`) has narrowed the catches).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-67-main-hygiene-leftovers.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.


> **Deliverable 3 dropped 2026-10-29 (operator decision 2 of 18):** the module `pm-provider-ci` is re-added as a contract module (PLAN-V2-12 `ci-contract-module`), so the name is no longer stale and its derived state is regenerated by that plan; the three surface entries and the two claims of D3 went with it.

## Carried Open Items

Open items of the previous epic that this plan owns (register: `owner-register.md` of the epic; the full text is in the archived ledger `pm-mcp-core-m2-m4/epic.md`). They are not deliverables and extend no Done-when: read each while outlining, re-check it against `origin/main` (Claim Labels) and either adopt it into a deliverable of the outline or say in the landing why not.

- **PLAN-98 residues (analysis of #222, 2026-10-29)** (Op1) — the repository-wide architecture rule and the recording-test-network test of the Closed List of Outbound Calls belong to V2-53; the small residues (the two limits bisections as one helper, a WARN for a pre-send refusal, a negative fixture for a 400 without max_tokens_exceeded, the unused TASK_INVALID.wireName()) go to V2-67
- **Two SonarCloud items of PLAN-94 are unsettled, and 2 further issues of main are owned by no pla** (Op27) — main: java:S112 at MacSecurity (the downcall must declare throws Throwable: dismissal is the only settlement), java:S5778 at ProfileValidatorTest, java:S9428 at StoreDigest; re-query before editing

## Rescope and carries (2026-11-09)

Added by the orchestrator after the operator's walk-through of the open defects (decision log, 2026-11-09). Read at the outline, re-check each statement against `origin/main` (Claim Labels) and adopt it into a deliverable of the outline or say in the landing why not. It extends no Done-when by itself.

- **Narrowed (2026-11-09):** the SonarCloud item `java:S9428` at `StoreDigest.java:96` is settled on main (`@SuppressWarnings("java:S9428")` with a reason, commit `03f182c9`, #250); remove it from the scope and from the Done-when. What remains is `java:S112` at `MacSecurity.java:305` (`T call() throws Throwable;` mirrors `MethodHandle.invokeExact`; a code-side `@SuppressWarnings` with its reason) and the note that `sonar` is `not_configured` for the lane. Live Sonar state was not queried (HYPOTHESIS, verify-at-outline).

## Grounding corrections (cleanup 2026-11-09, main 363ecb48)

Claim 4: main has a SIXTH `java:S2637` suppression, `pm-providers/pm-provider-git/.../RefusingFS.java:53` (added by #274, with a library-contract reason comment), beside the five `core.spi` sites; the Objective's wording that they are the only ones of their kind is false. D4's grep is scoped to `core/spi`, so its Done-when is unaffected: the outline decides whether `RefusingFS` is in scope or deliberately left, and records it.

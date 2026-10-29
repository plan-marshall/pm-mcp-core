# PLAN-111: Settle the two SonarCloud issues of main that no plan owns and the stale pm-provider-ci naming

epic: pm-mcp-core-m2-m4
workstream: WS-02
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-111-main-hygiene-leftovers.md` and is queued as one row file, `queue/PLAN-111.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r2`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R13, R14, R16, R17, R18, R19. (R14 because `StoreDigest` is in `core.store`; R16 because `MacSecurity` is in `pm-runtime`, and R16 (4) is the rule Deliverable 2 applies; R12 is dropped, no message is added; R15 is dropped; R17 because the plan runs the links check.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-store.adoc` (Deliverable 1) and `pm-runtime/doc/developer/runtime-credentials.adoc` (Deliverable 2), each only if the decision changes what the guide says; ADR directories: `pm-core/doc/adr/core-store/` and `pm-runtime/doc/adr/runtime-credentials/` (the next free number of each); layout: workstream WS-02.

## Objective

SonarCloud reads 9 open issues on `main`. Seven are in `pm-runtime` `runtime.endpoint` and belong to the staged plan PLAN-114 (`runtime-endpoint-residues`); the other two belong to no plan: `java:S9428` at `StoreDigest.java:97` ("Make this MessageDigest a local variable") and `java:S112` at `MacSecurity.java:305` ("Replace generic exceptions"). The epic recorded that both need either a dismissal, for which this checkout holds no SonarCloud write credential, or a design change. Both can be settled in the code with a stated reason beside the declaration, which the Sonar analysis honours and which needs no credential. The same pull request clears the stale name `pm-provider-ci`, a module the HTTP move (#156) removed, from the places no other plan owns.

## Deliverables

1. **`StoreDigest.Builder`: S9428.** `StoreDigest.Builder` holds `private final MessageDigest digest = StoreSnapshot.newSha256();` (line 97) because the builder *is* the incremental digest: `JournalRecovery` (lines 216 and 222) and `ScopeTransaction` (line 396) feed it chunks over several calls and read it once at `build()`, with a bounded buffer for files and no chunk kept (its Javadoc states it). A local variable would force the builder to keep its chunks, which changes the documented bounded-memory property and reads a file at `build()` instead of at `update`. Settle the issue by an inline `@SuppressWarnings("java:S9428")` on the field with the reason beside it ("the builder is the incremental digest of one thread; chunks are not kept"), in the style `QueueTaskRecordCodec` and `ActivityEntryCodec` use for `java:S3776`. Record the choice between this and the redesign (a `StoreDigest.compute(consumer)` that keeps the digest local and moves the three call sites) in an ADR of `core-store`. No behaviour changes; `StoreDigestTest` stays green unchanged, and the Javadoc of the builder says it is confined to one thread.
2. **`MacSecurity.Downcall`: S112.** `private interface Downcall<T> { T call() throws Throwable; }` (line 305) mirrors `MethodHandle.invokeExact`, which declares `Throwable`; the interface cannot declare less and still compile every call site, and `call(Downcall)` already maps the failure and carries the inline `@SuppressWarnings("java:S1181")` for the same reason. R16 (4) is the rule: suppress inline at the declaration with the reason beside it rather than reshape the design. Add `@SuppressWarnings("java:S112")` with the reason to the interface, record the decision in an ADR of `runtime-credentials`. The class is OS-specific and has no unit test (`@EnabledOnOs`); no test changes, and the descriptor registration (`reachability-metadata.json`, R16 (1)) is untouched.
3. **`pm-provider-ci` where no other plan owns it.** The module is gone from the build and the code (#156; `pm-providers/pm-provider-ci/` holds only an untracked `target/`). Remove the name where it is stale and no live plan owns the file: `doc/plans/access-grants.adoc` line 18 ("the provider reads themselves (the provider packages and `pm-provider-ci`)": drop the name), and the derived state `.plan/project-architecture/`, which is tracked and still describes the module — the directory `pm-provider-ci/` and the mentions in `_project.json` (2), `pm-providers/enriched.json`, `pm-provider-github/enriched.json` (2), `pm-provider-gitlab/enriched.json` (2), `pm-provider-sonar/enriched.json` (2) and `guard-control-provider-product-module/enriched.json`. Regenerate it with the architecture tooling the repository is set up with (`plan-marshall:manage-architecture`) and review the diff; where the tool is not available to the lane, delete the directory and edit the mentions by hand and say so in the pull request body (the file set is the same). Left to the staged plan `forgejo-provider` (it owns the files): `doc/plans/forgejo-provider.adoc` line 14 and the `forgejo-provider` row of `doc/plans/README.adoc` (line 99, "the CI contract of `pm-provider-ci` (no work package)"); list both in the pull request body, with the finding that the row names a blocker that is no module any more. The two ADR mentions (`doc/adr/002`, `provider-http/001`) name the module as history and stay. Test: `git grep pm-provider-ci -- doc/plans .plan/project-architecture` finds the two lines left to `forgejo-provider` and nothing else.

**Done when:** `java:S9428` and `java:S112` are gone from the SonarCloud list of the pull request without a dismissal in the SonarCloud UI (the inline suppressions carry the reasons); the two ADRs are written; the grep of Deliverable 3 finds only the two lines left to `forgejo-provider`; `verify`, `verify -Ppre-commit` (its rewrites reviewed and committed), `verify -Pcoverage` and both integration profiles are green. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (`pm-core`, `pm-runtime`; where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and the integration tests of every changed module with tests (`verify -Pintegration-tests -pl <module> -am`, only on `pm-core` and `pm-runtime`) are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set:** no requirement is implemented (hygiene); the leads are the epic's Open Defects "Two SonarCloud items of PLAN-94 are unsettled" (`landings/PLAN-94.md`, `landings/PLAN-87.md`) and "`pm-provider-ci` still appears where the HTTP move could not reach" (`landings/PLAN-86.md`).

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **Default and its alternative.** The default settlement of D1 and D2 is the inline suppression with a stated reason. If the operator prefers the redesign of `StoreDigest.Builder`, the plan's Expected Surface grows by `JournalRecovery`, `ScopeTransaction` and their tests; ask at refine (R07), do not decide silently.
- **No credential, no UI dismissal.** The plan does not call the SonarCloud write API; a dismissal in the UI is not part of it.

## Claim Labels

- OBSERVED: `branch=main` reads 9 open issues; the two of this plan are `java:S9428` at `pm-core/.../store/StoreDigest.java:97` and `java:S112` at `pm-runtime/.../credentials/MacSecurity.java:305`; `java:S5778` (`ProfileValidatorTest`) and `java:S9412` (`LockKeyTest`) are no longer listed (fixed by #185, PLAN-104)
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: `https://sonarcloud.io/api/issues/search?componentKeys=plan-marshall_pm-mcp-core&branch=main&resolved=false&ps=500` read on 2026-10-29; `StoreDigest.java:97` and `MacSecurity.java:305` read at a8da4104
- OBSERVED: `StoreDigest.Builder` is used incrementally — `JournalRecovery.java` lines 216 and 222, `ScopeTransaction.java` line 396 (`StoreDigest.builder()` then `update` calls); `StoreDigestTest.java` exists; the module already suppresses Sonar rules inline with a reason (`QueueTaskRecordCodec.java:303`, `ActivityEntryCodec.java:197`, `ActivityEntry.java:330`, `JsonlStoreFile.java:176`)
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: `grep` over `pm-core/src/main`
- OBSERVED: `MacSecurity.call` already carries `@SuppressWarnings("java:S1181")` with the reason "MethodHandle.invokeExact declares Throwable; Errors are rethrown" (line 310); R16 (4) names the inline suppression for the cases where the design requires the flagged shape
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: `MacSecurity.java` lines 299 to 320 read
- OBSERVED: `pm-provider-ci` appears at `doc/plans/README.adoc:99`, `doc/plans/forgejo-provider.adoc:14`, `doc/plans/access-grants.adoc:18`, two ADR sentences that name it as history, and in seven tracked files of `.plan/project-architecture/` (`git ls-files` lists 22 files there; `grep -c` finds the name in `_project.json` 2, `pm-provider-ci/enriched.json` 1, `pm-providers` 1, `pm-provider-github` 2, `pm-provider-gitlab` 2, `pm-provider-sonar` 2, `guard-control-provider-product-module` 1); `git ls-files pm-providers/pm-provider-ci` lists nothing
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: `git grep` and `grep -c` at a8da4104; the epic's line numbers (`:129`, `:141`) have moved to 99
- HYPOTHESIS: the Sonar analysis honours `@SuppressWarnings("java:S9428")` and `("java:S112")` as it does the `java:S3776` and `java:S1181` ones already in the code — confirm/refute at the run of the pull request (R18); if S9428 is not honoured the redesign of the constraint above is the fallback and the operator is asked
  - verdict: unverifiable | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: hypothesis
- HYPOTHESIS: the tool `plan-marshall:manage-architecture` is available to the runbook lane and rewrites `.plan/project-architecture/` without touching other files — confirm/refute at the outline; the fallback is the hand edit named in Deliverable 3
  - verdict: unverifiable | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: hypothesis
- Verify-first clause: before editing, list the open pull requests for one that touches `StoreDigest.java`, `MacSecurity.java` or `.plan/project-architecture/`, and re-read the Sonar list of `main` (a plan that landed meanwhile may have cleared or added one); a refutation loops back to re-scope.
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: no package file and no index row; no live spec names the three classes or files

## Expected Surface

- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/StoreDigest.java` — `Builder` field and Javadoc (D1)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/credentials/MacSecurity.java` — the `Downcall` interface (D2)
- OBSERVED: `doc/plans/access-grants.adoc` — line 18 (D3)
- OBSERVED: `.plan/project-architecture/_project.json` and `.plan/project-architecture/{pm-providers,pm-provider-github,pm-provider-gitlab,pm-provider-sonar,guard-control-provider-product-module}/enriched.json` — the mentions (D3)
- OBSERVED: `.plan/project-architecture/pm-provider-ci/enriched.json` — deleted (D3)
- HYPOTHESIS: `pm-core/doc/adr/core-store/` and `pm-runtime/doc/adr/runtime-credentials/` — one ADR each, the next free number of the directory

Not surface (R19): `doc/plans/README.adoc`, `doc/Traceability.adoc`, the ADR directories and developer guides above as bookkeeping where they exist.

## Dependencies and Sequencing

- Depends on: nothing
- Overlaps with the live plans (class level): none with a declared class. PLAN-46 declares the directory `pm-runtime/src/main/java/de/planmarshall/runtime/credentials/` as a HYPOTHESIS entry (the provider verification and any change to the delivered credential store): `MacSecurity` is in it, one interface of one class; D2 is an annotation. If the orchestrator reads a directory hypothesis as an overlap, emit this plan after PLAN-46 lands; D1 and D3 are free regardless. PLAN-49 `forgejo-provider` (parked) owns `doc/plans/forgejo-provider.adoc` and the row of the index, which this plan leaves alone.
- Disjoint from the other drafts of this cluster (no shared class): PLAN-114 (`runtime-endpoint-residues`) (the seven Sonar issues of `runtime.endpoint`), PLAN-113 (`qualification-token-split`) (`core.store` `AttemptEnd`, `UsageAggregate`, other classes), PLAN-110 (`corpus-schema-length-bounds`), PLAN-112 (`profile-wait-margin`).
- Adjacent to: the parent POM (the OpenRewrite recipe that inserts the `TODO` comments is its, and is not needed once PLAN-114 (`runtime-endpoint-residues`) has narrowed the catches).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-111-main-hygiene-leftovers.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

envelope_version=1
sender_type=plan
sender_id=rb-v2-85-core-store-tests-a
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-05T16:03:23Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-85-core-store-tests-a
epic=pm-mcp-core-m2-m4-v2
pr=#298
merge_state=merged
cleanup_owed=false
deliverables_total=2
deliverables_done=2
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2041
steps=setup:done,implement:done,build-gate:done,self-review:budget-exhausted,pr:done,reviews:done,merge:done
step.sonar-roundtrip.open_issue_count=0
step.sonar-roundtrip.open_issue_rules=none
```

guard: none on this harness (unenforced)

Standing rules: r6 (the spec names r4; the file read is r6), rules R02, R06, R08, R11, R17, R18, R19 (replaced by the spec's Standing Constraint), R20.

## 1. Deliverables

**D1 — generated test data in every class of the first half. Met (form), with the residuals in §9.**
- 54 in-scope classes: 45 carry `@EnableGeneratorController` and at least one `Generators.*` call; 9 carry the per-datum-true one-line contract statement (`AttemptEndParityTest`, `ContinuationMarkParityTest`, `DerivedFactsSchemaParityTest`, `EarsFormTest`, `EnrolmentCodecTest`, `FailingAuditSink`, `JobIdTest`, `JobRecordSchemaParityTest`, `LessonFrontMatterSchemaParityTest`).
- Evidence order `none` (the pre-existing class suite is the guard): baseline full `pm-core` suite 4338 tests, 0 failures at `11ef1250` (`maven-2026-11-05-064803.log`); after: 4356 tests, 0 failures at `d9459b4c`. No failing-output run was captured, so no fail-first claim is made.
- Test-method counts: no `@Test`/`@ParameterizedTest` removed or renamed; the contract test adds 4.
- Generated values drawn per test where they were class-init constants; identifiers normalised to their grammar (`project-`/`plan-`/`ent-<8 hex>`, lowercase); round-trip punctuation restored; order-pinning literals kept.
- Commits: `9f41fe78`, `79ced3c6`, `3b36e90c`, `79352db5`, `0a1148f0`, `d2ba1dc9`, `0c6dad21`, `9af96104`, merge `d9459b4c` → merged as `59fee682`.

**D2 — value-object contract test. Met with one documented override.**
- New `JsonlStoreFileContractTest` implements `ShouldHandleObjectContracts<JsonlStoreFile.Line>`; order `none`; 4 contract tests green.
- The interface's `shouldImplementSerializable` contract is deliberately inverted: `Line` is an in-memory value and its persistence contract is the JSONL wire format; making it `Serializable` would be a production change outside this plan.
- `ScopeTransaction.PendingTarget` is the only other hand-written `equals` in the store package; it is in the second half and is explicitly PLAN-V2-86's D2 (`ScopeTransactionContractTest`).

Per-head test runs: full `pm-core` suite at `9af96104` (4356 tests) and at `d9459b4c` (4356 tests); store-package suite 2122 tests at each reviewed head; integration `pm-core` 21 tests and `pm-runtime` 38 tests at `9af96104`; integration `pm-core` 21 / `pm-runtime` 38 at `d9459b4c`.

## 2. Personas and skills

- Persona: `persona-module-tester` applied as the standard for every deliverable (unit/module test work); `persona-code-reviewer` applied in the four self-review rounds and in triaging every PR comment.
- Skills: the spec declares none (`skills: none named`); the required skill reading was `cui-testing`, `junit-core`, `persona-module-tester`, `java-core`, `java-null-safety`, `java-lombok`. No surface-drift skill was added; the change stayed inside the declared test package.

## 3. Hypotheses

- OBSERVED "89 files in the store test package" — **refuted**: `git ls-files` shows 112 files at `origin/main` (`cui-testing` migration plans landed after the audit). The name-sorted scope `≤ LockOrderTest` is 54 files; the second half is PLAN-V2-86.
- OBSERVED "0 `Generators` calls in `pm-core` tests" — **refuted**: 13 files already called `Generators` at `origin/main` (57 call lines).
- OBSERVED "`JsonlStoreFile.java` line 106 declares `equals`/`hashCode`" — **confirmed**: `JsonlStoreFile.Line.equals` at 106, `hashCode` at 112; no contract test existed.
- OBSERVED "~8140 string literals" — not re-measured mechanically; the per-class quote-line figures in the PR are a labelled proxy (they include display names and messages).

## 4. Findings

- Self-review round 1: 12 findings. Fixed: 5 out-of-scope files reverted; round-trip punctuation; false contract comments; entry-id grammar; duplicate markers. Open: messages, seeds, `@GeneratorsSource`, generator reuse, partial literals.
- Self-review round 2: fixes confirmed; new lowercase/hex grammar findings in `AuditSchemaParityTest`/`AuditLogSinkTest` → fixed `79352db5`.
- Self-review round 3: round limit reached with the five residuals open; operator asked and chose "Continue fixing the open items".
- Self-review round 4: mass pass over all 50 changed files — assertion messages, per-test seed draws, `CoreGenerators` reuse in `EnrolmentStoreTest`/`LeaseStoreTest`, remaining literals. Small message-length/collision findings → fixed `0c6dad21`.
- PR review (CodeRabbit, optional Sourcery): inline collision finding fixed (`9af96104`); the four nitpicks either fixed (clock precision, digest size, revision heading) or answered (`PROJECT_SCOPES`, `LockKeyTest` factory table); thread resolved by CodeRabbit ("Addressed in commit 9af9610").
- SonarCloud on `9af96104`/`d9459b4c`: the first analysis carried 5 new issues (3× `java:S5778` lambdas in `IntentRecordTest`, 2× `java:S1117` shadowing in `DerivedFactRefreshTest`); all fixed → **0 new issues, gate passed**.

## 5. Reviewers

- **CodeRabbit (required, quota_limited).** Reviewed `0c6dad21` (formal `COMMENTED` review) and `9af96104` (marker `coveredCommitId=9af96104`, `kind=reviewed`). Re-triggered after the merge push `d9459b4c`; it refused with "Already reviewed the last commit … 0 remain after this review" (exhausted allowance). Standing authorization applies on the merge head: qualifying review on a PR head; every finding handled; one re-trigger after the last push with the refusal recorded; every other gate condition green.
- **Sourcery (optional, quota_limited).** Refused: "larger than the review limit of 150,000 diff characters". Not gating.

## 6. Operator questions

- Asked: "The self-review reached its 3-round limit … How should I continue?" Options: {continue fixing the open items, proceed to PR with findings reported open, stop the run}. Selected: **"Continue fixing the open items"**.

## 7. Rule check

- `guard: none on this harness (unenforced)`; no guard log lines exist for the run.
- Spec read: `spec_source=ledger_worktree`, `spec_differ: true` (the main-checkout copy differs); the worktree copy was authoritative.
- `executor-skew`: `match` (executor and lane both 0.1.2041).
- Scope: an early pass edited 27 files sorting after `LockOrderTest` (PLAN-V2-86 scope); reverted with `git apply -R` before the D1 commit. A second pass added comments to 5 further out-of-scope files; reverted in `3b36e90c`. Final diff contains only A…`LockOrderTest` plus the new contract test.
- One implementation sub-agent disclosed a single read-only `grep` through the shell before switching to the file tools; nothing was modified by it; all later edits used read/edit. The self-review sub-agents were instructed to use file tools and git diff; no build was run by them.
- Build routing: preflight at setup was `down` (`unreachable`); 22 builds ran `in_process_fallback` for that reason; after the daemon became reachable, 11 builds ran routed (`daemon_longpoll`) with no launch-fault retry, no degradation and no killed job. `build-routing` answered `auto` (executor present in the worktree).
- R17 links check ran from the main checkout against the documentation checkout; it reports pre-existing cross-repo breakages only (the plan changes no doc file).

## 8. Build server

- Setup preflight: `down`, reason `unreachable`. Later resolve: `ready`.
- Builds: 22 `in_process_fallback` (reason `unreachable`) and 11 `daemon_longpoll`; fallbacks: `none` beyond the pre-daemon `degraded` reason; no `executor_absent_from_worktree` retry and no launch-fault retry; no `killed` job.
- `runtime_stamp_reason`: none.

## 9. Residue

- `@GeneratorsSource` is not applied anywhere: every 3+-variant parameterized table in scope is deliberately invalid input, a tuple `GeneratorType` cannot produce, or an enum exhaustiveness table (`@EnumSource`).
- Some non-contract-looking names remain literal where a shared fixture or a wire document pins them (`plan-one` in `JobRecordStoreTest` via `JobRecordCodecTest.PLAN_SCOPE`; canonical JSON/YAML documents).
- Custom assertion helper methods are not themselves messaged; their internal assertions are.
- `plugin_version` and `total_tokens` are `unknown` (the seams measured nothing).
- The self-review state is `budget-exhausted` because the above residuals remain open after the operator-authorised extra round.

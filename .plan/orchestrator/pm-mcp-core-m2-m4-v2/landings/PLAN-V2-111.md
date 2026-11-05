# Landing Analysis: PLAN-V2-111 — Derived-facts guard coverage (tests that can fail, no operator-only value in a reason, PM-WATCH-STORE-43 restored)

epic: pm-mcp-core-m2-m4-v2
workstream: WS-05
pr: #283 (merge commit `02ef2cdb`, `02ef2cdba47da7273519e785877ec2c45e6e8a4d`; 22 files, +437/-144)

> Landing record for one shipped plan. Lives at `landings/PLAN-V2-111.md`. Written by the `analyze` verb after verifying
> claims against ground truth (actual code, artifacts, PR state); a pasted claim is a lead, never a fact.

Source: inbox message `rb-v2-111-derived-facts-guard-coverage-001.md` (runbook lane, harness opencode, model
`deepseek-v4.1-flash/high`, effort high, executor 0.1.2041, standing rules **r5**, R02, R05–R08, R10, R11, R14, R16–R20).
Corroborated against `origin/main` (the merge diff of `02ef2cdb`) and the PR state (`ci checks status`: `overall_status:
success`).

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 — tests that can fail | shipped-as-specified, one test weaker than named | `DerivedFactStoreConcurrencyTest#nestedRefreshOfASecondKey` (pm-core) is a real guard; `DerivedFactStoreLockTest#planLockAndConcurrentRefresh` (pm-runtime) proves "no deadlock, no wedged state", not "no update lost" (L2-1) |
| D2 — no operator-only value in a reason | shipped-as-specified | `DerivedFactStore.java:283-285` redacts the schema-violation reason for an operator-only kind; `DerivedFactRefreshTest#operatorOnlyValueIsNotInTheReason` and `#nonOperatorValueStaysInTheReason` |
| D3 — statements, watch item, ADRs | shipped, one premise corrected by the lane | PM-WATCH-STORE-43 restored in `store-schemas.adoc` (748-760) with its spec backlink; `store-schemas/09` line 15, guide and Javadoc corrected; ADRs `core-store/063` and `064`, 056/057 corrected |

Surface delta: one file outside the declaration (`13-leases-integrity-testing.adoc`, the restored backlink; R19 bookkeeping,
disclosed) and ten `pm-providers` files rewritten by the `verify -Ppre-commit` profile (formatting only, disclosed; two record
headers now start a continuation line at column 0, `Response.java:32`, `Result.java:31`).

## Metrics and Anomalies

- Tokens `unknown`, `plugin_version` `unknown`: the open defect in `epic.md`, tenth recurrence.
- The spec was read at **r5**; `standing-rules.md` now carries r6 (the sub-agent clause binds the orchestrator only).
- Self-review: three rounds, clear. Reviews: CodeRabbit (required) reviewed `06d1357c`, three findings fixed, later heads
  refused on quota, merged under the standing authorization; Sourcery reviewed five times ("they look great"). No operator
  question was asked.
- Sonar: lane-stated gate green with 0 new issues on each head. Sonar `main` today: no issue in the touched files.

## Routing and Merge Behavior

- `ci checks status --pr-number 283`: `overall_status: success`. Ran beside PLAN-V2-110, 16 and 28; no collision in the diff.
- This plan was staged by the orchestrator after the three-layer analysis of PLAN-V2-05 (PM-WATCH-STORE-43). Its own
  analysis follows.

## Three-Layer Analysis (standing rule R21, r6)

Done at the merge commit `02ef2cdb` by a read-only sub-agent (the clause of r6), then the points marked **checked** re-read by
the orchestrator. Nothing was built or run. What only a run could show is marked "not shown by reading".

### Layer 1 — Specified: sound in aim; the order premise is wrong in two ways

- **S1 (medium, checked).** The spec's D3(e) ("a failure started later wins over a success started earlier for the same
  digest, the entry is `failed`") is false, and the lane's correction is itself imprecise. In `DerivedFactStore.update`
  (`:218-232`) the same-digest check (`:224-225`) runs before the outcome; a failure on an entry that already has a value
  becomes a **`stale`** entry that keeps the old `derivedAt` (`entryOf`, `:249`), so a later-started failure that commits
  before an earlier-started success is overwritten. Only a failure on an entry with no value becomes `failed` with
  `derivedAt = now`. The lane's text "a `Failed` entry keeps the last-good `derivedAt`" should say "a `stale` entry".
- **S2 (medium, checked).** D1(b) asks for "no `lock_order_violation`" in a scenario that cannot produce one:
  `FileLockManager.Transaction.acquire` checks only the held list of its own transaction, and thread B holds nothing. The
  Done-when contradicts the spec's own Objective (the order is asserted per transaction).
- **S3 (low).** D1(b) is ordered `none` (no failing run demanded), although the plan's theme is tests that can fail.
- **S4 (low, not shown by reading).** The lane says the sibling link `requirements/14-implementation.adoc` no longer names
  PM-WATCH-STORE-43; the reader found none at a documentation ref that may lag (memory: the documentation checkout lags origin).

### Layer 2 — Implemented as specified: met; one test proves less than its name

- **I1 (medium, checked by the reader's reading of the test).** `planLockAndConcurrentRefresh` (`DerivedFactStoreLockTest`
  `:143-190`) detects a derivation held under the derived-facts lock (B would block, the 10 s await would throw). Its assertion
  "the stored value is one of the two" passes for every outcome, including a lost update; the `LOCK_ORDER_VIOLATION` assertion
  can only fail if the store takes a scope lock in the wrong order. It cannot show where `DERIVED_FACTS` sits in the order.
  The display name and Javadoc stay honest about the order claim.
- **I2.** `nestedRefreshOfASecondKey` is deterministic and single-threaded: a write from the pre-derivation snapshot drops
  `SECOND`, so `size == 2` fails. It uses the `SerializingLockManager` fake, not the real manager. The lane's mutation-after
  runs are lane-stated, not re-run.
- **I3.** The two D2 tests are not vacuous: `SchemaSubset` echoes `value.toJson()` in a violation, so `reason.contains(secret)`
  fails on the unchanged code; the control kind (`PUSH_REMOTE`, not operator-only) is a real control.
- **I4 (low, checked).** The restored PM-WATCH-STORE-43 says the guard is "proved by" three test classes. That holds for
  "derivation outside the lock" and the pure order function (`LockOrderTest`); "takes no other lock and calls no foreign code
  under it" is shown by reading `update`, not tested.
- **I5 (low).** ADR 063 is cited by name from the Javadoc and ADR 056 but not linked from the guide; spec 09 line 15 now reads
  "so a refresh … takes it in its own transaction", where the "so" no longer follows from the sentence before it.
- Lane claims that hold: the redaction, Javadoc and guide text, ADRs 056/057/063/064 and spec 09 line 15 are present;
  `DerivedFactStore` is unchanged for non-operator kinds (the added branch is guarded by `key.kind().operatorOnly()`).

### Layer 3 — Implementation correctness: redaction is incomplete against the plan's own objective

- **C1 (medium, checked).** Only the schema-violation branch is redacted (`DerivedFactStore.java:283-285`). A consumer
  `Failed` reason passes through `entryOf` and `readingOf` unchanged, and line 281 echoes the consumer-supplied derivation
  identifier (`"The derivation identifier is outside its grammar: " + derived.derivation()`). ADR 064 says an operator-only
  value cannot enter a reason "through the store's own validation": true of the value, silent on line 281. The consumer
  contract is documented in the Javadoc and the guide, and the spec's "production touched only in `validated`" constraint is
  what kept the lane from redacting in `entryOf`. `DerivedFactReading.Value` and `DerivedFact` carry the value in their record
  `toString`; whether anything stringifies them: not shown by reading.
- **C2 (medium-low, checked).** The three residue gaps are real and ADR 056 states them accurately enough: a different-digest
  failure turns a newer `current` entry into `stale` (the reading is `Indeterminate` until the next refresh; self-healing);
  an earlier-started success overwrites a later-started failure's `stale` entry; a consumer's `Failed` reason carries operator
  text. ADR 056 omits the same-digest case with an earlier or equal `derived_at`, where the failure is applied as well.
- **No defect in the new tests' threading:** latch waits are bounded (10 s inside a 30 s class timeout), the executor closes on
  failure, the interrupt is restored, the audit sink is read after `Future.get`.

### CLAUDE.md conformance

Clean: no `null` return, no `Optional` parameter or field, no new `@Nullable` return, no generic catch, no new
`@SuppressWarnings`; assertion messages 23–49 characters; generators for the secret and the invalid value (the `"first"`,
`"second"`, `"alpha"`, `"beta"` literals are fixture data); no Mockito, Hamcrest, `Random` or `UUID`. The formatter rewrites
in `pm-providers` are the only style degradation.

### Verdict

Shipped as specified; the guard test the plan exists for (D1a) can fail, the redaction (D2) is real but narrower than the
plan's objective, and the lane's own premise correction is sound in direction and imprecise in wording. **No count-, lock- or
data-corrupting defect.** Owners: C1 (consumer reason and identifier echo) — PLAN-V2-65 already carries the `SchemaSubset`
general fix (ADR 064); the rest — nobody. They go to the operator, and become a staged fix-plan spec only on the operator's word.

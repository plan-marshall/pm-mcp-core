envelope_version=1
sender_type=plan
sender_id=rb-v2-111-derived-facts-guard-coverage
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-02T11:48:14Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-111-derived-facts-guard-coverage
epic=pm-mcp-core-m2-m4-v2
pr=#283
merge_state=merged
cleanup_owed=false
deliverables_total=3
deliverables_done=3
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2041
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
step.sonar-roundtrip.open_issue_count=n/a
step.sonar-roundtrip.open_issue_rules=n/a
```

guard: none on this harness (unenforced)

Standing rules: r5 — R02, R05, R06, R07, R08, R10, R11, R14, R16, R17, R18, R19, R20 (R21 binds the orchestrator, not this plan).
Spec: read from `ledger_worktree`; no `spec_differ` marker.

## Deliverables

### D1 — Tests that can fail — met

- `DerivedFactStoreConcurrencyTest#nestedRefreshOfASecondKey` (pm-core): a nested `refresh` of a second key inside the
  derivation of the first; both entries are stored afterwards. Order **mutation-after**: the production code was mutated
  (uncommitted) to write the document read before the derivation, the test failed at `22c34764`, log
  `61402f96dadc4804b7a178647fc3b6b5`; the mutation was reverted and the test passed at `22c34764`, log
  `c2ad33fe59d54bdeb849ae3ea6d0f050` (7 tests). Commits `22c34764`, `06d1357c`.
- `DerivedFactStoreLockTest#planLockAndConcurrentRefresh` (pm-runtime): real `FileLockManager`; two latches force both
  derivations to overlap while thread A holds a plan lock; both calls return, the stored entry is one of the two results
  with its own instant, and the `RecordingAuditSink` records no `lock_order_violation`. Order **none** (no failing run
  demanded); green at `61d7d144`, log `53fa107c68594aa7bc32ee2b3f4ddd9a` (3 tests); latch waits bounded in `6656ae00`.
- `planLockThenRefresh` keeps only "the refresh is permitted" in its display name and message; its order assertion is
  dropped. No test of the plan claims to show the lock order by a refresh.

### D2 — No operator-only value in a reason — met

- `DerivedFactRefreshTest#operatorOnlyValueIsNotInTheReason` and `#nonOperatorValueStaysInTheReason`. Order
  **test-first**: the test was committed at `86ca3526` and failed against the unchanged production code, log
  `d28796a5efcf4810a06c7e44b71e7237` (16 passed, 1 failed); after the redaction it passed at `6d68408d`, log
  `8b88a4821dc243f3833fa9f657012b94` (17 tests). The test asserts the reason names the kind and the violation count,
  never the value, `reading` is `Indeterminate`, and `couldNotEvaluate()` — the exact input of
  `DerivedFactGuards.evaluate` — does not contain it; the non-operator case still names its violation. The `refresh`
  Javadoc and the guide state the consumer-`Failed` contract. Commits `86ca3526`, `6d68408d`, `6656ae00`.

### D3 — The statements, the watch item, the ADRs — met, one premise corrected

- PM-WATCH-STORE-43 is restored in `doc/implementation-watch/store-schemas.adoc` under the same id and anchor, narrowed
  to the hazard/guard of the plan; its spec backlink in `13-leases-integrity-testing.adoc` is restored too (surface
  delta, R19 bookkeeping: the file was not named in the Expected Surface).
- `store-schemas/09` line 15 is corrected; the schema block, kind table, anchors and *Provider derivations* bullet are
  byte-stable (R10).
- The guide's scope-transaction paragraph and "violation the lock manager refuses" sentence are corrected; the
  stale-retry reason and the `DerivedFactStore` *Failure* Javadoc are corrected.
- ADR `core-store/063` (the store's transaction is its own; the order across transactions is a caller convention, with
  the `LockTransaction` overload recorded as the alternative) and `core-store/064` (the operator-only value never enters
  a reason, with the `SchemaSubset` general fix recorded as PLAN-V2-65's). ADRs 056 and 057 corrected.
- Links check (R17), order **none**: `BROKEN LINKS 0`, below the epic baseline of 1; `trace.py` reports
  `REFERENCED BUT UNDEFINED WATCH IDS ['PM-WATCH-STORE-56']` (pre-existing) and no `PM-WATCH-STORE-43`; `WATCH ITEMS NOT
  REFERENCED []` and `GAPS WATCH PM-WATCH-STORE` no longer lists 43. Commit `bf52d177`.
- **Premise corrected, not obeyed:** D3(e) asked ADR 056 to state that "a failure started later wins over a success
  started earlier for the same digest". Reading `DerivedFactStore.update` shows the same-digest `derivedAt` check runs
  before the outcome, and a `Failed` entry keeps the last-good `derivedAt`; a failure that commits before an
  earlier-started success can therefore be overwritten, and an older-input failure can mark a newer fact stale. The ADR
  states the true rule and names the gap (residue), rather than a falsehood. Commits `22e14242`, `6656ae00`.

Plan-level Done-when: the tests are green, `DerivedFactStore` is unchanged for every non-operator kind, the PR body
names the tests that cover the guard of PM-WATCH-STORE-43 and why the item stays, and the build, coverage, integration
and links runs are green.

## Personas and skills

- Production code (D2): `persona-implementer`.
- Unit/module tests (D1, D2): `persona-module-tester`.
- Documentation and ADRs (D3): `persona-documenter`.
- Every self-review round and PR-comment triage: `persona-code-reviewer`.
- Skills read (R20): `java-core`, `java-null-safety`, `javadoc`, `junit-core`, `cui-testing`; `skills: none named` by
  the spec's `Skills:` lines. No surface drift, so no skill was added.

## Hypotheses

- HYPOTHESIS (nested second-key refresh forces the lost update against a pre-derivation write) — **confirmed** at
  `pm-core/src/main/java/de/planmarshall/core/store/DerivedFactStore.java:212-226` (`update` re-reads under the lock;
  the mutated pre-derivation read loses the nested entry; log `61402f96...`).
- HYPOTHESIS (thread B can overlap A's held plan lock with latches and the real `FileLockManager`) — **confirmed** at
  `pm-runtime/src/test/java/de/planmarshall/runtime/lock/DerivedFactStoreLockTest.java:141-191`.
- HYPOTHESIS (`AGENT_IDENTITY` can be registered with an `enum` schema) — **confirmed** at
  `DerivedFactKinds.register` (`pm-core/.../DerivedFactKinds.java:49-55`) and `DerivedFactKindSpec`; the test registers
  it and the store validates against it.
- HYPOTHESIS (no other place states the manager enforces the order across transactions) — **confirmed**: only the
  corrected spec/guide/Javadoc/ADR sites; `FileLockManager.java:77-78` and `Transaction.acquire:175-189` assert within
  one transaction.
- Verify-first clause — no open PR touched `DerivedFact*`, `DerivedFactStoreLockTest`, the watch file or
  `store-schemas/09` at start (open PRs: 280, 277, 71); the three files were re-read at the head.

## Findings

- Self-review round 1 (persona-code-reviewer): F1 ADR 064 `const` wording (fixed `48eaa871`), F2 class lock Javadoc
  unqualified (fixed `48eaa871`), F9 ADR 056 named the lock test as proving the order (fixed `3fdeab4b`), F8 gate at
  the final head (closed); F3 spec-13 backlink undeclared (expected bookkeeping), F4 stale sibling-link premise,
  F5 surplus assertion, F7 pre-commit rewrites.
- Self-review round 2: F9/F8 closed; round 3: clear, no open finding.
- Sourcery (optional), 4 reviews: consumer-`Failed` reason leak (by design; contract documented; redacting consumer
  text is out of production scope), failure-ordering gaps (real limitations reported, not fixed; ADR 056 states them),
  guard-input not asserted (the spec words it as `couldNotEvaluate()`; pm-core cannot depend on pm-workflow),
  old stored failure reasons (nothing released), invalid derivation identifier (consumer text, out of scope),
  smoke-test acceptance of any non-first value (fixed `06d1357c`), fixed markers/group headings (answered as R20's
  literal exception and per-assertion messages). All threads resolved; final review: "they look great".
- CodeRabbit (required, quota-limited), review on `06d1357c`: ADR 056 failure-ordering statement (fixed `6656ae00`),
  unbounded latch wait (fixed `6656ae00`), redacted reason not pinned (fixed `6656ae00`); threads resolved. Re-triggered
  after every push; refusals recorded (08:54, 10:38, 11:15).
- CI: all green on `3fdeab4b`, `06d1357c`, `22e14242`, `6656ae00` and `b6735683`; SonarCloud quality gate green with
  0 new issues on each.

## Reviewers

| Reviewer | Required | Result | Head reviewed | History |
|---|---|---|---|---|
| coderabbit | yes (quota_limited) | refused on quota at 08:54, 10:38, 11:15; qualifying review exists on `06d1357c` | `06d1357c` (`final_review_risk_coverage` kind=reviewed); current head `b6735683` | trigger 08:54 → refused; 10:27 → reviewed `06d1357c`; 10:38 → refused; 11:15 → refused. Its 3 findings fixed in `6656ae00`, threads resolved; standing authorization applied. |
| sourcery | no (optional) | reviewed, approved on `06d1357c`; final review on `b6735683` says "they look great" | `b6735683` | triggered 08:54, 09:21, 09:44, 10:38, 11:15; 5 reviews; all findings answered or fixed and threads resolved. |

## Operator questions

none asked. No free-text operator message was received.

## Rule check

- `guard: none on this harness (unenforced)` — no tool-call guard was installed, so the guard log is not a witness.
- The run's own actions, audited from the transcript, not from a guard log: one shell `grep -r` for the test-manager
  names (a shell file search, against the "file tools, never shell `grep`" rule); `git show` used to read two deleted
  artifacts and the commit metadata (git history, not working files); the R17 links check was run directly as
  `python3 /home/oliver/git/plan-marshall-documentation/.claude/skills/doc-review/scripts/trace.py` because R17
  prescribes exactly that command for the sibling checkout and no bundle script wraps it. Every other command was one
  command per call, the executor for scripts, the resolved `build` commands for builds, and `ci` for CI. No file was
  read or written outside the repository except the R17 sibling documentation checkout and the harness skill text.
- `spec_source: ledger_worktree`; no `spec_differ` marker.
- `executor-skew`: `match` (executor 0.1.2041, lane 0.1.2041).

## Build server

Setup preflight `ready`. 30 routed builds, all `daemon_longpoll`, 0 `in_process_fallback`; 0 degraded, 0 launch-fault
retries. Three daemon bound expiries (`timeout_used_seconds` 521, `timeout_source` explicit) — the full
`verify -Ppre-commit`, the full `verify -Pcoverage`, and one `verify -Pcoverage -pl pm-runtime`; each was continued
with the split form or a raised bound (`--timeout 900`), never re-submitted at the same bound. Every pushed head had a
test run: `3fdeab4b` (pre-commit split, verify, coverage, integration), `06d1357c` (verify, CI), `22e14242` (verify),
`6656ae00` (verify, CI), `b6735683` (verify, CI). `runtime_stamp_reason` was not reported.

## Residue

- Production failure-ordering gaps found and **not fixed** (outside the plan's one-place production scope), for a
  follow-up: a later-started failure that commits before an earlier-started success of the same digest is overwritten;
  a failure with a different digest can mark a newer current fact stale; a consumer's own `Failed` reason and an
  invalid derivation identifier can carry operator-only text. ADR 056 records the first two.
- The spec's D3(e) claim that a failure started later always wins over an earlier-started success is false in one
  interleaving; the ADR states the actual rule (see D3).
- The spec's premise that the sibling `requirements/14-implementation.adoc` still links PM-WATCH-STORE-43 is stale at
  the documentation head checked out here; restoring the id is still correct and no documentation PR is owed.
- `pm-providers` rewrites in `d171e357` are the `verify -Ppre-commit` profile's own (formatting/import only) and are
  outside the spec's deliverables.
- Surface delta (R19): `doc/specification/store-schemas/13-leases-integrity-testing.adoc` (the restored backlink) was
  not in the Expected Surface; reported here as expected bookkeeping.

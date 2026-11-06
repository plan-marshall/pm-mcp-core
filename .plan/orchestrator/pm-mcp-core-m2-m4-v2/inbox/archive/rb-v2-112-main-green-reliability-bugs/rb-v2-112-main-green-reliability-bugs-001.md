envelope_version=1
sender_type=plan
sender_id=rb-v2-112-main-green-reliability-bugs
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-05T05:57:17Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-112-main-green-reliability-bugs
epic=pm-mcp-core-m2-m4-v2
pr=#293
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

# PLAN-V2-112 — main-green reliability bugs (landing)

Standing rules: r6, R02, R05, R06, R07, R08, R15, R17, R18, R19, R20 (R21 binds the orchestrator, not this plan).
Spec read from the ledger worktree (`spec_source=ledger_worktree`, no `spec_differ`). Executor skew: `match`
(executor 0.1.2041, lane 0.1.2041).

**The proof of this plan is `main`, read by the orchestrator after the merge, not this pull request.** The lane could
not read `main` before the merge and does not claim the terminal measurement. The forms chosen are named in
§ Hypotheses and § Deliverables; the orchestrator reads
`branch=main&resolved=false&rules=java:S2637` after the merge.

## 1. Deliverables

| # | Done-when | Verdict | Test | order | Commit |
|---|---|---|---|---|---|
| D1 | the cases pass | met | `ScoringTest.declaredThresholdWins`, `ScoringTest.defaultThresholdWhenNoneDeclared`; `test -Dtest=ScoringTest -pl pm-core` green, 6 tests | none | `7bf81ca5`, `0ee96eff`, `5a1e98a6` |
| D1 | the diff changes `Scoring.threshold` and `ScoringTest` only | met | diff is `Scoring.java` and `ScoringTest.java` | none | `7bf81ca5`, `0ee96eff`, `5a1e98a6` |
| D2 | the diff changes `UnitFrontmatterReader` only (and the test if a case is added) | met | no test file changed | none | `a77af12c` |
| D2 | the existing reader cases stay green unchanged; no behaviour change | met | `UnitFrontmatterReaderTest` unchanged; `test -Dtest=UnitFrontmatterReaderTest -pl pm-workflow -am` green, 151 tests | none | `a77af12c` |
| D2 | `previousKey` treated and its flag explained | met | `@Nullable` at `UnitFrontmatterReader.java:189`; explanation in § Hypotheses | none | `a77af12c` |
| D3 | ADR 013 carries the three statements | met | `doc/adr/013-…adoc` class (c), statements (a)–(c); `Source` names this plan | none | `250bacaf`, `4b8bb11a`, `c976f2e0` |
| D3 | its links resolve (R17) | met | `trace.py` run from the main checkout against the documentation checkout at its fetched `origin/main`: `BROKEN LINKS 450`, all pre-existing elsewhere, no line names `adr/013` | none | `250bacaf`, `4b8bb11a`, `c976f2e0` |

No Done-when clause contains a "fails at `{sha}`" or mutation clause — the two fixes are annotation/analysis changes
with no behaviour change, so there is no failing run to capture — so every row is `order: none` and none is
downgraded by that order.

Per-head test runs against pushed heads: `test -Dtest=ScoringTest -pl pm-core` green at `c976f2e0` content and at
`5a1e98a6` content (6 tests); `verify -Ppre-commit -pl pm-core` green, 4270 tests at `c976f2e0`, and green, 4338
tests at `fb72408`; `verify -Ppre-commit -pl pm-workflow` green, 2245 tests at `fb72408` (unchanged content at
`a77af12c`). A full-reactor `verify -Ppre-commit` exceeded the host's 1158 s bound after pm-core had completed
(4270 green); the plan's own allowance replaces it with the per-module runs.

## 2. Personas and skills

- Personas applied as standards: `persona-implementer` (D1, D2 production changes), `persona-module-tester` (D1, D2
  tests), `persona-documenter` (D3 ADR), `persona-code-reviewer` (self-review rounds, PR-comment triage).
- Domain skills: the resolve rows declare none (`skills: none named`); the spec's First Instruction names
  `java-core`, `java-null-safety`, `javadoc`, `junit-core` and `cui-testing` under R20, and all five were read
  before any edit (plus the `null-safety-core`, `null-safety-patterns` and `test-generator-framework` standards).
- Surface drift: none — the work stayed inside the spec's Expected Surface.

## 3. Hypotheses

- **H1 (single-read local clears `Scoring.java:80`) — confirmed by a true local-form site.** `CallGraphExpansion.
  entered(...)` (`pm-workflow/.../verify/CallGraphExpansion.java:331-332`) holds `callee.entryBindings().get(...)` in
  a local and defaults it with the same ternary; it is not among the two open `java:S2637` issues of `main` at
  `70bc999f`. No record accessor in the tree carries the exact local form, so acceptance is a prediction from that
  local-form site, not a measurement — the terminal proof is the post-merge `main` read.
- **H2 (`previousKey` is assigned before every read) — refuted.** `describe(...)` reads `previousKey` at
  `UnitFrontmatterReader.java:320` and `:324`, and `field(...)` calls `describe(...)` at `:270` before
  `previousKey = key` at `:271`; a first-line `entry_description` exercises the null read
  (`UnitFrontmatterReaderTest.java:270-271`). The null is a real state, so `@Nullable String previousKey` is the
  correct treatment and no `requireNonNull` is added (that would turn the `Refusal` into an `NPE`). Sonar does not
  flag it as `S2637` because it is only passed to `equals`, never dereferenced or handed to a non-null parameter;
  the exact rule-internal reason is not established by this reading, and the spec's stated reason is wrong.

## 4. Findings

- Self-review round 1: ADR class (c) mechanism/dating wrong (fixed); H2 reason false in the spec (recorded, spec is
  read-only); threshold test did not discriminate a `max(declared, default)` implementation (fixed by generating a
  threshold below the default); ADR `Source` did not name this plan (fixed).
- Self-review round 2: the ADR cited `EndpointProfile.readinessOrModelsPath()`, which is not a local-form site
  (fixed in `c976f2e0` with `CallGraphExpansion.entered(...)`).
- Self-review round 3: the run's own progress line repeated the retracted citation (fixed in the record); no code
  finding open.
- PR (Sourcery, optional): `assertAll` group heading missing — triaged as false-positive drift (the repository's
  established style omits the heading; every individual assertion carries a message), answered and the thread
  resolved.
- PR (CodeRabbit, required): add a rejection assertion for a score below a lower declared threshold — valid, fixed
  in `5a1e98a6`.

## 5. Reviewers

| Bot | Required | State | Head reviewed | History |
|---|---|---|---|---|
| coderabbit | yes (`quota_limited`) | refused on quota, review exists on a prior head | reviewed `c976f2e0` (qualifying; marker `kind=reviewed`) | refused 23:51, 01:21, 03:03; escalated `full review` 04:43 → review 04:47; finding fixed; re-triggered 04:49 and 05:19, both refused (last 05:19:29). Standing review authorization: qualifying review on `c976f2e0`, every finding handled, re-trigger after the last push refused and recorded |
| sourcery | no (`quota_limited`) | reviewed | `c976f2e0`, `5a1e98a6`, `fb72408` (all APPROVED) | 1 finding answered and its thread resolved |

## 6. Operator questions

None asked. No free-text operator messages.

## 7. Rule check

- `guard: none on this harness (unenforced)` — nothing was enforced, so no guard log exists for this run; no
  "all kept" claim is made.
- Executor-skew verdict `match` (executor 0.1.2041, lane 0.1.2041); spec read from `ledger_worktree`, no
  `spec_differ` marker.
- Rule breaks of this run's own actions: one read-class shell file operation — `tail -40` on a Maven build log to
  see where the timed-out reactor stopped (the rule routes file reads to the file tools). Everything else used the
  file tools, the executor for scripts, the `build` map for builds and `ci` for CI.
- Two spec-sanctioned actions outside the repository root: reading the R20 skill standards at their installed skill
  base, and running the R17 links check from `/home/oliver/git/plan-marshall-documentation`; no credential or config
  file was read.
- No forbidden git command was used (no stash, restore, checkout --, rebase, forced push, branch -D); the
  formatter's undeclared whitespace rewrite of `DerivedFactRefreshTest.java` was reverted in the worktree (before the
  merge brought `main`'s own version of it) and never committed.
- Restarts: none. Killed sub-agents: none. Re-dispatches: the self-review sub-agent on the same session for rounds 2
  and 3.

## 8. Build server

Setup preflight: `down` (`reason: unreachable`). All 13 build/maven calls ran `in_process_fallback`
(`requested=auto`, `resolved=in_process`, `reason=unreachable`) — no `daemon_longpoll` build. Fallbacks, one per
build: every build degraded to in-process for `unreachable`; additionally one build failed its dependency resolution
(`pm-core:jar:tests` absent from a single-module `-pl pm-workflow` run) and was re-run with `-am`. No job was
`killed`. `runtime_stamp_reason` was not returned (all stamp facts measured).

## 9. Residue

- The terminal proof is the orchestrator's post-merge read of `main`; this landing claims no measurement of it.
- PR #290 (`PLAN-V2-87`) is still open and rewrites `ScoringTest` for generated data; it may conflict with the three
  new test methods here. Whoever lands it should re-run `ScoringTest` after the merge.
- No other residue: cleanup is complete (worktree removed, local and remote branch pruned, main checkout at the merge
  commit `11ef1250`).

envelope_version=1
sender_type=plan
sender_id=NO_PLAN
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-14T16:01:31Z

```landing-facts
schema=landing-facts/1
plan_id=NO_PLAN
epic=pm-mcp-core-m2-m4-v2
pr=#416
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=0.1.2118
executor_version=0.1.2118
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=10
review_body_findings_handled=10
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced)

# PLAN-V2-136 residue — the closed list enforced and its residue cleared (ad-hoc)

Merged as PR #416, merge commit `df6663050bfc772b14a152e75845c5bc2c3b31ee`, through the main merge queue.
This is an ad-hoc change outside a plan and outside the runbook lane, at the operator's word; it fixes the residue a
read-only landing analysis of PLAN-V2-136 (#411, `6aa55a56`) found. `plan_id=NO_PLAN` is the plan-less sentinel, not a
queue entry. The five `deliverables` are the five parts of the operator's brief, which stands in for a solution
outline; no outline exists for this run.

## 1. Deliverables

Every part is met; the test order is `none` for all five (the fixes are refactors and assertions; no fail-first run is
claimed). Branch base `origin/main` `8724a5e4`; final head `b1d88e13`; the merge commit is `df666305`.

- **Part 1 — mocks that cannot fail on a missing call: met.** `AuditLogTest.failingPredecessor()` now throws on every
  `emptyLogPredecessor()` (`anyTimes()`), matching the old anchor that threw every time. `ActivityRecorderTest`'s
  message over `assertEquals(0, signals.size(), …)` now says `two entries raise no signal` (the old text said the
  opposite). `StartFailureGuardTest.protocol()` is documented as the nice stub it is; every close it records is asserted
  on `closed`. The other converted helpers were classified from their old tests: a recorded list an assertion reads
  proves the call (`.anyTimes()` stays), a no-op stub no test asserts is documented, and
  `WorkerActivityPumpTest.noSignals()` remains a strict `createMock`. `grep` over `pm-runtime/src/main` found the only
  `RuntimeException` catch around a protocol path is `WorkerSupervisor:325` around `startWorker`; EasyMock's
  unexpected-call failure is an `AssertionError` (`Error`), not caught, and no production code catches around `fence`,
  `agentStarted`, `offTrack` or `closeUnavailable`.
- **Part 2 — the two `Scripted` classes and `NoteStep`: met by conversion.** `EngineFixtures.Scripted`,
  `EngineFixtures.NoteStep` and `DefaultPlanPrimitives.Scripted` no longer implement `Primitive`; each is a holder that
  builds an EasyMock `Primitive` mock's expectations and keeps the script and scopes, while the registry receives the
  mock. Call sites (`.then`, `.calls()`, `.scopes()`, `.notes`, `scopes(id)`) are unchanged.
- **Part 3 — the closed list is enforced: met, option (a).** One `HandWrittenDoubleScanTest` per module
  (`pm-core`, `pm-workflow`, `pm-runtime`) scans the test sources for a named class/record/enum over a production
  interface or abstract class, an anonymous class over one, and an assigned lambda or method reference, and compares
  the exact set with `src/test/resources/hand-written-doubles.txt`. The baseline is ADR 026's closed list plus the
  population outside the old name scan, which PLAN-V2-125 and PLAN-V2-126 convert; a new double adds a line and fails,
  and the failure writes `target/hand-written-doubles-found.txt`. Controls inside each test prove the planted named,
  anonymous, lambda, generic-type, multi-declarator, qualified-annotation and escaped-text-block shapes.
- **Part 4 — false statements corrected: met.** Only the false sentences changed in `workflow-engine/006`,
  `runtime-start/003` and `012-generator-artifacts`; each cites PR #416, and ADR 026 carries the dated amendment.
  `CLAUDE.md` gained the approved pointer sentence, qualified after review to say the remaining doubles are the closed
  list *plus* the population the scan baseline names until PLAN-V2-125/126 convert it.
- **Part 5 — slips: met.** The two import gaps are normalised, `LessonStoreCrashTest:509` is wrapped, and ADR 026's
  published-surface sentence now names the new public `de.planmarshall.runtime.test.AuditSinkDoubles`. The assembly
  checkout `/home/oliver/git/plan-marshall-mcp` was read: it uses `TestBases` but does not reference `AuditSinkDoubles`.

Method count: `git diff -G'@(Test|ParameterizedTest|RepeatedTest|Nested)' --name-only origin/main` lists exactly the
three scan tests. `test -pl pm-core -am` 5008, `pm-workflow` 7508, `pm-runtime` 7350. `verify -Ppre-commit` and `verify`
are green at 10476 tests; CI is green at the merge head.

## 2. Personas and skills

Ad-hoc run; the five R20 skills were read before the first edit: `pm-dev-java:java-core`,
`pm-dev-java:java-lombok`, `pm-dev-java:java-null-safety`, `pm-dev-java:javadoc`, `pm-dev-java:junit-core`,
`pm-dev-java-cui:cui-testing`. No deliverable spec declares a `Persona:` line; the work followed the implementer,
module-tester and code-reviewer identities.

## 3. Hypotheses

- A mock whose call an assertion on a recorded list proves needs no `verify`: confirmed for the converted helpers; the
  assertion already fails on a missing call.
- EasyMock's unexpected-call `AssertionError` is not caught as a `RuntimeException` in production: confirmed by reading
  the only catch around a protocol path (`WorkerSupervisor.startWorker`).
- A source scan can enforce the closed list without a compiler: confirmed for the named, anonymous and assigned-lambda
  shapes, and honestly limited for a lambda passed straight into a constructor or method (no declared type at the call
  site; documented in the test and kept as a reviewer check).

## 4. Findings

- Self-review on the scanner found the original regex shapes super-linear and replaced them with a single-pass lexer and
  manual parsing; this also removed the earlier S8786/S6126 Sonar findings.
- CodeRabbit reviewed four rounds. Ten findings, all fixed and every thread answered and resolved: the CLAUDE.md
  overstatement (qualified), explicit-superclass interface detection, same-name collapse (per-name ordinal added),
  generic anonymous classes and constructor arguments, assigned-lambda layout, string/comment literals, escaped text
  blocks, modifier order and annotations, multi-declarators, and generic declared types plus qualified annotations.
- SonarCloud on `pullRequest=416`: **0 open issues**, quality gate **OK** at `b1d88e13`.
- One `pm-runtime` run hit the pre-existing timing flake `LspClientTest.stubborn` / `timeout`; the class is untouched
  and the re-run is green. Two further daemon runs hit the same flake; the final gates ran in-process.

## 5. Reviewers

- **coderabbit (required): reviewed.** Four qualifying review publications; all ten findings are handled, every thread
  is replied to and resolved; `review-gate` at the merge head reports `unresolved_review_threads=0`,
  `review_body_findings_total=10`, `review_body_findings_handled=10`, `merge_allowed: true`.
- **sourcery (optional): refused** — the review budget of 250,000 diff characters for seven days was already spent;
  recorded, optional and not gating.
- **SonarCloud:** 0 open new-code issues and gate OK at the merge head, read over plain HTTP with `pullRequest=416`.

## 6. Operator questions

1. "Part 3 adds one scan test per module that enforces ADR 026's closed list. What should it enforce?" Options:
   "(a) Pin the current baseline (Recommended)" and "(b) Force the population conversion". Selected: **(a)**.
2. "Part 4: ADR 026's spec item 'Carried 13' asked for one sentence in CLAUDE.md § Code Standards pointing to ADR 026.
   May I add it?" Options: "Yes, add the sentence (Recommended)" and "No, leave CLAUDE.md unchanged". Selected:
   **yes, add the sentence**.
3. The operator's free-text word at the end: "land and finalize" — the merge was executed and this landing filed.
   A question tool was available.

## 7. Rule check

Not a runbook-lane run, so the lane's guard rules do not all bind; the overlapping actions are reported honestly.
Guard: none on this harness (unenforced). Builds all went through
`python3 .plan/execute-script.py plan-marshall:build-maven:maven run`; several used `--execution-mode in_process` when
the daemon queue was long or after the LspClient flake, otherwise `daemon_longpoll`. Git branch, pull and branch
deletion were run directly at cleanup rather than through the lane's `switch-and-pull` and `prune-local-and-remote-ref`
verbs; one direct `gh pr view --json body` read was used to recover the PR body before editing it. No force-push, no
`reset`, no `stash`. CI actions otherwise went through the `ci` script. No credentials or environment dumps were read.

## 8. Build server

Setup preflight was not part of an ad-hoc run; the server was `ready` throughout. Most builds were `daemon_longpoll`;
`in_process_fallback` was used for repeated single-test and gate runs when the daemon queue exceeded the wait and for
the runs after the `LspClientTest` timing flake. No build was backgrounded, no job was killed, and no degraded fallback
carries a reason. Exact per-mechanism counts were not recorded in this ad-hoc run.

## 9. Residue

- **Deliverable-count provenance:** no solution outline exists for the ad-hoc brief; `deliverables_total=5` and
  `deliverables_done=5` count the brief's five parts, and this report names them. A reader of the queue should treat
  them as the operator's brief, not a spec.
- **Tokens:** `total_tokens=unknown` because this harness exposes no token measurement to the run; a landing with an
  unread total must not guess and must not write `n/a`. `inbox landing-check` is expected to name `total_tokens` alone.
- **Scan coverage:** a lambda passed straight into a constructor or method has no declared type at the call site and is
  outside the source scan; this is documented in the test and remains a reviewer's check.
- **Carried items:** the orchestrator can strike the closed items from PLAN-V2-125 (`pm-core`, `pm-workflow` tests),
  PLAN-V2-126 (`pm-runtime` tests), PLAN-V2-114 item 20 (ADR statements) and PLAN-V2-141; the PR body lists them.
  Nothing else was left open.

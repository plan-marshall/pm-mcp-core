envelope_version=1
sender_type=plan
sender_id=rb-v2-53-outbound-calls-guard
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-11T05:10:56Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-53-outbound-calls-guard
epic=pm-mcp-core-m2-m4-v2
pr=#365
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2088
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=2
review_body_findings_handled=2
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced).

Standing rules: read at the start, revision **r7** (2026-11-10). The spec's First Instruction names r4; the file at the head is r7 and binds this plan. Rule ids read: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R16, R17, R18, R19, R20 (R14, R15 dropped by the spec; R12: no log message holder).

# PLAN-V2-53 — Enforce the closed list of outbound calls (landing)

Spec: `.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-53-outbound-calls-guard.md`
(`spec_source: ledger_worktree`; no `spec_differ` marker). Branch `feature/v2-53-outbound-calls-guard`, PR #365, merge
commit `50ec9a55f990c5b57322ea358f927375495fa508`.

## Deliverables

| # | Deliverable | Verdict |
|---|---|---|
| D1 | Controls first, the dependency and the connection rule (rule A) | met |
| D2 | The closed list of destination classes (rule B) | met |
| D3 | The specification amended to match the code | met on the Done-when; sub-item (e)'s literal wording not written — operator-selected r7 reading of R05 (see below) |
| D4 | Documentation and bookkeeping | met |
| D5 | The read-only sweep, in the pull request body | met (PR #365 § D5) |

Done-when, clause by clause (order is the lane's evidence order; a `mutation-after` failing run came from mutating the
committed fix and the mutation was reverted afterwards):

- `OutboundCallsArchTest` exists in `pm-runtime`, both rules in one class — **met**; order `none`; commits `abd84143`
  (rule A) and `a8e3c59e` (rule B).
- passes on the main classes of `pm-runtime` and `pm-core` — **met**; test `OutboundCallsArchTest` (`MainClasses`,
  `DestinationList.ruleHolds`); order `mutation-after`; failing run at commit `a8e3c59e`, output
  `.plan/local/plans/NO_PLAN/build-results/pm-runtime/maven-2026-11-10-170936.log` (both `ruleHolds` failed with the
  exemptions emptied and the allow-list shortened); passed at `39fa90f3`
  (`.plan/local/plans/NO_PLAN/build-results/default/maven-2026-11-11-040922.log`, 10022 tests; the class's own 19 tests
  green at that head too, job `a8574795e9cb4cb6bcb7dd6bf1b40506`).
- fails on each connection fixture and on `CuiHttpFixture` — **met**; tests `ConnectionControls.failsOnFixture` (9 rows
  over the six connection-opening fixtures) and `DestinationControls.failsOnFixture`; order `mutation-after` for the
  rule-A fixtures; failing run at commit `de2b050e`, output
  `.plan/local/plans/NO_PLAN/build-results/pm-runtime/maven-2026-11-10-200910.log` (`failsOnFixture[2]`: "The rule fails
  on ClientSocketFixture ==> expected: <true> but was: <false>" after `java.net.Socket` was removed from the set); the
  `CuiHttpFixture` control runs on every green run (`none`).
  **Spec observation:** the clause says "seven connection fixtures and … `CuiHttpFixture`", while D2 and the Expected
  Surface enumerate nine fixture classes (six connection-opening fixtures, `CuiHttpFixture`, two positive controls). The
  enumerated set is what is delivered: read as "the seven hazard fixtures" (six + `CuiHttpFixture`, the trailing phrase
  restating one member), the clause is met; a literal eighth hazard fixture would contradict the same spec's nine-class
  surface.
- holds on the two positive controls — **met**; tests `holdsOnListeningOnly` and `holdsOnDataTypes`, run for both rules;
  order `none`.
- exemption map and allow-list: no dead entry, no unlisted violator — **met**; `MainClasses.exemptionsAreLoadBearing` and
  `DestinationList.allowListHasNoDeadEntryAndNoUnlistedUser`; order `mutation-after`; the `maven-2026-11-10-170936.log` run
  shows both failures (expected `[]` but was the four exempt classes; expected 3 entries but was 4 with
  `HuggingFaceSourceRepository`).
- `pm-runtime/pom.xml` differs by the one `archunit-junit5` test dependency — **met**; the diff of that file holds the one
  block and nothing else.
- the specification lists the corpus fetch and the loopback endpoint, states the local sockets, says what remains —
  **met**; `doc/specification/credentials-and-enrolment/03-audit-and-outbound-https.adoc:41-46`, anchor
  `[[closed-outbound-list]]` kept. The CodeRabbit review added the module scope and made the construction explicit (see
  Findings).
- the developer guide, the ADR with its index line, the README lines, the `CLAUDE.md` bullet and the `Traceability`
  lines — **met**; `pm-runtime/doc/developer/runtime-guard.adoc`, `doc/adr/023-the-outbound-call-guard-lives-in-pm-runtime.adoc`
  + `doc/adr/README.adoc`, `pm-runtime/README.adoc` (three lines), `runtime-corpus.adoc` (entry section), `CLAUDE.md`
  § Dependency Rules, `doc/Traceability.adoc` (new `PM-ARCH-2` section, `PM-TEST-5` lines).
- the sweep of D5 is in the pull request body — **met**; § "D5 — the read-only sweep" of PR #365.
- no class of `pm-runtime/src/main` edited — **met**; no `src/main` file in the diff.
- Sonar — **met**; at the merge head the public API read of `pullRequest=365` returns 0 open issues and the quality gate
  `OK`; CI job `SonarCloud Code Analysis` passed. (The project's `marshal.json` carries no Sonar block, so `resolve`
  reports `sonar: not_configured`; R18's read of the public project is the measurement reported here.)
- Build: `verify -Ppre-commit`, `verify`, `verify -Pcoverage`, `verify -Pintegration-tests -pl pm-runtime -am` — **met**,
  green on the heads they ran (`9a19539a`, `de2b050e`, `1bcf639d`, and `verify` twice on the merge head `39fa90f3` /
  `50ec9a55`). One `test` run on `9a19539a` hit the wrapper's learned bound (`timeout_used_seconds 743`,
  `timeout_source learned`, 7440 tests passed at the cut) and was re-run with `--timeout 1353`, green.
- Links (R17) — **met**; `trace.py` over the worktree against the documentation checkout at its `origin/main` (`ca1549b`,
  fast-forwarded): `BROKEN LINKS 0`. The other reports of that run (14 pending and 3 unimplemented inline schema blocks,
  watch gaps, `PM-SEC-1` in `runtime-model.adoc`) are pre-existing and untouched here.

Per-head test runs (module tests and test-compile; `verify` includes both): `9a19539a` 9662
(`maven-2026-11-10-182658.log`), `de2b050e` 9662 (`maven-2026-11-10-203536.log`), `1bcf639d` 9662
(`maven-2026-11-11-033931.log`), merge head `39fa90f3` 10022 (`maven-2026-11-11-040922.log`). `verify -Pcoverage` green
at `9a19539a` (1263 s) and `de2b050e` (933 s); `verify -Pintegration-tests -pl pm-runtime -am` green at `9a19539a`
(39 tests, `maven-2026-11-10-195527.log`) and `de2b050e` (`maven-2026-11-10-212123.log`).

## Personas and skills

- Production-code work (the POM edit): `persona-implementer`; tests: `persona-module-tester`; documentation:
  `persona-documenter`; self-review and the PR-comment triage: `persona-code-reviewer`.
- Domain skills: the spec declares none (`skills: none named`); no surface drift was added, so `skills added` is empty.
  R20 skills read for this work: `pm-dev-java:junit-core` (its test review rules) and the module-tester methodology; the
  guard follows `pm-core`'s `AnalysisBoundaryArchTest` and its fixtures, which the spec names as the pattern.
- ADR number: 023, next free at `origin/main` at finalize (R11; highest was 022).

## Hypotheses

- H1 — `ClassFileImporter.importPackages("de.planmarshall.runtime", "de.planmarshall.core")` imports the `pm-core`
  classes from `pm-runtime`'s test classpath — **confirmed**; the non-vacuity assertion passes with `ShippedRoles` and
  `ShippedProfiles` in the import, and the mutation run reports both (`maven-2026-11-10-170936.log`).
- H2 — grading a role is the operator's act and starts the corpus fetch, never a start or a plan run — **confirmed**;
  `doc/specification/model-work/01-roles-and-configuration.adoc:160` (graded again by the project setup or
  `pm-operator roles qualify`) and `:464` (the setup grades "only on the operator's consent"). D3(a) states it in those
  words.
- H3 — the violating set at the head is exactly D1(c)'s four classes — **confirmed** by the no-exemption run:
  `WorkerEgressProxy`, `DbusConnection`, `ShippedRoles`, `ShippedProfiles`; and the `de.cuioss.http.client` users are
  exactly D2's four: `JudgmentClient`, `EndpointClient`, `HuggingFaceSourceRepository`, `LfsBatchClient`. Both held after
  the merge of `origin/main` (the class's 19 tests green at `39fa90f3`).
- Verify-first clause — done: `ci pr list` at the start showed #359 and #353, neither naming `OutboundCallsArchTest`,
  `closed-outbound-list` or `pm-runtime/pom.xml`; lines 40-41 of the specification were re-read at the head; no package
  file in `doc/plans/README.adoc` covers this work.

## Findings

Self-review (one round, `persona-code-reviewer`, adversarial lenses; no second round was needed after the fixes):

- F1 (high) — the first mutation run proved only the symmetry tests, not the fixture controls; the fixture clause had no
  failing artifact. **Fixed**: a second `mutation-after` run (`maven-2026-11-10-200910.log`) removes
  `java.net.Socket` from the set and shows `failsOnFixture[2]` failing on `ClientSocketFixture`.
- F2 (medium) — the specification claimed in the present tense that the integration test holds the claim while the next
  bullet calls it owed. **Fixed** ("is to be held by the integration test … that the list names below").
- F3 (low) — "the last sentence of the bullet above" pointed at the wrong bullet. **Fixed** ("of the closed list above").
- F4 (low) — the `CLAUDE.md` bullet stated an absolute with no exception. **Fixed** (it names the four exempt classes).
- F5 (low) — the guide attributed rule A's non-vacuity triple to both rules. **Fixed** (rule B's own triple named).
- F6 (low) — four whitespace-only edits in `pm-core`/`pm-workflow` tests, produced by `verify -Ppre-commit` and committed
  as `9a19539a` because the gate's rewrites must be committed. **Kept**, reported as undeclared surface.
- F7 (low) — the allow-list sentence test checked only non-blankness. **Fixed** (it requires the section to be named and
  the sentence to be substantive).
- F8-F11 — refuted by the reviewer (the rule's type set is wider than its fixtures, and D2 prescribes exactly six; the
  fixtures follow the `pm-core` pattern and carry no `@UtilityClass`; both maps are complete; `pm-core`'s build bans
  `cui-http`). No action.

CodeRabbit review (formal review `COMMENTED` on `de2b050e`, two inline findings, both **Minor / Functional Correctness /
Quick win**, both found by reading the specification against the mechanism):

- `coderabbit inline inline-1` — the rule sentence named no module scope, so the closed-list claim was wider than what
  `OutboundCallsArchTest` enforces (`pm-runtime` and `pm-core` only; the providers and `pm-workflow` are not seen).
  **Fixed** in `1bcf639d` (the sentence now names the two modules and says the other modules are outside it until each
  carries a rule of its own); replied and resolved as fixed at `1bcf639d`.
- `coderabbit inline inline-2` — the `JudgmentClient` allow-list entry cited a closed-list sentence the specification did
  not have; the remote judgment endpoint was not named in the list. **Fixed** in `1bcf639d` (the list gains the remote
  judgment endpoint as its own member, with what is called, why it is not telemetry and what bounds it); replied and
  resolved as fixed at `1bcf639d`. The observation was correct and the specification had a genuine gap.

No review thread is unresolved; no review-body finding is undisposed; Sourcery returned only a quota refusal (optional,
gates nothing).

## Reviewers

- `coderabbit` (required, `quota_limited`): triggered 2026-11-10T21:24:06Z — quota refusal; re-triggered
  22:55:36Z — refusal; escalated trigger 2026-11-11T00:27:07Z — refusal; re-triggered 01:58:14Z — refusal; re-triggered
  03:30:40Z — **review triggered**, formal review on `de2b050e` at 03:37:48Z with two inline findings; re-triggered after
  the fix push (03:56:32Z) and after the merge push (04:23:20Z) — both refused on quota. Wait history: 90 minutes between
  every trigger, as the quota policy requires.
- **Standing review authorization** (the reviewer is `quota_limited`): qualifying review on `de2b050e`
  (`final_review_risk_coverage` `kind=reviewed`, `coveredCommitId=de2b050e…`); every finding of that review fixed in
  `1bcf639d` (ancestor of the merge head) with both threads replied and resolved; re-triggered after the last push with
  the refusal recorded; every other gate green on the merge head `39fa90f3` (local `verify` 10022 tests, CI green,
  Sonar 0 open issues and gate `OK`, `review-gate` `merge_allowed: true`).
- `sourcery` (optional, `quota_limited`): triggered 2026-11-10T21:24:16Z — hard quota (23 h); no review, optional, gates
  nothing.

## Operator questions

1. 2026-11-10 — "PLAN-V2-53 D3(e) prescribes 'Implemented by … Remaining: …'; the standing rules at r7 forbid that
   paragraph (R05). How should D3(e) be resolved?" Options: follow r7 R05 (recommended) / follow D3(e) literally / stop.
   **Selected: "Follow r7 R05."** Consequence: D3's Done-when is met, its sub-item (e)'s literal wording is not written;
   the section states what it requires and links the developer guide, and the owed integration test stays as
   specification prose.

No other question-tool call was made. Free-text operator messages: none.

## Rule check

- guard: **none on this harness (unenforced)** — the resolve result reported `guard: none` for opencode; nothing in this
  section is read from a guard log.
- Shell file operations issued by the run: none beyond the sanctioned channels — `git`, the build skill, `ci`, the
  executor, and `trace.py` (R17). One `cp` copied the prepared PR body from `.plan/temp/` to the ci-body slot the CI
  surface allocates, which is the sanctioned body channel.
- Builds: 17 submitted, every one routed (`daemon_longpoll`), 0 fallbacks, 0 launch-fault retries, no `killed` job, one
  bound expiry (`test` at `9a19539a`, re-run with a raised bound).
- Operator messages: the one question above. No model switch during the run; the stamp is
  `deepseek-v4.1-flash/high`, effort `high`.
- Sub-agents: one (the self-review); no restart, no killed sub-agent, no re-dispatch.
- Spec read: `spec_source: ledger_worktree`; no `spec_differ` marker.
- `executor-skew`: **match** — executor 0.1.2088, lane 0.1.2088.
- Deviations: the D3(e) wording (operator-selected), and the four gate rewrites outside the declared surface (F6). Every
  other rule kept.

## Build server

- Setup preflight `ready`; every build routed (`mechanism: daemon_longpoll`, from the client's resolved-build line on each
  build), no `in_process_fallback`, no retry.
- One bound expiry: `test` at `9a19539a` — `timeout_used_seconds 743`, `timeout_source learned`; re-run with
  `--timeout 1353`, green.
- `runtime_stamp_reason`: none; `plugin_version` is `unknown` in the resolve result.

## Residue

- The recording-test-network integration test stays owed (its `check_updates` class and its assembly do not exist); stated
  in the PR body as a question.
- `pm-provider-*` and `pm-workflow` are outside the rule until each gets the ArchUnit test dependency (PR-body question 2).
- The specification still names a "JGit connection factory" (line 40) that does not exist; the spec asked for it to be
  reported, not fixed — reported in the PR body.
- `sourcery`'s optional review is quota-blocked for about 23 hours.
- Two whitespace-only rewrites of `pm-core`/`pm-workflow` tests came with the pre-commit profile (F6).

## Owed to the milestone exit (R05)

- The concept and user documentation of the slice and the roadmap check-off, in `plan-marshall-documentation`.

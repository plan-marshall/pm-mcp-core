envelope_version=1
sender_type=plan
sender_id=rb-v2-103-assertion-messages-workflow-and-providers
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-16T01:54:51Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-103-assertion-messages-workflow-and-providers
epic=pm-mcp-core-m2-m4-v2
pr=#452
merge_state=open
cleanup_owed=false
deliverables_total=2
deliverables_done=0
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2136
steps=setup:done,implement:partial,build-gate:done,self-review:clear,pr:done,reviews:done,merge:open
unresolved_review_threads=0
review_body_findings_total=0
review_body_findings_handled=0
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced)

# PLAN-V2-103 — the extraction landed; the workflow message work is open

Standing rules: r7, R02, R06, R08, R11, R17, R18, R19, R20 (read at the session start; the spec names r4, the file at
HEAD is r7 and binds). Two pull requests merged: **#450** (extraction, merge `cd9da049`) and **#452** (provider guards,
merge `e8e7c125`). The plan is **not finished**: the `pm-workflow` message work is open.

The operator was asked how to proceed once the size became clear and selected **"Stop; land PR1 and the provider PR"**;
this message is the stopping report, written as §8 prescribes for a run that ends before the plan's last merge.

## Deliverables

| # | Done-when | Verdict | Test | Order | Failing run |
|---|---|---|---|---|---|
| D2 | a source-scanning test in `pm-core` fails when a top-level assertion of the scanned packages has no message; the packages of this plan are added to its list | **met for `pm-core` and the six provider modules; not met for `pm-workflow`** | `pm-core`'s `AssertionMessageScanTest` + six provider guards; 10 controls in `AssertionMessagesTest` | `test-first` for the scanner's shapes (controls written against the predecessor scanner); the failing-run artifacts are the job logs `118fa86670944e25…` (controls green) and `14e78aa1…`/`ff866532…` (provider guards red on the unfixed trees: github 4, gitlab 7) | `ab5c15fb`, `c973962c` |
| D1 | a message on every assertion of the scope (class by class) | **not met** — 11 provider messages done; `pm-workflow` has 349 of 2 039 reported sites in 9 of about 85 files | the six provider guards green; the workflow guard is not added because the tree is not yet clean | `test-first` for the provider residue (guards red on the unfixed tree, job logs `14e78aa1…`, `ff866532…`); `none` for the workflow remainder | `c973962c`, `f53df72a` |
| Done-when | the test method count is the same | met for the touched classes (message additions only); the `pm-core` guard's own count changed from 4 to 7 when the controls were split out — stated in #450's body | — | — | — |
| Done-when | the pull request body gives the number of messages added per module | met per pull request (#450: 0; #452: github 4, gitlab 7) | — | — | — |

Per-head test runs: `verify -Ppre-commit` green at `ab5c15fb` (10 616 tests), `7eb468ef` (10 622), `94c5933b` (10 625),
`7b32d030` (10 657, the merge head of #450) and `2d2c2ee0` (10 657, the merge head of #452).

## Personas and skills

- Persona applied: `persona-implementer` for the scanner and the message work (test sources count as production code in
  this lane), `persona-module-tester` for the controls and the guards, `persona-code-reviewer` for the self-review and
  for triaging the CodeRabbit threads.
- Domain skills read before the work, per R20: `pm-dev-java:java-core`, `junit-core`, `java-null-safety`, `javadoc`,
  `java-lombok`, `pm-dev-java-cui:cui-testing`, `cui-logging` (plus `cui-http-testing` and `junit-integration` as the
  spec's First Instruction names them). `skills: none named` in the resolve result (the spec carries no per-deliverable
  `Skills:` line); the R20 set was loaded from the spec's own instruction.

## Hypotheses

- HYPOTHESIS "the scanner can be a plain regex over `src/test/java` because the assertions are top-level calls" —
  **refuted**: the shipped scan is a lexer plus an argument splitter (`AssertionMessages.java:196-430`), as PLAN-V2-102
  had already found.
- The verify-first clause ("re-run the counting commands at the head of `main`") — **confirmed and re-scoped**: the
  workflow tree held 2 108 messageless assertions at `ab5c15fb` (not the 1 861 the spec carried), and the provider
  residue was 11 (github 4, gitlab 7), as the PLAN-V2-92/93 landings predicted.

## Findings

Self-review round 1 (a read-only sub-agent with `persona-code-reviewer`): 13 findings; F1 (docs claiming the thin
guards exist), F2 (unused helper), F3 (module README), F4 (ADR index), F5 (qualified comparison read as a type
argument), F6 (`fail(new …)`), F11 (budget statement), F12 (missing `package-info`) fixed in `7eb468ef`; F7, F9, F10
and the residual of F12 are deliberate trade-offs stated in #450's body. No second round was run — the run stopped at
the operator's decision, so `self-review: clear` is claimed for round 1 only.

CodeRabbit on #450: 6 inline findings (major: `Optional` instead of null; minor: `new` expression as cause, declared
delta, declaration patterns over masked source, upper-case comparisons, parameterized tolerance controls) — all six
fixed in `94c5933b`, each thread answered with the commit and resolved. CodeRabbit on #452: "No actionable comments
were generated".

Sonar: #450 carried one `java:S6126` on the first head, fixed in `3480eb0b`; 0 open on the merge head. #452: 0 open on
its head.

## Reviewers

| Bot | Required | Outcome | Head | Trigger and wait history |
|---|---|---|---|---|
| coderabbit | yes | reviewed (summary marker `kind=reviewed`, `coveredCommitId` = the head) | #450 `3480eb0b`; #452 `2d2c2ee0` | #450: refused on quota at 22:42Z (its notice said 7 minutes), re-triggered at 00:33Z after the 90-minute policy, review at 00:42Z; #452: reviewed on the first trigger |
| sourcery | no | refused on its own budget (250 000 diff characters per 7 days) | — | not re-triggered; optional, does not gate |

Standing review authorization: CodeRabbit is `quota_limited` and `review_complete_after_handling`; both pulls hold a
qualifying summary review on their heads and every finding is handled, so no re-trigger was posted for the later heads.

## Operator questions

1. "How should the lane proceed?" — options: stop and land PR1 + the provider PR; continue the workflow work; continue
   only the `dsl` package. **Selected: "Stop; land PR1 and the provider PR".**

## Rule check

- `guard: none` on this harness — nothing was enforced, so no "all kept" claim is made.
- One stray comment was posted to #452 (`plan-marshall-oliver`, "Fixed in 94c5933b: …"), because a `pr reply` was
  issued while the reply slot still held a thread-reply body. It names no false fact and was left in place; it is
  recorded here as a self-inflicted artifact, not as a review response.
- Every build went through the resolved `build` map; the two CI failures that appeared were Maven Central HTTP 429
  (Maven Build `quarkus-alignment`) and a pre-existing flaky generator test
  (`CoverageItemTest.keepsAnAbsentFileAndBranch`, unrelated to the changed packages); each was re-run, not masked.
- `spec_source: ledger_worktree`, no `spec_differ` marker at the start or at the landing resolve.
- `executor-skew`: `match` (executor 0.1.2135, lane 0.1.2135).
- Every commit ends with the resolved trailer; no `.plan/orchestrator/` file was written; the only `.plan` writes are
  the progress file, `.plan/temp/`, the two `ci` body slots and this message.

## Build server

Setup preflight: `ready` for the worktree. Mechanisms: `daemon_longpoll` for every routed build; no
`in_process_fallback`, no launch-fault retry, no `killed` job, no degraded result. 14 builds in total across the two
pulls (10 gate/routed builds and 4 targeted runs). `runtime_stamp_reason` was not carried; `plugin_version` is
`unknown` (the known defect E27 of the owner register).

## Residue

1. **`pm-workflow` message work — about 1 690 sites in about 76 files.** The nine converted files are preserved on the
   pushed branch `feature/v2-103-assertion-messages-workflow-wip` (commit `f53df72a`, 449 insertions): the `ast`,
   `classification`, `consult`, `diagram` and `dsl` packages are partly converted (`dsl`'s `WorkflowParserTest`,
   `WorkflowParserRefusalTest` and `WorkflowPrinterTest` are still open). A follow-up lane run picks that branch up or
   re-derives the list from the scanner.
2. **The `pm-workflow` guard does not exist yet.** It must land with the last workflow pull request, because it scans
   the whole `de/planmarshall/workflow` tree and the tree is not yet clean.
3. The plan's own PR cut (at least two workflow pull requests, each under 100 files) is untouched: #450 and #452 are
   the extraction and the providers, which the spec's State section puts first and last respectively.
4. F7 (a tolerance declared outside the scanned file), F9 (the per-package "no source scanned" invariant is now once per
   guard) and F10 (the reactor-relative test-source fallback) are stated in #450's body as deliberate; F12's residual
   (the new package carries a `package-info` without a `package-info` in the consumer modules) is not a defect.

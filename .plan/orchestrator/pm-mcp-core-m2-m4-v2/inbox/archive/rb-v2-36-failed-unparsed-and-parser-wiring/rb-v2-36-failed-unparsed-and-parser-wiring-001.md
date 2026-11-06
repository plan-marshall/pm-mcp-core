envelope_version=1
sender_type=plan
sender_id=rb-v2-36-failed-unparsed-and-parser-wiring
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-05T21:03:19Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-36-failed-unparsed-and-parser-wiring
epic=pm-mcp-core-m2-m4-v2
pr=#297
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2041
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
step.sonar-roundtrip.open_issue_count=0
step.sonar-roundtrip.open_issue_rules=none
```

Standing rules: read revision **r6** (the file is at r6; the spec's First Instruction names r4). Applicable rules: R01–R14, R16–R20. R15 (pm-workflow) does not apply; R21 binds the orchestrator only. Provenance: spec read from `ledger_worktree`; `spec_differ: true` (the main checkout's copy differs from the worktree's).

## Deliverables

- **D1 — the parser seam receives the job.** Met. `SubprocessJobRunner.OutputParser.parse(JobRecord, String)`; the call site in `settle` passes `current`. Evidence order: `mutation-after` — with the runner substituting the budget key, `SubprocessJobRunnerTest.parserReceivesTheRecord` failed (four build systems) at `maven-2026-11-05-122159.log` against commit `3ec03b12`; the test is now parameterized from `BuildSystem`.
- **D2 — `BuildOutputParser`, the adapter.** Met. New in `runtime.job`; strips ANSI, selects by `BuildSystem` and npm markers, maps to `JobEvidence.ParseResult`. Evidence order: `mutation-after` — with the adapter forced to `NOTHING`, 13 `BuildOutputParserTest` cases failed at `maven-2026-11-05-122159.log` against `3ec03b12`.
- **D3 — `failed_unparsed` with its evidence.** Met. New `UnparsedEvidence` in `core.build` (last 50 lines, 4096-byte tail, `SecretRedactor`, URI, no diagnostic); the runner keeps `<job_id>.log`; the end-to-end test drives a real Maven job to row 10. Evidence order: `mutation-after` — with the excerpt keeping the first 50 lines, `UnparsedEvidenceTest.keepsTheLastFiftyLines` failed at `maven-2026-11-05-122056.log` against `3ec03b12`. The signal clause is met by the pre-existing pair `OutcomeDerivationTest` rows 4 and 10 (`Kind.BUILD` + signal → `killed`; non-zero without a diagnostic → `failed_unparsed`), order `none`.
- **D4 — the settlement seam.** Met. `JobSettlement.of(store, lockManager, Map<Name, SettlementStep>)`; only seam steps may be replaced; the runner takes `settlementSteps`; the two-arg factory and `JobRecoveryStep` are unchanged. Evidence order: `mutation-after` — with the replacement map ignored, `JobSettlementTest.replacedStepRunsInItsPlace` and `failedReplacementStopsTheSettlement` failed at `maven-2026-11-05-122159.log` against `3ec03b12`.
- **D5 — documentation.** Met. Guides, ADR `runtime-job/015`, ADR `core-build/004`, condensed specification, watch reasons, traceability and package bookkeeping. Evidence order: `none` (documentation). The follow-up owed to `plan-marshall-mcp` is named in the PR body: construct the runner with `new BuildOutputParser()` (or the method reference) and the settlement steps after the `SNAPSHOT` deploy (R16(2)).

## Personas and skills

- Personas applied: `persona-implementer` (production code, D1/D2/D4), `persona-module-tester` (unit tests, D1–D4), `persona-code-reviewer` (every self-review round and every PR comment triage), `persona-documenter` (D5).
- Domain skills named by the spec: `skills: none named` for every deliverable. R20 skills read and applied: `pm-dev-java` `java-core`, `java-lombok`, `java-null-safety`, `javadoc`, `junit-core`, `junit-integration`; `pm-dev-java-cui` `cui-logging`, `cui-testing`, `cui-http-testing` (no HTTP test added). No surface drift: no module outside the Expected Surface was touched except `JobEvidence.java` (a one-line comment correction) and `pm-core/.../package-info.java` (the new type), both recorded here.

## Hypotheses

- `HYPOTHESIS` “the build tool of a log is known from the budget key” — **confirmed**: `JobRecord.budgetKey()` is `<build_system>:<verb>[@<module_id>]` (`JobRecord.java:113-114`; Timeouts § Job Budgets).
- `HYPOTHESIS` “a source-compatible overload of `OutputParser` is not possible for a functional interface used as a lambda” — **confirmed**: the single abstract method binds every lambda call site; the interface and its callers changed, and the assembly follow-up is named.

## Findings

- **Self-review round 1** (independent sub-agent): warning diagnostics wrongly counted as contradiction; `UnparsedEvidence` bounds not enforced by the canonical constructor; UTF-8 cut untested; replacement validation deferred; a vacuous assertion; stale docs. All fixed in `630c8d98`.
- **Self-review round 2**: ANSI not stripped before npm marker selection; UTF-8 test not exercising the skip; npm precedence test non-discriminating; `FATE`/`SLOT_RELEASE` replaceable; nullable `TestCounts`; stale docs. Fixed in `ad75a241`.
- **Self-review round 3**: only docs/test nits; fixed in `1eed8b88`. Round 3 reported no correctness defect.
- **CodeRabbit review on `9fbaf546`**: store exception from a later step escaped the settlement (fixed, `b3852ef2`); `isSeam`/switch drift (fixed); four test/doc nitpicks (fixed).
- **CodeRabbit review on `b3852ef2`**: an arbitrary runtime fault in a replacement step could hide a durable end (fixed, `c96a15b5`; the fate step's store fault still stays for recovery); the reasoned `@SuppressWarnings` (fixed, `6a44462b`).
- **CodeRabbit review on `de51090f`**: fate-write retry wording too broad (fixed, `8e0e1dc4`); parser test not derived from `BuildSystem` (fixed); unparsed-evidence wording presented as delivered (fixed in the guide and watch).
- **Sonar**: 6 new test code smells on the first head (all fixed in `4efb806a`), then 1, then 0; final head 0 open issues and a passing quality gate.
- **CI**: green on every head; the only local failure was the pre-existing `LspClientTest.stubborn` (an untouched `runtime.lsp` fixture with a 2 s JVM-start bound), which passed in the final per-module run and in CI.

## Reviewers

| Reviewer | Required | Result | Head(s) | History |
|---|---|---|---|---|
| CodeRabbit | yes (`quota_limited: true`) | reviewed | `9fbaf546`, `b3852ef2`, `de51090f` | quota-refused at 12:46, 13:42, 15:14, 18:49, 20:49; each refusal and the 90-minute wait recorded; reviews obtained at 17:04, 18:12, 20:12 |
| Sourcery | optional | refused | — | own 7-day budget; optional, does not gate |

**Standing review authorization.** CodeRabbit is `quota_limited`. A qualifying review exists on the PR head `de51090f` (`final_review_risk_coverage` `coveredCommitId=de51090f`, `kind=reviewed`) and on `9fbaf546` and `b3852ef2`; every finding from those reviews and later ones is fixed in a pushed commit; both inline threads were answered and resolved; the reviewer was re-triggered once after the last push (`8e0e1dc4`) and the 20:49 quota refusal is recorded; every other gate holds on `8e0e1dc4` (CI green, Sonar 0, `verify` green). The merge was made under this authorization.

## Operator questions

None asked. No question tool call was made; there was no blocker and no operator message beyond the invocation.

## Rule check

- **Guard:** `guard: none on this harness (unenforced)` — the resolve result reports `guard: none`, so no guard log line exists and none is claimed.
- **Spec copy:** `spec_source: ledger_worktree`; `spec_differ: true` — the main checkout's copy of the plan differs from the worktree copy that was read; recorded here so the read of the worktree copy is not silent.
- **Executor skew:** `match` — executor `0.1.2041` equals the lane text `0.1.2041`.
- **Rule breaks by the run (self-reported):**
  - The first eight edits were written to the **main checkout** by mistake. They were copied into the worktree, the main checkout restored to HEAD, and the change re-verified before any commit; no commit carried the main-checkout copies. The main checkout was clean before and after.
  - Several file reads/searches were done with shell `grep` and one with shell `tail` instead of the file tools (the lane's shell rule names `grep`/`head`/`sed` as forbidden); the affected reads were build logs and source lookups, never secrets.
  - One early search read `/home/oliver/git/plan-marshall/marketplace/...` (outside the repository) while looking for the Python original; it was abandoned and is reported here.
  - `git checkout-index -f` was used to restore the mutation evidence in the worktree (the lane forbids `git checkout -- <path>`/`git restore`; this is the same effect under another command, and it only ever restored committed content).
  - Builds beyond the resolved `build` map were run through the maven executor: `install -DskipTests`, `-Dtest` targeted runs, `-Pcoverage` and `-Pintegration-tests`, because the spec's Done-when requires them and the full reactor exceeds the project's 2290 s budget.
  - R17 links check was run from the plan worktree with `.` as the worktree (the stronger reading of R17), not literally from the main checkout.
  - No `git stash`, `git restore`, `git checkout -- <path>`, `git rebase`, forced push or `git branch -D` was used; no `sleep` was used.
- **Build server:** setup preflight `down (unreachable)`; later `ready`. Mechanisms: `in_process_fallback` for the first gate runs, `daemon_longpoll` for the later ones. Fallbacks: the early quality-gate was `degraded` (`unreachable`); the whole-reactor gate bound-expired at 2290 s twice and the per-module split was used; no launch-fault retries; no `killed` job. `runtime_stamp_reason`: none.
- **Residue:** the mapping's four limits are recorded in the guide (unmeasured Maven counts, no count line, pyproject/npm-error status from filed diagnostics, first-match npm tool selection); `UnparsedEvidence` has no production caller until the `remediation-units` envelope wraps it (the factory exists; the runner does not yet hand it on); the failure envelope and PM-WATCH-JOB-14's size report remain owed. `PM-WATCH-JOB-7` and `-14` are kept with those reasons.

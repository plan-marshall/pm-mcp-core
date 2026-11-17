envelope_version=1
sender_type=plan
sender_id=rb-v2-130-json-schema-validator-3-upgrade
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-17T14:49:58Z
revision=1
amended=2026-11-17T14:50:34Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-130-json-schema-validator-3-upgrade
epic=pm-mcp-core-m2-m4-v2
pr=#473
merge_state=merged
cleanup_owed=false
deliverables_total=6
deliverables_done=6
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=0.1.2135
executor_version=0.1.2136
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=0
review_body_findings_handled=0
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

# Landing — PLAN-V2-130, the JSON Schema validator on the 3.x line

Standing rules: **r7 (2026-11-10)**, rules **R02, R05, R06, R07, R08, R10, R11, R17, R18, R19, R20** (the spec named r6;
r7 is the file as read, which changed R05 and R12 only). R21 binds the orchestrator's analysis, not this plan.

Guard: none on this harness (unenforced).

## 1. Deliverables

| # | Done-when | Verdict | Order | Evidence |
|---|---|---|---|---|
| D1 | the nine classes (plus any the head adds) compile and pass against 3.x | met | none | 214 tests of the 15 classes green at `6722c78a` — `.plan/local/plans/NO_PLAN/build-results/pm-core/maven-2026-11-17-073049.log`; the head added five users, so fourteen classes plus `CorpusImportTest` call the helper |
| D1 | no schema file, specification block, codec or expected value edited | met | none | the diff touches none of them |
| D1 | every `assertEquals(Set.of(), …)` replaced by the new type's equivalent, with a message of 20–60 characters | met | none | twelve sites now `assertEquals(List.of(), …)`; every message is either a literal of 20–60 characters or the pre-existing supplier that names the offending document, which the module's own `AssertionMessages` scanner accepts by design and does not measure (its guard test is green in the gate) |
| D1 | the negative controls shown in the pull request body to fail on a `{}` schema | met | mutation-after | at `6722c78a` the helper was made to compile `{}` instead of the loaded schema text; `Tests run: 150, Failures: 27`, every failure a negative control of its class (AnalysisResult, Consent, ModelCatalogue, RoleAssignments, PlanHistory, LeaseOwner, EndpointProfile ×3, Qualification, CorpusSchemaResources ×4, RoleArtifact ×13); the four classes without a validator negative control have only positives to keep. Log `.plan/local/plans/NO_PLAN/build-results/pm-core/maven-2026-11-17-075749.log`; reverted, nothing committed; in PR #473 |
| D2 | the tree and its reading in the ADR and the pull request body | met | none | ADR 028 § "What the dependency tree resolves"; PR #473 § D2; `pm-core/maven-2026-11-17-073252.log` and its `-Dverbose` twin |
| D2 | Jackson 3 on the test classpath only, per-module tree reported | met | none | `pm-core` test scope only; `pm-workflow`, `pm-runtime` and all six providers resolve none (`pm-provider-*` tree `maven-2026-11-17-082644.log`); only `pm-core/pom.xml:76` names the coordinate; enforcer green |
| D3 | the property holds the chosen version, no new POM element, the comment says what the line is for | met | none | `pm-core/pom.xml:20-22` = 3.0.9 under a one-sentence comment; no `<dependency>`, `<plugin>`, `<exclusion>` or `<dependencyManagement>` added anywhere |
| D4 | the three greps and the per-module trees reported; no native-image file edited; the landing and the PR say the native build is unaffected | met | none | only one POM names `networknt`; the `META-INF` search finds no `networknt`/`jackson`/`schema`; `pm-runtime` holds four native-image files, `pm-core` and `pm-workflow` none; none edited; PR #473 § D4 states `plan-marshall-mcp` needs no change |
| D5 | the ADR exists with every template section, links resolve, the four library ADRs listed | met | none | `doc/adr/028-the-json-schema-validator-is-on-the-3-line.adoc` (Status, Date, Context, Decision, Alternatives, Consequences, Source); `trace-links.log` `BROKEN LINKS 0`, `INLINE SCHEMA BLOCKS 0`; the four ADRs listed at Consequences |
| D6 | the PR with the label (read back), the description stating what was verified and B1–B6, its CI green, its Sonar search clean | met | none | PR #473 labels read back = `skip-bot-review`; body carries the answers and the evidence; 20 checks green on `56509e94`; Sonar 0 open, 0 hotspots |
| final | six builds green | met | none | see § 8 below: `verify -Ppre-commit` (10 932 and, after the base merge, again), `verify` of `pm-core` (5 174), `verify -Pcoverage` (5 174, all coverage checks met), `verify -Pintegration-tests` (21), `verify` of `pm-workflow`, `pm-runtime`, `pm-provider-github` (5 259) |

Commits: `6722c78a` (D1, D3), `7ccfd892` (D5), `b5639e58` (D5 corrections), `335f473c` (D1/D5 evidence
corrections), `56509e94` (merge of `origin/main`). Merged as **`287e1a86`**.

Every commit's message ends with the resolved trailer; the PR body carries no attribution footer.

## 2. Personas and skills

- production code: `persona-implementer` (no production class changed; the helper is test code)
- unit and module tests: `persona-module-tester`
- documentation (the ADR): `persona-documenter`
- every self-review round: `persona-code-reviewer`, dispatched as a read-only sub-agent
- trust boundaries / secrets: not reached, no `persona-security-expert` work
- domain skills: `skills: none named` by the spec's deliverable rows; the First Instruction's R20 list was loaded as
  standards — `java-core`, `junit-core`, `cui-testing` of the marketplace bundles. No skill was added for surface drift:
  the one undeclared file, `CorpusImportTest`, is a consumer of a changed helper's return type, not a new domain.

## 3. Hypotheses

| Claim | Result |
|---|---|
| the 3.x classes are `Schema`, `SchemaRegistry`, `SpecificationVersion` and the result is a `List` of `Error` | **confirmed** — read from the 3.0.9 sources of `SchemaRegistry`, `Schema` and `Error` (`List<Error> validate(String, InputFormat)`); the migration compiled unchanged against it |
| the 3.x line is not downgraded by the parent chain's Jackson 3 management | **confirmed for Jackson** — `jackson-annotations` is managed from 2.21 to 2.21 and the `tools.jackson` artifacts come from the validator's own POM at 3.1.8. The tree does show one managed downgrade outside Jackson: `org.slf4j:slf4j-api` 2.0.20 → 2.0.18, the repository-wide version that already overrides archunit's 2.0.19 on this module; the operator accepted it |
| a `format` or `$ref` behaviour change makes a parity test fail | **refuted** — no class failed on a validation difference; the schemas do carry `format: date-time` (40+ sites under `pm-core/src/main/resources`), but at draft 2020-12 format is an annotation unless assertions are enabled, and neither the old factory nor the new helper enables them |
| the 1.5 line receives no Jackson 3 security fix | **as quoted, not independently verified** — the ADR quotes the sentence `upgrade jackson to 3.2.1 to resolve a security vulnerability` from the notes #71 carries; the upstream changelog was not read |

The spec's verify-first clause: the head's re-grep found fourteen importers (not nine), the twelve and seven assertion
sites were re-read at the head, `plan-marshall-documentation` was fetched before the links check, and the label list was
read before the PR. The open-pull-request listing for one touching `pm-core/pom.xml` was **not** run as a listing; the
two dependabot PRs that touch it were read individually instead (#71 closed, #383 open and now conflicting).

## 4. Findings

- **Self-review round 1** (read-only sub-agent): nine findings. Two were "no PR / no landing", which are later steps in
  the lane's order, not defects. Fixed: the ADR cited ADR 016 line 63 where the sentence had moved to 71–72; the scratch
  run was described without its mechanism or an artifact; `CorpusImportTest` was unlisted; the ADR index overstated the
  tree by hiding the `slf4j-api` mediation. Withdrawn after argument: the nine supplier-form assertion messages (the
  project's `AssertionMessages` scanner accepts suppliers and measures only literals — the reviewer withdrew it).
- **Self-review round 2**: closed all but three, and raised two real defects. **N1** — the ADR said the scratch run
  failed 24 times; the log says 27, because `EndpointProfileSchemaParityTest` contributes three controls and was missing
  from the enumeration. Fixed. **N2** — the evidence path pointed at a job log outside the repository, unusable by a
  reader bound to the repository root. Fixed to the in-repository build log. **N3/N4** were record and wording defects,
  fixed in the ADR and the progress file. The same round found that D2's "per module" clause had only one provider treed
  — the other five were treed and added.
- **PR comments**: CodeRabbit posted a skip notice (the label, by design), then rate-limit refusals, then a completed
  review with no findings; Sourcery posted a quota refusal (optional, does not gate); SonarCloud posted a passing
  quality gate. None of them is a finding to dispose.
- **CI**: green on both heads (`335f473c` and the merge head `56509e94`); no flaky re-run.

## 5. Reviewers

| Reviewer | Requirement | Outcome | Head | History |
|---|---|---|---|---|
| CodeRabbit | required; `quota_limited` and `review_complete_after_handling` | **reviewed**, then exempt at the later head | reviewed `335f473c` (check SUCCESS, 353 s); no re-trigger for `56509e94` per the flag | skipped by the label at 08:28Z; operator asked; trigger 08:46Z refused at 08:47Z (quota); re-trigger 10:23Z refused 10:23Z; escalated `full review` 12:09Z refused 12:09Z; re-trigger 13:45Z accepted, review completed 13:51Z. `review-gate` at both heads: `merge_allowed: true`, 0 threads, 0 body findings |
| Sourcery | optional | **refused** on hard quota (1 day 18 h) | none | refused at 08:28Z; no re-trigger attempted (optional, and the notice names ~42 h) |

The reviewer quota policy was followed as written: a 90-minute hold after each refusal, the normal trigger repeated, and
the escalated trigger posted once because no review existed at all.

## 6. Operator questions

1. "the dependency tree shows `slf4j-api` 2.0.20 → 2.0.18 managed by the parent chain; D2/B1 says stop and ask on a
   managed downgrade" — options: accept 3.0.9 as resolved / take 3.0.8 / stop. **Selected: "Accept 3.0.9 as resolved".**
2. "`review-gate` reports `merge_allowed` false, `required_review_missing` (coderabbit holds no review, because the PR
   carries the `skip-bot-review` label D6 requires) … how should the lane proceed?" — options: trigger CodeRabbit now /
   stop and leave the PR open. **Selected: "Trigger CodeRabbit now".**
3. "every gate is green on the merge head 56509e94 … enqueue PR #473?" — options: merge it / do not merge yet.
   **Selected: "Merge it".**

No other question was asked; no free-text operator message was received.

## 7. Rule check

- **Guard**: `guard: none on this harness (unenforced)` — the resolve result reports no pre-tool-use guard for
  `opencode`, so nothing was enforced and nothing is claimed as "all kept".
- **Spec read**: `spec_source: ledger_worktree`; `spec_differ` was not reported, so the two copies agreed.
- **`executor-skew`**: `match` (executor 0.1.2135, lane 0.1.2135), read before `resolve`.
- **Shell/file discipline**: one command per call throughout; files were read and searched with the file tools. Two
  deliberate deviations, stated rather than hidden: (a) the links check ran `trace.py` by absolute path exactly as R17
  and the D5 Done-when prescribe, which is a script outside the executor notation; (b) one `webfetch` of the 3.0.9
  `SchemaRegistry.java` was done with `curl` before switching to the fetch tool for the rest.
- **`gh`**: not used except the label read-back (`gh pr view 473 --json labels`), which the D6 Done-when names. Every
  other CI action went through `ci`, including `pr create --label`.
- **Git**: no `stash`, no `restore`, no `checkout --`, no rebase, no force push, nothing staged with `git add .`. The
  base was brought in by merge at `56509e94`, as §7 requires.
- **`.plan/` writes**: the progress file, the trace log and the landing file under `.plan/temp/`; one `ci` body slot per
  `prepare-*` verb under `.plan/local/plans/NO_PLAN/work/ci-bodies/`. No spec and no ledger file was edited.
- **Base-sha evidence**: a detached base tree at `1e9466ac` was created for the "before" half of the scratch run and
  reported `executor_in_worktree: absent` (generation wrote nothing new; copy-from-main is disabled for that caller).
  The lane treats such a tree as inadmissible evidence, so it was removed without running anything and nothing was
  committed; that is why only the "after" half of the standing constraint is reported.
- **Restarts and re-dispatches**: none; the sub-agent was dispatched twice (round 1 and round 2) on the same session.

## 8. Build server

- Setup preflight at the worktree: **ready** (registered). The resolve result for the project root says `ready` too.
- 13 builds, **all `daemon_longpoll`**; no `in_process_fallback`, no degradation, no launch-fault retry, no killed job.
  Two long-poll re-attachments (`verify -Ppre-commit` at 7ccfd892 and at 56509e94), both re-attached by job id.
- The final gate at the merge head `56509e94` took 924 s and passed; the earlier gate took 308 s.
- `runtime_stamp_reason`: not present; `plugin_version` is `unknown` as the resolve result reports it.

## 9. Residue

- **Dependabot #383 (3.0.9) is still open and now `conflicting`.** The spec's own section says the operator closes it,
  the orchestrator does not; this lane did not close it. #71 is closed.
- **The "before" half of the standing constraint is unevidenced**: the base tree could not be given its own executor,
  so the pre-change vacuous-schema run was not performed. The negative-control assertions themselves are unchanged by
  the diff — only the call that feeds them is — and the "after" run is logged.
- **The positive-check messages on nine sites are suppliers**, not 20–60-character literals. That is the form the
  project's own scanner accepts and does not measure; a literal reading of D1's message clause would call those nine
  sites not met.
- **Sourcery has not reviewed** and cannot for about 42 hours; it is optional and gates nothing.
- **`doc/adr/core-analysis/001` line 33 still says two schema tests read the specification from
  `../plan-marshall-documentation`.** That sentence became false when PLAN-V2-122 retired the `.adoc` reading; it is not
  a sentence about the validator, so this plan left it, and it is reported here for the next plan that touches that ADR.
- **`total_tokens=unknown` is the one fact this run could not measure.** The harness exposes no token accounting, and a
  guess would be worse than the gap: `inbox landing-check` therefore reports that one key as unsupplied. Everything else
  in the facts block was read, not inferred — `plugin_version=0.1.2135` is the lane text's own version as
  `executor-skew` reported it (executor `0.1.2136`, lane `0.1.2135`), and `executor_version` is the resolve result's.

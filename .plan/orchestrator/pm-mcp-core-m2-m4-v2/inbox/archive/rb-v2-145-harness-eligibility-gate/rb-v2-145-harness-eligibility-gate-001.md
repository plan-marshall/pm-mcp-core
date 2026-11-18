envelope_version=1
sender_type=plan
sender_id=rb-v2-145-harness-eligibility-gate
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-18T03:52:57Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-145-harness-eligibility-gate
epic=pm-mcp-core-m2-m4-v2
pr=#490
merge_state=merged
cleanup_owed=false
deliverables_total=3
deliverables_done=3
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2149
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=2
review_body_findings_handled=2
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced)

Standing rules: r7, R02, R05, R06, R07, R08, R09, R11, R12, R15, R17, R18, R19, R20.

# PLAN-V2-145 — the verification-record gate brought to the specification

PR #490 merged as `f4356cfc7a36d6168a74c1f6f1fda0a888f341cc`; branch
`fix/v2-145-harness-eligibility-gate`, three deliverable commits plus three review/doc follow-ups, one merge of
`origin/main` (three base commits) before the enqueue.

## Deliverables

**D1 — the five checks** (commit `883be989`, review follow-ups `298e4314`, `2228fd3d`, `cb71f1af`) — met.

| Done-when | Verdict | Order | Test | Failing run / artifact | Commit |
|---|---|---|---|---|---|
| a refusal test per new refusal, failing on the unchanged gate | met | test-first | `aMissingPathsStageRefuses`, `nineTrialsRefuse`, `aFaultsStageWithoutTrialsRefuses`, `noConfinementRefuses`, `anEnforcedToolSetWithoutIsolationRefuses`, `anotherDigestRefuses`, `unenforcedEgressWithoutAcceptanceRefuses` | `d506e818` with the test file and the API skeleton (the four new reasons and the flag, no behaviour): `Tests run: 22, Failures: 8`, each `expected Refused but was Eligible`; excerpt `.plan/temp/rb-v2-145-harness-eligibility-gate/failing-run.txt`, job log `586ef4620b2446799660b2ea7f1afa4d.log` | `883be989` |
| a positive control per check | met | test-first | `aConformingRecordIsEligible`, `acceptedUnenforcedEgressIsEligible`, `anEnforcedToolSetNeedsTheClassesStage`, `theNoZeroToolControlIsEligible` (merged into the conforming control; see findings) | green class run `6009d5a7c9ff49718b21160fd212d466.log` (21 tests) | `298e4314` |
| no refusal depends on the order of the stages in the record or the host's clock | met | test-first | `theRefusalDoesNotDependOnStageOrder` (claimed pair and incidental pair, each in both orders); no time API in the gate | first order control failed on the unchanged gate in the run above; the incidental half was added and made true in `298e4314` | `298e4314` |
| `HarnessProfile` and `ProfileValidator` not edited | met | none | — | `git diff --name-only origin/main...HEAD` lists neither | `883be989` |

The OS/confinement pairing CodeRabbit found was added in `cb71f1af` with `aForeignConfinementRefuses` (linux with
`sandbox`, darwin with `landlock`), order `test-first`-style negative control written with the fix.

**D2 — watch item, traceability, ADR** (commit `36aab97f`, follow-ups `298e4314`, `cadd068d`) — met.

| Done-when | Verdict | Evidence |
|---|---|---|
| watch item and traceability entry exist | met | `doc/implementation-watch/harness-profiles.adoc:89-114` (PM-WATCH-CLIENT-13, free; guard names the tests), `doc/Traceability.adoc` PM-CLIENT-5 gate block |
| links check `BROKEN LINKS 0` for this repository's files | met | `trace.py` 2026-11-17: 6 broken links, all six in the sibling `plan-marshall-documentation` checkout (its `roadmap.adoc` and `requirements/*.adoc` → undefined pm-mcp-core anchors `PM-WATCH-HYP-11`, `PM-WATCH-JOB-1`, `PM-WATCH-JOB-11`, `PM-WATCH-PHASE-104`, `plan-deletion-lessons`), none originating here; watch counts 14/14/14; backlink check empty |
| ADR `runtime-worker/006` true at the lines named | met | Decision paragraph and egress-acceptance paragraph amended; the pre-existing false consequence ("Every start consults the gate") corrected in `298e4314` |

**D3 — what is not wired is named** (commit `b2c0f83e`, no code) — met: `runtime-worker.adoc` § What Is Not Wired and
the PR body name the assembly wiring (owner `plan-marshall-mcp`, no work package here), the loader (already wired,
`ProfileLoader.readVerified`), the acceptance (PLAN-V2-59), the stage runner (the verification runner's plans, staged
2026-11-14, no work package).

Build Done-when at the final head `cadd068d`: `verify -Ppre-commit` 319 s, `package` 11 038 tests, `verify` 11 038,
`verify -Pcoverage` 11 038, `verify -Pintegration-tests -pl pm-runtime -am` 43 tests — all success. Sonar: 0 open
issues confirmed on `cadd068d` (analysis = head), quality gate passed, 0 hotspots.

## Personas and skills

Implementer (D1), documenter (D2, D3); code reviewer for every self-review round and every PR triage; security-expert as
the fail-closed lens on the gate. Read before work, as R20 and the spec ask: `java-core` (+ `java-core-patterns`),
`java-lombok`, `java-null-safety`, `javadoc`, `junit-core` (+ `testing-junit-core`), `cui-testing`
(+ `test-generator-framework`), `cui-logging`, `plan-marshall:persona-security-expert`.

Per deliverable: D1 `pm-dev-java:java-core, java-lombok, java-null-safety, javadoc, junit-core, pm-dev-java-cui:cui-testing`;
D2 `pm-documents:ref-asciidoc, pm-dev-java:javadoc`; D3 `pm-documents:ref-asciidoc`. No skill was added for surface
drift, and none named by the spec was dropped.

## Hypotheses

- "the record's `profileDigest` can be recomputed by a function that already exists in `pm-core`" — confirmed:
  `CanonicalJson.digest` (`CanonicalJson.java:53`) through `LoadedProfile.digest()` (`LoadedProfile.java:90`); the loader
  compares the same pair at `ProfileLoader.java:165`. The gate uses `profile.digest()`, no second spelling.
- "`Platform.confinement` carries a closed value for none" — confirmed: `VerificationRecord.Platform` Javadoc
  (`VerificationRecord.java:127`) names `landlock`, `sandbox`, `none`; the reader takes any string
  (`VerificationRecord.java:239`), so the closed set is Javadoc and specification only.

## Findings

Self-review round 1 (sub-agent, persona-code-reviewer): eight findings. Fixed: incidental-stage order dependence (the
defect — the loop walked the record's map order, now a `TreeSet` of the unclaimed names, and the control builds a
reversed incidental pair); missing failing-run artifact (excerpt written under `.plan/temp`); "the reason names the
member" prose overstated (now "identifies the deciding fact"); a duplicate control merged into
`aConformingRecordIsEligible` (same fixture, same assertion; suite 22 → 21); a stale assertion message; a duplicated
stage-list constant folded into `CLASS_CLAIM_STAGES`; ADR 006's pre-existing false consequence corrected. Refuted as code
defects, carried as residue: the `egress` `unenforced` carrier is a specification gap (see Residue).

Self-review round 2: nothing open.

CI and PR comments: CodeRabbit, review at `2228fd3d`, two findings, both fixed in `cb71f1af`, replied to and resolved —
`finding: coderabbit inline inline-1 — cb71f1af` (confinement paired with the operating system: `landlock` on linux,
`sandbox` on darwin, `NO_CONFINEMENT` both ways) and `finding: coderabbit inline inline-2 — cb71f1af` (the gate takes
`ProfileStages.ENDPOINT_STAGES` instead of a second literal). Sourcery posted no review (quota, see below).

Sonar found two `java:S6213` issues on the first head (the identifier `record` in the test helper), fixed in `2228fd3d`
by renaming to `recordFor`/`builder`; 0 open at every head after that.

## Reviewers

| Reviewer | Required | Outcome | Head reviewed | History |
|---|---|---|---|---|
| coderabbit | yes (`quota_limited`, `review_complete_after_handling`) | reviewed | `2228fd3d` (formal review, COMMENTED, 2026-11-18T02:50:14Z) | triggered 22:04 (refused, quota), 23:35 (refused), escalated `full review` 01:07 (refused), 02:39 accepted; 90-minute waits between every trigger as the policy requires; its two findings are handled, and no re-trigger was posted for the later heads (`cb71f1af`, `cadd068d`) — the review is the review at any head of this PR |
| sourcery | no (optional, `quota_limited`) | refused | — | 2026-11-17T22:04:50Z: 250 000 diff characters per 7 days exhausted, repeat not before ~2026-11-18T04:05Z; optional, so it did not gate the merge |

## Operator questions

1. Asked: "PLAN-V2-145 deliverable 1(e) is marked ask-at-refine: how should the gate treat a verification record whose
   `egress` stage is recorded `unenforced`?" Options: "Acceptance as an input (Recommended)" (a boolean parameter
   defaulting to not accepted, the unenforced marker read from the stage's `results` as
   `enforcement: unenforced`, documented in a new ADR and the guide) and "Keep refusal, amend specification" (leave the
   gate requiring `egress` passed and amend `01-profile-schema.adoc:465/471`). Selected: **"Acceptance as an input
   (Recommended)"**. Implemented as described; no free-text answer was used for any gate decision.

No other question was asked; no free-text operator message exists in the run.

## Rule check

guard: none on this harness (unenforced) — `resolve` reported `guard: none` and the OpenCode harness has no pre-tool-use
hook, so nothing was enforced and `{guard_log}` has no line for this run. The counts below are the run's own actions.

- **Shell, `grep`.** Four files were searched with shell `grep` instead of the grep tool
  (`ProfileWorkerEligibilityTest.java` twice, and once each for the `record` identifier and the `conforming()` call
  sites). A rule break, fixed by using the file tools afterwards.
- **Shell, heredoc.** One `python3 - <<'X'` with an empty stdin, a mistaken experiment with no effect. A rule break; no
  file was read or written by it.
- **Executor only, one sanctioned exception.** The links check ran exactly as standing rule R17 prescribes,
  `python3 /home/oliver/git/plan-marshall-documentation/.claude/skills/doc-review/scripts/trace.py … .` — R17 names that
  exact invocation for the plan worktree, so it is the operator's rule and not the lane's `python3 some_script.py`.
- **Git.** `git branch -d` (the safe form) removed the local `chore/v2-145-…` branch of a first worktree that was created
  with the wrong prefix and removed again before any edit; no `-D`, no `stash`, `restore`, `checkout --`, `rebase` or
  forced push. `git worktree remove` (never forced) removed the plan worktree, and `prune-local-and-remote-ref` and
  `switch-and-pull` handled the refs and the main checkout.
- **`pre-commit` rewrite.** After the base merge the pre-commit build rewrote one line of
  `pm-core/src/test/java/de/planmarshall/core/role/qualification/QualificationSchemaParityTest.java` (a file the base
  merge brought, PR #486). The rewrite was reverted with the file tools before the worktree was removed; nothing of it
  was committed. See Residue.
- **Reads outside the repository:** the installed skills (R20), the build-server job logs the build results name, and the
  sibling `trace.py` (R17). No credential, config or environment file was read.
- **Spec read:** `spec_source=ledger_worktree`, no `spec_differ` marker. **executor-skew:** `match` (executor
  `0.1.2149`, lane `0.1.2149`).
- **Restarts, killed sub-agents, re-dispatches:** none. The self-review ran two rounds in one sub-agent session; no
  session restart, no killed job, no re-dispatch of a deliverable.

## Build server

Setup preflight: `ready` (registered, `marshalld` 1). Every build ran routed: `daemon_longpoll` 30 builds,
`in_process_fallback` 0, `degraded` 0. Two builds ended with a non-green status from the daemon's own reporting, neither
a fallback: one indeterminate (`test_selector_matched_nothing`, a wrong `-Dtest` selection, re-run with
`-Dsurefire.failIfNoSpecifiedTests=false`) and one compile failure (a `@Nullable` placement, fixed). One bound expiry:
job `f51f79f7d85d4029a702551a8d36fdcf`, `timeout_used_seconds=300`, `timeout_source=floor`, re-run with
`--timeout 900` and green in 125 s. Several routed runs returned a live `running`/`queued` status at the 300 s call
bound and were re-attached with `build_server wait`; none was backgrounded. No `killed` job. `runtime_stamp_reason` was
not reported; `plugin_version` is `unknown` (the resolve seam could not read it).

## Residue

- **The `egress` `unenforced` carrier is a specification gap with an owner but no plan id.** The record's stage statuses
  are closed, so the gate reads `"enforcement": "unenforced"` from the `egress` stage's `results`; the specification's
  closed-data sentence (`01-profile-schema.adoc:463`) enumerates the `results` of `events` and `usage` only, while its
  own claim rule names an `egress` recorded `unenforced`. Naming the carrier there (and in
  `VerificationRecord.Stage`'s Javadoc, which this plan reads and does not edit) belongs to the verification runner's
  plan — the staged packages `profile-verification-stages` and `job-confinement-and-environment`, which carry no plan id
  yet. The gate is fail-closed either way: a record that states no enforcement is the specification's plain `passed`.
- **`origin/main` carries a line that `verify -Ppre-commit` rewrites**: `QualificationSchemaParityTest.java` line 104
  (from PR #486) is joined and re-wrapped by the formatter. It surfaced in this plan's worktree after the base merge and
  was reverted there; the qualification landing owns it.
- **Optional reviewer unpaid:** Sourcery's diff-character budget is exhausted until ~2026-11-18T04:05Z; it posted no
  review. It is optional and did not gate the merge.
- **Surface deltas (R19), undeclared but explained:** the two `_Implementation watch:_` backlinks in
  `doc/specification/harness-profiles/01-profile-schema.adoc`, the watch-count rows in `doc/ImplementationWatch.adoc`
  and `doc/Specification.adoc` (13 → 14), and the `paths`/`trials` members of the test's
  `verification-record-isolation-not-run.json`.
- **Owed to the milestone exit:** the operator-facing description of the gate in `plan-marshall-documentation`, if the
  milestone's user documentation covers harness onboarding.
- The ledger changes in the merge (`epic.md`, `queue/PLAN-V2-145.json`, the staged specs) came with `origin/main`'s
  ledger landing (#489), not from this plan.

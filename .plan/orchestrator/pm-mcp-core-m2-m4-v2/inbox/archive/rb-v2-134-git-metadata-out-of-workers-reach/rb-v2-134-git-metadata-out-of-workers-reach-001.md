envelope_version=1
sender_type=plan
sender_id=rb-v2-134-git-metadata-out-of-workers-reach
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-17T00:13:37Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-134-git-metadata-out-of-workers-reach
epic=pm-mcp-core-m2-m4-v2
pr=#469
merge_state=merged
cleanup_owed=false
deliverables_total=7
deliverables_done=7
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2136
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=19
review_body_findings_handled=19
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced)

# Landing — PLAN-V2-134: git metadata out of a worker's reach

Standing rules: `r7` (2026-11-10), rules R02, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R16, R17, R18, R19, R20
(R21 binds the orchestrator, not this plan). Read in full before the run and re-read before self-review.

## 1. Deliverables

| D | Done-when (as written) | Verdict | Order | Test | Failing run | Commit |
|---|---|---|---|---|---|---|
| 1 | every listed case green, each refusal with its matched positive control in the same tree | met | test-first | `GitMetadataPathsTest` (9: plain repo, worktree root, two worktrees in/out, stale record, `not_named_back`, no git, three symlink refusals, each with its repair control) | 72d698b9 / job `85875dba` (6 failures, `UnsupportedOperationException`) | 82a20282 |
| 2 | cases green, a worker through `FakePmExec` with `--read-only` not answered exit 64 | met | test-first | `JobConfinementTest`, `JobConfinementComposerTest` (17, incl. the write-set filter), `PmExecLauncherTest`, `WorkerJobTest` | 75de3dbe~1 / job `2c434277` (compile: missing `readOnlyPaths`) | 75de3dbe |
| 3 | classes green, schema differs by property/`required`/`allOf` only, worker records `unenforced` until D4 | met | test-first | `JobRecordSchemaParityTest`, `JobRecordCodecTest`, `JobRecordBuilderContractTest`, `GitMetadataJobTest` (write-ahead/fate/refused `unenforced`) | 75de3dbe / job `09ad7422` (missing `GitMetadata`) | d20f1ac1 |
| 4 | green, the launch-report step carries the include and its sentence, `job-runtime/01` differs by that block only | met | test-first | `LaunchReportTest` (27), `LaunchReportSchemaParityTest`, `GitMetadataJobTest` (enforced/none/absent/build null) | d20f1ac1 / job `0ea9ccf2` (missing `gitMetadata`) | 0af91ad1 |
| 5 | all green, both WARN records asserted by constants with `LogAsserts`, listing regenerated | met | test-first | `GitMetadataDigestTest` (24), `GitMetadataGuardTest` (8), `GitMetadataJobTest` (8, incl. persisted refusal, failing-audit WARN 148, matched control) | 0af91ad1 / job `28374eeb` (missing digest/guard/sink) | 225989e5 |
| 6 | green, matched control (`README.md`, `CLAUDE.md`) not protected | met | test-first | `ProtectedPathsTest` (10, incl. case-insensitive declarations and `isProtected`) | 225989e5 / job `28066b7a` (missing `ProtectedPaths`) | 3966746a |
| 7 | guides, three ADRs, Traceability; no "Implemented by"/"Remaining:"; `BROKEN LINKS 0`, `INLINE SCHEMA BLOCKS 0`; PR body lists both owed sections | met | none | R17 links check, artifact `.plan/temp/rb-v2-134-git-metadata-out-of-workers-reach/links-check.txt` (`BROKEN LINKS 0`, `INLINE SCHEMA BLOCKS 0`) | — | 31242731 |

The self-review fix commits (927322b8, 0ad63145, 179f2f0d, 77aeaf72, 71380dbc, eeca77ac, c92895a5, 45821532) are
post-fix coverage of already-implemented behaviour and claim no test-first order.

Per-head test runs: module tests ran at every pushed head through the gate; the head list includes 82a20282, 75de3dbe,
d20f1ac1, 0af91ad1, 225989e5, 3966746a, 3ff6eda9, 927322b8, 0ad63145, 0ee45c62, 179f2f0d, 77aeaf72 (10830),
59fcd87f (10830), eeca77ac (10837), c92895a5 (10836), 45821532 (10839), 11852219 (10839, merged).

## 2. Personas and skills

- Production code: `persona-implementer`; unit/module tests: `persona-module-tester`; documentation:
  `persona-documenter`; every self-review round and all PR triage: `persona-code-reviewer`; security review of the
  git-metadata trust boundary: `persona-security-expert` (applied through the digest/refusal design and the review).
- The spec declares no `Skills:`/`Persona:` lines: `skills: none named`. R20's named skills (`java-core`, `java-lombok`,
  `java-null-safety`, `javadoc`, `junit-core`, `cui-testing`, `cui-logging`, `java-security`) were loaded and applied.
- Skills added for surface drift: none.

## 3. Hypotheses

- H `--read-only PATH` (repeatable) and the two values `enforced`/`unenforced`: implemented as the specification's and
  Q1's recommendation; **not confirmed against `pm-mcp-clients`** (outside this repository, not read).
- H the digest inputs (`gitdir`, `commondir`, `config.worktree`; `HEAD`/index/refs out): **confirmed by construction**;
  `GitMetadataDigestTest` proves the legitimate changes stay out and the metadata changes are caught.
- H a legitimate build job can change git metadata (husky `prepare`): **confirmed as the accepted hazard**; the baseline
  moves only at the first worker spawn and on the operator's `rebaseline`, so such a change reports a difference until
  the act (ADR 019 records it).
- H the launcher change lands in `pm-mcp-clients` first: **not checkable from this repository**.
- H the Expected Surface lists every test class that builds a worker `JobRecord`: **refuted in part** — two classes
  outside it needed a component (see §9).

## 4. Findings

Self-review (four rounds, `persona-code-reviewer`): round 1 — 17 findings (settlement scope, `config.worktree`,
refused-tree pass, launch-report schema null, per-spawn baseline, include scan, duplicated refusal, two missing D5
assertions, read-only outside the write set, fixture confinement lie, vacuous NFC test, untested `isProtected`, missing
`@Builder`, spec wording, duplicate Traceability pair, surface deltas) all fixed in 927322b8; round 2 — N1..N7 fixed in
0ad63145; round 3 — M1 (unreadable-at-baseline silently never compared) fixed in 179f2f0d after the operator chose to
continue, m1/m2/n1 in the same commit; round 4 — M1a (a readable repair cleared a withheld detection) and n4a/n4b fixed
in 77aeaf72; the review then closed. Sonar round 1 (22 issues, gate red on reliability) fixed in 18d0454b, 0192e34c and
59fcd87f; PR review round 1 (13 findings) fixed in 71380dbc (one answered with a reason: spec-parity seam removed on
main), PR review round 2 (4 findings: traversal budget, continued lines, invalid path, write-set normalization) fixed in
45821532. Every PR comment was answered with its commit or a stated reason; 19/19 body findings handled, 0 unresolved
threads.

## 5. Reviewers

- `coderabbit` (required, quota-limited, `review_complete_after_handling`): reviewed `0192e34c` (13 findings) and
  `eeca77ac` (4 findings); all handled; one rate-limit notice was posted and recorded, no refusal that required the
  quota policy; the qualifying review on record is `eeca77ac` (`kind=reviewed`) and, with every finding handled, it is
  the review at any head of the PR. No re-trigger was therefore owed for later heads.
- `sourcery` (optional): refused — "your pull request is larger than the review limit of 150,000 diff characters". Not
  gating; recorded.

## 6. Operator questions

One question-tool call: at the self-review 3-round limit, with M1 open ("an unreadable metadata tree at the baseline is
never compared"), options: (1) apply M1 and continue, (2) proceed with the findings open, (3) stop before the PR. The
operator selected **"Apply M1 and continue (Recommended)"**. The run then closed M1 and continued.

## 7. Rule check

- Guard: `guard: none on this harness (unenforced)` — nothing was enforced; no guard-log line exists to read.
- Spec read: `spec_source=ledger_worktree`, no `spec_differ` marker. `executor-skew`: `match` (executor 0.1.2135 / lane
  0.1.2135).
- Shell-rule breaks by the run itself: one inline `python3 - <<EOF` edit of a test file (replacing a generated-value
  call), one `cat >> … <<'ADOC'` append to `core-store.adoc`, and one `find … | wc -l` (counting test sources). The R17
  links check is a direct `python3 …/trace.py` invocation, which R17 itself prescribes. All were file operations a file
  tool could have done; they are reported as the run's own breaks, not as guard refusals.
- `.plan/` writes: only `.plan/temp/…` (progress, PR body draft, links-check artifact), the `ci` body slots returned by
  `prepare-*`, and this one inbox message. The spec and `.plan/orchestrator/` were not edited (the merge of `origin/main`
  brought main's ledger changes into the merge commit; the lane made none).

## 8. Build server

- Setup preflight: `ready` (registered).
- Every routed build ran on the daemon (`mechanism=daemon_longpoll`, `requested=auto`, `resolved=routed`); no in-process
  fallback, no launch-fault retry. One supervisory bound expiry (`verify -Ppre-commit` at a 330 s bound, job
  `4cce9199`) was recorded and re-run with a raised bound (900 s), per the bound-expiry rule.
- `killed` jobs: none.
- Runtime stamp: harness `opencode`, model `deepseek-v4.1-flash/high`, effort `high`, plugin version `unknown`,
  executor 0.1.2136 (no switch during the run).

## 9. Residue

- The `.git/config` include scan is the subset of git's value grammar a path needs (comments outside quotes, `~`,
  backslash escapes and line continuations, a depth bound of 10 and an aggregate budget of 1024 files); it evaluates no
  `includeIf` condition (over-inclusive, harmless) and `info/attributes` is not digested (Q4's optional addition).
- `seccomp` stays out of the launch report and its schema (Q10), reported as owed.
- Submodule link files and worktrees created after the spawn (Q8) are covered only by the digest at the next comparison.
- The engine's hold, the operator's act and the native variant's call of `verify` are owed to `plan-marshall-mcp`
  (Q3/Q9); `ProtectedPaths` has no caller until `PLAN-V2-20`'s `FootprintBoundary` lands (the one-line hand-off).
- Surface delta: `JobRecordBuilderContractTest` and `LaunchReportRecordsTest` changed because the compile demanded the new
  component; `pm-workflow`'s `LandingVariantValidationTest` got one blank line from the pre-commit rewrite, committed
  alone (`978e083a`).
- The spec's Verify-first clause (list the open PRs naming the shared classes) was **not performed**; the known staged
  overlaps were read from the spec and the merge with `main` (`43c33c90`) settled the current conflicts.
- The merged `main` had removed the specification-reading test seam (`SpecificationDocument` and the
  `pm.specification.root` surefire property); a `ProtectedPaths`–specification parity test is therefore owed to the plan
  that restores such a seam (the reviewer's finding is answered with that reason).
- Main moved during the run; the base merge and the re-run gate are at `11852219`, merged as `a9754c56`.

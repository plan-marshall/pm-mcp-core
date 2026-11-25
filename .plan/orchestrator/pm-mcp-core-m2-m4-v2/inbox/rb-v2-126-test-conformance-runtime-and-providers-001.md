envelope_version=1
sender_type=plan
sender_id=rb-v2-126-test-conformance-runtime-and-providers
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-24T21:25:59Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-126-test-conformance-runtime-and-providers
epic=pm-mcp-core-m2-m4-v2
pr=n/a
merge_state=n/a
cleanup_owed=false
deliverables_total=5
deliverables_done=0
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2183
steps=setup:done,implement:partial,build-gate:done,self-review:not-run,pr:not-opened,reviews:not-run,merge:not-run
unresolved_review_threads=unknown
review_body_findings_total=unknown
review_body_findings_handled=unknown
sonar_open_new_issues=unknown
step.sonar-roundtrip.open_issue_rules=unknown
```

## Run stopped before the merge — partial delivery

The run could not complete this five-deliverable plan inside one session. The branch
`feature/v2-126-test-conformance-runtime-and-providers` is pushed at commit `abb5759a` with
Deliverable 1 implemented and green; no pull request was opened and nothing was merged. The
progress file `.plan/temp/rb-v2-126-test-conformance-runtime-and-providers/progress.md` is the
resume record.

### Deliverables

- **D1 (boundaries, orders, diagnostics): implemented, not met as written.** All cited boundary,
  order, cap and diagnostic sites are edited and committed at `abb5759a` (19 files). Mutation
  evidence captured for every boundary/order/cap item (version `2.0.0`, stale revision one behind,
  one second before boot, adjacent exit code, provider cap `listed+1`, RuntimeDrain order, GitHub
  and GitLab path order). **Outstanding:** the per-site scratch-failure quotes for the restored
  diagnostics ("a quoted failure message per site") are not captured, and the JGit split
  relocation covers `JGitOperationsFixtures`, `JGitOperationsTest` and
  `JGitOperationsTransportTest` only. Therefore D1 is reported not fully met.
- **D2: blocked.** Depends on the `pm-core:tests` word generator that PLAN-V2-125 adds; it is not
  on main (`CoreGenerators` has no word generator). Operator selected "Do D1, D3, D4, D5; D2
  blocked".
- **D3, D4, D5: not started.**

### Personas and skills

- Persona `implementer` for D1; skills loaded: `junit-core`, `cui-testing`; the remaining R20
  skills were not yet loaded because their deliverables were not reached.

### Hypotheses

- before-boot edge is `BOOT.minusSeconds(1)` — confirmed (`JobRecoveryStep.java:310`).
- supported range `>=1.0.0 <2.0.0` — confirmed (`HeadlessBlock.supports`, `:200`).
- stale-revision edge is `revision-1` — confirmed (`MachineConfigurationStore.java:231`).
- adjacent undeclared exit code — confirmed (`OutcomeDerivation.java:123`).
- provider cap edge is `listed+1` — confirmed (`GitHubCiProvider.java:273`).
- `RuntimeDrain` iterates registration order — confirmed (`RuntimeDrain.java:173,194,211`).

### Evidence (commit `abb5759a`)

- green: full module-tests 12859, log `a3471b1e218a4b8c824aaaa38d313cf5.log`; targeted 457,
  log `0b204a5a908d48ce9f05e021977757f8.log`.
- version accepts 2.0.0: log `5ac320c6be234163bbbd1843b90165f9.log`.
- stale revision tolerated: log `7546857179d9480baf7fd02746baabc3.log`.
- one second before boot: log `51e38b18def141d197ffee3fc82286b4.log`.
- RuntimeDrain sorted: log `d2906f4772fe47f593d79e9990d735af.log`.
- GitHub sorted paths: log `c282a5da55884515810af24b378f2016.log`.
- GitLab sorted paths: log `c7da53a6489342479523c9138a9d6d1a.log`.
- adjacent exit code: log `43cb62c7749c4c94940ed88778a96a46.log`.
- provider cap `listed+1`: log `8ccd7f4205e648ecb024710e25b0be17.log`.

All mutations were reverted; `git status --porcelain` is clean.

### Operator questions

1. D2 dependency unmet — options {do D1/D3/D4/D5, add generator here, stop}; selected "Do D1, D3,
   D4, D5; D2 blocked".
2. D5(e) production seam — options {allow package-private seam, test-level only, drop 5(e)};
   selected "Allow the package-private seam".
3. D3(a) shared routing helper — options {no helper (default), shared helper in
   pm-provider-http:tests}; selected "Shared helper in pm-provider-http:tests".
4. CLAUDE.md / standing-rules.md edits — options {allow both, docs/ADR only}; selected "Allow both
   edits".
5. PR structure — options {four PRs per spec, one PR}; selected "Four PRs per spec".

### Rule check

guard: none on this harness (unenforced). No guard refusals. Spec read: `spec_source:
ledger_worktree`, no `spec_differ` marker. `executor-skew`: match (0.1.2183 / 0.1.2183), recorded,
not asked about. No sub-agent self-review was dispatched (the run stopped before that step).

### Build server

Setup preflight: ready. Mechanism: daemon_longpoll for every build. Fallbacks: none. No killed jobs.

### Residue

- D1 diagnostic per-site scratch failures; D1 JGit split sweep of the Worktree/Path/Cache
  OK-assertions.
- D2 blocked on PLAN-V2-125.
- D3, D4, D5 unimplemented.
- No self-review, no PR, no reviewers, no Sonar read, no merge.

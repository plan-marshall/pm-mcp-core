# Landing Analysis: PLAN-V2-134 — Git metadata out of a worker's reach

epic: pm-mcp-core-m2-m4-v2
workstream: WS-06
pr: #469 (merge `a9754c56`, 2026-11-17)

> Landing record for one shipped plan, **delivered as specified, with one cross-repository break and four design defects in the guard**.
> Written by `analyze` (inbox scan) from the lane's landing message `rb-v2-134-git-metadata-out-of-workers-reach-001.md` (7 of 7
> deliverables claimed). The seven deliverables are in the diff and tested. The reading finds that the launcher the runtime now drives
> does not accept the new flag, that the baseline is lost at a restart and never advances after legitimate git work, and that a
> refused tree is not fail-closed at the confinement. A claim of the message is a lead; the launcher, the baseline storage, the
> composer's handling of a refusal and the guard's Javadoc were re-read.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 `GitMetadataPaths` and its cases | met | `GitMetadataPathsTest`, 9 tests, each refusal with its repair control |
| D2 `readOnlyPaths` and `--read-only`, worker only | met in this repository | rendered after `--read` and before `--deny-read` (`JobConfinement.launcherArguments`); the composer fills it for a worker only (`JobConfinementComposer.java:188`); **the launcher it drives rejects the flag (Layer 3)** |
| D3 job-record `gitMetadata`, honest `unenforced` | met | `JobRecord.requireKindRules`, codec, schema, parity; a record without the member is refused by design (R13, no migration; ADR `core-store/070` says so) |
| D4 launch report, optional member, schema | met, one statement missing | `LaunchReport.parse`, `launch-report.schema.json`, `job-runtime/01` include block; "a non-worker's member is not recorded, stated in the guide (Q1)" is in the code (`SubprocessJobRunner` lines 1105-1112) and **not in the guide or ADR 070**; the build-kind test case was replaced by a `git` job |
| D5 the guard | met | `start` (`SubprocessJobRunner.java:553-560`), the pre-check at `:1042-1044`, `verifyWorkerEnd`, `reportChange`; baseline set by `putIfAbsent`, moved by `rebaseline` alone |
| D6 `ProtectedPaths` | met, no caller | 10 tests; no caller until `PLAN-V2-20`'s `FootprintBoundary` (the lane says so) |
| D7 documents | met | guide, ADR `runtime-job/019`, `core-store/070`, `core-store/071`, Traceability; no "Implemented by" or "Remaining:" added; the lane's `links-check` is not re-run |

The Verify-first clause (list the open PRs naming the shared classes, fetch the sibling checkouts) was not performed (the lane says so);
Layer 3 L3-1 is the consequence.

## Three-Layer Analysis

One read-only leaf (`execution-context-level-3`, `orchestrator.analyze`) read the merged diff (`a9754c56^1..a9754c56`, 45 files,
+3390/-72) against the spec and its questions Q1 to Q10; the orchestrator re-read the launcher at `pm-mcp-clients` `origin/main` (fetched
2026-11-17), the baseline storage, the composer and the guard's Javadoc. By reading, no build, no test run, no write. Line numbers
are exact only for `SubprocessJobRunner`; the other files are cited by method.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity |
|---------|-------|---------|----------|
| L1-1 | the job-record schema "differs by property, `required` and `allOf` only" | false: `job-record.schema.json:6`, the `required` line went from a 2-space to a 4-space indent | the lane is wrong, minor |
| L1-2 | D4 "stated in the developer guide (Q1)" for a non-worker's member | not in the guide or ADR 070 | minor |
| L1-3 | Q4's optional extras (`info/attributes`, the `core.hooksPath` directory) | not digested, and neither is recorded as a decision in ADR 019 or the guide | the lane says only `info/attributes` |
| L1-4 | the spec's launch-report step lists `seccomp`; its included schema has `additionalProperties:false` and no `seccomp` | the spec contradicts its own schema (Q10, owed) | minor |
| L1-5 | files the diff touches outside the Expected Surface | `LaunchReportRecordsTest` and `JobRecordBuilderContractTest` (the compile demanded the new component) | the lane lists both |
| L1-6 | `pm-workflow`'s `LandingVariantValidationTest` blank line "in its own commit `978e083a`" | the file is not in the merged diff; `978e083a` is not an ancestor of the merge (squash); the blank line on `main` is #464's (PLAN-V2-114 item 38) | the message is stale |

### Layer 2 — Implemented

| Point | Reading | Verdict |
|-------|---------|---------|
| Test counts | 9, 17, 24, 8, 8, 10 are the method counts; `LaunchReportTest` is **7 methods** (27 invocations of its parameterised cases) | the lane's unit is wrong, minor |
| WARN records | 147 is asserted in `workerChangeRefusesTheNextGitJob`, 148 in `failingAuditIsLogged`; the sink-throws branch and the audit, WARN and sink of the `git_job` point are not asserted; both records are in `RuntimeLogMessages` and `doc/log-messages/pm-runtime.adoc` | confirmed, partial coverage |
| Include scan | depth 10 and 1024 files (`GitMetadataDigest.MAX_INCLUDE_DEPTH`, `MAX_INCLUDE_FILES`), both tested; `includeIf` conditions are never evaluated (over-inclusive); no test covers `includeIf` | confirmed |
| Guard Javadoc | "A project without git has no baseline and never compares" (`GitMetadataGuard.java:32`): `record` stores the empty digest as the baseline (`:70`, no skip for an empty tree), so a `.git` created later gives `Changed` | **false statement** (re-read) |
| Guard Javadoc, restart | "the recovery ends a running worker at restart, so the next worker's spawn records a fresh one" (`:28-30`): see L3-3 | the premise does not follow |
| `outsideWorktreeIsNotReadOnly` | the display name says "not read-only", the assertion expects it to be: `base/unused/..` normalises to `base`, which is in the write set; the real outside-the-write-set case is untested | minor defect in a test |
| Existing records | a record without the member is refused by the strict codec; `JobRecoveryStep` is unchanged and uses `toBuilder`, which keeps it | accepted, pre-1.0 |
| ADR 070 | "the compile finds the builder call sites": a Lombok builder that omits the member fails at run time (`IllegalArgumentException` for a worker), not at compile time | doc wrong, minor |

### Layer 3 — Correct

| Point | Reading | Verdict |
|-------|---------|---------|
| **L3-1 the launcher does not know `--read-only`** | `pm-mcp-clients` `origin/main` `pm-exec` `Command.java`: the option switch has `WRITE`, `READ` and `DENY_READ` and `default -> throw new UsageException("unknown option " + option)` (`:103-106`); the package Javadoc lists no `--read-only`. The runtime fills `readOnlyPaths` for every worker of a git project (`JobConfinementComposer.java:188`), so the launcher exits 64 and the worker fails at launch (64 is a launcher-failure exit, per the leaf). The leaf reports that Q1 required `pm-exec` to merge first; the lane's own H "the launcher change lands in `pm-mcp-clients` first" is marked not checkable. A search of the clients repository's pull requests by title and body finds none for `--read-only` (a narrow search) | **major, cross-repository, introduced by this PR**: real workers of a git project break when the assembly takes this `SNAPSHOT` |
| **L3-2 the baseline never advances** | the baseline is written by `record` (`putIfAbsent`) and `rebaseline` only (re-read, `GitMetadataGuard.java:54-72,100-104`). The digest covers the link files, `worktrees/*/{gitdir,commondir,config.worktree}` and the `.git/config` bytes (per the leaf); a server `git worktree add` or a `push -u` (which writes `branch.*` into the config) after the first spawn changes the digest, and the next git job is refused until the operator's `rebaseline`. ADR 019 and the guide reason about build jobs only; Q2's "move after a clean git job" was not built. By reading; no test exercises a real git job | **major (functional, fails closed)**; the effect rests on the server's own git jobs writing those files |
| **L3-3 a restart launders tampering** | `baselines` is an in-memory `ConcurrentHashMap` (`:38`); after a restart `verify` finds no baseline and answers `Unchanged` (`:86-88`), and the next spawn baselines the tampered tree. Ending the running worker at restart does not revert its writes, so ADR 019's "accepted because the recovery ends such a worker" does not follow | **major** (re-read) |
| L3-4 a refused `.git` is not fail-closed at the confinement | `gitMetadataInside` reads `GitMetadataPaths.of(root).paths()` only (`JobConfinementComposer.java:294-300`); `refused()` and `skipped()` are ignored there and used in the digest alone. A symlinked `.git` gives an empty read-only set with no log; the runtime never checks the set it sent against what the launcher reports as `enforced` | minor to major, defect (re-read) |
| L3-5 a garbage `.git` file at the baseline reads as "no git" | `GitMetadataDigest.of`: with no git directory and only a skipped entry, `failures` is empty and no `unreadable` part is added; the digest is `{}`. A later change is still a difference | minor, fail-open at the first spawn |
| L3-6 `core.hooksPath` target | the config bytes cover the setting, not the directory; ADR 019 defers a tracked directory such as `.husky/_` to `ProtectedPaths`, which has no caller; a target outside the repository is covered by nothing | minor to major, the rationale rests on code with no caller |
| L3-7 `.git/modules/*`, nested repositories, `info/attributes`, worktrees created after the spawn | not in the read-only set or the digest; `git status` in the superproject can run a submodule's `core.fsmonitor`; the guide names the submodule link files and later worktrees (Q8) only | minor, partly documented |
| L3-8 include-scan bypass | `include` and `includeif*` sections and a same-line key are scanned; a target created later is a new part; a symlinked target is followed; gaps: backslash escapes only inside quotes, no `includeIf` test | not bypassed |
| L3-9 TOCTOU and `.git` file redirection | the link file and `commondir` are digested; the window between `verify` and the spawn is documented | held |
| L3-10 case and NFC | `ProtectedPaths` lowercases and NFC-normalises; the digest uses the filesystem's semantics | ok |
| L3-11 `ProtectedPaths` scope | a bare `.husky` file or `./.husky/x` is not matched; `lefthook.yaml`, `.json`, `.toml` are not (the spec's list is exact) | minor, a spec gap |
| L3-12 `isGitTarget` | also matches `HostToolCall(GIT)` of any kind; the spec, guide and ADR say "git job" | doc gap |
| L3-13 generic catch | two new `catch (RuntimeException \| Error)` in `reportChange`, with a `cui-rewrite:disable` and `@SuppressWarnings("java:S1181")`; the repository forbids catching generic `RuntimeException` and PLAN-V2-94 removes such clauses | minor, rule |

## Review and Quality Numbers

| Number | Message | Re-read |
|--------|---------|---------|
| unresolved review threads | 0 | not re-queried here |
| review body findings total / handled | 19 / 19 | not re-verified (progress prose) |
| Sonar open new issues | 0 | not re-queried |

`landing-check`: `complete=false`, missing `total_tokens` and `plugin_version` only (register E27). Surface delta unmeasured. The lane named
three shell-rule breaks of its own (one `python3 -` edit, one `cat >>`, one `find | wc`) and an unenforced guard. One operator question
was asked at the self-review limit (apply M1 and continue; the operator chose to continue). The four rounds of self-review closed with
the findings above not among them: the reading's L3-2 to L3-4 are design questions the lane's own review did not name.

## Residue

1. **Open operator decision, cross-repository (L3-1): `pm-exec` must accept `--read-only` before the assembly takes this `SNAPSHOT`.**
   The change is a `pm-mcp-clients` pull request (the capability of the launcher: Landlock read-only paths); this repository cannot fix
   it. Recorded as an Open Defect in `epic.md`.
2. **Carried into PLAN-V2-114, item 41** (defects, with the decision named in the outline): L3-2 and L3-3 (persist the baseline per Q2-B
   or refuse git jobs after a restart until the first record; advance after a git job that was `Unchanged` at its pre-check), L3-4
   (act on `refused()` and `skipped()`; do not trust `enforced` when no path was sent), L3-5 (a failure when a link file is skipped and
   no git directory resolves), L3-13 (the two clauses, narrow or list under PLAN-V2-94), the `job-record.schema.json:6` indent; and the
   statements: the guard Javadoc, ADR 019 Consequences, the guide's non-worker rule and `isGitTarget`, ADR 070 "the compile finds",
   the spec's `seccomp` sentence, `outsideWorktreeIsNotReadOnly`, the missing sink-throws, `git_job` and `includeIf` tests, the
   `LaunchReportTest` unit.
3. **Watch in `epic.md`:** `ProtectedPaths` and the two decisions that rest on it (a tracked hooks directory, L3-6) have no caller until
   PLAN-V2-20's `FootprintBoundary`; the engine's hold, the operator's `rebaseline` act and the native variant's call of `verify` are
   owed to `plan-marshall-mcp`; a `ProtectedPaths`-specification parity test is owed to the plan that restores a specification-reading
   seam (PLAN-V2-122 retired it).
4. **Not routed:** the `seccomp` member (Q10, owed by the spec); Sourcery's refusal by diff size (optional reviewer).

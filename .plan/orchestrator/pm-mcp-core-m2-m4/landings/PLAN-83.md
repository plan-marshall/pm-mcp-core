# Landing Analysis: PLAN-83 — Restore the previous service unit when a new one does not load

epic: pm-mcp-core-m2-m4
workstream: WS-01
pr: #125 (https://github.com/plan-marshall/pm-mcp-core/pull/125), merged as squash `7d980c1` through the merge queue on 2026-10-21

> Landing record for one shipped plan. Lives at `landings/PLAN-83.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source of the landing: inbox message `rb-83-service-unit-restore-001.md` (`kind: landing`, sender `rb-83-service-unit-restore`, runbook lane on opencode, model `deepseek-v4.1-flash`, plan-reported). `inbox landing-check`: `complete: false`, `missing_keys: [total_tokens]`.

## Deliverable Fidelity vs Spec

The landing counts four deliverables (`deliverables_total=4`, `deliverables_done=4`) and six Done-when clauses.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 Keep the previous unit | shipped-as-specified | `ServiceUnitFile.keepForRestore()` copies the unit in force to `<unit>.previous` and writes the marker `<unit>.reinstall` before the new unit is written; with no unit installed it deletes any stale copy and writes the marker only; a pending pair is left untouched and the call answers `false`. `discardRestore()` deletes the marker before the copy. Read at `7d980c1` |
| D2 Restore on a failed load | shipped-as-specified | `ServiceUnitInstaller.install()`: keep, write, load; on a failed load `restoreKept()` runs and its failure is added suppressed to the load failure, which is rethrown. `restoreKept()` writes the kept bytes back (or removes the new file when none was kept), drops the pair, then loads. A unit that cannot be written drops only a pair this call kept. Read at `7d980c1` |
| D3 Order against self-termination | shipped-as-specified, wiring owed | every file operation precedes the command that can end the caller (keep before load, drop before the restore's load). `ServiceUnitInstaller.restore()` is the start's restore step: no marker issues no command and answers `false`. No production start calls it: `pm-mcp-server` is untouched, as the spec's Expected Surface declares. The squash's own Javadoc says "the assembly does not wire that step yet" (`ServiceUnitInstaller`, `package-info.java`) |
| D4 Plain install decided in an ADR | shipped-as-specified | `pm-runtime/doc/adr/runtime-start/014-previous-service-unit-kept-and-restored.adoc` has Status, Date, Context, Decision, Alternatives Considered, Consequences, Source; ADR 003's Status now points to it and its unit-file and service-manager split stays |

Done-when clauses, as the landing grades them: five met, one met only in the library. The clause "on macOS the start after an interrupted reinstall restores the kept unit" is met at the library level (`restore()` and its tests). The wiring into the start is not in this repository. The operator accepted that reading when asked ("Accept the library-level step", plan-reported in the landing message and its progress file). The clause is NOT met end to end, and the roadmap must not count the macOS interrupted-reinstall recovery as running until `pm-mcp-server` calls `restore()` first at start.

Hypotheses of the spec, as the landing settles them:
- "the Expected Surface is inferred from the `Where` line and ADR `runtime-start/003`" — confirmed: the squash changes exactly `ServiceUnitInstaller` and `ServiceUnitFile` as the production types of the unit, and no other file under `runtime/start` touches the unit apart from one Javadoc paragraph of `LaunchdServiceManager`.
- "the macOS reinstall ends the runtime inside the load" — confirmed by the class Javadoc of `LaunchdServiceManager` (the `bootout` ends the caller before the `bootstrap`); not exercised on macOS: the tests use a recording fake.

Surface delta (`inbox landing-check`): `unmeasured` (neither side supplied). Orchestrator count against the spec's `## Expected Surface` (`corpus declaration-currency`, footprint base `60b8e27`): 14 files merged, 8 declared and merged, 6 undeclared, 1 declared and untouched.
- Undeclared: `doc/LogMessages.adoc` (four new rows PM_RUNTIME-33/34/135/136), `doc/Traceability.adoc` (PM-ARCH-7 reworded, a new PM-DIST-1 entry), `RuntimeLogMessages.java` (four records), `RuntimeLogMessagesTest.java`, `LaunchdServiceManager.java` (Javadoc only, +4/-1), `package-info.java` of `runtime.start` (Javadoc only). The spec's Dependencies line already names Traceability and the log holder as overlaps "which is not a declared surface"; the other four follow from a new log record and two Javadoc paragraphs.
- Declared and untouched: `doc/specification/runtime-lifecycle/01-startup-recovery-drain.adoc`. The landing's last residue line gives the reason: only the service-unit part of the upgrade rollback is implemented, so the specification's Upgrade section is not deleted. Corroborated: the section at `7d980c1` still states "the previous service unit restored and reloaded" as a requirement of the upgrade.

New identifiers, checked against the holder: INFO 33 and 34 follow 32 (PLAN-82), WARN 135 and 136 follow 134, all with rows in `doc/LogMessages.adoc` whose text matches the record templates.

## Removed-Assertion Analysis

One test was replaced in `ServiceUnitInstallerTest` and nothing else was removed from the two test files (`git show 7d980c1` lines starting `-`): `shouldKeepUnitOnManagerFailure` became `shouldRemoveNewUnitOnManagerFailure`. Its assertion that the new unit stays written after a failed load (`assertEquals(golden(platform), Files.readString(unit.path()))`) became `assertFalse(unit.exists())`, and `assertNoLogMessagePresent(INFO, ServiceUnitInstaller.class)` became `assertLogMessagePresentContaining(INFO, SERVICE_UNIT_RESTORE_WITHOUT_PREVIOUS)`. Both inversions follow from the decision of ADR 014 (the failed-load rule of ADR 003 is superseded). The single-ERROR-log, thrown-failure and one-command assertions stay. No weakening.

## Metrics and Anomalies

- Tokens: `unknown` (the opencode harness measured none). The sixth runbook landing on opencode carrying it (after PLAN-78, 88, 81, 90, 82), and the one missing key of `landing-check`.
- Tests (plan-reported): 6843 tests in each of two whole-tree runs at `a086599` (`verify` and `verify -Pcoverage`); 4651 tests in the module-plus-upstream run after the `origin/main` merge (`0910ea4`). The last two figures were not re-run by the orchestrator; CI at the merged head is green (below). `ServiceUnitInstallerTest` carries 33 annotated test methods at `7d980c1`, most parameterized over both platforms.
- Local whole-tree `verify` red at `0910ea4`: `SubprocessJobRunnerTest.escalated`, "User limit of inotify watches reached", reproduced by the lane on the base branch in the same environment (plan-reported). CI on the merged head runs the same test green, so the failure is the operator's machine limit, not the tree.
- Deliverable order (plan-reported): test-first for D1 and D2 (failing run at `1ba292d`, `maven-2026-10-21-070915.log`), mutation-after for D3 (`restore()` neutered at `7af118b`, six assertion failures), none for D4. All named commits (`cc8dc10`, `7af118b`, `5792148`, `a086599`, `2ddd4da`, `1e32377`, `7db6e66`, `9da71a4`, `0910ea4`) exist in the object store.
- Progress file (`.plan/temp/rb-83-service-unit-restore/progress.md`, 27 lines) agrees with the landing on every sha, log name, round and operator answer. `.plan/temp/guard.log` does not exist, which is what the landing states: no guard on this harness.
- Rule breaks (plan-reported, not independently observable on an unguarded harness):
  - The resolved build names (`quality-gate`, `module-tests`, `test-compile`) reached Maven and failed ("Unknown lifecycle phase"). The lane then ran the canonical Maven commands through the same executor. This is the stale `runbook_lane` (pinned 0.1.1961) the epic already tracks: the same defect as in PLAN-90 and PLAN-82.
  - The lane read the installed skill documentation under `~/.config/opencode/skills/` (build-maven, workflow-integration-sonar), which the lane forbids reading. Read-only; the cause is the stale executor, which left the lane without the invocation surface.
  - The `origin/main` merge commit `0910ea4` carries no `Co-Authored-By` trailer. The squash on `main` carries `Co-authored-by: plan-marshall <noreply@cuioss.de>` (read in `git show 7d980c1`), so the project rule holds for what reached `main`.
  - `git reset HEAD -- <path>` once and `git commit --amend` once before the first push: neither is on the forbidden list.
- The lane asked the operator two questions (the wiring clause and a fourth review round); both answers are in the landing message. A question about the build-command naming error was not asked, as the lane's rule directs.

## Routing and Merge Behavior

- Review: `coderabbit` (required) reviewed `1e32377` with no actionable comment and one security-architecture note, `7db6e66` with two nitpicks (both fixed in `9da71a4`), and ended at `0910ea4` with "no unreviewed commit" (read through `ci pr comments`: fifteen comments, none unresolved as a thread; `reviewThreads` is empty). `sourcery` (optional) refused on its review budget twice, at 08:45 and 08:49 (2,500,000 diff characters per 7 days). Three self-review rounds, the operator waived a fourth.
- CI/merge (read from the PR through `ci`): `state: merged`, merge commit `7d980c1` on `origin/main`; `checks status`: `overall_status: success`, 30 checks, including `build / build (25)`, `build / build (26)`, `integration-tests`, `quarkus-alignment` and `SonarCloud Code Analysis` (pass).
- SonarCloud: quality gate passed, 0 new issues, 96.6% coverage on new code, 0.0% duplication (the bot's comment on the PR). The lane's two earlier code smells (`java:S1141`, `java:S1130`) were fixed in `7db6e66`, as the landing says. No untriaged Sonar issue on #125.
- Collision with the plans that ran beside it, from `corpus declaration-currency` (footprint base `60b8e27`):
  - PLAN-64 (running): 5 shared files — `doc/plans/README.adoc`, ADR 003, `runtime-start/014` (the number), `runtime-start.adoc`, `runtime/start/package-info.java`. The collision the gate predicted came in. Read-only look at PLAN-64's worktree: it merged `origin/main` at `da892e9` (the log contains `7d980c1`) and its ADR already sits at `015-stall-derived-on-read-one-record-per-episode.adoc`, so the renumbering its spec asks for was done. PLAN-64's worktree also carries an uncommitted edit of `ServiceUnitInstallerTest.java` (14 lines, `throws IOException` widened to `throws Exception`), a file its spec does not declare; the reason was not read (a watch).
  - PLAN-89 and PLAN-85 share `Traceability.adoc` and `README.adoc` with the footprint (2 files each); PLAN-84 shares `LogMessages.adoc`, `README.adoc` and, through its directory entry `runtime/start/`, `ServiceUnitInstallerTest.java`. No staged spec names `ServiceUnitInstaller`, `ServiceUnitFile`, or PM_RUNTIME-33/34/135/136, so no staged claim is contradicted by this landing.
  - PLAN-87 and PLAN-91: no shared file.
- Index: the squash removed the `service-unit-restore` row from `doc/plans/README.adoc` and deleted `doc/plans/service-unit-restore.adoc` — the standing constraint was exercised.
- Cleanup: `cleanup_owed=false`. The lane reports its worktree removed and the local and remote refs pruned (plan-reported); the remote branch `fix/83-service-unit-restore` is the head of a merged PR and no longer in `git branch -r` after the fetch.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-83 --status shipped`
- [x] row `pr` stamped — `#125`
- [x] row `landing` stamped — `landings/PLAN-83.md`
- [x] row `plan_marshall_plan_id` stamped — `rb-83-service-unit-restore` (the sender, per the runbook rule)
- [x] epic.md: the token defect and the opencode watch get their sixth landing; the executor watch recurs; the owed start wiring, the concurrency residue and the retained CodeRabbit concern are recorded as Watches; the PLAN-64 watch is updated
- [x] the landing carries no candidate lesson for this store
- [x] resume anchor updated; `queue-view.md` regenerated

## Follow-Ups

- **Start wiring owed** (accepted by the operator): `pm-mcp-server` in `plan-marshall-mcp` must call `ServiceUnitInstaller.restore()` first at start. The same repository owes `CredentialStoreChoice.record` (PLAN-82). A Watch.
- **Concurrency of install, restore and uninstall** over one kept pair has no cross-process owner. CodeRabbit raised it, the lane answered on the PR, and no production caller exists yet. It belongs to the change that wires the operator verbs `pm-operator runtime install|uninstall` and the start. A Watch.
- **Retained CodeRabbit concern, not named in the landing's Residue** (the bot's security-architecture note in its summary comment): a load that succeeds followed by a marker drop that fails still reports success, and the retained marker lets a later start restore the OLDER unit (WARN 136 documents it, so the design acknowledges it). The suggestion is to separate the install's commit state from the garbage collection of the kept pair. A bot claim: a lead, recorded as a Watch, not a defect; the behaviour itself is corroborated by the WARN 136 text and `dropAfterLoad`.
- **No retry after the drop**: if the restore's own load fails after the pair was dropped, no later start retries it (documented in `restoreKept`, PM_RUNTIME-135 and the developer guide); the next login loads the unit file. Recorded as part of the Watch above.
- **Owed to the milestone exit** (plan-reported and in the PR body): the concept and user documentation of the slice and the roadmap check-off in `plan-marshall-documentation`; the rest of the upgrade rollback (the staged installation directory and the switch) is not implemented.
- The landing's local-environment finding (inotify limit) is the operator's machine, not the repository. Not tracked.

# PLAN-V2-95: Keep the lock manager's OS lock when a JSONL append closes its channel

epic: pm-mcp-core-m2-m4-v2
workstream: WS-05
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-95-lock-manager-channel-close.md` and is queued as one row file, `queue/PLAN-V2-95.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.
>
> Origin: the skills conformance audit of 2026-10-30 (`skills-audit-2026-10-30.md` in the epic directory), taken at `origin/main` `6847c748`, and the operator's decisions of the same day (Decisions in `epic.md`). The plan family is PLAN-V2-83 to PLAN-V2-108 (workstream WS-11, with PLAN-V2-95 and PLAN-V2-96 in WS-05); it runs first, except PLAN-V2-96, which follows PLAN-V2-31.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r4`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R06, R08, R11, R13, R14, R17, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R05, R07, R09, R10, R12, R15, R16: no specification, message or format change.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-store.adoc`, `pm-runtime/doc/developer/runtime-lock.adoc` (where it exists); ADR directory: `pm-core/doc/adr/core-store/`; layout: workstream WS-05.
- R12 — none.

**Read the skills first.** Before the outline, read `SKILL.md` and the standards it points to of every skill R20 names that this plan uses: `plan-marshall:../marketplace/bundles/pm-dev-java-cui/skills/{cui-logging,cui-testing,cui-http-testing}` and `../marketplace/bundles/pm-dev-java/skills/{java-core,java-lombok,java-null-safety,javadoc,junit-core,junit-integration}` (the plan-marshall checkout beside this repository: `/home/oliver/git/plan-marshall/marketplace/bundles/`). Say in the first progress note which you read. Where two skills disagree, the ADR of PLAN-V2-83 decides; where it does not, stop and report through the inbox message; do not choose.

## Objective

On POSIX, closing any channel on a file releases every OS lock the process holds on it. `JsonlStoreFile` closes its channel after every append, so on a file-locked store the lock manager's OS lock is lost; ADR `pm-core/doc/adr/core-store/024` accepted that for the audit log alone and relied on the per-key in-process lock and `RuntimeLock`. Every other file-locked JSONL store is affected and no plan owned the fix. `PLAN-V2-07` adds two more JSONL stores (the epic inbox and the register) on the same discipline. The operator decided on 2026-10-30 (decision 3) to fix it in the lock manager before PLAN-V2-07, not to accept it a second time.

## Deliverables

1. **Decide and write the fix (ADR).** Read ADR `core-store/024`, `JsonlStoreFile` (`append`, `snapshot`, `repairTail`), the lock manager port `LockManager` / `LockTransaction` of `pm-core` `core.service` and `FileLockManager` of `pm-runtime`. Choose the mechanism and record it in an ADR that supersedes the 'lost OS lock is accepted' decision of ADR 024 in part: candidates are (a) the OS lock is taken on a lock file that no append channel ever opens (a sidecar `<key>.lock`), (b) the lock manager keeps one long-lived channel per held key and the store appends through it, (c) the append channel is not closed while the transaction holds the key. The ADR names the rejected alternatives and why.
2. **Implement it (test first).** Tests prove: (1) an append through `JsonlStoreFile` under a `LockTransaction` leaves the OS lock held until the transaction ends (a second `FileLockManager` instance on the same base, in a second thread with its own channel, cannot take the key meanwhile); (2) a torn-tail repair and the audit-log anchor calls still run in the one transaction; (3) releasing the transaction releases the lock exactly once. Change `JsonlStoreFile`, `LockManager`/`LockTransaction` and `FileLockManager` only as far as the chosen mechanism needs; the stored format does not change (R13).
3. **Apply it to the audit log and document it.** `AuditLog` appends under the OS lock now; ADR 024 gets its status line 'Superseded in part by ADR NNN' and a pointer; `core-store.adoc` and the `runtime` lock guide describe the rule in one paragraph. The Javadoc of the second stated hazard, 'one `FileLockManager` instance per runtime', stays or is removed according to the mechanism (say which).

**Done when:** the tests of Deliverable 2 pass and fail on the unchanged code (run once on a copy to show it); no test relies on a sleep; the ADR and the guides say the same as the code; `PLAN-V2-07` can list the dependency as met. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and, for `pm-core` and `pm-runtime`, `verify -Pintegration-tests -pl <module> -am` are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main`.

**Trace starting set:** ADR `pm-core/doc/adr/core-store/024`; Store Schemas § Audit Log and Job Runtime § Locks (read at the outline); the carried item (Wa26) in the owner register; `java-core` (concurrency standards); R14.

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **No new dependency and no change of a stored format** (R13).
- **The lock order is unchanged:** `LockOrderTest` and `LockKey` order rules stay green.
- **Operating-system behaviour is tested with `@EnabledOnOs`** where the behaviour is POSIX-only (CLAUDE.md); the Windows path is not in scope.

## Claim Labels

- OBSERVED: ADR `core-store/024` Context: 'JsonlStoreFile closes its channel after every append, and closing a channel releases every lock of the operating system the process holds on that file'; Decision: 'The lost OS lock is accepted ... Neither the lock manager nor JsonlStoreFile changes for the audit log'
- OBSERVED: `JsonlStoreFile.java` (`pm-core/.../core/store/`), `LockManager.java` (`pm-core/.../core/service/`), `FileLockManager.java` and `RuntimeLock.java` (`pm-runtime/.../runtime/lock/` and `.../start/`)
- OBSERVED: `PLAN-V2-07` builds `EpicInbox` and `EpicRegisterStore` on `JsonlStoreFile.append` under the epic lock (its Deliverables 2 and 4)
- HYPOTHESIS: the other file-locked JSONL stores (`WaiverLog`, `ChangeLedger`, `QuestionLog`) append through the same path — list them with `git grep -l 'JsonlStoreFile'` at the outline
- Verify-first clause: before editing, list the open pull requests for one that touches the packages of the Expected Surface and re-run the counting commands of the claims above at the head of `main` (a plan of the family may have landed since the audit); keep only what is still open; a refutation loops back to re-scope.

## Expected Surface

- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/JsonlStoreFile.java`
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/service/LockManager.java`
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/AuditLog.java`
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/lock/FileLockManager.java`
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/store/JsonlStoreFileTest.java`
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/store/AuditLogTest.java`
- HYPOTHESIS: `pm-runtime/src/test/java/de/planmarshall/runtime/lock/FileLockManagerTest.java` (extended)
- OBSERVED: `pm-core/doc/adr/core-store/024-the-audit-log-is-appended-through-the-jsonl-store-file-under-the-in-process-lock.adoc` (status line)
- HYPOTHESIS: `pm-core/doc/adr/core-store/NNN-*.adoc` (new, next free number, R11)

Not surface (R19): `doc/plans/README.adoc`, `doc/Traceability.adoc`, `doc/LogMessages.adoc`, the ADR directory and developer guide of an existing package as additive bookkeeping.

## Dependencies and Sequencing

- Depends on: PLAN-V2-83, PLAN-V2-84, PLAN-V2-85 and PLAN-V2-86 (it edits `JsonlStoreFile` and the store tests the migration plans rewrite) and PLAN-V2-90 (the lock tests).
- PLAN-V2-07 depends on this plan (the orchestrator adds the edge).
- Overlaps by class with PLAN-V2-07 only through that dependency.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-95-lock-manager-channel-close.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

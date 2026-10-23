# PLAN-87: Reset the transaction journal to format version 1

epic: pm-mcp-core-m2-m4
workstream: WS-02

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-87-journal-format-version-reset.md` and is queued as one row file, `queue/PLAN-87.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r1`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R15, R16, R17, R18.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: the developer file of each package it changes (`{module}/doc/developer/{package}.adoc`); ADR directory: `{module}/doc/adr/{package}/`; layout: workstream WS-10.
- R12 — log message holder: none expected: a constant, its tests and its documents; the refusal of an outdated journal already has its record.

## Objective

The operator decided on 2026-10-13 that every versioned artifact stays at version 1 while nothing is released, and recorded this as an Open Defect of the epic to be cleaned up at the end: `TransactionJournal.FORMAT_VERSION` is 3 (`pm-core/src/main/java/de/planmarshall/core/store/TransactionJournal.java`), raised 1 to 2 by PLAN-06 (#26, owed audit events) and 2 to 3 by PLAN-12 (#51, delete mode). Every other versioned artifact is at 1 (checked on 2026-10-13 at `bed24eb`). This plan brings the journal to 1 in code, tests, fixtures, guide, ADR and specification, with the behaviour of versions 2 and 3 as the shape of version 1.

## Deliverables

1. **The constant and the document.** `FORMAT_VERSION` becomes 1; the Javadoc of the class (the document shape `{"format_version": 1, ...}` and the sentence "Version 1 had no owed events and version 2 no delete target; both are refused as outdated") is rewritten to describe one version; the reader refuses a journal of any other version with the existing named reason.
2. **No reader of the old versions.** The journal tests that pin 2 or 3 change: `TransactionJournalTest` ("a journal of version 2, which had no delete target, is refused as outdated") becomes a test that a journal of another version is refused as outdated; every fixture, writer and assertion in `TransactionJournalTest`, `JournalRecoveryTest`, `ScopeTransactionTest`, `ScopeSessionLogTest`, `PendingEntryStoreTest`, `ScopeIntegrityTest` and `EnrolmentStoreTest` that carries a journal version is found with `git grep` and set to 1.
3. **The documents.** `pm-core/doc/developer/core-store.adoc` (the passages "The journal is written in `format_version` 3", the version-2 refusal row and the test line), ADR `pm-core/doc/adr/core-store/007-delete-mode-of-the-transaction-journal.adoc` (it records the bump: record the decision that the journal's shape is version 1 instead, in the form of the ADR template and without a bump; the number of the ADR does not change), and the Store Schemas specification part that shows `"format_version"` for the journal (`store-schemas/01-format-and-session-log.adoc` and what `git grep` finds).
4. **The same sweep for the others.** `git grep` the repository for `format_version` and `FORMAT_VERSION` with a value other than 1 in code, fixtures, schemas, specification examples and ADRs; report each in the pull request body, and fix those the operator's decision of 2026-10-13 covers.

5. **The open SonarCloud issues of the landed pull requests #120, #121 and #122 (added by the operator on 2026-10-21).** Fix the 29 issues the landings left untriaged, in the code, not with a suppression (a rename, a method reference, a constant, an unnamed pattern, a split lambda, a simpler expression, a removed parameter): #120 one (`java:S7467`, `PortedFixtures`); #121 ten (`S6213` x5 restricted identifiers, `S5961` too many assertions in one method, `S1130`, `S9358`, `S5778`, `S1172` unused parameter `from`); #122 eighteen (`S5778` x5, `S7467` x3, `S6213` x2, `S1612` x2, `S1192` x2 CRITICAL duplicate literals in `AuditEvent`, `S3400`, `S9358`, `S8786` a super-linear regex, `S1172` unused `projectId` in `WrapperVerification`). Re-list them first (the lines below are those of the pull-request heads; the files changed on `main` since), fix those still open on `main`, and report in the pull request body each issue as fixed, already gone, or dismissed with the reason — a dismissal (`wontfix`, `falsepositive`) needs a stated reason and is the exception. A behaviour change is out of scope: where a fix would alter what a test asserts or what a method returns, keep the behaviour and say so.

**Done when:** `TransactionJournal.FORMAT_VERSION` is 1; every issue of Deliverable 5 is fixed or reported as gone or dismissed with a reason, and the SonarCloud analysis of the pull request lists none of them as new; no file of the repository states a journal `format_version` of 2 or 3 except as the refused example of a test; the journal tests, the recovery tests and `verify`, `verify -Ppre-commit`, `verify -Pcoverage` and `verify -Pintegration-tests` are green; the pull request body lists the sweep of Deliverable 4; the links check (R17, absolute paths of the sibling checkout) reports `BROKEN LINKS 0` and the run is recorded in the pull request body. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready.

**Trace starting set:**

- Requirements: PM-IMPL-2 (as PLAN-06 and PLAN-12)
- Specification: Store Schemas § Format and Session Log
- Watch: Store Schemas

## Standing Constraints

None beyond the standing rules named in the First Instruction above.

## Claim Labels

- OBSERVED: `TransactionJournal.FORMAT_VERSION` is 3 and its Javadoc says version 1 had no owed events and version 2 no delete target — `pm-core/src/main/java/de/planmarshall/core/store/TransactionJournal.java` lines 48-54 and 77 at 7756dfd
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: 1 cited path(s) exist at 4617865 and are unchanged since 7756dfd (git diff --stat 7756dfd..4617865 empty): pm-core/src/main/java/de/planmarshall/core/store/TransactionJournal.java
- OBSERVED: every other `FORMAT_VERSION` constant of `pm-core` main code is 1 — grep `FORMAT_VERSION *= *[0-9]` over `pm-*/src/main` at 4617865 (every other constant, in `pm-core` and the other modules, is 1)
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: git grep of FORMAT_VERSION constants over pm-core, pm-runtime, pm-workflow and pm-providers src/main at 4617865: 28 constants, every one but TransactionJournal's is 1 (COMPILED_FORMAT_VERSION included)
- OBSERVED: the guide `core-store.adoc` line 215 says the journal is written in `format_version` 3, ADR `core-store/007` records the bump, and `TransactionJournalTest` line 367 pins the refusal of a version 2 journal — files at 4617865
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: core-store.adoc line 215 says the journal is written in format_version 3 (the guide grew in #129), ADR core-store/007 present (007-delete-mode-of-the-transaction-journal.adoc), TransactionJournalTest pins the refusal of version 2 (line 367) at 4617865
- HYPOTHESIS: the list of tests in Deliverable 2 is complete — confirm/refute with `git grep -n 'format_version\|FORMAT_VERSION' -- pm-core/src/test` (verify-at-outline)
  - verdict: unverifiable | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- HYPOTHESIS: `store-schemas/01-format-and-session-log.adoc` is the only specification part that shows the journal version — confirm/refute with `git grep -n 'format_version' -- doc/specification` (verify-at-outline)
  - verdict: unverifiable | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- OBSERVED: SonarCloud lists open issues on the pull requests #120 (1), #121 (10) and #122 (18), in 14 files that all exist on `main`, and all 29 are open on `main` too at the same file, rule and line (the `main` analysis lists 109 open issues in all; the other 80 belong to no plan) — `https://sonarcloud.io/api/issues/search?componentKeys=plan-marshall_pm-mcp-core&pullRequest={120,121,122}&resolved=false`, the same with `branch=main`, and `git cat-file -e origin/main:{file}` at 7756dfd
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: SonarCloud API issues/search branch=main resolved=false on 2026-10-22: 109 issues, the same set (component, rule, line) as on 2026-10-21; no main analysis has run on code since; 4617865 is docs-only
- HYPOTHESIS: the issues are still open against `main` after the merge of the pull requests — confirm/refute with the SonarCloud issue search on the `main` branch (verify-at-outline)
  - verdict: unverifiable | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- Verify-first clause: before editing, list the open pull requests for one that touches `TransactionJournal`, and re-run the two greps of the hypotheses at `main`; a refutation of any claim above loops back to re-scope.
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: no package file and no index row for this spec (a plan with no package file); the only open PR (ci pr list) is #71, a dependabot bump, which does not name it; the claims above were re-read individually

## Expected Surface

- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/TransactionJournal.java` — the constant and the Javadoc
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/TransactionJournalTest.java` — the version cases
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/JournalRecoveryTest.java` — journal fixtures
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/ScopeTransactionTest.java` — journal fixtures
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/PendingEntryStoreTest.java` — journal fixtures
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/ScopeIntegrityTest.java` — journal fixtures
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/EnrolmentStoreTest.java` — journal fixtures
- OBSERVED: `pm-core/doc/developer/core-store.adoc` — the journal passages
- OBSERVED: `pm-core/doc/adr/core-store/007-delete-mode-of-the-transaction-journal.adoc` — records the decision without a bump
- HYPOTHESIS: `doc/specification/store-schemas/01-format-and-session-log.adoc` — the journal version, if it shows one
- OBSERVED: `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/ported/PortedFixtures.java` — Deliverable 5 (#120)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/credentials/CredentialStoreChoice.java` — Deliverable 5 (#121)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/start/CredentialStoreMoveResume.java` — Deliverable 5 (#121)
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/credentials/CredentialStoreChoiceTest.java` — Deliverable 5 (#121)
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/credentials/CredentialStoreMoveRecordParityTest.java` — Deliverable 5 (#121)
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/credentials/CredentialStoreMoveRecordTest.java` — Deliverable 5 (#121)
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/credentials/CredentialStoreMoveTest.java` — Deliverable 5 (#121)
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/start/CredentialStoreMoveResumeTest.java` — Deliverable 5 (#121)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/service/AuditEvent.java` — Deliverable 5 (#122; shared hot file)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/store/AuditCodecTest.java` — Deliverable 5 (#122)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/job/WrapperVerification.java` — Deliverable 5 (#122; PLAN-84 may touch the job runner)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/start/StalledScopeDetector.java` — Deliverable 5 (#122)
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/job/JobRefusalTest.java` — Deliverable 5 (#122)
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/start/StalledScopeDetectorTest.java` — Deliverable 5 (#122)

## Dependencies and Sequencing

- Depends on: nothing
- Runs beside PLAN-94 (`sonar-main-backlog`, running), PLAN-25 and PLAN-26 (running): PLAN-94 owns the other 89 and the 176 SonarCloud issues, never the 29 of Deliverable 5; do not fix an issue that sits in PLAN-94's list, and merge the base branch (the lane's step 7) before the merge gate, because #144 (PLAN-95) changed `SubprocessJobRunner.java`, `SubprocessJobRunnerTest.java`, `ProbeJobTest.java`, `ProcessTreeTrackerTest.java` and `JobRecoveryStepTest.java` after the line numbers of Deliverable 5 were taken. `verify -Ppre-commit` should no longer rewrite those five files (PLAN-95 committed its rewrite of them); if it rewrites any other file outside this spec's surface, ask the operator before committing the rewrite.
- Overlaps with: computed by `corpus cross-check` at emit time; PLAN-85 `store-records-advisories` and PLAN-88 touch `core.store` and `core-store.adoc` through other passages; every plan through `doc/Traceability.adoc` only if it adds entries
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` and the client contract `pm-api` in `pm-mcp-clients`, which stay untouched

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-87-journal-format-version-reset.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

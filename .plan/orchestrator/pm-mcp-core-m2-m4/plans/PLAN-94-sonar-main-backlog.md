# PLAN-94: Clear the SonarCloud issues of `main` that no plan owns

epic: pm-mcp-core-m2-m4
workstream: WS-10

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-94-sonar-main-backlog.md` and is queued as one row file, `queue/PLAN-94.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r1`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R15, R18.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: the developer file of each package it changes (`{module}/doc/developer/{package}.adoc`); ADR directory: `{module}/doc/adr/{package}/`; layout: workstream WS-10.
- R12 — log message holder: none: documents only.

## Objective

SonarCloud lists 118 open issues on `main` (2026-10-22, after #135 and #129). PLAN-87 Deliverable 5 owns 29 of them (the pull requests #120, #121, #122); the other 89 belong to no plan (found by the cleanup of 2026-10-21, added by the operator, who asked for all of them to be handled at once): 22 `java:S4032` on packages that hold only a `package-info.java`, and 67 further code smells, all `CODE_SMELL`, in 36 files of `pm-core`, `pm-runtime`, `pm-workflow`, `pm-provider-git`, `pm-provider-ci`, `pm-provider-github` and `pm-provider-gitlab`. Fix the 67 in the code, not by suppression, with no change of behaviour, and settle the 22 as Deliverable 7 says: the packages stay, the rule is ignored for `package-info.java` in the analysis configuration.

**Added after #137 (PLAN-19, merged 2026-10-22 with a red SonarCloud gate, accepted by the operator):** `main` lists 293 open issues, 176 of them in the 15 files of the new package `core.profile` (Deliverable 9, disjoint from Deliverables 1 to 8 and the operator ruled on 2026-10-22 that up to 12 deliverables per plan are fine, so it stays one plan), and `S4032` is 21, not 22, because `core.profile` holds code now. The 89 above are unchanged.

## Deliverables

1. **Unnamed patterns** (`java:S7467` x15, `S7466` x1, `S7475` x1): `CorpusValidator.java` 574; `CiHttpClient.java` 149; `CiHttpClient.java` 221; `Json.java` 64; `JGitOperations.java` 113; `JGitOperations.java` 164; `JGitOperations.java` 268; `JGitOperations.java` 329; `JGitOperations.java` 331; `JGitOperations.java` 356; `JGitOperations.java` 383; `JGitOperations.java` 415; `JGitOperations.java` 433; `InstallationTokens.java` 155; `FileSecretStore.java` 245; `LspClient.java` 241; `QuestionLogRecordReaderReadTest.java` 153. Use the unnamed pattern or variable where the binding is unused; `S7475` removes the unused type of an unnamed pattern, `S7466` takes `var`.
2. **Lambdas that can throw twice** (`java:S5778` x13, all in tests): `ToonEncoderTest.java` 189; `ToonEncoderTest.java` 190; `ToonEncoderTest.java` 192; `FileSecretStoreTest.java` 225; `FileSecretStoreTest.java` 236; `FileSecretStoreTest.java` 238; `FileSecretStoreTest.java` 247; `FileSecretStoreTest.java` 266; `FileSecretStoreTest.java` 276; `JobRecoveryStepTest.java` 391 (new since #135); `WorkflowStatusCodecTest.java` 495; `WorkflowStatusCodecTest.java` 503; `WorkflowStatusCodecTest.java` 511 (new since #129). Split each lambda so that exactly one call can throw, the arrange step outside it.
3. **Loops and conditionals** (`java:S9391` x7, `S9358` x2, `S135` x1): `EnrolmentStore.java` 602; `ToonEncoder.java` 259; `JGitOperations.java` 376; `JGitOperations.java` 609; `GitHubClient.java` 215; `GitLabClient.java` 233; `DbusReader.java` 100; `FakeSecretService.java` 65; `UnitFrontmatterReaderTest.java` 669; `ProjectResolver.java` 149 (`S135`, too many `break`/`continue` in the loop; new since #129). A stream where the loop is a stream, the conditional moved inside the operation; if the loop reads clearer, say why and dismiss that one issue with the reason.
4. **Regex and record patterns** (`java:S6353` x4, `S6878` x4): `ToonEncoder.java` 73; `ToonEncoder.java` 73; `ToonEncoder.java` 73; `ToonEncoder.java` 317; `ToonEncoder.java` 318; `ToonEncoder.java` 319; `GitOperations.java` 296; `GlabCommands.java` 38. `\d` for `[0-9]`; the record pattern for the pattern-match variable.
5. **Names and test hygiene** (`java:S6213` x3, `S1130` x2, `S2925` x1, `S1481` x1, `S1854` x1): `JGitOperationsTest.java` 380; `QuestionLogRecordReader.java` 161; `SubprocessJobRunnerTest.java` 1632 (1309 before #135); `LspClientTest.java` 70; `QuestionFixtures.java` 141; `QuestionLogRecordReaderTest.java` 302; `QuestionLogMalformedTest.java` 104 (`S1481` and `S1854`, the same unused local `accepted`; new since #129). Drop the unused local. Rename the restricted identifiers, drop the thrown `Exception` that cannot be thrown, replace the `Thread.sleep` of the test by waiting for the condition it waits for.
6. **Design smells in main code** (`java:S127` x2, `S3776` x3, `S112` x1, `S2386` x2, `S1075` x2, `S8491` x1): `ToonEncoder.java` 406; `MacSecurity.java` 302; `SubprocessJobRunner.java` 1176 (new since #135); `ProjectResolver.java` 146 (new since #129); `SecretServiceStore.java` 40; `DbusConnection.java` 39; `DbusConnection.java` 132; `PosixModes.java` 28; `PosixModes.java` 31; `UnitFrontmatterReader.java` 188; `ProjectUnitLoader.java` 286. Fix each in the code (loop counter not assigned in the body, the method split below the allowed complexity, a specific exception, the member made `protected` or private, the URI taken from a parameter or a named constant, the dangling Javadoc merged); where a fix would change behaviour or a public type, report the issue as dismissed with the reason instead of fixing it.
7. **The 21 `java:S4032` on package-only packages (22 until #137; `core.profile` holds code now and is no longer reported): the packages stay, the rule is ignored for them (operator decision 2026-10-22).** The packages are `core.architecture`, `core.build`, `core.config`, `core.discovery`, `core.domain`, `core.id`, `core.recipe`, `runtime.launch`, `runtime.worker`, `workflow.consult`, `workflow.decision`, `workflow.envelope`, `workflow.epic`, `workflow.hypermedia`, `workflow.model`, `workflow.primitive`, `workflow.question`, `workflow.screening`, `workflow.setup`, `workflow.spend`, `workflow.task`. **Do not remove any, and do not annotate them.** The operator allowed the removal of a package that is empty and referenced nowhere; none of the 21 qualifies: the Module Structure Specification (`plan-marshall-documentation`, `doc/specification/module-structure.adoc` § Packages) lists every one of them and states that "every package exists in the code with a `package-info.java` … also where no type has arrived yet", `CLAUDE.md` requires the file, and each package is the home of a staged plan or of a specification that gets its work package (see the Open Defect of the epic). A `@SuppressWarnings("java:S4032")` settles nothing (verified 2026-10-22 in the SonarJava sources): the rule reports on the file, at the end of the analysis, with no line (`UselessPackageInfoCheck`, `addIssueOnFile`), and the filter that honours `@SuppressWarnings` visits classes, methods and variables only and matches a line, so neither the package declaration of a `package-info.java` nor a type (there is none in such a package) can carry it. `NOSONAR` fails the same way and the repository uses none. Settle the rule in the analysis configuration instead: add to the `<properties>` of the root `pom.xml` `sonar.issue.ignore.multicriteria` = `e1`, `sonar.issue.ignore.multicriteria.e1.ruleKey` = `java:S4032` and `sonar.issue.ignore.multicriteria.e1.resourceKey` = `**/package-info.java` (a property: no dependency and no plugin is added). If the re-list of Deliverable 8 after the first analysis of the pull request still shows any of the 21, do not iterate on the property: report it and dismiss the 22 in SonarCloud as `wontfix` with the reason above. Record the decision as an ADR in `doc/adr/` and name it in the pull request body.
8. **Re-list and report.** Re-list the open issues with `branch=main` before the first edit and after the last: report each of the 89 as fixed, gone, or dismissed with its reason, with the count before and after, and the issues that appeared since this spec was written as a separate list. No behaviour changes: every test that passed before passes after.

9. **The 176 issues of the new package `core.profile` (found by the analysis of PLAN-19, #137, 2026-10-22).** Re-list them first (`componentKeys=plan-marshall_pm-mcp-core&branch=main&resolved=false`, the files under `pm-core/src/{main,test}/java/de/planmarshall/core/profile/`); at the analysis they were: `java:S1192` x123 (132 issues sit in `ProfileValidator.java`, 113 of them duplicated string literals: the JSON member names and pointers of the closed schema), `S6353` x12, `S6213` x8, `S5778` x8 (tests), `S3776` x4 (`ProfileValidator.java` 356, 564, 724, 976), `S135` x3, `S3358` x3 (`SupportedVersions.java` 126, 137, 190), `S6218` x2, `S7467` x2, and `S1612`, `S1172`, `S2924`, `S1144`, `S1643`, `S9391`, `S1068`, `S5843`, `S3626`, `S1075`, `S127` once each. Fix them in the code, not by suppression, with no change of behaviour, in this order: (a) the two `java:S6218` issues, `LoadedProfile.java` 31 and `ProfileSets.java` 46, a record that holds an array: they are the two `BUG`-type issues that fail the reliability rating of the quality gate; hold a `List`, or override `equals`, `hashCode` and `toString`, whichever the types' callers allow; (b) `S1192`: name the repeated literals once, as constants of the package (decide where at the outline and record it as an ADR in `pm-core/doc/adr/core-profile/`, the next free number); (c) the other rules as the earlier deliverables treat the same rule; (d) the coverage of the new code: the gate of #137 read 74.9 % against 80 %; `LoadedProfile.java` (61.9 %) and `ShippedProfiles.java` (65.1 %) are the lowest files, `ProfileValidator.java` has 132 uncovered conditions: add tests for the branches that are not covered, none for a line only to move a figure. Report each of the 176 as fixed, gone or dismissed with its reason, with the count before and after.

**Done when:** the SonarCloud search `componentKeys=plan-marshall_pm-mcp-core&branch=main&resolved=false` lists no issue of the 58 other than one reported as dismissed with its reason, the 21 of Deliverable 7 are no longer listed (or are dismissed as `wontfix` with the reason), `verify -Ppre-commit` has been run with its rewrites reviewed and committed, and the full `verify` is green; and the search lists no issue in the files of the package `core.profile` other than one reported as dismissed with its reason, the two `java:S6218` among the fixed, and the SonarCloud search of the pull request itself (`pullRequest=<N>&resolved=false`) lists no open issue before the merge.

**Trace starting set:** no requirement of its own: a quality cleanup of code that other plans delivered.

## Standing Constraints

None beyond the standing rules named in the First Instruction above.

## Claim Labels

- OBSERVED: SonarCloud lists 118 open issues on `main` after #135 and #129; 29 sit at the same file, rule and line as the issues of the pull requests #120, #121 and #122 (PLAN-87), and the other 89 are 22 x `java:S4032` and 67 further `CODE_SMELL` issues in 36 files (rules `S7467` 15, `S5778` 13, `S9391` 7, `S6353` 4, `S6878` 4, `S6213` 3, `S3776` 3, `S9358` 2, `S1130` 2, `S127` 2, `S1075` 2, `S2386` 2, and one each of `S8491`, `S7475`, `S7466`, `S112`, `S2925`, `S135`, `S1481`, `S1854`) — `https://sonarcloud.io/api/issues/search?componentKeys=plan-marshall_pm-mcp-core&branch=main&resolved=false` on 2026-10-22
  - verdict: corroborated | checked_at: 9732401d6041fcaf45319877b7147debca5b950e | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: SonarCloud API issues/search branch=main resolved=false on 2026-10-22 after #135 and #129 (main at 9732401): 118 issues; against the 111 at 0bf64f7 the delta is seven issues of #129 (S3776 and S135 ProjectResolver.java 146/149, S1854 and S1481 QuestionLogMalformedTest.java 104, S5778 x3 WorkflowStatusCodecTest.java 495/503/511); none of those three files is named by PLAN-87, so its 29 are unchanged and the other 89 are 22 S4032 and 67 further smells in 36 files; rule counts re-counted from the API
- OBSERVED: the 22 `S4032` packages hold only a `package-info.java` — `ls` of each package directory at `0bf64f7`
  - verdict: corroborated | checked_at: 9732401d6041fcaf45319877b7147debca5b950e | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: ls of the 22 directories named by the java:S4032 issues at 9732401: each holds package-info.java alone
- OBSERVED: the 36 files are disjoint from the 14 files of PLAN-87's Deliverable 5, and no open pull request touches them — comparison of the file lists on 2026-10-22; `git diff --name-only origin/main...` of #137 (the one open pull request of the epic)
  - verdict: corroborated | checked_at: 9732401d6041fcaf45319877b7147debca5b950e | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: the five files new since #135 and #129 are not named in PLAN-87's spec (grep); the other 31 were compared on 2026-10-21 and PLAN-87 is unchanged; git diff --name-only origin/main...pull/137 (PLAN-19, the one open PR of the epic) shares no path with the 36 java files of this spec's surface
- HYPOTHESIS: the files and lines above are still the ones SonarCloud reports when this plan starts — confirm/refute with the search of Deliverable 8 (verify-at-outline)
  - verdict: unverifiable | checked_at: 9732401d6041fcaf45319877b7147debca5b950e | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- Verify-first clause: before editing, list the open pull requests for one that touches these files (PLAN-84 and PLAN-85 have landed as #135 and #129; #137 belongs to PLAN-19), and re-run the SonarCloud search; a refutation of any claim above loops back to re-scope.
  - verdict: corroborated | checked_at: 9732401d6041fcaf45319877b7147debca5b950e | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: open PRs (ci pr list) on 2026-10-22: #137 (PLAN-19) and #71 (dependabot); none names this plan and none touches its files; PLAN-84 and PLAN-85 landed as #135 and #129 (9732401)
- OBSERVED: `S4032` cannot be suppressed by annotation — `UselessPackageInfoCheck.endOfAnalysis` calls `addIssueOnFile(this, "Remove this package.")`, `SuppressWarningFilter` visits `ClassTree`, `MethodTree` and `VariableTree` and matches `issue.line()` (SonarJava `master`, 2026-10-22); the issue JSON of the API carries no `line` and no `textRange`
  - verdict: corroborated | checked_at: 9732401d6041fcaf45319877b7147debca5b950e | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: SonarJava master read on 2026-10-22: UselessPackageInfoCheck.endOfAnalysis -> addIssueOnFile(this, "Remove this package."); SuppressWarningFilter visits ClassTree, MethodTree and VariableTree and tests excludedRule.getValue().contains(issue.line()); the S4032 issue of the API has line None and no textRange; JDK 25 java.lang.SuppressWarnings has no @Target, so the annotation is legal on a package, but the filter never reads it
- OBSERVED: all 22 packages are listed in the Module Structure Specification, whose § Packages states that every package exists in the code with a `package-info.java` also where no type has arrived yet — `plan-marshall-documentation/doc/specification/module-structure.adoc` at `origin/main` `b8c61b3`
  - verdict: corroborated | checked_at: 9732401d6041fcaf45319877b7147debca5b950e | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: module-structure.adoc at documentation origin/main b8c61b3 (extracted with git archive): all 22 names de.planmarshall.<package> occur in § Packages, and the sentence 'Every package exists in the code with a package-info.java … also where no type has arrived yet' is there
- HYPOTHESIS: the Sonar analysis property `sonar.issue.ignore.multicriteria` set in the root `pom.xml` takes effect on SonarCloud for a file-level issue of the rule — confirm/refute with the search of Deliverable 8 after the first analysis of the pull request (verify-at-outline); the fallback is the dismissal named in Deliverable 7
  - verdict: unverifiable | checked_at: 9732401d6041fcaf45319877b7147debca5b950e | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan

- OBSERVED: after #137 `main` lists 293 open issues, 176 of them in the 15 files of `core.profile` (rules: S1192 x123, S6353 x12, S6213 x8, S5778 x8, S3776 x4, S135 x3, S3358 x3, S6218 x2, S7467 x2, eleven single rules), `S4032` lists 21 packages (not 22), and the quality gate of the pull request read ERROR (`new_reliability_rating` 3 against 1, `new_coverage` 74.9 against 80) — SonarCloud API `issues/search` with `branch=main` and with `pullRequest=137`, and `qualitygates/project_status?pullRequest=137`, on 2026-10-22 (main at 9c02edc)
  - verdict: corroborated | checked_at: 9c02edc253fbcffeb02e7cb1707274566e7004a0 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: SonarCloud API on 2026-10-22 (main at 9c02edc): issues/search branch=main resolved=false total 293, of which 176 in /core/profile/ files; pullRequest=137 total 176 (rules counted: S1192 123, S6353 12, S6213 8, S5778 8, S3776 4, S135 3, S3358 3, S6218 2, S7467 2); rules=java:S4032 on main total 21, none of core.profile; qualitygates/project_status pullRequest=137: ERROR, new_reliability_rating 3 (threshold 1), new_coverage 74.9 (threshold 80)

## Expected Surface

- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/corpus/CorpusValidator.java` — the issue lines of Deliverables 1 to 6
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/EnrolmentStore.java` — the issue lines of Deliverables 1 to 6
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/toon/ToonEncoder.java` — the issue lines of Deliverables 1 to 6
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/toon/ToonEncoderTest.java` — the issue lines of Deliverables 1 to 6
- OBSERVED: `pm-providers/pm-provider-ci/src/main/java/de/planmarshall/provider/ci/CiHttpClient.java` — the issue lines of Deliverables 1 to 6
- OBSERVED: `pm-providers/pm-provider-ci/src/main/java/de/planmarshall/provider/ci/Json.java` — the issue lines of Deliverables 1 to 6
- OBSERVED: `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/GitOperations.java` — the issue lines of Deliverables 1 to 6
- OBSERVED: `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/JGitOperations.java` — the issue lines of Deliverables 1 to 6
- OBSERVED: `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/JGitOperationsTest.java` — the issue lines of Deliverables 1 to 6
- OBSERVED: `pm-providers/pm-provider-github/src/main/java/de/planmarshall/provider/github/GitHubClient.java` — the issue lines of Deliverables 1 to 6
- OBSERVED: `pm-providers/pm-provider-github/src/main/java/de/planmarshall/provider/github/InstallationTokens.java` — the issue lines of Deliverables 1 to 6
- OBSERVED: `pm-providers/pm-provider-gitlab/src/main/java/de/planmarshall/provider/gitlab/GitLabClient.java` — the issue lines of Deliverables 1 to 6
- OBSERVED: `pm-providers/pm-provider-gitlab/src/main/java/de/planmarshall/provider/gitlab/GlabCommands.java` — the issue lines of Deliverables 1 to 6
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/credentials/FileSecretStore.java` — the issue lines of Deliverables 1 to 6
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/credentials/MacSecurity.java` — the issue lines of Deliverables 1 to 6
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/credentials/SecretServiceStore.java` — the issue lines of Deliverables 1 to 6
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/credentials/dbus/DbusConnection.java` — the issue lines of Deliverables 1 to 6
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/credentials/dbus/DbusReader.java` — the issue lines of Deliverables 1 to 6
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/lsp/LspClient.java` — the issue lines of Deliverables 1 to 6
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/question/QuestionLogRecordReader.java` — the issue lines of Deliverables 1 to 6
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/start/PosixModes.java` — the issue lines of Deliverables 1 to 6
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/credentials/FakeSecretService.java` — the issue lines of Deliverables 1 to 6
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/credentials/FileSecretStoreTest.java` — the issue lines of Deliverables 1 to 6
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/job/SubprocessJobRunnerTest.java` — the issue lines of Deliverables 1 to 6
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/lsp/LspClientTest.java` — the issue lines of Deliverables 1 to 6
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/question/QuestionFixtures.java` — the issue lines of Deliverables 1 to 6
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/question/QuestionLogRecordReaderReadTest.java` — the issue lines of Deliverables 1 to 6
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/question/QuestionLogRecordReaderTest.java` — the issue lines of Deliverables 1 to 6
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/job/SubprocessJobRunner.java` — the issue line of Deliverable 6 (new since #135)
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/start/JobRecoveryStepTest.java` — the issue line of Deliverable 2 (new since #135)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/ProjectResolver.java` — the issue lines of Deliverables 3 and 6 (new since #129)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/store/QuestionLogMalformedTest.java` — the issue line of Deliverable 5 (new since #129)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/store/WorkflowStatusCodecTest.java` — the issue lines of Deliverable 2 (new since #129)
- OBSERVED: `pom.xml` — the three `sonar.issue.ignore.multicriteria` properties of Deliverable 7
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/profile/` — the issue lines of Deliverable 9 (the package landed with #137)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/profile/` — the issue lines of Deliverable 9, and the tests it adds
- HYPOTHESIS: `pm-core/doc/adr/core-profile/` — the ADR for the named constants of Deliverable 9
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/dsl/UnitFrontmatterReader.java` — the issue lines of Deliverables 1 to 6
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/unit/ProjectUnitLoader.java` — the issue lines of Deliverables 1 to 6
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/dsl/UnitFrontmatterReaderTest.java` — the issue lines of Deliverables 1 to 6

## Dependencies and Sequencing

- Depends on: nothing
- Sequenced after: PLAN-84 and PLAN-85, landed as #135 and #129 on 2026-10-22 (the first edited `SubprocessJobRunnerTest` and left two new issues, the second left seven in the stores); PLAN-87 (its 14 files are disjoint, but both change `pm-runtime`/`pm-core` code the same week) has no ordering constraint
- Overlaps with: computed by `corpus cross-check` at emit time
- Adjacent to: the Sonar configuration of the project (`.github/project.yml` `sonar:`), which stays untouched: the ignore of Deliverable 7 is an analysis property in the root `pom.xml`

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-94-sonar-main-backlog.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

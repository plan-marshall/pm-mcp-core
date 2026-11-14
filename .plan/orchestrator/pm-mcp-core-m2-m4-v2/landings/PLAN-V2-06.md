# Landing Analysis: PLAN-V2-06 — one definition of the role-name grammar with a source-scan guard

epic: pm-mcp-core-m2-m4-v2
workstream: WS-05
pr: #410 (merge `32a711f9`), `merged` through the merge queue, no label

> Landing record for one shipped plan, written by the `analyze` inbox drain of 2026-11-14 from the message
> `rb-v2-06-role-name-single-definition-001.md` of the runbook lane (harness opencode, model
> deepseek-v4.1-flash/high, executor 0.1.2118, standing rules r7). The message is a lead. The three layers were read by
> one read-only leaf from git at `origin/main` `32a711f9` (no build, no test run); the orchestrator re-read the points
> marked "re-read"; the provider figures were re-read from the SonarCloud public API.

## Deliverable Fidelity vs Spec

The spec carried a "binding corrections" section written at the emit (24 literals in 22 classes, three `core.config` copies, no `CREDENTIAL_KEY` exemption, `HarnessId` not `ClientId`, schemas are files read through `SchemaResource`, no "Implemented by" pointer); the verdicts are against it.

| Deliverable | Verdict | Evidence |
|-------------|---------|----------|
| D1 types, tests, parity | met | `ShortName.TEXT` is written once (`core/id/ShortName.java:30`, package-private); `RoleName` and `HarnessId` are `@UtilityClass` types with `PATTERN` and `matches` (null raises NPE, no `Optional`), `package-info.java` `@NullMarked`; `RoleNameTest` and `HarnessIdTest` pin the grammar edges (`ab\n`, full-width forms, 32 and 33, null); `IdentifierSpecificationParityTest` reads two schema members through `SchemaResource` and opens no `doc/` path (the role-artifact `properties.name` at `role-artifact-v1.schema.json:10` and the metrics `$defs.harness` at `metrics.schema.json:38`). |
| D2 the pm-core copies | met | re-read: `git grep -nF '[a-z][a-z0-9-]{1,31}' -- '*/src/main/java/*'` returns only `ShortName.java:30`; the 22 classes, the three `core.config` ones and `HarnessCandidate` (the 23rd, added by #412) are replaced: 17 `pm-core`, 5 `pm-workflow`, 1 `pm-runtime` production files. |
| D3 workflow and runtime | met | `WaitRequest.java:49` uses `RoleName.matches`, `WorkflowValidator.java:899-900` keeps the reserved-keyword check, `HarnessEnrolment.java:57`. |
| D4 the guard | met, with gaps | `guard/RoleNameSingleDefinitionTest`: 15 tests, tokenises comments, character literals, string literals and text blocks, no exemption map, non-vacuity checks for the three roots and empty roots, the root found as `NullMarkedPackagesTest` does. The scratch-commit demonstration (`a50e59d9`) is recorded in the PR body only; no in-tree file-level planted-copy test. |
| D5 documentation | met | ADR `pm-core/doc/adr/core-id/001`, guide `core-id.adoc`, README lines, pointers in five guides; `identifiers.adoc` carries a plain link and no "Implemented by" (r7 R05). |
| D6 the sweep | met, incomplete | 14 rows, none a role or harness name; it omits `SourceManifest.NAME_PATTERN` (`{1,63}`), `PrimitiveRegistry.IDENTIFIER`, `WorkflowParser.SEGMENT` and `PARAM_NAME`, `ICON_ID`, `HeaderSchemaCheck.PHASE_6_STEP`; none is a role name. D6(b) (no hand-written `charAt` check) not checked by the leaf. |

Behaviour is unchanged: the leaf compared the replaced call sites with the old code (`WorkflowStatus.requireMatch`, still the only helper that prints the pattern, keeps its anchored message at line ~501; `WorkerState.requireName`, `WorkflowValidator.isValidRoleName`, `RoleArtifactValidator`, `ShippedRoles`, `QualificationRecordStore`, `QualificationSettings`, `ConsultationFact`, `ConsultationRequest`, `HarnessEnrolment`, `ModelCatalogueCodec`, `RoleAssignmentValue`, `RoleAssignmentsCodec`); every use is `matches()`, so a stored record that parsed before still parses. No existing test file is in the diff (four new files only), so "tests unchanged" holds.

## Three-Layer Analysis

### Layer 1 — Specified

The design is coherent: one text, two types (the two registry entries may diverge), a source-scan guard, the home `core.id` with no imports; the reworded cycle argument is accurate (five `core.store` classes import `core.role.corpus`, 13 `core.role` classes import `core.store` or `core.queue`). Gaps the lane had to decide:

- the spec recommended asserting "the two role-artifact patterns", but `role-artifact-v1.schema.json:18` is `{0,31}` (1 to 32), so the lane asserted the `HarnessId` against the metrics schema instead; the deviation is in the PR body and landing section 9, not in the landing facts and not in the ADR;
- the registry-cell parity of the old D1 is gone as the binding corrections allow: nothing now ties `id-role-name` and `id-harness-id` in the specification to the code;
- whether to align the 1-to-32 and 2-to-32 constants was left open and the lane kept each where its schema states it (a lead for the documentation repository);
- the spec's point 8 ("`EnvelopePacker` may use `RoleName.matches`, the outline decides") was decided nowhere.

### Layer 2 — Implemented

As the table. The guard cannot see: a literal concatenated across two strings, a reordered class such as `[0-9a-z-]`, `\\d` and `\\w` forms, `\u` escapes, an escaped `\"""` in a text block (documented), and the JSON resources (a grep finds 36 lines in 20 schema files, the spec said 35). The limits are in the Javadoc, the guide and the ADR, but the Javadoc and the ADR do not name JSON as out of scope (the guide says "production source only").

### Layer 3 — Correct

No behavioural bug found. Conformance deviations, all minor:

- **Assertion messages:** 27 literals measured by the leaf, all 20 to 60 characters but one of 62 (re-read, counted: 62): `RoleNameSingleDefinitionTest.java:152` "A copy after an escaped character literal should be an offence"; two dynamic messages in the guard (lines 84-88) exceed 60 as well.
- `@EnableGeneratorController` is declared and no generator is used (the literal justification is the class Javadoc: "boundary of the contract"); the `RoleNameTest` and `HarnessIdTest` bodies are near-duplicates.
- `IdentifierSpecificationParityTest` Javadoc (re-read, line 24) says the `harness` pattern comes from "the qualification record schema"; the code reads the metrics schema.
- The ADR, the guide and the PR body still say "22 classes / 24 literals"; the tree has 23 and 25 (`HarnessCandidate` came with #412).
- `{0,31}` constants stay (re-read): `ProfileValidator.java:234` (`CLIENT_ID`) and `:242` (`BUNDLED_ICON`), `ShippedProfiles.java:41`, `RoleArtifactValidator.java:59`, `GradedConfiguration.java:46`; recorded in the guide and the PR body, not in the ADR.
- The reserved-keyword refusal exists only in `WorkflowValidator`; `RoleArtifactValidator` and `ShippedRoles` accept a reserved keyword (reported by the lane, no owner).

## Conformance to the Project's Own Rules

Neighbours: PLAN-V2-39 (#412, merged just before) added `HarnessCandidate.HARNESS_ID` (`^...{1,31}$`) and edited `HarnessEnrolment`; the guard failed CI on it and the lane merged `origin/main` and replaced it (`b08c3b7b`): the guard's first run caught an unrelated plan's new copy. #411 (136) and #407 (138): no overlap found by the lane (the leaf did not read their diffs). The lane did not load the Java skills; the deviations above (a 62-character message, an unused generator controller) are the kind they would catch. Sonar `pullRequest=410`: 0 open, gate `OK` (re-read); `main` open 83. Reviews: two review-body findings, both handled; Sourcery refused on quota.

## Unverified leads

- Test counts, CI runs; the pointer lines in `core-queue`, `core-role` and `workflow-consult` (stat only); D6(b).
- Whether `doc/plans/harness-renames.adoc` names `HarnessId` (a grep for `RoleName`, `HarnessId`, `core.id` returned nothing).
- The guard was not run by the leaf; by grep only `ShortName.java:30` matches the three spellings.

## Metrics and Anomalies

`total_tokens` unknown (opencode lane), harness opencode, model deepseek-v4.1-flash/high, executor 0.1.2118. `.plan/marshal.json` has no `sonar` block, so `resolve` answers `not_configured` while CI analyses SonarCloud on every PR; the lane read the public API instead (0 open, gate OK, new coverage 81.9 %, 0 hotspots). The 14 `lessons-routing`-bound messages and the five of PLAN-V2-136 are separate; this lane filed none.

## Routing and Merge Behavior

One pull request through the merge queue under the `CLAUDE.md` "Who merges" rule. No `candidate-lesson` message from the runbook lane. The lane's note on the missing `sonar` block of `.plan/marshal.json` is a project-configuration item for the operator, not a lesson.

## Reconciliation Actions

Row PLAN-V2-06 `running` -> `shipped`, `pr` `#410`, `landing` `landings/PLAN-V2-06.md`, `plan_marshall_plan_id` `rb-v2-06-role-name-single-definition`; message archived; Watch entry in `epic.md`; decision log; resume anchor.

## Follow-Ups

Placed 2026-11-14 under the rule "every leftover goes into a staged plan":

1. **PLAN-V2-123** (schema parity): the 36 JSON-schema lines are unguarded; add a resource-scan or parity assertion for at least the role-artifact `name` and the store `role` and `harness` members (ask-at-refine: all 20 files or the two role-artifact patterns, recommendation the members that name a role or harness, about 60%); and the Javadoc correction of `IdentifierSpecificationParityTest` (metrics, not the qualification record schema).
2. **PLAN-V2-125** (test conformance, `pm-core` and `pm-workflow`): the 62-character message at `RoleNameSingleDefinitionTest.java:152`, the dynamic messages at `:84-88`, the unused `@EnableGeneratorController`, the duplicated `RoleNameTest` and `HarnessIdTest` bodies; the in-tree planted-copy test (D4) that today lives only in the PR body.
3. **PLAN-V2-114** (analysis findings): the "22 classes / 24 literals" counts of the ADR and guide (23 and 25), the D6 list of local grammars the sweep omitted, a sentence in the ADR for the `{0,31}` decision, the reserved-keyword refusal that only `WorkflowValidator` applies (ask-at-refine: refuse in `RoleArtifactValidator` and `ShippedRoles` too or document the asymmetry, recommendation refuse, about 65%), and whether `EnvelopePacker.PlannedTaskSummary` and `PackedEnvelope` (`EnvelopePacker.java:73,107`) use `RoleName.matches` (recommendation yes, about 70%; no copy exists, so no breach).
4. **Package `harness-renames`** (no staged plan): the 212 `clientId` places and the "Not a client id of ..." messages; the 1-to-32 against 2-to-32 grammar of `harness_id` is a lead for the documentation repository.
5. **Operator, project configuration:** `.plan/marshal.json` has no `sonar` block (the lane's `resolve` answers `not_configured`; three plans ran their Sonar read by plain HTTP for this reason).

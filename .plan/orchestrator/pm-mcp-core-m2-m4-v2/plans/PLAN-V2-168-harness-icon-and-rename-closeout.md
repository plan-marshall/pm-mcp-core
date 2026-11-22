# PLAN-V2-168: The closeout of the harness icon store and the harness rename: the PNG and SVG sanitiser corners, the store's recovery, the rename's stale wording and fixtures, and the identifier grammar copies

epic: pm-mcp-core-m2-m4-v2
workstream: WS-04
origin: the three-layer analysis of the landing of PLAN-V2-142 (`landings/PLAN-V2-142.md`, 2026-11-22, #552 `c910c59b` and #548 `fee03237`) and the operator's rule that leftovers go into a staged plan (no staged plan owns the icon store or the rename's remainder)
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-168-harness-icon-and-rename-closeout.md` and is queued as one row file, `queue/PLAN-V2-168.json`,
> in the epic ledger (lane `runbook`: the operator runs it with the runbook lane). The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r6`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.


Rules that apply to this plan: R02, R05, R06, R07, R08, R10, R11, R13, R17, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R09: Deliverable 1 touches the confinement of the icon files; R12: no message is added; R10 because the profile and role schemas are read by parity tests: their schema blocks stay byte-stable unless Deliverable 4's operator decision changes the grammar; R11 because the ADRs `runtime-launch/012` and `013` are corrected; R13: no stored format version changes.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-runtime/doc/developer/runtime-launch.adoc`, `pm-core/doc/developer/core-config.adoc`, `core-id.adoc` and `core-profile.adoc` (exist; the corrected statements); ADR directory: `pm-runtime/doc/adr/runtime-launch/` (renumbered against `origin/main`, R11; the two ADRs 012 and 013 are corrected, not added); layout: workstream WS-04.
- R12 — none (no message).

**Read the skills first (R20).** `java-core`, `java-null-safety`, `javadoc`, `junit-core` and `cui-testing` of `/home/oliver/git/plan-marshall/marketplace/bundles/{pm-dev-java,pm-dev-java-cui}/skills/`; say in the first progress note which you read. R20 applies to every test line this plan touches.

## Objective

PLAN-V2-142 shipped the harness icon store with its sanitising accept step, the corrections of PLAN-V2-39 and the rename of `client_id` to `harness_id`. The analysis found no blocker and no bypass of the SVG allow-list that reading shows. What stays: a PNG whose header claims enormous dimensions is accepted and served; two registered PNG chunks that can carry text are copied; the byte-level DOCTYPE scan and the XML sniffer ignore encodings and signed bytes; one index-slicing bug in the URL check; a store that wedges on an unreadable index; and a rename that left stale wording, fixtures and tests on the old name, a misleading missing-id pointer, and four copies of a grammar the registry narrowed. None changes a stored datum except where Deliverable 4 says the operator decides.

## Deliverables

All file and line references come from the landing analysis and are re-read at the head of `main` before editing; drop what a merge since then settled and name the commit.

1. **The sanitiser corners (carried item 86 a to f).** `PngSanitiser.java:162`: bound the pixel dimensions or inflate-check the IDAT (a 256 KiB PNG whose header claims about 2^31 by 2^31 with a zlib stream of zeros is accepted and served today); decide `iCCP` and `sPLT` (`:42`, `STRIPPED` lists only `tEXt`, `zTXt`, `iTXt` and `eXIf`; ADR `runtime-launch/012` states the bound for private chunks only); `SvgSanitiser.java:149-152` the DOCTYPE byte scan decodes ISO-8859-1 so a UTF-16 `<!DOCTYPE` does not match (measure what the StAX DTD event does under `SUPPORT_DTD=false` for UTF-16 and add the negative controls); `:119` the sniffer compares signed bytes (`candidate[position] <= ' '` skips every byte at or above 0x80; only the refusal reason differs); `:283-297` `requireLocalUrls` finds `url(` in the lower-cased text and slices the original (a U+0130 before `url(` shifts the slice: `fill="İ url(x#a)"`; rewrite without index sharing); ADR 012 `:40` names only `url(`, `@import` and the backslash while the code also refuses `image-set(` and `image(`; consider refusing `src(` and `position:fixed`. The `style` deny-list stays a reviewed deny-list (the operator's accepted decision).

2. **The store (carried item 86 g to k).** `IconStore.java:196-200`: an unreadable index (or a key that fails `HarnessId.matches`) makes `index()` throw inside `accept`, so every later accept refuses and no verb repairs it: add a recovery or remove path and a sweep of orphan files and `.staged` leftovers; the sequence icon write, other-media delete, index write leaves the old entry unserved on a failure in between (safe, but the icon is lost: say so or order it); `:223-232` `isHttpsUrl` accepts userinfo and writes it into the index (reject it); `IconRefusal` at `:176` echoes the caller's harness id; `find` returns a `Path` after verifying it and the caller reads later (the NOFOLLOW check at `:134` and the follow-links open at `:147` are separate): document the same-user race in the Javadoc. Tests (`IconStoreTest`): the index file's mode and the directory's mode, that the lock is taken and that two accepts are excluded, `deleteOtherMedia` on a non-empty directory, a UTF-16 and a non-UTF-8 document, the index of an unreadable store.

3. **The rename's remainder (carried item 86 l to q).** Stale wording in main: `ProfileLoader.java:36` and the refusal text at `:163` ("the record names the client"), `ProfileSet.java:40,46,58` and `HarnessEnrolmentStore.java:114,125` ("client-id order"), `core-config.adoc:35,115`, `core-id.adoc:115-116` (names `RoleArtifactValidator.CLIENT_ID` and `ProfileValidator.CLIENT_ID`, now `HARNESS_ID`), `core-profile.adoc:182` (`enrolledByClientId`). `VerificationRecord.java:222-232`: a canonical record with neither member reports the pointer `/client_id`; report `/harness_id` and a message that names both spellings. Fixtures and tests still on the old name (`LaunchFixtures.java:67,80`, `SupervisorTestFixture.java:496`, `ProfileWorkerEligibilityTest.java:405,516`, `HarnessProfileTest.java:40,87`, `LongContextThresholdsTest.java:40,78,81,105`, `HeadlessBlockTest.java:117`, `pm-workflow` `EnvelopeBudgetProfileThresholdTest.java:56`, `worker/verification-record-*.json`, and the test names and display names that say "client id": `ProfileLoaderTest.java:100,258,291`, `ShippedProfilesTest.java:102`, `ModelCatalogueStoreTest.java:152`): make `harness_id` the primary fixture form and keep one old-name case per carrier. Add the missing test `ProfileValidatorTest.harnessIdIsRead` (the landing message names it; only `VerificationRecordTest.harnessIdIsRead` exists), the both-refused test of the endpoint profile (`EndpointProfileSchemaParityTest.java:162-181` has old-name and new-name cases only), and an audit-key test for `harnessUpdated` and `harnessWeightsUpdated` (`AuditEvent.java:154`, `DETAIL_HARNESS_ID`; no test asserts the new key). `HarnessListings.java:~63-68`: the scan row of an enrolled harness whose executable is gone is removed and appended at the end, which breaks "keeps the scan's order" of `HarnessList`: keep the order. The gate rewrites that #548 and #552 put in their diffs without naming them (FQN-to-import and import-order rewrites in `QuestionRecordReader`, `PendingEntryCreation`, `PendingEntryRecord`, `SourceManifest`, `SourceManifestCodec` and four test files; `ReservedKeywordsSpecificationParityTest.java` `throws Exception` and a double blank line) stay as merged; do not revert them, record them.

4. **The identifier grammar (carried item 86 r, ask-at-refine, R07).** The harness-id pattern has four Java copies and two schema copies where the registry's `HarnessId.PATTERN` is `^[a-z][a-z0-9-]{1,31}$`: `ProfileValidator.java:275`, `ShippedProfiles.java:43`, `RoleArtifactValidator.java:63` (all `{0,31}`), `GradedConfiguration.java:46` (`HARNESS_NAME`, unanchored), `harness-profile-v1.schema.json:43` and `role-artifact-v1.schema.json:18`; the landing message named three. They accept a one-character id the registry refuses. Aligning them changes what the validators accept (a behaviour change, so it was outside the rename): put it to the operator with the recommendation to align all six in one step against the registry through one definition (read `RoleNameSingleDefinitionTest` of `pm-core`, which guards the same shape for the role name), with a test per copy that fails on `{0,31}`.

**Done when:** Deliverables 1 to 4; every carried item below has its recorded outcome; no behaviour change beyond the corrections the items state. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of the changed modules (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module runs); the links check (R17) reports `BROKEN LINKS 0` and `INLINE SCHEMA BLOCKS 0` run from a checkout whose documentation sibling is fresh (the lane reported 3247 and called them pre-existing; the documentation checkout lags origin).

## Standing Constraints

- **Read every cited line at the head first.** The lines and counts of a carried item are those of the landing analysis it names, read on an older head; re-read each at `origin/main` before editing and drop what a merge since then settled (name the commit).
- **Work to the Done-when.** Do not stop to ask the operator which part to continue with; ask only a decision the spec marks ask-at-refine (R07) or one a carried item says needs the operator, and put it in the outline's question, not in a stop.
- **Every carried item gets an outcome** in the landing message and the pull request: adopted into a deliverable, split off to a NAMED existing plan, or dropped, each with its reason; "not in this run's surface" is no reason.
- **No behaviour change beyond the corrections the items state.** A change a carried item does not ask for is ask-at-refine (R07).
- **Where the project analyses its pull requests with Sonar** (the plan lifecycle's Sonar step reports it configured; where it cannot scan, read the pull request over plain HTTP: `pullRequest=N`, never `branch=<feature>`): an open Sonar issue is fixed in a pushed commit, or dismissed with a stated reason; the quality gate is green before the pull request is reported ready.
- **No dependency or plugin is added.**
- **No more than 100 files per pull request** (the watch of the epic ledger; CodeRabbit refuses a larger one): cut the plan into pull requests by deliverable or by module before the first one is opened.
- **Tests that can fail.** A new test fails on the code it guards (a failing run or a mutation, reported by log id); a test of a refusal asserts the reason, not only the type (CLAUDE.md, R20).

## Claim Labels

- OBSERVED: the absence of `ProfileValidatorTest.harnessIdIsRead`, the neither-member pointer of `VerificationRecord.java:222-232`, the PNG header check at `PngSanitiser.java:158-166` and `STRIPPED` at `:42`, and the four Java copies of the grammar were re-read by the orchestrator at `origin/main`; every other point was read by the analysis leaf at `fee03237`.
- LEAD (unverified): the StAX behaviour for a UTF-16 DOCTYPE, the DOM clobbering by the allow-listed `id` and `class` if a client inlines an icon, whether the lock blocks across processes (`FileLockManager` was not read), and whether `pm-mcp-clients` or the assembly read the audit detail `client_id` or the removed `HarnessUsageCoverage.of(UsageResult)`.

## Expected Surface

Source, tests, specification and documentation (specific files, R19; the outline adds the files the re-read at the head names):

- OBSERVED (`pm-runtime`): `pm-runtime/src/main/java/de/planmarshall/runtime/icon/PngSanitiser.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/icon/SvgSanitiser.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/icon/IconStore.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/icon/IconRefusal.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/launch/HarnessListings.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/launch/HarnessEnrolmentStore.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/icon/IconStoreTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/icon/SvgSanitiserAllowListParityTest.java`, `pm-runtime/doc/adr/runtime-launch/012-the-icon-sanitiser-is-an-allow-list.adoc`, `pm-runtime/doc/adr/runtime-launch/013-the-icon-store-serves-only-a-file-that-matches-its-entry.adoc`, `pm-runtime/doc/developer/runtime-launch.adoc`.
- OBSERVED (`pm-core`): `pm-core/src/main/java/de/planmarshall/core/profile/ProfileLoader.java`, `pm-core/src/main/java/de/planmarshall/core/profile/ProfileSet.java`, `pm-core/src/main/java/de/planmarshall/core/profile/VerificationRecord.java`, `pm-core/src/main/java/de/planmarshall/core/profile/ProfileValidator.java`, `pm-core/src/main/java/de/planmarshall/core/profile/ShippedProfiles.java`, `pm-core/src/main/java/de/planmarshall/core/role/RoleArtifactValidator.java`, `pm-core/src/main/java/de/planmarshall/core/role/qualification/GradedConfiguration.java`, `pm-core/src/main/java/de/planmarshall/core/id/HarnessId.java`, `pm-core/src/main/java/de/planmarshall/core/service/AuditEvent.java`, `pm-core/src/main/resources/de/planmarshall/core/profile/harness-profile-v1.schema.json`, `pm-core/src/main/resources/de/planmarshall/core/role/role-artifact-v1.schema.json`, `pm-core/src/test/java/de/planmarshall/core/profile/ProfileValidatorTest.java`, `pm-core/src/test/java/de/planmarshall/core/profile/VerificationRecordTest.java`, `pm-core/src/test/java/de/planmarshall/core/profile/EndpointProfileSchemaParityTest.java`, `pm-core/src/test/java/de/planmarshall/core/service/AuditEventTest.java`, `pm-core/doc/developer/core-config.adoc`, `pm-core/doc/developer/core-id.adoc`, `pm-core/doc/developer/core-profile.adoc`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-142 (shipped, `landings/PLAN-V2-142.md`: the plan this one succeeds), PLAN-V2-83.
- Overlaps with (class level): PLAN-V2-161 by `RoleArtifactValidator.java` (its item 80 corrects the reserved-keyword copy in the same class), PLAN-V2-146 to 150 and 162 by the profile and launch classes and `Traceability.adoc`: the gate decides at emit; one shared file does not block.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-168-harness-icon-and-rename-closeout.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Carried Open Items

86. **The residue of the landing of PLAN-V2-142, the sanitiser corners, the store's recovery, the rename's remainder and the grammar copies** (`landings/PLAN-V2-142.md`, carried 2026-11-22; merged as #552 `c910c59b` and #548 `fee03237`; read by the analysis leaf at `fee03237`, four points re-read by the orchestrator; no blocker). Deliverables 1 to 4 above carry each point in detail: (a) PNG without a pixel bound; (b) `iCCP` and `sPLT` copied; (c) the DOCTYPE scan and the signed-byte sniffer; (d) the index-sharing slice of `requireLocalUrls`; (e) the ADR 012 list and the style gaps; (f) `id` and `class` allow-listed (a lead for the client); (g) the unreadable index wedge and the crash window; (h) `source_url` userinfo and the echoed id; (i) the unrecorded same-user race of `find`; (j) the store's test gaps; (k) the order of the missing row; (l) the stale wording; (m) the neither-member pointer; (n) the fixtures and tests on the old name; (o) the missing `harnessIdIsRead`, the endpoint both-refused case and the audit-key test; (p) the unnamed gate rewrites; (q) the claims of the message that the diff does not bear (36 and 97 files against 37 and 107; "no stored format changes" while the audit detail key changed; the link check reported 3247 broken links against the required 0); (r) the six copies of the harness-id grammar, ask-at-refine. The start spacing that no production class calls is PLAN-V2-162 item 85. API the assembly must follow: the accessors and builder methods `clientId` to `harnessId` on `HarnessEnrolment`, `LoadedProfile`, `VerificationRecord`, `EndpointProfile`, `HarnessProfile` and `EligibilityRefusal`, `ProfileValidator.clientId(JsonNode)` to `harnessId(JsonNode)`, `AuditEvent.DETAIL_CLIENT_ID` to `DETAIL_HARNESS_ID`, `HarnessUsageCoverage.of(UsageResult)` removed, a builder on `HarnessVerification.Request`.

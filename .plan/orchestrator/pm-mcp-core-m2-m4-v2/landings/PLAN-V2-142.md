# Landing Analysis: PLAN-V2-142 — harness-verification-residue

epic: pm-mcp-core-m2-m4-v2
workstream: WS-04
pr: #552 (merge `c910c59b923c5cc2ecf295382a930947e543ec80`, D1 and D2, 37 files, +2920/-122) and #548 (merge `fee032378b3bf5cf90097d1bd2562ed0f738b933`, D3 the rename, 100 files by `gh`, 107 by `git show --stat`, +652/-536); the single PR was refused by CodeRabbit at 126 files (limit 100) and the operator chose the split

> Landing record, runbook-lane form (`rb-v2-142-harness-verification-residue`, harness opencode, model `deepseek-v4.1-flash/high`, effort high, plugin version `unknown`, executor 0.1.2184, 3 deliverables, standing rules r7). Two landing messages: `-001` (2026-11-21T22:41:55Z, `merge_state=open`, reviews blocked on the required reviewer's quota; observed in the twentieth run) and `-002` (2026-11-22T15:40:03Z, `merge_state=merged`). `landing-facts`: `total_tokens` and `plugin_version` `unknown` (the two keys of every opencode landing, folded into the standing Open Defect); review threads 0, body findings 2 of 2 handled, Sonar 0 open: stated by the lane, not re-verified. Self-review `clear` after four rounds. No blocker and no major defect.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 the harness icon store and its sanitising accept step | shipped-as-specified | `IconStore.java:170-203` (`icons/<id>.<ext>`, mode 0600, `index.json`, `locks/icons.lock`, atomic write, 256 KiB), `LockKey.icons`; `SvgSanitiser.java:56-299` re-serialises from StAX events, so comments, processing instructions and foreign namespaces are dropped or refused; `PngSanitiser.java:65-196`; ADRs `runtime-launch/012` and `013` with `SvgSanitiserAllowListParityTest` binding the ADR lists to the code; neither `OutboundCallsArchTest` nor `runtime/guard` is in either PR's file list |
| D2 the corrections of PLAN-V2-39 | shipped-as-specified | Traceability PM-CLIENT-5 and PM-SEC-8 now say "precondition, no production caller" (`git grep '.admit('` finds only tests); `@Builder` on `HarnessVerification.Request` with `HarnessVerificationBuilderContractTest`; `requireStages` refuses empty, duplicate and unknown with codes asserted (`HarnessVerificationTest.java:133,144,155`); `HarnessEnrolment.updated` drops only a `VERIFIED` usage report, messages, Javadoc and `runtime-launch.adoc:305` agree; the missing row for an enrolled harness whose executable is gone (test `enrolledHarnessNoLongerFoundIsShownAsMissing`); `HarnessUsageCoverage.of(UsageResult)` deleted |
| D3 the `harness-renames` package | shipped-modified | the readers read the old member where the new is absent and refuse both (`ProfileValidator.java:324-406`, `VerificationRecord.java:222-232`); no `clientId` or `client_id` is left in main outside the two compatibility readers and the GitHub App `client_id` of `credential-file.schema.json`; the package file and its index row are deleted. Modified: the rename is incomplete in wording, fixtures and tests (L3-15, L3-16), and one of the tests the message names does not exist (L2-1) |

The premise "a `harnesses.json` written with the old key" is false: no entry member was ever named `client_id` (`runtime-launch.adoc:317` records it). The shipped schemas already said `harness_id` (`harness-profile-v1.schema.json:41,560`, `endpoint-profile.schema.json:6,11`); the Java readers required `client_id`, so Java and schema had drifted, and D3 closes it with no schema file changed (byte-stability holds).

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`, `orchestrator.analyze` surface); no file written, no build run; the spec was passed as data from a file outside the repository. Reason: the icon store's security surface and a 107-file rename are the depth the dispatch rule exists for, and the landing was one of two in this drain.
- **Re-read by the orchestrator at `origin/main`:** `harnessIdIsRead` is found only at `VerificationRecordTest.java:127`; `VerificationRecord.java:222-232` (a canonical record with neither member reports `/client_id`); `PngSanitiser.java:42` and `:158-166` (the header check is `>= 1`; `STRIPPED` is the four text chunks); the four Java copies of the harness-id grammar (`ProfileValidator.java:275`, `ShippedProfiles.java:43`, `RoleArtifactValidator.java:63`, `GradedConfiguration.java:46`). **Not re-read, kept as the leaf's claims (unverified leads):** the sanitiser bypass analysis, the store's race and wedge points, the stale-wording and fixture lists, the API list, the unexplained-files claim.
- **Verdict:** layer 1: findings (a false premise, drift Java against schema); layer 2: findings (one unborne claim, wrong counts, unexplained files); layer 3: minor only, plus leads; no blocker, no major, and no bypass of the SVG allow-list that reading shows.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence |
|---------|-------|---------|----------|----------|
| L1-1 | "A `harnesses.json` written with the old key" | checked | refutes | no entry member was ever named `client_id`; `runtime-launch.adoc:317` |
| L1-2 | The shipped schemas already carry `harness_id`; only the Java readers carried `client_id` | checked | confirms | the two schema files cited above; D3 changes no schema |
| L1-3 | The spec's "28 files" was already corrected by the re-ground record; `JudgmentEndpoint` is gone; the machine-config override carried no `client_id` | checked | confirms | |

**Layer 1 verdict:** findings — 1 (minor, a false premise the lane found and stated).

### Layer 2 — Implemented

| Finding | Detail | Verdict |
|---------|--------|---------|
| L2-1 | The message names `ProfileValidatorTest.harnessIdIsRead`; it does not exist (re-read). The canonical profile case is covered only indirectly, by every fixture now written with `harness_id`. The endpoint profile has old-name and new-name tests (`EndpointProfileSchemaParityTest.java:162-181`) but no both-refused test | checked (re-read) |
| L2-2 | The message counts 36 and 97 files; `gh` reports 37 and 100 and `git show --stat` of the #548 merge reports 107 | checked |
| L2-3 | The #548 body says "no stored format changes"; the audit detail key changed (`AuditEvent.java:154`, `DETAIL_HARNESS_ID`), which the spec sanctioned: the body contradicts itself | checked |
| L2-4 | The message says "BROKEN LINKS 3247, pre-existing"; CLAUDE.md demands 0 before every merge; the 3247 is unverifiable and the documentation checkout lags origin | unverified lead |
| L2-5 | Files no deliverable names: in #548 FQN-to-import and import-order rewrites in `QuestionRecordReader`, `PendingEntryCreation`, `PendingEntryRecord`, `SourceManifest`, `SourceManifestCodec` and four test files (pre-commit gate rewrites that belong in a separate cleanup commit); in #552 `ReservedKeywordsSpecificationParityTest.java` `throws IOException` became `throws Exception` with a stray double blank line | checked (main-code diffs read; the four test diffs not read) |
| L2-6 | The four accepted items are confirmed as stated (the style deny-list, `<!doctype` in a comment refused, the icon directory not fsynced, the `LockKeyTest` line); the `LockKeyTest` diff shows two clean added lines, so that item looks moot | checked |
| L2-7 | Test quality: no Mockito or Hamcrest in the files read; assertion messages of the spot-checked tests are 20 to 60 characters; `EndpointVerificationTest.foreignExecutableRefusesDraft` uses a lambda `SocketOwner` double in the style of its neighbours. Not run: the 12 127 tests, the log ids, the Sonar and coverage figures | checked, partly |

**Layer 2 verdict:** findings — 5 (minor).

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `PngSanitiser.java:162` (re-read) | a 256 KiB PNG whose IHDR claims about 2^31 by 2^31 with a zlib stream of zeros | only `>= 1` is required and the IDAT is never inflated; the store accepts and serves it, so a client's decoder pays the decompression cost; the 256 KiB bound is on bytes | minor (the sink is the client; the only source is the deferred fetch) |
| L3-2 | `PngSanitiser.java:42` (re-read) | a PNG with `iCCP` or `sPLT` | copied; an ICC profile can carry free text; ADR 012 states the bound for private chunks only | minor |
| L3-3 | `SvgSanitiser.java:149-152` | a UTF-16 `<!DOCTYPE` | the byte scan decodes ISO-8859-1 and does not match; the fallback is the StAX DTD event at `:140`, whether the JDK emits it under `SUPPORT_DTD=false` is not verified; the output is rebuilt, so the impact is probably nil; no test with UTF-16 or a non-UTF-8 document | minor (lead) |
| L3-4 | `SvgSanitiser.java:119` | `FF FF 3C ...` | `candidate[position] <= ' '` compares signed bytes and skips every byte at or above 0x80; it passes `looksLikeXml` and fails in the parser; only the refusal reason differs | minor |
| L3-5 | `SvgSanitiser.java:283-297` | `fill="İ url(x#a)"` | `requireLocalUrls` finds `url(` in the lower-cased text and slices the original; U+0130 shifts the slice and passes the fragment check; judged unexploitable because the attributes that honour such a value are not allow-listed | minor |
| L3-6 | `SvgSanitiser.java:276-277`, ADR 012 `:40` | `src(`, `position:fixed` | not refused (an accepted deny-list); the ADR names only `url(`, `@import` and the backslash while the code also refuses `image-set(` and `image(` | minor |
| L3-7 | `SvgSanitiser` allow-list | `id` and `class` | allow-listed; DOM clobbering if a client ever inlines an icon instead of serving it as an image | minor (lead, depends on the client) |
| L3-8 | `IconStore.java:196-200` | an unreadable index, or a key that fails `HarnessId.matches`; a failure between the icon write and the index write | `index()` throws inside `accept`, so every later accept refuses, and no remove or repair verb exists; the other-media delete before the index write leaves the old entry unserved (safe, the icon is lost) | minor |
| L3-9 | `IconStore.java:134`, `:147` | a same-user race | `find` is lock-free and returns a `Path` after verifying it; the NOFOLLOW check and the follow-links open are separate; by design, undocumented in the Javadoc | minor |
| L3-10 | `IconStore.java:223-232`, `:176` | `https://user:pw@host/`; a caller's harness id | `isHttpsUrl` accepts userinfo and writes it into the index; `IconRefusal` echoes the caller's id | minor |
| L3-11 | `IconStoreTest` | the index file's mode, the directory's mode, the lock, two concurrent accepts, `deleteOtherMedia` on a non-empty directory | untested (only the icon file's 0600 is asserted) | minor (test gap) |
| L3-12 | `HarnessListings.java:~63-68` | an enrolled harness whose executable is gone | the scan row is removed and the missing row appended at the end, which breaks "keeps the scan's order" of `HarnessList` | minor |
| L3-14 | `VerificationRecord.java:222-232` (re-read) | a canonical record with neither member | the missing-id refusal names the pointer `/client_id` (the old spelling) | minor |
| L3-15 | `ProfileLoader.java:36,163`, `ProfileSet.java:40,46,58`, `HarnessEnrolmentStore.java:114,125`, `core-config.adoc:35,115`, `core-id.adoc:115-116`, `core-profile.adoc:182` | the text | still says "the record names the client", "client-id order", names `RoleArtifactValidator.CLIENT_ID` and `ProfileValidator.CLIENT_ID` (now `HARNESS_ID`) and `enrolledByClientId` | minor |
| L3-16 | `LaunchFixtures.java:67,80`, `SupervisorTestFixture.java:496`, `ProfileWorkerEligibilityTest.java:405,516`, `HarnessProfileTest.java:40,87`, `LongContextThresholdsTest.java:40,78,81,105`, `HeadlessBlockTest.java:117`, `EnvelopeBudgetProfileThresholdTest.java:56`, `worker/verification-record-*.json`; names at `ProfileLoaderTest.java:100,258,291`, `ShippedProfilesTest.java:102`, `ModelCatalogueStoreTest.java:152` | the fixtures | the primary form is still `client_id`, so the canonical `harness_id` is barely exercised outside `core.profile`; test names still say "client id" | minor |
| L3-17 | `ProfileLoader.java:143,251` | a client that matches on a refusal pointer | the pointers moved from `/client_id` to `/harness_id` and the messages were reworded; intended | minor |
| L3-18 | `AuditEvent.java:154` | a reader of `details.client_id` of `harness_updated` lines | `DETAIL_HARNESS_ID = "harness_id"` is a stored-datum change for new lines; old lines keep `client_id`; no main code reads detail keys; no spec lists them; no test asserts the new key for `harnessUpdated` and `harnessWeightsUpdated`; the hash chain is not rewritten (the chain code was not read) | minor (lead for other repositories) |
| L3-19 | the harness-id grammar | a one-character id | four Java copies of `{0,31}` (`ProfileValidator.java:275`, `ShippedProfiles.java:43`, `RoleArtifactValidator.java:63`, re-read, and `GradedConfiguration.java:46`, unanchored) and two schemas (`harness-profile-v1.schema.json:43`, `role-artifact-v1.schema.json:18`) accept it where `HarnessId.PATTERN` is `^[a-z][a-z0-9-]{1,31}$`; the message named three copies | minor (a behaviour change, outside the rename) |
| L3-20 | `HarnessStartSpacing.java:72` | the start path | `awaitTurn` has no non-test caller; ADR `runtime-worker/001` still describes the shared instance; the launch guide was corrected by `ce508e17` to say it is owed | minor (confirmed) |

**Layer 3 not shown by reading:** (a) the tests, the 12 127 count, the log ids, the coverage and Sonar figures and the CodeRabbit claims; (b) the JDK StAX behaviour for a UTF-16 DOCTYPE and for undeclared entities (a negative control settles it); (c) whether a real browser or the web client mishandles an accepted output (an inline context, an `image` element, the `<title>` integration point); (d) the bodies of `IconSanitiserTest`, `IconStoreTest`, `PngFixtures`, `IconIndex*`, `IconRefusal` and the four unexplained test diffs; (e) whether the lock blocks across processes (`FileLockManager` was not read); (f) audit consumers in other repositories, and whether `pm-mcp-clients` and the assembly build against the renamed API.

**Layer 3 verdict:** findings — 18 (minor), leads — 4; not shown by reading — 6. No blocker, no major; no bypass of the SVG allow-list that reading shows.

### Conformance to the Project's Own Rules

- No `OutboundCallsArchTest` or guard change; no connection opened by the store; the `source` form's fetch stays deferred to `pm-mcp-clients`.
- Gate rewrites outside the plan's scope sit in the plan's commits, not in a cleanup commit (L2-5); the rule says a separate commit.
- The links check: the lane reported `BROKEN LINKS 3247`, called them pre-existing and merged; the project's rule is 0 before every merge (L2-4).
- Two operator questions and decisions are recorded in the progress file: the fourth self-review round, the split into two PRs, regenerate the executor at the resume.

## Public API the assembly must follow

The accessors and builder methods `clientId` to `harnessId` on `HarnessEnrolment`, `LoadedProfile`, `VerificationRecord`, `EndpointProfile`, `HarnessProfile` and `EligibilityRefusal`; `ProfileValidator.clientId(JsonNode)` to `harnessId(JsonNode)`; `AuditEvent.DETAIL_CLIENT_ID` to `DETAIL_HARNESS_ID` (value `harness_id`); `HarnessStartSpacing.harnessEnrolled(String)` and `LaunchRefusal.resolvingAct` (parameter names); `HarnessUsageCoverage.of(UsageResult)` removed; a builder on `HarnessVerification.Request`. `pm-mcp-clients` must ship its profiles with `harness_id` (the core reader still accepts the old name).

## Residue

| Residue | Owner |
|---------|-------|
| L3-1 to L3-11 (the sanitiser, the store, its tests), L3-12, L3-14 to L3-16, L3-18, L3-19, L2-1, L2-5 (the sanitiser corners, the store's recovery, the rename's remainder, the grammar copies) | PLAN-V2-168 (new, staged, item 86) |
| L3-20 the start spacing that no production class calls | PLAN-V2-162 item 85 |
| the assembly and `pm-mcp-clients` pieces (compile against the renamed API, profiles with `harness_id`, readers of the audit detail) | Watch in `epic.md` |

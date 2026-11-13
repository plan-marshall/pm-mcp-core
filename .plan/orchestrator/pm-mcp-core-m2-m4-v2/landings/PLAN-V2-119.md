# Landing Analysis: PLAN-V2-119 — the last 14 schema blocks move to files

epic: pm-mcp-core-m2-m4-v2
workstream: WS-12
pr: #404 (merge `072418d3`), `merged` through the merge queue, no label

> Landing record for one shipped plan, written by the `analyze` inbox drain of 2026-11-13 from the message
> `rb-v2-119-schema-files-other-specifications-001.md` of the runbook lane (harness opencode, model
> deepseek-v4.1-flash/high, executor 0.1.2114, standing rules r7). The message is a lead. The three layers were read by
> one read-only leaf from git at `origin/main` `072418d3` (it ran `trace.py` and read-only JSON and substring checks, no
> build, no test run); the orchestrator re-read the points marked "re-read"; the provider figures were re-read from the
> SonarCloud public API and `gh pr view 404`.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 move the 14 blocks | met | all 14 files are substrings of the pre-merge documents (`git show 072418d3^:doc/...`), so the move is byte-identical; each document carries `[source,json]` over `----` with one `include::../../../{module}/src/main/resources/.../x.schema.json[]` (for example `harness-profiles/01-profile-schema.adoc:22-25`, `03-endpoint-harness.adoc:31-34`), all 14 targets exist, all 14 files load as JSON and carry an `$id`; the verification-record example stays inline and says so (`01-profile-schema.adoc:417`, "The block below is an example of that record, not a schema."). |
| D2 a file of the same record already exists | met, vacuously | the 14 `$id` values are unique among the 95 tracked `*.schema.json` files; the near-name `core/store/screen-verdict.schema.json` is a different record (the model's `items[]` reply is `workflow/screening/screen-verdict-v1.schema.json`). The spec's premise that corpus schemas pair with these blocks was empty (the lane flagged it as stale premise 4). |
| D3 a parity test per schema whose reader exists | met, with gaps | four classes (`RoleArtifactSchemaParityTest` 21 cases, `EndpointProfileSchemaParityTest` 7, `HarnessProfileSchemaParityTest` 5, `ProjectScriptInputSchemaParityTest` 2 in `pm-runtime`), all read the files through `SchemaResource` of `pm-core:tests`; eleven records have no code (`core.domain` and `core.recipe` hold only `package-info.java`) and are named for the packages that will build the codec. |
| Package bookkeeping (R01, R08) | met | `doc/plans/schema-files-other-specifications.adoc` deleted, its README row removed, the package dropped from the `depends on` cell of `adoc-reading-retired`; `git grep schema-files-other-specifications` finds one hit, `doc/adr/018-...adoc:89` (below). |

Not shown by reading: the four verify runs of the Done-when (348 s, 143 s, 159 s and 145 s at the merge head by the message), the Sonar and CodeRabbit narrative beyond the figures below.

## Three-Layer Analysis

### Layer 1 — Specified

The spec is coherent. ADR 016 requires the file to be "the text of the block byte for byte" and the plan's own constraint says "a deviation is reported, a schema is not changed inside the move", so "byte-identical move" and "parity test" do not conflict: the test pins the drift and the file is the single authority. The three-deliverable cut is plausible; D2 was vacuous because its premise came from the package file. D3's hypothesis "the codecs lie in `pm-core`, `pm-workflow`, `pm-runtime` `endpoint/`" was weaker than stated (no `*Codec` exists for any of the 14 records). One spec flaw: D3 asks that a block whose codec does not exist be "named in the package that builds the codec"; the lane named Java packages and not work packages, and no page under `doc/plans/` names them.

### Layer 2 — Implemented

Conformance (read by the leaf): `@DisplayName` on every test, no Mockito, `Random`, `UUID` or AAA comments, positive and negative controls where described, wire literals justified in the class Javadoc. Deviations:

- **Two `assertAll` headings break the 20 to 60 character rule (re-read, counted: 61 and 64):** "The file names the reader's one protocol (no constant exists)" (`EndpointProfileSchemaParityTest.java:89`) and "The document's members are the file's, with the reader's id name" (`HarnessProfileSchemaParityTest.java:92`); the leaf quoted the line numbers 122 and 62, which hold the same statements' assertion lines. This is what the four Java `SKILL.md` files the lane did not load (its own process gap, R20) would likely have caught.
- The commit and pull request body say "three tests (32 cases)"; four classes landed (35 cases).
- `harness-profiles` test is structural only (the cross-file `$ref` does not resolve, so no fixture is schema-validated, and no negative control); `ProjectScriptInputSchemaParityTest.java:47-54` compares `format_version` with the literal 1 (a pin); the endpoint test does not compare the top-level `required` (12 members) with `ENDPOINT_TOP_LEVEL` (`ProfileValidator.java:190`).
- `doc/Traceability.adoc` has no entry for the four tests (additive bookkeeping, the same gap PLAN-V2-116 had).
- The lane ran on opencode with a different model than the lifecycle; it says it did not load the four Java skills; its code follows the surrounding tests' style.

### Layer 3 — Correct

Spot checks of the constraints against the code agree (role artifact `count` 1 to 16 at `RoleArtifactValidator.java:141`, `budget_floor_seconds` 60 to 86400 at `:145`, the `name`, `harness` and `tool` patterns, the length bounds, `idle_wakeups`, `token_budget`, `hold_seconds`, `resume_within_seconds`; endpoint profile `context_tokens`, `answer_reserve_tokens`, `request_timeout_seconds`, `display_name` and the one protocol at `ProfileValidator.java:420-432`; the five `WorkerKind` constants equal the enum of `project-script-input-v1.schema.json:21-23`); only `count` and `budget_floor_seconds` are covered by a test. No lead-in paragraph contradicts its schema; the rendering is uniform (no `tag=` or leveloffset); no "block above" dangling text. Findings:

- **The endpoint-profile file is looser than the reader (re-read, F2):** `endpoint-profile.schema.json:14` (`icon`) and `:42` (`model_tiers`), and `update_evidence`, are `{ "description": ... }` only, so any value validates, while `ProfileValidator.java:370` (`icon`), `:383` (`update_evidence`) and `:389` (`model_tiers`) validate them strictly; a document with `"model_tiers": 5` passes the file and is refused by the reader. The test Javadoc (`EndpointProfileSchemaParityTest.java:40-42`) says "this test does not compare them" and names no owner; the landing message answered CodeRabbit with "owner named" and named none. **PLAN-V2-123 did not own it** (re-read: its text has no mention of endpoint-profile or harness-profile).
- **Conditions kept as prose in two files no code reads yet:** `recipe-descriptor-v1.schema.json` (`project_script_id` required when `kind == 'project_script'`) and `question-envelope-v1.schema.json` (option counts, `recommendation`, `value_schema` by answer type; only `maxItems: 5` is enforced). Latent holes, no current divergence.
- **Unowned pins:** `headless.loaded` is "owned" by a specification section (`HarnessProfileSchemaParityTest` Javadoc lines 34 to 39), `headless.usage.accumulation` by nothing; the other pins check out (`harness_id`/`client_id` `doc/plans/harness-renames.adoc:15`, `inline_settings` `client-profile-measured-values.adoc:16`, `readable_paths` job-confinement-and-environment, `usage.model` PLAN-V2-135).
- **Stale statement (re-read):** `doc/adr/018-...adoc:88-91` names `schema-files-machine-stores` (done, PLAN-V2-117) and, by the same sentence, `schema-files-other-specifications` (done now) as packages that "will place" a codec-less block; PLAN-V2-123 Deliverable 5(a) covers only the first.
- Three files lack `$schema` (`log-parser-facts-v1`, `judge-score-v1`, `screen-verdict-v1`; every other schema file has it, apart from two test fixtures): permitted by "byte for byte", a standing inconsistency. The `$id` convention matches; the `-v1` suffix is uneven as in the source blocks.
- **`BROKEN LINKS` is 1, not 0, at the head:** `credentials-and-enrolment/03-audit-and-outbound-https.adoc:43` points to `specification/client-profiles/04-judgment-endpoint.adoc`, which the sibling checkout does not carry (the sibling lag; PLAN-V2-137 Deliverable 4 repoints the link). The message reported "BROKEN LINKS 1, unrelated" and the figure of the Done-when is "0": the Done-when says "for this repository's files" and the link is one of them, so the Done-when read strictly is not met at the merge head; the cause is not this plan's diff.

## Conformance to the Project's Own Rules

CodeRabbit (required, quota-limited): five inline findings on `f7a2835f`, two fixed (`23929d32`: the protocol literal, the `worker_kind` enum) and three answered as prose-only changes out of scope (see F2 and the conditions above); five threads resolved; Sourcery (optional) refused on quota. Sonar over plain HTTP: 6 `java:S5778` in the first head, all fixed in `f7a2835f`; `pullRequest=404` 0 open, gate `OK` (re-read); `main` open 83. The lane's three self-review rounds (ten, two, then clear) are in the message.

## Unverified leads

- Whether the PLAN-V2-65 length bounds apply to the new `pm-workflow` and `pm-runtime` schema files (the spec's "State at the emit" noted that old corpus blocks deviate from them; not checked for these 14).
- Whether `doc/plans/` pages exist for the codec-less packages (`core.domain`, `core.recipe`, `workflow.question`, `workflow.screening`, `workflow.model`, `runtime.job`).
- Three `UNIMPLEMENTED` blocks reported by `trace.py` in the documentation repository (`harness-integration/02-bundle-manifest.adoc:48`, `mcp-tools/02-service-and-skill-tools.adoc:31` and `:103`): reported by design, outside this plan's eight documents.

## Metrics and Anomalies

`total_tokens` unknown (opencode lane; the message records `unknown`), harness opencode, model deepseek-v4.1-flash/high, executor 0.1.2114, `guard: none on this harness (unenforced)`; the first head ran verify at 827 s, 584 s, 1009 s and 261 s and the merge head at 348 s, 143 s, 159 s and 145 s (the `reuseForks` setting of #403 had not reached the first head's branch). Review counts: unresolved threads 0, body findings 5 of 5 handled, Sonar 0 open.

## Routing and Merge Behavior

One pull request through the merge queue; the lane merged under the standing authorisation of the `CLAUDE.md` "Who merges" rule. No `candidate-lesson` message (the runbook lane files none). Nothing is routed to `lessons-routing`. The lane's own process gap (four Java skills not loaded) is recorded here, not routed.

## Reconciliation Actions

Row PLAN-V2-119 `running` -> `shipped`, `pr` `#404`, `landing` `landings/PLAN-V2-119.md`, `plan_marshall_plan_id` `rb-v2-119-schema-files-other-specifications`; message `rb-v2-119-schema-files-other-specifications-001` archived; Watch entry in `epic.md`; decision log; resume anchor.

## Follow-Ups

Placed 2026-11-13 under the operator's rule "every leftover goes into a staged plan":

1. **PLAN-V2-123 (Carried Open Items, six items a to f, Expected Surface extended):** the endpoint-profile file's `icon`, `update_evidence` and `model_tiers` constraints and their parity cases (recommendation yes, the file may change, about 80%), the test weaknesses and the two over-long headings, the prose conditions of the recipe and question-envelope files, the owners of `headless.loaded` and `headless.usage.accumulation` (recommendation name them in the Javadoc, about 70%), `$schema` on the three files (about 75%), ADR 018 lines 88 to 91 for both packages, the Traceability entries.
2. The broken link is PLAN-V2-137's (Deliverable 4); no new item.
3. PLAN-V2-122 (`adoc-reading-retired`) now depends on `schema-files-tested-records` only, as the lane left it; it still waits for 129, 121, 123 per the epic order (129 and 121 shipped; 123 staged).

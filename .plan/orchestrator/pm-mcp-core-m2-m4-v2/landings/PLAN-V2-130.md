# Landing Analysis: PLAN-V2-130 — The JSON Schema validator on the 3.x line

epic: pm-mcp-core-m2-m4-v2
workstream: WS-10
pr: #473 (merged `287e1a86`, parent `4b3b775f`; 19 files, +321/-132)

> Landing record for one merged plan, **delivered as specified, no blocker and no major finding**. Written by `analyze` (inbox scan) from the lane's
> landing message `rb-v2-130-json-schema-validator-3-upgrade-001.md` (`deliverables_done` 6 of 6, `merge_state=merged`, `cleanup_owed=false`). One
> read-only leaf (`execution-context-level-3`, `orchestrator.analyze`) read `git diff 4b3b775f 287e1a86` and the merged tree through `git show` and
> `git grep`; by reading, no build, no test run. The orchestrator re-read the helper `SchemaValidation.java`, one supplier-form positive site
> (`ConsentSchemaParityTest.java:110-114`), the `$ref` count of `harness-profile-v1.schema.json` and the stale sentence of ADR `core-analysis/001`.

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|-------------|---------|----------|
| D1 the classes compile and pass on 3.x, nothing but the call edited | met | `SchemaValidation.java:35-36` compiles the loaded text through `SchemaRegistry.withDefaultDialect(DRAFT_2020_12)`; every call site passes the schema text of `SchemaResource`; no schema file, specification block, codec or expected value in the diff; every hunk compared, no assertion removed or weakened |
| D1 the `Set.of()` checks replaced by the equivalent | met | 16 `assertEquals(List.of(), …)` sites (the spec's 12 plus `RoleArtifact` 2, `Intent` 1, `CorpusImportTest` 1); a non-empty `List<Error>` is never equal to `List.of()`, so the sites still fail on an invalid document |
| D1 a message of 20–60 characters | **met in form only** | thirteen literals measured 34–51; eight positive sites keep a pre-existing supplier message (the lane says nine); the module's scanner accepts and does not measure suppliers (L3-1) |
| D1 negative controls fail on a `{}` schema | met for the "after" half | `Tests run: 150, Failures: 27` in the in-repository log; the 27 match the ADR's enumeration; the "before" half was not run (L2-3) |
| D2 the tree, Jackson 3 on the test classpath only | met | `networknt` is named only at `pm-core/pom.xml:76`; none of `pm-workflow`, `pm-runtime`, `pm-providers` imports a validator or a Jackson class (grep); the `slf4j-api` 2.0.20 to 2.0.18 managed downgrade was put to the operator and accepted (lane account) |
| D3 the property, no new POM element | met | `pm-core/pom.xml:20-22`, five changed lines; no `<dependency>`, `<plugin>`, `<exclusion>`, `<dependencyManagement>` |
| D4 three greps, no native-image edit | met | no `META-INF` file in the diff; no other module names the coordinate |
| D5 ADR 028 and its index line | met | `doc/adr/028-the-json-schema-validator-is-on-the-3-line.adoc` follows 027; `doc/adr/README.adoc` one line; the cited `016` line 71 is real |
| D6 the PR, its labels, CI and Sonar | by the lane's account | labels, CI (20 checks) and Sonar (0 open) are not in the repository; not re-queried |
| Expected Surface | clean, with one forced file | five head-added importers (allowed by D1), `CorpusImportTest.java:217` (a consumer of a changed return type, disclosed); no production class, schema, codec or other POM |

## Three-Layer Analysis

### Layer 2 — Implemented

| Id | Point | Verdict | Severity |
|----|-------|---------|----------|
| L2-1 | the helper is not vacuous and is test-scope only: `SchemaValidation` lives in `pm-core/src/test`, and the test JAR's includes do not list it; `pm-workflow`, `pm-runtime` and the providers are unaffected | confirmed (helper re-read; includes by the leaf) | ok |
| L2-2 | `QualificationSchemaParityTest` keeps its `Set<String>` through `Error::getMessage`; both `Set<?>` helper signatures took `List<?>`; no `Set`/`List` import left unused | leaf reading | ok |
| L2-3 | the standing "before and after" scratch run exists for the after half only: the base tree could not be given an executor. The negative-control assertions are untouched by the diff, so the risk is low | confirmed by the landing message and the log | minor |

### Layer 3 — Correct

| Id | Point | Verdict | Severity |
|----|-------|---------|----------|
| L3-1 | eight positive `List.of()` sites keep the supplier message `"…violations of " + document` (`AnalysisResultSchemaTest:98`, `ConsentSchemaParityTest:113` re-read, `ModelCatalogueSchemaParityTest:97`, `RoleAssignmentsSchemaParityTest:98`, `TaskQueueSchemaParityTest:109`, `CorpusSchemaResourcesTest:146`, `AuditSchemaParityTest:105`, `CorpusImportTest:217`); a ninth, `QualificationSchemaParityTest.assertValid`, is a supplier site the diff did not touch; they follow the scanner's rule and are unmeasured, so D1's literal 20–60 wording is not met there; the same class of residue as PLAN-V2-114 items 28, 32(b) and 45(c) | confirmed (one site re-read; the count is the leaf's) | minor |
| L3-2 | the ADR says "no `$ref` difference surfaced" but not whether the 3.x registry resolves or skips the schemas' external references; `harness-profile-v1.schema.json` carries 8 `$ref` (count re-read), is validated by `EndpointProfileSchemaParityTest` (3 of its controls failed under `{}`), and the spec asked D5 to record a `$ref` resolution change | confirmed (count); the resolution behaviour is not read | minor |
| L3-3 | all 69 `$schema` declarations of `pm-core/src/main/resources` are draft 2020-12, equal to the helper's default; `format` is an annotation at that dialect and no test enables assertions, so a `format` change cannot weaken a parity test | leaf reading | ok |
| L3-4 | ADR 028 says "the twelve positive checks of the fourteen classes"; after the change there are 16 sites | leaf reading | cosmetic |
| L3-5 | ADR 028 states "the 1.5 line receives no Jackson 3 security fix" as fact; it rests on one line quoted from the notes of #71 and the ADR says so a sentence later | leaf reading | info |
| L3-6 | the helper's Javadoc (`SchemaValidation.java:25-26`) leans on "the registry is thread-safe and caches the schemas it compiles"; not verified, and no test depends on it | leaf reading | info |
| L3-7 | `pm-core/doc/adr/core-analysis/001-analysis-result-record-with-strict-canonical-codec.adoc` line 33 says two schema tests read the specification from `../plan-marshall-documentation` and are skipped without it; no test under `analysis/` or `findings/` references `pm.documentation.root` any more (the leaf's grep); line 34 is still true | confirmed (line re-read); the lane named it and left it | minor |
| — | `slf4j-api` 2.0.20 to 2.0.18 on the test classpath of `pm-core`, accepted by the operator | recorded in the lane's message | info |

## Review and Quality Numbers

| Number | Message | Re-read |
|--------|---------|---------|
| unresolved review threads | 0 | not re-queried |
| review body findings total / handled | 0 / 0 (CodeRabbit reviewed `335f473c` after four refused triggers; the merge head stands under the quota exemption; Sourcery refused on quota, optional) | the lane's account |
| Sonar open new issues | 0 | gate `ok` by the lane; not re-queried |

`landing-check`: `complete=false`, missing `total_tokens` (E27; the harness exposes none). Surface delta unmeasured. Dependabot #383 is closed (`gh pr view`
reads `CLOSED`, last mergeable state conflicting); #71 closed.

## Residue

1. **Carried into PLAN-V2-114, item 46:** L3-1, L3-2 (the one read that settles it), L3-4, L3-7; L2-3 and L3-5/L3-6 are recorded here only (no action
   changes a file).
2. **Not routed:** the "before" half of the scratch run (L2-3), the unverified upstream changelog (L3-5).

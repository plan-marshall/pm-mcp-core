# PLAN-V2-189: The outline record loses scope_estimate and added_dependencies, and the finding source dependency_delta goes

epic: pm-mcp-core-m2-m4-v2
workstream: WS-12
origin: doc-review-2026-11-24-001.md section 1 ("scope_estimate removed", "Outline added_dependencies and the Dependency Delta Check removed"); operator's cut, plan 2.
lane: plan-marshall

> Staged plan spec — ready for `/plan-marshall` hand-off. Lives at `plans/PLAN-V2-189-outline-record-scope-and-dependency-removals.md`; one queue row, lane `plan-marshall`. The orchestrator EMITS the command; it never launches the plan inline.

## First Instruction: Read The Standing Rules

Read `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` completely first; say so with the revision in the first progress note and repeat revision and rule ids in the landing message. Drafted set: R02, R05, R06, R08, R10, R11, R13, R14, R18, R19, R20 (orchestrator confirms; R13 binds: stored formats shrink). Plan parameters: R06 developer documentation `pm-core/doc/developer/core-store.adoc`, `core-spi.adoc`, `core-findings.adoc`; ADR directories `pm-core/doc/adr/core-store/` and `core-findings/` (new ADR only for a design decision the outline takes); layout WS-12.

## Objective

The outline record states the size of the change (`scope_estimate`) and the dependencies it adds (`added_dependencies`); the specification now routes the lane on the request's facts and reads dependencies from the build in the phase `sanity-check`. This plan removes both from the record, its codec, renderer and validator, removes the status members that carried the estimate, and removes the finding source `dependency_delta`. A stored old record is refused, naming the member.

## Deliverables

Three deliverables, about 20 files, no split needed. Schema files first.

1. **`scope_estimate` (record, schema, status).** `outline-record-v1.schema.json` (`required` :9 and property :27-35), `status.schema.json:357-359` (`scope_estimate`, `scope_estimate_pre_route`, `scope_estimate_provenance`); `OutlineRecord` (component :40/:66, enum `ScopeEstimate` :130-160), `OutlineCodec` (:81,171,302-316), `OutlineRenderer:41`; tests and fixtures that build or pin it.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java:java-null-safety, pm-dev-java-cui:cui-testing
   - Persona: implementer
   - Test order: first the negative controls in `DeletedMemberRefusalTest` (a stored outline carrying `scope_estimate` is refused whole, naming the member, as `capability` and `interfaces` are); delete `OutlineRecordTest` :100-110 (unknown-value case) and the scope-estimate half of `OutlineSchemaParityTest` (:133-136), update the fixtures and builder-contract tests; then schema and code in one commit.
2. **`added_dependencies` and the four `outline.dependency-*` rules.** `outline-record-v1.schema.json:58` (`required`) and :215-240, `OutlineRecord.AddedDependency` and the deliverable component (:307-359, :440-453), `OutlineCodec` (:97,231,340-373,437-454), `OutlineRenderer:89-91`, `OutlineValidator` (rules :62-71, section parser :87,105,124,224,248-267), tests `OutlineValidatorTest` (:74-75, :101-131, :147-161), `OutlineFixtures:61`, `OutlineRecordTest`, builder-contract tests; `core-spi.adoc:324`, `core-store.adoc:2886`.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java-cui:cui-testing
   - Persona: implementer
   - Test order: first `DeletedMemberRefusalTest` case for `added_dependencies`; a validator control that the heading `**Added dependencies:**` is now ordinary text and raises no violation; then removal.
3. **The finding source `dependency_delta`, and the FIX_PROOF / ADDED_LINES question.** `FindingSourceKind:47-48`, `finding-record.schema.json:23-24` (enum and the description's sentence on the three server sources), `FindingSourceKeyTest:151`, `core-findings.adoc:38-42`. CONFIRM-WHEN-CUTTING: per phase-workflows/05:251 the checks of dependencies, markers, added lines and fix proof are sanity-check steps "decided by the operator, never triaged as findings", so `FIX_PROOF` (:49-50) and `ADDED_LINES` (:51-52) contradict as well; the outline decides from the files named in the HYPOTHESIS below and, if they contradict, removes them in the same commit with the same controls.
   - Skills: pm-dev-java:java-core, pm-dev-java-cui:cui-testing, pm-documents:ref-asciidoc
   - Persona: implementer
   - Test order: first the negative control (`FindingSourceKind.fromWireName("dependency_delta")` is empty and a finding of that source is refused, naming it); then schema and enum together.

**Done when** (against `origin/main` e451ed04): a stored outline with `scope_estimate`, one with `added_dependencies`, and a finding with source `dependency_delta` are each refused and the member or value named — fails at e451ed04; an outline without both members round-trips through codec and renderer and passes the schema parity test — fails at e451ed04 (the record requires both); `OutlineValidator` raises none of the four dependency rules — fails at e451ed04; the other deliverable members, `change_type`, `satisfies`, `affected_files` read unchanged — holds at e451ed04. `verify -Ppre-commit` (rewrites reviewed and committed), `verify -Pcoverage`, `verify -Pintegration-tests -pl pm-core -am`, `verify -pl pm-core -am` green (targeted `-Dtest` where a full verify exceeds the host budget). Sonar: open new-code findings at the head 0 or each key selected by the operator.

## Standing Constraints

- Every removal deletes its pinning tests and adds a negative control; no read shim; schema and parity test in one commit.
- Read cited lines at the head first; drop what a merge settled.
- No dependency or plugin; at most 100 files per pull request; no model-facing content.
- Sonar: fix in a pushed commit or dismiss only on the operator's selection (`operator-selected: {issue-key}: {reason}` through `sonar_rest transition`); an undecidable read blocks.

## Claim Labels

- OBSERVED: every line cited in deliverables 1 and 2 matches `pm-core` at 1ae69d3f (`git grep` of `scopeEstimate`, `ScopeEstimate`, `AddedDependency`, `added_dependencies`, `RULE_DEPENDENCY_*`); no class of `pm-workflow`, `pm-runtime` or the providers names them.
  - verdict: contradicted | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: yes | evidence: status.schema.json scope_estimate members are at :355-357 at head (:357-359 holds scope_estimate_provenance, pr_title, closing braces); schema property block ends :36, added_dependencies :215-245; core-store.adoc:2886 is the 'The Outline Record' heading, added-dependencies sentence is :2892. Other cites (OutlineRecord, Codec, Renderer, Validator, core-spi:324) match.
- OBSERVED: `status.schema.json` `metadata` is retained verbatim (`WorkflowStatus.java:71,96`); no Java reads `scope_estimate*` of the status.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: WorkflowStatus.java:71 (@param retained, every other member by name as raw JSON) and :96 (Map<String,String> retained); WorkflowStatusCodec.java:177 lists metadata among retained members; git grep scope_estimate in *.java finds no reader of the status members.
- OBSERVED: the precedent for a refusal that names a deleted member is `DeletedMemberRefusalTest` (`intent` `capability`, `outline` `interfaces`, `key_links`) — read at `DeletedMemberRefusalTest.java:39-83`.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: DeletedMemberRefusalTest.java:39-83: javadoc names capability, interfaces, key_links, attempt_end; the parameter list holds capability, interfaces, key_links (and attempt_end).
- OBSERVED: no Java other than `FindingSourceKind` names `fix_proof`, `added_lines`, `dependency_delta` or their finding types (`fix_red_missing` etc.); `FindingSourceKeyTest:151` lists the three.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: git grep (java/json) of fix_proof|added_lines|dependency_delta|fix_red_missing: only FindingSourceKind.java:48,50,52, FindingSourceKeyTest.java:151-152 and finding-record.schema.json:23-24; no other Java names them or the finding types.
- OBSERVED: `phase-workflows/05:251` says the sanity checks "are decided by the operator, never triaged as findings"; `phase-workflows/06:164` enumerates the finding sources without `dependency_delta`, `fix_proof`, `added_lines`.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: phase-workflows/05-finalize-review-metrics-descriptor.adoc:251 'decided by the operator, never triaged as findings'; 06-finalize-re-entry-and-merge.adoc:164 enumerates github_pr, gitlab_mr, sonar, ci, self_review, qgate, security_audit, code_review, arch_gate, compiler, self_report; none of the three.
- HYPOTHESIS: `FIX_PROOF` and `ADDED_LINES` contradict the specification and are removed — decided by `doc/specification/store-schemas/03-findings-and-review-records.adoc`, `phase-workflows/06-finalize-re-entry-and-merge.adoc:164` and `doc/specification/phase-workflows/09-sanity-check.adoc` against `FindingSourceKind.java:49-52` (verify-at-outline).
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: 09-sanity-check.adoc:37,41 define added-lines and fix-proof as sanity steps; fix_red_missing is a sanity item (:52); no spec file lists dependency_delta, fix_proof or added_lines as a finding source; FindingSourceKind.java:49-52 still carries FIX_PROOF and ADDED_LINES, so they contradict the spec.
- HYPOTHESIS: `OutlineCoverageTest` and `ValidationFixtures` build deliverables with the removed components — confirm at `pm-core/src/test/java/de/planmarshall/core/spi/validation/OutlineCoverageTest.java`.
  - verdict: contradicted | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: yes | evidence: OutlineCoverageTest.java has no direct reference to the removed components; it builds deliverables only through ValidationFixtures. Only ValidationFixtures.java:94 (ScopeEstimate.SINGLE_MODULE) and :119 (List.of() as addedDependencies) name them, so OutlineCoverageTest needs no edit itself.
- Verify-first clause: if the outline finds the sources kept, deliverable 3 shrinks to `dependency_delta`; say so in the landing message.
  - verdict: unverifiable | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: Conditional statement about what the outline will find and the landing message will say (future tense); no read decides it. Reading at the head shows the specification does not keep the sources, so the clause would not fire.

## Expected Surface

- OBSERVED: `pm-core/src/main/resources/de/planmarshall/core/store/outline-record-v1.schema.json`, `pm-core/src/main/resources/de/planmarshall/core/store/status.schema.json`, `pm-core/src/main/resources/de/planmarshall/core/findings/finding-record.schema.json`
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/OutlineRecord.java`, `pm-core/src/main/java/de/planmarshall/core/store/OutlineCodec.java`, `pm-core/src/main/java/de/planmarshall/core/store/OutlineRenderer.java`, `pm-core/src/main/java/de/planmarshall/core/spi/validation/OutlineValidator.java`, `pm-core/src/main/java/de/planmarshall/core/findings/FindingSourceKind.java`
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/store/OutlineFixtures.java`, `pm-core/src/test/java/de/planmarshall/core/store/OutlineRecordTest.java`, `pm-core/src/test/java/de/planmarshall/core/store/OutlineRecordBuilderContractTest.java`, `pm-core/src/test/java/de/planmarshall/core/store/OutlineStoreBuilderContractTest.java`, `pm-core/src/test/java/de/planmarshall/core/store/OutlineSchemaParityTest.java`, `pm-core/src/test/java/de/planmarshall/core/store/DeletedMemberRefusalTest.java`, `pm-core/src/test/java/de/planmarshall/core/spi/validation/ValidationFixtures.java`, `pm-core/src/test/java/de/planmarshall/core/spi/validation/OutlineValidatorTest.java`, `pm-core/src/test/java/de/planmarshall/core/findings/FindingSourceKeyTest.java`
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/spi/validation/OutlineCoverageTest.java`
- OBSERVED: `pm-core/doc/developer/core-spi.adoc`, `pm-core/doc/developer/core-store.adoc`, `pm-core/doc/developer/core-findings.adoc`

Not surface (R19): `doc/plans/README.adoc`, `doc/adr/README.adoc`

## Dependencies and Sequencing

- Depends on: the schema-and-store-removals plan (it edits `outline-record-v1.schema.json:88`, `status.schema.json` and `finding-record.schema.json:102`); P2 starts after it merged.
- Overlaps with: P3 and P5 (`status.schema.json`, different members), P1 (above); `core-store.adoc` and PLAN-V2-185 (disjoint sections; 185 first or rebase); PLAN-V2-126 touches runtime and provider tests only.
- Adjacent to: `phase-sub-workflows` (its task text names the outline record; the text edit is the package-text plan), the sanity-check step `dependencies` (a feature of `finalize-pipeline-and-merge`, not built here).

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-189-outline-record-scope-and-dependency-removals.md"
```

## Write-Boundary

The plan touches only its own repository source and tests; it creates and edits NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message (`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary).

## Re-Ground Record of 2026-11-27 (cleanup, head 115d5987)

Binding, like every earlier record: where a claim above and this record disagree, the record is what is true at the head; the claim bullets are not rewritten. Read by read-only leaves (R21) from the head `origin/main` 115d5987; each contradicted claim is absorbed here (`rescoped: yes`).

- Claim 0: status.schema.json scope_estimate members are at :355-357 at head (:357-359 holds scope_estimate_provenance, pr_title, closing braces); schema property block ends :36, added_dependencies :215-245; core-store.adoc:2886 is the 'The Outline Record' heading, added-dependencies sentence is :2892. Other cites (OutlineRecord, Codec, Renderer, Validator, core-spi:324) match.
- Claim 6: OutlineCoverageTest.java has no direct reference to the removed components; it builds deliverables only through ValidationFixtures. Only ValidationFixtures.java:94 (ScopeEstimate.SINGLE_MODULE) and :119 (List.of() as addedDependencies) name them, so OutlineCoverageTest needs no edit itself.

## Fourth Doc-Review Round Note (2026-11-27, orchestrator)

Binding: The fourth doc-review round (`inbox/doc-review-2026-11-25-004.md` §2) asks whether this plan or a removal plan takes the removal of `evidence.class` `verifier`: the orchestrator's decision of 2026-11-27 is a removal plan, **PLAN-V2-211** (it also removes the stored `blocks`); this plan does not touch `evidence.class`. They share `finding-record.schema.json` and `core-store.adoc` (a re-read by whichever merges second).

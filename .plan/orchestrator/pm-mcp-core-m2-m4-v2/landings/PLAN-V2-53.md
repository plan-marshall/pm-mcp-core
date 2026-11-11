# Landing Analysis: PLAN-V2-53 — enforce the closed list of outbound calls

epic: pm-mcp-core-m2-m4-v2
workstream: WS-06
pr: #365 (merge `50ec9a55f990c5b57322ea358f927375495fa508`, state `merged` per the CI abstraction; 19 files, +1020 -2)

> Landing record for one shipped plan, written by the `analyze` inbox drain of 2026-11-11 from the runbook-lane
> message `rb-v2-53-outbound-calls-guard-001.md` (harness opencode, model deepseek-v4.1-flash/high). The message is
> a lead. The three layers were read by one read-only leaf from git at `50ec9a55` (no build); the orchestrator
> re-read the points marked "re-read"; the provider figures were re-read from GitHub, the run's progress file and the
> SonarCloud public API.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 controls first, the dependency, rule A (no class opens a connection) | shipped | `OutboundCallsArchTest` in `pm-runtime/src/test/.../guard/`; the connection-type set equals the spec's, subtypes through `isAssignableTo`, nested and anonymous classes mapped to their top-level class; the exemption map holds exactly four classes (`WorkerEgressProxy`, `DbusConnection`, `ShippedRoles`, `ShippedProfiles`), each really opening a connection or channel (leaf read the four sites). The dependency is one test-scope `archunit-junit5` in `pm-runtime/pom.xml`, no version literal (re-read). |
| D2 rule B (the closed list of destination classes) | shipped | The allow-list of four (`JudgmentClient`, `EndpointClient`, `HuggingFaceSourceRepository`, `LfsBatchClient`) equals the classes that import `de.cuioss.http.client` in `pm-runtime` and `pm-core` main; the symmetry test closes both directions. |
| D3 the specification amended | shipped on the Done-when | `03-audit-and-outbound-https.adoc:41-46` carries the rewritten rule, the corpus fetch, the loopback endpoint and the owed integration test; the literal "Implemented by ... Remaining:" wording of sub-item (e) was not written because standing rules r7 R05 forbid it (the operator chose r7). |
| D4 documentation and bookkeeping | shipped, with one broken link | The guide `runtime-guard.adoc`, ADR 023, the README lines, `runtime-corpus.adoc`, the `CLAUDE.md` bullet and the Traceability lines are in the diff; no file in the 19 is unexplained. |
| D5 the read-only sweep, in the PR body | shipped | Reported by the lane (PR body not re-read). |

Facts block: `deliverables_total=5`, `deliverables_done=5` — consistent with this table.

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`, `orchestrator.analyze`), reason: a new architecture rule whose soundness is a per-class question over two modules; no build.
- **Verdict:** specified — findings (minor); implemented — findings (minor); correct — no blocker or major defect.

### Layer 1 — Specified

| Finding | Claim | Verdict | Evidence |
|---------|-------|---------|----------|
| L1-1 | The Done-when says "seven connection fixtures and `CuiHttpFixture`" while D2 and the Expected Surface list nine classes (six connection fixtures, `CuiHttpFixture`, two positive controls); the clause only reads consistently as "six plus `CuiHttpFixture`". | checked, refutes | spec Done-when, D2, Expected Surface |
| L1-2 | D4(e) edits `doc/Traceability.adoc` while the spec's "Not surface (R19)" lists it as not surface; D3(e) prescribes wording that r7 R05 forbids while the spec cites r4. | checked, refutes | spec D3, D4, Not surface |
| L1-3 | The premise that `client-profiles/04-judgment-endpoint.adoc` is the home of the judgment endpoint does not hold: the directory is absent in this repository and at `plan-marshall-documentation` `origin/main` `ca1549b` (re-read after a fetch: `git ls-tree` finds no `client-profiles` or `judgment-endpoint` file). The plan copied the dead target into `03-audit-and-outbound-https.adoc:42` and `:44` and into `runtime-guard.adoc` § Specification Tracing (re-read for the two spec lines). | checked, refutes | spec Claim Labels; `03-audit-and-outbound-https.adoc:42,44` |

**Layer 1 verdict:** findings — three spec contradictions, and a premise that propagated a dead link.

### Layer 2 — Implemented

- **Confirmed:** every deliverable traces to code and to a test that can fail. The control fixtures fail non-vacuously: `failsOnFixture` first asserts `result.hasViolation()` and then that a failure detail names the forbidden target (`OutboundCallsArchTest.java:331-336`, re-read), and the two positive controls hold for both rules. The non-vacuity assertions name `ShippedRoles`, `WorkerEgressProxy` and `HuggingFaceSourceRepository` (`:295-299`). No `src/main` file is in the diff, and none of the 19 files is unexplained.
- **Dependency:** the diff adds one test dependency, `com.tngtech.archunit:archunit-junit5` (`pm-runtime/pom.xml`, re-read: 8 lines, a 3-line comment, no version). `CLAUDE.md` § Dependencies and Versions says never add a dependency without asking and does not list ArchUnit; the authorisation is the spec's decision A1 (2026-10-29, "the operator's word is given for the one new test dependency of `pm-runtime`"), which the leaf and I read in the spec but cannot confirm against the operator's own record. `pm-core` already declares the same dependency.
- **Not borne out:** the lane's narrative says four whitespace-only edits to `pm-core` and `pm-workflow` tests were kept and reported as undeclared surface; the merge diff holds no such file. The lane's "BROKEN LINKS 0" is contradicted by L1-3's dead target (the `trace.py` run was not repeated here).
- **Weak control:** the "every entry carries its specification sentence" test checks only that the string contains the section title and is at least 40 characters long (`OutboundCallsArchTest.java:378-388`).

**Layer 2 verdict:** findings — the guard is delivered and its controls can fail; the dependency rests on a spec statement, one link target is dead, and two lane claims are not borne out.

### Layer 3 — Correct

- The rule is sound for its stated set: the four exempt classes really open connections, and no other class of `pm-runtime` or `pm-core` opens one unseen (the leaf searched by reading at `50ec9a55`).
- **Gaps by design:** the call check reads `getMethodCallsFromSelf()` only, so a method reference such as `URL::openStream` is not seen (ArchUnit semantics from the library API, not demonstrated without a run); `URL.getContent`, DNS lookups, subprocesses that run network tools, reflection and other libraries are outside the set; exemptions are whole-class, so an exempt class may add any further connection type and still pass; the reasons are prose, not checks. "The list is closed in the code" is wider than what the rule proves.
- **Duplicated logic:** `connectionViolators()` re-implements the `connectionFree` condition (`OutboundCallsArchTest.java:239-248` against `:205-218`), so a change to one drifts silently from the other.
- **Rule deviations (re-read):** seven assertions pass the ArchUnit failure report as their message (`:334-335`, `:343`, `:351`, `:403-404`, `:416`, `:424`), neither 20 to 60 characters nor a statement of what is checked; the nine fixtures are static-only classes without `@UtilityClass`, copying `pm-core`'s guard fixtures; the `CLAUDE.md` bullet writes its guide pointer in AsciiDoc syntax (`link:...[...]`, line 146, re-read) in a Markdown file, so it does not render as a link.
- Not shown by reading: that the rule fails and passes as claimed when run, the pass counts (19 class tests, 10022 total as the lane reports), `verify`, coverage and the integration tests.

**Layer 3 verdict:** no blocker or major defect by reading; minor gaps in soundness and seven rule deviations.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md Dependencies and Versions | never add a dependency or plugin without asking | conforms on the spec's word; not confirmable here | one test dependency, authorised by spec decision A1; the allow-list in `CLAUDE.md` does not name ArchUnit |
| CLAUDE.md Dependencies and Versions | no version literal in a module POM | conforms | `pm-runtime/pom.xml` diff |
| CLAUDE.md Code Standards | every assertion carries a message of 20 to 60 characters | deviates | seven assertions, see Layer 3 |
| CLAUDE.md Code Standards | a class that holds only static methods is a `@UtilityClass` | deviates (minor) | nine fixtures |
| CLAUDE.md Code Standards | JUnit 5 only; no Mockito, Hamcrest; no AAA comments; no generic `Exception` | conforms | whole test class read |
| CLAUDE.md Build Rules / R17 | links check reports `BROKEN LINKS 0` | deviates | L1-3 |

## Metrics and Anomalies

- Tokens: `unknown`; `plugin_version` `unknown`; executor `0.1.2088`. The landing-check reports `complete: false` (`total_tokens`, `plugin_version`), folded into the standing Open Defect on runbook-lane landings.
- Review: `review_body_findings_total=2`, `review_body_findings_handled=2`. The provider cannot reproduce "handled"; the run's progress file carries two `finding:` lines (both at `1bcf639d`), so the number is re-verified from the progress file.

## Routing and Merge Behavior

- Review gate re-read: 0 unresolved threads, 2 review-body findings (`review-gate` reports `review_body_findings_handled: 0` and `body_findings_undisposed`; that number is the one the provider alone cannot reproduce, so it is not a difference between the landing and the provider); SonarCloud `pullRequest=365`: 0 open new-code issues, quality gate `OK`. Equal to the facts block.
- CI/merge: #365 `merged` at `50ec9a55`.
- Surface collisions: the lane notes `pm-runtime/README.adoc` (three hunks) as shared with the running PLAN-V2-81; the spec calls the overlap textual (merge the base). No collision predicted by the gate was observed.

## Reconciliation Actions

- [x] row `status` → `shipped`
- [x] row `pr` stamped `#365`
- [x] row `landing` stamped `landings/PLAN-V2-53.md`
- [x] row `plan_marshall_plan_id` stamped `v2-53-outbound-calls-guard`
- [x] the incomplete landing facts fold into the standing Open Defect on runbook-lane landings; a Watch records the residue
- [x] message archived after the disposition was persisted
- [ ] residue plan: not staged; put to the operator (Follow-Ups)

## Follow-Ups

- **Residue of PLAN-V2-53, awaiting the operator's word (one small runbook-lane plan):** correct the dead `client-profiles/04-judgment-endpoint.adoc` link in `03-audit-and-outbound-https.adoc:42,44` and `runtime-guard.adoc` (and the premise it came from); give the seven failure-report assertions a 20 to 60 character message; change the `CLAUDE.md` guide pointer to Markdown; remove the duplicated predicate in `connectionViolators()`; name ArchUnit in the `CLAUDE.md` dependency allow-list if the operator's word of 2026-10-29 stands. The method-reference gap and the whole-class exemptions are design notes for the operator, not defects.

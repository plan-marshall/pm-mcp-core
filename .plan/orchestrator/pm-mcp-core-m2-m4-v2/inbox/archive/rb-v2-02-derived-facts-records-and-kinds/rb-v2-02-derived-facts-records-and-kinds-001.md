envelope_version=1
sender_type=plan
sender_id=rb-v2-02-derived-facts-records-and-kinds
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-10-31T22:33:13Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-02-derived-facts-records-and-kinds
epic=pm-mcp-core-m2-m4-v2
pr=#266
merge_state=merged
cleanup_owed=false
deliverables_total=6
deliverables_done=6
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2041
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
step.sonar-roundtrip.open_issue_count=0
step.sonar-roundtrip.open_issue_rules=none
```

guard: none on this harness (unenforced)

# Landing — PLAN-V2-02: derived facts store (1 of 2)

Standing rules: r4, R01–R14, R17–R20 (R15 and R16 do not apply). Merge commit `f14b4ff3`, PR #266.

## 1. Deliverables

| # | Done-when (short) | Order | Verdict | Test / evidence | Commit |
|---|---|---|---|---|---|
| D1 | package file with the prescribed text, index row, two _Depends on_ cells name the package, links check recorded | none | met | `doc/plans/derived-facts-store.adoc`, `doc/plans/README.adoc`; `BROKEN LINKS 0` recorded in the PR body | `a39a8c2c` |
| D2 | `DerivedFactKindTest`, `DerivedFactKeyTest`, `DerivedFactTest` cover every invariant with a refusing case | none | met | 4 + 6 + 11 tests green; every stated invariant has a refusing case | `9e6db60e` |
| D3 | round trip, canonical bytes byte-identical, one refusing case per rule | none | met | `DerivedFactsCodecTest` 14 tests green | `4a81682e` (numeric form stated in `8ac8cb11`) |
| D4 | parity passes at the head and fails on a changed member, enumeration value or bound, shown on a scratch copy | mutation-after | met | `DerivedFactsSchemaParityTest` 5 green at `7a754fe6`; failing run `7a754fe6`, output `26544b8bd0f14849b203eb63411fd239` (`patternsAndBoundsEqualSchema`: `failure.reason.maxLength` 500 → 501 on a scratch copy) | `7a754fe6` |
| D5 | registration, duplicate refusal, value admitted and refused by a fixture schema, operator-only, empty registry | none | met | `DerivedFactKindsTest` 7 tests green; no concrete kind registered in production or in a default registry | `a5f4526c` |
| D6 | guide section, ADRs, reduced specification, links check | none | met | `core-store.adoc` § Derived Facts Store; ADRs `core-store/053`, `054`, `055`; the specification reduced (schema block, kind table, value-schema blocks, anchors and the *Provider derivations* bullet byte-stable); `doc/Traceability.adoc` PM-EXT-6/PM-IMPL-2/PM-IMPL-7; `BROKEN LINKS 0` | `b342d76d` (consistency in `8ac8cb11`, `6d6bf2ad`) |
| plan | types exist, round trip, every unreadable file a `StoreRefusal`, parity pins records and codec, no concrete kind, package and row, second plan unblocked, Sonar clean, four builds green, links check | none (D4 mutation-after) | met | 3896 tests green at `12d74851`; Sonar 0 new issues at `d2efa041`; `BROKEN LINKS 0` | — |

Per-head test runs: `verify -Ppre-commit` (full reactor) at `6d6bf2ad`, `12d74851` and `d2efa041`; `verify -pl pm-core -am` at `6d6bf2ad`, `12d74851` (3896 tests); `verify -Pcoverage -pl pm-core -am` and `verify -Pintegration-tests -pl pm-core -am` at `6d6bf2ad`, `12d74851`; targeted runs of the changed classes at `61ff9a4c` (47 tests) and `12d74851` (21 tests).

## 2. Personas and skills

- Production code (D2, D3, D5): `persona-implementer`, with `java-core`, `java-lombok`, `java-null-safety`, `javadoc` as standards.
- Tests (D2–D5): `persona-module-tester`, with `junit-core`, `cui-testing` (`@EnableGeneratorController`, `Generators`) as standards.
- Documentation (D1, D6): `persona-documenter`, with `pm-documents` conventions.
- Self-review rounds 1 and 2 and the PR-comment triage: `persona-code-reviewer`.
- Security: no trust boundary, input validation or secret is touched by this plan; `persona-security-expert` was not loaded.
- Per deliverable: the spec declares no `Skills:` or `Persona:` line, so `skills: none named`. Surface delta: `core.store/package-info.java` is the one conditional code edit D6 allows, and the condition holds (the file did not list `09-derived-facts-and-build-timings.adoc`).

## 3. Hypotheses

- HYPOTHESIS "the roadmap item of the milestone is worded so that `Serves` can name it": partly confirmed. Milestone 6 names module discovery (`doc/roadmap.adoc` line 388) and Milestone 7 names project onboarding (line 622); the phrase "persists its snapshot as the derived fact `module_discovery`" is this package's, not the roadmap's.
- HYPOTHESIS "reading the value into `JsonValue` and writing it back keeps a number's digits": refuted for an exponent-spelled number and a negative zero. The canonical form is the one the writer produces: `1e2` is written `1e+2` and `-0` is written `0`; `DerivedFactsCodecTest.numberSpellingIsNormalizedOnce` states it, and the codec Javadoc, the developer guide and ADR 054 state the same.
- HYPOTHESIS "a `current` entry with a `failure` member is a violation": confirmed and decided as stated in D2; the ADR 055 records the written form.
- Verify-first clause: no open PR touched `core.store` `DerivedFact*` or `store-schemas/09-…` (only dependabot #71 was open); `LockKey.derivedFacts` and the schema block were re-read at the head; `doc/plans/README.adoc` named no derived-facts package.

## 4. Findings

- Self-review round 1: F1 the codec/guide/ADR claimed the writer keeps "the digits it was read with" (stronger than the code) — fixed; F2 ADR 054 said the fact-kind schema keywords "are exactly" the `SchemaSubset` subset, though `format` is not evaluated — fixed. Other observations refuted as preferences.
- Self-review round 2: F1 still in the ADR 054 Decision paragraph — fixed; F3 ADR 053 claimed a specification rule the reduction had removed — fixed by carrying the rule in the `Implemented by …` paragraph; F4 the guide's "Tested by" list omitted `DerivedFactKindsTest` — fixed.
- PR review (CodeRabbit, head `6d6bf2ad`), all handled: "reconcile the store scope with `derived-facts-store-operations`" — answered (the second plan is a plan of the epic, not a second package) and the package file's `== What` names it; thread resolved and the bot withdrew the finding. "Reject JSON null as a current or stale value" — fixed (`DerivedFact` refuses a top-level `JsonNull`; `DerivedFactTest.jsonNullValueIsRefused`), bot verified. "Compare schema bounds without narrowing to int" — fixed (`intValueExact()`), bot verified. Follow-up "remove the fixed kind count" — fixed and answered.
- Sonar: 23 open issues at `6d6bf2ad` (S1192, S6916 ×3, S1845 ×2, test S5778/S5785); all fixed in `61ff9a4c`; 0 new issues at `61ff9a4c`, `12d74851` and `d2efa041`.
- CI: green at `6d6bf2ad`, `61ff9a4c`, `12d74851` and `d2efa041`.

## 5. Reviewers

| Reviewer | Required? | Result | Head reviewed | History |
|---|---|---|---|---|
| CodeRabbit | required, quota-limited | reviewed + standing authorization | `6d6bf2ad` (marker `kind=reviewed`) | triggered at setup and after `61ff9a4c` (refused, rate limited), `12d74851` (refused, rate limited) and `d2efa041` (refused, rate limited, 2026-10-31T22:02:41Z); all 3 threads resolved, every finding handled |
| Sourcery | optional, quota-limited | refused (own review budget of 250,000 diff characters for 7 days) | — | triggered at setup; its check is SKIPPED; optional, does not gate the merge |

Standing review authorization (CodeRabbit, quota-limited): a qualifying review exists on `6d6bf2ad`; every finding is fixed and every thread resolved; it was re-triggered after the last push and the refusal is recorded; every other gate holds on `d2efa041`.

## 6. Operator questions

none asked. No free-text operator message was needed; the run was not blocked.

## 7. Rule check

- guard: none on this harness (unenforced); `{guard_log}` (`.plan/temp/guard.log`) does not exist, so no guard refusal is attributable to this run.
- Shell file operations: none. Files were read and searched with the file tools; one command per shell call; no `&&`, `;`, pipes or heredocs.
- Routed builds: 24, all `daemon_longpoll`; one bound expiry (`verify -Pcoverage`, job `0019376c4ac746e3b7e255f5c14c6266`, 640 s) continued as the per-module split `verify -Pcoverage -pl pm-core -am`; no build was backgrounded; one daemon-routed build at a time.
- Operator messages: none; no model switch.
- Restarts / killed sub-agents / re-dispatches: none. Two self-review sub-agent rounds, both completed.
- Spec read: `spec_source=ledger_worktree`; no `spec_differ` marker was returned.
- `executor-skew`: `match` (executor 0.1.2041, lane text 0.1.2041).
- Documented deviations: (1) the R17 links check ran as
  `python3 /home/oliver/git/plan-marshall-documentation/.claude/skills/doc-review/scripts/trace.py …` — R17 prescribes exactly that command, and the script is not executor-mapped; (2) the SonarCloud read used the public API through WebFetch because the `workflow-integration-sonar` verb could not authenticate (no credentials) while `resolve` reported `sonar: not_configured` and R18 states the project is public with no token needed; (3) the roadmap and requirement wording was read from the sibling `plan-marshall-documentation` checkout, which R05, R17 and the spec's own trace instruction name.

## 8. Build server

- Setup preflight: `ready`.
- Per-mechanism count: `daemon_longpoll: 24`, `in_process_fallback: 0`.
- Fallbacks/retries: one bound expiry (`verify -Pcoverage`, 640 s) followed by the split per-module coverage run; no launch-fault retry; no `killed` job.
- `runtime_stamp_reason`: none.

## 9. Residue

- The package `derived-facts-store` stays `open`: its row and file remain, and the second plan `derived-facts-store-operations` (PLAN-V2-05) delivers the store and closes the package.
- Concept and user documentation of the derived facts store and the roadmap check-off are owed to the milestone exit.
- `plan-marshall-mcp` (`pm-mcp-server`) owes the concrete kind registrations and the facts' serving; `plan-marshall-documentation` owes nothing.
- Run scratch (not committed): `.plan/temp/rb-v2-02-derived-facts-records-and-kinds/` (progress, trace matrix, mutated scratch spec).

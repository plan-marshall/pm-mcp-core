# PLAN-02 remainder: check of the five leads against origin/main

Read: pm-mcp-core origin/main 9abf8d3c (code, doc/, pm-*/doc/); plan-marshall-documentation origin/main 32c56a9 (fetched; roadmap
`doc/roadmap.adoc`, `doc/requirements/02-workflow.adoc`, `doc/developer/primitive-spi.adoc`); plan-marshall-mcp origin/main (its `doc/plans`
only, grepped for pm_do and validators); the epic ledger `.plan/orchestrator/pm-mcp-core-m2-m4-v2/` in the working checkout (branch
chore/orchestrator-ledger, not origin/main) for "is there an owner". Not checked: the content of the tests, the stale local clones of other
repositories beyond the fetch, `pm-mcp-clients`.

Roadmap item PLAN-02 served: `doc/roadmap.adoc:230` (Milestone 3, status `[~]`). Its own "Remaining" sentence says exactly:
"the checks of a primitive that need more than its own declarations (the closed gate list, the reserved keywords of the workflow language),
the binding of free parameters, and the rules of `OutlineValidator` that need the working tree, the task derivation, or the build extensions
of a project". It does NOT list validators for `request.md` or pull request bodies. So four of the five leads are on the roadmap line, one is not.

The old package file (git history of `doc/plans/primitive-spi-and-validators.adoc`, commit 65bdb444) asked only for "the in-process content
validators a primitive runs over what a model or an operator submits" with Done-when "a primitive with a duplicate name, an open outcome
set or an undeclared side effect is refused at registration". That acceptance is met.

Present on main (so what is NOT missing): `Primitive`, `PrimitiveOutcome`, `PrimitiveResult`, `FreeParam`, `PrimitiveRegistry` with 15
refusal reasons incl. `MALFORMED_FREE_PARAMETER` (`pm-core/.../spi/PrimitiveRegistry.java:119-145`), `ConventionalCommitValidator`,
`AsciiDocValidator`, `OutlineValidator` (text rules: affected-file intent, dependency grammar; record rules `validate(OutlineRecord,
IntentRecord)`: `satisfies` unknown/uncovered/empty, unchanged-guard-downstream), `IntentAcceptance`/`EarsForm`/`ClarifiedRequestRenderer`,
`BuilderCheck`, `SelfReviewSubmission`; in pm-workflow the unit-vs-registry checks (`WorkflowValidator.requireRegistered`,
`checkParameterEquivalence`/`compareFreeParameters` at WorkflowValidator.java:~740-800) and `INV-GEN-2` tree-checkable part
(`GeneralInvariants.waiverDataFlow`, :189-206).

---

## 1. Validators for `request.md` and pull request bodies (PM-WF-6) -> (d), with the wiring part in lead 5

- PM-WF-6 bullet 4 (`doc/requirements/02-workflow.adoc:89`) names `request.md` and PR bodies only as examples of prose payloads, and
  names exactly three validators (`OutlineValidator`, `AsciiDocValidator`, `ConventionalCommitValidator`). No requirement, specification
  section, watch item or roadmap line names a `request.md` validator or a PR-body validator.
- The specification contradicts the need for a `request.md` text validator: the refine task submits the intent record, never prose; the
  server renders `## Clarified Request` into `request.md` itself (phase-workflows/02:260, :299; phase-workflows/01:419). Validation of that
  submission is the intent record schema + `IntentAcceptance` + `EarsForm`, all on main.
- PR body: spec states only `body: text[1..32768]` (a length bound), server-injected sections between `<!-- pm-mcp:section:<name> -->`
  markers, and `pr_title` validated by `ConventionalCommitValidator` (phase-workflows/04:245, state-catalogue/04:172,
  phase-workflows/02:283ff). No PR-body grammar exists to implement. Marker preservation/rendering is the finalize create-pr rendering
  (package `finalize-pipeline-and-merge`, not a validator).
- The roadmap line 230 does not carry it in "Remaining". The lead is stale/overstated. If the operator wants one, it needs a specification
  first (a documentation/spec decision), not a plan.
- What remains true: "run the domain validator in-process on the submitted parameter before persisting" (bullets 3-5) is not done; that is
  lead 5.

## 2. `OutlineValidator` rules that need the working tree -> (b), no package owns it

- Required: phase-workflows/02-planning-lanes-phases-3-4.adoc: § 1 `files_exist` gate (read/write-replace/delete/write-conditional paths
  must exist, write-new must not, glob expansion, `depends:` closure rule); § 7 `interfaces` (consumed symbol found in the tree by
  `pm_lsp workspace_symbol`, `unverified` while the LSP is not ready); § 8 `key_links` (pattern refused at submission when not compilable
  in the linear-time dialect; checked against the worktree in the Phase 5 sweep, `key_link_missing`). Roadmap: `roadmap.adoc:230`
  (Remaining), `:510` (Milestone 8: "in-band outline submission and validation ... the derivation dry-run"), `:519` (interfaces), `:564`
  (Milestone 9: key links in the verification sweep).
- State on main: `OutlineValidator` has no rule for any of this. `OutlineValidator.java:44-46` and `spi/validation/package-info.java:25-30`
  say these belong to `pm-workflow` (ADR core-spi/002). `git grep` finds no `files_exist`, no `interfaces` rule, no key-link matcher in
  any `*.java` (only record bounds in `OutlineRecord.java:503-525`, codec and renderer).
- Owner: none. `doc/plans/README.adoc` has no Phase 3/4 outline-or-derivation package ("the rest of the later milestones is not cut into
  packages yet"); the ledger's PLAN-V2-* specs only consume it (PLAN-V2-75 chat-plan-revision, re-planning). PLAN-V2-35 (build map, staged)
  and the open package `domain-extensions-and-architecture` supply the build-extension side.
- Smallest deliverable set (when staged): (i) the pure-text `key_links` pattern refusal and the `interfaces` rules that need only the
  transitive `depends:` closure (no tree) in `OutlineValidator`; (ii) a port for tree facts (path exists, glob expansion, symbol lookup
  with the `unverified` result) declared in `pm-core`, implemented in `pm-runtime`/assembly; (iii) the `files_exist` rules over that port
  incl. the `write-new`/`depends:` closure semantics. Fixtures per rule key with matched controls (PM-WATCH-PHASE-4).

## 3. Task derivation or build extensions -> (b), no package owns it

- Required: phase-workflows/02:218-226 (Phase 4 deterministic task derivation, profile-to-task mapping, `TaskGraphValidator`, atomic
  non-idempotent creation); § 2 six-bucket classifier "via the domain build extensions" with `module_testing` profile requirement;
  the derivation dry-run at the outline gate (phase-workflows/02:84, :162, :168, :283ff). Roadmap: `roadmap.adoc:230`, `:510` (derivation
  dry-run, Milestone 8), `:553` (Phase 4 task derivation, Milestone 9).
- State on main: nothing. `git grep` for `TaskGraphValidator`, six-bucket/file-type bucket, `derive-tasks`, `BuildExtension` over `*.java`
  finds only a Javadoc sentence in `pm-workflow/.../verify/OutlineInvariants.java:29` ("The outline validator with the derivation dry-run
  runs in the return evaluation"). The Javadoc of `OutlineValidator`/package-info says "belong to pm-workflow".
- Owner: none (same as lead 2). Depends on the domain-extension/build-map work (`domain-extensions-and-architecture` open; PLAN-V2-35
  staged) and on the skill registry (content side, `plan-marshall-mcp`) for skill resolution.
- Smallest deliverable set: the six-bucket classifier over the build map (needs PLAN-V2-35 first); the profile-to-task mapping +
  `TaskGraphValidator` as a pure function over `OutlineRecord`; the derivation dry-run as an `OutlineValidator`/outline-gate rule that
  refuses a non-derivable outline with `invalid_link_args`. Skill resolution (`skills[]`) is out of the dry-run's necessary core unless
  the registry exists; mark it out of slice.
- Leads 2 and 3 are one cluster (the dry-run is the join) and are better cut together as the Milestone 8 "Phase 3 outline validation"
  package than as a PLAN-02 remainder.

## 4. Registry checks that need the closed gate list or the keyword list -> split: gate list (b, weak), keyword list (d)

- Gate list: spec `workflow-dsl/05-primitives.adoc:104-106` ("Every declared identifier must be on the closed gate list (INV-GEN-2)")
  and `04-ast-node-model.adoc:108-110`. On main `Primitive.waivableGates()` is a free `Set<String>`; `PrimitiveRegistry` does not look at it;
  `INV-GEN-2` is checked only for its tree part (`GeneralInvariants.java:189-206`; ADR workflow-verify/019 names the rest a runtime hook;
  roadmap `:232` lists INV-GEN-2 among the hooks). There is NO enumerated closed gate list in code or in one specification place (it is
  spread over store-schemas/14 non-waivable list, the remediation units and the finalize barrier). So the check cannot be written before
  the list exists. Natural owners: open packages `remediation-units` and `finalize-pipeline-and-merge` (waivable merge gates, `waiver.lookup`).
  Smallest deliverable: a compiled `WaivableGate` list (identifier grammar incl. `ci_check:*`, `analysis_gate:*`, `findings:<class>`,
  `review_evidence[:<bot_kind>]`, `fix_budget:<policy>`) in one holder + a check that `waivableGates()` of each registered primitive is a
  subset (in `pm-workflow` verification context or `PrimitiveRegistry` taking the list as a constructor argument), with a refusing
  fixture per reason.
- Keyword list: the list is spec'd (`workflow-dsl/01-grammar.adoc:235`) and lives, duplicated, in `WorkflowParser.java:127` and
  `WorkflowValidator.java:144` (pm-workflow, which pm-core cannot see). The specification only says the parser rejects a use of a
  keyword as outcome/parameter name or primitive-id segment; a unit naming such a primitive is refused at load. No requirement or
  specification says the registry must refuse at registration (ADR core-spi/001 "handed to the packages that own them" - an
  implementer's note, not a requirement). Verdict (d): not required; if wanted it is a late-failure-to-early-failure improvement in
  pm-workflow (check registered ids/wire names/@FreeParam names against the one keyword set), best folded into the next change of
  `ProductRegistries` (PLAN-V2-10), not a plan of its own.

## 5. Binding of free parameters -> (b), the real gap; roadmap home is Milestone 7 `pm_do`

- Required: PM-WF-6 bullets 3-5; `workflow-dsl/05-primitives.adoc:193-204` step 5 "Validate & Bind Free Parameters": validate raw `args`
  against `LinkSpec.freeParams` (`additionalProperties:false`, required keys, enum, length bounds, int ranges), run the attached content
  validator, refuse with `undeclared_param`/`missing_param`/`invalid_link_args` and `detail`; mcp-tools/04:176, :212. Roadmap
  `:230` (Remaining: "the binding of free parameters") and `:456` (Milestone 7: "Implement `pm_state`, `pm_do`").
- State on main: the declaration side is done (registry `MALFORMED_FREE_PARAMETER`; verifier `compareFreeParameters`). The runtime side is
  not: `WorkflowEngine.ParameterBinder.bind(primitive, status, Map<String,String>)` is an interface whose default throws
  `UnsupportedOperationException` for any parameter (`WorkflowEngine.java:174-196`); `linkOf` (`:588-602`) only checks "has/has no
  params". `git grep` for `undeclared_param|missing_param` in `*.java` finds nothing. `Traceability.adoc` PM-WF-6 says it plainly: "Not
  implemented yet: the validation of `pm_do`, its link ids, entities and `args`, and running the domain validators of bullets 3 to 5
  before a store is persisted".
- Owner: none in this repository (`doc/plans` has no `pm_do` entry; no PLAN-V2 spec; plan-marshall-mcp `doc/plans` has none either - its
  `engine-wiring` is CDI producers only). The MCP tool binding itself is the assembly (plan-marshall-mcp), but the transaction/validation is
  engine logic (`pm-workflow`; `module-structure.adoc`: pm-workflow owns "the hypermedia state engine", the assembly "maps the MCP tools
  and resources to the engine").
- Spec gap to settle first: nothing says how a `ContentValidator` is attached to a free parameter (no field on `ParamSpec`/`LinkSpec`,
  `@FreeParam` carries only length bounds). The plan must decide (annotation member, a registry keyed by primitive id + parameter, or a
  fixed table link id -> validator) and write it into `workflow-dsl/05` and an ADR.
- Smallest deliverable set: (i) a pure `FreeParameterBinder` in `pm-workflow` (or `core.spi`) that takes the `LinkSpec`, the primitive's
  parameter record and the raw `args` map and returns the bound record or a closed refusal value (`undeclared_param`, `missing_param`,
  `invalid_link_args` + detail) - types: text with bounds, enum, int range; (ii) the content-validator attachment decision and the
  violation -> `detail` mapping (line / JSON pointer); (iii) the default `ParameterBinder` of the engine built on it, replacing the
  throwing default; (iv) fixtures: one refusing case per reason with matched accepted control; the refusal leaves the status unchanged.
  Running the validators before the store write for the existing submits (commit message, outline, ADR AsciiDoc) is then wiring in the
  primitives, which exist only from PLAN-V2-20 and later.

---

## Verdict table

| # | Lead | Verdict | Home / owner today |
|---|------|---------|--------------------|
| 1 | validators for `request.md` / PR bodies | (d) not in any specification or roadmap line; `request.md` is server-rendered | none needed; spec first if wanted |
| 2 | `OutlineValidator` rules needing the working tree | (b) | none; Milestone 8 outline validation (pm-workflow + port) |
| 3 | task derivation / build extensions | (b) | none; Milestones 8-9; needs PLAN-V2-35 / domain-extensions |
| 4a | registry check against the closed gate list | (b) weak, blocked: the list does not exist yet | `remediation-units`, `finalize-pipeline-and-merge` |
| 4b | registry check against the keyword list | (d) not required; parser already refuses | optional hygiene in PLAN-V2-10 |
| 5 | binding of free parameters / `args` validation | (b) | none; Milestone 7 `pm_do` (engine part in pm-workflow) |

## Is a single plan warranted?

No. The five leads are not one unit of work: lead 1 is stale, 4b is not required, 4a waits for the gate list that the waiver packages
define, 2 and 3 belong to a Milestone 8 package (outline validation with derivation dry-run) that depends on PLAN-V2-35 and on a port for
tree facts, and 5 is the engine's `pm_do` args validation. The only deliverable that can be staged now with no new dependency is lead 5
(plus the content-validator attachment decision); leads 2+3 as one later package; 4a folded into `remediation-units` /
`finalize-pipeline-and-merge`. The ledger entry (epic.md "roadmap item behind PLAN-02", owner-register D1 Op15) should be retired and
replaced by these three items. The roadmap line 230 still lists the remainder under Milestone 3 `[~]`; this is a documentation-repository
correction (the Milestone 3 item and the Milestone 7/8 items overlap), not part of this repository.

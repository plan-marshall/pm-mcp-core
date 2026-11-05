# Landing Analysis: PLAN-V2-107 — A generator artifact per module

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #269 (merge commit `df3e9f12`, `df3e9f120ff17f5efbec015b34b695f9b8054d00`)

> Landing record for one shipped plan. Lives at `landings/PLAN-V2-107.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source: inbox message `rb-v2-107-generator-artifacts-001.md` (runbook lane, harness opencode, model
`deepseek-v4.1-flash/high`, effort high, executor 0.1.2041, standing rules r4, R02, R06, R08, R11, R17–R20).
Corroborated against `origin/main` (the merge diff of `df3e9f12`, and `CLAUDE.md` § Generator Artifacts as it reads on
`main`) and the PR state read through `ci pr view` and `ci checks status`.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 — the `tests`-classified test JAR mechanism, guarded by `BuildGuardsIT` | shipped-as-specified | `pom.xml` (+27), `pm-workflow/pom.xml` (+32), `pm-runtime/pom.xml` (+7), `pm-provider-http/pom.xml` (+26) in the merge diff; five new fixture projects below `src/guard-controls/` (`provider-core-tests`, `runtime-core-tests`, `runtime-workflow-tests`, `workflow-core-tests`, `workflow-runtime-tests`). `BuildGuardsIT` 18/18 is the lane's run, not re-run here |
| D2 — the `testing.generators` package convention | shipped-as-specified | four `testing/generators/package-info.java` in the merge diff (`pm-core`, `pm-workflow`, `pm-runtime`, `pm-provider-http`) and one factory each (`WorkflowGenerators`, `RuntimeGenerators`, `HttpGenerators`; `pm-core`'s inventory generator beside its `package-info`). The narrowing of the three new jars by `<includes>`/`<excludes>` is the lane's `unzip -l` evidence, not re-run here |
| D3 — `pm-core` inventory and generators | shipped-modified | `RoleSkillGenerator` and its test only. The spec asked for test-first; the lane records the failing run as mutation-after and says so (`RoleSkillGeneratorTest` failed under a `not-a-digest` mutation, then green). The order deviation is stated, not hidden |
| D4 — `pm-workflow` / `pm-runtime` / `pm-provider-http` generators | shipped-as-specified | seven generators each with its own test in the merge diff: `QuestionClass`, `SourceLocation`, `UnitReference`, `ResolvedSkill`, `Endpoint`, `PagedResult`, `Response` |
| D5 — consumption and documentation | shipped-as-specified | consumers edited: `SkillDeliveryTest`, `GeneratedEnrolmentTest`, `GitHubClientTest`, `GitLabClientTest`, `InstallationTokensTest`; six module READMEs; `CLAUDE.md` § Generator Artifacts matches the consumption matrix (`pm-core:tests` for workflow, runtime and the providers; `pm-provider-http:tests` for github, gitlab, sonar; `pm-workflow:tests` and `pm-runtime:tests` for the assembly only); ADR `doc/adr/012-generator-artifacts.adoc` is the file `CLAUDE.md` names |

Surface delta: **57 files**. Inside the declaration: the module POMs, `CLAUDE.md`, the four generator packages. **Outside it**,
all required by a deliverable and all named by the lane: root `pom.xml` (the `dependencyManagement` entries), the five
`src/guard-controls/*/pom.xml` fixtures, six module READMEs, and the consumer test classes (`SkillDeliveryTest`,
`GitHubClientTest`, `GitLabClientTest`, `InstallationTokensTest`, `GeneratedEnrolmentTest`). `pm-provider-sonar/pom.xml`
is declared in the Expected Surface and is **not** in the merge diff: `pm-provider-sonar` does not yet consume
`pm-provider-http:tests`, so the generators that the migration plans give it wait for their own plan.

## Metrics and Anomalies

- Tokens: `unknown` (unobserved, not zero). Duration: not measured.
- Anomalies: **the landing reports `step.sonar-roundtrip.open_issue_count=n/a`** ("lane resolved `sonar: not_configured`"),
  yet the PR's own `SonarCloud Code Analysis` check is `SUCCESS` and the lane quotes the bot's "Quality Gate passed,
  0 New issues". The `n/a` records that the lane could not read Sonar, not that Sonar has nothing to say; the gate result
  comes from the PR check, which is the corroboration. Not a defect of the delivery.

## Routing and Merge Behavior

- Review: CodeRabbit reviewed `14729dac` (one nitpick, fixed in `084c82d6`) and the merge head `c4fc5845` with no
  actionable comments; one quota refusal in between. Sourcery refused on quota (optional).
- CI/merge: `ci pr view` reads `merged` with `df3e9f12`; `ci checks status --pr-number 269` reads `overall_status: success`
  over 20 checks, `build (25)` and `build (26)` both green. No rebase conflict reported.
- Sonar: see Anomalies. On `main`, the gate currently reads `ERROR` (the null-rule issues of PLAN-V2-84, 133 open
  of `S2583`/`S2589`/`S2637`; PLAN-V2-110 is working them down). That is not attributable to this change.
- Collision check: PLAN-V2-107 ran beside PLAN-V2-110, PLAN-V2-01 and PLAN-V2-02 as predicted disjoint. No collision was
  reported by any of the four landings; the prediction held.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-V2-107 --status shipped`
- [x] row `pr` stamped — `269`
- [x] row `landing` stamped — `landings/PLAN-V2-107.md`
- [x] row `plan_marshall_plan_id` stamped — `v2-107-generator-artifacts` (taken from the sender `rb-v2-107-generator-artifacts`)
- [x] epic.md narrative reconciled — the incomplete-landing defect records the recurrence and the Sonar `n/a`
- [x] resume anchor updated in `resume_anchor.md`
- [x] `queue-view.md` regenerated and committed with the row change

## Follow-Ups

- PLAN-V2-85 to 89 (the test migrations that consume the artifacts) no longer wait for 107. They still wait for
  PLAN-V2-110 (the anchor's rule: they edit its 99 files), so none is emittable yet.
- `pm-provider-sonar` consumption of `pm-provider-http:tests` has no staged owner; it belongs to PLAN-V2-92
  (`providers-tests`), which already lists it — recorded as an annotation, not a new plan.
- The generators sample a narrow space (for example `ResponseGenerator` is always a 200 OK) and `QualificationRecord`
  and the `pm-core`-only fixtures were not converted (deliberate, D3). The migration plans widen them as they consume.

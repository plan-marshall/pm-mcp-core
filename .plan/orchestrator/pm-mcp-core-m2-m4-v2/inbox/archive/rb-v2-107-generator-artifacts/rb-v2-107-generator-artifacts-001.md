envelope_version=1
sender_type=plan
sender_id=rb-v2-107-generator-artifacts
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-10-31T23:48:44Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-107-generator-artifacts
epic=pm-mcp-core-m2-m4-v2
pr=#269
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2041
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
step.sonar-roundtrip.open_issue_count=n/a
step.sonar-roundtrip.open_issue_rules=n/a
```

guard: none on this harness (unenforced)

Standing rules: r4, R02, R06, R08, R11, R17, R18, R19, R20. Merge commit `df3e9f120ff17f5efbec015b34b695f9b8054d00`.

## 1. Deliverables

| # | Verdict | Order | Test / evidence | Commit |
|---|---|---|---|---|
| D1 mechanism | met | none | `BuildGuardsIT` 18/18 at `c4fc5845`; `verify` builds the four `tests` jars; parent POM manages the jar plugin (`help:effective-pom`, maven-jar-plugin 3.5.1 in `pluginManagement`); no new plugin or deployment target | `7855f224` |
| D2 package convention | met | none | four `testing.generators` packages with `package-info`/`@NullMarked` and factories; the three new artifacts are narrowed by `<includes>` + `<excludes>` (a `unzip -l` of `pm-core:tests` shows only the factory, four generators and `package-info`) | `4ed3cc1c`, `2f084053`, `a52e549e` |
| D3 pm-core inventory + generators | met | mutation-after | `RoleSkillGeneratorTest.shouldYieldValidAndDistinctRoleSkills` failed at `14729dac` with `RoleSkillGenerator.digest()` mutated to `not-a-digest`: `maven-2026-10-31-215421.log`, `Not a SHA-256 digest: not-a-digest`; mutation reverted, suite green at `14729dac` (the spec asked test-first; the failing output is mutation-after, recorded as such) | `4ed3cc1c`, `084c82d6` |
| D4 workflow/runtime/provider-http | met | none | 7 generators, each with its own test, all green at `c4fc5845` | `814cd624`, `909b48fc`, `82af0f20` |
| D5 consumption + documentation | met | none | `SkillDeliveryTest`, `GeneratedEnrolmentTest`, `GitHubClientTest`, `GitLabClientTest` green; six READMEs, `CLAUDE.md`, ADR 012 agree | `d098b35e`, `2f084053`, `d2572a6a` |

Per-head test runs: `verify` and `verify -Pcoverage` at `084c82d6` and again at the merge head `c4fc5845`;
`verify -Pintegration-tests -pl pm-core -am` (18/18) and `-pl pm-runtime -am` (35 run, 2 skipped: no session
keyring) at both heads. The final head `c4fc5845` is the one merged.

## 2. Personas and skills

Personas applied: `persona-implementer` for the POM/build changes and the generator code; `persona-module-tester` for
the generator tests and the consumer tests; `persona-documenter` for the ADR and the module READMEs;
`persona-code-reviewer` for each self-review round and for triaging the PR comments.

The spec declares no `Skills:` or `Persona:` line (`skills: none named`). R20's skills were read before the outline:
`cui-logging`, `cui-testing`, `cui-http-testing`, `java-core`, `java-lombok`, `java-null-safety`, `javadoc`,
`junit-core`, `junit-integration` (SKILL.md and the standards each points to). No skill was added for surface drift.

## 3. Hypotheses

* **HYPOTHESIS: the parent POM already manages the jar plugin's `test-jar` goal** — confirmed. `help:effective-pom`
  of `pm-runtime` (`maven-2026-10-31-181638.log`) shows `maven-jar-plugin` 3.5.1 in `pluginManagement` (line 13446)
  with the archive configuration; the `test-jar` execution itself is module-level (line 14114), as `pm-runtime`
  declared it. The three new modules add only the execution.
* **OBSERVED claims** — confirmed: `pm-runtime` publishes a test JAR; the named fixtures exist; the dependency rules
  admit `pm-core:tests` for `pm-workflow`/`pm-runtime`/the providers and keep `pm-workflow` and `pm-runtime` apart.
* **Verify-first clause** — the counting commands were re-run at the worktree head (`origin/main`); the inventory
  result is below. The open-PR scan was run at the end (`ci pr list --state open`: #268, #267, #71), after editing
  rather than before; none of the three touches the plan's Expected Surface.

Inventory result: `pm-runtime` builds `ProjectEnrolment` (16), `EndpointProfile` (8) and `LeaseRecord` (5) by hand;
`pm-workflow` builds `RoleSkill` (11). `QualificationRecord` (4) was not converted (its construction needs a full
grading fixture the migration plans will provide). The fixtures used only inside `pm-core` stay; `VerificationResults`
is a file writer, not a domain fixture.

## 4. Findings

* **Self-review round 1** — 12 findings. Fixed: provider consumers missing, artifacts not narrowed, `SkillDeliveryTest`
  determinism, probabilistic distinctness, two false Javadoc claims, the `@GeneratorsSource` note, `CLAUDE.md`
  wording. Refuted: the claim that the pre-commit gate had no 2026-10-31 run (`maven-2026-10-31-182738.log` carries
  the OpenRewrite lines and BUILD SUCCESS). Accepted as landing notes: narrow sampling, inventory scope, surface delta.
* **Self-review round 2** — 10 findings. Fixed: the three narrowed jars still carried their own `*GeneratorTest`
  classes (excludes added), a nullable field in a `@NullMarked` package (`@Nullable`), missing assertion messages,
  `CLAUDE.md` overstatement, three Javadoc claims.
* **Self-review round 3** — `SELF-REVIEW CLEAR: no blocking defect`; one low Javadoc inaccuracy remained and was
  fixed in `14729dac`.
* **CI** — green on `14729dac`, `084c82d6` and the merge head `c4fc5845`.
* **PR #269 comments** — CodeRabbit's first review raised one trivial nitpick (assert the generated endpoint port
  range); fixed in `084c82d6`. After the merge push CodeRabbit reviewed `c4fc5845` with "No actionable comments"
  (`final_review_risk_coverage.coveredCommitId = c4fc5845`). Sourcery refused on quota (optional). Sonar cloud posted
  "Quality Gate passed, 0 New issues". No review threads were opened, so none needed resolving.

## 5. Reviewers

* **coderabbit** (required, quota-limited): reviewed `14729dac` (COMMENTED, one nitpick) and `c4fc5845` (summary
  marker `kind=reviewed`, no actionable comments). One quota refusal was recorded at 22:22 after the intermediate
  re-trigger; the next trigger after the merge push was answered by the `c4fc5845` review. The nitpick's finding is
  handled (fixed in `084c82d6`). Standing review authorization was available but not needed: the current head has a
  qualifying review.
* **sourcery** (optional, quota-limited): refused at 21:55 ("review budget ... for the last 7 days"), not re-triggered
  (optional). Not gating.

## 6. Operator questions

None asked. No question-tool call was made during the run.

## 7. Rule check

* **Guard:** `guard: none on this harness (unenforced)` — the resolve result reported no tool-call guard for
  opencode, so no guard-log line exists for this run. The rules were kept by the run's own discipline, with the
  deviations below.
* **Shell file operations the run issued (the read-class ones included):** shell `find` (2), shell `grep` (several
  inventory/evidence queries), shell `ls` (2), and `unzip -l` (2, inspecting the built `tests` jar). These are the
  section 1 shell-tool deviations; they read build output and source listings, never credentials or paths outside
  the repository (except the build `log_file`s the skill sanctions and the R17 documentation checkout).
* **R17 links check:** run as R17 mandates, with the absolute sibling path
  `python3 /home/oliver/git/plan-marshall-documentation/.claude/skills/doc-review/scripts/trace.py …/doc .`,
  `BROKEN LINKS 0` at the merge head.
* **Builds:** `verify -Ppre-commit`, `verify`, `verify -Pcoverage`, both integration-test runs, and one targeted
  mutation test (the failing `RoleSkillGeneratorTest` run); all routed through the build server, none backgrounded.
* **Restarts / killed sub-agents / re-dispatch:** none. Three read-only self-review sub-agents were dispatched and
  each completed without being killed.
* **Spec read:** `spec_source: ledger_worktree`, no `spec_differ` marker.
* **Executor skew:** `match` (executor 0.1.2041, lane 0.1.2041).
* **Model switches:** none; `deepseek-v4.1-flash/high` throughout.

## 8. Build server

Setup preflight: `ready`. Builds: all routed (`daemon_longpoll`); `in_process_fallback` 0. Fallbacks/retries: none —
no `degraded`, no `executor_absent_from_worktree`, no launch-fault retry. No `killed` job. `runtime_stamp_reason`:
absent. The one failed build was the deliberate mutation test (`role-skill-digest` mutation), not a launch fault; it
was reverted and the suite re-run green.

## 9. Residue

* The generators are valid but sample a narrow space (e.g. `ResponseGenerator` is always a 200 OK); the migration
  plans PLAN-V2-85 to PLAN-V2-93 may widen it when they consume the artifacts.
* Distinctness is probabilistic (~2⁻⁶³) rather than stateful-constructive; stated in the PR body and the ADR.
* `QualificationRecord` and the pm-core-only fixtures were not converted (deliberate, per D3); the landing records
  the inventory.
* R19 surface delta: root `pom.xml` (`dependencyManagement`), five `src/guard-controls/*/pom.xml`, six module
  READMEs and four consumer test classes are outside the declared Expected Surface but required by a deliverable.
* The lane's Sonar configuration resolved `not_configured`; the spec's Sonar clause was nevertheless met by the
  public SonarCloud read on PR #269 (`0` open issues, quality gate `OK`), recorded here rather than in the optional
  `sonar-roundtrip` facts.
* `BuildGuardsIT`'s pre-existing assertions still use the build log as their message rather than a 20–60 character
  sentence; they are pre-existing and were not rewritten.

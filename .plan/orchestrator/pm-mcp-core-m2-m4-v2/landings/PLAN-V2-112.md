# Landing Analysis: PLAN-V2-112 — Main-green reliability bugs (the two open S2637 bugs of `main`)

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #293 (merge commit `11ef1250`, `11ef125044725686a61b1a7686adec8d3b7c3143`; 4 files, +76/-5)

> Landing record for one shipped plan. Lives at `landings/PLAN-V2-112.md`. A pasted claim is a lead, never a fact.

Source: inbox message `rb-v2-112-main-green-reliability-bugs-001.md` (runbook lane, harness opencode, model
`deepseek-v4.1-flash/high`, effort high, executor 0.1.2041, standing rules r6). Corroborated: `ci pr view 293` state
`merged`, merge sha as above; `ci checks status`: `overall_status: success`.

## The terminal measurement (read by the orchestrator, 2026-11-05)

The spec's proof is Sonar `main` after the merge, not the pull request. Read from SonarCloud:

- Analysis of revision `11ef1250` (2026-11-05T06:14): event "Quality gate: Green (was Red)".
- `qualitygates/project_status?branch=main`: **OK**; `new_reliability_rating` actual 1 (threshold 1); new coverage 90.4 %,
  new duplication 0.3 %.
- `issues/search?branch=main&resolved=false&types=BUG`: **total 0**. Both S2637 sites (`Scoring.java:80`,
  `UnitFrontmatterReader.java:191`) are closed. **Terminal measurement met.** The `Maven Build` / `deploy-snapshot` runs of the
  merge commit were not read here; the analysis exists on the merge revision, which only a green sonar-build produces.

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|---|---|---|
| D1 `Scoring.threshold` single-read local | shipped as specified | `Scoring.java:80-81` (`var threshold = item.grading().passThreshold(); return threshold == null ? DEFAULT_PASS_THRESHOLD : threshold;`), two `ScoringTest` cases (checked) |
| D2 `UnitFrontmatterReader.Fields` null contract | shipped as specified, plus `previousKey` | `:188 private @Nullable LocalDate modificationDate;`, `:189 private @Nullable String previousKey;`, `:367 Objects.requireNonNull(modificationDate, "modificationDate")` (checked); `UnitFrontmatterReaderTest` unchanged |
| D3 ADR 013 statements | shipped, one overstated evidence sentence | class (c), statements (a)-(c), `Source` names the plan; the cited analogue is not an analogue (L3-1) |

Surface delta: none outside the four files. The formatter's rewrite of `DerivedFactRefreshTest.java` was reverted and is not in
the diff (checked by the sub-agent's reading of the diff list).

## Metrics and Anomalies

- Tokens / plugin_version `unknown` (the open defect in `epic.md`, eleventh recurrence). Reviews: CodeRabbit (required) reviewed
  `c976f2e0`, one finding fixed, later heads refused on quota; Sourcery approved three heads. No operator question.
- The lane found the spec's D2 reasoning wrong (the `previousKey` hypothesis) and said so (H2 refuted: `describe` reads
  `previousKey` at `:320`/`:324` before `:271` assigns it). The lane's correction holds (checked).

## Three-Layer Analysis (standing rule R21, r6)

Done at the merge commit by a read-only sub-agent, then the points marked **checked** re-read by the orchestrator. Nothing was
built or run.

### Layer 1 — Specified: sound; one false hypothesis already corrected by the lane
- **S1 (low, checked).** The spec said `previousKey` is assigned before every read; it is not (`UnitFrontmatterReader` `:271`
  after the `describe(...)` call at `:270`; first-line `entry_description` case in the test). The lane refuted it; no action.
- **S2 (low).** D1's Done-when "the diff changes `Scoring.threshold` and `ScoringTest` only" sits beside D3's ADR change in the same
  plan; per-deliverable wording, imprecise, not a contradiction.

### Layer 2 — Implemented: as specified
- All three deliverables are in the tree; no undeclared deviation. "BROKEN LINKS 450, none naming adr/013" and the lane's test
  counts are not shown by reading.

### Layer 3 — Correct
- **L3-1 (low, checked).** ADR 013 (a) and the landing's H1 cite `CallGraphExpansion.entered(...)` as "the same ternary" local-form
  site that Sonar does not flag. It is not the same form: `CallGraphExpansion.java:331-332` reads `var binding = ...get(...)`
  and returns `Optional.of(binding.stateId())` or `Optional.empty()`; the local is never returned, so S2637 could not fire there.
  The sentence overstates the evidence. The prediction itself is now proven by the `main` read above, so the ADR's reason is
  wrong but its conclusion stands. A one-sentence correction belongs in a later ADR edit (owner: PLAN-V2-114 D2 or the next ADR
  touch).
- **L3-2 (low).** ADR 013 (b) does not mention that `previousKey` takes `@Nullable` without a guard — the contrasting case.
- **Behaviour preserved** for both changes (read and confirmed). The new tests discriminate against `max(declared, default)`,
  against "always default" and against a min-style implementation.
- CLAUDE.md conformance: clean (assertion messages 33-47 characters, generated data, no Mockito/Hamcrest/Random/UUID, no
  suppression). The `"0.5"` literal has no one-line contract comment (the display name names 0.5).

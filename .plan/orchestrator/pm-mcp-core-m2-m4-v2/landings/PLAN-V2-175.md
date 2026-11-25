# Landing Analysis: PLAN-V2-175 — links-check-requirement-removed

epic: pm-mcp-core-m2-m4-v2
workstream: WS-10
pr: #582 (merge `7259b0390`, 4 files, +7/-10)

> Landing record, runbook-lane form (`rb-v2-175-links-check-requirement-removed`, harness opencode, model `deepseek-v4.1-flash/high`, effort high, executor 0.1.2184, standing rules r7, 2 of 2 deliverables plus an operator extension). Landing message `-001` (2026-11-23T19:59:11Z, `merge_state=merged`, 2 of 2 review-body findings handled, Sonar `unknown`). `landing-check`: incomplete on `total_tokens` and `plugin_version` (the standing opencode Open Defect) and on both Sonar facts (see Process notes). Read by a read-only three-layer leaf at the merge; the orchestrator re-read the residue claims in the ledger: the `**Links:** run the links check (R17)` clause in the staged specs 125 and 126 (found, fixed the same run), `owner-register.md` Wa76 (stale, marked settled) and `doc/adr/README.adoc:34` (verbatim as the lane says). Stated by the lane, not re-verified: the failing and passing check runs, the test counts (12 748 to 12 763), the CodeRabbit and Sourcery dispositions, the three operator questions.
>
> **Process notes the lane reports itself:** (1) Sonar could not be read: CI classifies the change docs-only and skips `build / sonar-build`, so no analysis of PR 582 exists (`gate-status` HTTP 404); the operator chose to merge with both Sonar facts recorded `unknown`; (2) the spec's premise "the two places" and "no other statement" did not hold: three prose statements in `doc/` said the check runs before every merge, and the lane extended the PR to them on the operator's word (question 1); (3) CodeRabbit refused on quota twice and reviewed `c621b94e` on the third try, then `2377b472`; the merged head `c779dc97` was not re-reviewed (done exemption); (4) self-review round 1 found the `doc/` statements; round 2 ran after the merge and found only residue.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | L1 specified | L2 implemented | L3 correct | Verdict |
|--------------------|--------------|----------------|------------|---------|
| D1 `CLAUDE.md`, section "Project" | remove the clause naming `trace.py`, `BROKEN LINKS 0`, `INLINE SCHEMA BLOCKS 0` and "runs locally before every merge"; keep "Links here are relative, also into the documentation (sibling paths)" | `CLAUDE.md:28` now ends "the same names here only point to them. Links here are relative, also into the documentation (sibling paths)."; one hunk, 3 lines out and 1 in | the kept sentence is whole; the paragraph (`:13-28`) reads as one; nothing else in the file changed | met |
| D2 the rules copy | the same sentence removed, file otherwise unchanged | `.opencode/rules/plan-marshall-standing-rules.md:35` is the same sentence; the same single hunk, identical text; the header (the `source-sha256` pin, line 4) is untouched | grep for both tokens over `CLAUDE.md`, `.opencode`, `.claude` returns nothing; the pin is stale (it predates this edit and two earlier `CLAUDE.md` edits) | met |
| D3 operator extension (not a spec deliverable) | three `doc/` statements | `doc/Specification.adoc:17` ("no workflow runs it, and no pull request of this repository is required to run or report it"), ADR 007 Status (`:6`), `:52-53` (superseded note), `:76-77` (Consequences) | each says what is true; ADR 007 is internally consistent (Status, `:53`, Consequences agree; `:84` is history); the one index lag is `doc/adr/README.adoc:34` | met |

The spec's Done-when is met: no `BROKEN LINKS 0` hit in `CLAUDE.md` and `.opencode`; the paragraph still states that links are relative. The remaining hits of the whole tree (excluding `.plan/`): the tool's own `SKILL.md:16`, the three `doc/` edits, ADR 016 `:68`, `doc/plans/README.adoc:375` (history) and unrelated "link check" uses (the engine's lease link check, `{@link Check...}` Javadoc); none states a requirement. No blocker, no major.

## Findings

M1 minor `.opencode/rules/plan-marshall-standing-rules.md:4`: the `source-sha256` pin predates this edit; the spec froze the file, a bridge re-emission refreshes it (recorded). M2 minor `doc/adr/README.adoc:34`: the index line for ADR 007 omits the 2026-11-23 removal that ADR 007's Status records; an index lag, not a contradiction. M3 info `doc/adr/007…:6`: the Status sentence is now one long sentence with inconsistent list items (style). Orchestrator, re-read: two STAGED specs still carried the clause the lane could not see (`PLAN-V2-125` Links paragraph, `PLAN-V2-126` Links paragraph and a "links check (R17) passes" in its Done-when), against the cleanup's statement that the staged ones were corrected: my cleanup missed them; fixed now (the clause reads as void with the reason). `owner-register.md` Wa76 said in the present tense that `CLAUDE.md:30` names an unreachable `INLINE SCHEMA BLOCKS 0`: marked settled.

## Public API changes

None (no Java, no resource, no schema touched).

## Residue and its placement

M2 and M3 are two edits of documentation with no owner: placed in the STAGED plan **PLAN-V2-141** (which already edits the ADR index `doc/adr/README.adoc` for its new ADR row) as a carried block, item 98, with its two files added to its Expected Surface. M1 is recorded, not fixed (the bridge's re-emission; the baseline is not in this repository). Owed outside this repository: `plan-marshall-documentation` (`doc/developer/project-setup.adoc` and the shared specifications) may restate the requirement; it was not read by the lane or by this analysis.

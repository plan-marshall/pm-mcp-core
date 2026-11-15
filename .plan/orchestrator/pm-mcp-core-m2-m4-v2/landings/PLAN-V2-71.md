# Landing Analysis: PLAN-V2-71 — a profile's load directive names the skill through exactly one `{{uri}}` slot

epic: pm-mcp-core-m2-m4-v2
workstream: WS-04
pr: #430 (merge `a73716e9`), `merged` through the merge queue, no label

> Landing record written by the `analyze` drain of 2026-11-15 from the message `rb-v2-71-profile-skill-load-template-check-001.md` of the runbook lane (harness opencode, model deepseek-v4.1-flash/high, executor 0.1.2125, standing rules r7). The message is a lead. The three layers were read by one read-only leaf from git at `a73716e9` (no build, no test run); the orchestrator re-read the points marked "re-read".

## Deliverable Fidelity vs Spec

5 files, +108 / −20: `ProfileValidator.java`, `ProfileValidatorTest.java`, `harness-profile-v1.schema.json`, `pm-core/doc/developer/core-profile.adoc`, `doc/Traceability.adoc` (the last one is outside the Expected Surface; the spec's R05 asks for the edit and the lane declared it). No package file, no ADR, no `ShippedProfilesTest` edit.

| Deliverable | Verdict | Evidence |
|---|---|---|
| D1 the rule | met | re-read: `skillUriSlot` (`ProfileValidator.java:535-545`) counts `SKILL_URI_SLOT_TOKEN` (`:254-259`, `(?<!\{)\Q{{uri}}\E(?!\})`, whole token) and refuses `occurrences != 1` at `/skill_loading/load_directive_template` with "the load directive carries the slot '{{uri}}' N times, not exactly once"; called right after `stringRule` (`:526-528`). Tests `loadDirectiveSlots` (`ProfileValidatorTest.java:52-77`): zero, one (accepted), two, split `{ {uri}}`, `{{{uri}}}`, `{{uri}}}`; the pointer is asserted. The five shipped `pm-api` profiles carry the slot once (read at `pm-mcp-clients` `origin/main`). |
| D2 specification and docs | met | schema: description string only (`harness-profile-v1.schema.json:114`), no `pattern`, no version bump (R13), `HarnessProfileSchemaParityTest` not in the diff; `core-profile.adoc` one new bullet; `Traceability.adoc` the load-directive rule under PM-CLIENT-2 and `ProfileValidatorTest`, `ShippedProfilesTest` under "Verified by" (the `Documentation:` line of the row still points at the endpoint anchor). |
| D3 residue of PLAN-V2-68 | partly met | (a) refusal wording met, re-read: `ProfileValidator.java:1069` "must be at least %d seconds below the idle timeout %d without progress frames", asserted whole by `marginRefusalWording` (`ProfileValidatorTest.java:472`); (b) acceptance assertions met (each row asserts the loaded idle and bounded values; the progress row moved to 480/480); the long line wrapped; (c) "host" → "harness" met for the `IDLE_MARGIN_SECONDS` Javadoc only (the spec named only that). Not done: ADR `core-profile/015` still says "host"; `pm-core/README.adoc` lists ADRs 005 and 010-012 only (re-read `:95-104`; 013-015 missing). |

Done-when: all D1 and D2 clauses met; D3 clause (a)-(c) as named met.

## Three-Layer Analysis

### Layer 1 — Specified
Coherent and grounded (`02-placeholders.adoc`, Rendering Invariants item 5). Decided by the lane: "exactly once" is the plan's reading (the specification says only that the template "has a resolution"; nothing forbids a second slot); whole-token matching (`{{{uri}}}` and `{{uri}}}` refused, a self-review finding); the refusal wording and the constants `SKILL_URI_SLOT`, `SKILL_URI_SLOT_TOKEN`, `PTR_LOAD_DIRECTIVE_TEMPLATE`; the check as its own method after `stringRule`. The validator guarantees the slot, not the call shape (`"{{uri}}"` alone validates); the lane said so.

### Layer 2 — Implemented
As the table.

### Layer 3 — Correct
No bug: blank template counts 0 and is refused at the pointer; a non-string template stops earlier; a profile with no `skill_loading` stops at `required`; `{{uri}}{{uri}}` counts 2. No regression; the one relaxed-looking change (240/240 → 480/480) strengthens. Rows for `{uri}` and `{{URI}}` are absent, and `{{URI}}`, `{{ uri }}` stop earlier at `stringRule`'s "not a closed placeholder". The refusal test asserts the pointer, not the detail text (the margin test asserts the detail). One new `assertThrows` has no message (`marginRefusalWording`, `ProfileValidatorTest.java:472`; the same shape exists at lines 583 and 596). Assertion messages in the new rows are 25-45 characters. Two pre-existing 121-character lines (`ProfileValidatorTest.java:340,399`) remain.

## Leftovers (placed, none asked)
- `ConnectionSettings` (`pm-workflow`, `engine/ConnectionSettings.java:17,26,27,44,58`) still says "host" and "is not %s below the idle timeout" — the wording D3(a) fixed in `pm-core`: PLAN-V2-114 (new item).
- ADR `core-profile/015` "host" (lines 14, 28, 35), the README ADR list gap (013-015), the `Traceability.adoc` `Documentation:` line of PM-CLIENT-2, the message of `marginRefusalWording` and the two 121-character lines: PLAN-V2-63 (it edits `ProfileValidatorTest`, `core-profile.adoc` and adds the next ADR of the same directory).

## Unverified leads
Test, build, coverage and Sonar claims of the lane (not re-run); the `ShippedProfilesTest` result on the merge commit; whether the PR body reports it (D1 asks for it).

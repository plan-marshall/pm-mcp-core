# PLAN-V2-196: The residue of the commit unit's fifth closeout: a caps rule that still contradicts itself and a host-path statement that is too narrow

epic: pm-mcp-core-m2-m4-v2
workstream: WS-01
origin: the three-layer analysis of the landing of PLAN-V2-185 (`landings/PLAN-V2-185.md`, #613, merge `3ba79c4f`), 2026-11-25, and the operator's rule that leftovers go into a staged plan (no staged plan names `CommitAttemptCaps`, `CommitAttemptCounter`, `GitStageAndCommitPrimitive` or ADR provider-git-primitive/011; the freshness-stamp residue of the same landing goes into PLAN-V2-192, which deletes the thing it describes).
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-196-commit-unit-fourth-residue-closeout.md` and is queued as one row file, `queue/PLAN-V2-196.json`,
> in the epic ledger (lane `runbook`). The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`). They bind this plan exactly as if they were written below. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R05, R06, R08, R10, R11, R13, R14, R18, R19, R20. (R12: no message is added; R13: no stored format changes; R17 is lifted.)

Plan parameters: R06 — developer documentation `pm-core/doc/developer/core-store.adoc` and `pm-providers/pm-provider-git/doc/developer/provider-git-primitive.adoc` (corrected statements only); ADR directories `pm-core/doc/adr/core-store/` and `pm-providers/pm-provider-git/doc/adr/provider-git-primitive/` (corrections edit the existing ADRs 087 and 011; no new ADR); layout WS-01. R12 — none.

**Read the skills first (R20).** `java-core`, `javadoc`, `junit-core` of `/home/oliver/git/plan-marshall/marketplace/bundles/pm-dev-java/skills/`, `cui-testing` of `pm-dev-java-cui`, and `ref-asciidoc` of `pm-documents`; say in the first progress note which you read.

## Objective

PLAN-V2-185 closed the commit unit's fourth residue; its analysis found no blocker and no major. What is left is two statements: the caps caller rule, which is now said once but whose new sentence contradicts the rule in four places, and the host-path Javadoc and ADR, which describe the supplementary-letter leak more narrowly than the mechanism they state, with the two cases that would show it unpinned. This plan changes statements and adds two tests; it changes no production behaviour and is meant to be the last of the commit-unit residue stream (it shrank 182, 185, 196).

## Deliverables

1. **The caps caller rule, without the contradiction.** The Javadoc of `CommitAttemptCaps` (`:30-32`) and `CommitAttemptCounter` (`:30-33`), ADR core-store/087 (`:49`) and `core-store.adoc` (`:340`) say the change-set cap is counted "at a failed commit attempt" and then "but a counting event reads nothing". A failed commit attempt is the `hook_failed` outcome, the one event at which the same paragraph says both predicates are read. Say what the rule meant: counting a `git.commit-fix` task reads nothing; both predicates are read only after a `hook_failed` outcome. In `CommitAttemptCounter` drop the unconditional "so the frame cap being spent after the third task cannot end the loop before the fourth attempt runs" and the tail "the counter carries no combined predicate that would let a caller ask elsewhere": the public `frameCapSpent()` can be read anywhere, so the sentence holds only if the caller follows the rule, and the text says exactly that. **Order:** `none` (statements only; no predicate changes, so no failing-first test is due). The four places agree after the change; a grep for "a counting event reads nothing" over the four files finds none.
   - Skills: pm-dev-java:java-core, pm-dev-java:javadoc, pm-documents:ref-asciidoc
   - Persona: documenter
2. **The supplementary-letter statement and its two pins.** `GitStageAndCommitPrimitive.modelDetail`'s Javadoc (`:339-344`) and ADR provider-git-primitive/011 (`:44-48`, `:71`) say "a segment that is such a letter alone becomes the code". The mechanism they state, a one-unit lookbehind that cannot consume the surrogate pair, applies to any segment that ENDS in a supplementary letter or digit (HYPOTHESIS: the pattern text holds no supplementary literal, so the JDK compiles the BMP-only lookbehind; confirm or refute by reading `HostPrefix` and the three existing cases, and by a scratch test run through the build at the outline). Settle the hypothesis first, then say in the Javadoc and the ADR what the pattern does, and pin the two cases the lane left open in `GitStageAndCommitPrimitiveDeclarationsTest`: a segment that ENDS in a supplementary letter, and a longer segment that STARTS with one and ends in a BMP name character (kept). Each new test is a `@ParameterizedTest` of three or more values drawn as the existing ones, carries a one-line comment naming ADR 011 as the contract, and asserts outcome, reason and detail. **Order:** the pins are written after the hypothesis is settled; the statement follows the observed behaviour, never the reverse. **Test order:** if the observation shows the leak is wider than the text, the new tests pin the observed outcome and the statement says so; if it shows the text was right, the tests pin the kept case and the statement is only sharpened.
   - Skills: pm-dev-java:java-core, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-documents:ref-asciidoc
   - Persona: module-tester

**Done when:** the four places of Deliverable 1 agree and contain no "a counting event reads nothing" and no unconditional claim the counter cannot keep; the Javadoc of `modelDetail` and ADR 011 state the supplementary-letter outcome the outline observed, and the two new parameterized tests pin the ends-with case and the kept case; no production line of `GitStageAndCommitPrimitive` or `CommitAttemptCaps` changes (`git diff --stat` of `src/main` shows Javadoc hunks only); `OutboundCallsArchTest` is unchanged; the PR body names the observed outcome of the hypothesis; `verify`, `verify -Ppre-commit` (its rewrites reviewed and committed), `verify -Pcoverage` and the module `verify` of `pm-core` and `pm-providers/pm-provider-git` are green. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready (the lane resolves `sonar: configured`).

## Standing Constraints

- Statements and tests only; no change of what is refused or stored; R10: no schema block is edited.
- Where the project analyses its pull requests with Sonar (`resolve` reports `sonar: configured`): an open Sonar finding is fixed in a pushed commit, or DISMISSED ONLY ON THE OPERATOR'S SELECTION — the question tool names each finding key, the progress record records the selection as `operator-selected: {issue-key}: {reason}`, and the chosen key is transmitted through the plan-less transition verb. The lane never records a dismissal of its own. The merge gate reads the open-finding count at the head, and an `undecidable` read blocks.

## Claim Labels

All lines read at `origin/main` `3ba79c4f` through the three-layer analysis of 2026-11-25; the outline re-reads each before it acts.

- OBSERVED: `CommitAttemptCaps.java:30-32` and `CommitAttemptCounter.java:30-31` say the change-set cap is counted at a failed commit attempt and then that a counting event reads nothing; ADR core-store/087 `:49` and `core-store.adoc` `:340` repeat it; the same paragraph says both predicates are read after `hook_failed`.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: CommitAttemptCaps.java:31-32 "counted at their own events ... at a failed commit attempt ... but a counting event reads nothing"; CommitAttemptCounter.java:30-31 same; ADR core-store/087 :48-49 and core-store.adoc:340 repeat it; the same paragraphs say both predicates are read after hook_failed (Caps :27-30, Counter :27-30, 087 :46-47).
- OBSERVED: `CommitAttemptCounter.java:31-33` states that the frame cap being spent after the third task cannot end the loop before the fourth attempt, beside "stated, not enforced".
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: CommitAttemptCounter.java:31-33 "so the frame cap being spent after the third task cannot end the loop before the fourth attempt runs. The rule is stated, not enforced: the counter carries no combined predicate that would let a caller ask elsewhere".
- OBSERVED: `GitStageAndCommitPrimitive.java:339-344` says a segment that is a supplementary letter alone becomes the code; the three new cases of `GitStageAndCommitPrimitiveDeclarationsTest` (from `:228`) are all "letter alone".
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: GitStageAndCommitPrimitive.java:342-344 (also :74-75) "a segment that is the letter alone becomes the code as well"; GitStageAndCommitPrimitiveDeclarationsTest.java:228-243 three cases (𐐀/x, "file 𐐁/spec.adoc is refused", 𐐂/y), all letter alone.
- HYPOTHESIS: the leak covers any segment ENDING in a supplementary letter or digit, because the pattern text holds no supplementary literal and the JDK compiles the BMP-only lookbehind (`NotBehind`, not `NotBehindS`) — confirm or refute at `GitStageAndCommitPrimitive.HostPrefix` and a scratch run (verify-at-outline).
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: By reading: NAME_CHARACTER (GitStageAndCommitPrimitive.java:140) = "[\\p{L}\\p{N}._\\-\\]})]" is ASCII-only so HOST_PREFIX (:144, note: constant is HOST_PREFIX not a HostPrefix type) compiles a one-unit lookbehind that only sees the unit before the separator; the existing letter-alone leak cases already depend on exactly that. No scratch run done.
- HYPOTHESIS: a longer segment that starts with a supplementary letter and ends in a BMP name character keeps its name — confirm or refute the same way (verify-at-outline).
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: By reading: the lookbehind inspects only the single UTF-16 unit before '/' (HOST_PREFIX :144 over ABSOLUTE_PATH :152), so a segment starting with a supplementary letter and ending in a BMP name character is seen as a name; no existing test pins it (only 'letter alone' cases at test :228-243). No scratch run done.
- Verify-first clause: settle both hypotheses by an observed outcome before the Javadoc or the ADR is edited; a refutation re-scopes Deliverable 2's text, never its tests.
  - verdict: unverifiable | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: Process clause about what the outline will do before editing Javadoc/ADR; future-tense.

## Expected Surface

- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/CommitAttemptCaps.java`, `pm-core/src/main/java/de/planmarshall/core/store/CommitAttemptCounter.java` — D1 (Javadoc only)
- OBSERVED: `pm-core/doc/adr/core-store/087-the-commit-attempt-caps-are-a-member-of-the-workflow-state.adoc`, `pm-core/doc/developer/core-store.adoc` — D1
- OBSERVED: `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/primitive/GitStageAndCommitPrimitive.java` — D2 (Javadoc only)
- OBSERVED: `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/primitive/GitStageAndCommitPrimitiveDeclarationsTest.java` — D2
- OBSERVED: `pm-providers/pm-provider-git/doc/adr/provider-git-primitive/011-the-host-path-introducer-is-the-complement-of-the-path-name-characters.adoc`, `pm-providers/pm-provider-git/doc/developer/provider-git-primitive.adoc` — D2

Not surface (R19): `doc/plans/README.adoc`, `doc/adr/README.adoc`. No `pom.xml`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-185 (shipped, #613). Independent of the others.
- Overlaps decided by the gate at emit: PLAN-V2-192 edits `core-store.adoc` (the freshness and change-ledger parts, other lines than `:340`) and ADR core-store/088 and 089; PLAN-V2-193 edits `provider-git-primitive.adoc:166` and ADR provider-git-primitive/006 (other lines).
- Not here: the freshness-stamp statements (PLAN-V2-192 deletes the stamp), the `status.schema.json:365` description (inside the block PLAN-V2-192 removes).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-196-commit-unit-fourth-residue-closeout.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

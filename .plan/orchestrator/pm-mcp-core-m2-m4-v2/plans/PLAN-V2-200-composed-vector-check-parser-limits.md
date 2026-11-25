# PLAN-V2-200: The composed-vector check's parser limits: the earliest-literal match, the JSON-slot literal exemption, the membership-only tool rule and the presence check

epic: pm-mcp-core-m2-m4-v2
workstream: WS-06
origin: PLAN-V2-162 (#567) landing revision 25, section 9: item 30(c) remainder (the lane's ADR 010 and guide statements stand)
lane: runbook

> Staged plan spec, one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off. SELF-SUFFICIENT together with the standing rules.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`). They bind this plan exactly as if they were written below. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R05, R06, R07, R10, R11, R19, R20 (the drafter's candidates, confirmed by the orchestrator against the rules file at staging; an id the lane finds not applicable it says so).

## Objective

`WorkerVectorCheck` parses the composed vector back against the profile template. Four limits remain, all latent until a profile hits them:
1. `carried()` takes the earliest occurrence of the next literal. One slot plus a trailing literal refuses a conforming vector whose value contains that literal, and adjacent slots refuse by construction.
2. Rule (d) exempts the literal parts of an element that carries a JSON slot, not just the slot value.
3. `{{tools}}` is membership-only, which the Javadoc states.
4. The absent-slot set and `keptIndices` copy the composer's fill algorithm, so the check is independent for values and not for presence.

These are parser changes with their own risk and test surface: adversarial inputs and matched controls first.

## Deliverables

1. **Tests first (adversarial and matched controls).**
   - A one-slot template plus trailing literal, with a slot value containing that literal: the conforming vector must not be refused. This fails at 3ba79c4f.
   - Adjacent slots: the template shape is refused at profile validation, or parsed unambiguously (decided in 3).
   - A literal tail of a JSON-slot element that carries a metacharacter: refused (fails at 3ba79c4f).
   - A conforming JSON slot value with metacharacters: accepted (control).
   - A tool value outside the role's tool set, a duplicate tool, and an empty member: refused.
   - An absent slot at each position (first, middle, last, flag before it) against the template alone.
   - One matched control per refusal, and `argument_rejected` asserted with its rule and position.
   - Skills: pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: module-tester
2. **Rule (d) narrowing.** Check the literal segments of a JSON-slot element for metacharacters and exempt only the slot value. Element 0 and the path slots stay as they are.
   - Skills: pm-dev-java:java-core, pm-dev-java:junit-core
   - Persona: implementer
3. **The earliest-literal match and the presence check (ASK-AT-REFINE, R07).** Choose one approach and record it in a new ADR.
   - For `carried()`: anchor to the last literal, or compare against the composer's own value where the check already knows it (`{{model}}`, `{{cwd}}`), or refuse the ambiguous template shape at profile validation, which is where the shape is decided.
   - For presence: compute absence from the template alone, or state the dependence in ADR 010.
   - Recommendation: refuse adjacent slots at profile validation and compare against known values for `carried()`. About 55 % sure. Last-literal anchoring is ambiguous in general (two valid parses).
   - Production change in `WorkerVectorCheck`, and in `ProfileValidator` if the shape is refused at validation.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety, pm-dev-java:junit-core
   - Persona: implementer
4. **Documents.** Amend ADR `runtime-launch/010` (its Consequences and rule list), add one new ADR in `pm-runtime/doc/adr/runtime-launch/` (next free number at the head of main when the outline is written), and update the guide `runtime-launch.adoc` and, if validation changes, `doc/specification/harness-profiles/02-placeholders.adoc`.
   - Skills: pm-documents:ref-asciidoc, pm-dev-java:javadoc
   - Persona: documenter

**Done when:**
- Deliverables 1 to 4.
- The earliest-literal and JSON-literal tests fail at the post-#567 head of main with a failing run id. At 3ba79c4f the earliest-literal match (`carried()`, `indexOf` of the next literal) is OBSERVED in the code, so that one is claimable as "fails at 3ba79c4f".
- A shipped profile, composed per template shape (`fresh`, `warm`, `resumed`, `zero_tool`), still conforms (matched control). The existing `WorkerVectorCheckTest` and `LaunchComposerTest` keep their counts and stay green.
- Build: `verify -Ppre-commit`, `verify` of `pm-runtime` (and `pm-core` if `ProfileValidator` changes), `verify -Pcoverage`, `verify -Pintegration-tests -pl pm-runtime -am` (and `-pl pm-core -am` if `pm-core` changes). Use per-module runs with log ids if a full run exceeds the host's bound.
- Sonar: the open-finding read at the head is `0` or each key is selected for dismissal by the operator (`unknown` for a failed read, never `0`).

## Standing Constraints

- Starts after PR #567 merged. Claims on the branch are HYPOTHESIS until re-read.
- Read every cited line at the head first and drop what a merge settled (name the commit).
- A change that alters a rule of the specification is ask-at-refine (R07), once, in the outline's question.
- A refusal test asserts the reason and position, never only the type. Test data come from the generators. A literal stays only where it is the contract and the test says so.
- No new hand-written fake. No dependency or plugin is added. No more than 100 files per PR.
- Pre-1.0: no shim. No model-facing content.
- Sonar dismissal only on the operator's selection, through `sonar_rest transition`.

## Claim Labels

- OBSERVED: at 3ba79c4f `WorkerVectorCheck.carried` uses `rendered.indexOf(next.text(), cursor)` (`:288`) and returns empty when the next segment is a slot (`:283-285`). `named.length == 0` sits at `:334`. `SHELL_METACHARACTERS = "|;&$\`><"` (`:75`).
- OBSERVED: at 3ba79c4f rule (d) loops from element 0 and exempts the whole JSON-slot element (`:162-168`), and `keptIndices` copies the composer's fill (`:193`). `LaunchComposer.fill` is at `:426`.
- OBSERVED: ADR `runtime-launch/010` states the metacharacter exemption, the three-lists-stay-three rule and the fail-closed direction.
- HYPOTHESIS: on the 162 branch element 0 is exempt from rule (d) and the dead empty-split branch is removed. Confirm at `WorkerVectorCheck.java` after #567 merged.
- HYPOTHESIS: refusing adjacent slots at profile validation fits `ProfileValidator` (`PLACEHOLDER` at `:289`, slot check at `:1383-1424`). Confirm there.
- Verify-first clause: re-read `WorkerVectorCheck` on main after #567. A line moved or a limit already fixed loops back to re-scope.

## Expected Surface

11 existing files plus 1 new ADR.

- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/launch/WorkerVectorCheck.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/launch/LaunchComposer.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/launch/WorkerVectorCheckTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/launch/LaunchFixtures.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/launch/LaunchComposerTest.java`
- OBSERVED: `pm-runtime/doc/adr/runtime-launch/010-the-composed-vector-is-parsed-back.adoc`, `pm-runtime/doc/developer/runtime-launch.adoc`, `doc/specification/harness-profiles/02-placeholders.adoc`
- OBSERVED (only if the shape is refused at validation): `pm-core/src/main/java/de/planmarshall/core/profile/ProfileValidator.java`, `pm-core/src/test/java/de/planmarshall/core/profile/ProfileValidatorTest.java`, `pm-core/src/test/java/de/planmarshall/core/profile/ShippedProfilesTest.java`
- HYPOTHESIS: a new ADR in `pm-runtime/doc/adr/runtime-launch/`.

## Dependencies and Sequencing

- Depends on: PR #567 merged. This plan starts after it.
- Overlaps with: PLAN-V2-126's former D-numbers do not touch it. PLAN-V2-147 and 148 (the profile chain) may touch `ProfileValidator`; the gate decides.
- Adjacent to: `HarnessEnrolmentStore` and the enrolment guide (untouched).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-200-composed-vector-check-parser-limits.md
```

## Write-Boundary

The plan touches only its own repository source and tests. It creates and edits NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message. See `persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

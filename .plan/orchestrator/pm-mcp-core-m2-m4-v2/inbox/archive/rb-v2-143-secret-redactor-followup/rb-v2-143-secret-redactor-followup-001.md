envelope_version=1
sender_type=plan
sender_id=rb-v2-143-secret-redactor-followup
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-15T02:50:12Z
revision=1
amended=2026-11-15T12:17:38Z

# Landing: PLAN-V2-143 — the secret-redactor follow-up (PR #434, merged)

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-143-secret-redactor-followup
epic=pm-mcp-core-m2-m4-v2
pr=#434
merge_state=merged
cleanup_owed=false
deliverables_total=4
deliverables_done=4
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2125
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=14
review_body_findings_handled=14
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced)

## Deliverables

Four deliverables, each with its own base commit, plus review-fix commits. Branch `fix/v2-143-secret-redactor-followup`,
merged into `main` as **`9c961d58780b96f1c0618a9e23281d7823304ff8`** (PR #434; #431 was closed unmerged and re-created on
the operator's word when the required reviewer was quota-limited there).

### D1 — the PEM pattern is linear (`d251ebff`)

- Done-when: a 400 000-character text of `BEGIN` markers redacts within the bound, and the test fails on the old
  pattern — **met**, `SecretRedactorTest#pemBeginMarkersWithoutEndAreLinear`, order **test-first**, failing run at base
  `1e316517` (build log `0dfb23dc57e64967a67b8341155cc578.log`, "execution timed out after 5000 ms",
  `SecretRedactorTest.java:412`; artifact `.plan/temp/rb-v2-143-secret-redactor-followup/d1-cost-test-failing.log`).
- Done-when: the 1 000 000-character quoted value raises no `StackOverflowError` — **met**,
  `#quotedFlagValueOfOneMillionCharacters`, order **none** (a guard: the possessive loop was already iterative; no failing
  run exists to report). The second cost test (3 700 markers) passes.

### D2 — the credential shapes that slip through (`aa82607e`, plus review fixes)

- Done-when: `https://:token@host` is redacted and a single-user URL without a colon is not — **met**,
  `#urlUserInformationEmptyUser` plus the unchanged control `#urlUserInformationNegativeControls`, order
  **test-first**, failing run at `d251ebff` (log `51ac83fb4cc343adbdefac6d43884ec4.log`).
- Operator decisions implemented: Unicode whitespace and invisible-character widening; a quoted assignment closes on its
  line, a refused close masks the rest of its line, a cross-line close is a documented residue. Tests:
  `#shortQuotedAssignment`, `#assignmentWithNonAsciiSpace`, `#assignmentWithInvisibleCharacters`,
  `#unclosedQuotedAssignmentIsMasked`, `#escapedQuoteIsNoCloseInAnAssignment`,
  `#closeFollowedByAWordCharacterIsUnclosed`, `#quotedAssignmentDoesNotSwallowALaterLabelOnItsLine`,
  `#quotedAssignmentDoesNotSwallowAPunctuationLeadingValue`, `#quotedAssignmentClosingOnTheNextLineIsUnclosed`,
  `#crossLineQuotedValueDoesNotSwallowTheNextLabel`, `#enclosingQuotedAssignmentSurvivesAnInnerLabel`,
  `#urlIsMaskedBeforeAnInnerAssignment`, `#credentialFlagWithNonAsciiSpace` — order **test-first** across the review
  rounds (failing logs `51ac83fb…`, `48065712…`, `62f2e7dc…`, `ae785761…`, `133…`).

### D3 — the diagnostics stored unredacted (`ffbece71`)

- Done-when: every parser's stored message passes through the redactor, a test per parser family — **met**,
  `DiagnosticCollectorRedactionTest` with eight families (TAP error, TAP stack, Jest, ESLint, TypeScript, Maven, Gradle,
  pyproject), order **test-first**, failing run at `aa82607e` (log `e43621643e354a838c55a22d7e593458.log`).
- `ProviderVerification` answer bounded, with the cut-tail URL/token masks; tests `#answerIsBounded`,
  `#urlHeadCutByTheBoundIsMasked`, `#urlHeadWithInternalAtCutByTheBoundIsMasked`,
  `#tokenTailCutByTheBoundIsMasked`, order **test-first** (logs `dda41391…` and later).
- Verify-first: the sibling checkout `plan-marshall-mcp` has **no job-log resource read and no `SecretRedactor`
  reference at all** (operator-authorized read-only check); the clause is vacuous and is reported here as the spec's
  finding, not fixed.

### D4 — the tests that cannot fail, and the wrong statements (`fcc8074f`)

- Done-when: the two parts-wrap tests are gone or can fail — **met** by deletion (`JestOutputParserTest#partsWrapping…
  ToTheBlockTotalAreUnmeasured`, `TapOutputParserTest#partsWrappingToTheTotalAreUnmeasured`), order **none** (a
  deletion; the removed assertions are entailed by the unreachable path).
- Done-when: ADR 009 is true at the named lines — **met** (head-residue corrected, the point shape, the 2^58
  statement, the LCOV/Maven Javadocs).
- The three small corrections: `CoverageRefusals` keeps the kind, `BuildLogText.boundLines` guards a surrogate pair,
  `TapOutputParser` anchors the point shape; fail-first runs at `ffbece71` (`094898e7…`, `652d1552…`, `df30858c…`).
  Review rounds added the reason bound, the blank-after-redaction fallbacks, the enclosing-span order and the TAP
  separator.

### Cross-cutting Done-when clauses

- The four build commands, re-run at the merge head `36ef9bed`: `verify -Ppre-commit`, `test`, `verify`,
  `verify -Pcoverage` and `verify -Pintegration-tests -pl pm-core -am` — all green; logs `d6247f67…`, `017542c2…`,
  `abda1069…` (10 589), `966c0830…`, `59fbf531…` (21). No rewrite was left to commit.
- Sonar: `0` confirmed open new-code issues at `36ef9bed`; the quality gate `OK`; `sonar-gate` `merge_allowed: true`.

## Personas and skills

- Persona per deliverable: `persona-implementer` for D1–D3 with `persona-security-expert` as the lens for the
  secrets/trust-boundary work; `persona-documenter` plus `persona-implementer` for D4; `persona-code-reviewer` for
  every self-review round.
- Skills: D1 `java-core`, `junit-core`, `cui-testing`, `java-null-safety`, `javadoc`; D2 `java-core`, `junit-core`,
  `cui-testing`; D3 the same plus `java-null-safety`, `javadoc`; D4 the same plus `pm-documents:ref-asciidoc`.
  `java-lombok` read under R20. No skill was added for surface drift.

## Hypotheses

- "A lazy quantifier under `(?s)` with a literal prefix is quadratic" — **confirmed** by the test-first measurement
  (`SecretRedactor.java:203-204` at base `1e316517`).
- "The JDK keeps the possessive loop iterative" — **confirmed**; no stack overflow at 1 000 000 characters.

## Findings

- Self-review rounds 1–5 plus a confirmation round; findings 12 → 8 → 3 → 4 → 1 → 0, all fixed
  (`0e843bb5`, `87a5c477`, `d471b996`, `e3cbcf06`, `93be86af`); **self-review: clear**.
- CodeRabbit reviewed four times on #434 (the last two after the merge and the operand fixes). 14 body findings, all
  handled: six in `f96675a1`/`6c0abea4`, six in `d4d3d825`, two in `36ef9bed`; one duplicate with no separate fix.
  Every inline thread was replied to with its commit and resolved.
- Sourcery (optional) refused on quota every time; it never gated.
- Sonar: `java:S1192` (6, `c578cee6`), `java:S5850` (1, `0907fb6f`/`d9a4eae3`), `java:S5869` (1, `d9a4eae3`), all fixed;
  `0` open at the merge head.

## Reviewers

| Reviewer | Required | Result | Head reviewed | History |
|---|---|---|---|---|
| coderabbit | yes | **reviewed** (`COMMENTED`, qualifying review with `coveredCommitId`/`kind=reviewed`) | `c578cee6`, `55e53d21`, `d9a4eae3` | quota-refused three times on #431 (90-minute waits); #431 re-created as #434 per the operator; reviewed there, then re-reviewed the later heads; every finding handled |
| sourcery | no (optional) | **refused (quota)** | — | own budget reset in ~1 day |

## Operator questions

1. The executor skew (0.1.2118 vs 0.1.2123) — options {Regenerate, then resume; Stop as blocked} — selected
   **Regenerate, then resume**; the check then answered `match`.
2. The resolve failure on the spec's annotated `Persona:` lines — options {You amend the spec; Authorize me to amend;
   Stop as blocked} — free-text answer **"It was updated, re-read"**; the operator amended the spec.
3. D2(b) the ASCII-only matcher — options {Do not widen; record it (recommended); Widen the matcher to Unicode} —
   selected **Widen the matcher to Unicode**.
4. D2(c) the short quoted assignment — options {(1) runs to its close (recommended); (2) keep the minimum; (3) widen
   for three labels} — selected **(1)**.
5. D3 the package cycle — options {Record it in ADR 009 (recommended); Move the redactor below both packages} —
   selected **Record it in ADR 009**.
6. The sibling-checkout verify-first read (the lane's stay-inside rule) — options {Authorize the one read-only check
   (recommended); Skip it and record the residue} — selected **Authorize the one read-only check**.
7. The self-review round limit — options {Run a fourth verification round (recommended); Report budget-exhausted} —
   selected **Run a fourth verification round**; rounds 4–6 ran until a round answered 0.
8. The reviewer quota block on #431 — the operator answered **"try again: re-create pr"** (free text), which selected
   the quota policy's new-PR step: #431 closed unmerged, #434 opened from the same branch and the reviewers re-triggered.

## Rule check

- `guard: none on this harness (unenforced)` — no guard log exists for this session tree and no "all kept" claim is
  made.
- Spec read: `spec_source: ledger_worktree`, no `spec_differ` marker at setup and at landing time.
- `executor-skew`: **skew** at start → blocked; the operator chose regeneration; the re-check answered **match**.
- Rules bent on the operator's explicit selection: (1) the stay-inside-the-repository rule for the one read-only
  sibling-checkout check; (2) the three-round self-review limit (rounds 4–6); (3) the quota policy's waiting cadence,
  replaced by the operator's new-PR instruction. All are recorded rather than hidden.
- One failed `verify` run at `93be86af` exited 1 with no surefire failure in any module and re-ran green (a transient,
  non-test failure). One `verify -Ppre-commit` at `87a5c477` hit the daemon's 330 s bound (`timeout_used_seconds=330`,
  `timeout_source=explicit`) and was re-run as the split form with `--timeout 600`, green.
- Every other shell action used one command per call, file tools for reads, `.plan/temp/` for temp files, the build
  seam for every build and `ci` for every CI action; no stash/restore/rebase/force-push.

## Build server

- Setup preflight `ready`. Every build reported `daemon_longpoll` (no in-process fallback, no launch-fault retry) —
  except none fell back; the one bound expiry is above.
- `manage-build-server status` reported `binary_diverges: true` (a stale running daemon binary), a machine-level
  observation recorded and not acted on by this lane.

## Residue

- Spec findings for the orchestrator: the head-residue justification ("inert because the stored message is cut to 500")
  was refuted and the ADR now states the bounded-but-present residue; the D3 job-log verify-first clause has no object
  in `plan-marshall-mcp`; the D2(c) cross-line quoted value is a retained residue rather than the flag-form parity the
  wording implies; the D2(b) widening was chosen over the recommendation and is recorded in the ADR.
- Surface delta (R19): `Diagnostic.java` edited although the Expected Surface named only `DiagnosticCollector` and
  `NpmErrorParser` — the stored seam cannot reach the collected `stackTrace`, so the model's constructor redacts it.
  Also listed: `DiagnosticCollectorRedactionTest.java`, `BuildLogTextSurrogateBoundTest.java`, `CoverageRefusalsTest.java`,
  `ProviderVerificationTest.java`, `pm-runtime/doc/developer/runtime-credentials.adoc`.
- `F9` (the dedup key computed from the redacted message) was accepted by design and not changed.
- Mailbox: `no_mailbox` / `never_delivered` at all three reads; that is an unanswered mailbox, not an empty one.

Standing rules: **r7**; rules applied R02, R05, R06, R07, R08, R09, R11, R17, R18, R19, R20.

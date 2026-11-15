envelope_version=1
sender_type=plan
sender_id=rb-v2-143-secret-redactor-followup
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-15T02:50:12Z

# Landing: PLAN-V2-143 — the secret-redactor follow-up (PR #431, stop before merge)

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-143-secret-redactor-followup
epic=pm-mcp-core-m2-m4-v2
pr=#431
merge_state=open
cleanup_owed=false
deliverables_total=4
deliverables_done=4
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2125
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:blocked,merge:pending
unresolved_review_threads=0
review_body_findings_total=0
review_body_findings_handled=0
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced)

## Deliverables

All four deliverables are committed, one commit each at the base of the branch, with review-fix commits after them.
Branch `fix/v2-143-secret-redactor-followup`, head `c578cee6`.

### D1 — the PEM pattern is linear (`d251ebff`)

- Done-when: a 400 000-character text of `BEGIN` markers redacts within the bound, and the test fails on the old
  pattern — **met**, `SecretRedactorTest#pemBeginMarkersWithoutEndAreLinear`, order **test-first**, failing run at base
  `1e316517` (build log `0dfb23dc57e64967a67b8341155cc578.log`, "execution timed out after 5000 ms",
  `SecretRedactorTest.java:412`; artifact `.plan/temp/rb-v2-143-secret-redactor-followup/d1-cost-test-failing.log`).
- Done-when: the 1 000 000-character quoted value raises no `StackOverflowError` — **met**,
  `#quotedFlagValueOfOneMillionCharacters`, order **none** (a guard: the possessive loop was already iterative; no
  failing run exists to report). The second cost test (3 700 markers) passes; ADR 009 and the `core.queue` guide are
  corrected.

### D2 — the credential shapes that slip through (`aa82607e`, plus review-fix commits)

- Done-when: `https://:token@host` is redacted and a single-user URL without a colon is not — **met**,
  `#urlUserInformationEmptyUser` plus the unchanged control `#urlUserInformationNegativeControls`, order
  **test-first**, failing run at `d251ebff` (log `51ac83fb4cc343adbdefac6d43884ec4.log`).
- Operator decisions implemented: the matcher widened to Unicode whitespace and zero-width characters; a quoted
  assignment closes on its own line (a refused close masks the rest of the line; a cross-line close is a documented
  residue). Tests: `#shortQuotedAssignment`, `#assignmentWithNonAsciiSpace`,
  `#assignmentWithInvisibleCharacters`, `#unclosedQuotedAssignmentIsMasked`, `#escapedQuoteIsNoCloseInAnAssignment`,
  `#closeFollowedByAWordCharacterIsUnclosed`, `#quotedAssignmentDoesNotSwallowALaterLabelOnItsLine`,
  `#quotedAssignmentClosingOnTheNextLineIsUnclosed`, `#crossLineQuotedValueDoesNotSwallowTheNextLabel`, order
  **test-first** across review rounds (failing logs `51ac83fb…`, `48065712…`, `62f2e7dc…`, `ae785761…`).

### D3 — the diagnostics stored unredacted (`ffbece71`)

- Done-when: every parser's stored message passes through the redactor, a test per parser family — **met**,
  `DiagnosticCollectorRedactionTest` with eight families (TAP error, TAP stack, Jest, ESLint, TypeScript, Maven,
  Gradle, pyproject), order **test-first**, failing run at `aa82607e` (log `e43621643e354a838c55a22d7e593458.log`).
- `ProviderVerification` answer bounded; test `ProviderVerificationTest#answerIsBounded`, order **test-first**
  (log `dda413918b194b06966788de6c5a8bee.log`).
- Verify-first: the sibling checkout `plan-marshall-mcp` has **no job-log resource read and no `SecretRedactor`
  reference at all** (operator-authorized read-only check); the clause is vacuous and is reported here as the spec's
  finding, not fixed.

### D4 — the tests that cannot fail, and the wrong statements (`fcc8074f`)

- Done-when: the two parts-wrap tests are gone or can fail — **met** by deletion
  (`JestOutputParserTest#partsWrappingToTheBlockTotalAreUnmeasured`,
  `TapOutputParserTest#partsWrappingToTheTotalAreUnmeasured`), order **none** (a deletion; the removed assertions are
  entailed by the unreachable path).
- Done-when: ADR 009 is true at the named lines — **met** (the head-residue justification was corrected twice after
  review; the point-shape gloss, the 2^58 statement and the LCOV/Maven Javadocs are fixed).
- The three small corrections: `CoverageRefusals` keeps the kind (test reworded, order **test-first**),
  `BuildLogText.boundLines` guards a surrogate pair, `TapOutputParser` anchors the point shape. Fail-first runs at
  `ffbece71`: `094898e7…`, `652d1552…`, `df30858c…`.

### Cross-cutting Done-when clauses

- The four build commands: **met at the final head `c578cee6`** — `verify -Ppre-commit` 10 499 tests
  (`7df9b2c7…`), `test` 10 499 (`91f06b2f…`), `verify` 10 499 (`1b639817…`), `verify -Pcoverage` 10 499
  (`1aae802e…`), `verify -Pintegration-tests -pl pm-core -am` 21 (`b6e06f75…`); no rewrite was left to commit.
  One earlier `verify` run failed transiently (exit 1, no surefire failure in any module) and re-ran green.
- Sonar: **met** — the PR's open new-code issues are `0` confirmed, the quality gate is `OK` (new coverage 97.3 %,
  duplication 0.0 %), read at `c578cee6`; `sonar-gate` answered `merge_allowed: true`.

The build gate is `deliverables_done = 4`; the merge gate is **not** met (see Reviewers).

## Personas and skills

- Persona per deliverable: `persona-implementer` for D1–D3 (production code and tests) with
  `persona-security-expert` loaded as the lens for the secret/trust-boundary work; `persona-documenter` plus
  `persona-implementer` for D4. `persona-code-reviewer` for every self-review round.
- Skills: D1 `java-core`, `junit-core`, `cui-testing`, `java-null-safety`, `javadoc`; D2 `java-core`, `junit-core`,
  `cui-testing`; D3 the same plus `java-null-safety`, `javadoc`; D4 the same plus `pm-documents:ref-asciidoc`.
  `java-lombok` was read under R20. No skill was added for surface drift.

## Hypotheses

- "A lazy quantifier under `(?s)` with a literal prefix is quadratic" — **confirmed** by the test-first measurement
  (the 400 000-character input timed out at 5 s on `SecretRedactor.java:203-204` at base `1e316517`).
- "The JDK keeps the possessive loop iterative" — **confirmed**; no stack overflow at 1 000 000 characters.

## Findings

- Self-review rounds 1–5 plus a confirmation round. Round 1: 12 findings (pyproject test, the false head-residue
  claim, three stale Javadocs, the unclosed-quote leak, the cycle record) — fixed in `0e843bb5`. Round 2: 8 (the gate
  at HEAD, ADR item 5(b), the point-shape gloss, the cross-line gap, the residue bound, the seam wording) — fixed in
  `87a5c477`, and the gate re-run. Round 3: 3 (the cross-line swallow, a Javadoc, ADR R5) — `d471b996`. Round 4: 4
  (the lookahead design, idempotency, docs, the gate) — `e3cbcf06`, where the unclosed pass moved before the closed
  pass and the assignment close became same-line. Round 5: 1 (a flag-residue sentence that could not occur) —
  `93be86af`. Confirmation round: **OPEN FINDINGS: 0**; `self-review: clear`.
- PR comments: none from a human; Sourcery's only comment is its quota refusal.
- Sonar: six `java:S1192` duplicated-literal issues on the changed `SecretRedactor`, all fixed in `c578cee6` (one
  label source, one shared prefix); re-read: 0.

## Reviewers

| Reviewer | Required | Result | Head | Trigger / wait history |
|---|---|---|---|---|
| coderabbit | yes | **refused (quota), no review** | — | triggered 23:16Z on `93be86af`; refused 23:16:50Z; re-triggered 00:52Z (after the 90-minute wait), refused 00:50:45Z; re-triggered 02:26Z, refused 02:25:24Z; next allowed trigger 03:55:24Z |
| sourcery | no (optional) | **refused (quota)** | — | triggered 23:17Z; refused 23:17:03Z with its own budget reset in ~1 day 11 h |

The standing review authorization does not apply: no qualifying review exists, so the quota policy is the only route,
and the run stops inside it rather than merging without the required review.

## Operator questions

1. The executor skew (0.1.2118 vs lane 0.1.2123) — options {Regenerate, then resume; Stop as blocked} — selected
   **Regenerate, then resume**; `generate_executor preflight` regenerated it and the check answered `match`.
2. The resolve failure on the spec's annotated `Persona:` lines — options {You amend the spec; Authorize me to amend;
   Stop as blocked} — answered with free text **"It was updated, re-read"**; the spec was amended by the operator and
   resolve then succeeded.
3. D2(b) the ASCII-only matcher — options {Do not widen; record it (recommended); Widen the matcher to Unicode} —
   selected **Widen the matcher to Unicode**.
4. D2(c) the short quoted assignment — options {(1) runs to its close (recommended); (2) keep the minimum; (3) widen
   for three labels} — selected **(1)**.
5. D3 the package cycle — options {Record it in ADR 009 (recommended); Move the redactor below both packages} —
   selected **Record it in ADR 009**.
6. The sibling-checkout verify-first read (the lane's stay-inside rule) — options {Authorize the one read-only check
   (recommended); Skip it and record the residue} — selected **Authorize the one read-only check**.
7. The self-review round limit — options {Run a fourth verification round (recommended); Report budget-exhausted} —
   selected **Run a fourth verification round**; two further rounds were run until the confirmation round answered 0.

## Rule check

- `guard: none on this harness (unenforced)` — the resolve result reported `guard: none`, so no guard log exists for
  this session tree and no "all kept" claim is made.
- Spec read: `spec_source: ledger_worktree`, no `spec_differ` marker at setup and at landing time.
- `executor-skew`: **skew** at start (executor 0.1.2118 < lane 0.1.2123) → the run was blocked; the operator chose
  regeneration; the re-check answered **match** (0.1.2123). The landing-time resolve reports executor_version 0.1.2125
  from the platform seam.
- Two rules were bent **on the operator's explicit selection**: (1) the stay-inside-the-repository rule for the one
  read-only sibling-checkout check; (2) the three-round self-review limit (rounds 4–5 and a confirmation round were
  run). Both are recorded here rather than hidden.
- Every other shell action used one command per call, file tools for reads, `.plan/temp/` only for temp files, the
  build seam for every build, and `ci` for every CI action. No `git stash`/`restore`/`rebase`/force-push was used.

## Build server

- Setup preflight: `ready`. Every build ran `daemon_longpoll` (no `in_process_fallback`, no launch-fault retry).
- One bound expiry: the `87a5c477` `verify -Ppre-commit` came back `timeout` (`timeout_used_seconds=330`,
  `timeout_source=explicit`); the split form was re-run with `--timeout 600` and was green. One transient `verify`
  failure at `93be86af` (no test failure) re-ran green.
- No `killed` job. The setup resolve also reported `binary_diverges: true` on the daemon (a stale running binary), a
  machine-level observation recorded by `manage-build-server status` and not acted on by this lane.

## Residue

- **PR #431 is open and not merged.** The only blocker is the required reviewer: CodeRabbit is quota-limited, three
  attempts were made, and the next allowed trigger is 2026-11-15T03:55:24Z. The merge gate's `review-gate` answered
  `merge_allowed: false` (`required_review_missing`); every other condition holds (CI green, Sonar clean, gate green,
  no unresolved threads, no review-body findings).
- The worktree and branch are kept for the open PR; no base-evidence tree was created, so `cleanup_owed` is false.
- Spec findings for the orchestrator: the head-residue justification ("inert because the stored message is cut to 500")
  is refuted by the code and corrected in ADR 009; the D3 job-log verify-first clause has no object in
  `plan-marshall-mcp`; the D2(c) cross-line quoted value is a retained residue rather than the flag-form parity the
  spec's wording implies.
- Surface delta (R19): `Diagnostic.java` was edited although the Expected Surface names only `DiagnosticCollector` and
  `NpmErrorParser` — the stored seam cannot reach the collected `stackTrace`, so the model's constructor redacts it.
- Mailbox: `no_mailbox` / `never_delivered` at all three reads (setup, before self-review, before the enqueue); that
  is an unanswered mailbox, not an empty one.

Standing rules: **r7**; rules applied R02, R05, R06, R07, R08, R09, R11, R17, R18, R19, R20.

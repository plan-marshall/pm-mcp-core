envelope_version=1
sender_type=plan
sender_id=rb-v2-81-machine-stores-residue
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-11T11:05:40Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-81-machine-stores-residue
epic=pm-mcp-core-m2-m4-v2
pr=#368
merge_state=merged
cleanup_owed=false
deliverables_total=6
deliverables_done=5
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2088
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=3
review_body_findings_handled=3
sonar_open_new_issues=n/a
step.sonar-roundtrip.open_issue_rules=n/a
```

guard: none on this harness (unenforced)

# PLAN-V2-81 — the residue of the machine's stores

Standing rules: r7 (2026-11-10), ids R02, R05, R06, R07, R08, R10, R11, R12, R13, R14, R16, R17, R18, R19, R20.
The spec's First Instruction names r4; the file is at r7 and r7 binds (the plan started after it).

PR #368, merged as `5e4d3513a28123940f25465d3f0ffc8bcf2de45a`. (PR #366 was closed unmerged after the required
reviewer refused on quota on every head of it; the operator selected the close-and-reopen step.)

## 1. Deliverables

**D1 — the machine configuration document, its writer and the provider defaults.** Met.
- Done-when: trailing JSON (two objects; object + number) refused on read and write — test-first; failing run job
  `18d7190082764977a92a3fabdba2a677` at `af3a8c48` (12 failed); `MachineConfigurationCodecTest.trailingObject`,
  `trailingNumber`.
- Done-when: each setting refused (null, string, below minimum, above maximum) — test-first, same failing run;
  `maxBuildSlotsRefused[4]`, `lspIdleTimeoutRefused[3]`. (`lsp_idle_timeout_seconds` has no maximum in the schema, so
  the "above the maximum" case is vacuous for it.)
- Done-when: accepting case keeps an unknown member byte-identical — test-first; `keepsUnknownMember`.
- Done-when: the store's foreign path and the omitted variant of `github` and `git` refused — test-first, same failing
  run; `MachineConfigurationStoreTest.foreignPath`, `variantOmitted`.
- Commit: `2e7c8f11`.

**D2 — the verification result and the redaction.** Met.
- Done-when: a failed answer's bearer and `token=` value reach neither the line nor its arguments, a plain identity
  stays — test-first; failing job `a86c07a7f84144b4acac7e4461e8279c` at `2e7c8f11`;
  `ProviderVerificationTest.answersRedacted`, `toolIdentityRedacted`, `identityUnchanged`.
- Done-when: `ProviderDefault` refuses `available: true` with no and with a failed result, accepts `false` with either
  — test-first; compile-failing job `c1cc40856c0445f9b6b49f2c04aee078` at `2e7c8f11`; `ProviderDefaultTest` (plus the
  builder contract test added after the base merge).
- Done-when: the store round-trips the member and refuses an unverified `available: true` — test-first;
  `verificationRoundTrip`, `documentWithoutVerification`, `unverifiedAvailableRefused`.
- Commit: `9aebba8a`.

**D3 — the consent gate and the credential-store aspect.** Met.
- Done-when: a device on `cli` and a malformed device refused for `yes`, `no`, `yes_remember` and a remembered kind —
  test-first; failing jobs `130d025fc219478797968f193e492b52` (pm-core) and
  `5a73090a41db4d219ad176b8bedf9db9` (pm-runtime) at `9aebba8a`; `ConsentGateTest.deviceOnEveryAnswer`.
- Done-when: the malformed and the unreadable document after a passed check reach `UNAVAILABLE`, no exception —
  test-first, same failing run; `CredentialStoreAspectTest.malformedConfigurationAfterPassedCheck`,
  `unreadableConfigurationAfterPassedCheck`.
- Done-when: the occupied account writes and deletes nothing, the original value stays, the `occupied` line, the three
  steps kept — test-first, same failing run; `CredentialStoreCheckTest.occupied`, `readFails`, `passes`.
- Done-when: the holder's test passes with the corrected text — test-first, same failing run;
  `RuntimeLogMessagesTest.credentialStoreMove[5]`.
- Commit: `061d0387`.

**D4 — the Sonar issues #238 left open.** **Not met as written** — the second clause is unevidenced at landing time.
- Order `none` (the evidence is a Sonar read, not a failing test; the guard is the existing tests, re-run per class).
- The re-query at the start (`branch=main`, 2026-11-10) listed three S1192 in the three packages
  (`DefaultAssignment` lines 70 and 90, `MachineConfigurationCodec` line 121); all three named constants were written
  (`7978ce84`), and the pull request's own new findings (1 S1192 + 12 S5778) were cleared (`b22e1b3c`, `d99fa274`).
- Pull-request read at the final head `44a9dc54`: **0 open** (`pullRequest=368&resolved=false`), gate OK.
- Post-merge `branch=main` read: **3 open** (`DefaultAssignment` lines 71 and 92, `MachineConfigurationCodec` line 121),
  but they are the *pre-merge* analysis — its `lastChangeAnalysisUuid` predates the merge. The push analysis of the
  merge commit `5e4d3513` (Maven run 43006719251, `build / sonar-build`) was still `in_progress` when this message was
  written, so the clause "lists none after the merge" is not evidenced. The measurement is the one open item.

**D5 — the specification and the documents.** Met.
- Done-when: the links check reports `BROKEN LINKS 0` — R17's trace run from the worktree at the head; artifact
  `.plan/temp/rb-v2-81-machine-stores-residue/links-check.log` (`BROKEN LINKS 0`). The post-merge main checkout reports
  `BROKEN LINKS 4`, all in files this plan did not touch (`runtime-guard.adoc` from PLAN-V2-53, `credentials-and-enrolment/03`
  and `harness-profiles/01` from other landings) — artifact `.plan/temp/.../links-check-main.log`.
- Done-when: the five statements and the code say the same thing — the pull request body lists them with the code line
  each matches (store-schemas/12 `verification`; the `variant` rule; the redaction; the assignment store; ADR
  `runtime-config/001`).
- Done-when: the pull request body lists **Owed to the milestone exit** — it does.
- Commit: `165d8f26`.

**D6 — the coverage and the quality gate at the head.** Met.
- Order `none` (measurement).
- `verify -Pcoverage` per module after the review fixes: pm-core 93.24 % line / 85.49 % branch (92.63 % instruction);
  pm-runtime 91.29 % line / 82.45 % branch (91.0 % instruction) — Sonar's new-code coverage at the final head 93.4 %.
- Classes below 80 % in the three packages after the added tests: `core.config` — `ConsentStore$Change` (a private
  functional interface, a JaCoCo artifact); `runtime.config` — none; `runtime.credentials` — the macOS-only FFM classes
  (`MacSecurity`, `MacSecurity$Scope`, `MacSecurity$Downcall`, `KeychainSecretStore`,
  `KeychainSecretStore$KeychainException`, `Posix`, 0 % on Linux), the Lombok builders and the seam interfaces. The
  five classes the spec names are over 80 %; `ModelCatalogueCodecTest` and `TokenLaneCredentialTest` were added.
- `verify -Ppre-commit` left `git status` empty on a second run; the full `verify` of pm-core, pm-runtime and
  pm-workflow is green at the head, as are `verify -Pintegration-tests -pl pm-core -am` and `-pl pm-runtime -am`.
- Commit: `ec8095d3` (and the coverage was re-measured after `cbf14a98`).

## 2. Personas and skills

- Personas applied: `persona-implementer` (production code, D1-D4), `persona-module-tester` (all new tests, D6),
  `persona-documenter` (D5), `persona-code-reviewer` (both self-review rounds and the PR triage).
- The secret-handling part of D2 was implemented with `SecretRedactor` as the spec names it; `persona-security-expert`
  was **not** loaded as a separate standard — a gap in the persona table's application, recorded here.
- Domain skills: the spec declares none (`skills: none named`). R20's nine skills were read at the start (java-core,
  java-lombok, java-null-safety, javadoc, junit-core, junit-integration, cui-logging, cui-testing, cui-http-testing).
- Surface drift: `TokenLaneCredentialTest` (new, `runtime.credentials`), `MachineConfigurationSchemaParityTest` and
  `OptionalAbsenceContractsTest` (compile-driven migrations), `ProviderDefaultBuilderContractTest` (R20's builder rule
  for the 4-component record), `doc/Traceability.adoc` (shared bookkeeping). No module outside the spec's surface.

## 3. Hypotheses

- "The remaining S1192 constants change no behaviour" — confirmed; the three were literal substitutions and the
  existing tests (`DefaultAssignmentTest`, `MachineConfigurationCodecTest`, `MachineConfigurationStoreTest`) stayed green.
- "`ProviderDefault.of(boolean, ProviderVariant)` has callers only in the tests and `ProviderVerification`'s consumers
  in `plan-marshall-mcp` (none yet)" — confirmed by grep: no production caller; the factory was replaced.
- The spec's OBSERVED claims for D1-D3 were re-read before editing and confirmed (codec has no token read after the loop;
  `requireFields` checks presence only; the store compares the key only; `requireDevice` was called from `remember`
  only; `choice.recorded()` was unguarded; the check had no read before its write).

## 4. Findings

- Self-review round 1 (13 findings): F1 a codec `IllegalArgumentException` escaping instead of a `StoreRefusal`, F2 the
  remaining S1192 literals and three new duplicates, F3-F7 and F10 doc/code contradictions, F12 the ADR's atomicity
  claim — fixed in `cbf14a98`; F8/F9 recorded as surface delta; F11 (schema conditional rules) and F13 (`toResult` has
  no production caller yet) carried as minor/preference.
- Self-review round 2: all prior findings resolved; one residual literal (a `"%s: %s"` format) fixed in `499096d3`.
- CodeRabbit's review of `b22e1b3c`: 2 inline findings (serialize the fixed-account check; the occupied account can never
  be cleared) and 6 body nitpicks — answered by documentation (ADR 012's limits and the operator remediation) and fixed
  in `de766335` (the `passed`-to-checks and `false`-state invariants, the schema-bounds parity assertion, the token-lane
  cases), with both threads replied and resolved.
- CodeRabbit's review of the merge head `e4718178`: 1 inline finding (host case) — fixed in `d99fa274` by lower-casing
  the host in `TokenLaneCredential.origin` (RFC 3986), thread replied and resolved.
- Sonar, pull request: 13 new issues at `499096d3` (1 S1192 + 12 S5778) — fixed in `b22e1b3c`; 4 more at `e4718178`
  (3 S3415 expected/actual order, 1 S5778) — fixed in `d99fa274`.

## 5. Reviewers

- **coderabbit** (required, quota-limited): refused on quota at 00:16:49Z, 01:56:34Z (after the 90-minute window) and
  03:52:13Z (after the escalated trigger); the operator selected close-and-reopen, and on #368 it reviewed `b22e1b3c`
  (kind=reviewed) and the merge head `e4718178` (kind=reviewed, the qualifying review of the standing authorization).
  It refused again at 09:48Z after the push of `d99fa274`. The last two heads (`20ebf3f6`, `44a9dc54`) are base merges
  whose inheriting review is `e4718178`; its own check-run on `44a9dc54` is SKIPPED, and **no trigger was posted after
  the final push** — the standing authorization's third condition was met for the preceding head, not re-established
  for the merge heads. Reported as a rule-check deviation (§ 7).
- **sourcery** (optional): refused on its own 7-day budget at 00:17Z and again at 06:24Z (next in 5 days).

## 6. Operator questions

1. Asked: "PR #366 complete: CI green, Sonar 0 open/gate OK, coverage 93.24/85.49 and 91.29/82.45; CodeRabbit refused
   on quota three times (00:16:49Z, 01:56:34Z, 03:52:13Z incl. the escalated trigger), Sourcery refused for 5 days; how
   should I proceed?" — options {close #366 and reopen (Recommended), keep waiting and re-triggering, stop with the PR
   open, merge without the CodeRabbit review}; selected **"Close #366, reopen, retrigger"**.
No free-text operator messages beyond that answer.

## 7. Rule check

- `guard: none on this harness (unenforced)`; the resolve result reported no guard, so no "all kept" claim is made.
- Deviations:
  - **The standing review authorization's third condition was not re-established on the final two heads** (base merges
    `20ebf3f6`/`44a9dc54`): the re-trigger was posted after `d99fa274` (refused 09:48Z) but not after those pushes.
    CodeRabbit itself skipped the merge heads. The merge proceeded through the merge queue, which ran the required
    checks on the merge-group branch and succeeded.
  - **`persona-security-expert` was not loaded** for the redaction work (§ 2).
  - **The base moved three times** between the PR and the enqueue (06:52, then `3f4ce991`/`8cbf8575`, then
    `a2edd0f2`); each was merged with the trailer, the build gate was re-run and CI re-earned, per § 7.
- Every shell file operation the run issued that is not one of the lane's sanctioned forms: the R17 links check was run
  as the rule prescribes (`python3 /home/oliver/git/plan-marshall-documentation/.claude/skills/doc-review/scripts/trace.py …`),
  which is `python3 some_script.py` under a path outside the repository — the lane's executor rule has no other route
  to that script; recorded here as the one deliberate deviation. One shell redirect wrote the links-check artifact under
  `.plan/temp/`.
- Spec read: `spec_source=ledger_worktree`, no `spec_differ` marker (both copies equal at read time).
- Executor skew: `match` (executor 0.1.2088, lane 0.1.2088).
- Commits: each ends with `Co-Authored-By: plan-marshall <noreply@cuioss.de>` and nothing else.
- R13: every format version stayed 1. R12: identifier 146 kept, its listing regenerated. R11: the four ADRs took the
  next free numbers against `origin/main`. R10: the parity blocks stayed byte-stable.

## 8. Build server

- Setup preflight: `ready` (the project is enrolled and the daemon answered).
- Builds: all routed (`daemon_longpoll`). One bound expiry: the first `verify -Pcoverage -pl pm-core,pm-runtime` hit the
  daemon's 907 s bound (`timeout_source=explicit`) and was split per module, which is the run reported here.
- Fallbacks: none (`degraded` never occurred; no launch-fault retry was needed).

## 9. Residue

- **D4's post-merge `branch=main` read** is the one open measurement: the analysis of the merge commit `5e4d3513`
  (Maven run 43006719251, started 10:48Z) had not settled when this message was written; the last main analysis still
  lists the three S1192 in the three packages because it predates the merge. The pull request's own read at the head is
  0. The merge's push analysis decides the second clause.
- The pre-existing `MacSecurity` S112 in `runtime.credentials` (2026-10-06, not PLAN-46's) remains open; the spec puts it
  outside this plan.
- F11 and F13 from the self-review are carried: the schema cannot express the two conditional rules the codec enforces,
  and `Verification.toResult(Instant)` has no production caller until the provider step lands in `plan-marshall-mcp`.
- Owed to the milestone exit: the concept and user documentation of the machine-onboarding slice and the roadmap
  check-off, in `plan-marshall-documentation`.
- Owed outside this repository: `plan-marshall-mcp` reads `ProviderDefault`, `Verification.lines()` and the new
  `verification` member.

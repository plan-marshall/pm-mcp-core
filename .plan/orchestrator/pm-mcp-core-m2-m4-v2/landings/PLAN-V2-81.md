# Landing Analysis: PLAN-V2-81 — the residue of the machine's stores

epic: pm-mcp-core-m2-m4-v2
workstream: WS-04
pr: #368 (merge `5e4d3513a28123940f25465d3f0ffc8bcf2de45a`, state `merged` per the CI abstraction; 42 files, +1630 -93)

> Landing record for one shipped plan, written by the `analyze` inbox drain of 2026-11-11 from the runbook-lane
> message `rb-v2-81-machine-stores-residue-001.md` (harness opencode, model deepseek-v4.1-flash/high). The message is
> a lead. The three layers were read by one read-only leaf from git at `5e4d3513` (no build); the orchestrator
> re-read the points marked "re-read"; the provider figures were re-read from GitHub (`ci pr view`,
> `runbook_lane review-gate`), the run's progress file and the SonarCloud public API.
> PR #366 was closed unmerged (the required reviewer refused on quota on every head); #368 is the reopen the operator chose.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 machine configuration document, writer, provider defaults | shipped-as-specified | trailing-token refusal on read and write at `MachineConfigurationCodec.java:245-247`; bounded settings at `:231-234`, `:273-283`; store path check at `MachineConfigurationStore.java:74-77`; omitted-variant refusal at `ProviderDefault.java:89-92`; tests in `MachineConfigurationCodecTest` and `MachineConfigurationStoreTest` (re-read) |
| D2 verification result and redaction | shipped-as-specified | `ProviderDefault.java:43-50` (available needs a passed result; not available refuses a passed one, re-read); `VerificationResult` new; redaction through `SecretRedactor` in `ProviderVerification`; `ProviderDefaultTest`, `VerificationResultTest`, `ProviderVerificationTest` |
| D3 consent gate and credential-store aspect | shipped-as-specified | `ConsentGate.java:165` validates the device before the remembered shortcut (re-read); `CredentialStoreCheck.java:164-173` reads before it writes (re-read); aspect maps `StoreRefusal` / `UncheckedIOException`; log text of identifier 146 corrected and pinned by `RuntimeLogMessagesTest` |
| D4 the Sonar issues #238 left open | shipped, **both clauses now evidenced** | The four literal substitutions are in the diff (see L2-1 for what "named constants written" really means). Clause 1: `pullRequest=368&resolved=false` lists 0 (re-read from SonarCloud). Clause 2, which the lane could not state at landing time: the push analysis of `5e4d3513` settled at 2026-11-11T11:08:56Z (revision `5e4d3513`), and `branch=main&resolved=false` lists **0** `java:S1192`; the three packages carry only `MacSecurity` S112 (pre-existing, named outside the spec) and one S5778 in `RoleAssignmentsStoreTest.java:153`, a file this plan did not touch |
| D5 specification and documents | shipped | schema, ADRs `runtime-config/002`, `003` and `runtime-credentials/011`, `012` at the next free numbers, developer guides, README line; the lane's own links run reports `BROKEN LINKS 0` at the head (artifact `.plan/temp/rb-v2-81-machine-stores-residue/links-check.log`); the four broken links it saw on main are in files this plan did not touch |
| D6 coverage and quality gate | **not shown by reading** | The figures (pm-core 92.63 % instruction / 85.49 % branch, pm-runtime 91.0 % / 82.45 %) are a run; Sonar's new-code coverage is the lane's claim, not re-read. The facts block says `deliverables_done=5` of 6; the narrative marks D1-D3, D5, D6 met and D4 not met at landing, so the 5 counted D4 as the open one. With D4 now evidenced the true count is 6 of 6 |

Facts block: `deliverables_total=6`, `deliverables_done=5` — true when written, superseded by the D4 measurement above.

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`, `orchestrator.analyze`), reason: a security-relevant redactor, a check-then-act store path and a codec with a token-level parser, read per deliverable against the merged diff; no build.
- **Verdict:** layer 1 findings (info only); layer 2 findings (minor); layer 3 findings (one **major**, five minor) — the layer-3 defect is named in the operator report of this turn.

The leaf returned 27 findings and 13 conformance rows. The orchestrator re-read the points marked "re-read" at their `file:line`; every point not re-read is an unverified lead and is listed under "Unverified leads".

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L1-1 | Decision 3 refuses on an occupied account instead of restoring; the spec accepts the cost explicitly ("nothing is written or deleted"), the dev guide and ADR 012 name the manual remedy. The leaf's "the spec does not say so" is not borne out — the cost is the chosen trade, recorded at L3-1 as a design limit | checked | confirms (leaf said refutes; re-read of spec Decision 3 settles it as a stated trade) | spec Decision 3; `runtime-credentials.adoc:221` (re-read) |
| L1-2 | D4 clause 2 is post-merge by design — the spec says "the push analysis of the merge decides; state the count". The leaf's "inconsistent" reading is not borne out. What is true: a lane cannot state it at landing time, so the orchestrator measures it (done above) | checked | confirms | spec D4 Done-when |
| L1-3 | The spec names `doc/LogMessages.adoc` where the generated listing is `doc/log-messages/pm-runtime.adoc` (included at `doc/LogMessages.adoc:22`), and the providers schema it places in `store-schemas/12` is the included `machine-config.schema.json` | not_shown_by_reading (leaf-only) | none | — |
| L1-4 | Spec premise "`requireDevice` ran only in `remember`" held before the merge | checked | confirms | `ConsentGate.java:165` (re-read, now in `answer`) |
| L1-5 | `SecretRedactor.redactText` exists in `pm-core` and the spec's "nor the log arguments" is vacuous (`ProviderVerification` logs only message keys) | not_shown_by_reading (leaf-only) | none | — |

**Layer 1 verdict:** findings — 0 refuting after re-read; two leaf refutations settled against the leaf (L1-1, L1-2); two leaf-only points.

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| D1 | `MachineConfigurationCodec.java:231-247`, `MachineConfigurationStore.java:74-77`, `ProviderDefault.java:89-92` | `MachineConfigurationCodecTest`, `MachineConfigurationStoreTest` (weak assertions: L2-3, L2-4, L2-5) | none | checked |
| D2 | `ProviderDefault.java:43-50`, `ProviderVerification` | `ProviderDefaultTest`, `VerificationResultTest`, `ProviderVerificationTest` | none | checked |
| D3 | `ConsentGate.java:165`, `CredentialStoreCheck.java:164-173`, `CredentialStoreAspect` | `ConsentGateTest`, `CredentialStoreCheckTest`, `CredentialStoreAspectTest`, `RuntimeLogMessagesTest` | none | checked |
| D4 | `DefaultAssignment.java:71,90,92` | existing tests | the claim "three named constants were written" is only half true (L2-1) | checked |
| D5 | ADRs, guides, schema | `MachineConfigurationSchemaParityTest` | `TokenLaneCredential.java` (production) is in no deliverable and no surface-drift line (L2-2) | checked |
| D6 | — | — | the coverage figures are a run | not_shown_by_reading |

- **L2-1 (minor, re-read).** The landing says "all three named constants were written (`7978ce84`)" and cites `DefaultAssignment` lines 70 and 90. The diff changes three `DefaultAssignment` literals (`"roles"`, `"tier"`, `"advisory"`, now at lines 71, 90, 92) to `FIELD_ROLES`, `FIELD_TIER` and `FIELD_ADVISORY`, which the pre-merge file already declared at lines 63-67 (checked with `git show 5e4d3513^1:…`). So the duplicate-literal issues are removed as specified, but no new constant was written in `DefaultAssignment`; the codec's new `DETAIL_FORMAT` and one literal substitution are the new part.
- **L2-2 (minor, re-read).** `TokenLaneCredential.java:43` (host lower-casing, `Locale.ROOT`) is a production change no deliverable explains and the landing's "Surface drift" list names only `TokenLaneCredentialTest`. The narrative's § 4 (CodeRabbit finding on `e4718178`, fixed in `d99fa274`) does explain it; the drift list is incomplete rather than the change unplanned. `MachineDefaultsValidationTest` and `VerificationResultTest` are likewise absent from the drift list.
- **L2-3 (minor, re-read).** `MachineConfigurationStoreTest.foreignPath` (line 272): the second assertion (`:284-286`) reads `MachineConfigurationStore.file(base).store().getFileName()` and compares it to `FILE_NAME` — a tautology; only a wrong directory is tried, never a wrong file name below the right base.
- **L2-4 (minor, re-read).** `variantOmitted` (line 291): `refusedGit.getMessage().contains("git")` (`:307`) is also true for the `github` message, so it cannot tell the two refusals apart. The leaf adds that the spec's "store refuses an unverified `available: true`" is asserted on the `ProviderDefault` constructor, not on the store (not re-read).
- **L2-5 (minor, not re-read).** `boundsAccepted` is named "at their edges" but tries only 1 and 60, never 16; the unknown-member test uses compact JSON and a substring check, so "byte-identical" is shown only for already-compact input (`MachineConfigurationCodecTest.java:147`, `:173-185`; the two names and the one-value call at `:143-152` were re-read, the whitespace point is a reading of the leaf's).
- **L2-6 (info).** The S5778 / S3415 counts (12+1, 3+1) are not shown by reading; the new tests do hoist setup out of `assertThrows` and put `expected` first (leaf, not re-read).

**Layer 2 verdict:** findings — 5 minor; D6 not shown by reading.

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `CredentialStoreCheck.java:216-219` with `:164-173` (re-read) | the delete step throws, or the run stops between `put` (`:179`) and `delete`; `cleanUp` (`:226`) also fails or is never reached | the test entry stays; every later check fails `write.occupied` for good and the aspect goes UNAVAILABLE; no code or verb clears a check-owned entry, only a manual keyring edit (dev guide `runtime-credentials.adoc:221`, ADR 012). Documented, and spec Decision 3 chose it over restoring, so this is a known limit, but its cost is a permanent block | major (leaf) — I record it as **major, accepted-by-spec**: operator to say whether a check-owned marker or a `clear` path is wanted |
| L3-2 | `CredentialStoreCheck.java:164` / `:179` (re-read) | an entry appears between `get` and `put` | the check overwrites it, and `cleanUp` then deletes whatever sits under the account, another writer's value included; ADR 012:33 rests on a runtime singleton lock this reading does not check | minor |
| L3-3 | `MachineConfigurationCodec.java:234`, `:278` (re-read) | `lsp_idle_timeout_seconds` above 2^31-1, which the schema allows (no maximum, schema line 13) | refused as unreadable with a Jackson coercion message; schema and codec disagree | minor |
| L3-4 | `MachineConfigurationStore.java:74` (re-read) | a hand-built `LockKey` of level `MACHINE_STORE`, id `machine-config` and a root-level path | `getParent().getParent()` is null, an NPE instead of the `IllegalArgumentException`; the factory-built key never hits it | minor (the leaf said info) |
| L3-5 | `SecretRedactor.java:63`, `:75` (re-read of the patterns; the narrowness is the leaf's reading) | `token="ab cd ef"`, `password="my pass phrase"`, a bearer value under 8 characters, a label-less secret | not redacted; the two forms the spec names (`Bearer …`, `token=sk-…`) are covered | minor |
| L3-6 | `ProviderVerification.java:194`, `:232` (re-read) | `toResult` of a verification that did not run (`ran=false`, no lines) | a failed `VerificationResult` timestamped for a check that never ran; no production caller yet | minor |

**Layer 3 not shown by reading:** the absence of backtracking blow-up in `SecretRedactor` (no nested quantifier seen; only an adversarial run settles it); the schema-looser-than-codec gap (landing F11) stays as the landing named it.

**Layer 3 verdict:** findings — 1 major, 5 minor.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md Dependency Rules | no dependency or plugin added | conforms | the diff touches no `pom.xml` |
| CLAUDE.md Dependency Rules | `pm-runtime` depends on `pm-core` and `pm-api` alone | conforms | the new types stay in `runtime.config` |
| CLAUDE.md Code Standards | `@NullMarked`, `Optional` as return type only | conforms | `ProviderDefault.verification` is `@Nullable` |
| CLAUDE.md Code Standards | record of 4+ components carries `@Builder` | conforms | `ProviderDefault` (4), builder contract test added |
| CLAUDE.md Code Standards | no generic `Exception` caught or thrown | conforms | the codec catches `IllegalArgumentException \| NullPointerException`, not generic |
| CLAUDE.md Code Standards | LogRecord holder and `doc/LogMessages.adoc` | conforms | `RuntimeLogMessages.java:1219` text changed, listing regenerated, `RuntimeLogMessagesTest:303-309` |
| CLAUDE.md Code Standards | every assertion carries a message of 20-60 characters | **deviates** (re-read) | `TokenLaneCredentialTest.java:46` message is 77 characters; `CredentialStoreAspectTest.java:251` and `:264` and `CredentialStoreCheckTest.java:177` have none |
| CLAUDE.md Code Standards | no Mockito / Hamcrest, no new hand-written fake | conforms | extends the existing fake only |
| CLAUDE.md Code Standards | generated test data, literals only where contract | conforms | one nit: `MachineConfigurationCodecTest.java:41` `toLowerCase()` without a `Locale` (not re-read) |
| CLAUDE.md Code Standards | every logged `LogRecord` asserted with `LogAsserts` | not_shown_by_reading | no new record; the `PASSED` INFO fires in new aspect rows without a `LogAsserts` line there |
| CLAUDE.md Git Workflow | commits end with the trailer | conforms | the squash message carries `Co-authored-by: plan-marshall <noreply@cuioss.de>` |
| CLAUDE.md Module Documentation | ADRs at next free numbers, guides extended, README line | conforms | ADR 012:33 rests on the singleton-lock premise the reading did not check |

## Unverified leads

Leaf points the orchestrator did not re-read at the line: L1-3, L1-5, the `ProviderDefault`/`VerificationResult`/redaction trace rows beyond the lines named above, the schema-vs-codec parity (landing F11, accepted), the S5778 / S3415 counts, the D6 coverage figures. They stay leads, not findings.

## Metrics and Anomalies

- Tokens: `unknown` (the harness measured none; recorded as unobserved, not zero). Duration: not in the message.
- Anomalies: `plugin_version=unknown` and `total_tokens=unknown` in the facts block make the landing **incomplete** under `inbox landing-check` (`missing_keys`: `total_tokens`, `plugin_version`). The surface delta is `unmeasured` (neither side supplied).
- One bound expiry: `verify -Pcoverage -pl pm-core,pm-runtime` hit the daemon's 907 s bound and was split per module.

## Routing and Merge Behavior

- Review: CodeRabbit (required) refused on quota three times on #366, reviewed `b22e1b3c` and the merge head `e4718178` on #368, refused again after `d99fa274`; the last two heads are base merges that inherit `e4718178`'s review, and **no trigger was posted after the final push** (the lane reports this as a rule-check deviation). Sourcery refused on its 7-day budget. Provider re-read (`runbook_lane review-gate --pr-number 368`): `unresolved_review_threads=0`, `review_body_findings_total=3`, both **match** the landing. `review_body_findings_handled`: the review-gate parser reports 0 handled (`reason: body_findings_undisposed`, `inline-1..3`); the run's progress file carries `finding:` lines for `inline-1`, `inline-2` and six nitpicks, and `inline-3` (the host-case finding of `e4718178`, fixed in `d99fa274`) sits on a `review:` line with no `finding:` line. By the field's own input (the `finding:` lines) handled is 2 of the 3 inline findings, the landing says 3: **not re-verified as 3**, and not a false claim, because the disposition of `inline-3` is recorded in prose.
- CI/merge: merged through the merge queue; the base moved three times (`3f4ce991`, `8cbf8575`, `a2edd0f2`) and each was merged and re-verified.
- Sonar: the landing's required `sonar_open_new_issues` is `n/a` while its narrative reports "0 open" — an internal inconsistency in the message (a project with Sonar should carry a number). Measured: PR #368 lists 0 open; `branch=main` at revision `5e4d3513` lists 0 `java:S1192` and 85 open in total, of which two sit in the three packages (`MacSecurity` S112, `RoleAssignmentsStoreTest` S5778). Quality gate of `main`: OK.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-V2-81 --status shipped`
- [x] row `pr` stamped — `#368`
- [x] row `landing` stamped — `landings/PLAN-V2-81.md`
- [x] row `plan_marshall_plan_id` stamped — `v2-81-machine-stores-residue` (from the sender `rb-v2-81-machine-stores-residue`)
- [x] epic.md narrative: Open Defect for the incomplete landing, the L3-1 defect and the CodeRabbit-after-final-push deviation recorded; the D4 watch retired
- [x] no `corpus set-verdict` persisted: the spec is shipped and the leaf's layer-1 points name no claim of the Claim Labels by ordinal
- [x] resume anchor updated; `queue-view.md` regenerated

## Follow-Ups

- **L3-1 (operator decision):** a check-owned marker or a `clear` path for a leftover test entry; folds into a staged spec only on the operator's word.
- **Test residue (L2-3, L2-4, L2-5, the three assertion-message deviations):** small, one test file each; offered to PLAN-V2-137 (records-builders-residue, runbook, already seven deliverables — cut line) or PLAN-V2-136, not folded without the operator's word.
- **L3-3 / L3-4:** the schema and the codec disagree on the `lsp_idle_timeout_seconds` upper bound; a one-line schema maximum or an `int` bound in the codec decides it; owner open.
- **`plan-marshall-mcp`** reads `ProviderDefault`, `Verification.lines()` and the `verification` member (owed outside this repository, per the landing).
- **Owed to the milestone exit:** concept and user documentation of the machine-onboarding slice and the roadmap check-off, in `plan-marshall-documentation`.

envelope_version=1
sender_type=plan
sender_id=rb-81-unit-frontmatter-hardening
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-20T15:25:45Z

```landing-facts
schema=landing-facts/1
plan_id=rb-81-unit-frontmatter-hardening
epic=pm-mcp-core-m2-m4
pr=#114
merge_state=merged
cleanup_owed=false
deliverables_total=7
deliverables_done=7
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
```

guard: none on this harness (unenforced)

# Deliverables

| # | Done-when | Verdict | Test | Order | Failing run | Commit |
|---|---|---|---|---|---|---|
| D1 | The decision is stated in the specification part | met | none (no behaviour) | none | n/a | acc59db, 92223c2 |
| D2 | A Cf/Zl/Zp character is refused with its code point | met | `UnitFrontmatterReaderTest$RefusedCharacters.refusesCharacter` (7 characters x 5 fields, LF+CRLF); `WorkflowPrinterTest.quotesEveryValuePlainRefuses`; `WorkflowParserRefusalTest.refusesCrlfTwin` | test-first; mutation-after for U+E0001 | maven-2026-10-20-054751.log (25 failures); mutation maven-2026-10-20-071606.log (30 failures) | bbd79a5, 91bc08a |
| D3 | An author equal after normalisation and case folding is refused as reserved | met | `ProjectUnitLoaderTest$AuthorCheck.reservedForm` (11 forms), `nearMiss`, `authorBehindAnIndentedDelimiter` | mutation-after | maven-2026-10-20-071516.log (9 failures); delimiter mutation maven-2026-10-20-122414.log | c2eb02c, 91bc08a, 5ca7772 |
| D4 | A unit file, a frontmatter and an echoed refusal are bounded | met | `ProjectUnitLoaderTest$FileSizeBound`, `UnitFrontmatterReaderTest$Bounds` | test-first; mutation-after for the far echo | maven-2026-10-20-054751.log (2 size + 5 bounds failures); maven-2026-10-20-071816.log (3 echo failures) | bbd79a5, c2eb02c, 91bc08a |
| D5 | One refusing fixture per class and bound, CRLF twins, enum-derived completeness | met | `WorkflowParserRefusalTest` (3 new fixtures, `refusesCrlfTwin`, `everyRefusalRuleHasAFixture`) | mutation-after | maven-2026-10-20-060049.log (`MISSING_SCOPE` missing from the fixtures' set) | bbd79a5, 91bc08a, 8a81a24 |
| D6 | Developer documentation, ADRs and the specification part in one PR | met | none (documents) | none | n/a | bbd79a5, c2eb02c, 92223c2, 8a81a24 |
| T | T1-T6 met; the tests seen failing before passing; the full gate green on the merged head | met | the table above plus the gate runs | mixed as above | as above | all |

The specification and the code are in one pull request (#114).

`test-compile` ran on every pushed head: c2eb02c (pre-commit 062044), 91bc08a (093358), 8a81a24
(093358), 92223c2 (105224), 0e6de69 (114746), 5ca7772 (123159), ead74de (132449).

Full gate on ead74de (the merged head):
- `verify -Ppre-commit` — success, 6780 executed, `errors[]` empty (maven-2026-10-20-132449.log)
- `verify` — success, 6780 executed, `errors[]` empty (maven-2026-10-20-133024.log)
- `verify -Pcoverage` — success, 6780 executed, `errors[]` empty; "All coverage checks have been met"
  for pm-core and pm-workflow (maven-2026-10-20-133406.log)
- pm-workflow: 1826 tests at origin/main 993a7e7 → 1964 at 8a81a24 → 1966 at 5ca7772 (the merge
  adds no pm-workflow test). Coverage of the touched module: 94 % instruction / 87 % branch
  (pm-workflow/target/site/jacoco/index.html, Total row).
- pm-core and pm-runtime are not touched by the plan's diff; no `-Pintegration-tests` run is due.
  The pull request's own Integration Tests workflow passed on ead74de.

# Personas and skills

- D1 (specification): `persona-documenter`.
- D2 (reader): `persona-implementer`, then `persona-module-tester` for the tests.
- D3 (loader author): `persona-implementer`, then `persona-module-tester`.
- D4 (bounds): `persona-implementer`, `persona-module-tester`; `persona-security-expert` for the
  trust boundary and the untrusted file read.
- D5 (tests and fixtures): `persona-module-tester`.
- D6 (documents and ADRs): `persona-documenter`.
- Every self-review round and the PR-comment triage: `persona-code-reviewer`.
- Domain skills per deliverable: `skills: none named` (the spec declares no `Skills:`/`Persona:`
  lines; the persona table selected the personas).
- Surface drift: no domain skills were added. The spec's Expected Surface is a hint; the realised
  surface adds `UnitFinding.java`, `WorkflowParserRefusalTest.java` and `WorkflowPrinterTest.java`
  for D4/D5 and matrix D2.

# Hypotheses

- `UnitFrontmatterReader` rejects only `Character.isISOControl`, so Cf/Zl/Zp pass: **confirmed** at
  `UnitFrontmatterReader.java:405-412` (origin/main 993a7e7).
- The reserved author is compared by exact equals: **confirmed** at
  `ProjectUnitLoader.java:266`.
- `ProjectUnitLoader.read` reads the file with no size limit: **confirmed** at
  `ProjectUnitLoader.java:223`.
- `UnitFrontmatterReader.read` splits the whole source although it reads only the frontmatter:
  **confirmed** at `UnitFrontmatterReader.java:126`.
- Neither finding is exploitable on its own: **confirmed by use** — the author is only compared and
  reported (`ProjectUnitLoader.java:264-272`) and no authorization decision reads it; the signature
  is carried and never verified (`UnitFrontmatterReader.java:319-320`).

# Findings

Self-review, three rounds with `persona-code-reviewer`, end state `SELF-REVIEW: clear`:
- Round 1 (15 findings): D3 had no genuine failing observation (fixed with a mutation); NFKC-space
  bypass in the author comparison (fixed, NFKC first); implemented rules under the
  "not implemented" lead (fixed); no CRLF twins (fixed with a twin over every malformed fixture);
  no far-past echo (fixed); dead author-line fallback and unreachable escape branch (removed);
  size TOCTOU (fixed with a bounded read); lowercasing called case folding (fixed); ADR link
  drift (fixed); surface additions and two observations recorded.
- Round 2 (10 findings): the full gate was not yet evidenced at that head (fixed by the gate runs);
  ADR 006's escape claim (fixed); Javadoc order (fixed); non-UTF-8 path untested (fixed); stale
  progress lines (fixed); CRLF display name (narrowed); D5 evidence label (corrected); unclosed
  last-line branch untested (fixed); two wording corrections.
- Round 3: `SELF-REVIEW: clear`; no open item.

CI and PR comments:
- CodeRabbit: 4 findings on 8a81a24 — unscoped file-size bound (scoped to project units),
  hardcoded indicator list (derived from `INDICATORS`), ADR 007 missing from the developer guide's
  decision list (added), ADR 006 missing from the workflow-unit decision line (added) — fixed in
  92223c2. One finding on 92223c2 — `rawAuthor` stripped the delimiter lines (fixed in 0e6de69).
  One nitpick on 0e6de69 — the delimiter regression test used a non-reserved author (fixed in
  5ca7772). Re-review of ead74de: no actionable comments, merge risk minimal.
- CodeRabbit inline threads: both resolved (the bot marked them "Addressed in commit 92223c2").
- cuioss-review-bot: no findings at 5ca7772; it did not review the merge head (see Reviewers).
- CI: one unrelated pm-runtime `LspClientTest.stubborn` flake (2-second LSP startup timeout) under a
  concurrent reactor build; the touched module is pm-workflow and the re-run is green.

# Reviewers

| Reviewer | Required | Reviewed head | Result | History |
|---|---|---|---|---|
| cuioss-review-bot | yes | 5ca7772 (not ead74de) | no findings | triggered on 8a81a24, 92223c2, 0e6de69, 5ca7772, and twice on ead74de; the guide stopped at 5ca7772; `wait-for-comments` on ead74de returned 0 new after 300 s |
| coderabbit | yes | 5ca7772 and ead74de | 5 findings, all fixed; re-review of ead74de clean | triggered after every push; its completion summary names ead74de as the covered commit |
| sourcery | optional | n/a | refused, quota ("request another review in 3 days and 1 hour") | triggered once |

Standing authorization: not applicable (neither required reviewer is quota-limited).
Operator-authorized gap: cuioss did not review the merge head ead74de; the operator selected
"Merge with the gap" in a question that named cuioss, 5ca7772 and ead74de.

# Operator questions

1. "D1: which characters does the frontmatter refuse, and how do D2 and D3 fit together?" — options
   "Refuse everywhere + raw author pre-check" / "Reader refuses all fields, no pre-check" /
   "Display fields only, author via loader"; selected "Refuse everywhere + raw author pre-check".
   (The first call was aborted and had no answer; it was re-asked and answered.)
2. "D1/D4: which bounds should the specification state and the code enforce?" — options
   "1 MiB / 200 lines / 80 chars" / "256 KiB / 100 lines / 64 chars" / "4 MiB / 500 lines /
   200 chars"; selected "1 MiB / 200 lines / 80 chars".
3. "May I read the sibling checkout ../plan-marshall-documentation read-only?" — options "No, stay
   in this repository" / "Yes, read-only"; selected "Yes, read-only".
4. "Merge gate for PR #114: cuioss last reviewed 5ca7772, not current head ead74de; coderabbit
   reviewed ead74de; CI+verify green; two /review triggers silent" — options "Re-trigger cuioss and
   wait (Recommended)" / "Merge with the gap" / "Stop and leave the PR open"; selected "Merge with
   the gap". (The first call was aborted and had no answer; it was re-asked and answered.)

No free-text operator messages were used to authorize any action.

# Rule check

- guard: none on this harness (unenforced). There is no guard log and no guard refusal; the guard
  state is unenforced.
- Rules not kept, all reported:
  - Six repository files were first edited in the main checkout by mistake (the test and constant
    changes); they were copied into the worktree with `cp` and every added block was removed from
    the main checkout with the edit tool until `git status --porcelain` was empty. No commit was
    made from the main checkout.
  - One scoped test run (maven-2026-10-20-054327.log) ran against the main checkout before the
    mistake was noticed; every later build ran against the worktree with
    `--execution-mode in_process`.
  - Build logs were read with `tail` twice instead of the Read tool, and `cp`/`rm` were used for the
    worktree correction and to delete three malformed CRLF fixture files.
  - Two pre-commit runs were terminated by the 30-minute shell timeout (maven-2026-10-20-074514.log,
    maven-2026-10-20-075605.log); the gate was re-run to completion.
- No rule was bent for the merge: the one gap (cuioss on the merge head) was put to the operator and
  selected explicitly.

# Residue

- The operator authorized reading `../plan-marshall-documentation` read-only, but the requirement
  texts were not read and `trace.py` was not run: the spec and this repository's
  `doc/Traceability.adoc` were sufficient for the change, and the changed documents link only to
  in-repository paths. No requirement text is claimed beyond the `PM-IMPL-5` section the spec names.
- The head-read clause is met by the code structure (a bounded `indexOf` loop) and a test that the
  body is never refused; no behavioural test can distinguish it from a whole-source split, so the
  evidence is structural.
- The project unit loader is not called on a lifecycle path in this repository; the bounds and the
  author refusal are enforced at the library boundary and applied by `pm-mcp-server`.
- The three new malformed fixtures are LF files; their CRLF twins are the parameterised
  `refusesCrlfTwin` over every malformed fixture and the in-memory twins in the reader test.
- The optional sourcery review was not obtained (quota refusal, optional).
- cuioss did not review the merge head; merged on the operator's explicit selection (above).

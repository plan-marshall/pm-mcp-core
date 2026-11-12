# Landing Analysis: PLAN-V2-65 — the corpus schemas bound every string they match with a pattern

epic: pm-mcp-core-m2-m4-v2
workstream: WS-05
pr: #389 (merge `a4c2fa8613b5c9cae143ae90b74fcdd85b1169a5`, state `merged` per the CI abstraction)

> Landing record for one shipped plan, written by the `analyze` inbox drain of 2026-11-12 from the message
> `rb-v2-65-corpus-schema-length-bounds-001.md` of the runbook lane (harness opencode, model deepseek-v4.1-flash/high,
> executor 0.1.2114). The message is a lead. The three layers were read by one read-only leaf from git at `a4c2fa86` (no
> build, no test run); the orchestrator re-read the points marked "re-read"; the provider figures were re-read from the
> CI abstraction and the SonarCloud public API.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 every non-trivial `pattern` has a `maxLength` | met | counted again from the JSON at `a4c2fa86`: item schema 9 patterns and 8 `maxLength` (`imported-item.schema.json:27,33,51,166,181,187,193,233,242` with `:26,32,40,165,180,186,192,232`), overlay 10 and 7, manifest 6 and 6; the six members whose pattern is a literal `.` (item `:51,242`, overlay `:36,71,103,263`) have none, as the spec allows; nothing sits in `$defs`, `patternProperties` or `additionalProperties`; 19 of 25 patterns are non-literal and every one is bounded. The cited narrative line numbers (`26,31,49,…`) are the numbers before the change. |
| D2 the records enforce the same bound, a parity test proves it | met | `SourceManifest.java:50-84,173-204`, `ImportedItem.java:71-84,275-276,343-344`, `SourceOverlay.java:99-100,137-138`; `CorpusPatternParityTest.boundEqualsSchema` compares the record constant with the schema's `maxLength` value (not existence), 17 cases plus the four dates in `SCHEMA_ONLY_BOUNDS` equal the 21 schema bounds. The length check precedes the pattern in the schema validator (`SchemaSubset.java:140-156`, length at 141-145 with `return`) and in every record constructor. |
| D3 ADR and developer guide | partly | ADR `core-role-corpus/009` and the guide are written; the guide's "every string member of a record that copies such a pattern checks the same bound" is untrue of `LfsPointer.oid` (`LfsPointer.java:44`, three-argument `require`), which the ADR carves out and the guide does not. |
| Done-when "`ProductCorpusValidationTest` and every existing corpus case stay green" | not shown by reading | the ADR says that test skips when `pm.product.root` is absent; a green job id does not show that it ran. |
| Done-when "the two patterns `.` get no bound" | wording error in the spec | six members, as the lane's residue 4 says. |
| Not in the declared surface | none | all 15 files are explained; `finalLineFeedStricter` became a commit-to-name change because a commit holding a line feed is now refused by length. |

## Three-Layer Analysis

Read at `a4c2fa86` by one read-only leaf (`execution-context-level-3`); the orchestrator re-read the composed-id path and the code-point count at their `file:line`.

### Layer 1 — Specified

**L1-1 (major, re-read): the spec treats id, topic and slug as independent ceilings; they are not.** The item id embeds the topic (`id = topic + "/" + slug`), so a topic of 255 or 256 characters can never form a valid id, and a topic and a slug of 256 each compose up to 513. The Standing Constraint "a bound never rejects what the formats admit today without a decision" is broken by the composed-id refusal, which no decision covers. L1-2 (minor): the ceilings should have read `topic + 1 + slug <= 256` and the spec never asks for a composition check on the overlay row. L1-3 (minor): "two patterns `.`" is six members. L1-4 (minor): the hypothesis "the ceilings clear the product corpus" was marked unverifiable, and its only check is a test that can skip.

### Layer 2 — Implemented

Conformance: `@Builder` on the three `Bound` records, no AAA comments, JUnit 5 only, `@ParameterizedTest` for three or more variants, no new fake, new constants documented, no generic catch. Each over-bound value is exactly bound plus one (41, 65, 257, 1025, 2049, 121, and the 257-character id and topic) and the at-bound case exists for every member except the item topic (explained by L1-1); the order test really observes the order (`schemaChecksTheLengthBeforeThePattern` requires exactly `["$.commit: longer than 40","$.created: longer than 10"]`, which a pattern-first order cannot produce). Deviations: no bound test uses a supplementary character (all `"a".repeat(n)`), so replacing `codePointCount` by `length()` in `SourceManifest.require` (`:198-204`) fails no test, while `path` and `repository` admit non-ASCII; `unboundedPatternReported` re-implements the predicate inline; the helper `patternOf` returns `null` where `maxLengthOf` returns `Optional`; `literalPatternIsUnbounded` forbids a later bound on the literal members; one assertion message has 61 characters (the case name `SourceManifest.REPOSITORY_MAX_LENGTH / item origin repository`); `@ParameterizedTest(name = "{0}")` prints the whole 1 to 2 KB strings into the display name; the bound-edge literals carry no "this is the contract" comment.

### Layer 3 — Correct

**M1 (major, re-read): an overlay row that passes its own bounds fails the import.** `SourceOverlay.Row` (`:97-100`) bounds `slug` and `topic` at 256 each and checks neither the composition; `itemId()` (`:128`) is `topic + "/" + slug`; `ImportedItem` bounds the id at 256 (`:343-344`). Trigger: a 256-character topic and a 256-character slug (any pair with a composed id over 256). It passes the row, the overlay schema, the codec and `CorpusValidator`; `ItemGenerator.generate` then throws, and `CorpusImport.generate` (`:250-253`, re-read) turns it into a `MALFORMED` source: none of that source's valid rows is written, the detail echoes the unbounded `row.key()`, and the validator reported the corpus clean. Before #389 the id had no length bound and the row imported. ADR 009 records the consequence; no test covers it.

Minor: m1 a slug of 251 to 254 characters (topic `a`, composed id 256) makes `CorpusImport.write` (`:335-343`) build a file name of 256 to 259 bytes, above the common 255-byte limit; `Files.write` can throw and an `UncheckedIOException` escapes `run()` instead of one source being reported malformed (pre-existing, not closed; the host file system is not shown); m2 the refusals that still echo an unbounded value, all outside the patterned set or pre-existing: `ImportedItem.requireText` (`:382-387`) and `SourceOverlay.requireKey` (`:159-165`) for a value of line feeds only, `CorpusImport.select` (`:261-273`) and the WARN log of `missed()`, `CorpusJson.date`, `LfsPointer.oid`, the `SchemaSubset` enum message, and `SourceManifestCodec.java:142` calls `URI.create(sourceRepository)` before the record's length check, so a malformed over-long URI is echoed whole by the `IllegalArgumentException` (the URI parser is not a regex); m3 the narrowing also refuses `sha256` and the dates followed by a line feed, which the schema used to admit (the ADR names it for a commit only); m4 `SourceOverlay.POSITION` (`:58`) runs `^[1-9]\d*$` on an unbounded `key` (linear, outside the bounded set); m5 a cached item with an id over 256 would fail validation until the next import (not shown).

Checked and found fine: schema and record numbers are equal for name, commit, sha256, licence, path, repository, id, topic, slug and title; the schema validator (`SchemaSubset.java:142`) and the records (`SourceManifest.java:176,200`, `ImportedItem.java:350`) both count code points; length-first holds in every constructor (`require` overload, `requireRepository`); the new length refusals do not echo the value; `CorpusJson.date` runs a fixed-length pattern on unbounded text that fails fast (the ADR documents it as schema-only).

## Conformance to the Project's Own Rules

See Layer 2. The review gate in the message: 0 unresolved threads, 5 body findings, 5 handled (CodeRabbit required, five findings fixed and answered; Sourcery optional, approved twice, later triggers refused on quota). The lane's own rule check names two shell-rule breaches (a heredoc edit and early shell reads), the `trace.py` executor-form exception that R17 prescribes, and a sequence deviation (the PR was opened before self-review round 1); none bears on the merged code.

## Unverified leads

- Whether `ProductCorpusValidationTest` ran or skipped in the jobs `f91147a9…` and `0e0def3f…` (read the surefire report).
- Whether the import really ends `FAILED` with no items for the source on a composed id of 257 or more (the logic is read, not run).
- Whether `Files.write` fails for a 256-byte file name on the host's file system.
- The lane's 29 routed builds and its coverage and integration runs: not shown here.

## Metrics and Anomalies

`complete: false` for `total_tokens` and `plugin_version` only (no metrics phase runs in this lane); `surface_delta` unmeasured. Sonar over plain HTTP: `pullRequest=389` 0 open issues, 0 hotspots to review, gate `OK`; the last analysis of `main` read at `067aef00` (84 open), not yet at the merge. **Build time, evidence for PLAN-V2-140:** 29 routed builds, none in process; `verify -Pcoverage` at `21917134` hit the lane's own 2050 s bound with 7553 tests and 0 failed, and ran green in 1824 s when re-submitted at 3600 s; the lane re-ran the four build commands at two heads (`6845066d`, `2e0fa815`). The first merge-queue enqueue was dequeued by a merge-group failure that was not this change (`surefire-junit-platform:3.6.0` absent from Maven Central, run 43288158768); the operator chose "re-enqueue" and the second enqueue merged (run 43289426322). Self-review ran three rounds (12, 5 and 6 findings, all fixed or answered), no `budget-exhausted` state.

## Routing and Merge Behavior

One PR (#389), squash through the merge queue. The message carries no `candidate-lesson`, so nothing is routed to `lessons-routing`.

## Reconciliation Actions

Row PLAN-V2-65 `running` -> `shipped`, `pr` `#389`, `landing` `landings/PLAN-V2-65.md`, `plan_marshall_plan_id` `rb-v2-65-corpus-schema-length-bounds`; message `rb-v2-65-corpus-schema-length-bounds-001` archived; Watch entry in `epic.md`; decision log.

## Follow-Ups

Placed 2026-11-12 under the operator's rule "every leftover goes into a staged plan":

1. M1 (the composed id, ask-at-refine, recommendation check the composition in the row ~80%), m1 (the file-name length, ask-at-refine, recommendation catch per source ~55%), m2 (the echoed values), m3 and the statements (the guide's `LfsPointer.oid`, the ADR's narrowing, "two patterns") and the `ProductCorpusValidationTest` question -> PLAN-V2-114 item 16.
2. The Layer-2 deviations (the supplementary-character cases, the inline predicate, the `null` helper, the 61-character message, the display names) -> PLAN-V2-125.
3. The corpus's own `verification-item.schema.json` in `plan-marshall-mcp` stays unbounded (the lane's residue 1, named in the PR body): a finding for that repository, no plan of this epic carries it.

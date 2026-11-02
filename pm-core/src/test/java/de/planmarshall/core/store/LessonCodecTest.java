/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.LessonCodec.Readable;
import de.planmarshall.core.store.LessonCodec.Unreadable;
import de.planmarshall.core.store.LessonCodec.UnreadableReason;
import de.planmarshall.core.store.LessonFrontMatter.Audience;
import de.planmarshall.core.store.LessonFrontMatter.Category;
import de.planmarshall.core.store.LessonFrontMatter.Claim;
import de.planmarshall.core.store.LessonFrontMatter.LastFilingFailure;
import de.planmarshall.core.store.LessonFrontMatter.MergedFrom;
import de.planmarshall.core.store.LessonFrontMatter.Promotion;
import de.planmarshall.core.store.LessonFrontMatter.Provenance;
import de.planmarshall.core.store.LessonFrontMatter.Remedy;
import de.planmarshall.core.store.LessonFrontMatter.Retired;
import de.planmarshall.core.store.LessonFrontMatter.Revision;
import de.planmarshall.core.store.LessonFrontMatter.Source;
import de.planmarshall.core.store.LessonFrontMatter.Status;

/**
 * The codec of one lesson file (Store Schemas § Lesson Record, File Format; PM-IMPL-2): the canonical bytes round-trip
 * byte for byte, a body that itself contains {@code ---} lines is kept whole, and every file outside the one shape is
 * an {@link Unreadable} value — never an exception and never {@code absent}. The front matter is YAML restricted to
 * the JSON data model, so a block-style file is readable and an anchor, an alias and a tag are refused (ADR
 * core-store/061).
 */
@EnableGeneratorController
@DisplayName("Lesson codec")
class LessonCodecTest {

    private static final String BODY = "# Lesson\n\nA body.\n";
    private static final String DIGEST = "a".repeat(64);
    private static final String INSTANT = "2026-10-31T10:00:00Z";
    private static final String STATEMENT = "Add a check that the store is read strictly";

    private static final String FRONT_MATTER = "{\"format_version\":1,\"lesson_id\":\"2026-09-22-12-001\","
            + "\"title\":\"A title\",\"component\":\"pm-core\",\"category\":\"bug\",\"bundle\":null,\"rule\":null,"
            + "\"remedy\":{\"kind\":\"check\",\"statement\":\"" + STATEMENT + "\",\"check\":null},"
            + "\"recurrence_count\":null,\"last_seen\":null,\"status\":\"active\",\"provenance\":\"run_artifact\","
            + "\"derived_from_untrusted\":false,\"audience\":\"local\",\"last_filing_failure\":null,"
            + "\"created_at\":\"" + INSTANT + "\",\"tool_version\":\"0.1.0\",\"promotions\":[],\"revision\":1,"
            + "\"sources\":[{\"kind\":\"plan\",\"ref\":\"v2-11\"}],"
            + "\"revisions\":[{\"n\":1,\"at\":\"" + INSTANT + "\","
            + "\"sources\":[{\"kind\":\"plan\",\"ref\":\"v2-11\"}],\"body_sha256\":\"" + DIGEST + "\"}],"
            + "\"merged_from\":[],\"claim\":null,\"retired\":null}";

    private static final String CANONICAL = "---\n" + FRONT_MATTER + "\n---\n" + BODY;

    private static final String BLOCK_FRONT_MATTER = "format_version: 1\n"
            + "lesson_id: 2026-09-22-12-001\n"
            + "title: A title\n"
            + "component: pm-core\n"
            + "category: bug\n"
            + "bundle: null\n"
            + "rule: null\n"
            + "remedy:\n"
            + "  kind: check\n"
            + "  statement: " + STATEMENT + "\n"
            + "  check: null\n"
            + "recurrence_count: null\n"
            + "last_seen: null\n"
            + "status: active\n"
            + "provenance: run_artifact\n"
            + "derived_from_untrusted: false\n"
            + "audience: local\n"
            + "last_filing_failure: null\n"
            + "created_at: " + INSTANT + "\n"
            + "tool_version: 0.1.0\n"
            + "promotions: []\n"
            + "revision: 1\n"
            + "sources:\n"
            + "  - kind: plan\n"
            + "    ref: v2-11\n"
            + "revisions:\n"
            + "  - n: 1\n"
            + "    at: " + INSTANT + "\n"
            + "    sources:\n"
            + "      - kind: plan\n"
            + "        ref: v2-11\n"
            + "    body_sha256: " + DIGEST + "\n"
            + "merged_from: []\n"
            + "claim: null\n"
            + "retired: null\n";

    private static Lesson canonical() {
        var source = new Source(Source.Kind.PLAN, "v2-11", null);
        return new Lesson(LessonFrontMatter.builder()
                .formatVersion(LessonFrontMatter.FORMAT_VERSION)
                .lessonId(new LessonId("2026-09-22-12-001"))
                .title("A title")
                .component("pm-core")
                .category(Category.BUG)
                .bundle(null)
                .rule(null)
                .remedy(new Remedy(Remedy.Kind.CHECK, STATEMENT, null))
                .recurrenceCount(null)
                .lastSeen(null)
                .status(Status.ACTIVE)
                .provenance(Provenance.RUN_ARTIFACT)
                .derivedFromUntrusted(false)
                .audience(Audience.LOCAL)
                .lastFilingFailure(null)
                .createdAt(Instant.parse(INSTANT))
                .toolVersion("0.1.0")
                .promotions(List.of())
                .revision(1)
                .sources(List.of(source))
                .revisions(List.of(new Revision(1, Instant.parse(INSTANT), List.of(source), DIGEST)))
                .mergedFrom(List.of())
                .claim(null)
                .retired(null)
                .build(), BODY);
    }

    private static LessonCodec.Reading decode(String file) {
        return LessonCodec.decode(file.getBytes(UTF_8));
    }

    private static Unreadable unreadable(String file, UnreadableReason reason) {
        var reading = decode(file);
        assertInstanceOf(Unreadable.class, reading, "The file is unreadable");
        var unreadable = (Unreadable) reading;
        assertEquals(reason, unreadable.reason(), "The file is unreadable for its reason");
        return unreadable;
    }

    @Test
    @DisplayName("the canonical bytes are written byte for byte")
    void canonicalBytesAreWrittenByteForByte() {
        assertArrayEquals(CANONICAL.getBytes(UTF_8), LessonCodec.encode(canonical()),
                "The writer writes the canonical file");
    }

    @Test
    @DisplayName("decode(encode(x)) is x and encode(decode(b)) is b for canonical bytes")
    void fileRoundTrips() {
        var canonical = canonical();
        var bytes = CANONICAL.getBytes(UTF_8);

        assertAll(
                () -> assertEquals(canonical, ((Readable) decode(CANONICAL)).lesson(),
                        "The canonical file reads into the lesson"),
                () -> assertEquals(canonical, ((Readable) LessonCodec.decode(
                        LessonCodec.encode(canonical))).lesson(), "decode(encode(x)) is x"),
                () -> assertArrayEquals(bytes, LessonCodec.encode(((Readable) LessonCodec.decode(bytes)).lesson()),
                        "encode(decode(b)) is b for canonical bytes"));
    }

    @Test
    @DisplayName("a body that contains fence lines is kept whole")
    void bodyWithFenceLinesIsKeptWhole() {
        var body = "# Lesson\n\n---\n\nA horizontal rule above.\n---\n";
        var lesson = new Lesson(canonical().frontMatter(), body);

        var reading = LessonCodec.decode(LessonCodec.encode(lesson));

        assertEquals(lesson, ((Readable) reading).lesson(), "The body after the first fence is kept whole");
    }

    @Test
    @DisplayName("a hand-edited block-style front matter is readable")
    void blockStyleFrontMatterIsReadable() {
        var file = "---\n" + BLOCK_FRONT_MATTER + "---\n" + BODY;

        var reading = decode(file);

        assertEquals(canonical(), ((Readable) reading).lesson(), "The block-style file reads into the lesson");
    }

    @Test
    @DisplayName("a CRLF file is decoded and its body keeps its line endings")
    void crlfFileIsDecoded() {
        var file = CANONICAL.replace("\n", "\r\n");

        var reading = decode(file);

        var lesson = ((Readable) reading).lesson();
        assertAll(
                () -> assertEquals(canonical().frontMatter(), lesson.frontMatter(),
                        "The CRLF front matter reads into the record"),
                () -> assertEquals(BODY.replace("\n", "\r\n"), lesson.body(),
                        "The CRLF body keeps its line endings"));
    }

    @Test
    @DisplayName("a file without the opening fence is unreadable")
    void missingOpeningFenceIsUnreadable() {
        var file = "no fence\n" + FRONT_MATTER + "\n---\n" + BODY;

        assertTrue(unreadable(file, UnreadableReason.NO_OPENING_FENCE).detail().contains("'---'"),
                "The detail names the missing opening fence");
    }

    @Test
    @DisplayName("a file without the closing fence is unreadable")
    void missingClosingFenceIsUnreadable() {
        var file = "---\n" + FRONT_MATTER + "\n" + BODY;

        assertTrue(unreadable(file, UnreadableReason.NO_CLOSING_FENCE).detail().contains("'---'"),
                "The detail names the missing closing fence");
    }

    @Test
    @DisplayName("a front matter that is not an object is unreadable")
    void frontMatterThatIsNoObjectIsUnreadable() {
        var file = "---\n- a\n- b\n---\n" + BODY;

        assertTrue(unreadable(file, UnreadableReason.NOT_AN_OBJECT).detail().contains("not an object"),
                "The detail names the missing object");
    }

    @Test
    @DisplayName("a front matter the strict reader refuses is unreadable")
    void strictReaderRefusalsAreUnreadable() {
        var unknownMember = BLOCK_FRONT_MATTER + "unknown_member: 1\n";
        var duplicateMember = BLOCK_FRONT_MATTER + "title: A second title\n";
        var missingMember = BLOCK_FRONT_MATTER.replace("tool_version: 0.1.0\n", "");
        var mistypedMember = BLOCK_FRONT_MATTER.replace("revision: 1\n", "revision: one\n");
        var anotherFormatVersion = BLOCK_FRONT_MATTER.replace("format_version: 1\n", "format_version: 2\n");
        var anotherInstant = BLOCK_FRONT_MATTER.replace("created_at: " + INSTANT,
                "created_at: 2026-10-31T10:00:00+00:00");

        assertAll(
                () -> unreadable("---\n" + unknownMember + "---\n" + BODY, UnreadableReason.STRICT_READER),
                () -> unreadable("---\n" + duplicateMember + "---\n" + BODY, UnreadableReason.STRICT_READER),
                () -> unreadable("---\n" + missingMember + "---\n" + BODY, UnreadableReason.STRICT_READER),
                () -> unreadable("---\n" + mistypedMember + "---\n" + BODY, UnreadableReason.STRICT_READER),
                () -> unreadable("---\n" + anotherFormatVersion + "---\n" + BODY, UnreadableReason.STRICT_READER),
                () -> unreadable("---\n" + anotherInstant + "---\n" + BODY, UnreadableReason.STRICT_READER));
    }

    @Test
    @DisplayName("a front matter that violates a rule of the record is unreadable")
    void ruleViolationIsUnreadable() {
        var wireCheckWithoutCheck = BLOCK_FRONT_MATTER.replace("kind: check", "kind: wire_check");

        var unreadable = unreadable("---\n" + wireCheckWithoutCheck + "---\n" + BODY,
                UnreadableReason.RULE_VIOLATION);

        assertTrue(unreadable.detail().contains(LessonFrontMatter.REMEDY_CHECK_MISMATCH),
                "The detail names remedy_check_mismatch");
    }

    @Test
    @DisplayName("an anchor, an alias and a tag are unreadable")
    void yamlSpellingsBeyondTheJsonModelAreUnreadable() {
        var anchor = BLOCK_FRONT_MATTER.replace("title: A title", "title: &title A title");
        var alias = BLOCK_FRONT_MATTER.replace("title: A title", "title: &title A title")
                .replace("component: pm-core", "component: *title");
        var tag = BLOCK_FRONT_MATTER.replace("title: A title", "title: !!str A title");

        assertAll(
                () -> assertTrue(unreadable("---\n" + anchor + "---\n" + BODY, UnreadableReason.STRICT_READER)
                        .detail().contains("anchor"), "The anchor is refused"),
                () -> unreadable("---\n" + alias + "---\n" + BODY, UnreadableReason.STRICT_READER),
                () -> assertTrue(unreadable("---\n" + tag + "---\n" + BODY, UnreadableReason.STRICT_READER)
                        .detail().contains("tag"), "The explicit tag is refused"));
    }

    @Test
    @DisplayName("more than one document in the front matter is unreadable")
    void moreThanOneDocumentIsUnreadable() {
        var file = "---\n--- \n" + BLOCK_FRONT_MATTER + "--- \n" + BLOCK_FRONT_MATTER + "---\n" + BODY;

        assertTrue(unreadable(file, UnreadableReason.STRICT_READER).detail().contains("more than one document"),
                "The guard refuses the second document");
    }

    @Test
    @DisplayName("a leading-zero integer is not read as an octal number")
    void leadingZeroIntegerIsRefused() {
        var octal = BLOCK_FRONT_MATTER.replace("revision: 1\n", "revision: 010\n");
        var invalidOctal = BLOCK_FRONT_MATTER.replace("revision: 1\n", "revision: 08\n");
        var yamlOctal = BLOCK_FRONT_MATTER.replace("revision: 1\n", "revision: 0o7\n");

        assertAll(
                () -> unreadable("---\n" + octal + "---\n" + BODY, UnreadableReason.STRICT_READER),
                () -> unreadable("---\n" + invalidOctal + "---\n" + BODY, UnreadableReason.STRICT_READER),
                () -> unreadable("---\n" + yamlOctal + "---\n" + BODY, UnreadableReason.STRICT_READER));
    }

    @Test
    @DisplayName("a body that is not encodable as UTF-8 is refused")
    void bodyWithAnUnpairedSurrogateIsRefused() {
        var frontMatter = canonical().frontMatter();
        var body = "A body with an unpaired surrogate: \uD800";

        assertThrows(IllegalArgumentException.class, () -> new Lesson(frontMatter, body),
                "A body with an unpaired surrogate is refused");
    }

    @Test
    @DisplayName("a full record with every optional member set round-trips")
    void fullRecordRoundTrips() {
        var source = new Source(Source.Kind.LESSON, "v2-10", DIGEST);
        var lesson = new Lesson(LessonFrontMatter.builder()
                .formatVersion(LessonFrontMatter.FORMAT_VERSION)
                .lessonId(new LessonId("2026-09-08-22-004"))
                .title("A full title")
                .component("pm-core")
                .category(Category.ARCH_CONSTRAINT)
                .bundle("pm-dev-java")
                .rule("no-direct-http")
                .remedy(new Remedy(Remedy.Kind.WIRE_CHECK, STATEMENT, "Gate pm-core:no-http"))
                .recurrenceCount(3)
                .lastSeen(LocalDate.of(2026, 10, 31))
                .status(Status.RETIRED)
                .provenance(Provenance.OPERATOR_ACTION)
                .derivedFromUntrusted(true)
                .audience(Audience.UPSTREAM)
                .lastFilingFailure(LastFilingFailure.RATE_LIMITED)
                .createdAt(Instant.parse(INSTANT))
                .toolVersion("0.1.0")
                .promotions(List.of(new Promotion(Promotion.Kind.ISSUE,
                        "plan-marshall/plan-marshall-documentation#1", Instant.parse(INSTANT))))
                .revision(2)
                .sources(List.of(source))
                .revisions(List.of(new Revision(1, Instant.parse(INSTANT), List.of(source), DIGEST),
                        new Revision(2, Instant.parse(INSTANT), List.of(source), DIGEST)))
                .mergedFrom(List.of(new MergedFrom(new LessonId("2026-09-13-06-002"), Instant.parse(INSTANT),
                        "operator")))
                .claim(new Claim("v2-11-lesson-record-and-codec", Instant.parse(INSTANT)))
                .retired(Retired.builder()
                        .at(Instant.parse(INSTANT))
                        .by("v2-11-lesson-record-and-codec")
                        .verdict(Retired.Verdict.APPLIED)
                        .into(null)
                        .reason("the plan landed")
                        .coveringClause(null)
                        .coveringInput(null)
                        .lessonState(Retired.LessonState.FOUND)
                        .build())
                .build(), BODY);

        var bytes = LessonCodec.encode(lesson);
        var reading = LessonCodec.decode(bytes);

        assertAll(
                () -> assertEquals(lesson, ((Readable) reading).lesson(), "The full record round-trips"),
                () -> assertArrayEquals(bytes,
                        LessonCodec.encode(((Readable) LessonCodec.decode(bytes)).lesson()),
                        "The full canonical bytes round-trip"));
    }

    @Test
    @DisplayName("a superseded retirement with its target round-trips")
    void supersededRetirementRoundTrips() {
        var retired = Retired.builder()
                .at(Instant.parse(INSTANT))
                .by("v2-11-lesson-record-and-codec")
                .verdict(Retired.Verdict.SUPERSEDED)
                .into(new LessonId("2026-09-13-06-002"))
                .reason("a later lesson supersedes it")
                .coveringClause("the later lesson")
                .coveringInput("the worked example")
                .lessonState(Retired.LessonState.PRESENT_UNREADABLE)
                .build();
        var lesson = new Lesson(LessonFixtures.frontMatterBuilder()
                .status(Status.RETIRED)
                .retired(retired)
                .build(), BODY);

        var reading = LessonCodec.decode(LessonCodec.encode(lesson));

        assertEquals(lesson, ((Readable) reading).lesson(), "The superseded retirement round-trips");
    }

    @Test
    @DisplayName("an empty body stays empty")
    void emptyBodyIsKeptEmpty() {
        var lesson = new Lesson(canonical().frontMatter(), "");

        var reading = LessonCodec.decode(LessonCodec.encode(lesson));

        assertEquals("", ((Readable) reading).lesson().body(), "The empty body stays empty");
    }

    @Test
    @DisplayName("a body whose first line is a fence is kept whole")
    void bodyStartingWithAFenceIsKeptWhole() {
        var body = "---\nA line after a fence.\n";
        var lesson = new Lesson(canonical().frontMatter(), body);

        var reading = LessonCodec.decode(LessonCodec.encode(lesson));

        assertEquals(body, ((Readable) reading).lesson().body(), "The body keeps its first fence line");
    }

    @Test
    @DisplayName("a file that is not UTF-8 is unreadable")
    void invalidUtf8IsUnreadable() {
        var canonical = CANONICAL.getBytes(UTF_8);
        var file = Arrays.copyOf(canonical, canonical.length + 1);
        file[canonical.length] = (byte) 0xFF;

        var reading = LessonCodec.decode(file);

        assertAll(
                () -> assertInstanceOf(Unreadable.class, reading, "The file is unreadable"),
                () -> assertEquals(UnreadableReason.STRICT_READER, ((Unreadable) reading).reason(),
                        "The file is refused by the strict reader"));
    }

    @Test
    @DisplayName("a lesson without front matter is not written")
    void lessonWithoutFrontMatterIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Lesson(null, BODY),
                "A lesson without front matter is refused");
    }
}

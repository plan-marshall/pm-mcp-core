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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.store.LessonFrontMatter.Category;
import de.planmarshall.core.store.LessonFrontMatter.MergedFrom;
import de.planmarshall.core.store.LessonFrontMatter.Promotion;
import de.planmarshall.core.store.LessonFrontMatter.Remedy;
import de.planmarshall.core.store.LessonFrontMatter.Retired;
import de.planmarshall.core.store.LessonFrontMatter.Retired.Verdict;
import de.planmarshall.core.store.LessonFrontMatter.Revision;
import de.planmarshall.core.store.LessonFrontMatter.Source;
import de.planmarshall.core.store.LessonFrontMatter.Status;

/**
 * U+007F (DEL) and a C0 control character in the front matter of a lesson (Store Schemas § Lesson Record; ADR
 * core-store/061): the writer escapes them, so every string member reads back unchanged instead of being refused by
 * the YAML reader. DEL is the one control character below U+0080 the front-matter writer spares from the JSON standard
 * escapes, beside the non-ASCII characters; the C0 range is escaped by those standard escapes already.
 * <p>
 * The literals the assertions pin are the contract — the escaped wire form {@code \u007F}, DEL itself and the C0
 * control character the round trip is stated for; every prose member of a fixture is generated.
 */
@EnableGeneratorController
@DisplayName("Lesson codec and DEL")
class LessonCodecControlCharacterTest {

    /** The escaped form the writer emits for DEL. */
    private static final String ESCAPED_DEL = "\\u007F";

    /** DEL itself, the character the writer must escape. */
    private static final String DEL = "\u007F";

    /** A C0 control character, the range the JSON writer escapes already. */
    private static final String C0 = "\u0001";

    static Stream<Arguments> members() {
        return Stream.of(
                Arguments.of("component", live(builder -> builder.component(text() + DEL))),
                Arguments.of("bundle", live(builder -> builder.bundle(text() + DEL))),
                Arguments.of("tool_version", live(builder -> builder.toolVersion(text() + DEL))),
                Arguments.of("rule", live(builder -> builder.category(Category.ARCH_CONSTRAINT)
                        .rule(text() + DEL))),
                Arguments.of("remedy.statement", live(builder -> builder.remedy(
                        new Remedy(Remedy.Kind.REVIEW_STANDARD, statement() + DEL, null)))),
                Arguments.of("remedy.check", live(builder -> builder.remedy(
                        new Remedy(Remedy.Kind.WIRE_CHECK, statement(), text() + DEL)))),
                Arguments.of("source.ref", live(builder -> builder.sources(
                        List.of(new Source(Source.Kind.PLAN, text() + DEL, null))))),
                Arguments.of("revisions[].sources[].ref", live(builder -> builder.revisions(List.of(
                        new Revision(1, LessonFixtures.instant(),
                                List.of(new Source(Source.Kind.PLAN, text() + DEL, null)),
                                LessonFixtures.digest()))))),
                Arguments.of("promotion.ref", live(builder -> builder.promotions(
                        List.of(new Promotion(Promotion.Kind.EPIC, text() + DEL, LessonFixtures.instant()))))),
                Arguments.of("merged_from.by", live(builder -> builder.mergedFrom(List.of(
                        new MergedFrom(LessonFixtures.lessonId(), LessonFixtures.instant(), text() + DEL))))),
                Arguments.of("retired.by", retired(retirement -> retirement.by(text() + DEL))),
                Arguments.of("retired.reason", retired(retirement -> retirement.reason(text() + DEL))),
                Arguments.of("retired.covering_clause",
                        retired(retirement -> retirement.coveringClause(text() + DEL))),
                Arguments.of("retired.covering_input",
                        retired(retirement -> retirement.coveringInput(text() + DEL))));
    }

    private static String text() {
        return LessonFixtures.text();
    }

    private static String statement() {
        return LessonFixtures.statement();
    }

    private static Lesson live(Consumer<LessonFrontMatter.LessonFrontMatterBuilder> change) {
        var builder = LessonFixtures.frontMatterBuilder();
        change.accept(builder);
        return new Lesson(builder.build(), LessonFixtures.body());
    }

    private static Lesson retired(Consumer<Retired.RetiredBuilder> change) {
        var retirement = LessonFixtures.retirement(Verdict.COMPLETELY_COVERED);
        change.accept(retirement);
        var builder = LessonFixtures.frontMatterBuilder().status(Status.RETIRED).retired(retirement.build());
        return new Lesson(builder.build(), LessonFixtures.body());
    }

    @ParameterizedTest(name = "a DEL in {0} round-trips through the codec")
    @MethodSource("members")
    @DisplayName("a DEL round-trips in every front-matter string")
    void delRoundTrips(String member, Lesson lesson) {
        var reading = LessonCodec.decode(LessonCodec.encode(lesson));

        var readable = assertInstanceOf(LessonCodec.Readable.class, reading,
                "the file with a DEL in " + member + " is readable");
        assertEquals(lesson, readable.lesson(), "the DEL in " + member + " round-trips");
    }

    @Test
    @DisplayName("a DEL in the title round-trips instead of being refused alone")
    void delInTitleRoundTrips() {
        var lesson = live(builder -> builder.title(text() + DEL));

        var reading = LessonCodec.decode(LessonCodec.encode(lesson));

        var readable = assertInstanceOf(LessonCodec.Readable.class, reading,
                "the DEL in the title must leave the file readable");
        assertEquals(lesson, readable.lesson(), "the DEL in the title round-trips unchanged");
    }

    @Test
    @DisplayName("a C0 control character round-trips through the codec")
    void c0ControlRoundTrips() {
        var lesson = live(builder -> builder.title(text() + C0)
                .sources(List.of(new Source(Source.Kind.PLAN, text() + C0, null))));

        var reading = LessonCodec.decode(LessonCodec.encode(lesson));

        var readable = assertInstanceOf(LessonCodec.Readable.class, reading,
                "the C0 control must leave the file readable");
        assertEquals(lesson, readable.lesson(), "the C0 control round-trips unchanged");
    }

    @Test
    @DisplayName("the writer escapes DEL instead of leaving it to the YAML reader")
    void writerEscapesDel() {
        var encoded = new String(LessonCodec.encode(live(builder -> builder.title(text() + DEL))), UTF_8);

        assertTrue(encoded.contains(ESCAPED_DEL), "the writer escapes DEL as \\u007F");
    }

    @Test
    @DisplayName("the reader refuses a literal DEL, which is why the writer escapes it")
    void readerRefusesALiteralDel() {
        var title = text();
        var escaped = new String(LessonCodec.encode(live(builder -> builder.title(title))), UTF_8);

        var reading = LessonCodec.decode(escaped
                .replace("\"title\":\"" + title + "\"", "\"title\":\"" + title + DEL + "\"")
                .getBytes(UTF_8));

        assertAll("A literal DEL in the title value leaves the file unreadable",
                () -> assertInstanceOf(LessonCodec.Readable.class, LessonCodec.decode(escaped.getBytes(UTF_8)),
                        "the same file without the literal DEL is readable"),
                () -> assertInstanceOf(LessonCodec.Unreadable.class, reading,
                        "the reader refuses a literal DEL in a scalar"));
    }
}

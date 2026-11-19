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
import de.planmarshall.core.store.LessonFrontMatter.Source;
import de.planmarshall.core.store.LessonFrontMatter.Status;

/**
 * U+007F (DEL) in the front matter of a lesson (Store Schemas § Lesson Record; ADR core-store/061): the writer escapes
 * it, so every string member reads back unchanged instead of being refused by the YAML reader. DEL is the one control
 * character below U+0080 that YAML does not admit literally, so it is escaped beside the non-ASCII characters.
 */
@EnableGeneratorController
@DisplayName("Lesson codec and DEL")
class LessonCodecControlCharacterTest {

    /** The escaped form the writer emits for DEL. */
    private static final String ESCAPED_DEL = "\\u007F";

    /** DEL itself, the character the writer must escape. */
    private static final String DEL = "\u007F";

    static Stream<Arguments> members() {
        return Stream.of(
                Arguments.of("component", live(builder -> builder.component("A component" + DEL))),
                Arguments.of("bundle", live(builder -> builder.bundle("A bundle" + DEL))),
                Arguments.of("tool_version", live(builder -> builder.toolVersion("1.2.3" + DEL))),
                Arguments.of("rule", live(builder -> builder.category(Category.ARCH_CONSTRAINT)
                        .rule("A rule" + DEL))),
                Arguments.of("remedy.statement", live(builder -> builder.remedy(
                        new Remedy(Remedy.Kind.REVIEW_STANDARD, "A standard" + DEL, null)))),
                Arguments.of("remedy.check", live(builder -> builder.remedy(
                        new Remedy(Remedy.Kind.WIRE_CHECK, "A standard", "A check" + DEL)))),
                Arguments.of("source.ref", live(builder -> builder.sources(
                        List.of(new Source(Source.Kind.PLAN, "v2-156" + DEL, null))))),
                Arguments.of("promotion.ref", live(builder -> builder.promotions(
                        List.of(new Promotion(Promotion.Kind.EPIC, "An epic" + DEL, LessonFixtures.instant()))))),
                Arguments.of("merged_from.by", live(builder -> builder.mergedFrom(List.of(
                        new MergedFrom(LessonFixtures.lessonId(), LessonFixtures.instant(), "A plan" + DEL))))),
                Arguments.of("retired.by", retired(retirement -> retirement.by("A principal" + DEL))),
                Arguments.of("retired.reason", retired(retirement -> retirement.reason("A reason" + DEL))),
                Arguments.of("retired.covering_clause",
                        retired(retirement -> retirement.coveringClause("ADR core-store/075" + DEL))),
                Arguments.of("retired.covering_input",
                        retired(retirement -> retirement.coveringInput("An input" + DEL))));
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
        var lesson = live(builder -> builder.title("A title" + DEL));

        var reading = LessonCodec.decode(LessonCodec.encode(lesson));

        var readable = assertInstanceOf(LessonCodec.Readable.class, reading,
                "the DEL in the title must leave the file readable");
        assertEquals(lesson, readable.lesson(), "the DEL in the title round-trips unchanged");
    }

    @Test
    @DisplayName("the writer escapes DEL instead of leaving it to the YAML reader")
    void writerEscapesDel() {
        var encoded = new String(LessonCodec.encode(live(builder -> builder.title("A title" + DEL))), UTF_8);

        assertTrue(encoded.contains(ESCAPED_DEL), "the writer escapes DEL as \\u007F");
    }

    @Test
    @DisplayName("the reader refuses a literal DEL, which is why the writer escapes it")
    void readerRefusesALiteralDel() {
        var escaped = new String(LessonCodec.encode(live(builder -> builder.title("A title"))), UTF_8);

        var reading = LessonCodec.decode(escaped.replace("A title", "A title" + DEL).getBytes(UTF_8));

        assertInstanceOf(LessonCodec.Unreadable.class, reading, "the reader refuses a literal DEL in a scalar");
    }
}

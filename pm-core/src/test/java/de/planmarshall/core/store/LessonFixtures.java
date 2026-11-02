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

import de.cuioss.test.generator.Generators;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.store.LessonFrontMatter.Audience;
import de.planmarshall.core.store.LessonFrontMatter.Category;
import de.planmarshall.core.store.LessonFrontMatter.Provenance;
import de.planmarshall.core.store.LessonFrontMatter.Remedy;
import de.planmarshall.core.store.LessonFrontMatter.Revision;
import de.planmarshall.core.store.LessonFrontMatter.Source;
import de.planmarshall.core.store.LessonFrontMatter.Status;

/**
 * Valid values of the lesson record for the tests of this package, and for the second plan
 * ({@code lesson-store-operations}) to reuse (PM-TEST-2: the ported fixtures live in test code).
 * <p>
 * A literal stays only where it is the contract: the identifier and the digest are wire-format values, so
 * {@link #lessonId()} and the watch fixtures are spelled out; everything else is generated.
 */
@UtilityClass
public class LessonFixtures {

    /** The lesson id of the first watch fixture (PM-WATCH-STORE-14): a headerless legacy lesson. */
    public static final String WATCH_ID_NO_HEADER = "2026-09-22-12-001";

    /** A lesson id of the headerless legacy lessons of the watch fixture. */
    public static final String WATCH_ID_HEADERLESS = "2026-09-08-22-004";

    /** A second lesson id of the headerless legacy lessons of the watch fixture. */
    public static final String WATCH_ID_HEADERLESS_SECOND = "2026-09-13-06-002";

    /** @return a valid lesson id */
    public static LessonId lessonId() {
        return new LessonId(WATCH_ID_NO_HEADER);
    }

    /** @return a non-empty text of the size of a title or a component */
    public static String text() {
        return Generators.letterStrings(1, 20).next();
    }

    /** @return a statement of a remedy, inside the bounds of the schema */
    public static String statement() {
        return Generators.letterStrings(LessonFrontMatter.MIN_REMEDY_STATEMENT, 60).next();
    }

    /** @return a SHA-256 digest in the one shape the schema admits */
    public static String digest() {
        return "%064x".formatted(Generators.longs().next());
    }

    /** @return an instant */
    public static Instant instant() {
        return Instant.ofEpochSecond(Generators.longs(1_600_000_000L, 1_900_000_000L).next());
    }

    /** @return a date */
    public static LocalDate date() {
        return LocalDate.ofEpochDay(Generators.longs(20_000L, 21_000L).next());
    }

    /** @return a source of the evidence of a body */
    public static Source source() {
        return new Source(Source.Kind.PLAN, text(), null);
    }

    /** @return a remedy of the kind {@code check}, the simplest kind */
    public static Remedy remedy() {
        return new Remedy(Remedy.Kind.CHECK, statement(), null);
    }

    /**
     * @param n the counter of the revision
     * @return a revision entry
     */
    public static Revision revision(int n) {
        return new Revision(n, instant(), List.of(source()), digest());
    }

    /** @return a builder of a front matter that satisfies every rule of the specification */
    public static LessonFrontMatter.LessonFrontMatterBuilder frontMatterBuilder() {
        return LessonFrontMatter.builder()
                .formatVersion(LessonFrontMatter.FORMAT_VERSION)
                .lessonId(lessonId())
                .title(text())
                .component(text())
                .category(Category.BUG)
                .bundle(null)
                .rule(null)
                .remedy(remedy())
                .recurrenceCount(null)
                .lastSeen(null)
                .status(Status.ACTIVE)
                .provenance(Provenance.RUN_ARTIFACT)
                .derivedFromUntrusted(false)
                .audience(Audience.LOCAL)
                .lastFilingFailure(null)
                .createdAt(instant())
                .toolVersion(text())
                .promotions(List.of())
                .revision(1)
                .sources(List.of(source()))
                .revisions(List.of(revision(1)))
                .mergedFrom(List.of())
                .claim(null)
                .retired(null);
    }

    /** @return a front matter that satisfies every rule of the specification */
    public static LessonFrontMatter frontMatter() {
        return frontMatterBuilder().build();
    }

    /** @return a Markdown body */
    public static String body() {
        return "# Lesson\n\n" + text() + "\n";
    }

    /** @return a lesson with a valid front matter and a body */
    public static Lesson lesson() {
        return new Lesson(frontMatter(), body());
    }
}

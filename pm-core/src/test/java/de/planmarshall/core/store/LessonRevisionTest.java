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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.store.LessonFrontMatter.Retired.Verdict;
import de.planmarshall.core.store.LessonFrontMatter.Revision;
import de.planmarshall.core.store.LessonFrontMatter.Source;
import de.planmarshall.core.store.LessonFrontMatter.Status;
import de.planmarshall.core.store.LessonResolution.Found;
import de.planmarshall.core.store.LessonResolution.Unavailable;

/**
 * The revision of a lesson body (Store Schemas § Lesson Record, Revisions; PM-WATCH-STORE-17): the body is replaced
 * wholesale as the next revision, never edited in part, the new sources are never empty and are recorded with the
 * digest of the body they support, old bodies are not kept, and {@code derived_from_untrusted} never turns back.
 */
@EnableGeneratorController
@DisplayName("Lesson revision")
class LessonRevisionTest {

    private static final String PROJECT = "pm-mcp-core";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-02T09:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path base;

    private JournalScope lessons;
    private LessonStore store;
    private RecordingLockManager locks;
    private RecordingAuditSink audit;

    @BeforeEach
    void createStore() throws IOException {
        lessons = JournalScope.lessons(base, PROJECT);
        Files.createDirectories(lessons.pathBase());
        store = new LessonStore(lessons);
        locks = new RecordingLockManager();
        audit = new RecordingAuditSink();
    }

    private <T> T inTransaction(Function<ScopeTransaction, T> work) {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(lessons.lockKey());
            var write = ScopeTransaction.open(transaction, lessons, audit, CLOCK);
            var result = work.apply(write);
            write.commit();
            return result;
        }
    }

    private void run(Consumer<ScopeTransaction> work) {
        inTransaction(write -> {
            work.accept(write);
            return null;
        });
    }

    private <T> T withoutCommit(Function<ScopeTransaction, T> work) {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(lessons.lockKey());
            return work.apply(ScopeTransaction.open(transaction, lessons, audit, CLOCK));
        }
    }

    private Lesson create() {
        return inTransaction(write -> store.create(write, new LessonId("2026-11-02-09-001"),
                LessonFixtures.newLesson().build(), CLOCK));
    }

    private static String sha256(String body) {
        return StoreDigest.of(body.getBytes(StandardCharsets.UTF_8)).sha256();
    }

    @Nested
    @DisplayName("replaced body")
    class ReplacedBody {

        @Test
        @DisplayName("is the next revision, never an edit in part, and the old body is not kept")
        void wholeBody() {
            var lesson = create();
            var lessonId = lesson.frontMatter().lessonId();
            var sources = List.of(new Source(Source.Kind.FINDING, LessonFixtures.text(), null));

            var outcome = inTransaction(write -> store.revise(write, lessonId, "# Regenerated\n", sources, false,
                    CLOCK.instant().plusSeconds(60)));

            var revised = assertInstanceOf(LessonResolution.Revised.class, outcome);
            assertAll(
                    () -> assertEquals("# Regenerated\n", revised.lesson().body(), "the body is the new one alone"),
                    () -> assertEquals(2, revised.lesson().frontMatter().revision(), "the counter is incremented"),
                    () -> assertEquals(2, revised.lesson().frontMatter().revisions().size(), "one entry is appended"),
                    () -> assertEquals(2, revised.lesson().frontMatter().revisions().getLast().n(),
                            "the entry names the new revision"),
                    () -> assertEquals(CLOCK.instant().plusSeconds(60),
                            revised.lesson().frontMatter().revisions().getLast().at(), "the entry carries its instant"),
                    () -> assertEquals(sources, revised.lesson().frontMatter().revisions().getLast().sources(),
                            "the entry carries the new sources"),
                    () -> assertEquals(sha256("# Regenerated\n"),
                            revised.lesson().frontMatter().revisions().getLast().bodySha256(),
                            "the entry carries the digest of the new body"),
                    () -> assertEquals(1, revised.lesson().frontMatter().revisions().getFirst().n(),
                            "the earlier entry stays"),
                    () -> assertEquals(sha256(lesson.body()),
                            revised.lesson().frontMatter().revisions().getFirst().bodySha256(),
                            "the earlier entry keeps its digest"));
        }

        @Test
        @DisplayName("after two revisions holds three entries whose digests match each body written")
        void twoRevisions() {
            var lesson = create();
            var lessonId = lesson.frontMatter().lessonId();
            var bodies = List.of("# One\n", "# Two\n");

            run(write -> store.revise(write, lessonId, bodies.get(0), List.of(LessonFixtures.source()), false,
                    CLOCK.instant()));
            run(write -> store.revise(write, lessonId, bodies.get(1), List.of(LessonFixtures.source()), false,
                    CLOCK.instant()));

            var found = assertInstanceOf(Found.class, store.get(lessonId));
            var revisions = found.lesson().frontMatter().revisions();
            assertAll(
                    () -> assertEquals(3, revisions.size(), "the history holds three revisions"),
                    () -> assertEquals(3, found.lesson().frontMatter().revision(), "the current revision is 3"),
                    () -> assertEquals(sha256(lesson.body()), revisions.get(0).bodySha256(),
                            "the first digest is the first body"),
                    () -> assertEquals(sha256(bodies.get(0)), revisions.get(1).bodySha256(),
                            "the second digest is the second body"),
                    () -> assertEquals(sha256(bodies.get(1)), revisions.get(2).bodySha256(),
                            "the third digest is the third body"),
                    () -> assertEquals(List.of(1, 2, 3), revisions.stream().map(Revision::n).toList(),
                            "the entries are in order"));
        }

        @Test
        @DisplayName("without sources is refused and the lesson is untouched")
        void withoutSources() {
            var lesson = create();
            var lessonId = lesson.frontMatter().lessonId();

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> run(write -> store.revise(write,
                            lessonId, "# Body\n", List.of(), false, CLOCK.instant()))),
                    () -> assertEquals(Optional.of(lesson), store.get(lessonId) instanceof Found found
                            ? Optional.of(found.lesson()) : Optional.empty(), "the lesson is untouched"));
        }

        @Test
        @DisplayName("of a retired lesson is refused and reports the state")
        void retired() {
            var lesson = create();
            var lessonId = lesson.frontMatter().lessonId();
            run(write -> store.retire(write, lessonId, LessonFixtures.retirement(Verdict.OBSOLETE).build()));

            var outcome = withoutCommit(write -> store.revise(write, lessonId, "# Changed\n",
                    List.of(LessonFixtures.source()), false, CLOCK.instant()));

            var unavailable = assertInstanceOf(Unavailable.class, outcome);
            assertAll(
                    () -> assertEquals(LessonResolution.Refusal.RETIRED, unavailable.refusal(),
                            "the refusal names the retirement"),
                    () -> assertEquals(Status.RETIRED,
                            store.get(lessonId) instanceof Found found
                                    ? found.lesson().frontMatter().status() : null,
                            "the retired lesson is untouched"));
        }
    }

    @Nested
    @DisplayName("derived from untrusted")
    class DerivedFromUntrusted {

        @Test
        @DisplayName("a lesson that is true stays true whatever the argument")
        void neverTurnsBack() {
            var lesson = inTransaction(write -> store.create(write, new LessonId("2026-11-02-09-001"),
                    LessonFixtures.newLesson().derivedFromUntrusted(true).build(), CLOCK));
            var lessonId = lesson.frontMatter().lessonId();

            run(write -> store.revise(write, lessonId, "# Clean\n", List.of(LessonFixtures.source()), false,
                    CLOCK.instant()));

            var found = assertInstanceOf(Found.class, store.get(lessonId));
            assertTrue(found.lesson().frontMatter().derivedFromUntrusted(),
                    "a marked lesson stays marked after a clean revision");
        }

        @Test
        @DisplayName("a revision whose sources name an untrusted lesson becomes true")
        void citesUntrusted() {
            var untrusted = inTransaction(write -> store.create(write, new LessonId("2026-11-02-09-002"),
                    LessonFixtures.newLesson().derivedFromUntrusted(true).build(), CLOCK));
            var lesson = inTransaction(write -> store.create(write, new LessonId("2026-11-02-09-001"),
                    LessonFixtures.newLesson().build(), CLOCK));
            var lessonId = lesson.frontMatter().lessonId();
            var source = new Source(Source.Kind.LESSON, untrusted.frontMatter().lessonId().value(), null);

            run(write -> store.revise(write, lessonId, "# Cites\n", List.of(source), false, CLOCK.instant()));

            var found = assertInstanceOf(Found.class, store.get(lessonId));
            assertTrue(found.lesson().frontMatter().derivedFromUntrusted(),
                    "a revision that cites an untrusted lesson is marked");
        }

        @Test
        @DisplayName("a revision that cites a persisted blank body marked untrusted becomes untrusted")
        void citesPersistedEmptyShell() {
            var blank = inTransaction(write -> store.create(write, new LessonId("2026-11-02-09-002"),
                    LessonFixtures.newLesson().derivedFromUntrusted(true).body("  \n").build(), CLOCK));
            var lesson = inTransaction(write -> store.create(write, new LessonId("2026-11-02-09-001"),
                    LessonFixtures.newLesson().build(), CLOCK));
            var lessonId = lesson.frontMatter().lessonId();
            var source = new Source(Source.Kind.LESSON, blank.frontMatter().lessonId().value(), null);

            run(write -> store.revise(write, lessonId, "# Cites\n", List.of(source), false, CLOCK.instant()));

            var found = assertInstanceOf(Found.class, store.get(lessonId));
            assertTrue(found.lesson().frontMatter().derivedFromUntrusted(),
                    "a blank persisted lesson marked untrusted still marks the revision");
        }

        @Test
        @DisplayName("a revision that cites an absent lesson stays false")
        void citesAbsentLesson() {
            var lesson = inTransaction(write -> store.create(write, new LessonId("2026-11-02-09-001"),
                    LessonFixtures.newLesson().build(), CLOCK));
            var lessonId = lesson.frontMatter().lessonId();
            var source = new Source(Source.Kind.LESSON, "2026-11-02-09-999", null);

            run(write -> store.revise(write, lessonId, "# Cites\n", List.of(source), false, CLOCK.instant()));

            var found = assertInstanceOf(Found.class, store.get(lessonId));
            assertFalse(found.lesson().frontMatter().derivedFromUntrusted(),
                    "a cited lesson that does not resolve does not mark the revision");
        }

        @Test
        @DisplayName("a revision that cites a clean lesson created in the same transaction stays false")
        void citesPendingCleanLesson() {
            var lesson = inTransaction(write -> store.create(write, new LessonId("2026-11-02-09-001"),
                    LessonFixtures.newLesson().build(), CLOCK));
            var lessonId = lesson.frontMatter().lessonId();

            var outcome = inTransaction(write -> {
                var clean = store.create(write, new LessonId("2026-11-02-09-002"),
                        LessonFixtures.newLesson().build(), CLOCK);
                var source = new Source(Source.Kind.LESSON, clean.frontMatter().lessonId().value(), null);
                return store.revise(write, lessonId, "# Cites\n", List.of(source), false, CLOCK.instant());
            });

            var revised = assertInstanceOf(LessonResolution.Revised.class, outcome);
            assertFalse(revised.lesson().frontMatter().derivedFromUntrusted(),
                    "a pending clean lesson does not mark the revision");
        }

        @Test
        @DisplayName("a revision of a clean lesson that cites a trusted one stays false")
        void staysClean() {
            var trusted = inTransaction(write -> store.create(write, new LessonId("2026-11-02-09-002"),
                    LessonFixtures.newLesson().build(), CLOCK));
            var lesson = inTransaction(write -> store.create(write, new LessonId("2026-11-02-09-001"),
                    LessonFixtures.newLesson().build(), CLOCK));
            var lessonId = lesson.frontMatter().lessonId();
            var source = new Source(Source.Kind.LESSON, trusted.frontMatter().lessonId().value(), null);

            run(write -> store.revise(write, lessonId, "# Cites\n", List.of(source), false, CLOCK.instant()));

            var found = assertInstanceOf(Found.class, store.get(lessonId));
            assertFalse(found.lesson().frontMatter().derivedFromUntrusted(),
                    "a revision that cites a trusted lesson stays unmarked");
        }
    }
}

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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import de.planmarshall.core.store.LessonFrontMatter.Retired;
import de.planmarshall.core.store.LessonFrontMatter.Retired.LessonState;
import de.planmarshall.core.store.LessonFrontMatter.Retired.Verdict;
import de.planmarshall.core.store.LessonFrontMatter.Status;
import de.planmarshall.core.store.LessonResolution.Found;
import de.planmarshall.core.store.LessonResolution.Refusal;
import de.planmarshall.core.store.LessonResolution.Unavailable;

/**
 * The retirement of a lesson (Store Schemas § Lesson Record, Retirement; PM-WATCH-STORE-20): the closed verdict set with
 * no default, the members each verdict requires, the record and the status written in one transaction while the file
 * stays in place, and a retirement that acts only on a lesson resolved found and unclaimed.
 */
@EnableGeneratorController
@DisplayName("Lesson retirement")
class LessonRetirementTest {

    private static final String PROJECT = "pm-mcp-core";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-02T09:00:00Z"), ZoneOffset.UTC);
    private static final LessonId LESSON_ID = new LessonId("2026-11-02-09-001");

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

    private Path expectedPath(LessonId lessonId) {
        return LockKey.projectStore(base, PROJECT).resolve("lessons").resolve(lessonId.fileName());
    }

    private Lesson create() {
        return inTransaction(write -> store.create(write, LESSON_ID, LessonFixtures.newLesson().build(), CLOCK));
    }

    /** Records the digest of a file written outside a transaction, as the operator's accept-edit does. */
    private void acceptEdit(LessonId lessonId) {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(lessons.lockKey());
            ScopeIntegrity.rebaseline(transaction, lessons, lessonId.fileName(), audit);
        }
    }

    private LessonResolution.Outcome retire(Retired retirement) {
        return withoutCommit(write -> store.retire(write, LESSON_ID, retirement));
    }

    @Nested
    @DisplayName("the closed verdict set")
    class Verdicts {

        @ParameterizedTest
        @EnumSource(Verdict.class)
        @DisplayName("retires a found, unclaimed lesson with every member its verdict requires")
        void eachVerdict(Verdict verdict) {
            var lesson = create();
            var retirement = LessonFixtures.retirement(verdict).build();

            var outcome = inTransaction(write -> store.retire(write, LESSON_ID, retirement));

            var retired = assertInstanceOf(LessonResolution.Retired.class, outcome);
            var frontMatter = retired.lesson().frontMatter();
            assertAll(
                    () -> assertEquals(Status.RETIRED, frontMatter.status(), "the status is retired"),
                    () -> assertEquals(retirement, frontMatter.retired(), "the record is the one given"),
                    () -> assertEquals(verdict, frontMatter.retired().verdict(), "the verdict is recorded"),
                    () -> assertEquals(lesson.body(), retired.lesson().body(), "the body stays in place"),
                    () -> assertEquals(expectedPath(LESSON_ID), store.store(LESSON_ID),
                            "the file stays where it was"));
        }

        @Test
        @DisplayName("a completely_covered retirement needs a non-blank clause and input")
        void completelyCoveredMembers() {
            create();

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> retire(LessonFixtures
                            .retirement(Verdict.COMPLETELY_COVERED).coveringClause(null).build())),
                    () -> assertThrows(IllegalArgumentException.class, () -> retire(LessonFixtures
                            .retirement(Verdict.COMPLETELY_COVERED).coveringInput(null).build())),
                    () -> assertThrows(IllegalArgumentException.class, () -> retire(LessonFixtures
                            .retirement(Verdict.COMPLETELY_COVERED).coveringClause("  ").build())),
                    () -> assertThrows(IllegalArgumentException.class, () -> retire(LessonFixtures
                            .retirement(Verdict.COMPLETELY_COVERED).coveringInput("\t").build())));
        }

        @Test
        @DisplayName("into is set exactly for merged and superseded")
        void intoMembers() {
            create();

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> retire(LessonFixtures.retirement(Verdict.MERGED).into(null).build())),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> retire(LessonFixtures.retirement(Verdict.SUPERSEDED).into(null).build())),
                    () -> assertThrows(IllegalArgumentException.class, () -> retire(LessonFixtures
                            .retirement(Verdict.APPLIED).into(new LessonId(LessonFixtures.WATCH_ID_NO_HEADER))
                            .build())));
        }

        @Test
        @DisplayName("a retirement cannot record a presentation state the store does not retire")
        void presentUnreadableNeedsTheOperatorAct() {
            create();

            assertThrows(IllegalArgumentException.class,
                    () -> retire(LessonFixtures.retirement(Verdict.OBSOLETE)
                            .lessonState(LessonState.PRESENT_UNREADABLE).build()));
        }

        @Test
        @DisplayName("the premise retire records the verdict obsolete and the entry id as its author")
        void premiseRetire() {
            create();
            var retirement = LessonFixtures.retirement(Verdict.OBSOLETE).by("ent-0123456789abcdef").build();

            var outcome = inTransaction(write -> store.retire(write, LESSON_ID, retirement));

            var retired = assertInstanceOf(LessonResolution.Retired.class, outcome);
            assertAll(
                    () -> assertEquals(Verdict.OBSOLETE, retired.lesson().frontMatter().retired().verdict(),
                            "the premise retires with the verdict obsolete"),
                    () -> assertEquals("ent-0123456789abcdef", retired.lesson().frontMatter().retired().by(),
                            "the entry id is the author of the retirement"));
        }
    }

    @Nested
    @DisplayName("the state the lesson is in")
    class States {

        @Test
        @DisplayName("a found lesson is retired and the file keeps its path and its body")
        void found() throws Exception {
            var lesson = create();
            var before = Files.readAllBytes(expectedPath(LESSON_ID));

            var outcome = inTransaction(write -> store.retire(write, LESSON_ID,
                    LessonFixtures.retirement(Verdict.REDUNDANT).build()));

            assertAll(
                    () -> assertInstanceOf(LessonResolution.Retired.class, outcome, "a found lesson is retired"),
                    () -> assertTrue(Files.exists(expectedPath(LESSON_ID)), "the file stays in place"),
                    () -> assertTrue(before.length > 0, "the fixture wrote the lesson"));
        }

        @Test
        @DisplayName("a claimed lesson is not retired and the claiming plan is named")
        void claimed() {
            create();
            run(write -> store.claim(write, LESSON_ID, "a-plan", CLOCK.instant()));

            var outcome = retire(LessonFixtures.retirement(Verdict.OBSOLETE).build());

            var unavailable = assertInstanceOf(Unavailable.class, outcome);
            assertAll(
                    () -> assertEquals(Refusal.CLAIMED, unavailable.refusal(), "the refusal names the claim"),
                    () -> assertEquals("a-plan", unavailable.claimingPlan(), "the claiming plan is named"),
                    () -> assertEquals(Status.ACTIVE, readFound().frontMatter().status(), "the lesson stays active"));
        }

        @Test
        @DisplayName("a retired lesson is not retired again and keeps its record")
        void retired() {
            create();
            run(write -> store.retire(write, LESSON_ID, LessonFixtures.retirement(Verdict.REDUNDANT).build()));
            var recorded = readFound().frontMatter().retired();

            var outcome = retire(LessonFixtures.retirement(Verdict.OBSOLETE).build());

            var unavailable = assertInstanceOf(Unavailable.class, outcome);
            assertAll(
                    () -> assertEquals(Refusal.RETIRED, unavailable.refusal(), "the refusal names the retirement"),
                    () -> assertEquals(recorded, readFound().frontMatter().retired(), "the record is unchanged"));
        }

        @Test
        @DisplayName("an absent lesson changes nothing and is reported absent")
        void absent() {
            var unavailable = assertInstanceOf(Unavailable.class,
                    retire(LessonFixtures.retirement(Verdict.OBSOLETE).build()));

            assertEquals(Refusal.ABSENT, unavailable.refusal(), "an absent lesson is reported absent");
        }

        @Test
        @DisplayName("an unreadable lesson is reported present_unreadable, never absent")
        void presentUnreadable() throws IOException {
            Files.writeString(expectedPath(LESSON_ID), "# No front matter\n");
            acceptEdit(LESSON_ID);

            var unavailable = assertInstanceOf(Unavailable.class,
                    retire(LessonFixtures.retirement(Verdict.OBSOLETE).build()));

            assertEquals(Refusal.PRESENT_UNREADABLE, unavailable.refusal(),
                    "an unreadable lesson is reported present_unreadable");
        }

        @Test
        @DisplayName("an empty shell is reported empty_shell and is not retired")
        void emptyShell() throws IOException {
            var lesson = new Lesson(LessonFixtures.frontMatterBuilder().lessonId(LESSON_ID).build(), " \n");
            Files.write(expectedPath(LESSON_ID), LessonCodec.encode(lesson));
            acceptEdit(LESSON_ID);

            var unavailable = assertInstanceOf(Unavailable.class,
                    retire(LessonFixtures.retirement(Verdict.OBSOLETE).build()));

            assertEquals(Refusal.EMPTY_SHELL, unavailable.refusal(), "an empty shell is reported empty_shell");
        }

        @Test
        @DisplayName("a tampered lesson is reported store_tampered and is not touched")
        void storeTampered() throws Exception {
            create();
            var bytes = Files.readAllBytes(expectedPath(LESSON_ID));
            bytes[bytes.length - 1] = '!';
            Files.write(expectedPath(LESSON_ID), bytes);

            var unavailable = assertInstanceOf(Unavailable.class,
                    retire(LessonFixtures.retirement(Verdict.OBSOLETE).build()));

            var after = Files.readAllBytes(expectedPath(LESSON_ID));
            assertAll(
                    () -> assertEquals(Refusal.STORE_TAMPERED, unavailable.refusal(),
                            "a tampered lesson is reported store_tampered"),
                    () -> assertArrayEquals(bytes, after, "the tampered file is left as it was"));
        }

        @Test
        @DisplayName("a lesson deleted out of band is reported deleted_out_of_band, never absent")
        void deletedOutOfBand() throws IOException {
            create();
            Files.delete(expectedPath(LESSON_ID));

            var unavailable = assertInstanceOf(Unavailable.class,
                    retire(LessonFixtures.retirement(Verdict.OBSOLETE).build()));

            assertEquals(Refusal.DELETED_OUT_OF_BAND, unavailable.refusal(),
                    "a vanished file with its entry is reported deleted_out_of_band");
        }

        @Test
        @DisplayName("a file without an entry is reported untracked")
        void untracked() throws IOException {
            var lesson = new Lesson(LessonFixtures.frontMatterBuilder().lessonId(LESSON_ID).build(), "# Body\n");
            Files.write(expectedPath(LESSON_ID), LessonCodec.encode(lesson));

            var unavailable = assertInstanceOf(Unavailable.class,
                    retire(LessonFixtures.retirement(Verdict.OBSOLETE).build()));

            assertEquals(Refusal.UNTRACKED, unavailable.refusal(), "a file without an entry is reported untracked");
        }

        private Lesson readFound() {
            return assertInstanceOf(Found.class, store.get(LESSON_ID)).lesson();
        }
    }

    @Nested
    @DisplayName("what the store writes")
    class Writes {

        @Test
        @DisplayName("the retirement record and the status are written in one transaction")
        void oneTransaction() throws Exception {
            create();
            var before = Files.readAllBytes(expectedPath(LESSON_ID));

            var retirement = LessonFixtures.retirement(Verdict.COMPLETELY_COVERED).build();
            run(write -> store.retire(write, LESSON_ID, retirement));

            var found = assertInstanceOf(Found.class, store.get(LESSON_ID));
            assertAll(
                    () -> assertFalse(java.util.Arrays.equals(before, Files.readAllBytes(expectedPath(LESSON_ID))),
                            "the file is rewritten in place"),
                    () -> assertEquals(retirement, found.lesson().frontMatter().retired(),
                            "the retirement is in the same file"),
                    () -> assertEquals(1, found.lesson().frontMatter().revisions().size(),
                            "a retirement adds no revision"),
                    () -> assertFalse(Files.exists(lessons.journalFile()), "the transaction is closed"));
        }
    }
}

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

import static org.easymock.EasyMock.newCapture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Stream;
import org.easymock.CaptureType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.store.LessonFrontMatter.Source;
import de.planmarshall.core.store.LessonResolution.Found;
import de.planmarshall.core.store.LessonResolution.Revised;

/**
 * The untrusted mark a citation carries (Store Schemas § Lesson Record, Derived from Untrusted Text; PM-SEC-1): a
 * revision is marked when a cited lesson is marked, or when the cited lesson cannot be read at all — a citation
 * whose trust cannot be established is never trusted. A citation of a lesson that does not exist reads nothing and
 * stays clean.
 */
@EnableGeneratorController
@DisplayName("Untrusted citation")
class LessonStoreUntrustedCitationTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-02T09:00:00Z"), ZoneOffset.UTC);
    private static final LessonId CITED = new LessonId("2026-11-02-09-002");
    private static final LessonId REVISED = new LessonId("2026-11-02-09-001");
    private static final Path FILE = Path.of("lessons", CITED.fileName());
    private static final String NO_FRONT_MATTER = "# No front matter\n";

    @TempDir
    Path base;

    private JournalScope lessons;
    private LessonStore store;
    private RecordingLockManager locks;
    private AuditSink audit;

    @BeforeEach
    void createStore() throws IOException {
        var project = Generators.letterStrings(4, 12).next().toLowerCase(Locale.ROOT);
        lessons = JournalScope.lessons(base, project);
        Files.createDirectories(lessons.pathBase());
        store = new LessonStore(lessons);
        locks = new RecordingLockManager();
        audit = AuditSinkDoubles.capturing(newCapture(CaptureType.ALL));
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

    private Lesson created(LessonId lessonId) {
        return inTransaction(write -> store.create(write, lessonId, LessonFixtures.newLesson().build(), CLOCK));
    }

    private static Source cites(LessonId lessonId) {
        return new Source(Source.Kind.LESSON, lessonId.value(), null);
    }

    private static Lesson lesson(boolean marked) {
        return new Lesson(LessonFixtures.frontMatterBuilder().derivedFromUntrusted(marked).build(),
                LessonFixtures.body());
    }

    private static LessonResolution found(boolean marked) {
        return Found.builder().lessonId(CITED).lesson(lesson(marked)).path(FILE)
                .integrity(StoreIntegrity.VERIFIED).build();
    }

    private static LessonResolution shell(boolean marked) {
        return LessonResolution.EmptyShell.builder().lessonId(CITED).lesson(lesson(marked)).path(FILE)
                .integrity(StoreIntegrity.VERIFIED).build();
    }

    private static LessonResolution unreadable() {
        return LessonResolution.PresentUnreadable.builder().lessonId(CITED).path(FILE)
                .reason(LessonCodec.UnreadableReason.NO_OPENING_FENCE).detail("no fence").build();
    }

    private static LessonResolution tampered() {
        return LessonResolution.StoreTampered.builder().lessonId(CITED).path(FILE)
                .recorded(StoreDigest.of(LessonFixtures.body().getBytes(StandardCharsets.UTF_8))).current(null).build();
    }

    static Stream<Arguments> resolutionArms() {
        return Stream.of(
                Arguments.of("found and marked", found(true), true),
                Arguments.of("found and clean", found(false), false),
                Arguments.of("empty shell and marked", shell(true), true),
                Arguments.of("empty shell and clean", shell(false), false),
                Arguments.of("present_unreadable", unreadable(), true),
                Arguments.of("store_tampered", tampered(), true),
                Arguments.of("deleted_out_of_band", new LessonResolution.DeletedOutOfBand(CITED), true),
                Arguments.of("untracked", new LessonResolution.Untracked(CITED, FILE), true),
                Arguments.of("absent", new LessonResolution.Absent(CITED), false));
    }

    @ParameterizedTest(name = "a cited lesson that is {0} marks the revision untrusted: {2}")
    @MethodSource("resolutionArms")
    @DisplayName("every resolution state of a citation has its own arm")
    void resolutionArms(String arm, LessonResolution resolution, boolean untrusted) {
        assertFalse(arm.isBlank(), "the case names its arm");

        assertEquals(untrusted, LessonStore.isUntrusted(resolution),
                "the resolution state decides the untrusted mark");
    }

    static Stream<Arguments> contentArms() {
        return Stream.of(
                Arguments.of("a clean lesson", LessonCodec.encode(lesson(false)), false),
                Arguments.of("a marked lesson", LessonCodec.encode(lesson(true)), true),
                Arguments.of("bytes that do not decode", NO_FRONT_MATTER.getBytes(StandardCharsets.UTF_8), true));
    }

    @ParameterizedTest(name = "a staged citation of {0} marks the revision untrusted: {2}")
    @MethodSource("contentArms")
    @DisplayName("every staged content of a citation has its own arm")
    void contentArms(String name, byte[] content, boolean untrusted) {
        assertFalse(name.isBlank(), "the case names the arm");

        assertEquals(untrusted, LessonStore.isUntrusted(content), "the staged content decides the untrusted mark");
    }

    @Test
    @DisplayName("a citation of bytes that do not decode in the same transaction marks the revision")
    void citesUnreadableStagedLesson() {
        created(CITED);
        var lesson = created(REVISED);
        var lessonId = lesson.frontMatter().lessonId();

        inTransaction(write -> {
            write.replace(LessonStore.storePath(CITED), NO_FRONT_MATTER.getBytes(StandardCharsets.UTF_8));
            return store.revise(write, lessonId, "# Cites\n", List.of(cites(CITED)), false, CLOCK.instant());
        });

        var found = assertInstanceOf(Found.class, store.get(lessonId), "the revised lesson is still found");
        assertTrue(found.lesson().frontMatter().derivedFromUntrusted(),
                "a citation that cannot be decoded marks the revision");
    }

    @Test
    @DisplayName("a citation of a lesson deleted in the same transaction marks the revision")
    void citesLessonDeletedInTheSameTransaction() {
        created(CITED);
        var lesson = created(REVISED);
        var lessonId = lesson.frontMatter().lessonId();

        inTransaction(write -> {
            write.delete(LessonStore.storePath(CITED));
            return store.revise(write, lessonId, "# Cites\n", List.of(cites(CITED)), false, CLOCK.instant());
        });

        var found = assertInstanceOf(Found.class, store.get(lessonId), "the revised lesson is still found");
        assertTrue(found.lesson().frontMatter().derivedFromUntrusted(),
                "a citation deleted in the transaction marks the revision");
    }

    @Test
    @DisplayName("a malformed lesson reference is refused and the lesson is left untouched")
    void refusesMalformedLessonReference() {
        created(CITED);
        var lesson = created(REVISED);
        var lessonId = lesson.frontMatter().lessonId();
        var malformed = new Source(Source.Kind.LESSON, "not-a-lesson-id", null);

        var exception = assertThrows(IllegalArgumentException.class,
                () -> inTransaction(write -> store.revise(write, lessonId, "# Cites\n", List.of(malformed), false,
                        CLOCK.instant())),
                "a malformed lesson reference is refused");

        assertEquals("A lesson source names a lesson id: 'not-a-lesson-id'", exception.getMessage(),
                "the refusal names the malformed reference");
    }
}

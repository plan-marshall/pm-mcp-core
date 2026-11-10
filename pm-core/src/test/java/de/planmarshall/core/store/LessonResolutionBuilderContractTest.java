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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builders of the resolution records of {@link LessonResolution} build the values the canonical constructors build,
 * and an absent lesson id is refused with the same refusal.
 */
@DisplayName("Lesson resolution builders")
class LessonResolutionBuilderContractTest {

    private static final Path LESSON_FILE = Path.of("/pm-base/lessons/2026-09-22-12-001.md");

    @Test
    @DisplayName("the found builder builds the constructor's value and refuses an absent lesson id")
    void shouldBuildFoundLikeTheConstructorAndRefuseAnAbsentLessonId() {
        var canonical = new LessonResolution.Found(LessonFixtures.lessonId(), LessonFixtures.lesson(), LESSON_FILE,
                StoreIntegrity.VERIFIED);
        var built = LessonResolution.Found.builder().lessonId(canonical.lessonId()).lesson(canonical.lesson())
                .path(canonical.path()).integrity(canonical.integrity()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new LessonResolution.Found(null, canonical.lesson(), LESSON_FILE,
                                    StoreIntegrity.VERIFIED),
                            "the constructor refuses a null lesson id");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> LessonResolution.Found.builder().lesson(canonical.lesson()).path(LESSON_FILE)
                                    .integrity(StoreIntegrity.VERIFIED).build(),
                            "the builder refuses a missing lesson id");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent lesson id with the same message");
                });
    }

    @Test
    @DisplayName("the present unreadable builder builds the constructor's value and refuses an absent lesson id")
    void shouldBuildPresentUnreadableLikeTheConstructorAndRefuseAnAbsentLessonId() {
        var canonical = new LessonResolution.PresentUnreadable(LessonFixtures.lessonId(), LESSON_FILE,
                LessonCodec.UnreadableReason.NO_OPENING_FENCE, "the file has no opening fence");
        var built = LessonResolution.PresentUnreadable.builder().lessonId(canonical.lessonId())
                .path(canonical.path()).reason(canonical.reason()).detail(canonical.detail()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new LessonResolution.PresentUnreadable(null, LESSON_FILE,
                                    LessonCodec.UnreadableReason.NO_OPENING_FENCE, "the file has no opening fence"),
                            "the constructor refuses a null lesson id");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> LessonResolution.PresentUnreadable.builder().path(LESSON_FILE)
                                    .reason(LessonCodec.UnreadableReason.NO_OPENING_FENCE)
                                    .detail("the file has no opening fence").build(),
                            "the builder refuses a missing lesson id");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent lesson id with the same message");
                });
    }

    @Test
    @DisplayName("the empty shell builder builds the constructor's value and refuses an absent lesson id")
    void shouldBuildEmptyShellLikeTheConstructorAndRefuseAnAbsentLessonId() {
        var canonical = new LessonResolution.EmptyShell(LessonFixtures.lessonId(), LessonFixtures.lesson(),
                LESSON_FILE, StoreIntegrity.UNCHECKED);
        var built = LessonResolution.EmptyShell.builder().lessonId(canonical.lessonId())
                .lesson(canonical.lesson()).path(canonical.path()).integrity(canonical.integrity()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new LessonResolution.EmptyShell(null, canonical.lesson(), LESSON_FILE,
                                    StoreIntegrity.UNCHECKED),
                            "the constructor refuses a null lesson id");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> LessonResolution.EmptyShell.builder().lesson(canonical.lesson()).path(LESSON_FILE)
                                    .integrity(StoreIntegrity.UNCHECKED).build(),
                            "the builder refuses a missing lesson id");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent lesson id with the same message");
                });
    }

    @Test
    @DisplayName("the store tampered builder builds the constructor's value and refuses an absent lesson id")
    void shouldBuildStoreTamperedLikeTheConstructorAndRefuseAnAbsentLessonId() {
        var recorded = new StoreDigest(42, "a".repeat(64));
        var canonical = new LessonResolution.StoreTampered(LessonFixtures.lessonId(), LESSON_FILE, recorded, null);
        var built = LessonResolution.StoreTampered.builder().lessonId(canonical.lessonId())
                .path(canonical.path()).recorded(canonical.recorded()).current(canonical.current()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new LessonResolution.StoreTampered(null, LESSON_FILE, recorded, null),
                            "the constructor refuses a null lesson id");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> LessonResolution.StoreTampered.builder().path(LESSON_FILE).recorded(recorded)
                                    .build(),
                            "the builder refuses a missing lesson id");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent lesson id with the same message");
                });
    }
}
